package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.exception;

import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * GlobalExceptionHandler - REST API 전역 예외 처리기
 *
 * <p><b>목적</b>: 모든 REST API 예외를 중앙에서 처리하고 일관된 에러 응답 반환</p>
 *
 * <p><b>처리 대상 예외</b>:</p>
 * <ul>
 *   <li>IllegalArgumentException - 요청 파라미터 검증 오류 (400)</li>
 *   <li>DomainException - 도메인 규칙 위반 (400)</li>
 *   <li>HttpMessageNotReadableException - 잘못된 JSON 형식 (400)</li>
 *   <li>HttpMediaTypeNotSupportedException - 지원하지 않는 미디어 타입 (415)</li>
 *   <li>NoHandlerFoundException - 찾을 수 없는 엔드포인트 (404)</li>
 *   <li>AsyncRequestNotUsableException - SSE 클라이언트 연결 종료 (정상 동작, DEBUG)</li>
 *   <li>Exception - 예상치 못한 예외 (500)</li>
 * </ul>
 *
 * <p><b>에러 응답 예시</b>:</p>
 * <pre>{@code
 * HTTP/1.1 400 Bad Request
 * Content-Type: application/json
 *
 * {
 *   "success": false,
 *   "error": {
 *     "code": "INVALID_REQUEST",
 *     "message": "certificateId must not be null or empty",
 *     "timestamp": "2025-10-25T14:30:00"
 *   },
 *   "path": "/api/validate",
 *   "status": 400,
 *   "traceId": "550e8400-e29b-41d4-a716-446655440000"
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * IllegalArgumentException 처리
     *
     * <p>요청 파라미터 검증 실패 또는 비즈니스 로직 기본 검증 오류</p>
     *
     * @param e 예외 객체
     * @param request HTTP 요청
     * @return 400 Bad Request 응답
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException e,
            WebRequest request) {

        log.warn("Invalid argument exception: {}", e.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .error(ErrorResponse.Error.builder()
                        .code("INVALID_REQUEST")
                        .message(e.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build())
                .path(request.getDescription(false).replace("uri=", ""))
                .status(HttpStatus.BAD_REQUEST.value())
                .traceId(UUID.randomUUID().toString())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * DomainException 처리
     *
     * <p>도메인 규칙 위반 또는 도메인 로직 오류</p>
     *
     * @param e 예외 객체
     * @param request HTTP 요청
     * @return 400 Bad Request 응답
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(
            DomainException e,
            WebRequest request) {

        log.warn("Domain exception: code={}, message={}", e.getErrorCode(), e.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .error(ErrorResponse.Error.builder()
                        .code(e.getErrorCode())
                        .message(e.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build())
                .path(request.getDescription(false).replace("uri=", ""))
                .status(HttpStatus.BAD_REQUEST.value())
                .traceId(UUID.randomUUID().toString())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * HttpMediaTypeNotSupportedException 처리
     *
     * <p>요청의 Content-Type이 지원하지 않는 형식인 경우</p>
     *
     * @param e 예외 객체
     * @param request HTTP 요청
     * @return 415 Unsupported Media Type 응답
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException e,
            WebRequest request) {

        log.warn("Unsupported media type: {}", e.getContentType());

        String message = String.format(
                "Content-Type '%s' is not supported. Supported types: application/json",
                e.getContentType());

        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .error(ErrorResponse.Error.builder()
                        .code("UNSUPPORTED_MEDIA_TYPE")
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build())
                .path(request.getDescription(false).replace("uri=", ""))
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value())
                .traceId(UUID.randomUUID().toString())
                .build();

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * HttpMessageNotReadableException 처리
     *
     * <p>요청 본문의 JSON이 잘못된 형식인 경우</p>
     *
     * @param e 예외 객체
     * @param request HTTP 요청
     * @return 400 Bad Request 응답
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e,
            WebRequest request) {

        log.warn("Invalid JSON format: {}", e.getMessage());

        String message = "Invalid JSON format. Please check the request body.";
        if (e.getCause() != null && e.getCause().getMessage() != null) {
            message = "Invalid JSON: " + e.getCause().getMessage();
        }

        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .error(ErrorResponse.Error.builder()
                        .code("INVALID_REQUEST")
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build())
                .path(request.getDescription(false).replace("uri=", ""))
                .status(HttpStatus.BAD_REQUEST.value())
                .traceId(UUID.randomUUID().toString())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * NoHandlerFoundException 처리
     *
     * <p>요청한 엔드포인트를 찾을 수 없는 경우</p>
     *
     * @param e 예외 객체
     * @param request HTTP 요청
     * @return 404 Not Found 응답
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFoundException(
            NoHandlerFoundException e,
            WebRequest request) {

        log.warn("Endpoint not found: {} {}", e.getHttpMethod(), e.getRequestURL());

        String message = String.format(
                "No handler found for %s %s",
                e.getHttpMethod(), e.getRequestURL());

        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .error(ErrorResponse.Error.builder()
                        .code("NOT_FOUND")
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build())
                .path(e.getRequestURL())
                .status(HttpStatus.NOT_FOUND.value())
                .traceId(UUID.randomUUID().toString())
                .build();

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * 일반 Exception 처리 (최후의 방어선)
     *
     * <p>예상치 못한 모든 예외를 처리</p>
     *
     * @param e 예외 객체
     * @param request HTTP 요청
     * @return 500 Internal Server Error 응답
     */

    /**
     * AsyncRequestNotUsableException - SSE 클라이언트 연결 종료 (정상 동작)
     *
     * <p>SSE(Server-Sent Events) 연결이 클라이언트에 의해 종료된 경우 발생합니다.
     * 이는 정상적인 동작이므로 DEBUG 레벨로 로깅하고 응답하지 않습니다.</p>
     *
     * <p><b>발생 시나리오</b>:</p>
     * <ul>
     *   <li>작업 완료 후 클라이언트가 SSE 연결 종료</li>
     *   <li>Heartbeat 스케줄러가 종료된 연결에 메시지 전송 시도</li>
     *   <li>브라우저 탭 닫기 또는 페이지 이동</li>
     * </ul>
     *
     * @param e AsyncRequestNotUsableException
     * @param request HTTP 요청
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsableException(
            AsyncRequestNotUsableException e,
            WebRequest request) {

        // SSE 클라이언트 연결 종료는 정상 동작이므로 DEBUG 레벨로 로깅
        log.debug("SSE client disconnected (normal behavior): {}", e.getMessage());

        // 응답하지 않음 - 이미 연결이 종료되었으므로 ResponseEntity 반환 불가
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(
            Exception e,
            WebRequest request) {

        log.error("Unexpected exception: {}", e.getMessage(), e);

        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .error(ErrorResponse.Error.builder()
                        .code("INTERNAL_SERVER_ERROR")
                        .message("An unexpected error occurred. Please try again later.")
                        .timestamp(LocalDateTime.now())
                        .build())
                .path(request.getDescription(false).replace("uri=", ""))
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .traceId(UUID.randomUUID().toString())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }
}
