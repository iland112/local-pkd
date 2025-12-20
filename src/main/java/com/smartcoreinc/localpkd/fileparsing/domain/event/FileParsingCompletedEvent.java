package com.smartcoreinc.localpkd.fileparsing.domain.event;

import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FileParsingCompletedEvent - 파일 파싱 완료 이벤트
 *
 * <p><b>Event-Driven Architecture</b>: 파일 파싱이 성공적으로 완료되었을 때 발행됩니다.</p>
 *
 * <p><b>Event Consumers</b>:</p>
 * <ul>
 *   <li>ProgressService: SSE를 통해 PARSING_COMPLETED 상태 전송</li>
 *   <li>CertificateValidationService: 인증서 검증 시작 트리거</li>
 *   <li>AuditService: 파싱 완료 로그 기록</li>
 *   <li>StatisticsService: 통계 업데이트</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * FileParsingCompletedEvent event = new FileParsingCompletedEvent(
 *     parsedFileId,
 *     uploadId,
 *     800,  // certificateCount
 *     50,   // crlCount
 *     850,  // totalProcessed
 *     LocalDateTime.now()
 * );
 * </pre>
 */
@Getter
@ToString
public class FileParsingCompletedEvent implements DomainEvent {

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
     * 추출된 인증서 개수
     */
    private final int certificateCount;

    /**
     * 추출된 CRL 개수
     */
    private final int crlCount;

    /**
     * 처리된 엔트리 총 개수
     */
    private final int totalProcessed;

    /**
     * 파싱 완료 시각
     */
    private final LocalDateTime completedAt;

    /**
     * 이벤트 발생 시각
     */
    private final LocalDateTime occurredOn;

    public FileParsingCompletedEvent(
        UUID parsedFileId,
        UUID uploadId,
        int certificateCount,
        int crlCount,
        int totalProcessed,
        LocalDateTime completedAt
    ) {
        this.eventId = UUID.randomUUID();
        this.parsedFileId = parsedFileId;
        this.uploadId = uploadId;
        this.certificateCount = certificateCount;
        this.crlCount = crlCount;
        this.totalProcessed = totalProcessed;
        this.completedAt = completedAt;
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
        return "FileParsingCompletedEvent";
    }
}
