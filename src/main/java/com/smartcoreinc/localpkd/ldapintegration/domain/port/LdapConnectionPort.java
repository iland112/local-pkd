package com.smartcoreinc.localpkd.ldapintegration.domain.port;

/**
 * LdapConnectionPort - LDAP 연결 관련 도메인 포트
 *
 * 이 포트는 LDAP 서버와의 연결 및 상태를 확인하는 기능을 제공합니다.
 * 인프라 계층의 어댑터에 의해 구현됩니다.
 */
public interface LdapConnectionPort {

    /**
     * LDAP 서버 연결 상태를 테스트합니다.
     *
     * @return 연결 성공 여부
     */
    boolean testConnection();
}
