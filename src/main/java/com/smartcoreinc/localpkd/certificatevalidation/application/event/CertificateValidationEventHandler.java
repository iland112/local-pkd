package com.smartcoreinc.localpkd.certificatevalidation.application.event;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.UploadToLdapResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.UploadToLdapUseCase;
import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CertificatesValidatedEvent;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProcessingStage;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateValidationEventHandler {

    private final ProgressService progressService;
    private final UploadToLdapUseCase uploadToLdapUseCase;

    @EventListener
    public void handleCertificatesValidated(CertificatesValidatedEvent event) {
        log.info("=== [Event-Sync] CertificatesValidated (Batch Validation Completed) ===");
        progressService.sendProgress(
            ProcessingProgress.validationCompleted(event.getUploadId(), event.getTotalValidated())
        );
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCertificatesValidatedAndTriggerLdapUpload(CertificatesValidatedEvent event) {
        log.info("=== [Event-Async] CertificatesValidated (Triggering LDAP upload) for uploadId: {} ===", event.getUploadId());

        if (event.getValidCertificateIds() == null || event.getValidCertificateIds().isEmpty()) {
            log.info("No valid certificates to upload to LDAP for uploadId: {}", event.getUploadId());
            progressService.sendProgress(ProcessingProgress.completed(event.getUploadId(), 0));
            return;
        }

        try {
            progressService.sendProgress(
                ProcessingProgress.ldapSavingStarted(event.getUploadId(), event.getValidCertificateCount())
            );

            UploadToLdapCommand command = UploadToLdapCommand.builder()
                .uploadId(event.getUploadId())
                .certificateIds(event.getValidCertificateIds())
                .isBatch(true)
                .build();
            
            UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

            if (response.isSuccess()) {
                log.info("LDAP upload completed successfully for uploadId: {}", event.getUploadId());
                progressService.sendProgress(
                    ProcessingProgress.ldapSavingCompleted(event.getUploadId(), response.getSuccessCount())
                );
            } else {
                log.error("LDAP upload failed for uploadId: {}: {}", event.getUploadId(), response.getErrorMessage());
                progressService.sendProgress(
                    ProcessingProgress.failed(event.getUploadId(), ProcessingStage.LDAP_SAVING_COMPLETED, response.getErrorMessage())
                );
            }
        } catch (Exception e) {
            log.error("Failed to trigger or execute LDAP upload for uploadId: {}", event.getUploadId(), e);
            progressService.sendProgress(
                ProcessingProgress.failed(event.getUploadId(), ProcessingStage.LDAP_SAVING_STARTED, "LDAP 업로드 실행 중 예외 발생: " + e.getMessage())
            );
        }
    }
}
