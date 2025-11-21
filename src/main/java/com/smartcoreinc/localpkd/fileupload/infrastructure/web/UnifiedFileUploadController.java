package com.smartcoreinc.localpkd.fileupload.infrastructure.web;

import com.smartcoreinc.localpkd.fileupload.application.command.CheckDuplicateFileCommand;
import com.smartcoreinc.localpkd.fileupload.application.command.UploadLdifFileCommand;
import com.smartcoreinc.localpkd.fileupload.application.command.UploadMasterListFileCommand;
import com.smartcoreinc.localpkd.fileupload.application.response.CheckDuplicateResponse;
import com.smartcoreinc.localpkd.fileupload.application.response.UploadFileResponse;
import com.smartcoreinc.localpkd.fileupload.application.usecase.CheckDuplicateFileUseCase;
import com.smartcoreinc.localpkd.fileupload.application.usecase.UploadLdifFileUseCase;
import com.smartcoreinc.localpkd.fileupload.application.usecase.UploadMasterListFileUseCase;
import com.smartcoreinc.localpkd.fileupload.domain.model.ProcessingMode;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

/**
 * 통합 파일 업로드 웹 컨트롤러 (DDD)
 *
 * <p>LDIF 파일과 Master List 파일을 모두 처리하는 통합 파일 업로드 컨트롤러입니다.
 * 자동 모드(AUTO)와 수동 모드(MANUAL) 처리를 지원합니다.</p>
 *
 * <h3>지원하는 파일 형식</h3>
 * <ul>
 *   <li>LDIF 파일 (.ldif): LDAP Data Interchange Format</li>
 *   <li>Master List 파일 (.ml): CMS Signed Master List</li>
 * </ul>
 *
 * <h3>처리 모드</h3>
 * <ul>
 *   <li><strong>AUTO</strong>: 파일 업로드 후 자동으로 파싱, 검증, LDAP 등록 진행</li>
 *   <li><strong>MANUAL</strong>: 각 단계를 사용자가 수동으로 트리거</li>
 * </ul>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>GET /file/upload: 통합 파일 업로드 페이지</li>
 *   <li>POST /file/upload: 파일 업로드 처리</li>
 *   <li>POST /ldif/api/check-duplicate: LDIF 중복 검사 API</li>
 *   <li>POST /masterlist/api/check-duplicate: Master List 중복 검사 API</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 2.0 (Unified Upload)
 * @since 2025-10-24
 */
@Tag(name = "File Upload", description = "통합 파일 업로드 API - LDIF 및 Master List 파일 업로드, 중복 검사")
@Slf4j
@Controller
@RequestMapping("/file")
@RequiredArgsConstructor
public class UnifiedFileUploadController {

    private final UploadLdifFileUseCase uploadLdifFileUseCase;
    private final UploadMasterListFileUseCase uploadMasterListFileUseCase;
    private final CheckDuplicateFileUseCase checkDuplicateFileUseCase;

    /**
     * 통합 파일 업로드 페이지 표시
     */
    @GetMapping("/upload")
    public String showUploadPage(Model model) {
        log.info("Unified file upload page accessed");
        return "file/upload";
    }

    /**
     * 파일 업로드 처리 (LDIF 또는 Master List)
     *
     * <p>업로드된 파일의 확장자에 따라 자동으로 LDIF 또는 Master List 업로드 Use Case를 선택합니다.
     * processingMode 파라미터로 자동 모드와 수동 모드를 제어할 수 있습니다.</p>
     */
    @Operation(
        summary = "파일 업로드",
        description = """
            LDIF 또는 Master List 파일을 업로드합니다.

            **처리 흐름**:
            1. 파일 확장자 검사 (.ldif 또는 .ml)
            2. 파일 해시로 중복 검사 (forceUpload=false인 경우)
            3. 파일 시스템에 저장
            4. 메타데이터 추출 (Collection 번호, 버전)
            5. 데이터베이스에 저장
            6. AUTO 모드: 자동으로 파싱 → 검증 → LDAP 업로드
            7. MANUAL 모드: 각 단계를 수동으로 트리거

            **중요**: fileHash 파라미터는 필수입니다. SHA-256 해시값을 계산하여 전송해야 합니다.
            """,
        tags = {"File Upload"}
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "302",
            description = "업로드 성공 - 업로드 이력 페이지로 리다이렉트",
            content = @Content(mediaType = "text/html")
        ),
        @ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 파일 형식 오류, 필수 파라미터 누락, 중복 파일 등",
            content = @Content(mediaType = "text/html")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "서버 오류 - 파일 저장 실패, 데이터베이스 오류 등",
            content = @Content(mediaType = "text/html")
        )
    })
    @PostMapping("/upload")
    public String uploadFile(
            @Parameter(
                name = "file",
                description = "업로드할 파일 (.ldif 또는 .ml)",
                required = true,
                content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
            @RequestParam("file") MultipartFile file,

            @Parameter(
                name = "forceUpload",
                description = "중복 파일 강제 업로드 여부 (기본값: false)",
                required = false,
                example = "false"
            )
            @RequestParam(value = "forceUpload", defaultValue = "false") boolean forceUpload,

            @Parameter(
                name = "expectedChecksum",
                description = "예상 체크섬 (SHA-1, 선택사항) - 파일 무결성 검증용",
                required = false,
                example = "a1b2c3d4e5f6789012345678901234567890abcd"
            )
            @RequestParam(value = "expectedChecksum", required = false) String expectedChecksum,

            @Parameter(
                name = "fileHash",
                description = "**[필수]** 파일 SHA-256 해시값 - 중복 검사 및 무결성 확인용",
                required = true,
                example = "96fc6106156491ab0b7ddd620b7f902429f61ae617e7e34e9bf0958f07ab10d4"
            )
            @RequestParam("fileHash") String fileHash,

            @Parameter(
                name = "processingMode",
                description = """
                    처리 모드 (기본값: AUTO)
                    - AUTO: 파일 업로드 후 자동으로 파싱 → 검증 → LDAP 업로드 진행
                    - MANUAL: 각 단계를 사용자가 수동으로 트리거
                    """,
                required = false,
                example = "AUTO",
                schema = @Schema(allowableValues = {"AUTO", "MANUAL"})
            )
            @RequestParam(value = "processingMode", defaultValue = "AUTO") String processingModeStr,

            RedirectAttributes redirectAttributes
    ) {
        log.info("=== Unified file upload request ===");
        log.info("File: {}, Size: {}, ProcessingMode: {}, ForceUpload: {}",
                file.getOriginalFilename(), file.getSize(), processingModeStr, forceUpload);

        try {
            // Validate file
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("파일이 선택되지 않았습니다");
            }

            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isBlank()) {
                throw new IllegalArgumentException("파일명이 없습니다");
            }

            // Parse processing mode
            ProcessingMode processingMode;
            try {
                processingMode = ProcessingMode.valueOf(processingModeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid processing mode: {}, using AUTO", processingModeStr);
                processingMode = ProcessingMode.AUTO;
            }

            // Read file content
            byte[] fileContent = file.getBytes();

            // Determine file type and call appropriate Use Case
            UploadFileResponse response;
            if (fileName.toLowerCase().endsWith(".ldif")) {
                log.info("Processing LDIF file: {}", fileName);
                response = handleLdifUpload(fileName, fileContent, file.getSize(), fileHash,
                        expectedChecksum, forceUpload, processingMode);
            } else if (fileName.toLowerCase().endsWith(".ml")) {
                log.info("Processing Master List file: {}", fileName);
                response = handleMasterListUpload(fileName, fileContent, file.getSize(), fileHash,
                        expectedChecksum, forceUpload, processingMode);
            } else {
                throw new IllegalArgumentException("지원하지 않는 파일 형식: " + fileName +
                        " (LDIF 또는 Master List 파일만 지원)");
            }

            // Redirect based on response
            if (response.success()) {
                log.info("File upload completed successfully: uploadId={}", response.uploadId());
                redirectAttributes.addFlashAttribute("success",
                        "파일 업로드 완료: " + response.fileName());
                redirectAttributes.addAttribute("id", response.uploadId());
                return "redirect:/upload-history";
            } else {
                log.warn("File upload failed: {}", response.errorMessage());
                redirectAttributes.addFlashAttribute("error", response.errorMessage());
                return "redirect:/file/upload";
            }

        } catch (IOException e) {
            log.error("Failed to read file content", e);
            redirectAttributes.addFlashAttribute("error",
                    "파일을 읽을 수 없습니다: " + e.getMessage());
            return "redirect:/file/upload";
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/file/upload";
        } catch (Exception e) {
            log.error("Unexpected error during file upload", e);
            redirectAttributes.addFlashAttribute("error",
                    "파일 업로드 중 오류가 발생했습니다: " + e.getMessage());
            return "redirect:/file/upload";
        }
    }

    /**
     * LDIF 파일 업로드 처리
     */
    private UploadFileResponse handleLdifUpload(
            String fileName,
            byte[] fileContent,
            long fileSize,
            String fileHash,
            String expectedChecksum,
            boolean forceUpload,
            ProcessingMode processingMode
    ) {
        UploadLdifFileCommand command = UploadLdifFileCommand.builder()
                .fileName(fileName)
                .fileContent(fileContent)
                .fileSize(fileSize)
                .fileHash(fileHash)
                .expectedChecksum(expectedChecksum)
                .forceUpload(forceUpload)
                .processingMode(processingMode)
                .build();

        return uploadLdifFileUseCase.execute(command);
    }

    /**
     * Master List 파일 업로드 처리
     */
    private UploadFileResponse handleMasterListUpload(
            String fileName,
            byte[] fileContent,
            long fileSize,
            String fileHash,
            String expectedChecksum,
            boolean forceUpload,
            ProcessingMode processingMode
    ) {
        UploadMasterListFileCommand command = UploadMasterListFileCommand.builder()
                .fileName(fileName)
                .fileContent(fileContent)
                .fileSize(fileSize)
                .fileHash(fileHash)
                .expectedChecksum(expectedChecksum)
                .forceUpload(forceUpload)
                .processingMode(processingMode)
                .build();

        return uploadMasterListFileUseCase.execute(command);
    }

    /**
     * LDIF 파일 중복 검사 API
     */
    @Operation(
        summary = "LDIF 파일 중복 검사",
        description = """
            업로드하려는 LDIF 파일의 중복 여부를 검사합니다.

            **검사 방식**:
            - SHA-256 파일 해시값으로 중복 검사
            - 기존 업로드 이력과 비교

            **응답 유형**:
            - isDuplicate=false: 중복되지 않음, 업로드 가능
            - isDuplicate=true: 중복 파일, 기존 파일 정보 반환
            """,
        tags = {"File Upload"}
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "중복 검사 완료",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = CheckDuplicateResponse.class),
                examples = {
                    @ExampleObject(
                        name = "중복 없음",
                        value = """
                            {
                              "isDuplicate": false,
                              "message": "파일이 중복되지 않았습니다.",
                              "warningType": null,
                              "existingFileId": null,
                              "canForceUpload": false
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "중복 발견",
                        value = """
                            {
                              "isDuplicate": true,
                              "message": "이 파일은 이미 업로드되었습니다.",
                              "warningType": "EXACT_DUPLICATE",
                              "existingFileId": "550e8400-e29b-41d4-a716-446655440000",
                              "existingFileName": "icaopkd-001-complete-009409.ldif",
                              "existingUploadDate": "2025-10-24T10:30:00",
                              "existingVersion": "009409",
                              "existingStatus": "COMPLETED",
                              "canForceUpload": false
                            }
                            """
                    )
                }
            )
        )
    })
    @PostMapping("/ldif/api/check-duplicate")
    @ResponseBody
    public ResponseEntity<CheckDuplicateResponse> checkDuplicateLdif(
            @Parameter(
                description = "중복 검사 요청 정보 (fileName, fileSize, fileHash 필수)",
                required = true,
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CheckDuplicateFileCommand.class),
                    examples = @ExampleObject(
                        value = """
                            {
                              "fileName": "icaopkd-001-complete-009409.ldif",
                              "fileSize": 78643200,
                              "fileHash": "96fc6106156491ab0b7ddd620b7f902429f61ae617e7e34e9bf0958f07ab10d4"
                            }
                            """
                    )
                )
            )
            @RequestBody CheckDuplicateFileCommand command
    ) {
        log.debug("Check duplicate API called (LDIF): fileName={}", command.fileName());

        try {
            CheckDuplicateResponse response = checkDuplicateFileUseCase.execute(command);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error during duplicate check (LDIF)", e);
            return ResponseEntity.ok(CheckDuplicateResponse.noDuplicate());
        }
    }

    /**
     * Master List 파일 중복 검사 API
     */
    @Operation(
        summary = "Master List 파일 중복 검사",
        description = """
            업로드하려는 Master List 파일의 중복 여부를 검사합니다.

            **검사 방식**:
            - SHA-256 파일 해시값으로 중복 검사
            - 기존 업로드 이력과 비교

            **응답 유형**:
            - isDuplicate=false: 중복되지 않음, 업로드 가능
            - isDuplicate=true: 중복 파일, 기존 파일 정보 반환
            """,
        tags = {"File Upload"}
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "중복 검사 완료",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = CheckDuplicateResponse.class),
                examples = {
                    @ExampleObject(
                        name = "중복 없음",
                        value = """
                            {
                              "isDuplicate": false,
                              "message": "파일이 중복되지 않았습니다.",
                              "warningType": null,
                              "existingFileId": null,
                              "canForceUpload": false
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "중복 발견",
                        value = """
                            {
                              "isDuplicate": true,
                              "message": "이 파일은 이미 업로드되었습니다.",
                              "warningType": "EXACT_DUPLICATE",
                              "existingFileId": "550e8400-e29b-41d4-a716-446655440000",
                              "existingFileName": "masterlist-2025.ml",
                              "existingUploadDate": "2025-10-24T10:30:00",
                              "existingVersion": "2025",
                              "existingStatus": "COMPLETED",
                              "canForceUpload": false
                            }
                            """
                    )
                }
            )
        )
    })
    @PostMapping("/masterlist/api/check-duplicate")
    @ResponseBody
    public ResponseEntity<CheckDuplicateResponse> checkDuplicateMasterList(
            @Parameter(
                description = "중복 검사 요청 정보 (fileName, fileSize, fileHash 필수)",
                required = true,
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CheckDuplicateFileCommand.class),
                    examples = @ExampleObject(
                        value = """
                            {
                              "fileName": "masterlist-2025.ml",
                              "fileSize": 5242880,
                              "fileHash": "a1b2c3d4e5f6789012345678901234567890abcdef123456789012345678901"
                            }
                            """
                    )
                )
            )
            @RequestBody CheckDuplicateFileCommand command
    ) {
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
