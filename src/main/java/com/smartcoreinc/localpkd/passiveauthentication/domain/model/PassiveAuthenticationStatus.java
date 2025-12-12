package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

/**
 * Overall status of Passive Authentication verification.
 *
 * <p>Status determination:
 * <ul>
 *   <li>VALID: All verification steps passed (certificate chain, SOD signature, data group hashes)</li>
 *   <li>INVALID: One or more verification steps failed</li>
 *   <li>ERROR: Unexpected error occurred during verification process</li>
 * </ul>
 */
public enum PassiveAuthenticationStatus {
    /**
     * All verification steps passed successfully.
     */
    VALID,

    /**
     * One or more verification steps failed.
     */
    INVALID,

    /**
     * Unexpected error occurred during verification.
     */
    ERROR
}
