package com.smartcoreinc.localpkd.fileparsing.infrastructure.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ParsedCertificateQueryRepository {

    @Query("SELECT COUNT(c) > 0 FROM ParsedFile pf JOIN pf.certificates c WHERE c.fingerprintSha256 = :fingerprintSha256")
    boolean existsByFingerprintSha256(@Param("fingerprintSha256") String fingerprintSha256);

    /**
     * Batch query to find existing fingerprints from a given set
     * Performance optimization: N+1 query → single IN query
     *
     * @param fingerprints Set of fingerprints to check
     * @return List of fingerprints that already exist in the database
     */
    @Query("SELECT DISTINCT c.fingerprintSha256 FROM ParsedFile pf JOIN pf.certificates c WHERE c.fingerprintSha256 IN :fingerprints")
    List<String> findFingerprintsByFingerprintSha256In(@Param("fingerprints") Set<String> fingerprints);

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
