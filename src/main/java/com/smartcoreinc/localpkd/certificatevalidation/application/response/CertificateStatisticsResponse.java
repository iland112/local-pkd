package com.smartcoreinc.localpkd.certificatevalidation.application.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map; // Map import 추가

@Value
@Builder
public class CertificateStatisticsResponse {
    long totalCertificates;
    long validCertificates;
    long invalidCertificates;
    long totalCrls;
    LocalDateTime lastUpdated;
    Map<String, Long> certificatesByType; // 인증서 유형별 개수 (예: CSCA, DSC, NC)
    Map<String, Long> certificatesByCountry; // 국가 코드별 개수 (예: KR, US)
}
