package com.smartcoreinc.localpkd.common.enums;

import lombok.Getter;

/**
 * PKD 파일 타입 분류
 * ICAO PKD Collection 기반
 */
@Getter
public enum FileType {
    /**
     * CSCA (Country Signing CA) Master List
     * Collection #1: 전체 회원국의 CSCA 인증서
     */
    CSCA("CSCA", "Country Signing CA Master List", "001"),
    
    /**
     * DSC/CRL (Document Signer Certificate + CRL)
     * Collection #2: DSC 인증서 및 폐기 목록
     */
    DSC_CRL("DSC_CRL", "Document Signer Certificates and CRLs", "002"),
    
    /**
     * Non-Conformant (비표준)
     * Collection #3: 더 이상 업데이트되지 않음 (Deprecated)
     */
    NON_CONFORMANT("NON_CONFORMANT", "Non-Conformant (Deprecated)", "003"),

    /**
     * Master List Legacy (CMS 포맷)
     * .ml 파일 지원
     */
    ML("ML", "Master List CMS", null);
    
    
    private final String code;
    private final String description;
    private final String collectionNumber;
    
    FileType(String code, String description, String collectionNumber) {
        this.code = code;
        this.description = description;
        this.collectionNumber = collectionNumber;
    }
    
    /**
     * Collection 번호로 FileType 조회
     * 
     * @param collectionNumber Collection 번호 (예: "001", "002", "003")
     * @return FileType
     * @throws IllegalArgumentException 유효하지 않은 Collection 번호
     */
    public static FileType fromCollectionNumber(String collectionNumber) {
        if (collectionNumber == null) {
            throw new IllegalArgumentException("Collection number cannot be null");
        }
        
        for (FileType type : values()) {
            if (collectionNumber.equals(type.collectionNumber)) {
                return type;
            }
        }
        
        throw new IllegalArgumentException("Unknown collection number: " + collectionNumber);
    }
    
    /**
     * Code로 FileType 조회
     * 
     * @param code FileType code
     * @return FileType
     * @throws IllegalArgumentException 유효하지 않은 code
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
     * 이 파일 타입이 ICAO PKD Collection에 속하는지 확인
     * 
     * @return true if ICAO PKD collection
     */
    public boolean isIcaoPkdCollection() {
        return collectionNumber != null;
    }
    
    /**
     * 이 파일 타입이 deprecated인지 확인
     * 
     * @return true if deprecated
     */
    public boolean isDeprecated() {
        return this == NON_CONFORMANT;
    }
    
    /**
     * 이 파일 타입이 레거시인지 확인
     * 
     * @return true if ML
     */
    public boolean isML() {
        return this == ML;
    }
    
    @Override
    public String toString() {
        return code;
    }
}
