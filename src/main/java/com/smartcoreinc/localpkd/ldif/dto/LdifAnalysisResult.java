package com.smartcoreinc.localpkd.ldif.dto;

import java.util.List;
import java.util.Map;

public class LdifAnalysisResult {
    private int totalEntries;
    private int addEntries;
    private int modifyEntries;
    private int deleteEntries;
    private List<LdifEntryDto> entries;
    private List<String> errors;
    private Map<String, Integer> objectClassCount;
    private boolean hasValidationErrors;

    public LdifAnalysisResult() {}

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

    public List<LdifEntryDto> getEntries() {
        return entries;
    }

    public void setEntries(List<LdifEntryDto> entries) {
        this.entries = entries;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public Map<String, Integer> getObjectClassCount() {
        return objectClassCount;
    }

    public void setObjectClassCount(Map<String, Integer> objectClassCount) {
        this.objectClassCount = objectClassCount;
    }

    public boolean isHasValidationErrors() {
        return hasValidationErrors;
    }

    public void setHasValidationErrors(boolean hasValidationErrors) {
        this.hasValidationErrors = hasValidationErrors;
    }

}
