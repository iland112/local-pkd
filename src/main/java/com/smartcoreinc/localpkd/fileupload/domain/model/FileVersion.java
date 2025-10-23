package com.smartcoreinc.localpkd.fileupload.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FileVersion - 파일 버전 Value Object
 *
 * <p>ICAO PKD 파일의 버전을 나타내는 도메인 객체입니다.
 * LDIF 파일은 숫자 버전(009410), Master List는 날짜 형식(July2025)을 사용합니다.</p>
 *
 * <h3>버전 형식</h3>
 * <ul>
 *   <li>LDIF: 숫자 버전 (예: 009410, 009411)</li>
 *   <li>Master List: 날짜 형식 (예: July2025, August2025)</li>
 * </ul>
 *
 * <h3>검증 규칙</h3>
 * <ul>
 *   <li>null이나 빈 문자열 불가</li>
 *   <li>최대 50자</li>
 *   <li>공백 trim 처리</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // 직접 생성
 * FileVersion version = FileVersion.of("009410");
 *
 * // 파일명에서 추출
 * FileName fileName = FileName.of("icaopkd-002-complete-009410.ldif");
 * FileFormat format = FileFormat.detectFromFileName(fileName);
 * FileVersion version = FileVersion.extractFromFileName(fileName, format);
 *
 * // 버전 비교
 * FileVersion v1 = FileVersion.of("009410");
 * FileVersion v2 = FileVersion.of("009411");
 * boolean isNewer = v2.isNewerThan(v1);      // true
 * int comparison = v2.compareTo(v1);          // 1 (positive)
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileVersion implements Comparable<FileVersion> {

    /**
     * 버전 값
     */
    private String value;

    /**
     * LDIF 버전 패턴: -(숫자).ldif$
     * 예: icaopkd-002-complete-009410.ldif → 009410
     */
    private static final Pattern LDIF_VERSION_PATTERN = Pattern.compile("-(\\d+)\\.ldif$", Pattern.CASE_INSENSITIVE);

    /**
     * Master List 버전 패턴 1: masterlist-{MonthYear}.ml
     * 예: masterlist-July2025.ml → July2025
     */
    private static final Pattern ML_VERSION_PATTERN_1 = Pattern.compile("masterlist-([A-Za-z]+\\d{4})\\.ml", Pattern.CASE_INSENSITIVE);

    /**
     * Master List 버전 패턴 2: -(숫자).ml$
     * 예: icaopkd-002-009410.ml → 009410
     */
    private static final Pattern ML_VERSION_PATTERN_2 = Pattern.compile("-(\\d+)\\.ml$", Pattern.CASE_INSENSITIVE);

    /**
     * Private 생성자 - 정적 팩토리 메서드를 통해서만 생성
     *
     * @param value 버전 문자열
     */
    private FileVersion(String value) {
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
                    "INVALID_FILE_VERSION",
                    "File version cannot be null or empty"
            );
        }

        if (value.trim().length() > 50) {
            throw new DomainException(
                    "INVALID_FILE_VERSION",
                    String.format("File version is too long (max 50 characters), but got: %d", value.trim().length())
            );
        }
    }

    /**
     * FileVersion 생성 (정적 팩토리 메서드)
     *
     * @param value 버전 문자열
     * @return FileVersion 인스턴스
     * @throws DomainException 검증 실패 시
     */
    public static FileVersion of(String value) {
        return new FileVersion(value);
    }

    /**
     * 파일명으로부터 버전 추출
     *
     * <p>파일명과 파일 포맷을 분석하여 버전을 추출합니다.</p>
     *
     * <h4>추출 규칙</h4>
     * <ul>
     *   <li>LDIF: -(\\d+)\\.ldif$ 패턴 → 숫자 버전</li>
     *   <li>ML (패턴 1): masterlist-([A-Za-z]+\\d{4})\\.ml → 날짜 버전</li>
     *   <li>ML (패턴 2): -(\\d+)\\.ml$ → 숫자 버전</li>
     *   <li>추출 실패: 파일명의 base name (확장자 제외)</li>
     * </ul>
     *
     * @param fileName 파일명 Value Object
     * @param fileFormat 파일 포맷
     * @return 추출된 FileVersion
     * @throws DomainException 파일명이나 포맷이 null인 경우
     */
    public static FileVersion extractFromFileName(FileName fileName, FileFormat fileFormat) {
        if (fileName == null) {
            throw new DomainException(
                    "INVALID_FILE_NAME",
                    "FileName cannot be null for version extraction"
            );
        }

        if (fileFormat == null) {
            throw new DomainException(
                    "INVALID_FILE_FORMAT",
                    "FileFormat cannot be null for version extraction"
            );
        }

        String name = fileName.getValue();

        // LDIF 파일 버전 추출
        if (fileFormat.isLdif()) {
            Matcher matcher = LDIF_VERSION_PATTERN.matcher(name);
            if (matcher.find()) {
                return new FileVersion(matcher.group(1));
            }
        }

        // Master List 파일 버전 추출
        if (fileFormat.isMasterList()) {
            // 패턴 1: masterlist-July2025.ml
            Matcher matcher1 = ML_VERSION_PATTERN_1.matcher(name);
            if (matcher1.find()) {
                return new FileVersion(matcher1.group(1));
            }

            // 패턴 2: -(숫자).ml$
            Matcher matcher2 = ML_VERSION_PATTERN_2.matcher(name);
            if (matcher2.find()) {
                return new FileVersion(matcher2.group(1));
            }
        }

        // 추출 실패: 파일명의 base name을 버전으로 사용
        String baseName = fileName.getBaseName();
        return new FileVersion(baseName);
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 숫자 버전 여부 확인
     *
     * @return 순수 숫자 버전이면 true
     */
    public boolean isNumeric() {
        return value.matches("^\\d+$");
    }

    /**
     * 버전 비교
     *
     * <p>숫자 버전은 정수 비교, 문자열 버전은 사전식 비교를 수행합니다.</p>
     *
     * @param other 비교 대상 버전
     * @return 양수: this > other, 0: 같음, 음수: this < other
     */
    @Override
    public int compareTo(FileVersion other) {
        if (other == null) {
            return 1;
        }

        // 둘 다 숫자 버전인 경우 정수 비교
        if (this.isNumeric() && other.isNumeric()) {
            try {
                long thisVersion = Long.parseLong(this.value);
                long otherVersion = Long.parseLong(other.value);
                return Long.compare(thisVersion, otherVersion);
            } catch (NumberFormatException e) {
                // Fallback to string comparison
            }
        }

        // 문자열 비교 (사전식 순서)
        return this.value.compareTo(other.value);
    }

    /**
     * 더 최신 버전인지 확인
     *
     * @param other 비교 대상 버전
     * @return this가 other보다 최신이면 true
     */
    public boolean isNewerThan(FileVersion other) {
        return this.compareTo(other) > 0;
    }

    /**
     * 더 오래된 버전인지 확인
     *
     * @param other 비교 대상 버전
     * @return this가 other보다 오래되었으면 true
     */
    public boolean isOlderThan(FileVersion other) {
        return this.compareTo(other) < 0;
    }

    /**
     * 동일 버전인지 확인
     *
     * @param other 비교 대상 버전
     * @return this와 other가 같으면 true
     */
    public boolean isSameAs(FileVersion other) {
        return this.compareTo(other) == 0;
    }

    /**
     * 문자열 표현
     *
     * @return "FileVersion[value=009410]"
     */
    @Override
    public String toString() {
        return String.format("FileVersion[value=%s]", value);
    }

    // ===== JPA Persistence 지원 =====

    /**
     * JPA Persistence용 문자열 변환
     *
     * @return 버전 문자열
     */
    public String toStorageValue() {
        return value;
    }

    /**
     * JPA Persistence용 문자열로부터 복원
     *
     * @param value 버전 문자열
     * @return FileVersion 인스턴스
     */
    public static FileVersion fromStorageValue(String value) {
        return new FileVersion(value);
    }
}
