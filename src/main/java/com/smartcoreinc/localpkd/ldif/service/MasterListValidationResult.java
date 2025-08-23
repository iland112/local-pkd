package com.smartcoreinc.localpkd.ldif.service;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Master List 검증 결과
 */
public class MasterListValidationResult {
    private final boolean valid;
    private final String message;
    private final String errorMessage;
    private final List<X509Certificate> cscaCertificates;
    private final MasterListInfo masterListInfo;
    
    private MasterListValidationResult(boolean valid, String message, String errorMessage, 
                                      List<X509Certificate> cscaCertificates, MasterListInfo masterListInfo) {
        this.valid = valid;
        this.message = message;
        this.errorMessage = errorMessage;
        this.cscaCertificates = cscaCertificates;
        this.masterListInfo = masterListInfo;
    }
    
    public static MasterListValidationResult valid(String message, List<X509Certificate> cscaCertificates, 
                                                   MasterListInfo masterListInfo) {
        return new MasterListValidationResult(true, message, null, cscaCertificates, masterListInfo);
    }
    
    public static MasterListValidationResult invalid(String errorMessage) {
        return new MasterListValidationResult(false, null, errorMessage, null, null);
    }
    
    public boolean isValid() { return valid; }
    public String getMessage() { return message; }
    public String getErrorMessage() { return errorMessage; }
    public List<X509Certificate> getCscaCertificates() { return cscaCertificates; }
    public MasterListInfo getMasterListInfo() { return masterListInfo; }
}
