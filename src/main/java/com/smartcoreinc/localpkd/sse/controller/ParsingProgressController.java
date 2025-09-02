package com.smartcoreinc.localpkd.sse.controller;

import java.time.Duration;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.smartcoreinc.localpkd.sse.SSEUtils;
import com.smartcoreinc.localpkd.sse.broker.ParsingProgressBroker;
import com.smartcoreinc.localpkd.sse.event.LdapSaveEvent;
import com.smartcoreinc.localpkd.sse.event.ParsingEvent;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Controller
@RequestMapping("/parsing-progress")
public class ParsingProgressController {

    private final ParsingProgressBroker parsingProgressBroker;

    public ParsingProgressController(ParsingProgressBroker parsingProgressBroker) {
        this.parsingProgressBroker = parsingProgressBroker;
    }

    @GetMapping
    public Flux<ServerSentEvent<String>> ldifParsingProgress(HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        String clientIP = getClientIP(request);
        log.info("LDIF Parsing progress SSE connection - SessionID: {}, ClientIP: {}", sessionId, clientIP);

        // 연결 확인 이벤트
        Flux<ServerSentEvent<String>> connectionEvent = Flux.just(
            ServerSentEvent.<String>builder()
                .id("connection-" + System.currentTimeMillis())
                .event("connection-established")
                .data("LDIF Parsing progress connection established for task: " + sessionId)
                .build()
        );

        // 하트비트
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(25))
                .map(sequence -> ServerSentEvent.<String>builder()
                        .id("heartbeat-" + sequence)
                        .event("heartbeat")
                        .data("ping")
                        .build())
                .onErrorResume(throwable -> {
                    if (isClientDisconnection(throwable)) {
                        log.debug("Client disconnected during LDIF Parsing heartbeat: {}", throwable.getMessage());
                    } else {
                        log.warn("LDIF Parsing heartbeat error: {}", throwable.getMessage());
                    }
                    return Flux.empty();
                });
        
        // LDAP 저장 진행률 스트림
        Flux<ServerSentEvent<String>> ldapUpdates = parsingProgressBroker
                .subscribeToParsingSession(sessionId)
                .map(this::createParsingProgressSSE)
                .onErrorResume(throwable -> {
                    if (isClientDisconnection(throwable)) {
                        log.debug("Client disconnected during LDIF Parsing updates: {}", throwable.getMessage());
                    } else {
                        log.error("LDIF Parsing updates error for session {}: {}", sessionId, throwable.getMessage());
                    }
                    return Flux.empty();
                });

        return Flux.merge(connectionEvent, heartbeat, ldapUpdates)
                .doOnSubscribe(subscription -> {
                    log.info("LDIF Parsing SSE subscription started - Session: {}, Client: {}", sessionId, clientIP);
                })
                .doOnCancel(() -> {
                    log.info("LDIF Parsing SSE connection cancelled - Session: {}, Client: {}", sessionId, clientIP);
                })
                .doOnError(throwable -> {
                    if (isClientDisconnection(throwable)) {
                        log.debug("LDIF Parsing SSE client disconnected - Session: {}, Reason: {}", 
                                sessionId, throwable.getMessage());
                    } else {
                        log.error("LDIF Parsing SSE connection error - Session: {}, Error: {}", 
                                sessionId, throwable.getMessage(), throwable);
                    }
                })
                .doFinally(signalType -> {
                    log.info("LDIF Parsing SSE connection finished - Session: {}, Signal: {}, Remaining subscribers: {}", 
                            sessionId, signalType, parsingProgressBroker.getActiveSubscriberCount());
                })
                .onErrorResume(throwable -> {
                    if (isClientDisconnection(throwable)) {
                        log.debug("Handling LDIF Parsing client disconnection gracefully");
                    } else {
                        log.warn("LDIF Parsing SSE stream error, allowing HTMX to handle reconnection: {}", 
                                throwable.getMessage());
                    }
                    return Flux.empty();
                });
    }

    /**
     * LDAP 저장 이벤트를 HTMX용 SSE 이벤트로 변환
     */
    private ServerSentEvent<String> createParsingProgressSSE(ParsingEvent event) {
        try {
            int progressRate = (int) Math.round(event.progress() * 100);
            boolean isComplete = event.isCompleted();
            boolean hasErrors = event.hasErrors();

            // 상태에 따른 스타일링
            String statusClass = hasErrors ? "from-red-50 to-red-50 border-red-200" :
                               isComplete ? "from-green-50 to-emerald-50 border-green-200" : 
                               "from-indigo-50 to-blue-50 border-indigo-200";
                               
            String progressBarClass = hasErrors ? "from-red-500 to-red-600" :
                                    isComplete ? "from-green-500 to-green-600" : 
                                    "from-indigo-500 to-indigo-600";
                                    
            String textClass = hasErrors ? "text-red-800" :
                             isComplete ? "text-green-800" : 
                             "text-indigo-800";

            String statusText = hasErrors ? "오류 발생" :
                              isComplete ? "저장 완료" : 
                              "저장 중";

            String iconClass = hasErrors ? "fa-exclamation-triangle" :
                             isComplete ? "fa-check-circle" : 
                             "fa-spinner fa-spin";

            String paringHtml = String.format(
                    """
                    <div class="bg-gradient-to-br %s border rounded-lg p-6">
                      <div class="flex items-center justify-between mb-3">
                        <span class="%s font-semibold text-lg">LDIF 파싱 진행률</span>
                        <span class="%s font-bold text-lg flex items-center">
                          <i class="fas %s mr-2"></i>%s (%d%%)
                        </span>
                      </div>
                      <div class="w-full bg-gray-200 rounded-full h-6 overflow-hidden shadow-inner">
                        <div class="bg-gradient-to-r %s h-6 rounded-full transition-all duration-300 flex items-center justify-center text-sm text-white font-semibold shadow-sm" 
                             style="width: %d%%">
                          %s
                        </div>
                      </div>
                      <div class="mt-3 text-sm %s">
                        <div class="flex items-center">
                          <i class="fas fa-info-circle mr-2"></i>
                          <span>%s</span>
                        </div>
                      </div>
                    </div>
                    """,
                    statusClass, textClass, textClass, iconClass, statusText, progressRate,
                    progressBarClass, progressRate, 
                    progressRate < 100 ? progressRate + "%" : "완료",
                    textClass, event.processedEntries(),
                    textClass, event.message()
            );

            return ServerSentEvent.<String>builder()
                    .id("ldif-parsing-" + System.currentTimeMillis())
                    .event("parsing-progress")
                    .data(paringHtml)
                    .build();

        } catch (Exception e) {
            log.error("Error creating LDIF parsing progress SSE for session {}: {}", event.sessionId(), e.getMessage(), e);
            return createErrorSSE(event.sessionId(), e.getMessage());
        }
    }
    
    /**
     * 오류 SSE 이벤트 생성
     */
    private ServerSentEvent<String> createErrorSSE(String sessionId, String errorMessage) {
        String errorHtml = String.format(
                """
                <div class="bg-red-50 border border-red-200 text-red-700 px-4 py-2 rounded-lg">
                    <div class="flex items-center">
                        <i class="fas fa-exclamation-triangle mr-2 text-red-500"></i>
                        <div>
                            <strong>LDAP 저장 오류:</strong> %s
                        </div>
                    </div>
                </div>
                """, errorMessage);

        return ServerSentEvent.<String>builder()
                .id("error-" + System.currentTimeMillis())
                .event("parsing-error")
                .data(errorHtml)
                .build();
    }
    
    /**
     * 클라이언트 연결 해제인지 확인
     */
    private boolean isClientDisconnection(Throwable throwable) {
        String message = throwable.getMessage();
        return message != null && (
            message.contains("Broken pipe") ||
            message.contains("Connection reset") ||
            message.contains("AsyncRequestNotUsableException") ||
            message.contains("ClientAbortException")
        );
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
