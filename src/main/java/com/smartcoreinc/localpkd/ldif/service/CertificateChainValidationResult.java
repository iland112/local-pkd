package com.smartcoreinc.localpkd.ldif.service;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * 인증서 체인 검증 결과
 */
public class CertificateChainValidationResult {
    private final boolean valid;
    private final String message;
    private final String errorMessage;
    private final List<X509Certificate> certificateChain;
    
    private CertificateChainValidationResult(boolean valid, String message, String errorMessage, 
                                            List<X509Certificate> certificateChain) {
        this.valid = valid;
        this.message = message;
        this.errorMessage = errorMessage;
        this.certificateChain = certificateChain;
    }
    
    public static CertificateChainValidationResult valid(String message, List<X509Certificate> certificateChain) {
        return new CertificateChainValidationResult(true, message, null, certificateChain);
    }
    
    public static CertificateChainValidationResult invalid(String errorMessage) {
        return new CertificateChainValidationResult(false, null, errorMessage, null);
    }
    
    public boolean isValid() { return valid; }
    public String getMessage() { return message; }
    public String getErrorMessage() { return errorMessage; }
    public List<X509Certificate> getCertificateChain() { return certificateChain; }
}
