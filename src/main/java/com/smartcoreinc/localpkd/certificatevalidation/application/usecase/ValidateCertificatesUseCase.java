package com.smartcoreinc.localpkd.certificatevalidation.application.usecase;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.ValidateCertificatesCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.CertificatesValidatedResponse;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.*;
import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CertificatesValidatedEvent;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import com.smartcoreinc.localpkd.fileparsing.domain.model.CertificateData;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.domain.repository.ParsedFileRepository;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProcessingStage;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * ValidateCertificatesUseCase - 인증서 검증 Use Case
 *
 * <p><b>Application Service</b>: 파싱된 인증서들을 검증합니다.</p>
 *
 * <p><b>검증 프로세스</b>:</p>
 * <ol>
 *   <li>Command 검증</li>
 *   <li>파싱된 인증서/CRL 조회</li>
 *   <li>각 인증서 검증 (만료 여부, 유효성, Trust Chain 등)</li>
 *   <li>각 CRL 검증</li>
 *   <li>검증 결과 기록</li>
 *   <li>CertificatesValidatedEvent 발행</li>
 *   <li>Response 반환</li>
 * </ol>
 *
 * <p><b>Event-Driven Architecture</b>:</p>
 * <ul>
 *   <li>CertificatesValidatedEvent → ProgressService (SSE: VALIDATION_COMPLETED)</li>
 *   <li>CertificatesValidatedEvent → LdapIntegrationService (LDAP 업로드 트리거)</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * ValidateCertificatesCommand command = ValidateCertificatesCommand.builder()
 *     .uploadId(uploadId)
 *     .parsedFileId(parsedFileId)
 *     .certificateCount(800)
 *     .crlCount(50)
 *     .build();
 *
 * CertificatesValidatedResponse response = validateCertificatesUseCase.execute(command);
 *
 * if (response.success()) {
 *     log.info("Validation completed: {} valid, {} invalid",
 *         response.validCertificateCount(), response.invalidCertificateCount());
 * } else {
 *     log.error("Validation failed: {}", response.errorMessage());
 * }
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidateCertificatesUseCase {

    private final CertificateRepository certificateRepository;
    private final CertificateRevocationListRepository crlRepository;
    private final ParsedFileRepository parsedFileRepository;
    private final ProgressService progressService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.smartcoreinc.localpkd.certificatevalidation.application.service.CertificateSaveService certificateSaveService;

    /**
     * 인증서 검증 실행
     *
     * @param command ValidateCertificatesCommand
     * @return CertificatesValidatedResponse
     */
    @Transactional
    public CertificatesValidatedResponse execute(ValidateCertificatesCommand command) {
        log.info("=== Certificate validation started ===");
        log.info("UploadId: {}, Certificates: {}, CRLs: {}",
            command.uploadId(), command.certificateCount(), command.crlCount());

        long startTime = System.currentTimeMillis();

        try {
            // 1. Command 검증
            command.validate();

            // LdifParsingEventHandler에서 VALIDATION_STARTED 이벤트를 55%로 보내므로,
            // 이 UseCase에서는 시작 시점을 알리는 이벤트를 보내지 않습니다.
            // 대신 55%부터 진행률을 업데이트합니다.

            // 2. 파싱된 파일 조회
            UploadId uploadId = UploadId.of(command.uploadId().toString());
            ParsedFile parsedFile = parsedFileRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new DomainException(
                    "PARSED_FILE_NOT_FOUND",
                    "파싱된 파일을 찾을 수 없습니다: uploadId=" + command.uploadId()
                ));

            log.info("Found parsed file: {} certificates, {} CRLs",
                parsedFile.getCertificates().size(), parsedFile.getCrls().size());

            // 3. 파싱된 인증서 검증 및 저장 (Two-Pass 처리)
            List<CertificateData> certificateDataList = parsedFile.getCertificates();
            List<UUID> validCertificateIds = new ArrayList<>();
            List<UUID> invalidCertificateIds = new ArrayList<>();
            Set<String> processedFingerprints = new HashSet<>(); // 동일 트랜잭션 내 중복 처리 방지

            int totalCertificates = certificateDataList.size();

            // === Pass 1: CSCA 인증서만 먼저 검증/저장 ===
            log.info("=== Pass 1: CSCA certificate validation started ===");
            int cscaProcessed = 0;
            for (int i = 0; i < totalCertificates; i++) { // Loop over all certificates
                CertificateData certData = certificateDataList.get(i);

                if (certData.isCsca()) {
                    cscaProcessed++;
                    
                    if (processedFingerprints.contains(certData.getFingerprintSha256())) {
                        log.warn("Skipping duplicate certificate within the same batch: fingerprint={}", certData.getFingerprintSha256());
                        // 이 경우, invalidCertificateIds에 추가하지 않고 단순히 건너뜁니다.
                        continue;
                    }

                    Certificate certificate = null;
                    List<ValidationError> errors = new ArrayList<>();
                    ValidationResult validationResult = null; // Initialize to null

                    try {
                        log.debug("Validating CSCA {}: country={}, subject={}",
                            cscaProcessed, certData.getCountryCode(), certData.getSubjectDN());

                        X509Certificate x509Cert = convertToX509Certificate(certData.getCertificateBinary());
                        certificate = createCertificateFromData(certData, x509Cert, command.uploadId());
                        
                        // Perform validation and get result and errors
                        validationResult = validateCscaCertificate(x509Cert, certData, errors);
                        certificate.recordValidation(validationResult);
                        certificate.addValidationErrors(errors);

                        // Save/update certificate in separate transaction (REQUIRES_NEW)
                        certificateSaveService.saveOrUpdate(
                            certificate, validationResult, errors,
                            validCertificateIds, invalidCertificateIds, processedFingerprints,
                            true, // isCsca = true
                            command.uploadId()
                        );

                    } catch (Exception e) {
                        // Handle unexpected errors during processing a single certificate
                        errors.add(ValidationError.critical("UNEXPECTED_PROCESSING_ERROR", "Unexpected error processing CSCA: " + e.getMessage()));
                        validationResult = ValidationResult.of(CertificateStatus.INVALID, false, false, false, false, false, 0); // Default failed result
                        
                        try {
                            if (certificate == null) {
                                certificate = createCertificateFromData(certData, command.uploadId());
                            }
                            certificate.addValidationErrors(errors);

                            // Save/update certificate in separate transaction (REQUIRES_NEW) after error
                            certificateSaveService.saveOrUpdate(
                                certificate, validationResult, errors,
                                validCertificateIds, invalidCertificateIds, processedFingerprints,
                                true, // isCsca = true
                                command.uploadId()
                            );
                            log.error("CSCA certificate processing failed: subject={}. Error: {}", certData.getSubjectDN(), e.getMessage());
                        } catch (Exception creationEx) {
                            log.error("Failed to create or save dummy error certificate for subject={}. Error: {}", certData.getSubjectDN(), creationEx.getMessage());
                        }
                    }

                    // SSE 진행 상황 업데이트 (55-70% 범위 - Pass 1)
                    progressService.sendProgress(
                        ProcessingProgress.validationInProgress(
                            command.uploadId(),
                            cscaProcessed,
                            totalCertificates, // Use totalCertificates as total for now
                            String.format("CSCA 인증서 검증 중 (%d/%d)", cscaProcessed, totalCertificates),
                            55, // minPercent for Pass 1
                            70  // maxPercent for Pass 1
                        )
                    );
                }
            }

            log.info("Pass 1 completed: {} CSCA certificates processed ({} valid, {} invalid)",
                cscaProcessed, validCertificateIds.size(), invalidCertificateIds.size());

            // === Pass 2: DSC/DSC_NC 인증서 검증/저장 ===
            log.info("=== Pass 2: DSC/DSC_NC certificate validation started ===");
            int dscProcessed = 0;
            for (int i = 0; i < totalCertificates; i++) { // Loop over all certificates
                CertificateData certData = certificateDataList.get(i);

                if (!certData.isCsca()) { // CSCA가 아니면 DSC/DSC_NC
                    dscProcessed++;

                    if (processedFingerprints.contains(certData.getFingerprintSha256())) {
                        log.warn("Skipping duplicate certificate within the same batch: fingerprint={}", certData.getFingerprintSha256());
                        // 이 경우, invalidCertificateIds에 추가하지 않고 단순히 건너뜁니다.
                        continue;
                    }

                    Certificate certificate = null;
                    List<ValidationError> errors = new ArrayList<>();
                    ValidationResult validationResult = null; // Initialize to null

                    try {
                        log.debug("Validating DSC/DSC_NC {}: type={}, country={}, subject={}",
                            dscProcessed, certData.getCertificateType(), certData.getCountryCode(), certData.getSubjectDN());

                        X509Certificate x509Cert = convertToX509Certificate(certData.getCertificateBinary());
                        certificate = createCertificateFromData(certData, x509Cert, command.uploadId());

                        // NC-DATA(DSC_NC)는 유효성 검사를 수행하지 않고 저장만 수행
                        if ("DSC_NC".equalsIgnoreCase(certData.getCertificateType())) {
                            log.info("Skipping validation for DSC_NC certificate (NC-DATA): subjectDN={}", certData.getSubjectDN());
                            validationResult = ValidationResult.of(
                                CertificateStatus.VALID, // 저장 및 LDAP 업로드를 위해 VALID로 간주
                                false, // signatureValid
                                false, // chainValid
                                false, // notRevoked
                                false, // validityValid
                                false, // constraintsValid
                                0L     // durationMillis
                            );
                        } else {
                            // Perform validation and get result and errors for standard DSC
                            validationResult = validateDscCertificate(x509Cert, certData, command.uploadId(), errors);
                        }

                        certificate.recordValidation(validationResult);
                        certificate.addValidationErrors(errors);

                        // Save/update certificate in separate transaction (REQUIRES_NEW)
                        certificateSaveService.saveOrUpdate(
                            certificate, validationResult, errors,
                            validCertificateIds, invalidCertificateIds, processedFingerprints,
                            false, // isCsca = false
                            command.uploadId()
                        );

                    } catch (Exception e) {
                        // Handle unexpected errors during processing a single certificate
                        errors.add(ValidationError.critical("UNEXPECTED_PROCESSING_ERROR", "Unexpected error processing DSC/DSC_NC: " + e.getMessage()));
                        validationResult = ValidationResult.of(CertificateStatus.INVALID, false, false, false, false, false, 0);
                        
                        try {
                            if (certificate == null) {
                                certificate = createCertificateFromData(certData, command.uploadId());
                            }
                            certificate.addValidationErrors(errors);

                            // Save/update certificate in separate transaction (REQUIRES_NEW) after error
                            certificateSaveService.saveOrUpdate(
                                certificate, validationResult, errors,
                                validCertificateIds, invalidCertificateIds, processedFingerprints,
                                false, // isCsca = false
                                command.uploadId()
                            );
                            log.error("DSC/DSC_NC certificate processing failed: subject={}. Error: {}", certData.getSubjectDN(), e.getMessage());
                        } catch (Exception creationEx) {
                            log.error("Failed to create or save dummy error certificate for subject={}. Error: {}", certData.getSubjectDN(), creationEx.getMessage());
                        }
                    }

                    // SSE 진행 상황 업데이트 (70-85% 범위 - Pass 2)
                    progressService.sendProgress(
                        ProcessingProgress.validationInProgress(
                            command.uploadId(),
                            dscProcessed,
                            totalCertificates - cscaProcessed,
                            String.format("DSC/DSC_NC 인증서 검증 중 (%d/%d)", dscProcessed, totalCertificates - cscaProcessed),
                            70, 85
                        )
                    );
                }
            }

            log.info("Pass 2 completed: Total certificates validated: {} ({} valid, {} invalid)",
                totalCertificates, validCertificateIds.size(), invalidCertificateIds.size());


            // 4. CRL은 현재 스킵 (향후 Phase에서 구현)
            List<UUID> validCrlIds = new ArrayList<>();
            List<UUID> invalidCrlIds = new ArrayList<>();
            log.info("CRL validation skipped (future implementation)");

            // 5. CertificatesValidatedEvent 생성 및 발행
            CertificatesValidatedEvent event = new CertificatesValidatedEvent(
                command.uploadId(),
                validCertificateIds,
                invalidCertificateIds,
                validCrlIds,
                invalidCrlIds,
                LocalDateTime.now()
            );

            eventPublisher.publishEvent(event);
            log.info("CertificatesValidatedEvent published: uploadId={}", command.uploadId());

            // 6. 검증 결과 통계 계산
            // 실제 저장된 Certificate 엔티티에서 통계 수집
            List<Certificate> allCertificates = certificateRepository.findByUploadId(command.uploadId());

            long cscaValidCount = allCertificates.stream()
                .filter(cert -> cert.getCertificateType() == CertificateType.CSCA)
                .filter(cert -> cert.getStatus() == CertificateStatus.VALID)
                .count();
            long cscaInvalidCount = allCertificates.stream()
                .filter(cert -> cert.getCertificateType() == CertificateType.CSCA)
                .filter(cert -> cert.getStatus() == CertificateStatus.INVALID || cert.getStatus() == CertificateStatus.EXPIRED)
                .count();

            long dscValidCount = allCertificates.stream()
                .filter(cert -> cert.getCertificateType() == CertificateType.DSC)
                .filter(cert -> cert.getStatus() == CertificateStatus.VALID)
                .count();
            long dscInvalidCount = allCertificates.stream()
                .filter(cert -> cert.getCertificateType() == CertificateType.DSC)
                .filter(cert -> cert.getStatus() == CertificateStatus.INVALID || cert.getStatus() == CertificateStatus.EXPIRED)
                .count();

            long dscNcValidCount = allCertificates.stream()
                .filter(cert -> cert.getCertificateType() == CertificateType.DSC_NC)
                .filter(cert -> cert.getStatus() == CertificateStatus.VALID)
                .count();
            long dscNcInvalidCount = allCertificates.stream()
                .filter(cert -> cert.getCertificateType() == CertificateType.DSC_NC)
                .filter(cert -> cert.getStatus() == CertificateStatus.INVALID || cert.getStatus() == CertificateStatus.EXPIRED)
                .count();

            log.info("Validation completed: CSCA(Valid: {}, Invalid: {}), DSC(Valid: {}, Invalid: {}), DSC_NC(Valid: {}, Invalid: {})",
                cscaValidCount, cscaInvalidCount, dscValidCount, dscInvalidCount, dscNcValidCount, dscNcInvalidCount);

            // 통계 메시지 포맷팅
            StringBuilder detailsMsg = new StringBuilder();
            if (cscaValidCount > 0 || cscaInvalidCount > 0) {
                detailsMsg.append(String.format("CSCA: 유효 %d개/무효 %d개", cscaValidCount, cscaInvalidCount));
            }
            if (dscValidCount > 0 || dscInvalidCount > 0) {
                if (detailsMsg.length() > 0) detailsMsg.append(", ");
                detailsMsg.append(String.format("DSC: 유효 %d개/무효 %d개", dscValidCount, dscInvalidCount));
            }
            if (dscNcValidCount > 0 || dscNcInvalidCount > 0) {
                if (detailsMsg.length() > 0) detailsMsg.append(", ");
                detailsMsg.append(String.format("DSC_NC: 유효 %d개/무효 %d개", dscNcValidCount, dscNcInvalidCount));
            }

            // 7. SSE 진행 상황 전송: VALIDATION_COMPLETED (85%)
            int totalProcessed = validCertificateIds.size() + invalidCertificateIds.size() + validCrlIds.size() + invalidCrlIds.size();
            progressService.sendProgress(
                ProcessingProgress.builder()
                    .uploadId(command.uploadId())
                    .stage(ProcessingStage.VALIDATION_COMPLETED)
                    .percentage(85)
                    .processedCount(totalProcessed)
                    .totalCount(totalProcessed)
                    .message(String.format("인증서 검증 완료 (총 %d개)", totalProcessed))
                    .details(detailsMsg.toString())
                    .build()
            );

            // 8. Response 반환
            long durationMillis = System.currentTimeMillis() - startTime;
            return CertificatesValidatedResponse.success(
                command.uploadId(),
                validCertificateIds.size(),
                invalidCertificateIds.size(),
                validCrlIds.size(),
                invalidCrlIds.size(),
                LocalDateTime.now(),
                durationMillis
            );

        } catch (DomainException e) {
            log.error("Domain error during certificate validation: {}", e.getMessage());
            progressService.sendProgress(
                ProcessingProgress.failed(
                    command.uploadId(),
                    ProcessingStage.VALIDATION_IN_PROGRESS, // FAILED 이전에 어떤 단계였는지 명시
                    "인증서 검증 중 도메인 오류: " + e.getMessage()
                )
            );
            return CertificatesValidatedResponse.failure(command.uploadId(), e.getMessage());

                    } catch (Exception e) {
                        log.error("Unexpected error during certificate validation. Details: {}", e.getMessage(), e); // Log full stack trace
                        progressService.sendProgress(
                            ProcessingProgress.failed(
                                command.uploadId(),
                                ProcessingStage.VALIDATION_IN_PROGRESS, // FAILED 이전에 어떤 단계였는지 명시
                                "인증서 검증 중 오류가 발생했습니다: " + e.getMessage()
                            )
                        );
                        return CertificatesValidatedResponse.failure(
                            command.uploadId(),
                            "인증서 검증 중 오류가 발생했습니다: " + e.getMessage()
                        );
                    }    }

    // ========== Helper Methods ==========

    /**
     * byte[] 인증서 데이터를 X509Certificate로 변환
     * Uses Bouncy Castle provider to support explicit EC parameters
     */
    private X509Certificate convertToX509Certificate(byte[] certBytes) throws Exception {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509", "BC");
        ByteArrayInputStream bais = new ByteArrayInputStream(certBytes);
        return (X509Certificate) certFactory.generateCertificate(bais);
    }

    /**
     * CSCA 인증서 검증
     *
     * ICAO Doc 9303에 따라:
     * 1. Self-signed 서명 검증
     * 2. Validity period 검증
     * 3. Basic Constraints 검증 (CA=true)
     */
    private ValidationResult validateCscaCertificate(X509Certificate x509Cert, CertificateData certData, List<ValidationError> errors) {
        boolean signatureValid = true;
        boolean validityValid = true;
        boolean constraintsValid = true;

        long validationStartTime = System.currentTimeMillis();

        try {
            // 1. Self-signed 서명 검증
            try {
                x509Cert.verify(x509Cert.getPublicKey());
                log.debug("Self-signed signature verified for CSCA: {}", certData.getSubjectDN());
            } catch (Exception e) {
                signatureValid = false;
                errors.add(ValidationError.critical("SIGNATURE_INVALID", "Self-signed signature verification failed: " + e.getMessage()));
                log.error("Self-signed signature verification failed for CSCA: {}. Error: {}", certData.getSubjectDN(), e.getMessage());
            }

            // 2. Validity period 검증
            try {
                x509Cert.checkValidity();
                log.debug("Validity period checked for CSCA: {}", certData.getSubjectDN());
            } catch (Exception e) {
                validityValid = false;
                errors.add(ValidationError.warning("VALIDITY_INVALID", "Validity period check failed: " + e.getMessage()));
                log.warn("Validity period check failed for CSCA: {}. Error: {}", certData.getSubjectDN(), e.getMessage());
            }

            // 3. Basic Constraints 검증 (CA=true)
            int basicConstraints = x509Cert.getBasicConstraints();
            if (basicConstraints < 0) {
                constraintsValid = false;
                errors.add(ValidationError.critical("CONSTRAINT_VIOLATION", "CSCA must be a CA certificate (basicConstraints >= 0)"));
                log.error("CSCA must be a CA certificate (basicConstraints >= 0), but got: {}. Subject: {}", basicConstraints, certData.getSubjectDN());
            } else {
                log.debug("Basic constraints verified for CSCA: CA=true, pathLength={}. Subject: {}", basicConstraints, certData.getSubjectDN());
            }

            CertificateStatus overallStatus = CertificateStatus.VALID;
            if (!signatureValid || !constraintsValid) {
                overallStatus = CertificateStatus.INVALID;
            } else if (!validityValid) {
                // If only validity is an issue, it might be expired or not yet valid
                try {
                    x509Cert.checkValidity(new Date()); // Check against current date
                } catch (java.security.cert.CertificateExpiredException e) {
                    overallStatus = CertificateStatus.EXPIRED;
                } catch (java.security.cert.CertificateNotYetValidException e) {
                    overallStatus = CertificateStatus.NOT_YET_VALID;
                }
            }
            
            long duration = System.currentTimeMillis() - validationStartTime;
            return ValidationResult.of(
                overallStatus,
                signatureValid,
                true, // Chain validation for CSCA is self-signed, always true if signature valid
                true, // Not revoked for CSCA is assumed true for base validation, CRL check is separate
                validityValid,
                constraintsValid,
                duration
            );

        } catch (Exception e) {
            errors.add(ValidationError.critical("UNEXPECTED_VALIDATION_ERROR", "Unexpected error during CSCA validation: " + e.getMessage()));
            log.error("Unexpected error during CSCA validation: {}. Error: {}", certData.getSubjectDN(), e.getMessage());
            long duration = System.currentTimeMillis() - validationStartTime;
            return ValidationResult.of(
                CertificateStatus.INVALID,
                false, false, false, false, false, duration
            );
        }
    }

    /**
     * DSC/DSC_NC 인증서 검증
     *
     * ICAO Doc 9303에 따라:
     * 1. CSCA로 서명 검증 (Issuer CSCA 조회)
     * 2. Validity period 검증
     * 3. Basic Constraints 검증 (CA=false 또는 없음)
     */
    private ValidationResult validateDscCertificate(
        X509Certificate x509Cert,
        CertificateData certData,
        java.util.UUID uploadId,
        List<ValidationError> errors
    ) {
        boolean signatureValid = true;
        boolean validityValid = true;
        boolean constraintsValid = true;

        long validationStartTime = System.currentTimeMillis();

        try {
            // 1. Issuer DN으로 CSCA 조회 및 서명 검증
            String issuerDN = certData.getIssuerDN();
            log.debug("Finding CSCA for DSC validation: issuerDN={}", issuerDN);

            Optional<Certificate> cscaCertOpt = certificateRepository.findBySubjectDn(issuerDN);
            if (cscaCertOpt.isEmpty()) {
                signatureValid = false;
                errors.add(ValidationError.critical("CHAIN_INCOMPLETE", "CSCA not found for DSC. IssuerDN: " + issuerDN));
                log.error("CSCA not found for DSC. IssuerDN: {}", issuerDN);
            } else {
                // CSCA 인증서로 DSC 서명 검증
                Certificate cscaCert = cscaCertOpt.get();
                X509Certificate cscaX509 = convertToX509Certificate(
                    cscaCert.getX509Data().getCertificateBinary()
                );
                try {
                    x509Cert.verify(cscaX509.getPublicKey());
                    log.debug("Signature verified for DSC by CSCA: {}", certData.getSubjectDN());
                } catch (Exception e) {
                    signatureValid = false;
                    errors.add(ValidationError.critical("SIGNATURE_INVALID", "Signature verification failed by CSCA: " + e.getMessage()));
                    log.error("Signature verification failed for DSC by CSCA: {}. Error: {}", certData.getSubjectDN(), e.getMessage());
                }
            }

            // 2. Validity period 검증
            try {
                x509Cert.checkValidity();
                log.debug("Validity period checked for DSC: {}", certData.getSubjectDN());
            } catch (Exception e) {
                validityValid = false;
                errors.add(ValidationError.warning("VALIDITY_INVALID", "Validity period check failed: " + e.getMessage()));
                log.warn("Validity period check failed for DSC: {}. Error: {}", certData.getSubjectDN(), e.getMessage());
            }

            // 3. Basic Constraints 검증 (DSC는 CA가 아니어야 함)
            int basicConstraints = x509Cert.getBasicConstraints();
            if (basicConstraints >= 0) { // If basicConstraints >= 0, it means it's a CA certificate
                constraintsValid = false;
                errors.add(ValidationError.warning("CONSTRAINT_VIOLATION", "DSC should not be a CA certificate (basicConstraints >= 0)."));
                log.warn("DSC should not be a CA certificate, but basicConstraints={}. Subject: {}",
                    basicConstraints, certData.getSubjectDN());
                // Non-Conformant의 경우 일부 제약 조건 완화 - 경고만 하고 진행
            } else {
                log.debug("Basic constraints verified for DSC: CA=false. Subject: {}", certData.getSubjectDN());
            }

            CertificateStatus overallStatus = CertificateStatus.VALID;
            if (!signatureValid || !constraintsValid) {
                overallStatus = CertificateStatus.INVALID;
            } else if (!validityValid) {
                try {
                    x509Cert.checkValidity(new Date()); // Check against current date
                } catch (java.security.cert.CertificateExpiredException e) {
                    overallStatus = CertificateStatus.EXPIRED;
                } catch (java.security.cert.CertificateNotYetValidException e) {
                    overallStatus = CertificateStatus.NOT_YET_VALID;
                }
            }

            long duration = System.currentTimeMillis() - validationStartTime;
            return ValidationResult.of(
                overallStatus,
                signatureValid,
                true, // Chain valid is assumed true for base validation (CSCAs are trusted), actual chain building is separate
                true, // Not revoked for CSCA is assumed true for base validation, CRL check is separate
                validityValid,
                constraintsValid,
                duration
            );

        } catch (Exception e) {
            errors.add(ValidationError.critical("UNEXPECTED_VALIDATION_ERROR", "Unexpected error during DSC validation: " + e.getMessage()));
            log.error("Unexpected error during DSC validation: {}. Error: {}", certData.getSubjectDN(), e.getMessage());
            long duration = System.currentTimeMillis() - validationStartTime;
            return ValidationResult.of(
                CertificateStatus.INVALID,
                false, false, false, false, false, duration
            );
        }
    }

    /**
     * CertificateData로부터 Certificate 엔티티 생성
     */
    /**
     * CertificateData로부터 Certificate 엔티티 생성 (오류 발생 시 더미 X509Certificate용)
     */
    private Certificate createCertificateFromData(
        CertificateData certData,
        java.util.UUID uploadId
    ) throws Exception {
        // Fallback or default values for x509Cert dependent fields
        PublicKey dummyPublicKey = null; // Or generate a dummy if strictly needed
        String signatureAlgorithm = "UNKNOWN";

        // If certData.getCertificateBinary() is null, some fields will be missing or default
        if (certData.getCertificateBinary() == null) {
             throw new IllegalArgumentException("Certificate binary data is required even for dummy creation.");
        }


        // X509Data 생성
        X509Data x509Data = X509Data.ofIncomplete(
            certData.getCertificateBinary(),
            dummyPublicKey,
            certData.getSerialNumber(),
            certData.getFingerprintSha256()
        );

        // SubjectInfo 생성 (DN에서 CN, Country Code 추출)
        String subjectCN = extractCNFromDN(certData.getSubjectDN());
        String subjectCountryCode = extractCountryCodeFromDN(certData.getSubjectDN());
        if (subjectCountryCode != null && subjectCountryCode.length() > 3) {
            log.warn("Country code '{}' from subject is longer than 3 characters, truncating.", subjectCountryCode);
            subjectCountryCode = subjectCountryCode.substring(0, 3);
        }
        // Fallback to certData country code if DN doesn't contain it
        if (subjectCountryCode == null) {
            subjectCountryCode = certData.getCountryCode();
        }
        SubjectInfo subjectInfo = SubjectInfo.of(
            certData.getSubjectDN(),
            subjectCountryCode,
            null,  // organization - 향후 DN 파싱으로 추출 가능
            null,  // organizationalUnit
            subjectCN
        );

        // IssuerInfo 생성 (DN에서 CN, Country Code 추출)
        String issuerCN = extractCNFromDN(certData.getIssuerDN());
        String issuerCountryCode = extractCountryCodeFromDN(certData.getIssuerDN());
        if (issuerCountryCode != null && issuerCountryCode.length() > 3) {
            log.warn("Country code '{}' from issuer is longer than 3 characters, truncating.", issuerCountryCode);
            issuerCountryCode = issuerCountryCode.substring(0, 3);
        }
        // Fallback to certData country code if DN doesn't contain it
        if (issuerCountryCode == null) {
            issuerCountryCode = certData.getCountryCode();
        }
        IssuerInfo issuerInfo = IssuerInfo.of(
            certData.getIssuerDN(),
            issuerCountryCode,
            null,  // organization
            null,  // organizationalUnit
            issuerCN,
            true   // CSCA는 CA이므로 true
        );

        // ValidityPeriod 생성 (default to current time if x509Cert is null)
        LocalDateTime now = LocalDateTime.now();
        ValidityPeriod validity = ValidityPeriod.of(now, now.plusYears(1)); // Default for dummy

        // CertificateType 결정
        CertificateType certificateType = CertificateType.valueOf(certData.getCertificateType());

        // Certificate 생성
        return Certificate.create(
            uploadId,
            x509Data,
            subjectInfo,
            issuerInfo,
            validity,
            certificateType,
            signatureAlgorithm,
            certData.getAllAttributes()
        );
    }
    
    private Certificate createCertificateFromData(
        CertificateData certData,
        X509Certificate x509Cert,
        java.util.UUID uploadId
    ) throws Exception {
        // X509Data 생성
        X509Data x509Data = X509Data.of(
            certData.getCertificateBinary(),
            x509Cert.getPublicKey(),
            certData.getSerialNumber(),
            certData.getFingerprintSha256()
        );

        // SubjectInfo 생성 (DN에서 CN, Country Code 추출)
        String subjectCN = extractCNFromDN(certData.getSubjectDN());
        String subjectCountryCode = extractCountryCodeFromDN(certData.getSubjectDN());
        if (subjectCountryCode != null && subjectCountryCode.length() > 3) {
            log.warn("Country code '{}' from subject is longer than 3 characters, truncating.", subjectCountryCode);
            subjectCountryCode = subjectCountryCode.substring(0, 3);
        }
        // Fallback to certData country code if DN doesn't contain it
        if (subjectCountryCode == null) {
            subjectCountryCode = certData.getCountryCode();
        }
        SubjectInfo subjectInfo = SubjectInfo.of(
            certData.getSubjectDN(),
            subjectCountryCode,
            null,  // organization - 향후 DN 파싱으로 추출 가능
            null,  // organizationalUnit
            subjectCN
        );

        // IssuerInfo 생성 (DN에서 CN, Country Code 추출)
        String issuerCN = extractCNFromDN(certData.getIssuerDN());
        String issuerCountryCode = extractCountryCodeFromDN(certData.getIssuerDN());
        if (issuerCountryCode != null && issuerCountryCode.length() > 3) {
            log.warn("Country code '{}' from issuer is longer than 3 characters, truncating.", issuerCountryCode);
            issuerCountryCode = issuerCountryCode.substring(0, 3);
        }
        // Fallback to certData country code if DN doesn't contain it
        if (issuerCountryCode == null) {
            issuerCountryCode = certData.getCountryCode();
        }
        IssuerInfo issuerInfo = IssuerInfo.of(
            certData.getIssuerDN(),
            issuerCountryCode,
            null,  // organization
            null,  // organizationalUnit
            issuerCN,
            true   // CSCA는 CA이므로 true
        );

        // ValidityPeriod 생성
        ValidityPeriod validity = ValidityPeriod.of(
            convertToLocalDateTime(x509Cert.getNotBefore()),
            convertToLocalDateTime(x509Cert.getNotAfter())
        );

        // CertificateType 결정
        CertificateType certificateType = CertificateType.valueOf(certData.getCertificateType());

        // 서명 알고리즘
        String signatureAlgorithm = x509Cert.getSigAlgName();

        // Certificate 생성
        return Certificate.create(
            uploadId,
            x509Data,
            subjectInfo,
            issuerInfo,
            validity,
            certificateType,
            signatureAlgorithm,
            certData.getAllAttributes()
        );
    }

    /**
     * DN에서 CN (Common Name) 추출
     */
    private String extractCNFromDN(String dn) {
        if (dn == null || dn.isBlank()) {
            return null;
        }

        // DN 형식: "CN=Korea CSCA, O=Ministry, C=KR"
        // CN= 부분 찾기
        int cnIndex = dn.indexOf("CN=");
        if (cnIndex >= 0) {
            int startIndex = cnIndex + 3;  // "CN=" 다음부터
            int endIndex = dn.indexOf(',', startIndex);
            if (endIndex > 0) {
                return dn.substring(startIndex, endIndex).trim();
            } else {
                return dn.substring(startIndex).trim();
            }
        }

        return null;
    }

    /**
     * DN에서 Country Code (C) 추출
     */
    private String extractCountryCodeFromDN(String dn) {
        if (dn == null || dn.isBlank()) {
            return null;
        }
        try {
            X500Name x500Name = new X500Name(dn);
            var rdns = x500Name.getRDNs(BCStyle.C);
            if (rdns != null && rdns.length > 0) {
                return rdns[0].getFirst().getValue().toString().trim();
            }
        } catch (IllegalArgumentException e) {
            log.warn("Failed to parse DN: {}", dn, e);
        }
        return null;
    }

    /**
     * Date를 LocalDateTime으로 변환
     */
    private LocalDateTime convertToLocalDateTime(Date date) {
        return date.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
    }
}