package com.smartcoreinc.localpkd.fileupload.infrastructure.web;

import com.smartcoreinc.localpkd.fileupload.application.command.CheckDuplicateFileCommand;
import com.smartcoreinc.localpkd.fileupload.application.command.UploadMasterListFileCommand;
import com.smartcoreinc.localpkd.fileupload.application.response.CheckDuplicateResponse;
import com.smartcoreinc.localpkd.fileupload.application.response.UploadFileResponse;
import com.smartcoreinc.localpkd.fileupload.application.usecase.CheckDuplicateFileUseCase;
import com.smartcoreinc.localpkd.fileupload.application.usecase.UploadMasterListFileUseCase;
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
 * Master List 파일 업로드 웹 컨트롤러 (DDD)
 *
 * <p>Master List 파일 업로드 UI 및 API를 제공하는 웹 컨트롤러입니다.</p>
 *
 * @author SmartCore Inc.
 * @version 2.0 (DDD Refactoring)
 * @since 2025-10-19
 */
@Slf4j
@Controller
@RequestMapping("/masterlist")
@RequiredArgsConstructor
public class MasterListUploadWebController {

    private final UploadMasterListFileUseCase uploadMasterListFileUseCase;
    private final CheckDuplicateFileUseCase checkDuplicateFileUseCase;

    /**
     * Master List 업로드 페이지 표시
     */
    @GetMapping("/upload")
    public String showUploadPage(Model model) {
        log.info("Master List upload page accessed");
        return "masterlist/upload-ml";
    }

    /**
     * Master List 파일 업로드 처리
     */
    @PostMapping("/upload")
    public String uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "forceUpload", defaultValue = "false") boolean forceUpload,
            @RequestParam(value = "expectedChecksum", required = false) String expectedChecksum,
            @RequestParam("fileHash") String fileHash,
            RedirectAttributes redirectAttributes
    ) {
        log.info("=== Master List file upload request ===");
        log.info("File: {}, Size: {}, ForceUpload: {}", 
                 file.getOriginalFilename(), file.getSize(), forceUpload);

        try {
            // 파일 내용 읽기
            byte[] fileContent = file.getBytes();

            // Command 생성
            UploadMasterListFileCommand command = UploadMasterListFileCommand.builder()
                    .fileName(file.getOriginalFilename())
                    .fileContent(fileContent)
                    .fileSize(file.getSize())
                    .fileHash(fileHash)
                    .expectedChecksum(expectedChecksum)
                    .forceUpload(forceUpload)
                    .build();

            // Use Case 실행
            UploadFileResponse response = uploadMasterListFileUseCase.execute(command);

            if (response.success()) {
                redirectAttributes.addFlashAttribute("success", 
                    "파일 업로드 완료: " + response.fileName());
                redirectAttributes.addAttribute("id", response.uploadId());
                return "redirect:/upload-history";
            } else {
                redirectAttributes.addFlashAttribute("error", response.errorMessage());
                return "redirect:/masterlist/upload";
            }

        } catch (IOException e) {
            log.error("Failed to read file content", e);
            redirectAttributes.addFlashAttribute("error", 
                "파일을 읽을 수 없습니다: " + e.getMessage());
            return "redirect:/masterlist/upload";
        } catch (Exception e) {
            log.error("Unexpected error during Master List upload", e);
            redirectAttributes.addFlashAttribute("error", 
                "파일 업로드 중 오류가 발생했습니다: " + e.getMessage());
            return "redirect:/masterlist/upload";
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
