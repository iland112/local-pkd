package com.smartcoreinc.localpkd.ldapintegration.integration;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldif.LDIFException;
import com.unboundid.util.ssl.SSLUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ldap.core.support.LdapContextSource;

import java.util.*;

/**
 * LdapIntegrationTestFixture - Embedded LDAP test server fixture
 *
 * <p><b>Purpose</b>:</p>
 * <ul>
 *   <li>Setup in-memory LDAP directory for testing</li>
 *   <li>Provide test data (certificates, CRLs, users)</li>
 *   <li>Manage LDAP server lifecycle</li>
 *   <li>Configure connection parameters for test environment</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * LdapIntegrationTestFixture fixture = new LdapIntegrationTestFixture();
 * fixture.start();
 * try {
 *     // Use fixture for testing
 *     LdapContextSource contextSource = fixture.createContextSource();
 *     // ...
 * } finally {
 *     fixture.stop();
 * }
 * }</pre>
 *
 * <h3>Default Directory Structure</h3>
 * <pre>
 * dc=ldap,dc=smartcoreinc,dc=com
 *   ├── ou=certificates
 *   │   ├── ou=csca
 *   │   ├── ou=dsc
 *   │   └── ou=ds
 *   ├── ou=crl
 *   └── ou=users
 *       └── cn=admin
 * </pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Slf4j
public class LdapIntegrationTestFixture {

    public static final String BASE_DN = "dc=ldap,dc=smartcoreinc,dc=com";
    public static final String ADMIN_DN = "cn=admin,dc=ldap,dc=smartcoreinc,dc=com";
    public static final String ADMIN_PASSWORD = "admin-password";

    public static final String CERTIFICATE_BASE_DN = "ou=certificates," + BASE_DN;
    public static final String CSCA_BASE_DN = "ou=csca," + CERTIFICATE_BASE_DN;
    public static final String DSC_BASE_DN = "ou=dsc," + CERTIFICATE_BASE_DN;
    public static final String DS_BASE_DN = "ou=ds," + CERTIFICATE_BASE_DN;
    public static final String CRL_BASE_DN = "ou=crl," + BASE_DN;
    public static final String USERS_BASE_DN = "ou=users," + BASE_DN;

    private static final int PORT = 13389;  // Port for embedded server (non-standard to avoid conflicts)

    @Getter
    private InMemoryDirectoryServer directoryServer;

    @Getter
    private int port = PORT;

    private boolean started = false;

    /**
     * Start embedded LDAP directory server
     */
    public void start() {
        if (started) {
            log.warn("LDAP test fixture already started");
            return;
        }

        try {
            log.info("Starting embedded LDAP test server on port {}", port);

            // Create configuration
            InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
            config.setListenerConfigs(
                    new InMemoryListenerConfig("default", null, port, null, null, null)
            );
            config.setEnforceAttributeSyntaxCompliance(true);

            // Create and start server
            directoryServer = new InMemoryDirectoryServer(config);

            // Initialize directory structure
            initializeDirectoryStructure();

            started = true;
            log.info("Embedded LDAP test server started successfully");

        } catch (LDAPException e) {
            log.error("Failed to start embedded LDAP server", e);
            throw new RuntimeException("Failed to start embedded LDAP server", e);
        }
    }

    /**
     * Stop embedded LDAP directory server
     */
    public void stop() {
        if (directoryServer != null) {
            try {
                log.info("Stopping embedded LDAP test server");
                directoryServer.shutDown(true);
                started = false;
                log.info("Embedded LDAP test server stopped");
            } catch (Exception e) {
                log.error("Error stopping embedded LDAP server", e);
            }
        }
    }

    /**
     * Create LdapContextSource configured for test environment
     *
     * @return LdapContextSource
     */
    public LdapContextSource createContextSource() {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrls(new String[]{String.format("ldap://localhost:%d", port)});
        contextSource.setBase(BASE_DN);
        contextSource.setUserDn(ADMIN_DN);
        contextSource.setPassword(ADMIN_PASSWORD);

        try {
            contextSource.afterPropertiesSet();
        } catch (Exception e) {
            log.error("Error initializing context source", e);
            throw new RuntimeException("Failed to initialize context source", e);
        }

        return contextSource;
    }

    /**
     * Initialize default directory structure and test data
     */
    private void initializeDirectoryStructure() {
        try {
            // Root entry
            directoryServer.add(createEntry(
                    BASE_DN,
                    "objectClass", "dcObject",
                    "objectClass", "organization",
                    "dc", "ldap",
                    "o", "SmartCore LDAP"
            ));

            // Admin user
            directoryServer.add(createEntry(
                    ADMIN_DN,
                    "objectClass", "inetOrgPerson",
                    "cn", "admin",
                    "sn", "Administrator",
                    "userPassword", ADMIN_PASSWORD
            ));

            // Organizational Units
            addOrganizationalUnit("ou=certificates," + BASE_DN, "Certificates");
            addOrganizationalUnit(CSCA_BASE_DN, "Country Signing Certification Authority");
            addOrganizationalUnit(DSC_BASE_DN, "Document Signer Certificates");
            addOrganizationalUnit(DS_BASE_DN, "Digital Signature");
            addOrganizationalUnit(CRL_BASE_DN, "Certificate Revocation Lists");
            addOrganizationalUnit(USERS_BASE_DN, "Users");

            log.info("LDAP directory structure initialized");

        } catch (LDAPException e) {
            log.error("Error initializing directory structure", e);
            throw new RuntimeException("Failed to initialize directory structure", e);
        }
    }

    /**
     * Create Entry from DN and attributes
     *
     * @param dn Distinguished Name
     * @param attributes Alternating attribute names and values
     * @return Entry
     */
    private Entry createEntry(String dn, String... attributes) {
        Entry entry = new Entry(dn);
        for (int i = 0; i < attributes.length; i += 2) {
            entry.addAttribute(attributes[i], attributes[i + 1]);
        }
        return entry;
    }

    /**
     * Add organizational unit to directory
     *
     * @param dn Distinguished Name of OU
     * @param description Description
     */
    private void addOrganizationalUnit(String dn, String description) throws LDAPException {
        directoryServer.add(createEntry(
                dn,
                "objectClass", "organizationalUnit",
                "ou", extractOu(dn),
                "description", description
        ));
    }

    /**
     * Extract OU name from DN (e.g., "ou=certificates,dc=ldap,dc=smartcoreinc,dc=com" → "certificates")
     *
     * @param dn Distinguished Name
     * @return OU name
     */
    private String extractOu(String dn) {
        String[] parts = dn.split(",");
        if (parts.length > 0 && parts[0].startsWith("ou=")) {
            return parts[0].substring(3);
        }
        return "unknown";
    }

    /**
     * Add test certificate entry to directory
     *
     * @param dn Certificate DN
     * @param cn Certificate common name
     * @param countryCode Country code
     * @param x509CertificateData Base64-encoded certificate
     */
    public void addTestCertificate(String dn, String cn, String countryCode, String x509CertificateData) {
        try {
            directoryServer.add(createEntry(
                    dn,
                    "objectClass", "inetOrgPerson",
                    "cn", cn,
                    "sn", cn,
                    "countryCode", countryCode,
                    "x509CertificateData", x509CertificateData,
                    "createTimestamp", String.valueOf(System.currentTimeMillis())
            ));
            log.debug("Added test certificate: {}", dn);
        } catch (LDAPException e) {
            log.error("Error adding test certificate: {}", dn, e);
            throw new RuntimeException("Failed to add test certificate", e);
        }
    }

    /**
     * Add test CRL entry to directory
     *
     * @param dn CRL DN
     * @param cn CRL name
     * @param issuerDn Issuer DN
     * @param x509CrlData Base64-encoded CRL
     */
    public void addTestCrl(String dn, String cn, String issuerDn, String x509CrlData) {
        try {
            directoryServer.add(createEntry(
                    dn,
                    "objectClass", "inetOrgPerson",
                    "cn", cn,
                    "sn", cn,
                    "issuerDN", issuerDn,
                    "x509CrlData", x509CrlData,
                    "thisUpdate", String.valueOf(System.currentTimeMillis()),
                    "nextUpdate", String.valueOf(System.currentTimeMillis() + 86400000)
            ));
            log.debug("Added test CRL: {}", dn);
        } catch (LDAPException | LDIFException e) {
            log.error("Error adding test CRL: {}", dn, e);
            throw new RuntimeException("Failed to add test CRL", e);
        }
    }

    /**
     * Get all entries from directory matching base DN
     *
     * @param baseDn Base DN to search from
     * @return List of entries
     */
    public List<Entry> getAllEntries(String baseDn) {
        try {
            return directoryServer.search(baseDn, SearchScope.SUB, "(objectClass=*)").getSearchEntries();
        } catch (LDAPException e) {
            log.error("Error retrieving entries from {}", baseDn, e);
            return Collections.emptyList();
        }
    }

    /**
     * Search directory by filter
     *
     * @param baseDn Base DN
     * @param filter LDAP filter
     * @return List of matching entries
     */
    public List<Entry> searchEntries(String baseDn, String filter) {
        try {
            return directoryServer.search(baseDn, SearchScope.SUB, filter).getSearchEntries();
        } catch (LDAPException e) {
            log.error("Error searching directory: baseDn={}, filter={}", baseDn, filter, e);
            return Collections.emptyList();
        }
    }

    /**
     * Get entry by DN
     *
     * @param dn Distinguished Name
     * @return Entry or null if not found
     */
    public Entry getEntry(String dn) {
        try {
            return directoryServer.getEntry(dn);
        } catch (LDAPException e) {
            log.debug("Entry not found: {}", dn);
            return null;
        }
    }

    /**
     * Check if LDAP server is running
     *
     * @return true if running
     */
    public boolean isRunning() {
        return directoryServer != null && started;
    }

    /**
     * Clear all test data from directory
     */
    public void clear() {
        try {
            List<Entry> entries = getAllEntries(BASE_DN);
            for (Entry entry : entries) {
                String dn = entry.getDN();
                if (!dn.equals(BASE_DN) && !dn.equals(ADMIN_DN)) {
                    try {
                        directoryServer.delete(dn);
                    } catch (LDAPException e) {
                        log.debug("Error deleting entry: {}", dn);
                    }
                }
            }
            log.info("Cleared all test data from LDAP directory");
        } catch (Exception e) {
            log.error("Error clearing test data", e);
        }
    }

    /**
     * Get directory server statistics
     *
     * @return Statistics map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("running", isRunning());
        stats.put("port", port);
        stats.put("baseDn", BASE_DN);

        try {
            stats.put("entryCount", getAllEntries(BASE_DN).size());
        } catch (Exception e) {
            stats.put("entryCount", "unknown");
        }

        return stats;
    }
}
