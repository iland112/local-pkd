
package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.adapter;

import com.smartcoreinc.localpkd.certificatevalidation.domain.exception.LdapConnectionException;
import com.smartcoreinc.localpkd.certificatevalidation.domain.exception.LdapOperationException;
import com.smartcoreinc.localpkd.certificatevalidation.domain.port.LdapConnectionPort;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldif.LDIFReader;
import com.unboundid.ldif.LDIFWriter;
import com.unboundid.ldif.LDIFChangeRecord;
import com.unboundid.util.ssl.SSLUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;

/**
 * UnboundIdLdapConnectionAdapter - UnboundID LDAPSDK 기반 LDAP 연결 어댑터
 *
 * <p><b>목적</b>: LdapConnectionPort 인터페이스를 UnboundID LDAPSDK를 이용해 구현합니다.</p>
 *
 * <p><b>설계 패턴</b>: Adapter Pattern (Hexagonal Architecture)
 * <ul>
 *   <li>Port: Domain Layer의 LdapConnectionPort 인터페이스</li>
 *   <li>Adapter: Infrastructure Layer의 이 클래스 (UnboundID SDK 구현체)</li>
 *   <li>장점: Domain이 UnboundID SDK에 의존하지 않음</li>
 * </ul>
 * </p>
 *
 * <p><b>UnboundID LDAPSDK 특징</b>:
 * <ul>
 *   <li>Pure Java LDAP client library</li>
 *   <li>연결 풀링 지원 (LDAPConnectionPool)</li>
 *   <li>SSL/TLS 지원</li>
 *   <li>동기/비동기 연산 지원</li>
 *   <li>LDIF 파일 지원</li>
 * </ul>
 * </p>
 *
 * <p><b>LDAP 연결 설정</b>:
 * <pre>
 * spring.ldap.urls=ldap://192.168.100.10:389
 * spring.ldap.base=dc=ldap,dc=smartcoreinc,dc=com
 * spring.ldap.username=cn=admin,dc=ldap,dc=smartcoreinc,dc=com
 * spring.ldap.password=admin_password
 * app.ldap.connection.pool.size=10
 * </pre>
 * </p>
 *
 * <p><b>Phase 17</b>: UnboundID SDK adapter for LDAP Integration completion</p>
 *
 * @see LdapConnectionPort
 * @see LDAPConnection
 * @see LDAPConnectionPool
 * @author SmartCore Inc.
 * @version 2.0
 * @since 2025-10-30 (Phase 17 Task 1.5 - UnboundID Implementation)
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "spring.ldap.urls", matchIfMissing = false)
@RequiredArgsConstructor
public class UnboundIdLdapConnectionAdapter implements LdapConnectionPort {

    @Value("${spring.ldap.urls}")
    private String ldapUrls;

    @Value("${spring.ldap.base}")
    private String baseDn;

    @Value("${spring.ldap.username}")
    private String bindDn;

    @Value("${spring.ldap.password}")
    private String bindPassword;

    @Value("${app.ldap.connection.pool.size:10}")
    private int connectionPoolSize;

    @Value("${app.ldap.connection.timeout:5000}")
    private int connectionTimeout;

    @Value("${app.ldap.read.timeout:10000}")
    private int readTimeout;

    private LDAPConnectionPool connectionPool;
    private volatile boolean connected = false;
    private String ldapHost;
    private int ldapPort = 389;

    /**
     * LDAP 서버에 연결
     *
     * <p>UnboundID LDAPSDK를 사용하여 LDAP 연결 풀을 초기화합니다.
     * 연결 풀링, 타임아웃, SSL/TLS 설정 등이 자동으로 관리됩니다.</p>
     *
     * @throws LdapConnectionException 연결 실패 시
     */
    @Override
    public void connect() throws LdapConnectionException {
        log.debug("=== LDAP Connection started (UnboundID SDK) ===");

        try {
            // 1. LDAP URL 파싱 (ldap://host:port)
            String url = ldapUrls.trim();
            if (url.startsWith("ldap://")) {
                url = url.substring(7);
            } else if (url.startsWith("ldaps://")) {
                url = url.substring(8);
            }

            String[] parts = url.split(":");
            ldapHost = parts[0];
            if (parts.length > 1) {
                ldapPort = Integer.parseInt(parts[1]);
            }

            log.debug("LDAP Server: {}:{}", ldapHost, ldapPort);

            // 2. 기본 연결 생성
            LDAPConnectionOptions options = new LDAPConnectionOptions();
            options.setConnectTimeoutMillis(connectionTimeout);
            options.setResponseTimeoutMillis(readTimeout);

            LDAPConnection connection = new LDAPConnection(options, ldapHost, ldapPort);

            // 3. Bind (인증)
            connection.bind(bindDn, bindPassword);
            log.info("LDAP bind successful: {}", bindDn);

            // 4. 연결 풀 생성
            connectionPool = new LDAPConnectionPool(connection, connectionPoolSize);
            connectionPool.setConnectionPoolName("SmartCore-LDAP-Pool");
            log.info("LDAP Connection Pool created: size={}, name=SmartCore-LDAP-Pool", connectionPoolSize);

            // 5. 테스트 연결 (Base DN 검색)
            SearchRequest searchRequest = new SearchRequest(
                baseDn,
                SearchScope.BASE,
                "(objectClass=*)"
            );
            SearchResult result = connectionPool.search(searchRequest);
            log.info("LDAP connection test successful: Base DN={}", baseDn);

            connected = true;
            log.info("LDAP connected successfully: {}", getConnectionStatus());

        } catch (LDAPException e) {
            log.error("LDAP connection failed", e);
            connected = false;
            throw LdapConnectionException.connectionFailed(ldapHost + ":" + ldapPort, e);
        } catch (Exception e) {
            log.error("Unexpected error during LDAP connection", e);
            connected = false;
            throw LdapConnectionException.connectionFailed(ldapHost + ":" + ldapPort, e);
        }
    }

    /**
     * LDAP 서버로부터 연결 해제
     *
     * <p>연결 풀을 안전하게 종료하고 리소스를 해제합니다.</p>
     *
     * @throws LdapConnectionException 연결 해제 실패 시
     */
    @Override
    public void disconnect() throws LdapConnectionException {
        log.debug("=== LDAP disconnection started ===");

        try {
            if (connectionPool != null) {
                connectionPool.close();
                log.info("LDAP Connection Pool closed");
            }
            connected = false;
            log.info("LDAP disconnected");

        } catch (Exception e) {
            log.error("LDAP disconnection error", e);
            throw new LdapConnectionException("Failed to disconnect from LDAP", e);
        }
    }

    /**
     * 현재 LDAP 연결 상태 확인
     *
     * <p>true: 연결 상태, false: 비연결 상태</p>
     *
     * @return 연결 여부
     */
    @Override
    public boolean isConnected() {
        if (!connected || connectionPool == null) {
            return false;
        }

        try {
            // Quick validation: 풀에서 연결 하나 가져오기
            LDAPConnection conn = connectionPool.getConnection();
            try {
                // 간단한 검색으로 검증
                SearchRequest searchRequest = new SearchRequest(
                    baseDn,
                    SearchScope.BASE,
                    "(objectClass=*)"
                );
                searchRequest.setTimeLimitSeconds(1);
                connectionPool.search(searchRequest);
                return true;
            } finally {
                connectionPool.releaseConnection(conn);
            }

        } catch (Exception e) {
            log.warn("LDAP connection validation failed", e);
            connected = false;
            return false;
        }
    }

    /**
     * LDAP 연결 상태 상세 정보 조회
     *
     * @return 연결 상태 문자열 (서버 주소, Base DN 등)
     */
    @Override
    public String getConnectionStatus() {
        try {
            String poolInfo = connectionPool != null ?
                String.format("Pool: %s (size=%d)",
                    connectionPool.getConnectionPoolName(),
                    connectionPoolSize) :
                "Pool: Not initialized";

            return String.format(
                "Connected to LDAP: %s:%d, Base DN: %s, Status: %s, %s",
                ldapHost,
                ldapPort,
                baseDn,
                connected ? "CONNECTED" : "DISCONNECTED",
                poolInfo
            );
        } catch (Exception e) {
            return "Unknown connection status";
        }
    }

    /**
     * 인증서를 LDAP 디렉토리에 업로드
     *
     * <p><b>LDAP Entry 생성 규칙</b>:</p>
     * <pre>
     * dn: cn={subject_cn},ou=certificates,{base_dn}
     * objectClass: pkiCertificate
     * cn: {subject_cn}
     * serialNumber: {certificate_serial}
     * certificateContent: {DER_encoded_certificate}
     * issuer: {issuer_dn}
     * validFrom: {validity_start}
     * validTo: {validity_end}
     * fingerprint: {sha256_fingerprint}
     * uploadedAt: {current_timestamp}
     * </pre>
     *
     * @param certificateDer DER 인코딩된 X.509 인증서 바이트 배열
     * @param subjectCn 인증서 Subject CN (예: "CN=Test Certificate")
     * @param baseDn LDAP Base DN (예: "dc=ldap,dc=smartcoreinc,dc=com")
     * @return 업로드된 인증서의 LDAP Distinguished Name (DN)
     * @throws LdapOperationException 업로드 실패 시
     */
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

            // Create LDAP entry attributes
            Attribute[] attributes = {
                new Attribute("objectClass", "pkiCertificate"),
                new Attribute("cn", subjectCn),
                new Attribute("certificateContent", certificateDer),
                new Attribute("uploadedAt", getCurrentTimestamp())
            };

            AddRequest addRequest = new AddRequest(certificateDn, attributes);

            // Execute add operation
            LDAPResult result = connectionPool.add(addRequest);
            if (result.getResultCode().equals(ResultCode.SUCCESS)) {
                log.info("Certificate uploaded to LDAP: {}", certificateDn);
                return certificateDn;
            } else {
                throw new LdapOperationException(
                    String.format("Failed to upload certificate: %s", result.getDiagnosticMessage()),
                    "uploadCertificate"
                );
            }

        } catch (LdapOperationException e) {
            throw e;
        } catch (LDAPException e) {
            log.error("Failed to upload certificate to LDAP", e);
            throw LdapOperationException.uploadCertificateFailed(subjectCn, e);
        } catch (Exception e) {
            log.error("Unexpected error during certificate upload", e);
            throw LdapOperationException.uploadCertificateFailed(subjectCn, e);
        }
    }

    /**
     * CRL (Certificate Revocation List)을 LDAP에 업로드
     *
     * <p><b>CRL Entry 생성 규칙</b>:</p>
     * <pre>
     * dn: cn={issuer_name}-{update_number},ou=crl,{base_dn}
     * objectClass: pkiCRL
     * cn: {issuer_name}-{update_number}
     * issuer: {issuer_dn}
     * crlContent: {DER_encoded_crl}
     * thisUpdate: {this_update_timestamp}
     * nextUpdate: {next_update_timestamp}
     * revokedCount: {count_of_revoked_certs}
     * uploadedAt: {current_timestamp}
     * </pre>
     *
     * @param crlDer DER 인코딩된 CRL 바이트 배열
     * @param issuerName 발급자 이름 (예: "CSCA-QA")
     * @param baseDn LDAP Base DN
     * @return 업로드된 CRL의 LDAP DN
     * @throws LdapOperationException 업로드 실패 시
     */
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

            // Create LDAP entry attributes
            Attribute[] attributes = {
                new Attribute("objectClass", "pkiCRL"),
                new Attribute("cn", issuerName),
                new Attribute("issuer", issuerName),
                new Attribute("crlContent", crlDer),
                new Attribute("uploadedAt", getCurrentTimestamp())
            };

            AddRequest addRequest = new AddRequest(crlDn, attributes);

            // Execute add operation
            LDAPResult result = connectionPool.add(addRequest);
            if (result.getResultCode().equals(ResultCode.SUCCESS)) {
                log.info("CRL uploaded to LDAP: {}", crlDn);
                return crlDn;
            } else {
                throw new LdapOperationException(
                    String.format("Failed to upload CRL: %s", result.getDiagnosticMessage()),
                    "uploadCrl"
                );
            }

        } catch (LdapOperationException e) {
            throw e;
        } catch (LDAPException e) {
            log.error("Failed to upload CRL to LDAP", e);
            throw LdapOperationException.uploadCrlFailed(issuerName, e);
        } catch (Exception e) {
            log.error("Unexpected error during CRL upload", e);
            throw LdapOperationException.uploadCrlFailed(issuerName, e);
        }
    }

    /**
     * LDAP에서 인증서 검색
     *
     * <p><b>검색 필터</b>: (cn={subjectCn})</p>
     *
     * @param subjectCn 검색할 Subject CN (예: "Test Certificate")
     * @param baseDn Base DN
     * @return 인증서 LDAP 엔트리 (없으면 empty Optional)
     * @throws LdapOperationException 검색 실패 시
     */
    @Override
    public Optional<LdapEntry> searchCertificate(String subjectCn, String baseDn)
            throws LdapOperationException {
        log.debug("=== Certificate LDAP search started ===");
        log.debug("Subject CN: {}", subjectCn);

        try {
            validateConnected();
            validateInputs(subjectCn, baseDn);

            String searchDn = String.format("ou=certificates,%s", baseDn);
            String filterString = String.format("(cn=%s)", escapeLdapFilterValue(subjectCn));

            SearchRequest searchRequest = new SearchRequest(
                searchDn,
                SearchScope.SUB,
                filterString
            );
            searchRequest.setBaseDN(searchDn);

            SearchResult result = connectionPool.search(searchRequest);

            if (result.getSearchEntries().isEmpty()) {
                log.debug("Certificate not found: {}", subjectCn);
                return Optional.empty();
            }

            SearchResultEntry entry = result.getSearchEntries().get(0);
            log.debug("Certificate found: {}", subjectCn);

            return Optional.of(mapSearchEntryToLdapEntry(entry));

        } catch (LdapOperationException e) {
            throw e;
        } catch (LDAPException e) {
            log.error("Failed to search certificate in LDAP", e);
            throw LdapOperationException.searchCertificateFailed(subjectCn);
        } catch (Exception e) {
            log.error("Unexpected error during certificate search", e);
            throw LdapOperationException.searchCertificateFailed(subjectCn);
        }
    }

    /**
     * LDAP에서 CRL 검색
     *
     * <p><b>검색 필터</b>: (issuer={issuerName})</p>
     *
     * @param issuerName 발급자 이름 (예: "CSCA-QA")
     * @param baseDn Base DN
     * @return CRL LDAP 엔트리 목록 (없으면 empty List)
     * @throws LdapOperationException 검색 실패 시
     */
    @Override
    public List<LdapEntry> searchCrls(String issuerName, String baseDn)
            throws LdapOperationException {
        log.debug("=== CRL LDAP search started ===");
        log.debug("Issuer: {}", issuerName);

        try {
            validateConnected();
            validateInputs(issuerName, baseDn);

            String searchDn = String.format("ou=crl,%s", baseDn);
            String filterString = String.format("(issuer=%s)", escapeLdapFilterValue(issuerName));

            SearchRequest searchRequest = new SearchRequest(
                searchDn,
                SearchScope.SUB,
                filterString
            );

            SearchResult result = connectionPool.search(searchRequest);

            List<LdapEntry> entries = new ArrayList<>();
            for (SearchResultEntry entry : result.getSearchEntries()) {
                entries.add(mapSearchEntryToLdapEntry(entry));
            }

            log.debug("Found {} CRL(s) for issuer: {}", entries.size(), issuerName);
            return entries;

        } catch (LdapOperationException e) {
            throw e;
        } catch (LDAPException e) {
            log.error("Failed to search CRLs in LDAP", e);
            throw LdapOperationException.searchCrlFailed(issuerName);
        } catch (Exception e) {
            log.error("Unexpected error during CRL search", e);
            throw LdapOperationException.searchCrlFailed(issuerName);
        }
    }

    /**
     * LDAP 디렉토리 엔트리 삭제
     *
     * <p>업로드된 인증서 또는 CRL을 LDAP에서 제거합니다.</p>
     *
     * @param dn 삭제할 엔트리의 Distinguished Name
     * @return 삭제 성공 여부
     * @throws LdapOperationException 삭제 실패 시
     */
    @Override
    public boolean deleteEntry(String dn) throws LdapOperationException {
        log.debug("=== LDAP entry deletion started ===");
        log.debug("DN: {}", dn);

        try {
            validateConnected();
            if (dn == null || dn.isBlank()) {
                throw new IllegalArgumentException("DN must not be null or blank");
            }

            DeleteRequest deleteRequest = new DeleteRequest(dn);
            LDAPResult result = connectionPool.delete(deleteRequest);

            if (result.getResultCode().equals(ResultCode.SUCCESS)) {
                log.info("LDAP entry deleted: {}", dn);
                return true;
            } else {
                log.warn("Failed to delete LDAP entry: {} - {}", dn, result.getDiagnosticMessage());
                return false;
            }

        } catch (LDAPException e) {
            log.error("Failed to delete LDAP entry", e);
            throw LdapOperationException.deleteEntryFailed(dn, e);
        } catch (Exception e) {
            log.error("Unexpected error during LDAP entry deletion", e);
            throw LdapOperationException.deleteEntryFailed(dn, e);
        }
    }

    /**
     * LDAP 배치 업로드를 위한 연결 유지
     *
     * <p>여러 개의 인증서/CRL을 업로드할 때 사용합니다.
     * 각 업로드마다 연결/연결 해제를 반복하지 않도록 하기 위함입니다.</p>
     *
     * @param timeoutSeconds 타임아웃 시간 (초)
     * @throws LdapConnectionException 연결 유지 실패 시
     */
    @Override
    public void keepAlive(int timeoutSeconds) throws LdapConnectionException {
        log.debug("=== LDAP keep-alive started ===");

        try {
            if (!isConnected()) {
                throw new IllegalStateException("LDAP not connected");
            }

            // Send a keep-alive search to maintain connection
            SearchRequest searchRequest = new SearchRequest(
                baseDn,
                SearchScope.BASE,
                "(objectClass=*)"
            );
            searchRequest.setTimeLimitSeconds(1);

            connectionPool.search(searchRequest);
            log.debug("LDAP keep-alive sent, timeout: {} seconds", timeoutSeconds);

        } catch (IllegalStateException e) {
            throw new LdapConnectionException(e.getMessage());
        } catch (LDAPException e) {
            log.warn("LDAP keep-alive failed", e);
            throw LdapConnectionException.connectionTimeout((long) timeoutSeconds * 1000);
        } catch (Exception e) {
            log.warn("Unexpected error during LDAP keep-alive", e);
            throw LdapConnectionException.connectionTimeout((long) timeoutSeconds * 1000);
        }
    }

    // ========== Helper Methods ==========

    private void validateConnected() throws IllegalStateException {
        if (!connected || connectionPool == null) {
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

    /**
     * DN 값을 이스케이프 처리합니다 (RFC 4514)
     */
    private String escapeDnValue(String value) {
        if (value == null) {
            return "";
        }
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

    /**
     * LDAP 필터 값을 이스케이프 처리합니다 (RFC 4515)
     */
    private String escapeLdapFilterValue(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\5c")
            .replace("*", "\\2a")
            .replace("(", "\\28")
            .replace(")", "\\29")
            .replace("\u0000", "\\00");
    }

    private String getCurrentTimestamp() {
        return new java.text.SimpleDateFormat("yyyyMMddHHmmss'Z'").format(new java.util.Date());
    }

    /**
     * LDIF 파일을 LDAP 디렉토리에 업로드
     *
     * <p><b>기능</b>:</p>
     * <ul>
     *   <li>LDIF 파일 읽기 (UnboundID LDIFReader 사용)</li>
     *   <li>각 엔트리별 ADD 작업 실행</li>
     *   <li>배치 처리로 성능 최적화</li>
     *   <li>성공/실패 통계 반환</li>
     * </ul>
     *
     * <p><b>반환 값</b>:</p>
     * <pre>
     * {
     *   "status": "SUCCESS" | "PARTIAL" | "FAILURE",
     *   "totalCount": 100,
     *   "successCount": 95,
     *   "failureCount": 5,
     *   "results": [
     *     {"dn": "...", "status": "SUCCESS"},
     *     {"dn": "...", "status": "FAILURE", "error": "..."}
     *   ]
     * }
     * </pre>
     *
     * @param ldifInputStream LDIF 파일의 InputStream
     * @return 업로드 결과 Map (status, count, results)
     * @throws LdapOperationException 업로드 실패 시
     */
    public Map<String, Object> uploadLdifFile(InputStream ldifInputStream) throws LdapOperationException {
        log.info("=== LDIF file upload started (Batch Processing) ===");

        try {
            validateConnected();

            Map<String, Object> response = new HashMap<>();
            List<Map<String, String>> uploadResults = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;
            int skippedCount = 0;
            int parseErrorCount = 0;

            // LDIFReader로 LDIF 파일 파싱 (Error-Tolerant Mode)
            LDIFReader ldifReader = new LDIFReader(ldifInputStream);
            com.unboundid.ldif.LDIFRecord ldifRecord;
            int totalCount = 0;
            int lineNumber = 0;

            while (true) {
                try {
                    // 개별 레코드 파싱 오류를 처리
                    ldifRecord = ldifReader.readLDIFRecord();

                    if (ldifRecord == null) {
                        break;
                    }

                    totalCount++;
                    String dn = ldifRecord.getDN();

                    if (dn == null || dn.isEmpty()) {
                        log.warn("⚠️ LDIF record #{} has no DN, skipping", totalCount);
                        uploadResults.add(Map.of(
                            "dn", "unknown",
                            "index", String.valueOf(totalCount),
                            "status", "SKIPPED",
                            "reason", "Missing DN"
                        ));
                        skippedCount++;
                        continue;
                    }

                    // LDIFRecord를 Entry로 변환
                    Entry entry = null;

                    if (ldifRecord instanceof Entry) {
                        // 직접 Entry인 경우
                        entry = (Entry) ldifRecord;
                    } else if (ldifRecord instanceof com.unboundid.ldif.LDIFChangeRecord) {
                        // LDIFChangeRecord 타입이면 ADD 작업 추출
                        LDIFChangeRecord changeRecord = (LDIFChangeRecord) ldifRecord;
                        if (ldifRecord instanceof Entry) {
                            entry = (Entry) ldifRecord;
                        } else {
                            log.debug("⚠️ LDIFChangeRecord is not an Entry: {}", dn);
                            skippedCount++;
                            uploadResults.add(Map.of(
                                "dn", dn,
                                "index", String.valueOf(totalCount),
                                "status", "SKIPPED",
                                "reason", "LDIFChangeRecord (not Entry)"
                            ));
                            continue;
                        }
                    }

                    if (entry == null) {
                        log.warn("⚠️ Could not parse LDIF entry as Entry: {}", dn);
                        skippedCount++;
                        uploadResults.add(Map.of(
                            "dn", dn,
                            "index", String.valueOf(totalCount),
                            "status", "SKIPPED",
                            "reason", "Could not parse as Entry"
                        ));
                        continue;
                    }

                    // AddRequest 생성 및 실행
                    try {
                        AddRequest addRequest = new AddRequest(entry);
                        LDAPResult result = connectionPool.add(addRequest);

                        // 결과 확인
                        if (result.getResultCode() == ResultCode.SUCCESS) {
                            uploadResults.add(Map.of(
                                "dn", dn,
                                "index", String.valueOf(totalCount),
                                "status", "SUCCESS"
                            ));
                            successCount++;
                            log.debug("✅ LDIF entry #{} added: {}", totalCount, dn);
                        } else if (result.getResultCode() == ResultCode.ENTRY_ALREADY_EXISTS) {
                            log.warn("⚠️ Entry #{} already exists: {}", totalCount, dn);
                            skippedCount++;
                            uploadResults.add(Map.of(
                                "dn", dn,
                                "index", String.valueOf(totalCount),
                                "status", "SKIPPED",
                                "reason", "Entry already exists"
                            ));
                        } else {
                            failureCount++;
                            uploadResults.add(Map.of(
                                "dn", dn,
                                "index", String.valueOf(totalCount),
                                "status", "FAILURE",
                                "error", result.getDiagnosticMessage()
                            ));
                            log.error("❌ Failed to add entry #{} {}: {}", totalCount, dn, result.getDiagnosticMessage());
                        }
                    } catch (LDAPException e) {
                        failureCount++;
                        uploadResults.add(Map.of(
                            "dn", dn,
                            "index", String.valueOf(totalCount),
                            "status", "FAILURE",
                            "error", e.getMessage()
                        ));
                        log.error("❌ LDAP exception while adding entry #{} {}: {}", totalCount, dn, e.getMessage());
                    }

                    // Progress logging (every 100 entries)
                    if (totalCount % 100 == 0) {
                        log.info("Processing LDIF entries: {} (Success: {}, Failure: {}, Skipped: {})",
                            totalCount, successCount, failureCount, skippedCount);
                    }

                } catch (com.unboundid.ldif.LDIFException e) {
                    // LDIF 파일 형식 오류: 레코드를 건너뛰고 계속 진행
                    parseErrorCount++;
                    log.warn("⚠️ LDIF parse error at line {}: {} - Skipping this record",
                        e.getLineNumber(), e.getMessage());
                    uploadResults.add(Map.of(
                        "dn", "unknown",
                        "line", String.valueOf(e.getLineNumber()),
                        "status", "PARSE_ERROR",
                        "reason", e.getMessage()
                    ));
                    skippedCount++;
                } catch (IOException e) {
                    // IO 오류: 파일 읽기 실패
                    log.error("❌ IO error while reading LDIF file: {}", e.getMessage());
                    uploadResults.add(Map.of(
                        "dn", "unknown",
                        "status", "IO_ERROR",
                        "reason", e.getMessage()
                    ));
                    break;  // 파일 읽기 중단
                }
            }

            ldifReader.close();

            // Response 생성
            response.put("status", failureCount == 0 && parseErrorCount == 0 ? "SUCCESS" : (successCount > 0 ? "PARTIAL" : "FAILURE"));
            response.put("totalCount", totalCount);
            response.put("successCount", successCount);
            response.put("failureCount", failureCount);
            response.put("skippedCount", skippedCount);
            response.put("parseErrorCount", parseErrorCount);
            response.put("results", uploadResults);
            response.put("message", String.format(
                "LDIF upload completed: %d total, %d success, %d failure, %d skipped, %d parse errors",
                totalCount, successCount, failureCount, skippedCount, parseErrorCount
            ));

            log.info("=== LDIF file upload completed ===");
            log.info("Results: Total={}, Success={}, Failure={}, Skipped={}, ParseErrors={}",
                totalCount, successCount, failureCount, skippedCount, parseErrorCount);

            return response;

        } catch (IOException e) {
            log.error("Error reading LDIF file", e);
            throw new LdapOperationException(e.getMessage(), "uploadLdifFile");
        } catch (Exception e) {
            log.error("Unexpected error during LDIF upload", e);
            throw new LdapOperationException(e.getMessage(), "uploadLdifFile");
        }
    }

    /**
     * CRL LDIF Entry를 LDAP에 업로드
     *
     * LDIF 파일에서 파싱된 CRL Entry를 받아서 DN을 변환하고 LDAP에 업로드합니다.
     *
     * DN 변환 규칙:
     * - 원본: cn=...,o=crl,c=QA,dc=data,dc=download,dc=pkd,dc=icao,dc=int
     * - 변환: cn=...,o=crl,c=QA,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
     * - 규칙: baseDN "dc=icao,dc=int" → "dc=ldap,dc=smartcoreinc,dc=com"로 변환
     *
     * @param crlEntry LDIF에서 파싱된 CRL Entry (Entry 타입)
     * @return 업로드 결과 Map (status, successCount, failureCount, crlDn, results, message)
     * @throws LdapOperationException LDAP 연결 오류 또는 업로드 실패
     */
    public Map<String, Object> uploadCrlToLdap(Entry crlEntry) throws LdapOperationException {
        log.info("=== CRL LDAP upload started ===");

        try {
            validateConnected();

            if (crlEntry == null) {
                throw new LdapOperationException("CRL entry is null", "uploadCrlToLdap");
            }

            String originalDn = crlEntry.getDN();
            log.debug("Original CRL DN: {}", originalDn);

            // 1. DN 변환: dc=icao,dc=int → dc=ldap,dc=smartcoreinc,dc=com
            String transformedDn = transformDn(originalDn);
            log.info("Transformed CRL DN: {}", transformedDn);

            Map<String, Object> response = new HashMap<>();
            List<Map<String, String>> uploadResults = new ArrayList<>();

            // 2. 변환된 DN으로 새 Entry 생성
            Entry transformedEntry = new Entry(transformedDn);

            // 3. 원본 Entry의 모든 속성을 복사
            for (Attribute attribute : crlEntry.getAttributes()) {
                transformedEntry.addAttribute(attribute);
            }

            // 4. LDAP에 추가
            try {
                AddRequest addRequest = new AddRequest(transformedEntry);
                LDAPResult result = connectionPool.add(addRequest);

                int successCount = 0;
                int failureCount = 0;

                if (result.getResultCode() == ResultCode.SUCCESS) {
                    String cn = transformedEntry.getAttributeValue("cn");
                    uploadResults.add(Map.of(
                        "dn", transformedDn,
                        "originalDn", originalDn,
                        "cn", cn != null ? cn : "unknown",
                        "status", "SUCCESS"
                    ));
                    successCount++;
                    log.info("✅ CRL entry added successfully: {}", transformedDn);

                    response.put("status", "SUCCESS");
                    response.put("successCount", successCount);
                    response.put("failureCount", failureCount);
                    response.put("crlDn", transformedDn);
                    response.put("results", uploadResults);
                    response.put("message", "CRL uploaded successfully: " + transformedDn);

                } else if (result.getResultCode() == ResultCode.ENTRY_ALREADY_EXISTS) {
                    log.warn("⚠️ CRL entry already exists: {}", transformedDn);

                    // 기존 엔트리 업데이트 시도 (ModifyRequest)
                    try {
                        List<Modification> modifications = new ArrayList<>();

                        // certificateRevocationList;binary 속성 업데이트
                        Attribute crlAttr = transformedEntry.getAttribute("certificateRevocationList;binary");
                        if (crlAttr != null) {
                            byte[][] byteArrays = crlAttr.getValueByteArrays();
                            if (byteArrays != null && byteArrays.length > 0) {
                                modifications.add(new Modification(ModificationType.REPLACE,
                                    "certificateRevocationList;binary", byteArrays[0]));
                            }
                        }

                        ModifyRequest modifyRequest = new ModifyRequest(transformedDn,
                            modifications.toArray(new Modification[0]));

                        LDAPResult modifyResult = connectionPool.modify(modifyRequest);
                        if (modifyResult.getResultCode() == ResultCode.SUCCESS) {
                            uploadResults.add(Map.of(
                                "dn", transformedDn,
                                "originalDn", originalDn,
                                "status", "UPDATED"
                            ));
                            successCount++;
                            log.info("✅ CRL entry updated successfully: {}", transformedDn);

                            response.put("status", "SUCCESS");
                            response.put("successCount", successCount);
                            response.put("failureCount", failureCount);
                            response.put("crlDn", transformedDn);
                            response.put("results", uploadResults);
                            response.put("message", "CRL updated successfully: " + transformedDn);
                        } else {
                            failureCount++;
                            uploadResults.add(Map.of(
                                "dn", transformedDn,
                                "originalDn", originalDn,
                                "status", "UPDATE_FAILED",
                                "error", modifyResult.getDiagnosticMessage()
                            ));
                            log.error("❌ Failed to update CRL entry {}: {}", transformedDn, modifyResult.getDiagnosticMessage());

                            response.put("status", "FAILURE");
                            response.put("successCount", successCount);
                            response.put("failureCount", failureCount);
                            response.put("crlDn", transformedDn);
                            response.put("results", uploadResults);
                            response.put("message", "Failed to update CRL: " + modifyResult.getDiagnosticMessage());
                        }
                    } catch (LDAPException e) {
                        failureCount++;
                        uploadResults.add(Map.of(
                            "dn", transformedDn,
                            "originalDn", originalDn,
                            "status", "UPDATE_FAILED",
                            "error", e.getMessage()
                        ));
                        log.error("❌ LDAP exception while updating CRL entry {}: {}", transformedDn, e.getMessage());

                        response.put("status", "FAILURE");
                        response.put("successCount", successCount);
                        response.put("failureCount", failureCount);
                        response.put("crlDn", transformedDn);
                        response.put("results", uploadResults);
                        response.put("message", "Failed to update CRL: " + e.getMessage());
                    }
                } else {
                    failureCount++;
                    uploadResults.add(Map.of(
                        "dn", transformedDn,
                        "originalDn", originalDn,
                        "status", "FAILURE",
                        "error", result.getDiagnosticMessage()
                    ));
                    log.error("❌ Failed to add CRL entry {}: {}", transformedDn, result.getDiagnosticMessage());

                    response.put("status", "FAILURE");
                    response.put("successCount", 0);
                    response.put("failureCount", failureCount);
                    response.put("crlDn", transformedDn);
                    response.put("results", uploadResults);
                    response.put("message", "Failed to upload CRL: " + result.getDiagnosticMessage());
                }

                log.info("=== CRL LDAP upload completed ===");
                return response;

            } catch (LDAPException e) {
                log.error("❌ LDAP exception during CRL upload: {}", e.getMessage());
                uploadResults.add(Map.of(
                    "dn", transformedDn,
                    "originalDn", originalDn,
                    "status", "FAILURE",
                    "error", e.getMessage()
                ));

                response.put("status", "FAILURE");
                response.put("successCount", 0);
                response.put("failureCount", 1);
                response.put("crlDn", transformedDn);
                response.put("results", uploadResults);
                response.put("message", "CRL upload failed: " + e.getMessage());

                return response;
            }

        } catch (LdapOperationException e) {
            log.error("❌ LDAP operation error: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "ERROR");
            response.put("successCount", 0);
            response.put("failureCount", 1);
            response.put("message", "CRL upload error: " + e.getMessage());
            return response;
        } catch (Exception e) {
            log.error("❌ Unexpected error during CRL upload: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "ERROR");
            response.put("successCount", 0);
            response.put("failureCount", 1);
            response.put("message", "Unexpected error: " + e.getMessage());
            return response;
        }
    }

    /**
     * DN 변환: baseDN "dc=icao,dc=int" → "dc=ldap,dc=smartcoreinc,dc=com"
     *
     * 예시:
     * - Input: cn=...,o=crl,c=QA,dc=data,dc=download,dc=pkd,dc=icao,dc=int
     * - Output: cn=...,o=crl,c=QA,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
     *
     * @param originalDn LDIF 파일의 원본 DN
     * @return 변환된 DN
     */
    private String transformDn(String originalDn) {
        if (originalDn == null) {
            return null;
        }
        // baseDN 변환: dc=icao,dc=int → dc=ldap,dc=smartcoreinc,dc=com
        return originalDn.replaceAll("dc=icao,dc=int$", "dc=ldap,dc=smartcoreinc,dc=com");
    }

    /**
     * SearchResultEntry를 LdapEntry로 변환
     */
    private LdapEntry mapSearchEntryToLdapEntry(SearchResultEntry entry) {
        String dn = entry.getDN();
        String cn = entry.getAttributeValue("cn");

        byte[] certificateContent = null;
        Attribute certAttr = entry.getAttribute("certificateContent");
        if (certAttr != null) {
            certificateContent = certAttr.getValueByteArray();
        }

        byte[] crlContent = null;
        Attribute crlAttr = entry.getAttribute("crlContent");
        if (crlAttr != null) {
            crlContent = crlAttr.getValueByteArray();
        }

        String uploadedAt = entry.getAttributeValue("uploadedAt");

        return new LdapEntry(dn, cn, certificateContent, crlContent, uploadedAt);
    }
}
