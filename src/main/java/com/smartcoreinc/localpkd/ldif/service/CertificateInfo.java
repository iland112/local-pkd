package com.smartcoreinc.localpkd.ldif.service;

import java.util.Date;

/**
 * 인증서 기본 정보
 */
public class CertificateInfo {
    private final String subject;
    private final String issuer;
    private final String serialNumber;
    private final Date notBefore;
    private final Date notAfter;

    public CertificateInfo(String subject, String issuer, String serialNumber, 
            Date notBefore, Date notAfter) {
        this.subject = subject;
        this.issuer = issuer;
        this.serialNumber = serialNumber;
        this.notBefore = notBefore;
        this.notAfter = notAfter;
    }

    public String getSubject() { return subject; }
    public String getIssuer() { return issuer; }
    public String getSerialNumber() { return serialNumber; }
    public Date getNotBefore() { return notBefore; }
    public Date getNotAfter() { return notAfter; }
}
