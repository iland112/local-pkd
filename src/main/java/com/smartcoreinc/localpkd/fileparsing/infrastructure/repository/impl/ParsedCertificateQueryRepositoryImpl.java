package com.smartcoreinc.localpkd.fileparsing.infrastructure.repository.impl;

import com.smartcoreinc.localpkd.fileparsing.infrastructure.repository.ParsedCertificateQueryRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ParsedCertificateQueryRepositoryImpl implements ParsedCertificateQueryRepository {

    private final EntityManager entityManager;

    @Override
    public boolean existsByFingerprintSha256(String fingerprintSha256) {
        Long count = entityManager.createQuery(
                        "SELECT COUNT(c) FROM ParsedFile pf JOIN pf.certificates c WHERE c.fingerprintSha256 = :fingerprintSha256", Long.class)
                .setParameter("fingerprintSha256", fingerprintSha256)
                .getSingleResult();
        return count > 0;
    }
}
