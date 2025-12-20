package com.smartcoreinc.localpkd.fileparsing.application.service;

import com.smartcoreinc.localpkd.fileparsing.infrastructure.repository.ParsedCertificateQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateExistenceService {

    private final ParsedCertificateQueryRepository parsedCertificateQueryRepository;

    /**
     * 단일 fingerprint 중복 체크 (기존 메서드)
     *
     * @deprecated Use {@link #findExistingFingerprints(Set)} for batch processing to avoid N+1 queries
     */
    @Deprecated
    @Transactional(readOnly = true)
    public boolean existsByFingerprintSha256(String fingerprintSha256) {
        return parsedCertificateQueryRepository.existsByFingerprintSha256(fingerprintSha256);
    }

    /**
     * 배치 fingerprint 중복 체크
     * Performance optimization: N+1 query 문제 해결
     *
     * @param fingerprints 체크할 fingerprint Set
     * @return 이미 존재하는 fingerprint Set
     */
    @Transactional(readOnly = true)
    public Set<String> findExistingFingerprints(Set<String> fingerprints) {
        if (fingerprints == null || fingerprints.isEmpty()) {
            log.debug("No fingerprints to check, returning empty set");
            return Collections.emptySet();
        }

        log.info("Checking {} fingerprints for duplicates (batch query)", fingerprints.size());
        List<String> existingList = parsedCertificateQueryRepository.findFingerprintsByFingerprintSha256In(fingerprints);

        Set<String> existingSet = new HashSet<>(existingList);
        log.info("Found {} existing fingerprints out of {} total", existingSet.size(), fingerprints.size());

        return existingSet;
    }
}
