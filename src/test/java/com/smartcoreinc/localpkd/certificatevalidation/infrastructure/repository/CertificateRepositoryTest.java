package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.repository;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.*;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * CertificateRepository 통합 테스트 - Phase 17 Task 1.4
 *
 * <p>Certificate Repository의 findByUploadId() 메서드 테스트</p>
 * <p>Cross-Context Reference: File Upload Context와 Certificate Validation Context 연동 검증</p>
 *
 * @author SmartCore Inc.
 * @since 2025-10-30 (Phase 17 Task 1.4)
 */
@DisplayName("CertificateRepository 통합 테스트 - findByUploadId()")
@DataJpaTest
@Import(JpaCertificateRepository.class)
@ActiveProfiles("test")
class CertificateRepositoryTest {

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private TestEntityManager entityManager;

    // ========== Helper Methods ==========

    /**
     * 테스트용 Certificate 생성
     */
    private Certificate createTestCertificate(
            UUID uploadId,
            String subjectCn,
            String issuerCn,
            CertificateType type) {
        return Certificate.create(
                uploadId,  // Phase 17: Cross-Context Reference
                X509Data.of(
                        new byte[]{1, 2, 3, 4, 5},  // Certificate binary
                        null,                        // PublicKey (not needed for test)
                        "123456789",                 // Serial number
                        "abc123def456abc123def456abc123def456"  // SHA-256 fingerprint
                ),
                SubjectInfo.of(
                        "CN=" + subjectCn + ",C=US,O=Test",
                        "US",
                        "Test",
                        "Test Unit",
                        subjectCn
                ),
                IssuerInfo.of(
                        "CN=" + issuerCn + ",C=US,O=Test",
                        "US",
                        "Test",
                        "Test Unit",
                        issuerCn,
                        type == CertificateType.CSCA
                ),
                ValidityPeriod.of(
                        LocalDateTime.now(),
                        LocalDateTime.now().plusYears(1)
                ),
                type,
                "SHA256withRSA"
        );
    }

    // ========== FindByUploadId Tests ==========

    @Test
    @DisplayName("업로드 ID로 Certificate 조회 - 존재하는 경우")
    void findByUploadId_WithExistingUploadId_ReturnsCertificates() {
        // given
        UUID uploadId = UUID.randomUUID();
        Certificate cert1 = createTestCertificate(uploadId, "Certificate1", "CSCA-Test", CertificateType.DSC);
        Certificate cert2 = createTestCertificate(uploadId, "Certificate2", "CSCA-Test", CertificateType.DSC);

        // when
        certificateRepository.save(cert1);
        certificateRepository.save(cert2);
        entityManager.flush();
        entityManager.clear();

        List<Certificate> found = certificateRepository.findByUploadId(uploadId);

        // then
        assertThat(found).isNotEmpty();
        assertThat(found).hasSize(2);
        assertThat(found).allMatch(cert -> cert.getUploadId().equals(uploadId));
        assertThat(found.stream().map(cert -> cert.getSubjectInfo().getCommonName()))
                .containsExactlyInAnyOrder("Certificate1", "Certificate2");
    }

    @Test
    @DisplayName("업로드 ID로 Certificate 조회 - 존재하지 않는 경우")
    void findByUploadId_WithNonExistingUploadId_ReturnsEmptyList() {
        // given
        UUID uploadId = UUID.randomUUID();

        // when
        List<Certificate> found = certificateRepository.findByUploadId(uploadId);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("업로드 ID로 Certificate 조회 - 같은 업로드에 여러 인증서")
    void findByUploadId_WithMultipleCertificatesInSameUpload_ReturnsAll() {
        // given
        UUID uploadId = UUID.randomUUID();
        List<Certificate> certs = List.of(
                createTestCertificate(uploadId, "CSCA-Test", "Self", CertificateType.CSCA),
                createTestCertificate(uploadId, "DSC-Test1", "CSCA-Test", CertificateType.DSC),
                createTestCertificate(uploadId, "DSC-Test2", "CSCA-Test", CertificateType.DSC),
                createTestCertificate(uploadId, "DS-Test", "DSC-Test1", CertificateType.DS)
        );

        // when
        certs.forEach(certificateRepository::save);
        entityManager.flush();
        entityManager.clear();

        List<Certificate> found = certificateRepository.findByUploadId(uploadId);

        // then
        assertThat(found).hasSize(4);
        assertThat(found).allMatch(cert -> cert.getUploadId().equals(uploadId));
        assertThat(found.stream().map(cert -> cert.getCertificateType()))
                .containsExactlyInAnyOrder(
                        CertificateType.CSCA,
                        CertificateType.DSC,
                        CertificateType.DSC,
                        CertificateType.DS
                );
    }

    @Test
    @DisplayName("업로드 ID로 Certificate 조회 - 다른 업로드와 구분")
    void findByUploadId_IsolatesResultsByUploadId() {
        // given
        UUID uploadId1 = UUID.randomUUID();
        UUID uploadId2 = UUID.randomUUID();

        certificateRepository.save(createTestCertificate(uploadId1, "Cert-Upload1", "CSCA", CertificateType.DSC));
        certificateRepository.save(createTestCertificate(uploadId2, "Cert-Upload2", "CSCA", CertificateType.DSC));

        entityManager.flush();
        entityManager.clear();

        // when
        List<Certificate> foundUpload1 = certificateRepository.findByUploadId(uploadId1);
        List<Certificate> foundUpload2 = certificateRepository.findByUploadId(uploadId2);

        // then
        assertThat(foundUpload1).hasSize(1);
        assertThat(foundUpload1.get(0).getUploadId()).isEqualTo(uploadId1);
        assertThat(foundUpload1.get(0).getSubjectInfo().getCommonName()).isEqualTo("Cert-Upload1");

        assertThat(foundUpload2).hasSize(1);
        assertThat(foundUpload2.get(0).getUploadId()).isEqualTo(uploadId2);
        assertThat(foundUpload2.get(0).getSubjectInfo().getCommonName()).isEqualTo("Cert-Upload2");
    }

    @Test
    @DisplayName("업로드 ID로 Certificate 조회 - 대량 인증서")
    void findByUploadId_WithManyUploadedCertificates_ReturnsAllCorrectly() {
        // given
        UUID uploadId = UUID.randomUUID();
        int certificateCount = 10;

        for (int i = 0; i < certificateCount; i++) {
            Certificate cert = createTestCertificate(
                    uploadId,
                    "Certificate-" + i,
                    "CSCA-Test",
                    i % 2 == 0 ? CertificateType.DSC : CertificateType.DS
            );
            certificateRepository.save(cert);
        }

        entityManager.flush();
        entityManager.clear();

        // when
        List<Certificate> found = certificateRepository.findByUploadId(uploadId);

        // then
        assertThat(found).hasSize(certificateCount);
        assertThat(found).allMatch(cert -> cert.getUploadId().equals(uploadId));
        assertThat(found.stream().filter(cert -> cert.getCertificateType() == CertificateType.DSC).count())
                .isEqualTo(certificateCount / 2);
    }

    @Test
    @DisplayName("업로드 ID로 Certificate 조회 - 다양한 인증서 타입")
    void findByUploadId_WithDifferentCertificateTypes_ReturnsAll() {
        // given
        UUID uploadId = UUID.randomUUID();

        certificateRepository.save(createTestCertificate(uploadId, "CSCA", "Self", CertificateType.CSCA));
        certificateRepository.save(createTestCertificate(uploadId, "DSC", "CSCA", CertificateType.DSC));
        certificateRepository.save(createTestCertificate(uploadId, "DSC-NC", "CSCA", CertificateType.DSC_NC));
        certificateRepository.save(createTestCertificate(uploadId, "DS", "DSC", CertificateType.DS));

        entityManager.flush();
        entityManager.clear();

        // when
        List<Certificate> found = certificateRepository.findByUploadId(uploadId);

        // then
        assertThat(found).hasSize(4);
        assertThat(found.stream().map(Certificate::getCertificateType).distinct())
                .containsExactlyInAnyOrder(
                        CertificateType.CSCA,
                        CertificateType.DSC,
                        CertificateType.DSC_NC,
                        CertificateType.DS
                );
    }

    @Test
    @DisplayName("업로드 ID로 Certificate 조회 - null uploadId 시 예외 발생")
    void findByUploadId_WithNullUploadId_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> certificateRepository.findByUploadId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uploadId must not be null");
    }

    @Test
    @DisplayName("업로드 ID로 Certificate 조회 - Cross-Context 참조 검증")
    void findByUploadId_VerifiesCrossContextReference() {
        // given
        UUID fileUploadId = UUID.randomUUID();  // From File Upload Context
        Certificate cert = createTestCertificate(
                fileUploadId,
                "CrossContextCert",
                "CSCA",
                CertificateType.DSC
        );

        // when
        certificateRepository.save(cert);
        entityManager.flush();
        entityManager.clear();

        List<Certificate> found = certificateRepository.findByUploadId(fileUploadId);

        // then
        assertThat(found).hasSize(1);
        Certificate foundCert = found.get(0);
        assertThat(foundCert.getUploadId()).isEqualTo(fileUploadId);
        assertThat(foundCert.getSubjectInfo().getCommonName()).isEqualTo("CrossContextCert");
        // Verify that uploadId correctly references the File Upload Context
        assertThat(foundCert.getUploadId())
                .as("uploadId should be a valid UUID pointing to File Upload Context")
                .isNotNull();
    }

    // ========== Integration with Other Methods ==========

    @Test
    @DisplayName("업로드 ID로 조회 후 개별 조회 일관성")
    void findByUploadId_ConsistencyWithFindById() {
        // given
        UUID uploadId = UUID.randomUUID();
        Certificate cert = createTestCertificate(uploadId, "TestCert", "CSCA", CertificateType.DSC);

        // when
        Certificate saved = certificateRepository.save(cert);
        entityManager.flush();
        entityManager.clear();

        List<Certificate> foundByUpload = certificateRepository.findByUploadId(uploadId);
        Optional<Certificate> foundById = certificateRepository.findById(saved.getId());

        // then
        assertThat(foundByUpload).hasSize(1);
        assertThat(foundById).isPresent();
        assertThat(foundByUpload.get(0).getId()).isEqualTo(foundById.get().getId());
    }
}
