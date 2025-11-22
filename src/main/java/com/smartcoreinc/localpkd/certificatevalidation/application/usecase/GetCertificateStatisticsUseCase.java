package com.smartcoreinc.localpkd.certificatevalidation.application.usecase;

import com.smartcoreinc.localpkd.certificatevalidation.application.response.CertificateStatisticsResponse;
import com.smartcoreinc.localpkd.fileparsing.infrastructure.repository.SpringDataParsedFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetCertificateStatisticsUseCase {

    private final SpringDataParsedFileRepository springDataParsedFileRepository;

    public CertificateStatisticsResponse execute() {
        long totalCertificates = springDataParsedFileRepository.countAllCertificates();
        long validCertificates = springDataParsedFileRepository.countCertificatesByValidationStatus(true);
        long invalidCertificates = springDataParsedFileRepository.countCertificatesByValidationStatus(false);
        long totalCrls = springDataParsedFileRepository.countAllCrls();

        // For simplicity, last updated can be now or from a specific entity if available
        LocalDateTime lastUpdated = LocalDateTime.now();

        return CertificateStatisticsResponse.builder()
                .totalCertificates(totalCertificates)
                .validCertificates(validCertificates)
                .invalidCertificates(invalidCertificates)
                .totalCrls(totalCrls)
                .lastUpdated(lastUpdated)
                .build();
    }
}
