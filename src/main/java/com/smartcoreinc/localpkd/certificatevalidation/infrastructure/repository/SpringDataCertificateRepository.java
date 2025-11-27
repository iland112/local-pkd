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
    List<Certificate> findByUploadId(java.util.UUID uploadId);
    List<Certificate> findByStatus(com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateStatus status);
    List<Certificate> findBySubjectInfo_CountryCode(String countryCode);
    @Query("SELECT c FROM Certificate c WHERE c.uploadedToLdap = false AND c.status = 'VALID'")
    List<Certificate> findNotUploadedToLdap();
    @Query("SELECT c FROM Certificate c WHERE c.validity.notAfter <= ?1 AND c.validity.notAfter >= ?2")
    List<Certificate> findExpiringSoon(LocalDateTime expiryThreshold, LocalDateTime now);
    Optional<Certificate> findBySubjectInfo_DistinguishedName(String subjectDn);
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
}
