package com.smartcoreinc.localpkd.sse.broker;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.sse.event.ParsingEvent;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

@Slf4j
@Component
public class ParsingProgressBroker implements DisposableBean {

    private final Map<String, Sinks.Many<ParsingEvent>> parsingSinks = new ConcurrentHashMap<>();
    private final AtomicInteger subscriberCount = new AtomicInteger(0);
    
    // 세션별 파싱 상태 추적
    private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

    // 지연된 정리 작업을 위한 스케줄러
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(2, r -> {
       Thread t = new Thread(r, "ParsingBroker-Cleanup");
       t.setDaemon(true);
       return t;
    });

    /**
     * 세션 상태 추적 클래스
     */
    private static class SessionState {
        private volatile boolean active = false;
        private volatile boolean completed = false;
        private volatile long lastActivityTime = System.currentTimeMillis();
        private volatile int progressUpdateCount = 0;

        public boolean isActive() { return active; }
        public void setActive(boolean active) {
            this.active = active;
            this.lastActivityTime = System.currentTimeMillis();
        }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
        public long getLastActivityTime() { return lastActivityTime; }
        public void updateActivity() {
            this.lastActivityTime = System.currentTimeMillis();
            this.progressUpdateCount++;
        }
        public int getProgressUpdateCount() { return progressUpdateCount; }
    }

    /**
     * 파싱 세션 시작
     */
    public void startParsingSession(String sessionId) {
        try {
            // 기존 세션이 있고 활성 상태면 중복 시작 방지
            SessionState existingState = sessionStates.get(sessionId);
            if (existingState != null && existingState.isActive()) {
                log.debug("Parsing session {} already active - skipping start", sessionId);
                return;
            }

            // 기존 세션이 있다면 정리
            if (existingState != null) {
                cleanupSessionImmediate(sessionId);
            }
            
            // 백프레셔 설정 개선
            Sinks.Many<ParsingEvent> sink = Sinks.many()
                .multicast()
                .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE * 2, false);

            parsingSinks.put(sessionId, sink);
            
            // 세션 상태 초기화
            SessionState state = new SessionState();
            state.setActive(true);
            sessionStates.put(sessionId, state);
            
            log.info("Parsing session started - SessionId: {}, Buffer size: {}",
                    sessionId, Queues.SMALL_BUFFER_SIZE * 2);

            // 시작 이벤트 발생
            publishParsingProgressInternal(sessionId, createStartEvent(sessionId));
            
        } catch (Exception e) {
            log.error("Error starting parsing session {}: {}", sessionId, e.getMessage(), e);
            cleanupSessionImmediate(sessionId);
        }
    }

    /**
     * 시작 이벤트 생성
     */
    private ParsingEvent createStartEvent(String sessionId) {
        return new ParsingEvent(
            sessionId, 
            0.0, 
            0, 
            0, 
            "", 
            "", 
            "LDIF 파싱을 준비하고 있습니다...", 
            0, 
            0, 
            new HashMap<>(), 
            Map.of(
                "stage", "initializing",
                "timestamp", System.currentTimeMillis(),
                "status", "starting"
            )
        );
    }

    /**
     * 파싱 진행률 발행
     */
    public void publishParsingProgress(String sessionId, ParsingEvent event) {
        SessionState state = sessionStates.get(sessionId);
        if (state == null || !state.isActive()) {
            log.debug("Session {} is not active - skipping event", sessionId);
            return;
        }
        
        // 진행률 발행 빈도 제어
        if (shouldSkipProgressUpdate(state, event)) {
            log.trace("Skipping progress update for session {} (frequency control)", sessionId);
            return;
        }

        publishParsingProgressInternal(sessionId, event);
        state.updateActivity();
    }

    /**
     * 세션이 없을 경우 자동 생성하는 개선된 메서드
     */
    private Sinks.Many<ParsingEvent> getOrCreateSink(String sessionId) {
        Sinks.Many<ParsingEvent> sink = parsingSinks.get(sessionId);

        if (sink == null) {
            synchronized (parsingSinks) {
                // Double-checked locking으로 동시성 문제 방지
                sink = parsingSinks.get(sessionId);
                if (sink == null) {
                    log.info("Creating sink for session {} (requested before session start)", sessionId);
                    
                    sink = Sinks.many()
                        .multicast()
                        .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE * 2, false);
                    
                    parsingSinks.put(sessionId, sink);
                    
                    // 세션 상태도 함께 생성 (비활성 상태로)
                    SessionState state = new SessionState();
                    state.setActive(false); // 아직 파싱이 시작되지 않은 상태
                    sessionStates.put(sessionId, state);
                    
                    // 대기 중 이벤트 발송
                    publishParsingProgressInternal(sessionId, createWaitingEvent(sessionId));
                }
            }
        }
        
        return sink;
    }

    /**
     * 대기 중 이벤트 생성
     */
    private ParsingEvent createWaitingEvent(String sessionId) {
        return new ParsingEvent(
            sessionId, 
            0.0, 
            0, 
            0, 
            "", 
            "", 
            "파싱 시작을 기다리는 중...", 
            0, 
            0, 
            new HashMap<>(), 
            Map.of(
                "stage", "waiting",
                "timestamp", System.currentTimeMillis(),
                "status", "waiting_for_parsing"
            )
        );
    }

    /**
     * 내부 진행률 발생 메서드
     */
    private void publishParsingProgressInternal(String sessionId, ParsingEvent event) {
        Sinks.Many<ParsingEvent> sink = getOrCreateSink(sessionId); // 개선된 메서드 사용

        try {
            Sinks.EmitResult result = sink.tryEmitNext(event);

            if (result.isFailure()) {
                handleEmitFailure(sessionId, event, result);
            } else {
                log.debug("Parsing progress published - Session: {}, Progress: {}%, Message: {}",
                        sessionId, String.format("%.1f", event.progress() * 100),
                        truncateMessage(event.message()));
            }
        } catch (Exception e) {
            log.error("Exception while publishing parsing progress for session {}: {}",
                    sessionId, e.getMessage(), e);
        };
    }

    /**
     * 진행률 업데이트 스킵 여부 결정
     */
    private boolean shouldSkipProgressUpdate(SessionState state, ParsingEvent event) {
        int updateCount = state.getProgressUpdateCount();
        double progress = event.progress();

        // 시작과 완료는 항상 발행
        if (progress <= 0.01 || progress >= 0.99) {
            return false;
        }

        // 에러가 있는 경우 항상 발행
        if (event.hasErrors()) {
            return false;
        }

        // 처음 50개 업데이트는 5개마다, 이후는 10개마다
        int skipInterval = updateCount < 50 ? 5 : 10;
        return updateCount % skipInterval != 0;
    }

    /**
     * Emit 실패 처리
     */
    private void handleEmitFailure(String sessionId, ParsingEvent event, Sinks.EmitResult result) {
        log.warn("Failed to emit parsing event for session {}: {} - attempting recovery", sessionId, result);

        switch (result) {
            case FAIL_OVERFLOW:
                // 백프레셔 오버플로우 시 재시도
                scheduler.schedule(() -> recreateSinkAndRetry(sessionId, event), 100, TimeUnit.MILLISECONDS);
                break;
            case FAIL_CANCELLED:
                log.info("Sink cancelled for session {} - cleaning up", sessionId);
                cleanupSessionImmediate(sessionId);
                break;
            case FAIL_TERMINATED:
                log.info("Sink terminated for session {} - cleaning up", sessionId);
                cleanupSessionImmediate(sessionId);
                break;
            default:
                log.warn("Unhandled emit failure for session {}: {}", sessionId, result);
        }
    }

    /**
     * 파싱 세션 구독
     */
    public Flux<ParsingEvent> subscribeToParsingSession(String sessionId) {
        Sinks.Many<ParsingEvent> sink = getOrCreateSink(sessionId); // 자동 생성 지원
        
        log.debug("Subscribing to parsing session: {}", sessionId);

        return sink.asFlux()
            .doOnSubscribe(sub -> {
                int count = subscriberCount.incrementAndGet();
                log.debug("Parsing subscription added for session {}. Total subscribers: {}", sessionId, count);

                // 세션 상태 업데이트
                SessionState state = sessionStates.get(sessionId);
                if (state != null) {
                    state.updateActivity();
                }
            })
            .doOnCancel(() -> {
                int count = subscriberCount.decrementAndGet();
                log.debug("Parsing subscription cancelled for session {}. Remaining subscribers: {}", sessionId, count);
            })
            .doOnTerminate(() -> {
                subscriberCount.decrementAndGet();
                log.debug("Parsing subscription terminated for session: {}", sessionId);
            })
            .doOnError(throwable -> {
                subscriberCount.decrementAndGet();
                if (isClientDisconnection(throwable)) {
                    log.debug("Client disconnected for parsing session {}: {}", sessionId, throwable.getMessage());
                } else {
                    log.error("Error in parsing subscription for session {}: {}", sessionId, throwable.getMessage());
                }
            })
            .onErrorResume(throwable -> {
                if (isClientDisconnection(throwable)) {
                    log.debug("Handling parsing client disconnection gracefully for session: {}", sessionId);
                } else {
                    log.warn("Parsing SSE stream error for session {}, allowing HTMX reconnection: {}", 
                            sessionId, throwable.getMessage());
                }
                return Flux.empty();
            });
    }

    /**
     * LDAP 저장 태스크 완료
     */
    public void completeParsingSession(String sessionId) {
        try {
            SessionState state = sessionStates.get(sessionId);
            if (state != null) {
                state.setCompleted(true);
                state.setActive(false);
            }

            log.info("LDIF Parsing session marked as completed: {} - scheduling cleanup", sessionId);

            // 지연된 정리 (크라이언트가 최종 메시지를 받을 시간 제공)
            scheduler.schedule(() -> {
                cleanupSessionImmediate(sessionId);
                log.info("Parsing session cleanup completed: {}", sessionId);
            }, 5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Error completing parsing session {}: {}", sessionId, e.getMessage(), e);
            // 에러 발생 시 즉시 정리
            cleanupSessionImmediate(sessionId);
        }
    }

    /**
     * Sink 재생성 및 재시도
     */
    private void recreateSinkAndRetry(String sessionId, ParsingEvent event) {
        try {
            SessionState state = sessionStates.get(sessionId);
            if (state == null || !state.isActive()) {
                log.debug("Session {} no longer active during sink recreation", sessionId);
                return;
            }

            log.warn("Recreating Parsing progress sink for session: {}", sessionId);
            
            // 새로운 sink 생성
            Sinks.Many<ParsingEvent> newSink = Sinks.many()
                .multicast()
                .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE * 3, false);
                
            parsingSinks.put(sessionId, newSink);
            
            // 재시도
            Sinks.EmitResult retryResult = newSink.tryEmitNext(event);
            if (retryResult.isFailure()) {
                log.error("Failed to emit LDIF parsing event after sink recreation: {}", retryResult);
            } else {
                log.info("Successfully re-emitted LDIF parsing event after sink recreation for session: {}", sessionId);
            }
        } catch (Exception e) {
            log.error("Error recreating LDIF parsing sink for task {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 즉시 세션 정리
     */
    private void cleanupSessionImmediate(String sessionId) {
        try {
            // Sink 정리
            Sinks.Many<ParsingEvent> sink = parsingSinks.get(sessionId);
            if (sink != null) {
                try {
                    sink.tryEmitComplete();
                } catch (Exception e) {
                    log.debug("Error completing sink for session {}: {}", sessionId, e.getMessage());
                }
            }

            // 세션 상태 정리
            sessionStates.remove(sessionId);

            log.debug("Session resources cleaned up immediately: {}", sessionId);
        } catch (Exception e) {
            log.error("Error during immediate session cleanup for {}: {}", sessionId, e.getMessage(), e);
        }
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
     * 메시지 자르기 유틸리티
     */
    private String truncateMessage(String message) {
        if (message == null || message.length() <= 100) {
            return message != null ? message : "";
        }
        return message.substring(0, 97) + "...";
    }

    /**
     * 활성 구독자 수 반환
     */
    public int getActiveSubscriberCount() {
        return subscriberCount.get();
    }
    
    /**
     * 활성 파싱 세션 수 반환
     */
    public int getActiveSessionCount() {
        return parsingSinks.size();
    }

    /**
     * 세션 상태 정보 반환 (디버깅용)
     */
    public Map<String, String> getSessionStatuses() {
        Map<String, String> statuses = new HashMap<>();
        sessionStates.forEach((sessionId, state) -> {
            statuses.put(sessionId, String.format("Active: %s, Completed: %s, Updates: %s",
                state.isActive(), state.isCompleted(), state.getProgressUpdateCount()));
        });
        return statuses;
    }

    /**
     * 리소스 정리 (애플리케이션 종료 시)
     */
    @Override
    public void destroy() throws Exception {
        log.info("Shutting down ParsingProgressBroker...");

        // 모든 세션 정리
        parsingSinks.keySet().forEach(this::cleanupSessionImmediate);

        // 스케줄러 종료
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("ParsingProgressBroker shutdown completed");
    }
}
