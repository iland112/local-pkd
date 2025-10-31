package com.smartcoreinc.localpkd.fileparsing.infrastructure.repository;

import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFileId;
import com.smartcoreinc.localpkd.fileparsing.domain.repository.ParsedFileRepository;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JpaParsedFileRepository - ParsedFileRepository 구현체
 *
 * <p><b>Infrastructure Layer</b>: Domain Repository 인터페이스를 JPA로 구현합니다.</p>
 *
 * <p><b>주요 책임</b>:</p>
 * <ol>
 *   <li>JPA를 통한 데이터베이스 접근</li>
 *   <li>Domain Events 자동 발행 (ApplicationEventPublisher)</li>
 *   <li>Aggregate Root 저장/조회</li>
 * </ol>
 *
 * <p><b>Event-Driven Architecture</b>:</p>
 * <pre>
 * 1. Use Case에서 Aggregate Root 저장 요청
 * 2. JPA save() 실행 (데이터베이스 저장)
 * 3. Aggregate의 Domain Events 추출
 * 4. ApplicationEventPublisher로 이벤트 발행
 * 5. Event Handlers 자동 실행
 * 6. Aggregate의 Domain Events 초기화
 * </pre>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * // Use Case에서
 * ParsedFile parsedFile = ParsedFile.create(...);
 * parsedFile.startParsing();  // FileParsingStartedEvent 추가
 *
 * ParsedFile saved = repository.save(parsedFile);
 * // → JPA save
 * // → FileParsingStartedEvent 발행
 * // → Event Handlers 실행
 * </pre>
 *
 * @see com.smartcoreinc.localpkd.fileparsing.domain.repository.ParsedFileRepository
 * @see SpringDataParsedFileRepository
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JpaParsedFileRepository implements ParsedFileRepository {

    private final SpringDataParsedFileRepository jpaRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * ParsedFile 저장
     *
     * <p>저장 후 Domain Events를 자동으로 발행합니다.</p>
     *
     * @param parsedFile ParsedFile Aggregate Root
     * @return 저장된 ParsedFile
     */
    @Override
    @Transactional
    public ParsedFile save(ParsedFile parsedFile) {
        log.debug("Saving ParsedFile: {}", parsedFile.getId().getId());

        // 1. Domain Events 발행 (JPA 저장 전에 발행 - transient 필드이므로 저장 후에는 사라짐)
        if (parsedFile.hasDomainEvents()) {
            log.debug("Publishing {} domain events for ParsedFile BEFORE save: {}",
                parsedFile.getDomainEvents().size(), parsedFile.getId().getId());

            parsedFile.getDomainEvents().forEach(event -> {
                log.debug("Publishing event: {}", event.getClass().getSimpleName());
                eventPublisher.publishEvent(event);
            });

            // 2. Domain Events 초기화
            parsedFile.clearDomainEvents();
        }

        // 3. JPA 저장
        ParsedFile saved = jpaRepository.save(parsedFile);

        return saved;
    }

    /**
     * ParsedFileId로 조회
     *
     * @param id ParsedFileId
     * @return Optional<ParsedFile>
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ParsedFile> findById(ParsedFileId id) {
        log.debug("Finding ParsedFile by id: {}", id.getId());
        return jpaRepository.findById(id);
    }

    /**
     * UploadId로 조회
     *
     * @param uploadId UploadId
     * @return Optional<ParsedFile>
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ParsedFile> findByUploadId(UploadId uploadId) {
        log.debug("Finding ParsedFile by uploadId: {}", uploadId.getId());
        return jpaRepository.findByUploadId(uploadId);
    }

    /**
     * ParsedFileId로 삭제
     *
     * @param id ParsedFileId
     */
    @Override
    @Transactional
    public void deleteById(ParsedFileId id) {
        log.debug("Deleting ParsedFile by id: {}", id.getId());
        jpaRepository.deleteById(id);
    }

    /**
     * ParsedFileId로 존재 여부 확인
     *
     * @param id ParsedFileId
     * @return 존재하면 true
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsById(ParsedFileId id) {
        return jpaRepository.existsById(id);
    }

    /**
     * UploadId로 존재 여부 확인
     *
     * @param uploadId UploadId
     * @return 존재하면 true
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByUploadId(UploadId uploadId) {
        return jpaRepository.existsByUploadId(uploadId);
    }
}
