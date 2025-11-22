package com.smartcoreinc.localpkd.fileupload.infrastructure.repository;

import com.smartcoreinc.localpkd.fileupload.domain.model.FileHash;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadStatus;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile;
import com.smartcoreinc.localpkd.fileupload.domain.repository.UploadedFileRepository;
import com.smartcoreinc.localpkd.shared.event.EventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA Uploaded File Repository - JPA 기반 업로드 파일 리포지토리 구현체
 *
 * <p>Domain Layer의 {@link UploadedFileRepository} 인터페이스를 구현한
 * Infrastructure Layer의 구현체입니다. Spring Data JPA와 EventBus를 사용합니다.</p>
 *
 * <h3>책임 (Responsibilities)</h3>
 * <ul>
 *   <li>Domain Aggregate Root의 영속성 관리 (CRUD)</li>
 *   <li>Domain Events 발행 (EventBus 통해)</li>
 *   <li>JPA 엔티티와 Domain 객체 간 변환 (필요 시)</li>
 * </ul>
 *
 * <h3>DDD 원칙 준수</h3>
 * <ul>
 *   <li><b>의존성 역전 원칙</b>: Domain 인터페이스를 Infrastructure에서 구현</li>
 *   <li><b>Aggregate 일관성</b>: 저장 시 Domain Events 자동 발행</li>
 *   <li><b>트랜잭션 경계</b>: 각 메서드가 트랜잭션 단위</li>
 * </ul>
 *
 * <h3>사용 예시 - Use Case에서 주입</h3>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class UploadFileUseCase {
 *
 *     // Domain 인터페이스 주입 (구현체는 Spring이 자동 연결)
 *     private final UploadedFileRepository repository;
 *
 *     @Transactional
 *     public UploadFileResponse execute(UploadFileCommand command) {
 *         UploadedFile uploadedFile = UploadedFile.create(...);
 *
 *         // 저장 시 Domain Events 자동 발행
 *         UploadedFile saved = repository.save(uploadedFile);
 *
 *         return new UploadFileResponse(...);
 *     }
 * }
 * }</pre>
 *
 * <h3>Domain Events 발행 흐름</h3>
 * <pre>
 * 1. Aggregate Root 생성 시 Domain Event 추가
 *    → uploadedFile.addDomainEvent(event)
 * 2. repository.save(uploadedFile) 호출
 * 3. JPA 저장 완료
 * 4. EventBus.publishAll(events) 호출
 *    → Spring ApplicationEventPublisher로 발행
 * 5. uploadedFile.clearDomainEvents() 호출
 *    → 이벤트 중복 발행 방지
 * 6. 저장된 Aggregate Root 반환
 * </pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 * @see UploadedFileRepository
 * @see SpringDataUploadedFileRepository
 * @see EventBus
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JpaUploadedFileRepository implements UploadedFileRepository {

    private final SpringDataUploadedFileRepository jpaRepository;
    private final EventBus eventBus;

    /**
     * 업로드된 파일 저장 및 Domain Events 발행
     *
     * <p>Aggregate Root를 저장한 후 Domain Events를 발행합니다.
     * 트랜잭션 커밋 후 이벤트가 발행됩니다.</p>
     *
     * @param aggregate 업로드된 파일 Aggregate Root
     * @return 저장된 Aggregate Root
     */
    @Override
    @Transactional
    public UploadedFile save(UploadedFile aggregate) {
        log.debug("Saving UploadedFile: id={}", aggregate.getId().getId());

        // 1. Domain Events 발행 (JPA 저장 전에 발행 - transient 필드이므로 저장 후에는 사라짐)
        if (!aggregate.getDomainEvents().isEmpty()) {
            log.debug("Publishing {} domain event(s) BEFORE save", aggregate.getDomainEvents().size());
            eventBus.publishAll(aggregate.getDomainEvents());
            aggregate.clearDomainEvents();
        }

        // 2. JPA 저장
        UploadedFile saved = jpaRepository.save(aggregate);

        log.debug("UploadedFile saved successfully: id={}", saved.getId().getId());

        return saved;
    }

    /**
     * 업로드 ID로 파일 조회
     *
     * @param id 업로드 ID
     * @return 업로드된 파일 (Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<UploadedFile> findById(UploadId id) {
        log.debug("Finding UploadedFile by id: {}", id.getId());
        return jpaRepository.findById(id);
    }

    /**
     * 파일 해시로 파일 조회
     *
     * <p>중복 파일 검사에 사용됩니다.</p>
     *
     * @param fileHash 파일 해시 (SHA-256)
     * @return 업로드된 파일 (Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<UploadedFile> findByFileHash(FileHash fileHash) {
        log.debug("Finding UploadedFile by fileHash: {}", fileHash.getShortHash());
        return jpaRepository.findByFileHash(fileHash);
    }

    /**
     * 업로드 ID로 파일 삭제
     *
     * @param id 업로드 ID
     */
    @Override
    @Transactional
    public void deleteById(UploadId id) {
        log.debug("Deleting UploadedFile by id: {}", id.getId());
        jpaRepository.deleteById(id);
    }

    /**
     * 업로드된 파일 존재 여부 확인
     *
     * @param id 업로드 ID
     * @return 존재하면 true
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UploadId id) {
        log.debug("Checking existence of UploadedFile by id: {}", id.getId());
        return jpaRepository.existsById(id);
    }

    /**
     * 파일 해시로 존재 여부 확인
     *
     * @param fileHash 파일 해시
     * @return 존재하면 true
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByFileHash(FileHash fileHash) {
        log.debug("Checking existence of UploadedFile by fileHash: {}", fileHash.getShortHash());
        return jpaRepository.existsByFileHash(fileHash);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jpaRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(UploadStatus status) {
        return jpaRepository.countByStatus(status);
    }
}
