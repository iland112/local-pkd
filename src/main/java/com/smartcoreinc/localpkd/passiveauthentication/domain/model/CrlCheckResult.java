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
     * CRL 상태에 대한 간단한 설명을 반환합니다 (영문).
     *
     * @return 영문 설명
     */
    public String getStatusDescription() {
        return status != null ? status.getDescription() : "Unknown status";
    }

    /**
     * CRL 상태에 대한 상세 설명을 반환합니다 (한글).
     * 외부 클라이언트가 사용자에게 표시할 수 있는 상세한 설명입니다.
     *
     * @return 한글 상세 설명
     */
    public String getStatusDetailedDescription() {
        return status != null ? status.getDetailedDescription() : "알 수 없는 상태입니다.";
    }

    /**
     * CRL 상태의 심각도 레벨을 반환합니다.
     *
     * @return SUCCESS, FAILURE, WARNING, INFO 중 하나
     */
    public String getStatusSeverity() {
        return status != null ? status.getSeverity() : "INFO";
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
     *
     * <p>각 상태값에 대한 상세 설명을 제공하여 외부 클라이언트가
     * CRL 검증 결과를 명확하게 이해할 수 있도록 합니다.</p>
     *
     * <h3>상태별 의미:</h3>
     * <ul>
     *   <li>VALID: 정상 - 인증서가 유효하며 폐기되지 않음</li>
     *   <li>REVOKED: 폐기됨 - 인증서가 CRL에 등록되어 신뢰할 수 없음</li>
     *   <li>CRL_UNAVAILABLE: CRL 없음 - LDAP에서 CRL을 조회할 수 없어 폐기 상태 확인 불가</li>
     *   <li>CRL_EXPIRED: CRL 만료 - CRL이 최신 상태가 아니어서 신뢰할 수 없음</li>
     *   <li>CRL_INVALID: CRL 무효 - CRL 서명 검증 실패로 CRL 자체를 신뢰할 수 없음</li>
     *   <li>NOT_CHECKED: 미검사 - CRL 검증이 수행되지 않음</li>
     * </ul>
     */
    public enum CrlStatus {
        /**
         * 인증서가 폐기되지 않았으며 CRL이 유효함
         */
        VALID("Certificate is valid and not revoked",
              "인증서가 유효하며 폐기되지 않았습니다. CRL 검증을 통해 인증서의 유효성이 확인되었습니다.",
              "SUCCESS"),

        /**
         * 인증서가 폐기됨
         */
        REVOKED("Certificate has been revoked",
                "인증서가 폐기되어 더 이상 신뢰할 수 없습니다. 해당 인증서로 서명된 문서를 신뢰해서는 안 됩니다.",
                "FAILURE"),

        /**
         * CRL을 LDAP에서 찾을 수 없음
         */
        CRL_UNAVAILABLE("CRL not available in LDAP",
                        "LDAP 서버에서 CRL(인증서 폐기 목록)을 조회할 수 없습니다. 폐기 상태를 확인할 수 없으므로 주의가 필요합니다.",
                        "WARNING"),

        /**
         * CRL의 nextUpdate가 지났음 (만료됨)
         */
        CRL_EXPIRED("CRL has expired (nextUpdate passed)",
                    "CRL이 만료되어 최신 폐기 정보를 반영하지 않습니다. 최신 CRL을 다운로드하여 다시 검증해야 합니다.",
                    "WARNING"),

        /**
         * CRL 서명 검증 실패 또는 구조 오류
         */
        CRL_INVALID("CRL signature verification failed or structure error",
                    "CRL 서명 검증에 실패했거나 CRL 형식이 올바르지 않습니다. CRL 자체를 신뢰할 수 없으므로 폐기 상태 확인이 불가능합니다.",
                    "FAILURE"),

        /**
         * CRL 검증이 수행되지 않음
         */
        NOT_CHECKED("CRL verification was not performed",
                    "CRL 검증이 수행되지 않았습니다. 설정에 따라 CRL 검증이 비활성화되었거나 검증 단계가 생략되었을 수 있습니다.",
                    "INFO");

        private final String description;
        private final String detailedDescription;
        private final String severity;

        CrlStatus(String description, String detailedDescription, String severity) {
            this.description = description;
            this.detailedDescription = detailedDescription;
            this.severity = severity;
        }

        /**
         * 상태에 대한 간단한 설명을 반환합니다 (영문).
         *
         * @return 간단한 설명
         */
        public String getDescription() {
            return description;
        }

        /**
         * 상태에 대한 상세 설명을 반환합니다 (한글).
         * 외부 클라이언트가 사용자에게 표시할 수 있는 상세한 설명입니다.
         *
         * @return 상세 설명
         */
        public String getDetailedDescription() {
            return detailedDescription;
        }

        /**
         * 상태의 심각도 레벨을 반환합니다.
         *
         * @return SUCCESS, FAILURE, WARNING, INFO 중 하나
         */
        public String getSeverity() {
            return severity;
        }

        /**
         * 해당 상태가 검증 실패를 나타내는지 여부를 반환합니다.
         *
         * @return 검증 실패 여부
         */
        public boolean isFailure() {
            return "FAILURE".equals(severity);
        }

        /**
         * 해당 상태가 경고를 나타내는지 여부를 반환합니다.
         *
         * @return 경고 여부
         */
        public boolean isWarning() {
            return "WARNING".equals(severity);
        }
    }
}
