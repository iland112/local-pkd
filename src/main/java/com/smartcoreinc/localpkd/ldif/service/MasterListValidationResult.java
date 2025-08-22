package com.smartcoreinc.localpkd.ldif.service;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Master List 검증 결과
 */
public class MasterListValidationResult {
    private final boolean isValid;
    private final String errorMessage;
    private final List<X509Certificate> cscaCertificates;
    private final MasterListInfo masterListInfo;

    private MasterListValidationResult(boolean isValid, String errorMessage, 
            List<X509Certificate> cscaCertificates, MasterListInfo masterListInfo) {
        this.isValid = isValid;
        this.errorMessage = errorMessage;
        this.cscaCertificates = cscaCertificates != null ? cscaCertificates : new ArrayList<>();
        this.masterListInfo = masterListInfo;
    }

    public static MasterListValidationResult valid(String message, 
            List<X509Certificate> certificates, MasterListInfo info) {
        return new MasterListValidationResult(true, message, certificates, info);
    }

    public static MasterListValidationResult invalid(String errorMessage) {
        return new MasterListValidationResult(false, errorMessage, null, null);
    }

    public boolean isValid() { return isValid; }
    public String getErrorMessage() { return errorMessage; }
    public List<X509Certificate> getCscaCertificates() { return cscaCertificates; }
    public MasterListInfo getMasterListInfo() { return masterListInfo; }
}
