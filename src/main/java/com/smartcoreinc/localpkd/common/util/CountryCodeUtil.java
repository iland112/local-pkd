package com.smartcoreinc.localpkd.common.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * 국가 코드 추출 및 검증 유틸리티
 * DN(Distinguished Name)에서 국가 코드 추출
 *
 * <p><b>지원 형식</b>:</p>
 * <ul>
 *   <li>CSCA-XX: CSCA-KR, CSCA-US (ICAO PKD 표준)</li>
 *   <li>DN: CN=CSCA Finland,OU=VRK,O=Finland,C=FI (X.509 표준)</li>
 *   <li>LDIF DN: cn=...,o=csca,c=KR,dc=data (LDAP 표준)</li>
 * </ul>
 *
 * <p><b>Case-Insensitive</b>: C=, c=, CSCA-, csca- 모두 지원</p>
 *
 * @since 2025-10-24
 * @version 2.0 (2025-12-11: CSCA-XX 형식 지원 추가, case-insensitive 개선)
 */
@Slf4j
public class CountryCodeUtil {

    // CSCA-XX 형식 패턴 (ICAO PKD)
    private static final Pattern CSCA_PATTERN = Pattern.compile("^CSCA-([A-Z]{2})$", Pattern.CASE_INSENSITIVE);

    // DN 내 C= 컴포넌트 패턴 (X.509 / LDAP)
    // C= 뒤에 공백이 있을 수 있으므로 \\s* 추가, 값도 공백 포함 가능하도록 변경
    // [,+] 플러스(+)와 쉼표(,) 모두 RDN 구분자로 인식 (multi-valued RDN 지원)
    private static final Pattern DN_COUNTRY_PATTERN = Pattern.compile("(?:^|[,+])\\s*C=\\s*([A-Z]{2,3})\\s*(?:[,+]|$)", Pattern.CASE_INSENSITIVE);

    // Legacy pattern (deprecated)
    private static final Pattern COUNTRY_CODE_PATTERN = Pattern.compile("\\bc=([A-Z]{2})\\b", Pattern.CASE_INSENSITIVE);

    private static final String UNKNOWN_COUNTRY = "UNKNOWN";

    private CountryCodeUtil() {
        // Utility class - private constructor
    }

    /**
     * 통합 국가 코드 추출 메서드 (권장)
     *
     * <p>CSCA-XX 형식과 DN 형식을 자동으로 감지하여 처리합니다.</p>
     *
     * <p><b>지원 형식</b>:</p>
     * <ul>
     *   <li>CSCA-XX: "CSCA-KR" → "KR"</li>
     *   <li>X.509 DN: "CN=CSCA Finland,OU=VRK,O=Finland,C=FI" → "FI"</li>
     *   <li>LDIF DN: "cn=...,o=csca,c=KR,dc=data" → "KR"</li>
     *   <li>Mixed case: "csca-kr", "c=fi" → "KR", "FI"</li>
     * </ul>
     *
     * @param value CSCA 이름 또는 DN 문자열
     * @return 국가 코드 (대문자, 2-3자) 또는 null
     */
    public static String extractCountryCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();

        // 1. CSCA-XX 형식 체크
        Matcher cscaMatcher = CSCA_PATTERN.matcher(trimmed);
        if (cscaMatcher.matches()) {
            return cscaMatcher.group(1).toUpperCase();
        }

        // 2. DN 형식 체크 (C= 컴포넌트)
        Matcher dnMatcher = DN_COUNTRY_PATTERN.matcher(trimmed);
        if (dnMatcher.find()) {
            return dnMatcher.group(1).toUpperCase();
        }

        log.debug("Could not extract country code from: {}", value);
        return null;
    }

    /**
     * DN 문자열에서 국가 코드 추출
     * 
     * @param dn Distinguished Name (예; "C=KR, O=MOFA, CN=CSCA Korea")
     * @return 2자리 국가 코드 (대문자) 또는 "UNKNOWN"
     */
    public static String extractFromDN(String dn) {
        if (dn == null || dn.isEmpty()) {
            log.warn("DN is null or empty");
            return UNKNOWN_COUNTRY;
        }

        // 정규식으로 C= 부분 추출
        Matcher matcher = COUNTRY_CODE_PATTERN.matcher(dn);
        if (matcher.find()) {
            String countryCode = matcher.group(1).toUpperCase();
            log.debug("Extracted country code: {} from DN: {}", countryCode, dn);
        }

        // 쉼표로 분리해서 찾기 (fallback)
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.toUpperCase().startsWith("C=")) {
                String countryCode = trimmed.substring(2).trim().toUpperCase();
                if (isValidCountryCode(countryCode)) {
                    log.debug("Extracted country code: {} from DN: {}", countryCode, dn);
                    return countryCode;
                }
            }
        }

        log.warn("Could not extract country code from DN: {}", dn);
        return UNKNOWN_COUNTRY;
    }

    /**
     * 국각 코드 유효성 검증
     * ISO 3166-1 alpha-2 (2자리 대문자)
     * 
     * @param countryCode 국가 코드
     * @return true if valid
     */
    public static boolean isValidCountryCode(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) {
            return false;
        }

        // ISO 3166-1 alpha-2 검증
        try {
            // Use modern, Set-based ISO country code validation
            String upperCode = countryCode.toUpperCase(Locale.ROOT);
            if (Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2).contains(upperCode)) {
                return true;
            }

            // 특수 케이스: UN, XK (Kosovo) 등
            return isSpecialCountryCode(upperCode);
        } catch (Exception e) {
            log.error("Error validating country code: {}", countryCode, e);
            return false;
        }
    }

    /**
     * 특수 국가 코드 검증
     * ISO 표준은 아니지만 ICAO PKD에서 사용되는 코드
     * 
     * @param countryCode 국가 코드
     * @return true if special valid code
     */
    private static boolean isSpecialCountryCode(String countryCode) {
        // UN (United Nations), XK (Kosovo), EU (European Union)
        return "UN".equals(countryCode) || 
               "XK".equals(countryCode) ||
               "EU".equals(countryCode);
    }

    /**
     * 국가 코드 정규화
     * 소문자를 대문자로 변환하고 유효성 검증
     * 
     * @param countryCode 국가 코드
     * @return 정규화된 국가 코드 또는 UNKNOWN
     */
    public static String normalize(String countryCode) {
        if (countryCode == null || countryCode.isEmpty()) {
            return UNKNOWN_COUNTRY;
        }

        String normalized = countryCode.trim().toLowerCase(Locale.ROOT);

        if (isValidCountryCode(normalized)) {
            return normalized;
        }

        return UNKNOWN_COUNTRY;
    }

    /**
     * 국가 이름 조회
     * 
     * @param countryCode 국가 코드
     * @return 국가 이름 (영문) 또는 코드 그대로
     */
    public static String getCountryName(String countryCode) {
        if (countryCode == null || countryCode.isEmpty()) {
            return UNKNOWN_COUNTRY;
        }

        try {
            Locale locale = new Locale.Builder().setRegion(countryCode).build();
            String displayName = locale.getDisplayCountry(Locale.ENGLISH);

            if (displayName != null && !displayName.isEmpty()) {
                return displayName;
            }
        } catch (Exception e) {
            log.debug("Could not get country name for code: {}", countryCode);
        }

        return countryCode;
    }

    /**
     * 국가 이름 조회 (한글)
     * 
     * @param countryCode 국가 코드
     * @return 국가 이름 (한글) 또는 영문 또는 코드
     */
    public static String getCountryNameKorean(String countryCode) {
        if (countryCode == null || countryCode.isEmpty()) {
            return "알 수 없음";
        }

        try {
            Locale locale = new Locale.Builder().setRegion(countryCode).build();
            String displayName = locale.getDisplayCountry(Locale.KOREAN);

            if (displayName != null && !displayName.isEmpty()) {
                return displayName;
            }
        } catch (Exception e) {
            log.debug("Could not get Korean country name for code: {}", countryCode);
        }

        return getCountryName(countryCode);
    }

    /**
     * UNKNOWN 국가 코드 여부 확인
     * 
     * @param countryCode 국가 코드
     * @return true if unknown
     */
    public static boolean isUnknown(String countryCode) {
        return UNKNOWN_COUNTRY.equals(countryCode);
    }

    /**
     * LDIF DN에서 국가 코드 추출
     * 예: "cn=CSCA...,o=csca,c=KR,dc=data" -> "KR""
     * @param ldifDn
     * @return
     */
    public static String extractFromLdifDn(String ldifDn) {
        if (ldifDn == null || ldifDn.isEmpty()) {
            return null;
        }

        // c=XX 패턴 찾기
        String[] parts = ldifDn.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.toLowerCase().startsWith("c=")) {
                String countryCode = trimmed.substring(2).toUpperCase();
                if (isValidCountryCode(countryCode)) {
                    return countryCode;
                }
            }
        }

        return null;
    }
}
