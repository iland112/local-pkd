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
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(5))
                .map(it -> ServerSentEvent.<String>builder().event("heatbeat").build());

        Flux<List<ProgressEvent>> updates = sseBroker.subscribeToUpdates();
        
        return Flux.merge(
            heartbeat,
            updates
            .flatMap(events -> {
                        return Flux.just(
                                    createLogEvent(events),
                                    createProgressEvent(events)
                                )
                                .filter(Objects::nonNull);
                    }
            )
            .doOnSubscribe(subscription -> log.debug("Subscription: {}", subscription))
            .doOnCancel(() -> log.debug("cancel"))
            .doOnError(throwable -> log.debug(throwable.getMessage(), throwable))
            .doFinally(signalType -> log.debug("finally: {}", signalType))
        );
    }

    private static ServerSentEvent<String> createProgressEvent(List<ProgressEvent> events) {
        String htmlTag = """
                    <progress class="progress w-full h-6" value="%d" max="100"></progress>
                    <p class="text-gray-900 font-mono">진행률: %d &percnt;</p>
                """; 
        return ServerSentEvent.<String>builder()
            .event("progress-event")
            .data(events.stream()
                    .max(Comparator.comparing(progressEvent -> progressEvent.progress().value()))
                    .map(progressEvent -> {
                        int progressRate = (int) (progressEvent.progress().value() * 100);
                        return htmlTag.formatted(progressRate, progressRate);
                    })
                    .orElse(null))
            .build();
    }

    private ServerSentEvent<String> createLogEvent(List<ProgressEvent> events) {
        return ServerSentEvent.<String>builder()
            .event("log-event")
            .data(events.stream()
                .map(progressEvent -> "<div>%s</div>".formatted(replaceNewLines(progressEvent.message())))
                .collect(Collectors.joining()))
            .build();
    }

    private String replaceNewLines(String message) {
        return message.replace("\n", "<br>");
    }
}
