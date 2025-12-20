package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web;

import com.smartcoreinc.localpkd.certificatevalidation.application.response.CertificateStatisticsResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.GetCertificateStatisticsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증서 통계 API", description = "저장된 인증서 관련 통계 정보를 제공하는 API")
@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateStatisticsApiController {

    private final GetCertificateStatisticsUseCase getCertificateStatisticsUseCase;

    @Operation(summary = "인증서 통계 조회",
               description = "전체 인증서, 유효/무효 인증서, CRL, 타입별, 국가별 통계 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "통계 조회 성공",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = CertificateStatisticsResponse.class)))
    @ApiResponse(responseCode = "500", description = "서버 내부 오류",
        content = @Content(mediaType = "application/json",
            schema = @Schema(example = "{\"success\": false, \"errorMessage\": \"통계 조회 중 오류가 발생했습니다.\", \"errorCode\": \"INTERNAL_ERROR\"}")))
    @GetMapping("/statistics")
    public ResponseEntity<CertificateStatisticsResponse> getStatistics() {
        CertificateStatisticsResponse statistics = getCertificateStatisticsUseCase.execute();
        return ResponseEntity.ok(statistics);
    }
}
