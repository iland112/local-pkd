package com.smartcoreinc.localpkd.ldif.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 분석 결과의 요약 정보만 포함하는 경량화된 DTO
 * 전체 엔트리 데이터는 페이징을 통해 별도로 조회
 */
@Data
public class LdifAnalysisSummary {
  
    private int totalEntries = 0;
    private int addEntries = 0;
    private int modifyEntries = 0;
    private int deleteEntries = 0;
    
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    
    private int totalErrors = 0;
    private int totalWarnings = 0;
    
    private Map<String, Integer> objectClassCount = new HashMap<>();
    private Map<String, Integer> certificateValidationStats = new HashMap<>();
    
    private boolean hasValidationErrors = false;

    // PKD 관련 추가 필드들
    private int totalCertificates = 0;
    private int validCertificates = 0;
    private int invalidCertificates = 0;
    
    private int totalMasterLists = 0;
    private int validMasterLists = 0;
    private int invalidMasterLists = 0;
    
    private int totalCrls = 0;
    private int validCrls = 0;
    private int invalidCrls = 0;
    
    // Trust Anchor 관련
    private int totalTrustAnchors = 0;
    private Map<String, Integer> countryStats = new HashMap<>();

    // 기본 생성자에서 초기화
    public LdifAnalysisSummary() {
        initializeCertificateValidationStats();
    }

    private void initializeCertificateValidationStats() {
        if (this.certificateValidationStats == null) {
            this.certificateValidationStats = new HashMap<>();
        }
        
        // 기본값들 설정
        this.certificateValidationStats.putIfAbsent("total", 0);
        this.certificateValidationStats.putIfAbsent("valid", 0);
        this.certificateValidationStats.putIfAbsent("invalid", 0);
        this.certificateValidationStats.putIfAbsent("totalMasterLists", 0);
        this.certificateValidationStats.putIfAbsent("validMasterLists", 0);
        this.certificateValidationStats.putIfAbsent("invalidMasterLists", 0);
        this.certificateValidationStats.putIfAbsent("totalCrls", 0);
        this.certificateValidationStats.putIfAbsent("validCrls", 0);
        this.certificateValidationStats.putIfAbsent("invalidCrls", 0);
        this.certificateValidationStats.putIfAbsent("totalTrustAnchors", 0);
    }

    // 안전한 getter 메서드들
    public Map<String, Integer> getCertificateValidationStats() {
        if (this.certificateValidationStats == null) {
            initializeCertificateValidationStats();
        }
        return this.certificateValidationStats;
    }

    public void setCertificateValidationStats(Map<String, Integer> certificateValidationStats) {
        this.certificateValidationStats = certificateValidationStats;
        if (this.certificateValidationStats != null) {
            initializeCertificateValidationStats(); // null 값들을 0으로 초기화
        }
    }

    public List<String> getErrors() {
        return this.errors != null ? this.errors : new ArrayList<>();
    }

    public List<String> getWarnings() {
        return this.warnings != null ? this.warnings : new ArrayList<>();
    }

    public Map<String, Integer> getObjectClassCount() {
        return this.objectClassCount != null ? this.objectClassCount : new HashMap<>();
    }

    public Map<String, Integer> getCountryStats() {
        return this.countryStats != null ? this.countryStats : new HashMap<>();
    }

    // 통계 업데이트 메서드들
    public void updateCertificateStats(int total, int valid, int invalid) {
        getCertificateValidationStats().put("total", total);
        getCertificateValidationStats().put("valid", valid);
        getCertificateValidationStats().put("invalid", invalid);
        
        this.totalCertificates = total;
        this.validCertificates = valid;
        this.invalidCertificates = invalid;
    }

    public void updateMasterListStats(int total, int valid, int invalid) {
        getCertificateValidationStats().put("totalMasterLists", total);
        getCertificateValidationStats().put("validMasterLists", valid);
        getCertificateValidationStats().put("invalidMasterLists", invalid);
        
        this.totalMasterLists = total;
        this.validMasterLists = valid;
        this.invalidMasterLists = invalid;
    }

    public void updateCrlStats(int total, int valid, int invalid) {
        getCertificateValidationStats().put("totalCrls", total);
        getCertificateValidationStats().put("validCrls", valid);
        getCertificateValidationStats().put("invalidCrls", invalid);
        
        this.totalCrls = total;
        this.validCrls = valid;
        this.invalidCrls = invalid;
    }

    public void updateTrustAnchorStats(int totalTrustAnchors) {
        getCertificateValidationStats().put("totalTrustAnchors", totalTrustAnchors);
        this.totalTrustAnchors = totalTrustAnchors;
    }

    // 편의 메서드들
    public int getTotalPkdObjects() {
        return getTotalCertificates() + getTotalMasterLists() + getTotalCrls();
    }

    public int getTotalValidPkdObjects() {
        return getValidCertificates() + getValidMasterLists() + getValidCrls();
    }

    public int getTotalInvalidPkdObjects() {
        return getInvalidCertificates() + getInvalidMasterLists() + getInvalidCrls();
    }

    public double getPkdValidityRate() {
        int total = getTotalPkdObjects();
        if (total == 0) return 0.0;
        return (double) getTotalValidPkdObjects() / total * 100.0;
    }

    public double getCertificateValidityRate() {
        if (totalCertificates == 0) return 0.0;
        return (double) validCertificates / totalCertificates * 100.0;
    }

    public double getMasterListValidityRate() {
        if (totalMasterLists == 0) return 0.0;
        return (double) validMasterLists / totalMasterLists * 100.0;
    }

    public double getCrlValidityRate() {
        if (totalCrls == 0) return 0.0;
        return (double) validCrls / totalCrls * 100.0;
    }

    // 오류 및 경고 추가 메서드들
    public void addError(String error) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(error);
        this.totalErrors = this.errors.size();
        this.hasValidationErrors = true;
    }

    public void addWarning(String warning) {
        if (this.warnings == null) {
            this.warnings = new ArrayList<>();
        }
        this.warnings.add(warning);
        this.totalWarnings = this.warnings.size();
    }

    public void addErrors(List<String> errors) {
        if (errors != null && !errors.isEmpty()) {
            if (this.errors == null) {
                this.errors = new ArrayList<>();
            }
            this.errors.addAll(errors);
            this.totalErrors = this.errors.size();
            this.hasValidationErrors = true;
        }
    }

    public void addWarnings(List<String> warnings) {
        if (warnings != null && !warnings.isEmpty()) {
            if (this.warnings == null) {
                this.warnings = new ArrayList<>();
            }
            this.warnings.addAll(warnings);
            this.totalWarnings = this.warnings.size();
        }
    }

    // ObjectClass 카운트 추가
    public void addObjectClass(String objectClass) {
        if (this.objectClassCount == null) {
            this.objectClassCount = new HashMap<>();
        }
        this.objectClassCount.merge(objectClass, 1, Integer::sum);
    }

    // 국가별 통계 업데이트
    public void addCountryStat(String countryCode, int count) {
        if (this.countryStats == null) {
            this.countryStats = new HashMap<>();
        }
        this.countryStats.put(countryCode, count);
    }

    // 전체 통계 요약 출력용 메서드
    public String getSummaryText() {
        StringBuilder sb = new StringBuilder();
        sb.append("LDIF Analysis Summary:\n");
        sb.append("- Total Entries: ").append(totalEntries).append("\n");
        sb.append("- Add Entries: ").append(addEntries).append("\n");
        sb.append("- Errors: ").append(getErrors().size()).append("\n");
        sb.append("- Warnings: ").append(getWarnings().size()).append("\n");
        
        if (getTotalPkdObjects() > 0) {
            sb.append("- PKD Objects: ").append(getTotalPkdObjects()).append("\n");
            sb.append("  * Certificates: ").append(totalCertificates).append(" (Valid: ").append(validCertificates).append(")\n");
            sb.append("  * Master Lists: ").append(totalMasterLists).append(" (Valid: ").append(validMasterLists).append(")\n");
            sb.append("  * CRLs: ").append(totalCrls).append(" (Valid: ").append(validCrls).append(")\n");
        }
        
        return sb.toString();
    }

    // Validation
    public boolean isValid() {
        return !hasValidationErrors && totalEntries > 0;
    }

    public boolean hasPkdContent() {
        return getTotalPkdObjects() > 0;
    }

    public boolean hasCertificates() {
        return totalCertificates > 0;
    }

    public boolean hasMasterLists() {
        return totalMasterLists > 0;
    }

    public boolean hasCrls() {
        return totalCrls > 0;
    }
}
