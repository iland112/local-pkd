package com.smartcoreinc.localpkd.ldapintegration.application.event;

import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CertificatesValidatedEvent;
import com.smartcoreinc.localpkd.fileupload.domain.model.ProcessingMode;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile;
import com.smartcoreinc.localpkd.fileupload.domain.repository.UploadedFileRepository;
import com.smartcoreinc.localpkd.ldapintegration.application.command.UploadToLdapCommand;
import com.smartcoreinc.localpkd.ldapintegration.application.response.UploadToLdapResponse;
import com.smartcoreinc.localpkd.ldapintegration.application.usecase.UploadToLdapUseCase;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProcessingStage;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event handler in the LDAP Integration context that listens for events from the Certificate Validation context.
 * This handler is responsible for triggering the LDAP upload process after certificates have been validated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CertificateValidatedEventHandler {

    private final UploadToLdapUseCase uploadToLdapUseCase;
    private final UploadedFileRepository uploadedFileRepository;
    private final ProgressService progressService;

    /**
     * Handles the completion of certificate validation and triggers the LDAP upload process.
     * This method runs asynchronously after the main database transaction is committed.
     *
     * @param event The event fired when certificate validation is complete.
     */
    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCertificatesValidatedAndTriggerLdapUpload(CertificatesValidatedEvent event) {
        log.info("=== [Event-Async][LDAP Context] CertificatesValidated event received, triggering LDAP upload for uploadId: {} ===", event.getUploadId());

        try {
            UploadedFile uploadedFile = uploadedFileRepository.findById(new UploadId(event.getUploadId()))
                    .orElseThrow(() -> new DomainException("UPLOAD_NOT_FOUND", "UploadedFile not found for ID: " + event.getUploadId()));

            // In MANUAL mode, we wait for a user to trigger the next step.
            if (uploadedFile.isManualMode()) {
                log.info("MANUAL mode: Validation completed for uploadId={}, waiting for user to trigger LDAP upload.", event.getUploadId());
                uploadedFile.markReadyForLdapUpload();
                uploadedFileRepository.save(uploadedFile);

                // Notify the UI that validation is done and is now waiting for manual trigger.
                progressService.sendProgress(
                    ProcessingProgress.manualPause(event.getUploadId(), ProcessingStage.VALIDATION_COMPLETED)
                );
                return; // Stop further processing
            }

            // In AUTO mode, proceed with LDAP upload automatically.
            // Upload ALL certificates (valid + invalid) to LDAP with their validation status
            int totalCertificates = event.getValidCertificateCount() + event.getInvalidCertificateCount();
            int totalCrls = event.getValidCrlCount() + event.getInvalidCrlCount();

            if (totalCertificates == 0 && totalCrls == 0) {
                log.info("No certificates or CRLs to upload to LDAP for uploadId: {}. Completing process.", event.getUploadId());
                progressService.sendProgress(ProcessingProgress.completed(event.getUploadId(), 0));
                return;
            }

            log.info("AUTO mode: Starting LDAP upload for {} total certificates ({} valid, {} invalid) and {} total CRLs ({} valid, {} invalid).",
                totalCertificates, event.getValidCertificateCount(), event.getInvalidCertificateCount(),
                totalCrls, event.getValidCrlCount(), event.getInvalidCrlCount());

            // Update status to UPLOADING_TO_LDAP
            uploadedFile.updateStatusToUploadingToLdap();
            uploadedFileRepository.save(uploadedFile);

            progressService.sendProgress(
                ProcessingProgress.ldapSavingStarted(event.getUploadId(), totalCertificates + totalCrls)
            );

            // Use the correct Command, UseCase, and Response from the ldapintegration context
            // Note: Command takes "validCertificateCount" but UseCase should upload ALL certificates
            UploadToLdapCommand command = UploadToLdapCommand.create(
                event.getUploadId(),
                totalCertificates,  // Total (valid + invalid) - UseCase will query DB for all
                totalCrls           // Total (valid + invalid)
            );

            UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

            if (response.success()) {
                log.info("LDAP upload completed successfully for uploadId: {}", event.getUploadId());
                progressService.sendProgress(
                    ProcessingProgress.ldapSavingCompleted(event.getUploadId(), response.getTotalUploaded())
                );
                // Also send the final completion event
                progressService.sendProgress(ProcessingProgress.completed(event.getUploadId(), response.getTotalUploaded()));
            } else {
                log.error("LDAP upload failed for uploadId: {}: {}", event.getUploadId(), response.errorMessage());
                progressService.sendProgress(
                    ProcessingProgress.failed(event.getUploadId(), ProcessingStage.LDAP_SAVING_IN_PROGRESS, response.errorMessage())
                );
            }

        } catch (Exception e) {
            log.error("Failed to trigger or execute LDAP upload for uploadId: {}", event.getUploadId(), e);
            progressService.sendProgress(
                ProcessingProgress.failed(event.getUploadId(), ProcessingStage.VALIDATION_COMPLETED, "Failed to start LDAP upload: " + e.getMessage())
            );
        }
    }
}
