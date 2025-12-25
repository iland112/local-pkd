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
    private final com.smartcoreinc.localpkd.fileupload.domain.repository.UploadedFileRepository uploadedFileRepository;

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
            // 1. Update UploadedFile status to COMPLETED (if not already in terminal state)
            com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile uploadedFile =
                uploadedFileRepository.findById(new com.smartcoreinc.localpkd.fileupload.domain.model.UploadId(event.getUploadId()))
                    .orElseThrow(() -> new com.smartcoreinc.localpkd.shared.exception.DomainException(
                        "UPLOAD_NOT_FOUND",
                        "UploadedFile not found for ID: " + event.getUploadId()
                    ));

            // Skip status update if already in terminal state (COMPLETED or FAILED)
            if (uploadedFile.getStatus().isTerminal()) {
                log.info("UploadedFile already in terminal state ({}), skipping status update for uploadId: {}",
                    uploadedFile.getStatus(), event.getUploadId());
            } else if (event.isSuccess()) {
                // Update status to COMPLETED
                uploadedFile.updateStatusToCompleted();
                uploadedFileRepository.save(uploadedFile);
                log.info("UploadedFile status updated to COMPLETED for uploadId: {}", event.getUploadId());
            } else {
                // Update status to FAILED if there were failures
                String errorMsg = String.format("LDAP upload completed with %d failures", event.getFailedCount());
                uploadedFile.fail(errorMsg);
                uploadedFileRepository.save(uploadedFile);
                log.warn("UploadedFile status updated to FAILED for uploadId: {}", event.getUploadId());
            }

            // 2. SSE 진행 상황 전송: COMPLETED (100%)
            if (event.isSuccess()) {
                log.info("LDAP upload completed successfully");

                // 동적 완료 메시지 생성 (인증서/CRL/MasterList 각각 표시)
                StringBuilder completionMsg = new StringBuilder("처리 완료: ");
                boolean hasItem = false;

                if (event.getUploadedCertificateCount() > 0) {
                    // Master List 파일인 경우 CSCA, 그 외는 DSC (CRL은 DSC와 함께 오므로 DSC 파일로 간주)
                    String certLabel = (event.getUploadedMasterListCount() > 0 && event.getUploadedCrlCount() == 0) ? "CSCA" : "DSC";
                    completionMsg.append(String.format("%d개 %s", event.getUploadedCertificateCount(), certLabel));
                    hasItem = true;
                }

                if (event.getUploadedCrlCount() > 0) {
                    if (hasItem) completionMsg.append(", ");
                    completionMsg.append(String.format("%d개 CRL", event.getUploadedCrlCount()));
                    hasItem = true;
                }

                if (event.getUploadedMasterListCount() > 0) {
                    if (hasItem) completionMsg.append(", ");
                    completionMsg.append(String.format("%d개 Master List", event.getUploadedMasterListCount()));
                    hasItem = true;
                }

                if (hasItem) {
                    completionMsg.append(" 업로드됨");
                } else {
                    completionMsg.append("업로드 항목 없음 (모두 동일하여 스킵됨)");
                }

                progressService.sendProgress(
                    ProcessingProgress.builder()
                        .uploadId(event.getUploadId())
                        .stage(ProcessingStage.COMPLETED)
                        .percentage(100)
                        .message(completionMsg.toString())
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
