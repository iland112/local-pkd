package com.smartcoreinc.localpkd.fileupload.infrastructure.web;

import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.exception.ErrorResponse;
import com.smartcoreinc.localpkd.fileupload.application.service.AsyncUploadProcessor;
import com.smartcoreinc.localpkd.fileupload.domain.model.ProcessingMode;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.fileupload.infrastructure.exception.FileUploadException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Tag(name = "File Upload", description = "통합 파일 업로드 API")
@Slf4j
@Controller
@RequestMapping("/file")
@RequiredArgsConstructor
public class UnifiedFileUploadController {

    private final AsyncUploadProcessor asyncUploadProcessor;

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
            CHECKSUM은 백엔드에서 계산됩니다.
            """,
        tags = {"File Upload"}
    )
    @ApiResponse(responseCode = "202", description = "업로드 요청 접수됨", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = UploadAcceptedResponse.class)))
    @ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "중복 파일", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "forceUpload", defaultValue = "false") boolean forceUpload,
            @RequestParam(value = "processingMode", defaultValue = "AUTO") String processingModeStr) {
        
        log.info("=== Unified async file upload request ===");
        log.info("File: {}, Size: {}, ProcessingMode: {}, ForceUpload: {}",
                file.getOriginalFilename(), file.getSize(), processingModeStr, forceUpload);

        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest("파일이 선택되지 않았습니다."));
            }

            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isBlank()) {
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest("파일명이 없습니다."));
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
                asyncUploadProcessor.processLdif(uploadId, fileName, fileContent, file.getSize(), forceUpload, processingMode);
            } else if (fileName.toLowerCase().endsWith(".ml")) {
                log.info("Queuing Master List file for processing: {}", fileName);
                asyncUploadProcessor.processMasterList(uploadId, fileName, fileContent, file.getSize(), forceUpload, processingMode);
            } else {
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest("지원하지 않는 파일 형식입니다: " + fileName));
            }

            log.info("File upload request accepted: uploadId={}", uploadId.getId());
            return ResponseEntity.accepted().body(new UploadAcceptedResponse("File processing started.", uploadId.getId().toString()));

        } catch (FileUploadException.DuplicateFileException e) {
            log.warn("Duplicate file detected during upload: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.conflict("DUPLICATE_FILE", e.getMessage(), e.getDetails()));
        } catch (IOException e) {
            log.error("Failed to read file content", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.internalServerError("파일을 읽을 수 없습니다."));
        } catch (Exception e) {
            log.error("Unexpected error during file upload request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.internalServerError("파일 업로드 중 알 수 없는 오류가 발생했습니다."));
        }
    }

    private record UploadAcceptedResponse(String message, String uploadId) {}
}