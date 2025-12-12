package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of Passive Authentication verification.
 *
 * <p>Aggregates the results of all verification steps:
 * <ul>
 *   <li>Overall verification status (VALID/INVALID/ERROR)</li>
 *   <li>Certificate chain validation result</li>
 *   <li>SOD signature validation result</li>
 *   <li>Data group hash verification statistics</li>
 *   <li>Detailed error list</li>
 * </ul>
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // For JPA
public class PassiveAuthenticationResult {

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 20)
    private PassiveAuthenticationStatus status;

    @Column(name = "certificate_chain_valid")
    private boolean certificateChainValid;

    @Column(name = "sod_signature_valid")
    private boolean sodSignatureValid;

    @Column(name = "total_data_groups")
    private int totalDataGroups;

    @Column(name = "valid_data_groups")
    private int validDataGroups;

    @Column(name = "invalid_data_groups")
    private int invalidDataGroups;

    @Column(name = "errors", columnDefinition = "JSONB")
    private String errorsJson;  // Serialized List<PassiveAuthenticationError>

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Create a VALID result (all verifications passed).
     *
     * @param totalDataGroups total number of data groups verified
     * @return PassiveAuthenticationResult with VALID status
     */
    public static PassiveAuthenticationResult valid(int totalDataGroups) {
        PassiveAuthenticationResult result = new PassiveAuthenticationResult();
        result.status = PassiveAuthenticationStatus.VALID;
        result.certificateChainValid = true;
        result.sodSignatureValid = true;
        result.totalDataGroups = totalDataGroups;
        result.validDataGroups = totalDataGroups;
        result.invalidDataGroups = 0;
        result.errorsJson = serializeErrors(Collections.emptyList());
        return result;
    }

    /**
     * Create an INVALID result (one or more verifications failed).
     *
     * @param certificateChainValid certificate chain validation result
     * @param sodSignatureValid SOD signature validation result
     * @param totalDataGroups total number of data groups
     * @param validDataGroups number of valid data groups
     * @param invalidDataGroups number of invalid data groups
     * @param errors list of verification errors
     * @return PassiveAuthenticationResult with INVALID status
     */
    public static PassiveAuthenticationResult invalid(
        boolean certificateChainValid,
        boolean sodSignatureValid,
        int totalDataGroups,
        int validDataGroups,
        int invalidDataGroups,
        List<PassiveAuthenticationError> errors
    ) {
        PassiveAuthenticationResult result = new PassiveAuthenticationResult();
        result.status = PassiveAuthenticationStatus.INVALID;
        result.certificateChainValid = certificateChainValid;
        result.sodSignatureValid = sodSignatureValid;
        result.totalDataGroups = totalDataGroups;
        result.validDataGroups = validDataGroups;
        result.invalidDataGroups = invalidDataGroups;
        result.errorsJson = serializeErrors(errors);
        return result;
    }

    /**
     * Create an ERROR result (unexpected error occurred).
     *
     * @param error critical error that prevented verification
     * @return PassiveAuthenticationResult with ERROR status
     */
    public static PassiveAuthenticationResult error(PassiveAuthenticationError error) {
        PassiveAuthenticationResult result = new PassiveAuthenticationResult();
        result.status = PassiveAuthenticationStatus.ERROR;
        result.certificateChainValid = false;
        result.sodSignatureValid = false;
        result.totalDataGroups = 0;
        result.validDataGroups = 0;
        result.invalidDataGroups = 0;
        result.errorsJson = serializeErrors(List.of(error));
        return result;
    }

    /**
     * Create result with detailed verification statistics.
     *
     * @param certificateChainValid certificate chain validation result
     * @param sodSignatureValid SOD signature validation result
     * @param totalDataGroups total number of data groups
     * @param validDataGroups number of valid data groups
     * @param errors list of verification errors
     * @return PassiveAuthenticationResult
     */
    public static PassiveAuthenticationResult withStatistics(
        boolean certificateChainValid,
        boolean sodSignatureValid,
        int totalDataGroups,
        int validDataGroups,
        List<PassiveAuthenticationError> errors
    ) {
        int invalidDataGroups = totalDataGroups - validDataGroups;

        // Determine overall status
        PassiveAuthenticationStatus overallStatus;
        if (certificateChainValid && sodSignatureValid && invalidDataGroups == 0) {
            overallStatus = PassiveAuthenticationStatus.VALID;
        } else {
            overallStatus = PassiveAuthenticationStatus.INVALID;
        }

        PassiveAuthenticationResult result = new PassiveAuthenticationResult();
        result.status = overallStatus;
        result.certificateChainValid = certificateChainValid;
        result.sodSignatureValid = sodSignatureValid;
        result.totalDataGroups = totalDataGroups;
        result.validDataGroups = validDataGroups;
        result.invalidDataGroups = invalidDataGroups;
        result.errorsJson = serializeErrors(errors);
        return result;
    }

    /**
     * Get deserialized list of verification errors.
     *
     * @return list of PassiveAuthenticationError objects
     */
    public List<PassiveAuthenticationError> getErrors() {
        return deserializeErrors(errorsJson);
    }

    /**
     * Check if verification was successful.
     *
     * @return true if status is VALID
     */
    public boolean isValid() {
        return status == PassiveAuthenticationStatus.VALID;
    }

    /**
     * Check if verification failed.
     *
     * @return true if status is INVALID
     */
    public boolean isInvalid() {
        return status == PassiveAuthenticationStatus.INVALID;
    }

    /**
     * Check if an error occurred during verification.
     *
     * @return true if status is ERROR
     */
    public boolean isError() {
        return status == PassiveAuthenticationStatus.ERROR;
    }

    /**
     * Get hash verification success rate.
     *
     * @return percentage of valid data groups (0-100)
     */
    public double getHashVerificationSuccessRate() {
        if (totalDataGroups == 0) {
            return 0.0;
        }
        return (double) validDataGroups / totalDataGroups * 100.0;
    }

    /**
     * Check if all verification components passed.
     *
     * @return true if certificate chain, SOD signature, and all hashes are valid
     */
    public boolean allComponentsValid() {
        return certificateChainValid
            && sodSignatureValid
            && invalidDataGroups == 0
            && totalDataGroups > 0;
    }

    /**
     * Serialize verification errors to JSON.
     *
     * @param errors list of PassiveAuthenticationError
     * @return JSON string
     */
    private static String serializeErrors(List<PassiveAuthenticationError> errors) {
        try {
            if (errors == null || errors.isEmpty()) {
                return "[]";
            }
            return OBJECT_MAPPER.writeValueAsString(errors);
        } catch (JsonProcessingException e) {
            throw new DomainException(
                "ERROR_SERIALIZATION_FAILED",
                "Failed to serialize verification errors: " + e.getMessage()
            );
        }
    }

    /**
     * Deserialize verification errors from JSON.
     *
     * @param json JSON string
     * @return list of PassiveAuthenticationError
     */
    private static List<PassiveAuthenticationError> deserializeErrors(String json) {
        try {
            if (json == null || json.isBlank() || "[]".equals(json)) {
                return new ArrayList<>();
            }
            return OBJECT_MAPPER.readValue(
                json,
                OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, PassiveAuthenticationError.class)
            );
        } catch (JsonProcessingException e) {
            throw new DomainException(
                "ERROR_DESERIALIZATION_FAILED",
                "Failed to deserialize verification errors: " + e.getMessage()
            );
        }
    }
}
