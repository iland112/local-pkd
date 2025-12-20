package com.smartcoreinc.localpkd.certificatevalidation.application.response;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * LdapBatchUploadResult - LDAP 배치 업로드 결과 DTO
 *
 * <p>인증서 또는 CRL 배치를 LDAP에 업로드한 결과를 담습니다.</p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * LdapBatchUploadResult result = ldapBatchUploadService.uploadCertificates(certificates);
 * if (result.hasFailures()) {
 *     log.warn("Some uploads failed: {} success, {} failed",
 *         result.successCount(), result.failedCount());
 * }
 * </pre>
 *
 * @param successCount 성공적으로 업로드된 항목 수
 * @param skippedCount 중복으로 건너뛴 항목 수
 * @param failedCount 업로드 실패한 항목 수
 * @param failedIds 업로드 실패한 항목의 ID 목록
 * @param errorMessages 실패 원인 메시지 목록
 */
public record LdapBatchUploadResult(
    int successCount,
    int skippedCount,
    int failedCount,
    List<UUID> failedIds,
    List<String> errorMessages
) {
    /**
     * 모든 항목이 성공적으로 업로드되었는지 확인
     *
     * @return 모든 항목 업로드 성공 여부
     */
    public boolean isAllSuccess() {
        return failedCount == 0;
    }

    /**
     * 실패한 항목이 있는지 확인
     *
     * @return 실패 항목 존재 여부
     */
    public boolean hasFailures() {
        return failedCount > 0;
    }

    /**
     * 총 처리된 항목 수 (성공 + 건너뜀 + 실패)
     *
     * @return 총 처리 항목 수
     */
    public int totalProcessed() {
        return successCount + skippedCount + failedCount;
    }

    /**
     * 성공적인 결과 생성 (모든 항목 업로드 성공)
     *
     * @param successCount 성공 항목 수
     * @param skippedCount 건너뛴 항목 수 (중복)
     * @return LdapBatchUploadResult
     */
    public static LdapBatchUploadResult success(int successCount, int skippedCount) {
        return new LdapBatchUploadResult(
            successCount,
            skippedCount,
            0,
            Collections.emptyList(),
            Collections.emptyList()
        );
    }

    /**
     * 부분 성공 결과 생성 (일부 항목 실패)
     *
     * @param successCount 성공 항목 수
     * @param skippedCount 건너뛴 항목 수
     * @param failedCount 실패 항목 수
     * @param failedIds 실패한 항목 ID 목록
     * @param errorMessages 실패 원인 메시지 목록
     * @return LdapBatchUploadResult
     */
    public static LdapBatchUploadResult partial(
        int successCount,
        int skippedCount,
        int failedCount,
        List<UUID> failedIds,
        List<String> errorMessages
    ) {
        return new LdapBatchUploadResult(
            successCount,
            skippedCount,
            failedCount,
            failedIds != null ? failedIds : Collections.emptyList(),
            errorMessages != null ? errorMessages : Collections.emptyList()
        );
    }

    /**
     * 빈 결과 생성 (업로드할 항목 없음)
     *
     * @return LdapBatchUploadResult
     */
    public static LdapBatchUploadResult empty() {
        return new LdapBatchUploadResult(0, 0, 0, Collections.emptyList(), Collections.emptyList());
    }
}
