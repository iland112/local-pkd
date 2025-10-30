package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.adapter;

import com.smartcoreinc.localpkd.certificatevalidation.domain.exception.LdapConnectionException;
import com.smartcoreinc.localpkd.certificatevalidation.domain.exception.LdapOperationException;
import com.smartcoreinc.localpkd.certificatevalidation.domain.port.LdapConnectionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.stereotype.Component;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchControls;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

/**
 * SpringLdapConnectionAdapter - Spring LDAP 기반 LDAP 연결 어댑터
 *
 * <p><b>목적</b>: LdapConnectionPort 인터페이스를 Spring LDAP을 이용해 구현합니다.</p>
 *
 * <p><b>설계 패턴</b>: Adapter Pattern (Hexagonal Architecture)
 * <ul>
 *   <li>Port: Domain Layer의 LdapConnectionPort 인터페이스</li>
 *   <li>Adapter: Infrastructure Layer의 이 클래스 (Spring LDAP 구현체)</li>
 *   <li>장점: Domain이 Spring LDAP에 의존하지 않음</li>
 * </ul>
 * </p>
 *
 * <p><b>Spring LDAP 설정</b>:
 * <pre>
 * # application.properties
 * spring.ldap.urls=ldap://192.168.100.10:389
 * spring.ldap.base=dc=ldap,dc=smartcoreinc,dc=com
 * spring.ldap.username=cn=admin,dc=ldap,dc=smartcoreinc,dc=com
 * spring.ldap.password=admin_password
 *
 * # Optional: SSL/TLS
 * app.ldap.ssl.enabled=false
 * app.ldap.connection.timeout=5000
 * app.ldap.read.timeout=10000
 * </pre>
 * </p>
 *
 * <p><b>LDAP Entry 스키마</b>:
 * <pre>
 * # 인증서 엔트리
 * dn: cn=Test Certificate,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com
 * objectClass: pkiCertificate
 * cn: Test Certificate
 * serialNumber: 1234567890ABCDEF
 * certificateContent: {binary_cert_data}
 * issuer: CN=CSCA-Test
 * validFrom: 20240101000000Z
 * validTo: 20250101000000Z
 * fingerprint: A1B2C3D4...
 * uploadedAt: {timestamp}
 *
 * # CRL 엔트리
 * dn: cn=CSCA-Test-001,ou=crl,dc=ldap,dc=smartcoreinc,dc=com
 * objectClass: pkiCRL
 * cn: CSCA-Test-001
 * issuer: CN=CSCA-Test
 * crlContent: {binary_crl_data}
 * thisUpdate: 20240101000000Z
 * nextUpdate: 20240131000000Z
 * revokedCount: 5
 * uploadedAt: {timestamp}
 * </pre>
 * </p>
 *
 * <p><b>Phase 17</b>: Spring LDAP adapter for LDAP Integration foundation</p>
 *
 * @see LdapConnectionPort
 * @see LdapTemplate
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24 (Phase 17 Task 1.5)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.ldap.urls", matchIfMissing = false)
@RequiredArgsConstructor
public class SpringLdapConnectionAdapter implements LdapConnectionPort {

    private final LdapTemplate ldapTemplate;
    private final LdapContextSource contextSource;

    @Value("${spring.ldap.base}")
    private String baseDn;

    @Value("${app.ldap.connection.timeout:5000}")
    private int connectionTimeout;

    @Value("${app.ldap.read.timeout:10000}")
    private int readTimeout;

    private volatile boolean connected = false;

    @Override
    public void connect() throws LdapConnectionException {
        log.debug("=== LDAP Connection started ===");

        try {
            // Test connection
            ldapTemplate.search(
                LdapNameBuilder.newInstance(baseDn).build(),
                "(objectClass=*)",
                new SearchControls() {{
                    setSearchScope(SearchControls.OBJECT_SCOPE);
                    setTimeLimit(connectionTimeout);
                    setReturningObjFlag(false);
                }},
                (AttributesMapper<Void>) attributes -> null
            );

            connected = true;
            log.info("LDAP connected successfully: {}", getConnectionStatus());

        } catch (Exception e) {
            log.error("LDAP connection failed", e);
            connected = false;
            throw LdapConnectionException.connectionFailed(
                contextSource.getUrls()[0],
                e
            );
        }
    }

    @Override
    public void disconnect() throws LdapConnectionException {
        log.debug("=== LDAP disconnection started ===");

        try {
            // Spring LDAP's LdapTemplate handles connection lifecycle automatically
            // But we can invalidate the cached context source
            connected = false;
            log.info("LDAP disconnected");

        } catch (Exception e) {
            log.error("LDAP disconnection error", e);
            throw new LdapConnectionException("Failed to disconnect from LDAP", e);
        }
    }

    @Override
    public boolean isConnected() {
        if (!connected) {
            return false;
        }

        try {
            // Quick validation: test with a minimal search
            List<String> results = ldapTemplate.search(
                LdapNameBuilder.newInstance(baseDn).build(),
                "(objectClass=*)",
                new SearchControls() {{
                    setSearchScope(SearchControls.OBJECT_SCOPE);
                    setTimeLimit(1000);  // 1 second timeout
                    setReturningObjFlag(false);
                }},
                (AttributesMapper<String>) attrs -> "ok"
            );
            return true;

        } catch (Exception e) {
            log.warn("LDAP connection validation failed", e);
            connected = false;
            return false;
        }
    }

    @Override
    public String getConnectionStatus() {
        try {
            String url = String.join(", ", contextSource.getUrls());
            return String.format(
                "Connected to LDAP: %s, Base DN: %s, Status: %s",
                url,
                baseDn,
                connected ? "CONNECTED" : "DISCONNECTED"
            );
        } catch (Exception e) {
            return "Unknown connection status";
        }
    }

    @Override
    public String uploadCertificate(byte[] certificateDer, String subjectCn, String baseDn)
            throws LdapOperationException {
        log.debug("=== Certificate LDAP upload started ===");
        log.debug("Subject CN: {}, Base DN: {}", subjectCn, baseDn);

        try {
            validateConnected();
            validateInputs(certificateDer, subjectCn, baseDn);

            // Create DN for certificate entry
            String certificateDn = String.format(
                "cn=%s,ou=certificates,%s",
                escapeDnValue(subjectCn),
                baseDn
            );

            // Create LDAP attributes
            Attributes attributes = new BasicAttributes();
            attributes.put(new BasicAttribute("objectClass", "pkiCertificate"));
            attributes.put(new BasicAttribute("cn", subjectCn));
            attributes.put(new BasicAttribute("certificateContent", certificateDer));
            attributes.put(new BasicAttribute("uploadedAt", getCurrentTimestamp()));

            // Add to LDAP
            ldapTemplate.bind(certificateDn, null, attributes);

            log.info("Certificate uploaded to LDAP: {}", certificateDn);
            return certificateDn;

        } catch (IllegalStateException e) {
            throw new LdapOperationException(e.getMessage(), "uploadCertificate");
        } catch (Exception e) {
            log.error("Failed to upload certificate to LDAP", e);
            throw LdapOperationException.uploadCertificateFailed(subjectCn, e);
        }
    }

    @Override
    public String uploadCrl(byte[] crlDer, String issuerName, String baseDn)
            throws LdapOperationException {
        log.debug("=== CRL LDAP upload started ===");
        log.debug("Issuer: {}, Base DN: {}", issuerName, baseDn);

        try {
            validateConnected();
            validateInputs(crlDer, issuerName, baseDn);

            // Create DN for CRL entry
            String crlDn = String.format(
                "cn=%s,ou=crl,%s",
                escapeDnValue(issuerName),
                baseDn
            );

            // Create LDAP attributes
            Attributes attributes = new BasicAttributes();
            attributes.put(new BasicAttribute("objectClass", "pkiCRL"));
            attributes.put(new BasicAttribute("cn", issuerName));
            attributes.put(new BasicAttribute("issuer", issuerName));
            attributes.put(new BasicAttribute("crlContent", crlDer));
            attributes.put(new BasicAttribute("uploadedAt", getCurrentTimestamp()));

            // Add to LDAP
            ldapTemplate.bind(crlDn, null, attributes);

            log.info("CRL uploaded to LDAP: {}", crlDn);
            return crlDn;

        } catch (IllegalStateException e) {
            throw new LdapOperationException(e.getMessage(), "uploadCrl");
        } catch (Exception e) {
            log.error("Failed to upload CRL to LDAP", e);
            throw LdapOperationException.uploadCrlFailed(issuerName, e);
        }
    }

    @Override
    public Optional<LdapEntry> searchCertificate(String subjectCn, String baseDn)
            throws LdapOperationException {
        log.debug("=== Certificate LDAP search started ===");
        log.debug("Subject CN: {}", subjectCn);

        try {
            validateConnected();
            validateInputs(subjectCn, baseDn);

            String searchDn = String.format("ou=certificates,%s", baseDn);
            EqualsFilter filter = new EqualsFilter("cn", subjectCn);

            List<LdapEntry> results = ldapTemplate.search(
                searchDn,
                filter.encode(),
                (AttributesMapper<LdapEntry>) this::mapAttributesToEntry
            );

            if (results.isEmpty()) {
                log.debug("Certificate not found: {}", subjectCn);
                return Optional.empty();
            }

            log.debug("Certificate found: {}", subjectCn);
            return Optional.of(results.get(0));

        } catch (IllegalStateException e) {
            throw new LdapOperationException(e.getMessage(), "searchCertificate");
        } catch (Exception e) {
            log.error("Failed to search certificate in LDAP", e);
            throw LdapOperationException.searchCertificateFailed(subjectCn);
        }
    }

    @Override
    public List<LdapEntry> searchCrls(String issuerName, String baseDn)
            throws LdapOperationException {
        log.debug("=== CRL LDAP search started ===");
        log.debug("Issuer: {}", issuerName);

        try {
            validateConnected();
            validateInputs(issuerName, baseDn);

            String searchDn = String.format("ou=crl,%s", baseDn);
            EqualsFilter filter = new EqualsFilter("issuer", issuerName);

            List<LdapEntry> results = ldapTemplate.search(
                searchDn,
                filter.encode(),
                (AttributesMapper<LdapEntry>) this::mapAttributesToEntry
            );

            log.debug("Found {} CRL(s) for issuer: {}", results.size(), issuerName);
            return results;

        } catch (IllegalStateException e) {
            throw new LdapOperationException(e.getMessage(), "searchCrls");
        } catch (Exception e) {
            log.error("Failed to search CRLs in LDAP", e);
            throw LdapOperationException.searchCrlFailed(issuerName);
        }
    }

    @Override
    public boolean deleteEntry(String dn) throws LdapOperationException {
        log.debug("=== LDAP entry deletion started ===");
        log.debug("DN: {}", dn);

        try {
            validateConnected();
            if (dn == null || dn.isBlank()) {
                throw new IllegalArgumentException("DN must not be null or blank");
            }

            ldapTemplate.unbind(dn);
            log.info("LDAP entry deleted: {}", dn);
            return true;

        } catch (IllegalStateException e) {
            throw new LdapOperationException(e.getMessage(), "deleteEntry", dn);
        } catch (Exception e) {
            log.error("Failed to delete LDAP entry", e);
            throw LdapOperationException.deleteEntryFailed(dn, e);
        }
    }

    @Override
    public void keepAlive(int timeoutSeconds) throws LdapConnectionException {
        log.debug("=== LDAP keep-alive started ===");

        try {
            if (!isConnected()) {
                throw new IllegalStateException("LDAP not connected");
            }

            // Send a keep-alive search to maintain connection
            ldapTemplate.search(
                LdapNameBuilder.newInstance(baseDn).build(),
                "(objectClass=*)",
                new SearchControls() {{
                    setSearchScope(SearchControls.OBJECT_SCOPE);
                    setTimeLimit(1000);
                    setReturningObjFlag(false);
                }},
                (AttributesMapper<Void>) attrs -> null
            );

            log.debug("LDAP keep-alive sent, timeout: {} seconds", timeoutSeconds);

        } catch (IllegalStateException e) {
            throw new LdapConnectionException(e.getMessage());
        } catch (Exception e) {
            log.warn("LDAP keep-alive failed", e);
            throw LdapConnectionException.connectionTimeout((long) timeoutSeconds * 1000);
        }
    }

    // ========== Helper Methods ==========

    private void validateConnected() throws IllegalStateException {
        if (!connected) {
            throw new IllegalStateException("LDAP connection not established. Call connect() first.");
        }
    }

    private void validateInputs(byte[] data, String name, String baseDn) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data must not be null or empty");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be null or blank");
        }
        if (baseDn == null || baseDn.isBlank()) {
            throw new IllegalArgumentException("Base DN must not be null or blank");
        }
    }

    private void validateInputs(String name, String baseDn) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be null or blank");
        }
        if (baseDn == null || baseDn.isBlank()) {
            throw new IllegalArgumentException("Base DN must not be null or blank");
        }
    }

    private String escapeDnValue(String value) {
        if (value == null) {
            return "";
        }
        // Escape special characters in DN values per RFC 4514
        return value
            .replace("\\", "\\5c")
            .replace(",", "\\2c")
            .replace("=", "\\3d")
            .replace("+", "\\2b")
            .replace("\"", "\\22")
            .replace("<", "\\3c")
            .replace(">", "\\3e")
            .replace(";", "\\3b");
    }

    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    private LdapEntry mapAttributesToEntry(Attributes attributes) throws NamingException {
        String dn = attributes.get("dn") != null ?
            attributes.get("dn").get().toString() : "";

        String cn = attributes.get("cn") != null ?
            attributes.get("cn").get().toString() : "";

        byte[] certificateContent = null;
        if (attributes.get("certificateContent") != null) {
            Object content = attributes.get("certificateContent").get();
            if (content instanceof byte[]) {
                certificateContent = (byte[]) content;
            }
        }

        byte[] crlContent = null;
        if (attributes.get("crlContent") != null) {
            Object content = attributes.get("crlContent").get();
            if (content instanceof byte[]) {
                crlContent = (byte[]) content;
            }
        }

        String uploadedAt = attributes.get("uploadedAt") != null ?
            attributes.get("uploadedAt").get().toString() : "";

        return new LdapEntry(dn, cn, certificateContent, crlContent, uploadedAt);
    }
}
