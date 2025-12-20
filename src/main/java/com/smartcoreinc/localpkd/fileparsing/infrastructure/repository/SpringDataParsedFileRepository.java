package com.smartcoreinc.localpkd.fileparsing.infrastructure.repository;

import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFileId;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * SpringDataParsedFileRepository - Spring Data JPA Repository 인터페이스
 *
 * <p><b>Infrastructure Layer</b>: JPA를 사용한 데이터베이스 접근 인터페이스입니다.</p>
 *
 * <p><b>주요 기능</b>:</p>
 * <ul>
 *   <li>CRUD 작업 (JpaRepository 상속)</li>
 *   <li>UploadId 기반 조회</li>
 *   <li>존재 여부 확인</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * // JPA Repository를 통한 기본 CRUD
 * ParsedFile parsedFile = repository.save(parsedFile);
 * Optional<ParsedFile> found = repository.findById(parsedFileId);
 * boolean exists = repository.existsById(parsedFileId);
 *
 * // Custom Query
 * Optional<ParsedFile> found = repository.findByUploadId(uploadId);
 * </pre>
 *
 * @see com.smartcoreinc.localpkd.fileparsing.domain.repository.ParsedFileRepository
 * @see JpaParsedFileRepository
 */
public interface SpringDataParsedFileRepository extends JpaRepository<ParsedFile, ParsedFileId> {

    /**
     * UploadId로 ParsedFile 조회
     *
     * <p>File Upload Context의 UploadedFile과 1:1 관계입니다.</p>
     *
     * @param uploadId UploadId
     * @return Optional<ParsedFile>
     */
    @Query("SELECT pf FROM ParsedFile pf WHERE pf.uploadId = :uploadId")
    Optional<ParsedFile> findByUploadId(@Param("uploadId") UploadId uploadId);

    /**
     * UploadId로 존재 여부 확인
     *
     * @param uploadId UploadId
     * @return 존재하면 true
     */
    @Query("SELECT CASE WHEN COUNT(pf) > 0 THEN true ELSE false END FROM ParsedFile pf WHERE pf.uploadId = :uploadId")
    boolean existsByUploadId(@Param("uploadId") UploadId uploadId);

    // Count all CRLs in the parsed_crl collection table
    @Query(value = "SELECT COUNT(*) FROM parsed_crl", nativeQuery = true)
    long countAllCrls();
}
