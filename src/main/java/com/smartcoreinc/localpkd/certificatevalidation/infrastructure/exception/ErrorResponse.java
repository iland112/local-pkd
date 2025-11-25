package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @Default
    private boolean success = false;
    private Error error;
    private String path;
    private int status;
    private String traceId;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Error {
        private String code;
        private String message;
        private List<ErrorDetail> details;
        private Object data; // Added for generic details
        private LocalDateTime timestamp;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        private String field;
        private String message;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Object rejectedValue;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String validationRule;
    }

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

    public static ErrorResponse conflict(String errorCode, String message, Object data) {
        return ErrorResponse.builder()
                .success(false)
                .error(Error.builder()
                        .code(errorCode)
                        .message(message)
                        .data(data)
                        .timestamp(LocalDateTime.now())
                        .build())
                .status(409)
                .build();
    }

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