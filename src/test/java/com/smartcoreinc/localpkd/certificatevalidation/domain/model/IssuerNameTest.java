package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IssuerName Value Object Tests")
class IssuerNameTest {

    @Test
    @DisplayName("CSCA-XX 형식에서 국가 코드 추출 성공")
    void getCountryCode_CscaFormat_Success() {
        // Given
        IssuerName issuerName = IssuerName.of("CSCA-KR");

        // When
        String countryCode = issuerName.getCountryCode();

        // Then
        assertThat(countryCode).isEqualTo("KR");
    }

    @Test
    @DisplayName("CSCA-XX 형식 소문자 입력 시 대문자로 정규화")
    void of_CscaFormatLowercase_NormalizedToUppercase() {
        // Given
        String input = "csca-kr";

        // When
        IssuerName issuerName = IssuerName.of(input);

        // Then
        assertThat(issuerName.getValue()).isEqualTo("CSCA-KR");
        assertThat(issuerName.getCountryCode()).isEqualTo("KR");
    }

    @Test
    @DisplayName("DN 형식(대문자 C=)에서 국가 코드 추출 성공")
    void getCountryCode_DnFormatUppercase_Success() {
        // Given
        IssuerName issuerName = IssuerName.of("CN=CSCA Finland,OU=VRK,O=Finland,C=FI");

        // When
        String countryCode = issuerName.getCountryCode();

        // Then
        assertThat(countryCode).isEqualTo("FI");
    }

    @Test
    @DisplayName("DN 형식(소문자 c=)에서 국가 코드 추출 성공 - CASE INSENSITIVE")
    void getCountryCode_DnFormatLowercase_Success() {
        // Given
        IssuerName issuerName = IssuerName.of("CN=CSCA Finland,OU=VRK,O=Finland,c=fi");

        // When
        String countryCode = issuerName.getCountryCode();

        // Then
        assertThat(countryCode).isEqualTo("FI");
    }

    @Test
    @DisplayName("DN 형식(혼합 대소문자 c=FI)에서 국가 코드 추출 성공")
    void getCountryCode_DnFormatMixedCase_Success() {
        // Given
        IssuerName issuerName = IssuerName.of("CN=CSCA Finland,OU=VRK,O=Finland,c=FI");

        // When
        String countryCode = issuerName.getCountryCode();

        // Then
        assertThat(countryCode).isEqualTo("FI");
    }

    @Test
    @DisplayName("DN 형식에서 C= 컴포넌트가 없으면 빈 문자열 반환")
    void getCountryCode_DnFormatNoCountry_ReturnsEmpty() {
        // Given
        IssuerName issuerName = IssuerName.of("CN=CSCA Finland,OU=VRK,O=Finland");

        // When
        String countryCode = issuerName.getCountryCode();

        // Then
        assertThat(countryCode).isEmpty();
    }

    @Test
    @DisplayName("DN 형식 원본 값 유지 (대소문자 변경 없음)")
    void of_DnFormat_PreservesOriginalCase() {
        // Given
        String originalDn = "CN=CSCA Finland,OU=VRK,O=Finland,c=FI";

        // When
        IssuerName issuerName = IssuerName.of(originalDn);

        // Then
        assertThat(issuerName.getValue()).isEqualTo(originalDn); // 원본 유지
        assertThat(issuerName.getCountryCode()).isEqualTo("FI"); // 추출된 국가 코드는 대문자
    }

    @Test
    @DisplayName("DN 형식에서 공백이 있는 C= 컴포넌트 추출")
    void getCountryCode_DnFormatWithSpaces_Success() {
        // Given
        IssuerName issuerName = IssuerName.of("CN=CSCA Finland, OU=VRK, O=Finland, C=FI");

        // When
        String countryCode = issuerName.getCountryCode();

        // Then
        assertThat(countryCode).isEqualTo("FI");
    }

    @Test
    @DisplayName("실제 CRL Issuer DN 형식 테스트 (Finland)")
    void getCountryCode_RealCrlIssuerDn_Finland() {
        // Given - 실제 로그에서 발견된 DN
        IssuerName issuerName = IssuerName.of("CN=CSCA Finland,OU=VRK CA,O=Vaestorekisterikeskus CA,C=FI");

        // When
        String countryCode = issuerName.getCountryCode();

        // Then
        assertThat(countryCode).isEqualTo("FI");
    }

    @Test
    @DisplayName("null 입력 시 DomainException 발생")
    void of_NullInput_ThrowsException() {
        assertThatThrownBy(() -> IssuerName.of(null))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("Issuer name cannot be null or blank");
    }

    @Test
    @DisplayName("빈 문자열 입력 시 DomainException 발생")
    void of_BlankInput_ThrowsException() {
        assertThatThrownBy(() -> IssuerName.of("   "))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("Issuer name cannot be null or blank");
    }

    @Test
    @DisplayName("isCountry() 메서드 - CSCA 형식 일치 확인")
    void isCountry_CscaFormat_Match() {
        // Given
        IssuerName issuerName = IssuerName.of("CSCA-KR");

        // When & Then
        assertThat(issuerName.isCountry("KR")).isTrue();
        assertThat(issuerName.isCountry("US")).isFalse();
    }

    @Test
    @DisplayName("isCountry() 메서드 - DN 형식 일치 확인")
    void isCountry_DnFormat_Match() {
        // Given
        IssuerName issuerName = IssuerName.of("CN=CSCA Finland,OU=VRK,O=Finland,C=FI");

        // When & Then
        assertThat(issuerName.isCountry("FI")).isTrue();
        assertThat(issuerName.isCountry("SE")).isFalse();
    }
}
