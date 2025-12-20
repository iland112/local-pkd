package com.smartcoreinc.localpkd.fileparsing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MasterListVersion - Version number of ICAO Master List
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-27
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MasterListVersion {

    @Column(name = "version", length = 50)
    private String value;

    private MasterListVersion(String value) {
        validate(value);
        this.value = value;
    }

    public static MasterListVersion of(String value) {
        return new MasterListVersion(value);
    }

    public static MasterListVersion unknown() {
        return new MasterListVersion("UNKNOWN");
    }

    private void validate(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Master List version cannot be null or empty");
        }

        if (value.length() > 50) {
            throw new IllegalArgumentException(
                "Master List version too long (" + value.length() + " chars). Maximum is 50 characters.");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
