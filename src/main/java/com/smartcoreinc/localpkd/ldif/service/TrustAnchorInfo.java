package com.smartcoreinc.localpkd.ldif.service;

import java.util.List;

/**
 * Trust Anchor 정보
 */
public class TrustAnchorInfo {
    private final String country;
    private final List<CertificateInfo> certificates;
    private final MasterListInfo masterListInfo;
    
    public TrustAnchorInfo(String country, List<CertificateInfo> certificates, MasterListInfo masterListInfo) {
        this.country = country;
        this.certificates = certificates;
        this.masterListInfo = masterListInfo;
    }
    
    public String getCountry() { return country; }
    public List<CertificateInfo> getCertificates() { return certificates; }
    public MasterListInfo getMasterListInfo() { return masterListInfo; }
}
