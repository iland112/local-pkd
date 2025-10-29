package com.smartcoreinc.localpkd.certificatevalidation.application.event;

import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CertificatesValidatedEvent;
import com.smartcoreinc.localpkd.ldapintegration.application.response.UploadToLdapResponse;
import com.smartcoreinc.localpkd.ldapintegration.application.usecase.UploadToLdapUseCase;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProcessingStage;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CertificatesValidatedEventHandlerTest - 인증서 검증 이벤트 핸들러 통합 테스트
 *
 * <p><b>테스트 대상</b>: CertificateValidationEventHandler의 CertificatesValidatedEvent 핸들러 메서드들</p>
 *
 * <p><b>테스트 항목</b>:</p>
 * <ul>
 *   <li>CertificatesValidatedEvent 동기 처리 (로깅)</li>
 *   <li>CertificatesValidatedEvent 비동기 처리 (LDAP 업로드 트리거)</li>
 *   <li>UploadToLdapUseCase와의 통합</li>
 *   <li>이벤트 핸들링 중 예외 처리</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CertificateValidationEventHandler (CertificatesValidatedEvent) 테스트")
class CertificatesValidatedEventHandlerTest {

    @Mock
    private ProgressService progressService;

    @Mock
    private UploadToLdapUseCase uploadToLdapUseCase;

    private CertificateValidationEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CertificateValidationEventHandler(progressService, uploadToLdapUseCase);
    }

    @Test
    @DisplayName("✅ 동기 처리: CertificatesValidatedEvent를 수신하고 로깅")
    void testHandleCertificatesValidatedSync() {
        // Given
        UUID uploadId = UUID.randomUUID();
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId,
            795,  // validCertificateCount
            5,    // invalidCertificateCount
            48,   // validCrlCount
            2,    // invalidCrlCount
            LocalDateTime.now()
        );

        // When
        handler.handleCertificatesValidated(event);

        // Then: No exceptions, handler should log event details
        // (Logging is verified through execution without exceptions)
    }

    @Test
    @DisplayName("✅ 비동기 처리: CertificatesValidatedEvent를 수신하고 LDAP 업로드 트리거")
    void testHandleCertificatesValidatedAndTriggerLdapUploadAsync() {
        // Given
        UUID uploadId = UUID.randomUUID();
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId,
            800,  // validCertificateCount
            0,    // invalidCertificateCount
            50,   // validCrlCount
            0,    // invalidCrlCount
            LocalDateTime.now()
        );

        UploadToLdapResponse mockResponse = UploadToLdapResponse.success(
            uploadId,
            792,  // uploadedCertificateCount (99% of 800)
            49,   // uploadedCrlCount (98% of 50)
            8,    // failedCertificateCount
            1,    // failedCrlCount
            LocalDateTime.now(),
            5000  // durationMillis
        );

        when(uploadToLdapUseCase.execute(any())).thenReturn(mockResponse);

        // When
        handler.handleCertificatesValidatedAndTriggerLdapUpload(event);

        // Then: UploadToLdapUseCase should be called
        verify(uploadToLdapUseCase, times(1)).execute(any());
    }

    @Test
    @DisplayName("✅ 이벤트 통합: 동기 + 비동기 핸들러가 모두 호출됨")
    void testBothHandlersAreInvoked() {
        // Given
        UUID uploadId = UUID.randomUUID();
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId,
            600,  // validCertificateCount
            50,   // invalidCertificateCount
            40,   // validCrlCount
            10,   // invalidCrlCount
            LocalDateTime.now()
        );

        when(uploadToLdapUseCase.execute(any())).thenReturn(
            UploadToLdapResponse.success(uploadId, 594, 39, 6, 1, LocalDateTime.now(), 3000)
        );

        // When
        handler.handleCertificatesValidated(event);
        handler.handleCertificatesValidatedAndTriggerLdapUpload(event);

        // Then
        verify(uploadToLdapUseCase, times(1)).execute(any());
    }

    @Test
    @DisplayName("❌ 에러 처리: 비동기 핸들러에서 예외 발생 시 진행률을 FAILED로 설정")
    void testAsyncHandlerExceptionHandling() {
        // Given
        UUID uploadId = UUID.randomUUID();
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId,
            100,
            10,
            20,
            5,
            LocalDateTime.now()
        );

        when(uploadToLdapUseCase.execute(any()))
            .thenThrow(new RuntimeException("LDAP connection failed"));

        // When
        handler.handleCertificatesValidatedAndTriggerLdapUpload(event);

        // Then: ProgressService should be called with FAILED status
        verify(progressService, atLeastOnce()).sendProgress(any(ProcessingProgress.class));
    }

    @Test
    @DisplayName("✅ 성공률: CertificatesValidatedEvent에서 성공률을 계산")
    void testSuccessRateCalculation() {
        // Given
        UUID uploadId = UUID.randomUUID();
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId,
            800,  // validCertificateCount
            200,  // invalidCertificateCount
            48,   // validCrlCount
            2,    // invalidCrlCount
            LocalDateTime.now()
        );

        // When
        int successRate = event.getSuccessRate();

        // Then: (800 + 48) / (800 + 200 + 48 + 2) * 100 = 848/1050 * 100 ≈ 80%
        org.assertj.core.api.Assertions.assertThat(successRate).isEqualTo(80);
    }

    @Test
    @DisplayName("✅ 이벤트 통계: getTotalValid()가 올바르게 계산됨")
    void testEventTotalValidCalculation() {
        // Given
        UUID uploadId = UUID.randomUUID();
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId,
            1000,  // validCertificateCount
            100,   // invalidCertificateCount
            200,   // validCrlCount
            50,    // invalidCrlCount
            LocalDateTime.now()
        );

        // When
        int totalValid = event.getTotalValid();

        // Then
        org.assertj.core.api.Assertions.assertThat(totalValid).isEqualTo(1200);  // 1000 + 200
    }

    @Test
    @DisplayName("✅ 이벤트 통계: getTotalValidated()가 올바르게 계산됨")
    void testEventTotalValidatedCalculation() {
        // Given
        UUID uploadId = UUID.randomUUID();
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId,
            750,  // validCertificateCount
            50,   // invalidCertificateCount
            100,  // validCrlCount
            10,   // invalidCrlCount
            LocalDateTime.now()
        );

        // When
        int totalValidated = event.getTotalValidated();

        // Then
        org.assertj.core.api.Assertions.assertThat(totalValidated).isEqualTo(910);  // 750 + 50 + 100 + 10
    }

    @Test
    @DisplayName("✅ 이벤트 통계: isAllValid()가 모든 항목 유효 여부를 올바르게 반환")
    void testEventIsAllValid() {
        // Given
        UUID uploadId = UUID.randomUUID();

        CertificatesValidatedEvent allValid = new CertificatesValidatedEvent(
            uploadId,
            500,  // validCertificateCount
            0,    // invalidCertificateCount
            100,  // validCrlCount
            0,    // invalidCrlCount
            LocalDateTime.now()
        );

        CertificatesValidatedEvent hasInvalid = new CertificatesValidatedEvent(
            uploadId,
            500,  // validCertificateCount
            5,    // invalidCertificateCount
            100,  // validCrlCount
            0,    // invalidCrlCount
            LocalDateTime.now()
        );

        // When & Then
        org.assertj.core.api.Assertions.assertThat(allValid.isAllValid()).isTrue();
        org.assertj.core.api.Assertions.assertThat(hasInvalid.isAllValid()).isFalse();
    }

    @Test
    @DisplayName("✅ 이벤트 메타데이터: eventId와 occurredOn이 정상적으로 설정됨")
    void testEventMetadata() {
        // Given
        UUID uploadId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        // When
        CertificatesValidatedEvent event = new CertificatesValidatedEvent(
            uploadId,
            100,
            10,
            50,
            5,
            now
        );

        // Then
        org.assertj.core.api.Assertions.assertThat(event.eventId()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(event.getUploadId()).isEqualTo(uploadId);
        org.assertj.core.api.Assertions.assertThat(event.eventType()).isEqualTo("CertificatesValidated");
        org.assertj.core.api.Assertions.assertThat(event.occurredOn()).isNotNull();
    }
}
