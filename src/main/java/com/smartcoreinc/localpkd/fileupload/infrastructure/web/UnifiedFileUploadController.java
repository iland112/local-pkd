package com.smartcoreinc.localpkd.fileupload.infrastructure.web;

import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.exception.ErrorResponse;
import com.smartcoreinc.localpkd.fileupload.application.command.CheckDuplicateFileCommand;
import com.smartcoreinc.localpkd.fileupload.application.response.CheckDuplicateResponse;
import com.smartcoreinc.localpkd.fileupload.application.service.AsyncUploadProcessor;
import com.smartcoreinc.localpkd.fileupload.application.usecase.CheckDuplicateFileUseCase;
import com.smartcoreinc.localpkd.fileupload.domain.model.ProcessingMode;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

@Tag(name = "File Upload", description = "통합 파일 업로드 API - LDIF 및 Master List 파일 업로드, 중복 검사")
@Slf4j
@Controller
@RequestMapping("/file")
@RequiredArgsConstructor
public class UnifiedFileUploadController {

    private final AsyncUploadProcessor asyncUploadProcessor;
    private final CheckDuplicateFileUseCase checkDuplicateFileUseCase;

    @GetMapping("/upload")
    public String showUploadPage(Model model) {
        log.info("Unified file upload page accessed");
        return "file/upload";
    }

    @Operation(
        summary = "파일 비동기 업로드",
        description = """
            LDIF 또는 Master List 파일을 비동기적으로 업로드합니다.
            요청이 접수되면 즉시 uploadId를 반환하고, 백그라운드에서 파일 처리를 시작합니다.
            클라이언트는 반환된 uploadId를 사용하여 SSE 엔드포인트에서 진행 상황을 수신할 수 있습니다.

            **처리 흐름**:
            1. 요청 접수 후 즉시 `202 Accepted` 상태와 `uploadId` 반환
            2. (백그라운드) 파일 확장자 검사 (.ldif 또는 .ml)
            3. (백그라운드) 파일 해시로 중복 검사 (forceUpload=false인 경우)
            4. (백그라운드) 파일 시스템에 저장
            5. (백그라운드) 메타데이터 추출 및 데이터베이스에 저장
            6. (백그라운드) AUTO 모드: 자동으로 파싱 → 검증 → LDAP 업로드
            """,
        tags = {"File Upload"}
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "202",
            description = "업로드 요청 접수됨 - uploadId 반환",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                               schema = @Schema(implementation = UploadAcceptedResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 파일 형식 오류, 필수 파라미터 누락 등",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                               schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "서버 오류",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                               schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "forceUpload", defaultValue = "false") boolean forceUpload,
            @RequestParam(value = "expectedChecksum", required = false) String expectedChecksum,
            @RequestParam("fileHash") String fileHash,
            @RequestParam(value = "processingMode", defaultValue = "AUTO") String processingModeStr) {
        log.info("=== Unified async file upload request ===");
        log.info("File: {}, Size: {}, ProcessingMode: {}, ForceUpload: {}",
                file.getOriginalFilename(), file.getSize(), processingModeStr, forceUpload);

        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest("파일이 선택되지 않았습니다"));
            }

            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isBlank()) {
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest("파일명이 없습니다"));
            }

            ProcessingMode processingMode;
            try {
                processingMode = ProcessingMode.valueOf(processingModeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid processing mode: {}, using AUTO", processingModeStr);
                processingMode = ProcessingMode.AUTO;
            }

            byte[] fileContent = file.getBytes();
            UploadId uploadId = UploadId.newId();

            if (fileName.toLowerCase().endsWith(".ldif")) {
                log.info("Queuing LDIF file for processing: {}", fileName);
                asyncUploadProcessor.processLdif(uploadId, fileName, fileContent, file.getSize(), fileHash,
                        expectedChecksum, forceUpload, processingMode);
            } else if (fileName.toLowerCase().endsWith(".ml")) {
                log.info("Queuing Master List file for processing: {}", fileName);
                asyncUploadProcessor.processMasterList(uploadId, fileName, fileContent, file.getSize(), fileHash,
                        expectedChecksum, forceUpload, processingMode);
            } else {
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest("지원하지 않는 파일 형식: " + fileName +
                        " (LDIF 또는 Master List 파일만 지원)"));
            }

            log.info("File upload request accepted: uploadId={}", uploadId.getId());
            
            return ResponseEntity.accepted()
                    .header("X-Upload-ID", uploadId.getId().toString())
                    .body(new UploadAcceptedResponse("File processing started.", uploadId.getId().toString()));

        } catch (IOException e) {
            log.error("Failed to read file content", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.internalServerError("파일을 읽을 수 없습니다: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during file upload request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.internalServerError("파일 업로드 요청 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    // Inner DTO for the 202 Accepted response
    private record UploadAcceptedResponse(String message, String uploadId) {}


    @Operation(
        summary = "LDIF 파일 중복 검사",
        description = "...",
        tags = {"File Upload"}
    )
    @PostMapping("/ldif/api/check-duplicate")
    @ResponseBody
    public ResponseEntity<CheckDuplicateResponse> checkDuplicateLdif(
            @RequestBody CheckDuplicateFileCommand command) {
        log.debug("Check duplicate API called (LDIF): fileName={}", command.fileName());
        try {
            CheckDuplicateResponse response = checkDuplicateFileUseCase.execute(command);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during duplicate check (LDIF)", e);
            return ResponseEntity.ok(CheckDuplicateResponse.noDuplicate());
        }
    }

    @Operation(
        summary = "Master List 파일 중복 검사",
        description = "...",
        tags = {"File Upload"}
    )
    @PostMapping("/masterlist/api/check-duplicate")
    @ResponseBody
    public ResponseEntity<CheckDuplicateResponse> checkDuplicateMasterList(
            @RequestBody CheckDuplicateFileCommand command) {
        log.debug("Check duplicate API called (Master List): fileName={}", command.fileName());
        try {
            CheckDuplicateResponse response = checkDuplicateFileUseCase.execute(command);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during duplicate check (Master List)", e);
            return ResponseEntity.ok(CheckDuplicateResponse.noDuplicate());
        }
    }
}