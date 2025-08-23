package com.smartcoreinc.localpkd.ldif.service;

/**
 * 인증서 검증 결과
 */
public class CertificateValidationResult {
    private final boolean valid;
    private final String message;
    private final String details;
    private final String errorMessage;

    private CertificateValidationResult(boolean valid, String message, String details, String errorMessage) {
        this.valid = valid;
        this.message = message;
        this.details = details;
        this.errorMessage = errorMessage;
    }
    
    public static CertificateValidationResult valid(String message, String details) {
        return new CertificateValidationResult(true, message, details, null);
    }
    
    public static CertificateValidationResult invalid(String errorMessage) {
        return new CertificateValidationResult(false, null, null, errorMessage);
    }
    
    public static CertificateValidationResult invalid(String message, String details) {
        return new CertificateValidationResult(false, message, details, message);
    }
    
    public boolean isValid() { return valid; }
    public String getMessage() { return message; }
    public String getDetails() { return details; }
    public String getErrorMessage() { return errorMessage; }
}
