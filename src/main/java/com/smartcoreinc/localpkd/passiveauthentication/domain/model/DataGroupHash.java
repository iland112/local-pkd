package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hash value of a Data Group.
 *
 * <p>Represents the cryptographic hash (SHA-256, SHA-384, SHA-512) of a data group content.
 * Used for integrity verification in Passive Authentication.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // For JPA
public class DataGroupHash {

    @Column(name = "hash_value", length = 128)
    private String value;  // Hex-encoded hash

    /**
     * Create DataGroupHash from hex-encoded string.
     *
     * @param hexValue hex-encoded hash value
     * @return DataGroupHash instance
     */
    public static DataGroupHash of(String hexValue) {
        return new DataGroupHash(hexValue);
    }

    /**
     * Create DataGroupHash from byte array.
     *
     * @param hashBytes raw hash bytes
     * @return DataGroupHash instance
     */
    public static DataGroupHash of(byte[] hashBytes) {
        if (hashBytes == null || hashBytes.length == 0) {
            throw new DomainException("INVALID_HASH", "Hash bytes cannot be null or empty");
        }
        String hexValue = HexFormat.of().formatHex(hashBytes);
        return new DataGroupHash(hexValue);
    }

    /**
     * Calculate hash from data group content.
     *
     * @param content data group content bytes
     * @param algorithm hash algorithm (SHA-256, SHA-384, SHA-512)
     * @return calculated DataGroupHash
     */
    public static DataGroupHash calculate(byte[] content, String algorithm) {
        if (content == null || content.length == 0) {
            throw new DomainException("INVALID_CONTENT", "Content cannot be null or empty");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hashBytes = digest.digest(content);
            return of(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new DomainException(
                "UNSUPPORTED_ALGORITHM",
                "Hash algorithm not supported: " + algorithm
            );
        }
    }

    private DataGroupHash(String hexValue) {
        validate(hexValue);
        this.value = hexValue.toLowerCase();
    }

    private void validate(String hexValue) {
        if (hexValue == null || hexValue.isBlank()) {
            throw new DomainException("INVALID_HASH", "Hash value cannot be null or empty");
        }

        // Validate hex format (SHA-256: 64 chars, SHA-384: 96 chars, SHA-512: 128 chars)
        if (!hexValue.matches("^[0-9a-fA-F]{64,128}$")) {
            throw new DomainException(
                "INVALID_HASH_FORMAT",
                "Hash must be hex string (64-128 characters). Got: " + hexValue.length()
            );
        }
    }

    /**
     * Get raw hash bytes.
     *
     * @return hash as byte array
     */
    public byte[] getBytes() {
        return HexFormat.of().parseHex(value);
    }
}
