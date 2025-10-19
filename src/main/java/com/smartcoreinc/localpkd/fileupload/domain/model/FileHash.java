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
 * File Hash - 파일 해시 Value Object
 *
 * <p>파일의 SHA-256 해시값을 나타내는 불변 값 객체입니다.
 * 파일 무결성 검증 및 중복 파일 탐지에 사용됩니다.</p>
 *
 * <h3>비즈니스 규칙</h3>
 * <ul>
 *   <li>SHA-256 해시 형식 (64자리 16진수)</li>
 *   <li>소문자로 정규화</li>
 *   <li>null 또는 빈 문자열 불가</li>
 * </ul>
 *
 * <h3>사용 예시 - 생성</h3>
 * <pre>{@code
 * // 1. 정상적인 SHA-256 해시
 * String hashValue = "a1b2c3d4e5f67890123456789abcdef01234567890abcdef0123456789abcdef";
 * FileHash fileHash = FileHash.of(hashValue);
 *
 * // 2. 대문자 해시 (자동으로 소문자 변환)
 * FileHash fileHash = FileHash.of("A1B2C3D4...");
 * System.out.println(fileHash.value());  // "a1b2c3d4..." (소문자)
 *
 * // 3. 잘못된 해시 (DomainException 발생)
 * try {
 *     FileHash invalid = FileHash.of("invalid-hash");  // ❌ 64자가 아님
 * } catch (DomainException e) {
 *     System.out.println(e.getErrorCode());  // INVALID_FILE_HASH
 * }
 * }</pre>
 *
 * <h3>사용 예시 - 엔티티에서 사용</h3>
 * <pre>{@code
 * @Entity
 * public class UploadedFile extends AggregateRoot<UploadId> {
 *
 *     @Embedded
 *     @AttributeOverride(name = "value", column = @Column(name = "file_hash"))
 *     private FileHash fileHash;
 *
 *     public UploadedFile(UploadId id, FileHash fileHash) {
 *         this.id = id;
 *         this.fileHash = fileHash;  // 이미 검증됨
 *     }
 *
 *     public boolean isSameFileAs(UploadedFile other) {
 *         return this.fileHash.equals(other.fileHash);
 *     }
 * }
 * }</pre>
 *
 * <h3>사용 예시 - 중복 파일 검사</h3>
 * <pre>{@code
 * // Repository에서 해시로 조회
 * public interface UploadedFileRepository {
 *     Optional<UploadedFile> findByFileHash(FileHash fileHash);
 * }
 *
 * // Service에서 중복 검사
 * public boolean isDuplicateFile(FileHash fileHash) {
 *     return repository.findByFileHash(fileHash).isPresent();
 * }
 * }</pre>
 *
 * <h3>사용 예시 - 파일 해시 계산 (Infrastructure Layer)</h3>
 * <pre>{@code
 * public class FileHashCalculator {
 *     public FileHash calculate(MultipartFile file) throws IOException {
 *         MessageDigest digest = MessageDigest.getInstance("SHA-256");
 *         byte[] hash = digest.digest(file.getBytes());
 *
 *         StringBuilder hexString = new StringBuilder();
 *         for (byte b : hash) {
 *             String hex = Integer.toHexString(0xff & b);
 *             if (hex.length() == 1) hexString.append('0');
 *             hexString.append(hex);
 *         }
 *
 *         return FileHash.of(hexString.toString());
 *     }
 * }
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
public class FileHash implements ValueObject {

    private static final int SHA256_LENGTH = 64;
    private static final String HEX_PATTERN = "^[a-fA-F0-9]{64}$";

    /**
     * 파일 해시 값 (SHA-256, 64자리 16진수)
     */
    @Column(name = "file_hash", nullable = false, length = SHA256_LENGTH)
    private String value;

    /**
     * 파일 해시 생성자 (private)
     *
     * @param value SHA-256 해시 값
     */
    private FileHash(String value) {
        validate(value);
        this.value = value.toLowerCase();  // 소문자로 정규화
    }

    /**
     * 파일 해시 생성 (Factory Method)
     *
     * <p>SHA-256 해시 검증 후 FileHash 객체를 생성합니다.</p>
     *
     * @param value SHA-256 해시 문자열 (64자리 16진수)
     * @return FileHash 객체
     * @throws DomainException 해시가 유효하지 않은 경우
     */
    public static FileHash of(String value) {
        return new FileHash(value);
    }

    /**
     * 파일 해시 검증
     *
     * @param value 해시 값
     * @throws DomainException 검증 실패 시
     */
    private void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException(
                    "INVALID_FILE_HASH",
                    "File hash cannot be null or empty"
            );
        }

        if (!value.matches(HEX_PATTERN)) {
            throw new DomainException(
                    "INVALID_FILE_HASH",
                    "File hash must be a valid SHA-256 hash (64 hexadecimal characters)"
            );
        }
    }

    /**
     * 해시 값의 앞 8자리 반환 (로깅/디버깅용)
     *
     * <p>전체 해시 대신 앞 8자리만 표시할 때 사용합니다.</p>
     *
     * @return 해시 앞 8자리
     */
    public String getShortHash() {
        return value.substring(0, Math.min(8, value.length()));
    }

    /**
     * 문자열 표현
     *
     * @return 파일 해시 (소문자)
     */
    @Override
    public String toString() {
        return value;
    }
}
