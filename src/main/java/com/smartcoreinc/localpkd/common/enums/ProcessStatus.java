package com.smartcoreinc.localpkd.common.enums;

import lombok.Getter;

@Getter
public enum ProcessStatus {
    UPLOADED("Uploaded", "파일이 업로드됨"),
    PARSING("Parsing", "파싱 중"),
    PARSED("Parsed", "파싱 완료"),
    VERIFYING("Verifying", "검증 중"),
    VERIFIED("Verified", "검증 완료"),
    APPLYING("Applying to LDAP", "LDAP 적용 중"),
    APPLIED("Applied", "LDAP 적용 완료"),
    FAILED("Failed", "실패");
    
    private final String code;
    private final String description;
    
    ProcessStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public boolean isCompleted() {
        return this == APPLIED;
    }
    
    public boolean isFailed() {
        return this == FAILED;
    }
    
    public boolean isProcessing() {
        return this == PARSING || this == VERIFYING || this == APPLYING;
    }
}
