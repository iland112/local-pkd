package com.smartcoreinc.localpkd.ldapintegration.domain.event;

import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * LDAP Upload Failed Event - LDAP 업로드 실패 이벤트
 *
 * <p><b>Event-Driven Architecture</b>: 인증서 또는 CRL을 LDAP 서버에 업로드하는 과정에서 오류가 발생했을 때 발행됩니다.</p>
 *
 * <p><b>Event Consumers</b>:</p>
 * <ul>
 *   <li>ProgressService: SSE를 통해 FAILED 상태 전송</li>
 *   <li>UploadHistoryService: 업로드 상태를 FAILED로 업데이트, 에러 메시지 저장</li>
 *   <li>AuditService: 실패 로그 기록</li>
 *   <li>NotificationService: 관리자 알림 발송</li>
 *   <li>MonitoringService: 실패율 추적 및 알림</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * LdapUploadFailedEvent event = new LdapUploadFailedEvent(
 *     uploadId,
 *     "LDAP connection timeout: Unable to connect to ldap://192.168.100.10:389",
 *     10,   // attemptedCount
 *     LocalDateTime.now()
 * );
 * </pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-21
 * @see DomainEvent
 */
@Getter
@ToString
public class LdapUploadFailedEvent implements DomainEvent {

    /**
     * 이벤트 고유 식별자
     */
    private final UUID eventId;

    /**
     * 원본 파일 업로드 ID (File Upload Context)
     */
    private final UUID uploadId;

    /**
     * 오류 메시지
     */
    private final String errorMessage;

    /**
     * 업로드 시도한 총 항목 개수
     */
    private final int attemptedCount;

    /**
     * 업로드 실패 시각
     */
    private final LocalDateTime failedAt;

    /**
     * 이벤트 발생 시각
     */
    private final LocalDateTime occurredOn;

    /**
     * LdapUploadFailedEvent 생성자
     *
     * @param uploadId 원본 파일 업로드 ID
     * @param errorMessage 오류 메시지
     * @param attemptedCount 업로드 시도한 총 항목 개수
     * @param failedAt 실패 시각
     */
    public LdapUploadFailedEvent(
            UUID uploadId,
            String errorMessage,
            int attemptedCount,
            LocalDateTime failedAt
    ) {
        this.eventId = UUID.randomUUID();
        this.uploadId = uploadId;
        this.errorMessage = errorMessage;
        this.attemptedCount = attemptedCount;
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
        return "LdapUploadFailed";
    }

    /**
     * 에러 메시지 요약 (100자 제한)
     *
     * @return 요약된 에러 메시지
     */
    public String getShortErrorMessage() {
        if (errorMessage == null) {
            return "Unknown error";
        }
        return errorMessage.length() > 100 ?
                errorMessage.substring(0, 97) + "..." :
                errorMessage;
    }
}
