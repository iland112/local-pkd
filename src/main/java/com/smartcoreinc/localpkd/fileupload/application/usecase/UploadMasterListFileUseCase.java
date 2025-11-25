package com.smartcoreinc.localpkd.fileupload.application.usecase;

import com.smartcoreinc.localpkd.fileupload.application.command.UploadMasterListFileCommand;
import com.smartcoreinc.localpkd.fileupload.application.response.UploadFileResponse;
import com.smartcoreinc.localpkd.fileupload.domain.model.*;
import com.smartcoreinc.localpkd.fileupload.domain.port.FileStoragePort;
import com.smartcoreinc.localpkd.fileupload.domain.repository.UploadedFileRepository;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProcessingStage;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import com.smartcoreinc.localpkd.shared.event.EventBus; // Keep EventBus import if needed elsewhere, but not for direct publishing here
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadMasterListFileUseCase {

    private final UploadedFileRepository repository;
    private final FileStoragePort fileStoragePort;
    private final ProgressService progressService;
    private final EntityManager entityManager; 
    // private final EventBus eventBus; // Removed direct EventBus injection

    @Transactional
    public UploadFileResponse execute(UploadMasterListFileCommand command) {
        log.info("=== Master List file upload started ===");
        
        UploadId uploadId = command.uploadId() != null ? command.uploadId() : UploadId.newId();

        try {
            command.validate();

            FileName fileName = FileName.of(command.fileName());
            FileHash fileHash = FileHash.of(command.fileHash());
            FileSize fileSize = FileSize.ofBytes(command.fileSize());

            if (!command.forceUpload()) {
                checkDuplicate(fileHash, fileName);
            } else {
                log.warn("Force upload enabled - skipping duplicate check for uploadId: {}", uploadId.getId());
            }

            FileFormat fileFormat = FileFormat.detectFromFileName(fileName);
            if (!fileFormat.isMasterList()) {
                throw new DomainException("INVALID_FILE_FORMAT", "파일이 Master List 형식이 아닙니다: " + fileFormat.getType());
            }

            FilePath savedPath = fileStoragePort.saveFile(command.fileContent(), fileFormat, fileName);
            log.info("File saved to: {} for uploadId: {}", savedPath.getValue(), uploadId.getId());

            CollectionNumber collectionNumber = CollectionNumber.extractFromFileName(fileName);
            FileVersion version = FileVersion.extractFromFileName(fileName, fileFormat);

            ProcessingMode processingMode = command.processingMode();
            UploadedFile uploadedFile = UploadedFile.createWithMetadata(
                uploadId, fileName, fileHash, fileSize, fileFormat,
                collectionNumber, version, savedPath, processingMode);

            if (command.expectedChecksum() != null && !command.expectedChecksum().isBlank()) {
                Checksum expectedChecksum = Checksum.of(command.expectedChecksum());
                uploadedFile.setExpectedChecksum(expectedChecksum);
                Checksum calculatedChecksum = fileStoragePort.calculateChecksum(savedPath);
                uploadedFile.validateChecksum(calculatedChecksum);
                log.info("Checksum validation status for uploadId {}: {}", uploadId.getId(), uploadedFile.getStatus());
            }

            if (command.forceUpload()) {
                Optional<UploadedFile> existingFile = repository.findByFileHash(fileHash);
                existingFile.ifPresent(file -> uploadedFile.markAsDuplicate(file.getId()));
            }

            UploadedFile saved = repository.save(uploadedFile);
            // Re-adding flush for debugging purposes, as it was removed in the previous step
            entityManager.flush(); 

            // eventBus.publishAll(saved.getDomainEvents()); // Removed direct event publishing
            // saved.clearDomainEvents(); // Removed direct event clearing
            log.info("File upload entity saved and flushed: uploadId={}, status={}, processingMode={}", // Changed log message
                     saved.getId().getId(), saved.getStatus(), saved.getProcessingMode());

            return UploadFileResponse.success(
                saved.getId().getId(), saved.getFileName().getValue(), saved.getFileSize().getBytes(),
                saved.getFileSizeDisplay(), saved.getFileFormatType(),
                saved.getCollectionNumber() != null ? saved.getCollectionNumber().getValue() : null,
                saved.getVersion() != null ? saved.getVersion().getValue() : null,
                saved.getUploadedAt(), saved.getStatus().name()
            );

        } catch (DomainException e) {
            log.error("Domain error during Master List upload for uploadId {}: {}", uploadId.getId(), e.getMessage());
            progressService.sendProgress(ProcessingProgress.failed(uploadId.getId(), ProcessingStage.UPLOAD_COMPLETED, e.getMessage()));
            return UploadFileResponse.failure(command.fileName(), e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected error during Master List upload for uploadId {}", uploadId.getId(), e);
            progressService.sendProgress(ProcessingProgress.failed(uploadId.getId(), ProcessingStage.UPLOAD_COMPLETED, "서버 내부 오류가 발생했습니다."));
            return UploadFileResponse.failure(command.fileName(), "파일 업로드 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    private void checkDuplicate(FileHash fileHash, FileName fileName) {
        Optional<UploadedFile> existingFile = repository.findByFileHash(fileHash);
        if (existingFile.isPresent()) {
            UploadedFile existing = existingFile.get();
            String errorMessage = String.format(
                "이 파일은 이미 업로드되었습니다. (ID: %s, 업로드 일시: %s, 상태: %s)",
                existing.getId().getId(),
                existing.getUploadedAt(),
                existing.getStatus().getDisplayName()
            );
            log.warn("Duplicate file detected: hash={}, existingId={}",
                     fileHash.getValue(), existing.getId().getId());
            throw new DomainException("DUPLICATE_FILE", errorMessage);
        }
    }
}
