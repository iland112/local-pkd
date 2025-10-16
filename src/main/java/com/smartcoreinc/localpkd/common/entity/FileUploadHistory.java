package com.smartcoreinc.localpkd.common.entity;

import com.smartcoreinc.localpkd.common.enums.FileFormat;
import com.smartcoreinc.localpkd.common.enums.UploadStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 파일 업로드 이력
 *
 * ICAO PKD 파일 업로드 및 처리 과정의 모든 정보를 기록합니다.
 *
 * @author SmartCore Inc.
 * @version 1.0
 */
@Entity
@Table(name = "file_upload_history", indexes = {
    @Index(name = "idx_upload_status", columnList = "status"),
    @Index(name = "idx_upload_date", columnList = "uploadedAt"),
    @Index(name = "idx_collection_version", columnList = "collectionNumber,version"),
    @Index(name = "idx_checksum", columnList = "calculatedChecksum")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class FileUploadHistory {

    // ========================================
    // Primary Key
    // ========================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========================================
    // 파일 정보
    // ========================================

    /**
     * 원본 파일명
     * 예: icaopkd-001-complete-009410.ldif
     */
    @Column(nullable = false, length = 255)
    private String filename;

    /**
     * Collection 번호 (001, 002, 003)
     */
    @Column(length = 3)
    private String collectionNumber;

    /**
     * 파일 버전
     * - LDIF: 숫자 (예: 009410)
     * - ML: Month/Year (예: July2025)
     */
    @Column(length = 50)
    private String version;

    /**
     * 파일 포맷
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private FileFormat fileFormat;

    /**
     * 파일 크기 (bytes)
     */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /**
     * 파일 크기 (human-readable)
     * 예: "74.3 MiB"
     */
    @Column(length = 20)
    private String fileSizeDisplay;

    // ========================================
    // 업로드 정보
    // ========================================

    /**
     * 업로드 일시
     */
    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    /**
     * 업로드 사용자 ID
     * TODO: Spring Security 연동 시 @CreatedBy 사용
     */
    @Column(length = 100)
    private String uploadedBy;

    /**
     * 로컬 파일 경로
     */
    @Column(length = 500)
    private String localFilePath;

    // ========================================
    // 체크섬 검증 정보
    // ========================================

    /**
     * 계산된 SHA-1 체크섬
     */
    @Column(length = 40)
    private String calculatedChecksum;

    /**
     * 기대되는 체크섬 (ICAO 공식, 사용자 입력)
     */
    @Column(length = 40)
    private String expectedChecksum;

    /**
     * 체크섬 검증 여부
     */
    @Column(name = "checksum_validated")
    private Boolean checksumValidated;

    /**
     * 체크섬 일치 여부
     */
    @Column(name = "checksum_valid")
    private Boolean checksumValid;

    /**
     * 체크섬 계산 소요 시간 (밀리초)
     */
    @Column(name = "checksum_elapsed_time_ms")
    private Long checksumElapsedTimeMs;

    // ========================================
    // 처리 상태 정보
    // ========================================

    /**
     * 업로드 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private UploadStatus status = UploadStatus.RECEIVED;

    /**
     * 오류 메시지
     */
    @Column(length = 1000)
    private String errorMessage;

    /**
     * 처리된 엔트리 수
     */
    @Column(name = "entries_processed")
    private Integer entriesProcessed;

    /**
     * 실패한 엔트리 수
     */
    @Column(name = "entries_failed")
    private Integer entriesFailed;

    /**
     * 처리 시작 시간
     */
    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    /**
     * 처리 완료 시간
     */
    @Column(name = "processing_completed_at")
    private LocalDateTime processingCompletedAt;

    /**
     * 총 처리 시간 (초)
     */
    @Column(name = "total_processing_time_seconds")
    private Long totalProcessingTimeSeconds;

    // ========================================
    // 중복 체크 정보
    // ========================================

    /**
     * 중복 파일 여부
     */
    @Column(name = "is_duplicate")
    @Builder.Default
    private Boolean isDuplicate = false;

    /**
     * 새로운 버전 여부
     */
    @Column(name = "is_newer_version")
    @Builder.Default
    private Boolean isNewerVersion = false;

    /**
     * 대체된 이전 파일 ID
     * 새 버전 업로드로 인해 대체된 파일의 ID
     */
    @Column(name = "replaced_file_id")
    private Long replacedFileId;

    // ========================================
    // 추가 메타데이터
    // ========================================

    /**
     * ICAO 공식 설명
     */
    @Column(length = 1000)
    private String description;

    /**
     * Deprecated 여부
     */
    @Column(name = "is_deprecated")
    @Builder.Default
    private Boolean isDeprecated = false;

    /**
     * 비고 (관리자 메모)
     */
    @Column(length = 1000)
    private String remarks;

    // ========================================
    // Audit Fields
    // ========================================

    /**
     * 생성 일시
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 수정 일시
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * 처리 시작
     */
    public void startProcessing() {
        this.processingStartedAt = LocalDateTime.now();
    }

    /**
     * 처리 완료
     */
    public void completeProcessing() {
        this.processingCompletedAt = LocalDateTime.now();

        if (this.processingStartedAt != null) {
            this.totalProcessingTimeSeconds = java.time.Duration.between(
                this.processingStartedAt,
                this.processingCompletedAt
            ).getSeconds();
        }
    }

    /**
     * 상태 변경 (검증 포함)
     *
     * @param newStatus 새로운 상태
     * @throws IllegalStateException 유효하지 않은 상태 전이
     */
    public void changeStatus(UploadStatus newStatus) {
        if (this.status.canTransitionTo(newStatus)) {
            this.status = newStatus;

            // 상태별 자동 처리
            if (newStatus == UploadStatus.PARSING) {
                startProcessing();
            } else if (newStatus.isFinal()) {
                completeProcessing();
            }
        } else {
            throw new IllegalStateException(
                String.format("Invalid status transition: %s -> %s",
                    this.status, newStatus)
            );
        }
    }

    /**
     * Collection 카테고리 반환
     *
     * @return Collection 카테고리
     */
    public String getCollectionCategory() {
        if (collectionNumber == null) return "UNKNOWN";

        return switch (collectionNumber) {
            case "001" -> "eMRTD PKI Objects";
            case "002" -> "CSCA Master Lists";
            case "003" -> "Non-Conformant (Deprecated)";
            default -> "UNKNOWN";
        };
    }

    /**
     * 성공 여부
     *
     * @return 성공 여부
     */
    public boolean isSuccess() {
        return status != null && status.isSuccess();
    }

    /**
     * 진행 중 여부
     *
     * @return 진행 중 여부
     */
    public boolean isInProgress() {
        return status != null && status.isInProgress();
    }

    /**
     * 실패 여부
     *
     * @return 실패 여부
     */
    public boolean isFailed() {
        return status != null && status.isError();
    }
}
