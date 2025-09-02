package com.smartcoreinc.localpkd.sse;

import java.time.Duration;

import org.springframework.http.codec.ServerSentEvent;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class SSEUtils {
    public static <T> Flux<ServerSentEvent<String>> createSSEStream(
            String identifier, 
            Flux<T> dataStream) {
        
        // 연결 확인 이벤트
        Flux<ServerSentEvent<String>> connectionEvent = Flux.just(
            ServerSentEvent.<String>builder()
                .id("connection-" + System.currentTimeMillis())
                .event("connection-established")
                .data("SSE connection established for " + identifier)
                .build()
        );
        
        // 하트비트
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(25))
            .map(sequence -> ServerSentEvent.<String>builder()
                .id("heartbeat-" + sequence)
                .event("heartbeat") 
                .data("ping")
                .build())
            .onErrorResume(throwable -> Flux.empty());
        
        // 실제 데이터 스트림
        Flux<ServerSentEvent<String>> dataEvents = dataStream
            .map(data -> convertToSSEEvent(data))
            .onErrorResume(throwable -> Flux.empty());
            
        return Flux.merge(connectionEvent, heartbeat, dataEvents)
            .doOnSubscribe(sub -> log.info("SSE subscription started: {}", identifier))
            .doOnCancel(() -> log.info("SSE cancelled: {}", identifier))
            .doFinally(signal -> log.info("SSE finished: {} - {}", identifier, signal));
    }

    private static ServerSentEvent<String> convertToSSEEvent(Object data) {
        // 데이터 타입에 따른 SSE 이벤트 변환 로직
        return ServerSentEvent.<String>builder()
            .event("data-update")
            .data(data.toString())
            .build();
    }
}
