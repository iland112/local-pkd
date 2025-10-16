package com.smartcoreinc.localpkd.common.enums;

/**
 * LDIF Entry 타입
 *
 * LDIF 파일 내의 Entry가 어떤 데이터를 담고 있는지 구분
 * - CERTIFICATE: 인증서 (CSCA, DSC, BCSC 등)
 * - CRL: 인증서 폐기 목록
 * - UNKNOWN: 알 수 없는 타입
 *
 * @author SmartCore Inc.
 * @version 1.0
 */
public enum EntryType {

    /**
     * 인증서 Entry
     * - objectClass: pkiCA, cscaCertificateObject, documentSignerCertificateObject 등
     * - 속성: cACertificate;binary, userCertificate;binary
     */
    CERTIFICATE("인증서", "Certificate"),

    /**
     * CRL Entry
     * - objectClass: cRLDistributionPoint, certificateRevocationList
     * - 속성: certificateRevocationList;binary
     */
    CRL("인증서 폐기 목록", "CRL"),

    /**
     * 알 수 없는 타입
     * - 조직 단위, 루트 Entry 등
     */
    UNKNOWN("알 수 없음", "Unknown");

    private final String koreanName;
    private final String englishName;

    EntryType(String koreanName, String englishName) {
        this.koreanName = koreanName;
        this.englishName = englishName;
    }

    public String getKoreanName() {
        return koreanName;
    }

    public String getEnglishName() {
        return englishName;
    }

    /**
     * LDIF objectClass로부터 EntryType 추론
     *
     * @param objectClasses objectClass 배열
     * @return EntryType (추론 실패 시 UNKNOWN)
     */
    public static EntryType fromObjectClasses(String[] objectClasses) {
        if (objectClasses == null || objectClasses.length == 0) {
            return UNKNOWN;
        }

        for (String oc : objectClasses) {
            String lower = oc.toLowerCase();

            // CRL 확인
            if (lower.contains("crl") || lower.contains("revocation")) {
                return CRL;
            }

            // 인증서 확인
            if (lower.contains("certificate") ||
                lower.contains("csca") ||
                lower.contains("dsc") ||
                lower.contains("documentsigner") ||
                lower.contains("barcodesigner") ||
                lower.contains("pkica")) {
                return CERTIFICATE;
            }
        }

        return UNKNOWN;
    }

    /**
     * 인증서 타입 여부
     */
    public boolean isCertificate() {
        return this == CERTIFICATE;
    }

    /**
     * CRL 타입 여부
     */
    public boolean isCrl() {
        return this == CRL;
    }

    /**
     * 알 수 없는 타입 여부
     */
    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", koreanName, englishName);
    }
}