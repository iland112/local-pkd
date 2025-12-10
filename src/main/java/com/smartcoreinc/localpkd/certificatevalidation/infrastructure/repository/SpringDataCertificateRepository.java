package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.repository;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateSourceType;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateStatus;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCount;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.TypeCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface SpringDataCertificateRepository extends JpaRepository<Certificate, CertificateId> {

    long countAllBy();
    long countByStatus(CertificateStatus status);

    @Query("SELECT new com.smartcoreinc.localpkd.certificatevalidation.domain.model.TypeCount(c.certificateType, COUNT(c)) FROM Certificate c GROUP BY c.certificateType")
    List<TypeCount> countCertificatesByType();

    @Query("SELECT new com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCount(c.issuerInfo.countryCode, COUNT(c)) FROM Certificate c GROUP BY c.issuerInfo.countryCode")
    List<CountryCount> countCertificatesByCountry();

    Optional<Certificate> findById(CertificateId id);
    Optional<Certificate> findByX509Data_FingerprintSha256(String fingerprintSha256);
    Optional<Certificate> findByX509Data_SerialNumber(String serialNumber);
    void deleteById(CertificateId id);
    boolean existsById(CertificateId id);
    boolean existsByX509Data_FingerprintSha256(String fingerprintSha256);

    /**
     * 배치 fingerprint로 기존 Certificate 조회
     * Phase 1-1 배치 중복 체크 최적화: N+1 query → single IN query
     *
     * @param fingerprints 조회할 fingerprint 집합
     * @return DB에 이미 존재하는 fingerprint 목록
     */
    @Query("SELECT c.x509Data.fingerprintSha256 FROM Certificate c WHERE c.x509Data.fingerprintSha256 IN :fingerprints")
    List<String> findFingerprintsByFingerprintSha256In(@org.springframework.data.repository.query.Param("fingerprints") java.util.Set<String> fingerprints);

    List<Certificate> findByUploadId(java.util.UUID uploadId);
    List<Certificate> findByStatus(com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateStatus status);
    List<Certificate> findBySubjectInfo_CountryCode(String countryCode);
    @Query("SELECT c FROM Certificate c WHERE c.uploadedToLdap = false AND c.status = 'VALID'")
    List<Certificate> findNotUploadedToLdap();
    @Query("SELECT c FROM Certificate c WHERE c.validity.notAfter <= ?1 AND c.validity.notAfter >= ?2")
    List<Certificate> findExpiringSoon(LocalDateTime expiryThreshold, LocalDateTime now);

    /**
     * Find certificate by Subject DN (latest if duplicates exist)
     * Returns the most recently created certificate when multiple certificates have the same Subject DN.
     * This prevents NonUniqueResultException when duplicate Subject DNs exist.
     *
     * @param subjectDn Subject Distinguished Name
     * @return Optional containing the latest certificate with the given Subject DN
     */
    Optional<Certificate> findFirstBySubjectInfo_DistinguishedNameOrderByCreatedAtDesc(String subjectDn);

    Optional<Certificate> findByIssuerInfo_DistinguishedName(String issuerDn);

    // ===========================
    // Phase 1 - Master List & Source Tracking Queries
    // ===========================

    /**
     * Find certificates by source type
     *
     * @param sourceType CertificateSourceType (MASTER_LIST, LDIF_DSC, LDIF_CSCA)
     * @return List of certificates from the specified source
     */
    List<Certificate> findBySourceType(CertificateSourceType sourceType);

    /**
     * Find certificates extracted from a specific Master List
     *
     * @param masterListId Master List ID
     * @return List of CSCA certificates from the Master List
     */
    List<Certificate> findByMasterListId(java.util.UUID masterListId);

    /**
     * Find all certificates from Master List sources
     *
     * @return List of all Master List certificates
     */
    @Query("SELECT c FROM Certificate c WHERE c.sourceType = 'MASTER_LIST'")
    List<Certificate> findMasterListCertificates();

    /**
     * Find all certificates from LDIF sources
     *
     * @return List of all LDIF certificates (DSC and CSCA)
     */
    @Query("SELECT c FROM Certificate c WHERE c.sourceType IN ('LDIF_DSC', 'LDIF_CSCA')")
    List<Certificate> findLdifCertificates();

    // ===========================
    // Statistics Queries for Upload History
    // ===========================

    /**
     * Count total certificates by uploadId
     *
     * @param uploadId Upload ID
     * @return Total certificate count
     */
    long countByUploadId(java.util.UUID uploadId);

    /**
     * Count certificates by uploadId and status
     *
     * @param uploadId Upload ID
     * @param status Certificate status
     * @return Certificate count for the given status
     */
    long countByUploadIdAndStatus(java.util.UUID uploadId, CertificateStatus status);

    /**
     * Find all certificates by certificate type
     * Phase 1-2 CSCA 캐시 최적화: DSC 검증 시 전체 CSCA 조회
     *
     * @param certificateType Certificate type (CSCA, DSC, DSC_NC)
     * @return List of certificates of the specified type
     */
    List<Certificate> findByCertificateType(com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType certificateType);
}
