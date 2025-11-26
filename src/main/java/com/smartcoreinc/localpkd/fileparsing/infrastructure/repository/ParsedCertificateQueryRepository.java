package com.smartcoreinc.localpkd.fileparsing.infrastructure.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ParsedCertificateQueryRepository {

    @Query("SELECT COUNT(c) > 0 FROM ParsedFile pf JOIN pf.certificates c WHERE c.fingerprintSha256 = :fingerprintSha256")
    boolean existsByFingerprintSha256(@Param("fingerprintSha256") String fingerprintSha256);
}
