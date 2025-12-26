package com.smartcoreinc.localpkd.passiveauthentication.infrastructure.adapter;

import com.smartcoreinc.localpkd.ldapintegration.infrastructure.config.LdapProperties;
import com.smartcoreinc.localpkd.passiveauthentication.domain.port.CrlLdapPort;
import com.smartcoreinc.localpkd.shared.exception.InfrastructureException;
import com.unboundid.ldap.sdk.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class UnboundIdCrlLdapAdapter implements CrlLdapPort {

    private final LdapProperties ldapProperties;

    private LDAPConnectionPool connectionPool;

    /**
     * LDAP PKD Base DN (CRL 검색용)
     */
    private static final String PKD_BASE_DN = "dc=data,dc=download,dc=pkd";

    /**
     * LDAP Read 연결 수립 (Connection Pool 생성)
     *
     * <p>Read/Write 분리가 활성화된 경우:</p>
     * <ul>
     *   <li>Read URL: HAProxy 로드밸런싱 (app.ldap.read.url)</li>
     *   <li>용도: PA 검증 시 CRL 조회</li>
     * </ul>
     */
    @PostConstruct
    public void connect() throws LDAPException {
        String readUrl = ldapProperties.getReadUrl();
        String baseDn = ldapProperties.getBase();
        String bindDn = ldapProperties.getUsername();
        String bindPassword = ldapProperties.getPassword();

        log.info("=== LDAP CRL Repository Read Connection started ===");
        log.info("Read URL: {} (R/W Separation: {})", readUrl, ldapProperties.isReadWriteSeparationEnabled());
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
            String host = readUrl.replace("ldap://", "").replace("ldaps://", "");
            String[] parts = host.split(":");
            String hostname = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 389;

            // LDAP 연결 생성
            LDAPConnection connection = new LDAPConnection(hostname, port, bindDn, bindPassword);

            // Connection Pool 생성 (LdapProperties에서 설정값 가져오기)
            int initialSize = ldapProperties.getReadPoolInitialSize();
            int maxSize = ldapProperties.getReadPoolMaxSize();
            connectionPool = new LDAPConnectionPool(connection, initialSize, maxSize);

            log.info("LDAP CRL Repository Read Connection Pool created: {} initial, {} max", initialSize, maxSize);

        } catch (LDAPException e) {
            log.error("LDAP Read connection failed: {}", e.getMessage(), e);
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
            String searchBaseDn = String.format("o=crl,c=%s,%s,%s", countryCode, PKD_BASE_DN, ldapProperties.getBase());
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
     * <p><b>중요:</b> LDAP에 저장된 CRL의 cn 값이 DN escaped 형식으로 저장되어 있습니다.</p>
     * <p>예: "CN=CSCA-KOREA,O=Government,C=KR" → "CN\3DCSCA-KOREA\2CO\3DGovernment\2CC\3DKR"</p>
     *
     * <p>이 메서드는 두 단계 escape를 수행합니다:</p>
     * <ol>
     *   <li>DN escape: = → \3D, , → \2C (LDAP cn 값 매칭용)</li>
     *   <li>RFC 4515 Filter escape: *, (, ), \, NUL</li>
     * </ol>
     *
     * @param value 원본 필터 값 (예: "CN=CSCA-KOREA,O=Government,C=KR")
     * @return LDAP cn 값과 매칭되는 escape된 필터 값
     */
    private String escapeFilterValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // LDAP에 저장된 CRL cn 값이 DN escaped 형식: CN\3DCSCA-KOREA\2COU\3DMOFA...
        // RFC 4515 필터에서 이를 매칭하려면 백슬래시를 \5c로 escape 해야 함
        //
        // 예: CN=CSCA-KOREA,OU=MOFA,O=Government,C=KR
        //  → CN\5c3DCSCA-KOREA\5c2COU\5c3DMOFA\5c2CO\5c3DGovernment\5c2CC\5c3DKR (필터용)
        //
        // 이 필터가 LDAP의 cn=CN\3DCSCA-KOREA\2COU\3DMOFA... 값과 매칭됨

        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '=' -> escaped.append("\\5c3D");  // = → \3D (LDAP) → \5c3D (filter)
                case ',' -> escaped.append("\\5c2C");  // , → \2C (LDAP) → \5c2C (filter)
                case '*' -> escaped.append("\\2a");
                case '(' -> escaped.append("\\28");
                case ')' -> escaped.append("\\29");
                case '\\' -> escaped.append("\\5c");
                case '\0' -> escaped.append("\\00");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
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
