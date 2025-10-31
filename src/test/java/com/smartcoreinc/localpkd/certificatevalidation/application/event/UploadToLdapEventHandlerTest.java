package com.smartcoreinc.localpkd.certificatevalidation.application.event;

import com.smartcoreinc.localpkd.certificatevalidation.application.response.UploadToLdapResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.UploadToLdapUseCase;
import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CertificatesValidatedEvent;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateStatus;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.IssuerInfo;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.SubjectInfo;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.ValidityPeriod;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.X509Data;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UploadToLdapEventHandlerTest - LDAP 업로드 이벤트 핸들러 단위 테스트
 *
 * <p><b>테스트 범위</b>:
 * <ul>
 *   <li>이벤트 리스닝 및 처리</li>
 *   <li>인증서 조회 및 필터링</li>
 *   <li>Use Case 호출 및 결과 처리</li>
 *   <li>에러 처리 및 로깅</li>
 *   <li>비동기 처리</li>
 * </ul>
 * </p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-30 (Phase 17 Task 1.7)
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("UploadToLdapEventHandler - 이벤트 핸들러 테스트")
public class UploadToLdapEventHandlerTest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private UploadToLdapUseCase uploadToLdapUseCase;

    @InjectMocks
    private UploadToLdapEventHandler eventHandler;

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

        // Set defaultBaseDn field using reflection
        ReflectionTestUtils.setField(eventHandler, "defaultBaseDn", "dc=ldap,dc=smartcoreinc,dc=com");
    }

    // ========== Event Handling Tests ==========

    @Test
    @DisplayName("CertificatesValidatedEvent 처리 - 단일 인증서 성공")
    void testHandleCertificatesValidated_SingleCertificateSuccess() {
        // Given
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId, 1, 0, 0, 0, LocalDateTime.now()
        );

        List<Certificate> certificates = List.of(certificate1);
        when(certificateRepository.findByUploadId(uploadId))
            .thenReturn(certificates);

        UploadToLdapResponse response = UploadToLdapResponse.success(
            uploadId, 1,
            List.of("cn=Test-Cert-1,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com")
        );
        when(uploadToLdapUseCase.execute(any()))
            .thenReturn(response);

        // When
        eventHandler.handleCertificatesValidated(event);

        // Then
        verify(certificateRepository).findByUploadId(uploadId);
        verify(uploadToLdapUseCase).execute(argThat(cmd ->
            cmd.getUploadId().equals(uploadId) &&
            cmd.getCertificateIds().size() == 1 &&
            !cmd.isBatch()
        ));

        log.info("✅ Single certificate success test passed");
    }

    @Test
    @DisplayName("CertificatesValidatedEvent 처리 - 배치 인증서 성공")
    void testHandleCertificatesValidated_BatchCertificateSuccess() {
        // Given
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId, 2, 0, 0, 0, LocalDateTime.now()
        );

        List<Certificate> certificates = List.of(certificate1, certificate2);
        when(certificateRepository.findByUploadId(uploadId))
            .thenReturn(certificates);

        UploadToLdapResponse response = UploadToLdapResponse.success(
            uploadId, 2,
            List.of(
                "cn=Test-Cert-1,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com",
                "cn=Test-Cert-2,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"
            )
        );
        when(uploadToLdapUseCase.execute(any()))
            .thenReturn(response);

        // When
        eventHandler.handleCertificatesValidated(event);

        // Then
        verify(certificateRepository).findByUploadId(uploadId);
        verify(uploadToLdapUseCase).execute(argThat(cmd ->
            cmd.getUploadId().equals(uploadId) &&
            cmd.getCertificateIds().size() == 2 &&
            cmd.isBatch()
        ));

        log.info("✅ Batch certificate success test passed");
    }

    @Test
    @DisplayName("CertificatesValidatedEvent 처리 - 부분 성공")
    void testHandleCertificatesValidated_PartialSuccess() {
        // Given
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId, 2, 0, 0, 0, LocalDateTime.now()
        );

        List<Certificate> certificates = List.of(certificate1, certificate2);
        when(certificateRepository.findByUploadId(uploadId))
            .thenReturn(certificates);

        UploadToLdapResponse response = UploadToLdapResponse.partialSuccess(
            uploadId, 2,
            List.of("cn=Test-Cert-1,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"),
            List.of(certificateId2)
        );
        when(uploadToLdapUseCase.execute(any()))
            .thenReturn(response);

        // When
        eventHandler.handleCertificatesValidated(event);

        // Then
        verify(certificateRepository).findByUploadId(uploadId);
        verify(uploadToLdapUseCase).execute(any());

        log.info("✅ Partial success test passed");
    }

    @Test
    @DisplayName("CertificatesValidatedEvent 처리 - 인증서 목록 없음")
    void testHandleCertificatesValidated_NoCertificates() {
        // Given
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId, 0, 0, 0, 0, LocalDateTime.now()
        );

        when(certificateRepository.findByUploadId(uploadId))
            .thenReturn(new ArrayList<>());

        // When
        eventHandler.handleCertificatesValidated(event);

        // Then
        verify(certificateRepository).findByUploadId(uploadId);
        verify(uploadToLdapUseCase, never()).execute(any());

        log.info("✅ No certificates test passed");
    }

    @Test
    @DisplayName("CertificatesValidatedEvent 처리 - 업로드 실패")
    void testHandleCertificatesValidated_UploadFailure() {
        // Given
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId, 1, 0, 0, 0, LocalDateTime.now()
        );

        List<Certificate> certificates = List.of(certificate1);
        when(certificateRepository.findByUploadId(uploadId))
            .thenReturn(certificates);

        UploadToLdapResponse response = UploadToLdapResponse.failure(
            uploadId, "LDAP Connection Error: Server unavailable"
        );
        when(uploadToLdapUseCase.execute(any()))
            .thenReturn(response);

        // When
        eventHandler.handleCertificatesValidated(event);

        // Then
        verify(uploadToLdapUseCase).execute(any());

        log.info("✅ Upload failure test passed");
    }

    @Test
    @DisplayName("CertificatesValidatedEvent 처리 - Exception 처리")
    void testHandleCertificatesValidated_ExceptionHandling() {
        // Given
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId, 1, 0, 0, 0, LocalDateTime.now()
        );

        when(certificateRepository.findByUploadId(uploadId))
            .thenThrow(new RuntimeException("Database error"));

        // When
        // Exception을 던지지 않고 로깅만 수행
        assertThatNoException().isThrownBy(() ->
            eventHandler.handleCertificatesValidated(event)
        );

        // Then
        verify(uploadToLdapUseCase, never()).execute(any());

        log.info("✅ Exception handling test passed");
    }

    @Test
    @DisplayName("CertificatesValidatedEvent 처리 - Use Case 예외 처리")
    void testHandleCertificatesValidated_UseCaseException() {
        // Given
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId, 1, 0, 0, 0, LocalDateTime.now()
        );

        List<Certificate> certificates = List.of(certificate1);
        when(certificateRepository.findByUploadId(uploadId))
            .thenReturn(certificates);

        when(uploadToLdapUseCase.execute(any()))
            .thenThrow(new RuntimeException("Use case error"));

        // When
        // Exception을 던지지 않고 로깅만 수행
        assertThatNoException().isThrownBy(() ->
            eventHandler.handleCertificatesValidated(event)
        );

        // Then
        verify(uploadToLdapUseCase).execute(any());

        log.info("✅ Use case exception handling test passed");
    }

    // ========== Certificate Filtering Tests ==========

    @Test
    @DisplayName("인증서 ID 추출 - 올바른 개수")
    void testCertificateIdExtraction() {
        // Given
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId, 3, 0, 0, 0, LocalDateTime.now()
        );

        Certificate cert3 = createTestCertificate(UUID.randomUUID(), "CN=Test-Cert-3");
        List<Certificate> certificates = List.of(certificate1, certificate2, cert3);
        when(certificateRepository.findByUploadId(uploadId))
            .thenReturn(certificates);

        UploadToLdapResponse response = UploadToLdapResponse.success(uploadId, 3, new ArrayList<>());
        when(uploadToLdapUseCase.execute(any()))
            .thenReturn(response);

        // When
        eventHandler.handleCertificatesValidated(event);

        // Then
        ArgumentCaptor<com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand> captor =
            ArgumentCaptor.forClass(com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand.class);
        verify(uploadToLdapUseCase).execute(captor.capture());

        com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand command = captor.getValue();
        assertThat(command.getCertificateIds()).hasSize(3);
        assertThat(command.isBatch()).isTrue();

        log.info("✅ Certificate ID extraction test passed");
    }

    // ========== Command Building Tests ==========

    @Test
    @DisplayName("UploadToLdapCommand 빌드 - 단일 인증서는 배치 플래그 false")
    void testCommandBuilding_SingleCertificateIsBatch() {
        // Given
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId, 1, 0, 0, 0, LocalDateTime.now()
        );

        List<Certificate> certificates = List.of(certificate1);
        when(certificateRepository.findByUploadId(uploadId))
            .thenReturn(certificates);

        UploadToLdapResponse response = UploadToLdapResponse.success(uploadId, 1, new ArrayList<>());
        when(uploadToLdapUseCase.execute(any()))
            .thenReturn(response);

        // When
        eventHandler.handleCertificatesValidated(event);

        // Then
        ArgumentCaptor<com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand> captor =
            ArgumentCaptor.forClass(com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand.class);
        verify(uploadToLdapUseCase).execute(captor.capture());

        com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand command = captor.getValue();
        assertThat(command.isBatch()).isFalse();

        log.info("✅ Single certificate batch flag test passed");
    }

    @Test
    @DisplayName("UploadToLdapCommand 빌드 - 여러 인증서는 배치 플래그 true")
    void testCommandBuilding_MultipleCertificatesIsBatch() {
        // Given
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId, 2, 0, 0, 0, LocalDateTime.now()
        );

        List<Certificate> certificates = List.of(certificate1, certificate2);
        when(certificateRepository.findByUploadId(uploadId))
            .thenReturn(certificates);

        UploadToLdapResponse response = UploadToLdapResponse.success(uploadId, 2, new ArrayList<>());
        when(uploadToLdapUseCase.execute(any()))
            .thenReturn(response);

        // When
        eventHandler.handleCertificatesValidated(event);

        // Then
        ArgumentCaptor<com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand> captor =
            ArgumentCaptor.forClass(com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand.class);
        verify(uploadToLdapUseCase).execute(captor.capture());

        com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand command = captor.getValue();
        assertThat(command.isBatch()).isTrue();

        log.info("✅ Multiple certificates batch flag test passed");
    }

    // ========== Async Processing Tests ==========

    // NOTE: handleCertificatesValidatedAsync 메서드는 Phase 17에서 제거되었습니다.
    // 동기 처리 방식으로 전환되었으므로 이 테스트는 더 이상 필요하지 않습니다.

    /*
    @Test
    @DisplayName("비동기 처리 - Exception 처리")
    void testAsyncProcessing_ExceptionHandling() {
        // Given
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId, 1, 0, 0, 0, LocalDateTime.now()
        );

        // When & Then
        assertThatNoException().isThrownBy(() ->
            eventHandler.handleCertificatesValidatedAsync(event)
        );

        log.info("✅ Async processing exception handling test passed");
    }
    */

    // ========== Helper Methods ==========

    private Certificate createTestCertificate(UUID certificateId, String commonName) {
        X509Data x509Data = X509Data.createForTest(
            new byte[]{0x30, (byte) 0x82, 0x01, 0x00},
            "123456789ABCDEF0",
            false
        );

        return Certificate.createForTest(
            CertificateId.of(certificateId),
            CertificateType.DSC,
            SubjectInfo.of("CN=" + commonName + ",C=KR", "KR", null, null, commonName),
            IssuerInfo.of("CN=CSCA-Test,C=KR", "KR", null, null, "CSCA-Test", true),
            ValidityPeriod.of(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(365)),
            x509Data,
            CertificateStatus.VALID
        );
    }
}
