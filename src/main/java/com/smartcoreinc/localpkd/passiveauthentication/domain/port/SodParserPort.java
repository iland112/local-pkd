package com.smartcoreinc.localpkd.passiveauthentication.domain.port;

import com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroupHash;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroupNumber;

import java.security.PublicKey;
import java.util.Map;

/**
 * Port for parsing and verifying Security Object Document (SOD) from ePassport.
 * <p>
 * SOD is a PKCS#7 SignedData structure (CMS - Cryptographic Message Syntax) that contains:
 * - Data Group hashes (DG1-DG16)
 * - Digital signature from Document Signer Certificate (DSC)
 * - Hash algorithm identifier
 * - Signature algorithm identifier
 * <p>
 * This port follows Hexagonal Architecture pattern, allowing different implementations
 * (e.g., Bouncy Castle, JMRTD, etc.) without affecting domain logic.
 * <p>
 * Reference: ICAO Doc 9303 Part 11 - Security Mechanisms for MRTDs
 *
 * @see <a href="https://www.icao.int/publications/Documents/9303_p11_cons_en.pdf">ICAO Doc 9303 Part 11</a>
 */
public interface SodParserPort {

    /**
     * Parses SOD and extracts Data Group hashes.
     * <p>
     * SOD contains a LDSSecurityObject which includes hash values for each Data Group.
     * This method extracts those hash values and returns them as a map.
     * <p>
     * Example SOD structure (ASN.1):
     * <pre>
     * LDSSecurityObject ::= SEQUENCE {
     *   version INTEGER,
     *   hashAlgorithm DigestAlgorithmIdentifier,
     *   dataGroupHashValues SEQUENCE SIZE (1..ub-DataGroups) OF DataGroupHash,
     *   ...
     * }
     * </pre>
     *
     * @param sodBytes Binary SOD data (PKCS#7 SignedData)
     * @return Map of Data Group Number to DataGroupHash
     * @throws com.smartcoreinc.localpkd.shared.exception.InfrastructureException if SOD parsing fails
     */
    Map<DataGroupNumber, DataGroupHash> parseDataGroupHashes(byte[] sodBytes);

    /**
     * Verifies SOD signature using DSC public key.
     * <p>
     * The SOD is signed by the Document Signer Certificate (DSC). This method verifies
     * that the signature is valid using the DSC's public key.
     * <p>
     * Verification steps:
     * 1. Extract SignerInfo from CMSSignedData
     * 2. Build SignerInformationVerifier with DSC public key
     * 3. Verify signature
     * <p>
     * Common signature algorithms:
     * - SHA256withRSA
     * - SHA384withRSA
     * - SHA512withRSA
     * - SHA256withECDSA
     *
     * @param sodBytes Binary SOD data (PKCS#7 SignedData)
     * @param dscPublicKey DSC public key for verification
     * @return true if signature is valid, false otherwise
     * @throws com.smartcoreinc.localpkd.shared.exception.InfrastructureException if verification process fails
     */
    boolean verifySignature(byte[] sodBytes, PublicKey dscPublicKey);

    /**
     * Extracts hash algorithm identifier from SOD.
     * <p>
     * SOD contains a DigestAlgorithmIdentifier which specifies the hash algorithm
     * used for Data Group hashing.
     * <p>
     * Common hash algorithms:
     * - SHA-256 (OID: 2.16.840.1.101.3.4.2.1)
     * - SHA-384 (OID: 2.16.840.1.101.3.4.2.2)
     * - SHA-512 (OID: 2.16.840.1.101.3.4.2.3)
     * - SHA-1 (OID: 1.3.14.3.2.26) - deprecated, legacy only
     * <p>
     * This method returns the algorithm name (e.g., "SHA-256"), not the OID.
     *
     * @param sodBytes Binary SOD data (PKCS#7 SignedData)
     * @return Hash algorithm name (e.g., "SHA-256", "SHA-384", "SHA-512")
     * @throws com.smartcoreinc.localpkd.shared.exception.InfrastructureException if algorithm extraction fails
     */
    String extractHashAlgorithm(byte[] sodBytes);

    /**
     * Extracts signature algorithm identifier from SOD.
     * <p>
     * SOD SignerInfo contains a SignatureAlgorithmIdentifier which specifies the
     * signature algorithm used by the DSC.
     * <p>
     * Common signature algorithms:
     * - SHA256withRSA
     * - SHA384withRSA
     * - SHA512withRSA
     * - SHA256withECDSA
     * - SHA384withECDSA
     *
     * @param sodBytes Binary SOD data (PKCS#7 SignedData)
     * @return Signature algorithm name (e.g., "SHA256withRSA")
     * @throws com.smartcoreinc.localpkd.shared.exception.InfrastructureException if algorithm extraction fails
     */
    String extractSignatureAlgorithm(byte[] sodBytes);

    /**
     * Extracts Document Signer Certificate (DSC) information from SOD.
     * <p>
     * SOD (Security Object Document) is a CMS SignedData structure that contains
     * the DSC certificate used to sign the passport data. This method extracts
     * the DSC's Subject DN and Serial Number from the embedded certificate.
     * <p>
     * The extracted information is used for:
     * - LDAP lookup to find and retrieve the full DSC certificate
     * - Trust chain verification (DSC must be signed by CSCA)
     * - Audit logging and traceability
     * <p>
     * CMS SignedData structure:
     * <pre>
     * SignedData ::= SEQUENCE {
     *   version CMSVersion,
     *   digestAlgorithms DigestAlgorithmIdentifiers,
     *   encapContentInfo EncapsulatedContentInfo,
     *   certificates [0] IMPLICIT CertificateSet OPTIONAL,  ← DSC is here
     *   crls [1] IMPLICIT RevocationInfoChoices OPTIONAL,
     *   signerInfos SignerInfos
     * }
     * </pre>
     *
     * @param sodBytes Binary SOD data (PKCS#7 SignedData)
     * @return DscInfo containing Subject DN and Serial Number
     * @throws com.smartcoreinc.localpkd.shared.exception.InfrastructureException if DSC extraction fails or no DSC found
     */
    DscInfo extractDscInfo(byte[] sodBytes);

    /**
     * Extracts full Document Signer Certificate (DSC) from SOD.
     * <p>
     * <b>ICAO 9303 Passive Authentication Standard Approach</b>
     * <p>
     * According to ICAO Doc 9303 Part 11 (Passive Authentication), the SOD contains
     * the complete DSC certificate in its CMS SignedData structure. This approach
     * eliminates the need for LDAP lookup and uses the certificate directly from
     * the passport chip.
     * <p>
     * <b>Benefits of this approach:</b>
     * <ul>
     *   <li>Works even if DSC is not in LDAP (e.g., new/updated certificates)</li>
     *   <li>Uses the actual certificate from the passport chip</li>
     *   <li>Simpler verification flow: SOD DSC → LDAP CSCA → Verify</li>
     *   <li>Complies with ICAO 9303 standard implementation</li>
     * </ul>
     * <p>
     * <b>Verification flow:</b>
     * <pre>
     * 1. Extract DSC from SOD (this method)
     * 2. Extract CSCA DN from DSC issuer field
     * 3. Retrieve CSCA from LDAP
     * 4. Verify DSC signature using CSCA public key
     * 5. Verify SOD signature using DSC public key
     * </pre>
     *
     * @param sodBytes Binary SOD data (PKCS#7 SignedData)
     * @return X509Certificate DSC certificate extracted from SOD
     * @throws com.smartcoreinc.localpkd.shared.exception.InfrastructureException if DSC extraction fails or no DSC found
     */
    java.security.cert.X509Certificate extractDscCertificate(byte[] sodBytes);
}
