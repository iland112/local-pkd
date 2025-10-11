package com.smartcoreinc.localpkd.sse;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Controller
@RequestMapping("/progress")
public class ProgressController {

    private final SseBroker sseBroker;

    public ProgressController(SseBroker sseBroker) {
        this.sseBroker = sseBroker;
    }

    @GetMapping
    public Flux<ServerSentEvent<String>> progress(HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        String clientIP = getClientIP(request);

        log.info("SSE connection requested - SessionID: {}, ClientIP: {}, Active subscribers: {}", 
                sessionId, clientIP, sseBroker.getActiveSubscriberCount());
        
        // 연결 확인용 초기 이벤트
        Flux<ServerSentEvent<String>> connectionEvent = Flux.just(
            ServerSentEvent.<String>builder()
                .id("connection-" + System.currentTimeMillis())
                .event("connection-established")
                .data("SSE connection established")
                .build()
        );
        
        // HTMX SSE extension을 위한 하트비트 (연결 유지)
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(25))
                .map(sequence -> ServerSentEvent.<String>builder()
                        .id("heartbeat-" + sequence)
                        .event("heartbeat")
                        .data("ping")
                        .build())
                .onErrorResume(throwable -> {
                    if (isClientDisconnection(throwable)) {
                        log.debug("Client disconnected during heartbeat: {}", throwable.getMessage());
                    } else {
                        log.warn("Heartbeat error: {}", throwable.getMessage());
                    }
                    return Flux.empty();
                });

        // 프로그레스 업데이트 스트림
        Flux<ServerSentEvent<String>> progressUpdates = sseBroker.subscribeToUpdates()
                .flatMap(events -> {
                    try {
                        // HTMX는 여러 이벤트를 동시에 처리할 수 있으므로 개별 이벤트로 방출
                        return Flux.fromIterable(List.of(
                                createProgressEvent(events),
                                createLogEvent(events),
                                createStatusEvent(events)
                        )).filter(Objects::nonNull);
                    } catch (Exception e) {
                        log.error("Error creating events for session {}: {}", sessionId, e.getMessage(), e);
                        return createErrorEvent(e.getMessage());
                    }
                })
                .onErrorResume(throwable -> {
                    if (isClientDisconnection(throwable)) {
                        log.debug("Client disconnected during progress updates: {}", throwable.getMessage());
                    } else {
                        log.error("Progress updates error for session {}: {}", sessionId, throwable.getMessage());
                    }
                    return Flux.empty();
                });

        return Flux.merge(connectionEvent, heartbeat, progressUpdates)
                .doOnSubscribe(subscription -> {
                    log.info("SSE subscription started - Session: {}, Client: {}", sessionId, clientIP);
                })
                .doOnCancel(() -> {
                    log.info("SSE connection cancelled - Session: {}, Client: {}", sessionId, clientIP);
                })
                .doOnError(throwable -> {
                    if (isClientDisconnection(throwable)) {
                        log.debug("SSE client disconnected - Session: {}, Reason: {}", 
                                sessionId, throwable.getMessage());
                    } else {
                        log.error("SSE connection error - Session: {}, Error: {}", 
                                sessionId, throwable.getMessage(), throwable);
                    }
                })
                .doFinally(signalType -> {
                    log.info("SSE connection finished - Session: {}, Signal: {}, Remaining subscribers: {}", 
                            sessionId, signalType, sseBroker.getActiveSubscriberCount());
                })
                // 에러 시 스트림 종료하여 HTMX가 재연결하도록 함
                .onErrorResume(throwable -> {
                    if (isClientDisconnection(throwable)) {
                        log.debug("Handling client disconnection gracefully");
                    } else {
                        log.warn("SSE stream error, allowing HTMX to handle reconnection: {}", 
                                throwable.getMessage());
                    }
                    return Flux.empty();
                });
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

    /**
     * HTMX용 프로그레스 이벤트 생성 (개선됨)
     */
    private ServerSentEvent<String> createProgressEvent(List<ProgressEvent> events) {
        try {
            ProgressEvent latestEvent = events.stream()
                    .max(Comparator.comparing(progressEvent -> progressEvent.progress().value()))
                    .orElse(null);

            if (latestEvent == null) {
                return null;
            }

            double progressValue = latestEvent.progress().value();
            int progressRate = (int) Math.round(progressValue * 100);
            boolean isComplete = progressValue >= 1.0;

            // 성공률 계산
            double successRate = latestEvent.processedCount() > 0
                    ? (double) latestEvent.processedCount() / latestEvent.totalCount() * 100
                    : 0;

            // 완료 상태에 따른 스타일링
            String statusClass = isComplete ? "from-green-50 to-emerald-50 border-green-200" : "from-blue-50 to-indigo-50 border-blue-200";
            String progressBarClass = isComplete ? "from-green-500 to-green-600" : "from-blue-500 to-blue-600";
            String textClass = isComplete ? "text-green-800" : "text-blue-800";
            String statusText = isComplete ? "완료" : "진행중";

            String progressHtml = String.format(
                    """
                    <div class="bg-gradient-to-br %s border rounded-lg p-6">
                      <div class="flex items-center justify-between mb-3">
                        <span class="%s font-semibold text-lg">저장 진행률</span>
                        <span class="%s font-bold text-lg">%s (%d%%)</span>
                      </div>
                      <div class="w-full bg-gray-200 rounded-full h-6 overflow-hidden shadow-inner">
                        <div class="bg-gradient-to-r %s h-6 rounded-full transition-all duration-300 flex items-center justify-center text-sm text-white font-semibold shadow-sm" 
                             style="width: %d%%">
                          %d%%
                        </div>
                      </div>
                      <div class="flex justify-between mt-3 text-sm">
                        <div class="%s">
                          <span class="font-medium">처리됨:</span>
                          <span class="font-bold">%d</span>
                        </div>
                        <div class="%s">
                          <span class="font-medium">총:</span>
                          <span class="font-bold">%d</span>개
                        </div>
                      </div>
                      <div class="flex justify-between mt-2 text-xs">
                        <div class="text-green-600">
                          <i class="fas fa-check-circle mr-1"></i>
                          <span class="font-medium">성공률:</span>
                          <span class="font-bold">%.1f%%</span>
                        </div>
                        <span class="text-gray-600">
                          <i class="fas %s mr-1"></i> %s
                        </span>
                      </div>
                    </div>
                    """,
                    statusClass, textClass, textClass, statusText, progressRate,
                    progressBarClass, progressRate, progressRate,
                    textClass, latestEvent.processedCount(),
                    textClass, latestEvent.totalCount(),
                    successRate,
                    isComplete ? "fa-check" : "fa-clock",
                    isComplete ? "작업 완료" : "작업 진행중..."
            );

            return ServerSentEvent.<String>builder()
                    .event("progress-update")
                    .data(progressHtml)
                    .build();

        } catch (Exception e) {
            log.error("Error creating progress event: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * HTMX용 로그 이벤트 생성
     */
    private ServerSentEvent<String> createLogEvent(List<ProgressEvent> events) {
        try {
            String logHtml = events.stream()
                    .map(progressEvent -> {
                        String timestamp = java.time.LocalTime.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                        return """
                                <div class="log-entry flex items-start gap-2 py-1 px-2 hover:bg-gray-800 rounded">
                                    <span class="text-xs text-gray-400 font-mono min-w-fit">%s</span>
                                    <span class="text-sm text-gray-200 flex-1">%s</span>
                                </div>
                                """.formatted(timestamp, replaceNewLines(progressEvent.message()));
                    })
                    .collect(Collectors.joining());

            if (logHtml.isEmpty()) {
                return null;
            }

            return ServerSentEvent.<String>builder()
                    .event("log-update")
                    .data(logHtml)
                    .build();

        } catch (Exception e) {
            log.error("Error creating log event: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * HTMX용 상태 업데이트 이벤트
     */
    private ServerSentEvent<String> createStatusEvent(List<ProgressEvent> events) {
        try {
            ProgressEvent latestEvent = events.stream()
                    .max(Comparator.comparing(progressEvent -> progressEvent.progress().value()))
                    .orElse(null);

            if (latestEvent == null) {
                return null;
            }

            boolean isComplete = latestEvent.progress().value() >= 1.0;
            String statusClass = isComplete ? "text-green-600" : "text-blue-600";
            String statusText = isComplete ? "완료" : "진행중";
            String iconClass = isComplete ? "fa-check-circle" : "fa-spinner fa-spin";

            String statusHtml = """
                    <div class="status-indicator %s font-semibold flex items-center">
                        <i class="fas %s mr-2"></i>
                        상태: %s
                    </div>
                    """.formatted(statusClass, iconClass, statusText);

            return ServerSentEvent.<String>builder()
                    .event("status-update")
                    .data(statusHtml)
                    .build();

        } catch (Exception e) {
            log.error("Error creating status event: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 오류 발생시 클라이언트에 알림
     */
    private Flux<ServerSentEvent<String>> createErrorEvent(String errorMessage) {
        String errorHtml = """
                <div class="error-message bg-red-50 border border-red-200 text-red-700 px-4 py-2 rounded-lg mb-4">
                    <div class="flex items-center">
                        <i class="fas fa-exclamation-triangle mr-2 text-red-500"></i>
                        <div>
                            <strong>오류:</strong> %s
                        </div>
                    </div>
                </div>
                """.formatted(errorMessage);

        return Flux.just(ServerSentEvent.<String>builder()
                .event("error-update")
                .data(errorHtml)
                .build());
    }

    private String replaceNewLines(String message) {
        if (message == null) {
            return "";
        }
        return message.replace("\n", "<br>").replace("\r", "").trim();
    }
}