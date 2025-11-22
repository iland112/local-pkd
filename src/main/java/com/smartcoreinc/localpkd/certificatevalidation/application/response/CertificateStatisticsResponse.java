package com.smartcoreinc.localpkd.certificatevalidation.application.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class CertificateStatisticsResponse {
    long totalCertificates;
    long validCertificates;
    long invalidCertificates;
    long totalCrls;
    LocalDateTime lastUpdated;
}
