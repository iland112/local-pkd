package com.smartcoreinc.localpkd.fileparsing.domain.event;

import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ParsingFailedEvent - 파일 파싱 실패 이벤트
 *
 * <p><b>Event-Driven Architecture</b>: 파일 파싱 중 오류가 발생하여 실패했을 때 발행됩니다.</p>
 *
 * <p><b>Event Consumers</b>:</p>
 * <ul>
 *   <li>ProgressService: SSE를 통해 FAILED 상태 전송</li>
 *   <li>NotificationService: 관리자에게 실패 알림 전송</li>
 *   <li>AuditService: 파싱 실패 로그 기록</li>
 *   <li>ErrorTrackingService: 오류 추적 및 모니터링</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * ParsingFailedEvent event = new ParsingFailedEvent(
 *     parsedFileId,
 *     uploadId,
 *     "Invalid LDIF format: Missing required attribute",
 *     LocalDateTime.now()
 * );
 * </pre>
 */
@Getter
@ToString
public class ParsingFailedEvent implements DomainEvent {

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
     * 오류 메시지
     */
    private final String errorMessage;

    /**
     * 파싱 실패 시각
     */
    private final LocalDateTime failedAt;

    /**
     * 이벤트 발생 시각
     */
    private final LocalDateTime occurredOn;

    public ParsingFailedEvent(
        UUID parsedFileId,
        UUID uploadId,
        String errorMessage,
        LocalDateTime failedAt
    ) {
        this.eventId = UUID.randomUUID();
        this.parsedFileId = parsedFileId;
        this.uploadId = uploadId;
        this.errorMessage = errorMessage;
        this.failedAt = failedAt;
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
        return "ParsingFailedEvent";
    }

    /**
     * 오류 메시지 존재 여부
     */
    public boolean hasErrorMessage() {
        return errorMessage != null && !errorMessage.isBlank();
    }
}
