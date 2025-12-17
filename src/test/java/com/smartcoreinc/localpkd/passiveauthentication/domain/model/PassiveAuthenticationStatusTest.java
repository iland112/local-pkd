package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PassiveAuthenticationStatus Unit Tests")
class PassiveAuthenticationStatusTest {

    @Test
    @DisplayName("3가지 상태가 정의되어 있어야 함")
    void shouldHaveThreeStatuses() {
        PassiveAuthenticationStatus[] statuses = PassiveAuthenticationStatus.values();
        assertThat(statuses).hasSize(3);
        assertThat(statuses).containsExactly(
            PassiveAuthenticationStatus.VALID,
            PassiveAuthenticationStatus.INVALID,
            PassiveAuthenticationStatus.ERROR
        );
    }

    @Test
    @DisplayName("VALID 상태는 성공을 나타냄")
    void validStatusShouldIndicateSuccess() {
        assertThat(PassiveAuthenticationStatus.VALID.name()).isEqualTo("VALID");
    }

    @Test
    @DisplayName("INVALID 상태는 검증 실패를 나타냄")
    void invalidStatusShouldIndicateFailure() {
        assertThat(PassiveAuthenticationStatus.INVALID.name()).isEqualTo("INVALID");
    }

    @Test
    @DisplayName("ERROR 상태는 처리 오류를 나타냄")
    void errorStatusShouldIndicateError() {
        assertThat(PassiveAuthenticationStatus.ERROR.name()).isEqualTo("ERROR");
    }
}
