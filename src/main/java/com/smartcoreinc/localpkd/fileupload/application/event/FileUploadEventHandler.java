package com.smartcoreinc.localpkd.fileupload.application.event;

import com.smartcoreinc.localpkd.fileupload.domain.event.DuplicateFileDetectedEvent;
import com.smartcoreinc.localpkd.fileupload.domain.event.FileUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * File Upload Event Handler - 파일 업로드 도메인 이벤트 핸들러
 *
 * <p>File Upload Context에서 발행되는 Domain Event를 처리합니다.
 * 파일 업로드 완료 후 파싱 트리거, 통계 업데이트, 알림 등의 후속 작업을 수행합니다.</p>
 *
 * <h3>처리하는 이벤트</h3>
 * <ul>
 *   <li>{@link FileUploadedEvent} - 신규 파일 업로드 완료</li>
 *   <li>{@link DuplicateFileDetectedEvent} - 중복 파일 감지</li>
 * </ul>
 *
 * <h3>이벤트 처리 전략</h3>
 * <ul>
 *   <li><b>동기 처리</b>: 통계 업데이트, 로깅</li>
 *   <li><b>비동기 처리</b>: 파일 파싱, LDAP 업로드, 알림</li>
 *   <li><b>트랜잭션 후 처리</b>: {@code @TransactionalEventListener(AFTER_COMMIT)}</li>
 * </ul>
 *
 * <h3>사용 예시 - 이벤트 발행 흐름</h3>
 * <pre>
 * 1. UploadedFile.create() → FileUploadedEvent 추가
 * 2. repository.save() → JpaRepository 저장
 * 3. EventBus.publishAll() → Spring ApplicationEventPublisher
 * 4. [Transaction Commit]
 * 5. @TransactionalEventListener(AFTER_COMMIT) → 이벤트 핸들러 실행
 * 6. 파일 파싱 트리거 (비동기)
 * </pre>
 *
 * <h3>향후 확장</h3>
 * <ul>
 *   <li>파일 파싱 Context로 ParseFileCommand 발행</li>
 *   <li>LDAP Integration Context로 UploadToLdapCommand 발행</li>
 *   <li>Notification Context로 SendNotificationCommand 발행</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 * @see FileUploadedEvent
 * @see DuplicateFileDetectedEvent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadEventHandler {

    // TODO: Phase 4에서 추가될 Use Cases
    // private final ParseFileUseCase parseFileUseCase;
    // private final UpdateStatisticsUseCase updateStatisticsUseCase;
    // private final SendNotificationUseCase sendNotificationUseCase;

    /**
     * 파일 업로드 완료 이벤트 처리 (동기)
     *
     * <p>파일 업로드 직후 동기적으로 처리해야 할 작업을 수행합니다.
     * 현재는 로깅만 수행하며, 향후 통계 업데이트를 추가할 예정입니다.</p>
     *
     * <h4>처리 내용</h4>
     * <ul>
     *   <li>업로드 성공 로깅</li>
     *   <li>TODO: 업로드 통계 즉시 업데이트</li>
     * </ul>
     *
     * @param event 파일 업로드 이벤트
     */
    @EventListener
    public void handleFileUploaded(FileUploadedEvent event) {
        log.info("=== [Event] FileUploaded ===");
        log.info("Upload ID: {}", event.uploadId().getId());
        log.info("File name: {}", event.fileName());
        log.info("File hash: {}", event.fileHash().substring(0, 8) + "...");
        log.info("File size: {} bytes ({})",
                event.fileSizeBytes(),
                formatFileSize(event.fileSizeBytes()));
        log.info("Uploaded at: {}", event.uploadedAt());

        // TODO: 통계 업데이트
        // updateStatisticsUseCase.execute(new UpdateStatsCommand(...));
    }

    /**
     * 파일 업로드 완료 이벤트 처리 (비동기, 트랜잭션 커밋 후)
     *
     * <p>트랜잭션 커밋 후 비동기적으로 파일 파싱을 트리거합니다.
     * 파싱이 실패해도 업로드 트랜잭션에 영향을 주지 않습니다.</p>
     *
     * <h4>처리 내용</h4>
     * <ul>
     *   <li>TODO: 파일 파싱 트리거 (Phase 4)</li>
     *   <li>TODO: LDAP 업로드 트리거 (Phase 5)</li>
     * </ul>
     *
     * @param event 파일 업로드 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFileUploadedAsync(FileUploadedEvent event) {
        log.info("=== [Event-Async] FileUploaded (Triggering parsing) ===");
        log.info("Upload ID: {}", event.uploadId().getId());

        try {
            // TODO: Phase 4 - 파일 파싱 Context 연동
            // ParseFileCommand command = new ParseFileCommand(
            //     event.uploadId().getId().toString(),
            //     event.fileName(),
            //     event.fileHash()
            // );
            // parseFileUseCase.execute(command);

            log.info("File parsing would be triggered here (Phase 4)");

        } catch (Exception e) {
            log.error("Failed to trigger file parsing for uploadId: {}",
                    event.uploadId().getId(), e);
            // TODO: 실패 시 재시도 로직 또는 Dead Letter Queue
        }
    }

    /**
     * 중복 파일 감지 이벤트 처리 (동기)
     *
     * <p>중복 파일이 감지되었을 때 로깅 및 통계를 업데이트합니다.</p>
     *
     * <h4>처리 내용</h4>
     * <ul>
     *   <li>중복 파일 경고 로깅</li>
     *   <li>TODO: 중복 파일 통계 업데이트</li>
     *   <li>TODO: 관리자 알림 (설정에 따라)</li>
     * </ul>
     *
     * @param event 중복 파일 감지 이벤트
     */
    @EventListener
    public void handleDuplicateFileDetected(DuplicateFileDetectedEvent event) {
        log.warn("=== [Event] DuplicateFileDetected ===");
        log.warn("Duplicate upload ID: {}", event.duplicateUploadId().getId());
        log.warn("Original upload ID: {}", event.originalUploadId().getId());
        log.warn("File name: {}", event.fileName());
        log.warn("File hash: {}", event.fileHash().substring(0, 8) + "...");
        log.warn("Detected at: {}", event.detectedAt());

        // TODO: 중복 파일 통계 업데이트
        // updateStatisticsUseCase.incrementDuplicateCount();

        // TODO: 알림 발송 (설정에 따라)
        // if (notificationEnabled) {
        //     sendNotificationUseCase.execute(new DuplicateFileNotification(...));
        // }
    }

    /**
     * 중복 파일 감지 이벤트 처리 (비동기, 트랜잭션 커밋 후)
     *
     * <p>중복 파일에 대한 추가 분석 및 정리 작업을 비동기로 수행합니다.</p>
     *
     * <h4>처리 내용</h4>
     * <ul>
     *   <li>TODO: 중복 파일 자동 삭제 정책 적용</li>
     *   <li>TODO: 중복 패턴 분석 및 리포트 생성</li>
     * </ul>
     *
     * @param event 중복 파일 감지 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDuplicateFileDetectedAsync(DuplicateFileDetectedEvent event) {
        log.info("=== [Event-Async] DuplicateFileDetected (Additional processing) ===");
        log.info("Duplicate upload ID: {}", event.duplicateUploadId().getId());

        try {
            // TODO: 중복 파일 자동 정리 정책
            // if (autoDeletionEnabled) {
            //     deleteFileUseCase.execute(new DeleteFileCommand(
            //         event.duplicateUploadId().getId().toString()
            //     ));
            // }

            log.info("Duplicate file additional processing would happen here (Phase 4)");

        } catch (Exception e) {
            log.error("Failed to process duplicate file: {}",
                    event.duplicateUploadId().getId(), e);
        }
    }

    /**
     * 파일 크기를 사람이 읽기 쉬운 형식으로 변환
     *
     * @param bytes 파일 크기 (바이트)
     * @return 사람이 읽기 쉬운 형식 (예: "75.0 MB")
     */
    private String formatFileSize(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else if (bytes >= 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return bytes + " bytes";
        }
    }
}
