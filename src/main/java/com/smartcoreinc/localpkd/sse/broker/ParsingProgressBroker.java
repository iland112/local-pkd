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
    // 세션별 구독자 수 추적
    private final Map<String, AtomicInteger> sessionSubscriberCounts = new ConcurrentHashMap<>();

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
        private volatile boolean subscribed = false;
        private volatile long lastActivityTime = System.currentTimeMillis();
        private volatile int progressUpdateCount = 0;

        public boolean isActive() { return active; }
        public void setActive(boolean active) {
            this.active = active;
            this.lastActivityTime = System.currentTimeMillis();
        }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
        public boolean isSubscribed() { return subscribed; }
        public void setSubscribed(boolean subscribed) { this.subscribed = subscribed; }
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
            log.info("Starting parsing session: {}", sessionId);

            // 기존 세션이 있고 활성 상태면 상태만 업데이트
            SessionState existingState = sessionStates.get(sessionId);
            boolean hadExistingSubscription = existingState != null && existingState.isSubscribed();

            if (existingState != null && existingState.isActive()) {
                log.debug("Parsing session {} already active - update state", sessionId);
                existingState.updateActivity();
                return;
            }

            // 기존 Sink가 있고 구독자가 있으면 재 사용
            Sinks.Many<ParsingEvent> sink = parsingSinks.get(sessionId);
            if (sink == null || !hadExistingSubscription) {
                // 완전히 새로운 sink 생성
                sink = Sinks.many()
                        .multicast()
                        .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE * 2, false);
                parsingSinks.put(sessionId, sink);

                // 세션별 구독자 수 초기화
                sessionSubscriberCounts.put(sessionId, new AtomicInteger(0));

                log.debug("Created new sink for session: {}", sessionId);
            } else {
                log.debug("Reusing existing sink for session: {}", sessionId);
            }

            // 세션 상태 초기화 또는 업데이트
            SessionState state = sessionStates.computeIfAbsent(sessionId, k -> new SessionState());
            state.setActive(true);
            state.updateActivity();

            log.info("Parsing session activated - SessionId: {}, Had existing subscription: {}",
                    sessionId, hadExistingSubscription);

            // 시작 이벤트 발생
            publishParsingProgressSafe(sessionId, createStartEvent(sessionId));
            
        } catch (Exception e) {
            log.error("Error starting parsing session {}: {}", sessionId, e.getMessage(), e);
            cleanupSessionImmediate(sessionId);
        }
    }

    /**
     * 세션이 없을 경우 자동 생성하는 메서드 (구독용)
     */
    private Sinks.Many<ParsingEvent> getOrCreateSinkForSubscription(String sessionId) {
        Sinks.Many<ParsingEvent> sink = parsingSinks.get(sessionId);

        if (sink == null) {
            synchronized (parsingSinks) {
                sink = parsingSinks.get(sessionId);
                if (sink == null) {
                    log.info("Creating sink for subscription to session: {}", sessionId);

                    sink = Sinks.many()
                            .multicast()
                            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE * 2, false);

                    parsingSinks.put(sessionId, sink);
                    sessionSubscriberCounts.put(sessionId, new AtomicInteger(0));

                    // 세션 상태 생성 (아직 구독은 안됨 - doOnSubscribe에서 처리)
                    SessionState state = sessionStates.computeIfAbsent(sessionId, k -> new SessionState());
                    state.updateActivity();
                }
            }
        } else {
            // 기존 sink에 구독 표시
            SessionState state = sessionStates.get(sessionId);
            if (state != null) {
                state.setSubscribed(true);
                state.updateActivity();
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
        if (state == null) {
            log.warn("No Session state found for {}, creating default state", sessionId);
            SessionState newState = new SessionState();
            newState.setActive(true);
            sessionStates.put(sessionId, newState);
        }
        
        // 진행률 발행 빈도 제어
        // if (state != null && shouldSkipProgressUpdate(state, event)) {
        //     log.trace("Skipping progress update for session {} (frequency control)", sessionId);
        //     return;
        // }

        publishParsingProgressSafe(sessionId, event);
        if (state != null) {
            state.updateActivity();
        }
        
        log.debug("Published parsing progress for session {}: {}%",
            sessionId, String.format("%.1f", event.progress() * 100));
    }

    /**
     * 안전한 이벤트 발행 메서드 (FAIL_ZERO_SUBSCRIBER 처리)
     */
    private void publishParsingProgressSafe(String sessionId, ParsingEvent event) {
        Sinks.Many<ParsingEvent> sink = parsingSinks.get(sessionId);
        if (sink == null) {
            log.warn("No sink found for session: {} - event ignored", sessionId);
            return;
        }

        // 구독자 수 확인
        AtomicInteger subscriberCount = sessionSubscriberCounts.get(sessionId);
        if (subscriberCount == null || subscriberCount.get() == 0) {
            log.debug("No subscribers for session {}, storing event for potential reconnection", sessionId);
            // 구독자가 없어도 에러로 처리하지 않고 단순히 스킵
            return;
        }

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
        }
    }

    /**
     * 진행률 업데이트 스킵 여부 결정
     */
    private boolean shouldSkipProgressUpdate(SessionState state, ParsingEvent event) {
        int updateCount = state.getProgressUpdateCount();
        double progress = event.progress();

        // 시작과 완료는 항상 발행
        if (progress <= 0.02 || progress >= 0.98) {
            return false;
        }

        // 에러가 있는 경우 항상 발행
        if (event.hasErrors()) {
            return false;
        }

        // 스킵 간격
        int skipInterval = updateCount < 20 ? 2 : (updateCount < 100 ? 5 : 10);
        return updateCount % skipInterval != 0;
    }

    /**
     * Emit 실패 처리
     */
    private void handleEmitFailure(String sessionId, ParsingEvent event, Sinks.EmitResult result) {
        switch (result) {
            case FAIL_ZERO_SUBSCRIBER:
                log.debug("No subscribers for session {} - ignoring event", sessionId);
                // 구독자가 없으면 단순히 무시 (복구 시도 안함)
                break;
            case FAIL_OVERFLOW:
                log.warn("Buffer overflow for session {} - attempting recovery", sessionId);
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
        log.info("Subscribing to parsing session: {}", sessionId);

        Sinks.Many<ParsingEvent> sink = getOrCreateSinkForSubscription(sessionId);

        return sink.asFlux()
            .doOnSubscribe(sub -> {
                int globalCount = subscriberCount.incrementAndGet();
                
                // 세션별 구독자 수 증가
                AtomicInteger sessionCount = sessionSubscriberCounts.get(sessionId);
                int currentSessionCount = sessionCount != null ? sessionCount.incrementAndGet() : 1;
                
                log.debug("Parsing subscription added for session {}. Session subscribers: {}, Total subscribers: {}", 
                         sessionId, currentSessionCount, globalCount);

                // 세션 상태 업데이트
                SessionState state = sessionStates.get(sessionId);
                if (state != null) {
                    state.setSubscribed(true);
                    state.updateActivity();
                    log.debug("Updated session state for {}: active={}, subscribed={}",
                        sessionId, state.isActive(), state.isSubscribed());
                }
            })
            .doOnCancel(() -> {
                int globalCount = subscriberCount.decrementAndGet();
                
                // 세션별 구독자 수 감소
                AtomicInteger sessionCount = sessionSubscriberCounts.get(sessionId);
                int currentSessionCount = sessionCount != null ? sessionCount.decrementAndGet() : 0;
                
                log.debug("Parsing subscription cancelled for session {}. Session subscribers: {}, Remaining global: {}", 
                         sessionId, currentSessionCount, globalCount);

                // 구독 취소 시 상태 업데이트
                SessionState state = sessionStates.get(sessionId);
                if (state != null && currentSessionCount <= 0) {
                    state.setSubscribed(false);
                }
            })
            .doOnTerminate(() -> {
                subscriberCount.decrementAndGet();
                
                // 세션별 구독자 수 감소
                AtomicInteger sessionCount = sessionSubscriberCounts.get(sessionId);
                if (sessionCount != null) {
                    sessionCount.decrementAndGet();
                }
                
                log.debug("Parsing subscription terminated for session: {}", sessionId);
            })
            .doOnError(throwable -> {
                subscriberCount.decrementAndGet();
                
                // 세션별 구독자 수 감소
                AtomicInteger sessionCount = sessionSubscriberCounts.get(sessionId);
                if (sessionCount != null) {
                    sessionCount.decrementAndGet();
                }
                
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
     * LDIF 파싱 세션 완료
     */
    public void completeParsingSession(String sessionId) {
        try {
            SessionState state = sessionStates.get(sessionId);
            if (state != null) {
                state.setCompleted(true);
                state.setActive(false);
            }

            log.info("LDIF Parsing session marked as completed: {} - scheduling cleanup", sessionId);

            // 완료 이벤트 발행 (구독자가 있을 때만)
            ParsingEvent completionEvent = new ParsingEvent(
                sessionId, 1.0, 0, 0, "", "", "파싱이 완료되었습니다.", 0, 0, 
                new HashMap<>(), Map.of("stage", "completed", "status", "finished", 
                                      "timestamp", System.currentTimeMillis())
            );
            publishParsingProgressSafe(sessionId, completionEvent);

            // 지연된 정리 (클라이언트가 최종 메시지를 받을 시간 제공)
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
            publishParsingProgressSafe(sessionId, event);
        } catch (Exception e) {
            log.error("Error recreating LDIF parsing sink for task {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 즉시 세션 정리
     */
    private void cleanupSessionImmediate(String sessionId) {
        try {
            Sinks.Many<ParsingEvent> sink = parsingSinks.remove(sessionId);
            if (sink != null) {
                try {
                    sink.tryEmitComplete();
                } catch (Exception e) {
                    log.debug("Error completing sink for session {}: {}", sessionId, e.getMessage());
                }
            }

            // 세션 상태 정리
            sessionStates.remove(sessionId);
            sessionSubscriberCounts.remove(sessionId);
            
            log.debug("Session resources cleaned up immediately: {}", sessionId);
        } catch (Exception e) {
            log.error("Error during immediate session cleanup for {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 세션 상태 디버깅 메서드
     */
    public void debugSessionState(String sessionId) {
        SessionState state = sessionStates.get(sessionId);
        Sinks.Many<ParsingEvent> sink = parsingSinks.get(sessionId);
        AtomicInteger sessionSubscriberCount = sessionSubscriberCounts.get(sessionId);

        log.info("=== Session Debug Info for {} ===", sessionId);
        log.info("SessionState exists: {}", state != null);
        if (state != null) {
            log.info("  - Active: {}", state.isActive());
            log.info("  - Subscribed: {}", state.isSubscribed());
            log.info("  - Completed: {}", state.isCompleted());
            log.info("  - Progress Updates: {}", state.getProgressUpdateCount());
        }
        log.info("Sink exists: {}", sink != null);
        log.info("Session subscribers: {}", sessionSubscriberCount != null ? sessionSubscriberCount.get() : 0);
        log.info("Total active sessions: {}", parsingSinks.size());
        log.info("Total global subscribers: {}", subscriberCount.get());
        log.info("===============================");
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
            AtomicInteger sessionSubscriberCount = sessionSubscriberCounts.get(sessionId);
            statuses.put(sessionId, String.format("Active: %s, Completed: %s, Updates: %s, Subscribers: %s",
                state.isActive(), state.isCompleted(), state.getProgressUpdateCount(),
                sessionSubscriberCount != null ? sessionSubscriberCount.get() : 0));
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
