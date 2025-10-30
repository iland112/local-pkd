package com.smartcoreinc.localpkd.fileupload.domain.event;

import com.smartcoreinc.localpkd.fileupload.domain.model.ProcessingMode;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.shared.domain.DomainEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * File Uploaded Event - 파일 업로드 완료 이벤트
 *
 * <p>새로운 파일이 성공적으로 업로드되었을 때 발행되는 도메인 이벤트입니다.
 * 이 이벤트를 구독하여 파일 파싱, 검증, LDAP 업로드 등의 후속 작업을 트리거할 수 있습니다.</p>
 *
 * <h3>이벤트 발행 시점</h3>
 * <ul>
 *   <li>새로운 파일이 처음 업로드되었을 때</li>
 *   <li>파일 저장소에 물리적으로 저장된 후</li>
 *   <li>데이터베이스에 메타데이터가 기록되기 전</li>
 * </ul>
 *
 * <h3>사용 예시 - 이벤트 발행</h3>
 * <pre>{@code
 * public class UploadedFile extends AggregateRoot<UploadId> {
 *     public static UploadedFile create(...) {
 *         UploadedFile file = new UploadedFile(...);
 *
 *         // 이벤트 발행
 *         file.addDomainEvent(new FileUploadedEvent(
 *             file.getId(),
 *             file.getFileNameValue(),
 *             file.getFileHashValue(),
 *             file.getFileSizeBytes(),
 *             file.getUploadedAt()
 *         ));
 *
 *         return file;
 *     }
 * }
 * }</pre>
 *
 * <h3>사용 예시 - 이벤트 구독 (동기)</h3>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * @Slf4j
 * public class FileUploadEventHandler {
 *
 *     @EventListener
 *     public void handleFileUploaded(FileUploadedEvent event) {
 *         log.info("File uploaded: {} ({})",
 *             event.fileName(), event.uploadId());
 *
 *         // 동기적으로 처리할 작업
 *         // 예: 업로드 통계 업데이트
 *     }
 * }
 * }</pre>
 *
 * <h3>사용 예시 - 이벤트 구독 (비동기)</h3>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * @Slf4j
 * public class FileParsingEventHandler {
 *
 *     private final ParseFileUseCase parseFileUseCase;
 *
 *     @Async
 *     @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 *     public void handleFileUploadedAsync(FileUploadedEvent event) {
 *         log.info("Starting async file parsing for: {}", event.uploadId());
 *
 *         try {
 *             parseFileUseCase.execute(new ParseFileCommand(
 *                 event.uploadId().getId().toString()
 *             ));
 *         } catch (Exception e) {
 *             log.error("File parsing failed: {}", event.uploadId(), e);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>사용 예시 - Repository에서 이벤트 발행</h3>
 * <pre>{@code
 * @Repository
 * @RequiredArgsConstructor
 * public class JpaUploadedFileRepository implements UploadedFileRepository {
 *
 *     private final JpaRepository<UploadedFile, UploadId> jpaRepository;
 *     private final EventBus eventBus;
 *
 *     @Override
 *     @Transactional
 *     public UploadedFile save(UploadedFile aggregate) {
 *         UploadedFile saved = jpaRepository.save(aggregate);
 *
 *         // 도메인 이벤트 발행
 *         eventBus.publishAll(saved.getDomainEvents());
 *         saved.clearDomainEvents();
 *
 *         return saved;
 *     }
 * }
 * }</pre>
 *
 * @param uploadId 업로드 ID
 * @param fileName 파일명
 * @param fileHash 파일 해시 (SHA-256)
 * @param fileSizeBytes 파일 크기 (바이트)
 * @param uploadedAt 업로드 일시
 * @param processingMode 파일 처리 방식 (AUTO: 자동 처리, MANUAL: 수동 처리)
 *
 * <h3>Phase 18 업데이트 (Dual Mode Processing Support)</h3>
 * <p>ProcessingMode 필드가 추가되었습니다.
 * AUTO 모드일 경우 이벤트 핸들러가 자동으로 다음 단계(파싱)를 시작합니다.
 * MANUAL 모드일 경우 사용자가 수동으로 파싱을 트리거할 때까지 대기합니다.</p>
 *
 * @author SmartCore Inc.
 * @version 1.1 (Phase 18)
 * @since 2025-10-18, updated 2025-10-24
 * @see DomainEvent
 * @see UploadedFile
 * @see ProcessingMode
 */
public record FileUploadedEvent(
        UUID eventId,
        LocalDateTime occurredOn,
        UploadId uploadId,
        String fileName,
        String fileHash,
        long fileSizeBytes,
        LocalDateTime uploadedAt,
        ProcessingMode processingMode
) implements DomainEvent {

    /**
     * FileUploadedEvent 생성자 (ProcessingMode 미포함, 기본값 AUTO)
     *
     * @param uploadId 업로드 ID
     * @param fileName 파일명
     * @param fileHash 파일 해시
     * @param fileSizeBytes 파일 크기 (바이트)
     * @param uploadedAt 업로드 일시
     */
    public FileUploadedEvent(
            UploadId uploadId,
            String fileName,
            String fileHash,
            long fileSizeBytes,
            LocalDateTime uploadedAt
    ) {
        this(uploadId, fileName, fileHash, fileSizeBytes, uploadedAt, ProcessingMode.AUTO);
    }

    /**
     * FileUploadedEvent 생성자 (ProcessingMode 포함)
     *
     * @param uploadId 업로드 ID
     * @param fileName 파일명
     * @param fileHash 파일 해시
     * @param fileSizeBytes 파일 크기 (바이트)
     * @param uploadedAt 업로드 일시
     * @param processingMode 파일 처리 방식 (AUTO 또는 MANUAL)
     */
    public FileUploadedEvent(
            UploadId uploadId,
            String fileName,
            String fileHash,
            long fileSizeBytes,
            LocalDateTime uploadedAt,
            ProcessingMode processingMode
    ) {
        this(
                UUID.randomUUID(),
                LocalDateTime.now(),
                uploadId,
                fileName,
                fileHash,
                fileSizeBytes,
                uploadedAt,
                processingMode
        );
    }

    /**
     * 이벤트 타입
     *
     * @return 이벤트 타입 문자열
     */
    @Override
    public String eventType() {
        return "FileUploaded";
    }
}
