package com.smartcoreinc.localpkd.common.enums;

import lombok.Getter;

/**
 * LDIF Change Type
 * Delta LDIF 파일의 변경 타입
 */
@Getter
public enum LdifChangeType {
    /**
     * 추가 (changetype: add)
     * 새로운 항목 추가
     */
    ADD("add", "추가"),
    
    /**
     * 수정 (changetype: modify)
     * 기존 항목 수정
     */
    MODIFY("modify", "수정"),
    
    /**
     * 삭제 (changetype: delete)
     * 항목 삭제
     */
    DELETE("delete", "삭제"),
    
    /**
     * DN 변경 (changetype: modrdn 또는 moddn)
     * DN 변경 (드물게 사용됨)
     */
    MODRDN("modrdn", "DN 변경");
    
    private final String code;
    private final String description;
    
    LdifChangeType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    /**
     * Code로부터 LdifChangeType 생성
     */
    public static LdifChangeType fromCode(String code) {
        if (code == null) {
            return null;
        }
        
        String normalized = code.trim().toLowerCase();
        
        for (LdifChangeType type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        
        // modrdn 또는 moddn 모두 MODRDN으로 처리
        if ("moddn".equals(normalized)) {
            return MODRDN;
        }
        
        return null;
    }
    
    @Override
    public String toString() {
        return code;
    }
}
