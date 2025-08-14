package com.smartcoreinc.localpkd.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception ex, HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String contentType = request.getContentType();
        String acceptHeader = request.getHeader("Accept");
        
        log.error("Exception occurred for path: {}, content-type: {}, accept: {}", 
                requestPath, contentType, acceptHeader, ex);
        
        // SSE 요청 확인 (Accept 헤더 또는 경로로 판단)
        boolean isSseRequest = (acceptHeader != null && acceptHeader.contains("text/event-stream")) 
                            || requestPath.contains("/progress");
        
        if (isSseRequest) {
            // SSE 요청의 경우 text/event-stream으로 응답
            log.debug("Handling SSE request exception");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body("event: error\ndata: {\"message\":\"Internal server error\"}\n\n");
        }
        
        // 일반 API 요청의 경우 JSON으로 응답
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Internal Server Error");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("path", requestPath);
        errorResponse.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body(errorResponse);
    }
    
    /**
     * AsyncRequestNotUsableException 전용 처리
     * (이미 SseExceptionHandler에서 처리하지만, 우선순위를 위해 여기서도 처리)
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<String> handleAsyncRequestNotUsable(
            AsyncRequestNotUsableException ex, 
            HttpServletRequest request) {
        
        String requestPath = request.getRequestURI();
        log.debug("Async request not usable for path: {}", requestPath);
        
        if (requestPath.contains("/progress")) {
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body("");
        }
        
        return ResponseEntity.badRequest().body("Request not usable");
    }
}