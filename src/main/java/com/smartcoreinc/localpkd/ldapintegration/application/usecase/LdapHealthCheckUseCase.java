package com.smartcoreinc.localpkd.ldapintegration.application.usecase;

import java.util.Map;

/**
 * LdapHealthCheckUseCase - LDAP 헬스 체크 유스케이스
 *
 * 이 유스케이스는 LDAP 서버의 현재 상태를 확인하고 그 결과를 반환합니다.
 */
public interface LdapHealthCheckUseCase {

    /**
     * LDAP 서버의 연결 상태를 확인하고 상세 정보를 반환합니다.
     *
     * @return LDAP 연결 상태 및 추가 정보를 담은 맵 (예: success, message 등)
     */
    Map<String, Object> execute();
}
