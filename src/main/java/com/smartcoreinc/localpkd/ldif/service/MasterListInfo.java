package com.smartcoreinc.localpkd.ldif.service;

/**
 * Master List 정보
 */
public class MasterListInfo {
    private final String countryCode;
    private final int certificateCount;
    private final boolean signatureValid;
    private final long timestamp;
    
    public MasterListInfo(String countryCode, int certificateCount, boolean signatureValid, long timestamp) {
        this.countryCode = countryCode;
        this.certificateCount = certificateCount;
        this.signatureValid = signatureValid;
        this.timestamp = timestamp;
    }
    
    public String getCountryCode() { return countryCode; }
    public int getCertificateCount() { return certificateCount; }
    public boolean isSignatureValid() { return signatureValid; }
    public long getTimestamp() { return timestamp; }
}
