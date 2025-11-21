package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.adapter;

import com.smartcoreinc.localpkd.certificatevalidation.domain.exception.LdapConnectionException;
import com.smartcoreinc.localpkd.certificatevalidation.domain.exception.LdapOperationException;
import com.smartcoreinc.localpkd.certificatevalidation.domain.port.LdapConnectionPort;
import com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter.UnboundIdLdapAdapter;
import com.unboundid.ldap.sdk.LDAPException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * UnboundIdLdapConnectionAdapter - UnboundID SDK 기반 LDAP 연결 어댑터
 *
 * <p><b>목적</b>: LdapConnectionPort를 구현하여 UnboundID SDK를 통한 LDAP 연결 및 업로드를 제공합니다.</p>
 *
 * <p><b>설계 패턴</b>: Hexagonal Architecture (Port & Adapter)
 * <ul>
 *   <li>Port: LdapConnectionPort (Domain Layer)</li>
 *   <li>Adapter: UnboundIdLdapConnectionAdapter (Infrastructure Layer)</li>
 *   <li>Delegation: UnboundIdLdapAdapter로 실제 LDAP 작업 위임</li>
 * </ul>
 * </p>
 *
 * <p><b>주요 기능</b>:
 * <ul>
 *   <li>Certificate 도메인 객체 → LDIF 엔트리 변환</li>
 *   <li>LDAP DIT 구조 자동 생성 (dc=data,dc=download,dc=pkd → c=<Country> → o=ml)</li>
 *   <li>UnboundIdLdapAdapter를 통한 LDAP 작업 위임</li>
 *   <li>예외 변환 (LDAPException → Domain Exceptions)</li>
 * </ul>
 * </p>
 *
 * <p><b>LDAP DIT 구조</b>:
 * <pre>
 * dc=ldap,dc=smartcoreinc,dc=com (Root DN)
 * └── dc=data,dc=download,dc=pkd
 *     └── c=<Country Code> (예: FR, NZ, KR)
 *         └── o=ml (Master List)
 *             └── cn=<Certificate Subject> (인증서 엔트리)
 * </pre>
 * </p>
 *
 * <p><b>LDIF Entry 형식</b>:
 * <pre>
 * dn: cn=CN\=CSCA-FRANCE\,O\=Gouv\,C\=FR,o=ml,c=FR,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
 * objectClass: top
 * objectClass: person
 * objectClass: pkdMasterList
 * objectClass: pkdDownload
 * cn: CN=CSCA-FRANCE,O=Gouv,C=FR
 * sn: 1
 * pkdMasterListContent:: [base64 encoded certificate]
 * </pre>
 * </p>
 *
 * <p><b>Configuration</b>:
 * <pre>
 * # application.properties
 * ldap.adapter.type=unboundid  # 이 어댑터 활성화
 * spring.ldap.urls=ldap://192.168.100.10:389
 * spring.ldap.base=dc=ldap,dc=smartcoreinc,dc=com
 * spring.ldap.username=cn=admin,dc=ldap,dc=smartcoreinc,dc=com
 * spring.ldap.password=core
 * </pre>
 * </p>
 *
 * @see LdapConnectionPort
 * @see UnboundIdLdapAdapter
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ldap.adapter.type", havingValue = "unboundid", matchIfMissing = false)
public class UnboundIdLdapConnectionAdapter implements LdapConnectionPort {

    private final UnboundIdLdapAdapter unboundIdLdapAdapter;

    /**
     * LDAP 연결 초기화
     *
     * <p>애플리케이션 시작 시 자동으로 LDAP 연결을 초기화합니다.</p>
     */
    @PostConstruct
    @Override
    public void connect() {
        log.info("=== UnboundIdLdapConnectionAdapter: Connecting to LDAP ===");
        try {
            unboundIdLdapAdapter.connect();
            log.info("LDAP connection successful");
        } catch (LDAPException e) {
            log.error("LDAP connection failed", e);
            throw new LdapConnectionException(
                "Failed to connect to LDAP: " + e.getMessage(),
                e
            );
        }
    }

    @Override
    public void disconnect() {
        log.info("=== UnboundIdLdapConnectionAdapter: Disconnecting from LDAP ===");
        unboundIdLdapAdapter.disconnect();
        log.info("LDAP connection closed");
    }

    @Override
    public boolean isConnected() {
        return unboundIdLdapAdapter.isConnected();
    }

    @Override
    public String getConnectionStatus() {
        if (unboundIdLdapAdapter.isConnected()) {
            return unboundIdLdapAdapter.getConnectionPoolStats();
        }
        return "Not connected";
    }

    /**
     * 인증서를 LDAP에 업로드
     *
     * <p><b>처리 흐름</b>:
     * <ol>
     *   <li>입력값 검증 (country code 포함)</li>
     *   <li>DN 생성: cn=<Subject CN>,o=ml,c=<Country>,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com</li>
     *   <li>Certificate DER → Base64 인코딩</li>
     *   <li>LDIF 엔트리 텍스트 생성 (pkdMasterList objectClass)</li>
     *   <li>UnboundIdLdapAdapter.addLdifEntry() 호출</li>
     *   <li>부모 노드 자동 생성 (ensureParentEntriesExist)</li>
     * </ol>
     * </p>
     *
     * @param certificateDer DER 인코딩된 X.509 인증서
     * @param subjectCn 인증서 Subject CN (예: "CSCA Kuwait", "Singapore Passport CA")
     * @param countryCode 국가 코드 (ISO 3166-1 alpha-2, 예: "KW", "SG", "KR")
     * @param baseDn Base DN (예: "dc=ldap,dc=smartcoreinc,dc=com")
     * @return 업로드된 인증서의 LDAP DN
     */
    @Override
    public String uploadCertificate(byte[] certificateDer, String subjectCn, String countryCode, String baseDn) {
        log.debug("=== uploadCertificate started ===");
        log.debug("Subject CN: {}, Country Code: {}, Base DN: {}", subjectCn, countryCode, baseDn);

        try {
            // 1. 입력값 검증 (country code 포함)
            validateCertificateInputs(certificateDer, subjectCn, countryCode, baseDn);

            // 2. Country Code 정규화 (대문자 변환)
            String normalizedCountryCode = countryCode.trim().toUpperCase();

            // 3. DN 생성 (ICAO PKD DIT 구조)
            String dn = buildCertificateDn(subjectCn, normalizedCountryCode, baseDn);
            log.debug("Generated DN: {}", dn);

            // 4. Base64 인코딩
            String base64Cert = Base64.getEncoder().encodeToString(certificateDer);

            // 5. LDIF 엔트리 텍스트 생성
            String ldifEntry = buildCertificateLdifEntry(dn, subjectCn, base64Cert);

            log.debug("LDIF Entry:\n{}", ldifEntry);

            // 6. LDAP에 추가 (부모 노드 자동 생성)
            boolean success = unboundIdLdapAdapter.addLdifEntry(ldifEntry);

            if (!success) {
                // Duplicate entry는 경고로 처리 (성공으로 간주)
                log.warn("Certificate already exists in LDAP (duplicate), skipping: {}", dn);
                return dn;
            }

            log.info("Certificate uploaded successfully: {}", dn);
            return dn;

        } catch (LDAPException e) {
            log.error("LDAP operation failed", e);
            throw new LdapOperationException(
                "Failed to upload certificate: " + e.getMessage(),
                e
            );
        } catch (Exception e) {
            log.error("Unexpected error during certificate upload", e);
            throw new LdapOperationException(
                "Unexpected error: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * CRL을 LDAP에 업로드
     *
     * @param crlDer DER 인코딩된 CRL
     * @param issuerName 발급자 이름
     * @param baseDn Base DN
     * @return 업로드된 CRL의 LDAP DN
     */
    @Override
    public String uploadCrl(byte[] crlDer, String issuerName, String baseDn) {
        log.debug("=== uploadCrl started ===");
        log.debug("Issuer: {}, Base DN: {}", issuerName, baseDn);

        try {
            // 1. 입력값 검증
            validateInputs(crlDer, issuerName, baseDn);

            // 2. Country Code 추출
            String countryCode = extractCountryCode(issuerName);
            if (countryCode == null) {
                throw new LdapOperationException(
                    "Cannot extract country code from Issuer name: " + issuerName
                );
            }

            // 3. DN 생성
            String dn = buildCrlDn(issuerName, countryCode, baseDn);
            log.debug("Generated CRL DN: {}", dn);

            // 4. Base64 인코딩
            String base64Crl = Base64.getEncoder().encodeToString(crlDer);

            // 5. LDIF 엔트리 텍스트 생성
            String ldifEntry = buildCrlLdifEntry(dn, issuerName, base64Crl);

            // 6. LDAP에 추가
            boolean success = unboundIdLdapAdapter.addLdifEntry(ldifEntry);

            if (!success) {
                // Duplicate entry는 경고로 처리 (성공으로 간주)
                log.warn("CRL already exists in LDAP (duplicate), skipping: {}", dn);
                return dn;
            }

            log.info("CRL uploaded successfully: {}", dn);
            return dn;

        } catch (LDAPException e) {
            log.error("LDAP operation failed", e);
            throw new LdapOperationException(
                "Failed to upload CRL: " + e.getMessage(),
                e
            );
        } catch (Exception e) {
            log.error("Unexpected error during CRL upload", e);
            throw new LdapOperationException(
                "Unexpected error: " + e.getMessage(),
                e
            );
        }
    }

    @Override
    public Optional<LdapEntry> searchCertificate(String subjectCn, String baseDn) {
        log.warn("searchCertificate() not implemented yet");
        return Optional.empty();
    }

    @Override
    public List<LdapEntry> searchCrls(String issuerName, String baseDn) {
        log.warn("searchCrls() not implemented yet");
        return List.of();
    }

    @Override
    public boolean deleteEntry(String dn) {
        log.debug("=== deleteEntry started: {} ===", dn);
        try {
            return unboundIdLdapAdapter.deleteEntry(dn);
        } catch (LDAPException e) {
            log.error("Failed to delete entry: {}", dn, e);
            throw new LdapOperationException(
                "Failed to delete entry: " + e.getMessage(),
                e
            );
        }
    }

    @Override
    public void keepAlive(int timeoutSeconds) {
        log.debug("keepAlive({} seconds)", timeoutSeconds);
        // UnboundIdLdapAdapter는 Connection Pool을 사용하므로 별도 keep-alive 불필요
    }

    /**
     * Certificate DN 생성
     *
     * <p>형식: cn={subject_cn},o=ml,c={country},dc=data,dc=download,dc=pkd,{base_dn}</p>
     *
     * @param subjectCn 인증서 Subject CN
     * @param countryCode 국가 코드 (2자리)
     * @param baseDn Base DN
     * @return 생성된 DN
     */
    private String buildCertificateDn(String subjectCn, String countryCode, String baseDn) {
        // CN escape: 쉼표와 등호를 백슬래시로 이스케이프
        String escapedCn = escapeDnValue(subjectCn);

        return String.format(
            "cn=%s,o=ml,c=%s,dc=data,dc=download,dc=pkd,%s",
            escapedCn,
            countryCode.toUpperCase(),
            baseDn
        );
    }

    /**
     * CRL DN 생성
     *
     * <p>형식: cn={issuer_name},o=ml,c={country},dc=data,dc=download,dc=pkd,{base_dn}</p>
     *
     * @param issuerName 발급자 이름
     * @param countryCode 국가 코드
     * @param baseDn Base DN
     * @return 생성된 DN
     */
    private String buildCrlDn(String issuerName, String countryCode, String baseDn) {
        String escapedIssuer = escapeDnValue(issuerName);

        return String.format(
            "cn=%s-CRL,o=ml,c=%s,dc=data,dc=download,dc=pkd,%s",
            escapedIssuer,
            countryCode.toUpperCase(),
            baseDn
        );
    }

    /**
     * Certificate LDIF 엔트리 텍스트 생성
     *
     * @param dn Distinguished Name
     * @param cn Common Name
     * @param base64Cert Base64 인코딩된 인증서
     * @return LDIF 엔트리 텍스트
     */
    private String buildCertificateLdifEntry(String dn, String cn, String base64Cert) {
        return String.format(
            "dn: %s\n" +
            "objectClass: top\n" +
            "objectClass: person\n" +
            "objectClass: pkdMasterList\n" +
            "objectClass: pkdDownload\n" +
            "cn: %s\n" +
            "sn: 1\n" +
            "pkdVersion: 70\n" +
            "pkdMasterListContent:: %s\n",
            dn,
            cn,
            base64Cert
        );
    }

    /**
     * CRL LDIF 엔트리 텍스트 생성
     *
     * @param dn Distinguished Name
     * @param cn Common Name
     * @param base64Crl Base64 인코딩된 CRL
     * @return LDIF 엔트리 텍스트
     */
    private String buildCrlLdifEntry(String dn, String cn, String base64Crl) {
        return String.format(
            "dn: %s\n" +
            "objectClass: top\n" +
            "objectClass: person\n" +
            "objectClass: pkdMasterList\n" +
            "objectClass: pkdDownload\n" +
            "cn: %s\n" +
            "sn: 1\n" +
            "pkdVersion: 70\n" +
            "pkdMasterListContent:: %s\n",
            dn,
            cn,
            base64Crl
        );
    }

    /**
     * Subject CN에서 Country Code 추출
     *
     * <p>예: "CN=CSCA-FRANCE,O=Gouv,C=FR" → "FR"</p>
     *
     * @param subjectCn Subject CN 문자열
     * @return 2자리 국가 코드 (없으면 null)
     */
    private String extractCountryCode(String subjectCn) {
        if (subjectCn == null || subjectCn.isEmpty()) {
            return null;
        }

        // C=XX 패턴 찾기 (마지막 C= 부분)
        int lastCIndex = subjectCn.lastIndexOf("C=");
        if (lastCIndex == -1) {
            return null;
        }

        // C= 이후 2글자 추출
        int startIndex = lastCIndex + 2;
        if (startIndex + 2 > subjectCn.length()) {
            return null;
        }

        String countryCode = subjectCn.substring(startIndex, startIndex + 2);

        // 알파벳 대문자 2글자인지 검증
        if (!countryCode.matches("[A-Z]{2}")) {
            return null;
        }

        return countryCode;
    }

    /**
     * DN 값 이스케이프
     *
     * <p>LDAP DN에서 특수 문자를 이스케이프합니다:
     * <ul>
     *   <li>, → \,</li>
     *   <li>= → \=</li>
     *   <li>+ → \+</li>
     *   <li># → \#</li>
     *   <li>; → \;</li>
     * </ul>
     * </p>
     *
     * @param value 원본 값
     * @return 이스케이프된 값
     */
    private String escapeDnValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        return value
            .replace("\\", "\\\\")  // 백슬래시 먼저
            .replace(",", "\\,")
            .replace("=", "\\=")
            .replace("+", "\\+")
            .replace("#", "\\#")
            .replace(";", "\\;");
    }

    /**
     * 인증서 업로드 입력값 검증 (country code 포함)
     *
     * @param certificateDer 인증서 DER 바이트 배열
     * @param subjectCn Subject CN
     * @param countryCode 국가 코드 (ISO 3166-1 alpha-2)
     * @param baseDn Base DN
     */
    private void validateCertificateInputs(byte[] certificateDer, String subjectCn,
                                          String countryCode, String baseDn) {
        if (certificateDer == null || certificateDer.length == 0) {
            throw new IllegalArgumentException("Certificate DER must not be null or empty");
        }
        if (subjectCn == null || subjectCn.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject CN must not be null or empty");
        }
        if (countryCode == null || countryCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Country code must not be null or empty");
        }
        if (!countryCode.trim().matches("[A-Za-z]{2}")) {
            throw new IllegalArgumentException(
                "Country code must be 2 alphabetic characters (ISO 3166-1 alpha-2): " + countryCode
            );
        }
        if (baseDn == null || baseDn.trim().isEmpty()) {
            throw new IllegalArgumentException("Base DN must not be null or empty");
        }
    }

    /**
     * 입력값 검증 (CRL 용)
     *
     * @param data 바이트 배열
     * @param name 이름 (CN 또는 Issuer Name)
     * @param baseDn Base DN
     */
    private void validateInputs(byte[] data, String name, String baseDn) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data must not be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name must not be null or empty");
        }
        if (baseDn == null || baseDn.trim().isEmpty()) {
            throw new IllegalArgumentException("Base DN must not be null or empty");
        }
    }
}
