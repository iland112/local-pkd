package com.smartcoreinc.localpkd.ldif.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Trust Anchor 정보
 */
public class TrustAnchorInfo {
    private final String countryCode;
    private final List<CertificateInfo> certificates;
    private final MasterListInfo masterListInfo;

    public TrustAnchorInfo(String countryCode, List<CertificateInfo> certificates, MasterListInfo masterListInfo) {
        this.countryCode = countryCode;
        this.certificates = certificates != null ? certificates : new ArrayList<>();
        this.masterListInfo = masterListInfo;
    }

    public String getCountryCode() { return countryCode; }
    public List<CertificateInfo> getCertificates() { return certificates; }
    public MasterListInfo getMasterListInfo() { return masterListInfo; }
}
