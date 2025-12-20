package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DataGroupNumber Unit Tests")
class DataGroupNumberTest {

    @Test
    @DisplayName("모든 Data Group 번호(DG1-DG16)가 정의되어 있어야 함")
    void shouldHaveAllDataGroupNumbers() {
        DataGroupNumber[] allDataGroups = DataGroupNumber.values();
        assertThat(allDataGroups).hasSize(16);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DG1", "DG2", "DG15", "DG16"})
    @DisplayName("유효한 Data Group 문자열을 올바르게 변환해야 함")
    void shouldConvertValidDataGroupString(String dgString) {
        DataGroupNumber result = DataGroupNumber.fromString(dgString);
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo(dgString);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 15, 16})
    @DisplayName("유효한 Data Group 번호를 올바르게 변환해야 함")
    void shouldConvertValidDataGroupNumber(int dgNumber) {
        DataGroupNumber result = DataGroupNumber.fromInt(dgNumber);
        assertThat(result).isNotNull();
        assertThat(result.getValue()).isEqualTo(dgNumber);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "DG0", "DG17", "INVALID"})
    @DisplayName("유효하지 않은 Data Group 문자열은 예외를 발생시켜야 함")
    void shouldThrowExceptionForInvalidString(String invalidString) {
        assertThatThrownBy(() -> DataGroupNumber.fromString(invalidString))
            .isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    @DisplayName("DG1은 값 1을 가져야 함")
    void dg1ShouldHaveValue1() {
        assertThat(DataGroupNumber.DG1.getValue()).isEqualTo(1);
    }
}
