package com.smartcoreinc.localpkd.fileupload.application.event;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.ValidateCertificatesCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.CertificatesValidatedResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.ValidateCertificatesUseCase;
import com.smartcoreinc.localpkd.fileparsing.application.command.ParseLdifFileCommand;
import com.smartcoreinc.localpkd.fileparsing.application.command.ParseMasterListFileCommand;
import com.smartcoreinc.localpkd.fileparsing.application.response.ParseFileResponse;
import com.smartcoreinc.localpkd.fileparsing.application.usecase.ParseLdifFileUseCase;
import com.smartcoreinc.localpkd.fileparsing.application.usecase.ParseMasterListFileUseCase;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.domain.repository.ParsedFileRepository;
import com.smartcoreinc.localpkd.fileupload.domain.event.DuplicateFileDetectedEvent;
import com.smartcoreinc.localpkd.fileupload.domain.event.FileUploadedEvent;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile;
import com.smartcoreinc.localpkd.fileupload.domain.port.FileStoragePort;
import com.smartcoreinc.localpkd.fileupload.domain.repository.UploadedFileRepository;
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

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadEventHandler {

    private final UploadedFileRepository uploadedFileRepository;
    private final FileStoragePort fileStoragePort;
    private final ParseLdifFileUseCase parseLdifFileUseCase;
    private final ParseMasterListFileUseCase parseMasterListFileUseCase;
    private final ValidateCertificatesUseCase validateCertificatesUseCase;
    private final ProgressService progressService;
    private final ParsedFileRepository parsedFileRepository;
    private final com.smartcoreinc.localpkd.ldapintegration.application.usecase.UploadToLdapUseCase uploadToLdapUseCase;

    @EventListener
    public void handleFileUploaded(FileUploadedEvent event) {
        log.info("=== [Event] FileUploaded ===");
        log.info("Upload ID: {}", event.uploadId().getId());
        log.info("File name: {}", event.fileName());
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFileUploadedAsync(FileUploadedEvent event) {
        log.info("=== [Event-Async] FileUploaded (Processing Mode: {}) ===", event.processingMode().getDisplayName());

        if (event.processingMode().isManual()) {
            log.info("MANUAL mode: Waiting for user to trigger parsing for uploadId={}", event.uploadId().getId());
            return;
        }

        try {
            Optional<UploadedFile> uploadedFileOpt = uploadedFileRepository.findById(event.uploadId());
            if (uploadedFileOpt.isEmpty()) {
                throw new IllegalStateException("UploadedFile not found: " + event.uploadId().getId());
            }
            UploadedFile uploadedFile = uploadedFileOpt.get();

            // 1. Parse File
            ParseFileResponse parseResponse = parseFile(uploadedFile);
            if (!parseResponse.success()) {
                throw new RuntimeException("Parsing failed: " + parseResponse.errorMessage());
            }

            // 2. Validate Certificates
            // LDIF(NC-DATA 포함)처럼 Certificate 테이블에 아직 저장되지 않은 경우에도
            // ParsedFile에 추출된 인증서가 있으면 항상 검증 단계를 수행하도록 조건을 제거한다.
            ParsedFile parsedFile = parsedFileRepository.findByUploadId(uploadedFile.getId())
                .orElseThrow(() -> new DomainException("PARSED_FILE_NOT_FOUND", "Parsed file not found for uploadId: " + uploadedFile.getId().getId()));

            CertificatesValidatedResponse validationResponse = validateCertificates(
                uploadedFile.getId().getId(),
                parsedFile.getId().getId(), // Pass parsedFileId
                parseResponse.certificateCount(),
                parseResponse.crlCount() // Pass crlCount
            );
            if (!validationResponse.success()) {
               throw new RuntimeException("Validation failed: " + validationResponse.errorMessage());
            }

            // 3. Upload to LDAP
            com.smartcoreinc.localpkd.ldapintegration.application.response.UploadToLdapResponse ldapResponse = uploadToLdap(
                uploadedFile,
                validationResponse
            );
            if (!ldapResponse.success()) {
                throw new RuntimeException("LDAP upload failed: " + ldapResponse.errorMessage());
            }

        } catch (Exception e) {
            log.error("Failed to process file for uploadId: {}", event.uploadId().getId(), e);
            progressService.sendProgress(
                ProcessingProgress.failed(event.uploadId().getId(), ProcessingStage.FAILED, "File processing failed: " + e.getMessage())
            );
        }
    }

    private ParseFileResponse parseFile(UploadedFile uploadedFile) throws Exception {
        // Update status to PARSING
        uploadedFile.updateStatusToParsing();
        uploadedFileRepository.save(uploadedFile);

        progressService.sendProgress(ProcessingProgress.parsingStarted(uploadedFile.getId().getId(), uploadedFile.getFileName().getValue()));

        byte[] fileBytes = fileStoragePort.readFile(uploadedFile.getFilePath());
        String fileFormatType = uploadedFile.getFileFormatType();

        ParseFileResponse response;
        if (fileFormatType.equals("ML_SIGNED_CMS") || fileFormatType.equals("ML_UNSIGNED")) {
            ParseMasterListFileCommand command = ParseMasterListFileCommand.builder()
                .uploadId(uploadedFile.getId().getId())
                .fileBytes(fileBytes)
                .fileFormat(fileFormatType)
                .build();
            response = parseMasterListFileUseCase.execute(command);
        } else {
            ParseLdifFileCommand command = ParseLdifFileCommand.builder()
                .uploadId(uploadedFile.getId().getId())
                .fileBytes(fileBytes)
                .fileFormat(fileFormatType)
                .build();
            response = parseLdifFileUseCase.execute(command);
        }

        if (response.success()) {
            // Update status to PARSED after successful parsing
            uploadedFile.updateStatusToParsed();
            uploadedFileRepository.save(uploadedFile);

            progressService.sendProgress(ProcessingProgress.parsingCompleted(uploadedFile.getId().getId(), response.certificateCount()));
            log.info("Parsing completed successfully for uploadId={}", uploadedFile.getId().getId());
        } else {
            // Update status to FAILED on parsing error
            uploadedFile.fail(response.errorMessage());
            uploadedFileRepository.save(uploadedFile);

            progressService.sendProgress(ProcessingProgress.failed(uploadedFile.getId().getId(), ProcessingStage.PARSING_COMPLETED, response.errorMessage()));
            log.error("Parsing failed for uploadId={}: {}", uploadedFile.getId().getId(), response.errorMessage());
        }
        return response;
    }

    private CertificatesValidatedResponse validateCertificates(
            UUID uploadId, UUID parsedFileId, int certificateCount, int crlCount) {
        progressService.sendProgress(ProcessingProgress.validationStarted(uploadId, certificateCount));
        log.info("Triggering certificate validation for uploadId={}", uploadId);
        ValidateCertificatesCommand validationCommand = ValidateCertificatesCommand.builder()
            .uploadId(uploadId)
            .parsedFileId(parsedFileId)
            .certificateCount(certificateCount)
            .crlCount(crlCount)
            .build();
        return validateCertificatesUseCase.execute(validationCommand);
    }

    private com.smartcoreinc.localpkd.ldapintegration.application.response.UploadToLdapResponse uploadToLdap(
            UploadedFile uploadedFile,
            CertificatesValidatedResponse validationResponse) {
        // Update status to UPLOADING_TO_LDAP
        uploadedFile.updateStatusToUploadingToLdap();
        uploadedFileRepository.save(uploadedFile);

        log.info("Triggering LDAP upload for uploadId={}", uploadedFile.getId().getId());

        // Create command for LDAP upload
        // Note: ALL certificates (valid + invalid + expired) are uploaded to LDAP with their validation status
        com.smartcoreinc.localpkd.ldapintegration.application.command.UploadToLdapCommand ldapCommand =
            com.smartcoreinc.localpkd.ldapintegration.application.command.UploadToLdapCommand.create(
                uploadedFile.getId().getId(),
                validationResponse.validCertificateCount() + validationResponse.invalidCertificateCount(),  // All certificates
                validationResponse.validCrlCount() + validationResponse.invalidCrlCount()  // All CRLs
            );

        // Execute LDAP upload
        com.smartcoreinc.localpkd.ldapintegration.application.response.UploadToLdapResponse ldapResponse =
            uploadToLdapUseCase.execute(ldapCommand);

        if (ldapResponse.success()) {
            // Update status to COMPLETED
            uploadedFile.updateStatusToCompleted();
            uploadedFileRepository.save(uploadedFile);

            log.info("LDAP upload completed successfully for uploadId={}, totalUploaded={}, totalFailed={}",
                uploadedFile.getId().getId(), ldapResponse.getTotalUploaded(), ldapResponse.getTotalFailed());
        } else {
            // Update status to FAILED
            uploadedFile.fail(ldapResponse.errorMessage());
            uploadedFileRepository.save(uploadedFile);

            log.error("LDAP upload failed for uploadId={}: {}", uploadedFile.getId().getId(), ldapResponse.errorMessage());
        }

        return ldapResponse;
    }


    /**
     * Handle FileParsingCompletedEvent to send manual pause for MANUAL mode
     */
    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFileParsingCompleted(com.smartcoreinc.localpkd.fileparsing.domain.event.FileParsingCompletedEvent event) {
        log.info("=== [Event-Async] FileParsingCompleted for uploadId: {} ===", event.getUploadId());

        try {
            Optional<UploadedFile> uploadedFileOpt = uploadedFileRepository.findById(new com.smartcoreinc.localpkd.fileupload.domain.model.UploadId(event.getUploadId()));
            if (uploadedFileOpt.isEmpty()) {
                log.warn("UploadedFile not found for uploadId: {}", event.getUploadId());
                return;
            }

            UploadedFile uploadedFile = uploadedFileOpt.get();

            // In MANUAL mode, send PAUSE event to notify UI that parsing is complete
            if (uploadedFile.isManualMode()) {
                log.info("MANUAL mode: Parsing completed for uploadId={}, waiting for user to trigger validation.", event.getUploadId());

                // Send manual pause event to UI
                progressService.sendProgress(
                    ProcessingProgress.manualPause(event.getUploadId(), ProcessingStage.PARSING_COMPLETED)
                );
            }
        } catch (Exception e) {
            log.error("Failed to handle FileParsingCompleted event for uploadId: {}", event.getUploadId(), e);
        }
    }

    @EventListener
    public void handleDuplicateFileDetected(DuplicateFileDetectedEvent event) {
        log.warn("=== [Event] DuplicateFileDetected ===");
        log.warn("Duplicate upload ID: {}", event.duplicateUploadId().getId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDuplicateFileDetectedAsync(DuplicateFileDetectedEvent event) {
        log.info("=== [Event-Async] DuplicateFileDetected (Additional processing) ===");
    }

    private String formatFileSize(long bytes) {
        if (bytes >= 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        if (bytes >= 1024) return String.format("%.1f KB", bytes / 1024.0);
        return bytes + " bytes";
    }
}
