package com.smartcoreinc.localpkd.fileparsing.domain.event;

import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FileParsingStartedEvent - 파일 파싱 시작 이벤트
 *
 * <p><b>Event-Driven Architecture</b>: 파일 파싱이 시작되었을 때 발행됩니다.</p>
 *
 * <p><b>Event Consumers</b>:</p>
 * <ul>
 *   <li>ProgressService: SSE를 통해 실시간 진행 상황 전송 (PARSING_STARTED)</li>
 *   <li>AuditService: 파싱 시작 로그 기록</li>
 *   <li>StatisticsService: 통계 업데이트</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * FileParsingStartedEvent event = new FileParsingStartedEvent(
 *     parsedFileId,
 *     uploadId,
 *     "CSCA_COMPLETE_LDIF",
 *     LocalDateTime.now()
 * );
 *
 * // Event 발행 (Aggregate Root 내부에서)
 * addDomainEvent(event);
 * </pre>
 */
@Getter
@ToString
public class FileParsingStartedEvent implements DomainEvent {

    /**
     * 이벤트 고유 식별자
     */
    private final UUID eventId;

    /**
     * 파싱 파일 ID
     */
    private final UUID parsedFileId;

    /**
     * 원본 업로드 파일 ID
     */
    private final UUID uploadId;

    /**
     * 파일 포맷
     */
    private final String fileFormat;

    /**
     * 파싱 시작 시각
     */
    private final LocalDateTime startedAt;

    /**
     * 이벤트 발생 시각
     */
    private final LocalDateTime occurredOn;

    public FileParsingStartedEvent(
        UUID parsedFileId,
        UUID uploadId,
        String fileFormat,
        LocalDateTime startedAt
    ) {
        this.eventId = UUID.randomUUID();
        this.parsedFileId = parsedFileId;
        this.uploadId = uploadId;
        this.fileFormat = fileFormat;
        this.startedAt = startedAt;
        this.occurredOn = LocalDateTime.now();
    }

    @Override
    public UUID eventId() {
        return eventId;
    }

    @Override
    public LocalDateTime occurredOn() {
        return occurredOn;
    }

    @Override
    public String eventType() {
        return "FileParsingStartedEvent";
    }
}
