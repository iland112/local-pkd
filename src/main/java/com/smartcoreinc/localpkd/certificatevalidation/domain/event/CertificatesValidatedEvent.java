package com.smartcoreinc.localpkd.certificatevalidation.domain.event;

import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CertificatesValidatedEvent - 인증서 검증 완료 이벤트
 *
 * <p><b>Event-Driven Architecture</b>: 인증서 검증이 완료되었을 때 발행됩니다.</p>
 *
 * <p><b>Event Consumers</b>:</p>
 * <ul>
 *   <li>ProgressService: SSE를 통해 VALIDATION_COMPLETED 상태 전송</li>
 *   <li>LdapIntegrationService: LDAP 업로드 트리거</li>
 *   <li>AuditService: 검증 완료 로그 기록</li>
 *   <li>StatisticsService: 검증 결과 통계 업데이트</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * CertificatesValidatedEvent event = new CertificatesValidatedEvent(
 *     uploadId,           // 원본 업로드 파일 ID
 *     validCertIds,
 *     invalidCertIds,
 *     validCrlIds,
 *     invalidCrlIds,
 *     LocalDateTime.now()
 * );
 * </pre>
 */
@Getter
@ToString
public class CertificatesValidatedEvent implements DomainEvent {

    /**
     * 이벤트 고유 식별자
     */
    private final UUID eventId;

    /**
     * 원본 업로드 파일 ID (File Upload Context)
     */
    private final UUID uploadId;

    private final List<UUID> validCertificateIds;
    private final List<UUID> invalidCertificateIds;
    private final List<UUID> validCrlIds;
    private final List<UUID> invalidCrlIds;


    /**
     * 검증 완료 시각
     */
    private final LocalDateTime completedAt;

    /**
     * 이벤트 발생 시각
     */
    private final LocalDateTime occurredOn;

    public CertificatesValidatedEvent(
        UUID uploadId,
        List<UUID> validCertificateIds,
        List<UUID> invalidCertificateIds,
        List<UUID> validCrlIds,
        List<UUID> invalidCrlIds,
        LocalDateTime completedAt
    ) {
        this.eventId = UUID.randomUUID();
        this.uploadId = uploadId;
        this.validCertificateIds = validCertificateIds;
        this.invalidCertificateIds = invalidCertificateIds;
        this.validCrlIds = validCrlIds;
        this.invalidCrlIds = invalidCrlIds;
        this.completedAt = completedAt;
        this.occurredOn = LocalDateTime.now();
    }

    public int getValidCertificateCount() {
        return validCertificateIds.size();
    }

    public int getInvalidCertificateCount() {
        return invalidCertificateIds.size();
    }

    public int getValidCrlCount() {
        return validCrlIds.size();
    }
    
    public int getInvalidCrlCount() {
        return invalidCrlIds.size();
    }


    /**
     * 검증된 총 항목 수 (성공 + 실패)
     */
    public int getTotalValidated() {
        return getValidCertificateCount() + getInvalidCertificateCount() + getValidCrlCount() + getInvalidCrlCount();
    }

    /**
     * 검증 성공 항목 수
     */
    public int getTotalValid() {
        return getValidCertificateCount() + getValidCrlCount();
    }

    /**
     * 검증 성공률 (%)
     */
    public int getSuccessRate() {
        int total = getTotalValidated();
        if (total == 0) {
            return 0;
        }
        return (int) ((getTotalValid() / (double) total) * 100);
    }

    /**
     * 모든 항목이 유효한지 확인
     */
    public boolean isAllValid() {
        return getInvalidCertificateCount() == 0 && getInvalidCrlCount() == 0;
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
        return "CertificatesValidated";
    }
}
