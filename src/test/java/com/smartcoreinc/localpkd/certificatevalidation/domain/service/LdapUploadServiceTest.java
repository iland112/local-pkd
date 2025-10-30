package com.smartcoreinc.localpkd.certificatevalidation.domain.service;

import com.smartcoreinc.localpkd.certificatevalidation.domain.exception.LdapConnectionException;
import com.smartcoreinc.localpkd.certificatevalidation.domain.exception.LdapOperationException;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.*;
import com.smartcoreinc.localpkd.certificatevalidation.domain.port.LdapConnectionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LdapUploadServiceTest - LDAP 업로드 도메인 서비스 단위 테스트
 *
 * <p><b>테스트 범위</b>:
 * <ul>
 *   <li>단일 인증서 업로드 (성공/실패)</li>
 *   <li>배치 인증서 업로드 (성공/부분실패)</li>
 *   <li>단일 CRL 업로드 (성공/실패)</li>
 *   <li>배치 CRL 업로드</li>
 *   <li>LDAP 연결 관리</li>
 *   <li>입력값 검증</li>
 *   <li>에러 처리</li>
 * </ul>
 * </p>
 *
 * <p><b>Mock 전략</b>:
 * <ul>
 *   <li>LdapConnectionPort: Mockito로 모킹</li>
 *   <li>Certificate/CRL: 실제 Domain 객체 사용</li>
 *   <li>실제 LDAP 연결 없음</li>
 * </ul>
 * </p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24 (Phase 17 Task 1.5)
 */
@DisplayName("LdapUploadService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class LdapUploadServiceTest {

    @Mock
    private LdapConnectionPort ldapConnectionPort;

    private LdapUploadService ldapUploadService;

    @BeforeEach
    void setUp() {
        ldapUploadService = new LdapUploadService(ldapConnectionPort);
    }

    // ========== Helper Methods ==========

    /**
     * 테스트용 PublicKey 생성 (RSA 256비트 공개 키)
     */
    private PublicKey createMockPublicKey() {
        try {
            // RSA 256-bit public key (base64 encoded DER format)
            String pubKeyBase64 = "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBALGFLb6W7VH+Ss9/mNfKHnLX/V5R" +
                    "2SmHdlJvWaXPnGQZVEVPnF/vvqN8UakuKB8XkxGiGa8aVGfxyoVNc8nJlAEC" +
                    "AwEAAQ==";
            byte[] decodedKey = Base64.getDecoder().decode(pubKeyBase64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create mock public key", e);
        }
    }

    private Certificate createTestCertificate(UUID uploadId) {
        return Certificate.create(
            uploadId,
            X509Data.of(
                new byte[]{1, 2, 3, 4, 5},
                createMockPublicKey(),
                "123456789",
                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
            ),
            SubjectInfo.of(
                "CN=Test Certificate,C=US,O=Test",
                "US",
                "Test",
                "Test Unit",
                "Test Certificate"
            ),
            IssuerInfo.of(
                "CN=CSCA-Test,C=US,O=Test",
                "US",
                "Test",
                "Test Unit",
                "CSCA-Test",
                true
            ),
            ValidityPeriod.of(
                LocalDateTime.now(),
                LocalDateTime.now().plusYears(1)
            ),
            CertificateType.DSC,
            "SHA256withRSA"
        );
    }

    private CertificateRevocationList createTestCrl(UUID uploadId) {
        return CertificateRevocationList.create(
            uploadId,
            CrlId.newId(),
            IssuerName.of("CSCA-US"),
            CountryCode.of("US"),
            ValidityPeriod.of(
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
            ),
            X509CrlData.of(new byte[]{1, 2, 3, 4, 5}, 5),
            RevokedCertificates.empty()
        );
    }

    // ========== Single Certificate Upload Tests ==========

    @Test
    @DisplayName("단일 인증서를 LDAP에 업로드 - 성공")
    void testUploadCertificate_Success() {
        // Given
        UUID uploadId = UUID.randomUUID();
        Certificate certificate = createTestCertificate(uploadId);
        String baseDn = "dc=ldap,dc=smartcoreinc,dc=com";
        String expectedLdapDn = "cn=Test Certificate,ou=certificates," + baseDn;

        when(ldapConnectionPort.isConnected()).thenReturn(true);
        when(ldapConnectionPort.uploadCertificate(
            any(byte[].class),
            eq("Test Certificate"),
            eq(baseDn)
        )).thenReturn(expectedLdapDn);

        // When
        LdapUploadService.UploadResult result = ldapUploadService.uploadCertificate(certificate, baseDn);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getLdapDn()).isEqualTo(expectedLdapDn);
        assertThat(result.getEntityId()).isEqualTo(certificate.getId().getId().toString());

        verify(ldapConnectionPort).isConnected();
        verify(ldapConnectionPort).uploadCertificate(any(byte[].class), anyString(), anyString());
    }

    @Test
    @DisplayName("단일 인증서 업로드 - LDAP 연결 실패")
    void testUploadCertificate_LdapConnectionException() {
        // Given
        UUID uploadId = UUID.randomUUID();
        Certificate certificate = createTestCertificate(uploadId);
        String baseDn = "dc=ldap,dc=smartcoreinc,dc=com";

        when(ldapConnectionPort.isConnected()).thenReturn(false);
        doThrow(LdapConnectionException.connectionFailed("ldap://localhost:389", new RuntimeException("Connection timeout")))
            .when(ldapConnectionPort).connect();

        // When
        LdapUploadService.UploadResult result = ldapUploadService.uploadCertificate(certificate, baseDn);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("LDAP Connection Error");
    }

    @Test
    @DisplayName("단일 인증서 업로드 - LDAP 작업 실패")
    void testUploadCertificate_LdapOperationException() {
        // Given
        UUID uploadId = UUID.randomUUID();
        Certificate certificate = createTestCertificate(uploadId);
        String baseDn = "dc=ldap,dc=smartcoreinc,dc=com";

        when(ldapConnectionPort.isConnected()).thenReturn(true);
        when(ldapConnectionPort.uploadCertificate(any(), any(), any()))
            .thenThrow(LdapOperationException.uploadCertificateFailed("Test Certificate", new Exception("Already exists")));

        // When
        LdapUploadService.UploadResult result = ldapUploadService.uploadCertificate(certificate, baseDn);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("LDAP Operation Error");
    }

    @Test
    @DisplayName("단일 인증서 업로드 - null Certificate 검증")
    void testUploadCertificate_NullCertificate() {
        // When & Then
        assertThatThrownBy(() -> ldapUploadService.uploadCertificate(null, "dc=test"))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Certificate must not be null");
    }

    @Test
    @DisplayName("단일 인증서 업로드 - null Base DN 검증")
    void testUploadCertificate_NullBaseDn() {
        // Given
        UUID uploadId = UUID.randomUUID();
        Certificate certificate = createTestCertificate(uploadId);

        // When & Then
        assertThatThrownBy(() -> ldapUploadService.uploadCertificate(certificate, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Base DN must not be null");
    }

    // ========== Batch Certificate Upload Tests ==========

    @Test
    @DisplayName("배치 인증서 업로드 - 모두 성공")
    void testUploadCertificatesBatch_AllSuccess() {
        // Given
        UUID uploadId = UUID.randomUUID();
        List<Certificate> certificates = Arrays.asList(
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId)
        );
        String baseDn = "dc=ldap,dc=smartcoreinc,dc=com";

        when(ldapConnectionPort.isConnected()).thenReturn(false);
        doNothing().when(ldapConnectionPort).connect();
        when(ldapConnectionPort.uploadCertificate(any(), any(), any()))
            .thenReturn("cn=test,ou=certificates," + baseDn);
        doNothing().when(ldapConnectionPort).keepAlive(anyInt());
        doNothing().when(ldapConnectionPort).disconnect();

        // When
        LdapUploadService.BatchUploadResult result = ldapUploadService.uploadCertificatesBatch(certificates, baseDn);

        // Then
        assertThat(result.getTotalCount()).isEqualTo(3);
        assertThat(result.getSuccessCount()).isEqualTo(3);
        assertThat(result.getFailureCount()).isEqualTo(0);
        assertThat(result.getSuccessRate()).isEqualTo(100.0);

        verify(ldapConnectionPort).connect();
        verify(ldapConnectionPort).uploadCertificate(any(), any(), any());
        verify(ldapConnectionPort).disconnect();
    }

    @Test
    @DisplayName("배치 인증서 업로드 - 부분 실패")
    void testUploadCertificatesBatch_PartialFailure() {
        // Given
        UUID uploadId = UUID.randomUUID();
        List<Certificate> certificates = Arrays.asList(
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId)
        );
        String baseDn = "dc=ldap,dc=smartcoreinc,dc=com";

        when(ldapConnectionPort.isConnected()).thenReturn(false);
        doNothing().when(ldapConnectionPort).connect();
        when(ldapConnectionPort.uploadCertificate(any(), any(), any()))
            .thenReturn("cn=test,ou=certificates," + baseDn)
            .thenThrow(LdapOperationException.entryAlreadyExists("cn=test,ou=certificates," + baseDn))
            .thenReturn("cn=test,ou=certificates," + baseDn);
        doNothing().when(ldapConnectionPort).disconnect();

        // When
        LdapUploadService.BatchUploadResult result = ldapUploadService.uploadCertificatesBatch(certificates, baseDn);

        // Then
        assertThat(result.getTotalCount()).isEqualTo(3);
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getSuccessRate()).isEqualTo(66.66666666666666);
        assertThat(result.getFailedItems()).hasSize(1);

        verify(ldapConnectionPort).connect();
        verify(ldapConnectionPort).disconnect();
    }

    @Test
    @DisplayName("배치 인증서 업로드 - 연결 실패")
    void testUploadCertificatesBatch_ConnectionFailure() {
        // Given
        UUID uploadId = UUID.randomUUID();
        List<Certificate> certificates = Arrays.asList(
            createTestCertificate(uploadId),
            createTestCertificate(uploadId)
        );
        String baseDn = "dc=ldap,dc=smartcoreinc,dc=com";

        when(ldapConnectionPort.isConnected()).thenReturn(false);
        doThrow(LdapConnectionException.connectionFailed("ldap://localhost", new RuntimeException("Server down")))
            .when(ldapConnectionPort).connect();
        doNothing().when(ldapConnectionPort).disconnect();

        // When
        LdapUploadService.BatchUploadResult result = ldapUploadService.uploadCertificatesBatch(certificates, baseDn);

        // Then
        assertThat(result.getTotalCount()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getFailureCount()).isEqualTo(0);  // Connection error doesn't count as individual failures
        assertThat(result.hasConnectionError()).isTrue();

        verify(ldapConnectionPort).disconnect();
    }

    @Test
    @DisplayName("배치 인증서 업로드 - 빈 리스트 검증")
    void testUploadCertificatesBatch_EmptyList() {
        // When & Then
        assertThatThrownBy(() -> ldapUploadService.uploadCertificatesBatch(
            List.of(),
            "dc=ldap,dc=smartcoreinc,dc=com"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be empty");
    }

    // ========== Single CRL Upload Tests ==========

    @Test
    @DisplayName("단일 CRL을 LDAP에 업로드 - 성공")
    void testUploadCrl_Success() {
        // Given
        UUID uploadId = UUID.randomUUID();
        CertificateRevocationList crl = createTestCrl(uploadId);
        String baseDn = "dc=ldap,dc=smartcoreinc,dc=com";
        String expectedLdapDn = "cn=CSCA-US,ou=crl," + baseDn;

        when(ldapConnectionPort.isConnected()).thenReturn(true);
        when(ldapConnectionPort.uploadCrl(any(byte[].class), eq("CSCA-US"), eq(baseDn)))
            .thenReturn(expectedLdapDn);

        // When
        LdapUploadService.UploadResult result = ldapUploadService.uploadCrl(crl, baseDn);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getLdapDn()).isEqualTo(expectedLdapDn);
    }

    @Test
    @DisplayName("단일 CRL 업로드 - null CRL 검증")
    void testUploadCrl_NullCrl() {
        // When & Then
        assertThatThrownBy(() -> ldapUploadService.uploadCrl(null, "dc=test"))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("CRL must not be null");
    }

    // ========== Batch CRL Upload Tests ==========

    @Test
    @DisplayName("배치 CRL 업로드 - 성공")
    void testUploadCrlsBatch_Success() {
        // Given
        UUID uploadId = UUID.randomUUID();
        List<CertificateRevocationList> crls = Arrays.asList(
            createTestCrl(uploadId),
            createTestCrl(uploadId),
            createTestCrl(uploadId)
        );
        String baseDn = "dc=ldap,dc=smartcoreinc,dc=com";

        when(ldapConnectionPort.isConnected()).thenReturn(false);
        doNothing().when(ldapConnectionPort).connect();
        when(ldapConnectionPort.uploadCrl(any(), any(), any()))
            .thenReturn("cn=CSCA-Test,ou=crl," + baseDn);
        doNothing().when(ldapConnectionPort).disconnect();

        // When
        LdapUploadService.BatchUploadResult result = ldapUploadService.uploadCrlsBatch(crls, baseDn);

        // Then
        assertThat(result.getTotalCount()).isEqualTo(3);
        assertThat(result.getSuccessCount()).isEqualTo(3);
        assertThat(result.getFailureCount()).isEqualTo(0);

        verify(ldapConnectionPort).connect();
        verify(ldapConnectionPort).uploadCrl(any(), any(), any());
        verify(ldapConnectionPort).disconnect();
    }

    // ========== Connection Management Tests ==========

    @Test
    @DisplayName("자동 LDAP 연결 - 미연결 상태")
    void testAutoConnect_NotConnected() {
        // Given
        UUID uploadId = UUID.randomUUID();
        Certificate certificate = createTestCertificate(uploadId);
        String baseDn = "dc=ldap,dc=smartcoreinc,dc=com";

        when(ldapConnectionPort.isConnected())
            .thenReturn(false)  // First call - not connected
            .thenReturn(true);   // After connect

        doNothing().when(ldapConnectionPort).connect();
        when(ldapConnectionPort.uploadCertificate(any(), any(), any()))
            .thenReturn("cn=test,ou=certificates," + baseDn);

        // When
        LdapUploadService.UploadResult result = ldapUploadService.uploadCertificate(certificate, baseDn);

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(ldapConnectionPort).connect();  // Auto-connect was called
    }

    @Test
    @DisplayName("배치 업로드 중 Keep-Alive 호출")
    void testBatchUpload_KeepAliveEvery10Items() {
        // Given
        UUID uploadId = UUID.randomUUID();
        List<Certificate> certificates = Arrays.asList(
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),  // 11th item - trigger keep-alive
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId),
            createTestCertificate(uploadId)
        );
        String baseDn = "dc=ldap,dc=smartcoreinc,dc=com";

        when(ldapConnectionPort.isConnected()).thenReturn(false);
        doNothing().when(ldapConnectionPort).connect();
        when(ldapConnectionPort.uploadCertificate(any(), any(), any()))
            .thenReturn("cn=test,ou=certificates," + baseDn);
        doNothing().when(ldapConnectionPort).keepAlive(anyInt());
        doNothing().when(ldapConnectionPort).disconnect();

        // When
        LdapUploadService.BatchUploadResult result = ldapUploadService.uploadCertificatesBatch(certificates, baseDn);

        // Then
        assertThat(result.getSuccessCount()).isEqualTo(20);
        verify(ldapConnectionPort, times(2)).keepAlive(300);  // Called after 10th and 20th items
    }
}
