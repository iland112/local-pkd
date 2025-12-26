package com.smartcoreinc.localpkd.passiveauthentication.application.usecase;

import com.smartcoreinc.localpkd.passiveauthentication.application.exception.PassiveAuthenticationApplicationException;
import com.smartcoreinc.localpkd.passiveauthentication.application.response.PassiveAuthenticationResponse;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroup;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroupNumber;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassiveAuthenticationError;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassiveAuthenticationStatus;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassportData;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassportDataId;
import com.smartcoreinc.localpkd.passiveauthentication.domain.repository.PassportDataRepository;
import com.smartcoreinc.localpkd.passiveauthentication.infrastructure.adapter.Dg1MrzParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Use Case for retrieving Passive Authentication verification history.
 * <p>
 * This use case provides various query methods to retrieve past PA verification
 * results, supporting filtering by status, date range, and document details.
 * </p>
 *
 * <h3>Supported Queries:</h3>
 * <ul>
 *   <li>Get verification by ID</li>
 *   <li>Get all verifications (paginated)</li>
 *   <li>Get verifications by status</li>
 *   <li>Get verifications by date range</li>
 *   <li>Get verifications by DSC fingerprint</li>
 * </ul>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * @Autowired
 * private GetPassiveAuthenticationHistoryUseCase useCase;
 *
 * // Get specific verification
 * PassiveAuthenticationResponse response = useCase.getById(verificationId);
 *
 * // Get all valid verifications (paginated)
 * Page<PassiveAuthenticationResponse> validVerifications =
 *     useCase.getByStatus(PassiveAuthenticationStatus.VALID, pageable);
 *
 * // Get verifications in date range
 * List<PassiveAuthenticationResponse> recentVerifications =
 *     useCase.getByDateRange(startDate, endDate);
 * }</pre>
 *
 * @see PassiveAuthenticationResponse
 * @see PassportDataRepository
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class GetPassiveAuthenticationHistoryUseCase {

    private final PassportDataRepository passportDataRepository;
    private final Dg1MrzParser dg1MrzParser;

    /**
     * Retrieves a specific verification by ID.
     *
     * @param verificationId Verification UUID
     * @return PassiveAuthenticationResponse
     * @throws BusinessException if verification not found
     */
    public PassiveAuthenticationResponse getById(UUID verificationId) {
        log.debug("Retrieving verification by ID: {}", verificationId);

        PassportData passportData = passportDataRepository.findById(PassportDataId.of(verificationId.toString()))
            .orElseThrow(() -> new PassiveAuthenticationApplicationException(
                "VERIFICATION_NOT_FOUND",
                String.format("Verification not found: %s", verificationId)
            ));

        return toResponse(passportData);
    }

    /**
     * Retrieves PassportData domain entity by ID for accessing raw DG data.
     *
     * @param verificationId Verification UUID
     * @return PassportData or null if not found
     */
    public PassportData getPassportDataById(UUID verificationId) {
        log.debug("Retrieving PassportData by ID: {}", verificationId);

        return passportDataRepository.findById(PassportDataId.of(verificationId.toString()))
            .orElse(null);
    }

    /**
     * Retrieves all verifications.
     *
     * @return List of PassiveAuthenticationResponse
     */
    public List<PassiveAuthenticationResponse> getAll() {
        log.debug("Retrieving all verifications");

        List<PassportData> passportDataList = passportDataRepository.findAll();
        return passportDataList.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * Retrieves verifications by status.
     *
     * @param status Verification status (VALID, INVALID, ERROR)
     * @return List of PassiveAuthenticationResponse
     */
    public List<PassiveAuthenticationResponse> getByStatus(PassiveAuthenticationStatus status) {
        log.debug("Retrieving verifications by status: {}", status);

        List<PassportData> passportDataList = passportDataRepository.findByVerificationStatus(status);
        return passportDataList.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * Retrieves verifications by date range.
     *
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of PassiveAuthenticationResponse
     */
    public List<PassiveAuthenticationResponse> getByDateRange(
        LocalDateTime startDate,
        LocalDateTime endDate
    ) {
        log.debug("Retrieving verifications by date range: {} to {}", startDate, endDate);

        List<PassportData> passportDataList = passportDataRepository.findByStartedAtBetween(
            startDate,
            endDate
        );

        return passportDataList.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * Retrieves completed verifications.
     *
     * @return List of PassiveAuthenticationResponse
     */
    public List<PassiveAuthenticationResponse> getCompleted() {
        log.debug("Retrieving completed verifications");

        List<PassportData> passportDataList = passportDataRepository.findCompleted();
        return passportDataList.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * Retrieves in-progress verifications.
     *
     * @return List of PassiveAuthenticationResponse
     */
    public List<PassiveAuthenticationResponse> getInProgress() {
        log.debug("Retrieving in-progress verifications");

        List<PassportData> passportDataList = passportDataRepository.findInProgress();
        return passportDataList.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * Counts verifications by status.
     *
     * @param status Verification status
     * @return Count of verifications
     */
    public long countByStatus(PassiveAuthenticationStatus status) {
        log.debug("Counting verifications by status: {}", status);
        return passportDataRepository.countByVerificationStatus(status);
    }

    /**
     * Counts in-progress verifications.
     *
     * @return Count of in-progress verifications
     */
    public long countInProgress() {
        log.debug("Counting in-progress verifications");
        return passportDataRepository.countInProgress();
    }

    /**
     * Converts PassportData domain entity to PassiveAuthenticationResponse DTO.
     * <p>
     * Note: This is a simplified conversion that returns minimal information.
     * Full DTO mapping with certificate chain, SOD, and data group validation
     * details would require storing these validation results in the database
     * and reconstructing them here.
     * </p>
     */
    private PassiveAuthenticationResponse toResponse(PassportData passportData) {
        // Determine verification status and timestamp
        PassiveAuthenticationStatus status = passportData.getVerificationStatus();
        LocalDateTime timestamp = passportData.getCompletedAt() != null
            ? passportData.getCompletedAt()
            : passportData.getStartedAt();

        // Extract issuing country and document number from PassportData
        String issuingCountry = passportData.getIssuingCountry() != null
            ? passportData.getIssuingCountry()
            : "UNKNOWN";
        String documentNumber = passportData.getDocumentNumber();

        // If document number is UNKNOWN or null, try to extract from DG1
        if (documentNumber == null || documentNumber.isBlank() || "UNKNOWN".equals(documentNumber)) {
            documentNumber = extractDocumentNumberFromDg1(passportData);
            if (documentNumber == null || documentNumber.isBlank()) {
                documentNumber = "UNKNOWN";
            }
        }

        // Get errors from result if available
        List<PassiveAuthenticationError> errors = passportData.getResult() != null
            ? passportData.getResult().getErrors()
            : List.of();

        // Return appropriate response based on status
        if (status == PassiveAuthenticationStatus.VALID) {
            return PassiveAuthenticationResponse.valid(
                passportData.getId().getId(),
                timestamp,
                issuingCountry,
                documentNumber,
                null,  // TODO: Reconstruct CertificateChainValidationDto
                null,  // TODO: Reconstruct SodSignatureValidationDto
                null,  // TODO: Reconstruct DataGroupValidationDto
                passportData.getProcessingDurationMs()
            );
        } else if (status == PassiveAuthenticationStatus.INVALID) {
            return PassiveAuthenticationResponse.invalid(
                passportData.getId().getId(),
                timestamp,
                issuingCountry,
                documentNumber,
                null,  // TODO: Reconstruct CertificateChainValidationDto
                null,  // TODO: Reconstruct SodSignatureValidationDto
                null,  // TODO: Reconstruct DataGroupValidationDto
                passportData.getProcessingDurationMs(),
                errors
            );
        } else {
            return PassiveAuthenticationResponse.error(
                passportData.getId().getId(),
                timestamp,
                issuingCountry,
                documentNumber,
                passportData.getProcessingDurationMs(),
                errors
            );
        }

        // TODO: Future enhancement - Store validation details in PassportData
        // and implement full DTO reconstruction with:
        // - CertificateChainValidationDto (DSC/CSCA info, CRL check)
        // - SodSignatureValidationDto (signature algorithms, validation status)
        // - DataGroupValidationDto (per-DG hash validation results)
    }

    /**
     * Extracts document number from DG1 (MRZ) data stored in PassportData.
     * <p>
     * This method is used to recover document number from legacy records
     * where the document number was not extracted at verification time.
     * </p>
     *
     * @param passportData PassportData containing DG1 data
     * @return Document number from MRZ, or null if not available
     */
    private String extractDocumentNumberFromDg1(PassportData passportData) {
        if (passportData.getDataGroups() == null || passportData.getDataGroups().isEmpty()) {
            log.debug("No data groups found in PassportData {}", passportData.getId());
            return null;
        }

        // Find DG1 in data groups
        for (DataGroup dg : passportData.getDataGroups()) {
            if (dg.getNumber() == DataGroupNumber.DG1 && dg.getContent() != null && dg.getContent().length > 0) {
                try {
                    Map<String, String> mrzData = dg1MrzParser.parse(dg.getContent());
                    String docNumber = mrzData.get("documentNumber");
                    if (docNumber != null && !docNumber.isBlank()) {
                        log.debug("Extracted document number {} from DG1 for PassportData {}",
                            docNumber, passportData.getId());
                        return docNumber;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse DG1 for document number extraction in PassportData {}: {}",
                        passportData.getId(), e.getMessage());
                }
            }
        }

        return null;
    }
}
