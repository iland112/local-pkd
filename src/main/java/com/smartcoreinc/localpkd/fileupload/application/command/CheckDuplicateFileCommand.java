package com.smartcoreinc.localpkd.fileupload.application.command;

import lombok.Builder;

@Builder
public record CheckDuplicateFileCommand(
        String fileName,
        Long fileSize,
        String fileHash
) {
    public void validate() {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        if (fileSize == null || fileSize <= 0) {
            throw new IllegalArgumentException("fileSize must be positive");
        }
        if (fileHash == null || fileHash.isBlank()) {
            throw new IllegalArgumentException("fileHash must not be blank");
        }
    }
}