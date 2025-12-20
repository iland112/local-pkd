package com.smartcoreinc.localpkd.passiveauthentication.domain.service;

import com.smartcoreinc.localpkd.passiveauthentication.domain.model.*;

import java.util.List;

/**
 * Domain Service for Passive Authentication verification.
 *
 * <p>Orchestrates the PA verification process according to ICAO 9303 standards:
 * <ol>
 *   <li>Certificate Chain Validation: Verify trust chain (CSCA → DSC)</li>
 *   <li>SOD Signature Verification: Verify SOD signature using DSC public key</li>
 *   <li>Data Group Hash Verification: Verify each data group hash against SOD</li>
 * </ol>
 *
 * <p>This is a domain service that encapsulates complex verification logic
 * that doesn't naturally fit within any single aggregate.
 */
public interface PassiveAuthenticationService {

    /**
     * Perform complete Passive Authentication verification.
     *
     * <p>This method orchestrates all three PA verification steps:
     * <ol>
     *   <li>Validates certificate chain from CSCA to DSC</li>
     *   <li>Verifies SOD signature using DSC public key</li>
     *   <li>Verifies data group hashes against SOD</li>
     * </ol>
     *
     * @param passportData PassportData containing SOD and data groups
     * @return PassiveAuthenticationResult with verification outcome
     */
    PassiveAuthenticationResult verify(PassportData passportData);

    /**
     * Validate certificate chain (CSCA → DSC).
     *
     * <p>Verifies the trust chain by:
     * <ul>
     *   <li>Retrieving CSCA certificate from LDAP based on issuer DN</li>
     *   <li>Verifying CSCA self-signature</li>
     *   <li>Verifying DSC signature using CSCA public key</li>
     *   <li>Checking certificate validity periods</li>
     *   <li>Verifying against CRL if available</li>
     * </ul>
     *
     * @param sod Security Object Document containing signer certificate reference
     * @return true if certificate chain is valid
     */
    boolean validateCertificateChain(SecurityObjectDocument sod);

    /**
     * Verify SOD signature using DSC public key.
     *
     * <p>Extracts the signature from SOD and verifies it using the
     * Document Signer Certificate's public key retrieved from LDAP.
     *
     * @param sod Security Object Document
     * @return true if SOD signature is valid
     */
    boolean verifySODSignature(SecurityObjectDocument sod);

    /**
     * Verify data group hashes against SOD.
     *
     * <p>For each data group:
     * <ul>
     *   <li>Calculates actual hash of data group content</li>
     *   <li>Compares with expected hash from SOD</li>
     *   <li>Updates data group validation status</li>
     * </ul>
     *
     * @param dataGroups list of data groups to verify
     * @param sod Security Object Document containing expected hashes
     * @return list of data groups with updated validation status
     */
    List<DataGroup> verifyDataGroupHashes(List<DataGroup> dataGroups, SecurityObjectDocument sod);

    /**
     * Extract hash algorithm from SOD.
     *
     * <p>Determines which hash algorithm was used (SHA-256, SHA-384, SHA-512)
     * by parsing the SOD structure.
     *
     * @param sod Security Object Document
     * @return hash algorithm identifier (e.g., "SHA-256")
     */
    String extractHashAlgorithm(SecurityObjectDocument sod);

    /**
     * Extract signature algorithm from SOD.
     *
     * <p>Determines which signature algorithm was used (e.g., SHA256withRSA)
     * by parsing the SOD structure.
     *
     * @param sod Security Object Document
     * @return signature algorithm identifier (e.g., "SHA256withRSA")
     */
    String extractSignatureAlgorithm(SecurityObjectDocument sod);

    /**
     * Retrieve DSC certificate from LDAP.
     *
     * <p>Searches LDAP for the Document Signer Certificate using
     * the issuer DN and serial number from SOD.
     *
     * @param issuerDN issuer distinguished name
     * @param serialNumber certificate serial number
     * @return DSC certificate bytes or null if not found
     */
    byte[] retrieveDSCFromLDAP(String issuerDN, String serialNumber);

    /**
     * Retrieve CSCA certificate from LDAP.
     *
     * <p>Searches LDAP for the Country Signing CA certificate using
     * the subject DN.
     *
     * @param subjectDN subject distinguished name
     * @return CSCA certificate bytes or null if not found
     */
    byte[] retrieveCSCAFromLDAP(String subjectDN);

    /**
     * Check certificate revocation status.
     *
     * <p>Queries CRL from LDAP to check if the certificate
     * has been revoked.
     *
     * @param issuerDN CRL issuer DN
     * @param serialNumber certificate serial number to check
     * @return true if certificate is revoked
     */
    boolean checkCertificateRevocation(String issuerDN, String serialNumber);
}
