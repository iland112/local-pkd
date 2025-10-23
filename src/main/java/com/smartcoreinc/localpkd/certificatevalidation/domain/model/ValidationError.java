package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * ValidationError - 인증서 검증 오류 Value Object
 *
 * <p><b>DDD Value Object Pattern</b>:</p>
 * <ul>
 *   <li>Immutability: 생성 후 변경 불가</li>
 *   <li>Self-validation: 생성 시 필수 필드 검증</li>
 *   <li>Value equality: 모든 필드 값으로 동등성 판단</li>
 * </ul>
 *
 * <p><b>책임</b>:</p>
 * <ul>
 *   <li>검증 과정에서 발생하는 개별 오류 정보</li>
 *   <li>오류 심각도 (ERROR/WARNING)</li>
 *   <li>오류 발생 시간 및 추적 정보</li>
 * </ul>
 *
 * <p><b>오류 코드 예시</b>:</p>
 * <ul>
 *   <li>SIGNATURE_INVALID: 서명 검증 실패</li>
 *   <li>CHAIN_INCOMPLETE: 신뢰 체인 불완전</li>
 *   <li>CERT_REVOKED: 인증서 폐기됨</li>
 *   <li>EXPIRED: 유효기간 만료</li>
 *   <li>NOT_YET_VALID: 유효하지 않은 기간</li>
 *   <li>CONSTRAINT_VIOLATION: 제약조건 위반</li>
 *   <li>KEY_USAGE_VIOLATION: 키 사용 제약 위반</li>
 *   <li>BASIC_CONSTRAINT_VIOLATION: 기본 제약조건 위반</li>
 *   <li>ISSUER_NOT_FOUND: 발급자를 찾을 수 없음</li>
 *   <li>CRL_FETCH_FAILED: CRL 다운로드 실패</li>
 *   <li>OCSP_FAILED: OCSP 조회 실패</li>
 * </ul>
 *
 * <p><b>심각도</b>:</p>
 * <ul>
 *   <li>ERROR: 인증서가 유효하지 않음 (검증 실패)</li>
 *   <li>WARNING: 주의 필요하나 선택적 (예: CRL 만료임박)</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // 서명 검증 실패
 * ValidationError sigError = ValidationError.of(
 *     "SIGNATURE_INVALID",
 *     "Certificate signature verification failed",
 *     "ERROR"
 * );
 *
 * // CRL 오래됨 경고
 * ValidationError crlWarning = ValidationError.of(
 *     "CRL_OUTDATED",
 *     "CRL will expire in 5 days",
 *     "WARNING"
 * );
 *
 * // 오류 정보 확인
 * if (sigError.isCritical()) {
 *     System.out.println("Critical error: " + sigError.getMessage());
 * }
 * }</pre>
 *
 * @see ValidationResult
 * @see CertificateStatus
 * @see ValueObject
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-23
 */
@Embeddable
public class ValidationError implements ValueObject {

    /**
     * 오류 코드
     *
     * <p>오류를 프로그래밍적으로 구분하기 위한 고정 문자열입니다.</p>
     * <p>예: "SIGNATURE_INVALID", "CHAIN_INCOMPLETE", "CERT_REVOKED"</p>
     */
    @Column(name = "error_code", length = 50, nullable = false)
    private String errorCode;

    /**
     * 오류 메시지
     *
     * <p>사용자에게 표시할 상세 오류 메시지입니다.</p>
     * <p>예: "Certificate signature verification failed due to invalid key"</p>
     */
    @Column(name = "error_message", length = 500, nullable = false)
    private String errorMessage;

    /**
     * 심각도 (ERROR 또는 WARNING)
     *
     * <p>ERROR: 인증서가 유효하지 않음 (검증 실패)</p>
     * <p>WARNING: 주의 필요하나 선택적 (경고)</p>
     */
    @Column(name = "error_severity", length = 20, nullable = false)
    private String severity;

    /**
     * 오류 발생 시간
     *
     * <p>오류가 감지된 시간입니다.</p>
     */
    @Column(name = "error_occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /**
     * JPA용 기본 생성자 (protected)
     */
    protected ValidationError() {
    }

    /**
     * ValidationError 생성 (Static Factory Method)
     *
     * @param errorCode 오류 코드 (예: "SIGNATURE_INVALID")
     * @param errorMessage 오류 메시지
     * @param severity 심각도 ("ERROR" 또는 "WARNING")
     * @return ValidationError
     * @throws IllegalArgumentException 필수 필드가 null이거나 형식이 올바르지 않은 경우
     */
    public static ValidationError of(
            String errorCode,
            String errorMessage,
            String severity
    ) {
        if (errorCode == null || errorCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Error code cannot be null or blank");
        }
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Error message cannot be null or blank");
        }
        if (severity == null || severity.trim().isEmpty()) {
            throw new IllegalArgumentException("Severity cannot be null or blank");
        }

        // 심각도 검증 (ERROR 또는 WARNING만 가능)
        String upperSeverity = severity.toUpperCase();
        if (!upperSeverity.equals("ERROR") && !upperSeverity.equals("WARNING")) {
            throw new IllegalArgumentException(
                String.format("Severity must be ERROR or WARNING, but got: %s", severity)
            );
        }

        ValidationError error = new ValidationError();
        error.errorCode = errorCode.toUpperCase();
        error.errorMessage = errorMessage;
        error.severity = upperSeverity;
        error.occurredAt = LocalDateTime.now();

        return error;
    }

    /**
     * 치명적 오류 생성 (ERROR 심각도)
     *
     * @param errorCode 오류 코드
     * @param errorMessage 오류 메시지
     * @return ValidationError (심각도: ERROR)
     */
    public static ValidationError critical(String errorCode, String errorMessage) {
        return of(errorCode, errorMessage, "ERROR");
    }

    /**
     * 경고 생성 (WARNING 심각도)
     *
     * @param errorCode 오류 코드
     * @param errorMessage 오류 메시지
     * @return ValidationError (심각도: WARNING)
     */
    public static ValidationError warning(String errorCode, String errorMessage) {
        return of(errorCode, errorMessage, "WARNING");
    }

    // ========== Getters ==========

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getSeverity() {
        return severity;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    // ========== Business Logic Methods ==========

    /**
     * 치명적 오류 여부
     *
     * @return 심각도가 ERROR이면 true
     */
    public boolean isCritical() {
        return "ERROR".equals(severity);
    }

    /**
     * 경고 여부
     *
     * @return 심각도가 WARNING이면 true
     */
    public boolean isWarning() {
        return "WARNING".equals(severity);
    }

    /**
     * 특정 오류 코드 일치 여부
     *
     * @param code 비교할 오류 코드
     * @return 오류 코드가 일치하면 true
     */
    public boolean hasErrorCode(String code) {
        if (code == null) {
            return false;
        }
        return this.errorCode.equalsIgnoreCase(code.toUpperCase());
    }

    /**
     * 서명 검증 오류 여부
     *
     * @return 서명 관련 오류면 true
     */
    public boolean isSignatureError() {
        return errorCode.contains("SIGNATURE");
    }

    /**
     * 체인 검증 오류 여부
     *
     * @return 체인 관련 오류면 true
     */
    public boolean isChainError() {
        return errorCode.contains("CHAIN") || errorCode.contains("ISSUER");
    }

    /**
     * 폐기 관련 오류 여부
     *
     * @return 폐기 관련 오류면 true
     */
    public boolean isRevocationError() {
        return errorCode.contains("REVOKED") || errorCode.contains("CRL") || errorCode.contains("OCSP");
    }

    /**
     * 유효기간 관련 오류 여부
     *
     * @return 유효기간 관련 오류면 true
     */
    public boolean isValidityError() {
        return errorCode.contains("EXPIRED") || errorCode.contains("NOT_YET_VALID") || errorCode.contains("VALIDITY");
    }

    /**
     * 제약조건 관련 오류 여부
     *
     * @return 제약조건 관련 오류면 true
     */
    public boolean isConstraintError() {
        return errorCode.contains("CONSTRAINT") || errorCode.contains("KEY_USAGE") || errorCode.contains("BASIC");
    }

    /**
     * 완전한 오류 정보 여부
     *
     * @return 모든 필드가 채워져 있으면 true
     */
    public boolean isComplete() {
        return errorCode != null && !errorCode.trim().isEmpty() &&
               errorMessage != null && !errorMessage.trim().isEmpty() &&
               severity != null && !severity.trim().isEmpty() &&
               occurredAt != null;
    }

    // ========== equals & hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationError that = (ValidationError) o;
        return Objects.equals(errorCode, that.errorCode) &&
               Objects.equals(errorMessage, that.errorMessage) &&
               Objects.equals(severity, that.severity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorCode, errorMessage, severity);
    }

    @Override
    public String toString() {
        return String.format("ValidationError[code=%s, severity=%s, message=%s, at=%s]",
            errorCode, severity, errorMessage, occurredAt);
    }
}
