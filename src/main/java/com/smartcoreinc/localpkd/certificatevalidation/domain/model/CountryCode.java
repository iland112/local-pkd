package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Map;
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
     * 정규식 패턴: 대문자 알파벳 2자 (ISO 3166-1 alpha-2)
     * 예: QA, NZ, US, KR, JP, CN
     */
    private static final Pattern COUNTRY_CODE_ALPHA2_PATTERN = Pattern.compile("^[A-Z]{2}$");

    /**
     * 정규식 패턴: 대문자 알파벳 3자 (ISO 3166-1 alpha-3)
     * 예: QAT, NZL, USA, KOR, JPN, CHN
     */
    private static final Pattern COUNTRY_CODE_ALPHA3_PATTERN = Pattern.compile("^[A-Z]{3}$");

    /**
     * ISO 3166-1 alpha-3 to alpha-2 conversion map (Common countries in ICAO PKD)
     */
    private static final Map<String, String> ALPHA3_TO_ALPHA2 = Map.ofEntries(
        Map.entry("KOR", "KR"),  // Korea
        Map.entry("USA", "US"),  // United States
        Map.entry("GBR", "GB"),  // United Kingdom
        Map.entry("JPN", "JP"),  // Japan
        Map.entry("CHN", "CN"),  // China
        Map.entry("FRA", "FR"),  // France
        Map.entry("DEU", "DE"),  // Germany
        Map.entry("CAN", "CA"),  // Canada
        Map.entry("AUS", "AU"),  // Australia
        Map.entry("IND", "IN"),  // India
        Map.entry("BRA", "BR"),  // Brazil
        Map.entry("RUS", "RU"),  // Russia
        Map.entry("MEX", "MX"),  // Mexico
        Map.entry("ITA", "IT"),  // Italy
        Map.entry("ESP", "ES"),  // Spain
        Map.entry("NLD", "NL"),  // Netherlands
        Map.entry("SWE", "SE"),  // Sweden
        Map.entry("NOR", "NO"),  // Norway
        Map.entry("DNK", "DK"),  // Denmark
        Map.entry("FIN", "FI"),  // Finland
        Map.entry("POL", "PL"),  // Poland
        Map.entry("BEL", "BE"),  // Belgium
        Map.entry("CHE", "CH"),  // Switzerland
        Map.entry("AUT", "AT"),  // Austria
        Map.entry("NZL", "NZ"),  // New Zealand
        Map.entry("SGP", "SG"),  // Singapore
        Map.entry("THA", "TH"),  // Thailand
        Map.entry("MYS", "MY"),  // Malaysia
        Map.entry("IDN", "ID"),  // Indonesia
        Map.entry("PHL", "PH"),  // Philippines
        Map.entry("VNM", "VN"),  // Vietnam
        Map.entry("TUR", "TR"),  // Turkey
        Map.entry("SAU", "SA"),  // Saudi Arabia
        Map.entry("ARE", "AE"),  // United Arab Emirates
        Map.entry("QAT", "QA"),  // Qatar
        Map.entry("EGY", "EG"),  // Egypt
        Map.entry("ZAF", "ZA"),  // South Africa
        Map.entry("ARG", "AR"),  // Argentina
        Map.entry("CHL", "CL"),  // Chile
        Map.entry("COL", "CO"),  // Colombia
        Map.entry("PER", "PE")   // Peru
    );

    /**
     * JPA용 기본 생성자 (protected)
     */
    protected CountryCode() {
    }

    /**
     * CountryCode 생성 (Static Factory Method)
     * <p>
     * Supports both ISO 3166-1 alpha-2 (2-letter) and alpha-3 (3-letter) codes.
     * Alpha-3 codes are automatically converted to alpha-2 for storage.
     * <p>
     * <b>ICAO Doc 9303 Compliance:</b> Passport MRZ uses alpha-3 codes (e.g., KOR),
     * but this domain model stores alpha-2 codes (e.g., KR) for consistency.
     *
     * @param value ISO 3166-1 alpha-2 (예: QA, KR) or alpha-3 (예: QAT, KOR) 국가 코드
     * @return CountryCode
     * @throws DomainException 형식이 유효하지 않거나 변환 불가능한 경우
     */
    public static CountryCode of(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException(
                "INVALID_COUNTRY_CODE",
                "Country code cannot be null or blank"
            );
        }

        String normalized = value.trim().toUpperCase();

        // Check if it's alpha-2 (2-letter code)
        if (COUNTRY_CODE_ALPHA2_PATTERN.matcher(normalized).matches()) {
            CountryCode countryCode = new CountryCode();
            countryCode.value = normalized;
            return countryCode;
        }

        // Check if it's alpha-3 (3-letter code) and convert to alpha-2
        if (COUNTRY_CODE_ALPHA3_PATTERN.matcher(normalized).matches()) {
            String alpha2 = ALPHA3_TO_ALPHA2.get(normalized);
            if (alpha2 == null) {
                throw new DomainException(
                    "UNSUPPORTED_COUNTRY_CODE",
                    "Country code '" + normalized + "' (ISO 3166-1 alpha-3) is not supported. " +
                    "Please add it to the conversion map or use alpha-2 format."
                );
            }
            CountryCode countryCode = new CountryCode();
            countryCode.value = alpha2;
            return countryCode;
        }

        // Invalid format
        throw new DomainException(
            "INVALID_COUNTRY_CODE_FORMAT",
            "Country code must be ISO 3166-1 alpha-2 (2 letters, e.g., KR) or alpha-3 (3 letters, e.g., KOR). Got: " + value
        );
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
