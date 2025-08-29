package com.smartcoreinc.localpkd.ldif.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.smartcoreinc.localpkd.enums.EntryType;

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
    
    private Map<String, Integer> entryTypeCount = new HashMap<>();
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
        initializeEntryTypeCount();
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

    private void initializeEntryTypeCount() {
        if (this.entryTypeCount == null) {
            this.entryTypeCount = new HashMap<>();
        }
        
        // EntryType enum의 모든 값들 초기화
        for (EntryType entryType : EntryType.values()) {
            this.entryTypeCount.putIfAbsent(entryType.name(), 0);
        }
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

    public Map<String, Integer> getEntryTypeCount() {
        if (this.entryTypeCount == null) {
            initializeEntryTypeCount();
        }
        return this.entryTypeCount;
    }

    public void setEntryTypeCount(Map<String, Integer> entryTypeCount) {
        this.entryTypeCount = entryTypeCount;
        if (this.entryTypeCount != null) {
            initializeEntryTypeCount();
        }
    }

    public List<String> getErrors() {
        return this.errors != null ? this.errors : new ArrayList<>();
    }

    public List<String> getWarnings() {
        return this.warnings != null ? this.warnings : new ArrayList<>();
    }

    // ObjectClass 관련 메서드 제거하고 EntryType 메서드로 대체
    public Map<String, Integer> getObjectClassCount() {
        // 하위 호환성을 위해 빈 맵 반환
        return new HashMap<>();
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

    // EntryType 통계 업데이트 메서드들
    public void addEntryType(EntryType entryType) {
        getEntryTypeCount().merge(entryType.name(), 1, Integer::sum);
    }

    public void addEntryType(String entryTypeName) {
        getEntryTypeCount().merge(entryTypeName, 1, Integer::sum);
    }

    public void updateEntryTypeStats(Map<String, Integer> entryTypeStats) {
        if (entryTypeStats != null) {
            getEntryTypeCount().putAll(entryTypeStats);
        }
    }

    // EntryType 관련 편의 메서드들
    public int getEntryTypeTotal() {
        return getEntryTypeCount().values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getPkdEntryCount() {
        return getEntryTypeCount().getOrDefault("ML", 0) +
               getEntryTypeCount().getOrDefault("DSC", 0) +
               getEntryTypeCount().getOrDefault("CRL", 0);
    }

    public int getInfrastructureEntryCount() {
        return getEntryTypeCount().getOrDefault("C", 0) +
               getEntryTypeCount().getOrDefault("O", 0) +
               getEntryTypeCount().getOrDefault("DC", 0);
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

    // ObjectClass 관련 메서드는 하위 호환성을 위해 유지하되 EntryType으로 위임
    @Deprecated
    public void addObjectClass(String objectClass) {
        // EntryType으로 매핑하여 처리
        EntryType entryType = mapObjectClassToEntryType(objectClass);
        addEntryType(entryType);
    }

    private EntryType mapObjectClassToEntryType(String objectClass) {
        if (objectClass == null) return EntryType.UNKNOWN;
        
        String lowerClass = objectClass.toLowerCase();
        if (lowerClass.contains("country")) return EntryType.C;
        if (lowerClass.contains("organization")) return EntryType.O;
        if (lowerClass.contains("domain")) return EntryType.DC;
        if (lowerClass.contains("pkd") || lowerClass.contains("master")) return EntryType.ML;
        if (lowerClass.contains("cert")) return EntryType.DSC;
        if (lowerClass.contains("crl")) return EntryType.CRL;
        
        return EntryType.UNKNOWN;
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
        
        if (getPkdEntryCount() > 0) {
            sb.append("- PKD Entries: ").append(getPkdEntryCount()).append("\n");
            sb.append("  * ML: ").append(getEntryTypeCount().getOrDefault("ML", 0)).append("\n");
            sb.append("  * DSC: ").append(getEntryTypeCount().getOrDefault("DSC", 0)).append("\n");
            sb.append("  * CRL: ").append(getEntryTypeCount().getOrDefault("CRL", 0)).append("\n");
        }
        
        if (getInfrastructureEntryCount() > 0) {
            sb.append("- Infrastructure Entries: ").append(getInfrastructureEntryCount()).append("\n");
            sb.append("  * Country: ").append(getEntryTypeCount().getOrDefault("C", 0)).append("\n");
            sb.append("  * Organization: ").append(getEntryTypeCount().getOrDefault("O", 0)).append("\n");
            sb.append("  * Domain: ").append(getEntryTypeCount().getOrDefault("DC", 0)).append("\n");
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

    // EntryType 관련 검사 메서드들
    public boolean hasEntryType(EntryType entryType) {
        return getEntryTypeCount().getOrDefault(entryType.name(), 0) > 0;
    }

    public boolean hasEntryType(String entryTypeName) {
        return getEntryTypeCount().getOrDefault(entryTypeName, 0) > 0;
    }

    public boolean hasPkdEntries() {
        return getPkdEntryCount() > 0;
    }

    public boolean hasInfrastructureEntries() {
        return getInfrastructureEntryCount() > 0;
    }

    // EntryType별 비율 계산 메서드들
    public double getEntryTypePercentage(EntryType entryType) {
        if (totalEntries == 0) return 0.0;
        int count = getEntryTypeCount().getOrDefault(entryType.name(), 0);
        return (double) count / totalEntries * 100.0;
    }

    public double getEntryTypePercentage(String entryTypeName) {
        if (totalEntries == 0) return 0.0;
        int count = getEntryTypeCount().getOrDefault(entryTypeName, 0);
        return (double) count / totalEntries * 100.0;
    }
}
