package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.regex.Pattern;

/**
 * CountryCode - ISO 3166-1 alpha-2 국가 코드 Value Object
 *
 * <p><b>DDD Value Object Pattern</b>:</p>
 * <ul>
 *   <li>Immutability: 생성 후 변경 불가</li>
 *   <li>Self-validation: 생성 시 형식 검증</li>
 *   <li>Value equality: 값 기반 동등성 판단</li>
 * </ul>
 *
 * <p><b>비즈니스 규칙</b>:</p>
 * <ul>
 *   <li>ISO 3166-1 alpha-2 표준 준수 (2자 대문자 알파벳)</li>
 *   <li>예: QA (Qatar), NZ (New Zealand), US (United States), KR (Korea)</li>
 *   <li>대문자로 정규화됨</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // 정상 생성
 * CountryCode country = CountryCode.of("QA");
 * String code = country.getValue();  // "QA"
 * boolean isQatar = country.isQatar();  // true
 *
 * // 검증 실패
 * CountryCode.of("ABC");  // ❌ DomainException: Invalid country code format
 * }</pre>
 *
 * @see IssuerName
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
@Embeddable
@Getter
@EqualsAndHashCode
public class CountryCode implements ValueObject {

    /**
     * ISO 3166-1 alpha-2 국가 코드 (예: QA, NZ, US)
     */
    @Column(name = "country_code", length = 2, nullable = false)
    private String value;

    /**
     * 정규식 패턴: 대문자 알파벳 2자
     * 예: QA, NZ, US, KR, JP, CN
     */
    private static final Pattern COUNTRY_CODE_PATTERN = Pattern.compile("^[A-Z]{2}$");

    /**
     * JPA용 기본 생성자 (protected)
     */
    protected CountryCode() {
    }

    /**
     * CountryCode 생성 (Static Factory Method)
     *
     * @param value ISO 3166-1 alpha-2 국가 코드 (예: QA)
     * @return CountryCode
     * @throws DomainException 형식이 유효하지 않은 경우
     */
    public static CountryCode of(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException(
                "INVALID_COUNTRY_CODE",
                "Country code cannot be null or blank"
            );
        }

        String normalized = value.trim().toUpperCase();

        // 형식 검증: 정확히 2자 대문자 알파벳
        if (!COUNTRY_CODE_PATTERN.matcher(normalized).matches()) {
            throw new DomainException(
                "INVALID_COUNTRY_CODE_FORMAT",
                "Country code must be exactly 2 uppercase letters (ISO 3166-1 alpha-2). Got: " + value
            );
        }

        CountryCode countryCode = new CountryCode();
        countryCode.value = normalized;
        return countryCode;
    }

    /**
     * 값 반환
     *
     * @return 2자 국가 코드
     */
    public String getValue() {
        return value;
    }

    /**
     * Qatar 국가 코드 확인
     *
     * @return QA 여부
     */
    public boolean isQatar() {
        return "QA".equals(value);
    }

    /**
     * New Zealand 국가 코드 확인
     *
     * @return NZ 여부
     */
    public boolean isNewZealand() {
        return "NZ".equals(value);
    }

    /**
     * 특정 국가 코드 일치 여부
     *
     * @param code 국가 코드 (대소문자 무시)
     * @return 일치 여부
     */
    public boolean matches(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return value.equalsIgnoreCase(code.toUpperCase());
    }

    /**
     * 문자열 표현
     *
     * @return 국가 코드
     */
    @Override
    public String toString() {
        return value;
    }
}
