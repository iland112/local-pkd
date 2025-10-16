package com.smartcoreinc.localpkd.common.enums;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;

/**
 * ICAO PKD 파일 포맷 정의
 *
 * ICAO PKD 공식 다운로드 페이지 기준 Collection 구조:
 * - Collection #1 (001): eMRTD PKI Objects
 *   → DSC, BCSC, BCSC-NC (VDS-NC), CRL 포함
 *   → Latest: icaopkd-001-complete-009410.ldif (74.3 MiB)
 *
 * - Collection #2 (002): CSCA Master Lists
 *   → CSCA 인증서만 포함
 *   → Latest: icaopkd-002-complete-000325.ldif (9.7 MiB)
 *
 * - Collection #3 (003): Non-Conformant (Deprecated)
 *   → Non-Conformant DSC, CRL
 *   → Latest: icaopkd-003-complete-000090.ldif (1.5 MiB)
 *   → 더 이상 업데이트 안 됨
 *
 * - Master List (.ml): CSCA Master List (CMS Signed, Legacy)
 *
 * ⚠️ 주의: Enum 이름(CSCA_*, EMRTD_*)과 실제 Collection 번호가 반대!
 * - CSCA_COMPLETE_LDIF → Collection #001 (실제로는 eMRTD)
 * - EMRTD_COMPLETE_LDIF → Collection #002 (실제로는 CSCA)
 *
 * 파일명 패턴:
 * - ML Signed CMS: ICAO_ml_{Month}{Year}.ml
 * - LDIF Complete: icaopkd-{collection}-complete-{version}.ldif
 * - LDIF Delta: icaopkd-{collection}-delta-{version}.ldif
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @see <a href="https://pkddownloadsg.icao.int/download">ICAO PKD Download</a>
 */
@Getter
public enum FileFormat {
    // ========================================
    // Master List (.ml 파일)
    // ========================================

    /**
     * CSCA Master List - CMS Signed Format (Legacy)
     * 파일명: ICAO_ml_July2025.ml
     * 용도: CMS 서명된 CSCA 인증서 목록 (레거시)
     */
    ML_SIGNED_CMS(
        "ml",
        "002",  // ML 파일은 Collection #2 (CSCA) 내용과 동일
        null,
        "CSCA Master List (CMS Signed)",
        false
    ),

    // ========================================
    // Collection #1: eMRTD PKI Objects
    // ========================================
    // ICAO 공식: "The latest collection of eMRTD PKI objects (Document Signer
    //            certificates (DSCs), Bar Code Signer certificates (BCSCs/VDSs),
    //            Bar Code Signer for non-constrained environments certificates
    //            (BCSC-NCs/VDS-NCs) and Certificate Revocation Lists (CRLs))"
    //
    // ⚠️ 주의: Enum 이름은 CSCA이지만, 실제로는 Collection #1 (eMRTD)
    //         레거시 코드이며 변경 시 전체 리팩토링 필요

    /**
     * Collection #001 - eMRTD PKI Objects Complete
     *
     * 파일명: icaopkd-001-complete-{version}.ldif
     * 예시: icaopkd-001-complete-009410.ldif
     *
     * 공식 설명: "The latest collection of eMRTD PKI objects"
     * 포함 내용:
     * - DSC (Document Signer Certificates)
     * - BCSC (Bar Code Signer Certificates / VDS)
     * - BCSC-NC (VDS for Non-Constrained environments)
     * - CRL (Certificate Revocation Lists)
     *
     * Latest Version: 009410 (74.3 MiB)
     *
     * ⚠️ 주의: Enum 이름은 CSCA_* 이지만 실제로는 eMRTD Collection
     */
    CSCA_COMPLETE_LDIF(
        "ldif",
        "001",
        null,
        "eMRTD PKI Objects Complete [Collection #1]",
        false
    ),

    /**
     * Collection #001 - eMRTD PKI Objects Delta
     *
     * 파일명: icaopkd-001-delta-{version}.ldif
     * 예시: icaopkd-001-delta-009410.ldif
     *
     * 공식 설명: "eMRTD Delta {version}"
     * 포함 내용: DSC, BCSC, BCSC-NC, CRL의 증분 업데이트
     *
     * ⚠️ 주의: Enum 이름은 CSCA_* 이지만 실제로는 eMRTD Collection
     */
    CSCA_DELTA_LDIF(
        "ldif",
        "001",
        "delta",
        "eMRTD PKI Objects Delta [Collection #1]",
        true
    ),

    // ========================================
    // Collection #2: CSCA Master Lists
    // ========================================
    // ICAO 공식: "The latest collection of CSCA Master Lists."
    //
    // ⚠️ 주의: Enum 이름은 EMRTD이지만, 실제로는 Collection #2 (CSCA)
    //         레거시 코드이며 변경 시 전체 리팩토링 필요

    /**
     * Collection #002 - CSCA Master Lists Complete
     *
     * 파일명: icaopkd-002-complete-{version}.ldif
     * 예시: icaopkd-002-complete-000325.ldif
     *
     * 공식 설명: "The latest collection of CSCA Master Lists."
     * 포함 내용:
     * - CSCA (Country Signing Certificate Authority)
     * - DN 구조: o=ml (Master List)
     *
     * Latest Version: 000325 (9.7 MiB)
     *
     * ⚠️ 주의: Enum 이름은 EMRTD_* 이지만 실제로는 CSCA Collection
     */
    EMRTD_COMPLETE_LDIF(
        "ldif",
        "002",
        null,
        "CSCA Master Lists Complete [Collection #2]",
        false
    ),

    /**
     * Collection #002 - CSCA Master Lists Delta
     *
     * 파일명: icaopkd-002-delta-{version}.ldif
     * 예시: icaopkd-002-delta-000325.ldif
     *
     * 공식 설명: "CSCA MasterList Delta {version}"
     * 포함 내용: CSCA 인증서의 증분 업데이트
     *
     * ⚠️ 주의: Enum 이름은 EMRTD_* 이지만 실제로는 CSCA Collection
     */
    EMRTD_DELTA_LDIF(
        "ldif",
        "002",
        "delta",
        "CSCA Master Lists Delta [Collection #2]",
        true
    ),
    
    // ========================================
    // Collection #3: Non-Conformant (Deprecated)
    // ========================================
    // ICAO 공식: "The latest collection of NON-CONFORMANT Document Signer
    //            certificates (DSCs) and Certificate Revocation Lists (CRLs)
    //            to verify electronic passports. Note: the non-conformance
    //            branch has been deprecated and does not receive updates."

    /**
     * Collection #003 - Non-Conformant Complete [DEPRECATED]
     *
     * 파일명: icaopkd-003-complete-{version}.ldif
     * 예시: icaopkd-003-complete-000090.ldif
     *
     * 공식 설명: "The latest collection of NON-CONFORMANT Document Signer
     *           certificates (DSCs) and Certificate Revocation Lists (CRLs)"
     *
     * 포함 내용:
     * - Non-Conformant DSC
     * - Non-Conformant CRL
     * - DN 구조: dc=nc-data (non-conformant data)
     *
     * Latest Version: 000090 (1.5 MiB)
     *
     * ⚠️ 주의: 더 이상 업데이트되지 않음 (Deprecated)
     */
    NON_CONFORMANT_COMPLETE_LDIF(
        "ldif",
        "003",
        null,
        "Non-Conformant Complete [Collection #3] [DEPRECATED]",
        false
    ),

    /**
     * Collection #003 - Non-Conformant Delta [DEPRECATED]
     *
     * 파일명: icaopkd-003-delta-{version}.ldif
     * 예시: icaopkd-003-delta-000090.ldif
     *
     * 공식 설명: "eMRTD NON-CONFORMANT Delta {version}"
     * 포함 내용: Non-Conformant DSC, CRL의 증분 업데이트
     *
     * ⚠️ 주의: 더 이상 업데이트되지 않음 (Deprecated)
     */
    NON_CONFORMANT_DELTA_LDIF(
        "ldif",
        "003",
        "delta",
        "Non-Conformant Delta [Collection #3] [DEPRECATED]",
        true
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
     * - Delta: icaopkd-001-delta-009409.ldif, icaopkd-002-delta-000318.ldif
     *
     * 실제 ICAO PKD 파일은 단순 "delta" 형식 사용 (dscs-delta, crls-delta 등 없음)
     */
    private static final Pattern LDIF_PATTERN =
        Pattern.compile("icaopkd-(\\d{3})-(complete|delta)-(\\d+)\\.ldif", Pattern.CASE_INSENSITIVE);
    
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
            String collection = ldifMatcher.group(1);  // 001, 002, 003
            String type = ldifMatcher.group(2);        // complete or delta

            return detectLdifFormat(collection, type);
        }
        
        throw new IllegalArgumentException("Unsupported ICAO PKD file format: " + filename);
    }

    /**
     * LDIF 파일 포맷 세부 감지
     *
     * @param collection Collection 번호 (001, 002, 003)
     * @param type complete 또는 delta
     * @return 감지된 FileFormat
     */
    private static FileFormat detectLdifFormat(String collection, String type) {
        boolean isComplete = "complete".equalsIgnoreCase(type);

        // Collection #1: CSCA Master List
        if ("001".equals(collection)) {
            return isComplete ? CSCA_COMPLETE_LDIF : CSCA_DELTA_LDIF;
        }

        // Collection #2: eMRTD PKI Objects
        if ("002".equals(collection)) {
            return isComplete ? EMRTD_COMPLETE_LDIF : EMRTD_DELTA_LDIF;
        }

        // Collection #3: Non-Conformant
        if ("003".equals(collection)) {
            return isComplete ? NON_CONFORMANT_COMPLETE_LDIF : NON_CONFORMANT_DELTA_LDIF;
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
            return ldifMatcher.group(3);  // 변경된 정규식: group(3)이 버전 번호
        }

        return null;
    }

    /**
     * 파일명에서 Collection 번호 추출
     *
     * ML 파일의 경우: 항상 "002" (CSCA Master List와 동일 내용)
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

        // ML 파일 - Collection #2 (CSCA) 내용과 동일
        Matcher mlMatcher = ML_PATTERN.matcher(normalized);
        if (mlMatcher.matches()) {
            return "002";
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
     * @return Delta 타입 ("delta") 또는 null (Complete 파일)
     * 주의: 실제 ICAO PKD는 세부 타입 없이 단순 "delta" 사용
     */
    public static String extractDeltaType(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }

        String normalized = filename.toLowerCase().trim();

        Matcher ldifMatcher = LDIF_PATTERN.matcher(normalized);
        if (ldifMatcher.matches()) {
            String type = ldifMatcher.group(2); // "complete" or "delta"
            return "delta".equalsIgnoreCase(type) ? "delta" : null;
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
            case EMRTD_COMPLETE_LDIF, EMRTD_DELTA_LDIF ->
                FileType.EMRTD_PKI_OBJECTS;
            case NON_CONFORMANT_COMPLETE_LDIF, NON_CONFORMANT_DELTA_LDIF ->
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
        return this == NON_CONFORMANT_COMPLETE_LDIF || this == NON_CONFORMANT_DELTA_LDIF;
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
