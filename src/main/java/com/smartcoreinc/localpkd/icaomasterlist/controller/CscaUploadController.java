package com.smartcoreinc.localpkd.icaomasterlist.controller;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.smartcoreinc.localpkd.icaomasterlist.service.CscaMasterListParser;
import com.smartcoreinc.localpkd.sse.SseBroker;
import com.smartcoreinc.localpkd.sse.SseBroker.ProgressEvent;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Controller
@RequestMapping("/icao/csca")
public class CscaUploadController {
    private final CscaMasterListParser parser;
    private final SseBroker broker;

    private int count = 0;

    public CscaUploadController(CscaMasterListParser parser, SseBroker broker) {
        this.parser = parser;
        this.broker = broker;
    }

    @GetMapping
    public String uploadForm() {
        return "masterlist/upload";
    }

    @HxRequest
    @PostMapping("/upload")
    @ResponseBody
    public void handleUpload(@RequestParam("file") MultipartFile file, Model model) throws Exception {
        log.debug("Start file upload: {}, {} bytes", file.getOriginalFilename(), file.getSize());
        
        // TODO: 개발 완료 후 isAddLdap 파라미터 제거할 것
        List<X509Certificate> x509Certs = parser.parseMasterList(file.getBytes(), true);
        log.debug("result certs count: {}", x509Certs.size());
        Map<String, Integer> cscaCountByCountry = parser.getCscaCountByCountry();
        
        cscaCountByCountry.forEach((key, value) -> {
            count = count + value;
            log.debug("key: {}, value: {}, count: {}", key, value, count);
        });
        log.debug("CSCA 국가 수: {}, {}", cscaCountByCountry.size(), count);
    }

    @GetMapping("/progress")
    public Flux<ServerSentEvent<String>> progress() {
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(5))
                .map(it -> ServerSentEvent.<String>builder().event("heatbeat").build());
        Flux<List<SseBroker.ProgressEvent>> updates = broker.subscribeToUpdates();
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

    // 국가별 CSCA 인증서 개 수 리턴
    // private ServerSentEvent<String> createCountEvent(List<ProgressEvent> events) {
    //     String html = """
    //         <div class="indicator">
    //             <span class="indicator-item badge badge-secondary">%d</span>
    //             <img class="h-6 w-12 object-cover" src="%s" />
    //         </div>
    //     """;
    //     StringBuilder stringBuilder = new StringBuilder();
    //     parser.getCscaCountByCountry().forEach((key, value) -> stringBuilder.append(html.formatted(value, String.format("https://flagcdn.com/%s.svg", key.toLowerCase()))));
    //     return ServerSentEvent.<String>builder()
    //         .event("count-event")
    //         .data(stringBuilder.toString())
    //         .build();
    // }

}
