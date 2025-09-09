package com.smartcoreinc.localpkd.sse.controller;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.smartcoreinc.localpkd.sse.broker.ParsingProgressBroker;
import com.smartcoreinc.localpkd.sse.event.ParsingEvent;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Controller
@RequestMapping("/parsing-progress")
public class ParsingProgressController {

    private final ParsingProgressBroker parsingProgressBroker;

    // 연결된 클라이언트 추적 (디버깅용)
    private final AtomicInteger connectionCount = new AtomicInteger(0);
    private final Map<String, Long> sessionConnections = new ConcurrentHashMap<>();

    public ParsingProgressController(ParsingProgressBroker parsingProgressBroker) {
        this.parsingProgressBroker = parsingProgressBroker;
    }

    @GetMapping
    public Flux<ServerSentEvent<String>> ldifParsingProgress(HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        String clientIP = getClientIP(request);
        int connectionId = connectionCount.incrementAndGet();
        
        log.info("LDIF Parsing progress SSE connection #{} - SessionID: {}, ClientIP: {}", 
                connectionId, sessionId, clientIP);
        
        // 세션별 연결 시간 추적
        sessionConnections.put(sessionId, System.currentTimeMillis());

        // 연결 확인 이벤트
        Flux<ServerSentEvent<String>> connectionEvent = Flux.just(
            ServerSentEvent.<String>builder()
                .id("connection-" + connectionId)
                .event("connection-established")
                .data(createConnectionEstablishedHtml(sessionId, connectionId))
                .build()
        );

        // 하트비트
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(30))
                .map(sequence -> ServerSentEvent.<String>builder()
                        .id("heartbeat-" + connectionId + "-" + sequence)
                        .event("heartbeat")
                        .data("ping")
                        .build())
                .onErrorResume(throwable -> {
                    if (isClientDisconnection(throwable)) {
                        log.debug("Client #{} disconnected during heartbeat: {}", connectionId, throwable.getMessage());
                    } else {
                        log.warn("LDIF Parsing heartbeat error for connection #{}: {}", connectionId, throwable.getMessage());
                    }
                    return Flux.empty();
                });
        
        // LDAP 파싱 진행률 스트림
        Flux<ServerSentEvent<String>> parsingUpdates = parsingProgressBroker
                .subscribeToParsingSession(sessionId)
                .map(event -> {
                    // log.debug("현재 진행률: {}", event.getProgressPercentage());
                    return createParsingProgressSSE(event, connectionId);
                })
                .onErrorResume(throwable -> {
                    if (isClientDisconnection(throwable)) {
                        log.debug("Client #{} disconnected during parsing updates: {}", connectionId, throwable.getMessage());
                    } else {
                        log.error("LDIF Parsing updates error for session {} (connection #{}): {}", 
                                sessionId, connectionId, throwable.getMessage());
                    }
                    return Flux.empty();
                });
        
        return Flux.merge(connectionEvent, heartbeat, parsingUpdates)
                .doOnSubscribe(subscription -> {
                    log.info("LDIF Parsing SSE subscription started - Session: {}, Connection: #{}, Client: {}", 
                            sessionId, connectionId, clientIP);
                })
                .doOnCancel(() -> {
                    log.info("LDIF Parsing SSE connection #{} cancelled - Session: {}, Client: {}", 
                            connectionId, sessionId, clientIP);
                    cleanupConnection(sessionId, connectionId);
                })
                .doOnError(throwable -> {
                    if (isClientDisconnection(throwable)) {
                        log.debug("LDIF Parsing SSE client #{} disconnected - Session: {}, Reason: {}", 
                                connectionId, sessionId, throwable.getMessage());
                    } else {
                        log.error("LDIF Parsing SSE connection #{} error - Session: {}, Error: {}", 
                                connectionId, sessionId, throwable.getMessage(), throwable);
                    }
                })
                .doFinally(signalType -> {
                    log.info("LDIF Parsing SSE connection #{} finished - Session: {}, Signal: {}, Active sessions: {}", 
                            connectionId, sessionId, signalType, parsingProgressBroker.getActiveSessionCount());
                    cleanupConnection(sessionId, connectionId);
                })
                .onErrorResume(throwable -> {
                    if (isClientDisconnection(throwable)) {
                        log.debug("Handling LDIF Parsing client #{} disconnection gracefully", connectionId);
                    } else {
                        log.warn("LDIF Parsing SSE stream error for connection #{}, allowing HTMX reconnection: {}", 
                                connectionId, throwable.getMessage());
                    }
                    return Flux.empty();
                });
    }

    /**
     * 연결 확인 HTML 생성
     */
    private String createConnectionEstablishedHtml(String sessionId, int connectionId) {
        return String.format("""
            <div class="bg-blue-50 border border-blue-200 text-blue-700 px-4 py-3 rounded-lg">
                <div class="flex items-center">
                    <i class="fas fa-satellite-dish mr-2 text-blue-500"></i>
                    <div>
                        실시간 진행률 연결됨 (연결 #%d)
                        <div class="text-sm text-blue-600 mt-1">세션: %s</div>
                    </div>
                </div>
            </div>
            """, connectionId, sessionId.substring(0, Math.min(8, sessionId.length())));
    }

    /**
     * LDAP 저장 이벤트를 HTMX용 SSE 이벤트로 변환
     */
    private ServerSentEvent<String> createParsingProgressSSE(ParsingEvent event, int connectionId) {
        try {
            int progressRate = (int) Math.round(event.progress() * 100);
            boolean isComplete = event.isCompleted();
            boolean hasErrors = event.hasErrors();

            // 상태에 따른 스타일링 (개선된 색상 구성)
            String statusClass = determineStatusClass(hasErrors, isComplete);
            String progressBarClass = determineProgressBarClass(hasErrors, isComplete);
            String textClass = determineTextClass(hasErrors, isComplete);
            String statusText = determineStatusText(hasErrors, isComplete);
            String iconClass = determineIconClass(hasErrors, isComplete);

            // 통계 정보 포맷팅
            String statisticsInfo = createStatisticsInfo(event);
            String detailInfo = createDetailInfo(event);

            String parsingHtml = String.format(
                    """
                    <div class="bg-gradient-to-br %s border rounded-lg p-6 transition-all duration-300">
                      <div class="flex items-center justify-between mb-4">
                        <div class="flex items-center">
                          <span class="%s font-semibold text-lg">LDIF 파싱 진행률</span>
                          <span class="ml-2 text-sm text-gray-500">(#%d)</span>
                        </div>
                        <span class="%s font-bold text-lg flex items-center">
                          <i class="fas %s mr-2"></i>%s (%d%%)
                        </span>
                      </div>
                      
                      <div class="w-full bg-gray-200 rounded-full h-6 overflow-hidden shadow-inner mb-4">
                        <div class="bg-gradient-to-r %s h-6 rounded-full transition-all duration-500 flex items-center justify-center text-sm text-white font-semibold shadow-sm progress-bar-animation" 
                             style="width: %d%%">
                          %s
                        </div>
                      </div>
                      
                      <div class="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
                        <div class="%s">
                          <div class="flex items-center mb-2">
                            <i class="fas fa-info-circle mr-2"></i>
                            <span class="font-medium">진행 상태</span>
                          </div>
                          <div class="ml-6 space-y-1">
                            <div>처리된 엔트리: <span class="font-semibold">%s</span></div>
                            <div class="text-xs text-gray-600">%s</div>
                          </div>
                        </div>
                        
                        <div class="%s">
                          <div class="flex items-center mb-2">
                            <i class="fas fa-chart-bar mr-2"></i>
                            <span class="font-medium">통계 정보</span>
                          </div>
                          <div class="ml-6 space-y-1">
                            %s
                          </div>
                        </div>
                      </div>
                      
                      %s
                    </div>
                    """,
                    statusClass, textClass, connectionId, textClass, iconClass, statusText, progressRate,
                    progressBarClass, progressRate, 
                    progressRate < 100 ? progressRate + "%" : "완료",
                    textClass, formatNumber(event.processedEntries()), 
                    truncateString(event.message(), 80),
                    textClass, statisticsInfo, detailInfo
            );

            return ServerSentEvent.<String>builder()
                    .id("ldif-parsing-" + connectionId + "-" + System.currentTimeMillis())
                    .event("parsing-progress")
                    .data(parsingHtml)
                    .build();

        } catch (Exception e) {
            log.error("Error creating LDIF parsing progress SSE for session {} (connection #{}): {}", 
                     event.sessionId(), connectionId, e.getMessage(), e);
            return createErrorSSE(event.sessionId(), connectionId, e.getMessage());
        }
    }

    /**
     * 상태 클래스 결정
     */
    private String determineStatusClass(boolean hasErrors, boolean isComplete) {
        if (hasErrors) {
            return "from-red-50 to-red-50 border-red-200";
        } else if (isComplete) {
            return "from-green-50 to-emerald-50 border-green-200";
        } else {
            return "from-indigo-50 to-blue-50 border-indigo-200";
        }
    }

    private String determineProgressBarClass(boolean hasErrors, boolean isComplete) {
        if (hasErrors) {
            return "from-red-500 to-red-600";
        } else if (isComplete) {
            return "from-green-500 to-green-600";
        } else {
            return "from-indigo-500 to-indigo-600";
        }
    }

    private String determineTextClass(boolean hasErrors, boolean isComplete) {
        if (hasErrors) {
            return "text-red-800";
        } else if (isComplete) {
            return "text-green-800";
        } else {
            return "text-indigo-800";
        }
    }

    private String determineStatusText(boolean hasErrors, boolean isComplete) {
        if (hasErrors) {
            return "오류 발생";
        } else if (isComplete) {
            return "파싱 완료";
        } else {
            return "파싱 중";
        }
    }

    private String determineIconClass(boolean hasErrors, boolean isComplete) {
        if (hasErrors) {
            return "fa-exclamation-triangle text-red-500";
        } else if (isComplete) {
            return "fa-check-circle text-green-500";
        } else {
            return "fa-spinner fa-spin text-indigo-500";
        }
    }

    /**
     * 통계 정보 생성
     */
    private String createStatisticsInfo(ParsingEvent event) {
        StringBuilder stats = new StringBuilder();
        
        if (event.errorCount() > 0) {
            stats.append(String.format("<div class=\"text-red-600\">오류: %d개</div>", event.errorCount()));
        }
        
        if (event.warningCount() > 0) {
            stats.append(String.format("<div class=\"text-yellow-600\">경고: %d개</div>", event.warningCount()));
        }
        
        Map<String, Integer> entryTypes = event.entryTypeStats();
        if (entryTypes != null && !entryTypes.isEmpty()) {
            entryTypes.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .limit(3) // 최대 3개만 표시
                .forEach(entry -> stats.append(String.format(
                    "<div class=\"text-xs text-gray-600\">%s: %d</div>", 
                    entry.getKey(), entry.getValue())));
        }
        
        if (stats.length() == 0) {
            stats.append("<div class=\"text-gray-500 text-xs\">통계 계산 중...</div>");
        }
        
        return stats.toString();
    }

    /**
     * 세부 정보 생성
     */
    private String createDetailInfo(ParsingEvent event) {
        if (event.isCompleted()) {
            return String.format("""
                <div class="mt-4 pt-4 border-t border-gray-200">
                  <div class="text-center text-sm text-gray-600">
                    <i class="fas fa-clock mr-2"></i>파싱 완료 시간: %s
                  </div>
                </div>
                """, formatTimestamp(System.currentTimeMillis()));
        } else if (!event.currentDN().isEmpty()) {
            return String.format("""
                <div class="mt-4 pt-4 border-t border-gray-200">
                  <div class="text-xs text-gray-500">
                    <div class="font-medium mb-1">현재 처리 중:</div>
                    <div class="bg-gray-100 p-2 rounded text-xs font-mono break-all">%s</div>
                  </div>
                </div>
                """, truncateString(event.currentDN(), 100));
        }
        return "";
    }
    
    /**
     * 오류 SSE 이벤트 생성
     */
    private ServerSentEvent<String> createErrorSSE(String sessionId, int connectionId, String errorMessage) {
        String errorHtml = String.format(
                """
                <div class="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
                    <div class="flex items-center">
                        <i class="fas fa-exclamation-triangle mr-2 text-red-500"></i>
                        <div>
                            <strong>LDIF 파싱 오류:</strong> %s
                            <div class="text-sm text-red-600 mt-1">연결 #%d, 세션: %s</div>
                        </div>
                    </div>
                </div>
                """, errorMessage, connectionId, sessionId.substring(0, Math.min(8, sessionId.length())));

        return ServerSentEvent.<String>builder()
                .id("error-" + connectionId + "-" + System.currentTimeMillis())
                .event("parsing-error")
                .data(errorHtml)
                .build();
    }
    
    /**
     * 연결 정리
     */
    private void cleanupConnection(String sessionId, int connectionId) {
        try {
            sessionConnections.remove(sessionId);
            log.debug("Connection #{} cleaned up for session: {}", connectionId, sessionId);
        } catch (Exception e) {
            log.error("Error cleaning up connection #{} for session {}: {}", 
                     connectionId, sessionId, e.getMessage());
        }
    }

    /**
     * 숫자 포맷팅
     */
    private String formatNumber(int number) {
        if (number >= 1000000) {
            return String.format("%.1fM", number / 1000000.0);
        } else if (number >= 1000) {
            return String.format("%.1fK", number / 1000.0);
        }
        return String.valueOf(number);
    }

    /**
     * 타임스탬프 포맷팅
     */
    private String formatTimestamp(long timestamp) {
        return java.time.LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(timestamp),
            java.time.ZoneId.systemDefault()
        ).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    /**
     * 문자열 자르기
     */
    private String truncateString(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str != null ? str : "";
        }
        return str.substring(0, maxLength) + "...";
    }
    
    /**
     * 클라이언트 연결 끊김 감지
     */
    private boolean isClientDisconnection(Throwable throwable) {
        String message = throwable.getMessage();
        return message != null && (
            message.contains("Broken pipe") ||
            message.contains("Connection reset") ||
            message.contains("AsyncRequestNotUsableException") ||
            message.contains("ClientAbortException") ||
            message.contains("Connection closed prematurely") ||
            message.contains("Premature close")
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

    /**
     * 디버깅용 엔드포인트 - 활성 연결 상태 조회
     */
    @GetMapping("/status")
    public Map<String, Object> getConnectionStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("activeConnections", connectionCount.get());
        status.put("activeSessions", parsingProgressBroker.getActiveSessionCount());
        status.put("activeSubscribers", parsingProgressBroker.getActiveSubscriberCount());
        status.put("sessionConnections", sessionConnections.size());
        status.put("brokerStatus", parsingProgressBroker.getSessionStatuses());
        return status;
    }
}
