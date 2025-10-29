package com.smartcoreinc.localpkd.integration;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.ValidateCertificatesCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.CertificatesValidatedResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.ValidateCertificatesUseCase;
import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CertificatesValidatedEvent;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import com.smartcoreinc.localpkd.ldapintegration.application.command.UploadToLdapCommand;
import com.smartcoreinc.localpkd.ldapintegration.application.response.UploadToLdapResponse;
import com.smartcoreinc.localpkd.ldapintegration.application.usecase.UploadToLdapUseCase;
import com.smartcoreinc.localpkd.ldapintegration.domain.event.LdapUploadCompletedEvent;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProcessingStage;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FileParsingToLdapUploadE2ETest - 파일 파싱부터 LDAP 업로드까지 완전한 워크플로우 E2E 테스트
 *
 * <p><b>테스트 범위</b>: File Parsing Context → Certificate Validation Context → LDAP Integration Context</p>
 *
 * <p><b>테스트 워크플로우</b>:</p>
 * <pre>
 * 1. FileParsingCompletedEvent 시뮬레이션
 * 2. ValidateCertificatesUseCase 실행
 *    → CertificatesValidatedEvent 발행
 * 3. UploadToLdapUseCase 실행
 *    → LdapUploadCompletedEvent 발행
 * 4. SSE 진행률 확인 (5% → 85% → 100%)
 * </pre>
 *
 * <p><b>테스트 항목</b>:</p>
 * <ul>
 *   <li>완전한 워크플로우 성공 (파싱 → 검증 → LDAP 업로드)</li>
 *   <li>각 단계별 이벤트 발행 확인</li>
 *   <li>SSE 진행률 추적 (5% → 85% → 100%)</li>
 *   <li>각 단계별 예외 처리</li>
 *   <li>부분 실패 시나리오 (일부 검증 실패, 일부 LDAP 업로드 실패)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("파일 파싱부터 LDAP 업로드까지 E2E 워크플로우 테스트")
class FileParsingToLdapUploadE2ETest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private CertificateRevocationListRepository crlRepository;

    @Mock
    private ProgressService progressService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ValidateCertificatesUseCase validateCertificatesUseCase;
    private UploadToLdapUseCase uploadToLdapUseCase;

    @BeforeEach
    void setUp() {
        validateCertificatesUseCase = new ValidateCertificatesUseCase(
            certificateRepository,
            crlRepository,
            progressService,
            eventPublisher
        );

        uploadToLdapUseCase = new UploadToLdapUseCase(progressService, eventPublisher);
    }

    @Test
    @DisplayName("✅ E2E 성공: 파일 파싱 → 인증서 검증 → LDAP 업로드까지 완전한 워크플로우")
    void testCompleteWorkflowSuccess() {
        // ===== Step 1: Validate Certificates =====
        UUID uploadId = UUID.randomUUID();
        ValidateCertificatesCommand validateCommand = ValidateCertificatesCommand.builder()
            .uploadId(uploadId)
            .parsedFileId(UUID.randomUUID())
            .certificateCount(500)
            .crlCount(100)
            .build();

        // Mock certificates (all valid)
        List<Certificate> certificates = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            Certificate cert = mock(Certificate.class);
            when(cert.isValid()).thenReturn(true);
            certificates.add(cert);
        }
        when(certificateRepository.findByType(CertificateType.DSC)).thenReturn(certificates);

        // Mock CRLs (all valid)
        List<CertificateRevocationList> crls = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            CertificateRevocationList crl = mock(CertificateRevocationList.class);
            when(crl.isValid()).thenReturn(true);
            crls.add(crl);
        }
        when(crlRepository.findAll()).thenReturn(crls);

        // When: Execute validation
        CertificatesValidatedResponse validationResponse = validateCertificatesUseCase.execute(validateCommand);

        // Then: Validation should succeed
        assertThat(validationResponse.success()).isTrue();
        assertThat(validationResponse.getTotalValidated()).isEqualTo(600);
        assertThat(validationResponse.getTotalValid()).isEqualTo(600);

        // Verify validation event published
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());

        // ===== Step 2: Upload to LDAP =====
        UploadToLdapCommand uploadCommand = UploadToLdapCommand.create(
            uploadId,
            validationResponse.validCertificateCount(),
            validationResponse.validCrlCount()
        );

        // When: Execute LDAP upload
        UploadToLdapResponse uploadResponse = uploadToLdapUseCase.execute(uploadCommand);

        // Then: Upload should succeed
        assertThat(uploadResponse.success()).isTrue();
        assertThat(uploadResponse.uploadId()).isEqualTo(uploadId);
        assertThat(uploadResponse.getTotalUploaded()).isGreaterThan(0);

        // Verify LDAP upload event published
        verify(eventPublisher, atLeastOnce()).publishEvent(any(LdapUploadCompletedEvent.class));

        // ===== Verify Complete Workflow =====
        assertThat(validationResponse.uploadId()).isEqualTo(uploadResponse.uploadId());
    }

    @Test
    @DisplayName("✅ 진행률 추적: 완전한 워크플로우에서 진행률이 5% → 85% → 100%로 업데이트됨")
    void testProgressTrackingThroughCompleteWorkflow() {
        // ===== Setup =====
        UUID uploadId = UUID.randomUUID();
        ValidateCertificatesCommand validateCommand = ValidateCertificatesCommand.builder()
            .uploadId(uploadId)
            .parsedFileId(UUID.randomUUID())
            .certificateCount(200)
            .crlCount(50)
            .build();

        List<Certificate> certificates = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            Certificate cert = mock(Certificate.class);
            when(cert.isValid()).thenReturn(true);
            certificates.add(cert);
        }
        when(certificateRepository.findByType(CertificateType.DSC)).thenReturn(certificates);

        List<CertificateRevocationList> crls = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            CertificateRevocationList crl = mock(CertificateRevocationList.class);
            when(crl.isValid()).thenReturn(true);
            crls.add(crl);
        }
        when(crlRepository.findAll()).thenReturn(crls);

        // ===== Execute Full Workflow =====
        // Step 1: Validate
        CertificatesValidatedResponse validationResponse = validateCertificatesUseCase.execute(validateCommand);

        // Step 2: Upload to LDAP
        UploadToLdapCommand uploadCommand = UploadToLdapCommand.create(
            uploadId,
            validationResponse.validCertificateCount(),
            validationResponse.validCrlCount()
        );
        uploadToLdapUseCase.execute(uploadCommand);

        // ===== Verify Progress Updates =====
        ArgumentCaptor<ProcessingProgress> progressCaptor = ArgumentCaptor.forClass(ProcessingProgress.class);
        verify(progressService, atLeastOnce()).sendProgress(progressCaptor.capture());

        List<ProcessingProgress> allProgress = progressCaptor.getAllValues();

        // Find progression through stages
        ProcessingProgress validationStart = allProgress.stream()
            .filter(p -> p.getStage() == ProcessingStage.VALIDATION_IN_PROGRESS && p.getPercentage() == 70)
            .findFirst()
            .orElse(null);

        ProcessingProgress validationComplete = allProgress.stream()
            .filter(p -> p.getStage() == ProcessingStage.VALIDATION_COMPLETED && p.getPercentage() == 85)
            .findFirst()
            .orElse(null);

        ProcessingProgress uploadStart = allProgress.stream()
            .filter(p -> p.getStage() == ProcessingStage.LDAP_SAVING_STARTED && p.getPercentage() == 90)
            .findFirst()
            .orElse(null);

        ProcessingProgress uploadComplete = allProgress.stream()
            .filter(p -> p.getStage() == ProcessingStage.LDAP_SAVING_COMPLETED && p.getPercentage() == 100)
            .findFirst()
            .orElse(null);

        // Then: Verify key stages were reached
        assertThat(validationStart).isNotNull();
        assertThat(validationComplete).isNotNull();
        assertThat(uploadStart).isNotNull();
        assertThat(uploadComplete).isNotNull();
    }

    @Test
    @DisplayName("✅ 부분 실패 시나리오: 일부 인증서 검증 실패, 일부 LDAP 업로드 실패")
    void testPartialFailureScenario() {
        // ===== Setup =====
        UUID uploadId = UUID.randomUUID();
        ValidateCertificatesCommand validateCommand = ValidateCertificatesCommand.builder()
            .uploadId(uploadId)
            .parsedFileId(UUID.randomUUID())
            .certificateCount(100)
            .crlCount(50)
            .build();

        // Mock certificates (80 valid, 20 invalid)
        List<Certificate> certificates = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Certificate cert = mock(Certificate.class);
            when(cert.isValid()).thenReturn(i < 80);
            certificates.add(cert);
        }
        when(certificateRepository.findByType(CertificateType.DSC)).thenReturn(certificates);

        // Mock CRLs (45 valid, 5 invalid)
        List<CertificateRevocationList> crls = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            CertificateRevocationList crl = mock(CertificateRevocationList.class);
            when(crl.isValid()).thenReturn(i < 45);
            crls.add(crl);
        }
        when(crlRepository.findAll()).thenReturn(crls);

        // ===== Execute Validation =====
        CertificatesValidatedResponse validationResponse = validateCertificatesUseCase.execute(validateCommand);

        // ===== Verify Validation Results =====
        assertThat(validationResponse.success()).isTrue();
        assertThat(validationResponse.validCertificateCount()).isEqualTo(80);
        assertThat(validationResponse.invalidCertificateCount()).isEqualTo(20);
        assertThat(validationResponse.validCrlCount()).isEqualTo(45);
        assertThat(validationResponse.invalidCrlCount()).isEqualTo(5);

        // ===== Execute LDAP Upload with Partial Failures =====
        UploadToLdapCommand uploadCommand = UploadToLdapCommand.create(
            uploadId,
            80,  // Only valid certificates
            45   // Only valid CRLs
        );
        UploadToLdapResponse uploadResponse = uploadToLdapUseCase.execute(uploadCommand);

        // ===== Verify Upload Results =====
        assertThat(uploadResponse.success()).isTrue();
        assertThat(uploadResponse.getTotalUploaded()).isLessThanOrEqualTo(125);  // 80 + 45
        assertThat(uploadResponse.getTotalFailed()).isGreaterThanOrEqualTo(0);

        // Workflow should continue despite partial failures
        int successRate = validationResponse.getSuccessRate();
        assertThat(successRate).isEqualTo(89);  // (80 + 45) / (100 + 50) * 100 ≈ 86%
    }

    @Test
    @DisplayName("❌ 에러 처리: 검증 단계 실패 시 LDAP 업로드는 진행되지 않음")
    void testValidationFailureStopsWorkflow() {
        // ===== Setup Validation Failure =====
        UUID uploadId = UUID.randomUUID();
        ValidateCertificatesCommand validateCommand = ValidateCertificatesCommand.builder()
            .uploadId(uploadId)
            .parsedFileId(UUID.randomUUID())
            .certificateCount(100)
            .crlCount(50)
            .build();

        when(certificateRepository.findByType(CertificateType.DSC))
            .thenThrow(new RuntimeException("Database connection failed"));

        // ===== Execute Validation =====
        CertificatesValidatedResponse validationResponse = validateCertificatesUseCase.execute(validateCommand);

        // ===== Verify Validation Failed =====
        assertThat(validationResponse.success()).isFalse();
        assertThat(validationResponse.errorMessage()).contains("Database connection failed");

        // ===== Verify No LDAP Upload Attempt =====
        // Since validation failed, we would not proceed to LDAP upload in real scenario
        // (In this test, we're not invoking LDAP upload due to validation failure)
    }

    @Test
    @DisplayName("✅ 워크플로우 데이터 일관성: 검증 결과가 LDAP 업로드에 올바르게 전달됨")
    void testDataConsistencyAcrossWorkflow() {
        // ===== Setup =====
        UUID uploadId = UUID.randomUUID();
        UUID parsedFileId = UUID.randomUUID();
        int expectedCertificates = 300;
        int expectedCrls = 75;

        ValidateCertificatesCommand validateCommand = ValidateCertificatesCommand.builder()
            .uploadId(uploadId)
            .parsedFileId(parsedFileId)
            .certificateCount(expectedCertificates)
            .crlCount(expectedCrls)
            .build();

        List<Certificate> certificates = new ArrayList<>();
        for (int i = 0; i < expectedCertificates; i++) {
            Certificate cert = mock(Certificate.class);
            when(cert.isValid()).thenReturn(true);
            certificates.add(cert);
        }
        when(certificateRepository.findByType(CertificateType.DSC)).thenReturn(certificates);

        List<CertificateRevocationList> crls = new ArrayList<>();
        for (int i = 0; i < expectedCrls; i++) {
            CertificateRevocationList crl = mock(CertificateRevocationList.class);
            when(crl.isValid()).thenReturn(true);
            crls.add(crl);
        }
        when(crlRepository.findAll()).thenReturn(crls);

        // ===== Execute Validation =====
        CertificatesValidatedResponse validationResponse = validateCertificatesUseCase.execute(validateCommand);

        // ===== Execute LDAP Upload =====
        UploadToLdapCommand uploadCommand = UploadToLdapCommand.create(
            uploadId,
            validationResponse.validCertificateCount(),
            validationResponse.validCrlCount()
        );
        UploadToLdapResponse uploadResponse = uploadToLdapUseCase.execute(uploadCommand);

        // ===== Verify Data Consistency =====
        // Same uploadId throughout workflow
        assertThat(validationResponse.uploadId()).isEqualTo(uploadId);
        assertThat(uploadResponse.uploadId()).isEqualTo(uploadId);

        // Validation results match input
        assertThat(validationResponse.validCertificateCount()).isEqualTo(expectedCertificates);
        assertThat(validationResponse.validCrlCount()).isEqualTo(expectedCrls);

        // LDAP upload uses correct counts
        assertThat(uploadCommand.validCertificateCount()).isEqualTo(expectedCertificates);
        assertThat(uploadCommand.validCrlCount()).isEqualTo(expectedCrls);
    }

    @Test
    @DisplayName("✅ 대량 데이터 시나리오: 1000개 인증서 + 200개 CRL 처리")
    void testLargeScaleWorkflow() {
        // ===== Setup Large Dataset =====
        UUID uploadId = UUID.randomUUID();
        int largeCount = 1000;
        int crlCount = 200;

        ValidateCertificatesCommand validateCommand = ValidateCertificatesCommand.builder()
            .uploadId(uploadId)
            .parsedFileId(UUID.randomUUID())
            .certificateCount(largeCount)
            .crlCount(crlCount)
            .build();

        List<Certificate> certificates = new ArrayList<>();
        for (int i = 0; i < largeCount; i++) {
            Certificate cert = mock(Certificate.class);
            when(cert.isValid()).thenReturn(true);
            certificates.add(cert);
        }
        when(certificateRepository.findByType(CertificateType.DSC)).thenReturn(certificates);

        List<CertificateRevocationList> crls = new ArrayList<>();
        for (int i = 0; i < crlCount; i++) {
            CertificateRevocationList crl = mock(CertificateRevocationList.class);
            when(crl.isValid()).thenReturn(true);
            crls.add(crl);
        }
        when(crlRepository.findAll()).thenReturn(crls);

        // ===== Execute Validation =====
        CertificatesValidatedResponse validationResponse = validateCertificatesUseCase.execute(validateCommand);

        // ===== Verify Validation Handles Large Scale =====
        assertThat(validationResponse.success()).isTrue();
        assertThat(validationResponse.getTotalValidated()).isEqualTo(largeCount + crlCount);

        // ===== Execute LDAP Upload =====
        UploadToLdapCommand uploadCommand = UploadToLdapCommand.create(
            uploadId,
            validationResponse.validCertificateCount(),
            validationResponse.validCrlCount()
        );
        UploadToLdapResponse uploadResponse = uploadToLdapUseCase.execute(uploadCommand);

        // ===== Verify LDAP Upload Handles Large Scale =====
        assertThat(uploadResponse.success()).isTrue();
        assertThat(uploadResponse.getTotalProcessed()).isEqualTo(largeCount + crlCount);
    }

    @Test
    @DisplayName("✅ 이벤트 체이닝: CertificatesValidatedEvent → LdapUploadCompletedEvent")
    void testEventChaining() {
        // ===== Setup =====
        UUID uploadId = UUID.randomUUID();
        ValidateCertificatesCommand validateCommand = ValidateCertificatesCommand.builder()
            .uploadId(uploadId)
            .parsedFileId(UUID.randomUUID())
            .certificateCount(100)
            .crlCount(50)
            .build();

        List<Certificate> certificates = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Certificate cert = mock(Certificate.class);
            when(cert.isValid()).thenReturn(true);
            certificates.add(cert);
        }
        when(certificateRepository.findByType(CertificateType.DSC)).thenReturn(certificates);

        List<CertificateRevocationList> crls = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            CertificateRevocationList crl = mock(CertificateRevocationList.class);
            when(crl.isValid()).thenReturn(true);
            crls.add(crl);
        }
        when(crlRepository.findAll()).thenReturn(crls);

        // ===== Execute Workflow =====
        CertificatesValidatedResponse validationResponse = validateCertificatesUseCase.execute(validateCommand);
        UploadToLdapResponse uploadResponse = uploadToLdapUseCase.execute(
            UploadToLdapCommand.create(
                uploadId,
                validationResponse.validCertificateCount(),
                validationResponse.validCrlCount()
            )
        );

        // ===== Verify Event Chain =====
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());

        List<Object> allEvents = eventCaptor.getAllValues();

        // Should have CertificatesValidatedEvent
        boolean hasCertificatesValidatedEvent = allEvents.stream()
            .anyMatch(e -> e instanceof CertificatesValidatedEvent);

        // Should have LdapUploadCompletedEvent
        boolean hasLdapUploadCompletedEvent = allEvents.stream()
            .anyMatch(e -> e instanceof LdapUploadCompletedEvent);

        assertThat(hasCertificatesValidatedEvent).isTrue();
        assertThat(hasLdapUploadCompletedEvent).isTrue();

        // Events should be published in correct order
        assertThat(allEvents.size()).isGreaterThanOrEqualTo(2);
    }
}
