package com.smartcoreinc.localpkd.certificatevalidation.domain.service;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.TrustPath;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * CertificatePathBuilderImpl - 인증서 신뢰 경로 자동 구축 Domain Service 구현체
 *
 * <p>주어진 인증서로부터 CSCA (Root of Trust)까지의 경로를 재귀적으로 구축합니다.
 * Issuer DN을 따라 부모 인증서를 검색하여 Trust Path를 생성합니다.</p>
 *
 * <h3>알고리즘</h3>
 * <pre>
 * 1. 시작 인증서 로드
 * 2. Self-Signed 확인 → CSCA (종료)
 * 3. Issuer DN 추출
 * 4. Repository에서 부모 인증서 검색 (Subject DN == Issuer DN)
 * 5. 부모 발견 → 경로에 추가 → 2단계 반복
 * 6. 최대 깊이 도달 또는 순환 참조 → 실패
 * </pre>
 *
 * <h3>보호 메커니즘</h3>
 * <ul>
 *   <li>최대 깊이 제한: 5 (무한 루프 방지)</li>
 *   <li>순환 참조 감지: Set으로 방문 인증서 추적</li>
 *   <li>Missing Issuer 처리: Optional.empty() 반환</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificatePathBuilderImpl implements CertificatePathBuilder {

    private final CertificateRepository certificateRepository;

    @Override
    public Optional<TrustPath> buildPath(CertificateId certificateId) {
        if (certificateId == null) {
            throw new IllegalArgumentException("Certificate ID must not be null");
        }

        log.info("=== Building Trust Path ===");
        log.info("Starting certificate ID: {}", certificateId.getId());

        // Load starting certificate
        Optional<Certificate> certOpt = certificateRepository.findById(certificateId);
        if (certOpt.isEmpty()) {
            log.error("Certificate not found: {}", certificateId.getId());
            return Optional.empty();
        }

        return buildPath(certOpt.get());
    }

    @Override
    public Optional<TrustPath> buildPath(Certificate certificate) {
        if (certificate == null) {
            throw new IllegalArgumentException("Certificate must not be null");
        }

        log.debug("Building path from: {}", certificate.getSubjectInfo().getCommonName());

        try {
            // Build path recursively
            List<UUID> path = new ArrayList<>();
            Set<UUID> visited = new HashSet<>();

            boolean success = buildPathRecursive(certificate, path, visited, 0);

            if (!success) {
                log.error("Failed to build trust path: CSCA not found");
                return Optional.empty();
            }

            // Reverse path to have CSCA first (Root → Leaf)
            Collections.reverse(path);

            TrustPath trustPath = TrustPath.of(path);
            log.info("Trust path built successfully: depth={}, path={}",
                    trustPath.getDepth(), trustPath.toShortString());

            return Optional.of(trustPath);

        } catch (Exception e) {
            log.error("Error building trust path", e);
            return Optional.empty();
        }
    }

    @Override
    public boolean isSelfSigned(Certificate certificate) {
        if (certificate == null) {
            return false;
        }

        // Check if Subject DN equals Issuer DN
        String subjectDn = certificate.getSubjectInfo().getDistinguishedName();
        String issuerDn = certificate.getIssuerInfo().getDistinguishedName();

        boolean selfSigned = subjectDn.equals(issuerDn);

        if (selfSigned) {
            log.debug("Certificate is self-signed: {}", certificate.getSubjectInfo().getCommonName());
        }

        return selfSigned;
    }

    @Override
    public Optional<Certificate> findIssuerCertificate(String issuerDn) {
        if (issuerDn == null || issuerDn.trim().isEmpty()) {
            throw new IllegalArgumentException("Issuer DN must not be null or blank");
        }

        log.debug("Finding issuer certificate with Subject DN: {}", issuerDn);

        // Search by Subject DN (Subject of parent == Issuer of child)
        Optional<Certificate> issuerCert = certificateRepository.findBySubjectDn(issuerDn);

        if (issuerCert.isPresent()) {
            log.debug("Issuer certificate found: {}",
                    issuerCert.get().getSubjectInfo().getCommonName());
        } else {
            log.warn("Issuer certificate not found for DN: {}", issuerDn);
        }

        return issuerCert;
    }

    // ==================== Private Helper Methods ====================

    /**
     * 재귀적으로 Trust Path 구축
     *
     * @param current 현재 인증서
     * @param path 구축 중인 경로 (Leaf → Root 순서)
     * @param visited 방문한 인증서 ID Set (순환 참조 방지)
     * @param depth 현재 깊이
     * @return 성공 시 true (CSCA 도달), 실패 시 false
     */
    private boolean buildPathRecursive(
            Certificate current,
            List<UUID> path,
            Set<UUID> visited,
            int depth
    ) {
        // 1. Maximum depth check
        if (depth >= TrustPath.MAX_DEPTH) {
            log.error("Maximum trust path depth exceeded: {}", depth);
            return false;
        }

        // 2. Circular reference check
        UUID currentUuid = current.getId().getId();
        if (visited.contains(currentUuid)) {
            log.error("Circular reference detected: certificate already visited: {}",
                    current.getSubjectInfo().getCommonName());
            return false;
        }

        // 3. Add current certificate to path
        path.add(currentUuid);
        visited.add(currentUuid);

        log.debug("Path construction: depth={}, certificate={}",
                depth, current.getSubjectInfo().getCommonName());

        // 4. Check if current is CSCA (Self-Signed)
        if (isSelfSigned(current)) {
            log.debug("CSCA (Root of Trust) reached: {}",
                    current.getSubjectInfo().getCommonName());
            return true;  // Success - reached root
        }

        // 5. Find parent (issuer) certificate
        String issuerDn = current.getIssuerInfo().getDistinguishedName();
        Optional<Certificate> parentOpt = findIssuerCertificate(issuerDn);

        if (parentOpt.isEmpty()) {
            log.error("Issuer certificate not found: issuerDN={}", issuerDn);
            return false;  // Failed - missing link in chain
        }

        // 6. Recursive call with parent certificate
        Certificate parent = parentOpt.get();
        return buildPathRecursive(parent, path, visited, depth + 1);
    }

    /**
     * Distinguished Name 정규화
     *
     * <p>DN 문자열을 정규화하여 비교 가능하도록 만듭니다.
     * 공백 제거, 대소문자 통일 등</p>
     *
     * @param dn Distinguished Name
     * @return 정규화된 DN
     */
    @SuppressWarnings("unused")  // Utility method for future DN comparison
    private String normalizeDn(String dn) {
        if (dn == null) {
            return "";
        }

        // Simple normalization: remove extra spaces, trim
        return dn.trim()
                .replaceAll("\\s*,\\s*", ",")  // Remove spaces around commas
                .replaceAll("\\s*=\\s*", "=")  // Remove spaces around equals
                .toLowerCase();                // Lowercase for case-insensitive comparison
    }
}
