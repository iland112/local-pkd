package com.smartcoreinc.localpkd.common.enums;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;

/**
 * ICAO PKD 파일 포맷 정의
 * 
 * ICAO PKD는 3가지 Collection으로 구성:
 * - Collection #1 (001): CSCA Master List
 * - Collection #2 (002): eMRTD PKI Objects (DSC, BCSC, CRL)
 * - Collection #3 (003): Non-Conformant (Deprecated)
 * 
 * 파일명 패턴:
 * - ML Signed CMS: icaopkd-001-ml-{version}.ml
 * - LDIF Complete: icaopkd-{collection}-complete-{version}.ldif
 * - LDIF Delta: icaopkd-{collection}-{type}-delta-{version}.ldif
 * 
 * @author SmartCore Inc.
 * @version 1.0
 */
@Getter
public enum FileFormat {
    // ========================================
    // Collection #1: CSCA Master List
    // ========================================
    
    /**
     * CSCA Master List - CMS Signed Format (Legacy)
     * 파일명: icaopkd-001-ml-000325.ml
     * 용도: CMS 서명된 CSCA 인증서 목록 (레거시)
     */
    ML_SIGNED_CMS(
        "ml",
        "001",
        null,
        "CSCA Master List (CMS Signed)",
        false
    ),
    
    /**
     * CSCA Master List - LDIF Complete
     * 파일명: icaopkd-001-complete-000325.ldif
     * 용도: 전체 CSCA 인증서 목록 (LDIF)
     */
    CSCA_COMPLETE_LDIF(
        "ldif",
        "001",
        null,
        "CSCA Master List Complete (LDIF)",
        false
    ),
    
    /**
     * CSCA Master List - LDIF Delta
     * 파일명: icaopkd-001-ml-delta-000326.ldif
     * 용도: CSCA 인증서 증분 업데이트
     */
    CSCA_DELTA_LDIF(
        "ldif",
        "001",
        "ml",
        "CSCA Master List Delta (LDIF)",
        true
    ),
    
    // ========================================
    // Collection #2: eMRTD PKI Objects
    // ========================================
    
    /**
     * eMRTD PKI Objects - LDIF Complete
     * 파일명: icaopkd-002-complete-009398.ldif
     * 용도: 전체 DSC, BCSC, CRL (LDIF)
     */
    EMRTD_COMPLETE_LDIF(
        "ldif",
        "002",
        null,
        "eMRTD PKI Objects Complete (LDIF)",
        false
    ),
    
    /**
     * Document Signer Certificates - LDIF Delta
     * 파일명: icaopkd-002-dscs-delta-009399.ldif
     * 용도: DSC 증분 업데이트
     */
    DSC_DELTA_LDIF(
        "ldif",
        "002",
        "dscs",
        "Document Signer Certificates Delta (LDIF)",
        true
    ),
    
    /**
     * Bar Code Signer Certificates - LDIF Delta
     * 파일명: icaopkd-002-bcscs-delta-009399.ldif
     * 용도: BCSC (VDS) 증분 업데이트
     */
    BCSC_DELTA_LDIF(
        "ldif",
        "002",
        "bcscs",
        "Bar Code Signer Certificates Delta (LDIF)",
        true
    ),
    
    /**
     * Certificate Revocation Lists - LDIF Delta
     * 파일명: icaopkd-002-crls-delta-009399.ldif
     * 용도: CRL 증분 업데이트
     */
    CRL_DELTA_LDIF(
        "ldif",
        "002",
        "crls",
        "Certificate Revocation Lists Delta (LDIF)",
        true
    ),
    
    // ========================================
    // Collection #3: Non-Conformant (Deprecated)
    // ========================================
    
    /**
     * Non-Conformant - LDIF Complete
     * 파일명: icaopkd-003-complete-000090.ldif
     * 용도: 비표준 인증서 (더 이상 업데이트 안 됨)
     */
    NON_CONFORMANT_LDIF(
        "ldif",
        "003",
        null,
        "Non-Conformant Complete (LDIF) [Deprecated]",
        false
    );
    
    // ========================================
    // 필드 정의
    // ========================================
    
    /**
     * 파일 확장자
     */
    private final String extension;
    
    /**
     * ICAO PKD Collection 번호 (001, 002, 003)
     */
    private final String collectionNumber;
    
    /**
     * Delta 타입 (ml, dscs, bcscs, crls)
     * Complete 파일의 경우 null
     */
    private final String deltaType;
    
    /**
     * 파일 포맷 설명
     */
    private final String description;
    
    /**
     * Delta 파일 여부
     */
    private final boolean isDelta;
    
    // ========================================
    // 정규식 패턴
    // ========================================
    
    /**
     * ML 파일 패턴 (ICAO 표준): ICAO_ml_July2025.ml, ICAO_ml_01April2025.ml
     * 패턴: ICAO_ml_{Month}{Year}.ml 또는 ICAO_ml_{DD}{Month}{Year}.ml
     */
    private static final Pattern ML_PATTERN = 
        Pattern.compile("ICAO_ml_(?:(\\d{2}))?([A-Za-z]+)(\\d{4})\\.ml", Pattern.CASE_INSENSITIVE);
    
    /**
     * LDIF 파일 패턴: icaopkd-{collection}-{type}-{version}.ldif
     * - Complete: icaopkd-001-complete-000325.ldif
     * - Delta: icaopkd-002-dscs-delta-009399.ldif
     */
    private static final Pattern LDIF_PATTERN = 
        Pattern.compile("icaopkd-(\\d{3})-(complete|([a-z]+)-delta)-(\\d+)\\.ldif", Pattern.CASE_INSENSITIVE);
    
    FileFormat(String extension, String collectionNumber, String deltaType, String description, boolean isDelta) {
        this.extension = extension;
        this.collectionNumber = collectionNumber;
        this.deltaType = deltaType;
        this.description = description;
        this.isDelta = isDelta;
    }

    // ========================================
    // 파일명 감지 메서드
    // ========================================
    
    /**
     * 파일명으로부터 FileFormat 자동 감지
     * 
     * @param filename ICAO PKD 파일명
     * @return 감지된 FileFormat
     * @throws IllegalArgumentException 지원하지 않는 파일 형식
     */
    public static FileFormat detectFromFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }
        
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }
        
        String normalizedFilename = filename.toLowerCase().trim();
        
        // ML 파일 감지
        Matcher mlMatcher = ML_PATTERN.matcher(normalizedFilename);
        if (mlMatcher.matches()) {
            return ML_SIGNED_CMS;
        }
        
        // LDIF 파일 감지
        Matcher ldifMatcher = LDIF_PATTERN.matcher(normalizedFilename);
        if (ldifMatcher.matches()) {
            String collection = ldifMatcher.group(1);
            String typeOrComplete = ldifMatcher.group(2);
            String deltaType = ldifMatcher.group(3); // null for complete files
            
            return detectLdifFormat(collection, typeOrComplete, deltaType);
        }
        
        throw new IllegalArgumentException("Unsupported ICAO PKD file format: " + filename);
    }

    /**
     * LDIF 파일 포맷 세부 감지
     */
    private static FileFormat detectLdifFormat(String collection, String typeOrComplete, String deltaType) {
        // Collection #1: CSCA Master List
        if ("001".equals(collection)) {
            if ("complete".equalsIgnoreCase(typeOrComplete)) {
                return CSCA_COMPLETE_LDIF;
            } else if ("ml".equalsIgnoreCase(deltaType)) {
                return CSCA_DELTA_LDIF;
            }
        }
        
        // Collection #2: eMRTD PKI Objects
        if ("002".equals(collection)) {
            if ("complete".equalsIgnoreCase(typeOrComplete)) {
                return EMRTD_COMPLETE_LDIF;
            } else if (deltaType != null) {
                return switch (deltaType.toLowerCase()) {
                    case "dscs" -> DSC_DELTA_LDIF;
                    case "bcscs" -> BCSC_DELTA_LDIF;
                    case "crls" -> CRL_DELTA_LDIF;
                    default -> throw new IllegalArgumentException("Unknown delta type: " + deltaType);
                };
            }
        }
        
        // Collection #3: Non-Conformant
        if ("003".equals(collection)) {
            return NON_CONFORMANT_LDIF;
        }
        
        throw new IllegalArgumentException("Unknown collection number: " + collection);
    }
    
    // ========================================
    // 메타데이터 추출 메서드
    // ========================================
    
    /**
     * 파일명에서 버전 번호 추출
     * 
     * ML 파일의 경우: Month/Year를 버전으로 사용
     * - ICAO_ml_July2025.ml → "July2025"
     * - ICAO_ml_01April2025.ml → "01April2025"
     * 
     * LDIF 파일의 경우: 숫자 버전
     * - icaopkd-001-complete-000325.ldif → "000325"
     * 
     * @param filename ICAO PKD 파일명
     * @return 버전 번호
     */
    public static String extractVersion(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        
        String normalized = filename.trim();
        
        // ML 파일 - Month/Year 형식
        Matcher mlMatcher = ML_PATTERN.matcher(normalized);
        if (mlMatcher.matches()) {
            String day = mlMatcher.group(1);        // null or "01"
            String month = mlMatcher.group(2);      // "July", "April"
            String year = mlMatcher.group(3);       // "2025"
            
            if (day != null) {
                return day + month + year;  // "01April2025"
            } else {
                return month + year;        // "July2025"
            }
        }
        
        // LDIF 파일 - 숫자 버전
        Matcher ldifMatcher = LDIF_PATTERN.matcher(normalized.toLowerCase());
        if (ldifMatcher.matches()) {
            return ldifMatcher.group(4);
        }
        
        return null;
    }

    /**
     * 파일명에서 Collection 번호 추출
     * 
     * ML 파일의 경우: 항상 "001" (CSCA Master List)
     * LDIF 파일의 경우: 파일명에서 추출 (001, 002, 003)
     * 
     * @param filename ICAO PKD 파일명
     * @return Collection 번호 (001, 002, 003)
     */
    public static String extractCollectionNumber(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        
        String normalized = filename.trim();
        
        // ML 파일 - 항상 Collection #1 (CSCA)
        Matcher mlMatcher = ML_PATTERN.matcher(normalized);
        if (mlMatcher.matches()) {
            return "001";
        }
        
        // LDIF 파일
        Matcher ldifMatcher = LDIF_PATTERN.matcher(normalized.toLowerCase());
        if (ldifMatcher.matches()) {
            return ldifMatcher.group(1);
        }
        
        return null;
    }
    
    /**
     * 파일명에서 Delta 타입 추출
     * 
     * @param filename ICAO PKD 파일명
     * @return Delta 타입 (ml, dscs, bcscs, crls) 또는 null (Complete 파일)
     */
    public static String extractDeltaType(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        
        String normalized = filename.toLowerCase().trim();
        
        Matcher ldifMatcher = LDIF_PATTERN.matcher(normalized);
        if (ldifMatcher.matches()) {
            return ldifMatcher.group(3); // Delta type, null for complete
        }
        
        return null;
    }
    
    /**
     * 파일명 유효성 검증
     * 
     * @param filename ICAO PKD 파일명
     * @return 유효한 파일명 여부
     */
    public static boolean isValidFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        
        String normalized = filename.trim();
        return ML_PATTERN.matcher(normalized).matches() || 
               LDIF_PATTERN.matcher(normalized.toLowerCase()).matches();
    }

    // ========================================
    // 파일명 생성 메서드
    // ========================================
    
    /**
     * 표준 ICAO PKD 파일명 생성
     * 
     * ML 파일의 경우 버전은 Month/Year 형식
     * - "July2025" → "ICAO_ml_July2025.ml"
     * - "01April2025" → "ICAO_ml_01April2025.ml"
     * 
     * LDIF 파일의 경우 버전은 숫자
     * - "000325" → "icaopkd-001-complete-000325.ldif"
     * 
     * @param version 버전 번호
     * @return 생성된 파일명
     */
    public String buildFilename(String version) {
        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException("Version cannot be null or empty");
        }
        
        // ML Signed CMS - ICAO_ml_{version}.ml
        if (this == ML_SIGNED_CMS) {
            return String.format("ICAO_ml_%s.%s", version, extension);
        }
        
        // LDIF Complete
        if (!isDelta) {
            return String.format("icaopkd-%s-complete-%s.%s", 
                collectionNumber, version, extension);
        }
        
        // LDIF Delta
        return String.format("icaopkd-%s-%s-delta-%s.%s", 
            collectionNumber, deltaType, version, extension);
    }
    
    // ========================================
    // 타입 매핑 메서드
    // ========================================
    
    /**
     * FileFormat에 대응하는 FileType 반환
     * 
     * @return 대응하는 FileType
     */
    public FileType getFileType() {
        return switch (this) {
            case ML_SIGNED_CMS, CSCA_COMPLETE_LDIF, CSCA_DELTA_LDIF -> 
                FileType.CSCA_MASTER_LIST;
            case EMRTD_COMPLETE_LDIF, DSC_DELTA_LDIF, BCSC_DELTA_LDIF, CRL_DELTA_LDIF -> 
                FileType.EMRTD_PKI_OBJECTS;
            case NON_CONFORMANT_LDIF -> 
                FileType.NON_CONFORMANT;
        };
    }
    
    // ========================================
    // 편의 메서드
    // ========================================
    
    /**
     * CMS Signed 파일 여부
     */
    public boolean isSignedCms() {
        return this == ML_SIGNED_CMS;
    }
    
    /**
     * LDIF 파일 여부
     */
    public boolean isLdif() {
        return "ldif".equalsIgnoreCase(extension);
    }
    
    /**
     * Complete 파일 여부
     */
    public boolean isComplete() {
        return !isDelta;
    }
    
    /**
     * Deprecated 파일 여부
     */
    public boolean isDeprecated() {
        return this == NON_CONFORMANT_LDIF;
    }
    
    /**
     * CSCA 관련 파일 여부
     */
    public boolean isCsca() {
        return collectionNumber != null && "001".equals(collectionNumber);
    }
    
    /**
     * eMRTD PKI 관련 파일 여부
     */
    public boolean isEmrtdPki() {
        return collectionNumber != null && "002".equals(collectionNumber);
    }
    
    // ========================================
    // toString Override
    // ========================================
    
    @Override
    public String toString() {
        return String.format("%s [collection=%s, ext=%s, delta=%s, type=%s]",
            name(), collectionNumber, extension, isDelta, deltaType);
    }
}
