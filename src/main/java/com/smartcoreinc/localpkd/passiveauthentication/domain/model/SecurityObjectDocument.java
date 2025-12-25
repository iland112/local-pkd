package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

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

    @JdbcTypeCode(java.sql.Types.BINARY)  // Hibernate 6: bytea 매핑을 위해 필수
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

        // Valid SOD formats:
        // 1. ICAO 9303 EF.SOD: starts with Tag 0x77 (Application[23])
        // 2. Raw CMS SignedData: starts with Tag 0x30 (SEQUENCE)
        int firstByte = sodBytes[0] & 0xFF;
        if (firstByte != 0x30 && firstByte != 0x77) {
            throw new DomainException(
                "INVALID_SOD_FORMAT",
                String.format("SOD data does not appear to be valid (expected tag 0x30 or 0x77, got 0x%02X)", firstByte)
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
     * <p>Note: 메서드명을 'getSize' 대신 'calculateSize'로 사용하여
     * Hibernate가 JavaBeans 프로퍼티로 인식하지 않도록 함</p>
     *
     * @return size in bytes
     */
    public int calculateSize() {
        return encodedData != null ? encodedData.length : 0;
    }
}
