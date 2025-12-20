package com.smartcoreinc.localpkd.fileupload.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * FileFormat - 파일 포맷 Value Object
 *
 * <p>ICAO PKD 파일의 형식을 나타내는 도메인 객체입니다.
 * LDIF 파일(CSCA, eMRTD)과 Master List 파일을 구분합니다.</p>
 *
 * <h3>지원 포맷</h3>
 * <ul>
 *   <li>CSCA_COMPLETE_LDIF - CSCA Complete LDIF (Collection 001)</li>
 *   <li>CSCA_DELTA_LDIF - CSCA Delta LDIF (Collection 001)</li>
 *   <li>EMRTD_COMPLETE_LDIF - eMRTD Complete LDIF (Collection 002)</li>
 *   <li>EMRTD_DELTA_LDIF - eMRTD Delta LDIF (Collection 002)</li>
 *   <li>ML_SIGNED_CMS - Master List Signed CMS (Collection 002)</li>
 *   <li>ML_UNSIGNED - Master List Unsigned (Collection 002)</li>
 * </ul>
 *
 * <h3>파일명 패턴</h3>
 * <ul>
 *   <li>CSCA Complete: icaopkd-001-complete-{version}.ldif</li>
 *   <li>CSCA Delta: icaopkd-001-delta-{version}.ldif</li>
 *   <li>eMRTD Complete: icaopkd-002-complete-{version}.ldif</li>
 *   <li>eMRTD Delta: icaopkd-002-delta-{version}.ldif</li>
 *   <li>Master List: ICAO_ml_{version}.ml 또는 masterlist-{version}.ml</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // 파일명에서 자동 감지
 * FileName fileName = FileName.of("icaopkd-002-complete-009410.ldif");
 * FileFormat format = FileFormat.detectFromFileName(fileName);
 *
 * // 포맷 정보 조회
 * boolean isLdif = format.isLdif();              // true
 * String storagePath = format.getStoragePath();  // "ldif/emrtd-complete"
 * String extension = format.getFileExtension();  // ".ldif"
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA requires no-args constructor
public class FileFormat {

    /**
     * 파일 포맷 타입
     */
    public enum Type {
        CSCA_COMPLETE_LDIF("CSCA Complete LDIF", "ldif/csca-complete", ".ldif", "001"),
        CSCA_DELTA_LDIF("CSCA Delta LDIF", "ldif/csca-delta", ".ldif", "001"),
        EMRTD_COMPLETE_LDIF("eMRTD Complete LDIF", "ldif/emrtd-complete", ".ldif", "002"),
        EMRTD_DELTA_LDIF("eMRTD Delta LDIF", "ldif/emrtd-delta", ".ldif", "002"),
        ML_SIGNED_CMS("Master List (Signed CMS)", "ml/signed-cms", ".ml", "002"),
        ML_UNSIGNED("Master List (Unsigned)", "ml/unsigned", ".ml", "002");

        private final String displayName;
        private final String storagePath;
        private final String extension;
        private final String defaultCollection;

        Type(String displayName, String storagePath, String extension, String defaultCollection) {
            this.displayName = displayName;
            this.storagePath = storagePath;
            this.extension = extension;
            this.defaultCollection = defaultCollection;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getStoragePath() {
            return storagePath;
        }

        public String getExtension() {
            return extension;
        }

        public String getDefaultCollection() {
            return defaultCollection;
        }
    }

    /**
     * 파일 포맷 타입 (non-final for JPA compatibility)
     */
    @Enumerated(EnumType.STRING)
    private Type type;

    /**
     * Private 생성자 - 정적 팩토리 메서드를 통해서만 생성
     */
    private FileFormat(Type type) {
        if (type == null) {
            throw new DomainException(
                    "INVALID_FILE_FORMAT",
                    "File format type cannot be null"
            );
        }
        this.type = type;
    }

    /**
     * FileFormat 생성 (정적 팩토리 메서드)
     *
     * @param type 파일 포맷 타입
     * @return FileFormat 인스턴스
     */
    public static FileFormat of(Type type) {
        return new FileFormat(type);
    }

    /**
     * 파일명으로부터 FileFormat 자동 감지
     *
     * <p>파일명 패턴을 분석하여 적절한 FileFormat을 반환합니다.</p>
     *
     * <h4>감지 규칙</h4>
     * <ul>
     *   <li>Collection 001 + complete → CSCA_COMPLETE_LDIF</li>
     *   <li>Collection 001 + delta → CSCA_DELTA_LDIF</li>
     *   <li>Collection 002 + complete → EMRTD_COMPLETE_LDIF</li>
     *   <li>Collection 002 + delta → EMRTD_DELTA_LDIF</li>
     *   <li>.ml 확장자 → ML_SIGNED_CMS (기본값)</li>
     * </ul>
     *
     * @param fileName 파일명 Value Object
     * @return 감지된 FileFormat
     * @throws DomainException 파일명이 null이거나 형식을 감지할 수 없는 경우
     */
    public static FileFormat detectFromFileName(FileName fileName) {
        if (fileName == null) {
            throw new DomainException(
                    "INVALID_FILE_NAME",
                    "FileName cannot be null for format detection"
            );
        }

        String name = fileName.getValue().toLowerCase();

        // LDIF 파일 감지
        if (name.endsWith(".ldif")) {
            // Collection 001 (eMRTD) - CORRECTED: 001 is eMRTD, not CSCA
            if (name.contains("001")) {
                if (name.contains("complete")) {
                    return new FileFormat(Type.EMRTD_COMPLETE_LDIF);
                } else if (name.contains("delta")) {
                    return new FileFormat(Type.EMRTD_DELTA_LDIF);
                }
            }

            // Collection 002 (CSCA) - CORRECTED: 002 is CSCA, not eMRTD
            if (name.contains("002")) {
                if (name.contains("complete")) {
                    return new FileFormat(Type.CSCA_COMPLETE_LDIF);
                } else if (name.contains("delta")) {
                    return new FileFormat(Type.CSCA_DELTA_LDIF);
                }
            }

            // 기본값: eMRTD Complete (Collection 001로 간주)
            return new FileFormat(Type.EMRTD_COMPLETE_LDIF);
        }

        // Master List 파일 감지
        if (name.endsWith(".ml")) {
            // 향후 unsigned 감지 로직 추가 가능
            return new FileFormat(Type.ML_SIGNED_CMS);
        }

        // 알 수 없는 형식
        throw new DomainException(
                "UNKNOWN_FILE_FORMAT",
                String.format("Cannot detect file format from filename: %s", fileName.getValue())
        );
    }

    /**
     * 파일 확장자로부터 FileFormat 생성 (간단한 케이스)
     *
     * @param extension 파일 확장자 (.ldif 또는 .ml)
     * @return FileFormat 인스턴스
     * @throws DomainException 지원하지 않는 확장자인 경우
     */
    public static FileFormat fromExtension(String extension) {
        if (extension == null || extension.trim().isEmpty()) {
            throw new DomainException(
                    "INVALID_EXTENSION",
                    "File extension cannot be null or empty"
            );
        }

        String ext = extension.toLowerCase();
        if (!ext.startsWith(".")) {
            ext = "." + ext;
        }

        return switch (ext) {
            case ".ldif" -> new FileFormat(Type.CSCA_COMPLETE_LDIF);  // 기본값
            case ".ml" -> new FileFormat(Type.ML_SIGNED_CMS);
            default -> throw new DomainException(
                    "UNSUPPORTED_EXTENSION",
                    String.format("Unsupported file extension: %s", extension)
            );
        };
    }

    // ===== 비즈니스 메서드 =====

    /**
     * LDIF 파일 여부 확인
     *
     * @return LDIF 파일이면 true
     */
    public boolean isLdif() {
        return type == Type.CSCA_COMPLETE_LDIF ||
                type == Type.CSCA_DELTA_LDIF ||
                type == Type.EMRTD_COMPLETE_LDIF ||
                type == Type.EMRTD_DELTA_LDIF;
    }

    /**
     * Master List 파일 여부 확인
     *
     * @return Master List 파일이면 true
     */
    public boolean isMasterList() {
        return type == Type.ML_SIGNED_CMS || type == Type.ML_UNSIGNED;
    }

    /**
     * Complete 파일 여부 확인 (Delta 아닌 경우)
     *
     * @return Complete 파일이면 true
     */
    public boolean isComplete() {
        return type == Type.CSCA_COMPLETE_LDIF ||
                type == Type.EMRTD_COMPLETE_LDIF ||
                isMasterList();  // ML은 항상 complete로 간주
    }

    /**
     * Delta 파일 여부 확인
     *
     * @return Delta 파일이면 true
     */
    public boolean isDelta() {
        return type == Type.CSCA_DELTA_LDIF || type == Type.EMRTD_DELTA_LDIF;
    }

    /**
     * CSCA Collection 여부 확인
     *
     * @return CSCA Collection (001)이면 true
     */
    public boolean isCsca() {
        return type == Type.CSCA_COMPLETE_LDIF || type == Type.CSCA_DELTA_LDIF;
    }

    /**
     * eMRTD Collection 여부 확인
     *
     * @return eMRTD Collection (002)이면 true
     */
    public boolean isEmrtd() {
        return type == Type.EMRTD_COMPLETE_LDIF || type == Type.EMRTD_DELTA_LDIF;
    }

    /**
     * 파일 저장 경로 반환
     *
     * @return 상대 경로 (예: "ldif/emrtd-complete")
     */
    public String getStoragePath() {
        return type.getStoragePath();
    }

    /**
     * 파일 확장자 반환
     *
     * @return 확장자 (예: ".ldif", ".ml")
     */
    public String getFileExtension() {
        return type.getExtension();
    }

    /**
     * 표시용 이름 반환
     *
     * @return 표시 이름 (예: "eMRTD Complete LDIF")
     */
    public String getDisplayName() {
        return type.getDisplayName();
    }

    /**
     * 기본 Collection 번호 반환
     *
     * @return Collection 번호 (예: "001", "002")
     */
    public String getDefaultCollectionNumber() {
        return type.getDefaultCollection();
    }

    /**
     * 문자열 표현
     *
     * @return "FileFormat[type=EMRTD_COMPLETE_LDIF]"
     */
    @Override
    public String toString() {
        return String.format("FileFormat[type=%s]", type.name());
    }

    // ===== JPA Persistence 지원 =====

    /**
     * JPA Persistence용 문자열 변환
     *
     * @return Type Enum 이름
     */
    public String toStorageValue() {
        return type.name();
    }

    /**
     * JPA Persistence용 문자열로부터 복원
     *
     * @param value Type Enum 이름
     * @return FileFormat 인스턴스
     */
    public static FileFormat fromStorageValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new DomainException(
                    "INVALID_STORAGE_VALUE",
                    "Storage value cannot be null or empty"
            );
        }

        try {
            Type type = Type.valueOf(value);
            return new FileFormat(type);
        } catch (IllegalArgumentException e) {
            throw new DomainException(
                    "INVALID_STORAGE_VALUE",
                    String.format("Unknown file format type: %s", value)
            );
        }
    }
}
