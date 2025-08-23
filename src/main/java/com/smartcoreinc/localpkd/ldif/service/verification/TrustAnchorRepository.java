package com.smartcoreinc.localpkd.ldif.service.verification;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.smartcoreinc.localpkd.ldif.service.CertificateInfo;
import com.smartcoreinc.localpkd.ldif.service.MasterListInfo;
import com.smartcoreinc.localpkd.ldif.service.TrustAnchorInfo;

import lombok.extern.slf4j.Slf4j;

// Trust Anchor 저장소
@Slf4j
@Repository
public class TrustAnchorRepository {
    private final Map<String, List<X509Certificate>> trustAnchors = new HashMap<>();
    private final Map<String, MasterListInfo> masterListInfoMap = new HashMap<>();
    
    public void storeTrustAnchors(String countryCode, List<X509Certificate> certificates) {
        if (!certificates.isEmpty()) {
            trustAnchors.put(countryCode.toUpperCase(), new ArrayList<>(certificates));
            log.info("Stored {} CSCA certificates as trust anchors for country: {}", 
                certificates.size(), countryCode);
        }
    }
    
    public List<X509Certificate> getTrustAnchors(String countryCode) {
        return trustAnchors.get(countryCode.toUpperCase());
    }
    
    public Map<String, List<X509Certificate>> getAllTrustAnchors() {
        return Collections.unmodifiableMap(trustAnchors);
    }
    
    public void storeMasterListInfo(String countryCode, MasterListInfo masterListInfo) {
        masterListInfoMap.put(countryCode.toUpperCase(), masterListInfo);
    }
    
    public Map<String, MasterListInfo> getMasterListInfoMap() {
        return Collections.unmodifiableMap(masterListInfoMap);
    }
    
    public Map<String, TrustAnchorInfo> getTrustAnchorsSummary() {
        return trustAnchors.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> createTrustAnchorInfo(entry.getKey(), entry.getValue())
            ));
    }
    
    private TrustAnchorInfo createTrustAnchorInfo(String country, List<X509Certificate> certificates) {
        List<CertificateInfo> certInfos = certificates.stream()
                .map(this::createCertificateInfo)
                .collect(Collectors.toList());
        
        MasterListInfo masterListInfo = masterListInfoMap.get(country);
        return new TrustAnchorInfo(country, certInfos, masterListInfo);
    }
    
    private CertificateInfo createCertificateInfo(X509Certificate cert) {
        return new CertificateInfo(
            cert.getSubjectX500Principal().getName(),
            cert.getIssuerX500Principal().getName(),
            cert.getSerialNumber().toString(),
            cert.getNotBefore(),
            cert.getNotAfter()
        );
    }
}
