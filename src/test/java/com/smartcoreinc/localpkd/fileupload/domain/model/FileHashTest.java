package com.smartcoreinc.localpkd.fileupload.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * FileHash Value Object 테스트
 *
 * @author SmartCore Inc.
 * @since 2025-10-18
 */
@DisplayName("FileHash Value Object 테스트")
class FileHashTest {

    private static final String VALID_HASH = "a1b2c3d4e5f67890123456789abcdef01234567890abcdef0123456789abcdef";

    @Test
    @DisplayName("정상적인 SHA-256 해시로 FileHash 생성")
    void createFileHash_WithValidHash_Success() {
        // when
        FileHash fileHash = FileHash.of(VALID_HASH);

        // then
        assertThat(fileHash.getValue()).isEqualTo(VALID_HASH);
    }

    @Test
    @DisplayName("대문자 해시 입력 시 소문자로 변환")
    void createFileHash_WithUpperCase_ConvertsToLowerCase() {
        // given
        String upperCaseHash = VALID_HASH.toUpperCase();

        // when
        FileHash fileHash = FileHash.of(upperCaseHash);

        // then
        assertThat(fileHash.getValue()).isEqualTo(VALID_HASH.toLowerCase());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("빈 문자열이나 공백으로 FileHash 생성 시 예외 발생")
    void createFileHash_WithEmptyOrBlank_ThrowsException(String invalidHash) {
        // when & then
        assertThatThrownBy(() -> FileHash.of(invalidHash))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("null로 FileHash 생성 시 예외 발생")
    void createFileHash_WithNull_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> FileHash.of(null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("64자가 아닌 해시로 생성 시 예외 발생")
    void createFileHash_WithInvalidLength_ThrowsException() {
        // given
        String shortHash = "a1b2c3d4";  // 8자

        // when & then
        assertThatThrownBy(() -> FileHash.of(shortHash))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("valid SHA-256 hash");
    }

    @Test
    @DisplayName("16진수가 아닌 문자가 포함된 해시로 생성 시 예외 발생")
    void createFileHash_WithNonHexCharacters_ThrowsException() {
        // given
        String invalidHash = "g1h2i3j4k5l67890123456789abcdef01234567890abcdef0123456789abcdef";

        // when & then
        assertThatThrownBy(() -> FileHash.of(invalidHash))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("valid SHA-256 hash");
    }

    @Test
    @DisplayName("짧은 해시 추출 - 앞 8자리")
    void getShortHash_ReturnsFirst8Characters() {
        // given
        FileHash fileHash = FileHash.of(VALID_HASH);

        // when
        String shortHash = fileHash.getShortHash();

        // then
        assertThat(shortHash).isEqualTo("a1b2c3d4");
    }

    @Test
    @DisplayName("Value Object 동등성 - 같은 값은 동일")
    void equals_SameValue_ReturnsTrue() {
        // given
        FileHash fileHash1 = FileHash.of(VALID_HASH);
        FileHash fileHash2 = FileHash.of(VALID_HASH);

        // when & then
        assertThat(fileHash1).isEqualTo(fileHash2);
        assertThat(fileHash1.hashCode()).isEqualTo(fileHash2.hashCode());
    }

    @Test
    @DisplayName("Value Object 동등성 - 대소문자 다른 해시도 동일")
    void equals_CaseInsensitive_ReturnsTrue() {
        // given
        FileHash fileHash1 = FileHash.of(VALID_HASH.toLowerCase());
        FileHash fileHash2 = FileHash.of(VALID_HASH.toUpperCase());

        // when & then
        assertThat(fileHash1).isEqualTo(fileHash2);
    }

    @Test
    @DisplayName("Value Object 동등성 - 다른 값은 다름")
    void equals_DifferentValue_ReturnsFalse() {
        // given
        FileHash fileHash1 = FileHash.of(VALID_HASH);
        String differentHash = "b2c3d4e5f67890123456789abcdef01234567890abcdef01234567890abcdef0";
        FileHash fileHash2 = FileHash.of(differentHash);

        // when & then
        assertThat(fileHash1).isNotEqualTo(fileHash2);
    }
}
