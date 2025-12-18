package com.smartcoreinc.localpkd.passiveauthentication.infrastructure.adapter;

import com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroupNumber;
import com.smartcoreinc.localpkd.passiveauthentication.domain.port.SodParserPort;
import com.smartcoreinc.localpkd.shared.exception.InfrastructureException;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.icao.DataGroupHash;
import org.bouncycastle.asn1.icao.LDSSecurityObject;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.security.PublicKey;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;

/**
 * Bouncy Castle implementation of SodParserPort.
 *
 * <p>Provides SOD (Security Object Document) parsing and verification
 * using Bouncy Castle cryptographic library.
 *
 * <p>SOD is a PKCS#7 SignedData structure containing:
 * <ul>
 *   <li>LDSSecurityObject with Data Group hashes</li>
 *   <li>Digital signature from DSC</li>
 *   <li>Hash and signature algorithm identifiers</li>
 * </ul>
 *
 * <p>Reference: ICAO Doc 9303 Part 11 - Security Mechanisms for MRTDs
 *
 * @see <a href="https://www.icao.int/publications/Documents/9303_p11_cons_en.pdf">ICAO Doc 9303 Part 11</a>
 */
@Slf4j
@Component
public class BouncyCastleSodParserAdapter implements SodParserPort {

    // OID mappings for hash algorithms
    private static final Map<String, String> HASH_ALGORITHM_NAMES = Map.of(
        "1.3.14.3.2.26", "SHA-1",       // Deprecated, legacy only
        "2.16.840.1.101.3.4.2.1", "SHA-256",
        "2.16.840.1.101.3.4.2.2", "SHA-384",
        "2.16.840.1.101.3.4.2.3", "SHA-512"
    );

    // OID mappings for signature algorithms
    private static final Map<String, String> SIGNATURE_ALGORITHM_NAMES = Map.of(
        "1.2.840.113549.1.1.11", "SHA256withRSA",
        "1.2.840.113549.1.1.12", "SHA384withRSA",
        "1.2.840.113549.1.1.13", "SHA512withRSA",
        "1.2.840.10045.4.3.2", "SHA256withECDSA",
        "1.2.840.10045.4.3.3", "SHA384withECDSA",
        "1.2.840.10045.4.3.4", "SHA512withECDSA"
    );

    static {
        // Register Bouncy Castle security provider
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            log.info("Bouncy Castle security provider registered");
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>Parses PKCS#7 SignedData using Bouncy Castle CMSSignedData</li>
     *   <li>Extracts LDSSecurityObject from SignedData content</li>
     *   <li>Iterates through DataGroupHash entries</li>
     *   <li>Maps Data Group numbers (1-16) to hash values</li>
     * </ul>
     */
    @Override
    public Map<DataGroupNumber, com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroupHash> parseDataGroupHashes(
        byte[] sodBytes
    ) {
        try {
            log.debug("Parsing SOD to extract Data Group hashes (SOD size: {} bytes)", sodBytes.length);

            // Remove ICAO 9303 Tag 0x77 wrapper if present
            byte[] cmsBytes = unwrapIcaoSod(sodBytes);

            // Parse CMS SignedData
            CMSSignedData cmsSignedData = new CMSSignedData(cmsBytes);

            // Extract LDSSecurityObject from content
            LDSSecurityObject ldsSecurityObject = extractLdsSecurityObject(cmsSignedData);

            // Extract Data Group hashes
            Map<DataGroupNumber, com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroupHash> hashMap = new HashMap<>();

            DataGroupHash[] dataGroupHashes = ldsSecurityObject.getDatagroupHash();
            if (dataGroupHashes == null || dataGroupHashes.length == 0) {
                log.warn("No Data Group hashes found in SOD");
                return hashMap;
            }

            for (DataGroupHash dgHash : dataGroupHashes) {
                int dgNumber = dgHash.getDataGroupNumber();
                byte[] hashValue = dgHash.getDataGroupHashValue().getOctets();

                DataGroupNumber dataGroupNumber = DataGroupNumber.fromInt(dgNumber);
                com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroupHash domainHash =
                    com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroupHash.of(hashValue);

                hashMap.put(dataGroupNumber, domainHash);

                log.debug("Extracted hash for {}: {} bytes", dataGroupNumber, hashValue.length);
            }

            log.info("Successfully parsed {} Data Group hashes from SOD", hashMap.size());
            return hashMap;

        } catch (Exception e) {
            throw new InfrastructureException(
                "SOD_PARSE_ERROR",
                "Failed to parse Data Group hashes from SOD: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>Parses PKCS#7 SignedData</li>
     *   <li>Extracts SignerInfo from SignedData</li>
     *   <li>Builds SignerInformationVerifier with DSC public key</li>
     *   <li>Verifies signature using Bouncy Castle</li>
     * </ul>
     */
    @Override
    public boolean verifySignature(byte[] sodBytes, PublicKey dscPublicKey) {
        try {
            log.debug("Verifying SOD signature with DSC public key");

            // Remove ICAO 9303 Tag 0x77 wrapper if present
            byte[] cmsBytes = unwrapIcaoSod(sodBytes);

            // Parse CMS SignedData
            CMSSignedData cmsSignedData = new CMSSignedData(cmsBytes);

            // Extract SignerInfo
            SignerInformationStore signerInfos = cmsSignedData.getSignerInfos();
            if (signerInfos.size() == 0) {
                log.error("No SignerInfo found in SOD");
                return false;
            }

            SignerInformation signerInfo = signerInfos.getSigners().iterator().next();

            // Build verifier with DSC public key
            var verifierBuilder = new JcaSimpleSignerInfoVerifierBuilder()
                .setProvider("BC")
                .build(dscPublicKey);

            // Verify signature
            boolean valid = signerInfo.verify(verifierBuilder);

            if (valid) {
                log.info("SOD signature verification succeeded");
            } else {
                log.error("SOD signature verification failed");
            }

            return valid;

        } catch (Exception e) {
            throw new InfrastructureException(
                "SOD_SIGNATURE_VERIFY_ERROR",
                "Failed to verify SOD signature: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>Extracts LDSSecurityObject from SOD</li>
     *   <li>Gets DigestAlgorithmIdentifier</li>
     *   <li>Maps OID to algorithm name (SHA-256, SHA-384, SHA-512)</li>
     * </ul>
     */
    @Override
    public String extractHashAlgorithm(byte[] sodBytes) {
        try {
            log.debug("Extracting hash algorithm from SOD");

            // Remove ICAO 9303 Tag 0x77 wrapper if present
            byte[] cmsBytes = unwrapIcaoSod(sodBytes);

            // Parse CMS SignedData
            CMSSignedData cmsSignedData = new CMSSignedData(cmsBytes);

            // Extract LDSSecurityObject
            LDSSecurityObject ldsSecurityObject = extractLdsSecurityObject(cmsSignedData);

            // Get hash algorithm identifier
            AlgorithmIdentifier hashAlgorithm = ldsSecurityObject.getDigestAlgorithmIdentifier();
            String oid = hashAlgorithm.getAlgorithm().getId();

            // Map OID to algorithm name
            String algorithmName = HASH_ALGORITHM_NAMES.get(oid);
            if (algorithmName == null) {
                log.warn("Unknown hash algorithm OID: {}", oid);
                algorithmName = "UNKNOWN(" + oid + ")";
            }

            log.info("Extracted hash algorithm: {} (OID: {})", algorithmName, oid);
            return algorithmName;

        } catch (Exception e) {
            throw new InfrastructureException(
                "HASH_ALGORITHM_EXTRACT_ERROR",
                "Failed to extract hash algorithm from SOD: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>Extracts SignerInfo from SOD</li>
     *   <li>Gets SignatureAlgorithmIdentifier</li>
     *   <li>Maps OID to algorithm name (SHA256withRSA, etc.)</li>
     * </ul>
     */
    @Override
    public String extractSignatureAlgorithm(byte[] sodBytes) {
        try {
            log.debug("Extracting signature algorithm from SOD");

            // Remove ICAO 9303 Tag 0x77 wrapper if present
            byte[] cmsBytes = unwrapIcaoSod(sodBytes);

            // Parse CMS SignedData
            CMSSignedData cmsSignedData = new CMSSignedData(cmsBytes);

            // Extract SignerInfo
            SignerInformationStore signerInfos = cmsSignedData.getSignerInfos();
            if (signerInfos.size() == 0) {
                throw new InfrastructureException(
                    "NO_SIGNER_INFO",
                    "No SignerInfo found in SOD"
                );
            }

            SignerInformation signerInfo = signerInfos.getSigners().iterator().next();

            // Get signature algorithm identifier
            AlgorithmIdentifier signatureAlgorithm = signerInfo.getDigestAlgorithmID();
            String oid = signatureAlgorithm.getAlgorithm().getId();

            // Map OID to algorithm name
            String algorithmName = SIGNATURE_ALGORITHM_NAMES.get(oid);
            if (algorithmName == null) {
                log.warn("Unknown signature algorithm OID: {}", oid);
                algorithmName = "UNKNOWN(" + oid + ")";
            }

            log.info("Extracted signature algorithm: {} (OID: {})", algorithmName, oid);
            return algorithmName;

        } catch (Exception e) {
            throw new InfrastructureException(
                "SIGNATURE_ALGORITHM_EXTRACT_ERROR",
                "Failed to extract signature algorithm from SOD: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Unwraps ICAO 9303 Tag 0x77 wrapper from SOD if present.
     * <p>
     * ICAO Doc 9303 Part 10 specifies that EF.SOD file is wrapped with Tag 0x77 (Application 23).
     * The structure is:
     * <pre>
     * Tag 0x77 (Application 23) - EF.SOD wrapper
     *   ├─ Length (TLV format)
     *   └─ Value: CMS SignedData (Tag 0x30 SEQUENCE)
     * </pre>
     * <p>
     * This method removes the 0x77 wrapper to extract the pure CMS SignedData bytes.
     *
     * @param sodBytes SOD bytes potentially wrapped with Tag 0x77
     * @return Pure CMS SignedData bytes (starts with Tag 0x30)
     */
    private byte[] unwrapIcaoSod(byte[] sodBytes) {
        if (sodBytes == null || sodBytes.length < 4) {
            return sodBytes;
        }

        // Check if first byte is Tag 0x77 (ICAO EF.SOD wrapper)
        if ((sodBytes[0] & 0xFF) != 0x77) {
            // No wrapper, return as-is
            log.debug("SOD does not have Tag 0x77 wrapper, using raw bytes");
            return sodBytes;
        }

        log.debug("SOD has Tag 0x77 wrapper, unwrapping...");

        // Parse TLV structure to skip wrapper
        int offset = 1; // Skip tag byte

        // Parse length byte(s)
        int lengthByte = sodBytes[offset++] & 0xFF;

        if ((lengthByte & 0x80) == 0) {
            // Short form: length is in the lower 7 bits
            // Content starts immediately after length byte
        } else {
            // Long form: number of subsequent octets is in lower 7 bits
            int numOctets = lengthByte & 0x7F;
            offset += numOctets; // Skip length octets
        }

        // Extract CMS SignedData (everything after TLV header)
        byte[] cmsBytes = new byte[sodBytes.length - offset];
        System.arraycopy(sodBytes, offset, cmsBytes, 0, cmsBytes.length);

        log.debug("Unwrapped SOD: {} bytes (was {} bytes with wrapper)", cmsBytes.length, sodBytes.length);

        return cmsBytes;
    }

    /**
     * Extract LDSSecurityObject from CMSSignedData.
     *
     * <p>LDSSecurityObject is embedded in the SignedData content (eContent).
     *
     * @param cmsSignedData CMS SignedData
     * @return LDSSecurityObject instance
     * @throws Exception if extraction fails
     */
    private LDSSecurityObject extractLdsSecurityObject(CMSSignedData cmsSignedData) throws Exception {
        // Get SignedData content (eContent) bytes
        byte[] contentBytes = (byte[]) cmsSignedData.getSignedContent().getContent();

        if (contentBytes == null || contentBytes.length == 0) {
            throw new InfrastructureException(
                "EMPTY_SOD_CONTENT",
                "SOD content (eContent) is empty"
            );
        }

        // Parse LDSSecurityObject from bytes
        try (ASN1InputStream asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(contentBytes))) {
            ASN1Sequence sequence = (ASN1Sequence) asn1InputStream.readObject();
            return LDSSecurityObject.getInstance(sequence);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>Parses PKCS#7 SignedData to extract certificates</li>
     *   <li>Assumes first certificate in certificates field is the DSC</li>
     *   <li>Extracts Subject DN and Serial Number from X.509 certificate</li>
     *   <li>Serial Number is converted to hexadecimal uppercase format</li>
     * </ul>
     */
    @Override
    public com.smartcoreinc.localpkd.passiveauthentication.domain.port.DscInfo extractDscInfo(byte[] sodBytes) {
        try {
            log.debug("Extracting DSC information from SOD");

            // Remove ICAO 9303 Tag 0x77 wrapper if present
            byte[] cmsBytes = unwrapIcaoSod(sodBytes);

            // Parse CMS SignedData
            CMSSignedData cmsSignedData = new CMSSignedData(cmsBytes);

            // Extract certificates from SignedData
            var certificates = cmsSignedData.getCertificates();
            if (certificates == null || certificates.getMatches(null).isEmpty()) {
                throw new InfrastructureException(
                    "NO_DSC_IN_SOD",
                    "No certificates found in SOD"
                );
            }

            // Get first certificate (should be DSC)
            var certIterator = certificates.getMatches(null).iterator();
            if (!certIterator.hasNext()) {
                throw new InfrastructureException(
                    "NO_DSC_IN_SOD",
                    "Certificate iterator is empty"
                );
            }

            var certHolder = (org.bouncycastle.cert.X509CertificateHolder) certIterator.next();

            // Extract Subject DN
            String subjectDn = certHolder.getSubject().toString();

            // Extract Serial Number (convert to hex uppercase)
            String serialNumber = certHolder.getSerialNumber().toString(16).toUpperCase();

            log.info("Extracted DSC info - Subject: {}, Serial: {}", subjectDn, serialNumber);

            return new com.smartcoreinc.localpkd.passiveauthentication.domain.port.DscInfo(
                subjectDn,
                serialNumber
            );

        } catch (Exception e) {
            throw new InfrastructureException(
                "DSC_EXTRACT_ERROR",
                "Failed to extract DSC information from SOD: " + e.getMessage(),
                e
            );
        }
    }
}
