package com.smartcoreinc.localpkd.fileparsing.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;

/**
 * ParsingError - 파싱 오류 정보 Value Object
 *
 * <p><b>DDD Value Object 패턴</b>:</p>
 * <ul>
 *   <li>Immutability: 생성 후 변경 불가</li>
 *   <li>Self-validation: 생성 시 필수 필드 검증</li>
 *   <li>Value equality: 모든 필드 값으로 동등성 판단</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * // Entry 파싱 오류
 * ParsingError error = ParsingError.entryError(
 *     "cn=cert123,o=dsc,dc=data,dc=download,dc=pkd,dc=icao,dc=int",
 *     "Invalid certificate format: Missing Subject DN"
 * );
 *
 * // 인증서 검증 오류
 * ParsingError error = ParsingError.certificateError(
 *     "cert-fingerprint-abc123",
 *     "Certificate expired: notAfter=2020-12-31"
 * );
 *
 * // 일반 파싱 오류
 * ParsingError error = ParsingError.of(
 *     "PARSE_ERROR",
 *     "Line 123: Malformed LDIF entry"
 * );
 * </pre>
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParsingError implements ValueObject {

    /**
     * 오류 타입
     *
     * <p>예: ENTRY_ERROR, CERTIFICATE_ERROR, CRL_ERROR, VALIDATION_ERROR, PARSE_ERROR</p>
     */
    @Column(name = "error_type", length = 50, nullable = false)
    private String errorType;

    /**
     * 오류 발생 위치
     *
     * <p>예: Entry DN, Certificate fingerprint, Line number 등</p>
     */
    @Column(name = "error_location", length = 500)
    private String errorLocation;

    /**
     * 오류 메시지
     */
    @Column(name = "error_message", length = 1000, nullable = false)
    private String errorMessage;

    /**
     * 오류 발생 시각
     */
    @Column(name = "error_occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    // ========== Static Factory Methods ==========

    /**
     * ParsingError 생성 (전체 필드)
     *
     * @param errorType 오류 타입
     * @param errorLocation 오류 발생 위치
     * @param errorMessage 오류 메시지
     * @return ParsingError
     */
    public static ParsingError of(
        String errorType,
        String errorLocation,
        String errorMessage
    ) {
        ParsingError error = new ParsingError();
        error.errorType = errorType;
        error.errorLocation = errorLocation;
        error.errorMessage = errorMessage;
        error.occurredAt = LocalDateTime.now();

        // Validation
        error.validate();

        return error;
    }

    /**
     * ParsingError 생성 (위치 없음)
     *
     * @param errorType 오류 타입
     * @param errorMessage 오류 메시지
     * @return ParsingError
     */
    public static ParsingError of(String errorType, String errorMessage) {
        return of(errorType, null, errorMessage);
    }

    /**
     * Entry 파싱 오류 생성
     *
     * @param entryDN Entry DN
     * @param errorMessage 오류 메시지
     * @return ParsingError
     */
    public static ParsingError entryError(String entryDN, String errorMessage) {
        return of("ENTRY_ERROR", entryDN, errorMessage);
    }

    /**
     * 인증서 파싱 오류 생성
     *
     * @param certificateId 인증서 식별자 (fingerprint, serial number 등)
     * @param errorMessage 오류 메시지
     * @return ParsingError
     */
    public static ParsingError certificateError(String certificateId, String errorMessage) {
        return of("CERTIFICATE_ERROR", certificateId, errorMessage);
    }

    /**
     * CRL 파싱 오류 생성
     *
     * @param crlId CRL 식별자
     * @param errorMessage 오류 메시지
     * @return ParsingError
     */
    public static ParsingError crlError(String crlId, String errorMessage) {
        return of("CRL_ERROR", crlId, errorMessage);
    }

    /**
     * 검증 오류 생성
     *
     * @param location 오류 위치
     * @param errorMessage 오류 메시지
     * @return ParsingError
     */
    public static ParsingError validationError(String location, String errorMessage) {
        return of("VALIDATION_ERROR", location, errorMessage);
    }

    /**
     * 일반 파싱 오류 생성
     *
     * @param errorMessage 오류 메시지
     * @return ParsingError
     */
    public static ParsingError parseError(String errorMessage) {
        return of("PARSE_ERROR", null, errorMessage);
    }

    // ========== Business Logic Methods ==========

    /**
     * Entry 오류 여부
     */
    public boolean isEntryError() {
        return "ENTRY_ERROR".equals(errorType);
    }

    /**
     * 인증서 오류 여부
     */
    public boolean isCertificateError() {
        return "CERTIFICATE_ERROR".equals(errorType);
    }

    /**
     * CRL 오류 여부
     */
    public boolean isCrlError() {
        return "CRL_ERROR".equals(errorType);
    }

    /**
     * 검증 오류 여부
     */
    public boolean isValidationError() {
        return "VALIDATION_ERROR".equals(errorType);
    }

    /**
     * 위치 정보 있음 여부
     */
    public boolean hasLocation() {
        return errorLocation != null && !errorLocation.isBlank();
    }

    // ========== Validation ==========

    private void validate() {
        if (errorType == null || errorType.isBlank()) {
            throw new IllegalArgumentException("errorType must not be blank");
        }

        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorMessage must not be blank");
        }

        if (errorMessage.length() > 1000) {
            throw new IllegalArgumentException("errorMessage must not exceed 1000 characters");
        }

        if (errorLocation != null && errorLocation.length() > 500) {
            throw new IllegalArgumentException("errorLocation must not exceed 500 characters");
        }
    }

    @Override
    public String toString() {
        if (hasLocation()) {
            return String.format(
                "ParsingError[type=%s, location=%s, message=%s, time=%s]",
                errorType,
                errorLocation.length() > 50 ? errorLocation.substring(0, 47) + "..." : errorLocation,
                errorMessage.length() > 50 ? errorMessage.substring(0, 47) + "..." : errorMessage,
                occurredAt
            );
        } else {
            return String.format(
                "ParsingError[type=%s, message=%s, time=%s]",
                errorType,
                errorMessage.length() > 50 ? errorMessage.substring(0, 47) + "..." : errorMessage,
                occurredAt
            );
        }
    }
}
