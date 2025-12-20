package com.smartcoreinc.localpkd.fileparsing.application.command;

import lombok.Builder;

import java.util.UUID;

/**
 * ParseMasterListFileCommand - Master List 파일 파싱 명령
 *
 * <p><b>CQRS Write Side</b>: Master List (CMS) 파일을 파싱하여 인증서를 추출하는 명령입니다.</p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * ParseMasterListFileCommand command = ParseMasterListFileCommand.builder()
 *     .uploadId(uploadId)
 *     .fileBytes(mlBytes)
 *     .fileFormat("ML_SIGNED_CMS")
 *     .build();
 *
 * command.validate();  // 검증
 *
 * ParseFileResponse response = parseMasterListFileUseCase.execute(command);
 * </pre>
 *
 * @see com.smartcoreinc.localpkd.fileparsing.application.usecase.ParseMasterListFileUseCase
 */
@Builder
public record ParseMasterListFileCommand(
    /**
     * 업로드된 파일 ID (File Upload Context)
     */
    UUID uploadId,

    /**
     * 파일 바이트 배열 (Master List 파일 내용)
     */
    byte[] fileBytes,

    /**
     * 파일 포맷 (ML_SIGNED_CMS, ML_UNSIGNED)
     */
    String fileFormat
) {

    /**
     * Command 검증
     *
     * @throws IllegalArgumentException 필수 필드가 누락되었거나 잘못된 경우
     */
    public void validate() {
        if (uploadId == null) {
            throw new IllegalArgumentException("uploadId must not be null");
        }
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("fileBytes must not be empty");
        }
        if (fileFormat == null || fileFormat.isBlank()) {
            throw new IllegalArgumentException("fileFormat must not be blank");
        }
        if (!fileFormat.startsWith("ML_")) {
            throw new IllegalArgumentException("fileFormat must be Master List format (ML_*)");
        }
    }

    /**
     * 파일 크기 (bytes)
     */
    public long getFileSizeBytes() {
        return fileBytes != null ? fileBytes.length : 0;
    }

    /**
     * 파일 크기 (사람 친화적 표현)
     */
    public String getFileSizeDisplay() {
        long bytes = getFileSizeBytes();
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
