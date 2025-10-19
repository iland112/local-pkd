package com.smartcoreinc.localpkd.shared.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate Root 추상 클래스
 *
 * <p>DDD의 Aggregate 패턴을 구현하는 루트 Entity입니다.
 * Aggregate는 일관성 경계(Consistency Boundary)를 정의하며,
 * 외부에서는 반드시 Aggregate Root를 통해서만 접근해야 합니다.</p>
 *
 * <h3>Aggregate 패턴</h3>
 * <ul>
 *   <li><b>일관성 경계</b>: Aggregate 내부의 불변식(Invariant) 보호</li>
 *   <li><b>트랜잭션 경계</b>: 하나의 Aggregate는 하나의 트랜잭션으로 처리</li>
 *   <li><b>이벤트 발행</b>: 도메인 이벤트를 수집하고 발행</li>
 * </ul>
 *
 * <h3>설계 규칙</h3>
 * <ol>
 *   <li>Aggregate Root를 통해서만 외부 접근</li>
 *   <li>작은 Aggregate를 선호 (성능 및 동시성 고려)</li>
 *   <li>ID로 다른 Aggregate 참조 (객체 참조 금지)</li>
 *   <li>도메인 이벤트로 Aggregate 간 통신</li>
 * </ol>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * @Entity
 * @Table(name = "uploaded_files")
 * public class UploadedFile extends AggregateRoot<UploadId> {
 *
 *     @EmbeddedId
 *     private UploadId id;
 *
 *     @Embedded
 *     private FileName fileName;
 *
 *     @Enumerated(EnumType.STRING)
 *     private UploadStatus status;
 *
 *     protected UploadedFile() {}
 *
 *     public UploadedFile(FileName fileName) {
 *         this.id = UploadId.random();
 *         this.fileName = fileName;
 *         this.status = UploadStatus.RECEIVED;
 *     }
 *
 *     // Domain Method - 비즈니스 규칙 적용
 *     public void validateSize(long maxSize) {
 *         if (this.size.getBytes() > maxSize) {
 *             throw new FileSizeLimitExceededException("File too large");
 *         }
 *     }
 *
 *     // Domain Method - 상태 변경 및 이벤트 발행
 *     public void markAsReceived() {
 *         this.status = UploadStatus.RECEIVED;
 *         addDomainEvent(new FileUploadedEvent(this.id, this.fileName));
 *     }
 *
 *     @Override
 *     public UploadId getId() {
 *         return id;
 *     }
 * }
 * }</pre>
 *
 * <h3>이벤트 처리 흐름</h3>
 * <pre>
 * 1. Domain Method 호출 → 2. 상태 변경 → 3. addDomainEvent() →
 * 4. Repository.save() → 5. EventBus.publishAll() → 6. Event Handlers
 * </pre>
 *
 * @param <ID> Aggregate Root의 식별자 타입
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 */
public abstract class AggregateRoot<ID> extends Entity<ID> {

    /**
     * 도메인 이벤트 목록
     *
     * <p>Aggregate에서 발생한 도메인 이벤트를 임시로 저장합니다.
     * Repository에서 save() 후 EventBus로 발행합니다.</p>
     */
    private final transient List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * 도메인 이벤트 추가
     *
     * <p>비즈니스 로직 수행 후 도메인 이벤트를 기록합니다.
     * 이벤트는 트랜잭션 커밋 후 발행됩니다.</p>
     *
     * <h4>사용 예시</h4>
     * <pre>{@code
     * public void markAsReceived() {
     *     this.status = UploadStatus.RECEIVED;
     *     addDomainEvent(new FileUploadedEvent(this.id, this.fileName));
     * }
     * }</pre>
     *
     * @param event 도메인 이벤트
     */
    protected void addDomainEvent(DomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Domain event cannot be null");
        }
        this.domainEvents.add(event);
    }

    /**
     * 도메인 이벤트 목록 조회
     *
     * <p>Repository에서 save() 후 이벤트를 발행하기 위해 사용합니다.</p>
     *
     * @return 불변 도메인 이벤트 리스트
     */
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * 도메인 이벤트 초기화
     *
     * <p>EventBus에서 이벤트 발행 후 호출하여 이벤트 목록을 비웁니다.</p>
     */
    public void clearDomainEvents() {
        this.domainEvents.clear();
    }

    /**
     * 도메인 이벤트 존재 여부
     *
     * @return 이벤트가 있으면 true
     */
    public boolean hasDomainEvents() {
        return !this.domainEvents.isEmpty();
    }
}
