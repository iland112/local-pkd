package com.smartcoreinc.localpkd.fileupload.domain.repository;

import com.smartcoreinc.localpkd.fileupload.domain.model.FileHash;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadStatus; // Added import
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile;

import java.util.Optional;

/**
 * Uploaded File Repository - 업로드된 파일 리포지토리 인터페이스
 *
 * <p>Domain Layer에 정의된 리포지토리 인터페이스입니다.
 * 구현체는 Infrastructure Layer에 위치하며, JPA를 통해 구현됩니다.</p>
 *
 * <h3>DDD 원칙</h3>
 * <ul>
 *   <li>인터페이스는 Domain Layer에 정의 (의존성 역전 원칙)</li>
 *   <li>구현체는 Infrastructure Layer에 정의</li>
 *   <li>Domain 객체만 다루며, JPA 엔티티나 DTO를 직접 다루지 않음</li>
 * </ul>
 *
 * <h3>책임 (Responsibilities)</h3>
 * <ul>
 *   <li>UploadedFile Aggregate Root의 영속성 관리</li>
 *   <li>파일 해시 기반 중복 검사</li>
 *   <li>업로드 ID 기반 조회</li>
 * </ul>
 *
 * <h3>사용 예시 - Service에서 사용</h3>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class UploadFileUseCase {
 *
 *     private final UploadedFileRepository repository;
 *     private final EventBus eventBus;
 *
 *     @Transactional
 *     public UploadFileResponse execute(UploadFileCommand command) {
 *         // 1. Value Objects 생성
 *         FileName fileName = FileName.of(command.fileName());
 *         FileHash fileHash = FileHash.of(command.fileHash());
 *         FileSize fileSize = FileSize.ofBytes(command.fileSizeBytes());
 *
 *         // 2. 중복 검사
 *         Optional<UploadedFile> existing = repository.findByFileHash(fileHash);
 *         if (existing.isPresent()) {
 *             throw new DuplicateFileException(existing.get().getId());
 *         }
 *
 *         // 3. Aggregate Root 생성
 *         UploadId uploadId = UploadId.newId();
 *         UploadedFile uploadedFile = UploadedFile.create(
 *             uploadId, fileName, fileHash, fileSize
 *         );
 *
 *         // 4. 저장 (Domain Events 발행)
 *         UploadedFile saved = repository.save(uploadedFile);
 *
 *         return new UploadFileResponse(saved.getId());
 *     }
 * }
 * }</pre>
 *
 * <h3>사용 예시 - Infrastructure 구현체</h3>
 * <pre>{@code
 * @Repository
 * @RequiredArgsConstructor
 * public class JpaUploadedFileRepository implements UploadedFileRepository {
 *
 *     private final SpringDataUploadedFileRepository jpaRepository;
 *     private final EventBus eventBus;
 *
 *     @Override
 *     @Transactional
 *     public UploadedFile save(UploadedFile aggregate) {
 *         // 1. JPA 저장
 *         UploadedFile saved = jpaRepository.save(aggregate);
 *
 *         // 2. Domain Events 발행
 *         eventBus.publishAll(saved.getDomainEvents());
 *         saved.clearDomainEvents();
 *
 *         return saved;
 *     }
 *
 *     @Override
 *     public Optional<UploadedFile> findById(UploadId id) {
 *         return jpaRepository.findById(id);
 *     }
 *
 *     @Override
 *     public Optional<UploadedFile> findByFileHash(FileHash fileHash) {
 *         return jpaRepository.findByFileHash(fileHash);
 *     }
 * }
 * }</pre>
 *
 * <h3>사용 예시 - 중복 검사 UseCase</h3>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class CheckDuplicateFileUseCase {
 *
 *     private final UploadedFileRepository repository;
 *
 *     public DuplicateCheckResult execute(CheckDuplicateCommand command) {
 *         FileHash fileHash = FileHash.of(command.fileHash());
 *
 *         Optional<UploadedFile> existing = repository.findByFileHash(fileHash);
 *
 *         if (existing.isPresent()) {
 *             return DuplicateCheckResult.duplicate(
 *                 existing.get().getId(),
 *                 existing.get().getUploadedAt()
 *             );
 *         }
 *
 *         return DuplicateCheckResult.noDuplicate();
 *     }
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 * @see UploadedFile
 * @see UploadId
 * @see FileHash
 */
public interface UploadedFileRepository {

    /**
     * 업로드된 파일 저장
     *
     * <p>Aggregate Root를 저장하고 Domain Events를 발행합니다.
     * 구현체는 반드시 이벤트 발행 후 {@code clearDomainEvents()}를 호출해야 합니다.</p>
     *
     * @param aggregate 업로드된 파일 Aggregate Root
     * @return 저장된 Aggregate Root
     */
    UploadedFile save(UploadedFile aggregate);

    /**
     * 업로드 ID로 파일 조회
     *
     * <p>존재하지 않는 경우 {@code Optional.empty()}를 반환합니다.</p>
     *
     * @param id 업로드 ID
     * @return 업로드된 파일 (Optional)
     */
    Optional<UploadedFile> findById(UploadId id);

    /**
     * 파일 해시로 파일 조회
     *
     * <p>중복 파일 검사에 사용됩니다.</p>
     *
     * @param fileHash 파일 해시 (SHA-256)
     * @return 업로드된 파일 (Optional)
     */
    Optional<UploadedFile> findByFileHash(FileHash fileHash);

    /**
     * 업로드 ID로 파일 삭제
     *
     * <p>파일이 존재하지 않아도 예외를 발생시키지 않습니다.</p>
     *
     * @param id 업로드 ID
     */
    void deleteById(UploadId id);

    /**
     * 업로드된 파일 존재 여부 확인
     *
     * @param id 업로드 ID
     * @return 존재하면 true
     */
    boolean existsById(UploadId id);

    /**
     * 파일 해시로 존재 여부 확인
     *
     * <p>중복 파일 검사에 사용됩니다.</p>
     *
     * @param fileHash 파일 해시
     * @return 존재하면 true
     */
    boolean existsByFileHash(FileHash fileHash);

    long count();

    long countByStatus(UploadStatus status);
}
