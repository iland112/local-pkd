package com.smartcoreinc.localpkd.fileupload.application.command;

import com.smartcoreinc.localpkd.fileupload.domain.model.ProcessingMode;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import lombok.Builder;

@Builder
public record UploadLdifFileCommand(
        UploadId uploadId,
        String fileName,
        byte[] fileContent,
        Long fileSize,
        String fileHash,
        boolean forceUpload,
        ProcessingMode processingMode
) {
    public void validate() {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        if (fileContent == null || fileContent.length == 0) {
            throw new IllegalArgumentException("fileContent must not be empty");
        }
        if (fileSize == null || fileSize <= 0) {
            throw new IllegalArgumentException("fileSize must be positive");
        }
        if (fileHash == null || fileHash.isBlank()) {
            throw new IllegalArgumentException("fileHash must not be blank");
        }
        if (!fileName.toLowerCase().endsWith(".ldif")) {
            throw new IllegalArgumentException("fileName must end with .ldif");
        }
    }
}