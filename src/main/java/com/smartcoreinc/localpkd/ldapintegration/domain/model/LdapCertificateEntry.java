package com.smartcoreinc.localpkd.ldapintegration.domain.model;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;
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
import java.util.UUID;

/**
 * LdapCertificateEntry - LDAP에 저장된 인증서 항목
 *
 * <p>LDAP 디렉토리에 저장된 X.509 인증서를 표현합니다.</p>
 *
 * <h3>LDAP DN 구조</h3>
 * <pre>{@code
 * cn=CSCA-KOREA,ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com
 * }</pre>
 *
 * <h3>LDAP Attributes</h3>
 * <ul>
 *   <li>{@code cn}: Common Name (Subject DN)</li>
 *   <li>{@code x509certificate}: Base64 인증서 바이너리</li>
 *   <li>{@code certificateFingerprint}: SHA-256 Fingerprint</li>
 *   <li>{@code serialNumber}: 인증서 시리얼 번호</li>
 *   <li>{@code issuerDN}: 발급자 DN</li>
 *   <li>{@code notBefore}: 유효기간 시작</li>
 *   <li>{@code notAfter}: 유효기간 종료</li>
 *   <li>{@code certificateType}: CSCA, DSC, DS</li>
 *   <li>{@code validationStatus}: VALID, INVALID, REVOKED</li>
 *   <li>{@code lastSyncAt}: 마지막 동기화 시간</li>
 * </ul>
 *
 * <h3>비즈니스 규칙</h3>
 * <ul>
 *   <li>DN은 null일 수 없음</li>
 *   <li>x509CertificateBase64는 null일 수 없음</li>
 *   <li>certificateType은 null일 수 없음 (CSCA, DSC, DS 중 하나)</li>
 *   <li>만료된 인증서는 {@code isExpired()}로 확인</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // Certificate에서 LdapCertificateEntry 생성
 * LdapCertificateEntry entry = LdapCertificateEntry.createFromCertificate(
 *     certificate,
 *     CertificateType.CSCA
 * );
 *
 * // LDAP에 저장하기 전 검증
 * if (entry.isExpired()) {
 *     log.warn("Certificate has expired: {}", entry.getFingerprint());
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
public class LdapCertificateEntry {

    /**
     * LDAP Distinguished Name
     * 예: cn=CSCA-KOREA,ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com
     */
    @EqualsAndHashCode.Include
    private DistinguishedName dn;

    /**
     * 원본 Certificate의 UUID
     * 로컬 DB와의 매핑을 위해 저장
     */
    private UUID certificateId;

    /**
     * X.509 인증서 (Base64 인코딩)
     * DER 형식의 바이너리를 Base64로 인코딩하여 저장
     */
    private String x509CertificateBase64;

    /**
     * 인증서 Fingerprint (SHA-256)
     * LDAP에서 검색 가능하도록 별도로 저장
     */
    private String fingerprint;

    /**
     * 인증서 시리얼 번호
     */
    private String serialNumber;

    /**
     * 발급자 DN (Issuer Distinguished Name)
     * Trust Chain Building에서 사용
     */
    private String issuerDn;

    /**
     * 인증서 타입 (CSCA, DSC, DS)
     */
    private CertificateType certificateType;

    /**
     * 유효기간 시작 (NotBefore)
     */
    private LocalDateTime notBefore;

    /**
     * 유효기간 종료 (NotAfter)
     */
    private LocalDateTime notAfter;

    /**
     * 검증 상태 (VALID, INVALID, REVOKED)
     */
    @Builder.Default
    private String validationStatus = "VALID";

    /**
     * 마지막 동기화 시간
     */
    private LocalDateTime lastSyncAt;

    /**
     * Certificate Aggregate에서 LdapCertificateEntry 생성
     *
     * <p>인증서 정보를 LDAP 형식으로 변환합니다.</p>
     *
     * @param certificate 원본 인증서
     * @param certificateType 인증서 타입 (CSCA, DSC, DS)
     * @return LdapCertificateEntry
     * @throws DomainException 변환 중 오류 발생 시
     */
    public static LdapCertificateEntry createFromCertificate(
            Certificate certificate,
            CertificateType certificateType) {

        if (certificate == null) {
            throw new DomainException(
                "CERTIFICATE_NOT_NULL",
                "Certificate must not be null"
            );
        }

        if (certificateType == null) {
            throw new DomainException(
                "CERTIFICATE_TYPE_NOT_NULL",
                "CertificateType must not be null"
            );
        }

        try {
            // CN (Common Name) 생성
            String subjectDn = certificate.getSubjectInfo().getDistinguishedName();
            String cn = extractCommonName(subjectDn);

            // DN 생성
            String dnValue = String.format("cn=%s,%s",
                    sanitizeCnForLdap(cn),
                    buildOuForType(certificateType));
            DistinguishedName dn = DistinguishedName.of(dnValue);

            // Entry 빌드
            return LdapCertificateEntry.builder()
                    .dn(dn)
                    .certificateId(certificate.getId().getId())
                    .x509CertificateBase64(encodeToBase64(certificate.getX509Data().getCertificateBinary()))
                    .fingerprint(certificate.getX509Data().getFingerprintSha256())
                    .serialNumber(certificate.getX509Data().getSerialNumber())
                    .issuerDn(certificate.getIssuerInfo().getDistinguishedName())
                    .certificateType(certificateType)
                    .notBefore(certificate.getValidity().getNotBefore())
                    .notAfter(certificate.getValidity().getNotAfter())
                    .validationStatus("VALID")
                    .lastSyncAt(null)  // 아직 동기화되지 않음
                    .build();

        } catch (Exception e) {
            log.error("Failed to create LDAP certificate entry: {}", e.getMessage(), e);
            throw new DomainException(
                "LDAP_ENTRY_CREATION_ERROR",
                "Failed to create LDAP certificate entry: " + e.getMessage()
            );
        }
    }

    /**
     * Subject DN에서 Common Name (cn) 추출
     *
     * @param subjectDn Subject Distinguished Name
     * @return Common Name
     */
    private static String extractCommonName(String subjectDn) {
        // DN 형식: CN=..., O=..., C=...
        String[] parts = subjectDn.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("CN=") || trimmed.startsWith("cn=")) {
                return trimmed.substring(3);
            }
        }
        // CN을 찾을 수 없으면 전체 DN 사용
        return subjectDn;
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
     * 인증서 타입에 따른 OU 경로 생성
     *
     * @param certificateType 인증서 타입
     * @return OU 경로 (예: "ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com")
     */
    private static String buildOuForType(CertificateType certificateType) {
        return switch (certificateType) {
            case CSCA -> "ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com";
            case DSC -> "ou=dsc,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com";
            case DS -> "ou=ds,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com";
            default -> "ou=certificates,dc=ldap,dc=smartcoreinc,dc=com";
        };
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
                "EMPTY_CERTIFICATE_DATA",
                "Certificate data must not be empty"
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
    public byte[] decodeX509Certificate() {
        try {
            return Base64.getDecoder().decode(x509CertificateBase64);
        } catch (IllegalArgumentException e) {
            throw new DomainException(
                "INVALID_BASE64",
                "Invalid Base64 encoded certificate: " + e.getMessage()
            );
        }
    }

    /**
     * 인증서가 만료되었는지 확인
     *
     * <p>현재 시간이 NotAfter를 지났으면 true</p>
     *
     * @return 만료된 경우 true
     */
    public boolean isExpired() {
        if (notAfter == null) {
            return false;  // NotAfter 정보 없으면 유효한 것으로 간주
        }
        return LocalDateTime.now().isAfter(notAfter);
    }

    /**
     * 인증서가 아직 유효하기 시작하지 않았는지 확인
     *
     * <p>현재 시간이 NotBefore보다 이전이면 true</p>
     *
     * @return 아직 유효하지 않은 경우 true
     */
    public boolean isNotYetValid() {
        if (notBefore == null) {
            return false;  // NotBefore 정보 없으면 유효한 것으로 간주
        }
        return LocalDateTime.now().isBefore(notBefore);
    }

    /**
     * 인증서가 현재 유효한지 확인
     *
     * <p>NotBefore <= 현재 시간 <= NotAfter인 경우 true</p>
     *
     * @return 현재 유효한 경우 true
     */
    public boolean isCurrentlyValid() {
        return !isExpired() && !isNotYetValid();
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
     * 동기화 필요 여부 확인
     *
     * <p>아직 동기화되지 않았거나 일정 시간 이상 지난 경우 true</p>
     *
     * @param syncIntervalMinutes 동기화 간격 (분)
     * @return 동기화 필요 시 true
     */
    public boolean needsSync(int syncIntervalMinutes) {
        if (lastSyncAt == null) {
            return true;  // 아직 동기화되지 않음
        }
        LocalDateTime nextSyncTime = lastSyncAt.plusMinutes(syncIntervalMinutes);
        return LocalDateTime.now().isAfter(nextSyncTime);
    }

    /**
     * 문자열 표현
     *
     * @return "LdapCertificateEntry[dn=..., fingerprint=..., type=...]"
     */
    @Override
    public String toString() {
        return String.format(
                "LdapCertificateEntry[dn=%s, fingerprint=%s, type=%s, status=%s]",
                dn, fingerprint, certificateType, validationStatus
        );
    }
}
