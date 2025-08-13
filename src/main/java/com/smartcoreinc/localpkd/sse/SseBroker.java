package com.smartcoreinc.localpkd.sse;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.icaomasterlist.service.CscaMasterListParser;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

@Component
public class SseBroker {
    private final Sinks.Many<ProgressEvent> eventPublisher;

    private final ProgressPublisher progressPublisher;
    private ProgressListener progressListener;


    public SseBroker(ProgressPublisher progressPublisher) {
        this.progressPublisher = progressPublisher;
        eventPublisher = createNewSink();
    }

    @PostConstruct
    void init() {
        progressListener = (progress, processedCount, totalCount, message) -> 
            eventPublisher.tryEmitNext(new ProgressEvent(progress, processedCount, totalCount, message));
        progressPublisher.addProgressListener(progressListener);
    }

    @PreDestroy
    void destroy() {
        progressPublisher.removeProgressListener(progressListener);
    }

    public Flux<List<ProgressEvent>> subscribeToUpdates() {
        return this.eventPublisher.asFlux()
            .buffer(Duration.ofSeconds(1));
    }

    private static Sinks.Many<ProgressEvent> createNewSink() {
        return Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
    }

}
