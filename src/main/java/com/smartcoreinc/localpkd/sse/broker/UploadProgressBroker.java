package com.smartcoreinc.localpkd.sse.broker;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.sse.event.UploadEvent;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

@Slf4j
@Component
public class UploadProgressBroker {

    private final Map<String, Sinks.Many<UploadEvent>> uploadSinks = new ConcurrentHashMap<>();
    private final AtomicInteger subscriberCount = new AtomicInteger(0);

    public void startUploadSession(String sessionId) {
        Sinks.Many<UploadEvent> sink = Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
        uploadSinks.put(sessionId, sink);
        log.info("Upload session started: {}", sessionId);
    }

    public void publishUploadProgress(String sessionId, UploadEvent event) {
        Sinks.Many<UploadEvent> sink = uploadSinks.get(sessionId);
        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure()) {
                log.warn("Failed to emit upload event for session {}: {}", sessionId, result);
            }
        }
    }

    public Flux<List<UploadEvent>> subscribeToUploads(String sessionId) {
        Sinks.Many<UploadEvent> sink = uploadSinks.get(sessionId);
        if (sink == null) {
            return Flux.empty();
        }

        return sink.asFlux()
            .buffer(Duration.ofMillis(500))
            .filter(events -> !events.isEmpty())
            .doOnSubscribe(sub -> {
                int count = subscriberCount.incrementAndGet();
                log.debug("Upload subscribtion added for session {}. Total: {}", sessionId, count);
            })
            .doOnCancel(() -> {
                int count = subscriberCount.decrementAndGet();
                log.debug("Upload subscribtion cancelled for session {}. Total: {}", sessionId, count);
            })
            .doOnTerminate(() -> subscriberCount.decrementAndGet());
    }

    public void completeUploadSession(String sessionId) {
        Sinks.Many<UploadEvent> sink = uploadSinks.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
            log.info("Upload session completed: {}", sessionId);
        }
    }
}
