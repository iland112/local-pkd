package com.smartcoreinc.localpkd.passiveauthentication.domain.port;

import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * LDAP에서 CSCA 인증서를 조회하는 Port 인터페이스
 *
 * <p>ICAO 9303 Part 11 Passive Authentication에서 요구하는
 * CSCA 조회 기능을 정의합니다.</p>
 *
 * <p>Architecture Decision: PA Module은 LDAP만 사용하고 DBMS는 사용하지 않습니다.
 * DBMS의 certificate 테이블은 PKD Upload Module의 업로드 이력/통계 용도이며,
 * PA 검증은 실시간 LDAP 조회를 통해 수행됩니다.</p>
 *
 * @see com.smartcoreinc.localpkd.passiveauthentication.infrastructure.adapter.UnboundIdLdapCscaAdapter
 * @since Phase 4.11.4
 */
public interface LdapCscaRepository {

    /**
     * Subject DN으로 CSCA 인증서를 조회합니다.
     *
     * <p>LDAP 검색 조건:</p>
     * <ul>
     *     <li>Base DN: dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com</li>
     *     <li>Filter: (&(objectClass=pkdDownload)(o=csca)(cn={escaped-dn}))</li>
     *     <li>Attribute: userCertificate;binary</li>
     * </ul>
     *
     * @param subjectDn CSCA Subject DN (예: "CN=CSCA003,OU=MOFA,O=Government,C=KR")
     * @return CSCA X.509 인증서 (존재하지 않으면 Optional.empty())
     */
    Optional<X509Certificate> findBySubjectDn(String subjectDn);
}
