package com.smartcoreinc.localpkd.certificatevalidation.application.response;

import lombok.Builder;
import java.util.List;
import java.util.Map;

/**
 * UploadCrlResponse - CRL LDAP 업로드 응답
 *
 * <p><b>목적</b>: CRL LDAP 업로드 결과를 나타내는 응답 DTO입니다.</p>
 *
 * <p><b>응답 형식</b>:</p>
 * <pre>{@code
 * {
 *   "success": true,
 *   "message": "CRL upload completed: 5 uploaded, 0 failed",
 *   "totalCount": 5,
 *   "successCount": 5,
 *   "failedCount": 0,
 *   "uploadedDns": [
 *     "cn=CSCA-QA-001,ou=crl,dc=data,...",
 *     "cn=CSCA-NZ-001,ou=crl,dc=data,..."
 *   ],
 *   "failedCrlIds": [],
 *   "errorDetails": {}
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-13
 */
@Builder
public record UploadCrlResponse(
    /**
     * 업로드 성공 여부
     * true: 모든 CRL 업로드 성공
     * false: 하나 이상의 CRL 업로드 실패
     */
    boolean success,

    /**
     * 결과 메시지
     * 예: "CRL upload completed: 5 uploaded, 0 failed"
     */
    String message,

    /**
     * 업로드 대상 CRL 총 개수
     */
    int totalCount,

    /**
     * 성공한 CRL 개수
     */
    int successCount,

    /**
     * 실패한 CRL 개수
     */
    int failedCount,

    /**
     * 성공적으로 업로드된 CRL의 LDAP DN 목록
     */
    List<String> uploadedDns,

    /**
     * 업로드 실패한 CRL ID 목록
     */
    List<java.util.UUID> failedCrlIds,

    /**
     * 오류 상세 정보 (CRL ID → 오류 메시지)
     */
    Map<java.util.UUID, String> errorDetails
) {
    /**
     * 성공 응답 생성 (정적 팩토리 메서드)
     */
    public static UploadCrlResponse success(
        int totalCount,
        List<String> uploadedDns
    ) {
        return UploadCrlResponse.builder()
            .success(true)
            .message(String.format("CRL upload completed: %d uploaded, 0 failed", totalCount))
            .totalCount(totalCount)
            .successCount(totalCount)
            .failedCount(0)
            .uploadedDns(uploadedDns)
            .failedCrlIds(List.of())
            .errorDetails(Map.of())
            .build();
    }

    /**
     * 부분 성공 응답 생성 (정적 팩토리 메서드)
     */
    public static UploadCrlResponse partialSuccess(
        int totalCount,
        int successCount,
        List<String> uploadedDns,
        List<java.util.UUID> failedCrlIds,
        Map<java.util.UUID, String> errorDetails
    ) {
        int failedCount = totalCount - successCount;
        return UploadCrlResponse.builder()
            .success(false)
            .message(String.format(
                "CRL upload partially completed: %d uploaded, %d failed",
                successCount, failedCount
            ))
            .totalCount(totalCount)
            .successCount(successCount)
            .failedCount(failedCount)
            .uploadedDns(uploadedDns)
            .failedCrlIds(failedCrlIds)
            .errorDetails(errorDetails)
            .build();
    }

    /**
     * 실패 응답 생성 (정적 팩토리 메서드)
     */
    public static UploadCrlResponse failure(
        int totalCount,
        String errorMessage
    ) {
        return UploadCrlResponse.builder()
            .success(false)
            .message("CRL upload failed: " + errorMessage)
            .totalCount(totalCount)
            .successCount(0)
            .failedCount(totalCount)
            .uploadedDns(List.of())
            .failedCrlIds(List.of())
            .errorDetails(Map.of())
            .build();
    }

    /**
     * 성공률 계산
     *
     * @return 성공률 (0~100)
     */
    public double getSuccessRate() {
        if (totalCount == 0) {
            return 100.0;
        }
        return (successCount * 100.0) / totalCount;
    }

    /**
     * 완전 성공 여부
     */
    public boolean isFullySuccessful() {
        return success && failedCount == 0;
    }

    /**
     * 부분 성공 여부
     */
    public boolean isPartiallySuccessful() {
        return !success && successCount > 0;
    }

    /**
     * 완전 실패 여부
     */
    public boolean isFullyFailed() {
        return !success && successCount == 0;
    }
}
