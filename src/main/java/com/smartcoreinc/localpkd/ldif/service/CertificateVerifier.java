package com.smartcoreinc.localpkd.ldif.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * X.509 인증서 검증 및 Master List 처리 전담 클래스
 */
@Slf4j
@Component
public class CertificateVerifier {
    private static final CertificateFactory CERTIFICATE_FACTORY;
    
    // Trust Anchor 저장소 (국가별 CSCA 인증서들)
    private final Map<String, List<X509Certificate>> trustAnchors = new HashMap<>();
    private final Map<String, MasterListInfo> masterListInfoMap = new HashMap<>();

    static {
        try {
            if (java.security.Security.getProvider("BC") == null) {
                java.security.Security.addProvider(new BouncyCastleProvider());
            }

            CERTIFICATE_FACTORY = java.security.Security.getProvider("BC") != null ?
                CertificateFactory.getInstance("X.509", "BC") :
                CertificateFactory.getInstance("X.509");
        } catch (CertificateException | NoSuchProviderException e) {
            throw new RuntimeException("Failed to initialize CertificateFactory", e);
        }
    }

    /**
     * X.509 인증서 검증
     */
    public CertificateValidationResult validateX509Certificate(byte[] certBytes, int recordNumber) {
        if (certBytes == null || certBytes.length < 10) {
            return CertificateValidationResult.invalid("Certificate data is null or too short");
        }

        if (certBytes[0] != 0x30) {
            return CertificateValidationResult.invalid("Invalid DER encoding - does not start with 0x30");
        }

        try {
            X509Certificate certificate = parseX509Certificate(certBytes);
            return validateCertificateValidity(certificate);
        } catch (CertificateException e) {
            return CertificateValidationResult.invalid("Certificate parsing failed: " + e.getMessage());
        } catch (Exception e) {
            return CertificateValidationResult.invalid("Unexpected error during validation: " + e.getMessage());
        }
    }

    /**
     * X.509 인증서 파싱
     */
    public X509Certificate parseX509Certificate(byte[] certBytes) throws CertificateException {
        // BouncyCastle 우선 시도 (EC 파라미터 처리에 더 강함)
        try {
            X509CertificateHolder holder = new X509CertificateHolder(certBytes);
            return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
        } catch (Exception e) {
            // 기본 CertificateFactory로 fallback
        }

        try (InputStream bis = new ByteArrayInputStream(certBytes)) {
            return (X509Certificate) CERTIFICATE_FACTORY.generateCertificate(bis);
        } catch (CertificateException e) {
            throw e;
        } catch (Exception e) {
            throw new CertificateException("Failed to parse X.509 certificate: " + e.getMessage(), e);
        }
    }

    /**
     * 인증서 유효성 검증
     */
    private CertificateValidationResult validateCertificateValidity(X509Certificate certificate) {
        try {
            String subject = certificate.getSubjectX500Principal().getName();
            String issuer = certificate.getIssuerX500Principal().getName();
            String serialNumber = certificate.getSerialNumber().toString();

            // 유효 기간 검증
            certificate.checkValidity();

            String details = String.format("Subject: %s, Issuer: %s, Serial: %s", subject, issuer, serialNumber);
            return CertificateValidationResult.valid("Valid certificate", details);
        } catch (Exception validityException) {
            String subject = certificate.getSubjectX500Principal().getName();
            String issuer = certificate.getIssuerX500Principal().getName();
            String serialNumber = certificate.getSerialNumber().toString();
            
            String details = String.format("Subject: %s, Issuer: %s, Serial: %s, Validity Error: %s",
                    subject, issuer, serialNumber, validityException.getMessage());
            return CertificateValidationResult.invalid("Certificate validity check failed", details);
        }
    }

    /**
     * Master List 검증 및 CSCA 인증서 추출
     */
    public MasterListValidationResult validateAndExtractMasterList(byte[] masterListData, int recordNumber, String countryCode) {
        if (masterListData == null || masterListData.length < 100) {
            return MasterListValidationResult.invalid("Master List data is null or too short");
        }

        log.info("Processing Master List for country {} (record {}) - {} bytes", 
            countryCode, recordNumber, masterListData.length);

        try {
            CMSSignedData cmsSignedData = new CMSSignedData(masterListData);
            
            // 서명 검증
            SignatureValidationResult signatureResult = validateSignature(cmsSignedData);
            
            // CSCA 인증서 추출
            List<X509Certificate> cscaCertificates = extractCscaCertificates(cmsSignedData);
            
            // Trust Anchors 저장
            storeTrustAnchors(countryCode, cscaCertificates);
            
            // Master List 정보 저장
            MasterListInfo masterListInfo = createMasterListInfo(countryCode, cscaCertificates.size(), signatureResult.isValid());
            masterListInfoMap.put(countryCode.toUpperCase(), masterListInfo);

            String message = signatureResult.isValid() ? 
                "Master List validated successfully" : 
                "Master List parsed but signature verification failed: " + signatureResult.getErrorMessage();

            return MasterListValidationResult.valid(message, cscaCertificates, masterListInfo);

        } catch (Exception e) {
            log.error("Error processing Master List for country {}: {}", countryCode, e.getMessage(), e);
            return MasterListValidationResult.invalid("Master List parsing failed: " + e.getMessage());
        }
    }

    /**
     * CMS SignedData 서명 검증
     */
    private SignatureValidationResult validateSignature(CMSSignedData cmsSignedData) {
        try {
            SignerInformationStore signers = cmsSignedData.getSignerInfos();
            if (signers.size() == 0) {
                return new SignatureValidationResult(false, "No signers found in Master List");
            }

            for (SignerInformation signer : signers.getSigners()) {
                try {
                    X509CertificateHolder signerCert = (X509CertificateHolder) cmsSignedData.getCertificates()
                            .getMatches(signer.getSID()).iterator().next();
                    
                    if (signer.verify(new JcaSimpleSignerInfoVerifierBuilder()
                            .setProvider("BC").build(signerCert))) {
                        log.debug("Master List signature verified successfully");
                        return new SignatureValidationResult(true, "Signature verified");
                    }
                } catch (Exception e) {
                    log.debug("Signature verification failed: {}", e.getMessage());
                }
            }
            
            return new SignatureValidationResult(false, "All signature verifications failed");
        } catch (Exception e) {
            return new SignatureValidationResult(false, "Signature validation error: " + e.getMessage());
        }
    }

    /**
     * CSCA 인증서 추출
     */
    private List<X509Certificate> extractCscaCertificates(CMSSignedData cmsSignedData) {
        List<X509Certificate> certificates = new ArrayList<>();
        
        // 1. SignedData의 certificates 필드에서 추출
        extractFromCertificatesField(cmsSignedData, certificates);
        
        // 2. Content에서 추출
        extractFromContent(cmsSignedData, certificates);
        
        // 중복 제거
        return removeDuplicateCertificates(certificates);
    }

    /**
     * SignedData의 certificates 필드에서 인증서 추출
     */
    private void extractFromCertificatesField(CMSSignedData cmsSignedData, List<X509Certificate> certificates) {
        try {
            SignerInformationStore signers = cmsSignedData.getSignerInfos();
            
            for (Object certHolder : cmsSignedData.getCertificates().getMatches(null)) {
                if (certHolder instanceof X509CertificateHolder) {
                    try {
                        X509Certificate cert = new JcaX509CertificateConverter()
                            .setProvider("BC")
                            .getCertificate((X509CertificateHolder) certHolder);
                        
                        // 서명자 인증서가 아닌 경우만 CSCA로 간주
                        if (!isSignerCertificate(cert, certHolder, signers)) {
                            certificates.add(cert);
                            log.debug("Found CSCA certificate from certificates field: Subject={}", 
                                cert.getSubjectX500Principal().getName());
                        }
                    } catch (Exception e) {
                        log.debug("Failed to convert certificate holder: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract from certificates field: {}", e.getMessage());
        }
    }

    /**
     * Content에서 인증서 추출
     */
    private void extractFromContent(CMSSignedData cmsSignedData, List<X509Certificate> certificates) {
        try {
            Object content = cmsSignedData.getSignedContent().getContent();
            if (content instanceof byte[]) {
                byte[] contentBytes = (byte[]) content;
                log.debug("Processing signed content: {} bytes", contentBytes.length);
                
                CertificateExtractor extractor = new CertificateExtractor();
                List<X509Certificate> contentCertificates = extractor.extractCertificatesFromContent(contentBytes);
                
                for (X509Certificate cert : contentCertificates) {
                    certificates.add(cert);
                    log.debug("Added certificate from content: Subject={}", 
                        cert.getSubjectX500Principal().getName());
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract from content: {}", e.getMessage());
        }
    }

    /**
     * 서명자 인증서인지 확인
     */
    private boolean isSignerCertificate(X509Certificate cert, Object certHolder, SignerInformationStore signers) {
        for (SignerInformation signer : signers.getSigners()) {
            if (signer.getSID().match(certHolder)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 중복 인증서 제거
     */
    private List<X509Certificate> removeDuplicateCertificates(List<X509Certificate> certificates) {
        Map<String, X509Certificate> uniqueCerts = new HashMap<>();
        
        for (X509Certificate cert : certificates) {
            String key = cert.getSerialNumber().toString() + "|" + cert.getIssuerX500Principal().getName();
            uniqueCerts.put(key, cert);
        }
        
        return new ArrayList<>(uniqueCerts.values());
    }

    /**
     * Trust Anchors 저장
     */
    private void storeTrustAnchors(String countryCode, List<X509Certificate> certificates) {
        if (!certificates.isEmpty()) {
            trustAnchors.put(countryCode.toUpperCase(), new ArrayList<>(certificates));
            log.info("Stored {} CSCA certificates as trust anchors for country: {}", 
                certificates.size(), countryCode);
        }
    }

    /**
     * MasterListInfo 생성
     */
    private MasterListInfo createMasterListInfo(String countryCode, int certificateCount, boolean signatureValid) {
        return new MasterListInfo(countryCode, certificateCount, signatureValid, System.currentTimeMillis());
    }

    /**
     * 인증서 체인 검증
     */
    public CertificateChainValidationResult validateCertificateChain(X509Certificate certificate, String issuerCountry) {
        try {
            List<X509Certificate> trustAnchorsForCountry = trustAnchors.get(issuerCountry.toUpperCase());
            if (trustAnchorsForCountry == null || trustAnchorsForCountry.isEmpty()) {
                return CertificateChainValidationResult.invalid("No trust anchors found for country: " + issuerCountry);
            }

            // 각 Trust Anchor로 검증 시도
            for (X509Certificate trustAnchor : trustAnchorsForCountry) {
                try {
                    if (validateAgainstTrustAnchor(certificate, trustAnchor)) {
                        log.info("Certificate chain validation successful using Trust Anchor: {}",
                            trustAnchor.getSubjectX500Principal().getName());

                        return CertificateChainValidationResult.valid(
                            "Certificate chain validated successfully",
                            Arrays.asList(certificate, trustAnchor)
                        );
                    }
                } catch (Exception e) {
                    log.debug("Validation failed with trust anchor {}: {}", 
                        trustAnchor.getSubjectX500Principal().getName(), e.getMessage());
                }
            }
            
            return CertificateChainValidationResult.invalid(
                "Certificate could not be validated against any trust anchor for country: " + issuerCountry);
                
        } catch (Exception e) {
            return CertificateChainValidationResult.invalid(
                "Error during certificate chain validation: " + e.getMessage());
        }
    }

    /**
     * Trust Anchor 대상 검증
     */
    private boolean validateAgainstTrustAnchor(X509Certificate certificate, X509Certificate trustAnchor) throws Exception {
        // 발급자 확인
        if (!certificate.getIssuerX500Principal().equals(trustAnchor.getSubjectX500Principal())) {
            return false;
        }
        
        // 서명 검증
        certificate.verify(trustAnchor.getPublicKey());
        
        // 유효기간 확인
        certificate.checkValidity();
        trustAnchor.checkValidity();
        
        return true;
    }

    /**
     * Trust Anchors 정보 조회
     */
    public Map<String, TrustAnchorInfo> getTrustAnchorsSummary() {
        return trustAnchors.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> createTrustAnchorInfo(entry.getKey(), entry.getValue())
            ));
    }

    /**
     * TrustAnchorInfo 생성
     */
    private TrustAnchorInfo createTrustAnchorInfo(String country, List<X509Certificate> certificates) {
        List<CertificateInfo> certInfos = certificates.stream()
                .map(this::createCertificateInfo)
                .collect(Collectors.toList());
        
        MasterListInfo masterListInfo = masterListInfoMap.get(country);
        return new TrustAnchorInfo(country, certInfos, masterListInfo);
    }

    /**
     * CertificateInfo 생성
     */
    private CertificateInfo createCertificateInfo(X509Certificate cert) {
        return new CertificateInfo(
            cert.getSubjectX500Principal().getName(),
            cert.getIssuerX500Principal().getName(),
            cert.getSerialNumber().toString(),
            cert.getNotBefore(),
            cert.getNotAfter()
        );
    }

    // Getter methods for trust anchors (for backward compatibility)
    public Map<String, List<X509Certificate>> getTrustAnchors() {
        return Collections.unmodifiableMap(trustAnchors);
    }

    public Map<String, MasterListInfo> getMasterListInfoMap() {
        return Collections.unmodifiableMap(masterListInfoMap);
    }

    // 내부 클래스들
    private static class SignatureValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public SignatureValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
}
