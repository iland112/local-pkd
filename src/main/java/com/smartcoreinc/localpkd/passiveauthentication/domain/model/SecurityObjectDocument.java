package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Security Object Document (SOD) from ePassport.
 *
 * <p>SOD is a CMS SignedData (PKCS#7) structure containing:
 * <ul>
 *   <li>LDSSecurityObject with hashes of all data groups</li>
 *   <li>Signature created with Document Signer Certificate (DSC)</li>
 *   <li>Hash algorithm identifier (SHA-256, SHA-384, SHA-512)</li>
 *   <li>Signature algorithm identifier (SHA256withRSA, etc.)</li>
 * </ul>
 *
 * <p>Used for Passive Authentication verification.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // For JPA
public class SecurityObjectDocument {

    @Column(name = "sod_encoded", columnDefinition = "BYTEA")
    private byte[] encodedData;  // PKCS#7 SignedData binary

    @Column(name = "hash_algorithm", length = 20)
    private String hashAlgorithm;  // SHA-256, SHA-384, SHA-512

    @Column(name = "signature_algorithm", length = 50)
    private String signatureAlgorithm;  // SHA256withRSA, SHA384withRSA, etc.

    /**
     * Create SecurityObjectDocument from encoded bytes.
     *
     * @param sodBytes PKCS#7 SignedData bytes
     * @return SecurityObjectDocument instance
     */
    public static SecurityObjectDocument of(byte[] sodBytes) {
        return new SecurityObjectDocument(sodBytes);
    }

    /**
     * Create SecurityObjectDocument with algorithms.
     *
     * @param sodBytes PKCS#7 SignedData bytes
     * @param hashAlgorithm hash algorithm name
     * @param signatureAlgorithm signature algorithm name
     * @return SecurityObjectDocument instance
     */
    public static SecurityObjectDocument withAlgorithms(
        byte[] sodBytes,
        String hashAlgorithm,
        String signatureAlgorithm
    ) {
        SecurityObjectDocument sod = new SecurityObjectDocument(sodBytes);
        sod.hashAlgorithm = hashAlgorithm;
        sod.signatureAlgorithm = signatureAlgorithm;
        return sod;
    }

    private SecurityObjectDocument(byte[] encodedData) {
        validate(encodedData);
        this.encodedData = encodedData;
    }

    private void validate(byte[] sodBytes) {
        if (sodBytes == null || sodBytes.length == 0) {
            throw new DomainException("INVALID_SOD", "SOD data cannot be null or empty");
        }

        // Basic validation: SOD should start with PKCS#7 SignedData tag (0x30)
        if (sodBytes[0] != 0x30) {
            throw new DomainException(
                "INVALID_SOD_FORMAT",
                "SOD data does not appear to be valid PKCS#7 SignedData (expected tag 0x30)"
            );
        }
    }

    /**
     * Set hash algorithm (extracted from LDSSecurityObject).
     *
     * @param algorithm hash algorithm name
     */
    public void setHashAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            throw new DomainException("INVALID_HASH_ALGORITHM", "Hash algorithm cannot be null or empty");
        }
        this.hashAlgorithm = algorithm;
    }

    /**
     * Set signature algorithm (extracted from SignerInfo).
     *
     * @param algorithm signature algorithm name
     */
    public void setSignatureAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            throw new DomainException("INVALID_SIGNATURE_ALGORITHM", "Signature algorithm cannot be null or empty");
        }
        this.signatureAlgorithm = algorithm;
    }

    /**
     * Get SOD size in bytes.
     *
     * @return size in bytes
     */
    public int getSize() {
        return encodedData != null ? encodedData.length : 0;
    }
}
