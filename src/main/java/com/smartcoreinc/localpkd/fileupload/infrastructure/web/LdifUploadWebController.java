package com.smartcoreinc.localpkd.fileupload.infrastructure.web;

import com.smartcoreinc.localpkd.fileupload.application.command.CheckDuplicateFileCommand;
import com.smartcoreinc.localpkd.fileupload.application.command.UploadLdifFileCommand;
import com.smartcoreinc.localpkd.fileupload.application.response.CheckDuplicateResponse;
import com.smartcoreinc.localpkd.fileupload.application.response.UploadFileResponse;
import com.smartcoreinc.localpkd.fileupload.application.usecase.CheckDuplicateFileUseCase;
import com.smartcoreinc.localpkd.fileupload.application.usecase.UploadLdifFileUseCase;
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
 * LDIF 파일 업로드 웹 컨트롤러 (DDD)
 *
 * <p>LDIF 파일 업로드 UI 및 API를 제공하는 웹 컨트롤러입니다.</p>
 *
 * @author SmartCore Inc.
 * @version 2.0 (DDD Refactoring)
 * @since 2025-10-19
 */
@Slf4j
@Controller
@RequestMapping("/ldif")
@RequiredArgsConstructor
public class LdifUploadWebController {

    private final UploadLdifFileUseCase uploadLdifFileUseCase;
    private final CheckDuplicateFileUseCase checkDuplicateFileUseCase;

    /**
     * LDIF 업로드 페이지 표시
     */
    @GetMapping("/upload")
    public String showUploadPage(Model model) {
        log.info("LDIF upload page accessed");
        return "ldif/upload-ldif";
    }

    /**
     * LDIF 파일 업로드 처리
     *
     * <p>processingMode 파라미터로 AUTO 또는 MANUAL 모드를 선택할 수 있습니다.
     * - AUTO: 파일 업로드 후 자동으로 파싱, 검증, LDAP 등록 진행
     * - MANUAL: 각 단계를 사용자가 수동으로 트리거</p>
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
        log.info("=== LDIF file upload request ===");
        log.info("File: {}, Size: {}, ProcessingMode: {}, ForceUpload: {}",
                 file.getOriginalFilename(), file.getSize(), processingModeStr, forceUpload);

        try {
            // 처리 모드 파싱
            ProcessingMode processingMode;
            try {
                processingMode = ProcessingMode.valueOf(processingModeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid processing mode: {}, using AUTO", processingModeStr);
                processingMode = ProcessingMode.AUTO;
            }

            // 파일 내용 읽기
            byte[] fileContent = file.getBytes();

            // Command 생성
            UploadLdifFileCommand command = UploadLdifFileCommand.builder()
                    .fileName(file.getOriginalFilename())
                    .fileContent(fileContent)
                    .fileSize(file.getSize())
                    .fileHash(fileHash)
                    .expectedChecksum(expectedChecksum)
                    .forceUpload(forceUpload)
                    .processingMode(processingMode)
                    .build();

            // Use Case 실행
            UploadFileResponse response = uploadLdifFileUseCase.execute(command);

            if (response.success()) {
                redirectAttributes.addFlashAttribute("success", 
                    "파일 업로드 완료: " + response.fileName());
                redirectAttributes.addAttribute("id", response.uploadId());
                return "redirect:/upload-history";
            } else {
                redirectAttributes.addFlashAttribute("error", response.errorMessage());
                return "redirect:/ldif/upload";
            }

        } catch (IOException e) {
            log.error("Failed to read file content", e);
            redirectAttributes.addFlashAttribute("error", 
                "파일을 읽을 수 없습니다: " + e.getMessage());
            return "redirect:/ldif/upload";
        } catch (Exception e) {
            log.error("Unexpected error during LDIF upload", e);
            redirectAttributes.addFlashAttribute("error", 
                "파일 업로드 중 오류가 발생했습니다: " + e.getMessage());
            return "redirect:/ldif/upload";
        }
    }

    /**
     * 중복 파일 검사 API
     */
    @PostMapping("/api/check-duplicate")
    @ResponseBody
    public ResponseEntity<CheckDuplicateResponse> checkDuplicate(
            @RequestBody CheckDuplicateFileCommand command
    ) {
        log.debug("Check duplicate API called: fileName={}", command.fileName());

        try {
            CheckDuplicateResponse response = checkDuplicateFileUseCase.execute(command);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error during duplicate check", e);
            return ResponseEntity.ok(CheckDuplicateResponse.noDuplicate());
        }
    }
}
