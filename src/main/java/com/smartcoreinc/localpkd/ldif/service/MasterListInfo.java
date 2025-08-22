package com.smartcoreinc.localpkd.ldif.service;

/**
 * Master List 정보
 */
public class MasterListInfo {
    private final String countryCode;
    private final int certificateCount;
    private final boolean signatureValid;
    private final long lastUpdated;

    public MasterListInfo(String countryCode, int certificateCount, boolean signatureValid, long lastUpdated) {
        this.countryCode = countryCode;
        this.certificateCount = certificateCount;
        this.signatureValid = signatureValid;
        this.lastUpdated = lastUpdated;
    }

    public String getCountryCode() { return countryCode; }
    public int getCertificateCount() { return certificateCount; }
    public boolean isSignatureValid() { return signatureValid; }
    public long getLastUpdated() { return lastUpdated; }
}
