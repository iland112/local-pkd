package com.smartcoreinc.localpkd.common.enums;

import lombok.Getter;

/**
 * ICAO PKD 인증서 타입
 * 
 * eMRTD (전자 여권) PKI 계층 구조:
 * CSCA (Country Signing CA)
 *   └─ DSC (Document Signer Certificate)
 *   |    └─ 전자 여권 서명
 *   └─ DSC-NC(Non Conformant DSC)
 * 
 * VDS (Visible Digital Seal) 계층 구조:
 * CSCA (Country Signing CA)
 *   └─ BCSC (Bar Code Signer Certificate)
 *       ├─ BCSC (VDS) - 표준 VDS
 *       └─ BCSC-NC (VDS-NC) - Non-Constrained VDS
 */
@Getter
public enum CertificateType {
    /**
     * CSCA (Country Signing Certificate Authority)
     * 국가 서명 인증 기관
     * - Collection #1 (CSCA Master List)에 포함
     * - 모든 DSC와 BCSC의 발급자
     */
    CSCA(
        "CSCA", 
        "Country Signing Certificate Authority", 
        "국가 서명 인증 기관",
        true,   // isCA
        null    // parentType
    ),
    
    /**
     * DSC (Document Signer Certificate)
     * 전자 여권 문서 서명자 인증서
     * - Collection #2 (eMRTD PKI Objects)에 포함
     * - CSCA에 의해 발급됨
     * - 전자 여권의 칩 데이터(DG)를 서명
     */
    DSC(
        "DSC",
        "Document Signer Certificate",
        "전자 여권 서명자 인증서",
        false,  // isCA
        CSCA    // parentType
    ),

    DSC_NC(
        "DSC_NC",
        "Non Comformant Document Signer Certificate",
        "부 적합 전자 여권 서명자 인증서",
        false,  // isCA
        CSCA    // parentType
    ),
    
    /**
     * BCSC (Bar Code Signer Certificate) - Standard VDS
     * 표준 VDS 서명자 인증서
     * - Collection #2 (eMRTD PKI Objects)에 포함
     * - CSCA에 의해 발급됨
     * - VDS (Visible Digital Seal) 서명
     */
    BCSC(
        "BCSC",
        "Bar Code Signer Certificate (VDS)",
        "바코드 서명자 인증서 (표준 VDS)",
        false,  // isCA
        CSCA    // parentType
    ),
    
    /**
     * BCSC-NC (Bar Code Signer Certificate - Non-Constrained) - VDS-NC
     * 비제약 VDS 서명자 인증서
     * - Collection #2 (eMRTD PKI Objects)에 포함
     * - CSCA에 의해 발급됨
     * - VDS-NC (Non-Constrained Visible Digital Seal) 서명
     * - 더 유연한 VDS 사용 시나리오
     */
    BCSC_NC(
        "BCSC-NC",
        "Bar Code Signer Certificate (VDS-NC)",
        "바코드 서명자 인증서 (비제약 VDS)",
        false,  // isCA
        CSCA    // parentType
    );
    
    private final String code;
    private final String name;
    private final String koreanName;
    private final boolean isCA;
    private final CertificateType parentType;
    
    CertificateType(String code, String name, String koreanName, boolean isCA, CertificateType parentType) {
        this.code = code;
        this.name = name;
        this.koreanName = koreanName;
        this.isCA = isCA;
        this.parentType = parentType;
    }
    
    /**
     * CSCA 타입인지 확인
     */
    public boolean isCsca() {
        return this == CSCA;
    }
    
    /**
     * DSC 타입인지 확인
     */
    public boolean isDsc() {
        return this == DSC;
    }

    /**
     * DSC_NC 타입인지 확인
     */
    public boolean isDscNc() {
        return this == DSC_NC;
    }
    
    /**
     * BCSC 타입인지 확인 (BCSC 또는 BCSC-NC)
     */
    public boolean isBcsc() {
        return this == BCSC || this == BCSC_NC;
    }
    
    /**
     * VDS 관련 인증서인지 확인
     */
    public boolean isVdsRelated() {
        return isBcsc();
    }
    
    /**
     * eMRTD 관련 인증서인지 확인
     */
    public boolean isEmRtdRelated() {
        return this == DSC || this == CSCA;
    }
    
    /**
     * End-Entity 인증서인지 확인 (CA가 아닌 경우)
     */
    public boolean isEndEntity() {
        return !isCA;
    }
    
    /**
     * Code로부터 CertificateType 생성
     * 
     * @param code 인증서 타입 코드
     * @return CertificateType
     */
    public static CertificateType fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Code cannot be null");
        }
        
        // 대소문자 무시 및 공백 제거
        String normalized = code.trim().toUpperCase();
        
        for (CertificateType type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        
        // BCSC-NC의 경우 다양한 표현 허용
        if ("BCSCNC".equals(normalized) || "BCSC_NC".equals(normalized)) {
            return BCSC_NC;
        }
        
        throw new IllegalArgumentException("Unknown certificate type code: " + code);
    }
    
    /**
     * LDIF objectClass로부터 CertificateType 추론
     * 
     * @param objectClasses LDIF objectClass 목록
     * @return CertificateType 또는 null
     */
    public static CertificateType fromLdifObjectClasses(String[] objectClasses) {
        if (objectClasses == null || objectClasses.length == 0) {
            return null;
        }
        
        for (String oc : objectClasses) {
            String lower = oc.toLowerCase();
            
            if (lower.contains("csca") || lower.contains("countrysigning")) {
                return CSCA;
            }
            if (lower.contains("documentsigner") || lower.contains("dsc")) {
                return DSC;
            }
            if (lower.contains("barcodesigner") || lower.contains("bcsc")) {
                if (lower.contains("nonconstrained") || lower.contains("nc")) {
                    return BCSC_NC;
                }
                return BCSC;
            }
        }
        
        return null;
    }
    
    /**
     * 상위 타입이 유효한지 검증
     * 
     * @param issuerType 발급자 타입
     * @return true if valid
     */
    public boolean isValidIssuer(CertificateType issuerType) {
        return this.parentType == issuerType;
    }
    
    @Override
    public String toString() {
        return code;
    }
}
