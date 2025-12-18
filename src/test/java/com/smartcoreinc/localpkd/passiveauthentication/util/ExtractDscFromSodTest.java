package com.smartcoreinc.localpkd.passiveauthentication.util;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.util.encoders.Base64;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Collection;

/**
 * Extract DSC certificate from Korean passport SOD for test fixtures.
 */
class ExtractDscFromSodTest {

    @Test
    void extractDscCertificate() throws Exception {
        // 1. Read SOD binary
        byte[] sodBytes = Files.readAllBytes(
            Paths.get("src/test/resources/passport-fixtures/valid-korean-passport/sod.bin")
        );

        System.out.println("=== Extracting DSC from Korean Passport SOD ===");
        System.out.println("SOD file size: " + sodBytes.length + " bytes\n");

        // 2. Unwrap ICAO 9303 Tag 0x77
        byte[] cmsBytes = unwrapIcaoSod(sodBytes);
        System.out.println("CMS size after unwrapping: " + cmsBytes.length + " bytes\n");

        // 3. Parse CMS SignedData
        CMSSignedData signedData = new CMSSignedData(cmsBytes);

        // 4. Extract certificates
        Collection<X509CertificateHolder> certs = signedData.getCertificates().getMatches(null);

        if (certs.isEmpty()) {
            System.out.println("ERROR: No certificates found in SOD!");
            return;
        }

        System.out.println("Found " + certs.size() + " certificate(s) in SOD\n");

        // 5. Get first certificate (DSC)
        X509CertificateHolder certHolder = certs.iterator().next();

        // 6. Extract certificate information
        String subjectDn = certHolder.getSubject().toString();
        String issuerDn = certHolder.getIssuer().toString();
        String serialHex = certHolder.getSerialNumber().toString(16).toUpperCase();
        String serialDec = certHolder.getSerialNumber().toString();

        System.out.println("=== DSC Certificate Information ===");
        System.out.println("Subject DN: " + subjectDn);
        System.out.println("Issuer DN:  " + issuerDn);
        System.out.println("Serial (Hex): " + serialHex);
        System.out.println("Serial (Dec): " + serialDec);
        System.out.println("Not Before: " + certHolder.getNotBefore());
        System.out.println("Not After:  " + certHolder.getNotAfter());
        System.out.println();

        // 7. Calculate SHA-256 fingerprint
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] fingerprint = sha256.digest(certHolder.getEncoded());
        String fingerprintHex = bytesToHex(fingerprint);

        System.out.println("SHA-256 Fingerprint: " + fingerprintHex);
        System.out.println();

        // 8. Encode certificate as Base64
        byte[] certEncoded = certHolder.getEncoded();
        String certBase64 = Base64.toBase64String(certEncoded);

        System.out.println("=== Certificate Base64 (for data.sql) ===");
        System.out.println(certBase64);
        System.out.println();

        // 9. Print SQL-ready values
        System.out.println("=== SQL Values ===");
        System.out.println("subject_dn: '" + escapeSql(subjectDn) + "'");
        System.out.println("issuer_dn: '" + escapeSql(issuerDn) + "'");
        System.out.println("serial_number: '" + serialHex + "'");
        System.out.println("fingerprint_sha256: '" + fingerprintHex + "'");
        System.out.println("not_before: '" + certHolder.getNotBefore() + "'");
        System.out.println("not_after: '" + certHolder.getNotAfter() + "'");
        System.out.println("encoded: decode('" + certBase64 + "', 'base64')");
    }

    private byte[] unwrapIcaoSod(byte[] sodBytes) throws IOException {
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

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String escapeSql(String input) {
        return input.replace("'", "''");
    }
}
