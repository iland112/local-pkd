package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

/**
 * CertificateSourceType - Indicates the origin of a certificate
 *
 * <p>This enum distinguishes between certificates obtained from different sources:</p>
 * <ul>
 *   <li><b>MASTER_LIST</b>: CSCA extracted from ICAO Master List (.ml file)</li>
 *   <li><b>LDIF_DSC</b>: DSC certificate from LDIF file</li>
 *   <li><b>LDIF_CSCA</b>: CSCA certificate from LDIF file (rare, usually comes from Master List)</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-27
 */
public enum CertificateSourceType {

    /**
     * CSCA certificate extracted from Master List binary
     * <p>These are stored in PostgreSQL for analysis but not uploaded individually to LDAP.
     * The Master List binary itself is uploaded to LDAP with {@code pkdMasterListContent} attribute.</p>
     */
    MASTER_LIST,

    /**
     * DSC (Document Signer Certificate) from LDIF file
     * <p>These are individual certificates uploaded to LDAP with {@code userCertificate;binary} attribute
     * under {@code o=dsc} organization type.</p>
     */
    LDIF_DSC,

    /**
     * CSCA (Country Signing Certificate Authority) from LDIF file
     * <p>Rare case where CSCA is provided as individual certificate in LDIF format.
     * Typically, CSCAs come from Master List files.</p>
     */
    LDIF_CSCA;

    /**
     * Check if this source type is from a Master List
     */
    public boolean isFromMasterList() {
        return this == MASTER_LIST;
    }

    /**
     * Check if this source type is from an LDIF file
     */
    public boolean isFromLdif() {
        return this == LDIF_DSC || this == LDIF_CSCA;
    }

    /**
     * Check if this represents a CSCA certificate
     */
    public boolean isCsca() {
        return this == MASTER_LIST || this == LDIF_CSCA;
    }

    /**
     * Check if this represents a DSC certificate
     */
    public boolean isDsc() {
        return this == LDIF_DSC;
    }
}
