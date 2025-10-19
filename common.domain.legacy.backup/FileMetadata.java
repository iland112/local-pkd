package com.smartcoreinc.localpkd.common.domain;

import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * ICAO PKD 파일 메타데이터
 *
 * ICAO PKD 다운로드 페이지의 파일 정보를 담는 도메인 클래스
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @see <a href="https://pkddownloadsg.icao.int/download">ICAO PKD Download</a>
 */
@Getter
@Builder
@ToString
public class FileMetadata {

    /**
     * 파일명
     * 예: icaopkd-001-complete-009410.ldif, ICAO_ml_July2025.ml
     */
    private String filename;

    /**
     * 파일 포맷
     */
    private FileFormat fileFormat;

    /**
     * 버전 번호
     * - LDIF: 숫자 (예: 009410, 000325)
     * - ML: Month/Year (예: July2025)
     */
    private String version;

    /**
     * Collection 번호 (001, 002, 003)
     * ML 파일의 경우 002 (CSCA)
     */
    private String collectionNumber;

    /**
     * 파일 크기 (bytes)
     */
    private Long fileSizeBytes;

    /**
     * 파일 크기 (human-readable)
     * 예: "74.3 MiB", "9.7 MiB"
     */
    private String fileSizeDisplay;

    /**
     * SHA-1 Checksum
     * ICAO PKD는 SHA-1 체크섬 제공
     */
    private String sha1Checksum;

    /**
     * 공식 설명
     * ICAO PKD 다운로드 페이지의 Description
     */
    private String description;

    /**
     * 다운로드 URL
     */
    private String downloadUrl;

    /**
     * 파일 생성/업로드 일시
     */
    private LocalDateTime createdAt;

    /**
     * 로컬 다운로드 일시
     */
    private LocalDateTime downloadedAt;

    /**
     * 로컬 파일 경로
     */
    private String localFilePath;

    /**
     * Deprecated 여부
     * Collection #3 (Non-Conformant)는 더 이상 업데이트 안 됨
     */
    private Boolean deprecated;

    /**
     * 파일 타입 (Complete / Delta)
     */
    public boolean isComplete() {
        return fileFormat != null && fileFormat.isComplete();
    }

    /**
     * Delta 파일 여부
     */
    public boolean isDelta() {
        return fileFormat != null && fileFormat.isDelta();
    }

    /**
     * LDIF 파일 여부
     */
    public boolean isLdif() {
        return fileFormat != null && fileFormat.isLdif();
    }

    /**
     * CMS Signed 파일 여부 (.ml)
     */
    public boolean isSignedCms() {
        return fileFormat != null && fileFormat.isSignedCms();
    }

    /**
     * Collection 번호 기반 카테고리
     */
    public String getCollectionCategory() {
        if (collectionNumber == null) return "UNKNOWN";

        return switch (collectionNumber) {
            case "001" -> "eMRTD PKI Objects";
            case "002" -> "CSCA Master Lists";
            case "003" -> "Non-Conformant (Deprecated)";
            default -> "UNKNOWN";
        };
    }

    /**
     * 파일명으로부터 FileMetadata 생성 (기본 정보만)
     *
     * @param filename 파일명
     * @return FileMetadata
     */
    public static FileMetadata fromFilename(String filename) {
        try {
            FileFormat format = FileFormat.detectFromFilename(filename);
            String version = FileFormat.extractVersion(filename);
            String collectionNumber = FileFormat.extractCollectionNumber(filename);

            return FileMetadata.builder()
                .filename(filename)
                .fileFormat(format)
                .version(version)
                .collectionNumber(collectionNumber)
                .deprecated(format.isDeprecated())
                .build();
        } catch (IllegalArgumentException e) {
            // 파일명 인식 실패 시 null 반환
            return null;
        }
    }

    /**
     * 파일 크기를 human-readable 형식으로 변환
     *
     * @param bytes 파일 크기 (bytes)
     * @return human-readable 문자열 (예: "74.3 MiB")
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String[] units = {"B", "KiB", "MiB", "GiB"};
        double value = bytes / Math.pow(1024, exp);

        return String.format("%.1f %s", value, units[exp]);
    }
}
