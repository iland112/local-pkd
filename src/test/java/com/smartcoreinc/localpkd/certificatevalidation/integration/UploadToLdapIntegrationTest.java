package com.smartcoreinc.localpkd.certificatevalidation.integration;

import com.smartcoreinc.localpkd.certificatevalidation.application.event.UploadToLdapEventHandler;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.UploadToLdapResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.UploadToLdapUseCase;
import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CertificatesValidatedEvent;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.*;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.certificatevalidation.domain.service.LdapUploadService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * LDAP 업로드 End-to-End 통합 테스트
 *
 * <p><b>테스트 범위</b>:</p>
 * <ol>
 *   <li>CertificatesValidatedEvent 발행</li>
 *   <li>UploadToLdapEventHandler 트리거 (동기/비동기)</li>
 *   <li>UploadToLdapUseCase 실행</li>
 *   <li>인증서 조회 (Repository)</li>
 *   <li>LDAP 업로드 (LdapUploadService Mock)</li>
 *   <li>트랜잭션 처리</li>
 *   <li>이벤트 전파</li>
 * </ol>
 *
 * <p><b>테스트 시나리오</b>:</p>
 * <ul>
 *   <li>단일 인증서 업로드 성공</li>
 *   <li>배치 인증서 업로드 성공 (여러 개)</li>
 *   <li>부분 성공 (일부 실패)</li>
 *   <li>인증서 없음</li>
 *   <li>LDAP 연결 오류</li>
 *   <li>Use Case 예외 처리</li>
 * </ul>
 *
 * <p><b>Spring Context</b>:</p>
 * <ul>
 *   <li>UploadToLdapEventHandler (실제 Bean)</li>
 *   <li>UploadToLdapUseCase (실제 Bean)</li>
 *   <li>CertificateRepository (실제 JPA Repository)</li>
 *   <li>LdapUploadService (Mock Bean)</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-30 (Phase 17 Task 1.8)
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("LDAP 업로드 E2E 통합 테스트")
public class UploadToLdapIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private UploadToLdapEventHandler eventHandler;

    @Autowired
    private UploadToLdapUseCase uploadToLdapUseCase;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private LdapUploadService ldapUploadService;

    private UUID uploadId;
    private UUID certificateId1;
    private UUID certificateId2;
    private Certificate certificate1;
    private Certificate certificate2;

    @BeforeEach
    void setUp() {
        uploadId = UUID.randomUUID();
        certificateId1 = UUID.randomUUID();
        certificateId2 = UUID.randomUUID();

        certificate1 = createTestCertificate(certificateId1, "CN=Test-Cert-1");
        certificate2 = createTestCertificate(certificateId2, "CN=Test-Cert-2");
    }

    // ========== E2E Integration Tests ==========

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)  // Run outside transaction so event fires immediately
    @DisplayName("E2E: CertificatesValidatedEvent → EventHandler → UseCase → LDAP 업로드 (단일 인증서)")
    void e2e_SingleCertificateUpload_Success() throws InterruptedException {
        log.info("=== E2E Integration Test: Single Certificate Upload ===");

        // Given - Execute in transaction and commit
        transactionTemplate.execute(status -> {
            certificateRepository.save(certificate1);

            CertificatesValidatedEvent event = new CertificatesValidatedEvent(
                uploadId, 1, 0, 0, 0, LocalDateTime.now()
            );

            LdapUploadService.UploadResult uploadResult = LdapUploadService.UploadResult.success(
                certificateId1.toString(),
                "cn=Test-Cert-1,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"
            );
            when(ldapUploadService.uploadCertificate(any(Certificate.class), anyString()))
                .thenReturn(uploadResult);

            // When
            log.info("Step 1: Publishing CertificatesValidatedEvent...");
            eventPublisher.publishEvent(event);

            return null;  // Transaction will commit here, triggering AFTER_COMMIT listener
        });

        // Wait for async event handler to complete (AFTER_COMMIT listener)
        Thread.sleep(500);

        // Then
        log.info("Step 2: Verifying LDAP upload was called...");
        verify(ldapUploadService, times(1))
            .uploadCertificate(any(Certificate.class), eq("dc=ldap,dc=smartcoreinc,dc=com"));

        log.info("✅ E2E Single Certificate Upload Test Passed");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("E2E: 배치 인증서 업로드 성공 (2개)")
    void e2e_BatchCertificatesUpload_Success() throws InterruptedException {
        log.info("=== E2E Integration Test: Batch Certificates Upload ===");

        // Given - Execute in transaction and commit
        transactionTemplate.execute(status -> {
            certificateRepository.save(certificate1);
            certificateRepository.save(certificate2);

            CertificatesValidatedEvent event = new CertificatesValidatedEvent(
                uploadId, 2, 0, 0, 0, LocalDateTime.now()
            );

            LdapUploadService.BatchUploadResult batchResult = new LdapUploadService.BatchUploadResult(2);
            batchResult.addSuccess(certificate1, "cn=Test-Cert-1,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
            batchResult.addSuccess(certificate2, "cn=Test-Cert-2,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

            when(ldapUploadService.uploadCertificatesBatch(anyList(), anyString()))
                .thenReturn(batchResult);

            // When
            log.info("Step 1: Publishing CertificatesValidatedEvent...");
            eventPublisher.publishEvent(event);

            return null;  // Transaction will commit here
        });

        // Wait for async event handler
        Thread.sleep(500);

        // Then
        log.info("Step 2: Verifying batch LDAP upload was called...");
        verify(ldapUploadService, times(1))
            .uploadCertificatesBatch(anyList(), eq("dc=ldap,dc=smartcoreinc,dc=com"));

        log.info("✅ E2E Batch Certificates Upload Test Passed");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("E2E: 부분 성공 (1개 성공, 1개 실패)")
    void e2e_PartialSuccess() throws InterruptedException {
        log.info("=== E2E Integration Test: Partial Success ===");

        // Given - Execute in transaction and commit
        transactionTemplate.execute(status -> {
            certificateRepository.save(certificate1);
            certificateRepository.save(certificate2);

            CertificatesValidatedEvent event = new CertificatesValidatedEvent(
                uploadId, 2, 0, 0, 0, LocalDateTime.now()
            );

            LdapUploadService.BatchUploadResult batchResult = new LdapUploadService.BatchUploadResult(2);
            batchResult.addSuccess(certificate1, "cn=Test-Cert-1,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
            batchResult.addFailure(certificate2, "LDAP entry already exists");

            when(ldapUploadService.uploadCertificatesBatch(anyList(), anyString()))
                .thenReturn(batchResult);

            // When
            log.info("Step 1: Publishing CertificatesValidatedEvent...");
            eventPublisher.publishEvent(event);

            return null;  // Transaction will commit here
        });

        // Wait for async event handler
        Thread.sleep(500);

        // Then
        log.info("Step 2: Verifying batch LDAP upload was called...");
        verify(ldapUploadService, times(1))
            .uploadCertificatesBatch(anyList(), eq("dc=ldap,dc=smartcoreinc,dc=com"));

        log.info("✅ E2E Partial Success Test Passed");
    }

    @Test
    @DisplayName("E2E: 인증서 없음 - LDAP 업로드 미실행")
    void e2e_NoCertificates_NoUpload() {
        log.info("=== E2E Integration Test: No Certificates ===");

        // Given
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId, 0, 0, 0, 0, LocalDateTime.now()
        );

        // When
        log.info("Step 1: Publishing CertificatesValidatedEvent with 0 certificates...");
        eventPublisher.publishEvent(event);

        // Then
        log.info("Step 2: Verifying LDAP upload was NOT called...");
        verify(ldapUploadService, never())
            .uploadCertificate(any(Certificate.class), anyString());
        verify(ldapUploadService, never())
            .uploadCertificatesBatch(anyList(), anyString());

        log.info("✅ E2E No Certificates Test Passed");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("E2E: LDAP 연결 오류")
    void e2e_LdapConnectionError() throws InterruptedException {
        log.info("=== E2E Integration Test: LDAP Connection Error ===");

        // Given - Execute in transaction and commit
        transactionTemplate.execute(status -> {
            certificateRepository.save(certificate1);

            CertificatesValidatedEvent event = new CertificatesValidatedEvent(
                uploadId, 1, 0, 0, 0, LocalDateTime.now()
            );

            LdapUploadService.UploadResult uploadResult = LdapUploadService.UploadResult.failure(
                certificateId1.toString(),
                "LDAP Server unavailable"
            );
            when(ldapUploadService.uploadCertificate(any(Certificate.class), anyString()))
                .thenReturn(uploadResult);

            // When
            log.info("Step 1: Publishing CertificatesValidatedEvent...");
            eventPublisher.publishEvent(event);

            return null;  // Transaction will commit here
        });

        // Wait for async event handler
        Thread.sleep(500);

        // Then
        log.info("Step 2: Verifying LDAP upload was called (but failed)...");
        verify(ldapUploadService, times(1))
            .uploadCertificate(any(Certificate.class), eq("dc=ldap,dc=smartcoreinc,dc=com"));

        log.info("✅ E2E LDAP Connection Error Test Passed");
    }

    @Test
    @DisplayName("E2E: EventHandler 예외 처리 (Repository 오류)")
    void e2e_EventHandlerException_RepositoryError() {
        log.info("=== E2E Integration Test: EventHandler Exception ===");

        // Given
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            UUID.randomUUID(),  // 존재하지 않는 uploadId
            1, 0, 0, 0, LocalDateTime.now()
        );

        // When
        log.info("Step 1: Publishing CertificatesValidatedEvent with non-existent uploadId...");
        assertThatNoException().isThrownBy(() -> eventPublisher.publishEvent(event));

        // Then
        log.info("Step 2: Verifying LDAP upload was NOT called (no certificates found)...");
        verify(ldapUploadService, never())
            .uploadCertificate(any(Certificate.class), anyString());

        log.info("✅ E2E EventHandler Exception Test Passed");
    }

    // ========== Component-Level Integration Tests ==========

    @Test
    @DisplayName("UseCase 직접 호출: 단일 인증서 업로드")
    void directUseCase_SingleCertificateUpload() {
        log.info("=== Direct UseCase Integration Test: Single Certificate ===");

        // Given
        Certificate saved = certificateRepository.save(certificate1);

        com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand command =
            com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand.builder()
                .uploadId(uploadId)
                .certificateIds(List.of(saved.getId().getId()))
                .baseDn("dc=ldap,dc=smartcoreinc,dc=com")
                .isBatch(false)
                .build();

        LdapUploadService.UploadResult uploadResult = LdapUploadService.UploadResult.success(
            certificateId1.toString(),
            "cn=Test-Cert-1,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"
        );
        when(ldapUploadService.uploadCertificate(any(Certificate.class), anyString()))
            .thenReturn(uploadResult);

        // When
        log.info("Step 1: Executing UploadToLdapUseCase directly...");
        UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

        // Then
        log.info("Step 2: Verifying response...");
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getUploadedDns()).hasSize(1);

        verify(ldapUploadService, times(1))
            .uploadCertificate(any(Certificate.class), eq("dc=ldap,dc=smartcoreinc,dc=com"));

        log.info("✅ Direct UseCase Single Certificate Test Passed");
    }

    @Test
    @DisplayName("UseCase 직접 호출: 배치 업로드")
    void directUseCase_BatchUpload() {
        log.info("=== Direct UseCase Integration Test: Batch Upload ===");

        // Given
        Certificate saved1 = certificateRepository.save(certificate1);
        Certificate saved2 = certificateRepository.save(certificate2);

        com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand command =
            com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand.builder()
                .uploadId(uploadId)
                .certificateIds(List.of(saved1.getId().getId(), saved2.getId().getId()))
                .baseDn("dc=ldap,dc=smartcoreinc,dc=com")
                .isBatch(true)
                .build();

        LdapUploadService.BatchUploadResult batchResult = new LdapUploadService.BatchUploadResult(2);
        batchResult.addSuccess(saved1, "cn=Test-Cert-1,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
        batchResult.addSuccess(saved2, "cn=Test-Cert-2,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        when(ldapUploadService.uploadCertificatesBatch(anyList(), anyString()))
            .thenReturn(batchResult);

        // When
        log.info("Step 1: Executing UploadToLdapUseCase with batch...");
        UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

        // Then
        log.info("Step 2: Verifying response...");
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getSuccessCount()).isEqualTo(2);
        assertThat(response.getTotalCount()).isEqualTo(2);

        verify(ldapUploadService, times(1))
            .uploadCertificatesBatch(anyList(), eq("dc=ldap,dc=smartcoreinc,dc=com"));

        log.info("✅ Direct UseCase Batch Upload Test Passed");
    }

    @Test
    @DisplayName("Repository 통합: uploadId로 인증서 조회")
    void repository_FindByUploadId() {
        log.info("=== Repository Integration Test: FindByUploadId ===");

        // Given
        certificateRepository.save(certificate1);
        certificateRepository.save(certificate2);

        // When
        log.info("Step 1: Querying certificates by uploadId...");
        List<Certificate> certificates = certificateRepository.findByUploadId(uploadId);

        // Then
        log.info("Step 2: Verifying query results...");
        assertThat(certificates).hasSize(2);
        assertThat(certificates).extracting(cert -> cert.getId().getId())
            .containsExactlyInAnyOrder(certificateId1, certificateId2);

        log.info("✅ Repository FindByUploadId Test Passed");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("트랜잭션 전파: EventHandler → UseCase → Repository")
    void transactionPropagation_EventToRepository() throws InterruptedException {
        log.info("=== Transaction Propagation Integration Test ===");

        // Given - Execute in transaction and commit
        transactionTemplate.execute(status -> {
            certificateRepository.save(certificate1);

            CertificatesValidatedEvent event = new CertificatesValidatedEvent(
                uploadId, 1, 0, 0, 0, LocalDateTime.now()
            );

            LdapUploadService.UploadResult uploadResult = LdapUploadService.UploadResult.success(
                certificateId1.toString(),
                "cn=Test-Cert-1,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"
            );
            when(ldapUploadService.uploadCertificate(any(Certificate.class), anyString()))
                .thenReturn(uploadResult);

            // When
            log.info("Step 1: Publishing event within transaction...");
            eventPublisher.publishEvent(event);

            return null;  // Transaction will commit here
        });

        // Wait for async event handler
        Thread.sleep(500);

        // Then
        log.info("Step 2: Verifying certificate still exists after transaction...");
        Optional<Certificate> queriedCert = certificateRepository.findById(CertificateId.of(certificateId1));
        assertThat(queriedCert).isPresent();

        verify(ldapUploadService, times(1))
            .uploadCertificate(any(Certificate.class), anyString());

        log.info("✅ Transaction Propagation Test Passed");
    }

    // ========== Helper Methods ==========

    private Certificate createTestCertificate(UUID certificateId, String commonName) {
        X509Data x509Data = X509Data.createForTest(
            new byte[]{0x30, (byte) 0x82, 0x01, 0x00},
            "123456789ABCDEF0",
            false
        );

        // Certificate.create()는 uploadId를 첫 번째 파라미터로 받습니다
        Certificate cert = Certificate.create(
            uploadId,  // uploadId (Phase 17)
            x509Data,
            SubjectInfo.of("CN=" + commonName + ",C=KR", "KR", null, null, commonName),
            IssuerInfo.of("CN=CSCA-Test,C=KR", "KR", null, null, "CSCA-Test", true),
            ValidityPeriod.of(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(365)),
            CertificateType.DSC,
            "SHA256WithRSA"
        );

        // 테스트용 ID 설정 (리플렉션 사용)
        try {
            java.lang.reflect.Field idField = Certificate.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(cert, CertificateId.of(certificateId));
        } catch (Exception e) {
            throw new RuntimeException("Failed to set certificate ID for test", e);
        }

        return cert;
    }
}
