package com.smartcoreinc.localpkd.ldapintegration.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * LdapSearchFilterTest - Unit tests for LDAP search filter Value Object
 *
 * <p>Tests the search filter functionality including:
 * - Filter builder validation
 * - LDAP filter format validation (RFC 4515)
 * - Filter escaping and unescaping
 * - Filter combination (AND, OR operations)
 * - Static factory methods for common searches
 * </p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@DisplayName("LdapSearchFilter Tests")
class LdapSearchFilterTest {

    // ======================== Builder Tests ========================

    @Test
    @DisplayName("Builder should create valid search filter with required fields")
    void testBuilderSuccessWithRequiredFields() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        // When
        LdapSearchFilter filter = LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString("(cn=CSCA-KOREA)")
                .build();

        // Then
        assertThat(filter).isNotNull();
        assertThat(filter.getBaseDn()).isEqualTo(baseDn);
        assertThat(filter.getFilterString()).isEqualTo("(cn=CSCA-KOREA)");
        assertThat(filter.getScope()).isEqualTo(LdapSearchFilter.SearchScope.SUBTREE);  // default
        assertThat(filter.getSizeLimit()).isEqualTo(1000);  // default
        assertThat(filter.getTimeLimit()).isEqualTo(30);  // default
        assertThat(filter.isCaseSensitive()).isTrue();  // default
    }

    @Test
    @DisplayName("Builder should create search filter with all optional fields")
    void testBuilderSuccessWithAllFields() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        // When
        LdapSearchFilter filter = LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString("(cn=CSCA-*)")
                .scope(LdapSearchFilter.SearchScope.ONE_LEVEL)
                .returningAttributes("cn", "countryCode", "x509certificatedata")
                .sizeLimit(50)
                .timeLimit(60)
                .caseSensitive(false)
                .build();

        // Then
        assertThat(filter.getScope()).isEqualTo(LdapSearchFilter.SearchScope.ONE_LEVEL);
        assertThat(filter.getReturningAttributes()).hasSize(3);
        assertThat(filter.getReturningAttributes()).contains("cn", "countryCode", "x509certificatedata");
        assertThat(filter.getSizeLimit()).isEqualTo(50);
        assertThat(filter.getTimeLimit()).isEqualTo(60);
        assertThat(filter.isCaseSensitive()).isFalse();
    }

    @Test
    @DisplayName("Builder should throw exception when baseDn is null")
    void testBuilderNullBaseDn() {
        // When & Then
        assertThatThrownBy(() -> LdapSearchFilter.builder()
                .filterString("(cn=CSCA-KOREA)")
                .build())
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Base DN");
    }

    @Test
    @DisplayName("Builder should throw exception when filterString is null")
    void testBuilderNullFilterString() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        // When & Then
        assertThatThrownBy(() -> LdapSearchFilter.builder()
                .baseDn(baseDn)
                .build())
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Filter");
    }

    @Test
    @DisplayName("Builder should throw exception when filterString format is invalid")
    void testBuilderInvalidFilterFormat() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        // When & Then
        assertThatThrownBy(() -> LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString("cn=CSCA-KOREA")  // Missing parentheses
                .build())
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("RFC 4515");
    }

    @Test
    @DisplayName("Builder should throw exception when sizeLimit is invalid")
    void testBuilderInvalidSizeLimit() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        // When & Then
        assertThatThrownBy(() -> LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString("(cn=CSCA-KOREA)")
                .sizeLimit(-5)  // Invalid: not -1 (unlimited) or positive
                .build())
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Size limit");
    }

    @Test
    @DisplayName("Builder should throw exception when timeLimit is invalid")
    void testBuilderInvalidTimeLimit() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        // When & Then
        assertThatThrownBy(() -> LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString("(cn=CSCA-KOREA)")
                .timeLimit(-100)  // Invalid
                .build())
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Time limit");
    }

    // ======================== Filter Validation Tests ========================

    @Test
    @DisplayName("isValidFilter should return true for valid filter format")
    void testIsValidFilterSuccess() {
        // Given valid filters
        String filter1 = "(cn=CSCA-KOREA)";
        String filter2 = "(&(cn=CSCA-*)(countryCode=KR))";
        String filter3 = "(|(cn=CSCA-KR)(cn=CSCA-JP))";

        // When & Then
        assertThat(LdapSearchFilter.isValidFilter(filter1)).isTrue();
        assertThat(LdapSearchFilter.isValidFilter(filter2)).isTrue();
        assertThat(LdapSearchFilter.isValidFilter(filter3)).isTrue();
    }

    @Test
    @DisplayName("isValidFilter should return false for invalid filter format")
    void testIsValidFilterFailed() {
        // Given invalid filters
        String invalidFilter1 = "cn=CSCA-KOREA";  // Missing parentheses
        String invalidFilter2 = "(cn=CSCA-KOREA";  // Missing closing parenthesis
        String invalidFilter3 = "";  // Blank

        // When & Then
        assertThat(LdapSearchFilter.isValidFilter(invalidFilter1)).isFalse();
        assertThat(LdapSearchFilter.isValidFilter(invalidFilter2)).isFalse();
        assertThat(LdapSearchFilter.isValidFilter(invalidFilter3)).isFalse();
    }

    // ======================== Filter Escaping Tests ========================

    @Test
    @DisplayName("escapeLdapFilterValue should escape special characters")
    void testEscapeLdapFilterValue() {
        // Given value with special characters
        String value = "test*value(with)special\\chars";

        // When
        String escaped = LdapSearchFilter.escapeLdapFilterValue(value);

        // Then
        assertThat(escaped).contains("\\2a");  // * escaped
        assertThat(escaped).contains("\\28");  // ( escaped
        assertThat(escaped).contains("\\29");  // ) escaped
        assertThat(escaped).contains("\\5c");  // \ escaped
    }

    @Test
    @DisplayName("unescapeLdapFilterValue should unescape special characters")
    void testUnescapeLdapFilterValue() {
        // Given escaped value
        String escaped = "test\\2avalue\\28with\\29special\\5cchars";

        // When
        String unescaped = LdapSearchFilter.unescapeLdapFilterValue(escaped);

        // Then
        assertThat(unescaped).contains("*");  // \2a unescaped
        assertThat(unescaped).contains("(");  // \28 unescaped
        assertThat(unescaped).contains(")");  // \29 unescaped
        assertThat(unescaped).contains("\\");  // \5c unescaped
    }

    @Test
    @DisplayName("escapeLdapFilterValue should handle null and empty strings")
    void testEscapeLdapFilterValueNullAndEmpty() {
        // When & Then
        assertThat(LdapSearchFilter.escapeLdapFilterValue(null)).isNull();
        assertThat(LdapSearchFilter.escapeLdapFilterValue("")).isEmpty();
        assertThat(LdapSearchFilter.escapeLdapFilterValue("test")).isEqualTo("test");
    }

    // ======================== Filter Combination Tests ========================

    @Test
    @DisplayName("combineFiltersWithAnd should combine filters with AND operator")
    void testCombineFiltersWithAnd() {
        // Given filters
        String filter1 = "(cn=CSCA-*)";
        String filter2 = "(countryCode=KR)";

        // When
        String combined = LdapSearchFilter.combineFiltersWithAnd(filter1, filter2);

        // Then
        assertThat(combined).startsWith("(&");
        assertThat(combined).endsWith(")");
        assertThat(combined).contains(filter1);
        assertThat(combined).contains(filter2);
        assertThat(combined).isEqualTo("(&(cn=CSCA-*)(countryCode=KR))");
    }

    @Test
    @DisplayName("combineFiltersWithOr should combine filters with OR operator")
    void testCombineFiltersWithOr() {
        // Given filters
        String filter1 = "(cn=CSCA-KR)";
        String filter2 = "(cn=CSCA-JP)";
        String filter3 = "(cn=CSCA-US)";

        // When
        String combined = LdapSearchFilter.combineFiltersWithOr(filter1, filter2, filter3);

        // Then
        assertThat(combined).startsWith("(|");
        assertThat(combined).endsWith(")");
        assertThat(combined).contains(filter1);
        assertThat(combined).contains(filter2);
        assertThat(combined).contains(filter3);
        assertThat(combined).isEqualTo("(|(cn=CSCA-KR)(cn=CSCA-JP)(cn=CSCA-US))");
    }

    @Test
    @DisplayName("combineFiltersWithAnd should throw exception when no filters provided")
    void testCombineFiltersWithAndEmpty() {
        // When & Then
        assertThatThrownBy(() -> LdapSearchFilter.combineFiltersWithAnd())
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("At least one filter");
    }

    @Test
    @DisplayName("combineFiltersWithOr should throw exception when no filters provided")
    void testCombineFiltersWithOrEmpty() {
        // When & Then
        assertThatThrownBy(() -> LdapSearchFilter.combineFiltersWithOr())
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("At least one filter");
    }

    // ======================== Static Factory Methods Tests ========================

    @Test
    @DisplayName("forCertificateWithCn should create search filter for certificate with CN")
    void testForCertificateWithCnSuccess() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
        String cn = "CSCA-KOREA";

        // When
        LdapSearchFilter filter = LdapSearchFilter.forCertificateWithCn(baseDn, cn);

        // Then
        assertThat(filter).isNotNull();
        assertThat(filter.getBaseDn()).isEqualTo(baseDn);
        assertThat(filter.getFilterString()).contains(cn);
        assertThat(filter.getReturningAttributes()).contains("cn", "countryCode", "x509certificatedata");
    }

    @Test
    @DisplayName("forCertificateWithCn should throw exception when CN is null")
    void testForCertificateWithCnNullCn() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        // When & Then
        assertThatThrownBy(() -> LdapSearchFilter.forCertificateWithCn(baseDn, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("forCertificateWithCountry should create search filter for country code")
    void testForCertificateWithCountrySuccess() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
        String countryCode = "KR";

        // When
        LdapSearchFilter filter = LdapSearchFilter.forCertificateWithCountry(baseDn, countryCode);

        // Then
        assertThat(filter).isNotNull();
        assertThat(filter.getBaseDn()).isEqualTo(baseDn);
        assertThat(filter.getFilterString()).contains(countryCode);
        assertThat(filter.getReturningAttributes()).contains("countryCode");
    }

    @Test
    @DisplayName("forCertificateWithCountry should throw exception when country code is invalid")
    void testForCertificateWithCountryInvalidCode() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        // When & Then (3 chars instead of 2)
        assertThatThrownBy(() -> LdapSearchFilter.forCertificateWithCountry(baseDn, "KOR"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("2 characters");
    }

    @Test
    @DisplayName("forCrlWithIssuer should create search filter for CRL")
    void testForCrlWithIssuerSuccess() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=crls,dc=ldap,dc=smartcoreinc,dc=com");
        String issuerDn = "cn=Root CA,ou=ca,dc=ldap,dc=smartcoreinc,dc=com";

        // When
        LdapSearchFilter filter = LdapSearchFilter.forCrlWithIssuer(baseDn, issuerDn);

        // Then
        assertThat(filter).isNotNull();
        assertThat(filter.getBaseDn()).isEqualTo(baseDn);
        assertThat(filter.getFilterString()).contains("issuerDN");
        assertThat(filter.getReturningAttributes()).contains("issuerDN", "x509crldata");
    }

    @Test
    @DisplayName("forCrlWithIssuer should throw exception when issuerDn is null")
    void testForCrlWithIssuerNullIssuerDn() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=crls,dc=ldap,dc=smartcoreinc,dc=com");

        // When & Then
        assertThatThrownBy(() -> LdapSearchFilter.forCrlWithIssuer(baseDn, null))
                .isInstanceOf(DomainException.class);
    }

    // ======================== Miscellaneous Tests ========================

    @Test
    @DisplayName("getScopeDescription should return description for each scope")
    void testGetScopeDescription() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        // When
        LdapSearchFilter objectScopeFilter = LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString("(cn=CSCA-KOREA)")
                .scope(LdapSearchFilter.SearchScope.OBJECT_SCOPE)
                .build();

        LdapSearchFilter oneLevelFilter = LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString("(cn=CSCA-KOREA)")
                .scope(LdapSearchFilter.SearchScope.ONE_LEVEL)
                .build();

        LdapSearchFilter subtreeFilter = LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString("(cn=CSCA-KOREA)")
                .scope(LdapSearchFilter.SearchScope.SUBTREE)
                .build();

        // Then
        assertThat(objectScopeFilter.getScopeDescription()).contains("특정");
        assertThat(oneLevelFilter.getScopeDescription()).contains("직접");
        assertThat(subtreeFilter.getScopeDescription()).contains("전체");
    }

    @Test
    @DisplayName("toString should return filter summary")
    void testToString() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        // When
        LdapSearchFilter filter = LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString("(cn=CSCA-KOREA)")
                .returningAttributes("cn", "countryCode")
                .sizeLimit(50)
                .timeLimit(60)
                .build();

        String summary = filter.toString();

        // Then
        assertThat(summary).isNotEmpty();
        assertThat(summary).contains("LdapSearchFilter");
        assertThat(summary).contains("baseDn");
        assertThat(summary).contains("sizeLimit");
    }

    @Test
    @DisplayName("Two filters with same values should have value equality")
    void testValueEquality() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        // When
        LdapSearchFilter filter1 = LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString("(cn=CSCA-KOREA)")
                .build();

        LdapSearchFilter filter2 = LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString("(cn=CSCA-KOREA)")
                .build();

        // Then
        assertThat(filter1).isEqualTo(filter2);
        assertThat(filter1.hashCode()).isEqualTo(filter2.hashCode());
    }
}
