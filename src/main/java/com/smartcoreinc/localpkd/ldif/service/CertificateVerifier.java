package com.smartcoreinc.localpkd.ldif.service;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.ldif.service.verification.CertificateChainRequest;
import com.smartcoreinc.localpkd.ldif.service.verification.CertificateChainVerificationStrategy;
import com.smartcoreinc.localpkd.ldif.service.verification.CertificateParsingService;
import com.smartcoreinc.localpkd.ldif.service.verification.MasterListVerificationStrategy;
import com.smartcoreinc.localpkd.ldif.service.verification.TrustAnchorRepository;
import com.smartcoreinc.localpkd.ldif.service.verification.VerificationContext;
import com.smartcoreinc.localpkd.ldif.service.verification.X509CertificateVerificationStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * X.509 인증서 검증 및 Master List 처리 전담 클래스
 */
@Slf4j
@Component
public class CertificateVerifier {
    private final X509CertificateVerificationStrategy x509Strategy;
    private final MasterListVerificationStrategy masterListStrategy;
    private final CertificateChainVerificationStrategy chainStrategy;
    private final TrustAnchorRepository trustAnchorRepository;
    private final CertificateParsingService certificateParsingService;
    
    static {
        if (java.security.Security.getProvider("BC") == null) {
            java.security.Security.addProvider(new BouncyCastleProvider());
        }
    }
    
    public CertificateVerifier(
            X509CertificateVerificationStrategy x509Strategy,
            MasterListVerificationStrategy masterListStrategy,
            CertificateChainVerificationStrategy chainStrategy,
            TrustAnchorRepository trustAnchorRepository,
            CertificateParsingService certificateParsingService) {
        this.x509Strategy = x509Strategy;
        this.masterListStrategy = masterListStrategy;
        this.chainStrategy = chainStrategy;
        this.trustAnchorRepository = trustAnchorRepository;
        this.certificateParsingService = certificateParsingService;
    }
    
    /**
     * X.509 인증서 검증
     */
    public CertificateValidationResult validateX509Certificate(byte[] certBytes, int recordNumber) {
        VerificationContext context = new VerificationContext(recordNumber, "UNKNOWN");
        return x509Strategy.verify(certBytes, context);
    }
    
    /**
     * Master List 검증 및 CSCA 인증서 추출
     */
    public MasterListValidationResult validateAndExtractMasterList(byte[] masterListData, int recordNumber, String countryCode) {
        VerificationContext context = new VerificationContext(recordNumber, countryCode);
        MasterListValidationResult result = masterListStrategy.verify(masterListData, context);
        
        // Trust Anchors 저장
        if (result.isValid()) {
            List<X509Certificate> certificates = context.getAttribute("extractedCertificates");
            if (certificates != null) {
                trustAnchorRepository.storeTrustAnchors(countryCode, certificates);
                trustAnchorRepository.storeMasterListInfo(countryCode, result.getMasterListInfo());
            }
        }
        
        return result;
    }
    
    /**
     * 인증서 체인 검증
     */
    public CertificateChainValidationResult validateCertificateChain(X509Certificate certificate, String issuerCountry) {
        VerificationContext context = new VerificationContext(0, issuerCountry);
        CertificateChainRequest request = 
            new CertificateChainRequest(certificate, issuerCountry);
        
        return chainStrategy.verify(request, context);
    }
    
    /**
     * X.509 인증서 파싱 (유틸리티 메서드)
     */
    public X509Certificate parseX509Certificate(byte[] certBytes) throws CertificateException {
        return certificateParsingService.parseX509Certificate(certBytes);
    }
    
    // Delegate methods to repository
    public Map<String, List<X509Certificate>> getTrustAnchors() {
        return trustAnchorRepository.getAllTrustAnchors();
    }
    
    public Map<String, MasterListInfo> getMasterListInfoMap() {
        return trustAnchorRepository.getMasterListInfoMap();
    }
    
    public Map<String, TrustAnchorInfo> getTrustAnchorsSummary() {
        return trustAnchorRepository.getTrustAnchorsSummary();
    }
}
