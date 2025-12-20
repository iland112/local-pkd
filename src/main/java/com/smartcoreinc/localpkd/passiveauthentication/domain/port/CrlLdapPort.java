package com.smartcoreinc.localpkd.passiveauthentication.domain.port;

import java.security.cert.X509CRL;
import java.util.Optional;

/**
 * Port for retrieving Certificate Revocation Lists (CRLs) from LDAP directory.
 *
 * <p>ICAO Doc 9303 Part 12 - Public Key Infrastructure for MRTDs</p>
 * <p>RFC 5280 - X.509 Certificate and CRL Profile</p>
 *
 * <h3>LDAP DIT Structure (ICAO PKD)</h3>
 * <pre>
 * DN: cn={CSCA-DN},o=crl,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}
 *
 * Example:
 * cn=CN\=CSCA-KOREA\,O\=Government\,C\=KR,o=crl,c=KR,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
 *
 * ObjectClasses:
 * - top
 * - cRLDistributionPoint
 *
 * Attributes:
 * - cn: {CSCA-SUBJECT-DN}
 * - certificateRevocationList;binary: {CRL-DER-BYTES}
 * </pre>
 *
 * <h3>Usage in Passive Authentication</h3>
 * <p>CRL checking is performed after Trust Chain verification (Step 7):</p>
 * <ol>
 *   <li>Extract CSCA Subject DN from DSC Issuer field</li>
 *   <li>Retrieve CRL using CSCA DN and country code</li>
 *   <li>Verify CRL signature using CSCA public key</li>
 *   <li>Check if DSC serial number is in revoked list</li>
 * </ol>
 *
 * @see com.smartcoreinc.localpkd.passiveauthentication.domain.service.CrlVerificationService
 * @since Phase 4.12
 */
public interface CrlLdapPort {

    /**
     * Retrieves Certificate Revocation List (CRL) for the given CSCA from LDAP directory.
     *
     * <p>Search Process:</p>
     * <ol>
     *   <li>Build LDAP search filter: (&(objectClass=cRLDistributionPoint)(cn={escaped-dn}))</li>
     *   <li>Search in base DN: o=crl,c={country},dc=data,dc=download,dc=pkd,{baseDN}</li>
     *   <li>Extract certificateRevocationList;binary attribute</li>
     *   <li>Parse X.509 CRL</li>
     * </ol>
     *
     * <p><b>Important:</b> CSCA Subject DN must be escaped according to RFC 4515.</p>
     *
     * @param cscaSubjectDn CSCA Subject DN (RFC 4514 format, e.g., "CN=CSCA-KOREA,O=Government,C=KR")
     * @param countryCode ISO 3166-1 alpha-2 country code (e.g., "KR")
     * @return X509CRL if found in LDAP, empty otherwise
     * @throws com.smartcoreinc.localpkd.shared.exception.InfrastructureException if LDAP connection fails or CRL parsing fails
     */
    Optional<X509CRL> findCrlByCsca(String cscaSubjectDn, String countryCode);
}
