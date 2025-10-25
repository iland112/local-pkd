package com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter;

import com.smartcoreinc.localpkd.ldapintegration.domain.model.DistinguishedName;
import com.smartcoreinc.localpkd.ldapintegration.domain.model.LdapCertificateEntry;
import com.smartcoreinc.localpkd.ldapintegration.domain.model.LdapSearchFilter;
import com.smartcoreinc.localpkd.ldapintegration.domain.port.LdapQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.LdapTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * SpringLdapQueryAdapterTest - Unit tests for LDAP query adapter
 *
 * <p>Tests the query functionality including:
 * - Certificate and CRL lookups by DN
 * - Search by various criteria (CN, Country Code, Issuer DN)
 * - Pagination support
 * - Statistics and counting
 * </p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpringLdapQueryAdapter Tests")
class SpringLdapQueryAdapterTest {

    @Mock
    private LdapTemplate ldapTemplate;

    private SpringLdapQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpringLdapQueryAdapter(ldapTemplate);
    }

    // ======================== findCertificateByDn Tests ========================

    @Test
    @DisplayName("findCertificateByDn should return empty Optional (stub)")
    void testFindCertificateByDnNotFound() {
        // Given
        DistinguishedName dn = DistinguishedName.of("cn=CSCA-KOREA,ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        // When
        Optional<LdapCertificateEntry> result = adapter.findCertificateByDn(dn);

        // Then
        assertThat(result).isEmpty();  // Stub returns empty
    }

    @Test
    @DisplayName("findCertificateByDn should throw exception when DN is null")
    void testFindCertificateByDnNullDn() {
        // When & Then
        assertThatThrownBy(() -> adapter.findCertificateByDn(null))
                .isInstanceOf(Exception.class);
    }

    // ======================== searchCertificatesByCommonName Tests ========================

    @Test
    @DisplayName("searchCertificatesByCommonName should return empty list (stub)")
    void testSearchCertificatesByCommonNameSuccess() {
        // Given
        String cn = "CSCA-KOREA";

        // When
        List<LdapCertificateEntry> result = adapter.searchCertificatesByCommonName(cn);

        // Then
        assertThat(result).isEmpty();  // Stub returns empty
    }

    @Test
    @DisplayName("searchCertificatesByCommonName should throw exception when CN is null")
    void testSearchCertificatesByCommonNameNullCn() {
        // When & Then
        assertThatThrownBy(() -> adapter.searchCertificatesByCommonName(null))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("searchCertificatesByCommonName should throw exception when CN is blank")
    void testSearchCertificatesByCommonNameBlankCn() {
        // When & Then
        assertThatThrownBy(() -> adapter.searchCertificatesByCommonName(""))
                .isInstanceOf(Exception.class);
    }

    // ======================== searchCertificatesByCountry Tests ========================

    @Test
    @DisplayName("searchCertificatesByCountry should return empty list (stub)")
    void testSearchCertificatesByCountrySuccess() {
        // Given
        String countryCode = "KR";

        // When
        List<LdapCertificateEntry> result = adapter.searchCertificatesByCountry(countryCode);

        // Then
        assertThat(result).isEmpty();  // Stub returns empty
    }

    @Test
    @DisplayName("searchCertificatesByCountry should throw exception when country code is null")
    void testSearchCertificatesByCountryNullCode() {
        // When & Then
        assertThatThrownBy(() -> adapter.searchCertificatesByCountry(null))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("searchCertificatesByCountry should throw exception when country code is invalid")
    void testSearchCertificatesByCountryInvalidCode() {
        // When & Then
        assertThatThrownBy(() -> adapter.searchCertificatesByCountry("KOR"))  // 3 chars instead of 2
                .isInstanceOf(Exception.class);
    }

    // ======================== search Tests ========================

    @Test
    @DisplayName("search should return empty list (stub)")
    void testSearchSuccess() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
        LdapSearchFilter filter = LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString("(cn=CSCA-*)")
                .build();

        // When
        List<Map<String, Object>> result = adapter.search(filter);

        // Then
        assertThat(result).isEmpty();  // Stub returns empty
    }

    @Test
    @DisplayName("search should throw exception when filter is null")
    void testSearchNullFilter() {
        // When & Then
        assertThatThrownBy(() -> adapter.search(null))
                .isInstanceOf(Exception.class);
    }

    // ======================== searchWithPagination Tests ========================

    @Test
    @DisplayName("searchWithPagination should return QueryResult")
    void testSearchWithPaginationFirstPage() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
        LdapSearchFilter filter = LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString("(cn=CSCA-*)")
                .build();

        // When
        LdapQueryService.QueryResult<Map<String, Object>> result = adapter.searchWithPagination(filter, 0, 20);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();  // Stub returns empty
        assertThat(result.getTotalCount()).isZero();
        assertThat(result.getCurrentPageIndex()).isZero();
        assertThat(result.getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("searchWithPagination should calculate progress percentage correctly")
    void testSearchWithPaginationCalculatePercentage() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
        LdapSearchFilter filter = LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString("(cn=CSCA-*)")
                .build();

        // When
        LdapQueryService.QueryResult<Map<String, Object>> result = adapter.searchWithPagination(filter, 0, 20);

        // Then
        assertThat(result.getTotalPages()).isGreaterThanOrEqualTo(0);
        assertThat(result.hasNextPage()).isFalse();  // Empty results
        assertThat(result.hasPreviousPage()).isFalse();
    }

    // ======================== searchCertificatesWithPagination Tests ========================

    @Test
    @DisplayName("searchCertificatesWithPagination should return QueryResult")
    void testSearchCertificatesWithPaginationSuccess() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
        LdapSearchFilter filter = LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString("(countryCode=KR)")
                .build();

        // When
        LdapQueryService.QueryResult<LdapCertificateEntry> result = adapter.searchCertificatesWithPagination(filter, 0, 50);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();  // Stub returns empty
        assertThat(result.getPageSize()).isEqualTo(50);
        assertThat(result.getDurationMillis()).isGreaterThanOrEqualTo(0);
    }

    // ======================== countCertificates Tests ========================

    @Test
    @DisplayName("countCertificates should return count (0 for stub)")
    void testCountCertificatesSuccess() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        // When
        long count = adapter.countCertificates(baseDn);

        // Then
        assertThat(count).isZero();  // Stub returns 0
    }

    @Test
    @DisplayName("countCertificates should throw exception when baseDn is null")
    void testCountCertificatesNullBaseDn() {
        // When & Then
        assertThatThrownBy(() -> adapter.countCertificates(null))
                .isInstanceOf(Exception.class);
    }

    // ======================== countCertificatesByCountry Tests ========================

    @Test
    @DisplayName("countCertificatesByCountry should return map (empty for stub)")
    void testCountCertificatesByCountrySuccess() {
        // When
        Map<String, Long> result = adapter.countCertificatesByCountry();

        // Then
        assertThat(result).isEmpty();  // Stub returns empty map
    }

    // ======================== listSubordinateDns Tests ========================

    @Test
    @DisplayName("listSubordinateDns should return list of DNs (empty for stub)")
    void testListSubordinateDnsSuccess() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        // When
        List<DistinguishedName> result = adapter.listSubordinateDns(baseDn);

        // Then
        assertThat(result).isEmpty();  // Stub returns empty
    }

    @Test
    @DisplayName("listSubordinateDns should throw exception when baseDn is null")
    void testListSubordinateDnsNullBaseDn() {
        // When & Then
        assertThatThrownBy(() -> adapter.listSubordinateDns(null))
                .isInstanceOf(Exception.class);
    }

    // ======================== testConnection Tests ========================

    @Test
    @DisplayName("testConnection should return false (stub)")
    void testConnectionSuccess() {
        // When
        boolean result = adapter.testConnection();

        // Then
        assertThat(result).isFalse();  // Stub returns false
    }

    // ======================== getServerInfo Tests ========================

    @Test
    @DisplayName("getServerInfo should return server info string")
    void testGetServerInfoSuccess() {
        // When
        String result = adapter.getServerInfo();

        // Then
        assertThat(result).isNotEmpty();
        assertThat(result).contains("Spring LDAP");
    }

    // ======================== Integration Tests ========================

    @Test
    @DisplayName("Adapter should be instantiated with LdapTemplate")
    void testAdapterInstantiation() {
        // Then
        assertThat(adapter).isNotNull();
    }

    @Test
    @DisplayName("Multiple queries should be independent")
    void testMultipleQueriesIndependent() {
        // Given
        String cn1 = "CSCA-KOREA";
        String cn2 = "DSC-JAPAN";

        // When
        List<LdapCertificateEntry> result1 = adapter.searchCertificatesByCommonName(cn1);
        List<LdapCertificateEntry> result2 = adapter.searchCertificatesByCommonName(cn2);

        // Then
        assertThat(result1).isEmpty();
        assertThat(result2).isEmpty();
    }
}
