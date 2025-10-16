package com.smartcoreinc.localpkd.parser.core;

import java.security.cert.X509CRL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.smartcoreinc.localpkd.common.enums.LdifChangeType;

import lombok.Builder;
import lombok.Data;

/**
 * 파싱된 CRL 정보
 */
@Data
@Builder
public class ParsedCrl {

    /**
     * 국가 코드
     */
    private String countryCode;

    /**
     * Issuer DN
     */
    private String issuerDn;

    /**
     * This Update (CRL 발급 시간)
     */
    private LocalDateTime thisUpdate;

    /**
     * Next Update (다음 CRL 예정 시간)
     */
    private LocalDateTime nextUpdate;

    /**
     * CRL Number (확장 필드)
     */
    private String crlNumber;

    // ==================== 폐기 항목 ====================

    /**
     * 총 폐기 항목 수
     */
    @Builder.Default
    private int totalRevocations = 0;

    @Builder.Default
    private List<RevokedCertificateEntry> revokedCertificates = new ArrayList<>();

    // ==================== 원본 데이터 ====================
    
    /**
     * X509CRL 객체
     */
    private X509CRL x509Crl;
    
    /**
     * DER 인코딩된 CRL
     */
    private byte[] crlDer;
    
    // ==================== LDIF 관련 ====================

    /**
     * LDIF DN
     */
    private String ldifDn;
    
    /**
     * LDIF Change Type (Delta인 경우)
     */
    private LdifChangeType changeType;

    // ==================== 출처 ====================
    
    /**
     * 출처 파일 ID
     */
    private String sourceFileId;
    
    /**
     * 파싱 시간
     */
    @Builder.Default
    private LocalDateTime parsedAt = LocalDateTime.now();
    
    // ==================== 편의 메서드 ====================

    /**
     * CRL이 유효한지 확인 (만료되지 않았는지)
     */
    public boolean isValid() {
        if (nextUpdate == null) {
            return true;  // nextUpdate가 없으면 무기한 유효
        }
        return LocalDateTime.now().isBefore(nextUpdate);
    }
    
    /**
     * CRL이 만료되었는지 확인
     */
    public boolean isExpired() {
        return !isValid();
    }
    
    /**
     * 폐기 항목 추가
     */
    public void addRevokedCertificate(RevokedCertificateEntry entry) {
        this.revokedCertificates.add(entry);
        this.totalRevocations = this.revokedCertificates.size();
    }
    
    /**
     * 특정 Serial Number가 폐기되었는지 확인
     */
    public boolean isRevoked(String serialNumber) {
        return revokedCertificates.stream()
            .anyMatch(entry -> entry.getSerialNumber().equals(serialNumber));
    }
    
    /**
     * Delta add인지 확인
     */
    public boolean isAdd() {
        return changeType == LdifChangeType.ADD;
    }
    
    /**
     * Delta modify인지 확인
     */
    public boolean isModify() {
        return changeType == LdifChangeType.MODIFY;
    }
    
    /**
     * Delta delete인지 확인
     */
    public boolean isDelete() {
        return changeType == LdifChangeType.DELETE;
    }

    /**
     * 요약 정보
     */
    public String getSummary() {
        return String.format(
            "CRL [%s] - Issuer: %s, Revocations: %s, Valid until: %s",
            countryCode != null ? countryCode : "??",
            issuerDn,
            totalRevocations,
            nextUpdate != null ? nextUpdate.toString() : "N/A"
        );
    }

    /**
     * 폐기된 인증서 항목
     */
    @Data
    @Builder
    public static class RevokedCertificateEntry {
        /**
         * Serial Number
         */
        private String serialNumber;

        /**
         * 페기 날짜
         */
        private LocalDateTime revocationDate;

        /**
         * 폐기 이유
         */
        private String revocationReson;

        /**
         * 폐기 이유 코드
         * 0: unspecified
         * 1: keyCompromise
         * 2: cACompromise
         * 3: affiliationChanged
         * 4: superseded
         * 5: cessationOfOperation
         * 6: certificateHold
         * 8: removeFromCRL
         * 9: privilegeWithdrawn
         * 10: aACompromise
         */
        private Integer reasonCode;
    }
}
