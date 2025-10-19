package com.smartcoreinc.localpkd.fileupload.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * File Name - 파일명 Value Object
 *
 * <p>파일명을 나타내는 불변 값 객체입니다.
 * 파일명 검증 로직을 캡슐화하고, 비즈니스 규칙을 강제합니다.</p>
 *
 * <h3>비즈니스 규칙</h3>
 * <ul>
 *   <li>파일명은 필수값 (null 또는 빈 문자열 불가)</li>
 *   <li>파일명은 1자 이상 255자 이하</li>
 *   <li>파일명에 특수문자 제한 (/, \, :, *, ?, ", <, >, | 불가)</li>
 *   <li>확장자 포함 (예: file.txt)</li>
 * </ul>
 *
 * <h3>사용 예시 - 생성</h3>
 * <pre>{@code
 * // 1. 정상적인 파일명
 * FileName fileName = FileName.of("icaopkd-002-complete-009410.ldif");
 *
 * // 2. 잘못된 파일명 (DomainException 발생)
 * try {
 *     FileName invalid = FileName.of("");  // ❌ 빈 문자열
 * } catch (DomainException e) {
 *     System.out.println(e.getErrorCode());  // INVALID_FILE_NAME
 * }
 *
 * try {
 *     FileName invalid = FileName.of("file/name.txt");  // ❌ 슬래시 포함
 * } catch (DomainException e) {
 *     System.out.println(e.getErrorCode());  // INVALID_FILE_NAME
 * }
 * }</pre>
 *
 * <h3>사용 예시 - 엔티티에서 사용</h3>
 * <pre>{@code
 * @Entity
 * public class UploadedFile extends AggregateRoot<UploadId> {
 *
 *     @Embedded
 *     @AttributeOverride(name = "value", column = @Column(name = "file_name"))
 *     private FileName fileName;
 *
 *     public UploadedFile(UploadId id, FileName fileName) {
 *         this.id = id;
 *         this.fileName = fileName;  // 이미 검증됨
 *     }
 *
 *     public String getFileName() {
 *         return fileName.value();
 *     }
 * }
 * }</pre>
 *
 * <h3>사용 예시 - 확장자 추출</h3>
 * <pre>{@code
 * FileName fileName = FileName.of("document.pdf");
 * String extension = fileName.getExtension();  // "pdf"
 *
 * boolean isLdif = fileName.hasExtension("ldif");  // false
 * boolean isPdf = fileName.hasExtension("pdf");    // true
 * }</pre>
 *
 * <h3>사용 예시 - 파일명 변경</h3>
 * <pre>{@code
 * FileName original = FileName.of("file.txt");
 * FileName renamed = original.withBaseName("newfile");
 * System.out.println(renamed.value());  // "newfile.txt"
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 * @see ValueObject
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileName implements ValueObject {

    private static final int MAX_LENGTH = 255;
    private static final String INVALID_CHARS_REGEX = "[/\\\\:*?\"<>|]";

    /**
     * 파일명 값
     */
    @Column(name = "file_name", nullable = false, length = MAX_LENGTH)
    private String value;

    /**
     * 파일명 생성자 (private)
     *
     * @param value 파일명
     */
    private FileName(String value) {
        validate(value);
        this.value = value;
    }

    /**
     * 파일명 생성 (Factory Method)
     *
     * <p>파일명 검증 후 FileName 객체를 생성합니다.</p>
     *
     * @param value 파일명
     * @return FileName 객체
     * @throws DomainException 파일명이 유효하지 않은 경우
     */
    public static FileName of(String value) {
        return new FileName(value);
    }

    /**
     * 파일명 검증
     *
     * @param value 파일명
     * @throws DomainException 검증 실패 시
     */
    private void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException(
                    "INVALID_FILE_NAME",
                    "File name cannot be null or empty"
            );
        }

        if (value.length() > MAX_LENGTH) {
            throw new DomainException(
                    "INVALID_FILE_NAME",
                    String.format("File name length cannot exceed %d characters", MAX_LENGTH)
            );
        }

        if (value.matches(".*" + INVALID_CHARS_REGEX + ".*")) {
            throw new DomainException(
                    "INVALID_FILE_NAME",
                    "File name contains invalid characters: / \\ : * ? \" < > |"
            );
        }
    }

    /**
     * 확장자 추출
     *
     * <p>파일명에서 확장자를 추출합니다. 확장자가 없으면 빈 문자열을 반환합니다.</p>
     *
     * @return 확장자 (점 제외, 소문자)
     */
    public String getExtension() {
        int lastDotIndex = value.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == value.length() - 1) {
            return "";
        }
        return value.substring(lastDotIndex + 1).toLowerCase();
    }

    /**
     * 확장자 확인
     *
     * <p>파일명이 특정 확장자를 가지는지 확인합니다.</p>
     *
     * @param extension 확장자 (점 제외)
     * @return 일치 여부
     */
    public boolean hasExtension(String extension) {
        return getExtension().equalsIgnoreCase(extension);
    }

    /**
     * 베이스명 추출 (확장자 제외)
     *
     * <p>파일명에서 확장자를 제외한 부분을 반환합니다.</p>
     *
     * @return 베이스명
     */
    public String getBaseName() {
        int lastDotIndex = value.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return value;
        }
        return value.substring(0, lastDotIndex);
    }

    /**
     * 베이스명 변경 (확장자 유지)
     *
     * <p>베이스명을 변경하고 기존 확장자를 유지한 새 FileName을 생성합니다.</p>
     *
     * @param newBaseName 새 베이스명
     * @return 새 FileName 객체
     */
    public FileName withBaseName(String newBaseName) {
        String extension = getExtension();
        String newValue = extension.isEmpty() ?
                newBaseName : newBaseName + "." + extension;
        return FileName.of(newValue);
    }

    /**
     * 문자열 표현
     *
     * @return 파일명
     */
    @Override
    public String toString() {
        return value;
    }
}
