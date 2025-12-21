package com.smartcoreinc.localpkd.fileparsing.domain.repository;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCode;
import com.smartcoreinc.localpkd.fileparsing.domain.model.MasterList;
import com.smartcoreinc.localpkd.fileparsing.domain.model.MasterListId;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;

import java.util.List;
import java.util.Optional;

/**
 * MasterListRepository - MasterList Aggregate Repository Interface (Domain Layer)
 *
 * <p><b>DDD Repository Pattern</b>:</p>
 * <ul>
 *   <li>Interface: Domain Layer에 정의 (이 파일)</li>
 *   <li>Implementation: Infrastructure Layer에서 구현 (JpaMasterListRepository)</li>
 *   <li>Dependency Inversion Principle: Domain이 Infrastructure에 의존하지 않음</li>
 * </ul>
 *
 * <p><b>구현체</b>:</p>
 * <ul>
 *   <li>JpaMasterListRepository: JPA 기반 구현</li>
 *   <li>SpringDataMasterListRepository: Spring Data JPA Interface</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * // Application Layer (Use Case)
 * {@literal @}Autowired
 * private MasterListRepository repository;
 *
 * // 저장 (Domain Events 자동 발행)
 * MasterList saved = repository.save(masterList);
 *
 * // 조회
 * Optional{@literal <MasterList>} found = repository.findById(masterListId);
 *
 * // UploadId로 조회
 * Optional{@literal <MasterList>} found = repository.findByUploadId(uploadId);
 *
 * // 국가 코드로 조회 (최신순)
 * List{@literal <MasterList>} koreaLists = repository.findByCountryCodeOrderByCreatedAtDesc(countryCode);
 * </pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-27
 */
public interface MasterListRepository {

    /**
     * MasterList 저장
     *
     * <p><b>중요</b>: 저장 시 Aggregate Root의 Domain Events가 자동으로 발행됩니다.</p>
     *
     * @param masterList MasterList Aggregate
     * @return 저장된 MasterList
     */
    MasterList save(MasterList masterList);

    /**
     * ID로 MasterList 조회
     *
     * @param id MasterListId
     * @return MasterList (Optional)
     */
    Optional<MasterList> findById(MasterListId id);

    /**
     * UploadId로 MasterList 조회 (단일)
     *
     * <p>ML 파일의 경우 하나의 업로드 파일에 대해 하나의 Master List만 존재</p>
     *
     * @param uploadId UploadId
     * @return MasterList (Optional)
     */
    Optional<MasterList> findByUploadId(UploadId uploadId);

    /**
     * UploadId로 모든 MasterList 조회 (리스트)
     *
     * <p>LDIF 파일의 경우 하나의 업로드 파일에 여러 Master List가 존재할 수 있음</p>
     *
     * @param uploadId UploadId
     * @return MasterList 목록 (생성일자 오름차순)
     */
    List<MasterList> findAllByUploadId(UploadId uploadId);

    /**
     * 국가 코드로 MasterList 조회 (생성일자 내림차순)
     *
     * <p>동일 국가의 여러 버전 Master List가 있을 수 있으므로 List 반환</p>
     * <p>최신 Master List가 첫 번째 요소로 반환됨</p>
     *
     * @param countryCode CountryCode
     * @return MasterList 목록 (최신순)
     */
    List<MasterList> findByCountryCodeOrderByCreatedAtDesc(CountryCode countryCode);

    /**
     * 모든 MasterList 조회 (생성일자 내림차순)
     *
     * @return 모든 MasterList 목록 (최신순)
     */
    List<MasterList> findAllOrderByCreatedAtDesc();

    /**
     * MasterList 삭제
     *
     * @param id MasterListId
     */
    void deleteById(MasterListId id);

    /**
     * ID 존재 여부 확인
     *
     * @param id MasterListId
     * @return 존재 여부
     */
    boolean existsById(MasterListId id);

    /**
     * UploadId 존재 여부 확인
     *
     * @param uploadId UploadId
     * @return 존재 여부
     */
    boolean existsByUploadId(UploadId uploadId);

    /**
     * 전체 MasterList 개수 조회
     *
     * @return MasterList 총 개수
     */
    long count();

    /**
     * 국가별 MasterList 개수 조회
     *
     * @param countryCode CountryCode
     * @return 해당 국가의 MasterList 개수
     */
    long countByCountryCode(CountryCode countryCode);

    /**
     * UploadId별 MasterList 개수 조회
     *
     * @param uploadId UploadId
     * @return 해당 업로드의 MasterList 개수
     */
    long countByUploadId(UploadId uploadId);
}
