package com.smartcoreinc.localpkd.certificatevalidation.domain.port;

import java.util.List;
import java.util.Optional;

/**
 * LdapConnectionPort - LDAP Directory Connection Port (Hexagonal Architecture)
 *
 * <p><b>Purpose</b>: Defines the contract for LDAP directory operations without coupling
 * to a specific LDAP implementation (Spring LDAP, UnboundID, etc.)</p>
 *
 * <p><b>Design Pattern</b>: Hexagonal Architecture (Ports & Adapters)
 * <ul>
 *   <li>Port: Domain Layer interface defining how to interact with LDAP</li>
 *   <li>Adapter: Infrastructure Layer implementation (Spring LDAP)</li>
 *   <li>Domain independence: Domain layer depends on this interface, not on LDAP library</li>
 * </ul>
 * </p>
 *
 * <p><b>Bounded Context</b>: Certificate Validation Context
 * <ul>
 *   <li>Provides LDAP connectivity for certificate/CRL upload operations</li>
 *   <li>Used by UploadToLdapUseCase in application layer</li>
 *   <li>Implemented by SpringLdapConnectionAdapter in infrastructure layer</li>
 * </ul>
 * </p>
 *
 * <p><b>주요 책임</b>:
 * <ul>
 *   <li>LDAP 서버 연결/연결 해제</li>
 *   <li>인증서/CRL을 LDAP 디렉토리에 업로드</li>
 *   <li>LDAP에서 인증서/CRL 검색</li>
 *   <li>디렉토리 엔트리 생성/수정/삭제</li>
 *   <li>Connection Pooling 및 에러 처리</li>
 * </ul>
 * </p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // 1. LDAP 연결
 * ldapConnectionPort.connect();
 *
 * // 2. 인증서 업로드
 * String dn = ldapConnectionPort.uploadCertificate(
 *     certificate,
 *     "cn=subject,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"
 * );
 *
 * // 3. 연결 확인
 * if (ldapConnectionPort.isConnected()) {
 *     log.info("LDAP connected: {}", ldapConnectionPort.getConnectionStatus());
 * }
 *
 * // 4. 인증서 검색
 * Optional<LdapEntry> entry = ldapConnectionPort.searchCertificate("CN=subject");
 *
 * // 5. 연결 해제
 * ldapConnectionPort.disconnect();
 * }</pre>
 *
 * <p><b>에러 처리</b>:
 * <ul>
 *   <li>LdapConnectionException: 연결 실패, 타임아웃, 인증 실패</li>
 *   <li>LdapOperationException: LDAP 작업 실패 (업로드, 검색, 삭제)</li>
 * </ul>
 * </p>
 *
 * <p><b>Phase 17</b>: LDAP Integration foundation for real certificate upload implementation</p>
 *
 * @see LdapConnectionException
 * @see LdapOperationException
 * @see LdapEntry
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24 (Phase 17 Task 1.5)
 */
public interface LdapConnectionPort {

    /**
     * LDAP 서버에 연결
     *
     * <p>LDAP 서버에 대한 연결을 초기화합니다.
     * 연결 풀링, 타임아웃, SSL/TLS 설정 등은 구현체에서 관리합니다.</p>
     *
     * @throws LdapConnectionException 연결 실패 시
     * @throws IllegalStateException 이미 연결되어 있을 경우
     * @since Phase 17 Task 1.5
     */
    void connect();

    /**
     * LDAP 서버로부터 연결 해제
     *
     * <p>LDAP 연결을 안전하게 종료합니다.
     * 리소스 해제 및 연결 풀 반환 작업을 수행합니다.</p>
     *
     * @throws LdapConnectionException 연결 해제 실패 시
     * @since Phase 17 Task 1.5
     */
    void disconnect();

    /**
     * 현재 LDAP 연결 상태 확인
     *
     * <p>true: 연결 상태, false: 비연결 상태</p>
     *
     * @return 연결 여부
     * @since Phase 17 Task 1.5
     */
    boolean isConnected();

    /**
     * LDAP 연결 상태 상세 정보 조회
     *
     * <p>사용 예시</p>
     * <pre>{@code
     * String status = ldapConnectionPort.getConnectionStatus();
     * // Output: "Connected to ldap://192.168.100.10:389, Base DN: dc=ldap,dc=smartcoreinc,dc=com"
     * }</pre>
     *
     * @return 연결 상태 문자열 (서버 주소, Base DN 등)
     * @since Phase 17 Task 1.5
     */
    String getConnectionStatus();

    /**
     * 인증서를 LDAP 디렉토리에 업로드
     *
     * <p><b>LDAP Entry 생성 규칙</b>:</p>
     * <pre>
     * dn: cn={subject_cn},o=ml,c={country_code},dc=data,dc=download,dc=pkd,{base_dn}
     * objectClass: top
     * objectClass: person
     * objectClass: pkdMasterList
     * objectClass: pkdDownload
     * cn: {subject_cn}
     * sn: 1
     * pkdVersion: 70
     * pkdMasterListContent:: {base64_encoded_certificate}
     * </pre>
     *
     * @param certificateDer DER 인코딩된 X.509 인증서 바이트 배열
     * @param subjectCn 인증서 Subject CN (예: "CSCA Kuwait")
     * @param countryCode 국가 코드 (ISO 3166-1 alpha-2, 예: "KW", "KR", "US")
     * @param baseDn LDAP Base DN (예: "dc=ldap,dc=smartcoreinc,dc=com")
     * @return 업로드된 인증서의 LDAP Distinguished Name (DN)
     * @throws LdapOperationException 업로드 실패 시
     * @throws IllegalArgumentException 입력값 null/empty 시
     * @throws IllegalStateException 연결되지 않았을 때
     * @since Phase 17 Task 1.5 (Updated: 2025-11-21 - Added countryCode parameter)
     */
    String uploadCertificate(byte[] certificateDer, String subjectCn, String countryCode, String baseDn);

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
     * @throws IllegalArgumentException 입력값 null/empty 시
     * @throws IllegalStateException 연결되지 않았을 때
     * @since Phase 17 Task 1.5
     */
    String uploadCrl(byte[] crlDer, String issuerName, String baseDn);

    /**
     * LDAP에서 인증서 검색
     *
     * <p><b>검색 필터</b>: (cn={subjectCn})</p>
     *
     * @param subjectCn 검색할 Subject CN (예: "Test Certificate")
     * @param baseDn Base DN
     * @return 인증서 LDAP 엔트리 (없으면 empty Optional)
     * @throws LdapOperationException 검색 실패 시
     * @throws IllegalArgumentException 입력값 null/empty 시
     * @throws IllegalStateException 연결되지 않았을 때
     * @since Phase 17 Task 1.5
     */
    Optional<LdapEntry> searchCertificate(String subjectCn, String baseDn);

    /**
     * LDAP에서 CRL 검색
     *
     * <p><b>검색 필터</b>: (issuer={issuerName})</p>
     *
     * @param issuerName 발급자 이름 (예: "CSCA-QA")
     * @param baseDn Base DN
     * @return CRL LDAP 엔트리 목록 (없으면 empty List)
     * @throws LdapOperationException 검색 실패 시
     * @throws IllegalArgumentException 입력값 null/empty 시
     * @throws IllegalStateException 연결되지 않았을 때
     * @since Phase 17 Task 1.5
     */
    List<LdapEntry> searchCrls(String issuerName, String baseDn);

    /**
     * LDAP 디렉토리 엔트리 삭제
     *
     * <p>업로드된 인증서 또는 CRL을 LDAP에서 제거합니다.</p>
     *
     * @param dn 삭제할 엔트리의 Distinguished Name
     * @return 삭제 성공 여부
     * @throws LdapOperationException 삭제 실패 시
     * @throws IllegalArgumentException dn이 null/empty일 경우
     * @throws IllegalStateException 연결되지 않았을 때
     * @since Phase 17 Task 1.5
     */
    boolean deleteEntry(String dn);

    /**
     * LDAP 배치 업로드를 위한 연결 유지
     *
     * <p>여러 개의 인증서/CRL을 업로드할 때 사용합니다.
     * 각 업로드마다 연결/연결 해제를 반복하지 않도록 하기 위함입니다.</p>
     *
     * @param timeoutSeconds 타임아웃 시간 (초)
     * @throws LdapConnectionException 연결 유지 실패 시
     * @throws IllegalStateException 연결되지 않았을 때
     * @since Phase 17 Task 1.5
     */
    void keepAlive(int timeoutSeconds);

    /**
     * 기본 LDAP Entry 데이터 클래스
     *
     * <p>LDAP에서 조회한 엔트리 정보를 담는 DTO</p>
     */
    class LdapEntry {
        private final String dn;                    // Distinguished Name
        private final String cn;                    // Common Name
        private final byte[] certificateContent;    // DER-encoded certificate (optional)
        private final byte[] crlContent;            // DER-encoded CRL (optional)
        private final String uploadedAt;            // Timestamp

        public LdapEntry(String dn, String cn, byte[] certificateContent,
                        byte[] crlContent, String uploadedAt) {
            this.dn = dn;
            this.cn = cn;
            this.certificateContent = certificateContent;
            this.crlContent = crlContent;
            this.uploadedAt = uploadedAt;
        }

        public String getDn() { return dn; }
        public String getCn() { return cn; }
        public byte[] getCertificateContent() { return certificateContent; }
        public byte[] getCrlContent() { return crlContent; }
        public String getUploadedAt() { return uploadedAt; }
    }
}
