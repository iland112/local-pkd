package com.smartcoreinc.localpkd.certificatevalidation.domain.exception;

import com.smartcoreinc.localpkd.shared.exception.DomainException;

/**
 * LdapConnectionException - LDAP 연결 예외
 *
 * <p><b>목적</b>: LDAP 서버 연결 실패, 타임아웃, 인증 실패 등을 나타냅니다.</p>
 *
 * <p><b>발생 시나리오</b>:
 * <ul>
 *   <li>LDAP 서버 연결 실패 (Network, Server down)</li>
 *   <li>연결 타임아웃</li>
 *   <li>인증 실패 (Invalid credentials)</li>
 *   <li>SSL/TLS 핸드셰이크 실패</li>
 *   <li>연결 풀 고갈</li>
 * </ul>
 * </p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * try {
 *     ldapConnectionPort.connect();
 * } catch (LdapConnectionException e) {
 *     log.error("LDAP connection failed: {}", e.getMessage());
 *     throw e;
 * }
 * }</pre>
 *
 * @see DomainException
 * @see LdapOperationException
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24 (Phase 17 Task 1.5)
 */
public class LdapConnectionException extends DomainException {

    public static final String ERROR_CODE = "LDAP_CONNECTION_ERROR";

    public LdapConnectionException(String message) {
        super(ERROR_CODE, message);
    }

    public LdapConnectionException(String message, Throwable cause) {
        super(ERROR_CODE, message);
        this.initCause(cause);
    }

    /**
     * LDAP 서버 연결 실패 예외 생성
     *
     * @param server LDAP 서버 주소 (예: "ldap://192.168.100.10:389")
     * @param cause 원인 예외
     * @return LdapConnectionException
     */
    public static LdapConnectionException connectionFailed(String server, Throwable cause) {
        return new LdapConnectionException(
            String.format("Failed to connect to LDAP server: %s", server),
            cause
        );
    }

    /**
     * LDAP 연결 타임아웃 예외 생성
     *
     * @param timeoutMs 타임아웃 시간 (ms)
     * @return LdapConnectionException
     */
    public static LdapConnectionException connectionTimeout(long timeoutMs) {
        return new LdapConnectionException(
            String.format("LDAP connection timeout: %d ms", timeoutMs)
        );
    }

    /**
     * LDAP 인증 실패 예외 생성
     *
     * @param username 사용자명
     * @return LdapConnectionException
     */
    public static LdapConnectionException authenticationFailed(String username) {
        return new LdapConnectionException(
            String.format("LDAP authentication failed for user: %s", username)
        );
    }

    /**
     * LDAP SSL/TLS 실패 예외 생성
     *
     * @param cause 원인 예외
     * @return LdapConnectionException
     */
    public static LdapConnectionException tlsFailure(Throwable cause) {
        return new LdapConnectionException(
            "LDAP SSL/TLS handshake failed",
            cause
        );
    }
}
