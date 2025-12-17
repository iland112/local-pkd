package com.smartcoreinc.localpkd.passiveauthentication.integration;

import com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter.UnboundIdLdapAdapter;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.SearchScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for retrieving certificates from LDAP server.
 *
 * <p>Tests LDAP connectivity and certificate retrieval for Passive Authentication.
 *
 * <p><b>Prerequisites</b>:</p>
 * <ul>
 *   <li>LDAP server running at configured address</li>
 *   <li>Test data uploaded (CSCA, DSC, CRL for country KR)</li>
 * </ul>
 *
 * <p><b>Test Data (Korea - KR)</b>:</p>
 * <ul>
 *   <li>CSCA: 7 certificates</li>
 *   <li>DSC: 216 certificates</li>
 *   <li>CRL: 1 entry</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("local")
class LdapCertificateRetrievalIntegrationTest {

    @Autowired
    private UnboundIdLdapAdapter ldapAdapter;

    @BeforeEach
    void setUp() {
        assertThat(ldapAdapter).isNotNull();
        assertThat(ldapAdapter.testConnection()).isTrue();
    }

    @Test
    void shouldRetrieveKoreaCSCACertificates() throws Exception {
        // Given: Search filter for Korea CSCA certificates
        String baseDn = "o=csca,c=KR,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com";
        String filter = "(objectClass=*)";

        // When: Search LDAP for CSCA entries
        List<Entry> results = ldapAdapter.searchEntries(baseDn, filter, SearchScope.ONE);

        // Then: Should find 7 CSCA certificates
        assertThat(results).isNotEmpty();
        assertThat(results.size()).isEqualTo(7);

        // Verify first entry has required attributes
        Entry firstEntry = results.get(0);
        assertThat(firstEntry.getAttribute("cn")).isNotNull();
        assertThat(firstEntry.getAttribute("sn")).isNotNull();
        assertThat(firstEntry.getAttribute("description")).isNotNull();
        assertThat(firstEntry.getAttribute("userCertificate;binary")).isNotNull();
    }

    @Test
    void shouldRetrieveKoreaDSCCertificates() throws Exception {
        // Given: Search filter for Korea DSC certificates
        String baseDn = "o=dsc,c=KR,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com";
        String filter = "(objectClass=*)";

        // When: Search LDAP for DSC entries
        List<Entry> results = ldapAdapter.searchEntries(baseDn, filter, SearchScope.ONE);

        // Then: Should find 216 DSC certificates
        assertThat(results).isNotEmpty();
        assertThat(results.size()).isEqualTo(216);

        // Verify entries have required attributes
        Entry entry = results.get(0);
        assertThat(entry.getAttribute("cn")).isNotNull();
        assertThat(entry.getAttribute("sn")).isNotNull();
        assertThat(entry.getAttribute("userCertificate;binary")).isNotNull();
    }

    @Test
    void shouldRetrieveKoreaCRL() throws Exception {
        // Given: Search filter for Korea CRL
        String baseDn = "o=crl,c=KR,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com";
        String filter = "(objectClass=*)";

        // When: Search LDAP for CRL entries
        List<Entry> results = ldapAdapter.searchEntries(baseDn, filter, SearchScope.ONE);

        // Then: Should find 1 CRL entry
        assertThat(results).isNotEmpty();
        assertThat(results.size()).isEqualTo(1);

        // Verify CRL entry has required attributes
        Entry crlEntry = results.get(0);
        assertThat(crlEntry.getAttribute("cn")).isNotNull();
        assertThat(crlEntry.getAttribute("certificateRevocationList;binary")).isNotNull();
    }

    @Test
    void shouldRetrieveValidCSCA() throws Exception {
        // Given: Search for VALID CSCA
        String baseDn = "o=csca,c=KR,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com";
        String filter = "(description=VALID)";

        // When: Search LDAP
        List<Entry> results = ldapAdapter.searchEntries(baseDn, filter, SearchScope.ONE);

        // Then: Should find at least one VALID CSCA
        assertThat(results).isNotEmpty();

        // Verify description attribute
        Entry validCsca = results.get(0);
        String description = validCsca.getAttributeValue("description");
        assertThat(description).isEqualTo("VALID");
    }

    @Test
    void shouldRetrieveExpiredDSC() throws Exception {
        // Given: Search for EXPIRED DSC
        String baseDn = "o=dsc,c=KR,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com";
        String filter = "(description=EXPIRED*)";

        // When: Search LDAP
        List<Entry> results = ldapAdapter.searchEntries(baseDn, filter, SearchScope.ONE);

        // Then: Should find expired DSC certificates
        assertThat(results).isNotEmpty();

        // Verify description contains EXPIRED
        Entry expiredDsc = results.get(0);
        String description = expiredDsc.getAttributeValue("description");
        assertThat(description).startsWith("EXPIRED:");
    }

    @Test
    void shouldParseCSCACertificateBinary() throws Exception {
        // Given: Search for Korea CSCA
        String baseDn = "o=csca,c=KR,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com";
        String filter = "(description=VALID)";
        List<Entry> results = ldapAdapter.searchEntries(baseDn, filter, SearchScope.ONE);
        assertThat(results).isNotEmpty();

        // When: Extract certificate binary
        Entry csca = results.get(0);
        byte[] certBytes = csca.getAttributeValueBytes("userCertificate;binary");

        // Then: Certificate binary should be parseable
        assertThat(certBytes).isNotNull();
        assertThat(certBytes.length).isGreaterThan(100); // X.509 cert minimum size

        // Verify it starts with DER header (0x30 0x82 for SEQUENCE)
        assertThat(certBytes[0]).isEqualTo((byte) 0x30);
    }
}
