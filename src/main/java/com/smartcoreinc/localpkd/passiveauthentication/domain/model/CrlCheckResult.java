package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * CrlCheckResult - CRL 검증 결과 Value Object
 *
 * <p>ICAO Doc 9303 Part 12 및 RFC 5280 기반 CRL 검증 결과를 표현합니다.</p>
 *
 * <h3>검증 상태 (CrlStatus)</h3>
 * <ul>
 *   <li>VALID: 인증서가 폐기되지 않았으며 CRL이 유효함</li>
 *   <li>REVOKED: 인증서가 폐기됨 (revocationDate, reason 포함)</li>
 *   <li>CRL_UNAVAILABLE: CRL을 LDAP에서 찾을 수 없음</li>
 *   <li>CRL_EXPIRED: CRL의 nextUpdate가 지났음 (만료됨)</li>
 *   <li>CRL_INVALID: CRL 서명 검증 실패 또는 구조 오류</li>
 * </ul>
 *
 * <h3>RFC 5280 CRL Reason Codes</h3>
 * <pre>
 * CRLReason ::= ENUMERATED {
 *     unspecified             (0),
 *     keyCompromise           (1),
 *     cACompromise            (2),
 *     affiliationChanged      (3),
 *     superseded              (4),
 *     cessationOfOperation    (5),
 *     certificateHold         (6),
 *     removeFromCRL           (8),
 *     privilegeWithdrawn      (9),
 *     aACompromise           (10)
 * }
 * </pre>
 *
 * @see com.smartcoreinc.localpkd.passiveauthentication.domain.service.CrlVerificationService
 * @since Phase 4.12
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA용
public class CrlCheckResult {

    /**
     * CRL 검증 상태
     */
    @Enumerated(EnumType.STRING)
    private CrlStatus status;

    /**
     * 인증서 폐기 일시 (REVOKED 상태일 때만 유효)
     */
    private LocalDateTime revocationDate;

    /**
     * 폐기 사유 코드 (RFC 5280)
     * 0=unspecified, 1=keyCompromise, 2=cACompromise, etc.
     */
    private Integer revocationReason;

    /**
     * 폐기 사유 설명 (사람이 읽을 수 있는 형식)
     */
    private String revocationReasonText;

    /**
     * 오류 메시지 (CRL_UNAVAILABLE, CRL_EXPIRED, CRL_INVALID 상태일 때)
     */
    private String errorMessage;

    /**
     * Private 생성자
     */
    private CrlCheckResult(CrlStatus status, LocalDateTime revocationDate,
                           Integer revocationReason, String revocationReasonText,
                           String errorMessage) {
        validate(status, revocationDate, revocationReason);
        this.status = status;
        this.revocationDate = revocationDate;
        this.revocationReason = revocationReason;
        this.revocationReasonText = revocationReasonText;
        this.errorMessage = errorMessage;
    }

    /**
     * 검증 로직
     */
    private void validate(CrlStatus status, LocalDateTime revocationDate, Integer revocationReason) {
        if (status == null) {
            throw new DomainException("CRL_STATUS_NULL", "CRL status cannot be null");
        }

        if (status == CrlStatus.REVOKED && revocationDate == null) {
            throw new DomainException("REVOCATION_DATE_REQUIRED",
                "Revocation date is required when status is REVOKED");
        }

        if (revocationReason != null && (revocationReason < 0 || revocationReason > 10)) {
            throw new DomainException("INVALID_REVOCATION_REASON",
                String.format("Invalid CRL reason code: %d (must be 0-10)", revocationReason));
        }
    }

    // ========== Static Factory Methods ==========

    /**
     * VALID 상태 생성 - 인증서가 폐기되지 않았으며 CRL이 유효함
     */
    public static CrlCheckResult valid() {
        return new CrlCheckResult(CrlStatus.VALID, null, null, null, null);
    }

    /**
     * REVOKED 상태 생성 - 인증서가 폐기됨
     *
     * @param revocationDate 폐기 일시
     * @param reasonCode RFC 5280 CRL reason code (0-10)
     * @return CrlCheckResult
     */
    public static CrlCheckResult revoked(LocalDateTime revocationDate, Integer reasonCode) {
        String reasonText = getReasonText(reasonCode);
        return new CrlCheckResult(CrlStatus.REVOKED, revocationDate, reasonCode, reasonText, null);
    }

    /**
     * CRL_UNAVAILABLE 상태 생성 - CRL을 찾을 수 없음
     *
     * @param message 상세 메시지
     * @return CrlCheckResult
     */
    public static CrlCheckResult unavailable(String message) {
        return new CrlCheckResult(CrlStatus.CRL_UNAVAILABLE, null, null, null, message);
    }

    /**
     * CRL_EXPIRED 상태 생성 - CRL이 만료됨
     *
     * @param message 상세 메시지
     * @return CrlCheckResult
     */
    public static CrlCheckResult expired(String message) {
        return new CrlCheckResult(CrlStatus.CRL_EXPIRED, null, null, null, message);
    }

    /**
     * CRL_INVALID 상태 생성 - CRL 서명 검증 실패 또는 구조 오류
     *
     * @param message 상세 메시지
     * @return CrlCheckResult
     */
    public static CrlCheckResult invalid(String message) {
        return new CrlCheckResult(CrlStatus.CRL_INVALID, null, null, null, message);
    }

    // ========== Business Methods ==========

    /**
     * 인증서가 유효한지 확인 (폐기되지 않았고 CRL이 유효함)
     */
    public boolean isCertificateValid() {
        return status == CrlStatus.VALID;
    }

    /**
     * 인증서가 폐기되었는지 확인
     */
    public boolean isCertificateRevoked() {
        return status == CrlStatus.REVOKED;
    }

    /**
     * CRL 검증 자체가 실패했는지 확인 (CRL 조회 실패, 만료, 서명 오류)
     */
    public boolean hasCrlVerificationFailed() {
        return status == CrlStatus.CRL_UNAVAILABLE
            || status == CrlStatus.CRL_EXPIRED
            || status == CrlStatus.CRL_INVALID;
    }

    /**
     * RFC 5280 CRL Reason Code를 텍스트로 변환
     *
     * @param reasonCode CRL reason code (0-10)
     * @return 사유 텍스트
     */
    private static String getReasonText(Integer reasonCode) {
        if (reasonCode == null) {
            return "Unspecified";
        }

        return switch (reasonCode) {
            case 0 -> "Unspecified";
            case 1 -> "Key Compromise";
            case 2 -> "CA Compromise";
            case 3 -> "Affiliation Changed";
            case 4 -> "Superseded";
            case 5 -> "Cessation of Operation";
            case 6 -> "Certificate Hold";
            case 8 -> "Remove from CRL";
            case 9 -> "Privilege Withdrawn";
            case 10 -> "AA Compromise";
            default -> String.format("Unknown (%d)", reasonCode);
        };
    }

    /**
     * CRL 검증 상태
     */
    public enum CrlStatus {
        /**
         * 인증서가 폐기되지 않았으며 CRL이 유효함
         */
        VALID,

        /**
         * 인증서가 폐기됨
         */
        REVOKED,

        /**
         * CRL을 LDAP에서 찾을 수 없음
         */
        CRL_UNAVAILABLE,

        /**
         * CRL의 nextUpdate가 지났음 (만료됨)
         */
        CRL_EXPIRED,

        /**
         * CRL 서명 검증 실패 또는 구조 오류
         */
        CRL_INVALID
    }
}
