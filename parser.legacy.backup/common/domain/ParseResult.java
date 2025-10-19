package com.smartcoreinc.localpkd.parser.common.domain;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import com.smartcoreinc.localpkd.common.enums.FileType;

import lombok.Getter;

/**
 * 파일 파싱 결과
 * 
 * 파서가 파일을 처리한 후 반환하는 결과 객체
 * - 파싱 성공/실패 여부
 * - 추출된 인증서 목록
 * - 통계 정보
 * - 오류 메시지
 * 
 * @author SmartCore Inc.
 * @version 1.0
 */
@Getter
public class ParseResult {

    // ========================================
    // 기본 정보
    // ========================================
    
    private final String fileId;
    private final String filename;
    private final FileType fileType;
    private final FileFormat fileFormat;
    private final String version;
    
    // ========================================
    // 파싱 결과
    // ========================================
    
    private final boolean success;
    private final boolean completed;
    private final String failureReason;
    
    // ========================================
    // 인증서 데이터
    // ========================================
    
    private final List<X509Certificate> certificates;
    private final List<X509Certificate> validCertificates;
    private final List<X509Certificate> invalidCertificates;
    private final List<X509Certificate> duplicateCertificates;
    
    // ========================================
    // 통계 정보
    // ========================================
    
    private final int totalCertificates;
    private final int validCount;
    private final int invalidCount;
    private final int duplicateCount;
    private final int processedCount;
    private final Map<String, Integer> countByCountry;
    private final int totalCountries;
    
    // ========================================
    // 시간 정보
    // ========================================
    
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Long durationMillis;
    
    // ========================================
    // 오류 정보
    // ========================================
    
    private final List<String> errorMessages;
    private final List<String> warningMessages;
    
    // ========================================
    // 추가 데이터
    // ========================================
    
    private final Map<String, Object> additionalMetadata;
    
    // ========================================
    // Private 생성자
    // ========================================

    private ParseResult(Builder builder) {
        this.fileId = builder.fileId;
        this.filename = builder.filename;
        this.fileType = builder.fileType;
        this.fileFormat = builder.fileFormat;
        this.version = builder.version;
        
        this.success = builder.success;
        this.completed = builder.completed;
        this.failureReason = builder.failureReason;
        
        this.certificates = Collections.unmodifiableList(builder.certificates);
        this.validCertificates = Collections.unmodifiableList(builder.validCertificates);
        this.invalidCertificates = Collections.unmodifiableList(builder.invalidCertificates);
        this.duplicateCertificates = Collections.unmodifiableList(builder.duplicateCertificates);
        
        this.totalCertificates = builder.totalCertificates;
        this.validCount = builder.validCount;
        this.invalidCount = builder.invalidCount;
        this.duplicateCount = builder.duplicateCount;
        this.processedCount = builder.processedCount;
        this.countByCountry = Collections.unmodifiableMap(builder.countByCountry);
        this.totalCountries = builder.totalCountries;
        
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.durationMillis = builder.durationMillis;
        
        this.errorMessages = Collections.unmodifiableList(builder.errorMessages);
        this.warningMessages = Collections.unmodifiableList(builder.warningMessages);
        
        this.additionalMetadata = Collections.unmodifiableMap(builder.additionalMetadata);
    }

    // ========================================
    // 계산 메서드
    // ========================================
    
    /**
     * 유효성 비율 (%)
     */
    public double getValidityRate() {
        if (totalCertificates == 0) {
            return 0.0;
        }
        return (double) validCount / totalCertificates * 100.0;
    }
    
    /**
     * 무효 비율 (%)
     */
    public double getInvalidityRate() {
        return 100.0 - getValidityRate();
    }
    
    /**
     * 평균 국가당 인증서 수
     */
    public double getAverageCertificatesPerCountry() {
        if (totalCountries == 0) {
            return 0.0;
        }
        return (double) totalCertificates / totalCountries;
    }
    
    /**
     * 소요 시간 (Duration)
     */
    public Duration getDuration() {
        if (endTime == null) {
            return Duration.between(startTime, LocalDateTime.now());
        }
        return Duration.between(startTime, endTime);
    }
    
    /**
     * 소요 시간 (초)
     */
    public long getDurationSeconds() {
        return getDuration().getSeconds();
    }
    
    /**
     * 오류 발생 여부
     */
    public boolean hasErrors() {
        return !errorMessages.isEmpty();
    }
    
    /**
     * 경고 발생 여부
     */
    public boolean hasWarnings() {
        return !warningMessages.isEmpty();
    }
    
    /**
     * 부분 성공 여부 (일부 인증서만 처리 성공)
     */
    public boolean isPartialSuccess() {
        return success && (processedCount < totalCertificates || hasErrors());
    }
    
    // ========================================
    // 요약 메서드
    // ========================================
    
    /**
     * 간단한 요약 정보
     */
    public String getSummary() {
        return String.format(
            "총 %d개 인증서 (유효: %d, 무효: %d, 중복: %d) - %d개국 참여 - 소요시간: %d초",
            totalCertificates, validCount, invalidCount, duplicateCount, 
            totalCountries, getDurationSeconds()
        );
    }
    
    /**
     * 상세 요약 정보
     */
    public String getDetailedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 파싱 결과 요약 ===\n");
        sb.append(String.format("파일: %s (버전: %s)\n", filename, version));
        sb.append(String.format("타입: %s / 포맷: %s\n", fileType, fileFormat));
        sb.append(String.format("상태: %s\n", success ? "성공" : "실패"));
        
        if (!success && failureReason != null) {
            sb.append(String.format("실패 사유: %s\n", failureReason));
        }
        
        sb.append(String.format("\n총 인증서: %d개\n", totalCertificates));
        sb.append(String.format("  - 유효: %d개 (%.1f%%)\n", validCount, getValidityRate()));
        sb.append(String.format("  - 무효: %d개 (%.1f%%)\n", invalidCount, getInvalidityRate()));
        sb.append(String.format("  - 중복: %d개\n", duplicateCount));
        sb.append(String.format("  - 처리됨: %d개\n", processedCount));
        
        sb.append(String.format("\n참여 국가: %d개국\n", totalCountries));
        sb.append(String.format("평균 국가당 인증서: %.1f개\n", getAverageCertificatesPerCountry()));
        
        sb.append(String.format("\n소요 시간: %d초 (%d ms)\n", 
            getDurationSeconds(), durationMillis != null ? durationMillis : 0));
        
        if (hasErrors()) {
            sb.append(String.format("\n오류 발생: %d건\n", errorMessages.size()));
        }
        
        if (hasWarnings()) {
            sb.append(String.format("경고 발생: %d건\n", warningMessages.size()));
        }
        
        return sb.toString();
    }
    
    /**
     * JSON 형식으로 변환 (간단한 맵)
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("fileId", fileId);
        map.put("filename", filename);
        map.put("fileType", fileType != null ? fileType.name() : null);
        map.put("fileFormat", fileFormat != null ? fileFormat.name() : null);
        map.put("version", version);
        map.put("success", success);
        map.put("completed", completed);
        map.put("failureReason", failureReason);
        
        // 통계
        map.put("totalCertificates", totalCertificates);
        map.put("validCount", validCount);
        map.put("invalidCount", invalidCount);
        map.put("duplicateCount", duplicateCount);
        map.put("processedCount", processedCount);
        map.put("totalCountries", totalCountries);
        map.put("validityRate", getValidityRate());
        
        // 시간
        map.put("startTime", startTime);
        map.put("endTime", endTime);
        map.put("durationSeconds", getDurationSeconds());
        
        // 오류
        map.put("errorCount", errorMessages.size());
        map.put("warningCount", warningMessages.size());
        map.put("hasErrors", hasErrors());
        map.put("hasWarnings", hasWarnings());
        
        return map;
    }
    
    @Override
    public String toString() {
        return String.format(
            "ParseResult[fileId=%s, filename=%s, success=%s, total=%d, valid=%d, invalid=%d]",
            fileId, filename, success, totalCertificates, validCount, invalidCount
        );
    }
    
    // ========================================
    // Builder 클래스
    // ========================================
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        // 기본 정보
        private String fileId;
        private String filename;
        private FileType fileType;
        private FileFormat fileFormat;
        private String version;
        
        // 파싱 결과
        private boolean success = false;
        private boolean completed = false;
        private String failureReason;
        
        // 인증서 데이터
        private List<X509Certificate> certificates = new ArrayList<>();
        private List<X509Certificate> validCertificates = new ArrayList<>();
        private List<X509Certificate> invalidCertificates = new ArrayList<>();
        private List<X509Certificate> duplicateCertificates = new ArrayList<>();
        
        // 통계 정보
        private int totalCertificates = 0;
        private int validCount = 0;
        private int invalidCount = 0;
        private int duplicateCount = 0;
        private int processedCount = 0;
        private Map<String, Integer> countByCountry = new HashMap<>();
        private int totalCountries = 0;
        
        // 시간 정보
        private LocalDateTime startTime = LocalDateTime.now();
        private LocalDateTime endTime;
        private Long durationMillis;
        
        // 오류 정보
        private List<String> errorMessages = new ArrayList<>();
        private List<String> warningMessages = new ArrayList<>();
        
        // 추가 데이터
        private Map<String, Object> additionalMetadata = new HashMap<>();
        
        private Builder() {}
        
        // 기본 정보 설정
        public Builder fileId(String fileId) {
            this.fileId = fileId;
            return this;
        }
        
        public Builder filename(String filename) {
            this.filename = filename;
            return this;
        }
        
        public Builder fileType(FileType fileType) {
            this.fileType = fileType;
            return this;
        }
        
        public Builder fileFormat(FileFormat fileFormat) {
            this.fileFormat = fileFormat;
            return this;
        }
        
        public Builder version(String version) {
            this.version = version;
            return this;
        }
        
        // 파싱 결과 설정
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder completed(boolean completed) {
            this.completed = completed;
            return this;
        }
        
        public Builder failureReason(String failureReason) {
            this.failureReason = failureReason;
            return this;
        }
        
        // 인증서 설정
        public Builder certificates(List<X509Certificate> certificates) {
            this.certificates = new ArrayList<>(certificates);
            return this;
        }
        
        public Builder certificate(X509Certificate certificate) {
            this.certificates.add(certificate);
            return this;
        }
        
        public Builder validCertificates(List<X509Certificate> validCertificates) {
            this.validCertificates = new ArrayList<>(validCertificates);
            return this;
        }
        
        public Builder validCertificate(X509Certificate certificate) {
            this.validCertificates.add(certificate);
            return this;
        }
        
        public Builder invalidCertificates(List<X509Certificate> invalidCertificates) {
            this.invalidCertificates = new ArrayList<>(invalidCertificates);
            return this;
        }
        
        public Builder invalidCertificate(X509Certificate certificate) {
            this.invalidCertificates.add(certificate);
            return this;
        }
        
        public Builder duplicateCertificates(List<X509Certificate> duplicateCertificates) {
            this.duplicateCertificates = new ArrayList<>(duplicateCertificates);
            return this;
        }
        
        public Builder duplicateCertificate(X509Certificate certificate) {
            this.duplicateCertificates.add(certificate);
            return this;
        }
        
        // 통계 설정
        public Builder totalCertificates(int totalCertificates) {
            this.totalCertificates = totalCertificates;
            return this;
        }
        
        public Builder validCount(int validCount) {
            this.validCount = validCount;
            return this;
        }
        
        public Builder invalidCount(int invalidCount) {
            this.invalidCount = invalidCount;
            return this;
        }
        
        public Builder duplicateCount(int duplicateCount) {
            this.duplicateCount = duplicateCount;
            return this;
        }
        
        public Builder processedCount(int processedCount) {
            this.processedCount = processedCount;
            return this;
        }
        
        public Builder countByCountry(Map<String, Integer> countByCountry) {
            this.countByCountry = new HashMap<>(countByCountry);
            return this;
        }
        
        public Builder countryCount(String country, Integer count) {
            this.countByCountry.put(country, count);
            return this;
        }
        
        public Builder totalCountries(int totalCountries) {
            this.totalCountries = totalCountries;
            return this;
        }
        
        // 시간 설정
        public Builder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }
        
        public Builder endTime(LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }
        
        public Builder durationMillis(Long durationMillis) {
            this.durationMillis = durationMillis;
            return this;
        }
        
        // 오류 설정
        public Builder errorMessages(List<String> errorMessages) {
            this.errorMessages = new ArrayList<>(errorMessages);
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessages.add(errorMessage);
            return this;
        }
        
        public Builder warningMessages(List<String> warningMessages) {
            this.warningMessages = new ArrayList<>(warningMessages);
            return this;
        }
        
        public Builder warningMessage(String warningMessage) {
            this.warningMessages.add(warningMessage);
            return this;
        }
        
        // 메타데이터 설정
        public Builder additionalMetadata(Map<String, Object> metadata) {
            this.additionalMetadata = new HashMap<>(metadata);
            return this;
        }
        
        public Builder metadata(String key, Object value) {
            this.additionalMetadata.put(key, value);
            return this;
        }
        
        public ParseResult build() {
            return new ParseResult(this);
        }
    }
    
    // ========================================
    // 정적 팩토리 메서드
    // ========================================
    
    /**
     * 성공 결과 생성
     */
    public static ParseResult success(ParseContext context, List<X509Certificate> certificates) {
        LocalDateTime now = LocalDateTime.now();
        long duration = Duration.between(context.getStartTime(), now).toMillis();
        
        return ParseResult.builder()
            .fileId(context.getFileId())
            .filename(context.getFilename())
            .fileType(context.getFileType())
            .fileFormat(context.getFileFormat())
            .version(context.getVersion())
            .success(true)
            .completed(true)
            .certificates(certificates)
            .totalCertificates(certificates.size())
            .processedCount(certificates.size())
            .startTime(context.getStartTime())
            .endTime(now)
            .durationMillis(duration)
            .build();
    }
    
    /**
     * 실패 결과 생성
     */
    public static ParseResult failure(ParseContext context, String reason) {
        LocalDateTime now = LocalDateTime.now();
        long duration = Duration.between(context.getStartTime(), now).toMillis();
        
        return ParseResult.builder()
            .fileId(context.getFileId())
            .filename(context.getFilename())
            .fileType(context.getFileType())
            .fileFormat(context.getFileFormat())
            .version(context.getVersion())
            .success(false)
            .completed(true)
            .failureReason(reason)
            .startTime(context.getStartTime())
            .endTime(now)
            .durationMillis(duration)
            .errorMessage(reason)
            .build();
    }
    
    /**
     * 빈 결과 생성 (초기화용)
     */
    public static ParseResult empty(ParseContext context) {
        return ParseResult.builder()
            .fileId(context.getFileId())
            .filename(context.getFilename())
            .fileType(context.getFileType())
            .fileFormat(context.getFileFormat())
            .version(context.getVersion())
            .success(false)
            .completed(false)
            .startTime(context.getStartTime())
            .build();
    }
}
