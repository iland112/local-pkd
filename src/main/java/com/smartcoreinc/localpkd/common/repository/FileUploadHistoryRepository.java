package com.smartcoreinc.localpkd.common.repository;

import com.smartcoreinc.localpkd.common.entity.FileUploadHistory;
import com.smartcoreinc.localpkd.common.enums.UploadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 파일 업로드 이력 Repository
 *
 * @author SmartCore Inc.
 * @version 1.0
 */
@Repository
public interface FileUploadHistoryRepository extends JpaRepository<FileUploadHistory, Long> {

    /**
     * 특정 Collection의 최신 업로드 파일 조회
     *
     * @param collectionNumber Collection 번호 (001, 002, 003)
     * @return 최신 업로드 파일
     */
    @Query("SELECT f FROM FileUploadHistory f " +
           "WHERE f.collectionNumber = :collectionNumber " +
           "AND f.status = 'SUCCESS' " +
           "ORDER BY f.version DESC, f.uploadedAt DESC")
    Optional<FileUploadHistory> findLatestByCollection(@Param("collectionNumber") String collectionNumber);

    /**
     * 특정 Collection의 모든 성공한 업로드 파일 조회
     *
     * @param collectionNumber Collection 번호
     * @param pageable 페이징 정보
     * @return 업로드 파일 목록
     */
    Page<FileUploadHistory> findByCollectionNumberAndStatus(
        String collectionNumber,
        UploadStatus status,
        Pageable pageable
    );

    /**
     * 파일명으로 조회
     *
     * @param filename 파일명
     * @return 업로드 파일 목록
     */
    List<FileUploadHistory> findByFilename(String filename);

    /**
     * 체크섬으로 조회 (정확히 동일한 파일 찾기)
     *
     * @param checksum SHA-1 체크섬
     * @return 업로드 파일 목록
     */
    List<FileUploadHistory> findByCalculatedChecksum(String checksum);

    /**
     * 특정 버전의 파일 조회
     *
     * @param collectionNumber Collection 번호
     * @param version 버전
     * @return 업로드 파일 목록
     */
    List<FileUploadHistory> findByCollectionNumberAndVersion(
        String collectionNumber,
        String version
    );

    /**
     * 최근 업로드 파일 조회 (모든 Collection)
     *
     * @param pageable 페이징 정보
     * @return 최근 업로드 파일 목록
     */
    @Query("SELECT f FROM FileUploadHistory f ORDER BY f.uploadedAt DESC")
    Page<FileUploadHistory> findRecentUploads(Pageable pageable);

    /**
     * 진행 중인 업로드 조회
     *
     * @return 진행 중인 업로드 목록
     */
    @Query("SELECT f FROM FileUploadHistory f " +
           "WHERE f.status IN ('RECEIVED', 'VALIDATING', 'CHECKSUM_VALIDATING', 'PARSING', 'STORING') " +
           "ORDER BY f.uploadedAt DESC")
    List<FileUploadHistory> findInProgressUploads();

    /**
     * 실패한 업로드 조회
     *
     * @param pageable 페이징 정보
     * @return 실패한 업로드 목록
     */
    @Query("SELECT f FROM FileUploadHistory f " +
           "WHERE f.status IN ('FAILED', 'ROLLBACK', 'CHECKSUM_INVALID') " +
           "ORDER BY f.uploadedAt DESC")
    Page<FileUploadHistory> findFailedUploads(Pageable pageable);

    /**
     * Collection별 통계 조회
     *
     * @param collectionNumber Collection 번호
     * @return 업로드 통계
     */
    @Query("SELECT COUNT(f), " +
           "SUM(CASE WHEN f.status = 'SUCCESS' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN f.status IN ('FAILED', 'ROLLBACK') THEN 1 ELSE 0 END) " +
           "FROM FileUploadHistory f " +
           "WHERE f.collectionNumber = :collectionNumber")
    Object[] getUploadStatistics(@Param("collectionNumber") String collectionNumber);

    /**
     * 중복 파일 조회
     *
     * @return 중복 파일 목록
     */
    @Query("SELECT f FROM FileUploadHistory f " +
           "WHERE f.isDuplicate = true " +
           "ORDER BY f.uploadedAt DESC")
    List<FileUploadHistory> findDuplicateUploads();

    /**
     * 특정 기간 내 업로드 파일 조회
     *
     * @param startDate 시작 일시
     * @param endDate 종료 일시
     * @param pageable 페이징 정보
     * @return 업로드 파일 목록
     */
    @Query("SELECT f FROM FileUploadHistory f " +
           "WHERE f.uploadedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY f.uploadedAt DESC")
    Page<FileUploadHistory> findByUploadedAtBetween(
        @Param("startDate") java.time.LocalDateTime startDate,
        @Param("endDate") java.time.LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * 대체된 파일 조회
     *
     * @param replacedFileId 대체된 파일 ID
     * @return 대체한 새 파일
     */
    Optional<FileUploadHistory> findByReplacedFileId(Long replacedFileId);

    /**
     * 파일 해시로 조회 (중복 검사용)
     *
     * @param fileHash SHA-256 파일 해시
     * @return 업로드 파일
     */
    Optional<FileUploadHistory> findByFileHash(String fileHash);

    /**
     * 원본 파일명과 업로드 상태로 조회
     *
     * @param originalFileName 원본 파일명
     * @param uploadStatus 업로드 상태
     * @return 업로드 파일 목록
     */
    List<FileUploadHistory> findByOriginalFileNameAndUploadStatus(
        String originalFileName,
        UploadStatus uploadStatus
    );

    /**
     * 동적 검색 조건으로 파일 검색
     *
     * @param fileFormat 파일 포맷 (nullable)
     * @param uploadStatus 업로드 상태 (nullable)
     * @param startDate 시작 날짜 (nullable)
     * @param endDate 종료 날짜 (nullable)
     * @param fileName 파일명 검색 키워드 (nullable)
     * @param pageable 페이징 정보
     * @return 검색 결과
     */
    @Query("SELECT f FROM FileUploadHistory f " +
           "WHERE (:fileFormat IS NULL OR f.fileFormat = :fileFormat) " +
           "AND (:uploadStatus IS NULL OR f.uploadStatus = :uploadStatus) " +
           "AND (:startDate IS NULL OR f.uploadedAt >= :startDate) " +
           "AND (:endDate IS NULL OR f.uploadedAt <= :endDate) " +
           "AND (:fileName IS NULL OR LOWER(f.originalFileName) LIKE LOWER(CONCAT('%', :fileName, '%'))) " +
           "ORDER BY f.uploadedAt DESC")
    Page<FileUploadHistory> searchByMultipleCriteria(
        @Param("fileFormat") com.smartcoreinc.localpkd.common.enums.FileFormat fileFormat,
        @Param("uploadStatus") UploadStatus uploadStatus,
        @Param("startDate") java.time.LocalDateTime startDate,
        @Param("endDate") java.time.LocalDateTime endDate,
        @Param("fileName") String fileName,
        Pageable pageable
    );

    /**
     * 업로드 상태별 개수 조회
     *
     * @param uploadStatus 업로드 상태
     * @return 개수
     */
    long countByUploadStatus(UploadStatus uploadStatus);
}
