package com.smartcoreinc.localpkd.ldif.dto;

import java.util.List;

public class LdifAnalysisResult {

    private String fileName;
    private List<LdifEntryDto> entries;    
    private LdifAnalysisSummary summary;
    
    public LdifAnalysisResult() {}

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    } 

    public List<LdifEntryDto> getEntries() {
        return entries;
    }

    public void setEntries(List<LdifEntryDto> entries) {
        this.entries = entries;
    }

    public LdifAnalysisSummary getSummary() {
        return summary;
    }

    public void setSummary(LdifAnalysisSummary summary) {
        this.summary = summary;
    }

}
