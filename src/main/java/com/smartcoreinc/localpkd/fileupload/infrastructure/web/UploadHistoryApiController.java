package com.smartcoreinc.localpkd.fileupload.infrastructure.web;

import com.smartcoreinc.localpkd.fileupload.application.query.GetUploadHistoryQuery;
import com.smartcoreinc.localpkd.fileupload.application.response.UploadHistoryResponse;
import com.smartcoreinc.localpkd.fileupload.application.response.UploadStatisticsResponse;
import com.smartcoreinc.localpkd.fileupload.application.usecase.GetUploadHistoryUseCase;
import com.smartcoreinc.localpkd.fileupload.application.usecase.GetUploadStatisticsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/upload-history")
@RequiredArgsConstructor
public class UploadHistoryApiController {

    private final GetUploadHistoryUseCase getUploadHistoryUseCase;
    private final GetUploadStatisticsUseCase getUploadStatisticsUseCase;

    @GetMapping("/statistics")
    public UploadStatisticsResponse getUploadStatistics() {
        log.info("Request for upload statistics received.");
        return getUploadStatisticsUseCase.execute();
    }

    @GetMapping("/recent")
    public List<UploadHistoryResponse> getRecentUploads(
            @RequestParam(defaultValue = "5") int limit) {
        log.info("Request for recent uploads received with limit: {}", limit);
        GetUploadHistoryQuery query = GetUploadHistoryQuery.builder()
                .page(0)
                .size(limit)
                .sortBy("uploadedAt")
                .sortDirection("desc")
                .build();
        Page<UploadHistoryResponse> page = getUploadHistoryUseCase.execute(query);
        return page.getContent();
    }
}
