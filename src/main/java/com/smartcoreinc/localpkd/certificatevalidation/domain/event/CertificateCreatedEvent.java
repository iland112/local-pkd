package com.smartcoreinc.localpkd.certificatevalidation.domain.event;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * CertificateCreatedEvent - 인증서 생성됨 Domain Event
 *
 * <p><b>발행 시점</b>: Certificate Aggregate Root가 생성될 때</p>
 *
 * <p><b>용도</b>:</p>
 * <ul>
 *   <li>인증서 생성 이벤트 기록</li>
 *   <li>다른 Bounded Context에 알림</li>
 *   <li>감사(Audit) 로그 작성</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // 이벤트 발행
 * Certificate cert = Certificate.create(...);
 * // → CertificateCreatedEvent 자동 추가
 *
 * // Repository save 시 자동 발행
 * repository.save(cert);
 * // → EventBus.publishAll() 호출
 *
 * // Event Handler에서 구독
 * @EventListener
 * public void handleCertificateCreated(CertificateCreatedEvent event) {
 *     log.info("새로운 인증서 생성됨: {}", event.getCertificateId());
 * }
 * }</pre>
 *
 * @see com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate
 * @see DomainEvent
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-23
 */
public class CertificateCreatedEvent implements DomainEvent {

    /**
     * 이벤트 고유 식별자
     */
    private final UUID eventId;

    /**
     * 이벤트 발생 시간
     */
    private final LocalDateTime occurredOn;

    /**
     * 생성된 인증서의 ID
     */
    private final CertificateId certificateId;

    /**
     * CertificateCreatedEvent 생성
     *
     * @param certificateId 생성된 인증서의 ID
     * @throws IllegalArgumentException certificateId가 null인 경우
     */
    public CertificateCreatedEvent(CertificateId certificateId) {
        if (certificateId == null) {
            throw new IllegalArgumentException("certificateId cannot be null");
        }
        this.eventId = UUID.randomUUID();
        this.occurredOn = LocalDateTime.now();
        this.certificateId = certificateId;
    }

    // ========== DomainEvent Implementation ==========

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
        return "CertificateCreated";
    }

    // ========== Getters ==========

    public CertificateId getCertificateId() {
        return certificateId;
    }

    // ========== equals & hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CertificateCreatedEvent that = (CertificateCreatedEvent) o;
        return Objects.equals(eventId, that.eventId) &&
               Objects.equals(certificateId, that.certificateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, certificateId);
    }

    @Override
    public String toString() {
        return String.format("CertificateCreatedEvent[eventId=%s, certificateId=%s, occurredOn=%s]",
            eventId, certificateId != null ? certificateId.getId() : "null", occurredOn);
    }
}
