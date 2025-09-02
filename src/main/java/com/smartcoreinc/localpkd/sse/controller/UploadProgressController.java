package com.smartcoreinc.localpkd.sse.controller;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.smartcoreinc.localpkd.sse.SSEUtils;
import com.smartcoreinc.localpkd.sse.broker.UploadProgressBroker;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Controller
@RequestMapping("/upload-progress")
public class UploadProgressController {

    private final UploadProgressBroker uploadProgressBroker;

    public UploadProgressController(UploadProgressBroker uploadProgressBroker) {
        this.uploadProgressBroker = uploadProgressBroker;
    }

    @GetMapping("/{sessionId}")
    public Flux<ServerSentEvent<String>> uploadProgress(@PathVariable String sessionId, HttpServletRequest request) {
        String clientIP = getClientIP(request);
        log.info("Upload SSE connection - SessionID: {}, ClientIP: {}", sessionId, clientIP);

        return SSEUtils.createSSEStream(sessionId, uploadProgressBroker.subscribeToUploads(sessionId));
    }

    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        
        return request.getRemoteAddr();
    }
}
