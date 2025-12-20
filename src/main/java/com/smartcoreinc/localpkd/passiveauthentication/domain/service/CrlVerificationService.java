package com.smartcoreinc.localpkd.passiveauthentication.domain.service;

import com.smartcoreinc.localpkd.passiveauthentication.domain.model.CrlCheckResult;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * CrlVerificationService - CRL 검증 Domain Service
 *
 * <p>ICAO Doc 9303 Part 12 및 RFC 5280 표준에 따라 CRL 검증을 수행합니다.</p>
 *
 * <h3>CRL 검증 프로세스 (RFC 5280 Section 6.3)</h3>
 * <ol>
 *   <li>CRL 서명 검증 (CSCA 공개키로 검증)</li>
 *   <li>CRL 유효기간 검증 (thisUpdate, nextUpdate)</li>
 *   <li>인증서 시리얼 번호가 폐기 목록에 있는지 확인</li>
 *   <li>폐기 사유 코드 추출 (CRL Entry Extensions)</li>
 * </ol>
 *
 * <h3>CRL Freshness Check</h3>
 * <pre>
 * Valid Period: thisUpdate <= NOW < nextUpdate
 *
 * thisUpdate: CRL 발행 시각
 * nextUpdate: 다음 CRL 발행 예정 시각 (이 시각 이전에 새 CRL 조회 필요)
 * </pre>
 *
 * <h3>RFC 5280 CRL Reason Codes</h3>
 * <ul>
 *   <li>0: unspecified</li>
 *   <li>1: keyCompromise</li>
 *   <li>2: cACompromise</li>
 *   <li>3: affiliationChanged</li>
 *   <li>4: superseded</li>
 *   <li>5: cessationOfOperation</li>
 *   <li>6: certificateHold</li>
 *   <li>8: removeFromCRL</li>
 *   <li>9: privilegeWithdrawn</li>
 *   <li>10: aACompromise</li>
 * </ul>
 *
 * @see com.smartcoreinc.localpkd.passiveauthentication.domain.model.CrlCheckResult
 * @see com.smartcoreinc.localpkd.passiveauthentication.domain.port.CrlLdapPort
 * @since Phase 4.12
 */
@Slf4j
@Service
public class CrlVerificationService {

    /**
     * RFC 5280 CRL Reason Code Extension OID
     */
    private static final String CRL_REASON_OID = "2.5.29.21";

    /**
     * 인증서에 대한 CRL 검증을 수행합니다.
     *
     * <p>검증 프로세스:</p>
     * <ol>
     *   <li>CRL 서명 검증 (CSCA 공개키)</li>
     *   <li>CRL 유효기간 검증 (thisUpdate, nextUpdate)</li>
     *   <li>인증서 시리얼 번호 폐기 여부 확인</li>
     *   <li>폐기된 경우 revocationDate 및 reason 추출</li>
     * </ol>
     *
     * @param certificate 검증할 인증서 (DSC)
     * @param crl CRL (CSCA가 서명)
     * @param issuerCert 발행자 인증서 (CSCA)
     * @return CRL 검증 결과
     * @throws DomainException if verification process fails unexpectedly
     */
    public CrlCheckResult verifyCertificate(X509Certificate certificate,
                                            X509CRL crl,
                                            X509Certificate issuerCert) {
        log.debug("Starting CRL verification for certificate serial: {}", certificate.getSerialNumber());

        try {
            // Step 1: CRL 서명 검증
            if (!verifyCrlSignature(crl, issuerCert)) {
                log.warn("CRL signature verification failed. Issuer: {}", crl.getIssuerX500Principal());
                return CrlCheckResult.invalid(
                    "CRL signature verification failed using CSCA public key"
                );
            }

            // Step 2: CRL 유효기간 검증 (Freshness Check)
            CrlCheckResult freshnessResult = checkCrlFreshness(crl);
            if (freshnessResult.getStatus() != CrlCheckResult.CrlStatus.VALID) {
                return freshnessResult;  // CRL_EXPIRED
            }

            // Step 3: 인증서 폐기 여부 확인
            return checkRevocationStatus(certificate, crl);

        } catch (Exception e) {
            log.error("Unexpected error during CRL verification for serial: {}",
                certificate.getSerialNumber(), e);
            throw new DomainException("CRL_VERIFICATION_ERROR",
                "Failed to verify certificate against CRL: " + e.getMessage(), e);
        }
    }

    /**
     * CRL 서명을 CSCA 공개키로 검증합니다.
     *
     * <p>RFC 5280 Section 5.1.2.2 - CRL Signature Verification</p>
     *
     * @param crl 검증할 CRL
     * @param issuerCert CSCA 인증서 (CRL 발행자)
     * @return true if signature is valid
     */
    private boolean verifyCrlSignature(X509CRL crl, X509Certificate issuerCert) {
        try {
            crl.verify(issuerCert.getPublicKey());
            log.debug("CRL signature verified successfully. Issuer: {}", crl.getIssuerX500Principal());
            return true;
        } catch (Exception e) {
            log.error("CRL signature verification failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * CRL 유효기간을 검증합니다 (Freshness Check).
     *
     * <p>RFC 5280 Section 5.1.2.5 - thisUpdate, nextUpdate</p>
     * <pre>
     * Valid if: thisUpdate <= NOW < nextUpdate
     * </pre>
     *
     * @param crl 검증할 CRL
     * @return VALID if CRL is fresh, CRL_EXPIRED otherwise
     */
    private CrlCheckResult checkCrlFreshness(X509CRL crl) {
        Date thisUpdate = crl.getThisUpdate();
        Date nextUpdate = crl.getNextUpdate();
        Date now = new Date();

        log.debug("CRL thisUpdate: {}, nextUpdate: {}, current time: {}",
            thisUpdate, nextUpdate, now);

        // thisUpdate 체크
        if (now.before(thisUpdate)) {
            log.warn("CRL is not yet valid. thisUpdate: {}, current: {}", thisUpdate, now);
            return CrlCheckResult.expired(
                String.format("CRL is not yet valid (thisUpdate: %s)", thisUpdate)
            );
        }

        // nextUpdate 체크
        if (nextUpdate != null && now.after(nextUpdate)) {
            log.warn("CRL has expired. nextUpdate: {}, current: {}", nextUpdate, now);
            return CrlCheckResult.expired(
                String.format("CRL has expired (nextUpdate: %s)", nextUpdate)
            );
        }

        log.debug("CRL freshness check passed");
        return CrlCheckResult.valid();
    }

    /**
     * 인증서가 CRL에 폐기된 것으로 등록되어 있는지 확인합니다.
     *
     * <p>RFC 5280 Section 5.3 - Revoked Certificates</p>
     *
     * @param certificate 검증할 인증서
     * @param crl CRL
     * @return VALID if not revoked, REVOKED if revoked
     */
    private CrlCheckResult checkRevocationStatus(X509Certificate certificate, X509CRL crl) {
        BigInteger serialNumber = certificate.getSerialNumber();

        // CRL에서 인증서 시리얼 번호 검색
        X509CRLEntry revokedEntry = crl.getRevokedCertificate(serialNumber);

        if (revokedEntry == null) {
            log.debug("Certificate serial {} is not revoked", serialNumber);
            return CrlCheckResult.valid();
        }

        // 폐기된 경우 - revocationDate 및 reason 추출
        Date revocationDate = revokedEntry.getRevocationDate();
        LocalDateTime revocationDateTime = convertToLocalDateTime(revocationDate);

        Integer reasonCode = extractReasonCode(revokedEntry);

        log.warn("Certificate serial {} is REVOKED. Date: {}, Reason code: {}",
            serialNumber, revocationDate, reasonCode);

        return CrlCheckResult.revoked(revocationDateTime, reasonCode);
    }

    /**
     * CRL Entry에서 폐기 사유 코드를 추출합니다.
     *
     * <p>RFC 5280 Section 5.3.1 - CRL Entry Extensions - Reason Code</p>
     *
     * @param revokedEntry CRL Entry
     * @return Reason code (0-10), null if not present
     */
    private Integer extractReasonCode(X509CRLEntry revokedEntry) {
        try {
            // CRL Entry Extensions에서 Reason Code 추출
            byte[] extensionValue = revokedEntry.getExtensionValue(CRL_REASON_OID);

            if (extensionValue == null) {
                log.debug("CRL Entry does not have a Reason Code extension");
                return null;  // Reason code not present
            }

            // ASN.1 DER encoding: OCTET STRING wrapper + ENUMERATED value
            // Skip OCTET STRING tag and length (2 bytes), then ENUMERATED tag and length (2 bytes)
            if (extensionValue.length >= 5) {
                // The actual reason code is at index 4 (after wrapper bytes)
                int reasonCode = extensionValue[4] & 0xFF;
                log.debug("Extracted CRL reason code: {}", reasonCode);
                return reasonCode;
            }

            log.warn("Invalid CRL Reason Code extension format");
            return null;

        } catch (Exception e) {
            log.error("Failed to extract CRL reason code: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Date를 LocalDateTime으로 변환
     */
    private LocalDateTime convertToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return Instant.ofEpochMilli(date.getTime())
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
    }
}
