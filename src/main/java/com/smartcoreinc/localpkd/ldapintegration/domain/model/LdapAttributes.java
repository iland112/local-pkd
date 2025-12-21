package com.smartcoreinc.localpkd.ldapintegration.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * LdapAttributes - LDAP 속성 매핑 Value Object
 *
 * <p>인증서/CRL 데이터를 LDAP 속성으로 매핑하고 표준화합니다.</p>
 *
 * <h3>책임</h3>
 * <ul>
 *   <li>도메인 객체 → LDAP 속성 변환</li>
 *   <li>LDAP 속성 이름 및 형식 표준화</li>
 *   <li>특수 문자 이스케이핑 (DN 컴포넌트용)</li>
 *   <li>속성 검증</li>
 * </ul>
 *
 * <h3>LDAP Attribute 표준</h3>
 * <ul>
 *   <li>objectClass: 객체 타입 (inetOrgPerson, organizationalUnit, etc.)</li>
 *   <li>cn: Common Name (필수)</li>
 *   <li>description: 설명 (선택)</li>
 *   <li>createTimestamp: 생성 일시 (자동)</li>
 *   <li>modifyTimestamp: 수정 일시 (자동)</li>
 *   <li>x509certificatedata: Base64 인코딩된 인증서</li>
 *   <li>x509crldata: Base64 인코딩된 CRL</li>
 * </ul>
 *
 * <h3>DN Component Escaping</h3>
 * <p>RFC 4514를 준수한 DN 컴포넌트 이스케이핑:</p>
 * <pre>{@code
 * CN 값에 포함된 특수 문자:
 * - 쉼표(,): \,
 * - 등호(=): \=
 * - 플러스(+): \+
 * - 작은 따옴표(\\): \\
 * - 슬래시(/): \/
 * - 중괄호({, }): \{, \}
 * }</pre>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // 인증서 속성 맵 생성
 * LdapAttributes certAttrs = LdapAttributes.forCertificate(
 *     "CSCA-KOREA",
 *     "KR",
 *     "2025-10-25T10:30:00",
 *     "certificateBase64String"
 * );
 *
 * // CRL 속성 맵 생성
 * LdapAttributes crlAttrs = LdapAttributes.forCrl(
 *     "CSCA-KOREA",
 *     "KR",
 *     "2025-10-25T10:30:00",
 *     "2025-10-26T10:30:00",
 *     "crlBase64String"
 * );
 *
 * // 속성 맵 조회
 * Map<String, Object> attrs = certAttrs.getAttributeMap();
 * String safeCn = LdapAttributes.escapeDnComponent("CN=CSCA, Inc.");
 * // → "CN\\=CSCA\\, Inc."
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Getter
@EqualsAndHashCode
public class LdapAttributes implements ValueObject, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * LDAP 속성 맵 (immutable)
     * Key: 속성명, Value: 속성값
     */
    private final Map<String, Object> attributes;

    /**
     * 속성 생성 타임스탬프 (ISO-8601 형식)
     */
    private final String createdAt;

    /**
     * DN 컴포넌트 이스케이프 패턴
     * RFC 4514: DN 컴포넌트에서 이스케이프해야 할 특수 문자들
     */
    @SuppressWarnings("unused")  // Reserved for future DN escaping
    private static final Pattern DN_ESCAPE_PATTERN = Pattern.compile("[,=+\\\\/<>;\"]");

    /**
     * Private 생성자
     *
     * @param attributes 속성 맵
     * @param createdAt 생성 일시
     */
    private LdapAttributes(Map<String, Object> attributes, String createdAt) {
        this.attributes = Collections.unmodifiableMap(new HashMap<>(attributes));
        this.createdAt = createdAt;
    }

    /**
     * 인증서용 LDAP 속성 생성
     *
     * @param cn Common Name (발급자명)
     * @param countryCode 국가 코드 (2자리)
     * @param createdAt 생성 일시 (ISO-8601)
     * @param x509CertBase64 Base64 인코딩된 인증서
     * @return LdapAttributes
     * @throws DomainException 입력값이 유효하지 않은 경우
     */
    public static LdapAttributes forCertificate(
            String cn, String countryCode, String createdAt, String x509CertBase64) {

        validateInputs(cn, countryCode, x509CertBase64);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("objectClass", "inetOrgPerson");
        attrs.put("cn", escapeDnComponent(cn));
        attrs.put("sn", extractSurname(cn));
        attrs.put("countryCode", countryCode);
        attrs.put("description", String.format("X.509 Certificate - %s", cn));
        attrs.put("x509certificatedata", x509CertBase64);
        attrs.put("createTimestamp", createdAt);
        attrs.put("modifyTimestamp", getCurrentTimestamp());

        return new LdapAttributes(attrs, createdAt);
    }

    /**
     * CRL용 LDAP 속성 생성
     *
     * @param cn Common Name (발급자명)
     * @param countryCode 국가 코드 (2자리)
     * @param thisUpdate CRL 발행 일시 (ISO-8601)
     * @param nextUpdate CRL 다음 발행 예정일 (ISO-8601)
     * @param x509CrlBase64 Base64 인코딩된 CRL
     * @return LdapAttributes
     * @throws DomainException 입력값이 유효하지 않은 경우
     */
    public static LdapAttributes forCrl(
            String cn, String countryCode, String thisUpdate, String nextUpdate,
            String x509CrlBase64) {

        validateInputs(cn, countryCode, x509CrlBase64);

        if (thisUpdate == null || thisUpdate.isBlank()) {
            throw new DomainException(
                "INVALID_CRL_THIS_UPDATE",
                "CRL thisUpdate must not be null or blank"
            );
        }

        if (nextUpdate == null || nextUpdate.isBlank()) {
            throw new DomainException(
                "INVALID_CRL_NEXT_UPDATE",
                "CRL nextUpdate must not be null or blank"
            );
        }

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("objectClass", "x509crl");
        attrs.put("cn", escapeDnComponent(cn));
        attrs.put("countryCode", countryCode);
        attrs.put("description", String.format("X.509 CRL - %s", cn));
        attrs.put("x509crldata", x509CrlBase64);
        attrs.put("thisUpdate", thisUpdate);
        attrs.put("nextUpdate", nextUpdate);
        attrs.put("createTimestamp", thisUpdate);
        attrs.put("modifyTimestamp", getCurrentTimestamp());

        return new LdapAttributes(attrs, thisUpdate);
    }

    /**
     * 빌더 패턴을 사용한 커스텀 LDAP 속성 생성
     *
     * @return AttributeBuilder
     */
    public static AttributeBuilder builder() {
        return new AttributeBuilder();
    }

    /**
     * DN 컴포넌트의 특수 문자를 이스케이프 (RFC 4514)
     *
     * <p>LDAP DN에서 사용되는 특수 문자들을 백슬래시로 이스케이프합니다.</p>
     *
     * @param value 이스케이프할 문자열
     * @return 이스케이프된 문자열
     * @throws DomainException 입력값이 null인 경우
     */
    public static String escapeDnComponent(String value) {
        if (value == null) {
            throw new DomainException(
                "DN_COMPONENT_NOT_NULL",
                "DN component cannot be null"
            );
        }

        if (value.isEmpty()) {
            return value;
        }

        StringBuilder escaped = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c == ',' || c == '=' || c == '+' || c == '\\' || c == '<' ||
                c == '>' || c == ';' || c == '"' || c == '/') {
                escaped.append('\\').append(c);
            } else {
                escaped.append(c);
            }
        }

        return escaped.toString();
    }

    /**
     * 이스케이프된 DN 컴포넌트를 원본으로 복원
     *
     * @param escaped 이스케이프된 문자열
     * @return 원본 문자열
     */
    public static String unescapeDnComponent(String escaped) {
        if (escaped == null || !escaped.contains("\\")) {
            return escaped;
        }

        StringBuilder unescaped = new StringBuilder();
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (c == '\\' && i + 1 < escaped.length()) {
                unescaped.append(escaped.charAt(++i));
            } else {
                unescaped.append(c);
            }
        }

        return unescaped.toString();
    }

    /**
     * CN에서 Surname (마지막 단어) 추출
     *
     * <p>예: "CSCA-KOREA" → "KOREA"</p>
     *
     * @param cn Common Name
     * @return Surname
     */
    private static String extractSurname(String cn) {
        if (cn == null || cn.isEmpty()) {
            return "Unknown";
        }

        String[] parts = cn.split("-");
        return parts.length > 0 ? parts[parts.length - 1] : cn;
    }

    /**
     * 현재 일시를 ISO-8601 형식의 문자열로 반환
     *
     * @return ISO-8601 형식의 일시 (예: "2025-10-25T10:30:00")
     */
    private static String getCurrentTimestamp() {
        return LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * 입력값 검증
     *
     * @param cn Common Name
     * @param countryCode 국가 코드
     * @param dataBase64 Base64 데이터
     * @throws DomainException 검증 실패 시
     */
    private static void validateInputs(String cn, String countryCode, String dataBase64) {
        if (cn == null || cn.isBlank()) {
            throw new DomainException(
                "INVALID_CN",
                "Common Name (cn) must not be null or blank"
            );
        }

        if (countryCode == null || countryCode.isBlank()) {
            throw new DomainException(
                "INVALID_COUNTRY_CODE",
                "Country code must not be null or blank"
            );
        }

        if (countryCode.length() != 2) {
            throw new DomainException(
                "INVALID_COUNTRY_CODE_LENGTH",
                "Country code must be exactly 2 characters (ISO 3166-1 alpha-2)"
            );
        }

        if (dataBase64 == null || dataBase64.isBlank()) {
            throw new DomainException(
                "INVALID_DATA_BASE64",
                "Base64 encoded data must not be null or blank"
            );
        }
    }

    /**
     * 속성 맵의 복사본 반환
     *
     * @return 읽기 전용 속성 맵
     */
    public Map<String, Object> getAttributeMap() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * 특정 속성값 조회
     *
     * @param attributeName 속성명
     * @return 속성값 (없으면 null)
     */
    public Object getAttribute(String attributeName) {
        return attributes.get(attributeName);
    }

    /**
     * 특정 속성이 존재하는지 확인
     *
     * @param attributeName 속성명
     * @return 존재하면 true
     */
    public boolean hasAttribute(String attributeName) {
        return attributes.containsKey(attributeName);
    }

    /**
     * 속성 개수
     *
     * @return 속성 개수
     */
    public int getAttributeCount() {
        return attributes.size();
    }

    /**
     * 문자열 표현
     *
     * @return "LdapAttributes[cn=..., countryCode=..., attrCount=...]"
     */
    @Override
    public String toString() {
        String cn = (String) attributes.get("cn");
        String countryCode = (String) attributes.get("countryCode");
        return String.format(
                "LdapAttributes[cn=%s, countryCode=%s, attrCount=%d, createdAt=%s]",
                cn, countryCode, attributes.size(), createdAt
        );
    }

    /**
     * LDAP 속성 빌더
     *
     * <p>커스텀 LDAP 속성을 유연하게 구성할 수 있습니다.</p>
     */
    public static class AttributeBuilder {

        private final Map<String, Object> attrs = new LinkedHashMap<>();
        private String createdAt = getCurrentTimestamp();

        /**
         * 속성 추가
         *
         * @param name 속성명
         * @param value 속성값
         * @return 빌더 인스턴스 (체이닝용)
         */
        public AttributeBuilder add(String name, Object value) {
            Objects.requireNonNull(name, "Attribute name cannot be null");
            if (value != null) {
                attrs.put(name, value);
            }
            return this;
        }

        /**
         * 생성 일시 설정
         *
         * @param createdAt ISO-8601 형식의 일시
         * @return 빌더 인스턴스 (체이닝용)
         */
        public AttributeBuilder createdAt(String createdAt) {
            if (createdAt != null) {
                this.createdAt = createdAt;
            }
            return this;
        }

        /**
         * LdapAttributes 빌드
         *
         * @return LdapAttributes
         */
        public LdapAttributes build() {
            if (attrs.isEmpty()) {
                throw new DomainException(
                    "EMPTY_ATTRIBUTES",
                    "At least one attribute must be added"
                );
            }
            return new LdapAttributes(attrs, createdAt);
        }
    }
}
