package com.smartcoreinc.localpkd.certificatevalidation.domain.event;

import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
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
 *     795,                // validCertificateCount
 *     5,                  // invalidCertificateCount
 *     48,                 // validCrlCount
 *     2,                  // invalidCrlCount
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

    /**
     * 검증 성공한 인증서 개수
     */
    private final int validCertificateCount;

    /**
     * 검증 실패한 인증서 개수
     */
    private final int invalidCertificateCount;

    /**
     * 검증 성공한 CRL 개수
     */
    private final int validCrlCount;

    /**
     * 검증 실패한 CRL 개수
     */
    private final int invalidCrlCount;

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
        int validCertificateCount,
        int invalidCertificateCount,
        int validCrlCount,
        int invalidCrlCount,
        LocalDateTime completedAt
    ) {
        this.eventId = UUID.randomUUID();
        this.uploadId = uploadId;
        this.validCertificateCount = validCertificateCount;
        this.invalidCertificateCount = invalidCertificateCount;
        this.validCrlCount = validCrlCount;
        this.invalidCrlCount = invalidCrlCount;
        this.completedAt = completedAt;
        this.occurredOn = LocalDateTime.now();
    }

    /**
     * 검증된 총 항목 수 (성공 + 실패)
     */
    public int getTotalValidated() {
        return validCertificateCount + invalidCertificateCount + validCrlCount + invalidCrlCount;
    }

    /**
     * 검증 성공 항목 수
     */
    public int getTotalValid() {
        return validCertificateCount + validCrlCount;
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
        return invalidCertificateCount == 0 && invalidCrlCount == 0;
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
