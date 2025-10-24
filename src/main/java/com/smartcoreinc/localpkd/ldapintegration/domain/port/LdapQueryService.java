package com.smartcoreinc.localpkd.ldapintegration.domain.port;

import com.smartcoreinc.localpkd.ldapintegration.domain.model.DistinguishedName;
import com.smartcoreinc.localpkd.ldapintegration.domain.model.LdapCertificateEntry;
import com.smartcoreinc.localpkd.ldapintegration.domain.model.LdapCrlEntry;
import com.smartcoreinc.localpkd.ldapintegration.domain.model.LdapSearchFilter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * LdapQueryService - LDAP 서버에서 데이터 조회 Port (Hexagonal Architecture)
 *
 * <p><b>Hexagonal Architecture Port</b>:</p>
 * <ul>
 *   <li>도메인에서 인프라스트럭처로의 의존성 역전</li>
 *   <li>Spring LDAP 또는 다른 LDAP 라이브러리로 구현 가능</li>
 *   <li>테스트 시 Mock 구현으로 대체 가능</li>
 * </ul>
 *
 * <h3>책임</h3>
 * <ul>
 *   <li>LDAP 서버에서 인증서 조회 및 검색</li>
 *   <li>LDAP 서버에서 CRL 조회 및 검색</li>
 *   <li>복합 필터를 이용한 고급 검색</li>
 *   <li>특정 DN의 엔트리 조회</li>
 *   <li>검색 결과 페이징 지원</li>
 * </ul>
 *
 * <h3>검색 성능</h3>
 * <ul>
 *   <li>결과 캐싱 (선택사항)</li>
 *   <li>타임아웃 설정 (기본 30초)</li>
 *   <li>결과 크기 제한 (최대 1000개)</li>
 *   <li>인덱스 활용 (issuerDN, countryCode 등)</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // 특정 인증서 조회
 * Optional<LdapCertificateEntry> cert = ldapQueryService.findCertificateByDn(
 *     DistinguishedName.of("cn=CSCA-KOREA,ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com")
 * );
 *
 * // 국가 코드로 인증서 검색
 * List<LdapCertificateEntry> certs = ldapQueryService.searchCertificatesByCountry("KR");
 *
 * // 필터를 이용한 검색
 * LdapSearchFilter filter = LdapSearchFilter.builder()
 *     .baseDn("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com")
 *     .filterString("(&(cn=CSCA-*)(countryCode=KR))")
 *     .scope(SearchScope.SUBTREE)
 *     .build();
 * List<Map<String, Object>> results = ldapQueryService.search(filter);
 *
 * // 페이징 검색
 * QueryResult<LdapCertificateEntry> page = ldapQueryService.searchCertificatesWithPagination(
 *     filter, 0, 20
 * );
 * log.info("Total: {}, Page size: {}", page.getTotalCount(), page.getPageSize());
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
public interface LdapQueryService {

    /**
     * 특정 DN의 인증서 엔트리 조회
     *
     * @param dn Distinguished Name
     * @return 조회 결과
     * @throws LdapQueryException 조회 실패 시
     */
    Optional<LdapCertificateEntry> findCertificateByDn(DistinguishedName dn);

    /**
     * 특정 DN의 CRL 엔트리 조회
     *
     * @param dn Distinguished Name
     * @return 조회 결과
     * @throws LdapQueryException 조회 실패 시
     */
    Optional<LdapCrlEntry> findCrlByDn(DistinguishedName dn);

    /**
     * Common Name으로 인증서 검색
     *
     * @param cn Common Name (예: "CSCA-KOREA")
     * @return 매칭되는 인증서 목록
     */
    List<LdapCertificateEntry> searchCertificatesByCommonName(String cn);

    /**
     * 국가 코드로 인증서 검색
     *
     * <p>특정 국가의 모든 인증서를 조회합니다.</p>
     *
     * @param countryCode 국가 코드 (2자리, 예: "KR")
     * @return 해당 국가의 인증서 목록
     */
    List<LdapCertificateEntry> searchCertificatesByCountry(String countryCode);

    /**
     * 발급자 DN으로 CRL 검색
     *
     * @param issuerDn 발급자 Distinguished Name
     * @return 매칭되는 CRL 목록
     */
    List<LdapCrlEntry> searchCrlsByIssuerDn(String issuerDn);

    /**
     * 커스텀 LDAP 필터로 엔트리 검색
     *
     * <p>RFC 4515 형식의 필터를 사용합니다.</p>
     *
     * @param filter LDAP 검색 필터
     * @return 검색 결과 (속성 맵 리스트)
     * @throws LdapQueryException 검색 실패 시
     */
    List<Map<String, Object>> search(LdapSearchFilter filter);

    /**
     * 페이징을 지원하는 검색
     *
     * @param filter LDAP 검색 필터
     * @param pageIndex 페이지 인덱스 (0부터 시작)
     * @param pageSize 페이지 크기
     * @return 페이징된 검색 결과
     */
    QueryResult<Map<String, Object>> searchWithPagination(LdapSearchFilter filter, int pageIndex, int pageSize);

    /**
     * 인증서를 페이징으로 검색
     *
     * @param filter LDAP 검색 필터
     * @param pageIndex 페이지 인덱스
     * @param pageSize 페이지 크기
     * @return 페이징된 인증서 검색 결과
     */
    QueryResult<LdapCertificateEntry> searchCertificatesWithPagination(
            LdapSearchFilter filter, int pageIndex, int pageSize
    );

    /**
     * CRL을 페이징으로 검색
     *
     * @param filter LDAP 검색 필터
     * @param pageIndex 페이지 인덱스
     * @param pageSize 페이지 크기
     * @return 페이징된 CRL 검색 결과
     */
    QueryResult<LdapCrlEntry> searchCrlsWithPagination(LdapSearchFilter filter, int pageIndex, int pageSize);

    /**
     * 특정 기본 DN 아래의 모든 인증서 수 조회
     *
     * @param baseDn 기본 DN
     * @return 인증서 개수
     */
    long countCertificates(DistinguishedName baseDn);

    /**
     * 특정 기본 DN 아래의 모든 CRL 수 조회
     *
     * @param baseDn 기본 DN
     * @return CRL 개수
     */
    long countCrls(DistinguishedName baseDn);

    /**
     * 국가별 인증서 개수 조회
     *
     * @return 국가 코드 → 인증서 개수 맵
     */
    Map<String, Long> countCertificatesByCountry();

    /**
     * 발급자별 CRL 개수 조회
     *
     * @return 발급자 DN → CRL 개수 맵
     */
    Map<String, Long> countCrlsByIssuer();

    /**
     * 특정 기본 DN 아래의 모든 하위 DN 목록 조회
     *
     * <p>조직 단위(OU) 등 계층 구조 확인 시 사용</p>
     *
     * @param baseDn 기본 DN
     * @return 하위 DN 목록
     */
    List<DistinguishedName> listSubordinateDns(DistinguishedName baseDn);

    /**
     * LDAP 서버 연결 테스트
     *
     * @return 연결 성공 여부
     */
    boolean testConnection();

    /**
     * LDAP 서버 정보 조회
     *
     * @return 서버 정보 (버전, 지원 기능 등)
     */
    String getServerInfo();

    /**
     * 검색 결과 정보
     *
     * @param <T> 결과 항목 타입
     */
    interface QueryResult<T> {
        /**
         * 총 결과 개수
         */
        long getTotalCount();

        /**
         * 현재 페이지 인덱스
         */
        int getCurrentPageIndex();

        /**
         * 페이지 크기
         */
        int getPageSize();

        /**
         * 현재 페이지의 항목들
         */
        List<T> getContent();

        /**
         * 총 페이지 수
         */
        int getTotalPages();

        /**
         * 다음 페이지 존재 여부
         */
        boolean hasNextPage();

        /**
         * 이전 페이지 존재 여부
         */
        boolean hasPreviousPage();

        /**
         * 현재 페이지의 항목 개수
         */
        int getContentSize();

        /**
         * 검색 완료 시간 (밀리초)
         */
        long getDurationMillis();
    }

    /**
     * LDAP 조회 예외
     */
    class LdapQueryException extends RuntimeException {
        public LdapQueryException(String message) {
            super(message);
        }

        public LdapQueryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * LDAP 결과 없음 예외 (Optional 대신 예외를 사용할 때)
     */
    class LdapEntryNotFoundException extends LdapQueryException {
        private final DistinguishedName dn;

        public LdapEntryNotFoundException(DistinguishedName dn) {
            super("LDAP entry not found: " + dn.getValue());
            this.dn = dn;
        }

        public DistinguishedName getDn() {
            return dn;
        }
    }
}
