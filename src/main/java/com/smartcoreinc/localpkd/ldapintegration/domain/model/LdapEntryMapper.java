package com.smartcoreinc.localpkd.ldapintegration.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * LdapEntryMapper - LDAP 엔트리 ↔ 도메인 모델 변환 Value Object
 *
 * <p><b>DDD Value Object Pattern</b>:</p>
 * <ul>
 *   <li>Immutability: 생성 후 변경 불가</li>
 *   <li>Self-validation: 변환 시 타입 및 형식 검증</li>
 *   <li>Value equality: 매핑 규칙으로 동등성 판단</li>
 * </ul>
 *
 * <h3>책임</h3>
 * <ul>
 *   <li>LDAP 엔트리 속성을 Java 타입으로 변환</li>
 *   <li>도메인 모델을 LDAP 속성으로 역변환</li>
 *   <li>타입 변환 로직 (String → UUID, LocalDateTime, Base64 등)</li>
 *   <li>타입 안전성 확보</li>
 *   <li>변환 오류 처리</li>
 * </ul>
 *
 * <h3>지원되는 타입 변환</h3>
 * <ul>
 *   <li>String → UUID: UUID.fromString()</li>
 *   <li>String → LocalDateTime: ISO-8601 형식 파싱</li>
 *   <li>String → Base64: Base64.getDecoder()</li>
 *   <li>String → Integer/Long/Boolean: 표준 변환</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // 매퍼 생성
 * LdapEntryMapper mapper = LdapEntryMapper.builder()
 *     .mapAttribute("dn", String.class)
 *     .mapAttribute("cn", String.class)
 *     .mapAttribute("countryCode", String.class)
 *     .mapAttribute("x509certificatedata", byte[].class)
 *     .mapAttribute("createTimestamp", LocalDateTime.class)
 *     .build();
 *
 * // LDAP 엔트리 → Java 객체
 * Map<String, Object> ldapAttrs = new HashMap<>();
 * ldapAttrs.put("dn", "cn=CSCA-KOREA,...");
 * ldapAttrs.put("cn", "CSCA-KOREA");
 * ldapAttrs.put("x509certificatedata", "MIIDXz...");
 * ldapAttrs.put("createTimestamp", "2025-10-25T10:30:00");
 *
 * String dn = mapper.getValue(ldapAttrs, "dn", String.class);
 * byte[] cert = mapper.getValue(ldapAttrs, "x509certificatedata", byte[].class);
 * LocalDateTime created = mapper.getValue(ldapAttrs, "createTimestamp", LocalDateTime.class);
 *
 * // 타입 검증
 * if (mapper.isValidType("countryCode", String.class)) {
 *     // countryCode는 String 타입이어야 함
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Getter
@EqualsAndHashCode
public class LdapEntryMapper implements ValueObject, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 속성 이름 → 기대되는 Java 타입 매핑 (immutable)
     */
    private final Map<String, Class<?>> typeMapping;

    /**
     * Private 생성자
     */
    private LdapEntryMapper(Map<String, Class<?>> typeMapping) {
        this.typeMapping = Map.copyOf(typeMapping);
    }

    /**
     * 빌더 인스턴스 생성
     *
     * @return MapperBuilder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 인증서 엔트리용 매퍼 생성 (사전 정의됨)
     *
     * @return 인증서 매핑이 설정된 LdapEntryMapper
     */
    public static LdapEntryMapper forCertificateEntry() {
        return LdapEntryMapper.builder()
                .mapAttribute("dn", String.class)
                .mapAttribute("cn", String.class)
                .mapAttribute("countryCode", String.class)
                .mapAttribute("description", String.class)
                .mapAttribute("x509certificatedata", byte[].class)
                .mapAttribute("createTimestamp", LocalDateTime.class)
                .mapAttribute("modifyTimestamp", LocalDateTime.class)
                .mapAttribute("objectClass", String.class)
                .build();
    }

    /**
     * CRL 엔트리용 매퍼 생성 (사전 정의됨)
     *
     * @return CRL 매핑이 설정된 LdapEntryMapper
     */
    public static LdapEntryMapper forCrlEntry() {
        return LdapEntryMapper.builder()
                .mapAttribute("dn", String.class)
                .mapAttribute("cn", String.class)
                .mapAttribute("countryCode", String.class)
                .mapAttribute("issuerDN", String.class)
                .mapAttribute("x509crldata", byte[].class)
                .mapAttribute("thisUpdate", LocalDateTime.class)
                .mapAttribute("nextUpdate", LocalDateTime.class)
                .mapAttribute("createTimestamp", LocalDateTime.class)
                .mapAttribute("objectClass", String.class)
                .build();
    }

    /**
     * LDAP 속성에서 값 추출 및 타입 변환
     *
     * @param attributes LDAP 속성 맵
     * @param attributeName 속성명
     * @param targetType 목표 타입
     * @param <T> 제네릭 타입
     * @return 변환된 값
     * @throws DomainException 변환 실패 시 또는 타입 불일치 시
     */
    public <T> T getValue(Map<String, Object> attributes, String attributeName, Class<T> targetType) {
        Objects.requireNonNull(attributes, "Attributes map must not be null");
        Objects.requireNonNull(attributeName, "Attribute name must not be null");
        Objects.requireNonNull(targetType, "Target type must not be null");

        // 속성 존재 확인
        if (!attributes.containsKey(attributeName)) {
            throw new DomainException(
                "ATTRIBUTE_NOT_FOUND",
                String.format("Attribute '%s' not found in LDAP entry", attributeName)
            );
        }

        Object value = attributes.get(attributeName);

        // null 값 처리
        if (value == null) {
            throw new DomainException(
                "ATTRIBUTE_IS_NULL",
                String.format("Attribute '%s' has null value", attributeName)
            );
        }

        // 타입 검증
        if (!isValidType(attributeName, targetType)) {
            throw new DomainException(
                "TYPE_MISMATCH",
                String.format("Expected %s for attribute '%s', but mapper is configured for %s",
                        targetType.getSimpleName(), attributeName,
                        typeMapping.getOrDefault(attributeName, Object.class).getSimpleName())
            );
        }

        // 타입 변환
        try {
            return convertValue(value, targetType);
        } catch (Exception e) {
            throw new DomainException(
                "CONVERSION_ERROR",
                String.format("Failed to convert attribute '%s' to %s: %s",
                        attributeName, targetType.getSimpleName(), e.getMessage())
            );
        }
    }

    /**
     * LDAP 속성에서 선택적으로 값 추출
     *
     * @param attributes LDAP 속성 맵
     * @param attributeName 속성명
     * @param targetType 목표 타입
     * @param defaultValue 속성이 없으면 반환할 기본값
     * @param <T> 제네릭 타입
     * @return 변환된 값 또는 기본값
     */
    public <T> T getValueOrDefault(Map<String, Object> attributes, String attributeName,
                                     Class<T> targetType, T defaultValue) {
        try {
            return getValue(attributes, attributeName, targetType);
        } catch (DomainException e) {
            return defaultValue;
        }
    }

    /**
     * 속성이 매퍼에 등록된 타입과 일치하는지 확인
     *
     * @param attributeName 속성명
     * @param expectedType 기대하는 타입
     * @return 일치하면 true
     */
    public boolean isValidType(String attributeName, Class<?> expectedType) {
        if (!typeMapping.containsKey(attributeName)) {
            return false;
        }
        return typeMapping.get(attributeName).isAssignableFrom(expectedType);
    }

    /**
     * 등록된 모든 속성명 조회
     *
     * @return 속성명 배열
     */
    public String[] getAttributeNames() {
        return typeMapping.keySet().toArray(new String[0]);
    }

    /**
     * 속성 타입 조회
     *
     * @param attributeName 속성명
     * @return 매핑된 Java 타입 (없으면 null)
     */
    public Class<?> getAttributeType(String attributeName) {
        return typeMapping.get(attributeName);
    }

    /**
     * 등록된 속성 개수
     *
     * @return 속성 개수
     */
    public int getAttributeCount() {
        return typeMapping.size();
    }

    /**
     * 값을 목표 타입으로 변환
     *
     * @param value 변환할 값
     * @param targetType 목표 타입
     * @param <T> 제네릭 타입
     * @return 변환된 값
     */
    @SuppressWarnings("unchecked")
    private <T> T convertValue(Object value, Class<T> targetType) {
        // 이미 목표 타입인 경우
        if (targetType.isInstance(value)) {
            return (T) value;
        }

        // String에서 다른 타입으로 변환
        if (value instanceof String str) {
            if (targetType == UUID.class) {
                try {
                    return (T) UUID.fromString(str);
                } catch (IllegalArgumentException e) {
                    throw new DomainException(
                        "INVALID_UUID_FORMAT",
                        "Invalid UUID format: " + str
                    );
                }
            }

            if (targetType == LocalDateTime.class) {
                return (T) parseLocalDateTime(str);
            }

            if (targetType == Integer.class) {
                try {
                    return (T) Integer.valueOf(str);
                } catch (NumberFormatException e) {
                    throw new DomainException(
                        "INVALID_INTEGER_FORMAT",
                        "Invalid integer format: " + str
                    );
                }
            }

            if (targetType == Long.class) {
                try {
                    return (T) Long.valueOf(str);
                } catch (NumberFormatException e) {
                    throw new DomainException(
                        "INVALID_LONG_FORMAT",
                        "Invalid long format: " + str
                    );
                }
            }

            if (targetType == Boolean.class) {
                return (T) Boolean.valueOf(str);
            }

            if (targetType == byte[].class) {
                return (T) decodeBase64(str);
            }

            if (targetType == String.class) {
                return (T) str;
            }
        }

        // Base64 문자열에서 byte[] 로 변환
        if (value instanceof String && targetType == byte[].class) {
            return (T) decodeBase64((String) value);
        }

        throw new DomainException(
            "UNSUPPORTED_CONVERSION",
            String.format("Unsupported conversion from %s to %s",
                    value.getClass().getSimpleName(), targetType.getSimpleName())
        );
    }

    /**
     * ISO-8601 형식의 LocalDateTime 문자열 파싱
     *
     * <p>지원되는 형식:</p>
     * <ul>
     *   <li>2025-10-25T10:30:00</li>
     *   <li>2025-10-25T10:30:00Z</li>
     *   <li>2025-10-25 10:30:00</li>
     * </ul>
     *
     * @param dateTimeStr 파싱할 문자열
     * @return 파싱된 LocalDateTime
     * @throws DomainException 파싱 실패 시
     */
    private LocalDateTime parseLocalDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            throw new DomainException(
                "EMPTY_DATE_TIME",
                "DateTime string must not be null or blank"
            );
        }

        // 'Z' 제거
        String normalized = dateTimeStr.replace("Z", "").replace(" ", "T");

        try {
            // ISO-8601 형식 파싱 시도
            return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new DomainException(
                "INVALID_DATE_TIME_FORMAT",
                String.format("Invalid DateTime format (expected ISO-8601): %s", dateTimeStr)
            );
        }
    }

    /**
     * Base64 문자열을 바이트 배열로 디코딩
     *
     * @param base64Str Base64 인코딩된 문자열
     * @return 디코딩된 바이트 배열
     * @throws DomainException 디코딩 실패 시
     */
    private byte[] decodeBase64(String base64Str) {
        if (base64Str == null || base64Str.isBlank()) {
            throw new DomainException(
                "EMPTY_BASE64",
                "Base64 string must not be null or blank"
            );
        }

        try {
            return Base64.getDecoder().decode(base64Str);
        } catch (IllegalArgumentException e) {
            throw new DomainException(
                "INVALID_BASE64_FORMAT",
                "Invalid Base64 format: " + e.getMessage()
            );
        }
    }

    /**
     * 문자열 표현
     *
     * @return "LdapEntryMapper[attrCount=...]"
     */
    @Override
    public String toString() {
        return String.format("LdapEntryMapper[attrCount=%d, attrs=%s]",
                typeMapping.size(), typeMapping.keySet());
    }

    /**
     * LDAP 엔트리 매퍼 빌더
     */
    public static class Builder {

        private final Map<String, Class<?>> typeMapping = new HashMap<>();

        /**
         * 속성-타입 매핑 추가
         *
         * @param attributeName 속성명
         * @param javaType Java 타입
         * @return 빌더 인스턴스 (체이닝용)
         */
        public Builder mapAttribute(String attributeName, Class<?> javaType) {
            if (attributeName == null || attributeName.isBlank()) {
                throw new DomainException(
                    "ATTRIBUTE_NAME_NOT_BLANK",
                    "Attribute name must not be null or blank"
                );
            }

            if (javaType == null) {
                throw new DomainException(
                    "JAVA_TYPE_NOT_NULL",
                    "Java type must not be null"
                );
            }

            // 지원되는 타입 검증
            if (!isSupportedType(javaType)) {
                throw new DomainException(
                    "UNSUPPORTED_TYPE",
                    String.format("Type %s is not supported. Supported types: String, UUID, LocalDateTime, " +
                                    "Integer, Long, Boolean, byte[]",
                            javaType.getSimpleName())
                );
            }

            typeMapping.put(attributeName, javaType);
            return this;
        }

        /**
         * 여러 속성을 동일한 타입으로 매핑
         *
         * @param javaType Java 타입
         * @param attributeNames 속성명들
         * @return 빌더 인스턴스 (체이닝용)
         */
        public Builder mapAttributesAs(Class<?> javaType, String... attributeNames) {
            if (attributeNames != null) {
                for (String attributeName : attributeNames) {
                    mapAttribute(attributeName, javaType);
                }
            }
            return this;
        }

        /**
         * 속성 매핑 제거
         *
         * @param attributeName 속성명
         * @return 빌더 인스턴스 (체이닝용)
         */
        public Builder unmapAttribute(String attributeName) {
            typeMapping.remove(attributeName);
            return this;
        }

        /**
         * 모든 매핑 초기화
         *
         * @return 빌더 인스턴스 (체이닝용)
         */
        public Builder clear() {
            typeMapping.clear();
            return this;
        }

        /**
         * LdapEntryMapper 빌드
         *
         * @return LdapEntryMapper
         * @throws DomainException 매핑이 비어있는 경우
         */
        public LdapEntryMapper build() {
            if (typeMapping.isEmpty()) {
                throw new DomainException(
                    "EMPTY_MAPPING",
                    "At least one attribute mapping must be defined"
                );
            }

            return new LdapEntryMapper(typeMapping);
        }
    }

    /**
     * 타입이 변환을 지원하는지 확인
     *
     * @param type 확인할 타입
     * @return 지원하면 true
     */
    private static boolean isSupportedType(Class<?> type) {
        return type == String.class ||
                type == UUID.class ||
                type == LocalDateTime.class ||
                type == Integer.class ||
                type == Long.class ||
                type == Boolean.class ||
                type == byte[].class;
    }
}
