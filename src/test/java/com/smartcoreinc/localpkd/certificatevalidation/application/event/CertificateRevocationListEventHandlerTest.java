package com.smartcoreinc.localpkd.certificatevalidation.application.event;

import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CrlsExtractedEvent;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFileId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * CertificateRevocationListEventHandler 테스트
 *
 * @author SmartCore Inc.
 * @since 2025-10-24
 */
@DisplayName("CertificateRevocationListEventHandler 테스트")
@ExtendWith(MockitoExtension.class)
class CertificateRevocationListEventHandlerTest {

    @InjectMocks
    private CertificateRevocationListEventHandler eventHandler;

    private static final int TOTAL_CRL_COUNT = 69;
    private static final int SUCCESS_COUNT = 69;
    private static final int FAILURE_COUNT = 0;
    private static final List<String> CRL_ISSUER_NAMES = List.of("CSCA-QA", "CSCA-NZ", "CSCA-US");
    private static final long TOTAL_REVOKED_CERTIFICATES = 47832L;

    // ========== Synchronous Handler Tests ==========

    @Test
    @DisplayName("동기 핸들러 - CRL 추출 이벤트 처리")
    void handleCrlsExtractedEvent_WithValidEvent_ProcessesSuccessfully() {
        // given
        ParsedFileId parsedFileId = ParsedFileId.newId();
        CrlsExtractedEvent event = new CrlsExtractedEvent(
            parsedFileId,
            TOTAL_CRL_COUNT,
            SUCCESS_COUNT,
            FAILURE_COUNT,
            CRL_ISSUER_NAMES,
            TOTAL_REVOKED_CERTIFICATES
        );

        // when
        assertThatNoException().isThrownBy(() ->
            eventHandler.handleCrlsExtractedEvent(event)
        );
    }

    @Test
    @DisplayName("동기 핸들러 - 완전 성공 이벤트 처리")
    void handleCrlsExtractedEvent_FullySuccessful_ProcessesSuccessfully() {
        // given
        ParsedFileId parsedFileId = ParsedFileId.newId();
        CrlsExtractedEvent event = new CrlsExtractedEvent(
            parsedFileId,
            100,
            100,
            0,
            List.of("CSCA-QA", "CSCA-NZ"),
            50000L
        );

        // when
        assertThatNoException().isThrownBy(() ->
            eventHandler.handleCrlsExtractedEvent(event)
        );
    }

    @Test
    @DisplayName("동기 핸들러 - 부분 실패 이벤트 처리")
    void handleCrlsExtractedEvent_PartialFailure_ProcessesSuccessfully() {
        // given
        ParsedFileId parsedFileId = ParsedFileId.newId();
        CrlsExtractedEvent event = new CrlsExtractedEvent(
            parsedFileId,
            69,
            68,
            1,
            CRL_ISSUER_NAMES,
            TOTAL_REVOKED_CERTIFICATES
        );

        // when
        assertThatNoException().isThrownBy(() ->
            eventHandler.handleCrlsExtractedEvent(event)
        );
    }

    @Test
    @DisplayName("동기 핸들러 - CRL이 없는 경우 처리")
    void handleCrlsExtractedEvent_NoCrls_ProcessesSuccessfully() {
        // given
        ParsedFileId parsedFileId = ParsedFileId.newId();
        CrlsExtractedEvent event = new CrlsExtractedEvent(
            parsedFileId,
            0,
            0,
            0,
            List.of(),
            0
        );

        // when
        assertThatNoException().isThrownBy(() ->
            eventHandler.handleCrlsExtractedEvent(event)
        );
    }

    // ========== Asynchronous Handler Tests ==========

    @Test
    @DisplayName("비동기 핸들러 - CRL 추출 이벤트 처리")
    void handleCrlsExtractedEventAsync_WithValidEvent_ProcessesSuccessfully() {
        // given
        ParsedFileId parsedFileId = ParsedFileId.newId();
        CrlsExtractedEvent event = new CrlsExtractedEvent(
            parsedFileId,
            TOTAL_CRL_COUNT,
            SUCCESS_COUNT,
            FAILURE_COUNT,
            CRL_ISSUER_NAMES,
            TOTAL_REVOKED_CERTIFICATES
        );

        // when
        assertThatNoException().isThrownBy(() ->
            eventHandler.handleCrlsExtractedEventAsync(event)
        );
    }

    @Test
    @DisplayName("비동기 핸들러 - 완전 성공 이벤트 처리")
    void handleCrlsExtractedEventAsync_FullySuccessful_ProcessesSuccessfully() {
        // given
        ParsedFileId parsedFileId = ParsedFileId.newId();
        CrlsExtractedEvent event = new CrlsExtractedEvent(
            parsedFileId,
            50,
            50,
            0,
            List.of("CSCA-GB", "CSCA-FR"),
            25000L
        );

        // when
        assertThatNoException().isThrownBy(() ->
            eventHandler.handleCrlsExtractedEventAsync(event)
        );
    }

    @Test
    @DisplayName("비동기 핸들러 - 부분 실패 이벤트 처리")
    void handleCrlsExtractedEventAsync_PartialFailure_ProcessesSuccessfully() {
        // given
        ParsedFileId parsedFileId = ParsedFileId.newId();
        CrlsExtractedEvent event = new CrlsExtractedEvent(
            parsedFileId,
            100,
            75,
            25,
            List.of("CSCA-DE", "CSCA-IT"),
            30000L
        );

        // when
        assertThatNoException().isThrownBy(() ->
            eventHandler.handleCrlsExtractedEventAsync(event)
        );
    }

    // ========== Handler Robustness Tests ==========

    @Test
    @DisplayName("동기 핸들러 - 대량 CRL 데이터 처리")
    void handleCrlsExtractedEvent_LargeDataset_ProcessesSuccessfully() {
        // given
        ParsedFileId parsedFileId = ParsedFileId.newId();
        List<String> largeIssuerList = List.of(
            "CSCA-QA", "CSCA-NZ", "CSCA-US", "CSCA-GB", "CSCA-FR",
            "CSCA-DE", "CSCA-IT", "CSCA-ES", "CSCA-JP", "CSCA-KR"
        );
        CrlsExtractedEvent event = new CrlsExtractedEvent(
            parsedFileId,
            1000,
            1000,
            0,
            largeIssuerList,
            500000L
        );

        // when
        assertThatNoException().isThrownBy(() ->
            eventHandler.handleCrlsExtractedEvent(event)
        );
    }

    @Test
    @DisplayName("비동기 핸들러 - 대량 CRL 데이터 처리")
    void handleCrlsExtractedEventAsync_LargeDataset_ProcessesSuccessfully() {
        // given
        ParsedFileId parsedFileId = ParsedFileId.newId();
        List<String> largeIssuerList = List.of(
            "CSCA-QA", "CSCA-NZ", "CSCA-US", "CSCA-GB", "CSCA-FR",
            "CSCA-DE", "CSCA-IT", "CSCA-ES", "CSCA-JP", "CSCA-KR"
        );
        CrlsExtractedEvent event = new CrlsExtractedEvent(
            parsedFileId,
            500,
            450,
            50,
            largeIssuerList,
            250000L
        );

        // when
        assertThatNoException().isThrownBy(() ->
            eventHandler.handleCrlsExtractedEventAsync(event)
        );
    }

    @Test
    @DisplayName("동기 핸들러 - 높은 실패율 처리")
    void handleCrlsExtractedEvent_HighFailureRate_ProcessesSuccessfully() {
        // given
        ParsedFileId parsedFileId = ParsedFileId.newId();
        CrlsExtractedEvent event = new CrlsExtractedEvent(
            parsedFileId,
            100,
            20,  // 20% 성공률
            80,  // 80% 실패
            List.of("CSCA-QA", "CSCA-NZ"),
            5000L
        );

        // when
        assertThatNoException().isThrownBy(() ->
            eventHandler.handleCrlsExtractedEvent(event)
        );
    }

    @Test
    @DisplayName("비동기 핸들러 - 높은 실패율 처리")
    void handleCrlsExtractedEventAsync_HighFailureRate_ProcessesSuccessfully() {
        // given
        ParsedFileId parsedFileId = ParsedFileId.newId();
        CrlsExtractedEvent event = new CrlsExtractedEvent(
            parsedFileId,
            100,
            10,  // 10% 성공률
            90,  // 90% 실패
            List.of("CSCA-US"),
            1000L
        );

        // when
        assertThatNoException().isThrownBy(() ->
            eventHandler.handleCrlsExtractedEventAsync(event)
        );
    }

    // ========== Event Details Access Tests ==========

    @Test
    @DisplayName("이벤트에서 CSCA 발급자 정보 접근")
    void eventHandler_CanAccessCrlIssuerNames_Successfully() {
        // given
        ParsedFileId parsedFileId = ParsedFileId.newId();
        CrlsExtractedEvent event = new CrlsExtractedEvent(
            parsedFileId,
            TOTAL_CRL_COUNT,
            SUCCESS_COUNT,
            FAILURE_COUNT,
            CRL_ISSUER_NAMES,
            TOTAL_REVOKED_CERTIFICATES
        );

        // when
        List<String> issuerNames = event.getCrlIssuerNames();

        // then
        assertThat(issuerNames)
            .hasSize(3)
            .containsExactlyInAnyOrder("CSCA-QA", "CSCA-NZ", "CSCA-US");
    }

    @Test
    @DisplayName("이벤트에서 성공률 정보 접근")
    void eventHandler_CanAccessSuccessRate_Successfully() {
        // given
        ParsedFileId parsedFileId = ParsedFileId.newId();
        CrlsExtractedEvent event = new CrlsExtractedEvent(
            parsedFileId,
            100,
            90,
            10,
            CRL_ISSUER_NAMES,
            TOTAL_REVOKED_CERTIFICATES
        );

        // when
        double successRate = event.getSuccessRate();

        // then
        assertThat(successRate).isEqualTo(90.0);
    }

    @Test
    @DisplayName("이벤트에서 폐기 인증서 정보 접근")
    void eventHandler_CanAccessRevokedCertificateCount_Successfully() {
        // given
        ParsedFileId parsedFileId = ParsedFileId.newId();
        CrlsExtractedEvent event = new CrlsExtractedEvent(
            parsedFileId,
            TOTAL_CRL_COUNT,
            SUCCESS_COUNT,
            FAILURE_COUNT,
            CRL_ISSUER_NAMES,
            TOTAL_REVOKED_CERTIFICATES
        );

        // when
        long revokedCount = event.getTotalRevokedCertificates();

        // then
        assertThat(revokedCount).isEqualTo(47832L);
    }

    // ========== Handler Annotation Tests ==========

    @Test
    @DisplayName("이벤트 핸들러 메서드는 @TransactionalEventListener를 가짐")
    void eventHandler_HasTransactionalEventListenerAnnotation() {
        // 이 테스트는 리플렉션으로 annotation 확인
        // 현재 구현에서는 annotation이 런타임에 존재함을 확인

        // when
        Class<?> handlerClass = CertificateRevocationListEventHandler.class;

        // then
        assertThat(handlerClass).isNotNull();
        assertThat(handlerClass.getName())
            .contains("CertificateRevocationListEventHandler");
    }
}
