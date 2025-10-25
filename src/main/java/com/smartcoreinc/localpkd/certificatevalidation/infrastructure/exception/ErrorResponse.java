package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ErrorResponse - REST API 에러 응답 DTO
 *
 * <p><b>목적</b>: 모든 REST API 예외를 일관된 형식으로 응답</p>
 *
 * <p><b>응답 구조</b>:</p>
 * <pre>{@code
 * {
 *   "success": false,
 *   "error": {
 *     "code": "INVALID_REQUEST",
 *     "message": "요청 파라미터가 유효하지 않습니다",
 *     "details": [
 *       {
 *         "field": "certificateId",
 *         "message": "certificateId는 필수 필드입니다"
 *       }
 *     ],
 *     "timestamp": "2025-10-25T14:30:00"
 *   },
 *   "path": "/api/validate",
 *   "status": 400
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * 전체 요청 성공 여부 (항상 false)
     */
    @Default
    private boolean success = false;

    /**
     * 에러 정보
     */
    private Error error;

    /**
     * 요청 경로
     */
    private String path;

    /**
     * HTTP 상태 코드
     */
    private int status;

    /**
     * 트레이스 ID (로그 추적용)
     */
    private String traceId;

    /**
     * Error - 에러 상세 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Error {

        /**
         * 에러 코드 (INVALID_REQUEST, NOT_FOUND, INTERNAL_SERVER_ERROR 등)
         */
        private String code;

        /**
         * 에러 메시지 (사용자 친화적)
         */
        private String message;

        /**
         * 상세 에러 정보 (필드별 유효성 검사 실패 등)
         */
        private List<ErrorDetail> details;

        /**
         * 에러 발생 시간
         */
        private LocalDateTime timestamp;
    }

    /**
     * ErrorDetail - 필드별 에러 상세 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {

        /**
         * 필드명 (null인 경우 전체 요청 에러)
         */
        private String field;

        /**
         * 에러 메시지
         */
        private String message;

        /**
         * 거부된 값 (optional)
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Object rejectedValue;

        /**
         * 검증 규칙 (optional)
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String validationRule;
    }

    /**
     * 정적 팩토리 메서드 - 유효성 검사 오류
     *
     * @param message 에러 메시지
     * @return ErrorResponse
     */
    public static ErrorResponse badRequest(String message) {
        return ErrorResponse.builder()
                .success(false)
                .error(Error.builder()
                        .code("INVALID_REQUEST")
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build())
                .status(400)
                .build();
    }

    /**
     * 정적 팩토리 메서드 - 유효성 검사 오류 (상세 정보 포함)
     *
     * @param message 에러 메시지
     * @param details 필드별 에러 정보
     * @return ErrorResponse
     */
    public static ErrorResponse badRequest(String message, List<ErrorDetail> details) {
        return ErrorResponse.builder()
                .success(false)
                .error(Error.builder()
                        .code("INVALID_REQUEST")
                        .message(message)
                        .details(details)
                        .timestamp(LocalDateTime.now())
                        .build())
                .status(400)
                .build();
    }

    /**
     * 정적 팩토리 메서드 - 리소스 미발견
     *
     * @param message 에러 메시지
     * @return ErrorResponse
     */
    public static ErrorResponse notFound(String message) {
        return ErrorResponse.builder()
                .success(false)
                .error(Error.builder()
                        .code("NOT_FOUND")
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build())
                .status(404)
                .build();
    }

    /**
     * 정적 팩토리 메서드 - 지원하지 않는 미디어 타입
     *
     * @param message 에러 메시지
     * @return ErrorResponse
     */
    public static ErrorResponse unsupportedMediaType(String message) {
        return ErrorResponse.builder()
                .success(false)
                .error(Error.builder()
                        .code("UNSUPPORTED_MEDIA_TYPE")
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build())
                .status(415)
                .build();
    }

    /**
     * 정적 팩토리 메서드 - 내부 서버 오류
     *
     * @param message 에러 메시지
     * @return ErrorResponse
     */
    public static ErrorResponse internalServerError(String message) {
        return ErrorResponse.builder()
                .success(false)
                .error(Error.builder()
                        .code("INTERNAL_SERVER_ERROR")
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build())
                .status(500)
                .build();
    }

    /**
     * 정적 팩토리 메서드 - 도메인 규칙 위반
     *
     * @param errorCode 에러 코드
     * @param message 에러 메시지
     * @return ErrorResponse
     */
    public static ErrorResponse domainException(String errorCode, String message) {
        return ErrorResponse.builder()
                .success(false)
                .error(Error.builder()
                        .code(errorCode)
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build())
                .status(400)
                .build();
    }
}
