package com.smartcoreinc.localpkd.fileupload.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * FileName Value Object 테스트
 *
 * @author SmartCore Inc.
 * @since 2025-10-18
 */
@DisplayName("FileName Value Object 테스트")
class FileNameTest {

    @Test
    @DisplayName("정상적인 파일명으로 FileName 생성")
    void createFileName_WithValidName_Success() {
        // given
        String validName = "icaopkd-002-complete-009410.ldif";

        // when
        FileName fileName = FileName.of(validName);

        // then
        assertThat(fileName.getValue()).isEqualTo(validName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("빈 문자열이나 공백으로 FileName 생성 시 예외 발생")
    void createFileName_WithEmptyOrBlank_ThrowsException(String invalidName) {
        // when & then
        assertThatThrownBy(() -> FileName.of(invalidName))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("null로 FileName 생성 시 예외 발생")
    void createFileName_WithNull_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> FileName.of(null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("255자를 초과하는 파일명으로 생성 시 예외 발생")
    void createFileName_ExceedingMaxLength_ThrowsException() {
        // given
        String tooLongName = "a".repeat(256);

        // when & then
        assertThatThrownBy(() -> FileName.of(tooLongName))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("cannot exceed 255 characters");
    }

    @ParameterizedTest
    @ValueSource(strings = {"file/name.txt", "file\\name.txt", "file:name.txt",
            "file*name.txt", "file?name.txt", "file\"name.txt",
            "file<name.txt", "file>name.txt", "file|name.txt"})
    @DisplayName("특수문자가 포함된 파일명으로 생성 시 예외 발생")
    void createFileName_WithInvalidCharacters_ThrowsException(String invalidName) {
        // when & then
        assertThatThrownBy(() -> FileName.of(invalidName))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    @DisplayName("파일 확장자 추출 - 정상 케이스")
    void getExtension_WithValidFileName_ReturnsExtension() {
        // given
        FileName fileName = FileName.of("document.pdf");

        // when
        String extension = fileName.getExtension();

        // then
        assertThat(extension).isEqualTo("pdf");
    }

    @Test
    @DisplayName("파일 확장자 추출 - 확장자 없는 경우")
    void getExtension_WithoutExtension_ReturnsEmptyString() {
        // given
        FileName fileName = FileName.of("document");

        // when
        String extension = fileName.getExtension();

        // then
        assertThat(extension).isEmpty();
    }

    @Test
    @DisplayName("파일 확장자 확인 - 대소문자 무시")
    void hasExtension_CaseInsensitive_ReturnsTrue() {
        // given
        FileName fileName = FileName.of("Document.PDF");

        // when & then
        assertThat(fileName.hasExtension("pdf")).isTrue();
        assertThat(fileName.hasExtension("PDF")).isTrue();
        assertThat(fileName.hasExtension("Pdf")).isTrue();
    }

    @Test
    @DisplayName("베이스명 추출 - 확장자 제외")
    void getBaseName_WithExtension_ReturnsBaseName() {
        // given
        FileName fileName = FileName.of("icaopkd-002-complete-009410.ldif");

        // when
        String baseName = fileName.getBaseName();

        // then
        assertThat(baseName).isEqualTo("icaopkd-002-complete-009410");
    }

    @Test
    @DisplayName("베이스명 변경 - 확장자 유지")
    void withBaseName_ChangesBaseNameKeepsExtension() {
        // given
        FileName original = FileName.of("file.txt");

        // when
        FileName renamed = original.withBaseName("newfile");

        // then
        assertThat(renamed.getValue()).isEqualTo("newfile.txt");
        assertThat(renamed.getExtension()).isEqualTo("txt");
    }

    @Test
    @DisplayName("Value Object 동등성 - 같은 값은 동일")
    void equals_SameValue_ReturnsTrue() {
        // given
        FileName fileName1 = FileName.of("file.txt");
        FileName fileName2 = FileName.of("file.txt");

        // when & then
        assertThat(fileName1).isEqualTo(fileName2);
        assertThat(fileName1.hashCode()).isEqualTo(fileName2.hashCode());
    }

    @Test
    @DisplayName("Value Object 동등성 - 다른 값은 다름")
    void equals_DifferentValue_ReturnsFalse() {
        // given
        FileName fileName1 = FileName.of("file1.txt");
        FileName fileName2 = FileName.of("file2.txt");

        // when & then
        assertThat(fileName1).isNotEqualTo(fileName2);
    }
}
