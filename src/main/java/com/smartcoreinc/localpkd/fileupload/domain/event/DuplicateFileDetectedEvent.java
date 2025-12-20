package com.smartcoreinc.localpkd.fileupload.domain.event;

import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.shared.domain.DomainEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Duplicate File Detected Event - 중복 파일 감지 이벤트
 *
 * <p>업로드된 파일이 기존 파일과 동일한 해시를 가질 때 발행되는 도메인 이벤트입니다.
 * 이 이벤트를 구독하여 중복 파일에 대한 특별한 처리를 수행할 수 있습니다.</p>
 *
 * <h3>이벤트 발행 시점</h3>
 * <ul>
 *   <li>파일 해시가 계산된 후</li>
 *   <li>기존 파일과 해시가 일치하는 것이 확인되었을 때</li>
 *   <li>중복 파일이 데이터베이스에 기록되기 전</li>
 * </ul>
 *
 * <h3>사용 예시 - 이벤트 발행</h3>
 * <pre>{@code
 * public class UploadedFile extends AggregateRoot<UploadId> {
 *     public static UploadedFile createDuplicate(...) {
 *         UploadedFile duplicateFile = new UploadedFile(...);
 *
 *         // 이벤트 발행
 *         duplicateFile.addDomainEvent(new DuplicateFileDetectedEvent(
 *             duplicateFile.getId(),  // 새로 업로드된 파일 ID
 *             originalUploadId,       // 기존 원본 파일 ID
 *             duplicateFile.getFileNameValue(),
 *             duplicateFile.getFileHashValue(),
 *             duplicateFile.getUploadedAt()
 *         ));
 *
 *         return duplicateFile;
 *     }
 * }
 * }</pre>
 *
 * <h3>사용 예시 - 이벤트 구독 (경고 로깅)</h3>
 * <pre>{@code
 * @Component
 * @Slf4j
 * public class DuplicateFileEventHandler {
 *
 *     @EventListener
 *     public void handleDuplicateDetected(DuplicateFileDetectedEvent event) {
 *         log.warn("Duplicate file detected: {} (original: {})",
 *             event.duplicateUploadId(), event.originalUploadId());
 *
 *         log.info("File name: {}, Hash: {}",
 *             event.fileName(), event.fileHash().substring(0, 8));
 *     }
 * }
 * }</pre>
 *
 * <h3>사용 예시 - 이벤트 구독 (중복 처리 정책)</h3>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class DuplicateFileHandler {
 *
 *     private final UploadedFileRepository repository;
 *     private final NotificationService notificationService;
 *
 *     @EventListener
 *     public void handleDuplicateDetected(DuplicateFileDetectedEvent event) {
 *         // 1. 원본 파일 조회
 *         UploadedFile original = repository.findById(event.originalUploadId())
 *             .orElseThrow();
 *
 *         // 2. 중복 파일 통계 업데이트
 *         updateDuplicateStatistics(event);
 *
 *         // 3. 사용자에게 알림 (선택사항)
 *         notificationService.notifyDuplicateFile(
 *             event.fileName(),
 *             original.getUploadedAt(),
 *             event.detectedAt()
 *         );
 *
 *         // 4. 자동 정리 정책 적용 (선택사항)
 *         if (shouldAutoDelete(event)) {
 *             deleteFile(event.duplicateUploadId());
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>사용 예시 - 이벤트 구독 (메트릭 수집)</h3>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class UploadMetricsCollector {
 *
 *     private final MeterRegistry meterRegistry;
 *
 *     @EventListener
 *     public void onDuplicateDetected(DuplicateFileDetectedEvent event) {
 *         // Prometheus 메트릭 증가
 *         meterRegistry.counter("file.upload.duplicates",
 *             "file_name", event.fileName()
 *         ).increment();
 *     }
 * }
 * }</pre>
 *
 * @param duplicateUploadId 중복 파일 ID (새로 업로드된 파일)
 * @param originalUploadId 원본 파일 ID (기존 파일)
 * @param fileName 파일명
 * @param fileHash 파일 해시 (SHA-256)
 * @param detectedAt 중복 감지 일시
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 * @see DomainEvent
 * @see UploadedFile
 */
public record DuplicateFileDetectedEvent(
        UUID eventId,
        LocalDateTime occurredOn,
        UploadId duplicateUploadId,
        UploadId originalUploadId,
        String fileName,
        String fileHash,
        LocalDateTime detectedAt
) implements DomainEvent {

    /**
     * DuplicateFileDetectedEvent 생성자
     *
     * @param duplicateUploadId 중복 파일 ID
     * @param originalUploadId 원본 파일 ID
     * @param fileName 파일명
     * @param fileHash 파일 해시
     * @param detectedAt 감지 일시
     */
    public DuplicateFileDetectedEvent(
            UploadId duplicateUploadId,
            UploadId originalUploadId,
            String fileName,
            String fileHash,
            LocalDateTime detectedAt
    ) {
        this(
                UUID.randomUUID(),
                LocalDateTime.now(),
                duplicateUploadId,
                originalUploadId,
                fileName,
                fileHash,
                detectedAt
        );
    }

    /**
     * 이벤트 타입
     *
     * @return 이벤트 타입 문자열
     */
    @Override
    public String eventType() {
        return "DuplicateFileDetected";
    }
}
