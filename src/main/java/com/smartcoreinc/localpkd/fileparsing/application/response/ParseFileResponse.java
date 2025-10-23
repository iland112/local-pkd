package com.smartcoreinc.localpkd.fileparsing.application.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ParseFileResponse - 파일 파싱 응답
 *
 * <p>파일 파싱 결과를 담는 Response DTO입니다.</p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * // 성공 응답
 * ParseFileResponse response = ParseFileResponse.success(
 *     parsedFileId,
 *     uploadId,
 *     "CSCA_COMPLETE_LDIF",
 *     "PARSED",
 *     800,  // certificateCount
 *     50,   // crlCount
 *     2,    // errorCount
 *     1500L // durationMillis
 * );
 *
 * // 실패 응답
 * ParseFileResponse response = ParseFileResponse.failure(
 *     uploadId,
 *     "CSCA_COMPLETE_LDIF",
 *     "Invalid LDIF format"
 * );
 * </pre>
 */
@Builder
public record ParseFileResponse(
    /**
     * 파싱 파일 ID (성공 시)
     */
    UUID parsedFileId,

    /**
     * 업로드 파일 ID
     */
    UUID uploadId,

    /**
     * 파일 포맷
     */
    String fileFormat,

    /**
     * 파싱 상태
     */
    String status,

    /**
     * 파싱 시작 일시
     */
    LocalDateTime parsingStartedAt,

    /**
     * 파싱 완료 일시
     */
    LocalDateTime parsingCompletedAt,

    /**
     * 추출된 인증서 개수
     */
    int certificateCount,

    /**
     * 추출된 CRL 개수
     */
    int crlCount,

    /**
     * 오류 개수
     */
    int errorCount,

    /**
     * 파싱 소요 시간 (밀리초)
     */
    long durationMillis,

    /**
     * 성공 여부
     */
    boolean success,

    /**
     * 오류 메시지 (실패 시)
     */
    String errorMessage
) {

    // ========== Static Factory Methods ==========

    /**
     * 성공 응답 생성
     *
     * @param parsedFileId 파싱 파일 ID
     * @param uploadId 업로드 파일 ID
     * @param fileFormat 파일 포맷
     * @param status 파싱 상태
     * @param certificateCount 인증서 개수
     * @param crlCount CRL 개수
     * @param errorCount 오류 개수
     * @param durationMillis 소요 시간
     * @return ParseFileResponse
     */
    public static ParseFileResponse success(
        UUID parsedFileId,
        UUID uploadId,
        String fileFormat,
        String status,
        LocalDateTime parsingStartedAt,
        LocalDateTime parsingCompletedAt,
        int certificateCount,
        int crlCount,
        int errorCount,
        long durationMillis
    ) {
        return ParseFileResponse.builder()
            .parsedFileId(parsedFileId)
            .uploadId(uploadId)
            .fileFormat(fileFormat)
            .status(status)
            .parsingStartedAt(parsingStartedAt)
            .parsingCompletedAt(parsingCompletedAt)
            .certificateCount(certificateCount)
            .crlCount(crlCount)
            .errorCount(errorCount)
            .durationMillis(durationMillis)
            .success(true)
            .errorMessage(null)
            .build();
    }

    /**
     * 실패 응답 생성
     *
     * @param uploadId 업로드 파일 ID
     * @param fileFormat 파일 포맷
     * @param errorMessage 오류 메시지
     * @return ParseFileResponse
     */
    public static ParseFileResponse failure(
        UUID uploadId,
        String fileFormat,
        String errorMessage
    ) {
        return ParseFileResponse.builder()
            .parsedFileId(null)
            .uploadId(uploadId)
            .fileFormat(fileFormat)
            .status("FAILED")
            .parsingStartedAt(null)
            .parsingCompletedAt(null)
            .certificateCount(0)
            .crlCount(0)
            .errorCount(0)
            .durationMillis(0)
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }

    /**
     * 전체 추출 항목 수 (인증서 + CRL)
     */
    public int getTotalExtracted() {
        return certificateCount + crlCount;
    }

    /**
     * 파싱 성공 여부 (성공이고 오류율 < 5%)
     */
    public boolean isSuccessful() {
        if (!success) return false;
        if (getTotalExtracted() == 0) return false;
        double errorRate = (double) errorCount / getTotalExtracted() * 100.0;
        return errorRate < 5.0;
    }

    /**
     * 소요 시간 (초)
     */
    public double getDurationSeconds() {
        return durationMillis / 1000.0;
    }
}
