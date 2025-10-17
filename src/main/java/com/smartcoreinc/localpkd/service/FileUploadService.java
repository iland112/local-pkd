package com.smartcoreinc.localpkd.service;

import com.smartcoreinc.localpkd.common.domain.FileMetadata;
import com.smartcoreinc.localpkd.common.entity.FileUploadHistory;
import com.smartcoreinc.localpkd.common.enums.FileFormat;
import com.smartcoreinc.localpkd.common.enums.UploadStatus;
import com.smartcoreinc.localpkd.common.repository.FileUploadHistoryRepository;
import com.smartcoreinc.localpkd.common.util.ChecksumValidationResult;
import com.smartcoreinc.localpkd.common.util.ChecksumValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 파일 업로드 서비스
 *
 * ICAO PKD 파일 업로드의 전체 워크플로우를 관리합니다:
 * 1. Phase 1: 파일 수신 및 기본 검증
 * 2. Phase 2: 체크섬 검증
 * 3. Phase 3: 중복 및 버전 관리
 * 4. Phase 4: 파일 파싱 및 저장 (다른 서비스로 위임)
 *
 * @author SmartCore Inc.
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final FileUploadHistoryRepository uploadHistoryRepository;

    @Value("${app.upload.temp-dir:./data/temp}")
    private String tempUploadDir;

    @Value("${app.upload.permanent-dir:./data/uploads}")
    private String permanentUploadDir;

    @Value("${app.upload.max-file-size:104857600}") // 100MB
    private long maxFileSize;

    // ========================================
    // Phase 1: 파일 수신 및 기본 검증
    // ========================================

    /**
     * 파일 업로드 - 전체 워크플로우
     *
     * @param file 업로드된 파일
     * @param expectedChecksum 기대되는 체크섬 (선택사항)
     * @return FileUploadHistory
     */
    @Transactional
    public FileUploadHistory uploadFile(MultipartFile file, String expectedChecksum) {
        log.info("Starting file upload: {}", file.getOriginalFilename());

        // Phase 1: 파일 수신 및 기본 검증
        FileUploadHistory uploadHistory = receiveAndValidateFile(file, expectedChecksum);

        // Phase 2: 체크섬 검증 (백그라운드에서 수행 가능)
        if (uploadHistory.getStatus() == UploadStatus.RECEIVED) {
            validateChecksumPhase(uploadHistory);
        }

        // Phase 3: 중복 검사
        if (uploadHistory.getStatus() == UploadStatus.CHECKSUM_VALIDATING ||
            uploadHistory.getStatus() == UploadStatus.RECEIVED) {
            checkDuplicatePhase(uploadHistory);
        }

        uploadHistoryRepository.save(uploadHistory);
        log.info("File upload completed with status: {}", uploadHistory.getStatus());

        return uploadHistory;
    }

    /**
     * Phase 1: 파일 수신 및 기본 검증
     *
     * @param file 업로드 파일
     * @param expectedChecksum 기대 체크섬
     * @return FileUploadHistory
     */
    private FileUploadHistory receiveAndValidateFile(MultipartFile file, String expectedChecksum) {
        FileUploadHistory history = new FileUploadHistory();

        try {
            // 파일명 검증
            String filename = file.getOriginalFilename();
            if (filename == null || filename.isEmpty()) {
                throw new IllegalArgumentException("파일명이 없습니다.");
            }

            history.setFilename(filename);
            history.setUploadedAt(LocalDateTime.now());
            history.setStatus(UploadStatus.RECEIVED);

            // 파일 크기 검증
            long fileSize = file.getSize();
            if (fileSize == 0) {
                throw new IllegalArgumentException("파일이 비어있습니다.");
            }
            if (fileSize > maxFileSize) {
                throw new IllegalArgumentException(
                    String.format("파일 크기가 너무 큽니다. 최대: %d bytes", maxFileSize)
                );
            }

            history.setFileSizeBytes(fileSize);
            history.setFileSizeDisplay(FileMetadata.formatFileSize(fileSize));

            // 파일 포맷 검증
            if (!FileFormat.isValidFilename(filename)) {
                throw new IllegalArgumentException("지원하지 않는 파일 형식입니다: " + filename);
            }

            FileFormat fileFormat = FileFormat.detectFromFilename(filename);
            history.setFileFormat(fileFormat);
            history.setCollectionNumber(FileFormat.extractCollectionNumber(filename));
            history.setVersion(FileFormat.extractVersion(filename));
            history.setIsDeprecated(fileFormat.isDeprecated());

            // 체크섬 설정
            if (expectedChecksum != null && !expectedChecksum.trim().isEmpty()) {
                String normalized = ChecksumValidator.normalize(expectedChecksum);
                if (!ChecksumValidator.isValidSHA1Format(normalized)) {
                    throw new IllegalArgumentException("유효하지 않은 SHA-1 체크섬 형식입니다.");
                }
                history.setExpectedChecksum(normalized);
            }

            // 임시 저장소에 파일 저장
            String tempPath = saveToTemporary(file);
            history.setLocalFilePath(tempPath);

            log.info("File received successfully: {} ({} bytes)", filename, fileSize);

        } catch (Exception e) {
            log.error("Failed to receive file", e);
            history.setStatus(UploadStatus.FAILED);
            history.setErrorMessage(e.getMessage());
        }

        return history;
    }

    /**
     * 임시 저장소에 파일 저장
     *
     * @param file 업로드 파일
     * @return 저장된 파일 경로
     * @throws IOException 파일 저장 실패
     */
    private String saveToTemporary(MultipartFile file) throws IOException {
        // 임시 디렉토리 생성
        Path tempDir = Paths.get(tempUploadDir);
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }

        // 고유한 파일명 생성 (타임스탬프 + 원본 파일명)
        String timestamp = String.valueOf(System.currentTimeMillis());
        String filename = timestamp + "_" + file.getOriginalFilename();
        Path targetPath = tempDir.resolve(filename);

        // 파일 저장
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        log.debug("File saved to temporary directory: {}", targetPath);
        return targetPath.toString();
    }

    // ========================================
    // Phase 2: 체크섬 검증
    // ========================================

    /**
     * Phase 2: 체크섬 검증
     *
     * @param history 업로드 이력
     */
    private void validateChecksumPhase(FileUploadHistory history) {
        try {
            history.changeStatus(UploadStatus.CHECKSUM_VALIDATING);

            File file = new File(history.getLocalFilePath());
            ChecksumValidationResult result;

            if (history.getExpectedChecksum() != null) {
                // 기대 체크섬이 있는 경우 검증 수행
                result = ChecksumValidator.validate(file, history.getExpectedChecksum());

                history.setCalculatedChecksum(result.getCalculatedChecksum());
                history.setChecksumValidated(true);
                history.setChecksumValid(result.matches());
                history.setChecksumElapsedTimeMs(result.getElapsedTimeMs());

                if (!result.matches()) {
                    history.changeStatus(UploadStatus.CHECKSUM_INVALID);
                    history.setErrorMessage(result.getSummary());
                    log.warn("Checksum validation failed: {}", result.getSummary());
                }
            } else {
                // 기대 체크섬이 없는 경우 계산만 수행
                String checksum = ChecksumValidator.calculateSHA1(file);
                history.setCalculatedChecksum(checksum);
                history.setChecksumValidated(false);
                history.setChecksumValid(null);
                log.info("Checksum calculated (not validated): {}", checksum);
            }

        } catch (Exception e) {
            log.error("Checksum validation error", e);
            history.setStatus(UploadStatus.FAILED);
            history.setErrorMessage("체크섬 검증 실패: " + e.getMessage());
        }
    }

    // ========================================
    // Phase 3: 중복 및 버전 관리
    // ========================================

    /**
     * Phase 3: 중복 검사 및 버전 비교
     *
     * @param history 업로드 이력
     */
    private void checkDuplicatePhase(FileUploadHistory history) {
        try {
            // 동일 Collection의 최신 버전 조회
            Optional<FileUploadHistory> latestOpt = uploadHistoryRepository
                    .findLatestByCollection(history.getCollectionNumber());

            if (latestOpt.isEmpty()) {
                // 첫 번째 업로드
                history.setIsDuplicate(false);
                history.setIsNewerVersion(true);
                log.info("First upload for collection {}", history.getCollectionNumber());
                return;
            }

            FileUploadHistory latest = latestOpt.get();

            // 체크섬으로 정확히 동일한 파일인지 확인
            if (history.getCalculatedChecksum() != null &&
                history.getCalculatedChecksum().equals(latest.getCalculatedChecksum())) {
                history.setIsDuplicate(true);
                history.setIsNewerVersion(false);
                history.changeStatus(UploadStatus.DUPLICATE_DETECTED);
                history.setErrorMessage(
                    String.format("동일한 파일이 이미 존재합니다 (ID: %d, 버전: %s)",
                        latest.getId(), latest.getVersion())
                );
                log.warn("Duplicate file detected: {}", history.getFilename());
                return;
            }

            // 버전 비교
            int comparison = compareVersions(history.getVersion(), latest.getVersion());

            if (comparison > 0) {
                // 새로운 버전
                history.setIsDuplicate(false);
                history.setIsNewerVersion(true);
                history.setReplacedFileId(latest.getId());
                log.info("Newer version detected: {} > {}", history.getVersion(), latest.getVersion());
            } else if (comparison == 0) {
                // 동일 버전이지만 체크섬이 다른 경우
                history.setIsDuplicate(true);
                history.setIsNewerVersion(false);
                history.changeStatus(UploadStatus.DUPLICATE_DETECTED);
                history.setErrorMessage(
                    String.format("동일한 버전의 다른 파일이 존재합니다 (버전: %s)", latest.getVersion())
                );
                log.warn("Same version with different content: {}", history.getVersion());
            } else {
                // 이전 버전
                history.setIsDuplicate(false);
                history.setIsNewerVersion(false);
                history.changeStatus(UploadStatus.OLDER_VERSION);
                history.setErrorMessage(
                    String.format("현재 시스템 버전(%s)보다 오래된 버전입니다", latest.getVersion())
                );
                log.warn("Older version detected: {} < {}", history.getVersion(), latest.getVersion());
            }

        } catch (Exception e) {
            log.error("Duplicate check error", e);
            // 중복 체크 실패는 치명적이지 않으므로 계속 진행
            history.setIsDuplicate(false);
        }
    }

    /**
     * 버전 문자열 비교
     *
     * @param version1 버전 1
     * @param version2 버전 2
     * @return 비교 결과 (1: v1 > v2, 0: 같음, -1: v1 < v2)
     */
    private int compareVersions(String version1, String version2) {
        if (version1 == null || version2 == null) {
            return 0;
        }

        // 숫자 버전인 경우 (LDIF)
        try {
            int v1 = Integer.parseInt(version1);
            int v2 = Integer.parseInt(version2);
            return Integer.compare(v1, v2);
        } catch (NumberFormatException e) {
            // 문자열 비교 (ML 파일의 경우)
            return version1.compareTo(version2);
        }
    }

    // ========================================
    // Phase 4: 파일 파싱 및 저장 (별도 메서드로 분리)
    // ========================================

    /**
     * 영구 저장소로 파일 이동
     *
     * @param history 업로드 이력
     * @return 영구 저장 경로
     * @throws IOException 파일 이동 실패
     */
    public String moveToPermanentStorage(FileUploadHistory history) throws IOException {
        Path permanentDir = Paths.get(permanentUploadDir, history.getCollectionNumber());
        if (!Files.exists(permanentDir)) {
            Files.createDirectories(permanentDir);
        }

        Path sourcePath = Paths.get(history.getLocalFilePath());
        Path targetPath = permanentDir.resolve(history.getFilename());

        Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

        log.info("File moved to permanent storage: {}", targetPath);
        return targetPath.toString();
    }

    // ========================================
    // 조회 메서드
    // ========================================

    /**
     * 최근 업로드 파일 조회
     *
     * @param limit 조회 개수
     * @return 최근 업로드 파일 목록
     */
    public java.util.List<FileUploadHistory> getRecentUploads(int limit) {
        return uploadHistoryRepository.findRecentUploads(
            org.springframework.data.domain.PageRequest.of(0, limit)
        ).getContent();
    }

    /**
     * 업로드 이력 조회
     *
     * @param id 이력 ID
     * @return 업로드 이력
     */
    public Optional<FileUploadHistory> getUploadHistory(Long id) {
        return uploadHistoryRepository.findById(id);
    }

    /**
     * 다중 조건으로 업로드 이력 검색
     *
     * @param criteria 검색 조건
     * @param pageable 페이징 정보
     * @return 검색 결과 (페이징)
     */
    public org.springframework.data.domain.Page<FileUploadHistory> searchUploadHistory(
            com.smartcoreinc.localpkd.common.dto.FileSearchCriteria criteria,
            org.springframework.data.domain.Pageable pageable) {

        log.debug("Searching upload history with criteria: {}", criteria);

        // 검색 조건 검증
        if (criteria != null && !criteria.isValidDateRange()) {
            throw new IllegalArgumentException("시작 날짜가 종료 날짜보다 늦을 수 없습니다.");
        }

        // 조건이 모두 비어있으면 전체 조회
        if (criteria == null || criteria.isEmpty()) {
            return uploadHistoryRepository.findRecentUploads(pageable);
        }

        // 동적 검색 쿼리 실행
        return uploadHistoryRepository.searchByMultipleCriteria(
                criteria.getFileFormat(),
                criteria.getUploadStatus(),
                criteria.getStartDate(),
                criteria.getEndDate(),
                criteria.getFileName(),
                pageable
        );
    }

    /**
     * 파일 해시로 중복 파일 조회
     *
     * @param fileHash SHA-256 파일 해시
     * @return 기존 업로드 파일 (있으면)
     */
    public Optional<FileUploadHistory> findByFileHash(String fileHash) {
        return uploadHistoryRepository.findByFileHash(fileHash);
    }

    /**
     * 전체 업로드 통계 조회
     *
     * @return 통계 정보 맵
     */
    public java.util.Map<String, Object> getUploadStatistics() {
        long totalCount = uploadHistoryRepository.count();
        long successCount = uploadHistoryRepository.countByStatus(UploadStatus.SUCCESS);
        long failedCount = uploadHistoryRepository.countByStatus(UploadStatus.FAILED);

        // 진행 중인 업로드 수 계산 (RECEIVED, VALIDATING 등)
        long pendingCount = uploadHistoryRepository.findInProgressUploads().size();

        return java.util.Map.of(
                "totalCount", totalCount,
                "successCount", successCount,
                "failedCount", failedCount,
                "pendingCount", pendingCount,
                "successRate", totalCount > 0 ? (double) successCount / totalCount * 100 : 0.0
        );
    }
}
