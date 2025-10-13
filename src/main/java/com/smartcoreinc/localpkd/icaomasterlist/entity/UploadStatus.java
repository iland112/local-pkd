package com.smartcoreinc.localpkd.icaomasterlist.entity;

public enum UploadStatus {
    UPLOADED,           // 업로드 완료
    ANALYZING,          // 분석 중
    ANALYZED,           // 분석 완료 (LDAP 저장 대기)
    SAVING_TO_LDAP,     // LDAP 저장 중
    SAVED_TO_LDAP,      // LDAP 저장 완료
    FAILED              // 실패
}
