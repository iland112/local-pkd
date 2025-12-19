package com.smartcoreinc.localpkd.passiveauthentication.infrastructure.adapter;

import com.smartcoreinc.localpkd.passiveauthentication.domain.port.CrlLdapPort;
import com.smartcoreinc.localpkd.shared.exception.InfrastructureException;
import com.unboundid.ldap.sdk.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.util.Optional;

/**
 * UnboundIdCrlLdapAdapter - LDAP 기반 CRL 조회 어댑터
 *
 * <p>ICAO 9303 Part 12 PKI에서 요구하는 Certificate Revocation List (CRL)를
 * OpenLDAP PKD에서 조회합니다.</p>
 *
 * <h3>LDAP 검색 조건</h3>
 * <ul>
 *   <li>Base DN: o=crl,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}</li>
 *   <li>Filter: (&(objectClass=cRLDistributionPoint)(cn={escaped-dn}))</li>
 *   <li>Attribute: certificateRevocationList;binary</li>
 * </ul>
 *
 * <h3>RFC 4515 Filter Escaping</h3>
 * <pre>
 * LDAP Filter Escape 규칙 (RFC 4515):
 * - * (asterisk) → \2a
 * - ( (left paren) → \28
 * - ) (right paren) → \29
 * - \ (backslash) → \5c
 * - NUL → \00
 *
 * 주의: 쉼표(,)와 등호(=)는 필터에서 escape 불필요!
 * </pre>
 *
 * <h3>Architecture Decision</h3>
 * <p>PA Module은 CRL 조회 시 LDAP를 Primary로 사용하고,
 * Database는 Cache Layer로만 사용합니다 (CrlCacheService).</p>
 *
 * @see CrlLdapPort
 * @see com.smartcoreinc.localpkd.passiveauthentication.infrastructure.cache.CrlCacheService
 * @since Phase 4.12
 */
@Slf4j
@Component
public class UnboundIdCrlLdapAdapter implements CrlLdapPort {

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
     * LDAP PKD Base DN (CRL 검색용)
     */
    private static final String PKD_BASE_DN = "dc=data,dc=download,dc=pkd";

    /**
     * LDAP 연결 수립 (Connection Pool 생성)
     */
    @PostConstruct
    public void connect() throws LDAPException {
        log.info("=== LDAP CRL Repository Connection started ===");
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

            log.info("LDAP CRL Repository Connection Pool created: {} initial, {} max", 3, 10);

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
        log.info("=== LDAP CRL Repository Disconnection started ===");

        if (connectionPool != null) {
            connectionPool.close();
            log.info("LDAP Connection Pool closed");
        }
    }

    /**
     * CSCA Subject DN으로 CRL을 조회합니다.
     *
     * <p>검색 프로세스:</p>
     * <ol>
     *   <li>DN을 RFC 4515 형식으로 escape</li>
     *   <li>LDAP 검색 필터 생성: (&(objectClass=cRLDistributionPoint)(cn={escaped-dn}))</li>
     *   <li>certificateRevocationList;binary 속성 조회</li>
     *   <li>X.509 CRL 파싱</li>
     * </ol>
     *
     * @param cscaSubjectDn CSCA Subject DN (예: "CN=CSCA-KOREA,O=Government,C=KR")
     * @param countryCode ISO 3166-1 alpha-2 country code (예: "KR")
     * @return X.509 CRL (존재하지 않으면 Optional.empty())
     */
    @Override
    public Optional<X509CRL> findCrlByCsca(String cscaSubjectDn, String countryCode) {
        log.debug("Looking up CRL from LDAP for CSCA DN: {}, Country: {}", cscaSubjectDn, countryCode);

        try {
            // RFC 4515 Filter Escape
            String escapedDn = escapeFilterValue(cscaSubjectDn);
            log.debug("Escaped DN (RFC 4515): {}", escapedDn);

            // LDAP 검색 필터
            String filter = String.format("(&(objectClass=cRLDistributionPoint)(cn=%s))", escapedDn);
            log.debug("LDAP filter: {}", filter);

            // Search Base DN 구성: o=crl,c={country}를 Base DN에 포함
            // 이렇게 하면 o=crl 노드 아래에서만 검색하게 됨
            String searchBaseDn = String.format("o=crl,c=%s,%s,%s", countryCode, PKD_BASE_DN, baseDn);
            log.debug("Search base DN: {}", searchBaseDn);

            // LDAP 검색
            SearchRequest searchRequest = new SearchRequest(
                searchBaseDn,
                SearchScope.SUB,
                filter,
                "certificateRevocationList;binary"
            );

            LDAPConnection connection = connectionPool.getConnection();
            try {
                SearchResult searchResult = connection.search(searchRequest);

                if (searchResult.getEntryCount() == 0) {
                    log.debug("CRL not found in LDAP for CSCA: {}", cscaSubjectDn);
                    return Optional.empty();
                }

                if (searchResult.getEntryCount() > 1) {
                    log.warn("Multiple CRLs found for CSCA DN: {} (count: {})", cscaSubjectDn, searchResult.getEntryCount());
                }

                // 첫 번째 엔트리 사용 (가장 최신 CRL이어야 함)
                SearchResultEntry entry = searchResult.getSearchEntries().get(0);
                log.debug("Found LDAP entry: {}", entry.getDN());

                // certificateRevocationList;binary 속성 추출
                byte[][] crlBytes = entry.getAttributeValueByteArrays("certificateRevocationList;binary");
                if (crlBytes == null || crlBytes.length == 0) {
                    log.warn("No certificateRevocationList;binary attribute found in entry: {}", entry.getDN());
                    return Optional.empty();
                }

                // X.509 CRL 파싱
                X509CRL crl = parseCrl(crlBytes[0]);
                log.info("CRL retrieved successfully from LDAP. Issuer: {}, thisUpdate: {}, nextUpdate: {}",
                    crl.getIssuerX500Principal(), crl.getThisUpdate(), crl.getNextUpdate());

                return Optional.of(crl);

            } finally {
                connectionPool.releaseConnection(connection);
            }

        } catch (LDAPException e) {
            log.error("LDAP search failed for CSCA DN: {}", cscaSubjectDn, e);
            throw new InfrastructureException("LDAP_CRL_SEARCH_ERROR",
                "Failed to search CRL in LDAP: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during CRL lookup: {}", cscaSubjectDn, e);
            throw new InfrastructureException("CRL_LOOKUP_ERROR",
                "Failed to retrieve CRL: " + e.getMessage(), e);
        }
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
     * @param value 원본 필터 값 (예: "CN=CSCA-KOREA,O=Government,C=KR")
     * @return RFC 4515 escape된 필터 값 (쉼표, 등호는 그대로 유지)
     */
    private String escapeFilterValue(String value) {
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
     * X.509 CRL 파싱
     *
     * @param crlBytes DER 인코딩된 CRL 바이트
     * @return X.509 CRL
     * @throws InfrastructureException if CRL parsing fails
     */
    private X509CRL parseCrl(byte[] crlBytes) {
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            return (X509CRL) certFactory.generateCRL(new ByteArrayInputStream(crlBytes));
        } catch (Exception e) {
            log.error("Failed to parse X.509 CRL", e);
            throw new InfrastructureException("CRL_PARSE_ERROR",
                "Failed to parse X.509 CRL: " + e.getMessage(), e);
        }
    }
}
