package com.smartcoreinc.localpkd.fileparsing.infrastructure.repository;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCode;
import com.smartcoreinc.localpkd.fileparsing.domain.model.MasterList;
import com.smartcoreinc.localpkd.fileparsing.domain.model.MasterListId;
import com.smartcoreinc.localpkd.fileparsing.domain.repository.MasterListRepository;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JpaMasterListRepository - MasterListRepository 구현체
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
 * 1. Use Case에서 MasterList Aggregate Root 저장 요청
 * 2. JPA save() 실행 전 Domain Events 발행
 * 3. JPA save() 실행 (데이터베이스 저장)
 * 4. ApplicationEventPublisher로 이벤트 발행
 * 5. Event Handlers 자동 실행 (예: MasterListCreatedEvent → LDAP Upload)
 * 6. Aggregate의 Domain Events 초기화
 * </pre>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * // Use Case에서
 * MasterList masterList = MasterList.create(...);
 * // → MasterListCreatedEvent 추가됨
 *
 * MasterList saved = repository.save(masterList);
 * // → JPA save
 * // → MasterListCreatedEvent 발행
 * // → Event Handlers 실행 (예: LDAP 업로드 시작)
 * </pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-27
 * @see com.smartcoreinc.localpkd.fileparsing.domain.repository.MasterListRepository
 * @see SpringDataMasterListRepository
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JpaMasterListRepository implements MasterListRepository {

    private final SpringDataMasterListRepository jpaRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * MasterList 저장
     *
     * <p>저장 후 Domain Events를 자동으로 발행합니다.</p>
     *
     * @param masterList MasterList Aggregate Root
     * @return 저장된 MasterList
     */
    @Override
    @Transactional
    public MasterList save(MasterList masterList) {
        log.debug("Saving MasterList: {}", masterList.getId().getId());

        // 1. Domain Events 발행 (JPA 저장 전에 발행 - transient 필드이므로 저장 후에는 사라짐)
        if (masterList.hasDomainEvents()) {
            log.debug("Publishing {} domain events for MasterList BEFORE save: {}",
                masterList.getDomainEvents().size(), masterList.getId().getId());

            masterList.getDomainEvents().forEach(event -> {
                log.debug("Publishing event: {}", event.getClass().getSimpleName());
                eventPublisher.publishEvent(event);
            });

            // 2. Domain Events 초기화
            masterList.clearDomainEvents();
        }

        // 3. JPA 저장
        MasterList saved = jpaRepository.save(masterList);

        log.debug("Saved MasterList: {} (country={}, cscaCount={})",
            saved.getId().getId(), saved.getCountryCode(), saved.getCscaCount());

        return saved;
    }

    /**
     * MasterListId로 조회
     *
     * @param id MasterListId
     * @return Optional<MasterList>
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<MasterList> findById(MasterListId id) {
        log.debug("Finding MasterList by id: {}", id.getId());
        return jpaRepository.findById(id);
    }

    /**
     * UploadId로 조회 (단일)
     *
     * @param uploadId UploadId
     * @return Optional<MasterList>
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<MasterList> findByUploadId(UploadId uploadId) {
        log.debug("Finding MasterList by uploadId: {}", uploadId.getId());
        return jpaRepository.findByUploadId(uploadId);
    }

    /**
     * UploadId로 모든 MasterList 조회 (리스트)
     *
     * @param uploadId UploadId
     * @return List<MasterList>
     */
    @Override
    @Transactional(readOnly = true)
    public List<MasterList> findAllByUploadId(UploadId uploadId) {
        log.debug("Finding all MasterLists by uploadId: {}", uploadId.getId());
        return jpaRepository.findAllByUploadId(uploadId);
    }

    /**
     * CountryCode로 MasterList 조회 (생성일자 내림차순)
     *
     * @param countryCode CountryCode
     * @return List<MasterList> (최신순)
     */
    @Override
    @Transactional(readOnly = true)
    public List<MasterList> findByCountryCodeOrderByCreatedAtDesc(CountryCode countryCode) {
        log.debug("Finding MasterList by countryCode: {}", countryCode.getValue());
        return jpaRepository.findByCountryCodeOrderByCreatedAtDesc(countryCode);
    }

    /**
     * 모든 MasterList 조회 (생성일자 내림차순)
     *
     * @return List<MasterList> (최신순)
     */
    @Override
    @Transactional(readOnly = true)
    public List<MasterList> findAllOrderByCreatedAtDesc() {
        log.debug("Finding all MasterLists ordered by createdAt DESC");
        return jpaRepository.findAllOrderByCreatedAtDesc();
    }

    /**
     * MasterListId로 삭제
     *
     * @param id MasterListId
     */
    @Override
    @Transactional
    public void deleteById(MasterListId id) {
        log.debug("Deleting MasterList by id: {}", id.getId());
        jpaRepository.deleteById(id);
    }

    /**
     * MasterListId로 존재 여부 확인
     *
     * @param id MasterListId
     * @return 존재하면 true
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsById(MasterListId id) {
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

    /**
     * 전체 MasterList 개수 조회
     *
     * @return MasterList 총 개수
     */
    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jpaRepository.count();
    }

    /**
     * 국가별 MasterList 개수 조회
     *
     * @param countryCode CountryCode
     * @return 해당 국가의 MasterList 개수
     */
    @Override
    @Transactional(readOnly = true)
    public long countByCountryCode(CountryCode countryCode) {
        return jpaRepository.countByCountryCode(countryCode);
    }

    /**
     * UploadId별 MasterList 개수 조회
     *
     * @param uploadId UploadId
     * @return 해당 업로드의 MasterList 개수
     */
    @Override
    @Transactional(readOnly = true)
    public long countByUploadId(UploadId uploadId) {
        return jpaRepository.countByUploadId(uploadId);
    }
}
