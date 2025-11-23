package com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter;

import com.unboundid.ldap.sdk.*;
import com.unboundid.ldif.LDIFChangeRecord;
import com.unboundid.ldif.LDIFReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
import com.smartcoreinc.localpkd.ldapintegration.domain.port.LdapConnectionPort;

public class UnboundIdLdapAdapter implements LdapConnectionPort {

    @Value("${spring.ldap.urls}")
    private String ldapUrl;

    @Value("${spring.ldap.base}")
    private String targetBaseDn;  // dc=ldap,dc=smartcoreinc,dc=com

    @Value("${spring.ldap.username}")
    private String bindDn;

    @Value("${spring.ldap.password}")
    private String bindPassword;

    private LDAPConnectionPool connectionPool;

    // ICAO PKD 원본 Base DN
    private static final String ICAO_BASE_DN = "dc=icao,dc=int";

    /**
     * LDAP 연결 수립 (Connection Pool 생성)
     */
    public void connect() throws LDAPException {
        log.info("=== UnboundID LDAP Connection started ===");
        log.info("LDAP URL: {}", ldapUrl);
        log.info("Bind DN: {}", bindDn);
        log.info("Target Base DN: {}", targetBaseDn);

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
    private void disconnect() {
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
        if (connectionPool == null || connectionPool.isClosed()) {
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
            LDIFReader ldifReader = new LDIFReader(
                new ByteArrayInputStream(ldifEntryText.getBytes())
            );

            Entry entry;
            try {
                entry = ldifReader.readEntry();
            } catch (com.unboundid.ldif.LDIFException e) {
                log.error("Failed to parse LDIF entry", e);
                throw new LDAPException(ResultCode.DECODING_ERROR,
                    "LDIF parsing error: " + e.getMessage(), e);
            }
            ldifReader.close();

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
            // Already logged
            throw e;
        }
    }

    /**
     * LDIF 엔트리 배치 추가
     *
     * @param ldifEntries LDIF 엔트리 텍스트 목록
     * @return 추가 성공 횟수
     */
    public int addLdifEntriesBatch(List<String> ldifEntries) {
        log.info("=== LDIF Batch Add started: {} entries ===", ldifEntries.size());

        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;

        for (int i = 0; i < ldifEntries.size(); i++) {
            String ldifEntry = ldifEntries.get(i);
            try {
                boolean success = addLdifEntry(ldifEntry);
                if (success) {
                    successCount++;
                } else {
                    skipCount++;
                }
            } catch (Exception e) {
                log.warn("Failed to add LDIF entry [{}]: {}", i + 1, e.getMessage());
                errorCount++;
            }
        }

        log.info("LDIF Batch Add completed: {} success, {} skipped, {} errors",
            successCount, skipCount, errorCount);

        return successCount;
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
            createOrganizationalEntry(parentDn);

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
    private void createOrganizationalEntry(DN dn) {
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
                    log.warn("Failed to create organizational entry: {} ({})",
                        dn, result.getResultCode());
                }
            } finally {
                connectionPool.releaseConnection(connection);
            }

        } catch (LDAPException e) {
            if (e.getResultCode() == ResultCode.ENTRY_ALREADY_EXISTS) {
                log.debug("Organizational entry already exists: {}", dn);
            } else {
                log.error("Failed to create organizational entry: {}", dn, e);
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
            return false;  // 오류 시 중복 아니라고 간주
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
