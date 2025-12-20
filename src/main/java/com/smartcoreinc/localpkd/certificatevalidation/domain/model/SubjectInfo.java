package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * SubjectInfo - X.509 인증서 Subject 정보 Value Object
 *
 * <p><b>DDD Value Object Pattern</b>:</p>
 * <ul>
 *   <li>Immutability: 생성 후 변경 불가</li>
 *   <li>Self-validation: 생성 시 필수 필드 검증</li>
 *   <li>Value equality: 모든 필드 값으로 동등성 판단</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * SubjectInfo subject = SubjectInfo.of(
 *     "CN=Korea CSCA, O=Ministry of Foreign Affairs, C=KR",
 *     "KR",
 *     "Ministry of Foreign Affairs",
 *     "PKD",
 *     "Korea CSCA"
 * );
 *
 * // 조회
 * String countryCode = subject.getCountryCode();  // "KR"
 * String commonName = subject.getCommonName();    // "Korea CSCA"
 *
 * // 검증
 * boolean isKorean = subject.isCountry("KR");     // true
 * }</pre>
 *
 * @see ValueObject
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-23
 */
@Embeddable
public class SubjectInfo implements ValueObject {

    /**
     * Distinguished Name (DN)
     *
     * <p>예: CN=Korea CSCA, O=Ministry of Foreign Affairs, C=KR</p>
     */
    @Column(name = "subject_dn", length = 500, nullable = false)
    private String distinguishedName;

    /**
     * 발급 국가 코드 (ISO 3166-1 alpha-2)
     *
     * <p>예: KR, US, JP, CN</p>
     */
    @Column(name = "subject_country_code", length = 3)
    private String countryCode;

    /**
     * Organization (O)
     *
     * <p>예: Ministry of Foreign Affairs</p>
     */
    @Column(name = "subject_organization", length = 255)
    private String organization;

    /**
     * Organizational Unit (OU)
     *
     * <p>예: PKD</p>
     */
    @Column(name = "subject_organizational_unit", length = 255)
    private String organizationalUnit;

    /**
     * Common Name (CN)
     *
     * <p>예: Korea CSCA</p>
     */
    @Column(name = "subject_common_name", length = 255)
    private String commonName;

    /**
     * JPA용 기본 생성자 (protected)
     */
    protected SubjectInfo() {
    }

    /**
     * SubjectInfo 생성 (Static Factory Method)
     *
     * @param distinguishedName Distinguished Name
     * @param countryCode 국가 코드 (ISO 3166-1 alpha-2)
     * @param organization Organization (O)
     * @param organizationalUnit Organizational Unit (OU)
     * @param commonName Common Name (CN)
     * @return SubjectInfo
     * @throws IllegalArgumentException DN이 null이거나 비어있는 경우
     */
    public static SubjectInfo of(
            String distinguishedName,
            String countryCode,
            String organization,
            String organizationalUnit,
            String commonName
    ) {
        if (distinguishedName == null || distinguishedName.isBlank()) {
            throw new IllegalArgumentException("Distinguished Name cannot be null or blank");
        }

        SubjectInfo info = new SubjectInfo();
        info.distinguishedName = distinguishedName;
        info.countryCode = countryCode != null ? countryCode.toUpperCase() : null;
        info.organization = organization;
        info.organizationalUnit = organizationalUnit;
        info.commonName = commonName;

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

    // ========== Business Logic Methods ==========

    /**
     * 특정 국가 인증서 여부
     *
     * @param countryCode 국가 코드 (대소문자 무시)
     * @return 해당 국가 인증서 여부
     */
    public boolean isCountry(String countryCode) {
        if (countryCode == null || this.countryCode == null) {
            return false;
        }
        return this.countryCode.equalsIgnoreCase(countryCode.toUpperCase());
    }

    /**
     * 조직 일치 여부
     *
     * @param organization 조직명
     * @return 일치 여부
     */
    public boolean hasOrganization(String organization) {
        if (organization == null || this.organization == null) {
            return false;
        }
        return this.organization.equalsIgnoreCase(organization);
    }

    /**
     * 완전한 정보 여부 (모든 필드가 채워져 있음)
     *
     * @return 모든 필드가 null이 아니면 true
     */
    public boolean isComplete() {
        return distinguishedName != null && countryCode != null &&
               organization != null && organizationalUnit != null &&
               commonName != null;
    }

    /**
     * 부분 정보 여부 (최소 필수 필드만 존재)
     *
     * @return DN과 commonName만 있으면 true
     */
    public boolean hasMinimalInfo() {
        return distinguishedName != null && commonName != null;
    }

    // ========== equals & hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubjectInfo that = (SubjectInfo) o;
        return Objects.equals(distinguishedName, that.distinguishedName) &&
               Objects.equals(countryCode, that.countryCode) &&
               Objects.equals(organization, that.organization) &&
               Objects.equals(organizationalUnit, that.organizationalUnit) &&
               Objects.equals(commonName, that.commonName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(distinguishedName, countryCode, organization,
                          organizationalUnit, commonName);
    }

    @Override
    public String toString() {
        return String.format("SubjectInfo[dn=%s, country=%s, organization=%s, ou=%s, cn=%s]",
                distinguishedName, countryCode, organization, organizationalUnit, commonName);
    }
}
