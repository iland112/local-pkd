package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DataGroupHash Unit Tests")
class DataGroupHashTest {

    @Test
    @DisplayName("유효한 SHA-256 해시로 DataGroupHash 생성")
    void shouldCreateWithValidSha256Hash() {
        String validHash = "a1b2c3d4e5f67890123456789abcdef01234567890abcdef0123456789abcdef";
        DataGroupHash result = DataGroupHash.of(validHash);
        assertThat(result).isNotNull();
        assertThat(result.getValue()).isEqualTo(validHash);
    }

    @Test
    @DisplayName("null 해시로 생성 시 예외 발생")
    void shouldThrowExceptionForNullHash() {
        assertThatThrownBy(() -> DataGroupHash.of((String) null))
            .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("같은 해시값은 동등해야 함")
    void shouldBeEqualForSameHash() {
        String hash = "a1b2c3d4e5f67890123456789abcdef01234567890abcdef0123456789abcdef";
        DataGroupHash hash1 = DataGroupHash.of(hash);
        DataGroupHash hash2 = DataGroupHash.of(hash);
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1.hashCode()).isEqualTo(hash2.hashCode());
    }

    @Test
    @DisplayName("바이트 배열로부터 해시 계산")
    void shouldCalculateHashFromBytes() {
        byte[] data = "test data".getBytes();
        DataGroupHash result = DataGroupHash.calculate(data, "SHA-256");
        assertThat(result).isNotNull();
        assertThat(result.getValue()).hasSize(64);
    }
    
    @Test
    @DisplayName("바이트 배열로부터 DataGroupHash 생성")
    void shouldCreateFromByteArray() {
        byte[] hashBytes = new byte[32];  // SHA-256 hash size
        for (int i = 0; i < 32; i++) {
            hashBytes[i] = (byte) i;
        }
        DataGroupHash result = DataGroupHash.of(hashBytes);
        assertThat(result).isNotNull();
        assertThat(result.getValue()).isNotEmpty();
    }
}
