package com.smartcoreinc.localpkd.fileparsing.infrastructure.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ParsedCertificateQueryRepository {

    @Query("SELECT COUNT(c) > 0 FROM ParsedFile pf JOIN pf.certificates c WHERE c.fingerprintSha256 = :fingerprintSha256")
    boolean existsByFingerprintSha256(@Param("fingerprintSha256") String fingerprintSha256);

    /**
     * Count total certificates by uploadId
     */
    @Query("SELECT COUNT(c) FROM ParsedFile pf JOIN pf.certificates c WHERE pf.uploadId.id = :uploadId")
    long countByUploadId(@Param("uploadId") UUID uploadId);

    /**
     * Count certificates by uploadId and certificate type (CSCA, DSC, DSC_NC)
     */
    @Query("SELECT COUNT(c) FROM ParsedFile pf JOIN pf.certificates c WHERE pf.uploadId.id = :uploadId AND c.certificateType = :certType")
    long countByUploadIdAndCertType(@Param("uploadId") UUID uploadId, @Param("certType") String certType);
}
