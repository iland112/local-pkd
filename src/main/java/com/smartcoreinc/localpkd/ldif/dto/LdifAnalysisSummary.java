package com.smartcoreinc.localpkd.ldif.dto;

import java.util.List;
import java.util.Map;

/**
 * 분석 결과의 요약 정보만 포함하는 경량화된 DTO
 * 전체 엔트리 데이터는 페이징을 통해 별도로 조회
 */
public class LdifAnalysisSummary {
    private int totalEntries;
    private int addEntries;
    private int modifyEntries;
    private int deleteEntries;
    private boolean hasValidationErrors;
    private Map<String, Integer> objectClassCount;
    private Map<String, Integer> certificateValidationStats;

    private List<String> errors;
    private List<String> warnings;
    private int totalErrors;
    private int totalWarnings;

    public LdifAnalysisSummary() {}

    public int getTotalEntries() {
        return totalEntries;
    }

    public void setTotalEntries(int totalEntries) {
        this.totalEntries = totalEntries;
    }

    public int getAddEntries() {
        return addEntries;
    }

    public void setAddEntries(int addEntries) {
        this.addEntries = addEntries;
    }

    public int getModifyEntries() {
        return modifyEntries;
    }

    public void setModifyEntries(int modifyEntries) {
        this.modifyEntries = modifyEntries;
    }

    public int getDeleteEntries() {
        return deleteEntries;
    }

    public void setDeleteEntries(int deleteEntries) {
        this.deleteEntries = deleteEntries;
    }

    public boolean isHasValidationErrors() {
        return hasValidationErrors;
    }

    public void setHasValidationErrors(boolean hasValidationErrors) {
        this.hasValidationErrors = hasValidationErrors;
    }

    public Map<String, Integer> getObjectClassCount() {
        return objectClassCount;
    }

    public void setObjectClassCount(Map<String, Integer> objectClassCount) {
        this.objectClassCount = objectClassCount;
    }

    public Map<String, Integer> getCertificateValidationStats() {
        return certificateValidationStats;
    }

    public void setCertificateValidationStats(Map<String, Integer> certificateValidationStats) {
        this.certificateValidationStats = certificateValidationStats;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public int getTotalErrors() {
        return totalErrors;
    }

    public void setTotalErrors(int totalErrors) {
        this.totalErrors = totalErrors;
    }

    public int getTotalWarnings() {
        return totalWarnings;
    }

    public void setTotalWarnings(int totalWarnings) {
        this.totalWarnings = totalWarnings;
    }

}
