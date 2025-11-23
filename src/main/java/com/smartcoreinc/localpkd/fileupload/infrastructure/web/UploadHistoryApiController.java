package com.smartcoreinc.localpkd.fileupload.infrastructure.web;

import com.smartcoreinc.localpkd.fileupload.application.query.GetUploadHistoryQuery;
import com.smartcoreinc.localpkd.fileupload.application.response.UploadHistoryResponse;
import com.smartcoreinc.localpkd.fileupload.application.response.UploadStatisticsResponse;
import com.smartcoreinc.localpkd.fileupload.application.usecase.GetUploadHistoryUseCase;
import com.smartcoreinc.localpkd.fileupload.application.usecase.GetUploadStatisticsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "업로드 이력 API", description = "파일 업로드 이력 및 통계 관련 API")
@Slf4j
@RestController
@RequestMapping("/upload-history")
@RequiredArgsConstructor
public class UploadHistoryApiController {

    private final GetUploadHistoryUseCase getUploadHistoryUseCase;
    private final GetUploadStatisticsUseCase getUploadStatisticsUseCase;

    @Operation(summary = "업로드 통계 조회",
               description = "전체 업로드 수, 성공/실패 수, 성공률 등 통계 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "통계 조회 성공",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = UploadStatisticsResponse.class)))
    @GetMapping("/statistics")
    public UploadStatisticsResponse getUploadStatistics() {
        log.info("Request for upload statistics received.");
        return getUploadStatisticsUseCase.execute();
    }

    @Operation(summary = "최근 업로드 이력 조회",
               description = "최근 업로드된 파일 이력을 지정된 수만큼 조회합니다.")
    @ApiResponse(responseCode = "200", description = "최근 업로드 이력 조회 성공",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = UploadHistoryResponse.class)))
    @GetMapping("/recent")
    public List<UploadHistoryResponse> getRecentUploads(
            @Parameter(description = "조회할 최대 이력 수", example = "5")
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
