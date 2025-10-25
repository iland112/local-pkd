package com.smartcoreinc.localpkd.certificatevalidation.domain.service;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.*;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * CertificatePathBuilderTest - Certificate Path Builder Domain Service 단위 테스트
 *
 * <p>핵심 기능 테스트 (간소화 버전)</p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CertificatePathBuilder 단위 테스트")
class CertificatePathBuilderTest {

    @Mock
    private CertificateRepository certificateRepository;

    @InjectMocks
    private CertificatePathBuilderImpl certificatePathBuilder;

    @Test
    @DisplayName("null Certificate로 Path 구성 시 예외 발생")
    void testBuildPathWithNullCertificate() {
        // When & Then
        assertThatThrownBy(() -> certificatePathBuilder.buildPath((Certificate) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Certificate must not be null");
    }

    @Test
    @DisplayName("null CertificateId로 Path 구성 시 예외 발생")
    void testBuildPathWithNullCertificateId() {
        // When & Then
        assertThatThrownBy(() -> certificatePathBuilder.buildPath((CertificateId) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Certificate ID must not be null");
    }

    @Test
    @DisplayName("CSCA 인증서는 Self-Signed")
    void testCscaIsSelfSigned() {
        // Given
        Certificate csca = createMockCertificate(CertificateType.CSCA, true);

        // When
        boolean isSelfSigned = certificatePathBuilder.isSelfSigned(csca);

        // Then
        assertThat(isSelfSigned).isTrue();
    }

    @Test
    @DisplayName("DSC 인증서는 Self-Signed 아님")
    void testDscIsNotSelfSigned() {
        // Given
        Certificate dsc = createMockCertificate(CertificateType.DSC, false);

        // When
        boolean isSelfSigned = certificatePathBuilder.isSelfSigned(dsc);

        // Then
        assertThat(isSelfSigned).isFalse();
    }

    @Test
    @DisplayName("null Certificate로 Self-Signed 검사 시 예외 발생")
    void testIsSelfSignedWithNull() {
        // When & Then
        assertThatThrownBy(() -> certificatePathBuilder.isSelfSigned(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Certificate must not be null");
    }

    @Test
    @DisplayName("null Issuer DN 조회 시 예외 발생")
    void testFindIssuerCertificateWithNull() {
        // When & Then
        assertThatThrownBy(() -> certificatePathBuilder.findIssuerCertificate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Issuer DN must not be null");
    }

    @Test
    @DisplayName("빈 Issuer DN 조회 시 예외 발생")
    void testFindIssuerCertificateWithEmptyString() {
        // When & Then
        assertThatThrownBy(() -> certificatePathBuilder.findIssuerCertificate(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Issuer DN must not be blank");
    }

    @Test
    @DisplayName("유효한 Issuer DN으로 인증서 조회 성공")
    void testFindIssuerCertificateSuccess() {
        // Given
        String issuerDn = "CN=CSCA KR, O=Korea, C=KR";
        Certificate csca = createMockCertificate(CertificateType.CSCA, true);

        when(certificateRepository.findBySubjectDn(issuerDn))
                .thenReturn(Optional.of(csca));

        // When
        Optional<Certificate> issuerOpt = certificatePathBuilder.findIssuerCertificate(issuerDn);

        // Then
        assertThat(issuerOpt).isPresent();
        assertThat(issuerOpt.get().getId()).isEqualTo(csca.getId());
    }

    @Test
    @DisplayName("존재하지 않는 Issuer DN 조회 시 Empty 반환")
    void testFindIssuerCertificateNotFound() {
        // Given
        String issuerDn = "CN=Non-Existent, O=Test, C=KR";

        when(certificateRepository.findBySubjectDn(issuerDn))
                .thenReturn(Optional.empty());

        // When
        Optional<Certificate> issuerOpt = certificatePathBuilder.findIssuerCertificate(issuerDn);

        // Then
        assertThat(issuerOpt).isEmpty();
    }

    // ==================== Helper Methods ====================

    private Certificate createMockCertificate(CertificateType type, boolean isSelfSigned) {
        String subjectDn = "CN=Test Cert, O=Test, C=KR";
        String issuerDn = isSelfSigned ? subjectDn : "CN=Test Issuer, O=Test, C=KR";

        return Certificate.createForTest(
                CertificateId.newId(),
                type,
                SubjectInfo.of(
                        subjectDn,                       // distinguishedName
                        "KR",                            // countryCode
                        "Test",                          // organization
                        null,                            // organizationalUnit
                        "Test Cert"                      // commonName
                ),
                IssuerInfo.of(
                        issuerDn,                        // distinguishedName
                        "KR",                            // countryCode
                        "Test",                          // organization
                        null,                            // organizationalUnit
                        isSelfSigned ? "Test Cert" : "Test Issuer",
                        type == CertificateType.CSCA     // isCA
                ),
                ValidityPeriod.of(
                        LocalDateTime.now().minusYears(1),
                        LocalDateTime.now().plusYears(10)
                ),
                X509Data.createForTest(
                        new byte[]{(byte) 0x30, (byte) 0x82, (byte) 0x01, (byte) 0x00},
                        "a1b2c3d4e5f6",
                        type == CertificateType.CSCA
                ),
                CertificateStatus.VALID
        );
    }
}
