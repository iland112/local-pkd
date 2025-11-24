package com.smartcoreinc.localpkd.fileupload.application.command;

import com.smartcoreinc.localpkd.fileupload.domain.model.ProcessingMode;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import lombok.Builder;

/**
 * Master List 파일 업로드 Command
 *
 * <p>Master List 파일 업로드 요청을 나타내는 Command 객체입니다.</p>
 *
 * <h3>포함 정보</h3>
 * <ul>
 *   <li>업로드 ID (optional)</li>
 *   <li>파일명</li>
 *   <li>파일 내용 (byte array)</li>
 *   <li>파일 크기 (bytes)</li>
 *   <li>파일 해시 (SHA-256, 중복 검사용)</li>
 *   <li>예상 체크섬 (SHA-1, optional)</li>
 *   <li>강제 업로드 여부 (forceUpload)</li>
 *   <li>처리 모드 (AUTO 또는 MANUAL)</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.1
 * @since 2025-11-24
 */
@Builder
public record UploadMasterListFileCommand(
        UploadId uploadId, // Added to support pre-generated ID for async processing
        String fileName,
        byte[] fileContent,
        Long fileSize,
        String fileHash,
        String expectedChecksum,  // optional
        boolean forceUpload,
        ProcessingMode processingMode  // AUTO (default) or MANUAL
) {
    /**
     * All-args constructor for the builder.
     */
    public UploadMasterListFileCommand(
            UploadId uploadId, String fileName, byte[] fileContent, Long fileSize,
            String fileHash, String expectedChecksum, boolean forceUpload, ProcessingMode processingMode) {
        this.uploadId = uploadId;
        this.fileName = fileName;
        this.fileContent = fileContent;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.expectedChecksum = expectedChecksum;
        this.forceUpload = forceUpload;
        this.processingMode = processingMode;
    }

    /**
     * Legacy constructor for backward compatibility.
     */
    public UploadMasterListFileCommand(
            String fileName,
            byte[] fileContent,
            Long fileSize,
            String fileHash,
            String expectedChecksum
    ) {
        this(null, fileName, fileContent, fileSize, fileHash, expectedChecksum, false, ProcessingMode.AUTO);
    }

    /**
     * Legacy constructor for backward compatibility.
     */
    public UploadMasterListFileCommand(
            String fileName,
            byte[] fileContent,
            Long fileSize,
            String fileHash,
            String expectedChecksum,
            ProcessingMode processingMode
    ) {
        this(null, fileName, fileContent, fileSize, fileHash, expectedChecksum, false, processingMode);
    }

    /**
     * 검증
     */
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
        if (!fileName.toLowerCase().endsWith(".ml")) {
            throw new IllegalArgumentException("fileName must end with .ml");
        }
    }
}
