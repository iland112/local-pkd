package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * IssuerInfo - X.509 인증서 Issuer 정보 Value Object
 *
 * <p><b>DDD Value Object Pattern</b>:</p>
 * <ul>
 *   <li>Immutability: 생성 후 변경 불가</li>
 *   <li>Self-validation: 생성 시 필수 필드 검증</li>
 *   <li>Value equality: 모든 필드 값으로 동등성 판단</li>
 * </ul>
 *
 * <p><b>차이점 (vs SubjectInfo)</b>:</p>
 * <ul>
 *   <li>isCA: 발급자가 CA 인증서인지 여부 (Self-signed 여부)</li>
 *   <li>Root CA는 isCA=true이고 self-signed</li>
 *   <li>Leaf Certificate는 issuerDN이 다른 CA의 DN</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // Root CA (Self-signed)
 * IssuerInfo rootCA = IssuerInfo.of(
 *     "CN=Korea Root CA, O=MOPAS, C=KR",
 *     "KR",
 *     "Ministry of Public Administration and Security",
 *     "PKD",
 *     "Korea Root CA",
 *     true  // isCA
 * );
 *
 * // Intermediate CA
 * IssuerInfo intermediateCA = IssuerInfo.of(
 *     "CN=Korea Intermediate CA, O=MOPAS, C=KR",
 *     "KR",
 *     "Ministry of Public Administration and Security",
 *     "PKD",
 *     "Korea Intermediate CA",
 *     true  // isCA
 * );
 *
 * // 검증
 * boolean isSelfSigned = rootCA.isCA() && rootCA.getDistinguishedName()
 *     .equals(rootCA.getDistinguishedName());  // true
 * }</pre>
 *
 * @see SubjectInfo
 * @see ValueObject
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-23
 */
@Embeddable
public class IssuerInfo implements ValueObject {

    /**
     * Distinguished Name (DN)
     *
     * <p>예: CN=Korea CSCA, O=Ministry of Foreign Affairs, C=KR</p>
     */
    @Column(name = "issuer_dn", length = 500, nullable = false)
    private String distinguishedName;

    /**
     * 발급자 국가 코드 (ISO 3166-1 alpha-2)
     *
     * <p>예: KR, US, JP, CN</p>
     */
    @Column(name = "issuer_country_code", length = 3)
    private String countryCode;

    /**
     * Organization (O)
     *
     * <p>예: Ministry of Foreign Affairs</p>
     */
    @Column(name = "issuer_organization", length = 255)
    private String organization;

    /**
     * Organizational Unit (OU)
     *
     * <p>예: PKD</p>
     */
    @Column(name = "issuer_organizational_unit", length = 255)
    private String organizationalUnit;

    /**
     * Common Name (CN)
     *
     * <p>예: Korea CSCA</p>
     */
    @Column(name = "issuer_common_name", length = 255)
    private String commonName;

    /**
     * CA 인증서 여부
     *
     * <p>true: CA 인증서 (다른 인증서 발급 가능)
     * <p>false: End-entity 인증서 (다른 인증서 발급 불가)
     */
    @Column(name = "issuer_is_ca")
    private Boolean isCA;

    /**
     * JPA용 기본 생성자 (protected)
     */
    protected IssuerInfo() {
    }

    /**
     * IssuerInfo 생성 (Static Factory Method)
     *
     * @param distinguishedName Distinguished Name
     * @param countryCode 국가 코드 (ISO 3166-1 alpha-2)
     * @param organization Organization (O)
     * @param organizationalUnit Organizational Unit (OU)
     * @param commonName Common Name (CN)
     * @param isCA CA 인증서 여부
     * @return IssuerInfo
     * @throws IllegalArgumentException DN이 null이거나 비어있는 경우
     */
    public static IssuerInfo of(
            String distinguishedName,
            String countryCode,
            String organization,
            String organizationalUnit,
            String commonName,
            boolean isCA
    ) {
        if (distinguishedName == null || distinguishedName.isBlank()) {
            throw new IllegalArgumentException("Distinguished Name cannot be null or blank");
        }

        IssuerInfo info = new IssuerInfo();
        info.distinguishedName = distinguishedName;
        info.countryCode = countryCode != null ? countryCode.toUpperCase() : null;
        info.organization = organization;
        info.organizationalUnit = organizationalUnit;
        info.commonName = commonName;
        info.isCA = isCA;

        return info;
    }

    // ========== Getters ==========

    public String getDistinguishedName() {
        return distinguishedName;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getOrganization() {
        return organization;
    }

    public String getOrganizationalUnit() {
        return organizationalUnit;
    }

    public String getCommonName() {
        return commonName;
    }

    public Boolean isCA() {
        return isCA != null && isCA;
    }

    // ========== Business Logic Methods ==========

    /**
     * 특정 국가 발급자 여부
     *
     * @param countryCode 국가 코드 (대소문자 무시)
     * @return 해당 국가 발급자 여부
     */
    public boolean isCountry(String countryCode) {
        if (countryCode == null || this.countryCode == null) {
            return false;
        }
        return this.countryCode.equalsIgnoreCase(countryCode.toUpperCase());
    }

    /**
     * 같은 발급자 여부 (DN 비교)
     *
     * @param otherDN 다른 Distinguished Name
     * @return DN이 동일하면 true
     */
    public boolean isSameIssuer(String otherDN) {
        if (otherDN == null) {
            return false;
        }
        return this.distinguishedName.equalsIgnoreCase(otherDN);
    }

    /**
     * Self-signed CA 여부
     *
     * <p>SubjectDN == IssuerDN && isCA == true인 경우</p>
     *
     * @param subjectDN Subject Distinguished Name
     * @return Self-signed CA 여부
     */
    public boolean isSelfSignedCA(String subjectDN) {
        return isCA() && isSameIssuer(subjectDN);
    }

    /**
     * 완전한 정보 여부 (모든 필드가 채워져 있음)
     *
     * @return 모든 필드가 null이 아니면 true
     */
    public boolean isComplete() {
        return distinguishedName != null && countryCode != null &&
               organization != null && organizationalUnit != null &&
               commonName != null && isCA != null;
    }

    /**
     * 부분 정보 여부 (최소 필수 필드만 존재)
     *
     * @return DN만 있으면 true
     */
    public boolean hasMinimalInfo() {
        return distinguishedName != null;
    }

    // ========== equals & hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IssuerInfo that = (IssuerInfo) o;
        return Objects.equals(distinguishedName, that.distinguishedName) &&
               Objects.equals(countryCode, that.countryCode) &&
               Objects.equals(organization, that.organization) &&
               Objects.equals(organizationalUnit, that.organizationalUnit) &&
               Objects.equals(commonName, that.commonName) &&
               Objects.equals(isCA, that.isCA);
    }

    @Override
    public int hashCode() {
        return Objects.hash(distinguishedName, countryCode, organization,
                          organizationalUnit, commonName, isCA);
    }

    @Override
    public String toString() {
        return String.format("IssuerInfo[dn=%s, country=%s, organization=%s, ou=%s, cn=%s, isCA=%s]",
                distinguishedName, countryCode, organization, organizationalUnit, commonName, isCA);
    }
}
