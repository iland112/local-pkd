package com.smartcoreinc.localpkd.file.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.smartcoreinc.localpkd.common.enums.FileFormat;
import com.smartcoreinc.localpkd.common.enums.FileType;
import com.smartcoreinc.localpkd.common.enums.ProcessStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PKD 파일 엔터티
 * 모든 PKD 파일 (CSCA, DSC, CRL)의 통합 관리
 */
@Entity
@Table(name = "pkd_files", indexes = {
    @Index(name = "idx_pkd_file_type", columnList = "file_type"),
    @Index(name = "idx_pkd_file_format", columnList = "file_format"),
    @Index(name = "idx_pkd_country", columnList = "country_code"),
    @Index(name = "idx_pkd_status", columnList = "upload_status, parse_status"),
    @Index(name = "idx_pkd_uploaded", columnList = "uploaded_at"),
    @Index(name = "idx_pkd_collection", columnList = "collection_number"),
    @Index(name = "idx_pkd_version", columnList = "version_number"),
    @Index(name = "idx_pkd_delta", columnList = "is_delta")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PkdFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", unique = true, nullable = false, length = 36)
    private String fielId;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 20)
    private FileType fileType;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_format", nullable = false, length = 30)
    private FileFormat fileFormat;

    // ICAO PKD 메타데이터
    @Column(name = "collection_number", length = 3)
    private String collectionNumber;

    @Column(name = "version_numebr", length = 10)
    private String versionNumber;

    @Column(name = "is_delta")
    private Boolean isDelta;

    // 파일 정보
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;
    
    @Column(name = "stored_path", nullable = false, length = 500)
    private String storedPath;
    
    @Column(name = "file_size", nullable = false)
    private Long fileSize;
    
    @Column(name = "file_hash_sha256", length = 64)
    private String fileHashSha256;

    // 메타데이터
    @Column(name = "country_code", length = 2)
    private String countryCode;
    
    @Column(name = "issue_date")
    private LocalDateTime issueDate;
    
    @Column(name = "valid_from")
    private LocalDateTime validFrom;
    
    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    // 처리 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 20)
    @Builder.Default
    private ProcessStatus uploadStatus = ProcessStatus.UPLOADED;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", length = 20)
    private ProcessStatus parseStatus;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "verify_status", length = 20)
    private ProcessStatus verifyStatus;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "ldap_status", length = 20)
    private ProcessStatus ldapStatus;

    // 처리 시간
    @Column(name = "uploaded_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime uploadedAt;
    
    @Column(name = "parse_started_at")
    private LocalDateTime parseStartedAt;
    
    @Column(name = "parse_completed_at")
    private LocalDateTime parseCompletedAt;
    
    @Column(name = "verify_started_at")
    private LocalDateTime verifyStartedAt;
    
    @Column(name = "verify_completed_at")
    private LocalDateTime verifyCompletedAt;
    
    @Column(name = "ldap_started_at")
    private LocalDateTime ldapStartedAt;
    
    @Column(name = "ldap_completed_at")
    private LocalDateTime ldapCompletedAt;

    // 처리 결과
    @Column(name = "total_entries")
    @Builder.Default
    private Integer totalEntries = 0;
    
    @Column(name = "valid_entries")
    @Builder.Default
    private Integer validEntries = 0;
    
    @Column(name = "invalid_entries")
    @Builder.Default
    private Integer invalidEntries = 0;
    
    @Column(name = "duplicate_entries")
    @Builder.Default
    private Integer duplicateEntries = 0;
    
    @Column(name = "error_count")
    @Builder.Default
    private Integer errorCount = 0;

    // 에러 정보
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    // 다운로드 정보
    @Column(name = "download_source")
    private String downloadSource;
    
    @Column(name = "download_method", length = 20)
    private String downloadMethod;
    
    // 감사
    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @Column(name = "created_by", length = 100)
    @Builder.Default
    private String createdBy = "system";
    
    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    // 비즈니스 메서드
    
    /**
     * 파싱 시작
     */
    public void startParsing() {
        this.parseStatus = ProcessStatus.PARSING;
        this.parseStartedAt = LocalDateTime.now();
    }

    /**
     * 파싱 완료
     */
    public void completeParsing(int total, int valid, int invalid, int duplicate) {
        this.parseStatus = ProcessStatus.PARSED;
        this.parseCompletedAt = LocalDateTime.now();
        this.totalEntries = total;
        this.validEntries = valid;
        this.invalidEntries = invalid;
        this.duplicateEntries = duplicate;
    }
    
    /**
     * 파싱 실패
     */
    public void failParsing(String errorMsg) {
        this.parseStatus = ProcessStatus.FAILED;
        this.parseCompletedAt = LocalDateTime.now();
        this.errorMessage = errorMsg;
        this.errorCount = (this.errorCount != null ? this.errorCount : 0) + 1;
    }
    
    /**
     * 검증 시작
     */
    public void startVerifying() {
        this.verifyStatus = ProcessStatus.VERIFYING;
        this.verifyStartedAt = LocalDateTime.now();
    }

    /**
     * 검증 완료
     */
    public void completeVerifying() {
        this.verifyStatus = ProcessStatus.VERIFIED;
        this.verifyCompletedAt = LocalDateTime.now();
    }
    
    /**
     * 검증 실패
     */
    public void failVerifying(String errorMsg) {
        this.verifyStatus = ProcessStatus.FAILED;
        this.verifyCompletedAt = LocalDateTime.now();
        this.errorMessage = errorMsg;
        this.errorCount = (this.errorCount != null ? this.errorCount : 0) + 1;
    }
    
    /**
     * LDAP 적용 시작
     */
    public void startApplying() {
        this.ldapStatus = ProcessStatus.APPLYING;
        this.ldapStartedAt = LocalDateTime.now();
    }
    
    /**
     * LDAP 적용 완료
     */
    public void completeApplying() {
        this.ldapStatus = ProcessStatus.APPLIED;
        this.ldapCompletedAt = LocalDateTime.now();
        this.uploadStatus = ProcessStatus.APPLIED;
    }
    
    /**
     * LDAP 적용 실패
     */
    public void failApplying(String errorMsg) {
        this.ldapStatus = ProcessStatus.FAILED;
        this.ldapCompletedAt = LocalDateTime.now();
        this.uploadStatus = ProcessStatus.FAILED;
        this.errorMessage = errorMsg;
        this.errorCount = (this.errorCount != null ? this.errorCount : 0) + 1;
    }
    
    /**
     * 처리 완료 여부
     */
    public boolean isProcessed() {
        return this.uploadStatus == ProcessStatus.APPLIED;
    }
    
    /**
     * 실패 여부
     */
    public boolean isFailed() {
        return this.uploadStatus == ProcessStatus.FAILED;
    }
    
    /**
     * Delta 파일 여부
     */
    public boolean isDeltaFile() {
        return Boolean.TRUE.equals(this.isDelta);
    }
    
    /**
     * 처리 진행률 계산 (0-100)
     */
    public int getProgressPercentage() {
        if (uploadStatus == ProcessStatus.APPLIED) {
            return 100;
        }
        
        if (uploadStatus == ProcessStatus.FAILED) {
            return 0;
        }
        
        // 각 단계별 진행률
        int progress = 0;
        if (parseStatus != null) {
            progress += 25;
            if (parseStatus == ProcessStatus.PARSED) {
                progress += 25;
            }
        }
        if (verifyStatus != null) {
            progress += 25;
        }
        if (ldapStatus != null) {
            progress += 25;
        }
        
        return progress;
    }
}
