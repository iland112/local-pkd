package com.smartcoreinc.localpkd.passiveauthentication.integration;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.BERTags;
import org.bouncycastle.asn1.icao.LDSSecurityObject;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.Security;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Korean Passport Chip Data Analysis Test
 *
 * Based on ICAO Doc 9303 Part 10 - Logical Data Structure (LDS) for Storage of Biometrics
 *
 * This test analyzes actual ePassport chip data from a Korean passport:
 * - EF.SOD (Security Object Document) - Tag 0x77
 * - DG1 (MRZ - Machine Readable Zone) - Tag 0x61
 * - DG2 (Facial Image) - Tag 0x75
 * - DG14 (Security Info for Chip Authentication) - Tag 0x6E
 *
 * Purpose: Verify PA implementation against real-world data
 */
@SpringBootTest
@Slf4j
class RealPassportDataAnalysisTest {

    private static final String DATA_DIR = "data/temp/";

    @BeforeAll
    static void setUp() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void shouldParseSodAndExtractDataGroupHashes() throws Exception {
        // Given: Real Korean passport SOD (wrapped with LDS tag 0x77)
        byte[] sodBytesRaw = Files.readAllBytes(Path.of(DATA_DIR + "sod.bin"));
        log.info("SOD file size (raw): {} bytes", sodBytesRaw.length);

        // Remove LDS file wrapper (tag 0x77) per ICAO 9303-10
        byte[] sodBytes = removeLdsWrapper(sodBytesRaw);
        log.info("SOD file size (unwrapped): {} bytes", sodBytes.length);

        // When: Parse CMS SignedData (PKCS#7)
        CMSSignedData cmsSignedData = new CMSSignedData(sodBytes);

        // Then: Verify CMS structure
        assertThat(cmsSignedData).isNotNull();
        assertThat(cmsSignedData.getSignedContent()).isNotNull();

        // Extract LDSSecurityObject
        byte[] content = (byte[]) cmsSignedData.getSignedContent().getContent();
        LDSSecurityObject ldsSecurityObject = parseLDSSecurityObject(content);

        // Extract Data Group Hashes
        Map<Integer, byte[]> dataGroupHashes = extractDataGroupHashes(ldsSecurityObject);

        log.info("=== SOD Analysis Results ===");
        log.info("Hash Algorithm: {}", ldsSecurityObject.getDigestAlgorithmIdentifier().getAlgorithm());
        log.info("LDS Version: {}", ldsSecurityObject.getVersion());
        log.info("Data Groups found: {}", dataGroupHashes.size());

        for (Map.Entry<Integer, byte[]> entry : dataGroupHashes.entrySet()) {
            log.info("DG{}: hash = {}",
                entry.getKey(),
                Hex.toHexString(entry.getValue()));
        }

        // Verify expected Data Groups for Korean passport
        assertThat(dataGroupHashes).containsKeys(1, 2, 14);
        log.info("✅ Korean passport contains DG1 (MRZ), DG2 (Photo), DG14 (Chip Auth)");
    }

    @Test
    void shouldVerifyDG1Hash() throws Exception {
        // Given: Real DG1 data and expected hash from SOD
        byte[] dg1Bytes = Files.readAllBytes(Path.of(DATA_DIR + "dg1.bin"));
        byte[] sodBytes = Files.readAllBytes(Path.of(DATA_DIR + "sod.bin"));

        log.info("DG1 file size: {} bytes", dg1Bytes.length);

        // Extract expected hash from SOD
        Map<Integer, byte[]> expectedHashes = extractHashesFromSod(sodBytes);
        byte[] expectedDg1Hash = expectedHashes.get(1);

        assertThat(expectedDg1Hash).isNotNull();
        log.info("Expected DG1 hash from SOD: {}", Hex.toHexString(expectedDg1Hash));

        // When: Compute actual DG1 hash
        String hashAlgorithm = getHashAlgorithm(sodBytes);
        MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
        byte[] actualDg1Hash = digest.digest(dg1Bytes);

        log.info("Computed DG1 hash: {}", Hex.toHexString(actualDg1Hash));

        // Then: Hashes should match
        assertThat(actualDg1Hash).isEqualTo(expectedDg1Hash);
        log.info("✅ DG1 hash verification PASSED");
    }

    @Test
    void shouldVerifyDG2Hash() throws Exception {
        // Given: Real DG2 data and expected hash from SOD
        byte[] dg2Bytes = Files.readAllBytes(Path.of(DATA_DIR + "dg2.bin"));
        byte[] sodBytes = Files.readAllBytes(Path.of(DATA_DIR + "sod.bin"));

        log.info("DG2 file size: {} bytes", dg2Bytes.length);

        // Extract expected hash from SOD
        Map<Integer, byte[]> expectedHashes = extractHashesFromSod(sodBytes);
        byte[] expectedDg2Hash = expectedHashes.get(2);

        assertThat(expectedDg2Hash).isNotNull();
        log.info("Expected DG2 hash from SOD: {}", Hex.toHexString(expectedDg2Hash));

        // When: Compute actual DG2 hash
        String hashAlgorithm = getHashAlgorithm(sodBytes);
        MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
        byte[] actualDg2Hash = digest.digest(dg2Bytes);

        log.info("Computed DG2 hash: {}", Hex.toHexString(actualDg2Hash));

        // Then: Hashes should match
        assertThat(actualDg2Hash).isEqualTo(expectedDg2Hash);
        log.info("✅ DG2 hash verification PASSED");
    }

    @Test
    void shouldVerifyDG14Hash() throws Exception {
        // Given: Real DG14 data and expected hash from SOD
        byte[] dg14Bytes = Files.readAllBytes(Path.of(DATA_DIR + "dg14.bin"));
        byte[] sodBytes = Files.readAllBytes(Path.of(DATA_DIR + "sod.bin"));

        log.info("DG14 file size: {} bytes", dg14Bytes.length);

        // Extract expected hash from SOD
        Map<Integer, byte[]> expectedHashes = extractHashesFromSod(sodBytes);
        byte[] expectedDg14Hash = expectedHashes.get(14);

        assertThat(expectedDg14Hash).isNotNull();
        log.info("Expected DG14 hash from SOD: {}", Hex.toHexString(expectedDg14Hash));

        // When: Compute actual DG14 hash
        String hashAlgorithm = getHashAlgorithm(sodBytes);
        MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
        byte[] actualDg14Hash = digest.digest(dg14Bytes);

        log.info("Computed DG14 hash: {}", Hex.toHexString(actualDg14Hash));

        // Then: Hashes should match
        assertThat(actualDg14Hash).isEqualTo(expectedDg14Hash);
        log.info("✅ DG14 hash verification PASSED");
    }

    @Test
    void shouldExtractSignerCertificateFromSod() throws Exception {
        // Given: Real Korean passport SOD
        byte[] sodBytesRaw = Files.readAllBytes(Path.of(DATA_DIR + "sod.bin"));

        // Remove LDS wrapper
        byte[] sodBytes = removeLdsWrapper(sodBytesRaw);

        // When: Parse SOD and extract signer certificate (DSC)
        CMSSignedData cmsSignedData = new CMSSignedData(sodBytes);

        SignerInformationStore signerInfos = cmsSignedData.getSignerInfos();
        Collection<SignerInformation> signers = signerInfos.getSigners();

        assertThat(signers).hasSize(1);

        SignerInformation signer = signers.iterator().next();

        // Extract signer certificate
        @SuppressWarnings("unchecked")
        Collection<X509CertificateHolder> certCollection =
            (Collection<X509CertificateHolder>) cmsSignedData.getCertificates().getMatches(signer.getSID());

        assertThat(certCollection).hasSize(1);

        X509CertificateHolder certHolder = certCollection.iterator().next();

        // Then: Log certificate details
        log.info("=== SOD Signer Certificate (DSC) ===");
        log.info("Subject: {}", certHolder.getSubject());
        log.info("Issuer: {}", certHolder.getIssuer());
        log.info("Serial Number: {}", certHolder.getSerialNumber());
        log.info("Not Before: {}", certHolder.getNotBefore());
        log.info("Not After: {}", certHolder.getNotAfter());
        log.info("Signature Algorithm: {}", certHolder.getSignatureAlgorithm().getAlgorithm());

        // This DSC should be signed by a CSCA in LDAP
        String issuerDn = certHolder.getIssuer().toString();
        log.info("\n⚠️  Need to find CSCA in LDAP with Subject DN: {}", issuerDn);
    }

    // ========== Helper Methods (ICAO 9303-10 Compliant) ==========

    /**
     * Remove LDS file wrapper per ICAO 9303-10
     *
     * LDS File Structure:
     * - Tag: 0x77 (APPLICATION [23] for EF.SOD)
     * - Length: Variable
     * - Value: CMS SignedData
     */
    private byte[] removeLdsWrapper(byte[] ldsFileBytes) throws IOException {
        try (ASN1InputStream asn1In = new ASN1InputStream(new ByteArrayInputStream(ldsFileBytes))) {
            ASN1Primitive obj = asn1In.readObject();

            // Check if it's a tagged object (APPLICATION [23] = 0x77)
            if (!(obj instanceof ASN1TaggedObject)) {
                throw new IllegalArgumentException("EF.SOD is not a tagged object");
            }

            ASN1TaggedObject tagged = (ASN1TaggedObject) obj;

            // ICAO EF.SOD = Application[23] (Tag Number 23)
            if (tagged.getTagClass() != BERTags.APPLICATION || tagged.getTagNo() != 23) {
                throw new IllegalArgumentException("EF.SOD is not tagged with [APPLICATION 23]");
            }

            // EXPLICIT tagging - baseObject is the CMS ContentInfo (SEQUENCE)
            ASN1Primitive content = tagged.getBaseObject().toASN1Primitive();
            return content.getEncoded(ASN1Encoding.DER);
        }
    }

    /**
     * Parse LDSSecurityObject from CMS SignedData content
     *
     * LDSSecurityObject ::= SEQUENCE {
     *     version LDSSecurityObjectVersion,
     *     hashAlgorithm DigestAlgorithmIdentifier,
     *     datagroupHashValues SEQUENCE SIZE (2..ub-DataGroups) OF DataGroupHash
     * }
     */
    private LDSSecurityObject parseLDSSecurityObject(byte[] content) throws IOException {
        try (ASN1InputStream asn1In = new ASN1InputStream(new ByteArrayInputStream(content))) {
            ASN1Primitive obj = asn1In.readObject();
            return LDSSecurityObject.getInstance(obj);
        }
    }

    /**
     * Extract Data Group Hashes from LDSSecurityObject
     *
     * DataGroupHash ::= SEQUENCE {
     *     dataGroupNumber DataGroupNumber,
     *     dataGroupHashValue OCTET STRING
     * }
     */
    private Map<Integer, byte[]> extractDataGroupHashes(LDSSecurityObject ldsSecurityObject) {
        Map<Integer, byte[]> hashes = new HashMap<>();

        ASN1Encodable[] dataGroupHashValues = ldsSecurityObject.getDatagroupHash();

        for (ASN1Encodable encodable : dataGroupHashValues) {
            ASN1Sequence seq = ASN1Sequence.getInstance(encodable);

            ASN1Integer dataGroupNumber = ASN1Integer.getInstance(seq.getObjectAt(0));
            ASN1OctetString hashValue = ASN1OctetString.getInstance(seq.getObjectAt(1));

            int dgNumber = dataGroupNumber.getValue().intValue();
            byte[] hash = hashValue.getOctets();

            hashes.put(dgNumber, hash);
        }

        return hashes;
    }

    private Map<Integer, byte[]> extractHashesFromSod(byte[] sodBytesRaw) throws Exception {
        byte[] sodBytes = removeLdsWrapper(sodBytesRaw);
        CMSSignedData cmsSignedData = new CMSSignedData(sodBytes);
        byte[] content = (byte[]) cmsSignedData.getSignedContent().getContent();
        LDSSecurityObject ldsSecurityObject = parseLDSSecurityObject(content);
        return extractDataGroupHashes(ldsSecurityObject);
    }

    private String getHashAlgorithm(byte[] sodBytesRaw) throws Exception {
        byte[] sodBytes = removeLdsWrapper(sodBytesRaw);
        CMSSignedData cmsSignedData = new CMSSignedData(sodBytes);
        byte[] content = (byte[]) cmsSignedData.getSignedContent().getContent();
        LDSSecurityObject ldsSecurityObject = parseLDSSecurityObject(content);

        ASN1ObjectIdentifier algorithmOid = ldsSecurityObject.getDigestAlgorithmIdentifier().getAlgorithm();

        // OID to algorithm name mapping (NIST standards)
        if ("2.16.840.1.101.3.4.2.1".equals(algorithmOid.getId())) {
            return "SHA-256";
        } else if ("2.16.840.1.101.3.4.2.2".equals(algorithmOid.getId())) {
            return "SHA-384";
        } else if ("2.16.840.1.101.3.4.2.3".equals(algorithmOid.getId())) {
            return "SHA-512";
        } else {
            throw new IllegalArgumentException("Unsupported hash algorithm: " + algorithmOid.getId());
        }
    }
}
