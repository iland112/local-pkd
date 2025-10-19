package com.smartcoreinc.localpkd.fileupload.application.command;

import lombok.Builder;

/**
 * LDIF 파일 업로드 Command
 *
 * <p>LDIF 파일 업로드 요청을 나타내는 Command 객체입니다.</p>
 *
 * <h3>포함 정보</h3>
 * <ul>
 *   <li>파일명</li>
 *   <li>파일 내용 (byte array)</li>
 *   <li>파일 크기 (bytes)</li>
 *   <li>파일 해시 (SHA-256, 중복 검사용)</li>
 *   <li>예상 체크섬 (SHA-1, optional)</li>
 *   <li>강제 업로드 여부 (forceUpload)</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * UploadLdifFileCommand command = UploadLdifFileCommand.builder()
 *     .fileName("icaopkd-002-complete-009410.ldif")
 *     .fileContent(fileBytes)
 *     .fileSize(78643200L)
 *     .fileHash("a1b2c3d4...")
 *     .expectedChecksum("sha1-checksum")  // optional
 *     .forceUpload(false)
 *     .build();
 *
 * UploadFileResponse response = uploadLdifFileUseCase.execute(command);
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Builder
public record UploadLdifFileCommand(
        String fileName,
        byte[] fileContent,
        Long fileSize,
        String fileHash,
        String expectedChecksum,  // optional
        boolean forceUpload
) {
    /**
     * 기본 생성자 (forceUpload = false)
     */
    public UploadLdifFileCommand(
            String fileName,
            byte[] fileContent,
            Long fileSize,
            String fileHash,
            String expectedChecksum
    ) {
        this(fileName, fileContent, fileSize, fileHash, expectedChecksum, false);
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
        if (!fileName.toLowerCase().endsWith(".ldif")) {
            throw new IllegalArgumentException("fileName must end with .ldif");
        }
    }
}
