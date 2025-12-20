package com.smartcoreinc.localpkd.ldapintegration.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * LdapSearchFilter - LDAP 검색 조건 캡슐화 Value Object
 *
 * <p><b>DDD Value Object Pattern</b>:</p>
 * <ul>
 *   <li>Immutability: 생성 후 변경 불가</li>
 *   <li>Self-validation: 생성 시 필터 형식 검증</li>
 *   <li>Value equality: 모든 검색 조건으로 동등성 판단</li>
 * </ul>
 *
 * <h3>책임</h3>
 * <ul>
 *   <li>LDAP 검색 조건(필터, 범위, DN) 관리</li>
 *   <li>RFC 4515 LDAP 필터 형식 검증</li>
 *   <li>검색 범위 및 제한값 검증</li>
 *   <li>필터 문자열 이스케이핑</li>
 * </ul>
 *
 * <h3>LDAP 검색 범위</h3>
 * <ul>
 *   <li>{@code OBJECT_SCOPE}: 특정 DN의 객체만 검색</li>
 *   <li>{@code ONE_LEVEL}: 기본 DN의 직접 하위만 검색</li>
 *   <li>{@code SUBTREE}: 기본 DN 하위 전체 검색 (권장)</li>
 * </ul>
 *
 * <h3>LDAP 필터 예시</h3>
 * <pre>{@code
 * // 단순 검색
 * "(cn=CSCA-KOREA)"
 *
 * // 와일드카드 검색
 * "(cn=CSCA-*)"
 *
 * // AND 조건
 * "(&(cn=CSCA-*)(countryCode=KR))"
 *
 * // OR 조건
 * "(|(cn=CSCA-KR)(cn=CSCA-JP))"
 *
 * // NOT 조건
 * "(!(cn=CSCA-UNKNOWN))"
 *
 * // 복합 조건
 * "(&(objectClass=inetOrgPerson)(|(cn=CSCA-*)(cn=DSC-*)))"
 * }</pre>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // 검색 필터 생성
 * LdapSearchFilter filter = LdapSearchFilter.builder()
 *     .baseDn("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com")
 *     .filterString("(cn=CSCA-KOREA)")
 *     .scope(SearchScope.SUBTREE)
 *     .returningAttributes("cn", "countryCode", "x509certificatedata")
 *     .sizeLimit(100)
 *     .timeLimit(30)
 *     .build();
 *
 * // 필터 정보 조회
 * String baseDn = filter.getBaseDn();
 * String filterStr = filter.getFilterString();
 * List<String> attrs = filter.getReturningAttributes();
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Getter
@EqualsAndHashCode
public class LdapSearchFilter implements ValueObject, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * LDAP 검색 범위 (Scope)
     */
    public enum SearchScope {
        OBJECT_SCOPE("OBJECT_SCOPE", 0),
        ONE_LEVEL("ONE_LEVEL", 1),
        SUBTREE("SUBTREE", 2);

        private final String displayName;
        private final int value;

        SearchScope(String displayName, int value) {
            this.displayName = displayName;
            this.value = value;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * 검색 기본 DN
     */
    private final DistinguishedName baseDn;

    /**
     * LDAP 필터 문자열 (RFC 4515 형식)
     * 예: "(cn=CSCA-KOREA)"
     */
    private final String filterString;

    /**
     * 검색 범위
     */
    private final SearchScope scope;

    /**
     * 반환할 속성 목록 (immutable)
     */
    private final List<String> returningAttributes;

    /**
     * 검색 결과 크기 제한
     * -1: 제한 없음, 0이상: 최대 결과 수
     */
    private final int sizeLimit;

    /**
     * 검색 타임아웃 (초)
     * -1: 제한 없음, 0이상: 최대 대기 시간
     */
    private final int timeLimit;

    /**
     * 대소문자 구분 여부
     */
    private final boolean caseSensitive;

    /**
     * 검색 필터 정규식 검증 패턴
     * 기본 LDAP 필터 형식: "(...)"
     */
    private static final Pattern LDAP_FILTER_PATTERN = Pattern.compile("^\\([^)]*\\)$");

    /**
     * Private 생성자
     */
    private LdapSearchFilter(Builder builder) {
        this.baseDn = builder.baseDn;
        this.filterString = builder.filterString;
        this.scope = builder.scope;
        this.returningAttributes = Collections.unmodifiableList(builder.returningAttributes);
        this.sizeLimit = builder.sizeLimit;
        this.timeLimit = builder.timeLimit;
        this.caseSensitive = builder.caseSensitive;
    }

    /**
     * 빌더 인스턴스 생성
     *
     * @return FilterBuilder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 특정 CN으로 인증서 검색 필터 생성
     *
     * @param baseDn 검색 기본 DN
     * @param cn 찾을 Common Name
     * @return LdapSearchFilter
     */
    public static LdapSearchFilter forCertificateWithCn(DistinguishedName baseDn, String cn) {
        if (cn == null || cn.isBlank()) {
            throw new DomainException(
                "INVALID_CN",
                "Common Name must not be null or blank"
            );
        }

        String filter = String.format("(cn=%s)", escapeLdapFilterValue(cn));
        return LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString(filter)
                .scope(SearchScope.SUBTREE)
                .returningAttributes("cn", "countryCode", "x509certificatedata", "createTimestamp")
                .sizeLimit(10)
                .timeLimit(10)
                .build();
    }

    /**
     * 국가 코드로 인증서 검색 필터 생성
     *
     * @param baseDn 검색 기본 DN
     * @param countryCode 국가 코드 (2자리)
     * @return LdapSearchFilter
     */
    public static LdapSearchFilter forCertificateWithCountry(DistinguishedName baseDn, String countryCode) {
        if (countryCode == null || countryCode.length() != 2) {
            throw new DomainException(
                "INVALID_COUNTRY_CODE",
                "Country code must be exactly 2 characters"
            );
        }

        String filter = String.format("(countryCode=%s)", countryCode);
        return LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString(filter)
                .scope(SearchScope.SUBTREE)
                .returningAttributes("cn", "countryCode", "x509certificatedata")
                .sizeLimit(1000)
                .timeLimit(30)
                .build();
    }

    /**
     * CRL 검색 필터 생성
     *
     * @param baseDn 검색 기본 DN
     * @param issuerDn 발급자 DN
     * @return LdapSearchFilter
     */
    public static LdapSearchFilter forCrlWithIssuer(DistinguishedName baseDn, String issuerDn) {
        if (issuerDn == null || issuerDn.isBlank()) {
            throw new DomainException(
                "INVALID_ISSUER_DN",
                "Issuer DN must not be null or blank"
            );
        }

        String filter = String.format("(issuerDN=%s)", escapeLdapFilterValue(issuerDn));
        return LdapSearchFilter.builder()
                .baseDn(baseDn)
                .filterString(filter)
                .scope(SearchScope.SUBTREE)
                .returningAttributes("cn", "issuerDN", "x509crldata", "thisUpdate", "nextUpdate")
                .sizeLimit(10)
                .timeLimit(10)
                .build();
    }

    /**
     * LDAP 필터 값에서 특수 문자를 이스케이프
     *
     * <p>RFC 4515: LDAP 필터의 AssertionValue에서 이스케이프해야 할 문자들</p>
     *
     * @param value 이스케이프할 값
     * @return 이스케이프된 값
     */
    public static String escapeLdapFilterValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        StringBuilder escaped = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '*':
                    escaped.append("\\2a");
                    break;
                case '(':
                    escaped.append("\\28");
                    break;
                case ')':
                    escaped.append("\\29");
                    break;
                case '\\':
                    escaped.append("\\5c");
                    break;
                case '\0':
                    escaped.append("\\00");
                    break;
                default:
                    escaped.append(c);
            }
        }
        return escaped.toString();
    }

    /**
     * 이스케이프된 LDAP 필터 값을 원본으로 복원
     *
     * @param escaped 이스케이프된 값
     * @return 원본 값
     */
    public static String unescapeLdapFilterValue(String escaped) {
        if (escaped == null || !escaped.contains("\\")) {
            return escaped;
        }

        StringBuilder unescaped = new StringBuilder();
        for (int i = 0; i < escaped.length(); i++) {
            if (escaped.charAt(i) == '\\' && i + 2 < escaped.length()) {
                String hex = escaped.substring(i + 1, i + 3);
                try {
                    int charCode = Integer.parseInt(hex, 16);
                    unescaped.append((char) charCode);
                    i += 2;
                } catch (NumberFormatException e) {
                    unescaped.append(escaped.charAt(i));
                }
            } else {
                unescaped.append(escaped.charAt(i));
            }
        }
        return unescaped.toString();
    }

    /**
     * AND 연산자로 여러 필터 결합
     *
     * @param filters 결합할 필터들
     * @return 결합된 필터
     */
    public static String combineFiltersWithAnd(String... filters) {
        if (filters == null || filters.length == 0) {
            throw new DomainException(
                "EMPTY_FILTERS",
                "At least one filter must be provided"
            );
        }

        StringBuilder combined = new StringBuilder("(&");
        for (String filter : filters) {
            combined.append(filter);
        }
        combined.append(")");

        return combined.toString();
    }

    /**
     * OR 연산자로 여러 필터 결합
     *
     * @param filters 결합할 필터들
     * @return 결합된 필터
     */
    public static String combineFiltersWithOr(String... filters) {
        if (filters == null || filters.length == 0) {
            throw new DomainException(
                "EMPTY_FILTERS",
                "At least one filter must be provided"
            );
        }

        StringBuilder combined = new StringBuilder("(|");
        for (String filter : filters) {
            combined.append(filter);
        }
        combined.append(")");

        return combined.toString();
    }

    /**
     * 필터 문자열 유효성 확인
     *
     * @param filter 확인할 필터 문자열
     * @return 유효하면 true
     */
    public static boolean isValidFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return false;
        }

        // 간단한 패턴 검증 (정규식)
        return LDAP_FILTER_PATTERN.matcher(filter).matches();
    }

    /**
     * 검색 범위에 따른 설명 반환
     *
     * @return 범위 설명
     */
    public String getScopeDescription() {
        return switch (scope) {
            case OBJECT_SCOPE -> "특정 DN의 객체만";
            case ONE_LEVEL -> "기본 DN의 직접 하위만";
            case SUBTREE -> "기본 DN 하위 전체";
        };
    }

    /**
     * 검색 조건 요약
     *
     * @return "SearchFilter[baseDn=..., filter=..., scope=..., attrs=...]"
     */
    @Override
    public String toString() {
        return String.format(
                "LdapSearchFilter[baseDn=%s, filter=%s, scope=%s, attrCount=%d, sizeLimit=%d, timeLimit=%d]",
                baseDn.getValue(), filterString, scope.getDisplayName(),
                returningAttributes.size(), sizeLimit, timeLimit
        );
    }

    /**
     * LDAP 검색 필터 빌더
     */
    public static class Builder {

        private DistinguishedName baseDn;
        private String filterString;
        private SearchScope scope = SearchScope.SUBTREE;
        private List<String> returningAttributes = new ArrayList<>();
        private int sizeLimit = 1000;
        private int timeLimit = 30;
        private boolean caseSensitive = true;

        /**
         * 검색 기본 DN 설정
         *
         * @param baseDn 기본 DN
         * @return 빌더 인스턴스 (체이닝용)
         */
        public Builder baseDn(DistinguishedName baseDn) {
            if (baseDn == null) {
                throw new DomainException(
                    "BASE_DN_NOT_NULL",
                    "Base DN must not be null"
                );
            }
            this.baseDn = baseDn;
            return this;
        }

        /**
         * 검색 기본 DN 설정 (문자열)
         *
         * @param baseDn 기본 DN 문자열
         * @return 빌더 인스턴스 (체이닝용)
         */
        public Builder baseDn(String baseDn) {
            this.baseDn = DistinguishedName.of(baseDn);
            return this;
        }

        /**
         * LDAP 필터 문자열 설정
         *
         * @param filterString LDAP 필터 문자열
         * @return 빌더 인스턴스 (체이닝용)
         */
        public Builder filterString(String filterString) {
            if (filterString == null || filterString.isBlank()) {
                throw new DomainException(
                    "FILTER_NOT_BLANK",
                    "Filter string must not be null or blank"
                );
            }

            // 기본 유효성 검사 (간단한 문법)
            if (!isValidFilter(filterString)) {
                throw new DomainException(
                    "INVALID_FILTER_FORMAT",
                    "Filter must follow RFC 4515 format (e.g., '(cn=value)'). Got: " + filterString
                );
            }

            this.filterString = filterString;
            return this;
        }

        /**
         * 검색 범위 설정
         *
         * @param scope 검색 범위
         * @return 빌더 인스턴스 (체이닝용)
         */
        public Builder scope(SearchScope scope) {
            if (scope != null) {
                this.scope = scope;
            }
            return this;
        }

        /**
         * 반환 속성 설정
         *
         * @param attributes 속성명들
         * @return 빌더 인스턴스 (체이닝용)
         */
        public Builder returningAttributes(String... attributes) {
            if (attributes != null) {
                this.returningAttributes = new ArrayList<>(Arrays.asList(attributes));
            }
            return this;
        }

        /**
         * 반환 속성 설정 (리스트)
         *
         * @param attributes 속성명 리스트
         * @return 빌더 인스턴스 (체이닝용)
         */
        public Builder returningAttributes(List<String> attributes) {
            if (attributes != null) {
                this.returningAttributes = new ArrayList<>(attributes);
            }
            return this;
        }

        /**
         * 검색 결과 크기 제한 설정
         *
         * @param sizeLimit 최대 결과 수 (-1: 제한 없음)
         * @return 빌더 인스턴스 (체이닝용)
         */
        public Builder sizeLimit(int sizeLimit) {
            if (sizeLimit < -1) {
                throw new DomainException(
                    "INVALID_SIZE_LIMIT",
                    "Size limit must be -1 (unlimited) or 0 or greater"
                );
            }
            this.sizeLimit = sizeLimit;
            return this;
        }

        /**
         * 검색 타임아웃 설정
         *
         * @param timeLimit 최대 대기 시간(초) (-1: 제한 없음)
         * @return 빌더 인스턴스 (체이닝용)
         */
        public Builder timeLimit(int timeLimit) {
            if (timeLimit < -1) {
                throw new DomainException(
                    "INVALID_TIME_LIMIT",
                    "Time limit must be -1 (unlimited) or 0 or greater"
                );
            }
            this.timeLimit = timeLimit;
            return this;
        }

        /**
         * 대소문자 구분 설정
         *
         * @param caseSensitive 대소문자 구분 여부
         * @return 빌더 인스턴스 (체이닝용)
         */
        public Builder caseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }

        /**
         * LdapSearchFilter 빌드
         *
         * @return LdapSearchFilter
         * @throws DomainException 필수 필드가 설정되지 않았거나 유효하지 않은 경우
         */
        public LdapSearchFilter build() {
            if (baseDn == null) {
                throw new DomainException(
                    "BASE_DN_REQUIRED",
                    "Base DN is required"
                );
            }

            if (filterString == null || filterString.isBlank()) {
                throw new DomainException(
                    "FILTER_REQUIRED",
                    "Filter string is required"
                );
            }

            return new LdapSearchFilter(this);
        }
    }
}
