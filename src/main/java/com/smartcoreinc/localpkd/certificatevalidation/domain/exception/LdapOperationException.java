package com.smartcoreinc.localpkd.certificatevalidation.domain.exception;

import com.smartcoreinc.localpkd.shared.exception.DomainException;

/**
 * LdapOperationException - LDAP 작업 예외
 *
 * <p><b>목적</b>: LDAP 디렉토리 작업 실패를 나타냅니다.</p>
 *
 * <p><b>발생 시나리오</b>:
 * <ul>
 *   <li>인증서/CRL 업로드 실패</li>
 *   <li>LDAP 검색 실패</li>
 *   <li>디렉토리 엔트리 삭제 실패</li>
 *   <li>권한 부족 (Access denied)</li>
 *   <li>중복 엔트리 (Already exists)</li>
 *   <li>Schema violation</li>
 * </ul>
 * </p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * try {
 *     String dn = ldapConnectionPort.uploadCertificate(cert, cn, baseDn);
 * } catch (LdapOperationException e) {
 *     log.error("Certificate upload failed: {}", e.getMessage());
 *     // Handle error: Retry, notify user, etc.
 * }
 * }</pre>
 *
 * @see DomainException
 * @see LdapConnectionException
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24 (Phase 17 Task 1.5)
 */
public class LdapOperationException extends DomainException {

    public static final String ERROR_CODE = "LDAP_OPERATION_ERROR";

    private final String operation;    // 작업명 (uploadCertificate, searchCrl, etc.)
    private final String dn;           // Distinguished Name (있는 경우)
    private final int ldapErrorCode;   // LDAP error code (있는 경우)

    public LdapOperationException(String message) {
        super(ERROR_CODE, message);
        this.operation = null;
        this.dn = null;
        this.ldapErrorCode = -1;
    }

    public LdapOperationException(String message, String operation) {
        super(ERROR_CODE, message);
        this.operation = operation;
        this.dn = null;
        this.ldapErrorCode = -1;
    }

    public LdapOperationException(String message, String operation, String dn) {
        super(ERROR_CODE, message);
        this.operation = operation;
        this.dn = dn;
        this.ldapErrorCode = -1;
    }

    public LdapOperationException(String message, String operation, int ldapErrorCode) {
        super(ERROR_CODE, message);
        this.operation = operation;
        this.dn = null;
        this.ldapErrorCode = ldapErrorCode;
    }

    public LdapOperationException(String message, Throwable cause) {
        super(ERROR_CODE, message);
        this.initCause(cause);
        this.operation = null;
        this.dn = null;
        this.ldapErrorCode = -1;
    }

    public String getOperation() { return operation; }
    public String getDn() { return dn; }
    public int getLdapErrorCode() { return ldapErrorCode; }

    /**
     * 인증서 업로드 실패 예외 생성
     *
     * @param subjectCn 인증서 Subject CN
     * @param cause 원인 예외
     * @return LdapOperationException
     */
    public static LdapOperationException uploadCertificateFailed(String subjectCn, Throwable cause) {
        return new LdapOperationException(
            String.format("Failed to upload certificate: %s", subjectCn),
            "uploadCertificate"
        );
    }

    /**
     * CRL 업로드 실패 예외 생성
     *
     * @param issuerName CRL 발급자 이름
     * @param cause 원인 예외
     * @return LdapOperationException
     */
    public static LdapOperationException uploadCrlFailed(String issuerName, Throwable cause) {
        return new LdapOperationException(
            String.format("Failed to upload CRL: %s", issuerName),
            "uploadCrl"
        );
    }

    /**
     * 인증서 검색 실패 예외 생성
     *
     * @param subjectCn 검색 대상 Subject CN
     * @return LdapOperationException
     */
    public static LdapOperationException searchCertificateFailed(String subjectCn) {
        return new LdapOperationException(
            String.format("Failed to search certificate: %s", subjectCn),
            "searchCertificate"
        );
    }

    /**
     * CRL 검색 실패 예외 생성
     *
     * @param issuerName 검색 대상 발급자 이름
     * @return LdapOperationException
     */
    public static LdapOperationException searchCrlFailed(String issuerName) {
        return new LdapOperationException(
            String.format("Failed to search CRL: %s", issuerName),
            "searchCrl"
        );
    }

    /**
     * 엔트리 삭제 실패 예외 생성
     *
     * @param dn 삭제 대상 DN
     * @param cause 원인 예외
     * @return LdapOperationException
     */
    public static LdapOperationException deleteEntryFailed(String dn, Throwable cause) {
        return new LdapOperationException(
            String.format("Failed to delete LDAP entry: %s", dn),
            "deleteEntry",
            dn
        );
    }

    /**
     * 권한 부족 예외 생성
     *
     * @param operation 시도한 작업
     * @param dn 대상 DN
     * @return LdapOperationException
     */
    public static LdapOperationException accessDenied(String operation, String dn) {
        return new LdapOperationException(
            String.format("Access denied for operation '%s' on DN: %s", operation, dn),
            operation,
            dn
        );
    }

    /**
     * 중복 엔트리 예외 생성
     *
     * @param dn 중복된 DN
     * @return LdapOperationException
     */
    public static LdapOperationException entryAlreadyExists(String dn) {
        return new LdapOperationException(
            String.format("LDAP entry already exists: %s", dn),
            "create",
            dn
        );
    }

    /**
     * Schema violation 예외 생성
     *
     * @param message 위반 내용
     * @return LdapOperationException
     */
    public static LdapOperationException schemaViolation(String message) {
        return new LdapOperationException(
            String.format("LDAP schema violation: %s", message),
            "schemaViolation"
        );
    }
}
