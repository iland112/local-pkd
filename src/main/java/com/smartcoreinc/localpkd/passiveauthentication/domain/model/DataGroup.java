package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

/**
 * Data Group in ePassport LDS (Logical Data Structure).
 *
 * <p>Represents a single data group (DG1-DG16) with its content and hash values.
 * Used for Passive Authentication hash verification.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // For JPA
public class DataGroup {

    @Enumerated(EnumType.STRING)
    @Column(name = "data_group_number", length = 10)
    private DataGroupNumber number;

    @JdbcTypeCode(java.sql.Types.BINARY)  // Hibernate 6: bytea 매핑을 위해 필수
    @Column(name = "content", columnDefinition = "BYTEA")
    private byte[] content;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "expected_hash"))
    })
    private DataGroupHash expectedHash;  // From SOD

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "actual_hash"))
    })
    private DataGroupHash actualHash;  // Calculated

    @Column(name = "is_valid")
    private boolean valid;

    @Column(name = "hash_mismatch_detected")
    private boolean hashMismatchDetected;

    /**
     * Create DataGroup with content only (hash will be calculated later).
     *
     * @param number data group number
     * @param content data group content bytes
     * @return DataGroup instance
     */
    public static DataGroup of(DataGroupNumber number, byte[] content) {
        return new DataGroup(number, content);
    }

    /**
     * Create DataGroup with expected hash from SOD.
     *
     * @param number data group number
     * @param content data group content bytes
     * @param expectedHash hash value from SOD
     * @return DataGroup instance
     */
    public static DataGroup withExpectedHash(
        DataGroupNumber number,
        byte[] content,
        DataGroupHash expectedHash
    ) {
        DataGroup dataGroup = new DataGroup(number, content);
        dataGroup.expectedHash = expectedHash;
        return dataGroup;
    }

    private DataGroup(DataGroupNumber number, byte[] content) {
        validate(number, content);
        this.number = number;
        this.content = content;
        this.valid = false;
        this.hashMismatchDetected = false;
    }

    private void validate(DataGroupNumber number, byte[] content) {
        if (number == null) {
            throw new DomainException("INVALID_DG_NUMBER", "Data Group number cannot be null");
        }
        if (content == null || content.length == 0) {
            throw new DomainException("INVALID_DG_CONTENT", "Data Group content cannot be null or empty");
        }
    }

    /**
     * Calculate actual hash from content using specified algorithm.
     *
     * @param algorithm hash algorithm (SHA-256, SHA-384, SHA-512)
     */
    public void calculateActualHash(String algorithm) {
        this.actualHash = DataGroupHash.calculate(this.content, algorithm);
    }

    /**
     * Verify hash by comparing expected and actual hashes.
     *
     * @return true if hashes match
     */
    public boolean verifyHash() {
        if (expectedHash == null || actualHash == null) {
            throw new DomainException(
                "HASH_NOT_READY",
                "Both expected and actual hashes must be set before verification"
            );
        }

        boolean matches = expectedHash.equals(actualHash);
        this.valid = matches;
        this.hashMismatchDetected = !matches;
        return matches;
    }

    /**
     * Check if this data group is valid (hash matches).
     *
     * @return true if valid
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Get data group number as integer.
     *
     * @return data group number (1-16)
     */
    public int getNumberValue() {
        return number.getValue();
    }
}
