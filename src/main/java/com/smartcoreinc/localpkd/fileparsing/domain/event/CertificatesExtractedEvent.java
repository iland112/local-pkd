package com.smartcoreinc.localpkd.fileparsing.domain.event;

import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CertificatesExtractedEvent - 인증서 추출 완료 이벤트
 *
 * <p><b>Event-Driven Architecture</b>: 파일에서 인증서 및 CRL이 성공적으로 추출되었을 때 발행됩니다.</p>
 *
 * <p><b>Event Consumers</b>:</p>
 * <ul>
 *   <li>CertificateValidationService: 인증서 검증 시작 (Trust Chain, CRL 체크)</li>
 *   <li>StatisticsService: 인증서 추출 통계 업데이트</li>
 *   <li>AuditService: 인증서 추출 로그 기록</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * CertificatesExtractedEvent event = new CertificatesExtractedEvent(
 *     parsedFileId,
 *     uploadId,
 *     800,  // certificateCount
 *     50    // crlCount
 * );
 * </pre>
 */
@Getter
@ToString
public class CertificatesExtractedEvent implements DomainEvent {

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
     * 이벤트 발생 시각
     */
    private final LocalDateTime occurredOn;

    public CertificatesExtractedEvent(
        UUID parsedFileId,
        UUID uploadId,
        int certificateCount,
        int crlCount
    ) {
        this.eventId = UUID.randomUUID();
        this.parsedFileId = parsedFileId;
        this.uploadId = uploadId;
        this.certificateCount = certificateCount;
        this.crlCount = crlCount;
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
        return "CertificatesExtractedEvent";
    }

    /**
     * 전체 추출 항목 수 (인증서 + CRL)
     */
    public int getTotalExtracted() {
        return certificateCount + crlCount;
    }

    /**
     * 인증서 추출 성공 여부
     */
    public boolean hasCertificates() {
        return certificateCount > 0;
    }

    /**
     * CRL 추출 성공 여부
     */
    public boolean hasCrls() {
        return crlCount > 0;
    }
}
