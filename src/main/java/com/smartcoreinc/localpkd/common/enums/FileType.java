package com.smartcoreinc.localpkd.common.enums;

import lombok.Getter;

/**
 * ICAO PKD 파일 타입
 * 
 * 실제 ICAO PKD 다운로드 사이트 기반:
 * - https://www.icao.int/icao-pkd/icao-master-list (ML 파일)
 * - https://pkddownloadsg.icao.int/ (LDIF 파일)
 */
@Getter
public enum FileType {
    /**
     * CSCA Master List
     * Collection #1
     * - ML: ICAO_ml-{version}.ml (Signed CMS)
     * - LDIF: icaopkd-{serial number}-complete-{version}.ldif
     *   : serial number
     *   - 001: The latest collection of eMRTD PKI objects (Document Signer certificates (DSCs),
     *          Bar Code Signer certificates (BCSCs/VDSs),
     *          Bar Code Signer for non-constrained environments certificates (BCSC-NCs/VDS-NCs)
     *           and Certificate Revocation Lists (CRLs)) to verify electronic passports.
     *   - 002: The latest collection of CSCA Master Lists.
     *   - 003: The latest collection of NON-CONFORMANT Document Signer certificates (DSCs)
     *           and Certificate Revocation Lists (CRLs) to verify electronic passports.
     *           Note: the non-conformance branch has been deprecated and does not receive updates.
     * - LDIF Delta: icaopkd-{serial number}-delta-{version}.ldif
     */
    CSCA_MASTER_LIST(
        "CSCA_MASTER_LIST", 
        "CSCA Master List", 
        "001",
        "Country Signing CA certificates from all member states"
    ),
    
    /**
     * eMRTD PKI Objects (DSC, BCSC, BCSC-NC, CRL)
     * Collection #2
     * - LDIF: icaopkd-002-complete-{version}.ldif
     * - LDIF Delta: icaopkd-002-{type}-delta-{version}.ldif
     *   where type = dscs | bcscs | crls
     */
    EMRTD_PKI_OBJECTS(
        "EMRTD_PKI_OBJECTS",
        "eMRTD PKI Objects",
        "002",
        "Document Signer Certificates (DSC), Bar Code Signer Certificates (BCSC), and CRLs"
    ),
    
    /**
     * Non-Conformant PKI Objects
     * Collection #3 (Deprecated)
     * - LDIF: icaopkd-003-complete-{version}.ldif
     */
    NON_CONFORMANT(
        "NON_CONFORMANT",
        "Non-Conformant PKI Objects",
        "003",
        "Non-conformant eMRTD PKI objects (Deprecated)"
    );
    
    private final String code;
    private final String displayName;
    private final String collectionNumber;
    private final String description;
    
    FileType(String code, String displayName, String collectionNumber, String description) {
        this.code = code;
        this.displayName = displayName;
        this.collectionNumber = collectionNumber;
        this.description = description;
    }
    
    /**
     * Collection 번호로 FileType 조회
     * 
     * @param collectionNumber Collection 번호 ("001", "002", "003")
     * @return FileType
     */
    public static FileType fromCollectionNumber(String collectionNumber) {
        if (collectionNumber == null) {
            throw new IllegalArgumentException("Collection number cannot be null");
        }
        
        for (FileType type : values()) {
            if (type.collectionNumber.equals(collectionNumber)) {
                return type;
            }
        }
        
        throw new IllegalArgumentException("Unknown collection number: " + collectionNumber);
    }
    
    /**
     * Code로 FileType 조회
     */
    public static FileType fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Code cannot be null");
        }
        
        for (FileType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        
        throw new IllegalArgumentException("Unknown file type code: " + code);
    }
    
    /**
     * Deprecated 여부 확인
     */
    public boolean isDeprecated() {
        return this == NON_CONFORMANT;
    }
    
    /**
     * CSCA 타입인지 확인
     */
    public boolean isCsca() {
        return this == CSCA_MASTER_LIST;
    }
    
    /**
     * eMRTD PKI 타입인지 확인
     */
    public boolean isEmRtdPki() {
        return this == EMRTD_PKI_OBJECTS;
    }
    
    @Override
    public String toString() {
        return code;
    }
}
