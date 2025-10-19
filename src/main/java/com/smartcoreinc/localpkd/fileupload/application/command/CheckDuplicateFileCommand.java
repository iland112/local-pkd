package com.smartcoreinc.localpkd.fileupload.application.command;

import lombok.Builder;

/**
 * 중복 파일 검사 Command
 *
 * <p>파일 업로드 전 중복 여부를 검사하는 Command 객체입니다.</p>
 *
 * <h3>포함 정보</h3>
 * <ul>
 *   <li>파일명</li>
 *   <li>파일 크기 (bytes)</li>
 *   <li>파일 해시 (SHA-256)</li>
 *   <li>예상 체크섬 (SHA-1, optional)</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * CheckDuplicateFileCommand command = CheckDuplicateFileCommand.builder()
 *     .fileName("icaopkd-002-complete-009410.ldif")
 *     .fileSize(78643200L)
 *     .fileHash("a1b2c3d4...")
 *     .expectedChecksum("sha1-checksum")  // optional
 *     .build();
 *
 * CheckDuplicateResponse response = checkDuplicateFileUseCase.execute(command);
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Builder
public record CheckDuplicateFileCommand(
        String fileName,
        Long fileSize,
        String fileHash,
        String expectedChecksum  // optional
) {
    /**
     * 검증
     */
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
