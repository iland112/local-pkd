package com.smartcoreinc.localpkd.ldif.service;

/**
 * 인증서 검증 결과
 */
public class CertificateValidationResult {
    private final boolean isValid;
    private final String errorMessage;
    private final String details;

    private CertificateValidationResult(boolean isValid, String errorMessage, String details) {
        this.isValid = isValid;
        this.errorMessage = errorMessage;
        this.details = details;
    }

    public static CertificateValidationResult valid(String message, String details) {
        return new CertificateValidationResult(true, message, details);
    }

    public static CertificateValidationResult invalid(String errorMessage) {
        return new CertificateValidationResult(false, errorMessage, null);
    }

    public static CertificateValidationResult invalid(String errorMessage, String details) {
        return new CertificateValidationResult(false, errorMessage, details);
    }

    public boolean isValid() { return isValid; }
    public String getErrorMessage() { return errorMessage; }
    public String getDetails() { return details; }
}
