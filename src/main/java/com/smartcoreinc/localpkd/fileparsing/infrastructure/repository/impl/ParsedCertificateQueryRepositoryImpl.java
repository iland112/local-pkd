package com.smartcoreinc.localpkd.fileparsing.infrastructure.repository.impl;

import com.smartcoreinc.localpkd.fileparsing.infrastructure.repository.ParsedCertificateQueryRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    @Override
    public List<String> findFingerprintsByFingerprintSha256In(Set<String> fingerprints) {
        if (fingerprints == null || fingerprints.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                        "SELECT DISTINCT c.fingerprintSha256 FROM ParsedFile pf JOIN pf.certificates c WHERE c.fingerprintSha256 IN :fingerprints",
                        String.class)
                .setParameter("fingerprints", fingerprints)
                .getResultList();
    }

    @Override
    public long countByUploadId(UUID uploadId) {
        return entityManager.createQuery(
                        "SELECT COUNT(c) FROM ParsedFile pf JOIN pf.certificates c WHERE pf.uploadId.id = :uploadId", Long.class)
                .setParameter("uploadId", uploadId)
                .getSingleResult();
    }

    @Override
    public long countByUploadIdAndCertType(UUID uploadId, String certType) {
        return entityManager.createQuery(
                        "SELECT COUNT(c) FROM ParsedFile pf JOIN pf.certificates c WHERE pf.uploadId.id = :uploadId AND c.certificateType = :certType", Long.class)
                .setParameter("uploadId", uploadId)
                .setParameter("certType", certType)
                .getSingleResult();
    }
}
