package com.smartcoreinc.localpkd.parser.common.domain;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.smartcoreinc.localpkd.common.enums.FileFormat;
import com.smartcoreinc.localpkd.common.enums.FileType;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 파일 파싱 컨텍스트
 * 
 * 파서가 파일을 처리하는 동안 필요한 모든 컨텍스트 정보를 담는 클래스
 * - 파일 메타데이터
 * - 파싱 옵션
 * - 처리 상태
 * - 추가 속성
 * 
 * @author SmartCore Inc.
 * @version 1.0
 */
@Getter
@Builder
@ToString
public class ParseContext {

    // ========================================
    // 필수 필드
    // ========================================
    
    /**
     * 파일 ID (PkdFile의 Primary Key)
     */
    private final String fileId;
    
    /**
     * 원본 파일명
     */
    private final String filename;
    
    /**
     * 파일 타입
     */
    private final FileType fileType;
    
    /**
     * 파일 포맷
     */
    private final FileFormat fileFormat;
    
    /**
     * 파일 데이터 (byte array)
     */
    private final byte[] fileData;
    
    /**
     * 파일 크기 (bytes)
     */
    private final long fileSize;

    // ========================================
    // 메타데이터 필드
    // ========================================
    
    /**
     * 버전 번호
     * - ML: "July2025", "01April2025"
     * - LDIF: "000325", "009399"
     */
    private final String version;
    
    /**
     * Collection 번호 (001, 002, 003)
     */
    private final String collectionNumber;
    
    /**
     * Delta 타입 (ml, dscs, bcscs, crls)
     * Complete 파일의 경우 null
     */
    private final String deltaType;
    
    /**
     * Delta 파일 여부
     */
    @Builder.Default
    private final boolean isDelta = false;
    
    // ========================================
    // 처리 옵션
    // ========================================
    
    /**
     * LDAP에 저장 여부
     */
    @Builder.Default
    private final boolean saveToLdap = true;
    
    /**
     * 데이터베이스에 저장 여부
     */
    @Builder.Default
    private final boolean saveToDatabase = true;
    
    /**
     * 인증서 검증 수행 여부
     */
    @Builder.Default
    private final boolean performValidation = true;
    
    /**
     * Trust Anchor 검증 수행 여부 (ML 파일)
     */
    @Builder.Default
    private final boolean verifyTrustChain = true;
    
    /**
     * 중복 인증서 허용 여부
     */
    @Builder.Default
    private final boolean allowDuplicates = false;
    
    /**
     * 오류 발생 시 계속 진행 여부
     */
    @Builder.Default
    private final boolean continueOnError = true;
    
    /**
     * 진행 상황 알림 여부
     */
    @Builder.Default
    private final boolean notifyProgress = true;
    
    // ========================================
    // 상태 추적
    // ========================================
    
    /**
     * 파싱 시작 시간
     */
    @Builder.Default
    private final LocalDateTime startTime = LocalDateTime.now();
    
    /**
     * 업로드한 사용자 (선택사항)
     */
    private final String uploadedBy;
    
    /**
     * 처리 우선순위 (1-10, 기본값 5)
     */
    @Builder.Default
    private final int priority = 5;
    
    // ========================================
    // 추가 속성 (확장 가능)
    // ========================================
    
    /**
     * 추가 속성 맵
     * 파서별로 필요한 커스텀 속성을 저장
     */
    @Builder.Default
    private final Map<String, Object> attributes = new HashMap<>();
    
    // ========================================
    // 편의 메서드
    // ========================================

    /**
     * 속성 추가
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 속성 조회
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * 속성 조회 (타입 지정)
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 속성 존재 여부
     */
    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }

    // ========================================
    // 타입 확인 메서드
    // ========================================
    
    /**
     * ML CMS 파일 여부
     */
    public boolean isMLSignedCms() {
        return fileFormat == FileFormat.ML_SIGNED_CMS;
    }

    /**
     * LDIF 파일 여부
     */
    public boolean isLdif() {
        return fileFormat != null && fileFormat.isLdif();
    }

    /**
     * Complete 파일 여부
     */
    public boolean isComplete() {
        return fileFormat != null && fileFormat.isComplete();
    }

    /**
     * CSCA 관련 파일 여부
     */
    public boolean isCsca() {
        return fileType == FileType.CSCA_MASTER_LIST;
    }

    /**
     * eMRTD PKI 관련 파일 여부
     */
    public boolean isEmrtdPki() {
        return fileType == FileType.EMRTD_PKI_OBJECTS;
    }

    /**
     * Deprecated 파일 여부
     */
    public boolean isDeprecated() {
        return fileType == FileType.NON_CONFORMANT;
    }

    // ========================================
    // Builder 헬퍼 메서드
    // ========================================
    
    /**
     * 파일명으로부터 컨텍스트 생성
     */
    public static ParseContext fromFilename(String fileId, String filename, byte[] fileData) {
        FileFormat format = FileFormat.detectFromFilename(filename);
        FileType type = format.getFileType();
        String version = FileFormat.extractVersion(filename);
        String collection = FileFormat.extractCollectionNumber(filename);
        String deltaType = FileFormat.extractDeltaType(filename);

        return ParseContext.builder()
            .fileId(fileId)
            .filename(filename)
            .fileType(type)
            .fileFormat(format)
            .fileData(fileData)
            .fileSize(fileData.length)
            .version(version)
            .collectionNumber(collection)
            .deltaType(deltaType)
            .isDelta(format.isDelta())
            .build();
    }

    /**
     * 기본 컨텍스트 생서 (검증 비활성화)
     */
    public static ParseContext createSimple(String fileId,
                                            String filename,
                                            FileType fileType,
                                            FileFormat fileFormat,
                                            byte[] fileData) {
        return ParseContext.builder()
            .fileId(fileId)
            .filename(filename)
            .fileType(fileType)
            .fileFormat(fileFormat)
            .fileData(fileData)
            .fileSize(fileData.length)
            .performValidation(false)
            .verifyTrustChain(false)
            .saveToLdap(false)
            .saveToDatabase(false)
            .build();
    }

    /**
     * 프로덕션 컨텍스트 생성 (모든 검증 활성화)
     */
    public static ParseContext createProduction(String fileId, String filename, byte[] fileData) {
        FileFormat format = FileFormat.detectFromFilename(filename);
        FileType type = format.getFileType();
        String version = FileFormat.extractVersion(filename);
        String collection = FileFormat.extractCollectionNumber(filename);
        String deltaType = FileFormat.extractDeltaType(filename);

        return ParseContext.builder()
            .fileId(fileId)
            .filename(filename)
            .fileType(type)
            .fileFormat(format)
            .fileData(fileData)
            .fileSize(fileData.length)
            .version(version)
            .collectionNumber(collection)
            .deltaType(deltaType)
            .isDelta(format.isDelta())
            .performValidation(true)
            .verifyTrustChain(true)
            .saveToLdap(true)
            .saveToDatabase(true)
            .continueOnError(false)
            .notifyProgress(true)
            .build();
    }

    /**
     * 요약 정보 반환
     */
    public String getSummary() {
        return String.format(
            "ParseContext[fileId=%s, filename=%s, type=%s, format=%s, version=%s, size=%d bytes, isDelta=%s]",
            fileId, filename, fileType, fileFormat, version, fileSize, isDelta
        );
    }
}
