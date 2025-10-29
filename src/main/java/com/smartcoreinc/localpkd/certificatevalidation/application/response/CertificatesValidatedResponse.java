package com.smartcoreinc.localpkd.certificatevalidation.application.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CertificatesValidatedResponse - 인증서 검증 응답 DTO
 *
 * <p><b>Use Case Response</b>: 인증서 검증 결과를 반환합니다.</p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * CertificatesValidatedResponse response = validateCertificatesUseCase.execute(command);
 *
 * if (response.success()) {
 *     log.info("Validation completed: {} valid, {} invalid",
 *         response.validCertificateCount(), response.invalidCertificateCount());
 * } else {
 *     log.error("Validation failed: {}", response.errorMessage());
 * }
 * </pre>
 */
@Builder
public record CertificatesValidatedResponse(
    /**
     * 성공 여부
     */
    boolean success,

    /**
     * 원본 업로드 파일 ID
     */
    UUID uploadId,

    /**
     * 검증 성공한 인증서 개수
     */
    int validCertificateCount,

    /**
     * 검증 실패한 인증서 개수
     */
    int invalidCertificateCount,

    /**
     * 검증 성공한 CRL 개수
     */
    int validCrlCount,

    /**
     * 검증 실패한 CRL 개수
     */
    int invalidCrlCount,

    /**
     * 검증 완료 시각
     */
    LocalDateTime validatedAt,

    /**
     * 검증 소요 시간 (milliseconds)
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
    public static CertificatesValidatedResponse success(
        UUID uploadId,
        int validCertificateCount,
        int invalidCertificateCount,
        int validCrlCount,
        int invalidCrlCount,
        LocalDateTime validatedAt,
        long durationMillis
    ) {
        return CertificatesValidatedResponse.builder()
            .success(true)
            .uploadId(uploadId)
            .validCertificateCount(validCertificateCount)
            .invalidCertificateCount(invalidCertificateCount)
            .validCrlCount(validCrlCount)
            .invalidCrlCount(invalidCrlCount)
            .validatedAt(validatedAt)
            .durationMillis(durationMillis)
            .errorMessage(null)
            .build();
    }

    /**
     * 실패 응답 생성
     */
    public static CertificatesValidatedResponse failure(
        UUID uploadId,
        String errorMessage
    ) {
        return CertificatesValidatedResponse.builder()
            .success(false)
            .uploadId(uploadId)
            .validCertificateCount(0)
            .invalidCertificateCount(0)
            .validCrlCount(0)
            .invalidCrlCount(0)
            .validatedAt(null)
            .durationMillis(0)
            .errorMessage(errorMessage)
            .build();
    }

    /**
     * 검증된 총 항목 수
     */
    public int getTotalValidated() {
        return validCertificateCount + invalidCertificateCount + validCrlCount + invalidCrlCount;
    }

    /**
     * 검증 성공 항목 수
     */
    public int getTotalValid() {
        return validCertificateCount + validCrlCount;
    }

    /**
     * 검증 성공률 (%)
     */
    public int getSuccessRate() {
        int total = getTotalValidated();
        if (total == 0) {
            return 0;
        }
        return (int) ((getTotalValid() / (double) total) * 100);
    }

    /**
     * 모든 항목이 유효한지 확인
     */
    public boolean isAllValid() {
        return invalidCertificateCount == 0 && invalidCrlCount == 0;
    }
}
