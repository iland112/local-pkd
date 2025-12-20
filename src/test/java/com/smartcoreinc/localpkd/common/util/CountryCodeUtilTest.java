package com.smartcoreinc.localpkd.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CountryCodeUtil - DN Parsing Helper Tests")
class CountryCodeUtilTest {

    // ========== CSCA-XX 형식 테스트 ==========

    @Test
    @DisplayName("CSCA-XX 형식 (대문자) - KR 추출")
    void extractCountryCode_CscaUppercase_ExtractsKR() {
        // Given
        String input = "CSCA-KR";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isEqualTo("KR");
    }

    @Test
    @DisplayName("CSCA-XX 형식 (소문자) - US 추출 및 대문자 정규화")
    void extractCountryCode_CscaLowercase_ExtractsUS() {
        // Given
        String input = "csca-us";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isEqualTo("US");
    }

    @Test
    @DisplayName("CSCA-XX 형식 (혼합) - JP 추출")
    void extractCountryCode_CscaMixedCase_ExtractsJP() {
        // Given
        String input = "Csca-Jp";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isEqualTo("JP");
    }

    @Test
    @DisplayName("CSCA-XX 형식 공백 포함 - trim 후 처리")
    void extractCountryCode_CscaWithSpaces_ExtractsAfterTrim() {
        // Given
        String input = "  CSCA-FR  ";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isEqualTo("FR");
    }

    // ========== DN 형식 테스트 (X.509) ==========

    @Test
    @DisplayName("X.509 DN (대문자 C=) - FI 추출")
    void extractCountryCode_X509DnUppercase_ExtractsFI() {
        // Given
        String input = "CN=CSCA Finland,OU=VRK CA,O=Vaestorekisterikeskus CA,C=FI";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isEqualTo("FI");
    }

    @Test
    @DisplayName("X.509 DN (소문자 c=) - DE 추출")
    void extractCountryCode_X509DnLowercase_ExtractsDE() {
        // Given
        String input = "CN=CSCA Germany,OU=BSI,O=Germany,c=de";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isEqualTo("DE");
    }

    @Test
    @DisplayName("X.509 DN (혼합 대소문자 c=SE) - SE 추출")
    void extractCountryCode_X509DnMixedCase_ExtractsSE() {
        // Given
        String input = "CN=CSCA Sweden,OU=Police,O=Sweden,c=SE";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isEqualTo("SE");
    }

    @Test
    @DisplayName("X.509 DN 공백 포함 - 공백 trim 후 추출")
    void extractCountryCode_X509DnWithSpaces_ExtractsNO() {
        // Given
        String input = "CN=CSCA Norway, OU=Police, O=Norway, C=NO";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isEqualTo("NO");
    }

    @Test
    @DisplayName("X.509 DN 3자 국가 코드 - USA 추출")
    void extractCountryCode_X509DnThreeLetters_ExtractsUSA() {
        // Given
        String input = "CN=Test CA,O=Test Org,C=USA";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isEqualTo("USA");
    }

    // ========== LDIF DN 형식 테스트 ==========

    @Test
    @DisplayName("LDIF DN 형식 (소문자) - KR 추출")
    void extractCountryCode_LdifDnLowercase_ExtractsKR() {
        // Given
        String input = "cn=CN\\=CSCA-KOREA\\,O\\=Government\\,C\\=KR+sn=123,o=csca,c=KR,dc=data";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isEqualTo("KR");
    }

    @Test
    @DisplayName("LDIF DN 형식 (대문자) - FR 추출")
    void extractCountryCode_LdifDnUppercase_ExtractsFR() {
        // Given
        String input = "cn=CSCA France,o=ml,C=FR,dc=data,dc=download";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isEqualTo("FR");
    }

    // ========== Edge Case 테스트 ==========

    @Test
    @DisplayName("null 입력 - null 반환")
    void extractCountryCode_NullInput_ReturnsNull() {
        // When
        String result = CountryCodeUtil.extractCountryCode(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("빈 문자열 - null 반환")
    void extractCountryCode_EmptyString_ReturnsNull() {
        // When
        String result = CountryCodeUtil.extractCountryCode("");

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("공백만 있는 문자열 - null 반환")
    void extractCountryCode_BlankString_ReturnsNull() {
        // When
        String result = CountryCodeUtil.extractCountryCode("   ");

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("C= 컴포넌트 없는 DN - null 반환")
    void extractCountryCode_DnWithoutCountry_ReturnsNull() {
        // Given
        String input = "CN=Test CA,O=Test Org,OU=Test Unit";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("잘못된 CSCA 형식 - null 반환")
    void extractCountryCode_InvalidCscaFormat_ReturnsNull() {
        // Given - CSCA 뒤에 숫자만 있는 경우
        String input = "CSCA-123";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("CSCA 접두사만 있는 경우 - null 반환")
    void extractCountryCode_OnlyCscaPrefix_ReturnsNull() {
        // Given
        String input = "CSCA-";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isNull();
    }

    // ========== 실제 데이터 테스트 (Real-World Examples) ==========

    @Test
    @DisplayName("실제 CRL Issuer DN (Finland) - FI 추출")
    void extractCountryCode_RealCrlIssuerDn_Finland() {
        // Given - 실제 로그에서 발견된 DN
        String input = "CN=CSCA Finland,OU=VRK CA,O=Vaestorekisterikeskus CA,C=FI";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isEqualTo("FI");
    }

    @Test
    @DisplayName("실제 Master List DN (Korea) - KR 추출")
    void extractCountryCode_RealMasterListDn_Korea() {
        // Given - 실제 LDIF에서 발견된 DN
        String input = "cn=CN\\=CSCA-KOREA\\,O\\=Government of Republic of Korea\\,C\\=KR+sn=12345,o=csca,c=KR,dc=data,dc=download,dc=pkd";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isEqualTo("KR");
    }

    @Test
    @DisplayName("실제 DSC Subject DN (New Zealand) - NZ 추출")
    void extractCountryCode_RealDscSubjectDn_NewZealand() {
        // Given
        String input = "OU=Identity Services Passport CA,OU=Passports,O=Government of New Zealand,C=NZ";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isEqualTo("NZ");
    }

    // ========== 경계값 테스트 ==========

    @Test
    @DisplayName("DN 시작 부분에 C= 위치 - 정상 추출")
    void extractCountryCode_CountryAtStart_Extracts() {
        // Given
        String input = "C=IT,O=Test Org,CN=Test CA";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isEqualTo("IT");
    }

    @Test
    @DisplayName("DN 중간에 C= 위치 - 정상 추출")
    void extractCountryCode_CountryInMiddle_Extracts() {
        // Given
        String input = "CN=Test CA,C=ES,O=Test Org";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isEqualTo("ES");
    }

    @Test
    @DisplayName("C= 값에 공백 포함 - trim 후 추출")
    void extractCountryCode_CountryWithSpaces_ExtractsAfterTrim() {
        // Given
        String input = "CN=Test CA,C= BE ,O=Test Org";

        // When
        String result = CountryCodeUtil.extractCountryCode(input);

        // Then
        assertThat(result).isEqualTo("BE");
    }
}
