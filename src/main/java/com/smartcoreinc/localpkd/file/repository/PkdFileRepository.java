package com.smartcoreinc.localpkd.file.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smartcoreinc.localpkd.common.enums.FileFormat;
import com.smartcoreinc.localpkd.common.enums.FileType;
import com.smartcoreinc.localpkd.common.enums.ProcessStatus;
import com.smartcoreinc.localpkd.file.entity.PkdFile;

/**
 * PKD 파일 Repository
 */
public interface PkdFileRepository extends JpaRepository<PkdFile, Long> {
    /**
     * File ID로 조회
     */
    Optional<PkdFile> findByFileId(String fileId);
    
    /**
     * 파일 타입으로 조회
     */
    List<PkdFile> findByFileType(FileType fileType);
    
    /**
     * 파일 포맷으로 조회
     */
    List<PkdFile> findByFileFormat(FileFormat fileFormat);
    
    /**
     * 국가 코드로 조회
     */
    List<PkdFile> findByCountryCode(String countryCode);
    
    /**
     * 업로드 상태로 조회
     */
    List<PkdFile> findByUploadStatus(ProcessStatus status);
    
    /**
     * Collection 번호로 조회
     */
    List<PkdFile> findByCollectionNumber(String collectionNumber);
    
    /**
     * Collection과 버전으로 조회
     */
    Optional<PkdFile> findByCollectionNumberAndVersionNumber(String collectionNumber, String versionNumber);
    
    /**
     * Delta 파일 조회
     */
    List<PkdFile> findByIsDeltaTrue();
    
    /**
     * Complete 파일 조회
     */
    List<PkdFile> findByIsDeltaFalse();
    
    /**
     * 처리 대기 중인 파일 조회
     */
    @Query("SELECT p FROM PkdFile p WHERE p.uploadStatus = 'UPLOADED' " +
           "AND (p.parseStatus IS NULL OR p.parseStatus = 'FAILED') " +
           "ORDER BY p.uploadedAt ASC")
    List<PkdFile> findPendingFiles();
    
    /**
     * 특정 Collection의 최신 버전 조회
     */
    @Query("SELECT MAX(p.versionNumber) FROM PkdFile p " +
           "WHERE p.collectionNumber = :collectionNumber " +
           "AND p.uploadStatus = 'APPLIED'")
    Optional<String> findLatestVersionByCollection(@Param("collectionNumber") String collectionNumber);
    
    /**
     * 특정 Collection의 최신 Complete 파일 조회
     */
    @Query("SELECT p FROM PkdFile p " +
           "WHERE p.collectionNumber = :collectionNumber " +
           "AND p.isDelta = false " +
           "AND p.uploadStatus = 'APPLIED' " +
           "ORDER BY p.versionNumber DESC")
    Optional<PkdFile> findLatestCompleteFileByCollection(@Param("collectionNumber") String collectionNumber);
    
    /**
     * 특정 Collection의 적용된 Delta 파일 목록 조회
     */
    @Query("SELECT p FROM PkdFile p " +
           "WHERE p.collectionNumber = :collectionNumber " +
           "AND p.isDelta = true " +
           "AND p.uploadStatus = 'APPLIED' " +
           "ORDER BY p.versionNumber ASC")
    List<PkdFile> findAppliedDeltaFilesByCollection(@Param("collectionNumber") String collectionNumber);
    
    /**
     * 특정 기간 동안 업로드된 파일 조회
     */
    @Query("SELECT p FROM PkdFile p " +
           "WHERE p.uploadedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY p.uploadedAt DESC")
    List<PkdFile> findByUploadedAtBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * 실패한 파일 조회
     */
    @Query("SELECT p FROM PkdFile p " +
           "WHERE p.uploadStatus = 'FAILED' " +
           "OR p.parseStatus = 'FAILED' " +
           "OR p.verifyStatus = 'FAILED' " +
           "OR p.ldapStatus = 'FAILED' " +
           "ORDER BY p.uploadedAt DESC")
    List<PkdFile> findFailedFiles();
    
    /**
     * 파일 타입과 상태로 조회
     */
    List<PkdFile> findByFileTypeAndUploadStatus(FileType fileType, ProcessStatus status);
    
    /**
     * SHA256 해시로 중복 파일 확인
     */
    Optional<PkdFile> findByFileHashSha256(String hash);
    
    /**
     * 파일 타입별 통계
     */
    @Query("SELECT p.fileType, COUNT(p) FROM PkdFile p " +
           "GROUP BY p.fileType")
    List<Object[]> countByFileType();
    
    /**
     * 상태별 통계
     */
    @Query("SELECT p.uploadStatus, COUNT(p) FROM PkdFile p " +
           "GROUP BY p.uploadStatus")
    List<Object[]> countByStatus();
    
    /**
     * Collection별 통계
     */
    @Query("SELECT p.collectionNumber, COUNT(p), " +
           "SUM(CASE WHEN p.isDelta = true THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN p.isDelta = false THEN 1 ELSE 0 END) " +
           "FROM PkdFile p " +
           "WHERE p.collectionNumber IS NOT NULL " +
           "GROUP BY p.collectionNumber")
    List<Object[]> getCollectionStatistics();
    
    /**
     * 최근 업로드된 파일 조회 (상위 N개)
     */
    List<PkdFile> findTop10ByOrderByUploadedAtDesc();
    
    /**
     * 특정 날짜 이전의 파일 조회 (정리용)
     */
    @Query("SELECT p FROM PkdFile p " +
           "WHERE p.uploadedAt < :cutoffDate " +
           "AND p.uploadStatus IN ('APPLIED', 'FAILED')")
    List<PkdFile> findOldProcessedFiles(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * 에러가 발생한 파일 개수
     */
    @Query("SELECT COUNT(p) FROM PkdFile p WHERE p.errorCount > 0")
    long countFilesWithErrors();
    
    /**
     * 평균 처리 시간 계산 (파싱)
     */
    @Query("SELECT AVG(TIMESTAMPDIFF(SECOND, p.parseStartedAt, p.parseCompletedAt)) " +
           "FROM PkdFile p " +
           "WHERE p.parseStartedAt IS NOT NULL " +
           "AND p.parseCompletedAt IS NOT NULL " +
           "AND p.parseStatus = 'PARSED'")
    Optional<Double> calculateAverageParseTime();
    
    /**
     * 특정 버전 범위의 Delta 파일 조회
     */
    @Query("SELECT p FROM PkdFile p " +
           "WHERE p.collectionNumber = :collectionNumber " +
           "AND p.isDelta = true " +
           "AND p.versionNumber BETWEEN :startVersion AND :endVersion " +
           "ORDER BY p.versionNumber ASC")
    List<PkdFile> findDeltaFilesByVersionRange(
        @Param("collectionNumber") String collectionNumber,
        @Param("startVersion") String startVersion,
        @Param("endVersion") String endVersion
    );
    
    /**
     * 파일 존재 여부 확인
     */
    boolean existsByFileId(String fileId);
    
    /**
     * SHA256 해시로 파일 존재 여부 확인
     */
    boolean existsByFileHashSha256(String hash);
    
    /**
     * Collection과 버전으로 존재 여부 확인
     */
    boolean existsByCollectionNumberAndVersionNumber(String collectionNumber, String versionNumber);
}
