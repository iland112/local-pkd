package com.smartcoreinc.localpkd.ldapintegration.application.command;

import lombok.Builder;

import java.util.UUID;

/**
 * UploadToLdapCommand - LDAP 서버 업로드 명령 (CQRS Write Side)
 *
 * <p><b>CQRS Write Side</b>: 검증된 인증서들을 LDAP 서버에 업로드하는 명령입니다.</p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * UploadToLdapCommand command = UploadToLdapCommand.builder()
 *     .uploadId(uploadId)
 *     .validCertificateCount(795)
 *     .validCrlCount(48)
 *     .build();
 *
 * command.validate();  // 검증
 *
 * UploadToLdapResponse response = uploadToLdapUseCase.execute(command);
 * </pre>
 *
 * @see com.smartcoreinc.localpkd.ldapintegration.application.usecase.UploadToLdapUseCase
 */
@Builder
public record UploadToLdapCommand(
    /**
     * 원본 업로드 파일 ID (File Upload Context)
     */
    UUID uploadId,

    /**
     * 검증 성공한 인증서 개수
     */
    int validCertificateCount,

    /**
     * 검증 성공한 CRL 개수
     */
    int validCrlCount,

    /**
     * Master List 개수 (LDIF 파일에서 파싱된 국가별 Master List)
     */
    int masterListCount,

    /**
     * LDAP 배치 크기 (한 번에 몇 개씩 업로드할지)
     * 기본값: 100
     */
    int batchSize
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
        if (validCertificateCount < 0) {
            throw new IllegalArgumentException("validCertificateCount must not be negative");
        }
        if (validCrlCount < 0) {
            throw new IllegalArgumentException("validCrlCount must not be negative");
        }
        if (masterListCount < 0) {
            throw new IllegalArgumentException("masterListCount must not be negative");
        }
        if (validCertificateCount + validCrlCount + masterListCount == 0) {
            throw new IllegalArgumentException("At least one certificate, CRL, or Master List must be present");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
    }

    /**
     * 총 업로드할 항목 수 (certificates + CRLs + Master Lists)
     */
    public int getTotalCount() {
        return validCertificateCount + validCrlCount + masterListCount;
    }

    /**
     * 배치 크기 기본값을 적용한 생성자
     */
    public static UploadToLdapCommand create(UUID uploadId, int validCertificateCount, int validCrlCount) {
        return create(uploadId, validCertificateCount, validCrlCount, 0);
    }

    /**
     * Master List 포함 생성자
     */
    public static UploadToLdapCommand create(UUID uploadId, int validCertificateCount, int validCrlCount, int masterListCount) {
        return UploadToLdapCommand.builder()
            .uploadId(uploadId)
            .validCertificateCount(validCertificateCount)
            .validCrlCount(validCrlCount)
            .masterListCount(masterListCount)
            .batchSize(100)  // Default batch size
            .build();
    }
}
