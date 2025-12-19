package com.smartcoreinc.localpkd.passiveauthentication.infrastructure.adapter;

import com.smartcoreinc.localpkd.passiveauthentication.domain.port.LdapCscaRepository;
import com.smartcoreinc.localpkd.shared.exception.InfrastructureException;
import com.unboundid.ldap.sdk.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * UnboundIdLdapCscaAdapter - LDAP 기반 CSCA 조회 어댑터
 *
 * <p>ICAO 9303 Part 11 Passive Authentication에서 요구하는
 * CSCA 인증서를 OpenLDAP에서 조회합니다.</p>
 *
 * <h3>LDAP 검색 조건</h3>
 * <ul>
 *   <li>Base DN: dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com</li>
 *   <li>Filter: (&(objectClass=pkdDownload)(o=csca)(cn={escaped-dn}))</li>
 *   <li>Attribute: userCertificate;binary</li>
 * </ul>
 *
 * <h3>DN Escaping (RFC 4514)</h3>
 * <pre>
 * Original DN: CN=CSCA003,OU=MOFA,O=Government,C=KR
 * Escaped DN:  CN\3DCSCA003\2COU\3DMOFA\2CO\3DGovernment\2CC\3DKR
 *
 * Escape Rules:
 * - , (comma)  → \\2C
 * - = (equals) → \\3D
 * - + (plus)   → \\2B
 * - " (quote)  → \\22
 * - \ (backslash) → \\5C
 * - < (less than) → \\3C
 * - > (greater than) → \\3E
 * - ; (semicolon) → \\3B
 * </pre>
 *
 * <h3>Architecture Decision</h3>
 * <p>PA Module은 CSCA 조회 시 LDAP만 사용하고 DBMS는 사용하지 않습니다.
 * DBMS의 certificate 테이블은 PKD Upload Module의 업로드 이력/통계 용도입니다.</p>
 *
 * @see LdapCscaRepository
 * @since Phase 4.11.4
 */
@Slf4j
@Component
public class UnboundIdLdapCscaAdapter implements LdapCscaRepository {

    @Value("${app.ldap.urls}")
    private String ldapUrl;

    @Value("${app.ldap.base}")
    private String baseDn;

    @Value("${app.ldap.username}")
    private String bindDn;

    @Value("${app.ldap.password}")
    private String bindPassword;

    private LDAPConnectionPool connectionPool;

    /**
     * LDAP PKD Base DN (CSCA 검색용)
     */
    private static final String PKD_BASE_DN = "dc=data,dc=download,dc=pkd";

    /**
     * LDAP 연결 수립 (Connection Pool 생성)
     */
    @PostConstruct
    public void connect() throws LDAPException {
        log.info("=== LDAP CSCA Repository Connection started ===");
        log.info("LDAP URL: {}", ldapUrl);
        log.info("Bind DN: {}", bindDn);
        log.info("Base DN: {}", baseDn);

        if (baseDn == null || baseDn.isBlank()) {
            throw new IllegalStateException("LDAP Base DN is not configured");
        }

        if (connectionPool != null && !connectionPool.isClosed()) {
            log.warn("LDAP Connection Pool is already active");
            return;
        }

        try {
            // LDAP URL 파싱
            String host = ldapUrl.replace("ldap://", "").replace("ldaps://", "");
            String[] parts = host.split(":");
            String hostname = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 389;

            // LDAP 연결 생성
            LDAPConnection connection = new LDAPConnection(hostname, port, bindDn, bindPassword);

            // Connection Pool 생성 (초기 3개, 최대 10개)
            connectionPool = new LDAPConnectionPool(connection, 3, 10);

            log.info("LDAP CSCA Repository Connection Pool created: {} initial, {} max", 3, 10);

        } catch (LDAPException e) {
            log.error("LDAP connection failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * LDAP 연결 해제
     */
    @PreDestroy
    public void disconnect() {
        log.info("=== LDAP CSCA Repository Disconnection started ===");

        if (connectionPool != null) {
            connectionPool.close();
            log.info("LDAP Connection Pool closed");
        }
    }

    /**
     * Subject DN으로 CSCA 인증서를 조회합니다.
     *
     * <p>검색 프로세스:</p>
     * <ol>
     *   <li>DN을 RFC 4514 형식으로 escape</li>
     *   <li>LDAP 검색 필터 생성: (&(objectClass=pkdDownload)(o=csca)(cn={escaped-dn}))</li>
     *   <li>userCertificate;binary 속성 조회</li>
     *   <li>X.509 인증서 파싱</li>
     * </ol>
     *
     * @param subjectDn CSCA Subject DN (예: "CN=CSCA003,OU=MOFA,O=Government,C=KR")
     * @return CSCA X.509 인증서 (존재하지 않으면 Optional.empty())
     */
    @Override
    public Optional<X509Certificate> findBySubjectDn(String subjectDn) {
        log.debug("Looking up CSCA from LDAP with DN: {}", subjectDn);

        try {
            // Extract country code from DN (e.g., "CN=CSCA003,OU=MOFA,O=Government,C=KR" → "KR")
            String countryCode = extractCountryCode(subjectDn);
            log.debug("Extracted country code: {}", countryCode);

            // DN Escape (RFC 4514)
            String escapedDn = escapeDn(subjectDn);
            log.debug("Escaped DN: {}", escapedDn);

            // LDAP 검색 필터 (o=csca 제거 - DIT 노드이므로 Base DN에 포함)
            String filter = String.format("(&(objectClass=pkdDownload)(cn=%s))", escapedDn);
            log.debug("LDAP filter: {}", filter);

            // Search Base DN 구성: o=csca,c={country}를 Base DN에 포함
            // 이렇게 하면 o=csca 노드 아래에서만 검색하게 됨
            String searchBaseDn = "o=csca,c=" + countryCode + "," + PKD_BASE_DN + "," + baseDn;
            log.debug("Search base DN: {}", searchBaseDn);

            // LDAP 검색
            SearchRequest searchRequest = new SearchRequest(
                searchBaseDn,
                SearchScope.SUB,
                filter,
                "userCertificate;binary"
            );

            LDAPConnection connection = connectionPool.getConnection();
            try {
                SearchResult searchResult = connection.search(searchRequest);

                if (searchResult.getEntryCount() == 0) {
                    log.debug("CSCA not found in LDAP: {}", subjectDn);
                    return Optional.empty();
                }

                if (searchResult.getEntryCount() > 1) {
                    log.warn("Multiple CSCAs found for DN: {} (count: {})", subjectDn, searchResult.getEntryCount());
                }

                // 첫 번째 엔트리 사용
                SearchResultEntry entry = searchResult.getSearchEntries().get(0);
                log.debug("Found LDAP entry: {}", entry.getDN());

                // userCertificate;binary 속성 추출
                byte[][] certBytes = entry.getAttributeValueByteArrays("userCertificate;binary");
                if (certBytes == null || certBytes.length == 0) {
                    log.warn("No userCertificate;binary attribute found in entry: {}", entry.getDN());
                    return Optional.empty();
                }

                // X.509 인증서 파싱
                X509Certificate certificate = parseCertificate(certBytes[0]);
                log.info("CSCA retrieved successfully from LDAP: {}", certificate.getSubjectX500Principal());

                return Optional.of(certificate);

            } finally {
                connectionPool.releaseConnection(connection);
            }

        } catch (LDAPException e) {
            log.error("LDAP search failed for DN: {}", subjectDn, e);
            throw new InfrastructureException("LDAP_SEARCH_ERROR",
                "Failed to search CSCA in LDAP: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during CSCA lookup: {}", subjectDn, e);
            throw new InfrastructureException("CSCA_LOOKUP_ERROR",
                "Failed to retrieve CSCA certificate: " + e.getMessage(), e);
        }
    }

    /**
     * Extract country code from DN string.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"CN=CSCA003,OU=MOFA,O=Government,C=KR" → "KR"</li>
     *   <li>"C=KR,O=Government,CN=CSCA" → "KR"</li>
     * </ul>
     *
     * @param dn Distinguished Name
     * @return Country code (ISO 3166-1 alpha-2)
     * @throws InfrastructureException if country code not found in DN
     */
    private String extractCountryCode(String dn) {
        if (dn == null || dn.isEmpty()) {
            throw new InfrastructureException("INVALID_DN",
                "DN is null or empty");
        }

        // Match C=XX pattern (case-insensitive)
        String[] parts = dn.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.toUpperCase().startsWith("C=")) {
                String countryCode = trimmed.substring(2).trim();
                if (countryCode.length() == 2) {
                    return countryCode.toUpperCase();
                }
            }
        }

        throw new InfrastructureException("COUNTRY_CODE_NOT_FOUND",
            String.format("Country code not found in DN: %s", dn));
    }

    /**
     * LDAP 필터 값을 RFC 4515 형식으로 escape합니다.
     *
     * <p><b>중요:</b> DN escape (RFC 4514)와 Filter escape (RFC 4515)는 다릅니다!</p>
     *
     * <p>RFC 4515 (LDAP Search Filter) Escape 규칙:</p>
     * <ul>
     *   <li>* (asterisk) → \2a</li>
     *   <li>( (left paren) → \28</li>
     *   <li>) (right paren) → \29</li>
     *   <li>\ (backslash) → \5c</li>
     *   <li>NUL → \00</li>
     * </ul>
     *
     * <p><b>주의:</b> 쉼표(,)와 등호(=)는 필터에서 escape 불필요!</p>
     * <p>DN에서는 escape 필요하지만, 필터 값에서는 그대로 사용합니다.</p>
     *
     * @param value 원본 필터 값 (예: "CN=CSCA003,OU=MOFA,O=Government,C=KR")
     * @return RFC 4515 escape된 필터 값 (쉼표, 등호는 그대로 유지)
     */
    private String escapeDn(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // RFC 4515 - LDAP Filter escape only
        // Note: Comma (,) and equals (=) do NOT need escaping in filter values!
        return value
            .replace("\\", "\\5c")  // Backslash (must be first!)
            .replace("*", "\\2a")   // Asterisk
            .replace("(", "\\28")   // Left parenthesis
            .replace(")", "\\29")   // Right parenthesis
            .replace("\0", "\\00"); // NUL character
    }

    /**
     * X.509 인증서 파싱
     *
     * @param certBytes DER 인코딩된 인증서 바이트
     * @return X.509 인증서
     */
    private X509Certificate parseCertificate(byte[] certBytes) {
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) certFactory.generateCertificate(
                new ByteArrayInputStream(certBytes)
            );
        } catch (Exception e) {
            log.error("Failed to parse X.509 certificate", e);
            throw new InfrastructureException("CERT_PARSE_ERROR",
                "Failed to parse X.509 certificate: " + e.getMessage(), e);
        }
    }
}
