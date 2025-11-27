package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.repository;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.*;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.shared.event.EventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JpaCertificateRepository implements CertificateRepository {

    private final SpringDataCertificateRepository jpaRepository;
    private final EventBus eventBus;

    @Override
    @Transactional
    public Certificate save(Certificate certificate) {
        log.debug("Saving Certificate: id={}, fingerprint={}",
            certificate.getId().getId(),
            certificate.getX509Data().getFingerprintSha256());

        if (!certificate.getDomainEvents().isEmpty()) {
            log.debug("Publishing {} domain event(s) BEFORE save", certificate.getDomainEvents().size());
            eventBus.publishAll(certificate.getDomainEvents());
            certificate.clearDomainEvents();
        }

        Certificate saved = jpaRepository.save(certificate);
        log.debug("Certificate saved successfully: id={}", saved.getId().getId());
        return saved;
    }

    @Override
    @Transactional
    public List<Certificate> saveAll(List<Certificate> certificates) {
        if (certificates == null || certificates.isEmpty()) {
            throw new IllegalArgumentException("certificates cannot be null or empty");
        }

        log.debug("Saving {} certificates in batch", certificates.size());

        // Publish domain events for all certificates
        for (Certificate certificate : certificates) {
            if (!certificate.getDomainEvents().isEmpty()) {
                log.debug("Publishing {} domain event(s) for Certificate id={}",
                        certificate.getDomainEvents().size(),
                        certificate.getId().getId());
                eventBus.publishAll(certificate.getDomainEvents());
                certificate.clearDomainEvents();
            }
        }

        // Batch save using JPA
        List<Certificate> savedCertificates = jpaRepository.saveAll(certificates);
        log.debug("Successfully saved {} certificates in batch", savedCertificates.size());

        return savedCertificates;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Certificate> findById(CertificateId id) {
        log.debug("Finding Certificate by id: {}", id.getId());
        return jpaRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Certificate> findByFingerprint(String fingerprintSha256) {
        log.debug("Finding Certificate by fingerprint: {}...", fingerprintSha256.substring(0, 8) + "...");
        return jpaRepository.findByX509Data_FingerprintSha256(fingerprintSha256);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Certificate> findBySerialNumber(String serialNumber) {
        log.debug("Finding Certificate by serialNumber: {}", serialNumber);
        return jpaRepository.findByX509Data_SerialNumber(serialNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Certificate> findBySubjectDn(String subjectDn) {
        if (subjectDn == null || subjectDn.isBlank()) {
            log.warn("Cannot find Certificate by Subject DN: DN is null or blank");
            throw new IllegalArgumentException("subjectDn must not be null or blank");
        }
        log.debug("Finding Certificate by Subject DN: {}", subjectDn);
        return jpaRepository.findBySubjectInfo_DistinguishedName(subjectDn);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Certificate> findByIssuerDn(String issuerDn) {
        if (issuerDn == null || issuerDn.isBlank()) {
            log.warn("Cannot find Certificate by Issuer DN: DN is null or blank");
            throw new IllegalArgumentException("issuerDn must not be null or blank");
        }
        log.debug("Finding Certificate by Issuer DN: {}", issuerDn);
        return jpaRepository.findByIssuerInfo_DistinguishedName(issuerDn);
    }

    @Override
    @Transactional
    public void deleteById(CertificateId id) {
        log.debug("Deleting Certificate by id: {}", id.getId());
        jpaRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(CertificateId id) {
        log.debug("Checking existence of Certificate by id: {}", id.getId());
        return jpaRepository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByFingerprint(String fingerprintSha256) {
        log.debug("Checking existence of Certificate by fingerprint: {}...",
            fingerprintSha256.substring(0, 8));
        return jpaRepository.existsByX509Data_FingerprintSha256(fingerprintSha256);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findByUploadId(UUID uploadId) {
        if (uploadId == null) {
            log.warn("Cannot find Certificates by uploadId: uploadId is null");
            throw new IllegalArgumentException("uploadId must not be null");
        }
        log.debug("Finding Certificates by uploadId: {}", uploadId);
        return jpaRepository.findByUploadId(uploadId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findByStatus(String status) {
        log.debug("Finding Certificates by status: {}", status);
        return jpaRepository.findByStatus(CertificateStatus.valueOf(status));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findByCountryCode(String countryCode) {
        log.debug("Finding Certificates by countryCode: {}", countryCode);
        return jpaRepository.findBySubjectInfo_CountryCode(countryCode);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findNotUploadedToLdap() {
        log.debug("Finding Certificates not uploaded to LDAP");
        return jpaRepository.findNotUploadedToLdap();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findExpiringSoon(int daysThreshold) {
        log.debug("Finding Certificates expiring soon (within {} days)", daysThreshold);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryThreshold = now.plusDays(daysThreshold);
        return jpaRepository.findExpiringSoon(expiryThreshold, now);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAllBy() {
        log.debug("Counting all Certificates");
        return jpaRepository.countAllBy();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(String status) {
        log.debug("Counting Certificates by status: {}", status);
        return jpaRepository.countByStatus(CertificateStatus.valueOf(status));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TypeCount> countCertificatesByType() {
        log.debug("Counting Certificates by type");
        return jpaRepository.countCertificatesByType();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CountryCount> countCertificatesByCountry() {
        log.debug("Counting Certificates by country");
        return jpaRepository.countCertificatesByCountry();
    }

    // ========== Phase 2 - Master List & Source Tracking Query Methods ==========

    /**
     * 출처 타입별 Certificate 목록 조회
     *
     * @param sourceType 출처 타입 (MASTER_LIST, LDIF_DSC, LDIF_CSCA)
     * @return Certificate 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findBySourceType(CertificateSourceType sourceType) {
        log.debug("Finding Certificates by sourceType: {}", sourceType);
        return jpaRepository.findBySourceType(sourceType);
    }

    /**
     * Master List ID로 Certificate 목록 조회
     *
     * @param masterListId Master List ID
     * @return Certificate 목록 (해당 Master List에서 추출된 CSCA)
     */
    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findByMasterListId(UUID masterListId) {
        log.debug("Finding Certificates by masterListId: {}", masterListId);
        return jpaRepository.findByMasterListId(masterListId);
    }

    /**
     * Master List에서 추출된 모든 CSCA 인증서 조회
     *
     * @return Certificate 목록 (sourceType = MASTER_LIST)
     */
    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findMasterListCertificates() {
        log.debug("Finding all Master List Certificates");
        return jpaRepository.findMasterListCertificates();
    }

    /**
     * LDIF 파일에서 로드된 모든 인증서 조회
     *
     * @return Certificate 목록 (sourceType = LDIF_DSC or LDIF_CSCA)
     */
    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findLdifCertificates() {
        log.debug("Finding all LDIF Certificates");
        return jpaRepository.findLdifCertificates();
    }
}