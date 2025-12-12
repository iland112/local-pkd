package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

import com.smartcoreinc.localpkd.shared.domain.AggregateRoot;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;

/**
 * PassportData Aggregate Root.
 *
 * <p>Represents ePassport data submitted for Passive Authentication verification.
 * Contains:
 * <ul>
 *   <li>Security Object Document (SOD) - PKCS#7 SignedData</li>
 *   <li>Data Groups (DG1-DG16) - ePassport data groups</li>
 *   <li>Verification result - overall PA result</li>
 *   <li>Request metadata - audit information</li>
 *   <li>Timing information - for performance tracking</li>
 * </ul>
 *
 * <p>This aggregate enforces Passive Authentication business rules and
 * maintains audit trail through VerificationAuditLog entries.
 */
@Entity
@Table(
    name = "passport_data",
    indexes = {
        @Index(name = "idx_passport_status", columnList = "verification_status"),
        @Index(name = "idx_passport_started_at", columnList = "started_at"),
        @Index(name = "idx_passport_completed_at", columnList = "completed_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // For JPA
public class PassportData extends AggregateRoot<PassportDataId> {

    @EmbeddedId
    @AttributeOverride(name = "id", column = @Column(name = "id"))
    private PassportDataId id;

    @Embedded
    private SecurityObjectDocument sod;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "passport_data_group",
        joinColumns = @JoinColumn(name = "passport_data_id")
    )
    private List<DataGroup> dataGroups = new ArrayList<>();

    @Embedded
    private PassiveAuthenticationResult result;

    @Embedded
    private RequestMetadata requestMetadata;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "processing_duration_ms")
    private Long processingDurationMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 20)
    private PassiveAuthenticationStatus verificationStatus;

    @Column(name = "raw_request_data", columnDefinition = "JSONB")
    private String rawRequestData;

    /**
     * Create new PassportData for verification.
     *
     * @param sod Security Object Document
     * @param dataGroups list of data groups
     * @param requestMetadata request metadata for audit
     * @param rawRequestData raw request data (JSON)
     * @return PassportData instance
     */
    public static PassportData create(
        SecurityObjectDocument sod,
        List<DataGroup> dataGroups,
        RequestMetadata requestMetadata,
        String rawRequestData
    ) {
        validateCreationParameters(sod, dataGroups);

        PassportData passportData = new PassportData();
        passportData.id = PassportDataId.newId();
        passportData.sod = sod;
        passportData.dataGroups = new ArrayList<>(dataGroups);
        passportData.requestMetadata = requestMetadata;
        passportData.rawRequestData = rawRequestData;
        passportData.startedAt = LocalDateTime.now();
        passportData.verificationStatus = PassiveAuthenticationStatus.VALID;  // Initial optimistic status

        return passportData;
    }

    /**
     * Record verification result.
     *
     * @param result Passive Authentication result
     */
    public void recordResult(PassiveAuthenticationResult result) {
        if (result == null) {
            throw new DomainException("NULL_RESULT", "Verification result cannot be null");
        }

        this.result = result;
        this.verificationStatus = result.getStatus();
        this.completedAt = LocalDateTime.now();
        this.processingDurationMs = calculateProcessingDuration();
    }

    /**
     * Mark verification as started.
     */
    public void markVerificationStarted() {
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
    }

    /**
     * Mark verification as completed.
     *
     * @param status final verification status
     */
    public void markVerificationCompleted(PassiveAuthenticationStatus status) {
        if (status == null) {
            throw new DomainException("NULL_STATUS", "Verification status cannot be null");
        }

        this.verificationStatus = status;
        this.completedAt = LocalDateTime.now();
        this.processingDurationMs = calculateProcessingDuration();
    }

    /**
     * Add data group to passport data.
     *
     * @param dataGroup data group to add
     */
    public void addDataGroup(DataGroup dataGroup) {
        if (dataGroup == null) {
            throw new DomainException("NULL_DATA_GROUP", "Data group cannot be null");
        }

        // Check for duplicate data group numbers
        boolean exists = dataGroups.stream()
            .anyMatch(dg -> dg.getNumber() == dataGroup.getNumber());

        if (exists) {
            throw new DomainException(
                "DUPLICATE_DATA_GROUP",
                "Data group " + dataGroup.getNumber() + " already exists"
            );
        }

        this.dataGroups.add(dataGroup);
    }

    /**
     * Get data group by number.
     *
     * @param number data group number
     * @return Optional containing the data group if found
     */
    public Optional<DataGroup> getDataGroup(DataGroupNumber number) {
        return dataGroups.stream()
            .filter(dg -> dg.getNumber() == number)
            .findFirst();
    }

    /**
     * Get total number of data groups.
     *
     * @return count of data groups
     */
    public int getDataGroupCount() {
        return dataGroups.size();
    }

    /**
     * Get number of valid data groups.
     *
     * @return count of valid data groups
     */
    public int getValidDataGroupCount() {
        return (int) dataGroups.stream()
            .filter(DataGroup::isValid)
            .count();
    }

    /**
     * Get number of invalid data groups.
     *
     * @return count of invalid data groups
     */
    public int getInvalidDataGroupCount() {
        return (int) dataGroups.stream()
            .filter(dg -> !dg.isValid())
            .count();
    }

    /**
     * Check if all data groups are valid.
     *
     * @return true if all data groups passed hash verification
     */
    public boolean allDataGroupsValid() {
        if (dataGroups.isEmpty()) {
            return false;
        }
        return dataGroups.stream().allMatch(DataGroup::isValid);
    }

    /**
     * Check if verification is completed.
     *
     * @return true if verification has finished
     */
    public boolean isCompleted() {
        return completedAt != null;
    }

    /**
     * Check if verification is still in progress.
     *
     * @return true if verification has not finished
     */
    public boolean isInProgress() {
        return completedAt == null;
    }

    /**
     * Check if verification passed (VALID status).
     *
     * @return true if verification status is VALID
     */
    public boolean isValid() {
        return verificationStatus == PassiveAuthenticationStatus.VALID;
    }

    /**
     * Check if verification failed (INVALID status).
     *
     * @return true if verification status is INVALID
     */
    public boolean isInvalid() {
        return verificationStatus == PassiveAuthenticationStatus.INVALID;
    }

    /**
     * Check if an error occurred (ERROR status).
     *
     * @return true if verification status is ERROR
     */
    public boolean isError() {
        return verificationStatus == PassiveAuthenticationStatus.ERROR;
    }

    /**
     * Get processing duration in milliseconds.
     *
     * @return processing duration or null if not completed
     */
    public Long getProcessingDuration() {
        return processingDurationMs;
    }

    /**
     * Get processing duration in seconds.
     *
     * @return processing duration in seconds or null if not completed
     */
    public Double getProcessingDurationInSeconds() {
        if (processingDurationMs == null) {
            return null;
        }
        return processingDurationMs / 1000.0;
    }

    /**
     * Get verification errors if result is available.
     *
     * @return list of verification errors or empty list
     */
    public List<PassiveAuthenticationError> getVerificationErrors() {
        if (result == null) {
            return Collections.emptyList();
        }
        return result.getErrors();
    }

    /**
     * Get critical verification errors.
     *
     * @return list of critical errors
     */
    public List<PassiveAuthenticationError> getCriticalErrors() {
        return getVerificationErrors().stream()
            .filter(error -> error.getSeverity() == PassiveAuthenticationError.Severity.CRITICAL)
            .toList();
    }

    /**
     * Calculate processing duration from start to completion.
     *
     * @return duration in milliseconds
     */
    private Long calculateProcessingDuration() {
        if (startedAt == null || completedAt == null) {
            return null;
        }

        return java.time.Duration.between(startedAt, completedAt).toMillis();
    }

    /**
     * Validate creation parameters.
     *
     * @param sod Security Object Document
     * @param dataGroups list of data groups
     */
    private static void validateCreationParameters(
        SecurityObjectDocument sod,
        List<DataGroup> dataGroups
    ) {
        if (sod == null) {
            throw new DomainException("NULL_SOD", "SOD cannot be null");
        }

        if (dataGroups == null || dataGroups.isEmpty()) {
            throw new DomainException(
                "EMPTY_DATA_GROUPS",
                "At least one data group is required"
            );
        }

        // Check for duplicate data group numbers
        Set<DataGroupNumber> uniqueNumbers = new HashSet<>();
        for (DataGroup dg : dataGroups) {
            if (!uniqueNumbers.add(dg.getNumber())) {
                throw new DomainException(
                    "DUPLICATE_DATA_GROUP",
                    "Duplicate data group number: " + dg.getNumber()
                );
            }
        }
    }

    @Override
    public PassportDataId getId() {
        return id;
    }
}
