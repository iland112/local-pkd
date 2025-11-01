package com.smartcoreinc.localpkd.fileupload.infrastructure.web;

import com.smartcoreinc.localpkd.fileupload.application.query.GetUploadHistoryQuery;
import com.smartcoreinc.localpkd.fileupload.application.response.UploadHistoryResponse;
import com.smartcoreinc.localpkd.fileupload.application.usecase.GetUploadHistoryUseCase;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 업로드 이력 조회 웹 컨트롤러 (DDD)
 *
 * <p>업로드된 파일 이력을 조회하는 웹 컨트롤러입니다.</p>
 *
 * @author SmartCore Inc.
 * @version 2.0 (DDD Refactoring)
 * @since 2025-10-19
 */
@Slf4j
@Controller
@RequestMapping("/upload-history")
@RequiredArgsConstructor
public class UploadHistoryWebController {

    private final GetUploadHistoryUseCase getUploadHistoryUseCase;

    /**
     * 업로드 이력 조회 페이지
     */
    @GetMapping
    public String getUploadHistory(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String format,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String success,
            @RequestParam(required = false) String error,
            Model model
    ) {
        log.info("Upload history page accessed: page={}, size={}, search={}, status={}, format={}", 
                 page, size, search, status, format);

        try {
            // Query 생성
            GetUploadHistoryQuery query = GetUploadHistoryQuery.builder()
                    .searchKeyword(search)
                    .status(status)
                    .fileFormat(format)
                    .page(page)
                    .size(size)
                    .build();

            // Use Case 실행
            Page<UploadHistoryResponse> historyPage = getUploadHistoryUseCase.execute(query);

            // Model에 데이터 추가
            model.addAttribute("historyPage", historyPage);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", historyPage.getTotalPages());
            model.addAttribute("totalElements", historyPage.getTotalElements());
            model.addAttribute("size", size);
            model.addAttribute("search", search);
            model.addAttribute("status", status);
            model.addAttribute("format", format);
            model.addAttribute("highlightId", id);

            // Filter Options
            model.addAttribute("fileFormatTypes", FileFormat.Type.values());
            model.addAttribute("uploadStatuses", UploadStatus.values());

            // Flash 메시지
            if (success != null) {
                model.addAttribute("successMessage", success);
            }
            if (error != null) {
                model.addAttribute("errorMessage", error);
            }

            return "upload-history/list";

        } catch (Exception e) {
            log.error("Error during get upload history", e);
            model.addAttribute("errorMessage", "이력 조회 중 오류가 발생했습니다: " + e.getMessage());
            return "upload-history/list";
        }
    }

    /**
     * 업로드 통계 조회 API
     * Dashboard에서 사용
     */
    @GetMapping("/statistics")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStatistics() {
        log.debug("Statistics API called");

        try {
            // 전체 이력 조회 (페이징 없이)
            GetUploadHistoryQuery query = GetUploadHistoryQuery.builder()
                    .page(0)
                    .size(Integer.MAX_VALUE)
                    .build();

            Page<UploadHistoryResponse> allHistory = getUploadHistoryUseCase.execute(query);

            // 통계 계산
            long totalCount = allHistory.getTotalElements();
            long successCount = allHistory.getContent().stream()
                    .filter(h -> "COMPLETED".equals(h.status()))
                    .count();
            long failedCount = allHistory.getContent().stream()
                    .filter(h -> "FAILED".equals(h.status()))
                    .count();

            double successRate = totalCount > 0 ? (successCount * 100.0 / totalCount) : 0.0;

            Map<String, Object> statistics = new HashMap<>();
            statistics.put("totalCount", totalCount);
            statistics.put("successCount", successCount);
            statistics.put("failedCount", failedCount);
            statistics.put("successRate", successRate);

            return ResponseEntity.ok(statistics);

        } catch (Exception e) {
            log.error("Error during get statistics", e);
            Map<String, Object> errorStats = new HashMap<>();
            errorStats.put("totalCount", 0);
            errorStats.put("successCount", 0);
            errorStats.put("failedCount", 0);
            errorStats.put("successRate", 0.0);
            return ResponseEntity.ok(errorStats);
        }
    }

    /**
     * 최근 업로드 이력 조회 API
     * Dashboard에서 사용
     */
    @GetMapping("/recent")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getRecentActivity(
            @RequestParam(defaultValue = "5") int limit
    ) {
        log.debug("Recent activity API called: limit={}", limit);

        try {
            // 최근 이력 조회
            GetUploadHistoryQuery query = GetUploadHistoryQuery.builder()
                    .page(0)
                    .size(limit)
                    .build();

            Page<UploadHistoryResponse> recentHistory = getUploadHistoryUseCase.execute(query);

            // Response 변환 (Dashboard가 기대하는 형식으로)
            List<Map<String, Object>> recentActivity = recentHistory.getContent().stream()
                    .map(h -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("id", h.uploadId());
                        item.put("filename", h.fileName());
                        item.put("uploadedAt", h.uploadedAt());
                        item.put("status", h.status());
                        item.put("statusDisplay", getStatusDisplayName(h.status()));
                        return item;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(recentActivity);

        } catch (Exception e) {
            log.error("Error during get recent activity", e);
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * 상태 표시명 변환
     */
    private String getStatusDisplayName(String status) {
        if (status == null) return "알 수 없음";

        return switch (status) {
            case "RECEIVED" -> "수신됨";
            case "VALIDATING" -> "검증 중";
            case "VALIDATED" -> "검증 완료";
            case "PARSING" -> "파싱 중";
            case "PARSED" -> "파싱 완료";
            case "UPLOADING_TO_LDAP" -> "LDAP 업로드 중";
            case "COMPLETED" -> "완료";
            case "FAILED" -> "실패";
            default -> status;
        };
    }
}
