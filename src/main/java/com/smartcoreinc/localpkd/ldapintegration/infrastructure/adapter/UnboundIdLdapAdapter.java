package com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter;

import com.unboundid.ldap.sdk.*;
import com.unboundid.ldif.LDIFReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.ASN1Integer;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import com.smartcoreinc.localpkd.ldapintegration.domain.port.LdapConnectionPort;
import com.smartcoreinc.localpkd.ldapintegration.infrastructure.config.LdapProperties;

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
@RequiredArgsConstructor
public class UnboundIdLdapAdapter implements LdapConnectionPort {

    private final LdapProperties ldapProperties;

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
     * LDAP Write 연결 수립 (Connection Pool 생성)
     *
     * <p>Read/Write 분리가 활성화된 경우:</p>
     * <ul>
     *   <li>Write URL: OpenLDAP 1 직접 연결 (app.ldap.write.url)</li>
     *   <li>용도: PKD 업로드 시 데이터 저장</li>
     * </ul>
     */
    @PostConstruct
    public void connect() throws LDAPException {
        String writeUrl = ldapProperties.getWriteUrl();
        String targetBaseDn = ldapProperties.getBase();
        String bindDn = ldapProperties.getUsername();
        String bindPassword = ldapProperties.getPassword();

        log.info("=== UnboundID LDAP Write Connection started ===");
        log.info("Write URL: {} (R/W Separation: {})", writeUrl, ldapProperties.isReadWriteSeparationEnabled());
        log.info("Bind DN: {}", bindDn);
        log.info("Target Base DN: {}", targetBaseDn);

        if (targetBaseDn == null || targetBaseDn.isBlank()) {
            throw new IllegalStateException("LDAP Target Base DN ('app.ldap.base') is not configured. Please check your application properties.");
        }

        if (connectionPool != null && !connectionPool.isClosed()) {
            log.warn("LDAP Write Connection Pool is already active. Skipping initialization.");
            return;
        }

        try {
            // LDAP URL 파싱 (ldap://192.168.100.10:389)
            String host = writeUrl.replace("ldap://", "").replace("ldaps://", "");
            String[] parts = host.split(":");
            String hostname = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 389;

            // LDAP 연결 생성
            LDAPConnection connection = new LDAPConnection(hostname, port, bindDn, bindPassword);

            // Connection Pool 생성 (LdapProperties에서 설정값 가져오기)
            int initialSize = ldapProperties.getWritePoolInitialSize();
            int maxSize = ldapProperties.getWritePoolMaxSize();
            connectionPool = new LDAPConnectionPool(connection, initialSize, maxSize);

            log.info("LDAP Write Connection Pool created successfully");
            log.info("Connection Pool: {} connections (initial), {} max", initialSize, maxSize);

        } catch (LDAPException e) {
            log.error("LDAP Write connection failed: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during LDAP Write connection: {}", e.getMessage(), e);
            throw new LDAPException(ResultCode.CONNECT_ERROR,
                "Failed to connect to LDAP Write: " + e.getMessage(), e);
        }
    }

    /**
     * Target Base DN 반환 (LdapProperties에서)
     */
    private String getTargetBaseDn() {
        return ldapProperties.getBase();
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

            while (parent != null && !parent.toString().equals(getTargetBaseDn())) {
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
        String targetBaseDn = getTargetBaseDn();
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
            String targetBaseDn = getTargetBaseDn();
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

    // ============================================================================
    // CRL Comparison and Update Methods (RFC 5280 Compliant)
    // ============================================================================

    /**
     * CRL 비교 결과
     */
    public enum CrlCompareResult {
        /** 새 CRL이 더 최신 (업데이트 필요) */
        NEWER,
        /** 기존 CRL이 더 최신이거나 동일 (스킵) */
        OLDER_OR_EQUAL,
        /** 기존 CRL 없음 (신규 추가) */
        NOT_EXISTS,
        /** 비교 실패 */
        ERROR
    }

    /**
     * LDAP에서 기존 CRL의 CRL Number를 조회
     *
     * <p>RFC 5280에 따라 CRL Number (OID 2.5.29.20) 확장을 추출합니다.</p>
     *
     * @param dn CRL 엔트리의 DN
     * @return CRL Number (Optional - 없으면 empty)
     */
    public Optional<BigInteger> getCrlNumberFromLdap(String dn) {
        try {
            LDAPConnection connection = connectionPool.getConnection();
            try {
                // DN으로 CRL 엔트리 검색
                SearchResult searchResult = connection.search(
                    dn,
                    SearchScope.BASE,
                    "(objectClass=*)",
                    "certificateRevocationList;binary"
                );

                if (searchResult.getEntryCount() == 0) {
                    log.debug("CRL entry not found: {}", dn);
                    return Optional.empty();
                }

                Entry entry = searchResult.getSearchEntries().get(0);
                byte[] crlBinary = entry.getAttributeValueBytes("certificateRevocationList;binary");

                if (crlBinary == null || crlBinary.length == 0) {
                    log.warn("CRL entry has no binary data: {}", dn);
                    return Optional.empty();
                }

                // X.509 CRL 파싱하여 CRL Number 추출
                return extractCrlNumber(crlBinary);

            } finally {
                connectionPool.releaseConnection(connection);
            }

        } catch (LDAPException e) {
            if (e.getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                log.debug("CRL entry does not exist: {}", dn);
                return Optional.empty();
            }
            log.error("Failed to get CRL Number from LDAP: dn={}, error={}", dn, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * X.509 CRL 바이너리에서 CRL Number 추출
     *
     * <p>RFC 5280 Section 5.2.3: CRL Number Extension (OID 2.5.29.20)</p>
     *
     * @param crlBinary DER-encoded CRL binary
     * @return CRL Number (Optional)
     */
    public Optional<BigInteger> extractCrlNumber(byte[] crlBinary) {
        try {
            X509CRLHolder crlHolder = new X509CRLHolder(crlBinary);
            Extension crlNumberExt = crlHolder.getExtension(Extension.cRLNumber);

            if (crlNumberExt == null) {
                log.debug("CRL does not have CRL Number extension");
                return Optional.empty();
            }

            ASN1Integer crlNumber = ASN1Integer.getInstance(crlNumberExt.getParsedValue());
            return Optional.of(crlNumber.getValue());

        } catch (Exception e) {
            log.error("Failed to extract CRL Number from binary: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 새 CRL과 기존 CRL 비교 (RFC 5280 기준)
     *
     * <p>RFC 5280에 따라 CRL Number를 비교하여 어떤 CRL이 최신인지 결정합니다.</p>
     * <ul>
     *   <li>새 CRL Number > 기존 CRL Number: NEWER (업데이트 필요)</li>
     *   <li>새 CRL Number <= 기존 CRL Number: OLDER_OR_EQUAL (스킵)</li>
     *   <li>기존 CRL 없음: NOT_EXISTS (신규 추가)</li>
     * </ul>
     *
     * @param dn CRL 엔트리의 DN
     * @param newCrlBinary 새 CRL의 바이너리 데이터
     * @return 비교 결과
     */
    public CrlCompareResult compareCrl(String dn, byte[] newCrlBinary) {
        try {
            // 기존 CRL Number 조회
            Optional<BigInteger> existingCrlNumber = getCrlNumberFromLdap(dn);

            if (existingCrlNumber.isEmpty()) {
                log.debug("No existing CRL at DN: {}", dn);
                return CrlCompareResult.NOT_EXISTS;
            }

            // 새 CRL Number 추출
            Optional<BigInteger> newCrlNumber = extractCrlNumber(newCrlBinary);

            if (newCrlNumber.isEmpty()) {
                log.warn("New CRL has no CRL Number extension, treating as NEWER");
                // CRL Number가 없는 경우 thisUpdate 비교로 폴백할 수 있지만,
                // RFC 5280 권장사항에 따라 CRL Number가 없으면 최신으로 간주
                return CrlCompareResult.NEWER;
            }

            // CRL Number 비교
            int comparison = newCrlNumber.get().compareTo(existingCrlNumber.get());

            if (comparison > 0) {
                log.info("New CRL is newer: newCrlNumber={}, existingCrlNumber={}",
                    newCrlNumber.get(), existingCrlNumber.get());
                return CrlCompareResult.NEWER;
            } else {
                log.debug("Existing CRL is same or newer: newCrlNumber={}, existingCrlNumber={}",
                    newCrlNumber.get(), existingCrlNumber.get());
                return CrlCompareResult.OLDER_OR_EQUAL;
            }

        } catch (Exception e) {
            log.error("Failed to compare CRL: dn={}, error={}", dn, e.getMessage());
            return CrlCompareResult.ERROR;
        }
    }

    /**
     * LDAP에 CRL 엔트리 업데이트 (MODIFY 연산)
     *
     * <p>기존 CRL 엔트리의 certificateRevocationList;binary 속성을 교체합니다.</p>
     *
     * @param dn CRL 엔트리의 DN
     * @param newCrlBinary 새 CRL의 바이너리 데이터
     * @return 성공 여부
     */
    public boolean modifyCrlEntry(String dn, byte[] newCrlBinary) {
        try {
            LDAPConnection connection = connectionPool.getConnection();
            try {
                // MODIFY 요청 생성 - certificateRevocationList;binary 속성 교체
                Modification modification = new Modification(
                    ModificationType.REPLACE,
                    "certificateRevocationList;binary",
                    newCrlBinary
                );

                ModifyRequest modifyRequest = new ModifyRequest(dn, modification);
                LDAPResult result = connection.modify(modifyRequest);

                if (result.getResultCode() == ResultCode.SUCCESS) {
                    log.info("CRL entry updated successfully: {}", dn);
                    return true;
                } else {
                    log.error("Failed to update CRL entry: {} ({})", dn, result.getResultCode());
                    return false;
                }

            } finally {
                connectionPool.releaseConnection(connection);
            }

        } catch (LDAPException e) {
            log.error("LDAP error while updating CRL: dn={}, error={}", dn, e.getMessage(), e);
            return false;
        }
    }

    /**
     * CRL 추가 또는 업데이트 (RFC 5280 기준 비교 포함)
     *
     * <p>CRL Number를 비교하여 다음과 같이 처리합니다:</p>
     * <ul>
     *   <li>기존 CRL 없음: ADD 연산으로 신규 추가</li>
     *   <li>새 CRL이 더 최신: MODIFY 연산으로 교체</li>
     *   <li>기존 CRL이 더 최신/동일: 스킵</li>
     * </ul>
     *
     * @param ldifEntryText LDIF 형식의 CRL 엔트리
     * @return 결과 (SUCCESS=추가됨, SKIPPED=스킵됨, UPDATED=업데이트됨, ERROR=오류)
     */
    public CrlAddResult addOrUpdateCrlEntry(String ldifEntryText) {
        try {
            // LDIF 파싱
            LDIFReader ldifReader = new LDIFReader(new ByteArrayInputStream(ldifEntryText.getBytes()));
            Entry entry;
            try {
                entry = ldifReader.readEntry();
            } finally {
                try { ldifReader.close(); } catch (IOException ignored) {}
            }

            if (entry == null) {
                return CrlAddResult.ERROR;
            }

            // DN 변환
            String originalDn = entry.getDN();
            String convertedDn = convertDn(originalDn);

            // CRL 바이너리 추출
            byte[] crlBinary = entry.getAttributeValueBytes("certificateRevocationList;binary");
            if (crlBinary == null || crlBinary.length == 0) {
                log.warn("CRL entry has no binary data: {}", convertedDn);
                return CrlAddResult.ERROR;
            }

            // 부모 엔트리 생성
            ensureParentEntriesExist(convertedDn);

            // CRL 비교
            CrlCompareResult compareResult = compareCrl(convertedDn, crlBinary);

            switch (compareResult) {
                case NOT_EXISTS:
                    // 신규 추가
                    Entry convertedEntry = new Entry(convertedDn, entry.getAttributes());
                    LDAPConnection connection = connectionPool.getConnection();
                    try {
                        AddRequest addRequest = new AddRequest(convertedEntry);
                        LDAPResult result = connection.add(addRequest);

                        if (result.getResultCode() == ResultCode.SUCCESS) {
                            log.info("CRL entry added: {}", convertedDn);
                            return CrlAddResult.ADDED;
                        } else if (result.getResultCode() == ResultCode.ENTRY_ALREADY_EXISTS) {
                            // Race condition: 다른 스레드가 먼저 추가함 - 업데이트 시도
                            if (modifyCrlEntry(convertedDn, crlBinary)) {
                                return CrlAddResult.UPDATED;
                            }
                            return CrlAddResult.SKIPPED;
                        } else {
                            log.error("Failed to add CRL: {} ({})", convertedDn, result.getResultCode());
                            return CrlAddResult.ERROR;
                        }
                    } finally {
                        connectionPool.releaseConnection(connection);
                    }

                case NEWER:
                    // 새 CRL이 더 최신 - MODIFY 연산
                    if (modifyCrlEntry(convertedDn, crlBinary)) {
                        return CrlAddResult.UPDATED;
                    }
                    return CrlAddResult.ERROR;

                case OLDER_OR_EQUAL:
                    // 기존 CRL이 더 최신/동일 - 스킵
                    log.debug("Skipping older or equal CRL: {}", convertedDn);
                    return CrlAddResult.SKIPPED;

                case ERROR:
                default:
                    return CrlAddResult.ERROR;
            }

        } catch (Exception e) {
            log.error("Failed to add or update CRL: {}", e.getMessage(), e);
            return CrlAddResult.ERROR;
        }
    }

    /**
     * CRL 추가/업데이트 결과
     */
    public enum CrlAddResult {
        /** 신규 추가됨 */
        ADDED,
        /** 기존 CRL 업데이트됨 */
        UPDATED,
        /** 스킵됨 (기존 CRL이 더 최신/동일) */
        SKIPPED,
        /** 오류 발생 */
        ERROR
    }

    /**
     * CRL 배치 추가/업데이트 (RFC 5280 기준 비교 포함)
     *
     * @param ldifEntries LDIF 형식의 CRL 엔트리 목록
     * @return 추가/업데이트된 CRL 수 (added + updated)
     */
    public CrlBatchResult addOrUpdateCrlEntriesBatch(List<String> ldifEntries) {
        if (ldifEntries == null || ldifEntries.isEmpty()) {
            return new CrlBatchResult(0, 0, 0, 0);
        }

        long startTime = System.currentTimeMillis();
        log.info("=== CRL Batch Add/Update started: {} entries (RFC 5280 Comparison) ===", ldifEntries.size());

        AtomicInteger addedCount = new AtomicInteger(0);
        AtomicInteger updatedCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (String ldifEntryText : ldifEntries) {
            CrlAddResult result = addOrUpdateCrlEntry(ldifEntryText);

            switch (result) {
                case ADDED -> addedCount.incrementAndGet();
                case UPDATED -> updatedCount.incrementAndGet();
                case SKIPPED -> skippedCount.incrementAndGet();
                case ERROR -> errorCount.incrementAndGet();
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("=== CRL Batch Add/Update completed in {}ms: {} added, {} updated, {} skipped, {} errors ===",
            elapsed, addedCount.get(), updatedCount.get(), skippedCount.get(), errorCount.get());

        return new CrlBatchResult(addedCount.get(), updatedCount.get(), skippedCount.get(), errorCount.get());
    }

    /**
     * CRL 배치 처리 결과
     */
    public record CrlBatchResult(int added, int updated, int skipped, int errors) {
        public int totalSuccess() {
            return added + updated;
        }
    }

    // ============================================================================
    // Certificate (CSCA/DSC) Comparison and Update Methods (RFC 5280 Compliant)
    // ============================================================================

    /**
     * 인증서 비교 결과
     */
    public enum CertCompareResult {
        /** 신규 인증서 (추가 필요) */
        NOT_EXISTS,
        /** 동일 인증서 - 바이너리 동일 (스킵) */
        IDENTICAL,
        /** 동일 인증서 - 검증 상태만 다름 (업데이트 필요) */
        DESCRIPTION_CHANGED,
        /** 비교 실패 */
        ERROR
    }

    /**
     * LDAP에서 기존 인증서 정보 조회
     *
     * <p>RFC 5280에 따라 DN (Subject DN + Serial Number)으로 인증서를 식별합니다.</p>
     *
     * @param dn 인증서 엔트리의 DN
     * @return 인증서 바이너리와 description (Optional)
     */
    public Optional<CertificateEntryInfo> getCertificateFromLdap(String dn) {
        try {
            LDAPConnection connection = connectionPool.getConnection();
            try {
                SearchResult searchResult = connection.search(
                    dn,
                    SearchScope.BASE,
                    "(objectClass=*)",
                    "userCertificate;binary", "description"
                );

                if (searchResult.getEntryCount() == 0) {
                    log.debug("Certificate entry not found: {}", dn);
                    return Optional.empty();
                }

                Entry entry = searchResult.getSearchEntries().get(0);
                byte[] certBinary = entry.getAttributeValueBytes("userCertificate;binary");
                String description = entry.getAttributeValue("description");

                return Optional.of(new CertificateEntryInfo(certBinary, description));

            } finally {
                connectionPool.releaseConnection(connection);
            }

        } catch (LDAPException e) {
            if (e.getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                log.debug("Certificate entry does not exist: {}", dn);
                return Optional.empty();
            }
            log.error("Failed to get certificate from LDAP: dn={}, error={}", dn, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 인증서 엔트리 정보
     */
    public record CertificateEntryInfo(byte[] certBinary, String description) {}

    /**
     * 새 인증서와 기존 인증서 비교 (RFC 5280 기준)
     *
     * <p>RFC 5280에 따라 DN (Subject DN + Serial Number)이 동일한 인증서를 비교합니다.</p>
     * <ul>
     *   <li>기존 인증서 없음: NOT_EXISTS (신규 추가)</li>
     *   <li>바이너리 동일: IDENTICAL (스킵)</li>
     *   <li>바이너리 동일, description만 다름: DESCRIPTION_CHANGED (업데이트)</li>
     * </ul>
     *
     * @param dn 인증서 엔트리의 DN
     * @param newCertBinary 새 인증서의 바이너리 데이터
     * @param newDescription 새 description (검증 상태)
     * @return 비교 결과
     */
    public CertCompareResult compareCertificate(String dn, byte[] newCertBinary, String newDescription) {
        try {
            Optional<CertificateEntryInfo> existingCert = getCertificateFromLdap(dn);

            if (existingCert.isEmpty()) {
                log.debug("No existing certificate at DN: {}", dn);
                return CertCompareResult.NOT_EXISTS;
            }

            CertificateEntryInfo existing = existingCert.get();

            // 바이너리 비교 (인증서 자체가 동일한지)
            if (existing.certBinary() != null && newCertBinary != null) {
                if (!java.util.Arrays.equals(existing.certBinary(), newCertBinary)) {
                    // 바이너리가 다르면 다른 인증서 (하지만 DN이 같으므로 이론적으로 발생하지 않아야 함)
                    log.warn("Certificate binary mismatch at same DN: {}", dn);
                    // 이 경우는 기존 엔트리 업데이트로 처리
                    return CertCompareResult.DESCRIPTION_CHANGED;
                }
            }

            // 바이너리 동일 - description 비교
            String existingDesc = existing.description() != null ? existing.description() : "";
            String newDesc = newDescription != null ? newDescription : "";

            if (!existingDesc.equals(newDesc)) {
                log.info("Certificate description changed: dn={}, old={}, new={}",
                    dn, existingDesc, newDesc);
                return CertCompareResult.DESCRIPTION_CHANGED;
            }

            log.debug("Certificate is identical: {}", dn);
            return CertCompareResult.IDENTICAL;

        } catch (Exception e) {
            log.error("Failed to compare certificate: dn={}, error={}", dn, e.getMessage());
            return CertCompareResult.ERROR;
        }
    }

    /**
     * LDAP에 인증서 엔트리 업데이트 (MODIFY 연산)
     *
     * <p>기존 인증서 엔트리의 description 속성을 교체합니다.</p>
     *
     * @param dn 인증서 엔트리의 DN
     * @param newDescription 새 description (검증 상태)
     * @return 성공 여부
     */
    public boolean modifyCertificateDescription(String dn, String newDescription) {
        try {
            LDAPConnection connection = connectionPool.getConnection();
            try {
                Modification modification = new Modification(
                    ModificationType.REPLACE,
                    "description",
                    newDescription
                );

                ModifyRequest modifyRequest = new ModifyRequest(dn, modification);
                LDAPResult result = connection.modify(modifyRequest);

                if (result.getResultCode() == ResultCode.SUCCESS) {
                    log.info("Certificate description updated: {}", dn);
                    return true;
                } else {
                    log.error("Failed to update certificate description: {} ({})", dn, result.getResultCode());
                    return false;
                }

            } finally {
                connectionPool.releaseConnection(connection);
            }

        } catch (LDAPException e) {
            log.error("LDAP error while updating certificate: dn={}, error={}", dn, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 인증서 추가/업데이트 결과
     */
    public enum CertAddResult {
        /** 신규 추가됨 */
        ADDED,
        /** 기존 인증서 업데이트됨 (description 변경) */
        UPDATED,
        /** 스킵됨 (완전 동일) */
        SKIPPED,
        /** 오류 발생 */
        ERROR
    }

    /**
     * 인증서 추가 또는 업데이트 (RFC 5280 기준 비교 포함)
     *
     * <p>DN (Subject DN + Serial Number)을 기준으로 비교하여 처리합니다:</p>
     * <ul>
     *   <li>기존 인증서 없음: ADD 연산으로 신규 추가</li>
     *   <li>description만 다름: MODIFY 연산으로 업데이트</li>
     *   <li>완전 동일: 스킵</li>
     * </ul>
     *
     * @param ldifEntryText LDIF 형식의 인증서 엔트리
     * @return 결과 (ADDED, UPDATED, SKIPPED, ERROR)
     */
    public CertAddResult addOrUpdateCertificateEntry(String ldifEntryText) {
        try {
            // LDIF 파싱
            LDIFReader ldifReader = new LDIFReader(new ByteArrayInputStream(ldifEntryText.getBytes()));
            Entry entry;
            try {
                entry = ldifReader.readEntry();
            } finally {
                try { ldifReader.close(); } catch (IOException ignored) {}
            }

            if (entry == null) {
                return CertAddResult.ERROR;
            }

            // DN 변환
            String originalDn = entry.getDN();
            String convertedDn = convertDn(originalDn);

            // 인증서 바이너리 추출
            byte[] certBinary = entry.getAttributeValueBytes("userCertificate;binary");
            String description = entry.getAttributeValue("description");

            // 부모 엔트리 생성
            ensureParentEntriesExist(convertedDn);

            // 인증서 비교
            CertCompareResult compareResult = compareCertificate(convertedDn, certBinary, description);

            switch (compareResult) {
                case NOT_EXISTS:
                    // 신규 추가
                    Entry convertedEntry = new Entry(convertedDn, entry.getAttributes());
                    LDAPConnection connection = connectionPool.getConnection();
                    try {
                        AddRequest addRequest = new AddRequest(convertedEntry);
                        LDAPResult result = connection.add(addRequest);

                        if (result.getResultCode() == ResultCode.SUCCESS) {
                            log.debug("Certificate entry added: {}", convertedDn);
                            return CertAddResult.ADDED;
                        } else if (result.getResultCode() == ResultCode.ENTRY_ALREADY_EXISTS) {
                            // Race condition: 다른 스레드가 먼저 추가함
                            log.debug("Certificate already exists (race condition): {}", convertedDn);
                            return CertAddResult.SKIPPED;
                        } else {
                            log.error("Failed to add certificate: {} ({})", convertedDn, result.getResultCode());
                            return CertAddResult.ERROR;
                        }
                    } finally {
                        connectionPool.releaseConnection(connection);
                    }

                case DESCRIPTION_CHANGED:
                    // description만 다름 - MODIFY 연산
                    if (modifyCertificateDescription(convertedDn, description)) {
                        return CertAddResult.UPDATED;
                    }
                    return CertAddResult.ERROR;

                case IDENTICAL:
                    // 완전 동일 - 스킵
                    log.debug("Skipping identical certificate: {}", convertedDn);
                    return CertAddResult.SKIPPED;

                case ERROR:
                default:
                    return CertAddResult.ERROR;
            }

        } catch (Exception e) {
            log.error("Failed to add or update certificate: {}", e.getMessage(), e);
            return CertAddResult.ERROR;
        }
    }

    /**
     * 인증서 배치 추가/업데이트 (RFC 5280 기준 비교 포함)
     *
     * @param ldifEntries LDIF 형식의 인증서 엔트리 목록
     * @return 처리 결과
     */
    public CertBatchResult addOrUpdateCertificateEntriesBatch(List<String> ldifEntries) {
        if (ldifEntries == null || ldifEntries.isEmpty()) {
            return new CertBatchResult(0, 0, 0, 0);
        }

        long startTime = System.currentTimeMillis();
        log.info("=== Certificate Batch Add/Update started: {} entries (RFC 5280 Comparison) ===", ldifEntries.size());

        // 캐시 초기화
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

        log.info("Parsed {} certificate entries, {} unique parent DNs",
            parsedEntries.size(), allParentDns.size());

        // Phase 2: 부모 엔트리 생성
        ensureAllParentEntriesExist(allParentDns);

        // Phase 3: 인증서 병렬 업로드
        AtomicInteger addedCount = new AtomicInteger(0);
        AtomicInteger updatedCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        int poolSize = Math.min(PARALLEL_UPLOAD_THREADS, connectionPool.getMaximumAvailableConnections());
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        List<CompletableFuture<Void>> futures = ldifEntries.stream()
            .map(ldifEntryText -> CompletableFuture.runAsync(() -> {
                CertAddResult result = addOrUpdateCertificateEntry(ldifEntryText);
                switch (result) {
                    case ADDED -> addedCount.incrementAndGet();
                    case UPDATED -> updatedCount.incrementAndGet();
                    case SKIPPED -> skippedCount.incrementAndGet();
                    case ERROR -> errorCount.incrementAndGet();
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
        log.info("=== Certificate Batch Add/Update completed in {}ms: {} added, {} updated, {} skipped, {} errors ===",
            elapsed, addedCount.get(), updatedCount.get(), skippedCount.get(), errorCount.get());

        return new CertBatchResult(addedCount.get(), updatedCount.get(), skippedCount.get(), errorCount.get());
    }

    /**
     * 인증서 배치 처리 결과
     */
    public record CertBatchResult(int added, int updated, int skipped, int errors) {
        public int totalSuccess() {
            return added + updated;
        }
    }

    // ============================================================================
    // Master List Comparison and Update Methods
    // ============================================================================

    /**
     * Master List 비교 결과
     */
    public enum MasterListCompareResult {
        /** 신규 Master List (추가 필요) */
        NOT_EXISTS,
        /** 동일 Master List (스킵) */
        IDENTICAL,
        /** 다른 Master List (업데이트 필요) */
        DIFFERENT,
        /** 비교 실패 */
        ERROR
    }

    /**
     * LDAP에서 기존 Master List 정보 조회
     *
     * @param dn Master List 엔트리의 DN
     * @return Master List CMS 바이너리 (Optional)
     */
    public Optional<byte[]> getMasterListFromLdap(String dn) {
        try {
            LDAPConnection connection = connectionPool.getConnection();
            try {
                SearchResult searchResult = connection.search(
                    dn,
                    SearchScope.BASE,
                    "(objectClass=*)",
                    "pkdMasterListContent"
                );

                if (searchResult.getEntryCount() == 0) {
                    log.debug("Master List entry not found: {}", dn);
                    return Optional.empty();
                }

                Entry entry = searchResult.getSearchEntries().get(0);
                byte[] mlBinary = entry.getAttributeValueBytes("pkdMasterListContent");

                return Optional.ofNullable(mlBinary);

            } finally {
                connectionPool.releaseConnection(connection);
            }

        } catch (LDAPException e) {
            if (e.getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                log.debug("Master List entry does not exist: {}", dn);
                return Optional.empty();
            }
            log.error("Failed to get Master List from LDAP: dn={}, error={}", dn, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Master List 비교
     *
     * <p>CMS 바이너리를 비교하여 동일 여부를 판단합니다.</p>
     *
     * @param dn Master List 엔트리의 DN
     * @param newMlBinary 새 Master List의 CMS 바이너리
     * @return 비교 결과
     */
    public MasterListCompareResult compareMasterList(String dn, byte[] newMlBinary) {
        try {
            Optional<byte[]> existingMl = getMasterListFromLdap(dn);

            if (existingMl.isEmpty()) {
                log.debug("No existing Master List at DN: {}", dn);
                return MasterListCompareResult.NOT_EXISTS;
            }

            // 바이너리 비교
            if (java.util.Arrays.equals(existingMl.get(), newMlBinary)) {
                log.debug("Master List is identical: {}", dn);
                return MasterListCompareResult.IDENTICAL;
            }

            log.info("Master List content differs: {}", dn);
            return MasterListCompareResult.DIFFERENT;

        } catch (Exception e) {
            log.error("Failed to compare Master List: dn={}, error={}", dn, e.getMessage());
            return MasterListCompareResult.ERROR;
        }
    }

    /**
     * LDAP에 Master List 엔트리 업데이트 (MODIFY 연산)
     *
     * @param dn Master List 엔트리의 DN
     * @param newMlBinary 새 Master List의 CMS 바이너리
     * @return 성공 여부
     */
    public boolean modifyMasterListEntry(String dn, byte[] newMlBinary) {
        try {
            LDAPConnection connection = connectionPool.getConnection();
            try {
                Modification modification = new Modification(
                    ModificationType.REPLACE,
                    "pkdMasterListContent",
                    newMlBinary
                );

                ModifyRequest modifyRequest = new ModifyRequest(dn, modification);
                LDAPResult result = connection.modify(modifyRequest);

                if (result.getResultCode() == ResultCode.SUCCESS) {
                    log.info("Master List entry updated: {}", dn);
                    return true;
                } else {
                    log.error("Failed to update Master List entry: {} ({})", dn, result.getResultCode());
                    return false;
                }

            } finally {
                connectionPool.releaseConnection(connection);
            }

        } catch (LDAPException e) {
            log.error("LDAP error while updating Master List: dn={}, error={}", dn, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Master List 추가/업데이트 결과
     */
    public enum MasterListAddResult {
        /** 신규 추가됨 */
        ADDED,
        /** 기존 엔트리 업데이트됨 */
        UPDATED,
        /** 스킵됨 (동일) */
        SKIPPED,
        /** 오류 발생 */
        ERROR
    }

    /**
     * Master List 추가 또는 업데이트
     *
     * @param ldifEntryText LDIF 형식의 Master List 엔트리
     * @return 결과 (ADDED, UPDATED, SKIPPED, ERROR)
     */
    public MasterListAddResult addOrUpdateMasterListEntry(String ldifEntryText) {
        try {
            // LDIF 파싱
            LDIFReader ldifReader = new LDIFReader(new ByteArrayInputStream(ldifEntryText.getBytes()));
            Entry entry;
            try {
                entry = ldifReader.readEntry();
            } finally {
                try { ldifReader.close(); } catch (IOException ignored) {}
            }

            if (entry == null) {
                return MasterListAddResult.ERROR;
            }

            // DN 변환
            String originalDn = entry.getDN();
            String convertedDn = convertDn(originalDn);

            // Master List 바이너리 추출
            byte[] mlBinary = entry.getAttributeValueBytes("pkdMasterListContent");
            if (mlBinary == null || mlBinary.length == 0) {
                log.warn("Master List entry has no binary data: {}", convertedDn);
                return MasterListAddResult.ERROR;
            }

            // 부모 엔트리 생성
            ensureParentEntriesExist(convertedDn);

            // Master List 비교
            MasterListCompareResult compareResult = compareMasterList(convertedDn, mlBinary);

            switch (compareResult) {
                case NOT_EXISTS:
                    // 신규 추가
                    Entry convertedEntry = new Entry(convertedDn, entry.getAttributes());
                    LDAPConnection connection = connectionPool.getConnection();
                    try {
                        AddRequest addRequest = new AddRequest(convertedEntry);
                        LDAPResult result = connection.add(addRequest);

                        if (result.getResultCode() == ResultCode.SUCCESS) {
                            log.info("Master List entry added: {}", convertedDn);
                            return MasterListAddResult.ADDED;
                        } else if (result.getResultCode() == ResultCode.ENTRY_ALREADY_EXISTS) {
                            // Race condition: 다른 스레드가 먼저 추가함 - 업데이트 시도
                            if (modifyMasterListEntry(convertedDn, mlBinary)) {
                                return MasterListAddResult.UPDATED;
                            }
                            return MasterListAddResult.SKIPPED;
                        } else {
                            log.error("Failed to add Master List: {} ({})", convertedDn, result.getResultCode());
                            return MasterListAddResult.ERROR;
                        }
                    } finally {
                        connectionPool.releaseConnection(connection);
                    }

                case DIFFERENT:
                    // 다른 Master List - MODIFY 연산
                    if (modifyMasterListEntry(convertedDn, mlBinary)) {
                        return MasterListAddResult.UPDATED;
                    }
                    return MasterListAddResult.ERROR;

                case IDENTICAL:
                    // 동일 - 스킵
                    log.debug("Skipping identical Master List: {}", convertedDn);
                    return MasterListAddResult.SKIPPED;

                case ERROR:
                default:
                    return MasterListAddResult.ERROR;
            }

        } catch (Exception e) {
            log.error("Failed to add or update Master List: {}", e.getMessage(), e);
            return MasterListAddResult.ERROR;
        }
    }

    /**
     * Master List 배치 처리 결과
     */
    public record MasterListBatchResult(int added, int updated, int skipped, int errors) {
        public int totalSuccess() {
            return added + updated;
        }
    }
}