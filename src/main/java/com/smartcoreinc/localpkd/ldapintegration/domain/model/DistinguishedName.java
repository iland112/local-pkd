package com.smartcoreinc.localpkd.ldapintegration.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.Embeddable;

/**
 * DistinguishedName - LDAP Distinguished Name Value Object
 *
 * <p>LDAP의 고유한 객체 식별자(Distinguished Name)를 표현합니다.</p>
 *
 * <h3>형식</h3>
 * <pre>{@code
 * cn=example,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com
 * ^   ^      ^  ^            ^  ^     ^  ^      ^     ^  ^
 * |   |      |  |            |  |     |  |      |     |  |
 * RDN component attribute value
 * }</pre>
 *
 * <h3>비즈니스 규칙</h3>
 * <ul>
 *   <li>DN은 null이거나 빈 문자열일 수 없음</li>
 *   <li>DN은 쉼표(,)로 구분된 RDN (Relative Distinguished Name) 컴포넌트로 구성</li>
 *   <li>각 RDN은 attribute=value 형식</li>
 *   <li>특수 문자는 RFC 2253에 따라 이스케이프되어야 함</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * DistinguishedName dn = DistinguishedName.of(
 *     "cn=CSCA-KOREA,ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"
 * );
 *
 * String cn = dn.getCommonName();  // "CSCA-KOREA"
 * boolean isUnder = dn.isUnderBase(
 *     DistinguishedName.of("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com")
 * );  // true
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DistinguishedName {

    /**
     * Distinguished Name 값
     * 형식: cn=...,ou=...,dc=...
     */
    private String value;

    /**
     * Distinguished Name 생성
     *
     * @param value DN 문자열 (RFC 2253 형식)
     * @return DistinguishedName 객체
     * @throws DomainException value가 null이거나 빈 문자열인 경우
     */
    public static DistinguishedName of(String value) {
        validate(value);
        return new DistinguishedName(value);
    }

    /**
     * Distinguished Name 검증
     *
     * @param value DN 문자열
     * @throws DomainException 검증 실패 시
     */
    private static void validate(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new DomainException(
                "INVALID_DN",
                "Distinguished Name must not be null or empty"
            );
        }

        // RFC 2253 형식 검증
        if (!value.contains("=")) {
            throw new DomainException(
                "INVALID_DN_FORMAT",
                "Distinguished Name must contain '=' in RDN components: " + value
            );
        }

        // 기본적인 구조 검증 (cn=...),... 형식)
        String[] rdns = value.split(",");
        for (String rdn : rdns) {
            String trimmed = rdn.trim();
            if (!trimmed.contains("=")) {
                throw new DomainException(
                    "INVALID_RDN_FORMAT",
                    "RDN must be in attribute=value format: " + trimmed
                );
            }
        }
    }

    /**
     * Common Name (cn) 추출
     *
     * <p>DN에서 첫 번째 cn (Common Name) 컴포넌트를 추출합니다.</p>
     *
     * @return cn 값, 없으면 null
     * @throws DomainException cn을 파싱할 수 없는 경우
     */
    public String getCommonName() {
        return extractAttribute("cn");
    }

    /**
     * Organizational Unit (ou) 추출
     *
     * <p>DN에서 첫 번째 ou (Organizational Unit) 컴포넌트를 추출합니다.</p>
     *
     * @return ou 값, 없으면 null
     */
    public String getOrganizationalUnit() {
        return extractAttribute("ou");
    }

    /**
     * Domain Component (dc) 추출
     *
     * <p>DN에서 첫 번째 dc (Domain Component) 컴포넌트를 추출합니다.</p>
     *
     * @return dc 값, 없으면 null
     */
    public String getDomainComponent() {
        return extractAttribute("dc");
    }

    /**
     * 주어진 속성값 추출
     *
     * <p>DN에서 특정 속성(attribute)의 첫 번째 값을 추출합니다.</p>
     *
     * <p>예: DN "cn=example,ou=certificates,dc=ldap" 에서
     * extractAttribute("cn") → "example"</p>
     *
     * @param attributeType 속성명 (예: cn, ou, dc) - case-insensitive
     * @return 속성값 또는 null (속성이 없는 경우)
     */
    private String extractAttribute(String attributeType) {
        String pattern = attributeType.toLowerCase() + "=";
        String lowerValue = value.toLowerCase();

        int startIndex = lowerValue.indexOf(pattern);
        if (startIndex == -1) {
            return null;  // 속성이 없음
        }

        // 속성 시작 위치
        int valueStart = startIndex + pattern.length();

        // 쉼표(,) 또는 문자열 끝까지의 값 추출
        int valueEnd = value.indexOf(",", valueStart);
        if (valueEnd == -1) {
            valueEnd = value.length();
        }

        return value.substring(valueStart, valueEnd).trim();
    }

    /**
     * 현재 DN이 기본 DN 아래에 있는지 확인
     *
     * <p>이 DN이 주어진 baseDn의 하위에 있는지 확인합니다.</p>
     *
     * <p>예:
     * <pre>{@code
     * DistinguishedName dn = DistinguishedName.of(
     *     "cn=example,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"
     * );
     * DistinguishedName base = DistinguishedName.of(
     *     "ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"
     * );
     * boolean result = dn.isUnderBase(base);  // true
     * }</pre></p>
     *
     * @param baseDn 기본 DN
     * @return 이 DN이 baseDn 아래에 있으면 true
     * @throws DomainException baseDn이 null인 경우
     */
    public boolean isUnderBase(DistinguishedName baseDn) {
        if (baseDn == null) {
            throw new DomainException(
                "INVALID_BASE_DN",
                "Base DN must not be null"
            );
        }

        // 현재 DN이 base로 끝나는지 확인 (대소문자 구분 안 함)
        String currentLower = value.toLowerCase();
        String baseLower = baseDn.getValue().toLowerCase();

        // 정확한 일치 또는 ,로 구분되는 경우만 true
        if (currentLower.equals(baseLower)) {
            return true;  // DN과 base가 동일
        }

        if (currentLower.endsWith("," + baseLower)) {
            return true;  // DN이 base 아래에 있음
        }

        return false;
    }

    /**
     * RFC 2253 형식의 DN 반환
     *
     * <p>현재 DN을 RFC 2253 형식으로 정규화합니다.</p>
     *
     * @return RFC 2253 형식의 DN
     */
    public String toRfc2253Format() {
        // 기본적으로 이미 저장된 값이 RFC 2253 형식이므로 그대로 반환
        // 향후 특수 문자 이스케이프 처리 필요 시 구현
        return value;
    }

    /**
     * DN 문자열 표현
     *
     * @return DN 값
     */
    @Override
    public String toString() {
        return value;
    }
}
