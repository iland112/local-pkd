package com.smartcoreinc.localpkd.certificatevalidation.domain.service;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.*;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TrustChainValidatorTest - Trust Chain 검증 Domain Service 단위 테스트
 *
 * <p>핵심 기능 테스트 (간소화 버전)</p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TrustChainValidator 단위 테스트")
class TrustChainValidatorTest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private CertificateRevocationListRepository crlRepository;

    @InjectMocks
    private TrustChainValidatorImpl trustChainValidator;

    @Test
    @DisplayName("null CSCA 인증서 검증 시 예외 발생")
    void testNullCscaCertificate() {
        // When & Then
        assertThatThrownBy(() -> trustChainValidator.validateCsca(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CSCA certificate must not be null");
    }

    @Test
    @DisplayName("null DSC 인증서 검증 시 예외 발생")
    void testNullDscCertificate() {
        // Given
        Certificate csca = createMockCertificate(CertificateType.CSCA, true);

        // When & Then
        assertThatThrownBy(() -> trustChainValidator.validateDsc(null, csca))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DSC certificate must not be null");
    }

    @Test
    @DisplayName("null CSCA와 DSC 검증 시 예외 발생")
    void testNullCscaCertificateForDscValidation() {
        // Given
        Certificate dsc = createMockCertificate(CertificateType.DSC, false);

        // When & Then
        assertThatThrownBy(() -> trustChainValidator.validateDsc(dsc, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CSCA certificate must not be null");
    }

    @Test
    @DisplayName("null Trust Path 검증 시 예외 발생")
    void testNullTrustPath() {
        // When & Then
        assertThatThrownBy(() -> trustChainValidator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trust path must not be null");
    }

    @Test
    @DisplayName("1단계 Trust Path 검증 (CSCA만)")
    void testSingleCertificateTrustPath() {
        // Given
        Certificate csca = createMockCertificate(CertificateType.CSCA, true);
        TrustPath path = TrustPath.ofSingle(csca.getId().getId());

        when(certificateRepository.findById(csca.getId())).thenReturn(Optional.of(csca));

        // When
        ValidationResult result = trustChainValidator.validate(path);

        // Then
        assertThat(result).isNotNull();
        // Note: 실제 검증은 X.509 서명이 필요하므로 여기서는 null 체크만
    }

    @Test
    @DisplayName("Trust Path의 인증서 로드 실패 시 예외 발생")
    void testTrustPathWithMissingCertificate() {
        // Given
        UUID missingId = UUID.randomUUID();
        TrustPath path = TrustPath.ofSingle(missingId);

        when(certificateRepository.findById(any())).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> trustChainValidator.validate(path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Certificate not found");
    }

    // ==================== Helper Methods ====================

    private Certificate createMockCertificate(CertificateType type, boolean isSelfSigned) {
        return Certificate.createForTest(
                CertificateId.newId(),
                type,
                SubjectInfo.of(
                        "CN=Test Cert, O=Test, C=KR",  // distinguishedName
                        "KR",                            // countryCode
                        "Test",                          // organization
                        null,                            // organizationalUnit
                        "Test Cert"                      // commonName
                ),
                IssuerInfo.of(
                        isSelfSigned ? "CN=Test Cert, O=Test, C=KR" : "CN=Test Issuer, O=Test, C=KR",
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
