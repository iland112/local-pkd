package com.smartcoreinc.localpkd.controller;

import com.smartcoreinc.localpkd.common.dto.DuplicateCheckRequest;
import com.smartcoreinc.localpkd.common.dto.DuplicateCheckResponse;
import com.smartcoreinc.localpkd.common.entity.FileUploadHistory;
import com.smartcoreinc.localpkd.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * 파일 중복 검사 Controller
 *
 * @author Development Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/duplicate-check")
@RequiredArgsConstructor
public class DuplicateCheckController {

    private final FileUploadService fileUploadService;

    /**
     * 파일 중복 검사
     *
     * @param request 중복 검사 요청
     * @return 중복 검사 결과
     */
    @PostMapping
    public ResponseEntity<DuplicateCheckResponse> checkDuplicate(@RequestBody DuplicateCheckRequest request) {
        log.info("Duplicate check request: filename={}, fileSize={}, fileHash={}",
                request.getFilename(), request.getFileSize(), request.getFileHash());

        try {
            // 파일 해시로 중복 검사
            if (request.getFileHash() != null && !request.getFileHash().isEmpty()) {
                Optional<FileUploadHistory> existing = fileUploadService.findByFileHash(request.getFileHash());

                if (existing.isPresent()) {
                    FileUploadHistory existingFile = existing.get();

                    // 정확히 동일한 파일
                    DuplicateCheckResponse response = DuplicateCheckResponse.exactDuplicate(
                            existingFile.getId(),
                            existingFile.getFilename(),
                            existingFile.getUploadedAt(),
                            existingFile.getVersion(),
                            existingFile.getStatus().getDisplayName()
                    );

                    log.info("Duplicate detected: {}", response);
                    return ResponseEntity.ok(response);
                }
            }

            // 중복 없음
            return ResponseEntity.ok(DuplicateCheckResponse.noDuplicate());

        } catch (Exception e) {
            log.error("Error checking duplicate", e);
            return ResponseEntity.internalServerError()
                    .body(DuplicateCheckResponse.builder()
                            .isDuplicate(false)
                            .message("중복 검사 중 오류가 발생했습니다: " + e.getMessage())
                            .build());
        }
    }

    /**
     * 파일명으로 중복 검사 (간단한 버전)
     *
     * @param filename 파일명
     * @return 중복 검사 결과
     */
    @GetMapping("/by-filename")
    public ResponseEntity<DuplicateCheckResponse> checkDuplicateByFilename(
            @RequestParam String filename) {
        log.info("Duplicate check by filename: {}", filename);

        try {
            // 파일명으로 검사 (추가 구현 필요)
            // TODO: 파일명 패턴 매칭 및 버전 비교 로직 추가

            return ResponseEntity.ok(DuplicateCheckResponse.noDuplicate());

        } catch (Exception e) {
            log.error("Error checking duplicate by filename", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
