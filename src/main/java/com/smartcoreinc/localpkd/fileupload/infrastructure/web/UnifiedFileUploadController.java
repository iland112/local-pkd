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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    @PostMapping("/upload")
    public String uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "forceUpload", defaultValue = "false") boolean forceUpload,
            @RequestParam(value = "expectedChecksum", required = false) String expectedChecksum,
            @RequestParam("fileHash") String fileHash,
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
    @PostMapping("/ldif/api/check-duplicate")
    @ResponseBody
    public ResponseEntity<CheckDuplicateResponse> checkDuplicateLdif(
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
    @PostMapping("/masterlist/api/check-duplicate")
    @ResponseBody
    public ResponseEntity<CheckDuplicateResponse> checkDuplicateMasterList(
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
