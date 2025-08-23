package com.smartcoreinc.localpkd.ldif.service.processing;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.ldif.service.CertificateValidationResult;
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
    
    public X509CertificateAttributeProcessingStrategy(X509CertificateVerificationStrategy certificateVerificationStrategy) {
        this.certificateVerificationStrategy = certificateVerificationStrategy;
    }
    
    @Override
    public ProcessingResult process(String attributeName, byte[] data, ProcessingContext context) {
        ProcessingResult result = ProcessingResult.success("Certificate processed");
        result.getMetrics().incrementProcessed();
        
        try {
            VerificationContext verificationContext = new VerificationContext(
                context.getRecordNumber(), 
                context.getCountryCode()
            );
            
            CertificateValidationResult validationResult = certificateVerificationStrategy.verify(
                data, verificationContext
            );
            
            if (validationResult.isValid()) {
                result.getMetrics().incrementValid();
                log.debug("Valid X.509 certificate found in record {}: {}", 
                    context.getRecordNumber(), validationResult.getDetails());
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