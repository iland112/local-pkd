package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web;

import com.smartcoreinc.localpkd.certificatevalidation.application.response.CertificateStatisticsResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.GetCertificateStatisticsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateValidationApiController {

    private final GetCertificateStatisticsUseCase getCertificateStatisticsUseCase;

    @GetMapping("/statistics")
    public CertificateStatisticsResponse getCertificateStatistics() {
        log.info("Request for certificate statistics received.");
        return getCertificateStatisticsUseCase.execute();
    }
}