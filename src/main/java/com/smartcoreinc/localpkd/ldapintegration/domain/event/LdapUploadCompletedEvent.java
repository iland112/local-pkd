package com.smartcoreinc.localpkd.ldapintegration.domain.event;

import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * LDAP Upload Completed Event - LDAP 업로드 완료 이벤트
 *
 * <p><b>Event-Driven Architecture</b>: 검증된 인증서와 CRL이 LDAP 서버에 성공적으로 업로드되었을 때 발행됩니다.</p>
 *
 * <p><b>Event Consumers</b>:</p>
 * <ul>
 *   <li>ProgressService: SSE를 통해 LDAP_SAVING_COMPLETED 상태 전송</li>
 *   <li>UploadHistoryService: 업로드 상태를 COMPLETED로 업데이트</li>
 *   <li>AuditService: 업로드 완료 로그 기록</li>
 *   <li>StatisticsService: 통계 업데이트</li>
 *   <li>NotificationService: 사용자 알림 발송</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * LdapUploadCompletedEvent event = new LdapUploadCompletedEvent(
 *     uploadId,
 *     800,   // uploadedCertificateCount
 *     50,    // uploadedCrlCount
 *     5,     // failedCount
 *     LocalDateTime.now()
 * );
 * </pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-29
 * @see DomainEvent
 */
@Getter
@ToString
public class LdapUploadCompletedEvent implements DomainEvent {

    /**
     * 이벤트 고유 식별자
     */
    private final UUID eventId;

    /**
     * 원본 파일 업로드 ID (File Upload Context)
     */
    private final UUID uploadId;

    /**
     * LDAP에 성공적으로 업로드된 인증서 개수
     */
    private final int uploadedCertificateCount;

    /**
     * LDAP에 성공적으로 업로드된 CRL 개수
     */
    private final int uploadedCrlCount;

    /**
     * LDAP 업로드 실패한 항목 개수
     */
    private final int failedCount;

    /**
     * LDAP 업로드 완료 시각
     */
    private final LocalDateTime completedAt;

    /**
     * 이벤트 발생 시각
     */
    private final LocalDateTime occurredOn;

    /**
     * LdapUploadCompletedEvent 생성자
     *
     * @param uploadId 원본 파일 업로드 ID
     * @param uploadedCertificateCount LDAP에 업로드된 인증서 개수
     * @param uploadedCrlCount LDAP에 업로드된 CRL 개수
     * @param failedCount 업로드 실패한 항목 개수
     * @param completedAt 업로드 완료 시각
     */
    public LdapUploadCompletedEvent(
            UUID uploadId,
            int uploadedCertificateCount,
            int uploadedCrlCount,
            int failedCount,
            LocalDateTime completedAt
    ) {
        this.eventId = UUID.randomUUID();
        this.uploadId = uploadId;
        this.uploadedCertificateCount = uploadedCertificateCount;
        this.uploadedCrlCount = uploadedCrlCount;
        this.failedCount = failedCount;
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
        return "LdapUploadCompleted";
    }

    /**
     * 업로드 성공 여부
     *
     * @return 실패한 항목이 없으면 true
     */
    public boolean isSuccess() {
        return failedCount == 0;
    }

    /**
     * 총 업로드 항목 개수
     *
     * @return uploadedCertificateCount + uploadedCrlCount
     */
    public int getTotalUploaded() {
        return uploadedCertificateCount + uploadedCrlCount;
    }

    /**
     * 총 처리 항목 개수 (성공 + 실패)
     *
     * @return getTotalUploaded() + failedCount
     */
    public int getTotalProcessed() {
        return getTotalUploaded() + failedCount;
    }

    /**
     * 업로드 성공률
     *
     * @return 성공률 (0-100, 나눗셈 오류 시 0)
     */
    public int getSuccessRate() {
        if (getTotalProcessed() == 0) {
            return 0;
        }
        return (int) ((getTotalUploaded() / (double) getTotalProcessed()) * 100);
    }
}
