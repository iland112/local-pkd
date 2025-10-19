package com.smartcoreinc.localpkd.fileupload.infrastructure.repository;

import com.smartcoreinc.localpkd.fileupload.domain.model.FileHash;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data JPA Repository - 업로드된 파일
 *
 * <p>Spring Data JPA가 제공하는 JpaRepository 인터페이스입니다.
 * Infrastructure Layer의 구현 세부사항으로, Domain Layer에서는 직접 사용하지 않습니다.</p>
 *
 * <h3>역할</h3>
 * <ul>
 *   <li>Spring Data JPA의 기본 CRUD 메서드 제공</li>
 *   <li>커스텀 쿼리 메서드 정의</li>
 *   <li>{@link JpaUploadedFileRepository}에서 사용됨</li>
 * </ul>
 *
 * <h3>사용 예시 - JpaUploadedFileRepository에서 사용</h3>
 * <pre>{@code
 * @Repository
 * @RequiredArgsConstructor
 * public class JpaUploadedFileRepository implements UploadedFileRepository {
 *
 *     private final SpringDataUploadedFileRepository jpaRepository;
 *
 *     @Override
 *     public UploadedFile save(UploadedFile aggregate) {
 *         return jpaRepository.save(aggregate);
 *     }
 *
 *     @Override
 *     public Optional<UploadedFile> findByFileHash(FileHash fileHash) {
 *         return jpaRepository.findByFileHash(fileHash);
 *     }
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 * @see UploadedFile
 * @see JpaUploadedFileRepository
 */
public interface SpringDataUploadedFileRepository extends JpaRepository<UploadedFile, UploadId>, JpaSpecificationExecutor<UploadedFile> {

    /**
     * 파일 해시로 업로드된 파일 조회
     *
     * <p>FileHash Value Object의 value 필드로 조회합니다.</p>
     *
     * @param fileHash 파일 해시 Value Object
     * @return 업로드된 파일 (Optional)
     */
    @Query("SELECT u FROM UploadedFile u WHERE u.fileHash.value = :#{#fileHash.value}")
    Optional<UploadedFile> findByFileHash(@Param("fileHash") FileHash fileHash);

    /**
     * 파일 해시로 존재 여부 확인
     *
     * @param fileHash 파일 해시 Value Object
     * @return 존재하면 true
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UploadedFile u WHERE u.fileHash.value = :#{#fileHash.value}")
    boolean existsByFileHash(@Param("fileHash") FileHash fileHash);
}
