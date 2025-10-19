package com.smartcoreinc.localpkd.shared.event;

import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Event Bus - 도메인 이벤트 발행
 *
 * <p>Spring의 {@link ApplicationEventPublisher}를 래핑하여
 * 도메인 이벤트를 발행하는 인프라스트럭처 컴포넌트입니다.</p>
 *
 * <h3>특징</h3>
 * <ul>
 *   <li>Spring Event 기반 구현 (비동기 처리 지원)</li>
 *   <li>트랜잭션 커밋 후 이벤트 발행</li>
 *   <li>타입 안전한 이벤트 핸들링</li>
 * </ul>
 *
 * <h3>이벤트 발행 흐름</h3>
 * <pre>
 * 1. Aggregate Root에서 도메인 이벤트 수집
 *    → aggregate.addDomainEvent(new FileUploadedEvent(...))
 *
 * 2. Repository에서 save 후 이벤트 발행
 *    → repository.save(aggregate)
 *    → eventBus.publishAll(aggregate.getDomainEvents())
 *
 * 3. Event Handler에서 이벤트 처리
 *    → @EventListener or @TransactionalEventListener
 * </pre>
 *
 * <h3>사용 예시 - Repository에서 이벤트 발행</h3>
 * <pre>{@code
 * @Repository
 * @RequiredArgsConstructor
 * public class JpaUploadedFileRepository implements UploadedFileRepository {
 *     private final JpaRepository<UploadedFile, UploadId> jpaRepository;
 *     private final EventBus eventBus;
 *
 *     @Override
 *     public UploadedFile save(UploadedFile aggregate) {
 *         UploadedFile saved = jpaRepository.save(aggregate);
 *
 *         // 도메인 이벤트 발행
 *         eventBus.publishAll(saved.getDomainEvents());
 *         saved.clearDomainEvents();
 *
 *         return saved;
 *     }
 * }
 * }</pre>
 *
 * <h3>사용 예시 - Event Handler</h3>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * @Slf4j
 * public class FileUploadEventHandler {
 *
 *     private final ParseFileUseCase parseFileUseCase;
 *
 *     // 동기 처리
 *     @EventListener
 *     public void handleFileUploaded(FileUploadedEvent event) {
 *         log.info("File uploaded: {}", event.uploadId());
 *     }
 *
 *     // 비동기 처리 (트랜잭션 커밋 후)
 *     @Async
 *     @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 *     public void handleFileUploadedAsync(FileUploadedEvent event) {
 *         log.info("Starting async file parsing for: {}", event.uploadId());
 *         parseFileUseCase.execute(new ParseFileCommand(event.uploadId()));
 *     }
 * }
 * }</pre>
 *
 * <h3>비동기 처리 설정</h3>
 * <pre>{@code
 * @Configuration
 * @EnableAsync
 * public class AsyncConfiguration {
 *
 *     @Bean(name = "taskExecutor")
 *     public Executor taskExecutor() {
 *         ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *         executor.setCorePoolSize(4);
 *         executor.setMaxPoolSize(8);
 *         executor.setQueueCapacity(100);
 *         executor.setThreadNamePrefix("event-");
 *         executor.initialize();
 *         return executor;
 *     }
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventBus {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 단일 도메인 이벤트 발행
     *
     * <p>Spring의 {@link ApplicationEventPublisher}를 통해
     * 등록된 모든 Event Listener에게 이벤트를 전파합니다.</p>
     *
     * @param event 도메인 이벤트
     * @throws IllegalArgumentException event가 null인 경우
     */
    public void publish(DomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        log.debug("Publishing domain event: {} (id: {})",
                event.eventType(), event.eventId());

        eventPublisher.publishEvent(event);

        log.trace("Domain event published successfully: {}", event.eventType());
    }

    /**
     * 여러 도메인 이벤트 일괄 발행
     *
     * <p>Aggregate Root에서 수집된 모든 도메인 이벤트를
     * 순차적으로 발행합니다.</p>
     *
     * <h4>발행 순서</h4>
     * <ul>
     *   <li>이벤트는 추가된 순서대로 발행됨</li>
     *   <li>한 이벤트 처리 중 예외 발생 시 다음 이벤트는 발행되지 않음</li>
     * </ul>
     *
     * @param events 도메인 이벤트 목록
     * @throws IllegalArgumentException events가 null인 경우
     */
    public void publishAll(List<DomainEvent> events) {
        if (events == null) {
            throw new IllegalArgumentException("Events list cannot be null");
        }

        if (events.isEmpty()) {
            log.trace("No domain events to publish");
            return;
        }

        log.debug("Publishing {} domain event(s)", events.size());

        events.forEach(this::publish);

        log.debug("All {} domain event(s) published successfully", events.size());
    }
}
