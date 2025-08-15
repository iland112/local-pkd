package com.smartcoreinc.localpkd.sse;

import java.time.Duration;
import java.util.List;

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
    private final Sinks.Many<ProgressEvent> eventPublisher;
    private final ProgressPublisher progressPublisher;
    private ProgressListener progressListener;
    private MessageListener messageListener;


    public SseBroker(ProgressPublisher progressPublisher) {
        this.progressPublisher = progressPublisher;

        eventPublisher = createNewSink();
    }

    @PostConstruct
    void init() {
        progressListener = (progress, processedCount, totalCount, message) -> {
            try {
                ProgressEvent event = new ProgressEvent(progress, processedCount, totalCount, message);
                Sinks.EmitResult result = eventPublisher.tryEmitNext(event);

                if (result.isFailure()) {
                    log.warn("Failed to emit progress event: {}, event: {}", result, event);
                    // 실패 시 재 시도 로직
                    if (result == Sinks.EmitResult.FAIL_OVERFLOW) {
                        log.warn("Event buffer overflow, creating new sink");
                    }
                }
            } catch (Exception e) {
                log.error("Error emitting progress event: {}", e.getMessage(), e);
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
            eventPublisher.tryEmitComplete();
            log.info("SSE Broker destroyed");
        } catch (Exception e) {
            log.error("Error during SSE Broker destruction: {}", e.getMessage(), e);
        }
    }

    public Flux<List<ProgressEvent>> subscribeToUpdates() {
        return this.eventPublisher.asFlux()
            .buffer(Duration.ofSeconds(1))
            .filter(events -> !events.isEmpty())
            .doOnSubscribe(sub -> log.debug("New subscription to progress updates"))
            .doOnCancel(() -> log.debug("Progress updates subscription cancelled"))
            .onErrorResume(throwable -> {
                log.error("Error in progress updates flux: {}", throwable.getMessage(), throwable);
                return Flux.empty();
            });
    }

    private static Sinks.Many<ProgressEvent> createNewSink() {
        return Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
    }

}
