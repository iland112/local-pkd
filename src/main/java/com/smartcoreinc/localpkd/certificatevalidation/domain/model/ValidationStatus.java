package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

/**
 * ValidationStatus - Status of certificate validation
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-27
 */
public enum ValidationStatus {

    /**
     * Certificate passed all validation checks
     * <p>Trust chain verified, not expired, not revoked</p>
     */
    VALID,

    /**
     * Certificate failed one or more validation checks
     * <p>Could be expired, untrusted, revoked, or invalid signature</p>
     */
    INVALID,

    /**
     * Validation has not been performed yet
     * <p>Default state when certificate is first parsed</p>
     */
    PENDING;

    public boolean isValid() {
        return this == VALID;
    }

    public boolean isInvalid() {
        return this == INVALID;
    }

    public boolean isPending() {
        return this == PENDING;
    }
}
