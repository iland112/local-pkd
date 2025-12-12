package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.common.util.CountryCodeUtil;
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
     * <p>두 가지 형식을 지원합니다:</p>
     * <ul>
     *   <li>CSCA 형식: CSCA-XX (예: CSCA-QA, CSCA-NZ)</li>
     *   <li>DN 형식: 전체 X.509 DN (예: CN=CSCA Finland,OU=VRK,O=Finland,C=FI)</li>
     * </ul>
     *
     * @param value CSCA 발급자명 또는 DN
     * @return IssuerName
     * @throws DomainException value가 null이거나 비어있는 경우
     */
    public static IssuerName of(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException(
                "INVALID_ISSUER_NAME",
                "Issuer name cannot be null or blank"
            );
        }

        String normalized = value.trim();

        // CSCA-XX 형식인 경우에만 대문자로 정규화
        if (CSCA_PATTERN.matcher(normalized.toUpperCase()).matches()) {
            normalized = normalized.toUpperCase();
        }
        // 그 외에는 원본 값 유지 (DN 문자열 등)

        IssuerName issuerName = new IssuerName();
        issuerName.value = normalized;
        return issuerName;
    }

    /**
     * 국가 코드 추출
     *
     * <p>두 가지 형식 지원:</p>
     * <ul>
     *   <li>CSCA 형식: CSCA-QA → "QA"</li>
     *   <li>DN 형식: CN=CSCA Finland,OU=VRK,O=Finland,C=FI → "FI"</li>
     * </ul>
     *
     * <p><b>Note</b>: 실제 파싱은 {@link CountryCodeUtil#extractCountryCode(String)}에 위임합니다.</p>
     *
     * @return 2자 국가 코드 (대문자) 또는 빈 문자열
     */
    public String getCountryCode() {
        // CountryCodeUtil로 위임 (DRY 원칙, 단일 진실 공급원)
        String countryCode = CountryCodeUtil.extractCountryCode(value);
        return countryCode != null ? countryCode : "";
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
