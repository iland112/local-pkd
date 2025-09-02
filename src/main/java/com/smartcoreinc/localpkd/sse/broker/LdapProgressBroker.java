package com.smartcoreinc.localpkd.sse.broker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.sse.event.LdapSaveEvent;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

@Slf4j
@Component
public class LdapProgressBroker {
    private final Map<String, Sinks.Many<LdapSaveEvent>> taskSinks = new ConcurrentHashMap<>();
    private final AtomicInteger subscriberCount = new AtomicInteger(0);

    public void startSaveTask(String taskId) {
        Sinks.Many<LdapSaveEvent> sink = Sinks.many()
            .multicast()
            .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
        taskSinks.put(taskId, sink);
        log.info("LDAP save task started: {}", taskId);

        // 시작 이벤트 발행
        publishSaveProgress(taskId, createStartEvent(taskId));
    }

    public void publishSaveProgress(String taskId, LdapSaveEvent event) {
        Sinks.Many<LdapSaveEvent> sink = taskSinks.get(taskId);
        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure()) {
                log.warn("Failed to emit LDAP save event for task {}: {}", taskId, result);
                
                // 실패 시 재시도 로직
                if (result == Sinks.EmitResult.FAIL_OVERFLOW) {
                    recreateSinkAndRetry(taskId, event);
                }
            } else {
                log.debug("LDAP save progress published - Task: {}, Progress: {}%, Success: {}/{}", 
                         taskId, event.getProgressPercentage(), event.successCount(), event.processedEntries());
            }
        } else {
            log.warn("No LDAP save sink found for task: {}", taskId);
        }
    }

    /**
     * LDAP 저장 태스크 구독
     */
    public Flux<LdapSaveEvent> subscribeToTask(String taskId) {
        Sinks.Many<LdapSaveEvent> sink = taskSinks.get(taskId);
        if (sink == null) {
            log.warn("No LDAP save task found for task: {}", taskId);
            return Flux.empty();
        }
        
        return sink.asFlux()
            .doOnSubscribe(sub -> {
                int count = subscriberCount.incrementAndGet();
                log.debug("LDAP save subscription added for task {}. Total subscribers: {}", taskId, count);
            })
            .doOnCancel(() -> {
                int count = subscriberCount.decrementAndGet();
                log.debug("LDAP save subscription cancelled for task {}. Remaining subscribers: {}", taskId, count);
            })
            .doOnTerminate(() -> {
                subscriberCount.decrementAndGet();
                log.debug("LDAP save subscription terminated for task: {}", taskId);
            })
            .onErrorResume(throwable -> {
                subscriberCount.decrementAndGet();
                log.error("Error in LDAP save subscription for task {}: {}", taskId, throwable.getMessage());
                return Flux.empty();
            });
    }

    /**
     * LDAP 저장 태스크 완료
     */
    public void completeSaveTask(String taskId) {
        Sinks.Many<LdapSaveEvent> sink = taskSinks.remove(taskId);
        if (sink != null) {
            sink.tryEmitComplete();
            log.info("LDAP save task completed and cleaned up: {}", taskId);
        } else {
            log.warn("No LDAP save task found to complete: {}", taskId);
        }
    }

    /**
     * Sink 재생성 및 재시도
     */
    private void recreateSinkAndRetry(String taskId, LdapSaveEvent event) {
        try {
            log.warn("Recreating LDAP save sink for task: {}", taskId);
            
            Sinks.Many<LdapSaveEvent> newSink = Sinks.many()
                .multicast()
                .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
                
            taskSinks.put(taskId, newSink);
            
            // 재시도
            Sinks.EmitResult retryResult = newSink.tryEmitNext(event);
            if (retryResult.isFailure()) {
                log.error("Failed to emit LDAP save event even after sink recreation: {}", retryResult);
            }
            
        } catch (Exception e) {
            log.error("Error recreating LDAP save sink for task {}: {}", taskId, e.getMessage(), e);
        }
    }
    
    /**
     * 시작 이벤트 생성
     */
    private LdapSaveEvent createStartEvent(String taskId) {
        return new LdapSaveEvent(
            taskId,
            0.0,
            0,
            0,
            0,
            0,
            "started",
            "LDAP 저장 작업이 시작되었습니다.",
            Map.of("stage", "start", "timestamp", System.currentTimeMillis())
        );
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
        return taskSinks.size();
    }
}
