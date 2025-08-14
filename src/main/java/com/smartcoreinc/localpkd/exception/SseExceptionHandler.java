package com.smartcoreinc.localpkd.exception;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class SseExceptionHandler {

    /**
     * SSE 연결 관련 예외 처리
     * AsyncRequestNotUsableException는 클라이언트 연결 종료 시 발생하는 정상적인 상황
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<String> handleAsyncRequestNotUsable(
            AsyncRequestNotUsableException ex, 
            HttpServletRequest request) {
        
        String requestPath = request.getRequestURI();
        log.debug("SSE connection closed for path: {}, reason: {}", requestPath, ex.getMessage());
        
        // SSE 요청인 경우 특별 처리
        if (requestPath.contains("/progress")) {
            // 빈 응답 반환 (클라이언트 연결 종료는 정상 상황)
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body("");
        }
        
        // 다른 경로의 경우 기본 처리
        return ResponseEntity.badRequest()
            .contentType(MediaType.TEXT_PLAIN)
            .body("Request not usable");
    }
    
    /**
     * java.io.IOException: Broken pipe 처리
     */
    @ExceptionHandler(java.io.IOException.class)
    public ResponseEntity<String> handleIOException(
            java.io.IOException ex, 
            HttpServletRequest request) {
        
        String requestPath = request.getRequestURI();
        
        if ("Broken pipe".equals(ex.getMessage()) && requestPath.contains("/progress")) {
            log.debug("Broken pipe for SSE connection: {}", requestPath);
            // SSE 연결의 Broken pipe는 정상적인 클라이언트 종료
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body("");
        }
        
        log.error("IO Exception for path: {}, message: {}", requestPath, ex.getMessage());
        return ResponseEntity.internalServerError()
            .contentType(MediaType.TEXT_PLAIN)
            .body("IO Error occurred");
    }
}