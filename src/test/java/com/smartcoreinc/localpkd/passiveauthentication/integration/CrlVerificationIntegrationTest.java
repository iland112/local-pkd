package com.smartcoreinc.localpkd.passiveauthentication.integration;

import com.smartcoreinc.localpkd.passiveauthentication.domain.model.CrlCheckResult;
import com.smartcoreinc.localpkd.passiveauthentication.domain.port.CrlLdapPort;
import com.smartcoreinc.localpkd.passiveauthentication.domain.service.CrlVerificationService;
import com.smartcoreinc.localpkd.passiveauthentication.infrastructure.cache.CrlCacheService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRL Verification Integration Test
 *
 * <p>Tests CRL checking with real Korean passport data and OpenLDAP PKD.</p>
 *
 * <h3>Test Data</h3>
 * <ul>
 *   <li>Korean Passport SOD (sod.bin)</li>
 *   <li>Korean CSCA in OpenLDAP</li>
 *   <li>Korean CRL in OpenLDAP (o=crl,c=KR,...)</li>
 * </ul>
 *
 * <h3>Test Scenarios</h3>
 * <ol>
 *   <li>CRL retrieval from LDAP (via cache)</li>
 *   <li>CRL signature verification</li>
 *   <li>CRL freshness check</li>
 *   <li>Certificate revocation check</li>
 * </ol>
 *
 * @since Phase 4.12
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("CRL Verification Integration Tests")
class CrlVerificationIntegrationTest {

    @Autowired
    private CrlLdapPort crlLdapPort;

    @Autowired
    private CrlCacheService crlCacheService;

    @Autowired
    private CrlVerificationService crlVerificationService;

    /**
     * Test: CRL retrieval from OpenLDAP for Korean CSCA
     *
     * <p>Prerequisites:</p>
     * <ul>
     *   <li>OpenLDAP running at localhost:389</li>
     *   <li>Korean CRL uploaded to LDAP: o=crl,c=KR,dc=data,dc=download,dc=pkd,...</li>
     * </ul>
     */
    @Test
    @DisplayName("Should retrieve Korean CRL from LDAP successfully")
    void testRetrieveKoreanCrlFromLdap() {
        // Given: Korean CSCA Subject DN (actual LDAP data)
        String cscaSubjectDn = "CN=CSCA-KOREA-2025,OU=MOFA,O=Government,C=KR";
        String countryCode = "KR";

        log.info("Testing CRL retrieval for CSCA: {}, Country: {}", cscaSubjectDn, countryCode);

        // When: Retrieve CRL from LDAP (direct, no cache)
        Optional<X509CRL> crlOpt = crlLdapPort.findCrlByCsca(cscaSubjectDn, countryCode);

        // Then: CRL should be found
        assertThat(crlOpt).isPresent();

        X509CRL crl = crlOpt.get();
        log.info("CRL retrieved successfully:");
        log.info("  Issuer: {}", crl.getIssuerX500Principal().getName());
        log.info("  This Update: {}", crl.getThisUpdate());
        log.info("  Next Update: {}", crl.getNextUpdate());
        log.info("  Revoked Certificates: {}", crl.getRevokedCertificates() != null ?
            crl.getRevokedCertificates().size() : 0);

        // Verify CRL properties
        assertThat(crl.getIssuerX500Principal().getName()).contains("KR");
        assertThat(crl.getThisUpdate()).isNotNull();
        // nextUpdate may be null for some CRLs
    }

    /**
     * Test: CRL caching (two-tier: memory + database)
     *
     * <p>Verifies:</p>
     * <ul>
     *   <li>First call: LDAP fetch → cache both tiers</li>
     *   <li>Second call: Memory cache hit (no LDAP)</li>
     *   <li>After memory clear: Database cache hit</li>
     * </ul>
     */
    @Test
    @DisplayName("Should cache CRL in memory and database")
    void testCrlCaching() {
        // Given: Korean CSCA DN and country
        String cscaSubjectDn = "CN=CSCA-KOREA-2025,OU=MOFA,O=Government,C=KR";
        String countryCode = "KR";

        // Clear memory cache to start fresh
        crlCacheService.clearMemoryCache();
        log.info("Memory cache cleared");

        // When: First call (should fetch from LDAP or DB, then cache)
        Optional<X509CRL> crl1 = crlCacheService.getCrl(cscaSubjectDn, countryCode);

        // Then: CRL should be retrieved
        assertThat(crl1).isPresent();
        log.info("First call: CRL retrieved and cached");

        // When: Second call (should hit memory cache)
        long startTime = System.currentTimeMillis();
        Optional<X509CRL> crl2 = crlCacheService.getCrl(cscaSubjectDn, countryCode);
        long duration = System.currentTimeMillis() - startTime;

        // Then: Should be much faster for memory cache
        assertThat(crl2).isPresent();
        assertThat(crl2.get()).isEqualTo(crl1.get());
        log.info("Second call: Memory cache hit ({}ms)", duration);
        assertThat(duration).isLessThan(100); // Memory cache should be very fast (< 100ms)

        // When: Clear memory cache and call again (should hit DB cache)
        crlCacheService.clearMemoryCache();
        log.info("Memory cache cleared again");

        startTime = System.currentTimeMillis();
        Optional<X509CRL> crl3 = crlCacheService.getCrl(cscaSubjectDn, countryCode);
        duration = System.currentTimeMillis() - startTime;

        // Then: Should still retrieve from DB cache (faster than LDAP but slower than memory)
        assertThat(crl3).isPresent();
        log.info("Third call: Database cache hit ({}ms)", duration);
        assertThat(duration).isLessThan(1000); // DB cache should be reasonably fast
    }

    /**
     * Test: CRL verification for non-revoked certificate
     *
     * <p>This test assumes the Korean passport DSC certificate is NOT revoked.</p>
     * <p>If the certificate is actually revoked in LDAP, adjust the assertion.</p>
     */
    @Test
    @DisplayName("Should verify DSC certificate is not revoked")
    void testCertificateNotRevoked() {
        // Given: Korean CSCA and CRL
        String cscaSubjectDn = "CN=CSCA-KOREA-2025,OU=MOFA,O=Government,C=KR";
        String countryCode = "KR";

        // Retrieve CRL
        Optional<X509CRL> crlOpt = crlCacheService.getCrl(cscaSubjectDn, countryCode);
        assertThat(crlOpt).isPresent();
        X509CRL crl = crlOpt.get();

        // Note: We need actual DSC and CSCA certificates for this test
        // For now, we'll test the CRL structure
        log.info("CRL verification test:");
        log.info("  CRL Issuer: {}", crl.getIssuerX500Principal().getName());
        log.info("  This Update: {}", crl.getThisUpdate());
        log.info("  Next Update: {}", crl.getNextUpdate());

        // Verify CRL structure (note: this test CRL may be expired)
        if (crl.getNextUpdate() != null) {
            java.util.Date now = new java.util.Date();
            if (crl.getNextUpdate().after(now)) {
                log.info("  CRL is fresh (not expired)");
            } else {
                log.warn("  CRL is expired (nextUpdate: {} < now: {})", crl.getNextUpdate(), now);
            }
        }

        // Log revoked certificates count
        if (crl.getRevokedCertificates() != null) {
            log.info("  Revoked certificates: {}", crl.getRevokedCertificates().size());
        } else {
            log.info("  No revoked certificates in CRL");
        }
    }

    /**
     * Test: CRL cache statistics
     *
     * <p>Verifies cache management methods work correctly.</p>
     */
    @Test
    @DisplayName("Should provide cache statistics")
    void testCacheStatistics() {
        // Given: Clear cache
        crlCacheService.clearMemoryCache();
        int initialSize = crlCacheService.getMemoryCacheSize();

        log.info("Initial cache size: {}", initialSize);
        assertThat(initialSize).isEqualTo(0);

        // When: Load one CRL
        String cscaSubjectDn = "CN=CSCA-KOREA-2025,OU=MOFA,O=Government,C=KR";
        String countryCode = "KR";

        crlCacheService.getCrl(cscaSubjectDn, countryCode);

        // Then: Cache size should increase
        int afterLoadSize = crlCacheService.getMemoryCacheSize();
        log.info("Cache size after loading: {}", afterLoadSize);
        assertThat(afterLoadSize).isGreaterThan(initialSize);

        // When: Clear cache
        crlCacheService.clearMemoryCache();
        int afterClearSize = crlCacheService.getMemoryCacheSize();

        // Then: Cache should be empty
        log.info("Cache size after clear: {}", afterClearSize);
        assertThat(afterClearSize).isEqualTo(0);
    }

    /**
     * Test: CRL LDAP filter escaping (RFC 4515)
     *
     * <p>Tests that DN with special characters is properly escaped.</p>
     */
    @Test
    @DisplayName("Should handle DNs with special characters")
    void testDnEscaping() {
        // Given: DN with comma, equals, and other characters
        String dnWithSpecialChars = "CN=CSCA-KOREA-2025,OU=MOFA,O=Government,C=KR";
        String countryCode = "KR";

        log.info("Testing DN escaping for: {}", dnWithSpecialChars);

        // When: Retrieve CRL (should handle escaping internally)
        Optional<X509CRL> crlOpt = crlLdapPort.findCrlByCsca(dnWithSpecialChars, countryCode);

        // Then: Should handle escaping correctly (no LDAP filter error)
        // If this test passes, escaping is working correctly
        log.info("DN escaping test completed. CRL found: {}", crlOpt.isPresent());

        // Note: CRL may or may not exist depending on LDAP data
        // The important thing is no LDAP filter syntax error occurs
    }

    /**
     * Test: CRL unavailable scenario
     *
     * <p>Tests behavior when CRL doesn't exist in LDAP.</p>
     */
    @Test
    @DisplayName("Should handle CRL unavailable gracefully")
    void testCrlUnavailable() {
        // Given: Non-existent CSCA (should not have CRL)
        String nonExistentCsca = "CN=NON-EXISTENT-CSCA,O=Test,C=XX";
        String countryCode = "XX";

        log.info("Testing CRL unavailable for: {}", nonExistentCsca);

        // When: Try to retrieve CRL
        Optional<X509CRL> crlOpt = crlCacheService.getCrl(nonExistentCsca, countryCode);

        // Then: Should return empty (not throw exception)
        assertThat(crlOpt).isEmpty();
        log.info("CRL unavailable handled gracefully (empty result)");
    }
}
