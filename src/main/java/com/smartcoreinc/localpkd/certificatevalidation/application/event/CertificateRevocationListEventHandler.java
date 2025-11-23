package com.smartcoreinc.localpkd.certificatevalidation.application.event;

import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CrlsExtractedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * CertificateRevocationListEventHandler - CRL 이벤트 핸들러
 *
 * <p><b>책임</b>: CRL 추출 및 저장 관련 Domain Events를 처리합니다.</p>
 *
 * <p><b>처리 이벤트</b>:</p>
 * <ul>
 *   <li>{@link CrlsExtractedEvent}: CRL 추출 완료 이벤트</li>
 * </ul>
 *
 * <p><b>이벤트 처리 방식</b>:</p>
 * <ul>
 *   <li>동기 처리: 이벤트 발행과 동시에 처리 (읽기 전용 작업)</li>
 *   <li>비동기 처리: 트랜잭션 커밋 후 처리 (추가 작업, 외부 API 호출 등)</li>
 *   <li>Lombok {@code @Slf4j}를 사용하여 로거를 자동 주입합니다.</li>
 * </ul>
 *
 * <p><b>처리 순서</b>:</p>
 * <ol>
 *   <li>handleCrlsExtractedEvent() - 동기 처리
 *       <ul>
 *           <li>CRL 추출 결과 로깅</li>
 *           <li>추출 통계 기록</li>
 *           <li>검증 상태 업데이트</li>
 *       </ul>
 *   </li>
 *   <li>handleCrlsExtractedEventAsync() - 비동기 처리 (트랜잭션 커밋 후)
 *       <ul>
 *           <li>감시(Monitoring) 알림 발송</li>
 *           <li>메트릭 수집</li>
 *           <li>외부 시스템 연동</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // Domain Layer에서 이벤트 발행
 * CrlsExtractedEvent event = new CrlsExtractedEvent(
 *     parsedFileId, 69, 69, 0, List.of("CSCA-QA", "CSCA-NZ"), 47832
 * );
 * applicationEventPublisher.publishEvent(event);
 *
 * // 자동으로 이 핸들러가 처리
 * // 1. handleCrlsExtractedEvent() - 동기 처리
 * // 2. handleCrlsExtractedEventAsync() - 비동기 처리 (커밋 후)
 * }</pre>
 *
 * <p><b>주의사항</b>:</p>
 * <ul>
 *   <li>동기 핸들러에서는 롱런닝 작업 금지</li>
 *   <li>비동기 핸들러는 트랜잭션 커밋 후 실행됨</li>
 *   <li>예외 처리: 이벤트 처리 실패는 애플리케이션 동작에 영향 없음</li>
 * </ul>
 *
 * @see CrlsExtractedEvent
 * @see org.springframework.transaction.event.TransactionalEventListener
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateRevocationListEventHandler {

    // ========== Event Handlers ==========

    /**
     * CRL 추출 이벤트 처리 (동기)
     *
     * <p>CRL 추출 완료 시 호출되며, 추출 결과를 로깅하고 통계를 수집합니다.
     * 이 핸들러는 이벤트 발행과 동시에 실행되므로 빠른 작업만 처리합니다.</p>
     *
     * <p><b>처리 내용</b>:</p>
     * <ul>
     *   <li>CRL 추출 결과 요약 로깅</li>
     *   <li>발급자(CSCA) 목록 기록</li>
     *   <li>폐기 인증서 통계 기록</li>
     *   <li>성공률 계산 및 로깅</li>
     *   <li>경고 메시지 (추출 실패 발생 시)</li>
     * </ul>
     *
     * <p><b>로그 레벨</b>:</p>
     * <ul>
     *   <li>INFO: 전체 처리 결과 요약</li>
     *   <li>DEBUG: 발급자 목록, 상세 통계</li>
     *   <li>WARN: 추출 실패 발생</li>
     * </ul>
     *
     * @param event CRL 추출 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    @Transactional(readOnly = true)
    public void handleCrlsExtractedEvent(CrlsExtractedEvent event) {
        try {
            log.info("=== CRL Extraction Event Handler Started ===");
            log.info("Event ID: {}", event.eventId());
            log.info("Parsed File ID: {}", event.getParsedFileId());

            // 추출 결과 요약
            log.info("CRL Extraction Summary:");
            log.info("  - Total CRLs extracted: {}", event.getTotalCrlCount());
            log.info("  - Successfully saved: {}", event.getSuccessCount());
            log.info("  - Failed to save: {}", event.getFailureCount());
            log.info("  - Success rate: {:.2f}%", event.getSuccessRate());
            log.info("  - Total revoked certificates: {}", event.getTotalRevokedCertificates());

            // 발급자 목록 로깅
            if (!event.getCrlIssuerNames().isEmpty()) {
                log.debug("CRL Issuers (CSCA): {}", event.getCrlIssuerNames());
                log.debug("  - Unique issuer count: {}", event.getIssuerCount());
            }

            // 완전 성공 여부
            if (event.isFullySuccessful()) {
                log.info("✓ All CRLs extracted and saved successfully");
            }

            // 경고: 부분 실패
            if (event.getFailureCount() > 0) {
                log.warn("⚠ {} CRL(s) failed to save", event.getFailureCount());
                log.warn("  - Failed count: {}", event.getFailureCount());
                log.warn("  - Success rate: {:.2f}%", event.getSuccessRate());
            }

            log.info("=== CRL Extraction Event Handler Completed ===");

        } catch (Exception e) {
            log.error("Error handling CRL extraction event", e);
            // 예외를 던지지 않음 - 이벤트 처리 실패가 주 작업에 영향 없음
        }
    }

    /**
     * CRL 추출 이벤트 비동기 처리 (트랜잭션 커밋 후)
     *
     * <p>이 핸들러는 메인 트랜잭션이 커밋된 후 실행되므로
     * 롱런닝 작업이나 외부 시스템 호출에 적합합니다.</p>
     *
     * <p><b>향후 구현 예시</b>:</p>
     * <ul>
     *   <li>메트릭 시스템에 CRL 추출 통계 전송</li>
     *   <li>감시(Monitoring) 시스템에 알림 발송</li>
     *   <li>LDAP 서버에 CRL 동기화 상태 전송</li>
     *   <li>Audit 로그 저장</li>
     *   <li>다음 단계 워크플로우 트리거 (인증서 검증 등)</li>
     * </ul>
     *
     * @param event CRL 추출 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCrlsExtractedEventAsync(CrlsExtractedEvent event) {
        try {
            log.debug("=== Async CRL Extraction Event Handler Started ===");

            // TODO: 추가 비동기 처리 구현
            // 1. 메트릭 수집
            // log.debug("Publishing CRL extraction metrics...");
            // metricsService.recordCrlExtraction(event);

            // 2. 감시 알림
            // if (!event.isFullySuccessful()) {
            //     monitoringService.alertCrlExtractionFailure(event);
            // }

            // 3. Audit 로그
            // auditService.logCrlExtraction(event);

            // 4. 다음 단계 트리거
            // certificateValidationWorkflow.startValidationForParsedFile(event.getParsedFileId());

            log.debug("=== Async CRL Extraction Event Handler Completed ===");

        } catch (Exception e) {
            log.warn("Error in async CRL extraction event handling", e);
            // 예외를 던지지 않음 - 비동기 작업 실패가 주 작업에 영향 없음
        }
    }
}
