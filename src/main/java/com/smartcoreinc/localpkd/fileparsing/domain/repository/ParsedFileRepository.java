package com.smartcoreinc.localpkd.fileparsing.domain.repository;

import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFileId;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;

import java.util.Optional;

/**
 * ParsedFileRepository - ParsedFile Aggregate Repository Interface (Domain Layer)
 *
 * <p><b>DDD Repository Pattern</b>:</p>
 * <ul>
 *   <li>Interface: Domain Layer에 정의 (이 파일)</li>
 *   <li>Implementation: Infrastructure Layer에서 구현 (JpaParsedFileRepository)</li>
 *   <li>Dependency Inversion Principle: Domain이 Infrastructure에 의존하지 않음</li>
 * </ul>
 *
 * <p><b>구현체</b>:</p>
 * <ul>
 *   <li>JpaParsedFileRepository: JPA 기반 구현</li>
 *   <li>SpringDataParsedFileRepository: Spring Data JPA Interface</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * // Application Layer (Use Case)
 * {@literal @}Autowired
 * private ParsedFileRepository repository;
 *
 * // 저장 (Domain Events 자동 발행)
 * ParsedFile saved = repository.save(parsedFile);
 *
 * // 조회
 * Optional{@literal <ParsedFile>} found = repository.findById(parsedFileId);
 *
 * // UploadId로 조회
 * Optional{@literal <ParsedFile>} found = repository.findByUploadId(uploadId);
 * </pre>
 */
public interface ParsedFileRepository {

    /**
     * ParsedFile 저장
     *
     * <p><b>중요</b>: 저장 시 Aggregate Root의 Domain Events가 자동으로 발행됩니다.</p>
     *
     * @param parsedFile ParsedFile Aggregate
     * @return 저장된 ParsedFile
     */
    ParsedFile save(ParsedFile parsedFile);

    /**
     * ID로 ParsedFile 조회
     *
     * @param id ParsedFileId
     * @return ParsedFile (Optional)
     */
    Optional<ParsedFile> findById(ParsedFileId id);

    /**
     * UploadId로 ParsedFile 조회
     *
     * <p>하나의 업로드 파일에 대해 하나의 파싱 결과만 존재해야 함</p>
     *
     * @param uploadId UploadId
     * @return ParsedFile (Optional)
     */
    Optional<ParsedFile> findByUploadId(UploadId uploadId);

    /**
     * ParsedFile 삭제
     *
     * @param id ParsedFileId
     */
    void deleteById(ParsedFileId id);

    /**
     * ID 존재 여부 확인
     *
     * @param id ParsedFileId
     * @return 존재 여부
     */
    boolean existsById(ParsedFileId id);

    /**
     * UploadId 존재 여부 확인
     *
     * @param uploadId UploadId
     * @return 존재 여부
     */
    boolean existsByUploadId(UploadId uploadId);
}
