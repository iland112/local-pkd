package com.smartcoreinc.localpkd.controller;

import com.smartcoreinc.localpkd.common.entity.FileUploadHistory;
import com.smartcoreinc.localpkd.common.enums.FileFormat;
import com.smartcoreinc.localpkd.common.enums.UploadStatus;
import com.smartcoreinc.localpkd.service.FileStorageService;
import com.smartcoreinc.localpkd.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Master List 파일 업로드 컨트롤러
 *
 * ICAO PKD Master List 파일의 업로드를 처리합니다.
 *
 * @author SmartCore Inc.
 * @version 1.0
 */
@Slf4j
@Controller
@RequestMapping("/masterlist")
@RequiredArgsConstructor
public class MasterListUploadController {

    private final FileUploadService fileUploadService;
    private final FileStorageService fileStorageService;

    /**
     * Master List 업로드 페이지 표시
     */
    @GetMapping("/upload")
    public String showUploadPage(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String success,
            Model model
    ) {
        if (error != null) {
            model.addAttribute("error", error);
        }
        if (success != null) {
            model.addAttribute("success", success);
        }
        return "masterlist/upload-ml";
    }

    /**
     * Master List 파일 업로드 처리
     */
    @PostMapping("/upload")
    public String uploadMasterList(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean forceUpload,
            @RequestParam(required = false) String expectedChecksum,
            Model model
    ) {
        log.info("=== Master List file upload started ===");
        log.info("Filename: {}", file.getOriginalFilename());
        log.info("Size: {} bytes", file.getSize());
        log.info("Force upload: {}", forceUpload);

        try {
            // 1. 파일 유효성 검사
            validateFile(file);

            // 2. 파일 해시 계산
            String fileHash = fileStorageService.calculateFileHash(file);
            log.debug("File hash calculated: {}", fileHash);

            // 3. 중복 체크 (forceUpload가 false인 경우에만)
            if (!forceUpload) {
                Optional<FileUploadHistory> existingFile = fileUploadService.findByFileHash(fileHash);

                if (existingFile.isPresent()) {
                    FileUploadHistory existing = existingFile.get();
                    log.warn("Duplicate file detected: hash={}, existingId={}", fileHash, existing.getId());

                    String errorMsg = String.format(
                        "중복된 파일입니다. 기존 업로드: %s (버전: %s, 업로드 일시: %s)",
                        existing.getFilename(),
                        existing.getVersion(),
                        existing.getUploadedAt()
                    );

                    model.addAttribute("error", errorMsg);
                    return "masterlist/upload-ml";
                }
            } else {
                log.info("Force upload enabled - skipping duplicate check");
            }

            // 4. 파일 저장
            FileFormat fileFormat = detectFileFormat(file.getOriginalFilename());
            String savedPath = fileStorageService.saveFile(file, fileFormat);
            log.info("File saved to: {}", savedPath);

            // 5. 메타데이터 추출
            String collectionNumber = extractCollectionNumber(file.getOriginalFilename());
            String version = extractVersion(file.getOriginalFilename());

            log.debug("Metadata extracted - collection: {}, version: {}", collectionNumber, version);

            // 6. 업로드 이력 생성
            FileUploadHistory history = FileUploadHistory.builder()
                    .filename(file.getOriginalFilename())
                    .collectionNumber(collectionNumber)
                    .version(version)
                    .fileFormat(fileFormat)
                    .fileSizeBytes(file.getSize())
                    .fileSizeDisplay(formatFileSize(file.getSize()))
                    .uploadedAt(LocalDateTime.now())
                    .localFilePath(savedPath)
                    .fileHash(fileHash)
                    .expectedChecksum(expectedChecksum)
                    .status(UploadStatus.RECEIVED)
                    .isDuplicate(forceUpload) // 강제 업로드인 경우 중복으로 표시
                    .isNewerVersion(false)
                    .build();

            FileUploadHistory savedHistory = fileUploadService.saveUploadHistory(history);
            log.info("Upload history created: id={}, status={}", savedHistory.getId(), savedHistory.getStatus());

            // 7. 성공 메시지와 함께 이력 페이지로 리다이렉트
            return "redirect:/upload-history?id=" + savedHistory.getId() +
                   "&success=" + java.net.URLEncoder.encode("파일 업로드가 완료되었습니다.", "UTF-8");

        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            return "masterlist/upload-ml";

        } catch (Exception e) {
            log.error("Upload error", e);
            model.addAttribute("error", "파일 업로드 중 오류가 발생했습니다: " + e.getMessage());
            return "masterlist/upload-ml";
        }
    }

    /**
     * 파일 유효성 검사
     *
     * @param file 업로드 파일
     * @throws IllegalArgumentException 유효성 검사 실패 시
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 선택되지 않았습니다.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("파일명이 없습니다.");
        }

        if (!filename.toLowerCase().endsWith(".ml")) {
            throw new IllegalArgumentException("Master List 파일(.ml)만 업로드할 수 있습니다.");
        }

        long maxSize = 100 * 1024 * 1024; // 100MB
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(
                String.format("파일 크기가 너무 큽니다. 최대 크기: %s", formatFileSize(maxSize))
            );
        }

        if (file.getSize() == 0) {
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }

        log.debug("File validation passed: {}", filename);
    }

    /**
     * Collection 번호 추출
     * Master List는 보통 002 collection
     *
     * @param filename 파일명
     * @return Collection 번호
     */
    private String extractCollectionNumber(String filename) {
        // Master List 파일명 패턴: masterlist-{version}.ml 또는 icaopkd-002-...
        Pattern pattern = Pattern.compile("icaopkd-(\\d{3})-", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(filename);

        if (matcher.find()) {
            return matcher.group(1);
        }

        // 기본적으로 Master List는 002 collection
        log.debug("Using default collection 002 for Master List file: {}", filename);
        return "002";
    }

    /**
     * 버전 추출
     * Master List 버전은 보통 날짜 형식 (예: July2025)
     *
     * @param filename 파일명
     * @return 버전
     */
    private String extractVersion(String filename) {
        // 패턴 1: masterlist-{MonthYear}.ml (예: masterlist-July2025.ml)
        Pattern pattern1 = Pattern.compile("masterlist-([A-Za-z]+\\d{4})\\.ml", Pattern.CASE_INSENSITIVE);
        Matcher matcher1 = pattern1.matcher(filename);

        if (matcher1.find()) {
            return matcher1.group(1);
        }

        // 패턴 2: LDIF와 같은 숫자 패턴
        Pattern pattern2 = Pattern.compile("-(\\d+)\\.ml$", Pattern.CASE_INSENSITIVE);
        Matcher matcher2 = pattern2.matcher(filename);

        if (matcher2.find()) {
            return matcher2.group(1);
        }

        // 파일명 자체를 버전으로 사용 (확장자 제거)
        String version = filename.replaceAll("\\.ml$", "");
        log.warn("Using filename as version: {}", version);
        return version;
    }

    /**
     * 파일 포맷 감지
     * Master List는 기본적으로 ML_SIGNED_CMS 포맷
     *
     * @param filename 파일명
     * @return FileFormat
     */
    private FileFormat detectFileFormat(String filename) {
        // Master List 파일은 ML_SIGNED_CMS 포맷
        // 파일명 패턴: ICAO_ml_July2025.ml 또는 masterlist-*.ml
        return FileFormat.ML_SIGNED_CMS;
    }

    /**
     * 파일 크기 포맷팅
     *
     * @param bytes 바이트 크기
     * @return 포맷된 문자열 (예: "43.5 MiB")
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %siB", bytes / Math.pow(1024, exp), pre);
    }
}
