package com.smartcoreinc.localpkd.ldapintegration.application.event;

import com.smartcoreinc.localpkd.certificatevalidation.domain.event.UploadToLdapCompletedEvent;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.fileupload.domain.repository.UploadedFileRepository;
import com.smartcoreinc.localpkd.ldapintegration.domain.event.LdapUploadCompletedEvent;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProcessingStage;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

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
    private final UploadedFileRepository uploadedFileRepository;
    // TODO: Phase 18에서 추가될 Services
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

    /**
     * LDAP 업로드 완료 이벤트 처리 - UploadToLdapCompletedEvent (✅ Phase 17 Task 2.4)
     *
     * <p><b>이벤트 소스</b>: Certificate Validation Context
     * - UploadToLdapEventHandler가 LDAP 업로드 완료 후 발행</p>
     *
     * <p><b>처리 방식</b>: 비동기, 트랜잭션 커밋 후
     * - 업로드 완료 로깅</p>
     *
     * <p><b>Event Flow</b>:
     * <ol>
     *   <li>CertificatesValidatedEvent 발행 (ValidateCertificatesUseCase)</li>
     *   <li>UploadToLdapEventHandler 수신 (Certificate Validation Context)</li>
     *   <li>UploadToLdapUseCase 실행</li>
     *   <li>UploadToLdapCompletedEvent 발행 (이 이벤트)</li>
     *   <li>LdapUploadEventHandler 수신 (이 메서드) ← 여기</li>
     *   <li>최종 완료 처리</li>
     * </ol>
     * </p>
     *
     * @param event UploadToLdapCompletedEvent (Certificate Validation Context)
     * @since Phase 17 Task 2.4
     */
    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUploadToLdapCompletedAndMarkAsFinalized(UploadToLdapCompletedEvent event) {
        log.info("=== [Event-Async] UploadToLdapCompleted (Finalizing upload) ===");
        log.info("Upload ID: {}", event.getUploadId());
        log.info("LDAP Upload Summary:");
        log.info("  - Successfully uploaded: {}", event.getSuccessCount());
        log.info("  - Failed items: {}", event.getFailureCount());
        log.info("  - Success rate: {}%", event.getSuccessRate());

        try {
            // 1. SSE 진행 상황 전송: COMPLETED (100%)
            if (event.isSuccess()) {
                log.info("Upload to LDAP completed successfully");
                progressService.sendProgress(
                    ProcessingProgress.builder()
                        .uploadId(event.getUploadId())
                        .stage(ProcessingStage.COMPLETED)
                        .percentage(100)
                        .message(String.format("처리 완료: %d개 항목 LDAP에 업로드됨",
                                event.getSuccessCount()))
                        .processedCount(event.getSuccessCount())
                        .totalCount(event.getTotalProcessed())
                        .build()
                );
            } else {
                log.warn("Upload to LDAP completed with failures: {} items failed",
                        event.getFailureCount());
                progressService.sendProgress(
                    ProcessingProgress.builder()
                        .uploadId(event.getUploadId())
                        .stage(ProcessingStage.COMPLETED)
                        .percentage(100)
                        .message(String.format("처리 완료 (부분): %d개 성공, %d개 실패",
                                event.getSuccessCount(),
                                event.getFailureCount()))
                        .processedCount(event.getSuccessCount())
                        .totalCount(event.getTotalProcessed())
                        .errorMessage(event.getFailureCount() + "개 항목 LDAP 업로드 실패")
                        .build()
                );
            }

            // 2. UploadHistory 상태 업데이트 (Task 3: ✅ Phase 17 Complete)
            markUploadAsCompleted(event.getUploadId(), event.getSuccessCount(), event.getFailureCount());

            // 3. 사용자 알림 발송
            // TODO: Phase 18에서 구현 예정
            // if (event.isSuccess()) {
            //     notificationService.notifyUploadSuccess(
            //         event.getUploadId(),
            //         event.getSuccessCount()
            //     );
            // }

            log.info("Upload finalization completed for uploadId: {}", event.getUploadId());

        } catch (Exception e) {
            log.error("Failed to finalize upload for uploadId: {}",
                    event.getUploadId(), e);

            // SSE 진행 상황 전송: 경고 (완료이지만 후처리 실패)
            progressService.sendProgress(
                ProcessingProgress.builder()
                    .uploadId(event.getUploadId())
                    .stage(ProcessingStage.COMPLETED)
                    .percentage(100)
                    .message("업로드는 완료되었으나 후처리 중 오류가 발생했습니다")
                    .errorMessage("후처리 실패: " + e.getMessage())
                    .build()
            );
        }
    }

    /**
     * Upload 상태를 COMPLETED로 업데이트
     *
     * <p>주어진 uploadId를 기반으로 UploadedFile을 조회하여 상태를 COMPLETED로 변경합니다.</p>
     *
     * @param uploadId 업로드 ID (UUID)
     * @param successCount 성공한 항목 수
     * @param failureCount 실패한 항목 수
     */
    private void markUploadAsCompleted(UUID uploadId, int successCount, int failureCount) {
        try {
            // 1. UploadId Value Object 생성
            UploadId domainUploadId = new UploadId(uploadId);

            // 2. Repository에서 UploadedFile 조회
            var uploadedFileOpt = uploadedFileRepository.findById(domainUploadId);

            if (uploadedFileOpt.isEmpty()) {
                log.warn("UploadedFile not found for uploadId: {}", uploadId);
                return;
            }

            // 3. UploadedFile 상태 업데이트
            var uploadedFile = uploadedFileOpt.get();
            uploadedFile.complete();

            // 4. Repository에 저장 (Domain Events 자동 발행)
            uploadedFileRepository.save(uploadedFile);

            log.info("UploadedFile status updated to COMPLETED for uploadId: {} (success: {}, failure: {})",
                uploadId, successCount, failureCount);

        } catch (Exception e) {
            log.error("Error marking upload as completed for uploadId: {}", uploadId, e);
            // 예외를 던지지 않음 - 상태 업데이트 실패가 LDAP 업로드 완료에 영향을 주지 않도록
        }
    }
}
