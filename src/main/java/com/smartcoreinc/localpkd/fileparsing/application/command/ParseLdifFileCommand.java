package com.smartcoreinc.localpkd.fileparsing.application.command;

import lombok.Builder;

import java.util.UUID;

/**
 * ParseLdifFileCommand - LDIF 파일 파싱 명령
 *
 * <p><b>CQRS Write Side</b>: LDIF 파일을 파싱하여 인증서와 CRL을 추출하는 명령입니다.</p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * ParseLdifFileCommand command = ParseLdifFileCommand.builder()
 *     .uploadId(uploadId)
 *     .fileBytes(ldifBytes)
 *     .fileFormat("CSCA_COMPLETE_LDIF")
 *     .build();
 *
 * command.validate();  // 검증
 *
 * ParseFileResponse response = parseLdifFileUseCase.execute(command);
 * </pre>
 *
 * @see com.smartcoreinc.localpkd.fileparsing.application.usecase.ParseLdifFileUseCase
 */
@Builder
public record ParseLdifFileCommand(
    /**
     * 업로드된 파일 ID (File Upload Context)
     */
    UUID uploadId,

    /**
     * 파일 바이트 배열 (LDIF 파일 내용)
     */
    byte[] fileBytes,

    /**
     * 파일 포맷 (CSCA_COMPLETE_LDIF, CSCA_DELTA_LDIF, EMRTD_COMPLETE_LDIF, EMRTD_DELTA_LDIF)
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
        if (!fileFormat.endsWith("_LDIF")) {
            throw new IllegalArgumentException("fileFormat must be LDIF format");
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
