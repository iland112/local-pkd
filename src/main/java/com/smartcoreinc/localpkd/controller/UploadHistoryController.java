package com.smartcoreinc.localpkd.controller;

import com.smartcoreinc.localpkd.common.dto.FileSearchCriteria;
import com.smartcoreinc.localpkd.common.entity.FileUploadHistory;
import com.smartcoreinc.localpkd.common.enums.FileFormat;
import com.smartcoreinc.localpkd.common.enums.UploadStatus;
import com.smartcoreinc.localpkd.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

/**
 * 파일 업로드 이력 관리 Controller
 *
 * @author Development Team
 * @since 1.0.0
 */
@Slf4j
@Controller
@RequestMapping("/upload-history")
@RequiredArgsConstructor
public class UploadHistoryController {

    private final FileUploadService fileUploadService;

    /**
     * 업로드 이력 페이지
     *
     * @param format 파일 포맷 필터 (optional)
     * @param status 업로드 상태 필터 (optional)
     * @param startDate 시작 날짜 (optional)
     * @param endDate 종료 날짜 (optional)
     * @param fileName 파일명 검색 (optional)
     * @param page 페이지 번호 (default: 0)
     * @param size 페이지 크기 (default: 20)
     * @param sort 정렬 필드 (default: uploadedAt)
     * @param direction 정렬 방향 (default: DESC)
     * @param model 모델
     * @return 업로드 이력 페이지
     */
    @GetMapping
    public String getUploadHistory(
            @RequestParam(required = false) FileFormat format,
            @RequestParam(required = false) UploadStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String fileName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "uploadedAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            Model model) {

        // Flash Attributes에서 highlightId와 successMessage 가져오기
        Long highlightId = (Long) model.asMap().get("highlightId");
        String successMessage = (String) model.asMap().get("successMessage");
        String errorMessage = (String) model.asMap().get("errorMessage");

        log.info("Fetching upload history - page: {}, size: {}, format: {}, status: {}, highlightId: {}",
                page, size, format, status, highlightId);

        try {
            // 검색 조건 생성
            FileSearchCriteria criteria = FileSearchCriteria.builder()
                    .fileFormat(format)
                    .uploadStatus(status)
                    .startDate(startDate != null ? LocalDateTime.of(startDate, LocalTime.MIN) : null)
                    .endDate(endDate != null ? LocalDateTime.of(endDate, LocalTime.MAX) : null)
                    .fileName(fileName)
                    .sortBy(sort)
                    .sortDirection(direction)
                    .build();

            // 페이징 및 정렬 설정
            Sort.Direction sortDirection = "ASC".equalsIgnoreCase(direction)
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

            // 검색 실행
            Page<FileUploadHistory> uploadHistory = fileUploadService.searchUploadHistory(criteria, pageable);

            // 통계 정보 조회
            Map<String, Object> statistics = fileUploadService.getUploadStatistics();

            // 모델에 데이터 추가
            model.addAttribute("uploadHistory", uploadHistory);
            model.addAttribute("statistics", statistics);
            model.addAttribute("criteria", criteria);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", uploadHistory.getTotalPages());
            model.addAttribute("totalElements", uploadHistory.getTotalElements());
            model.addAttribute("size", size);
            model.addAttribute("sort", sort);
            model.addAttribute("direction", direction);

            // 필터 옵션 (Enum 전체 목록)
            model.addAttribute("fileFormatOptions", FileFormat.values());
            model.addAttribute("uploadStatusOptions", UploadStatus.values());

            // Flash Attributes는 이미 Model에 자동으로 추가되어 있음
            // highlightId, successMessage, errorMessage는 이미 사용 가능
            log.debug("Flash attributes - highlightId: {}, successMessage: {}, errorMessage: {}",
                    highlightId, successMessage, errorMessage);

            return "upload-history/list";

        } catch (Exception e) {
            log.error("Error fetching upload history", e);
            model.addAttribute("error", "업로드 이력을 불러오는 중 오류가 발생했습니다: " + e.getMessage());
            return "upload-history/list";
        }
    }

    /**
     * 업로드 이력 상세 조회 (AJAX)
     *
     * @param id 업로드 이력 ID
     * @return 업로드 이력 상세 정보
     */
    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> getUploadHistoryDetail(@PathVariable Long id) {
        log.info("Fetching upload history detail for ID: {}", id);

        Optional<FileUploadHistory> history = fileUploadService.getUploadHistory(id);

        if (history.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(history.get());
    }

    /**
     * 업로드 이력 삭제 (향후 구현)
     *
     * @param id 업로드 이력 ID
     * @return 삭제 결과
     */
    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteUploadHistory(@PathVariable Long id) {
        log.info("Delete request for upload history ID: {}", id);

        // TODO: 삭제 기능 구현
        return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "삭제 기능은 아직 구현되지 않았습니다."
        ));
    }

    /**
     * 업로드 통계 조회 (AJAX)
     *
     * @return 업로드 통계
     */
    @GetMapping("/statistics")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStatistics() {
        log.info("Fetching upload statistics");

        Map<String, Object> statistics = fileUploadService.getUploadStatistics();
        return ResponseEntity.ok(statistics);
    }

    /**
     * 최근 업로드 목록 조회 (AJAX)
     *
     * @param limit 조회 개수 (default: 10)
     * @return 최근 업로드 목록
     */
    @GetMapping("/recent")
    @ResponseBody
    public ResponseEntity<?> getRecentUploads(@RequestParam(defaultValue = "10") int limit) {
        log.info("Fetching recent uploads, limit: {}", limit);

        return ResponseEntity.ok(fileUploadService.getRecentUploads(limit));
    }
}
