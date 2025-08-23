package com.smartcoreinc.localpkd.ldif.service.verification;

import java.security.cert.X509Certificate;

// 체인 검증 요청 클래스
public class CertificateChainRequest {
    private final X509Certificate certificate;
    private final String issuerCountry;

    public CertificateChainRequest(X509Certificate certificate, String issuerCountry) {
        this.certificate = certificate;
        this.issuerCountry = issuerCountry;
    }

    public X509Certificate getCertificate() {
        return certificate;
    }

    public String getIssuerCountry() {
        return issuerCountry;
    }
}
