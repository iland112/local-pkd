package com.smartcoreinc.localpkd.common.enums;

import lombok.Getter;

@Getter
public enum CertificateType {
    CSCA("CSCA", "Country Signing Certificate Authority"),
    DSC("DSC", "Document Signer Certificate");
    
    private final String code;
    private final String description;
    
    CertificateType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
