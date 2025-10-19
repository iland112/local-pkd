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
 * File Size - 파일 크기 Value Object
 *
 * <p>파일 크기를 나타내는 불변 값 객체입니다.
 * 바이트 단위로 저장하며, 사용자 친화적인 표현(KB, MB)을 제공합니다.</p>
 *
 * <h3>비즈니스 규칙</h3>
 * <ul>
 *   <li>파일 크기는 0보다 커야 함 (빈 파일 불허)</li>
 *   <li>최대 파일 크기: 100 MB (104,857,600 bytes)</li>
 *   <li>바이트 단위로 정확하게 저장</li>
 * </ul>
 *
 * <h3>사용 예시 - 생성</h3>
 * <pre>{@code
 * // 1. 바이트로 생성
 * FileSize fileSize = FileSize.ofBytes(1024);  // 1 KB
 *
 * // 2. 킬로바이트로 생성
 * FileSize fileSize = FileSize.ofKiloBytes(10);  // 10 KB = 10240 bytes
 *
 * // 3. 메가바이트로 생성
 * FileSize fileSize = FileSize.ofMegaBytes(5);  // 5 MB
 *
 * // 4. 잘못된 크기 (DomainException 발생)
 * try {
 *     FileSize invalid = FileSize.ofBytes(0);  // ❌ 0 bytes
 * } catch (DomainException e) {
 *     System.out.println(e.getErrorCode());  // INVALID_FILE_SIZE
 * }
 *
 * try {
 *     FileSize tooBig = FileSize.ofMegaBytes(150);  // ❌ 150 MB (limit: 100 MB)
 * } catch (DomainException e) {
 *     System.out.println(e.getErrorCode());  // FILE_SIZE_LIMIT_EXCEEDED
 * }
 * }</pre>
 *
 * <h3>사용 예시 - 엔티티에서 사용</h3>
 * <pre>{@code
 * @Entity
 * public class UploadedFile extends AggregateRoot<UploadId> {
 *
 *     @Embedded
 *     @AttributeOverride(name = "bytes", column = @Column(name = "file_size_bytes"))
 *     private FileSize fileSize;
 *
 *     public UploadedFile(UploadId id, FileSize fileSize) {
 *         this.id = id;
 *         this.fileSize = fileSize;  // 이미 검증됨
 *     }
 *
 *     public String getFileSizeDisplay() {
 *         return fileSize.toHumanReadable();  // "10.5 MB"
 *     }
 * }
 * }</pre>
 *
 * <h3>사용 예시 - 크기 비교</h3>
 * <pre>{@code
 * FileSize size1 = FileSize.ofMegaBytes(10);
 * FileSize size2 = FileSize.ofKiloBytes(5000);  // 5 MB
 *
 * boolean isLarger = size1.isLargerThan(size2);  // true
 * boolean isSmaller = size1.isSmallerThan(size2);  // false
 *
 * // 최대 크기 검사
 * FileSize maxSize = FileSize.ofMegaBytes(100);
 * boolean exceedsLimit = size1.exceedsLimit(maxSize);  // false
 * }</pre>
 *
 * <h3>사용 예시 - 사용자 친화적 표현</h3>
 * <pre>{@code
 * FileSize size = FileSize.ofBytes(1536);
 * System.out.println(size.toHumanReadable());  // "1.5 KB"
 *
 * FileSize large = FileSize.ofBytes(10485760);
 * System.out.println(large.toHumanReadable());  // "10.0 MB"
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
public class FileSize implements ValueObject {

    private static final long MAX_FILE_SIZE_BYTES = 104_857_600L;  // 100 MB
    private static final long KB = 1024L;
    private static final long MB = KB * 1024L;

    /**
     * 파일 크기 (바이트)
     */
    @Column(name = "file_size_bytes", nullable = false)
    private long bytes;

    /**
     * 파일 크기 생성자 (private)
     *
     * @param bytes 파일 크기 (바이트)
     */
    private FileSize(long bytes) {
        validate(bytes);
        this.bytes = bytes;
    }

    /**
     * 바이트로 파일 크기 생성
     *
     * @param bytes 파일 크기 (바이트)
     * @return FileSize 객체
     * @throws DomainException 파일 크기가 유효하지 않은 경우
     */
    public static FileSize ofBytes(long bytes) {
        return new FileSize(bytes);
    }

    /**
     * 킬로바이트로 파일 크기 생성
     *
     * @param kiloBytes 파일 크기 (KB)
     * @return FileSize 객체
     * @throws DomainException 파일 크기가 유효하지 않은 경우
     */
    public static FileSize ofKiloBytes(long kiloBytes) {
        return new FileSize(kiloBytes * KB);
    }

    /**
     * 메가바이트로 파일 크기 생성
     *
     * @param megaBytes 파일 크기 (MB)
     * @return FileSize 객체
     * @throws DomainException 파일 크기가 유효하지 않은 경우
     */
    public static FileSize ofMegaBytes(long megaBytes) {
        return new FileSize(megaBytes * MB);
    }

    /**
     * 파일 크기 검증
     *
     * @param bytes 파일 크기 (바이트)
     * @throws DomainException 검증 실패 시
     */
    private void validate(long bytes) {
        if (bytes <= 0) {
            throw new DomainException(
                    "INVALID_FILE_SIZE",
                    "File size must be greater than 0"
            );
        }

        if (bytes > MAX_FILE_SIZE_BYTES) {
            throw new DomainException(
                    "FILE_SIZE_LIMIT_EXCEEDED",
                    String.format("File size %d bytes exceeds limit %d bytes (100 MB)",
                            bytes, MAX_FILE_SIZE_BYTES)
            );
        }
    }

    /**
     * 사용자 친화적인 크기 표현
     *
     * <p>파일 크기를 KB, MB 단위로 표현합니다.</p>
     *
     * @return 사용자 친화적인 크기 문자열 (예: "10.5 MB", "1.5 KB")
     */
    public String toHumanReadable() {
        if (bytes >= MB) {
            return String.format("%.1f MB", (double) bytes / MB);
        } else if (bytes >= KB) {
            return String.format("%.1f KB", (double) bytes / KB);
        } else {
            return bytes + " bytes";
        }
    }

    /**
     * 다른 파일 크기보다 큰지 확인
     *
     * @param other 비교할 파일 크기
     * @return 더 큰 경우 true
     */
    public boolean isLargerThan(FileSize other) {
        return this.bytes > other.bytes;
    }

    /**
     * 다른 파일 크기보다 작은지 확인
     *
     * @param other 비교할 파일 크기
     * @return 더 작은 경우 true
     */
    public boolean isSmallerThan(FileSize other) {
        return this.bytes < other.bytes;
    }

    /**
     * 최대 크기 제한을 초과하는지 확인
     *
     * @param maxSize 최대 크기
     * @return 초과하는 경우 true
     */
    public boolean exceedsLimit(FileSize maxSize) {
        return this.bytes > maxSize.bytes;
    }

    /**
     * 바이트 값 반환
     *
     * @return 파일 크기 (바이트)
     */
    public long getBytes() {
        return bytes;
    }

    /**
     * 문자열 표현
     *
     * @return 사용자 친화적인 크기 문자열
     */
    @Override
    public String toString() {
        return toHumanReadable();
    }
}
