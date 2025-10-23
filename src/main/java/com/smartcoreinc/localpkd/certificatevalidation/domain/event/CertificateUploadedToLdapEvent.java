package com.smartcoreinc.localpkd.certificatevalidation.domain.event;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * CertificateUploadedToLdapEvent - 인증서 LDAP 업로드됨 Domain Event
 *
 * <p><b>발행 시점</b>: Certificate가 OpenLDAP 디렉토리에 저장될 때</p>
 *
 * <p><b>용도</b>:</p>
 * <ul>
 *   <li>LDAP 업로드 완료 이벤트 기록</li>
 *   <li>다른 Bounded Context에 알림</li>
 *   <li>감사(Audit) 로그 작성</li>
 *   <li>통계 및 모니터링</li>
 * </ul>
 *
 * @see com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate#markAsUploadedToLdap
 * @see DomainEvent
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-23
 */
public class CertificateUploadedToLdapEvent implements DomainEvent {

    /**
     * 이벤트 고유 식별자
     */
    private final UUID eventId;

    /**
     * 이벤트 발생 시간
     */
    private final LocalDateTime occurredOn;

    /**
     * LDAP에 업로드된 인증서의 ID
     */
    private final CertificateId certificateId;

    /**
     * CertificateUploadedToLdapEvent 생성
     *
     * @param certificateId LDAP에 업로드된 인증서의 ID
     * @throws IllegalArgumentException certificateId가 null인 경우
     */
    public CertificateUploadedToLdapEvent(CertificateId certificateId) {
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
        return "CertificateUploadedToLdap";
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
        CertificateUploadedToLdapEvent that = (CertificateUploadedToLdapEvent) o;
        return Objects.equals(eventId, that.eventId) &&
               Objects.equals(certificateId, that.certificateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, certificateId);
    }

    @Override
    public String toString() {
        return String.format("CertificateUploadedToLdapEvent[eventId=%s, certificateId=%s, occurredOn=%s]",
            eventId, certificateId != null ? certificateId.getId() : "null", occurredOn);
    }
}
