package com.smartcoreinc.localpkd.ldapintegration.application.event;

import com.smartcoreinc.localpkd.ldapintegration.domain.event.LdapUploadCompletedEvent;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProcessingStage;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * LDAP Upload Event Handler - LDAP 업로드 도메인 이벤트 핸들러
 *
 * <p>LDAP Integration Context에서 발행되는 Domain Event를 처리합니다.
 * LDAP 업로드 완료 후 최종 처리 및 사용자 알림 등의 후속 작업을 수행합니다.</p>
 *
 * <h3>처리하는 이벤트</h3>
 * <ul>
 *   <li>{@link LdapUploadCompletedEvent} - LDAP 업로드 완료</li>
 * </ul>
 *
 * <h3>이벤트 처리 전략</h3>
 * <ul>
 *   <li><b>비동기 처리</b>: UploadHistory 상태 업데이트, 통계 기록</li>
 *   <li><b>트랜잭션 후 처리</b>: {@code @TransactionalEventListener(AFTER_COMMIT)}</li>
 *   <li><b>SSE 진행률 업데이트</b>: ProgressService를 통해 COMPLETED (100%) 전송</li>
 * </ul>
 *
 * <h3>워크플로우</h3>
 * <pre>
 * FileUploadedEvent
 *   → FileUploadEventHandler
 *     → ParseLdifFileUseCase
 *       → FileParsingCompletedEvent
 *         → LdifParsingEventHandler
 *           → Certificate Validation (향후)
 *             → LdapUploadCompletedEvent
 *               → LdapUploadEventHandler (이 클래스)
 *                 → 최종 완료 처리
 * </pre>
 *
 * <h3>향후 확장</h3>
 * <ul>
 *   <li>UploadHistory의 상태를 COMPLETED로 업데이트</li>
 *   <li>사용자 알림 발송 (이메일, 웹 알림)</li>
 *   <li>통계 및 감사 로그 기록</li>
 *   <li>웹훅 호출</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-29
 * @see LdapUploadCompletedEvent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LdapUploadEventHandler {

    private final ProgressService progressService;
    // TODO: Phase 4에서 추가될 Services
    // private final UploadHistoryService uploadHistoryService;
    // private final NotificationService notificationService;
    // private final AuditService auditService;

    /**
     * LDAP 업로드 완료 이벤트 처리 (비동기, 트랜잭션 커밋 후)
     *
     * <p>트랜잭션 커밋 후 비동기적으로 최종 완료 처리를 수행합니다.
     * LDAP 업로드가 실패해도 이전 단계의 트랜잭션에 영향을 주지 않습니다.</p>
     *
     * <h4>처리 내용</h4>
     * <ul>
     *   <li>SSE 진행 상황 전송 (COMPLETED, 100% 또는 업로드 성공률에 따라)</li>
     *   <li>업로드 성공/실패 로깅</li>
     *   <li>TODO: UploadHistory 상태 업데이트 (COMPLETED)</li>
     *   <li>TODO: 사용자 알림 발송</li>
     *   <li>TODO: 감사 로그 기록</li>
     * </ul>
     *
     * @param event LDAP 업로드 완료 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLdapUploadCompletedAndMarkAsFinalized(LdapUploadCompletedEvent event) {
        log.info("=== [Event-Async] LdapUploadCompleted (Finalizing upload) ===");
        log.info("Upload ID: {}", event.getUploadId());
        log.info("LDAP Upload Summary:");
        log.info("  - Uploaded Certificates: {}", event.getUploadedCertificateCount());
        log.info("  - Uploaded CRLs: {}", event.getUploadedCrlCount());
        log.info("  - Failed Items: {}", event.getFailedCount());
        log.info("  - Success Rate: {}%", event.getSuccessRate());

        try {
            // 1. SSE 진행 상황 전송: COMPLETED (100%)
            if (event.isSuccess()) {
                log.info("LDAP upload completed successfully");
                progressService.sendProgress(
                    ProcessingProgress.builder()
                        .uploadId(event.getUploadId())
                        .stage(ProcessingStage.COMPLETED)
                        .percentage(100)
                        .message(String.format("처리 완료: %d개 인증서, %d개 CRL 업로드됨",
                                event.getUploadedCertificateCount(),
                                event.getUploadedCrlCount()))
                        .processedCount(event.getTotalUploaded())
                        .totalCount(event.getTotalProcessed())
                        .build()
                );
            } else {
                log.warn("LDAP upload completed with failures: {} items failed",
                        event.getFailedCount());
                progressService.sendProgress(
                    ProcessingProgress.builder()
                        .uploadId(event.getUploadId())
                        .stage(ProcessingStage.COMPLETED)
                        .percentage(100)
                        .message(String.format("처리 완료 (부분): %d개 성공, %d개 실패",
                                event.getTotalUploaded(),
                                event.getFailedCount()))
                        .processedCount(event.getTotalUploaded())
                        .totalCount(event.getTotalProcessed())
                        .errorMessage(event.getFailedCount() + "개 항목 LDAP 업로드 실패")
                        .build()
                );
            }

            // 2. UploadHistory 상태 업데이트
            // TODO: Phase 4에서 구현 예정
            // uploadHistoryService.markAsCompleted(
            //     event.getUploadId(),
            //     event.getTotalUploaded(),
            //     event.getFailedCount()
            // );

            // 3. 사용자 알림 발송
            // TODO: Phase 4에서 구현 예정
            // if (event.isSuccess()) {
            //     notificationService.notifyUploadSuccess(
            //         event.getUploadId(),
            //         event.getUploadedCertificateCount(),
            //         event.getUploadedCrlCount()
            //     );
            // } else {
            //     notificationService.notifyUploadPartialSuccess(
            //         event.getUploadId(),
            //         event.getTotalUploaded(),
            //         event.getFailedCount()
            //     );
            // }

            // 4. 감사 로그 기록
            // TODO: Phase 4에서 구현 예정
            // auditService.recordLdapUploadCompleted(
            //     event.getUploadId(),
            //     event.getTotalProcessed(),
            //     event.getFailedCount()
            // );

            log.info("Upload finalization completed for uploadId: {}", event.getUploadId());

        } catch (Exception e) {
            log.error("Failed to finalize LDAP upload for uploadId: {}",
                    event.getUploadId(), e);

            // SSE 진행 상황 전송: 경고 (완료이지만 후처리 실패)
            progressService.sendProgress(
                ProcessingProgress.builder()
                    .uploadId(event.getUploadId())
                    .stage(ProcessingStage.COMPLETED)
                    .percentage(100)
                    .message("LDAP 업로드는 완료되었으나 후처리 중 오류가 발생했습니다")
                    .errorMessage("후처리 실패: " + e.getMessage())
                    .build()
            );
        }
    }
}
