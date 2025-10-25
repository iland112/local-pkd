package com.smartcoreinc.localpkd.certificatevalidation.domain.event;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * TrustChainVerifiedEvent - Trust Chain 검증됨 Domain Event
 *
 * <p><b>발행 시점</b>: End Entity Certificate부터 Trust Anchor (CSCA)까지의 Trust Chain 검증이 완료될 때</p>
 *
 * <p><b>Trust Chain 구조</b>:</p>
 * <pre>
 * CSCA (Root CA - Trust Anchor)
 *   ↓ (signed by)
 * DSC (Intermediate CA)
 *   ↓ (signed by)
 * Document Signer Certificate (End Entity)
 * </pre>
 *
 * <p><b>용도</b>:</p>
 * <ul>
 *   <li>Trust Chain 검증 완료 알림</li>
 *   <li>신뢰 관계 성립 확인</li>
 *   <li>LDAP Integration 트리거</li>
 *   <li>감사 로그 및 통계 수집</li>
 * </ul>
 *
 * <p><b>검증 항목</b>:</p>
 * <ul>
 *   <li>End Entity부터 Root CA까지의 연속된 서명 검증</li>
 *   <li>각 인증서의 유효기간 확인</li>
 *   <li>Trust Anchor 도달 확인</li>
 *   <li>체인 무결성 검증</li>
 * </ul>
 *
 * @see com.smartcoreinc.localpkd.certificatevalidation.application.usecase.VerifyTrustChainUseCase
 * @see DomainEvent
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Getter
public class TrustChainVerifiedEvent implements DomainEvent {

    /**
     * 이벤트 고유 식별자
     */
    private final UUID eventId;

    /**
     * 이벤트 발생 시간
     */
    private final LocalDateTime occurredOn;

    /**
     * 검증된 End Entity Certificate ID
     */
    private final CertificateId endEntityCertificateId;

    /**
     * Trust Anchor (CSCA) Certificate ID
     */
    private final CertificateId trustAnchorCertificateId;

    /**
     * Trust Chain 깊이 (레벨 수)
     * <p>예: 2 = End Entity + CSCA, 3 = End Entity + DSC + CSCA</p>
     */
    private final int chainDepth;

    /**
     * Trust Chain 검증 결과
     * <p>true: 체인 유효, false: 체인 무효</p>
     */
    private final boolean chainValid;

    /**
     * Trust Anchor 국가 코드 (선택사항)
     * <p>예: "KR", "US"</p>
     */
    private final String trustAnchorCountryCode;

    /**
     * TrustChainVerifiedEvent 생성
     *
     * @param endEntityCertificateId End Entity Certificate ID
     * @param trustAnchorCertificateId Trust Anchor Certificate ID
     * @param chainDepth 체인 깊이
     * @param chainValid 체인 검증 결과
     * @param trustAnchorCountryCode Trust Anchor 국가 코드
     * @throws IllegalArgumentException 필수 필드가 null인 경우
     */
    public TrustChainVerifiedEvent(
        CertificateId endEntityCertificateId,
        CertificateId trustAnchorCertificateId,
        int chainDepth,
        boolean chainValid,
        String trustAnchorCountryCode
    ) {
        if (endEntityCertificateId == null) {
            throw new IllegalArgumentException("endEntityCertificateId cannot be null");
        }
        if (trustAnchorCertificateId == null && chainValid) {
            throw new IllegalArgumentException("trustAnchorCertificateId cannot be null when chain is valid");
        }
        if (chainDepth < 1) {
            throw new IllegalArgumentException("chainDepth must be at least 1");
        }

        this.eventId = UUID.randomUUID();
        this.occurredOn = LocalDateTime.now();
        this.endEntityCertificateId = endEntityCertificateId;
        this.trustAnchorCertificateId = trustAnchorCertificateId;
        this.chainDepth = chainDepth;
        this.chainValid = chainValid;
        this.trustAnchorCountryCode = trustAnchorCountryCode;
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
        return "TrustChainVerified";
    }

    // ========== equals & hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrustChainVerifiedEvent that = (TrustChainVerifiedEvent) o;
        return Objects.equals(eventId, that.eventId) &&
               Objects.equals(endEntityCertificateId, that.endEntityCertificateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, endEntityCertificateId);
    }

    @Override
    public String toString() {
        return String.format(
            "TrustChainVerifiedEvent[eventId=%s, endEntity=%s, trustAnchor=%s, depth=%d, valid=%b, country=%s, occurredOn=%s]",
            eventId,
            endEntityCertificateId != null ? endEntityCertificateId.getId() : "null",
            trustAnchorCertificateId != null ? trustAnchorCertificateId.getId() : "null",
            chainDepth,
            chainValid,
            trustAnchorCountryCode,
            occurredOn
        );
    }
}
