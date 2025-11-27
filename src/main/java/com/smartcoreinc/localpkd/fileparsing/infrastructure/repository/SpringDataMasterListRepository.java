package com.smartcoreinc.localpkd.fileparsing.infrastructure.repository;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCode;
import com.smartcoreinc.localpkd.fileparsing.domain.model.MasterList;
import com.smartcoreinc.localpkd.fileparsing.domain.model.MasterListId;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SpringDataMasterListRepository - Spring Data JPA Repository for Master List
 *
 * <p><b>Infrastructure Layer</b>: JPA를 사용한 Master List 데이터베이스 접근 인터페이스입니다.</p>
 *
 * <p><b>주요 기능</b>:</p>
 * <ul>
 *   <li>CRUD 작업 (JpaRepository 상속)</li>
 *   <li>UploadId 기반 조회</li>
 *   <li>CountryCode 기반 조회 (최신순)</li>
 *   <li>존재 여부 확인</li>
 *   <li>통계 쿼리</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * // JPA Repository를 통한 기본 CRUD
 * MasterList masterList = repository.save(masterList);
 * Optional<MasterList> found = repository.findById(masterListId);
 *
 * // Custom Query
 * Optional<MasterList> found = repository.findByUploadId(uploadId);
 * List<MasterList> koreaLists = repository.findByCountryCodeOrderByCreatedAtDesc(countryCode);
 * </pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-27
 * @see com.smartcoreinc.localpkd.fileparsing.domain.repository.MasterListRepository
 * @see JpaMasterListRepository
 */
public interface SpringDataMasterListRepository extends JpaRepository<MasterList, MasterListId> {

    /**
     * UploadId로 MasterList 조회
     *
     * <p>하나의 업로드 파일에 대해 하나의 Master List만 존재해야 합니다.</p>
     *
     * @param uploadId UploadId
     * @return Optional<MasterList>
     */
    @Query("SELECT ml FROM MasterList ml WHERE ml.uploadId = :uploadId")
    Optional<MasterList> findByUploadId(@Param("uploadId") UploadId uploadId);

    /**
     * CountryCode로 MasterList 조회 (생성일자 내림차순)
     *
     * <p>동일 국가의 여러 버전 Master List가 있을 수 있으므로 List 반환</p>
     *
     * @param countryCode CountryCode
     * @return List<MasterList> (최신순)
     */
    @Query("SELECT ml FROM MasterList ml WHERE ml.countryCode = :countryCode ORDER BY ml.createdAt DESC")
    List<MasterList> findByCountryCodeOrderByCreatedAtDesc(@Param("countryCode") CountryCode countryCode);

    /**
     * 모든 MasterList 조회 (생성일자 내림차순)
     *
     * @return List<MasterList> (최신순)
     */
    @Query("SELECT ml FROM MasterList ml ORDER BY ml.createdAt DESC")
    List<MasterList> findAllOrderByCreatedAtDesc();

    /**
     * UploadId로 존재 여부 확인
     *
     * @param uploadId UploadId
     * @return 존재하면 true
     */
    @Query("SELECT CASE WHEN COUNT(ml) > 0 THEN true ELSE false END FROM MasterList ml WHERE ml.uploadId = :uploadId")
    boolean existsByUploadId(@Param("uploadId") UploadId uploadId);

    /**
     * CountryCode로 MasterList 개수 조회
     *
     * @param countryCode CountryCode
     * @return MasterList 개수
     */
    @Query("SELECT COUNT(ml) FROM MasterList ml WHERE ml.countryCode = :countryCode")
    long countByCountryCode(@Param("countryCode") CountryCode countryCode);
}
