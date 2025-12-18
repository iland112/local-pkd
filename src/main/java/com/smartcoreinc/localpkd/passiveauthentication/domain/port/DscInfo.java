package com.smartcoreinc.localpkd.passiveauthentication.domain.port;

/**
 * Document Signer Certificate (DSC) information extracted from SOD.
 * <p>
 * This record holds the DSC Subject DN and Serial Number that are extracted
 * from the CMS SignedData structure in the SOD.
 * <p>
 * These values are used for:
 * - LDAP lookup to find the DSC certificate
 * - Audit logging
 * - Trust chain verification
 *
 * @param subjectDn DSC Subject Distinguished Name (e.g., "CN=Document Signer,O=Ministry,C=KR")
 * @param serialNumber DSC Serial Number in hexadecimal format (e.g., "1A2B3C4D5E6F")
 */
public record DscInfo(
    String subjectDn,
    String serialNumber
) {
    /**
     * Validates DSC information.
     *
     * @throws IllegalArgumentException if either field is null or empty
     */
    public DscInfo {
        if (subjectDn == null || subjectDn.isBlank()) {
            throw new IllegalArgumentException("DSC Subject DN cannot be null or blank");
        }
        if (serialNumber == null || serialNumber.isBlank()) {
            throw new IllegalArgumentException("DSC Serial Number cannot be null or blank");
        }
    }
}
