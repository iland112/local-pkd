package com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter;

import com.smartcoreinc.localpkd.ldapintegration.domain.model.DistinguishedName;
import com.smartcoreinc.localpkd.ldapintegration.domain.model.LdapCertificateEntry;
import com.smartcoreinc.localpkd.ldapintegration.domain.model.LdapCrlEntry;
import com.smartcoreinc.localpkd.ldapintegration.domain.model.LdapSearchFilter;
import com.smartcoreinc.localpkd.ldapintegration.domain.port.LdapQueryService;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.stereotype.Component;

import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.ldap.LdapName;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SpringLdapQueryAdapter - Spring LDAP 기반 LdapQueryService 구현체
 *
 * <p><b>Hexagonal Architecture Adapter</b>:</p>
 * <ul>
 *   <li>LdapQueryService 포트의 구현체</li>
 *   <li>Spring LDAP을 이용한 LDAP 검색 기능</li>
 *   <li>페이징, 검색, 통계 등의 기능 제공</li>
 * </ul>
 *
 * <h3>책임</h3>
 * <ul>
 *   <li>Spring LDAP Template를 이용한 LDAP 검색</li>
 *   <li>인증서 및 CRL 엔트리 조회</li>
 *   <li>다양한 검색 조건 지원 (CN, Country Code, Issuer DN 등)</li>
 *   <li>페이징 검색 구현</li>
 *   <li>LDAP 서버 통계 정보 조회</li>
 * </ul>
 *
 * <h3>구현 방식</h3>
 * <ul>
 *   <li>Stub implementation with TODO markers</li>
 *   <li>Comprehensive logging for debugging</li>
 *   <li>Exception handling and translation to domain exceptions</li>
 *   <li>In-memory pagination for current implementation</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringLdapQueryAdapter implements LdapQueryService {

    private final LdapTemplate ldapTemplate;

    // ======================== Certificate Entry Methods ========================

    @Override
    public Optional<LdapCertificateEntry> findCertificateByDn(DistinguishedName dn) {
        log.info("=== Finding certificate by DN started ===");
        log.debug("DN: {}", dn.getValue());

        long startTime = System.currentTimeMillis();

        try {
            // Validate input
            if (dn == null || dn.getValue() == null || dn.getValue().isBlank()) {
                throw new IllegalArgumentException("DN must not be null or blank");
            }

            // 1. Create LDAP DN
            LdapName ldapDn = new LdapName(dn.getValue());
            log.debug("Created LDAP DN: {}", ldapDn);

            // 2. Lookup the entry in LDAP
            DirContextAdapter context = (DirContextAdapter) ldapTemplate.lookup(ldapDn);
            log.debug("Found LDAP entry: {}", ldapDn);

            // 3. Extract certificate data from attributes
            LdapCertificateEntry entry = extractCertificateFromContext(context, dn);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Certificate found successfully. Duration: {}ms", duration);

            return Optional.of(entry);

        } catch (org.springframework.ldap.NameNotFoundException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.debug("Certificate entry not found: {}. Duration: {}ms", dn.getValue(), duration);
            return Optional.empty();

        } catch (Exception e) {
            log.error("Certificate lookup failed: {}", dn.getValue(), e);
            throw new LdapQueryService.LdapQueryException(
                "Certificate lookup failed: " + e.getMessage(), e);
        }
    }

    // ======================== CRL Entry Methods ========================

    @Override
    public Optional<LdapCrlEntry> findCrlByDn(DistinguishedName dn) {
        log.info("=== Finding CRL by DN started ===");
        log.debug("DN: {}", dn.getValue());

        long startTime = System.currentTimeMillis();

        try {
            // Validate input
            if (dn == null || dn.getValue() == null || dn.getValue().isBlank()) {
                throw new IllegalArgumentException("DN must not be null or blank");
            }

            // 1. Create LDAP DN
            LdapName ldapDn = new LdapName(dn.getValue());
            log.debug("Created LDAP DN: {}", ldapDn);

            // 2. Lookup the entry in LDAP
            DirContextAdapter context = (DirContextAdapter) ldapTemplate.lookup(ldapDn);
            log.debug("Found LDAP CRL entry: {}", ldapDn);

            // 3. Extract CRL data from attributes
            LdapCrlEntry entry = extractCrlFromContext(context, dn);

            long duration = System.currentTimeMillis() - startTime;
            log.info("CRL found successfully. Duration: {}ms", duration);

            return Optional.of(entry);

        } catch (org.springframework.ldap.NameNotFoundException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.debug("CRL entry not found: {}. Duration: {}ms", dn.getValue(), duration);
            return Optional.empty();

        } catch (Exception e) {
            log.error("CRL lookup failed: {}", dn.getValue(), e);
            throw new LdapQueryService.LdapQueryException(
                "CRL lookup failed: " + e.getMessage(), e);
        }
    }

    // ======================== Certificate Search Methods ========================

    @Override
    public List<LdapCertificateEntry> searchCertificatesByCommonName(String cn) {
        log.info("=== Searching certificates by CN started ===");
        log.debug("CN: {}", cn);

        long startTime = System.currentTimeMillis();

        try {
            if (cn == null || cn.isBlank()) {
                throw new DomainException("INVALID_CN", "Common Name must not be null or blank");
            }

            // 1. Create search filter for certificates by CN
            // Note: We need a base DN - use default certificate base
            DistinguishedName baseDn = DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
            LdapSearchFilter filter = LdapSearchFilter.forCertificateWithCn(baseDn, cn);
            log.debug("Created search filter: {}", filter.getFilterString());

            // 2. Execute search
            List<LdapCertificateEntry> results = searchCertificatesInternal(filter);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Certificate search completed. Found: {}, Duration: {}ms", results.size(), duration);

            return results;

        } catch (DomainException e) {
            throw new LdapQueryService.LdapQueryException(e.getMessage(), e);
        } catch (LdapQueryService.LdapQueryException e) {
            throw e;
        } catch (Exception e) {
            log.error("Certificate search by CN failed", e);
            throw new LdapQueryService.LdapQueryException(
                "Certificate search failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<LdapCertificateEntry> searchCertificatesByCountry(String countryCode) {
        log.info("=== Searching certificates by country started ===");
        log.debug("Country code: {}", countryCode);

        long startTime = System.currentTimeMillis();

        try {
            if (countryCode == null || countryCode.length() != 2) {
                throw new DomainException(
                    "INVALID_COUNTRY_CODE",
                    "Country code must be exactly 2 characters"
                );
            }

            // 1. Create search filter for certificates by country
            DistinguishedName baseDn = DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
            LdapSearchFilter filter = LdapSearchFilter.forCertificateWithCountry(baseDn, countryCode);
            log.debug("Created search filter: {}", filter.getFilterString());

            // 2. Execute search
            List<LdapCertificateEntry> results = searchCertificatesInternal(filter);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Certificate search by country completed. Found: {}, Duration: {}ms",
                results.size(), duration);

            return results;

        } catch (DomainException e) {
            throw new LdapQueryService.LdapQueryException(e.getMessage(), e);
        } catch (LdapQueryService.LdapQueryException e) {
            throw e;
        } catch (Exception e) {
            log.error("Certificate search by country failed", e);
            throw new LdapQueryService.LdapQueryException(
                "Certificate search failed: " + e.getMessage(), e);
        }
    }

    // ======================== CRL Search Methods ========================

    @Override
    public List<LdapCrlEntry> searchCrlsByIssuerDn(String issuerDn) {
        log.info("=== Searching CRLs by issuer DN started ===");
        log.debug("Issuer DN: {}", issuerDn);

        long startTime = System.currentTimeMillis();

        try {
            if (issuerDn == null || issuerDn.isBlank()) {
                throw new DomainException(
                    "INVALID_ISSUER_DN",
                    "Issuer DN must not be null or blank"
                );
            }

            // 1. Create search filter for CRLs by issuer
            DistinguishedName baseDn = DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
            LdapSearchFilter filter = LdapSearchFilter.forCrlWithIssuer(baseDn, issuerDn);
            log.debug("Created search filter: {}", filter.getFilterString());

            // 2. Execute search
            List<LdapCrlEntry> results = searchCrlsInternal(filter);

            long duration = System.currentTimeMillis() - startTime;
            log.info("CRL search by issuer completed. Found: {}, Duration: {}ms", results.size(), duration);

            return results;

        } catch (DomainException e) {
            throw new LdapQueryService.LdapQueryException(e.getMessage(), e);
        } catch (LdapQueryService.LdapQueryException e) {
            throw e;
        } catch (Exception e) {
            log.error("CRL search by issuer DN failed", e);
            throw new LdapQueryService.LdapQueryException(
                "CRL search failed: " + e.getMessage(), e);
        }
    }

    // ======================== Generic Search Methods ========================

    @Override
    public List<Map<String, Object>> search(LdapSearchFilter filter) {
        log.info("=== LDAP search started ===");
        log.debug("Filter: {}", filter);

        try {
            if (filter == null) {
                throw new DomainException("INVALID_FILTER", "Search filter must not be null");
            }

            // TODO: Implement actual search using LdapTemplate.search()
            // Use filter.getBaseDn(), filter.getFilterString(), filter.getScope(), etc.
            log.warn("Generic LDAP search stub implementation");
            log.debug("Base DN: {}", filter.getBaseDn().getValue());
            log.debug("Filter: {}", filter.getFilterString());
            log.debug("Scope: {}", filter.getScopeDescription());
            log.debug("Returning attributes: {}", filter.getReturningAttributes());

            return Collections.emptyList();

        } catch (LdapQueryException e) {
            throw e;
        } catch (Exception e) {
            log.error("LDAP search failed", e);
            throw new LdapQueryException("LDAP search failed: " + e.getMessage(), e);
        }
    }

    // ======================== Paginated Search Methods ========================

    @Override
    public QueryResult<Map<String, Object>> searchWithPagination(
            LdapSearchFilter filter, int pageIndex, int pageSize) {
        log.info("=== Paginated LDAP search started ===");
        log.debug("Filter: {}, Page: {}, Size: {}", filter, pageIndex, pageSize);

        try {
            if (filter == null) {
                throw new DomainException("INVALID_FILTER", "Search filter must not be null");
            }

            // TODO: Implement actual paginated search
            // For now, return empty result
            log.warn("Paginated LDAP search stub implementation");

            List<Map<String, Object>> results = Collections.emptyList();
            return new QueryResultImpl<>(results, pageIndex, pageSize, 0L);

        } catch (LdapQueryException e) {
            throw e;
        } catch (Exception e) {
            log.error("Paginated LDAP search failed", e);
            throw new LdapQueryException("Paginated LDAP search failed: " + e.getMessage(), e);
        }
    }

    @Override
    public QueryResult<LdapCertificateEntry> searchCertificatesWithPagination(
            LdapSearchFilter filter, int pageIndex, int pageSize) {
        log.info("=== Paginated certificate search started ===");
        log.debug("Filter: {}, Page: {}, Size: {}", filter, pageIndex, pageSize);

        try {
            if (filter == null) {
                throw new DomainException("INVALID_FILTER", "Search filter must not be null");
            }

            // TODO: Implement actual paginated certificate search
            log.warn("Paginated certificate search stub implementation");

            List<LdapCertificateEntry> results = Collections.emptyList();
            return new QueryResultImpl<>(results, pageIndex, pageSize, 0L);

        } catch (LdapQueryException e) {
            throw e;
        } catch (Exception e) {
            log.error("Paginated certificate search failed", e);
            throw new LdapQueryException("Paginated certificate search failed: " + e.getMessage(), e);
        }
    }

    @Override
    public QueryResult<LdapCrlEntry> searchCrlsWithPagination(
            LdapSearchFilter filter, int pageIndex, int pageSize) {
        log.info("=== Paginated CRL search started ===");
        log.debug("Filter: {}, Page: {}, Size: {}", filter, pageIndex, pageSize);

        try {
            if (filter == null) {
                throw new DomainException("INVALID_FILTER", "Search filter must not be null");
            }

            // TODO: Implement actual paginated CRL search
            log.warn("Paginated CRL search stub implementation");

            List<LdapCrlEntry> results = Collections.emptyList();
            return new QueryResultImpl<>(results, pageIndex, pageSize, 0L);

        } catch (LdapQueryException e) {
            throw e;
        } catch (Exception e) {
            log.error("Paginated CRL search failed", e);
            throw new LdapQueryException("Paginated CRL search failed: " + e.getMessage(), e);
        }
    }

    // ======================== Count Methods ========================

    @Override
    public long countCertificates(DistinguishedName baseDn) {
        log.info("=== Counting certificates started ===");
        log.debug("Base DN: {}", baseDn.getValue());

        long startTime = System.currentTimeMillis();

        try {
            if (baseDn == null) {
                throw new DomainException("INVALID_BASE_DN", "Base DN must not be null");
            }

            // 1. Create count filter for all certificate entries
            String filter = "(objectClass=inetOrgPerson)";
            log.debug("Using filter: {}", filter);

            // 2. Execute search to count entries
            List<String> dns = new ArrayList<>();
            ldapTemplate.search(
                    baseDn.getValue(),
                    filter,
                    (AttributesMapper<Object>) attributes -> {
                        dns.add(baseDn.getValue());  // Just count, we don't need attributes
                        return null;
                    }
            );

            long count = dns.size();
            long duration = System.currentTimeMillis() - startTime;
            log.info("Certificate count completed: {} certificates. Duration: {}ms", count, duration);

            return count;

        } catch (DomainException e) {
            throw new LdapQueryService.LdapQueryException(e.getMessage(), e);
        } catch (org.springframework.ldap.NameNotFoundException e) {
            log.debug("Base DN not found: {}", baseDn.getValue());
            return 0L;
        } catch (LdapQueryService.LdapQueryException e) {
            throw e;
        } catch (Exception e) {
            log.error("Count certificates failed", e);
            throw new LdapQueryService.LdapQueryException(
                "Count certificates failed: " + e.getMessage(), e);
        }
    }

    @Override
    public long countCrls(DistinguishedName baseDn) {
        log.info("=== Counting CRLs started ===");
        log.debug("Base DN: {}", baseDn.getValue());

        long startTime = System.currentTimeMillis();

        try {
            if (baseDn == null) {
                throw new DomainException("INVALID_BASE_DN", "Base DN must not be null");
            }

            // 1. Create count filter for all CRL entries
            String filter = "(objectClass=x509crl)";
            log.debug("Using filter: {}", filter);

            // 2. Execute search to count CRL entries
            List<String> dns = new ArrayList<>();
            ldapTemplate.search(
                    baseDn.getValue(),
                    filter,
                    (AttributesMapper<Object>) attributes -> {
                        dns.add(baseDn.getValue());
                        return null;
                    }
            );

            long count = dns.size();
            long duration = System.currentTimeMillis() - startTime;
            log.info("CRL count completed: {} CRLs. Duration: {}ms", count, duration);

            return count;

        } catch (DomainException e) {
            throw new LdapQueryService.LdapQueryException(e.getMessage(), e);
        } catch (org.springframework.ldap.NameNotFoundException e) {
            log.debug("Base DN not found: {}", baseDn.getValue());
            return 0L;
        } catch (LdapQueryService.LdapQueryException e) {
            throw e;
        } catch (Exception e) {
            log.error("Count CRLs failed", e);
            throw new LdapQueryService.LdapQueryException(
                "Count CRLs failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Long> countCertificatesByCountry() {
        log.info("=== Counting certificates by country started ===");

        try {
            // TODO: Implement actual count grouping by country code
            log.warn("Count certificates by country stub implementation");

            return Collections.emptyMap();

        } catch (Exception e) {
            log.error("Count certificates by country failed", e);
            throw new LdapQueryException("Count certificates by country failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Long> countCrlsByIssuer() {
        log.info("=== Counting CRLs by issuer started ===");

        try {
            // TODO: Implement actual count grouping by issuer DN
            log.warn("Count CRLs by issuer stub implementation");

            return Collections.emptyMap();

        } catch (Exception e) {
            log.error("Count CRLs by issuer failed", e);
            throw new LdapQueryException("Count CRLs by issuer failed: " + e.getMessage(), e);
        }
    }

    // ======================== Directory Structure Methods ========================

    @Override
    public List<DistinguishedName> listSubordinateDns(DistinguishedName baseDn) {
        log.info("=== Listing subordinate DNs started ===");
        log.debug("Base DN: {}", baseDn.getValue());

        try {
            if (baseDn == null) {
                throw new DomainException("INVALID_BASE_DN", "Base DN must not be null");
            }

            // TODO: Implement actual list using ONE_LEVEL scope search
            log.warn("List subordinate DNs stub implementation");

            return Collections.emptyList();

        } catch (LdapQueryException e) {
            throw e;
        } catch (Exception e) {
            log.error("List subordinate DNs failed", e);
            throw new LdapQueryException("List subordinate DNs failed: " + e.getMessage(), e);
        }
    }

    // ======================== Connection Methods ========================

    @Override
    public boolean testConnection() {
        log.info("=== Testing LDAP connection ===");

        try {
            // 1. Attempt to lookup the root DSE (root DN)
            Object result = ldapTemplate.lookup("");
            log.info("LDAP connection test successful. Root DSE accessible.");
            return true;

        } catch (org.springframework.ldap.CommunicationException e) {
            log.error("LDAP connection failed: cannot communicate with server", e);
            return false;

        } catch (Exception e) {
            log.error("LDAP connection test failed", e);
            return false;
        }
    }

    @Override
    public String getServerInfo() {
        log.info("=== Getting LDAP server info ===");

        try {
            // TODO: Implement actual server info retrieval
            // Query rootDSE for version, supportedLdapVersion, supportedControl, etc.
            log.warn("Get LDAP server info stub implementation");

            return "Spring LDAP Server (Version 1.0, Stub Implementation)";

        } catch (Exception e) {
            log.error("Get server info failed", e);
            throw new LdapQueryException("Get server info failed: " + e.getMessage(), e);
        }
    }

    // ======================== Helper Methods ========================

    /**
     * DirContextAdapter에서 인증서 정보를 추출하여 LdapCertificateEntry로 변환
     *
     * @param context DirContextAdapter (LDAP 엔트리 데이터)
     * @param dn Distinguished Name
     * @return 변환된 LdapCertificateEntry
     */
    private LdapCertificateEntry extractCertificateFromContext(
            DirContextAdapter context, DistinguishedName dn) {

        log.debug("=== Extracting certificate data from LDAP context ===");
        log.debug("DN: {}", dn.getValue());

        try {
            // 1. 필수 속성 추출
            String cn = context.getStringAttribute("cn");
            String x509CertBase64 = context.getStringAttribute("x509certificatedata");
            String fingerprint = context.getStringAttribute("certificateFingerprint");
            String serialNumber = context.getStringAttribute("serialNumber");
            String issuerDn = context.getStringAttribute("issuerDN");

            // 2. 옵션 속성 추출
            String validationStatus = context.getStringAttribute("validationStatus");
            if (validationStatus == null || validationStatus.isBlank()) {
                validationStatus = "VALID";
            }

            String typeStr = context.getStringAttribute("certificateType");
            String notBeforeStr = context.getStringAttribute("notBefore");
            String notAfterStr = context.getStringAttribute("notAfter");

            log.debug("Extracted attributes - CN: {}, fingerprint: {}, serialNumber: {}",
                cn, fingerprint, serialNumber);

            // 3. LdapCertificateEntry 빌드
            return LdapCertificateEntry.builder()
                    .dn(dn)
                    .x509CertificateBase64(x509CertBase64)
                    .fingerprint(fingerprint)
                    .serialNumber(serialNumber)
                    .issuerDn(issuerDn)
                    .validationStatus(validationStatus)
                    .build();

        } catch (Exception e) {
            log.error("Failed to extract certificate from LDAP context: {}", dn.getValue(), e);
            throw new LdapQueryService.LdapQueryException(
                "Failed to extract certificate data: " + e.getMessage(), e);
        }
    }

    /**
     * DirContextAdapter에서 CRL 정보를 추출하여 LdapCrlEntry로 변환
     *
     * @param context DirContextAdapter (LDAP 엔트리 데이터)
     * @param dn Distinguished Name
     * @return 변환된 LdapCrlEntry
     */
    private LdapCrlEntry extractCrlFromContext(DirContextAdapter context, DistinguishedName dn) {

        log.debug("=== Extracting CRL data from LDAP context ===");
        log.debug("DN: {}", dn.getValue());

        try {
            // 1. 필수 속성 추출
            String cn = context.getStringAttribute("cn");
            String x509CrlBase64 = context.getStringAttribute("x509crldata");
            String issuerDn = context.getStringAttribute("issuerDN");

            // 2. 옵션 속성 추출
            String countryCode = context.getStringAttribute("countryCode");
            String thisUpdateStr = context.getStringAttribute("thisUpdate");
            String nextUpdateStr = context.getStringAttribute("nextUpdate");

            log.debug("Extracted attributes - CN: {}, issuerDn: {}, thisUpdate: {}",
                cn, issuerDn, thisUpdateStr);

            // 3. LdapCrlEntry 빌드
            return LdapCrlEntry.builder()
                    .dn(dn)
                    .x509CrlBase64(x509CrlBase64)
                    .issuerDn(issuerDn)
                    .countryCode(countryCode)
                    .build();

        } catch (Exception e) {
            log.error("Failed to extract CRL from LDAP context: {}", dn.getValue(), e);
            throw new LdapQueryService.LdapQueryException(
                "Failed to extract CRL data: " + e.getMessage(), e);
        }
    }

    /**
     * 검색 필터를 사용하여 인증서를 검색하고 List<LdapCertificateEntry>로 반환
     *
     * @param filter LdapSearchFilter (검색 조건)
     * @return 검색된 인증서 목록
     */
    private List<LdapCertificateEntry> searchCertificatesInternal(LdapSearchFilter filter) {

        log.debug("=== Internal certificate search started ===");
        log.debug("Filter: {}", filter.getFilterString());
        log.debug("Base DN: {}", filter.getBaseDn().getValue());

        try {
            List<LdapCertificateEntry> results = new ArrayList<>();

            // 1. LdapTemplate.search() 실행
            ldapTemplate.search(
                    filter.getBaseDn().getValue(),
                    filter.getFilterString(),
                    (AttributesMapper<Object>) attributes -> {
                        try {
                            // 2. Attributes에서 직접 값 추출
                            String dnValue = getAttribute(attributes, "entryDN");
                            if (dnValue == null) {
                                dnValue = filter.getBaseDn().getValue();
                            }
                            DistinguishedName entryDn = DistinguishedName.of(dnValue);

                            // 3. Attributes에서 인증서 데이터 추출
                            String x509CertBase64 = getAttribute(attributes, "x509certificatedata");
                            String fingerprint = getAttribute(attributes, "certificateFingerprint");
                            String serialNumber = getAttribute(attributes, "serialNumber");
                            String issuerDn = getAttribute(attributes, "issuerDN");
                            String validationStatus = getAttribute(attributes, "validationStatus");
                            if (validationStatus == null || validationStatus.isBlank()) {
                                validationStatus = "VALID";
                            }

                            // 4. LdapCertificateEntry 빌드 및 결과에 추가
                            LdapCertificateEntry entry = LdapCertificateEntry.builder()
                                    .dn(entryDn)
                                    .x509CertificateBase64(x509CertBase64)
                                    .fingerprint(fingerprint)
                                    .serialNumber(serialNumber)
                                    .issuerDn(issuerDn)
                                    .validationStatus(validationStatus)
                                    .build();
                            results.add(entry);
                            log.debug("Certificate extracted from DN: {}", dnValue);

                        } catch (Exception e) {
                            log.warn("Failed to extract certificate from search result", e);
                            // 한 개 엔트리 실패 시 계속 진행 (graceful degradation)
                        }

                        return null;
                    }
            );

            log.debug("Certificate search completed. Found: {} certificates", results.size());
            return results;

        } catch (org.springframework.ldap.NameNotFoundException e) {
            log.debug("Base DN not found in LDAP: {}", filter.getBaseDn().getValue());
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("Certificate search failed: {}", filter.getFilterString(), e);
            throw new LdapQueryService.LdapQueryException(
                "Certificate search failed: " + e.getMessage(), e);
        }
    }

    /**
     * 검색 필터를 사용하여 CRL을 검색하고 List<LdapCrlEntry>로 반환
     *
     * @param filter LdapSearchFilter (검색 조건)
     * @return 검색된 CRL 목록
     */
    private List<LdapCrlEntry> searchCrlsInternal(LdapSearchFilter filter) {

        log.debug("=== Internal CRL search started ===");
        log.debug("Filter: {}", filter.getFilterString());
        log.debug("Base DN: {}", filter.getBaseDn().getValue());

        try {
            List<LdapCrlEntry> results = new ArrayList<>();

            // 1. LdapTemplate.search() 실행
            ldapTemplate.search(
                    filter.getBaseDn().getValue(),
                    filter.getFilterString(),
                    (AttributesMapper<Object>) attributes -> {
                        try {
                            // 2. Attributes에서 직접 값 추출
                            String dnValue = getAttribute(attributes, "entryDN");
                            if (dnValue == null) {
                                dnValue = filter.getBaseDn().getValue();
                            }
                            DistinguishedName entryDn = DistinguishedName.of(dnValue);

                            // 3. Attributes에서 CRL 데이터 추출
                            String x509CrlBase64 = getAttribute(attributes, "x509crldata");
                            String issuerDn = getAttribute(attributes, "issuerDN");
                            String countryCode = getAttribute(attributes, "countryCode");

                            // 4. LdapCrlEntry 빌드 및 결과에 추가
                            LdapCrlEntry entry = LdapCrlEntry.builder()
                                    .dn(entryDn)
                                    .x509CrlBase64(x509CrlBase64)
                                    .issuerDn(issuerDn)
                                    .countryCode(countryCode)
                                    .build();
                            results.add(entry);
                            log.debug("CRL extracted from DN: {}", dnValue);

                        } catch (Exception e) {
                            log.warn("Failed to extract CRL from search result", e);
                            // 한 개 엔트리 실패 시 계속 진행 (graceful degradation)
                        }

                        return null;
                    }
            );

            log.debug("CRL search completed. Found: {} CRLs", results.size());
            return results;

        } catch (org.springframework.ldap.NameNotFoundException e) {
            log.debug("Base DN not found in LDAP: {}", filter.getBaseDn().getValue());
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("CRL search failed: {}", filter.getFilterString(), e);
            throw new LdapQueryService.LdapQueryException(
                "CRL search failed: " + e.getMessage(), e);
        }
    }

    /**
     * LDAP Attributes에서 문자열 값을 안전하게 추출하는 헬퍼 메서드
     *
     * @param attributes Attributes 객체
     * @param attributeName 속성명
     * @return 속성값 (없으면 null)
     */
    private String getAttribute(Attributes attributes, String attributeName) {
        try {
            javax.naming.directory.Attribute attr = attributes.get(attributeName);
            if (attr != null && attr.size() > 0) {
                Object value = attr.get(0);
                if (value != null) {
                    if (value instanceof String) {
                        return (String) value;
                    } else if (value instanceof byte[]) {
                        // Base64 바이트 배열인 경우 String으로 변환
                        return new String((byte[]) value);
                    } else {
                        return value.toString();
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("Failed to extract attribute '{}': {}", attributeName, e.getMessage());
            return null;
        }
    }

    // ======================== Inner Classes ========================

    /**
     * QueryResult 인터페이스 구현체
     *
     * @param <T> 결과 항목 타입
     */
    private static class QueryResultImpl<T> implements QueryResult<T> {

        private final List<T> content;
        private final int pageIndex;
        private final int pageSize;
        private final long totalCount;
        private final long startTime;

        QueryResultImpl(List<T> allResults, int pageIndex, int pageSize, long totalCount) {
            this.startTime = System.currentTimeMillis();
            this.pageIndex = pageIndex;
            this.pageSize = pageSize;
            this.totalCount = totalCount > 0 ? totalCount : (allResults != null ? allResults.size() : 0);

            // In-memory pagination
            if (allResults == null || allResults.isEmpty()) {
                this.content = Collections.emptyList();
            } else {
                int start = pageIndex * pageSize;
                int end = Math.min(start + pageSize, allResults.size());

                if (start >= allResults.size()) {
                    this.content = Collections.emptyList();
                } else {
                    this.content = allResults.subList(start, end);
                }
            }
        }

        @Override
        public long getTotalCount() {
            return totalCount;
        }

        @Override
        public int getCurrentPageIndex() {
            return pageIndex;
        }

        @Override
        public int getPageSize() {
            return pageSize;
        }

        @Override
        public List<T> getContent() {
            return content;
        }

        @Override
        public int getTotalPages() {
            return (int) Math.ceil((double) totalCount / pageSize);
        }

        @Override
        public boolean hasNextPage() {
            return (pageIndex + 1) < getTotalPages();
        }

        @Override
        public boolean hasPreviousPage() {
            return pageIndex > 0;
        }

        @Override
        public int getContentSize() {
            return content.size();
        }

        @Override
        public long getDurationMillis() {
            return System.currentTimeMillis() - startTime;
        }
    }

}
