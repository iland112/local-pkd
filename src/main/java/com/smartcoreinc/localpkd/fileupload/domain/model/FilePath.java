package com.smartcoreinc.localpkd.fileupload.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * FilePath - 파일 경로 Value Object
 *
 * <p>파일 시스템상의 경로를 나타내는 도메인 객체입니다.
 * 파일 저장 위치, 존재 여부 확인, 파일 크기 조회 등의 기능을 제공합니다.</p>
 *
 * <h3>경로 형식</h3>
 * <ul>
 *   <li>상대 경로: "./data/uploads/ldif/csca-complete/file.ldif"</li>
 *   <li>절대 경로: "/home/user/data/uploads/ldif/csca-complete/file.ldif"</li>
 * </ul>
 *
 * <h3>검증 규칙</h3>
 * <ul>
 *   <li>null이나 빈 문자열 불가</li>
 *   <li>최대 500자</li>
 *   <li>공백 trim 처리</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // 파일 경로 생성
 * FilePath filePath = FilePath.of("./data/uploads/ldif/csca-complete/file.ldif");
 *
 * // 파일 존재 여부 확인
 * boolean exists = filePath.exists();
 *
 * // 파일 크기 조회
 * long size = filePath.getFileSize();  // throws IOException
 *
 * // 경로 정보
 * String absolutePath = filePath.getAbsolutePath();
 * String parentPath = filePath.getParentPath();
 * String fileName = filePath.getFileName();
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FilePath {

    /**
     * 파일 경로 문자열
     */
    private String value;

    /**
     * Private 생성자 - 정적 팩토리 메서드를 통해서만 생성
     *
     * @param value 파일 경로
     */
    private FilePath(String value) {
        validate(value);
        this.value = value.trim();
    }

    /**
     * 검증 로직
     *
     * @param value 검증할 값
     * @throws DomainException 검증 실패 시
     */
    private void validate(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new DomainException(
                    "INVALID_FILE_PATH",
                    "File path cannot be null or empty"
            );
        }

        if (value.trim().length() > 500) {
            throw new DomainException(
                    "INVALID_FILE_PATH",
                    String.format("File path is too long (max 500 characters), but got: %d", value.trim().length())
            );
        }
    }

    /**
     * FilePath 생성 (정적 팩토리 메서드)
     *
     * @param value 파일 경로 문자열
     * @return FilePath 인스턴스
     * @throws DomainException 검증 실패 시
     */
    public static FilePath of(String value) {
        return new FilePath(value);
    }

    /**
     * Path 객체로부터 생성
     *
     * @param path Java NIO Path 객체
     * @return FilePath 인스턴스
     * @throws DomainException path가 null인 경우
     */
    public static FilePath of(Path path) {
        if (path == null) {
            throw new DomainException(
                    "INVALID_FILE_PATH",
                    "Path cannot be null"
            );
        }

        return new FilePath(path.toString());
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 파일 존재 여부 확인
     *
     * @return 파일이 존재하면 true
     */
    public boolean exists() {
        return new File(value).exists();
    }

    /**
     * 파일 크기 조회
     *
     * @return 파일 크기 (bytes)
     * @throws IOException 파일을 읽을 수 없는 경우
     * @throws DomainException 파일이 존재하지 않는 경우
     */
    public long getFileSize() throws IOException {
        if (!exists()) {
            throw new DomainException(
                    "FILE_NOT_FOUND",
                    String.format("File does not exist: %s", value)
            );
        }

        return Files.size(Paths.get(value));
    }

    /**
     * 절대 경로 반환
     *
     * @return 절대 경로 문자열
     */
    public String getAbsolutePath() {
        return new File(value).getAbsolutePath();
    }

    /**
     * 부모 디렉토리 경로 반환
     *
     * @return 부모 디렉토리 경로 (없으면 null)
     */
    public String getParentPath() {
        File file = new File(value);
        String parent = file.getParent();
        return parent != null ? parent : null;
    }

    /**
     * 파일명 반환 (경로 제외)
     *
     * @return 파일명
     */
    public String getFileName() {
        return new File(value).getName();
    }

    /**
     * Java NIO Path 객체로 변환
     *
     * @return Path 객체
     */
    public Path toPath() {
        return Paths.get(value);
    }

    /**
     * File 객체로 변환
     *
     * @return File 객체
     */
    public File toFile() {
        return new File(value);
    }

    /**
     * 디렉토리 여부 확인
     *
     * @return 디렉토리이면 true
     */
    public boolean isDirectory() {
        return new File(value).isDirectory();
    }

    /**
     * 파일 여부 확인
     *
     * @return 일반 파일이면 true
     */
    public boolean isFile() {
        return new File(value).isFile();
    }

    /**
     * 읽기 가능 여부 확인
     *
     * @return 읽기 가능하면 true
     */
    public boolean canRead() {
        return new File(value).canRead();
    }

    /**
     * 쓰기 가능 여부 확인
     *
     * @return 쓰기 가능하면 true
     */
    public boolean canWrite() {
        return new File(value).canWrite();
    }

    /**
     * 문자열 표현
     *
     * @return "FilePath[value=./data/uploads/...]"
     */
    @Override
    public String toString() {
        return String.format("FilePath[value=%s]", value);
    }

    // ===== JPA Persistence 지원 =====

    /**
     * JPA Persistence용 문자열 변환
     *
     * @return 파일 경로 문자열
     */
    public String toStorageValue() {
        return value;
    }

    /**
     * JPA Persistence용 문자열로부터 복원
     *
     * @param value 파일 경로 문자열
     * @return FilePath 인스턴스
     */
    public static FilePath fromStorageValue(String value) {
        return new FilePath(value);
    }
}
