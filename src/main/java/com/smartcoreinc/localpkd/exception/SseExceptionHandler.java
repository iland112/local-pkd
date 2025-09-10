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
     * AsyncRequestNotUsableException 처리
     * SSE 연결이 클라이언트에 의해 중단될 때 발생하는 정상적인 예외
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException ex) {
        // 로그 레벨을 TRACE로 낮춰서 노이즈 감소
        log.trace("SSE connection closed by client: {}", ex.getMessage());
        // 아무것도 반환하지 않음 - 정상적인 연결 종료로 처리
    }
    
    /**
     * 일반적인 SSE 관련 예외 처리
     */
    @ExceptionHandler({
        org.apache.catalina.connector.ClientAbortException.class,
        java.io.IOException.class
    })
    public void handleSSEDisconnection(Exception ex) {
        String message = ex.getMessage();
        if (message != null && (
            message.contains("Broken pipe") ||
            message.contains("Connection reset") ||
            message.contains("Connection closed prematurely") ||
            message.contains("ClientAbortException")
        )) {
            log.trace("SSE client disconnection: {}", ex.getMessage());
        } else {
            log.warn("Unexpected SSE exception: {}", ex.getMessage(), ex);
        }
    }
}