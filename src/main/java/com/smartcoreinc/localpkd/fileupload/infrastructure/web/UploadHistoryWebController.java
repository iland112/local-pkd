package com.smartcoreinc.localpkd.fileupload.infrastructure.web;

import com.smartcoreinc.localpkd.fileupload.application.query.GetUploadHistoryQuery;
import com.smartcoreinc.localpkd.fileupload.application.response.UploadHistoryResponse;
import com.smartcoreinc.localpkd.fileupload.application.usecase.GetUploadHistoryUseCase;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 업로드 이력 조회 웹 컨트롤러 (DDD)
 *
 * <p>업로드된 파일 이력을 조회하는 웹 컨트롤러입니다.</p>
 *
 * @author SmartCore Inc.
 * @version 2.1 (DDD Refactoring)
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
            model.addAttribute(
                "uploadStatusDisplayNames",
                Arrays.stream(UploadStatus.values())
                      .collect(Collectors.toMap(Enum::name, UploadStatus::getDisplayName))
            );

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
}