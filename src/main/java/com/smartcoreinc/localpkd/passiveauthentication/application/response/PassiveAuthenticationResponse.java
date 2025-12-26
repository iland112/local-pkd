package com.smartcoreinc.localpkd.passiveauthentication.application.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassiveAuthenticationError;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassiveAuthenticationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Main response DTO for Passive Authentication verification.
 * <p>
 * This DTO encapsulates the complete result of ePassport Passive Authentication
 * according to ICAO 9303 Part 11 standard, including certificate chain validation,
 * SOD signature verification, and data group hash verification.
 * </p>
 *
 * <h3>Response Structure:</h3>
 * <pre>
 * PassiveAuthenticationResponse
 * ├── status (VALID/INVALID/ERROR)
 * ├── verificationId (UUID)
 * ├── verificationTimestamp
 * ├── certificateChainValidation
 * │   └── (DSC → CSCA chain validation)
 * ├── sodSignatureValidation
 * │   └── (SOD signature verification)
 * ├── dataGroupValidation
 * │   └── (DG1-DG16 hash verification)
 * ├── processingDurationMs
 * └── errors (if any)
 * </pre>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * PassiveAuthenticationResponse response = useCase.execute(command);
 * if (response.status() == PassiveAuthenticationStatus.VALID) {
 *     System.out.println("Passport is authentic!");
 * } else {
 *     response.errors().forEach(error ->
 *         System.err.println(error.code() + ": " + error.message())
 *     );
 * }
 * }</pre>
 *
 * @param status Overall verification status (VALID, INVALID, ERROR)
 * @param verificationId Unique identifier for this verification
 * @param verificationTimestamp When verification was completed
 * @param issuingCountry ISO 3166-1 alpha-3 country code
 * @param documentNumber Passport document number
 * @param certificateChainValidation Certificate chain validation details
 * @param sodSignatureValidation SOD signature validation details
 * @param dataGroupValidation Data group hash validation details
 * @param processingDurationMs Processing time in milliseconds
 * @param errors List of validation errors (empty if VALID)
 *
 * @see CertificateChainValidationDto
 * @see SodSignatureValidationDto
 * @see DataGroupValidationDto
 */
public record PassiveAuthenticationResponse(
    PassiveAuthenticationStatus status,
    UUID verificationId,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul")
    LocalDateTime verificationTimestamp,
    String issuingCountry,
    String documentNumber,
    CertificateChainValidationDto certificateChainValidation,
    SodSignatureValidationDto sodSignatureValidation,
    DataGroupValidationDto dataGroupValidation,
    Long processingDurationMs,
    List<PassiveAuthenticationError> errors
) {
    /**
     * Creates a successful PassiveAuthenticationResponse.
     *
     * @param verificationId Verification UUID
     * @param timestamp Completion timestamp
     * @param issuingCountry Country code
     * @param documentNumber Document number
     * @param chainValidation Certificate chain result
     * @param sodValidation SOD signature result
     * @param dgValidation Data group result
     * @param durationMs Processing time
     * @return Response with VALID status
     */
    public static PassiveAuthenticationResponse valid(
        UUID verificationId,
        LocalDateTime timestamp,
        String issuingCountry,
        String documentNumber,
        CertificateChainValidationDto chainValidation,
        SodSignatureValidationDto sodValidation,
        DataGroupValidationDto dgValidation,
        Long durationMs
    ) {
        return new PassiveAuthenticationResponse(
            PassiveAuthenticationStatus.VALID,
            verificationId,
            timestamp,
            issuingCountry,
            documentNumber,
            chainValidation,
            sodValidation,
            dgValidation,
            durationMs,
            List.of()
        );
    }

    /**
     * Creates a failed PassiveAuthenticationResponse.
     *
     * @param verificationId Verification UUID
     * @param timestamp Completion timestamp
     * @param issuingCountry Country code
     * @param documentNumber Document number
     * @param chainValidation Certificate chain result
     * @param sodValidation SOD signature result
     * @param dgValidation Data group result
     * @param durationMs Processing time
     * @param errors List of validation errors
     * @return Response with INVALID status
     */
    public static PassiveAuthenticationResponse invalid(
        UUID verificationId,
        LocalDateTime timestamp,
        String issuingCountry,
        String documentNumber,
        CertificateChainValidationDto chainValidation,
        SodSignatureValidationDto sodValidation,
        DataGroupValidationDto dgValidation,
        Long durationMs,
        List<PassiveAuthenticationError> errors
    ) {
        return new PassiveAuthenticationResponse(
            PassiveAuthenticationStatus.INVALID,
            verificationId,
            timestamp,
            issuingCountry,
            documentNumber,
            chainValidation,
            sodValidation,
            dgValidation,
            durationMs,
            errors
        );
    }

    /**
     * Creates an error PassiveAuthenticationResponse.
     *
     * @param verificationId Verification UUID
     * @param timestamp Completion timestamp
     * @param issuingCountry Country code
     * @param documentNumber Document number
     * @param durationMs Processing time
     * @param errors List of errors
     * @return Response with ERROR status
     */
    public static PassiveAuthenticationResponse error(
        UUID verificationId,
        LocalDateTime timestamp,
        String issuingCountry,
        String documentNumber,
        Long durationMs,
        List<PassiveAuthenticationError> errors
    ) {
        return new PassiveAuthenticationResponse(
            PassiveAuthenticationStatus.ERROR,
            verificationId,
            timestamp,
            issuingCountry,
            documentNumber,
            null,
            null,
            null,
            durationMs,
            errors
        );
    }
}
