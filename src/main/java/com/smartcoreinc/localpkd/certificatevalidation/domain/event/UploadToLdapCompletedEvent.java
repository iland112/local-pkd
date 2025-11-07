package com.smartcoreinc.localpkd.certificatevalidation.domain.event;

import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * UploadToLdapCompletedEvent - LDAP 업로드 완료 이벤트 (Certificate Validation Context)
 *
 * <p><b>Event-Driven Architecture</b>: 인증서 검증이 완료되고 LDAP 업로드가 시작된 후,
 * 업로드 프로세스가 완료되었을 때 발행됩니다.</p>
 *
 * <p><b>Event Flow</b>:
 * <ol>
 *   <li>CertificatesValidatedEvent 발행 (ValidateCertificatesUseCase)</li>
 *   <li>UploadToLdapEventHandler 수신</li>
 *   <li>UploadToLdapUseCase 실행</li>
 *   <li>UploadToLdapCompletedEvent 발행 (이 이벤트)</li>
 *   <li>LdapUploadEventHandler 수신</li>
 *   <li>LdapUploadCompletedEvent 발행 (LDAP 컨텍스트에서)</li>
 * </ol>
 * </p>
 *
 * <p><b>Event Consumers</b>:
 * <ul>
 *   <li>LdapUploadEventHandler: 최종 완료 처리, UploadHistory 상태 업데이트</li>
 *   <li>ProgressService: SSE를 통해 LDAP_SAVING_COMPLETED 상태 전송</li>
 *   <li>AuditService: 업로드 완료 로그 기록</li>
 * </ul>
 * </p>
 *
 * <p><b>사용 예시</b>:
 * <pre>
 * UploadToLdapCompletedEvent event = new UploadToLdapCompletedEvent(
 *     uploadId,
 *     800,   // successCount
 *     5,     // failureCount
 *     LocalDateTime.now()
 * );
 * </pre>
 * </p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-07 (Phase 17 Task 2.1)
 * @see DomainEvent
 * @see CertificatesValidatedEvent
 */
@Getter
@ToString
public class UploadToLdapCompletedEvent implements DomainEvent {

    /**
     * 이벤트 고유 식별자
     */
    private final UUID eventId;

    /**
     * 원본 파일 업로드 ID (File Upload Context)
     */
    private final UUID uploadId;

    /**
     * LDAP에 성공적으로 업로드된 항목 개수
     */
    private final int successCount;

    /**
     * LDAP 업로드 실패한 항목 개수
     */
    private final int failureCount;

    /**
     * LDAP 업로드 완료 시각
     */
    private final LocalDateTime completedAt;

    /**
     * 이벤트 발생 시각
     */
    private final LocalDateTime occurredOn;

    /**
     * UploadToLdapCompletedEvent 생성자
     *
     * @param uploadId 원본 파일 업로드 ID
     * @param successCount LDAP에 성공적으로 업로드된 항목 개수
     * @param failureCount 업로드 실패한 항목 개수
     * @param completedAt 업로드 완료 시각
     */
    public UploadToLdapCompletedEvent(
            UUID uploadId,
            int successCount,
            int failureCount,
            LocalDateTime completedAt
    ) {
        this.eventId = UUID.randomUUID();
        this.uploadId = uploadId;
        this.successCount = successCount;
        this.failureCount = failureCount;
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
        return "UploadToLdapCompleted";
    }

    /**
     * 업로드 성공 여부
     *
     * @return 실패한 항목이 없으면 true
     */
    public boolean isSuccess() {
        return failureCount == 0;
    }

    /**
     * 총 처리 항목 개수 (성공 + 실패)
     *
     * @return successCount + failureCount
     */
    public int getTotalProcessed() {
        return successCount + failureCount;
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
        return (int) ((successCount / (double) getTotalProcessed()) * 100);
    }
}
