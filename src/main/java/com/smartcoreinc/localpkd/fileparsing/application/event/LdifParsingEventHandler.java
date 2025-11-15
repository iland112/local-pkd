package com.smartcoreinc.localpkd.fileparsing.application.event;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.ValidateCertificatesCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.ValidateCertificatesUseCase;
import com.smartcoreinc.localpkd.fileparsing.domain.event.FileParsingCompletedEvent;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProcessingStage;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * LDIF Parsing Event Handler - 파일 파싱 도메인 이벤트 핸들러
 *
 * <p>File Parsing Context에서 발행되는 Domain Event를 처리합니다.
 * 파일 파싱 완료 후 인증서 검증 트리거 등의 후속 작업을 수행합니다.</p>
 *
 * <h3>처리하는 이벤트</h3>
 * <ul>
 *   <li>{@link FileParsingCompletedEvent} - 파일 파싱 완료 완료</li>
 * </ul>
 *
 * <h3>이벤트 처리 전략</h3>
 * <ul>
 *   <li><b>비동기 처리</b>: Certificate Validation Context로 검증 트리거</li>
 *   <li><b>트랜잭션 후 처리</b>: {@code @TransactionalEventListener(AFTER_COMMIT)}</li>
 *   <li><b>SSE 진행률 업데이트</b>: ProgressService를 통해 VALIDATION_STARTED (65%) 전송</li>
 * </ul>
 *
 * <h3>워크플로우</h3>
 * <pre>
 * FileUploadedEvent
 *   → FileUploadEventHandler
 *     → ParseLdifFileUseCase
 *       → FileParsingCompletedEvent
 *         → LdifParsingEventHandler (이 클래스)
 *           → Certificate Validation 트리거 (향후 구현)
 * </pre>
 *
 * <h3>향후 확장</h3>
 * <ul>
 *   <li>Certificate Validation Context로 검증 커맨드 발행</li>
 *   <li>검증 실패 시 재시도 로직</li>
 *   <li>Dead Letter Queue 처리</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-29
 * @see FileParsingCompletedEvent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LdifParsingEventHandler {

    private final ProgressService progressService;
    private final ValidateCertificatesUseCase validateCertificatesUseCase;

    /**
     * 파일 파싱 완료 이벤트 처리 (비동기, 트랜잭션 커밋 후)
     *
     * <p>트랜잭션 커밋 후 비동기적으로 인증서 검증을 트리거합니다.
     * 검증이 실패해도 파싱 트랜잭션에 영향을 주지 않습니다.</p>
     *
     * <h4>처리 내용</h4>
     * <ul>
     *   <li>SSE 진행 상황 전송 (VALIDATION_STARTED, 65%)</li>
     *   <li>추출된 인증서 개수 로깅</li>
     *   <li>Certificate Validation Context로 검증 트리거</li>
     * </ul>
     *
     * <h4>트랜잭션 관리</h4>
     * <ul>
     *   <li><b>@Async</b>: 비동기 실행 (별도 스레드)</li>
     *   <li><b>@Transactional</b>: NEW 트랜잭션 생성</li>
     *   <li><b>@TransactionalEventListener(AFTER_COMMIT)</b>: 파싱 트랜잭션 커밋 후 실행</li>
     * </ul>
     *
     * <h4>문제 해결</h4>
     * <p>이전에는 @Transactional이 없어서 validateCertificatesUseCase.execute()의
     * 트랜잭션 전파가 제대로 되지 않았습니다. @Transactional(propagation=REQUIRES_NEW)
     * 추가로 명시적인 새 트랜잭션 생성을 보장합니다.</p>
     *
     * @param event 파일 파싱 완료 이벤트
     */
    @Async
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFileParsingCompletedAndTriggerValidation(FileParsingCompletedEvent event) {
        log.info("=== [Event-Async] FileParsingCompleted (Triggering certificate validation) ===");
        log.info("Upload ID: {}", event.getUploadId());
        log.info("Extracted: {} certificates, {} CRLs", event.getCertificateCount(), event.getCrlCount());

        try {
            // 1. SSE 진행 상황 전송: VALIDATION_STARTED (65%)
            progressService.sendProgress(
                ProcessingProgress.builder()
                    .uploadId(event.getUploadId())
                    .stage(ProcessingStage.VALIDATION_STARTED)
                    .percentage(65)
                    .message("인증서 검증 시작")
                    .build()
            );

            // 2. Certificate Validation Context로 검증 트리거
            ValidateCertificatesCommand command = ValidateCertificatesCommand.builder()
                .uploadId(event.getUploadId())
                .parsedFileId(event.getParsedFileId())
                .certificateCount(event.getCertificateCount())
                .crlCount(event.getCrlCount())
                .build();

            log.info("Triggering certificate validation: uploadId={}, certificates={}, crls={}",
                event.getUploadId(), event.getCertificateCount(), event.getCrlCount());

            var validationResponse = validateCertificatesUseCase.execute(command);

            if (validationResponse.success()) {
                log.info("Certificate validation completed successfully: uploadId={}, valid={}, invalid={}",
                    event.getUploadId(), validationResponse.getTotalValid(), validationResponse.getTotalValidated() - validationResponse.getTotalValid());
            } else {
                log.error("Certificate validation failed: uploadId={}, error={}",
                    event.getUploadId(), validationResponse.errorMessage());
            }

        } catch (Exception e) {
            log.error("Failed to trigger certificate validation for uploadId: {}",
                    event.getUploadId(), e);

            // SSE 진행 상황 전송: FAILED
            progressService.sendProgress(
                ProcessingProgress.builder()
                    .uploadId(event.getUploadId())
                    .stage(ProcessingStage.FAILED)
                    .percentage(0)
                    .errorMessage("인증서 검증 트리거 실패: " + e.getMessage())
                    .build()
            );
        }
    }
}
