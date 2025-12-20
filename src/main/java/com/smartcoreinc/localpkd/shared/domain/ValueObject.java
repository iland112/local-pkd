package com.smartcoreinc.localpkd.shared.domain;

/**
 * Value Object 마커 인터페이스
 *
 * <p>DDD의 Value Object 패턴을 구현할 때 사용하는 마커 인터페이스입니다.
 * Value Object는 식별자가 아닌 속성으로 동등성을 판단하는 불변 객체입니다.</p>
 *
 * <h3>Value Object 특징</h3>
 * <ul>
 *   <li><b>불변성(Immutability)</b>: 한번 생성되면 변경할 수 없음</li>
 *   <li><b>속성 기반 동등성</b>: 모든 속성 값이 같으면 같은 객체로 간주</li>
 *   <li><b>자가 검증(Self-validation)</b>: 생성 시 유효성 검증</li>
 *   <li><b>개념적 전체성</b>: 관련 속성들을 하나의 개념으로 묶음</li>
 * </ul>
 *
 * <h3>구현 권장사항</h3>
 * <ul>
 *   <li>Java 14+ Record 사용 권장 (불변성 및 equals/hashCode 자동 생성)</li>
 *   <li>생성자에서 유효성 검증</li>
 *   <li>의미 있는 메서드 제공 (비즈니스 로직)</li>
 * </ul>
 *
 * <h3>사용 예시 - Java Record</h3>
 * <pre>{@code
 * public record FileName(String value, FileExtension extension) implements ValueObject {
 *
 *     // Compact Constructor에서 유효성 검증
 *     public FileName {
 *         if (value == null || value.isBlank()) {
 *             throw new InvalidFileNameException("Filename cannot be blank");
 *         }
 *         if (extension == null) {
 *             throw new InvalidFileNameException("Extension cannot be null");
 *         }
 *     }
 *
 *     // 비즈니스 로직 메서드
 *     public String getFullName() {
 *         return value + extension.getValue();
 *     }
 *
 *     public boolean hasExtension(FileExtension ext) {
 *         return this.extension.equals(ext);
 *     }
 * }
 * }</pre>
 *
 * <h3>사용 예시 - 전통적인 클래스</h3>
 * <pre>{@code
 * public final class FileSize implements ValueObject {
 *     private final long bytes;
 *
 *     public FileSize(long bytes) {
 *         if (bytes < 0) {
 *             throw new IllegalArgumentException("File size cannot be negative");
 *         }
 *         this.bytes = bytes;
 *     }
 *
 *     public long getBytes() {
 *         return bytes;
 *     }
 *
 *     public String toDisplayString() {
 *         if (bytes < 1024) return bytes + " B";
 *         if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
 *         return (bytes / 1024 / 1024) + " MB";
 *     }
 *
 *     @Override
 *     public boolean equals(Object o) {
 *         if (this == o) return true;
 *         if (!(o instanceof FileSize fileSize)) return false;
 *         return bytes == fileSize.bytes;
 *     }
 *
 *     @Override
 *     public int hashCode() {
 *         return Long.hashCode(bytes);
 *     }
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 */
public interface ValueObject {
    // Marker interface - no methods required
    // Record는 자동으로 equals(), hashCode(), toString() 제공
}
