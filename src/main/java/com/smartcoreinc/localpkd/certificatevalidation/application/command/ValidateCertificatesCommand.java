package com.smartcoreinc.localpkd.certificatevalidation.application.command;

import lombok.Builder;

import java.util.UUID;

/**
 * ValidateCertificatesCommand - 인증서 검증 명령 (CQRS Write Side)
 *
 * <p><b>CQRS Write Side</b>: 파싱된 인증서들을 검증하는 명령입니다.</p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * ValidateCertificatesCommand command = ValidateCertificatesCommand.builder()
 *     .uploadId(uploadId)
 *     .parsedFileId(parsedFileId)
 *     .certificateCount(800)
 *     .crlCount(50)
 *     .build();
 *
 * command.validate();  // 검증
 *
 * CertificatesValidatedResponse response = validateCertificatesUseCase.execute(command);
 * </pre>
 *
 * @see com.smartcoreinc.localpkd.certificatevalidation.application.usecase.ValidateCertificatesUseCase
 */
@Builder
public record ValidateCertificatesCommand(
    /**
     * 원본 업로드 파일 ID (File Upload Context)
     */
    UUID uploadId,

    /**
     * 파싱된 파일 ID (File Parsing Context)
     */
    UUID parsedFileId,

    /**
     * 파싱된 인증서 개수
     */
    int certificateCount,

    /**
     * 파싱된 CRL 개수
     */
    int crlCount
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
        if (parsedFileId == null) {
            throw new IllegalArgumentException("parsedFileId must not be null");
        }
        if (certificateCount < 0) {
            throw new IllegalArgumentException("certificateCount must not be negative");
        }
        if (crlCount < 0) {
            throw new IllegalArgumentException("crlCount must not be negative");
        }
        if (certificateCount + crlCount == 0) {
            throw new IllegalArgumentException("At least one certificate or CRL must be present");
        }
    }

    /**
     * 총 처리할 항목 수 (certificates + CRLs)
     */
    public int getTotalCount() {
        return certificateCount + crlCount;
    }
}
