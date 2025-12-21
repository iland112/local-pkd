package com.smartcoreinc.localpkd.fileparsing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * CmsBinaryData - CMS (Cryptographic Message Syntax) signed binary data
 *
 * <p>Represents the complete CMS-signed Master List binary as downloaded from ICAO PKD.
 * This binary contains multiple CSCA certificates and signature information.</p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-27
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CmsBinaryData {

    // @Lob
    // @Column(name = "cms_binary", nullable = false)
    @Column(name = "cms_binary", nullable = false, columnDefinition = "BYTEA")
    private byte[] value;

    private CmsBinaryData(byte[] value) {
        validate(value);
        this.value = value.clone();  // Defensive copy
    }

    public static CmsBinaryData of(byte[] value) {
        return new CmsBinaryData(value);
    }

    private void validate(byte[] value) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException("CMS binary data cannot be null or empty");
        }

        // Check minimum size (CMS structure is at least a few hundred bytes)
        if (value.length < 100) {
            throw new IllegalArgumentException(
                "CMS binary data too small (" + value.length + " bytes). " +
                "Minimum expected size is 100 bytes for valid CMS structure.");
        }

        // Optional: Check CMS magic bytes (starts with 0x30 for ASN.1 SEQUENCE)
        if (value[0] != 0x30) {
            throw new IllegalArgumentException(
                "Invalid CMS binary format. Expected ASN.1 SEQUENCE tag (0x30) at start.");
        }
    }

    /**
     * Get a defensive copy of the binary data
     */
    public byte[] getValue() {
        return value != null ? value.clone() : null;
    }

    /**
     * Get the size of the binary data in bytes
     */
    public int getSize() {
        return value != null ? value.length : 0;
    }

    @Override
    public String toString() {
        return "CmsBinaryData[" + getSize() + " bytes]";
    }
}
