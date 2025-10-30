package com.smartcoreinc.localpkd.certificatevalidation.application.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * UploadToLdapResponse - LDAP 업로드 응답 (CQRS Read Side)
 *
 * <p><b>목적</b>: LDAP 업로드 작업의 결과를 반환합니다.</p>
 *
 * <p><b>응답 정보</b>:
 * <ul>
 *   <li>uploadId: 파일 업로드 ID</li>
 *   <li>totalCount: 업로드 시도 인증서 개수</li>
 *   <li>successCount: 성공한 인증서 개수</li>
 *   <li>failureCount: 실패한 인증서 개수</li>
 *   <li>successRate: 성공률 (%)</li>
 *   <li>uploadedDns: 업로드된 LDAP DN 목록</li>
 *   <li>failedCertificateIds: 실패한 인증서 ID 목록</li>
 *   <li>errorMessage: 오류 메시지 (있으면)</li>
 *   <li>completedAt: 완료 시간</li>
 * </ul>
 * </p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * UploadToLdapResponse response = uploadToLdapUseCase.execute(command);
 *
 * if (response.isSuccess()) {
 *     log.info("LDAP upload success: {}/{} certificates",
 *         response.getSuccessCount(),
 *         response.getTotalCount()
 *     );
 *     response.getUploadedDns().forEach(dn -> log.debug("Uploaded: {}", dn));
 * } else {
 *     log.error("LDAP upload failed: {}", response.getErrorMessage());
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-30 (Phase 17 Task 1.6)
 */
@Builder
@Getter
public class UploadToLdapResponse {

    private final UUID uploadId;                // File Upload ID
    private final int totalCount;              // Total certificates attempted
    private final int successCount;            // Successfully uploaded
    private final int failureCount;            // Upload failures
    private final double successRate;          // Success percentage
    private final List<String> uploadedDns;    // Uploaded LDAP DNs
    private final List<UUID> failedCertificateIds;  // Failed certificate IDs
    private final String errorMessage;         // Error message (if any)
    private final LocalDateTime completedAt;   // Completion time
    private final boolean success;             // Overall success flag

    /**
     * 전체 성공 여부
     *
     * @return 모든 인증서가 성공하면 true
     */
    public boolean isSuccess() {
        return success && failureCount == 0;
    }

    /**
     * 부분 성공 여부
     *
     * @return 일부 성공했으면 true
     */
    public boolean isPartialSuccess() {
        return success && successCount > 0 && failureCount > 0;
    }

    /**
     * 전체 실패 여부
     *
     * @return 모든 인증서가 실패했으면 true
     */
    public boolean isFailure() {
        return !success || successCount == 0;
    }

    /**
     * 성공 응답 생성
     *
     * @param uploadId Upload ID
     * @param totalCount 총 개수
     * @param uploadedDns 업로드된 DN 목록
     * @return UploadToLdapResponse (성공)
     */
    public static UploadToLdapResponse success(UUID uploadId, int totalCount, List<String> uploadedDns) {
        return UploadToLdapResponse.builder()
            .uploadId(uploadId)
            .totalCount(totalCount)
            .successCount(uploadedDns.size())
            .failureCount(0)
            .successRate(100.0)
            .uploadedDns(uploadedDns)
            .failedCertificateIds(new ArrayList<>())
            .errorMessage(null)
            .completedAt(LocalDateTime.now())
            .success(true)
            .build();
    }

    /**
     * 부분 성공 응답 생성
     *
     * @param uploadId Upload ID
     * @param totalCount 총 개수
     * @param uploadedDns 성공한 DN 목록
     * @param failedCertificateIds 실패한 ID 목록
     * @return UploadToLdapResponse (부분 성공)
     */
    public static UploadToLdapResponse partialSuccess(
            UUID uploadId,
            int totalCount,
            List<String> uploadedDns,
            List<UUID> failedCertificateIds) {

        int successCount = uploadedDns.size();
        int failureCount = failedCertificateIds.size();
        double successRate = totalCount > 0 ? (double) successCount / totalCount * 100 : 0;

        return UploadToLdapResponse.builder()
            .uploadId(uploadId)
            .totalCount(totalCount)
            .successCount(successCount)
            .failureCount(failureCount)
            .successRate(successRate)
            .uploadedDns(uploadedDns)
            .failedCertificateIds(failedCertificateIds)
            .errorMessage(null)
            .completedAt(LocalDateTime.now())
            .success(true)
            .build();
    }

    /**
     * 실패 응답 생성
     *
     * @param uploadId Upload ID
     * @param errorMessage 오류 메시지
     * @return UploadToLdapResponse (실패)
     */
    public static UploadToLdapResponse failure(UUID uploadId, String errorMessage) {
        return UploadToLdapResponse.builder()
            .uploadId(uploadId)
            .totalCount(0)
            .successCount(0)
            .failureCount(0)
            .successRate(0)
            .uploadedDns(new ArrayList<>())
            .failedCertificateIds(new ArrayList<>())
            .errorMessage(errorMessage)
            .completedAt(LocalDateTime.now())
            .success(false)
            .build();
    }

    /**
     * 연결 오류 응답 생성
     *
     * @param uploadId Upload ID
     * @param totalCount 시도한 개수
     * @param connectionError 연결 오류 메시지
     * @return UploadToLdapResponse (연결 오류)
     */
    public static UploadToLdapResponse connectionError(UUID uploadId, int totalCount, String connectionError) {
        return UploadToLdapResponse.builder()
            .uploadId(uploadId)
            .totalCount(totalCount)
            .successCount(0)
            .failureCount(totalCount)
            .successRate(0)
            .uploadedDns(new ArrayList<>())
            .failedCertificateIds(new ArrayList<>())
            .errorMessage("LDAP Connection Error: " + connectionError)
            .completedAt(LocalDateTime.now())
            .success(false)
            .build();
    }

    @Override
    public String toString() {
        return String.format(
            "UploadToLdapResponse[uploadId=%s, success=%d/%d (%.1f%%), error=%s]",
            uploadId, successCount, totalCount, successRate,
            errorMessage != null ? errorMessage : "none"
        );
    }
}
