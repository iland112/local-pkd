package com.smartcoreinc.localpkd.fileparsing.domain.model;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCode;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.fileparsing.domain.event.MasterListCreatedEvent;
import com.smartcoreinc.localpkd.shared.domain.AggregateRoot;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MasterList - Aggregate root for ICAO Master List
 *
 * <p><b>Purpose</b>: Represents an ICAO CSCA Master List file</p>
 *
 * <p>A Master List is a CMS-signed binary file containing multiple CSCA certificates
 * from various countries. It is published by ICAO PKD for e-Passport authentication.</p>
 *
 * <h3>Dual Storage Strategy</h3>
 * <ul>
 *   <li><b>LDAP</b>: Complete binary stored via {@code pkdMasterListContent} attribute (ICAO standard)</li>
 *   <li><b>PostgreSQL</b>: Binary + extracted CSCA certificates for analysis/search</li>
 * </ul>
 *
 * <h3>Bounded Context</h3>
 * <p>File Parsing Context - Represents parsed Master List file</p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-27
 */
@Entity
@Table(name = "master_list", indexes = {
    @Index(name = "idx_master_list_upload_id", columnList = "upload_id"),
    @Index(name = "idx_master_list_country_code", columnList = "country_code"),
    @Index(name = "idx_master_list_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MasterList extends AggregateRoot<MasterListId> {

    @EmbeddedId
    private MasterListId id;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "id", column = @Column(name = "upload_id", nullable = false))
    })
    private UploadId uploadId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "country_code", nullable = false, length = 3))
    private CountryCode countryCode;

    @Embedded
    private MasterListVersion version;

    @Column(name = "csca_count", nullable = false)
    private int cscaCount;

    @Embedded
    private CmsBinaryData cmsBinary;

    @Embedded
    private SignerInfo signerInfo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ===========================
    // Factory Methods
    // ===========================

    /**
     * Create a new Master List from parsed data
     *
     * @param id Unique identifier
     * @param uploadId Reference to uploaded file
     * @param countryCode Country code of Master List signer
     * @param version Master List version
     * @param cmsBinary Complete CMS-signed binary data
     * @param signerInfo Information about the signer
     * @param cscaCount Number of CSCA certificates in the Master List
     * @return New MasterList aggregate
     */
    public static MasterList create(MasterListId id,
                                     UploadId uploadId,
                                     CountryCode countryCode,
                                     MasterListVersion version,
                                     CmsBinaryData cmsBinary,
                                     SignerInfo signerInfo,
                                     int cscaCount) {
        if (id == null) {
            throw new IllegalArgumentException("MasterListId cannot be null");
        }
        if (uploadId == null) {
            throw new IllegalArgumentException("UploadId cannot be null");
        }
        if (countryCode == null) {
            throw new IllegalArgumentException("CountryCode cannot be null");
        }
        if (cmsBinary == null) {
            throw new IllegalArgumentException("CmsBinaryData cannot be null");
        }
        if (cscaCount < 0) {
            throw new IllegalArgumentException("CSCA count cannot be negative: " + cscaCount);
        }

        MasterList masterList = new MasterList();
        masterList.id = id;
        masterList.uploadId = uploadId;
        masterList.countryCode = countryCode;
        masterList.version = version != null ? version : MasterListVersion.unknown();
        masterList.cmsBinary = cmsBinary;
        masterList.signerInfo = signerInfo != null ? signerInfo : SignerInfo.empty();
        masterList.cscaCount = cscaCount;
        masterList.createdAt = LocalDateTime.now();

        // Register domain event
        masterList.addDomainEvent(new MasterListCreatedEvent(
            masterList.id,
            masterList.uploadId,
            masterList.countryCode,
            masterList.cscaCount
        ));

        return masterList;
    }

    // ===========================
    // Business Methods
    // ===========================

    /**
     * Get the size of the CMS binary in bytes
     */
    public int getBinarySize() {
        return cmsBinary != null ? cmsBinary.getSize() : 0;
    }

    /**
     * Check if signer information is available
     */
    public boolean hasSignerInfo() {
        return signerInfo != null && signerInfo.getSignerDn() != null;
    }

    /**
     * Get signer DN if available
     */
    public String getSignerDn() {
        return hasSignerInfo() ? signerInfo.getSignerDn() : null;
    }

    // ===========================
    // Aggregate Root Methods
    // ===========================

    @Override
    public MasterListId getId() {
        return id;
    }

    @Override
    public String toString() {
        return String.format("MasterList[id=%s, country=%s, version=%s, cscaCount=%d, size=%d bytes]",
                id, countryCode, version, cscaCount, getBinarySize());
    }
}
