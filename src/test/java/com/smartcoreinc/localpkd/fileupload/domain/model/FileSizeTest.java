package com.smartcoreinc.localpkd.fileupload.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * FileSize Value Object 테스트
 *
 * @author SmartCore Inc.
 * @since 2025-10-18
 */
@DisplayName("FileSize Value Object 테스트")
class FileSizeTest {

    @Test
    @DisplayName("바이트로 FileSize 생성 - 정상")
    void createFileSize_WithBytes_Success() {
        // given
        long bytes = 1024;

        // when
        FileSize fileSize = FileSize.ofBytes(bytes);

        // then
        assertThat(fileSize.getBytes()).isEqualTo(bytes);
    }

    @Test
    @DisplayName("킬로바이트로 FileSize 생성 - 정상")
    void createFileSize_WithKiloBytes_Success() {
        // given
        long kiloBytes = 10;

        // when
        FileSize fileSize = FileSize.ofKiloBytes(kiloBytes);

        // then
        assertThat(fileSize.getBytes()).isEqualTo(10 * 1024);
    }

    @Test
    @DisplayName("메가바이트로 FileSize 생성 - 정상")
    void createFileSize_WithMegaBytes_Success() {
        // given
        long megaBytes = 5;

        // when
        FileSize fileSize = FileSize.ofMegaBytes(megaBytes);

        // then
        assertThat(fileSize.getBytes()).isEqualTo(5 * 1024 * 1024);
    }

    @Test
    @DisplayName("0 바이트로 생성 시 예외 발생")
    void createFileSize_WithZeroBytes_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> FileSize.ofBytes(0))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("must be greater than 0");
    }

    @Test
    @DisplayName("음수 바이트로 생성 시 예외 발생")
    void createFileSize_WithNegativeBytes_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> FileSize.ofBytes(-1))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("must be greater than 0");
    }

    @Test
    @DisplayName("100MB를 초과하는 크기로 생성 시 예외 발생")
    void createFileSize_ExceedingLimit_ThrowsException() {
        // given
        long tooLarge = 104_857_601L;  // 100 MB + 1 byte

        // when & then
        assertThatThrownBy(() -> FileSize.ofBytes(tooLarge))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("exceeds limit");
    }

    @Test
    @DisplayName("사람이 읽기 쉬운 형식 - 바이트")
    void toHumanReadable_Bytes_ReturnsFormattedString() {
        // given
        FileSize fileSize = FileSize.ofBytes(512);

        // when
        String display = fileSize.toHumanReadable();

        // then
        assertThat(display).isEqualTo("512 bytes");
    }

    @Test
    @DisplayName("사람이 읽기 쉬운 형식 - 킬로바이트")
    void toHumanReadable_KiloBytes_ReturnsFormattedString() {
        // given
        FileSize fileSize = FileSize.ofBytes(1536);  // 1.5 KB

        // when
        String display = fileSize.toHumanReadable();

        // then
        assertThat(display).isEqualTo("1.5 KB");
    }

    @Test
    @DisplayName("사람이 읽기 쉬운 형식 - 메가바이트")
    void toHumanReadable_MegaBytes_ReturnsFormattedString() {
        // given
        FileSize fileSize = FileSize.ofBytes(10_485_760);  // 10 MB

        // when
        String display = fileSize.toHumanReadable();

        // then
        assertThat(display).isEqualTo("10.0 MB");
    }

    @Test
    @DisplayName("크기 비교 - 더 큰 경우")
    void isLargerThan_WhenLarger_ReturnsTrue() {
        // given
        FileSize size1 = FileSize.ofMegaBytes(10);
        FileSize size2 = FileSize.ofMegaBytes(5);

        // when & then
        assertThat(size1.isLargerThan(size2)).isTrue();
    }

    @Test
    @DisplayName("크기 비교 - 더 작은 경우")
    void isSmallerThan_WhenSmaller_ReturnsTrue() {
        // given
        FileSize size1 = FileSize.ofMegaBytes(5);
        FileSize size2 = FileSize.ofMegaBytes(10);

        // when & then
        assertThat(size1.isSmallerThan(size2)).isTrue();
    }

    @Test
    @DisplayName("최대 크기 제한 초과 확인 - 초과")
    void exceedsLimit_WhenExceeding_ReturnsTrue() {
        // given
        FileSize size = FileSize.ofMegaBytes(50);
        FileSize maxSize = FileSize.ofMegaBytes(30);

        // when & then
        assertThat(size.exceedsLimit(maxSize)).isTrue();
    }

    @Test
    @DisplayName("최대 크기 제한 초과 확인 - 미초과")
    void exceedsLimit_WhenNotExceeding_ReturnsFalse() {
        // given
        FileSize size = FileSize.ofMegaBytes(20);
        FileSize maxSize = FileSize.ofMegaBytes(30);

        // when & then
        assertThat(size.exceedsLimit(maxSize)).isFalse();
    }

    @Test
    @DisplayName("Value Object 동등성 - 같은 값은 동일")
    void equals_SameValue_ReturnsTrue() {
        // given
        FileSize fileSize1 = FileSize.ofBytes(1024);
        FileSize fileSize2 = FileSize.ofBytes(1024);

        // when & then
        assertThat(fileSize1).isEqualTo(fileSize2);
        assertThat(fileSize1.hashCode()).isEqualTo(fileSize2.hashCode());
    }

    @Test
    @DisplayName("Value Object 동등성 - 다른 값은 다름")
    void equals_DifferentValue_ReturnsFalse() {
        // given
        FileSize fileSize1 = FileSize.ofBytes(1024);
        FileSize fileSize2 = FileSize.ofBytes(2048);

        // when & then
        assertThat(fileSize1).isNotEqualTo(fileSize2);
    }

    @Test
    @DisplayName("toString은 사람이 읽기 쉬운 형식 반환")
    void toString_ReturnsHumanReadableFormat() {
        // given
        FileSize fileSize = FileSize.ofMegaBytes(75);

        // when
        String stringValue = fileSize.toString();

        // then
        assertThat(stringValue).isEqualTo("75.0 MB");
    }
}
