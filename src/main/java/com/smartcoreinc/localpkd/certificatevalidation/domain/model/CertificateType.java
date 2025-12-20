package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

/**
 * CertificateType - 인증서 타입 Enum
 *
 * <p>ICAO PKD 인증서 타입을 정의합니다.</p>
 *
 * <p><b>참고</b>:
 * ICAO Doc 9303 Part 12: 보안 요소의 테크니컬 스펙
 * </p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-23
 */
public enum CertificateType {

    /**
     * CSCA - Country Signing CA
     *
     * <p>국가 서명 인증 기관</p>
     * <ul>
     *   <li>모든 DSC의 발급자</li>
     *   <li>다른 CSCA를 발급할 수 있음 (교차 인증)</li>
     *   <li>Self-signed 또는 다른 CSCA에 의해 서명됨</li>
     *   <li>BasicConstraints: CA=true, pathLenConstraint=1</li>
     * </ul>
     */
    CSCA("CSCA (Country Signing CA)", "CSCA"),

    /**
     * DSC - Document Signer Certificate
     *
     * <p>문서 서명 인증서 (일반)</p>
     * <ul>
     *   <li>ePassport 관련 문서 서명</li>
     *   <li>CSCA에 의해 발급</li>
     *   <li>digitalSignature, nonRepudiation 용도</li>
     *   <li>BasicConstraints: CA=false</li>
     * </ul>
     */
    DSC("DSC (Document Signer Certificate)", "DSC"),

    /**
     * DSC_NC - Document Signer Certificate (No ePassport Linking)
     *
     * <p>문서 서명 인증서 (ePassport 비연결)</p>
     * <ul>
     *   <li>ePassport와 직접 연결되지 않은 DSC</li>
     *   <li>추가 서명 목적용</li>
     *   <li>CSCA에 의해 발급</li>
     * </ul>
     */
    DSC_NC("DSC_NC (Document Signer Certificate - No ePassport Linking)", "DSC_NC"),

    /**
     * DS - Document Signer
     *
     * <p>문서 서명자 (일반)</p>
     * <ul>
     *   <li>DSC와 유사하지만 더 일반적인 용도</li>
     *   <li>다양한 문서 서명에 사용</li>
     * </ul>
     */
    DS("DS (Document Signer)", "DS"),

    /**
     * UNKNOWN - 불명 또는 기타
     *
     * <p>타입을 결정할 수 없는 경우</p>
     */
    UNKNOWN("UNKNOWN (Unknown or Other)", "UNKNOWN");

    private final String displayName;
    private final String code;

    CertificateType(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }

    /**
     * CA 인증서 여부
     *
     * @return CSCA이면 true
     */
    public boolean isCA() {
        return this == CSCA;
    }

    /**
     * 문서 서명 인증서 여부
     *
     * @return DSC, DSC_NC, DS 중 하나이면 true
     */
    public boolean isDocumentSigner() {
        return this == DSC || this == DSC_NC || this == DS;
    }

    /**
     * ICAO PKD 표준 타입 여부
     *
     * @return UNKNOWN이 아니면 true
     */
    public boolean isStandardType() {
        return this != UNKNOWN;
    }

    /**
     * 문자열로부터 CertificateType 조회
     *
     * @param code 인증서 타입 코드 (대소문자 무시)
     * @return CertificateType (찾지 못하면 UNKNOWN)
     */
    public static CertificateType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN;
        }

        try {
            return valueOf(code.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
