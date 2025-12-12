package com.smartcoreinc.localpkd.passiveauthentication.application.usecase;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.adapter.BouncyCastleValidationAdapter;
import com.smartcoreinc.localpkd.passiveauthentication.application.command.PerformPassiveAuthenticationCommand;
import com.smartcoreinc.localpkd.passiveauthentication.application.exception.PassiveAuthenticationApplicationException;
import com.smartcoreinc.localpkd.passiveauthentication.application.response.CertificateChainValidationDto;
import com.smartcoreinc.localpkd.passiveauthentication.application.response.DataGroupValidationDto;
import com.smartcoreinc.localpkd.passiveauthentication.application.response.PassiveAuthenticationResponse;
import com.smartcoreinc.localpkd.passiveauthentication.application.response.SodSignatureValidationDto;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroup;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroupHash;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroupNumber;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassiveAuthenticationError;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassiveAuthenticationResult;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassiveAuthenticationStatus;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassportData;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassportDataId;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.RequestMetadata;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.SecurityObjectDocument;
import com.smartcoreinc.localpkd.passiveauthentication.domain.port.SodParserPort;
import com.smartcoreinc.localpkd.passiveauthentication.domain.repository.PassportDataRepository;
import com.smartcoreinc.localpkd.shared.exception.BusinessException;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.PublicKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Use Case for performing Passive Authentication (PA) verification on ePassport data.
 * <p>
 * This use case orchestrates the complete PA verification process according to
 * ICAO 9303 Part 11 standard, including:
 * <ol>
 *   <li>Certificate Chain Validation (DSC → CSCA)</li>
 *   <li>SOD Signature Verification</li>
 *   <li>Data Group Hash Verification</li>
 * </ol>
 * </p>
 *
 * <h3>Dependencies:</h3>
 * <ul>
 *   <li>{@link CertificateRepository} - Retrieve DSC/CSCA from LDAP</li>
 *   <li>{@link BouncyCastleValidationAdapter} - Certificate validation</li>
 *   <li>{@link CertificateRevocationListRepository} - CRL checking</li>
 *   <li>{@link SodParserPort} - SOD parsing and signature verification</li>
 *   <li>{@link PassportDataRepository} - Store verification results</li>
 * </ul>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * @Autowired
 * private PerformPassiveAuthenticationUseCase useCase;
 *
 * PerformPassiveAuthenticationCommand command = new PerformPassiveAuthenticationCommand(...);
 * PassiveAuthenticationResponse response = useCase.execute(command);
 *
 * if (response.status() == PassiveAuthenticationStatus.VALID) {
 *     log.info("Passport verification successful: {}", response.verificationId());
 * }
 * }</pre>
 *
 * @see PerformPassiveAuthenticationCommand
 * @see PassiveAuthenticationResponse
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PerformPassiveAuthenticationUseCase {

    private final CertificateRepository certificateRepository;
    private final CertificateRevocationListRepository crlRepository;
    private final BouncyCastleValidationAdapter validationAdapter;
    private final SodParserPort sodParser;
    private final PassportDataRepository passportDataRepository;

    /**
     * Executes the Passive Authentication verification process.
     *
     * @param command Verification command containing SOD and data groups
     * @return PassiveAuthenticationResponse with verification results
     * @throws BusinessException if DSC or CSCA not found in LDAP
     * @throws DomainException if validation fails
     */
    public PassiveAuthenticationResponse execute(PerformPassiveAuthenticationCommand command) {
        log.info("Starting Passive Authentication for document: {}-{}",
            command.issuingCountry().getValue(), command.documentNumber());

        LocalDateTime startTime = LocalDateTime.now();
        List<PassiveAuthenticationError> errors = new ArrayList<>();

        try {
            // Step 1: Retrieve DSC from LDAP
            Certificate dsc = retrieveDsc(command);
            log.debug("Retrieved DSC: {}", dsc.getSubjectInfo().getDistinguishedName());

            // Step 2: Retrieve CSCA from LDAP
            Certificate csca = retrieveCsca(dsc);
            log.debug("Retrieved CSCA: {}", csca.getSubjectInfo().getDistinguishedName());

            // Step 3: Validate Certificate Chain
            CertificateChainValidationDto chainValidation = validateCertificateChain(
                dsc, csca, command.issuingCountry().getValue(), errors
            );

            // Step 4: Parse SOD and validate signature
            SecurityObjectDocument sod = SecurityObjectDocument.of(command.sodBytes());
            SodSignatureValidationDto sodValidation = validateSodSignature(
                sod, dsc, errors
            );

            // Step 5: Validate Data Group Hashes
            DataGroupValidationDto dgValidation = validateDataGroupHashes(
                command.dataGroups(), sod, errors
            );

            // Step 6: Create PassportData aggregate and save
            PassportData passportData = createPassportData(
                command, sod, chainValidation, sodValidation, dgValidation, errors
            );
            passportDataRepository.save(passportData);

            // Step 7: Build response
            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = java.time.Duration.between(startTime, endTime).toMillis();

            PassiveAuthenticationStatus status = determineOverallStatus(
                chainValidation, sodValidation, dgValidation, errors
            );

            log.info("Passive Authentication completed with status: {} in {}ms",
                status, durationMs);

            if (status == PassiveAuthenticationStatus.VALID) {
                return PassiveAuthenticationResponse.valid(
                    passportData.getId().getId(),
                    endTime,
                    command.issuingCountry().getValue(),
                    command.documentNumber(),
                    chainValidation,
                    sodValidation,
                    dgValidation,
                    durationMs
                );
            } else {
                return PassiveAuthenticationResponse.invalid(
                    passportData.getId().getId(),
                    endTime,
                    command.issuingCountry().getValue(),
                    command.documentNumber(),
                    chainValidation,
                    sodValidation,
                    dgValidation,
                    durationMs,
                    errors
                );
            }

        } catch (Exception e) {
            log.error("Passive Authentication failed with exception", e);
            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = java.time.Duration.between(startTime, endTime).toMillis();

            PassiveAuthenticationError criticalError = PassiveAuthenticationError.critical(
                "PA_EXECUTION_ERROR",
                "Passive Authentication execution failed: " + e.getMessage()
            );
            errors.add(criticalError);

            return PassiveAuthenticationResponse.error(
                PassportDataId.newId().getId(),
                endTime,
                command.issuingCountry().getValue(),
                command.documentNumber(),
                durationMs,
                errors
            );
        }
    }

    /**
     * Retrieves Document Signer Certificate (DSC) from LDAP.
     */
    private Certificate retrieveDsc(PerformPassiveAuthenticationCommand command) {
        return certificateRepository.findBySubjectDn(command.dscSubjectDn())
            .stream()
            .filter(cert -> cert.getCertificateType() == CertificateType.DSC)
            .filter(cert -> cert.getX509Data().getSerialNumber().equals(command.dscSerialNumber()))
            .findFirst()
            .orElseThrow(() -> new PassiveAuthenticationApplicationException(
                "DSC_NOT_FOUND",
                String.format("DSC not found in LDAP: %s (Serial: %s)",
                    command.dscSubjectDn(), command.dscSerialNumber())
            ));
    }

    /**
     * Retrieves Country Signing CA (CSCA) from LDAP.
     */
    private Certificate retrieveCsca(Certificate dsc) {
        String issuerDn = dsc.getIssuerInfo().getDistinguishedName();
        return certificateRepository.findBySubjectDn(issuerDn)
            .stream()
            .filter(cert -> cert.getCertificateType() == CertificateType.CSCA)
            .findFirst()
            .orElseThrow(() -> new PassiveAuthenticationApplicationException(
                "CSCA_NOT_FOUND",
                String.format("CSCA not found in LDAP: %s", issuerDn)
            ));
    }

    /**
     * Validates certificate chain (DSC → CSCA).
     */
    private CertificateChainValidationDto validateCertificateChain(
        Certificate dsc,
        Certificate csca,
        String countryCode,
        List<PassiveAuthenticationError> errors
    ) {
        log.debug("Validating certificate chain: DSC → CSCA");

        boolean chainValid = false;
        boolean crlChecked = false;
        boolean revoked = false;
        StringBuilder validationErrors = new StringBuilder();

        try {
            // Validate trust chain
            validationAdapter.validateTrustChain(dsc, csca);
            chainValid = true;
            log.debug("Certificate chain validation passed");

        } catch (Exception e) {
            log.warn("Certificate chain validation failed", e);
            validationErrors.append("Chain validation failed: ").append(e.getMessage()).append("; ");
            errors.add(PassiveAuthenticationError.critical(
                "CHAIN_VALIDATION_FAILED",
                "Certificate chain validation failed: " + e.getMessage()
            ));
        }

        // Check CRL
        try {
            Optional<CertificateRevocationList> crl = crlRepository.findByIssuerNameAndCountry(
                csca.getSubjectInfo().getDistinguishedName(),
                countryCode
            );

            if (crl.isPresent()) {
                crlChecked = true;
                revoked = validationAdapter.isRevoked(dsc, crl.get());

                if (revoked) {
                    chainValid = false;
                    validationErrors.append("Certificate is revoked; ");
                    errors.add(PassiveAuthenticationError.critical(
                        "CERTIFICATE_REVOKED",
                        "DSC is revoked according to CRL"
                    ));
                    log.warn("DSC is revoked: {}", dsc.getSubjectInfo().getDistinguishedName());
                }
            } else {
                log.debug("No CRL found for CSCA: {}", csca.getSubjectInfo().getDistinguishedName());
            }

        } catch (Exception e) {
            log.warn("CRL check failed", e);
            validationErrors.append("CRL check failed: ").append(e.getMessage()).append("; ");
        }

        return new CertificateChainValidationDto(
            chainValid,
            dsc.getSubjectInfo().getDistinguishedName(),
            dsc.getX509Data().getSerialNumber(),
            csca.getSubjectInfo().getDistinguishedName(),
            csca.getX509Data().getSerialNumber(),
            dsc.getValidity().getNotBefore().atZone(ZoneId.systemDefault()).toLocalDateTime(),
            dsc.getValidity().getNotAfter().atZone(ZoneId.systemDefault()).toLocalDateTime(),
            crlChecked,
            revoked,
            validationErrors.length() > 0 ? validationErrors.toString() : null
        );
    }

    /**
     * Validates SOD signature using DSC public key.
     */
    private SodSignatureValidationDto validateSodSignature(
        SecurityObjectDocument sod,
        Certificate dsc,
        List<PassiveAuthenticationError> errors
    ) {
        log.debug("Validating SOD signature");

        String signatureAlgorithm = null;
        String hashAlgorithm = null;
        boolean signatureValid = false;
        StringBuilder validationErrors = new StringBuilder();

        try {
            // Extract algorithms
            signatureAlgorithm = sodParser.extractSignatureAlgorithm(sod.getEncodedData());
            hashAlgorithm = sodParser.extractHashAlgorithm(sod.getEncodedData());

            log.debug("SOD algorithms - Signature: {}, Hash: {}", signatureAlgorithm, hashAlgorithm);

            // Verify signature - Extract public key from DSC X509 certificate
            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            java.security.cert.Certificate cert = cf.generateCertificate(
                new java.io.ByteArrayInputStream(dsc.getX509Data().getCertificateBinary())
            );
            java.security.cert.X509Certificate dscX509 = (java.security.cert.X509Certificate) cert;
            PublicKey dscPublicKey = dscX509.getPublicKey();
            signatureValid = sodParser.verifySignature(sod.getEncodedData(), dscPublicKey);

            if (!signatureValid) {
                validationErrors.append("SOD signature verification failed; ");
                errors.add(PassiveAuthenticationError.critical(
                    "SOD_SIGNATURE_INVALID",
                    "SOD signature verification failed with DSC public key"
                ));
                log.warn("SOD signature invalid");
            } else {
                log.debug("SOD signature validation passed");
            }

        } catch (Exception e) {
            log.error("SOD signature validation error", e);
            validationErrors.append("SOD validation error: ").append(e.getMessage()).append("; ");
            errors.add(PassiveAuthenticationError.critical(
                "SOD_VALIDATION_ERROR",
                "SOD signature validation error: " + e.getMessage()
            ));
        }

        return new SodSignatureValidationDto(
            signatureValid,
            signatureAlgorithm,
            hashAlgorithm,
            validationErrors.length() > 0 ? validationErrors.toString() : null
        );
    }

    /**
     * Validates data group hashes against SOD.
     */
    private DataGroupValidationDto validateDataGroupHashes(
        Map<DataGroupNumber, byte[]> dataGroupsFromCommand,
        SecurityObjectDocument sod,
        List<PassiveAuthenticationError> errors
    ) {
        log.debug("Validating {} data groups", dataGroupsFromCommand.size());

        Map<DataGroupNumber, DataGroupHash> expectedHashes;
        try {
            expectedHashes = sodParser.parseDataGroupHashes(sod.getEncodedData());
        } catch (Exception e) {
            log.error("Failed to parse data group hashes from SOD", e);
            errors.add(PassiveAuthenticationError.critical(
                "SOD_PARSE_ERROR",
                "Failed to parse data group hashes from SOD: " + e.getMessage()
            ));
            return new DataGroupValidationDto(0, 0, 0, Map.of());
        }

        Map<DataGroupNumber, DataGroupValidationDto.DataGroupDetailDto> details = new HashMap<>();
        int validCount = 0;
        int invalidCount = 0;

        for (Map.Entry<DataGroupNumber, byte[]> entry : dataGroupsFromCommand.entrySet()) {
            DataGroupNumber dgNumber = entry.getKey();
            byte[] dgContent = entry.getValue();

            DataGroupHash expectedHash = expectedHashes.get(dgNumber);
            if (expectedHash == null) {
                log.warn("No expected hash found in SOD for {}", dgNumber);
                errors.add(PassiveAuthenticationError.warning(
                    "DG_HASH_MISSING",
                    String.format("No expected hash in SOD for %s", dgNumber)
                ));
                invalidCount++;
                continue;
            }

            DataGroup dataGroup = DataGroup.of(dgNumber, dgContent);
            String hashAlg = sodParser.extractHashAlgorithm(sod.getEncodedData());
            dataGroup.calculateActualHash(hashAlg);
            DataGroupHash actualHash = dataGroup.getActualHash();

            boolean hashMatches = expectedHash.equals(actualHash);

            if (hashMatches) {
                validCount++;
                log.debug("{} hash validation passed", dgNumber);
            } else {
                invalidCount++;
                log.warn("{} hash mismatch - Expected: {}, Actual: {}",
                    dgNumber, expectedHash.getValue(), actualHash.getValue());
                errors.add(PassiveAuthenticationError.critical(
                    "DG_HASH_MISMATCH",
                    String.format("%s hash mismatch (expected: %s, actual: %s)",
                        dgNumber, expectedHash.getValue(), actualHash.getValue())
                ));
            }

            details.put(dgNumber, new DataGroupValidationDto.DataGroupDetailDto(
                hashMatches,
                expectedHash.getValue(),
                actualHash.getValue()
            ));
        }

        log.info("Data group validation completed - Valid: {}, Invalid: {}",
            validCount, invalidCount);

        return new DataGroupValidationDto(
            dataGroupsFromCommand.size(),
            validCount,
            invalidCount,
            details
        );
    }

    /**
     * Creates PassportData aggregate with verification results.
     */
    private PassportData createPassportData(
        PerformPassiveAuthenticationCommand command,
        SecurityObjectDocument sod,
        CertificateChainValidationDto chainValidation,
        SodSignatureValidationDto sodValidation,
        DataGroupValidationDto dgValidation,
        List<PassiveAuthenticationError> errors
    ) {
        RequestMetadata metadata = RequestMetadata.of(
            command.requestIpAddress(),
            command.requestUserAgent(),
            command.requestedBy()
        );

        PassiveAuthenticationStatus status = determineOverallStatus(
            chainValidation, sodValidation, dgValidation, errors
        );

        // Convert data groups from command
        List<DataGroup> dataGroupList = command.dataGroups().entrySet().stream()
            .map(entry -> DataGroup.of(entry.getKey(), entry.getValue()))
            .toList();

        // Create raw request data as JSON
        String rawRequestData = String.format(
            "{\"country\":\"%s\",\"documentNumber\":\"%s\",\"dataGroupCount\":%d}",
            command.issuingCountry().getValue(),
            command.documentNumber(),
            command.dataGroups().size()
        );

        PassportData passportData = PassportData.create(
            sod,
            dataGroupList,
            metadata,
            rawRequestData
        );

        // Record verification result
        PassiveAuthenticationResult result = PassiveAuthenticationResult.withStatistics(
            chainValidation.valid(),
            sodValidation.valid(),
            dgValidation.totalGroups(),
            dgValidation.validGroups(),
            errors
        );
        passportData.recordResult(result);

        return passportData;
    }

    /**
     * Determines overall verification status based on all validation results.
     */
    private PassiveAuthenticationStatus determineOverallStatus(
        CertificateChainValidationDto chainValidation,
        SodSignatureValidationDto sodValidation,
        DataGroupValidationDto dgValidation,
        List<PassiveAuthenticationError> errors
    ) {
        if (!errors.isEmpty()) {
            boolean hasCriticalError = errors.stream()
                .anyMatch(error -> error.getSeverity() == PassiveAuthenticationError.Severity.CRITICAL);
            if (hasCriticalError) {
                return PassiveAuthenticationStatus.INVALID;
            }
        }

        if (chainValidation != null && !chainValidation.valid()) {
            return PassiveAuthenticationStatus.INVALID;
        }

        if (sodValidation != null && !sodValidation.valid()) {
            return PassiveAuthenticationStatus.INVALID;
        }

        if (dgValidation != null && dgValidation.invalidGroups() > 0) {
            return PassiveAuthenticationStatus.INVALID;
        }

        return PassiveAuthenticationStatus.VALID;
    }
}
