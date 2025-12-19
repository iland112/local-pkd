package com.smartcoreinc.localpkd.passiveauthentication.infrastructure.adapter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Debug test for SOD parsing - Phase 4.11.5
 */
@DisplayName("SOD Parsing Debug Tests")
class BouncyCastleSodParserAdapterDebugTest {

    private static final String SOD_PATH = "src/test/resources/passport-fixtures/valid-korean-passport/sod.bin";

    @Test
    @DisplayName("Debug: Parse SOD Data Group Hashes")
    void testParseDataGroupHashes() throws Exception {
        // Given
        byte[] sodBytes = Files.readAllBytes(Path.of(SOD_PATH));
        System.out.println("SOD size: " + sodBytes.length + " bytes");
        System.out.println("First 4 bytes: " + String.format("%02X %02X %02X %02X",
            sodBytes[0] & 0xFF, sodBytes[1] & 0xFF, sodBytes[2] & 0xFF, sodBytes[3] & 0xFF));

        BouncyCastleSodParserAdapter parser = new BouncyCastleSodParserAdapter();

        // When
        var hashes = parser.parseDataGroupHashes(sodBytes);

        // Then
        System.out.println("Data Group Hashes found: " + hashes.size());
        hashes.forEach((k, v) -> System.out.println("  " + k + ": " + v.getValue()));

        assertFalse(hashes.isEmpty(), "Should extract at least one data group hash");
    }

    @Test
    @DisplayName("Debug: Extract DSC Certificate from SOD")
    void testExtractDscCertificate() throws Exception {
        // Given
        byte[] sodBytes = Files.readAllBytes(Path.of(SOD_PATH));
        BouncyCastleSodParserAdapter parser = new BouncyCastleSodParserAdapter();

        // When
        var dscCert = parser.extractDscCertificate(sodBytes);

        // Then
        System.out.println("DSC Subject: " + dscCert.getSubjectX500Principal().getName());
        System.out.println("DSC Issuer: " + dscCert.getIssuerX500Principal().getName());
        System.out.println("DSC Serial: " + dscCert.getSerialNumber().toString(16).toUpperCase());

        assertNotNull(dscCert, "Should extract DSC certificate");
    }

    @Test
    @DisplayName("Debug: Extract DSC Info (Subject DN + Serial)")
    void testExtractDscInfo() throws Exception {
        // Given
        byte[] sodBytes = Files.readAllBytes(Path.of(SOD_PATH));
        BouncyCastleSodParserAdapter parser = new BouncyCastleSodParserAdapter();

        // When
        var dscInfo = parser.extractDscInfo(sodBytes);

        // Then
        System.out.println("DSC Info Subject DN: " + dscInfo.subjectDn());
        System.out.println("DSC Info Serial: " + dscInfo.serialNumber());

        assertNotNull(dscInfo.subjectDn(), "Should extract subject DN");
        assertNotNull(dscInfo.serialNumber(), "Should extract serial number");
    }

    @Test
    @DisplayName("Debug: Verify SOD Signature")
    void testVerifySignature() throws Exception {
        // Given
        byte[] sodBytes = Files.readAllBytes(Path.of(SOD_PATH));
        BouncyCastleSodParserAdapter parser = new BouncyCastleSodParserAdapter();

        // Extract DSC for signature verification
        var dscCert = parser.extractDscCertificate(sodBytes);

        // When
        boolean valid = parser.verifySignature(sodBytes, dscCert);

        // Then
        System.out.println("Signature valid: " + valid);
        assertTrue(valid, "SOD signature should be valid with embedded DSC");
    }

    @Test
    @DisplayName("Debug: Extract Hash Algorithm")
    void testExtractHashAlgorithm() throws Exception {
        // Given
        byte[] sodBytes = Files.readAllBytes(Path.of(SOD_PATH));
        BouncyCastleSodParserAdapter parser = new BouncyCastleSodParserAdapter();

        // When
        String hashAlg = parser.extractHashAlgorithm(sodBytes);

        // Then
        System.out.println("Hash algorithm: " + hashAlg);
        assertNotNull(hashAlg, "Should extract hash algorithm");
    }

    @Test
    @DisplayName("Debug: Extract Signature Algorithm")
    void testExtractSignatureAlgorithm() throws Exception {
        // Given
        byte[] sodBytes = Files.readAllBytes(Path.of(SOD_PATH));
        BouncyCastleSodParserAdapter parser = new BouncyCastleSodParserAdapter();

        // When
        String sigAlg = parser.extractSignatureAlgorithm(sodBytes);

        // Then
        System.out.println("Signature algorithm: " + sigAlg);
        assertNotNull(sigAlg, "Should extract signature algorithm");
    }
}
