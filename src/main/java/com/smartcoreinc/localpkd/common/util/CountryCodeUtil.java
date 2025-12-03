package com.smartcoreinc.localpkd.common.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * 국가 코드 추출 및 검증 유틸리티
 * DN(Distinguished Name)에서 국가 코드 추출
 */
@Slf4j
public class CountryCodeUtil {

    // ISO 3166-1 alpha-2 국가 코드 패턴
    private static final Pattern COUNTRY_CODE_PATTERN = Pattern.compile("\\bc=([A-Z]{2})\\b", Pattern.CASE_INSENSITIVE);
    private static final String UNKNOWN_COUNTRY = "UNKNOWN";

    private CountryCodeUtil() {
        // Utility class - private constructor
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
