package com.smartcoreinc.localpkd.fileupload.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Checksum - 체크섬 Value Object
 *
 * <p>파일 무결성 검증을 위한 SHA-1 체크섬을 나타내는 도메인 객체입니다.
 * ICAO PKD 표준에서는 SHA-1 해시를 체크섬으로 사용합니다.</p>
 *
 * <h3>형식</h3>
 * <ul>
 *   <li>SHA-1 해시: 40자 16진수 문자열</li>
 *   <li>대소문자 구분 없음 (내부적으로 소문자로 저장)</li>
 * </ul>
 *
 * <h3>검증 규칙</h3>
 * <ul>
 *   <li>정확히 40자의 16진수 문자열</li>
 *   <li>0-9, a-f, A-F 문자만 허용</li>
 *   <li>null이나 빈 문자열 불가</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // 체크섬 생성
 * Checksum checksum = Checksum.of("a1b2c3d4e5f67890123456789abcdef012345678");
 *
 * // 체크섬 비교
 * String calculatedHash = "a1b2c3d4e5f67890123456789abcdef012345678";
 * boolean matches = checksum.matches(calculatedHash);  // true
 *
 * // 짧은 표현
 * String shortHash = checksum.getShortValue();  // "a1b2c3d4..." (처음 8자)
 * }</pre>
 *
 * <h3>참고</h3>
 * <p>파일 업로드 시 FileHash(SHA-256)와 Checksum(SHA-1)은 별도로 관리됩니다:</p>
 * <ul>
 *   <li>FileHash (SHA-256): 중복 파일 검사용 (64자)</li>
 *   <li>Checksum (SHA-1): ICAO PKD 표준 검증용 (40자)</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Checksum {

    /**
     * SHA-1 체크섬 값 (40자 16진수, 소문자)
     */
    private String value;

    /**
     * Private 생성자 - 정적 팩토리 메서드를 통해서만 생성
     *
     * @param value SHA-1 체크섬 문자열
     */
    private Checksum(String value) {
        validate(value);
        this.value = value.toLowerCase();
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
                    "INVALID_CHECKSUM",
                    "Checksum cannot be null or empty"
            );
        }

        String trimmed = value.trim();

        if (!trimmed.matches("^[a-fA-F0-9]{40}$")) {
            throw new DomainException(
                    "INVALID_CHECKSUM",
                    String.format("Checksum must be a valid SHA-1 hash (40 hexadecimal characters), but got: %s (length: %d)",
                            trimmed.length() <= 20 ? trimmed : trimmed.substring(0, 20) + "...",
                            trimmed.length())
            );
        }
    }

    /**
     * Checksum 생성 (정적 팩토리 메서드)
     *
     * @param value SHA-1 체크섬 문자열
     * @return Checksum 인스턴스
     * @throws DomainException 검증 실패 시
     */
    public static Checksum of(String value) {
        return new Checksum(value);
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 체크섬 일치 여부 확인
     *
     * <p>대소문자를 구분하지 않고 비교합니다.</p>
     *
     * @param calculated 계산된 체크섬 값
     * @return 일치하면 true
     */
    public boolean matches(String calculated) {
        if (calculated == null || calculated.trim().isEmpty()) {
            return false;
        }

        return this.value.equalsIgnoreCase(calculated.trim());
    }

    /**
     * 체크섬 불일치 여부 확인
     *
     * @param calculated 계산된 체크섬 값
     * @return 불일치하면 true
     */
    public boolean doesNotMatch(String calculated) {
        return !matches(calculated);
    }

    /**
     * 짧은 표현 (처음 8자)
     *
     * <p>로그나 UI에서 축약 표시할 때 사용합니다.</p>
     *
     * @return 체크섬의 처음 8자 + "..."
     */
    public String getShortValue() {
        return value.substring(0, 8) + "...";
    }

    /**
     * 문자열 표현
     *
     * @return "Checksum[value=a1b2c3d4...]"
     */
    @Override
    public String toString() {
        return String.format("Checksum[value=%s]", getShortValue());
    }

    // ===== JPA Persistence 지원 =====

    /**
     * JPA Persistence용 문자열 변환
     *
     * @return SHA-1 체크섬 문자열 (소문자)
     */
    public String toStorageValue() {
        return value;
    }

    /**
     * JPA Persistence용 문자열로부터 복원
     *
     * @param value SHA-1 체크섬 문자열
     * @return Checksum 인스턴스
     */
    public static Checksum fromStorageValue(String value) {
        return new Checksum(value);
    }

    // ===== 유틸리티 메서드 =====

    /**
     * SHA-1 해시 형식 검증 (정적 메서드)
     *
     * <p>Checksum 인스턴스를 생성하지 않고 형식만 검증할 때 사용합니다.</p>
     *
     * @param value 검증할 문자열
     * @return 유효한 SHA-1 형식이면 true
     */
    public static boolean isValidFormat(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        return value.trim().matches("^[a-fA-F0-9]{40}$");
    }

    /**
     * 체크섬 정규화 (소문자 변환, trim)
     *
     * <p>입력 값을 정규화하여 반환합니다. 검증은 수행하지 않습니다.</p>
     *
     * @param value 정규화할 문자열
     * @return 정규화된 문자열 (소문자, trim)
     */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }

        return value.trim().toLowerCase();
    }
}
