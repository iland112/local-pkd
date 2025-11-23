package com.smartcoreinc.localpkd.certificatevalidation.application.usecase;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.UploadToLdapResponse;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateStatus;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.IssuerInfo;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.SubjectInfo;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.ValidityPeriod;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.X509Data;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.certificatevalidation.domain.service.LdapUploadService;
import com.smartcoreinc.localpkd.certificatevalidation.domain.service.LdapUploadService.UploadResult;
import com.smartcoreinc.localpkd.certificatevalidation.domain.service.LdapUploadService.BatchUploadResult;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UploadToLdapUseCaseTest - LDAP 업로드 Use Case 단위 테스트
 *
 * <p><b>테스트 범위</b>:
 * <ul>
 *   <li>단일 인증서 업로드</li>
 *   <li>배치 인증서 업로드</li>
 *   <li>부분 성공 시나리오</li>
 *   <li>완전 실패 시나리오</li>
 *   <li>입력 검증</li>
 *   <li>인증서 조회 실패</li>
 *   <li>LDAP 연결 실패</li>
 * </ul>
 * </p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-30 (Phase 17 Task 1.6)
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("UploadToLdapUseCase - LDAP 업로드 Use Case 테스트")
public class UploadToLdapUseCaseTest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private LdapUploadService ldapUploadService;

    @Mock // ProgressService Mock 추가
    private com.smartcoreinc.localpkd.shared.progress.ProgressService progressService; // fully qualified name

    @InjectMocks
    private UploadToLdapUseCase uploadToLdapUseCase;

    private UUID uploadId;
    private UUID certificateId1;
    private UUID certificateId2;
    private Certificate certificate1;
    private Certificate certificate2;
    private static final String DEFAULT_BASE_DN = "dc=ldap,dc=smartcoreinc,dc=com";

    @BeforeEach
    void setUp() throws Exception {
        uploadId = UUID.randomUUID();
        certificateId1 = UUID.randomUUID();
        certificateId2 = UUID.randomUUID();

        // Create test certificates
        certificate1 = createTestCertificate(certificateId1, "CN=Test-Certificate-1");
        certificate2 = createTestCertificate(certificateId2, "CN=Test-Certificate-2");
    }

    // ========== Single Certificate Upload Tests ==========

    @Test
    @DisplayName("단일 인증서 업로드 성공")
    void testUploadSingleCertificateSuccess() {
        // Given
        UploadToLdapCommand command = UploadToLdapCommand.builder()
            .uploadId(uploadId)
            .baseDn(DEFAULT_BASE_DN)
            .certificateIds(List.of(certificateId1))
            .isBatch(false)
            .build();

        when(certificateRepository.findById(CertificateId.of(certificateId1)))
            .thenReturn(Optional.of(certificate1));

        UploadResult uploadResult = UploadResult.success(
            certificateId1.toString(),
            "cn=Test-Certificate-1,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"
        );
        when(ldapUploadService.uploadCertificate(certificate1, "dc=ldap,dc=smartcoreinc,dc=com"))
            .thenReturn(uploadResult);

        // When
        UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getFailureCount()).isEqualTo(0);
        assertThat(response.getUploadedDns()).hasSize(1);

        verify(certificateRepository).findById(CertificateId.of(certificateId1));
        verify(ldapUploadService).uploadCertificate(certificate1, "dc=ldap,dc=smartcoreinc,dc=com");

        log.info("✅ Single certificate upload success test passed");
    }

    @Test
    @DisplayName("단일 인증서 업로드 - LDAP 연결 실패")
    void testUploadSingleCertificateLdapConnectionFailed() {
        // Given
        UploadToLdapCommand command = UploadToLdapCommand.builder()
            .uploadId(uploadId)
            .baseDn(DEFAULT_BASE_DN)
            .certificateIds(List.of(certificateId1))
            .isBatch(false)
            .build();

        when(certificateRepository.findById(CertificateId.of(certificateId1)))
            .thenReturn(Optional.of(certificate1));

        UploadResult uploadResult = UploadResult.failure(
            certificateId1.toString(),
            "LDAP Connection Error: Connection refused"
        );
        when(ldapUploadService.uploadCertificate(certificate1, "dc=ldap,dc=smartcoreinc,dc=com"))
            .thenReturn(uploadResult);

        // When
        UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getSuccessCount()).isEqualTo(0);
        assertThat(response.getFailureCount()).isEqualTo(1);
        assertThat(response.getFailedCertificateIds()).contains(certificateId1);

        log.info("✅ Single certificate LDAP connection failure test passed");
    }

    // ========== Batch Certificate Upload Tests ==========

    @Test
    @DisplayName("배치 인증서 업로드 완전 성공")
    void testUploadBatchCertificatesCompleteSuccess() {
        // Given
        UploadToLdapCommand command = UploadToLdapCommand.builder()
            .uploadId(uploadId)
            .baseDn(DEFAULT_BASE_DN)
            .certificateIds(List.of(certificateId1, certificateId2))
            .isBatch(true)
            .build();

        when(certificateRepository.findById(CertificateId.of(certificateId1)))
            .thenReturn(Optional.of(certificate1));
        when(certificateRepository.findById(CertificateId.of(certificateId2)))
            .thenReturn(Optional.of(certificate2));

        // Mock batch upload result
        BatchUploadResult batchResult = new BatchUploadResult(2);
        batchResult.addSuccess(certificate1, "cn=Test-Certificate-1,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
        batchResult.addSuccess(certificate2, "cn=Test-Certificate-2,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        when(ldapUploadService.uploadCertificatesBatch(anyList(), eq("dc=ldap,dc=smartcoreinc,dc=com")))
            .thenReturn(batchResult);

        // When
        UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTotalCount()).isEqualTo(2);
        assertThat(response.getSuccessCount()).isEqualTo(2);
        assertThat(response.getFailureCount()).isEqualTo(0);
        assertThat(response.getSuccessRate()).isEqualTo(100.0);

        verify(ldapUploadService).uploadCertificatesBatch(anyList(), eq("dc=ldap,dc=smartcoreinc,dc=com"));

        log.info("✅ Batch certificate upload complete success test passed");
    }

    @Test
    @DisplayName("배치 인증서 업로드 부분 성공 (1/2 성공)")
    void testUploadBatchCertificatesPartialSuccess() {
        // Given
        UploadToLdapCommand command = UploadToLdapCommand.builder()
            .uploadId(uploadId)
            .baseDn(DEFAULT_BASE_DN)
            .certificateIds(List.of(certificateId1, certificateId2))
            .isBatch(true)
            .build();

        when(certificateRepository.findById(CertificateId.of(certificateId1)))
            .thenReturn(Optional.of(certificate1));
        when(certificateRepository.findById(CertificateId.of(certificateId2)))
            .thenReturn(Optional.of(certificate2));

        // Mock batch upload result with 1 success, 1 failure
        BatchUploadResult batchResult = new BatchUploadResult(2);
        batchResult.addSuccess(certificate1, "cn=Test-Certificate-1,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
        batchResult.addFailure(certificate2, "LDAP Operation Error: Entry already exists");

        when(ldapUploadService.uploadCertificatesBatch(anyList(), eq("dc=ldap,dc=smartcoreinc,dc=com")))
            .thenReturn(batchResult);

        // When
        UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isPartialSuccess()).isTrue();
        assertThat(response.getTotalCount()).isEqualTo(2);
        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getFailureCount()).isEqualTo(1);
        assertThat(response.getSuccessRate()).isEqualTo(50.0);

        log.info("✅ Batch certificate upload partial success test passed");
    }

    @Test
    @DisplayName("배치 인증서 업로드 완전 실패")
    void testUploadBatchCertificatesCompleteFailure() {
        // Given
        UploadToLdapCommand command = UploadToLdapCommand.builder()
            .uploadId(uploadId)
            .baseDn(DEFAULT_BASE_DN)
            .certificateIds(List.of(certificateId1, certificateId2))
            .isBatch(true)
            .build();

        when(certificateRepository.findById(CertificateId.of(certificateId1)))
            .thenReturn(Optional.of(certificate1));
        when(certificateRepository.findById(CertificateId.of(certificateId2)))
            .thenReturn(Optional.of(certificate2));

        // Mock batch upload result with all failures
        BatchUploadResult batchResult = new BatchUploadResult(2);
        batchResult.addFailure(certificate1, "LDAP Connection Error: Server unavailable");
        batchResult.addFailure(certificate2, "LDAP Connection Error: Server unavailable");

        when(ldapUploadService.uploadCertificatesBatch(anyList(), eq("dc=ldap,dc=smartcoreinc,dc=com")))
            .thenReturn(batchResult);

        // When
        UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getTotalCount()).isEqualTo(2);
        assertThat(response.getSuccessCount()).isEqualTo(0);
        assertThat(response.getFailureCount()).isEqualTo(2);
        assertThat(response.getSuccessRate()).isEqualTo(0.0);

        log.info("✅ Batch certificate upload complete failure test passed");
    }

    // ========== Input Validation Tests ==========

    @Test
    @DisplayName("Command 유효성 검사 - 빈 certificateIds")
    void testCommandValidationEmptyCertificateIds() {
        // Given
        UploadToLdapCommand command = UploadToLdapCommand.builder()
            .uploadId(uploadId)
            .baseDn(DEFAULT_BASE_DN)
            .build(); // No certificate IDs

        // When
        UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Certificate IDs must not be null or empty");

        log.info("✅ Command validation empty certificateIds test passed");
    }

    @Test
    @DisplayName("인증서 미발견 - CertificateId로 조회 실패")
    void testCertificateNotFound() {
        // Given
        UploadToLdapCommand command = UploadToLdapCommand.builder()
            .uploadId(uploadId)
            .baseDn(DEFAULT_BASE_DN)
            .certificateIds(List.of(certificateId1))
            .isBatch(false)
            .build();

        when(certificateRepository.findById(CertificateId.of(certificateId1)))
            .thenReturn(Optional.empty());

        // When
        UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Certificate not found");

        verify(certificateRepository).findById(CertificateId.of(certificateId1));
        verify(ldapUploadService, never()).uploadCertificate(any(), any());

        log.info("✅ Certificate not found test passed");
    }

    // ========== Certificate Validation Tests ==========

    @Test
    @DisplayName("인증서 유효성 검사 - 유효하지 않은 인증서 (INVALID 상태)")
    void testCertificateInvalidStatus() {
        // Given
        UploadToLdapCommand command = UploadToLdapCommand.builder()
            .uploadId(uploadId)
            .baseDn(DEFAULT_BASE_DN)
            .certificateIds(List.of(certificateId1))
            .isBatch(false)
            .build();

        // Create invalid certificate with INVALID status
        X509Data x509Data = X509Data.createForTest(
            new byte[]{0x30, (byte) 0x82, 0x01, 0x00},
            "INVALID_CERT_SN",
            false
        );

        Certificate invalidCertificate = Certificate.createForTest(
            CertificateId.of(certificateId1),
            CertificateType.UNKNOWN,
            SubjectInfo.of("CN=Test,C=KR", "KR", null, null, "Test"),
            IssuerInfo.of("CN=CSCA,C=KR", "KR", null, null, "CSCA", false),
            ValidityPeriod.of(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(365)),
            x509Data,
            CertificateStatus.INVALID  // INVALID status
        );

        when(certificateRepository.findById(CertificateId.of(certificateId1)))
            .thenReturn(Optional.of(invalidCertificate));

        UploadResult uploadResult = UploadResult.failure(
            certificateId1.toString(),
            "Certificate validation failed: Invalid status"
        );
        when(ldapUploadService.uploadCertificate(invalidCertificate, "dc=ldap,dc=smartcoreinc,dc=com"))
            .thenReturn(uploadResult);

        // When
        UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getFailureCount()).isEqualTo(1);

        log.info("✅ Certificate validation invalid status test passed");
    }

    // ========== Custom BaseDn Tests ==========

    @Test
    @DisplayName("Custom BaseDn으로 업로드")
    void testUploadWithCustomBaseDn() {
        // Given
        String customBaseDn = "dc=custom,dc=example,dc=com";
        UploadToLdapCommand command = UploadToLdapCommand.builder()
            .uploadId(uploadId)
            .baseDn(customBaseDn)
            .certificateIds(List.of(certificateId1))
            .isBatch(false)
            .build();

        when(certificateRepository.findById(CertificateId.of(certificateId1)))
            .thenReturn(Optional.of(certificate1));

        UploadResult uploadResult = UploadResult.success(
            certificateId1.toString(),
            "cn=Test-Certificate-1,ou=certificates," + customBaseDn
        );
        when(ldapUploadService.uploadCertificate(certificate1, customBaseDn))
            .thenReturn(uploadResult);

        // When
        UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();

        verify(ldapUploadService).uploadCertificate(certificate1, customBaseDn);

        log.info("✅ Custom BaseDn upload test passed");
    }

    // ========== Large Batch Tests ==========

    @Test
    @DisplayName("대량 배치 업로드 (100개 인증서, 95% 성공률)")
    void testUploadLargeBatch() {
        // Given
        List<UUID> certificateIds = new ArrayList<>();
        List<Certificate> certificates = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            UUID id = UUID.randomUUID();
            certificateIds.add(id);
            certificates.add(createTestCertificate(id, "CN=Certificate-" + i));
        }

        UploadToLdapCommand command = UploadToLdapCommand.builder()
            .uploadId(uploadId)
            .baseDn(DEFAULT_BASE_DN)
            .certificateIds(certificateIds)
            .isBatch(true)
            .build();

        // Mock individual findById calls
        for (int i = 0; i < 100; i++) {
            when(certificateRepository.findById(CertificateId.of(certificateIds.get(i))))
                .thenReturn(Optional.of(certificates.get(i)));
        }

        // Mock batch result with 95 success, 5 failures
        BatchUploadResult batchResult = new BatchUploadResult(100);
        for (int i = 0; i < 100; i++) {
            if (i < 95) {
                batchResult.addSuccess(certificates.get(i), "cn=Certificate-" + i + ",ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
            } else {
                batchResult.addFailure(certificates.get(i), "Operation timeout");
            }
        }

        when(ldapUploadService.uploadCertificatesBatch(anyList(), eq("dc=ldap,dc=smartcoreinc,dc=com")))
            .thenReturn(batchResult);

        // When
        UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isPartialSuccess()).isTrue();
        assertThat(response.getTotalCount()).isEqualTo(100);
        assertThat(response.getSuccessCount()).isEqualTo(95);
        assertThat(response.getFailureCount()).isEqualTo(5);
        assertThat(response.getSuccessRate()).isEqualTo(95.0);

        log.info("✅ Large batch upload (100 certificates) test passed");
    }

    // ========== Error Handling Tests ==========

    @Test
    @DisplayName("LDAP 업로드 중 예외 처리")
    void testLdapUploadException() {
        // Given
        UploadToLdapCommand command = UploadToLdapCommand.builder()
            .uploadId(uploadId)
            .baseDn(DEFAULT_BASE_DN)
            .certificateIds(List.of(certificateId1))
            .isBatch(false)
            .build();

        when(certificateRepository.findById(CertificateId.of(certificateId1)))
            .thenReturn(Optional.of(certificate1));

        when(ldapUploadService.uploadCertificate(certificate1, "dc=ldap,dc=smartcoreinc,dc=com"))
            .thenThrow(new RuntimeException("LDAP server is unavailable"));

        // When
        UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("LDAP server is unavailable");

        log.info("✅ LDAP upload exception handling test passed");
    }

    @Test
    @DisplayName("Repository 조회 중 예외 처리")
    void testRepositoryException() {
        // Given
        UploadToLdapCommand command = UploadToLdapCommand.builder()
            .uploadId(uploadId)
            .baseDn(DEFAULT_BASE_DN)
            .certificateIds(List.of(certificateId1))
            .isBatch(false)
            .build();

        when(certificateRepository.findById(CertificateId.of(certificateId1)))
            .thenThrow(new RuntimeException("Database connection error"));

        // When
        UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Database connection error");

        log.info("✅ Repository exception handling test passed");
    }

    // ========== Success Rate Calculation Tests ==========

    @Test
    @DisplayName("성공률 계산 검증 (50%)")
    void testSuccessRateCalculation() {
        // Given
        UploadToLdapCommand command = UploadToLdapCommand.builder()
            .uploadId(uploadId)
            .baseDn(DEFAULT_BASE_DN)
            .certificateIds(List.of(certificateId1, certificateId2))
            .isBatch(true)
            .build();

        when(certificateRepository.findById(CertificateId.of(certificateId1)))
            .thenReturn(Optional.of(certificate1));
        when(certificateRepository.findById(CertificateId.of(certificateId2)))
            .thenReturn(Optional.of(certificate2));

        // Mock batch result with 1 success, 1 failure
        BatchUploadResult batchResult = new BatchUploadResult(2);
        batchResult.addSuccess(certificate1, "cn=Test-Certificate-1,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
        batchResult.addFailure(certificate2, "Error 1");

        when(ldapUploadService.uploadCertificatesBatch(anyList(), eq("dc=ldap,dc=smartcoreinc,dc=com")))
            .thenReturn(batchResult);

        // When
        UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

        // Then
        assertThat(response.getSuccessRate()).isCloseTo(50.0, within(0.1));

        log.info("✅ Success rate calculation test passed");
    }

    // ========== Helper Methods ==========

    private Certificate createTestCertificate(UUID certificateId, String commonName) {
        // Create X509Data
        X509Data x509Data = X509Data.createForTest(
            new byte[]{0x30, (byte) 0x82, 0x01, 0x00},  // Mock DER data
            "123456789ABCDEF0",
            false
        );

        return Certificate.createForTest(
            CertificateId.of(certificateId),
            CertificateType.DSC,  // Document Signing Certificate
            SubjectInfo.of("CN=" + commonName + ",C=KR", "KR", null, null, commonName),
            IssuerInfo.of("CN=CSCA-Test,C=KR", "KR", null, null, "CSCA-Test", true),
            ValidityPeriod.of(
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(365)
            ),
            x509Data,
            CertificateStatus.VALID  // VALID status for upload
        );
    }
}
