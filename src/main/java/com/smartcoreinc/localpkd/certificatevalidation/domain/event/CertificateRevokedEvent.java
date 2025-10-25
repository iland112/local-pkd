package com.smartcoreinc.localpkd.certificatevalidation.domain.event;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * CertificateRevokedEvent - 인증서 폐기됨 Domain Event
 *
 * <p><b>발행 시점</b>: Certificate Revocation List (CRL) 확인을 통해 인증서가 폐기된 것으로 판명될 때</p>
 *
 * <p><b>폐기 사유 코드</b>:</p>
 * <ul>
 *   <li>0 = unspecified: 사유 불명</li>
 *   <li>1 = keyCompromise: 개인키 유출</li>
 *   <li>2 = cACompromise: CA 유출</li>
 *   <li>3 = superseded: 대체됨</li>
 *   <li>4 = cessationOfOperation: 운영 중단</li>
 *   <li>5 = certificateHold: 임시 보류</li>
 *   <li>6 = removeFromCRL: CRL 제거</li>
 * </ul>
 *
 * <p><b>용도</b>:</p>
 * <ul>
 *   <li>인증서 폐기 상태 업데이트</li>
 *   <li>보안 알림 (폐기된 인증서로 검증 불가)</li>
 *   <li>감사 로그 기록</li>
 *   <li>통계 수집 (폐기된 인증서 추적)</li>
 *   <li>LDAP 엔트리 비활성화</li>
 * </ul>
 *
 * <p><b>보안 영향</b>:</p>
 * <ul>
 *   <li>이 인증서로 서명된 모든 문서는 신뢰할 수 없음</li>
 *   <li>이 인증서를 기반으로 한 Trust Chain도 무효화</li>
 *   <li>즉시 검증 거부 필요</li>
 * </ul>
 *
 * @see com.smartcoreinc.localpkd.certificatevalidation.application.usecase.CheckRevocationUseCase
 * @see DomainEvent
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Getter
public class CertificateRevokedEvent implements DomainEvent {

    /**
     * 이벤트 고유 식별자
     */
    private final UUID eventId;

    /**
     * 이벤트 발생 시간
     */
    private final LocalDateTime occurredOn;

    /**
     * 폐기된 인증서의 ID
     */
    private final CertificateId certificateId;

    /**
     * 인증서 일련번호
     */
    private final String serialNumber;

    /**
     * 인증서 발급자 DN
     */
    private final String issuerDn;

    /**
     * 폐기 날짜 (UTC)
     */
    private final LocalDateTime revokedAt;

    /**
     * 폐기 사유 코드 (RFC 5280)
     * <p>0: unspecified, 1: keyCompromise, 2: cACompromise, 3: superseded, 등</p>
     */
    private final int revocationReasonCode;

    /**
     * 폐기 사유 설명
     * <p>사람이 읽을 수 있는 형식의 폐기 사유</p>
     */
    private final String revocationReason;

    /**
     * CRL 버전
     * <p>폐기 정보를 확인한 CRL 버전</p>
     */
    private final long crlVersion;

    /**
     * CertificateRevokedEvent 생성
     *
     * @param certificateId 폐기된 인증서 ID
     * @param serialNumber 인증서 일련번호
     * @param issuerDn 발급자 DN
     * @param revokedAt 폐기 날짜
     * @param revocationReasonCode 폐기 사유 코드
     * @param revocationReason 폐기 사유 설명
     * @param crlVersion CRL 버전
     * @throws IllegalArgumentException 필수 필드가 null인 경우
     */
    public CertificateRevokedEvent(
        CertificateId certificateId,
        String serialNumber,
        String issuerDn,
        LocalDateTime revokedAt,
        int revocationReasonCode,
        String revocationReason,
        long crlVersion
    ) {
        if (certificateId == null) {
            throw new IllegalArgumentException("certificateId cannot be null");
        }
        if (serialNumber == null || serialNumber.isBlank()) {
            throw new IllegalArgumentException("serialNumber cannot be null or blank");
        }
        if (issuerDn == null || issuerDn.isBlank()) {
            throw new IllegalArgumentException("issuerDn cannot be null or blank");
        }
        if (revokedAt == null) {
            throw new IllegalArgumentException("revokedAt cannot be null");
        }
        if (revocationReasonCode < 0 || revocationReasonCode > 6) {
            throw new IllegalArgumentException("revocationReasonCode must be 0-6");
        }

        this.eventId = UUID.randomUUID();
        this.occurredOn = LocalDateTime.now();
        this.certificateId = certificateId;
        this.serialNumber = serialNumber;
        this.issuerDn = issuerDn;
        this.revokedAt = revokedAt;
        this.revocationReasonCode = revocationReasonCode;
        this.revocationReason = revocationReason != null ? revocationReason : "Unknown";
        this.crlVersion = crlVersion;
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
        return "CertificateRevoked";
    }

    // ========== Utility Methods ==========

    /**
     * 폐기 사유명을 반환합니다
     *
     * @return 폐기 사유 한글명
     */
    public String getRevocationReasonName() {
        return switch (revocationReasonCode) {
            case 0 -> "Unspecified";
            case 1 -> "Key Compromise";
            case 2 -> "CA Compromise";
            case 3 -> "Superseded";
            case 4 -> "Cessation of Operation";
            case 5 -> "Certificate Hold";
            case 6 -> "Remove From CRL";
            default -> "Unknown";
        };
    }

    // ========== equals & hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CertificateRevokedEvent that = (CertificateRevokedEvent) o;
        return Objects.equals(eventId, that.eventId) &&
               Objects.equals(certificateId, that.certificateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, certificateId);
    }

    @Override
    public String toString() {
        return String.format(
            "CertificateRevokedEvent[eventId=%s, certificateId=%s, serialNumber=%s, reason=%s(%d), revokedAt=%s, occurredOn=%s]",
            eventId,
            certificateId != null ? certificateId.getId() : "null",
            serialNumber,
            getRevocationReasonName(),
            revocationReasonCode,
            revokedAt,
            occurredOn
        );
    }
}
