package com.smartcoreinc.localpkd.fileupload.application.service;

import com.smartcoreinc.localpkd.fileupload.application.command.CheckDuplicateFileCommand;
import com.smartcoreinc.localpkd.fileupload.application.command.UploadLdifFileCommand;
import com.smartcoreinc.localpkd.fileupload.application.command.UploadMasterListFileCommand;
import com.smartcoreinc.localpkd.fileupload.application.response.CheckDuplicateResponse;
import com.smartcoreinc.localpkd.fileupload.application.usecase.CheckDuplicateFileUseCase;
import com.smartcoreinc.localpkd.fileupload.application.usecase.UploadLdifFileUseCase;
import com.smartcoreinc.localpkd.fileupload.application.usecase.UploadMasterListFileUseCase;
import com.smartcoreinc.localpkd.fileupload.domain.model.ProcessingMode;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.fileupload.infrastructure.exception.FileUploadException;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProcessingStage;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import com.smartcoreinc.localpkd.shared.util.HashingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncUploadProcessor {

    private final UploadLdifFileUseCase uploadLdifFileUseCase;
    private final UploadMasterListFileUseCase uploadMasterListFileUseCase;
    private final CheckDuplicateFileUseCase checkDuplicateFileUseCase;
    private final ProgressService progressService;

    @Async("taskExecutor")
    public void processLdif(UploadId uploadId, String fileName, byte[] fileContent, long fileSize, boolean forceUpload, ProcessingMode processingMode) {
        log.info("Starting async processing for LDIF uploadId: {}", uploadId.getId());
        try {
            String fileHash = HashingUtil.calculateSha256(fileContent);

            if (!forceUpload) {
                handleDuplicateCheck(uploadId, fileName, fileSize, fileHash);
            }

            UploadLdifFileCommand command = UploadLdifFileCommand.builder()
                    .uploadId(uploadId)
                    .fileName(fileName)
                    .fileContent(fileContent)
                    .fileSize(fileSize)
                    .fileHash(fileHash)
                    .forceUpload(forceUpload)
                    .processingMode(processingMode)
                    .build();
            uploadLdifFileUseCase.execute(command);
        } catch (Exception e) {
            log.error("Async LDIF processing failed for uploadId: {}", uploadId.getId(), e);
            progressService.sendProgress(ProcessingProgress.failed(uploadId.getId(), ProcessingStage.UPLOAD_COMPLETED, "LDIF 처리 중 예외 발생: " + e.getMessage()));
            if (e instanceof FileUploadException.DuplicateFileException) {
                throw (FileUploadException.DuplicateFileException) e;
            }
        }
    }

    @Async("taskExecutor")
    public void processMasterList(UploadId uploadId, String fileName, byte[] fileContent, long fileSize, boolean forceUpload, ProcessingMode processingMode) {
        log.info("Starting async processing for MasterList uploadId: {}", uploadId.getId());
        try {
            String fileHash = HashingUtil.calculateSha256(fileContent);

            if (!forceUpload) {
                handleDuplicateCheck(uploadId, fileName, fileSize, fileHash);
            }

            UploadMasterListFileCommand command = UploadMasterListFileCommand.builder()
                    .uploadId(uploadId)
                    .fileName(fileName)
                    .fileContent(fileContent)
                    .fileSize(fileSize)
                    .fileHash(fileHash)
                    .forceUpload(forceUpload)
                    .processingMode(processingMode)
                    .build();
            uploadMasterListFileUseCase.execute(command);
        } catch (Exception e) {
            log.error("Async MasterList processing failed for uploadId: {}", uploadId.getId(), e);
            progressService.sendProgress(ProcessingProgress.failed(uploadId.getId(), ProcessingStage.UPLOAD_COMPLETED, "MasterList 처리 중 예외 발생: " + e.getMessage()));
            if (e instanceof FileUploadException.DuplicateFileException) {
                throw (FileUploadException.DuplicateFileException) e;
            }
        }
    }

    private void handleDuplicateCheck(UploadId uploadId, String fileName, long fileSize, String fileHash) {
        CheckDuplicateFileCommand checkCommand = new CheckDuplicateFileCommand(fileName, fileSize, fileHash);
        CheckDuplicateResponse duplicateResponse = checkDuplicateFileUseCase.execute(checkCommand);

        if (duplicateResponse.isDuplicate()) {
            Map<String, Object> details = new HashMap<>();
            details.put("existingFileId", duplicateResponse.existingFileId());
            details.put("existingFileName", duplicateResponse.existingFileName());
            details.put("existingUploadDate", duplicateResponse.existingUploadDate());
            details.put("existingStatus", duplicateResponse.existingStatus());
            
            throw new FileUploadException.DuplicateFileException(duplicateResponse.message(), details);
        }
    }
}