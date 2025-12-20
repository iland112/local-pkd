package com.smartcoreinc.localpkd.passiveauthentication.util;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.util.encoders.Base64;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

/**
 * Utility class to extract DSC certificates from SOD for test fixtures.
 *
 * This is a test utility used to generate data.sql content from real passport SOD files.
 */
public class CertificateExtractorUtil {

    /**
     * Main method to extract DSC from Korean passport SOD.
     *
     * Usage:
     *   cd src/test/java
     *   javac -cp "path/to/bcprov.jar" com/smartcoreinc/.../CertificateExtractorUtil.java
     *   java -cp ".:path/to/bcprov.jar" com.smartcoreinc...CertificateExtractorUtil
     */
    public static void main(String[] args) throws Exception {
        Path sodPath = Paths.get("src/test/resources/passport-fixtures/valid-korean-passport/sod.bin");
        byte[] sodBytes = Files.readAllBytes(sodPath);

        System.out.println("=== Extracting DSC from Korean Passport SOD ===\n");

        // 1. Unwrap ICAO 9303 Tag 0x77 if present
        byte[] cmsBytes = unwrapIcaoSod(sodBytes);
        System.out.println("SOD size after unwrapping: " + cmsBytes.length + " bytes\n");

        // 2. Parse CMS SignedData
        CMSSignedData signedData = new CMSSignedData(cmsBytes);

        // 3. Extract certificates
        Collection<X509CertificateHolder> certs = signedData.getCertificates().getMatches(null);

        if (certs.isEmpty()) {
            System.out.println("ERROR: No certificates found in SOD!");
            return;
        }

        // 4. Get first certificate (DSC)
        X509CertificateHolder certHolder = certs.iterator().next();

        // 5. Convert to X509Certificate
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) certFactory.generateCertificate(
            new ByteArrayInputStream(certHolder.getEncoded())
        );

        // 6. Extract certificate information
        String subjectDn = cert.getSubjectX500Principal().getName();
        String issuerDn = cert.getIssuerX500Principal().getName();
        String serialHex = cert.getSerialNumber().toString(16).toUpperCase();
        String serialDec = cert.getSerialNumber().toString();

        System.out.println("DSC Certificate Information:");
        System.out.println("  Subject DN: " + subjectDn);
        System.out.println("  Issuer DN:  " + issuerDn);
        System.out.println("  Serial (Hex): " + serialHex);
        System.out.println("  Serial (Dec): " + serialDec);
        System.out.println("  Not Before: " + cert.getNotBefore());
        System.out.println("  Not After:  " + cert.getNotAfter());
        System.out.println("  Signature Algorithm: " + cert.getSigAlgName());
        System.out.println("  Public Key Algorithm: " + cert.getPublicKey().getAlgorithm());
        System.out.println();

        // 7. Calculate SHA-256 fingerprint
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] fingerprint = sha256.digest(cert.getEncoded());
        String fingerprintHex = bytesToHex(fingerprint);

        System.out.println("  SHA-256 Fingerprint: " + fingerprintHex);
        System.out.println();

        // 8. Encode certificate as Base64
        String certBase64 = Base64.toBase64String(cert.getEncoded());

        System.out.println("=== Certificate Base64 (for data.sql) ===");
        System.out.println(certBase64);
        System.out.println();

        // 9. Generate SQL INSERT statement
        System.out.println("=== SQL INSERT Statement ===");
        System.out.println("-- DSC Certificate from Korean Passport SOD");
        System.out.println("INSERT INTO parsed_certificate (");
        System.out.println("    id, parsed_file_id, certificate_type, country_code,");
        System.out.println("    subject_dn, issuer_dn, serial_number, fingerprint_sha256,");
        System.out.println("    not_before, not_after, encoded,");
        System.out.println("    public_key_algorithm, signature_algorithm,");
        System.out.println("    validation_status, validated_at");
        System.out.println(") VALUES (");
        System.out.println("    '11111111-1111-1111-1111-111111111111',  -- Fixed UUID for DSC");
        System.out.println("    '00000000-0000-0000-0000-000000000000',  -- Fixed UUID for test file");
        System.out.println("    'DSC',");
        System.out.println("    'KR',");
        System.out.println("    '" + escapeSql(subjectDn) + "',");
        System.out.println("    '" + escapeSql(issuerDn) + "',");
        System.out.println("    '" + serialHex + "',");
        System.out.println("    '" + fingerprintHex + "',");
        System.out.println("    '" + new java.sql.Timestamp(cert.getNotBefore().getTime()) + "',");
        System.out.println("    '" + new java.sql.Timestamp(cert.getNotAfter().getTime()) + "',");
        System.out.println("    decode('" + certBase64 + "', 'base64'),");
        System.out.println("    '" + cert.getPublicKey().getAlgorithm() + "',");
        System.out.println("    '" + cert.getSigAlgName() + "',");
        System.out.println("    'VALID',");
        System.out.println("    CURRENT_TIMESTAMP");
        System.out.println(");");
    }

    private static byte[] unwrapIcaoSod(byte[] sodBytes) throws IOException {
        if ((sodBytes[0] & 0xFF) != 0x77) {
            return sodBytes;  // No wrapper
        }

        int offset = 1;
        int lengthByte = sodBytes[offset++] & 0xFF;

        if ((lengthByte & 0x80) != 0) {
            int numOctets = lengthByte & 0x7F;
            offset += numOctets;
        }

        byte[] cmsBytes = new byte[sodBytes.length - offset];
        System.arraycopy(sodBytes, offset, cmsBytes, 0, cmsBytes.length);
        return cmsBytes;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String escapeSql(String input) {
        return input.replace("'", "''");
    }
}
