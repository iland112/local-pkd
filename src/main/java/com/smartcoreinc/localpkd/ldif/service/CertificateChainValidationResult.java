package com.smartcoreinc.localpkd.ldif.service;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * 인증서 체인 검증 결과
 */
public class CertificateChainValidationResult {
    private final boolean isValid;
    private final String message;
    private final List<X509Certificate> certificateChain;

    private CertificateChainValidationResult(boolean isValid, String message, List<X509Certificate> certificateChain) {
        this.isValid = isValid;
        this.message = message;
        this.certificateChain = certificateChain != null ? certificateChain : new ArrayList<>();
    }

    public static CertificateChainValidationResult valid(String message, List<X509Certificate> chain) {
        return new CertificateChainValidationResult(true, message, chain);
    }

    public static CertificateChainValidationResult invalid(String message) {
        return new CertificateChainValidationResult(false, message, null);
    }

    public boolean isValid() { return isValid; }
    public String getMessage() { return message; }
    public List<X509Certificate> getCertificateChain() { return certificateChain; }
}
