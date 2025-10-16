package com.smartcoreinc.localpkd.common.enums;

import lombok.Getter;

@Getter
public enum CertificateStatus {
    /**
     * 유효 - 정상적으로 사용 가능
     */
    VALID("유효", "정상적으로 사용 가능한 인증서", true),
    
    /**
     * 만료 - 유효기간 경과
     */
    EXPIRED("만료", "유효기간이 지난 인증서", false),
    
    /**
     * 폐기 - CRL에 의해 폐기됨
     */
    REVOKED("폐기", "CRL에 의해 폐기된 인증서", false),
    
    /**
     * 정지 - 일시적으로 사용 중지
     */
    SUSPENDED("정지", "일시적으로 사용이 중지된 인증서", false);
    
    private final String displayName;
    private final String description;
    private final boolean usable;
    
    CertificateStatus(String displayName, String description, boolean usable) {
        this.displayName = displayName;
        this.description = description;
        this.usable = usable;
    }
    
    /**
     * 인증서 사용 가능 여부
     * 
     * @return true if certificate is usable
     */
    public boolean isUsable() {
        return usable;
    }
    
    /**
     * 인증서가 만료되었는지 확인
     * 
     * @return true if expired
     */
    public boolean isExpired() {
        return this == EXPIRED;
    }
    
    /**
     * 인증서가 폐기되었는지 확인
     * 
     * @return true if revoked
     */
    public boolean isRevoked() {
        return this == REVOKED;
    }
    
    /**
     * String으로부터 CertificateStatus 생성
     * 
     * @param status 상태 문자열
     * @return CertificateStatus
     */
    public static CertificateStatus fromString(String status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        
        try {
            return valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown certificate status: " + status);
        }
    }
    
    @Override
    public String toString() {
        return name();
    }
}
