package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * ValidationResult - 인증서 검증 결과 Value Object
 *
 * <p><b>DDD Value Object Pattern</b>:</p>
 * <ul>
 *   <li>Immutability: 생성 후 변경 불가</li>
 *   <li>Self-validation: 생성 시 비즈니스 규칙 검증</li>
 *   <li>Value equality: 모든 필드 값으로 동등성 판단</li>
 * </ul>
 *
 * <p><b>책임</b>:</p>
 * <ul>
 *   <li>인증서 검증의 전체 결과 (VALID, EXPIRED, REVOKED, INVALID)</li>
 *   <li>각 검증 단계별 결과 (서명, 체인, 폐기, 유효기간, 제약조건)</li>
 *   <li>검증 수행 시간 및 성능 정보</li>
 * </ul>
 *
 * <p><b>비즈니스 규칙</b>:</p>
 * <ul>
 *   <li>overallStatus가 VALID이면 모든 검증이 성공해야 함</li>
 *   <li>특정 검증이 실패하면 overallStatus는 INVALID</li>
 *   <li>유효기간 만료는 overallStatus = EXPIRED</li>
 *   <li>폐기된 인증서는 overallStatus = REVOKED</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * ValidationResult result = ValidationResult.of(
 *     CertificateStatus.VALID,      // overallStatus
 *     true,                          // signatureValid
 *     true,                          // chainValid
 *     true,                          // notRevoked
 *     true,                          // validityValid
 *     true                           // constraintsValid
 * );
 *
 * if (result.isValid()) {
 *     System.out.println("인증서가 유효합니다");
 * }
 *
 * if (result.isSignatureValid()) {
 *     System.out.println("서명이 검증되었습니다");
 * }
 *
 * // 검증 결과 분석
 * long duration = result.getValidationDurationMillis();
 * System.out.println("검증 시간: " + duration + "ms");
 * }</pre>
 *
 * @see CertificateStatus
 * @see ValueObject
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-23
 */
@Embeddable
public class ValidationResult implements ValueObject {

    /**
     * 전체 검증 상태
     *
     * <p>다섯 가지 상태 중 하나:</p>
     * <ul>
     *   <li>VALID: 모든 검증 통과</li>
     *   <li>EXPIRED: 유효기간 만료</li>
     *   <li>NOT_YET_VALID: 아직 유효하지 않음</li>
     *   <li>REVOKED: 폐기됨</li>
     *   <li>INVALID: 기타 검증 실패</li>
     * </ul>
     */
    @Column(name = "validation_status", length = 30, nullable = false)
    private String overallStatus;

    /**
     * 서명 검증 여부
     *
     * <p>인증서의 공개 키를 사용한 서명 검증 결과입니다.</p>
     * <p>true: 서명이 올바름</p>
     * <p>false: 서명 검증 실패</p>
     */
    @Column(name = "signature_valid", nullable = false)
    private Boolean signatureValid;

    /**
     * 신뢰 체인 검증 여부
     *
     * <p>인증서 체인이 신뢰 루트까지 연결되는지 확인합니다.</p>
     * <p>true: 신뢰 체인 검증 성공</p>
     * <p>false: 체인 검증 실패 (발급자를 찾을 수 없음)</p>
     */
    @Column(name = "chain_valid", nullable = false)
    private Boolean chainValid;

    /**
     * 폐기 여부 (CRL/OCSP)
     *
     * <p>CRL (Certificate Revocation List) 또는 OCSP를 통해 폐기 여부를 확인합니다.</p>
     * <p>true: 폐기되지 않음</p>
     * <p>false: 폐기됨</p>
     */
    @Column(name = "not_revoked", nullable = false)
    private Boolean notRevoked;

    /**
     * 유효기간 여부
     *
     * <p>현재 시간이 notBefore ≤ now ≤ notAfter 범위에 있는지 확인합니다.</p>
     * <p>true: 현재 유효함</p>
     * <p>false: 유효기간 외</p>
     */
    @Column(name = "validity_valid", nullable = false)
    private Boolean validityValid;

    /**
     * 기본 제약조건 검증 여부
     *
     * <p>X.509 기본 제약조건(Basic Constraints), 키 사용(Key Usage) 등을 검증합니다.</p>
     * <p>true: 제약조건 만족</p>
     * <p>false: 제약조건 위반</p>
     */
    @Column(name = "constraints_valid", nullable = false)
    private Boolean constraintsValid;

    /**
     * 검증 수행 시간
     *
     * <p>검증이 시작된 시간입니다. 다시 검증 필요 여부 판단에 사용됩니다.</p>
     */
    @Column(name = "validated_at", nullable = false)
    private LocalDateTime validatedAt;

    /**
     * 검증 소요 시간 (밀리초)
     *
     * <p>검증 시작 시간부터 완료 시간까지의 소요 시간입니다.</p>
     * <p>성능 모니터링, 로깅 목적으로 사용됩니다.</p>
     */
    @Column(name = "validation_duration_millis", nullable = false)
    private Long validationDurationMillis;

    /**
     * JPA용 기본 생성자 (protected)
     */
    protected ValidationResult() {
    }

    /**
     * ValidationResult 생성 (Static Factory Method)
     *
     * @param overallStatus 전체 검증 상태 (CertificateStatus enum 값)
     * @param signatureValid 서명 검증 여부
     * @param chainValid 신뢰 체인 검증 여부
     * @param notRevoked 폐기되지 않음 여부
     * @param validityValid 유효기간 유효 여부
     * @param constraintsValid 제약조건 유효 여부
     * @return ValidationResult
     * @throws IllegalArgumentException 필수 필드가 null인 경우
     */
    public static ValidationResult of(
            CertificateStatus overallStatus,
            boolean signatureValid,
            boolean chainValid,
            boolean notRevoked,
            boolean validityValid,
            boolean constraintsValid
    ) {
        return of(overallStatus, signatureValid, chainValid, notRevoked, validityValid, constraintsValid, 0);
    }

    /**
     * ValidationResult 생성 (검증 소요 시간 포함)
     *
     * @param overallStatus 전체 검증 상태 (CertificateStatus enum 값)
     * @param signatureValid 서명 검증 여부
     * @param chainValid 신뢰 체인 검증 여부
     * @param notRevoked 폐기되지 않음 여부
     * @param validityValid 유효기간 유효 여부
     * @param constraintsValid 제약조건 유효 여부
     * @param validationDurationMillis 검증 소요 시간 (밀리초)
     * @return ValidationResult
     * @throws IllegalArgumentException 필수 필드가 null인 경우
     */
    public static ValidationResult of(
            CertificateStatus overallStatus,
            boolean signatureValid,
            boolean chainValid,
            boolean notRevoked,
            boolean validityValid,
            boolean constraintsValid,
            long validationDurationMillis
    ) {
        if (overallStatus == null) {
            throw new IllegalArgumentException("Overall status cannot be null");
        }

        // 비즈니스 규칙 검증
        if (overallStatus == CertificateStatus.VALID) {
            // VALID 상태이면 모든 검증이 성공해야 함
            if (!signatureValid || !chainValid || !notRevoked || !validityValid || !constraintsValid) {
                throw new IllegalArgumentException(
                    "If overall status is VALID, all validations must pass"
                );
            }
        }

        if (validationDurationMillis < 0) {
            throw new IllegalArgumentException("Validation duration cannot be negative");
        }

        ValidationResult result = new ValidationResult();
        result.overallStatus = overallStatus.name();
        result.signatureValid = signatureValid;
        result.chainValid = chainValid;
        result.notRevoked = notRevoked;
        result.validityValid = validityValid;
        result.constraintsValid = constraintsValid;
        result.validatedAt = LocalDateTime.now();
        result.validationDurationMillis = validationDurationMillis;

        return result;
    }

    // ========== Getters ==========

    public CertificateStatus getOverallStatus() {
        return CertificateStatus.valueOf(overallStatus);
    }

    public Boolean isSignatureValid() {
        return signatureValid != null && signatureValid;
    }

    public Boolean isChainValid() {
        return chainValid != null && chainValid;
    }

    public Boolean isNotRevoked() {
        return notRevoked != null && notRevoked;
    }

    public Boolean isValidityValid() {
        return validityValid != null && validityValid;
    }

    public Boolean isConstraintsValid() {
        return constraintsValid != null && constraintsValid;
    }

    public LocalDateTime getValidatedAt() {
        return validatedAt;
    }

    public Long getValidationDurationMillis() {
        return validationDurationMillis;
    }

    // ========== Business Logic Methods ==========

    /**
     * 검증 성공 여부
     *
     * @return VALID 상태이면 true
     */
    public boolean isValid() {
        return getOverallStatus() == CertificateStatus.VALID;
    }

    /**
     * 검증 실패 여부
     *
     * @return VALID가 아니면 true
     */
    public boolean isNotValid() {
        return !isValid();
    }

    /**
     * 인증서 만료 여부
     *
     * @return EXPIRED 상태이면 true
     */
    public boolean isExpired() {
        return getOverallStatus() == CertificateStatus.EXPIRED;
    }

    /**
     * 아직 유효하지 않은 상태 여부
     *
     * @return NOT_YET_VALID 상태이면 true
     */
    public boolean isNotYetValid() {
        return getOverallStatus() == CertificateStatus.NOT_YET_VALID;
    }

    /**
     * 인증서 폐기 여부
     *
     * @return REVOKED 상태이면 true
     */
    public boolean isRevoked() {
        return getOverallStatus() == CertificateStatus.REVOKED;
    }

    /**
     * 모든 검증이 성공했는가
     *
     * <p>이 메서드는 overallStatus와 무관하게 모든 개별 검증이 성공했는지 확인합니다.</p>
     *
     * @return 모든 검증이 성공하면 true
     */
    public boolean allValidationsPass() {
        return isSignatureValid() &&
               isChainValid() &&
               isNotRevoked() &&
               isValidityValid() &&
               isConstraintsValid();
    }

    /**
     * 검증 결과 요약
     *
     * @return 검증 결과 요약 문자열 (예: "VALID (5/5 checks passed)")
     */
    public String getSummary() {
        int passedChecks = 0;
        if (isSignatureValid()) passedChecks++;
        if (isChainValid()) passedChecks++;
        if (isNotRevoked()) passedChecks++;
        if (isValidityValid()) passedChecks++;
        if (isConstraintsValid()) passedChecks++;

        return String.format("%s (%d/5 checks passed, %dms)",
            getOverallStatus().getDisplayName(), passedChecks, validationDurationMillis);
    }

    /**
     * 검증 재수행 필요 여부
     *
     * <p>검증 결과가 1시간 이상 지났으면 재수행을 권장합니다.</p>
     *
     * @return 1시간 이상 경과하면 true
     */
    public boolean needsRevalidation() {
        if (validatedAt == null) return true;
        long ageMillis = System.currentTimeMillis() - validatedAt.toString().hashCode();
        return ageMillis > 3600000; // 1 hour
    }

    // ========== equals & hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationResult that = (ValidationResult) o;
        return Objects.equals(overallStatus, that.overallStatus) &&
               Objects.equals(signatureValid, that.signatureValid) &&
               Objects.equals(chainValid, that.chainValid) &&
               Objects.equals(notRevoked, that.notRevoked) &&
               Objects.equals(validityValid, that.validityValid) &&
               Objects.equals(constraintsValid, that.constraintsValid) &&
               Objects.equals(validatedAt, that.validatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(overallStatus, signatureValid, chainValid,
            notRevoked, validityValid, constraintsValid, validatedAt);
    }

    @Override
    public String toString() {
        return String.format("ValidationResult[status=%s, checks=%d/%d, duration=%dms]",
            getOverallStatus().getDisplayName(),
            (isSignatureValid() ? 1 : 0) +
            (isChainValid() ? 1 : 0) +
            (isNotRevoked() ? 1 : 0) +
            (isValidityValid() ? 1 : 0) +
            (isConstraintsValid() ? 1 : 0),
            5,
            validationDurationMillis
        );
    }
}
