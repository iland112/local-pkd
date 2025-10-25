package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.regex.Pattern;

/**
 * IssuerName - CSCA (Country Signing CA) 발급자명 Value Object
 *
 * <p><b>DDD Value Object Pattern</b>:</p>
 * <ul>
 *   <li>Immutability: 생성 후 변경 불가</li>
 *   <li>Self-validation: 생성 시 형식 및 비즈니스 규칙 검증</li>
 *   <li>Value equality: 값 기반 동등성 판단</li>
 * </ul>
 *
 * <p><b>비즈니스 규칙</b>:</p>
 * <ul>
 *   <li>반드시 "CSCA-" 접두사로 시작</li>
 *   <li>접두사 이후 2자 국가 코드 필수 (ISO 3166-1 alpha-2)</li>
 *   <li>예: CSCA-QA, CSCA-NZ, CSCA-US, CSCA-KR</li>
 *   <li>대문자로 정규화됨</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // 정상 생성
 * IssuerName issuer = IssuerName.of("CSCA-QA");
 * String country = issuer.getCountryCode();  // "QA"
 * boolean isQatar = issuer.isCountry("QA");  // true
 *
 * // 검증 실패
 * IssuerName.of("INVALID");  // ❌ DomainException: Invalid CSCA issuer name format
 * }</pre>
 *
 * @see CertificateRevocationList
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
@Embeddable
@Getter
@EqualsAndHashCode
public class IssuerName implements ValueObject {

    /**
     * CSCA 발급자명 (예: CSCA-QA, CSCA-NZ)
     */
    @Column(name = "issuer_name", length = 255, nullable = false)
    private String value;

    /**
     * 정규식 패턴: CSCA-[ISO3166-1_ALPHA2]
     * CSCA- 접두사 + 2자 국가 코드
     */
    private static final Pattern CSCA_PATTERN = Pattern.compile("^CSCA-[A-Z]{2}$");

    /**
     * JPA용 기본 생성자 (protected)
     */
    protected IssuerName() {
    }

    /**
     * IssuerName 생성 (Static Factory Method)
     *
     * @param value CSCA 발급자명 (예: CSCA-QA)
     * @return IssuerName
     * @throws DomainException 형식이 유효하지 않은 경우
     */
    public static IssuerName of(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException(
                "INVALID_ISSUER_NAME",
                "Issuer name cannot be null or blank"
            );
        }

        String normalized = value.trim().toUpperCase();

        // 형식 검증: CSCA-XX (X는 대문자 알파벳)
        if (!CSCA_PATTERN.matcher(normalized).matches()) {
            throw new DomainException(
                "INVALID_ISSUER_NAME_FORMAT",
                "Issuer name must match format 'CSCA-XX' (e.g., CSCA-QA, CSCA-NZ). Got: " + value
            );
        }

        IssuerName issuerName = new IssuerName();
        issuerName.value = normalized;
        return issuerName;
    }

    /**
     * 국가 코드 추출
     *
     * <p>CSCA-QA에서 "QA"를 추출</p>
     *
     * @return 2자 국가 코드
     */
    public String getCountryCode() {
        if (value == null || value.length() < 3) {
            return "";
        }
        return value.substring(6);  // "CSCA-"(5자) 이후 2자
    }

    /**
     * 특정 국가 발급자 여부
     *
     * @param countryCode 국가 코드 (대소문자 무시)
     * @return 일치 여부
     */
    public boolean isCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return false;
        }
        return getCountryCode().equalsIgnoreCase(countryCode.toUpperCase());
    }

    /**
     * CSCA 접두사 확인
     *
     * @return 항상 true (이미 검증됨)
     */
    public boolean isCSCA() {
        return value != null && value.startsWith("CSCA-");
    }

    /**
     * 값 반환
     *
     * @return CSCA 발급자명
     */
    public String getValue() {
        return value;
    }

    /**
     * 문자열 표현
     *
     * @return CSCA 발급자명
     */
    @Override
    public String toString() {
        return value;
    }
}
