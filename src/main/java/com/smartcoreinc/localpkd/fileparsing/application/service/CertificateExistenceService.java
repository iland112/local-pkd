package com.smartcoreinc.localpkd.fileparsing.application.service;

import com.smartcoreinc.localpkd.fileparsing.infrastructure.repository.ParsedCertificateQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CertificateExistenceService {

    private final ParsedCertificateQueryRepository parsedCertificateQueryRepository;

    @Transactional(readOnly = true)
    public boolean existsByFingerprintSha256(String fingerprintSha256) {
        return parsedCertificateQueryRepository.existsByFingerprintSha256(fingerprintSha256);
    }
}
