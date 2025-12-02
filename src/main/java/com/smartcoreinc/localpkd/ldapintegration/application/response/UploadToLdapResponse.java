package com.smartcoreinc.localpkd.ldapintegration.application.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * UploadToLdapResponse - LDAP 서버 업로드 응답 DTO
 *
 * <p><b>Use Case Response</b>: LDAP 업로드 결과를 반환합니다.</p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * UploadToLdapResponse response = uploadToLdapUseCase.execute(command);
 *
 * if (response.success()) {
 *     log.info("LDAP upload completed: {} uploaded, {} failed",
 *         response.uploadedCount(), response.failedCount());
 * } else {
 *     log.error("LDAP upload failed: {}", response.errorMessage());
 * }
 * </pre>
 */
@Builder
public record UploadToLdapResponse(
    /**
     * 성공 여부
     */
    boolean success,

    /**
     * 원본 업로드 파일 ID
     */
    UUID uploadId,

    /**
     * LDAP에 성공적으로 업로드된 인증서 개수
     */
    int uploadedCertificateCount,

    /**
     * LDAP에 성공적으로 업로드된 CRL 개수
     */
    int uploadedCrlCount,

    /**
     * LDAP에 성공적으로 업로드된 Master List 개수
     */
    int uploadedMasterListCount,

    /**
     * LDAP 업로드 실패한 인증서 개수
     */
    int failedCertificateCount,

    /**
     * LDAP 업로드 실패한 CRL 개수
     */
    int failedCrlCount,

    /**
     * LDAP 업로드 실패한 Master List 개수
     */
    int failedMasterListCount,

    /**
     * LDAP 업로드 완료 시각
     */
    LocalDateTime uploadedAt,

    /**
     * LDAP 업로드 소요 시간 (milliseconds)
     */
    long durationMillis,

    /**
     * 오류 메시지
     */
    String errorMessage
) {

    /**
     * 성공 응답 생성
     */
    public static UploadToLdapResponse success(
        UUID uploadId,
        int uploadedCertificateCount,
        int uploadedCrlCount,
        int uploadedMasterListCount,
        int failedCertificateCount,
        int failedCrlCount,
        int failedMasterListCount,
        LocalDateTime uploadedAt,
        long durationMillis
    ) {
        return UploadToLdapResponse.builder()
            .success(true)
            .uploadId(uploadId)
            .uploadedCertificateCount(uploadedCertificateCount)
            .uploadedCrlCount(uploadedCrlCount)
            .uploadedMasterListCount(uploadedMasterListCount)
            .failedCertificateCount(failedCertificateCount)
            .failedCrlCount(failedCrlCount)
            .failedMasterListCount(failedMasterListCount)
            .uploadedAt(uploadedAt)
            .durationMillis(durationMillis)
            .errorMessage(null)
            .build();
    }

    /**
     * 실패 응답 생성
     */
    public static UploadToLdapResponse failure(
        UUID uploadId,
        String errorMessage
    ) {
        return UploadToLdapResponse.builder()
            .success(false)
            .uploadId(uploadId)
            .uploadedCertificateCount(0)
            .uploadedCrlCount(0)
            .uploadedMasterListCount(0)
            .failedCertificateCount(0)
            .failedCrlCount(0)
            .failedMasterListCount(0)
            .uploadedAt(null)
            .durationMillis(0)
            .errorMessage(errorMessage)
            .build();
    }

    /**
     * 업로드된 총 항목 수
     */
    public int getTotalUploaded() {
        return uploadedCertificateCount + uploadedCrlCount + uploadedMasterListCount;
    }

    /**
     * 업로드 실패한 총 항목 수
     */
    public int getTotalFailed() {
        return failedCertificateCount + failedCrlCount + failedMasterListCount;
    }

    /**
     * 처리된 총 항목 수
     */
    public int getTotalProcessed() {
        return getTotalUploaded() + getTotalFailed();
    }

    /**
     * 업로드 성공률 (%)
     */
    public int getSuccessRate() {
        int total = getTotalProcessed();
        if (total == 0) {
            return 0;
        }
        return (int) ((getTotalUploaded() / (double) total) * 100);
    }

    /**
     * 모든 항목이 성공적으로 업로드되었는지 확인
     */
    public boolean isAllUploaded() {
        return failedCertificateCount == 0 && failedCrlCount == 0 && failedMasterListCount == 0;
    }
}
