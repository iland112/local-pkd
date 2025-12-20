package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

/**
 * Verification step in Passive Authentication process.
 *
 * <p>PA consists of the following steps:
 * <ol>
 *   <li>VERIFICATION_STARTED: Initial verification setup</li>
 *   <li>CERTIFICATE_CHAIN: Validate trust chain (CSCA → DSC)</li>
 *   <li>SOD_SIGNATURE: Verify SOD signature with DSC public key</li>
 *   <li>DATA_GROUP_HASH: Verify each data group hash against SOD</li>
 *   <li>VERIFICATION_COMPLETED: Final verification result</li>
 * </ol>
 */
public enum PassiveAuthenticationStep {
    /**
     * Verification process started.
     */
    VERIFICATION_STARTED,

    /**
     * Certificate chain validation (CSCA → DSC).
     */
    CERTIFICATE_CHAIN,

    /**
     * SOD signature verification using DSC public key.
     */
    SOD_SIGNATURE,

    /**
     * Data group hash verification.
     */
    DATA_GROUP_HASH,

    /**
     * Verification process completed.
     */
    VERIFICATION_COMPLETED
}
