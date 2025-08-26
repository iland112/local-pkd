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
    public Flux<ServerSentEvent<String>> progress() {
        // HTMX SSE extension을 위한 하트비트 (연결 유지)
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(30))
                .map(sequence -> ServerSentEvent.<String>builder()
                    .id(String.valueOf(sequence))
                    .event("heartbeat")
                    .data("ping")
                    .build())
                .onErrorResume(throwable -> {
                    log.debug("Heartbeat error: {}", throwable.getMessage());
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
                        createStatusEvent(events)  // 추가: 상태 업데이트용
                    )).filter(Objects::nonNull);
                } catch (Exception e) {
                    log.error("Error creating events: {}", e.getMessage(), e);
                    return createErrorEvent(e.getMessage());
                }
            })
            .onErrorResume(throwable -> {
                log.error("Progress updates error: {}", throwable.getMessage(), throwable);
                return createErrorEvent("Progress update failed");
            });
        
        return Flux.merge(heartbeat, progressUpdates)
            .doOnSubscribe(subscription -> {
                log.debug("New HTMX SSE subscription: {}", subscription);
            })
            .doOnCancel(() -> {
                log.debug("HTMX SSE connection cancelled (normal client disconnect)");
            })
            .doOnError(throwable -> {
                // Broken pipe 등은 정상적인 클라이언트 연결 종료
                if (throwable.getMessage() != null && 
                    (throwable.getMessage().contains("Broken pipe") || 
                     throwable.getMessage().contains("AsyncRequestNotUsableException"))) {
                    log.debug("Client disconnected: {}", throwable.getMessage());
                } else {
                    log.error("SSE error: {}", throwable.getMessage(), throwable);
                }
            })
            .doFinally(signalType -> {
                log.debug("SSE connection finished: {}", signalType);
            })
            // HTMX는 재연결을 자동 처리하므로 에러 시 스트림 종료
            .onErrorResume(throwable -> {
                log.debug("SSE stream error handled, letting HTMX handle reconnection: {}", 
                         throwable.getMessage());
                return Flux.empty();
            });
    }

    /**
     * HTMX용 프로그레스 이벤트 생성
     * 이벤트명: progress-update (HTMX에서 hx-sse="progress-update"로 수신)
     */
    private ServerSentEvent<String> createProgressEvent(List<ProgressEvent> events) {
        try {
            ProgressEvent latestEvent = events.stream()
                .max(Comparator.comparing(progressEvent -> progressEvent.progress().value()))
                .orElse(null);
                
            if (latestEvent == null) {
                return null;
            }
            
            String progressType = latestEvent.progress().type().toLowerCase();
            int progressRate = (int) (latestEvent.progress().value() * 100);
            
            // HTMX가 직접 DOM에 삽입할 수 있는 HTML 형태
            String progressHtml = """
                <div id="progress-container">
                    <progress class="progress w-full h-6" value="%d" max="100"></progress>
                    <div class="flex justify-between mt-2">
                        <span class="text-sm text-gray-600">%s</span>
                        <span class="text-sm font-semibold text-gray-900">%d%%</span>
                    </div>
                    <div class="text-xs text-gray-500 mt-1">
                        처리됨: %d / %d
                    </div>
                </div>
                """.formatted(
                    progressRate, 
                    progressType.toUpperCase(),
                    progressRate,
                    latestEvent.processedCount(),
                    latestEvent.totalCount()
                );
            
            return ServerSentEvent.<String>builder()
                .event("progress-update")  // HTMX에서 이 이벤트명으로 수신
                .data(progressHtml)
                .build();
                
        } catch (Exception e) {
            log.error("Error creating progress event: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * HTMX용 로그 이벤트 생성
     * 이벤트명: log-update
     */
    private ServerSentEvent<String> createLogEvent(List<ProgressEvent> events) {
        try {
            String logHtml = events.stream()
                .map(progressEvent -> {
                    String timestamp = java.time.LocalTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                    return """
                        <div class="log-entry flex items-start gap-2 py-1 px-2 hover:bg-gray-50">
                            <span class="text-xs text-gray-50 font-mono">%s</span>
                            <span class="text-sm text-gray-100 flex-1">%s</span>
                        </div>
                        """.formatted(timestamp, replaceNewLines(progressEvent.message()));
                })
                .collect(Collectors.joining());
                
            if (logHtml.isEmpty()) {
                return null;
            }
                
            return ServerSentEvent.<String>builder()
                .event("log-update")  // HTMX에서 이 이벤트명으로 수신
                .data(logHtml)
                .build();
                
        } catch (Exception e) {
            log.error("Error creating log event: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * HTMX용 상태 업데이트 이벤트
     * 이벤트명: status-update
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
            
            String statusHtml = """
                <div class="status-indicator %s font-semibold">
                    상태: %s
                </div>
                """.formatted(statusClass, statusText);
            
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
     * 오류 발생 시 클라이언트에 알림
     */
    private Flux<ServerSentEvent<String>> createErrorEvent(String errorMessage) {
        String errorHtml = """
            <div class="error-message bg-red-50 border border-red-200 text-red-700 px-4 py-2 rounded">
                <strong>오류:</strong> %s
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