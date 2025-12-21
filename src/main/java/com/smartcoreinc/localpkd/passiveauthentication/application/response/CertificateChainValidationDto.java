package com.smartcoreinc.localpkd.passiveauthentication.application.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * DTO representing Certificate Chain Validation result.
 * <p>
 * This DTO encapsulates the result of validating the trust chain between
 * Document Signer Certificate (DSC) and Country Signing CA (CSCA).
 * </p>
 *
 * <h3>Validation Steps:</h3>
 * <ol>
 *   <li>Verify DSC is signed by CSCA</li>
 *   <li>Check DSC validity period (notBefore, notAfter)</li>
 *   <li>Check CRL for revocation status</li>
 *   <li>Verify CSCA is self-signed (if root CA)</li>
 * </ol>
 *
 * <h3>CRL Status Values:</h3>
 * <ul>
 *   <li>VALID: CRL 확인 완료, 인증서 폐기되지 않음</li>
 *   <li>REVOKED: 인증서가 폐기됨</li>
 *   <li>CRL_UNAVAILABLE: LDAP에서 CRL을 찾을 수 없음</li>
 *   <li>CRL_EXPIRED: CRL이 만료됨</li>
 *   <li>CRL_INVALID: CRL 서명 검증 실패</li>
 * </ul>
 *
 * @param valid True if certificate chain is valid
 * @param dscSubject DSC Subject DN
 * @param dscSerialNumber DSC serial number (hex string)
 * @param cscaSubject CSCA Subject DN
 * @param cscaSerialNumber CSCA serial number (hex string)
 * @param notBefore DSC validity start date
 * @param notAfter DSC validity end date
 * @param crlChecked True if CRL was checked successfully
 * @param revoked True if DSC is revoked
 * @param crlStatus CRL check status (VALID, REVOKED, CRL_UNAVAILABLE, CRL_EXPIRED, CRL_INVALID)
 * @param crlMessage Detailed CRL check message (e.g., reason for unavailability)
 * @param validationErrors Validation error messages (if any)
 */
public record CertificateChainValidationDto(
    boolean valid,
    String dscSubject,
    String dscSerialNumber,
    String cscaSubject,
    String cscaSerialNumber,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    LocalDateTime notBefore,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    LocalDateTime notAfter,
    boolean crlChecked,
    boolean revoked,
    String crlStatus,
    String crlMessage,
    String validationErrors
) {
}
