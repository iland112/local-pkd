package com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter;

import com.unboundid.ldap.sdk.*;
import com.unboundid.ldif.LDIFReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import com.smartcoreinc.localpkd.ldapintegration.domain.port.LdapConnectionPort;

/**
 * UnboundIdLdapAdapter - UnboundID SDK 기반 LDAP Adapter
 *
 * <p><b>목적</b>: Spring LDAP을 대체하여 UnboundID SDK를 사용한 LDAP 연동</p>
 *
 * <h3>주요 기능</h3>
 * <ul>
 *   <li>LDAP 연결/해제 (Connection Pool 사용)</li>
 *   <li>LDIF 엔트리 직접 추가 (원시 LDIF 데이터 → OpenLDAP)</li>
 *   <li>DN 자동 변환 (dc=icao,dc=int → dc=ldap,dc=smartcoreinc,dc=com)</li>
 *   <li>중복 체크 (DN 기준 - OpenLDAP 기준)</li>
 *   <li>배치 추가 지원</li>
 * </ul>
 *
 * <h3>DN 변환 규칙</h3>
 * <pre>
 * LDIF 원본: cn=KOR-CSCA,ou=CSCA,o=ICAO-PKD,dc=icao,dc=int
 * 변환 후:    cn=KOR-CSCA,ou=CSCA,o=ICAO-PKD,dc=ldap,dc=smartcoreinc,dc=com
 * </pre>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * UnboundIdLdapAdapter adapter = new UnboundIdLdapAdapter(ldapHost, ldapPort, ...);
 * adapter.connect();
 *
 * // LDIF 엔트리 추가 (DN 자동 변환)
 * String ldifEntry = "dn: cn=KOR-CSCA,ou=CSCA,o=ICAO-PKD,dc=icao,dc=int\n" +
 *                    "objectClass: pkiCertificate\n" +
 *                    "certificateValue;binary:: ...\n";
 * boolean success = adapter.addLdifEntry(ldifEntry);
 *
 * adapter.disconnect();
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-19
 */
@Slf4j
@Component
public class UnboundIdLdapAdapter implements LdapConnectionPort {

    @Value("${app.ldap.urls}")
    private String ldapUrl;

    @Value("${app.ldap.base}")
    private String targetBaseDn;  // dc=ldap,dc=smartcoreinc,dc=com

    @Value("${app.ldap.username}")
    private String bindDn;

    @Value("${app.ldap.password}")
    private String bindPassword;

    private LDAPConnectionPool connectionPool;

    // ICAO PKD 원본 Base DN
    @SuppressWarnings("unused")  // Reserved for future ICAO PKD integration
    private static final String ICAO_BASE_DN = "dc=icao,dc=int";

    /**
     * 생성된 부모 엔트리 캐시 (성능 최적화)
     * - 매 업로드마다 동일한 부모 DN을 반복 체크하는 것을 방지
     * - 배치 시작 시 clearParentCache()로 초기화
     */
    private final Set<String> createdParentDnCache = ConcurrentHashMap.newKeySet();

    /**
     * 이미 존재하는 것으로 확인된 DN 캐시 (중복 체크 최적화)
     */
    private final Set<String> existingDnCache = ConcurrentHashMap.newKeySet();

    /**
     * 병렬 LDAP 업로드를 위한 스레드 풀 크기
     */
    private static final int PARALLEL_UPLOAD_THREADS = 8;

    /**
     * LDAP 연결 수립 (Connection Pool 생성)
     */
    @PostConstruct // Add this annotation
    public void connect() throws LDAPException {
        log.info("=== UnboundID LDAP Connection started ===");
        log.info("LDAP URL: {}", ldapUrl);
        log.info("Bind DN: {}", bindDn);
        log.info("Target Base DN: {}", targetBaseDn);

        if (targetBaseDn == null || targetBaseDn.isBlank()) {
            throw new IllegalStateException("LDAP Target Base DN ('spring.ldap.base') is not configured. Please check your application properties.");
        }

        if (connectionPool != null && !connectionPool.isClosed()) {
            log.warn("LDAP Connection Pool is already active. Skipping initialization.");
            return;
        }

        try {
            // LDAP URL 파싱 (ldap://192.168.100.10:389)
            String host = ldapUrl.replace("ldap://", "").replace("ldaps://", "");
            String[] parts = host.split(":");
            String hostname = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 389;

            // LDAP 연결 생성
            LDAPConnection connection = new LDAPConnection(hostname, port, bindDn, bindPassword);

            // Connection Pool 생성 (초기 5개, 최대 20개)
            connectionPool = new LDAPConnectionPool(connection, 5, 20);

            log.info("LDAP Connection Pool created successfully");
            log.info("Connection Pool: {} connections (initial), {} max", 5, 20);

        } catch (LDAPException e) {
            log.error("LDAP connection failed: {}", e.getMessage(), e);
            throw e; // LDAPException은 이미 명확하므로 다시 래핑하지 않음
        } catch (Exception e) {
            log.error("Unexpected error during LDAP connection: {}", e.getMessage(), e);
            throw new LDAPException(ResultCode.CONNECT_ERROR,
                "Failed to connect to LDAP: " + e.getMessage(), e);
        }
    }

    /**
     * LDAP 연결 해제
     */
    @PreDestroy
    public void disconnect() {
        log.info("=== UnboundID LDAP Disconnection started ===");

        if (connectionPool != null) {
            connectionPool.close();
            log.info("LDAP Connection Pool closed");
        }
    }

    /**
     * 연결 상태 확인
     */
    public boolean isConnected() {
        return connectionPool != null && !connectionPool.isClosed();
    }

    @Override
    public boolean testConnection() {
        // @PostConstruct 로 인해 connect()가 보장되므로, 이 부분의 connect() 호출 로직은 간소화될 수 있음.
        // 다만 혹시 모를 재연결을 위해 유지.
        if (connectionPool == null || connectionPool.isClosed()) {
            log.warn("LDAP connection pool is not active. Attempting to reconnect.");
            try {
                connect(); // 연결이 없으면 다시 시도
            } catch (LDAPException e) {
                log.error("Failed to re-establish LDAP connection for health check", e);
                return false;
            }
        }
        // 연결 풀의 유효한 연결을 하나 가져와서 테스트 후 반환
        LDAPConnection connection = null;
        try {
            connection = connectionPool.getConnection();
            connection.getConnectionOptions().setResponseTimeoutMillis(2000); // 2초 타임아웃
            connection.getRootDSE(); // 간단한 작업으로 연결 유효성 확인
            return true;
        } catch (LDAPException e) {
            log.error("LDAP connection test failed: {}", e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connectionPool.releaseConnection(connection);
            }
        }
    }

    /**
     * LDIF 엔트리 추가 (DN 자동 변환)
     *
     * <p>LDIF 원본 데이터를 파싱하여 OpenLDAP에 추가합니다.
     * DN의 baseDN 부분을 자동으로 변환합니다.</p>
     *
     * @param ldifEntryText LDIF 형식의 엔트리 텍스트
     * @return 성공 여부
     * @throws LDAPException LDAP 오류 발생 시
     */
    public boolean addLdifEntry(String ldifEntryText) throws LDAPException {
        log.debug("=== LDIF Entry Add started ===");

        try {
            // LDIF 텍스트 파싱
            LDIFReader ldifReader = new LDIFReader(new ByteArrayInputStream(ldifEntryText.getBytes()));

            Entry entry;
            try {
                entry = ldifReader.readEntry();
            } catch (com.unboundid.ldif.LDIFException e) {
                log.error("Failed to parse LDIF entry", e);
                throw new LDAPException(ResultCode.DECODING_ERROR,
                    "LDIF parsing error: " + e.getMessage(), e);
            } finally { // ensure ldifReader is closed
                try {
                    ldifReader.close();
                } catch (IOException ioException) {
                    log.warn("Failed to close LDIF reader", ioException);
                }
            }


            if (entry == null) {
                log.warn("Failed to parse LDIF entry");
                return false;
            }

            // DN 변환
            String originalDn = entry.getDN();
            String convertedDn = convertDn(originalDn);

            log.debug("Original DN: {}", originalDn);
            log.debug("Converted DN: {}", convertedDn);

            // 중복 체크
            if (isDuplicateEntry(convertedDn)) {
                log.warn("Duplicate entry detected, skipping: {}", convertedDn);
                return false;
            }

            // 부모 엔트리 생성 (DIT 구조 보장)
            ensureParentEntriesExist(convertedDn);

            // 새로운 Entry 생성 (변환된 DN)
            Entry convertedEntry = new Entry(convertedDn, entry.getAttributes());

            // OpenLDAP에 추가
            LDAPConnection connection = connectionPool.getConnection();
            try {
                AddRequest addRequest = new AddRequest(convertedEntry);
                LDAPResult result = connection.add(addRequest);

                if (result.getResultCode() == ResultCode.SUCCESS) {
                    log.info("LDIF entry added successfully: {}", convertedDn);
                    return true;
                } else {
                    log.warn("Failed to add LDIF entry: {} ({})",
                        convertedDn, result.getResultCode());
                    return false;
                }
            } finally {
                connectionPool.releaseConnection(connection);
            }

        } catch (IOException e) {
            log.error("Failed to parse LDIF entry", e);
            throw new LDAPException(ResultCode.DECODING_ERROR,
                "LDIF parsing error: " + e.getMessage(), e);
        } catch (LDAPException e) {
            // Already logged by ensureParentEntriesExist or createOrganizationalEntry
            throw e;
        }
    }

    /**
     * LDIF 엔트리 배치 추가 (병렬 처리 최적화)
     *
     * <p><b>성능 최적화</b>:</p>
     * <ul>
     *   <li>Phase 1: 부모 엔트리 캐싱 - 중복 LDAP 조회 제거</li>
     *   <li>Phase 2: 병렬 처리 - 다중 스레드로 동시 업로드</li>
     *   <li>Phase 3: 중복 체크 캐싱 - 이미 확인된 DN 스킵</li>
     * </ul>
     *
     * @param ldifEntries LDIF 엔트리 텍스트 목록
     * @return 추가 성공 횟수
     */
    public int addLdifEntriesBatch(List<String> ldifEntries) {
        if (ldifEntries == null || ldifEntries.isEmpty()) {
            return 0;
        }

        long startTime = System.currentTimeMillis();
        log.info("=== LDIF Batch Add started: {} entries (Parallel Mode) ===", ldifEntries.size());

        // 캐시 초기화 (새 배치 시작)
        clearCaches();

        // Phase 1: 모든 엔트리 사전 파싱 및 부모 DN 수집
        List<ParsedLdifEntry> parsedEntries = new ArrayList<>();
        Set<String> allParentDns = ConcurrentHashMap.newKeySet();

        for (String ldifEntryText : ldifEntries) {
            try {
                ParsedLdifEntry parsed = parseLdifEntry(ldifEntryText);
                if (parsed != null) {
                    parsedEntries.add(parsed);
                    collectParentDns(parsed.convertedDn, allParentDns);
                }
            } catch (Exception e) {
                log.debug("Failed to parse LDIF entry: {}", e.getMessage());
            }
        }

        log.info("Parsed {} entries, {} unique parent DNs to check",
            parsedEntries.size(), allParentDns.size());

        // Phase 2: 부모 엔트리 순차 생성 (계층 구조 보장)
        ensureAllParentEntriesExist(allParentDns);

        // Phase 3: 데이터 엔트리 병렬 업로드
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        int poolSize = Math.min(PARALLEL_UPLOAD_THREADS,
            connectionPool.getMaximumAvailableConnections());
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        List<CompletableFuture<Void>> futures = parsedEntries.stream()
            .map(parsed -> CompletableFuture.runAsync(() -> {
                try {
                    boolean success = addParsedEntry(parsed);
                    if (success) {
                        successCount.incrementAndGet();
                    } else {
                        skipCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.debug("Failed to add entry: {}", e.getMessage());
                    errorCount.incrementAndGet();
                }
            }, executor))
            .collect(Collectors.toList());

        // 모든 작업 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Executor interrupted during shutdown");
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("=== LDIF Batch Add completed in {}ms: {} success, {} skipped, {} errors ===",
            elapsed, successCount.get(), skipCount.get(), errorCount.get());

        return successCount.get();
    }

    /**
     * LDIF 엔트리 사전 파싱 (DN 변환 포함)
     */
    private ParsedLdifEntry parseLdifEntry(String ldifEntryText) throws LDAPException, IOException {
        LDIFReader ldifReader = new LDIFReader(new ByteArrayInputStream(ldifEntryText.getBytes()));
        try {
            Entry entry = ldifReader.readEntry();
            if (entry == null) {
                return null;
            }

            String originalDn = entry.getDN();
            String convertedDn = convertDn(originalDn);

            return new ParsedLdifEntry(convertedDn, entry.getAttributes());
        } catch (com.unboundid.ldif.LDIFException e) {
            throw new LDAPException(ResultCode.DECODING_ERROR, "LDIF parsing error: " + e.getMessage(), e);
        } finally {
            try {
                ldifReader.close();
            } catch (IOException e) {
                log.debug("Failed to close LDIF reader", e);
            }
        }
    }

    /**
     * 파싱된 엔트리 추가 (부모 체크 없이)
     */
    private boolean addParsedEntry(ParsedLdifEntry parsed) throws LDAPException {
        // 캐시된 중복 체크
        if (existingDnCache.contains(parsed.convertedDn)) {
            return false;
        }

        // LDAP 중복 체크
        if (isDuplicateEntry(parsed.convertedDn)) {
            existingDnCache.add(parsed.convertedDn);
            return false;
        }

        // LDAP 추가
        Entry entry = new Entry(parsed.convertedDn, parsed.attributes);
        LDAPConnection connection = connectionPool.getConnection();
        try {
            AddRequest addRequest = new AddRequest(entry);
            LDAPResult result = connection.add(addRequest);

            if (result.getResultCode() == ResultCode.SUCCESS) {
                log.debug("Entry added: {}", parsed.convertedDn);
                return true;
            } else if (result.getResultCode() == ResultCode.ENTRY_ALREADY_EXISTS) {
                existingDnCache.add(parsed.convertedDn);
                return false;
            } else {
                log.debug("Failed to add entry: {} ({})", parsed.convertedDn, result.getResultCode());
                return false;
            }
        } finally {
            connectionPool.releaseConnection(connection);
        }
    }

    /**
     * DN에서 모든 부모 DN 수집
     */
    private void collectParentDns(String dn, Set<String> parentDns) {
        try {
            DN parsedDn = new DN(dn);
            DN parent = parsedDn.getParent();

            while (parent != null && !parent.toString().equals(targetBaseDn)) {
                parentDns.add(parent.toString());
                parent = parent.getParent();
            }
        } catch (LDAPException e) {
            log.debug("Failed to collect parent DNs for: {}", dn);
        }
    }

    /**
     * 모든 부모 엔트리 생성 (계층 구조 순서 보장)
     */
    private void ensureAllParentEntriesExist(Set<String> parentDns) {
        // DN 길이 순으로 정렬 (짧은 것 먼저 = 상위 계층 먼저)
        List<String> sortedDns = parentDns.stream()
            .sorted((a, b) -> Integer.compare(a.length(), b.length()))
            .collect(Collectors.toList());

        for (String parentDn : sortedDns) {
            if (createdParentDnCache.contains(parentDn)) {
                continue; // 이미 생성됨
            }

            if (!isDuplicateEntry(parentDn)) {
                try {
                    createOrganizationalEntry(new DN(parentDn));
                } catch (LDAPException e) {
                    if (e.getResultCode() != ResultCode.ENTRY_ALREADY_EXISTS) {
                        log.debug("Failed to create parent: {}", parentDn);
                    }
                }
            }
            createdParentDnCache.add(parentDn);
        }

        log.info("Parent entries ensured: {} DNs processed", sortedDns.size());
    }

    /**
     * 캐시 초기화 (새 배치 시작 시 호출)
     */
    public void clearCaches() {
        createdParentDnCache.clear();
        existingDnCache.clear();
        log.debug("LDAP caches cleared");
    }

    /**
     * 파싱된 LDIF 엔트리 (내부 사용)
     */
    private static class ParsedLdifEntry {
        final String convertedDn;
        final java.util.Collection<Attribute> attributes;

        ParsedLdifEntry(String convertedDn, java.util.Collection<Attribute> attributes) {
            this.convertedDn = convertedDn;
            this.attributes = attributes;
        }
    }

    /**
     * DN 변환: ICAO PKD baseDN → OpenLDAP baseDN
     *
     * <p>ICAO PKD의 다양한 baseDN 패턴을 처리하며, 중간 계층 구조를 보존합니다:</p>
     * <ul>
     *   <li>dc=data,dc=download,dc=pkd,dc=icao,dc=int</li>
     *   <li>dc=icao,dc=int</li>
     *   <li>기타 dc=...,dc=icao,dc=int 패턴</li>
     * </ul>
     *
     * <p>변환 예시 (DIT 구조 보존):</p>
     * <pre>
     * c=NZ,dc=data,dc=download,dc=pkd,dc=icao,dc=int
     * → c=NZ,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
     * 
     * cn=KOR-CSCA,dc=icao,dc=int
     * → cn=KOR-CSCA,dc=ldap,dc=smartcoreinc,dc=com
     * </pre>
     *
     * @param originalDn 원본 DN
     * @return 변환된 DN (중간 계층 구조 보존)
     */
    private String convertDn(String originalDn) {
        if (originalDn == null || originalDn.isEmpty()) {
            return originalDn;
        }

        // 이미 targetBaseDn을 포함하면 그대로 반환
        if (originalDn.contains(targetBaseDn)) {
            log.debug("DN already has target base DN: {}", originalDn);
            return originalDn;
        }

        // ICAO baseDN이 없으면 그대로 반환
        if (!originalDn.contains("dc=icao,dc=int")) {
            log.debug("DN has no ICAO base DN: {}", originalDn);
            return originalDn;
        }

        try {
            // "dc=icao,dc=int" 문자열의 위치 찾기
            int icaoBaseDnIndex = originalDn.indexOf("dc=icao,dc=int");
            if (icaoBaseDnIndex == -1) {
                log.warn("Cannot find ICAO base DN in: {}", originalDn);
                return originalDn;
            }

            // ICAO baseDN 앞까지의 모든 컴포넌트 추출 (중간 계층 구조 보존)
            // 예: "c=NZ,dc=data,dc=download,dc=pkd," 부분
            String beforeIcaoDn = originalDn.substring(0, icaoBaseDnIndex);

            // 마지막 쉼표 제거 (있는 경우)
            if (beforeIcaoDn.endsWith(",")) {
                beforeIcaoDn = beforeIcaoDn.substring(0, beforeIcaoDn.length() - 1);
            }

            // 변환된 DN 생성: 원본 계층 구조 + 우리 baseDN
            String converted = beforeIcaoDn + "," + targetBaseDn;

            log.debug("DN converted (hierarchy preserved): {} → {}", originalDn, converted);
            return converted;

        } catch (Exception e) {
            // 예외 발생 시 폴백: 첫 번째 RDN만 추출
            log.warn("Failed to preserve DN hierarchy, using fallback: {}", originalDn, e);

            try {
                DN dn = new DN(originalDn);
                RDN firstRdn = dn.getRDN();
                String converted = firstRdn.toString() + "," + targetBaseDn;
                log.debug("DN converted (fallback): {} → {}", originalDn, converted);
                return converted;
            } catch (LDAPException ex) {
                log.error("Cannot convert DN: {}", originalDn, ex);
                return originalDn;
            }
        }
    }

    /**
     * 부모 엔트리 생성 (DIT 구조 보장)
     *
     * <p>주어진 DN의 모든 부모 엔트리가 존재하는지 확인하고,
     * 없으면 자동으로 생성합니다.</p>
     *
     * <p>예: c=NZ,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com 인 경우</p>
     * <ol>
     *   <li>dc=ldap,dc=smartcoreinc,dc=com (rootDN - 이미 존재)</li>
     *   <li>dc=pkd,dc=ldap,dc=smartcoreinc,dc=com (생성 필요)</li>
     *   <li>dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com (생성 필요)</li>
     *   <li>dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com (생성 필요)</li>
     * </ol>
     *
     * @param dn 대상 DN
     */
    private void ensureParentEntriesExist(String dn) {
        try {
            DN parsedDn = new DN(dn);
            DN parentDn = parsedDn.getParent();

            if (parentDn == null) {
                // rootDN에 도달하면 종료
                return;
            }

            // 부모 DN이 targetBaseDn이면 더 이상 올라가지 않음
            if (parentDn.toString().equals(targetBaseDn)) {
                log.debug("Reached target base DN: {}", targetBaseDn);
                return;
            }

            // 재귀적으로 상위 부모부터 생성
            ensureParentEntriesExist(parentDn.toString());

            // 현재 부모 엔트리가 이미 존재하는지 확인
            if (isDuplicateEntry(parentDn.toString())) {
                log.debug("Parent entry already exists: {}", parentDn);
                return;
            }

            // 부모 엔트리 생성
            try { // Catch LDAPException from createOrganizationalEntry
                createOrganizationalEntry(parentDn);
            } catch (LDAPException e) {
                log.warn("Failed to create parent organizational entry for DN: {}", parentDn, e);
                // Depending on requirements, you might want to rethrow or handle more gracefully
                // For now, we log and continue, as the original ensureParentEntriesExist also just logged
            }

        } catch (LDAPException e) {
            log.warn("Failed to ensure parent entries exist for DN: {}", dn, e);
        }
    }

    /**
     * Organizational 엔트리 생성
     *
     * <p>dc, ou, o 컴포넌트에 대한 기본 엔트리를 생성합니다.</p>
     *
     * @param dn 생성할 DN
     */
    private void createOrganizationalEntry(DN dn) throws LDAPException { // Declared to throw LDAPException
        try {
            RDN rdn = dn.getRDN();
            String rdnType = rdn.getAttributeNames()[0]; // "dc", "ou", "o" 등
            String rdnValue = rdn.getAttributeValues()[0];

            log.info("Creating organizational entry: {} (type: {})", dn, rdnType);

            // RDN 타입에 따라 적절한 objectClass 선택
            String objectClass;
            String attributeName;

            if ("dc".equalsIgnoreCase(rdnType)) {
                objectClass = "domain";
                attributeName = "dc";
            } else if ("ou".equalsIgnoreCase(rdnType)) {
                objectClass = "organizationalUnit";
                attributeName = "ou";
            } else if ("o".equalsIgnoreCase(rdnType)) {
                objectClass = "organization";
                attributeName = "o";
            } else if ("c".equalsIgnoreCase(rdnType)) {
                // Country 타입 처리
                objectClass = "country";
                attributeName = "c";
            } else {
                // 기타 타입은 organizationalUnit으로 처리
                log.warn("Unknown RDN type: {}, using organizationalUnit", rdnType);
                objectClass = "organizationalUnit";
                attributeName = "ou";
            }

            // Entry 생성
            Entry entry = new Entry(dn.toString());
            entry.addAttribute("objectClass", "top");
            entry.addAttribute("objectClass", objectClass);
            entry.addAttribute(attributeName, rdnValue);

            // OpenLDAP에 추가
            LDAPConnection connection = connectionPool.getConnection();
            try {
                AddRequest addRequest = new AddRequest(entry);
                LDAPResult result = connection.add(addRequest);

                if (result.getResultCode() == ResultCode.SUCCESS) {
                    log.info("Organizational entry created: {}", dn);
                } else if (result.getResultCode() == ResultCode.ENTRY_ALREADY_EXISTS) {
                    log.debug("Organizational entry already exists: {}", dn);
                } else {
                    log.error("Failed to create organizational entry: {} ({})", // Changed from warn to error for visibility
                        dn, result.getResultCode());
                    throw new LDAPException(result); // Throw exception to propagate failure
                }
            } finally {
                connectionPool.releaseConnection(connection);
            }

        } catch (LDAPException e) {
            if (e.getResultCode() == ResultCode.ENTRY_ALREADY_EXISTS) {
                log.debug("Organizational entry already exists: {}", dn);
            } else {
                log.error("Failed to create organizational entry: {}", dn, e);
                throw e; // Re-throw to propagate
            }
        }
    }

    /**
     * 중복 엔트리 체크 (OpenLDAP 기준)
     *
     * @param dn Distinguished Name
     * @return 중복 여부
     */
    private boolean isDuplicateEntry(String dn) {
        try {
            LDAPConnection connection = connectionPool.getConnection();
            try {
                // DN으로 검색 (엔트리 존재 여부 확인)
                SearchResult searchResult = connection.search(
                    dn,                     // Base DN
                    SearchScope.BASE,       // 해당 엔트리만 검색
                    "(objectClass=*)",      // 모든 objectClass
                    "1.1"                   // 속성 없이 DN만 반환
                );

                if (searchResult.getEntryCount() > 0) {
                    log.debug("Duplicate entry found: {}", dn);
                    return true;
                }

                return false;

            } finally {
                connectionPool.releaseConnection(connection);
            }

        } catch (LDAPException e) {
            if (e.getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                // 엔트리가 없으면 중복 아님
                return false;
            }

            log.warn("Duplicate check failed for DN: {}, error: {}",
                dn, e.getMessage());
            // 오류 발생 시 실제 존재 여부를 알 수 없으므로, 일단 중복이 아니라고 가정하는 대신,
            // 더 정확한 처리를 위해 예외를 다시 던지거나, 설정에 따라 동작하도록 고려해야 함.
            // 현재는 오류 발생 시에도 'false'를 반환하여 동작을 계속 이어가도록 함.
            return false;
        }
    }

    /**
     * 엔트리 삭제
     *
     * @param dn Distinguished Name
     * @return 성공 여부
     */
    public boolean deleteEntry(String dn) throws LDAPException {
        log.debug("=== LDAP Entry Deletion started ===");
        log.debug("DN: {}", dn);

        LDAPConnection connection = connectionPool.getConnection();
        try {
            DeleteRequest deleteRequest = new DeleteRequest(dn);
            LDAPResult result = connection.delete(deleteRequest);

            if (result.getResultCode() == ResultCode.SUCCESS) {
                log.info("Entry deleted successfully: {}", dn);
                return true;
            } else {
                log.warn("Failed to delete entry: {} ({})", dn, result.getResultCode());
                return false;
            }

        } finally {
            connectionPool.releaseConnection(connection);
        }
    }

    /**
     * 엔트리 검색
     *
     * @param baseDn 검색 Base DN
     * @param filter 검색 필터
     * @param scope 검색 범위
     * @return 검색 결과 엔트리 목록
     */
    public List<Entry> searchEntries(String baseDn, String filter, SearchScope scope)
            throws LDAPException {
        log.debug("=== LDAP Search started ===");
        log.debug("Base DN: {}, Filter: {}, Scope: {}", baseDn, filter, scope);

        LDAPConnection connection = connectionPool.getConnection();
        try {
            SearchResult searchResult = connection.search(baseDn, scope, filter);
            List<Entry> entries = new ArrayList<>(searchResult.getSearchEntries());

            log.debug("Search completed: {} entries found", entries.size());
            return entries;

        } finally {
            connectionPool.releaseConnection(connection);
        }
    }

    /**
     * Connection Pool 통계 정보
     */
    public String getConnectionPoolStats() {
        if (connectionPool == null) {
            return "Connection pool not initialized";
        }

        return String.format(
            "Connection Pool Stats: Available=%d, Max=%d, Current=%d",
            connectionPool.getCurrentAvailableConnections(),
            connectionPool.getMaximumAvailableConnections(),
            connectionPool.getCurrentAvailableConnections() +
                (connectionPool.getMaximumAvailableConnections() -
                 connectionPool.getCurrentAvailableConnections())
        );
    }
}