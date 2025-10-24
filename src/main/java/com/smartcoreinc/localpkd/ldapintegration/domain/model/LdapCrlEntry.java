package com.smartcoreinc.localpkd.ldapintegration.domain.model;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * LdapCrlEntry - LDAP에 저장된 인증서 폐기 목록(CRL) 항목
 *
 * <p>LDAP 디렉토리에 저장된 X.509 CRL을 표현합니다.</p>
 *
 * <h3>LDAP DN 구조</h3>
 * <pre>{@code
 * cn=CSCA-KOREA,ou=crl,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com
 * }</pre>
 *
 * <h3>LDAP Attributes</h3>
 * <ul>
 *   <li>{@code cn}: Common Name (Issuer CN)</li>
 *   <li>{@code x509crl}: Base64 인코딩된 CRL 바이너리</li>
 *   <li>{@code issuerDN}: CRL 발급자 DN</li>
 *   <li>{@code issuerName}: 발급자 이름</li>
 *   <li>{@code countryCode}: 국가 코드 (2자리)</li>
 *   <li>{@code thisUpdate}: CRL 발행일시</li>
 *   <li>{@code nextUpdate}: 다음 CRL 발행 예정일</li>
 *   <li>{@code revokedSerialNumbers}: 폐기된 인증서 시리얼 번호 목록</li>
 *   <li>{@code lastSyncAt}: 마지막 동기화 시간</li>
 * </ul>
 *
 * <h3>비즈니스 규칙</h3>
 * <ul>
 *   <li>DN은 null일 수 없음</li>
 *   <li>x509crlBase64는 null일 수 없음</li>
 *   <li>issuerDN은 null일 수 없음</li>
 *   <li>thisUpdate와 nextUpdate는 null일 수 없음</li>
 *   <li>만료된 CRL은 {@code isExpired()}로 확인</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // CertificateRevocationList에서 LdapCrlEntry 생성
 * LdapCrlEntry entry = LdapCrlEntry.createFromCertificateRevocationList(
 *     certificateRevocationList
 * );
 *
 * // LDAP에 저장하기 전 검증
 * if (entry.isExpired()) {
 *     log.warn("CRL has expired: {}", entry.getIssuerDn());
 * }
 *
 * // 동기화 완료 표시
 * entry.markAsSynced();
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Slf4j
@Getter
@Setter
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LdapCrlEntry {

    /**
     * LDAP Distinguished Name
     * 예: cn=CSCA-KOREA,ou=crl,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com
     */
    @EqualsAndHashCode.Include
    private DistinguishedName dn;

    /**
     * 원본 CertificateRevocationList의 ID
     * 로컬 DB와의 매핑을 위해 저장
     */
    private UUID crlId;

    /**
     * CRL 발급자 DN (Distinguished Name)
     * Trust Chain Building에서 사용
     */
    private String issuerDn;

    /**
     * CRL 발급자 이름 (Issuer CN)
     * LDAP 검색 시 사용
     */
    private String issuerName;

    /**
     * 발급자의 국가 코드 (2자리, 대문자)
     * 예: KR, JP, US, CN
     */
    private String countryCode;

    /**
     * X.509 CRL (Base64 인코딩)
     * DER 형식의 바이너리를 Base64로 인코딩하여 저장
     */
    private String x509CrlBase64;

    /**
     * CRL 발행일시 (thisUpdate)
     */
    private LocalDateTime thisUpdate;

    /**
     * 다음 CRL 발행 예정일 (nextUpdate)
     */
    private LocalDateTime nextUpdate;

    /**
     * 폐기된 인증서 시리얼 번호 목록
     * 쉼표로 구분된 16진수 시리얼 번호
     * 예: "0a1b2c3d,4e5f6a7b,8c9d0e1f"
     */
    private String revokedSerialNumbers;

    /**
     * 마지막 동기화 시간
     */
    private LocalDateTime lastSyncAt;

    /**
     * CertificateRevocationList Aggregate에서 LdapCrlEntry 생성
     *
     * <p>CRL 정보를 LDAP 형식으로 변환합니다.</p>
     *
     * @param crl 원본 CRL
     * @return LdapCrlEntry
     * @throws DomainException 변환 중 오류 발생 시
     */
    public static LdapCrlEntry createFromCertificateRevocationList(CertificateRevocationList crl) {

        if (crl == null) {
            throw new DomainException(
                "CRL_NOT_NULL",
                "CertificateRevocationList must not be null"
            );
        }

        try {
            // CN (Common Name) 생성
            String issuerDnStr = crl.getIssuerName().getValue();
            String cn = extractCommonName(issuerDnStr);

            // DN 생성
            String dnValue = String.format("cn=%s,%s",
                    sanitizeCnForLdap(cn),
                    buildCrlBaseDn());
            DistinguishedName dn = DistinguishedName.of(dnValue);

            // 폐기된 시리얼 번호 문자열 (Set을 세미콜론으로 구분된 String으로 변환)
            String revokedSerials = serializeRevokedSerialNumbers(
                    crl.getRevokedCertificates().getSerialNumbers());

            // Entry 빌드
            return LdapCrlEntry.builder()
                    .dn(dn)
                    .crlId(crl.getId().getId())
                    .issuerDn(issuerDnStr)
                    .issuerName(cn)
                    .countryCode(crl.getCountryCode().getValue())
                    .x509CrlBase64(encodeToBase64(crl.getX509CrlData().getCrlBinary()))
                    .thisUpdate(crl.getValidityPeriod().getNotBefore())
                    .nextUpdate(crl.getValidityPeriod().getNotAfter())
                    .revokedSerialNumbers(revokedSerials)
                    .lastSyncAt(null)  // 아직 동기화되지 않음
                    .build();

        } catch (Exception e) {
            log.error("Failed to create LDAP CRL entry: {}", e.getMessage(), e);
            throw new DomainException(
                "LDAP_CRL_CREATION_ERROR",
                "Failed to create LDAP CRL entry: " + e.getMessage()
            );
        }
    }

    /**
     * Subject DN에서 Common Name (cn) 추출
     *
     * @param issuerDn Distinguished Name
     * @return Common Name
     */
    private static String extractCommonName(String issuerDn) {
        // DN 형식: CN=..., O=..., C=...
        String[] parts = issuerDn.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("CN=") || trimmed.startsWith("cn=")) {
                return trimmed.substring(3);
            }
        }
        // CN을 찾을 수 없으면 전체 DN 사용
        return issuerDn;
    }

    /**
     * CN을 LDAP 형식으로 정규화
     *
     * <p>특수 문자 제거 및 공백 처리</p>
     *
     * @param cn Common Name
     * @return 정규화된 CN
     */
    private static String sanitizeCnForLdap(String cn) {
        if (cn == null) {
            return "unknown";
        }
        // 쉼표, 이퀄 기호 등 특수문자 제거
        return cn.replaceAll("[,=]", "-")
                .replaceAll("\\s+", "-")
                .toLowerCase();
    }

    /**
     * CRL 기본 경로 생성
     *
     * @return 경로 (예: "ou=crl,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com")
     */
    private static String buildCrlBaseDn() {
        return "ou=crl,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com";
    }

    /**
     * 폐기된 시리얼 번호 Set을 세미콜론 구분 문자열로 변환
     *
     * @param serialNumbers 폐기된 시리얼 번호 Set
     * @return 세미콜론 구분 문자열
     */
    private static String serializeRevokedSerialNumbers(Set<String> serialNumbers) {
        if (serialNumbers == null || serialNumbers.isEmpty()) {
            return "";
        }
        return String.join(";", serialNumbers.stream().sorted().toArray(String[]::new));
    }

    /**
     * 세미콜론 구분 문자열을 폐기된 시리얼 번호 Set으로 변환
     *
     * <p>RevokedCertificates 형식에 맞춰 세미콜론으로 구분된 문자열을 파싱합니다.</p>
     *
     * @return 폐기된 시리얼 번호 Set
     */
    public Set<String> deserializeRevokedSerialNumbers() {
        Set<String> serialNumbers = new HashSet<>();
        if (revokedSerialNumbers != null && !revokedSerialNumbers.trim().isEmpty()) {
            String[] parts = revokedSerialNumbers.split(";");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    serialNumbers.add(trimmed);
                }
            }
        }
        return serialNumbers;
    }

    /**
     * 바이트 배열을 Base64 문자열로 인코딩
     *
     * @param data 바이트 배열
     * @return Base64 인코딩된 문자열
     */
    private static String encodeToBase64(byte[] data) {
        if (data == null || data.length == 0) {
            throw new DomainException(
                "EMPTY_CRL_DATA",
                "CRL data must not be empty"
            );
        }
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Base64 문자열을 바이트 배열로 디코딩
     *
     * @return 디코딩된 바이트 배열
     * @throws DomainException 디코딩 실패 시
     */
    public byte[] decodeX509Crl() {
        try {
            return Base64.getDecoder().decode(x509CrlBase64);
        } catch (IllegalArgumentException e) {
            throw new DomainException(
                "INVALID_BASE64",
                "Invalid Base64 encoded CRL: " + e.getMessage()
            );
        }
    }

    /**
     * CRL이 만료되었는지 확인
     *
     * <p>현재 시간이 NextUpdate를 지났으면 true</p>
     *
     * @return 만료된 경우 true
     */
    public boolean isExpired() {
        if (nextUpdate == null) {
            return false;  // NextUpdate 정보 없으면 유효한 것으로 간주
        }
        return LocalDateTime.now().isAfter(nextUpdate);
    }

    /**
     * CRL이 업데이트가 필요한지 확인
     *
     * <p>아직 동기화되지 않았거나 NextUpdate를 지난 경우 true</p>
     *
     * @return 업데이트 필요 시 true
     */
    public boolean needsUpdate() {
        if (lastSyncAt == null) {
            return true;  // 아직 동기화되지 않음
        }
        return LocalDateTime.now().isAfter(nextUpdate);
    }

    /**
     * 동기화 완료 표시
     *
     * <p>LDAP에 저장한 후 호출</p>
     */
    public void markAsSynced() {
        this.lastSyncAt = LocalDateTime.now();
    }

    /**
     * 특정 시리얼 번호가 폐기되었는지 확인
     *
     * @param serialNumber 확인할 시리얼 번호
     * @return 폐기된 경우 true
     */
    public boolean isSerialNumberRevoked(String serialNumber) {
        Set<String> revokedSet = deserializeRevokedSerialNumbers();
        return revokedSet.contains(serialNumber);
    }

    /**
     * 문자열 표현
     *
     * @return "LdapCrlEntry[dn=..., issuer=..., thisUpdate=..., nextUpdate=...]"
     */
    @Override
    public String toString() {
        return String.format(
                "LdapCrlEntry[dn=%s, issuer=%s, thisUpdate=%s, nextUpdate=%s, revokedCount=%d]",
                dn, issuerName, thisUpdate, nextUpdate,
                deserializeRevokedSerialNumbers().size()
        );
    }
}
