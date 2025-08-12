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

    private final CscaMasterListParser masterListParser;
    private ProgressListener progressListener;


    public SseBroker(CscaMasterListParser masterListParser) {
        this.masterListParser = masterListParser;
        eventPublisher = createNewSink();
    }

    @PostConstruct
    void init() {
        progressListener = (progress, processedCount, totalCount, message) -> 
            eventPublisher.tryEmitNext(new ProgressEvent(progress, processedCount, totalCount, message));
        masterListParser.addProgressListener(progressListener);
    }

    @PreDestroy
    void destroy() {
        masterListParser.removeProgressListener(progressListener);
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

    public record ProgressEvent(Progress progress, int processedCount, int totalCount, String message) {

    }
}
