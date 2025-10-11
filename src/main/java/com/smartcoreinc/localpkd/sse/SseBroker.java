package com.smartcoreinc.localpkd.sse;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

@Slf4j
@Component
public class SseBroker {
    private volatile Sinks.Many<ProgressEvent> eventPublisher;
    private final ProgressPublisher progressPublisher;
    private ProgressListener progressListener;
    private final AtomicInteger subscriberCount = new AtomicInteger(0);
    
    public SseBroker(ProgressPublisher progressPublisher) {
        this.progressPublisher = progressPublisher;
        eventPublisher = createNewSink();
    }

    @PostConstruct
    void init() {
        progressListener = (progressEvent) -> {
            try {
                // 구독자가 없으면 이벤트 전송하지 않음
                if (subscriberCount.get() == 0) {
                    log.debug("No active subscribers, skipping event emission");
                    return;
                }

                Sinks.EmitResult result = eventPublisher.tryEmitNext(progressEvent);
                if (result.isFailure()) {
                    log.warn("Failed to emit progress event: {}, event type: {}", result, progressEvent.progress().type());
                    // 실패 시 재 시도 로직
                    if (result == Sinks.EmitResult.FAIL_OVERFLOW) {
                        log.warn("Event buffer overflow, creating new sink");
                        recreateSink();
                        // 재시도
                        eventPublisher.tryEmitNext(progressEvent);
                    }
                }
            } catch (Exception e) {
                log.error("Error emitting progress event: {}", e.getMessage(), e);
                // 에러 발생 시 Sink 재생성
                recreateSink(); 
            }
        };
        progressPublisher.addProgressListener(progressListener);
        log.info("SSE Broker initialized");
    }

    @PreDestroy
    void destroy() {
        try {
            if (progressListener != null) {
                progressPublisher.removeProgressListener(progressListener);
            }
            if (eventPublisher != null) {
                eventPublisher.tryEmitComplete();
            }
            log.info("SSE Broker destroyed");
        } catch (Exception e) {
            log.error("Error during SSE Broker destruction: {}", e.getMessage(), e);
        }
    }

    public Flux<List<ProgressEvent>> subscribeToUpdates() {
        return this.eventPublisher.asFlux()
            .buffer(Duration.ofMillis(800)) // 더 짧은 버퍼링 시간
            .filter(events -> !events.isEmpty())
            .doOnSubscribe(sub -> {
                int count = subscriberCount.incrementAndGet();
                log.debug("New subscription to progress updates. Active subscribers: {}", count);
            })
            .doOnCancel(() -> {
                int count = subscriberCount.decrementAndGet();
                log.debug("Progress updates subscription cancelled. Active subscribers: {}", count);
            })
            .doOnTerminate(() -> {
                int count = subscriberCount.decrementAndGet();
                log.debug("Progress updates subscription terminated. Active subscribers: {}", count);
            })
            .onErrorResume(throwable -> {
                subscriberCount.decrementAndGet();
                log.error("Error in progress updates flux: {}", throwable.getMessage(), throwable);
                // 에러 발생 시 빈 Flux 반환하여 HTMX가 재연결하도록 함
                return Flux.empty();
            });
    }

    /**
     * Sink 재생성 (동기화된 메소드)
     */
    private synchronized void recreateSink() {
        try {
            Sinks.Many<ProgressEvent> oldSink = this.eventPublisher;
            this.eventPublisher = createNewSink();
            
            // 기존 Sink 정리
            if (oldSink != null) {
                oldSink.tryEmitComplete();
            }
            
            log.info("SSE Sink recreated successfully");
        } catch (Exception e) {
            log.error("Failed to recreate SSE Sink: {}", e.getMessage(), e);
        }
    }

    private static Sinks.Many<ProgressEvent> createNewSink() {
        return Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
    }

    /**
     * 현재 활성 구독자 수 반환
     */
    public int getActiveSubscriberCount() {
        return subscriberCount.get();
    }
}
