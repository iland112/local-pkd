package com.smartcoreinc.localpkd.ldif.service.processing;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.icaomasterlist.entity.CscaCertificate;
import com.smartcoreinc.localpkd.icaomasterlist.service.CscaLdapSearchService;
import com.smartcoreinc.localpkd.ldif.service.CertificateValidationResult;
import com.smartcoreinc.localpkd.ldif.service.verification.CertificateChainVerificationStrategy;
import com.smartcoreinc.localpkd.ldif.service.verification.CertificateParsingService;
import com.smartcoreinc.localpkd.ldif.service.verification.VerificationContext;
import com.smartcoreinc.localpkd.ldif.service.verification.X509CertificateVerificationStrategy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class X509CertificateAttributeProcessingStrategy implements BinaryAttributeProcessingStrategy {
    
    private static final List<String> CERTIFICATE_ATTRIBUTES = Arrays.asList(
        "userCertificate", "caCertificate", "crossCertificatePair"
    );
    
    private final X509CertificateVerificationStrategy certificateVerificationStrategy;
    private final CscaLdapSearchService cscaLdapSearchService;
    private final CertificateChainVerificationStrategy chainVerificationStrategy;
    private final CertificateParsingService certificateParsingService;
    
    public X509CertificateAttributeProcessingStrategy(
        X509CertificateVerificationStrategy certificateVerificationStrategy,
            CscaLdapSearchService cscaLdapSearchService,
            CertificateChainVerificationStrategy chainVerificationStrategy,
            CertificateParsingService certificateParsingService) {
        this.certificateVerificationStrategy = certificateVerificationStrategy;
        this.cscaLdapSearchService = cscaLdapSearchService;
        this.chainVerificationStrategy = chainVerificationStrategy;
        this.certificateParsingService = certificateParsingService;
    }
    
    @Override
    public ProcessingResult process(String attributeName, byte[] data, ProcessingContext context) {
        ProcessingResult result = ProcessingResult.success("Certificate processed");
        result.getMetrics().incrementProcessed();
        
        try {
            VerificationContext verificationContext = new VerificationContext(
                context.getRecordNumber(), 
                context.getCountryCode(),
                context.getEntryType()
            );
            
            CertificateValidationResult validationResult =
                certificateVerificationStrategy.verify(data, verificationContext);
            
            if (validationResult.isValid()) {
                result.getMetrics().incrementValid();
                log.debug(
                    "Valid X.509 certificate found in record {}: {}", 
                    context.getRecordNumber(), validationResult.getDetails()
                );
                // 인증서가 유효한 경우 CSCA 인증서와의 신뢰 체인 검증 수행
                performTrustChainVerification(verificationContext, context, result);
            } else {
                result.getMetrics().incrementInvalid();
                result.addWarning(String.format("Record %d: Invalid X.509 certificate - %s",
                    context.getRecordNumber(), validationResult.getErrorMessage()));
                log.warn("Invalid X.509 certificate in record {}: {}", 
                    context.getRecordNumber(), validationResult.getErrorMessage());
            }
            
            return result;
            
        } catch (Exception e) {
            result.getMetrics().incrementInvalid();
            return ProcessingResult.failure("Certificate processing failed: " + e.getMessage())
                .addWarning(String.format("Record %d: Certificate processing error - %s", 
                    context.getRecordNumber(), e.getMessage()));
        }
    }

    /**
     * CSCA 인증서와의 신뢰 체인 검증 수행
     */
    private void performTrustChainVerification(VerificationContext verificationContext, 
                                             ProcessingContext context, 
                                             ProcessingResult result) {
        try {
            // 검증 컨텍스트에서 파싱된 인증서 추출
            X509Certificate parsedCertificate = verificationContext.getAttribute("parsedCertificate");
            if (parsedCertificate == null) {
                result.addWarning(String.format("Record %d: Parsed certificate not found in verification context", 
                    context.getRecordNumber()));
                return;
            }
            
            // 국가 코드로 해당 국가의 CSCA 인증서들 조회
            String countryCode = context.getCountryCode();
            if ("UNKNOWN".equals(countryCode)) {
                result.addWarning(String.format("Record %d: Cannot perform trust chain verification - unknown country code", 
                    context.getRecordNumber()));
                return;
            }
            
            List<CscaCertificate> cscaCertificates = cscaLdapSearchService.findCscaCertificatesByCountryCode(countryCode);
            
            if (cscaCertificates == null || cscaCertificates.isEmpty()) {
                result.addWarning(String.format("Record %d: No CSCA certificates found for country %s", 
                    context.getRecordNumber(), countryCode));
                return;
            }
            
            log.debug("Found {} CSCA certificates for country {} in record {}", 
                cscaCertificates.size(), countryCode, context.getRecordNumber());
            
            // CSCA 인증서들을 X509Certificate로 변환하고 직접 검증
            boolean chainValidated = false;
            String chainValidationDetails = null;
            String validatingCscaSubject = null;
            
            for (CscaCertificate cscaCert : cscaCertificates) {
                try {
                    if (cscaCert.getCertificate() == null) {
                        continue;
                    }
                    
                    X509Certificate cscaX509Cert = convertToX509Certificate(cscaCert);
                    if (cscaX509Cert == null) {
                        continue;
                    }
                    
                    // 직접 체인 검증 수행
                    if (validateCertificateChain(parsedCertificate, cscaX509Cert)) {
                        chainValidated = true;
                        validatingCscaSubject = cscaX509Cert.getSubjectX500Principal().getName();
                        chainValidationDetails = String.format("Certificate validated against CSCA: %s", validatingCscaSubject);
                        
                        log.info("Trust chain validation successful for record {} with CSCA: {}", 
                            context.getRecordNumber(), validatingCscaSubject);
                        break;
                    }
                    
                } catch (Exception e) {
                    log.debug("Chain verification failed with CSCA {} for record {}: {}", 
                        cscaCert.getCn(), context.getRecordNumber(), e.getMessage());
                }
            }
            
            // 체인 검증 결과를 결과에 반영
            if (chainValidated) {
                result.addWarning(String.format("Record %d: Trust chain validation successful - %s", 
                    context.getRecordNumber(), chainValidationDetails));
                // 컨텍스트에 체인 검증 성공 정보 저장
                context.setSharedData("trustChainValidated", true);
                context.setSharedData("trustChainDetails", chainValidationDetails);
                context.setSharedData("validatingCscaSubject", validatingCscaSubject);
            } else {
                result.addWarning(String.format("Record %d: Trust chain validation failed - certificate could not be validated against any CSCA for country %s", 
                    context.getRecordNumber(), countryCode));
                context.setSharedData("trustChainValidated", false);
            }
            
        } catch (Exception e) {
            result.addWarning(String.format("Record %d: Trust chain verification error - %s", 
                context.getRecordNumber(), e.getMessage()));
            log.error("Error during trust chain verification for record {}: {}", 
                context.getRecordNumber(), e.getMessage(), e);
        }
    }

    /**
     * 인증서와 CSCA 인증서 간의 신뢰 체인 검증
     */
    private boolean validateCertificateChain(X509Certificate certificate, X509Certificate cscaCertificate) {
        try {
            // 발급자 확인 - 인증서의 발급자가 CSCA의 주체와 일치하는지 확인
            if (!certificate.getIssuerX500Principal().equals(cscaCertificate.getSubjectX500Principal())) {
                log.debug("Issuer mismatch: Certificate issuer [{}] != CSCA subject [{}]", 
                    certificate.getIssuerX500Principal().getName(), 
                    cscaCertificate.getSubjectX500Principal().getName());
                return false;
            }
            
            // 서명 검증 - 인증서가 CSCA의 개인키로 서명되었는지 확인
            certificate.verify(cscaCertificate.getPublicKey());
            
            // 유효기간 확인 - 현재 시점에서 두 인증서 모두 유효한지 확인
            certificate.checkValidity();
            cscaCertificate.checkValidity();
            
            log.debug("Certificate chain validation successful: {} -> {}", 
                certificate.getSubjectX500Principal().getName(),
                cscaCertificate.getSubjectX500Principal().getName());
            
            return true;
            
        } catch (Exception e) {
            log.debug("Certificate chain validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * CSCA 인증서를 X509Certificate로 변환
     */
    private X509Certificate convertToX509Certificate(CscaCertificate cscaCertificate) {
        try {
            return certificateParsingService.parseX509Certificate(cscaCertificate.getCertificate());
        } catch (Exception e) {
            log.warn("Failed to parse CSCA certificate for country {}: {}", 
                cscaCertificate.getCountryCode(), e.getMessage());
            return null;
        }
    }
    
    @Override
    public boolean supports(String attributeName) {
        return CERTIFICATE_ATTRIBUTES.stream()
            .anyMatch(certAttr -> attributeName.toLowerCase().contains(certAttr.toLowerCase()));
    }
    
    @Override
    public int getPriority() {
        return 1; // 높은 우선순위
    }
}