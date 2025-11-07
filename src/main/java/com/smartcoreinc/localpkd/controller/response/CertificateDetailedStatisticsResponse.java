package com.smartcoreinc.localpkd.controller.response;

import java.util.HashMap;
import java.util.Map;

/**
 * CertificateDetailedStatisticsResponse - Dashboard 차트용 상세 통계 응답
 *
 * <p><b>목적</b>: Chart.js를 사용한 Dashboard 시각화를 위해 상세한 통계 데이터를 제공합니다.</p>
 *
 * <p><b>통계 항목</b>:</p>
 * <ul>
 *   <li>certificateTypeDistribution: 인증서 타입별 분포 (CSCA, DSC, DSC_NC)</li>
 *   <li>certificateStatusDistribution: 인증서 상태별 분포 (VALID, INVALID)</li>
 *   <li>crlStatusDistribution: CRL 상태별 분포 (VALID, EXPIRED)</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-07
 */
public record CertificateDetailedStatisticsResponse(
    Map<String, Long> certificateTypeDistribution,
    Map<String, Long> certificateStatusDistribution,
    Map<String, Long> crlStatusDistribution,
    long totalCertificates,
    long totalCrls
) {
    public static CertificateDetailedStatisticsResponse empty() {
        return new CertificateDetailedStatisticsResponse(
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            0,
            0
        );
    }
}
