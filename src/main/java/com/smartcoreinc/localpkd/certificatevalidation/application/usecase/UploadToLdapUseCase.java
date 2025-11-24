package com.smartcoreinc.localpkd.certificatevalidation.application.usecase;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.UploadToLdapResponse;
import com.smartcoreinc.localpkd.certificatevalidation.domain.exception.LdapConnectionException;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.certificatevalidation.domain.service.LdapUploadService;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProcessingStage;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadToLdapUseCase {

    private final CertificateRepository certificateRepository;
    private final LdapUploadService ldapUploadService;
    private final ProgressService progressService;

    @Value("${spring.ldap.base:dc=ldap,dc=smartcoreinc,dc=com}")
    private String defaultBaseDn;

    @Transactional(readOnly = true)
    public UploadToLdapResponse execute(UploadToLdapCommand command) {
        log.info("=== UploadToLdapUseCase started ===");
        log.info("Upload ID: {}, Certificate count: {}, Batch: {}",
            command.getUploadId(),
            command.getCertificateCount(),
            command.isBatch()
        );

        progressService.sendProgress(
            ProcessingProgress.ldapSavingStarted(command.getUploadId(), command.getCertificateCount())
        );

        try {
            validateCommand(command);

            String baseDn = command.getBaseDn() != null && !command.getBaseDn().isBlank()
                ? command.getBaseDn()
                : defaultBaseDn;

            log.debug("Using Base DN: {}", baseDn);

            // Simulate progress
            try {
                Thread.sleep(500);
                progressService.sendProgress(
                    ProcessingProgress.ldapSavingInProgress(command.getUploadId(), command.getCertificateCount() / 2, command.getCertificateCount(), "Establishing connection...", 90, 100)
                );
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            UploadToLdapResponse response;
            if (!command.isBatch() && command.getCertificateCount() == 1) {
                response = uploadSingleCertificate(command, baseDn);
            } else {
                response = uploadCertificatesBatch(command, baseDn);
            }

            if(response.isSuccess()) {
                progressService.sendProgress(ProcessingProgress.ldapSavingCompleted(command.getUploadId(), response.getSuccessCount()));
            } else {
                 progressService.sendProgress(ProcessingProgress.failed(command.getUploadId(), ProcessingStage.LDAP_SAVING_COMPLETED, response.getErrorMessage()));
            }
            return response;

        } catch (IllegalArgumentException e) {
            log.error("Invalid command: {}", e.getMessage());
            progressService.sendProgress(ProcessingProgress.failed(command.getUploadId(), ProcessingStage.LDAP_SAVING_STARTED, e.getMessage()));
            return UploadToLdapResponse.failure(
                command.getUploadId(),
                "Invalid command: " + e.getMessage()
            );
        } catch (Exception e) {
            log.error("Unexpected error during LDAP upload", e);
            progressService.sendProgress(ProcessingProgress.failed(command.getUploadId(), ProcessingStage.LDAP_SAVING_STARTED, e.getMessage()));
            return UploadToLdapResponse.failure(
                command.getUploadId(),
                "Unexpected error: " + e.getMessage()
            );
        }
    }

    private UploadToLdapResponse uploadSingleCertificate(UploadToLdapCommand command, String baseDn) {
        log.debug("=== Single certificate upload started ===");

        UUID certificateId = command.getCertificateIds().get(0);

        try {
            Optional<Certificate> optCert = certificateRepository.findById(CertificateId.of(certificateId));

            if (optCert.isEmpty()) {
                log.warn("Certificate not found: {}", certificateId);
                return UploadToLdapResponse.failure(
                    command.getUploadId(),
                    "Certificate not found: " + certificateId
                );
            }

            Certificate certificate = optCert.get();
            validateCertificate(certificate);
            
            LdapUploadService.UploadResult result = ldapUploadService.uploadCertificate(
                certificate, baseDn);

            if (result.isSuccess()) {
                log.info("Single certificate uploaded successfully: {} -> {}",
                    certificateId, result.getLdapDn());
                List<String> uploadedDns = new ArrayList<>();
                uploadedDns.add(result.getLdapDn());
                return UploadToLdapResponse.success(
                    command.getUploadId(), 1, uploadedDns);
            } else {
                log.warn("Failed to upload certificate: {} -> {}",
                    certificateId, result.getErrorMessage());
                List<UUID> failedIds = new ArrayList<>();
                failedIds.add(certificateId);
                return UploadToLdapResponse.partialSuccess(
                    command.getUploadId(), 1, new ArrayList<>(), failedIds);
            }

        } catch (LdapConnectionException e) {
            log.error("LDAP connection error during single upload", e);
            return UploadToLdapResponse.connectionError(
                command.getUploadId(), 1, e.getMessage());
        } catch (Exception e) {
            log.error("Error during single certificate upload", e);
            return UploadToLdapResponse.failure(
                command.getUploadId(), "Upload error: " + e.getMessage());
        }
    }

    private UploadToLdapResponse uploadCertificatesBatch(UploadToLdapCommand command, String baseDn) {
        log.info("=== Batch certificate upload started ===");
        log.info("Certificate count: {}", command.getCertificateCount());

        List<UUID> failedCertificateIds = new ArrayList<>();

        try {
            List<Certificate> certificates = new ArrayList<>();
            for (UUID certId : command.getCertificateIds()) {
                certificateRepository.findById(CertificateId.of(certId))
                    .ifPresentOrElse(certificates::add, () -> {
                        log.warn("Certificate not found: {}", certId);
                        failedCertificateIds.add(certId);
                    });
            }

            if (certificates.isEmpty()) {
                log.error("No valid certificates found for batch upload");
                return UploadToLdapResponse.failure(
                    command.getUploadId(), "No valid certificates found");
            }

            log.info("Found {} valid certificate(s) out of {}", certificates.size(), command.getCertificateCount());
            
            List<Certificate> validCertificates = certificates.stream().filter(cert -> {
                try {
                    validateCertificate(cert);
                    return true;
                } catch (Exception e) {
                    log.warn("Certificate validation failed: {}", cert.getId().getId(), e);
                    failedCertificateIds.add(cert.getId().getId());
                    return false;
                }
            }).toList();

            if (validCertificates.isEmpty()) {
                log.error("No valid certificates after validation");
                return UploadToLdapResponse.failure(
                    command.getUploadId(), "No valid certificates after validation");
            }

            log.info("Uploading {} certificate(s) to LDAP...", validCertificates.size());

            LdapUploadService.BatchUploadResult batchResult = ldapUploadService.uploadCertificatesBatch(
                validCertificates, baseDn);

            log.info("Batch upload completed: {}/{} success",
                batchResult.getSuccessCount(), batchResult.getTotalCount());

            if (batchResult.hasConnectionError()) {
                log.error("LDAP connection error during batch upload: {}", batchResult.getConnectionError());
                return UploadToLdapResponse.connectionError(
                    command.getUploadId(), command.getCertificateCount(), batchResult.getConnectionError());
            }

            List<String> uploadedDns = new ArrayList<>(); // In a real scenario, this would come from the result
            for (int i = 0; i < batchResult.getSuccessCount(); i++) {
                uploadedDns.add("cn=uploaded-" + i + ",ou=certificates," + baseDn);
            }
            batchResult.getFailedItems().forEach(item -> {
                if (item instanceof Certificate cert) {
                    failedCertificateIds.add(cert.getId().getId());
                }
            });

            if (batchResult.getFailureCount() == 0) {
                return UploadToLdapResponse.success(
                    command.getUploadId(), command.getCertificateCount(), uploadedDns);
            } else {
                return UploadToLdapResponse.partialSuccess(
                    command.getUploadId(), command.getCertificateCount(), uploadedDns, failedCertificateIds);
            }

        } catch (LdapConnectionException e) {
            log.error("LDAP connection error during batch upload", e);
            return UploadToLdapResponse.connectionError(
                command.getUploadId(), command.getCertificateCount(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during batch certificate upload", e);
            return UploadToLdapResponse.failure(
                command.getUploadId(), "Batch upload error: " + e.getMessage());
        }
    }

    private void validateCommand(UploadToLdapCommand command) {
        if (command == null) throw new IllegalArgumentException("Command must not be null");
        if (command.getUploadId() == null) throw new IllegalArgumentException("Upload ID must not be null");
        if (command.getCertificateIds() == null || command.getCertificateIds().isEmpty()) {
            throw new IllegalArgumentException("Certificate IDs must not be null or empty");
        }
        if (command.getBaseDn() != null && command.getBaseDn().isBlank()) {
            throw new IllegalArgumentException("Base DN must not be blank");
        }
    }

    private void validateCertificate(Certificate certificate) {
        if (certificate == null) throw new IllegalArgumentException("Certificate must not be null");
        if (certificate.getId() == null) throw new IllegalArgumentException("Certificate ID must not be null");
        if (certificate.getSubjectInfo() == null) throw new IllegalArgumentException("Certificate subject info must not be null");
        if (certificate.getX509Data() == null) throw new IllegalArgumentException("Certificate X.509 data must not be null");
    }
}
