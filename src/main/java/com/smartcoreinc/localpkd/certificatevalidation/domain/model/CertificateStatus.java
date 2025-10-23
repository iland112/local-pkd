package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

/**
 * CertificateStatus - 인증서 검증 상태 Enum
 *
 * <p>인증서의 현재 유효 상태를 나타냅니다.</p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-23
 */
public enum CertificateStatus {

    /**
     * VALID - 유효한 인증서
     *
     * <p>다음 조건을 모두 만족</p>
     * <ul>
     *   <li>서명 검증 성공</li>
     *   <li>유효기간 내 (notBefore ≤ now ≤ notAfter)</li>
     *   <li>폐기되지 않음</li>
     *   <li>Trust Chain 검증 성공</li>
     *   <li>제약조건 검증 성공</li>
     * </ul>
     */
    VALID("유효"),

    /**
     * EXPIRED - 만료된 인증서
     *
     * <p>현재 시간이 notAfter 이후</p>
     */
    EXPIRED("만료됨"),

    /**
     * NOT_YET_VALID - 아직 유효하지 않은 인증서
     *
     * <p>현재 시간이 notBefore 이전</p>
     */
    NOT_YET_VALID("유효 시간 이전"),

    /**
     * REVOKED - 폐기된 인증서
     *
     * <p>CRL 또는 OCSP 확인으로 폐기 여부 확인</p>
     */
    REVOKED("폐기됨"),

    /**
     * INVALID - 유효하지 않은 인증서
     *
     * <p>다음 중 하나의 이유로 유효하지 않음</p>
     * <ul>
     *   <li>서명 검증 실패</li>
     *   <li>Trust Chain 검증 실패</li>
     *   <li>제약조건 위반</li>
     *   <li>기타 검증 오류</li>
     * </ul>
     */
    INVALID("유효하지 않음");

    private final String displayName;

    CertificateStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 유효한 상태 여부
     *
     * @return VALID 상태이면 true
     */
    public boolean isValid() {
        return this == VALID;
    }

    /**
     * 만료된 상태 여부
     *
     * @return EXPIRED 상태이면 true
     */
    public boolean isExpired() {
        return this == EXPIRED;
    }

    /**
     * 폐기된 상태 여부
     *
     * @return REVOKED 상태이면 true
     */
    public boolean isRevoked() {
        return this == REVOKED;
    }

    /**
     * 어떤 형태로든 유효하지 않은 상태 여부
     *
     * @return VALID가 아니면 true
     */
    public boolean isNotValid() {
        return this != VALID;
    }
}
