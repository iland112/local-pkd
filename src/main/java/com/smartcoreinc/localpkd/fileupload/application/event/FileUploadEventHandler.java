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
            if (parseResponse.certificateCount() > 0 || parseResponse.crlCount() > 0) {
                 // Retrieve parsedFileId using uploadId
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
            }
            // TODO: Chain to LDAP Upload

        } catch (Exception e) {
            log.error("Failed to process file for uploadId: {}", event.uploadId().getId(), e);
            progressService.sendProgress(
                ProcessingProgress.failed(event.uploadId().getId(), ProcessingStage.FAILED, "File processing failed: " + e.getMessage())
            );
        }
    }

    private ParseFileResponse parseFile(UploadedFile uploadedFile) throws Exception {
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
            progressService.sendProgress(ProcessingProgress.parsingCompleted(uploadedFile.getId().getId(), response.certificateCount()));
            log.info("Parsing completed successfully for uploadId={}", uploadedFile.getId().getId());
        } else {
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
