package com.smartcoreinc.localpkd.fileupload.application.service;

import com.smartcoreinc.localpkd.fileupload.application.command.UploadLdifFileCommand;
import com.smartcoreinc.localpkd.fileupload.application.command.UploadMasterListFileCommand;
import com.smartcoreinc.localpkd.fileupload.application.usecase.UploadLdifFileUseCase;
import com.smartcoreinc.localpkd.fileupload.application.usecase.UploadMasterListFileUseCase;
import com.smartcoreinc.localpkd.fileupload.domain.model.ProcessingMode;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProcessingStage;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncUploadProcessor {

    private final UploadLdifFileUseCase uploadLdifFileUseCase;
    private final UploadMasterListFileUseCase uploadMasterListFileUseCase;
    private final ProgressService progressService;

    @Async("taskExecutor")
    public void processLdif(UploadId uploadId, String fileName, byte[] fileContent, long fileSize, String fileHash, String expectedChecksum, boolean forceUpload, ProcessingMode processingMode) {
        log.info("Starting async processing for LDIF uploadId: {}", uploadId.getId());
        try {
            UploadLdifFileCommand command = UploadLdifFileCommand.builder()
                    .uploadId(uploadId)
                    .fileName(fileName)
                    .fileContent(fileContent)
                    .fileSize(fileSize)
                    .fileHash(fileHash)
                    .expectedChecksum(expectedChecksum)
                    .forceUpload(forceUpload)
                    .processingMode(processingMode)
                    .build();
            uploadLdifFileUseCase.execute(command);
        } catch (Exception e) {
            log.error("Async LDIF processing failed for uploadId: {}", uploadId.getId(), e);
            progressService.sendProgress(ProcessingProgress.failed(uploadId.getId(), ProcessingStage.UPLOAD_COMPLETED, "LDIF 처리 중 예외 발생: " + e.getMessage()));
        }
    }

    @Async("taskExecutor")
    public void processMasterList(UploadId uploadId, String fileName, byte[] fileContent, long fileSize, String fileHash, String expectedChecksum, boolean forceUpload, ProcessingMode processingMode) {
        log.info("Starting async processing for MasterList uploadId: {}", uploadId.getId());
        try {
            UploadMasterListFileCommand command = UploadMasterListFileCommand.builder()
                    .uploadId(uploadId)
                    .fileName(fileName)
                    .fileContent(fileContent)
                    .fileSize(fileSize)
                    .fileHash(fileHash)
                    .expectedChecksum(expectedChecksum)
                    .forceUpload(forceUpload)
                    .processingMode(processingMode)
                    .build();
            uploadMasterListFileUseCase.execute(command);
        } catch (Exception e) {
            log.error("Async MasterList processing failed for uploadId: {}", uploadId.getId(), e);
            progressService.sendProgress(ProcessingProgress.failed(uploadId.getId(), ProcessingStage.UPLOAD_COMPLETED, "MasterList 처리 중 예외 발생: " + e.getMessage()));
        }
    }
}
