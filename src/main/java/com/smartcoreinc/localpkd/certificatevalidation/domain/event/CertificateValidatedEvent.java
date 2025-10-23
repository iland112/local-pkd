package com.smartcoreinc.localpkd.certificatevalidation.domain.event;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateStatus;
import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * CertificateValidatedEvent - 인증서 검증됨 Domain Event
 *
 * <p><b>발행 시점</b>: Certificate의 검증이 완료되고 결과가 기록될 때</p>
 *
 * <p><b>용도</b>:</p>
 * <ul>
 *   <li>인증서 검증 결과 기록</li>
 *   <li>LDAP Integration Context에 알림</li>
 *   <li>감사(Audit) 로그 및 통계 수집</li>
 * </ul>
 *
 * @see com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate#recordValidation
 * @see DomainEvent
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-23
 */
public class CertificateValidatedEvent implements DomainEvent {

    /**
     * 이벤트 고유 식별자
     */
    private final UUID eventId;

    /**
     * 이벤트 발생 시간
     */
    private final LocalDateTime occurredOn;

    /**
     * 검증된 인증서의 ID
     */
    private final CertificateId certificateId;

    /**
     * 검증 결과 상태
     */
    private final CertificateStatus validationStatus;

    /**
     * CertificateValidatedEvent 생성
     *
     * @param certificateId 검증된 인증서의 ID
     * @param validationStatus 검증 결과 상태
     * @throws IllegalArgumentException 필수 필드가 null인 경우
     */
    public CertificateValidatedEvent(CertificateId certificateId, CertificateStatus validationStatus) {
        if (certificateId == null) {
            throw new IllegalArgumentException("certificateId cannot be null");
        }
        if (validationStatus == null) {
            throw new IllegalArgumentException("validationStatus cannot be null");
        }
        this.eventId = UUID.randomUUID();
        this.occurredOn = LocalDateTime.now();
        this.certificateId = certificateId;
        this.validationStatus = validationStatus;
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
        return "CertificateValidated";
    }

    // ========== Getters ==========

    public CertificateId getCertificateId() {
        return certificateId;
    }

    public CertificateStatus getValidationStatus() {
        return validationStatus;
    }

    // ========== equals & hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CertificateValidatedEvent that = (CertificateValidatedEvent) o;
        return Objects.equals(eventId, that.eventId) &&
               Objects.equals(certificateId, that.certificateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, certificateId);
    }

    @Override
    public String toString() {
        return String.format("CertificateValidatedEvent[eventId=%s, certificateId=%s, status=%s, occurredOn=%s]",
            eventId, certificateId != null ? certificateId.getId() : "null", validationStatus, occurredOn);
    }
}
