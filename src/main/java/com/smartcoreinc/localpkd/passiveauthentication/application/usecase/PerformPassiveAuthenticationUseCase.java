package com.smartcoreinc.localpkd.passiveauthentication.application.usecase;

import com.smartcoreinc.localpkd.passiveauthentication.application.command.PerformPassiveAuthenticationCommand;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.CrlCheckResult;
import com.smartcoreinc.localpkd.passiveauthentication.domain.service.CrlVerificationService;
import com.smartcoreinc.localpkd.passiveauthentication.infrastructure.cache.CrlCacheService;
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
import com.smartcoreinc.localpkd.passiveauthentication.domain.port.LdapCscaRepository;
import com.smartcoreinc.localpkd.passiveauthentication.domain.port.SodParserPort;
import com.smartcoreinc.localpkd.passiveauthentication.domain.repository.PassportDataRepository;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *   <li>{@link LdapCscaRepository} - Retrieve CSCA from LDAP (ICAO 9303 standard)</li>
 *   <li>{@link CertificateRevocationListRepository} - CRL checking</li>
 *   <li>{@link SodParserPort} - SOD parsing, DSC extraction, signature verification</li>
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

    private final LdapCscaRepository ldapCscaRepository;
    private final SodParserPort sodParser;
    private final PassportDataRepository passportDataRepository;
    private final CrlCacheService crlCacheService;
    private final CrlVerificationService crlVerificationService;

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
            // Step 1: Extract DSC from SOD (ICAO 9303 standard approach)
            java.security.cert.X509Certificate dscX509 = sodParser.extractDscCertificate(command.sodBytes());
            log.debug("Extracted DSC from SOD: {}", dscX509.getSubjectX500Principal().getName());

            // Step 2: Retrieve CSCA from LDAP using DSC issuer DN
            String cscaDn = dscX509.getIssuerX500Principal().getName();
            X509Certificate cscaX509 = retrieveCscaFromLdap(cscaDn);
            log.debug("Retrieved CSCA from LDAP: {}", cscaX509.getSubjectX500Principal().getName());

            // Step 3: Validate Certificate Chain (DSC → CSCA)
            CertificateChainValidationDto chainValidation = validateCertificateChainWithX509Dsc(
                dscX509, cscaX509, command.issuingCountry().getValue(), errors
            );

            // Step 4: Parse SOD and validate signature
            SecurityObjectDocument sod = SecurityObjectDocument.of(command.sodBytes());
            SodSignatureValidationDto sodValidation = validateSodSignatureWithX509Dsc(
                sod, dscX509, errors
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
     * Retrieves Country Signing CA (CSCA) from LDAP using issuer DN.
     * <p>
     * Architecture Decision: PA Module uses LDAP only for CSCA lookup, not DBMS.
     * This follows ICAO 9303 Part 11 standard where PKD (Public Key Directory)
     * is the authoritative source for certificate validation.
     * <p>
     * DN Format Handling: This method tries multiple DN formats because:
     * <ul>
     *   <li>DSC Issuer DN may be in RFC 2253 format (CN=...,O=...,C=...)</li>
     *   <li>CSCA Subject DN in LDAP may be in RFC 1779 format (C=...,O=...,CN=...)</li>
     *   <li>Both formats represent the same certificate but in different order</li>
     * </ul>
     *
     * @param issuerDn DSC issuer DN (should match CSCA subject DN)
     * @return CSCA X.509 certificate from LDAP
     * @throws PassiveAuthenticationApplicationException if CSCA not found in LDAP
     */
    private X509Certificate retrieveCscaFromLdap(String issuerDn) {
        log.debug("Looking up CSCA from LDAP with DN: {}", issuerDn);

        // Try exact match first
        var csca = ldapCscaRepository.findBySubjectDn(issuerDn);
        if (csca.isPresent()) {
            log.debug("Found CSCA with exact DN match");
            return csca.get();
        }

        // Try normalized DN (RFC 2253 format)
        try {
            javax.security.auth.x500.X500Principal principal =
                new javax.security.auth.x500.X500Principal(issuerDn);
            String normalizedDn = principal.getName(javax.security.auth.x500.X500Principal.RFC2253);

            log.debug("Trying normalized DN (RFC 2253): {}", normalizedDn);
            csca = ldapCscaRepository.findBySubjectDn(normalizedDn);
            if (csca.isPresent()) {
                log.debug("Found CSCA with normalized DN match");
                return csca.get();
            }

            // Try canonical format (reverse order)
            String canonicalDn = principal.getName(javax.security.auth.x500.X500Principal.CANONICAL);
            log.debug("Trying canonical DN: {}", canonicalDn);
            csca = ldapCscaRepository.findBySubjectDn(canonicalDn);
            if (csca.isPresent()) {
                log.debug("Found CSCA with canonical DN match");
                return csca.get();
            }

        } catch (IllegalArgumentException e) {
            log.warn("Failed to normalize DN: {}", e.getMessage());
        }

        throw new PassiveAuthenticationApplicationException(
            "CSCA_NOT_FOUND",
            String.format("CSCA not found in LDAP for issuer DN: %s. " +
                "Ensure CSCA is uploaded to LDAP before performing PA verification.", issuerDn)
        );
    }

    /**
     * Validates certificate chain (DSC → CSCA) using X509Certificates.
     * <p>
     * This is the ICAO 9303 standard approach:
     * <ul>
     *   <li>DSC is extracted from SOD (not from LDAP/DBMS)</li>
     *   <li>CSCA is retrieved from LDAP (not from DBMS)</li>
     * </ul>
     */
    private CertificateChainValidationDto validateCertificateChainWithX509Dsc(
        X509Certificate dscX509,
        X509Certificate cscaX509,
        String countryCode,
        List<PassiveAuthenticationError> errors
    ) {
        log.debug("Validating certificate chain: DSC (from SOD) → CSCA (from LDAP)");

        boolean chainValid = false;
        boolean crlChecked = false;
        boolean revoked = false;
        StringBuilder validationErrors = new StringBuilder();

        // Extract DSC information
        String dscSubjectDn = dscX509.getSubjectX500Principal().getName();
        String dscSerialNumber = dscX509.getSerialNumber().toString(16).toUpperCase();
        LocalDateTime dscNotBefore = dscX509.getNotBefore().toInstant()
            .atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime dscNotAfter = dscX509.getNotAfter().toInstant()
            .atZone(ZoneId.systemDefault()).toLocalDateTime();

        // Extract CSCA information
        String cscaSubjectDn = cscaX509.getSubjectX500Principal().getName();
        String cscaSerialNumber = cscaX509.getSerialNumber().toString(16).toUpperCase();

        try {
            // Validate trust chain: DSC signature with CSCA public key
            dscX509.verify(cscaX509.getPublicKey());
            chainValid = true;
            log.debug("Certificate chain validation passed (DSC verified with CSCA public key)");

        } catch (Exception e) {
            log.warn("Certificate chain validation failed", e);
            validationErrors.append("Trust chain validation failed: ").append(e.getMessage()).append("; ");
            errors.add(PassiveAuthenticationError.critical(
                "CHAIN_VALIDATION_FAILED",
                "Certificate chain validation failed: " + e.getMessage()
            ));
        }

        // Phase 4.12: CRL Check (RFC 5280, ICAO 9303 Part 12)
        // Check if DSC certificate is revoked by querying CRL from LDAP/Cache
        CrlCheckResult crlCheckResult = performCrlCheck(dscX509, cscaX509, cscaSubjectDn, countryCode);
        crlChecked = !crlCheckResult.hasCrlVerificationFailed();
        revoked = crlCheckResult.isCertificateRevoked();

        // CRL 상태 및 상세 메시지 설정
        String crlStatus = crlCheckResult.getStatus().name();
        String crlMessage = buildCrlMessage(crlCheckResult, cscaSubjectDn, countryCode);

        if (crlCheckResult.isCertificateRevoked()) {
            log.warn("DSC certificate is REVOKED: serial={}, revocationDate={}, reason={}",
                dscSerialNumber, crlCheckResult.getRevocationDate(), crlCheckResult.getRevocationReasonText());
            validationErrors.append("Certificate is revoked: ")
                .append(crlCheckResult.getRevocationReasonText())
                .append(" (").append(crlCheckResult.getRevocationDate()).append("); ");
            errors.add(PassiveAuthenticationError.critical(
                "CERTIFICATE_REVOKED",
                String.format("DSC certificate is revoked: %s (Date: %s)",
                    crlCheckResult.getRevocationReasonText(), crlCheckResult.getRevocationDate())
            ));
            chainValid = false;  // Revoked certificate invalidates chain
        } else if (crlCheckResult.hasCrlVerificationFailed()) {
            log.warn("CRL verification failed: {}", crlCheckResult.getErrorMessage());
            validationErrors.append("CRL check failed: ").append(crlCheckResult.getErrorMessage()).append("; ");
            // Note: CRL verification failure does not invalidate the chain, but is logged
        } else {
            log.debug("CRL check passed: DSC certificate is not revoked");
        }

        return new CertificateChainValidationDto(
            chainValid,
            dscSubjectDn,
            dscSerialNumber,
            cscaSubjectDn,
            cscaSerialNumber,
            dscNotBefore,
            dscNotAfter,
            crlChecked,
            revoked,
            crlStatus,
            crlMessage,
            validationErrors.length() > 0 ? validationErrors.toString() : null
        );
    }

    /**
     * Validates SOD signature using DSC X509 certificate extracted from SOD.
     * <p>
     * This is the ICAO 9303 standard approach using the recommended
     * verifySignature(X509Certificate) method (like sod_example).
     */
    private SodSignatureValidationDto validateSodSignatureWithX509Dsc(
        SecurityObjectDocument sod,
        java.security.cert.X509Certificate dscX509,
        List<PassiveAuthenticationError> errors
    ) {
        log.debug("Validating SOD signature with DSC X509 certificate");

        String signatureAlgorithm = null;
        String hashAlgorithm = null;
        boolean signatureValid = false;
        StringBuilder validationErrors = new StringBuilder();

        try {
            // Extract algorithms
            signatureAlgorithm = sodParser.extractSignatureAlgorithm(sod.getEncodedData());
            hashAlgorithm = sodParser.extractHashAlgorithm(sod.getEncodedData());

            log.debug("SOD algorithms - Signature: {}, Hash: {}", signatureAlgorithm, hashAlgorithm);

            // Verify signature using X509Certificate (recommended approach - matches sod_example)
            signatureValid = sodParser.verifySignature(sod.getEncodedData(), dscX509);

            if (!signatureValid) {
                validationErrors.append("SOD signature verification failed; ");
                errors.add(PassiveAuthenticationError.critical(
                    "SOD_SIGNATURE_INVALID",
                    "SOD signature verification failed with DSC certificate"
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

    /**
     * Performs CRL (Certificate Revocation List) check for the DSC certificate.
     *
     * <p>Implementation follows RFC 5280 and ICAO 9303 Part 12:</p>
     * <ol>
     *   <li>Retrieve CRL from LDAP/Cache using CSCA DN and country code</li>
     *   <li>Verify CRL signature using CSCA public key</li>
     *   <li>Verify CRL freshness (thisUpdate, nextUpdate)</li>
     *   <li>Check if DSC serial number is in revoked list</li>
     * </ol>
     *
     * <p>Architecture: Two-tier caching strategy</p>
     * <ul>
     *   <li>Tier 1: In-Memory Cache (fast, volatile)</li>
     *   <li>Tier 2: Database Cache (persistent, fallback)</li>
     *   <li>Tier 3: LDAP (primary source, slow)</li>
     * </ul>
     *
     * @param dscX509 DSC certificate (to be checked)
     * @param cscaX509 CSCA certificate (CRL issuer)
     * @param cscaSubjectDn CSCA Subject DN
     * @param countryCode ISO 3166-1 alpha-2 country code
     * @return CrlCheckResult with verification outcome
     * @since Phase 4.12
     */
    private CrlCheckResult performCrlCheck(
        X509Certificate dscX509,
        X509Certificate cscaX509,
        String cscaSubjectDn,
        String countryCode
    ) {
        log.debug("Starting CRL check for DSC certificate: serial={}", dscX509.getSerialNumber());

        try {
            // Step 1: Retrieve CRL from LDAP/Cache (two-tier caching)
            java.util.Optional<java.security.cert.X509CRL> crlOpt =
                crlCacheService.getCrl(cscaSubjectDn, countryCode);

            if (crlOpt.isEmpty()) {
                log.debug("CRL not available for CSCA: {}, country: {}", cscaSubjectDn, countryCode);
                return CrlCheckResult.unavailable(
                    String.format("CRL not found in LDAP for CSCA: %s (country: %s)", cscaSubjectDn, countryCode)
                );
            }

            java.security.cert.X509CRL crl = crlOpt.get();
            log.debug("CRL retrieved successfully. Issuer: {}, thisUpdate: {}, nextUpdate: {}",
                crl.getIssuerX500Principal().getName(), crl.getThisUpdate(), crl.getNextUpdate());

            // Step 2-4: Verify CRL and check revocation status
            return crlVerificationService.verifyCertificate(dscX509, crl, cscaX509);

        } catch (Exception e) {
            log.error("CRL check failed with exception: serial={}", dscX509.getSerialNumber(), e);
            return CrlCheckResult.invalid(
                "CRL verification failed: " + e.getMessage()
            );
        }
    }

    /**
     * Builds a user-friendly CRL status message based on the check result.
     *
     * @param crlResult CRL check result
     * @param cscaSubjectDn CSCA Subject DN (for context)
     * @param countryCode Country code (for context)
     * @return Human-readable CRL status message
     */
    private String buildCrlMessage(CrlCheckResult crlResult, String cscaSubjectDn, String countryCode) {
        return switch (crlResult.getStatus()) {
            case VALID -> "CRL 확인 완료 - DSC 인증서가 폐기되지 않음";
            case REVOKED -> String.format("인증서 폐기됨 - 사유: %s, 폐기일: %s",
                crlResult.getRevocationReasonText(),
                crlResult.getRevocationDate() != null ? crlResult.getRevocationDate().toString() : "알 수 없음");
            case CRL_UNAVAILABLE -> String.format("LDAP에서 해당 CSCA의 CRL을 찾을 수 없음 (CSCA: %s, 국가: %s)",
                extractCn(cscaSubjectDn), countryCode);
            case CRL_EXPIRED -> "CRL이 만료됨 - nextUpdate 시간이 지남";
            case CRL_INVALID -> String.format("CRL 검증 실패: %s",
                crlResult.getErrorMessage() != null ? crlResult.getErrorMessage() : "서명 검증 실패");
        };
    }

    /**
     * Extracts CN (Common Name) from a DN string for display purposes.
     *
     * @param dn Distinguished Name (e.g., "CN=CSCA003,OU=MOFA,O=Government,C=KR")
     * @return CN value or original DN if CN not found
     */
    private String extractCn(String dn) {
        if (dn == null || dn.isEmpty()) {
            return "Unknown";
        }
        // Simple extraction: find CN= and extract until comma
        int cnStart = dn.indexOf("CN=");
        if (cnStart == -1) {
            cnStart = dn.indexOf("cn=");
        }
        if (cnStart == -1) {
            return dn;  // Return full DN if no CN found
        }
        int valueStart = cnStart + 3;
        int valueEnd = dn.indexOf(',', valueStart);
        if (valueEnd == -1) {
            valueEnd = dn.length();
        }
        return dn.substring(valueStart, valueEnd);
    }
}
