package com.smartcoreinc.localpkd.sse.broker;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.sse.event.ParsingEvent;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

@Slf4j
@Component
public class ParsingProgressBroker {

    private final Map<String, Sinks.Many<ParsingEvent>> parsingSinks = new ConcurrentHashMap<>();
    private final AtomicInteger subscriberCount = new AtomicInteger(0);

    /**
     * 파싱 세션 시작
     * @param String sessionId
     */
    public void startParsingSession(String sessionId) {
        Sinks.Many<ParsingEvent> sink = Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

        parsingSinks.put(sessionId, sink);
        log.info("Parsing session started for session: {}", sessionId);

        // 시작 이벤트 발생
        publishParsingProgress(sessionId, createStartEvent(sessionId));
    }

    private ParsingEvent createStartEvent(String sessionId) {
        ParsingEvent parsingEvent =
            new ParsingEvent(
                sessionId, 
                0.0, 
                0, 
                0, 
                "", 
                "", "Start Parsing LDIF Entries", 
                0, 
                0, 
                new HashMap<String, Integer>(), 
                new HashMap<String, Object>()
            );
        return parsingEvent;
    }

    /**
     * 파싱 진행률 발행
     */
    public void publishParsingProgress(String sessionId, ParsingEvent event) {
        Sinks.Many<ParsingEvent> sink = parsingSinks.get(sessionId);
        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure()) {
                log.warn("Failed to emit parsing event for session {}: {}", sessionId, result);
                // 실패 시 재시도 로직
                if (result == Sinks.EmitResult.FAIL_OVERFLOW) {
                    recreateSinkAndRetry(sessionId, event);
                }
            } else {
                log.debug(
                    "Parsing progress published - Session: {}, Progress: {}%",
                    sessionId, event.getProgressPercentage()
                );
            }
        } else {
            log.warn("No parsing sink found for session: {}", sessionId);
        }
    }

    /**
     * 파싱 세션 구독
     */
    public Flux<ParsingEvent> subscribeToParsingSession(String sessionId) {
        Sinks.Many<ParsingEvent> sink = parsingSinks.get(sessionId);
        if (sink == null) {
            log.warn("No parsing session found for session: {}", sessionId);
            return Flux.empty();
        }

        return sink.asFlux()
            .doOnSubscribe(sub -> {
                int count = subscriberCount.incrementAndGet();
                log.debug("Parsing subscription added for session {}. Total subscribers: {}", sessionId, count);
            })
            .doOnCancel(() -> {
                int count = subscriberCount.decrementAndGet();
                log.debug("Parsing subscription cancelled for session {}. Remaining subscribers: {}", sessionId, count);
            })
            .doOnTerminate(() -> {
                subscriberCount.decrementAndGet();
                log.debug("Parsing subscription", sessionId, sink);
            })
            .onErrorResume(throwable -> {
                subscriberCount.decrementAndGet();
                log.error("Error in parsing subscription for session {}: {}", sessionId, throwable.getMessage());
                return Flux.empty();
            });
    }

    /**
     * LDAP 저장 태스크 완료
     */
    public void completeParsingSession(String sessionId) {
        Sinks.Many<ParsingEvent> sink = parsingSinks.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
            log.info("LDIF parsing session completed and cleaned up: {}", sessionId);
        } else {
            log.warn("No LDIF parsing session found to complete: {}", sessionId);
        }
    }

    /**
     * Sink 재생성 및 재시도
     */
    private void recreateSinkAndRetry(String sessionId, ParsingEvent event) {
        try {
            log.warn("Recreating Parsing progress sink for session: {}", sessionId);
            
            Sinks.Many<ParsingEvent> newSink = Sinks.many()
                .multicast()
                .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
                
            parsingSinks.put(sessionId, newSink);
            
            // 재시도
            Sinks.EmitResult retryResult = newSink.tryEmitNext(event);
            if (retryResult.isFailure()) {
                log.error("Failed to emit LDIF parsing event after sink recreation: {}", retryResult);
            }
            
        } catch (Exception e) {
            log.error("Error recreating LDIF parsing sink for task {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 활성 구독자 수 반환
     */
    public int getActiveSubscriberCount() {
        return subscriberCount.get();
    }
    
    /**
     * 활성 저장 태스크 수 반환
     */
    public int getActiveTaskCount() {
        return parsingSinks.size();
    }
}
