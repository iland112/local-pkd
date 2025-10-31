package com.smartcoreinc.localpkd.fileparsing.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;
import java.time.LocalDateTime;

/**
 * CertificateData - 추출된 인증서 데이터 Value Object
 *
 * <p><b>DDD Value Object 패턴</b>:</p>
 * <ul>
 *   <li>Immutability: 생성 후 변경 불가</li>
 *   <li>Self-validation: 생성 시 필수 필드 검증</li>
 *   <li>Value equality: 모든 필드 값으로 동등성 판단</li>
 * </ul>
 *
 * <p><b>Hibernate/JPA 호환성</b>:</p>
 * <ul>
 *   <li>@Embeddable: JPA Embedded 타입</li>
 *   <li>protected 기본 생성자: JPA 리플렉션 인스턴스 생성용</li>
 *   <li>non-final 필드: JPA가 리플렉션으로 값 주입 가능</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * CertificateData certData = CertificateData.of(
 *     "CSCA",
 *     "KR",
 *     "CN=Korea CSCA, C=KR",
 *     "CN=Korea CSCA, C=KR",
 *     "1234567890ABCDEF",
 *     LocalDateTime.of(2020, 1, 1, 0, 0),
 *     LocalDateTime.of(2030, 12, 31, 23, 59),
 *     certBytes,
 *     "a1b2c3d4...",
 *     true
 * );
 *
 * // 유효 기간 확인
 * boolean isExpired = certData.isExpired();
 *
 * // 국가 코드 확인
 * boolean isKorean = certData.isCountry("KR");
 * </pre>
 *
 * @see ValueObject
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CertificateData implements ValueObject {

    /**
     * 인증서 타입 (CSCA, DSC, DSC_NC)
     */
    @Column(name = "cert_type", length = 20, nullable = false)
    private String certificateType;

    /**
     * 발급 국가 코드 (ISO 3166-1 alpha-2)
     *
     * <p>예: KR, US, JP, CN</p>
     */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    /**
     * Subject DN (Distinguished Name)
     *
     * <p>예: CN=Korea CSCA, O=Ministry of Foreign Affairs, C=KR</p>
     */
    @Column(name = "subject_dn", length = 500, nullable = false)
    private String subjectDN;

    /**
     * Issuer DN (Distinguished Name)
     *
     * <p>Self-signed의 경우 Subject DN과 동일</p>
     */
    @Column(name = "issuer_dn", length = 500, nullable = false)
    private String issuerDN;

    /**
     * Serial Number (16진수 문자열)
     *
     * <p>예: 1234567890ABCDEF</p>
     */
    @Column(name = "serial_number", length = 100, nullable = false)
    private String serialNumber;

    /**
     * 유효 시작일
     */
    @Column(name = "not_before", nullable = false)
    private LocalDateTime notBefore;

    /**
     * 유효 종료일
     */
    @Column(name = "not_after", nullable = false)
    private LocalDateTime notAfter;

    /**
     * 인증서 바이너리 (DER 인코딩)
     *
     * NOTE: @Lob 제거 - Hibernate/PostgreSQL bytea 매핑 버그 회피
     * columnDefinition="BYTEA"만으로도 충분함
     */
    @Column(name = "certificate_binary", nullable = false, columnDefinition = "BYTEA")
    private byte[] certificateBinary;

    /**
     * SHA-256 Fingerprint (64자 16진수 문자열)
     *
     * <p>인증서 고유 식별자로 사용</p>
     */
    @Column(name = "fingerprint_sha256", length = 64)
    private String fingerprintSha256;

    /**
     * 유효 여부
     *
     * <p>파싱 시점에 유효 기간, 서명 검증 등을 통해 판단</p>
     */
    @Column(name = "is_valid", nullable = false)
    private boolean valid;

    // ========== Static Factory Method ==========

    /**
     * CertificateData 생성 (전체 필드)
     *
     * @param certificateType 인증서 타입 (CSCA, DSC, DSC_NC)
     * @param countryCode 발급 국가 코드 (ISO 3166-1 alpha-2)
     * @param subjectDN Subject DN
     * @param issuerDN Issuer DN
     * @param serialNumber Serial Number (16진수)
     * @param notBefore 유효 시작일
     * @param notAfter 유효 종료일
     * @param certificateBinary 인증서 바이너리 (DER)
     * @param fingerprintSha256 SHA-256 Fingerprint
     * @param valid 유효 여부
     * @return CertificateData
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static CertificateData of(
        String certificateType,
        String countryCode,
        String subjectDN,
        String issuerDN,
        String serialNumber,
        LocalDateTime notBefore,
        LocalDateTime notAfter,
        byte[] certificateBinary,
        String fingerprintSha256,
        boolean valid
    ) {
        CertificateData data = new CertificateData();
        data.certificateType = certificateType;
        data.countryCode = countryCode;
        data.subjectDN = subjectDN;
        data.issuerDN = issuerDN;
        data.serialNumber = serialNumber;
        data.notBefore = notBefore;
        data.notAfter = notAfter;
        data.certificateBinary = certificateBinary;
        data.fingerprintSha256 = fingerprintSha256;
        data.valid = valid;

        // Self-validation
        data.validate();

        return data;
    }

    // ========== Business Logic Methods ==========

    /**
     * 인증서 만료 여부 확인
     *
     * @return 만료 여부
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(notAfter);
    }

    /**
     * 인증서 유효 기간 시작 전 여부
     *
     * @return 유효 기간 시작 전 여부
     */
    public boolean isNotYetValid() {
        return LocalDateTime.now().isBefore(notBefore);
    }

    /**
     * 현재 시점에 유효한 인증서 여부
     *
     * @return 유효 여부
     */
    public boolean isCurrentlyValid() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(notBefore) && !now.isAfter(notAfter) && valid;
    }

    /**
     * 특정 국가 인증서 여부
     *
     * @param countryCode 국가 코드 (ISO 3166-1 alpha-2)
     * @return 해당 국가 인증서 여부
     */
    public boolean isCountry(String countryCode) {
        return this.countryCode != null && this.countryCode.equalsIgnoreCase(countryCode);
    }

    /**
     * Self-signed 인증서 여부
     *
     * @return Self-signed 여부
     */
    public boolean isSelfSigned() {
        return subjectDN.equals(issuerDN);
    }

    /**
     * CSCA 인증서 여부
     *
     * @return CSCA 여부
     */
    public boolean isCsca() {
        return "CSCA".equalsIgnoreCase(certificateType);
    }

    /**
     * DSC 인증서 여부
     *
     * @return DSC 여부
     */
    public boolean isDsc() {
        return "DSC".equalsIgnoreCase(certificateType) || "DSC_NC".equalsIgnoreCase(certificateType);
    }

    // ========== Validation ==========

    private void validate() {
        if (certificateType == null || certificateType.isBlank()) {
            throw new IllegalArgumentException("certificateType must not be blank");
        }

        if (subjectDN == null || subjectDN.isBlank()) {
            throw new IllegalArgumentException("subjectDN must not be blank");
        }

        if (issuerDN == null || issuerDN.isBlank()) {
            throw new IllegalArgumentException("issuerDN must not be blank");
        }

        if (serialNumber == null || serialNumber.isBlank()) {
            throw new IllegalArgumentException("serialNumber must not be blank");
        }

        if (notBefore == null) {
            throw new IllegalArgumentException("notBefore must not be null");
        }

        if (notAfter == null) {
            throw new IllegalArgumentException("notAfter must not be null");
        }

        if (notAfter.isBefore(notBefore)) {
            throw new IllegalArgumentException("notAfter must be after notBefore");
        }

        if (certificateBinary == null || certificateBinary.length == 0) {
            throw new IllegalArgumentException("certificateBinary must not be empty");
        }

        // Country code validation (optional)
        if (countryCode != null && countryCode.length() != 2) {
            throw new IllegalArgumentException("countryCode must be 2 characters (ISO 3166-1 alpha-2)");
        }

        // Certificate type validation
        if (!certificateType.matches("^(CSCA|DSC|DSC_NC)$")) {
            throw new IllegalArgumentException("certificateType must be one of: CSCA, DSC, DSC_NC");
        }
    }

    @Override
    public String toString() {
        return String.format(
            "CertificateData[type=%s, country=%s, subject=%s, serial=%s, valid=%b]",
            certificateType,
            countryCode,
            subjectDN.length() > 50 ? subjectDN.substring(0, 47) + "..." : subjectDN,
            serialNumber,
            valid
        );
    }
}
