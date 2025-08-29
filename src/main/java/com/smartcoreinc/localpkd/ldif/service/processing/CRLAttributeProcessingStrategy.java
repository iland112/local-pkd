package com.smartcoreinc.localpkd.ldif.service.processing;

import java.io.ByteArrayInputStream;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.ldif.service.verification.CertificateVerificationStrategy;
import com.smartcoreinc.localpkd.ldif.service.verification.VerificationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CRLAttributeProcessingStrategy implements BinaryAttributeProcessingStrategy {
    
    private static final List<String> CRL_ATTRIBUTES = Arrays.asList(
        "certificateRevocationList", "authorityRevocationList"
    );

    private final CertificateVerificationStrategy<X509CRL, Boolean> crlVerificationStrategy;

    public CRLAttributeProcessingStrategy(CertificateVerificationStrategy<X509CRL, Boolean> crlVerificationStrategy) {
        this.crlVerificationStrategy = crlVerificationStrategy;
    }
    
    @Override
    public ProcessingResult process(String attributeName, byte[] data, ProcessingContext context) {
        ProcessingResult result = ProcessingResult.success("CRL processed");
        result.getMetrics().incrementProcessed();
        
        try {
            log.debug("Processing CRL attribute '{}' with {} bytes for record {}", 
                attributeName, data.length, context.getRecordNumber());
            
            // CRL 파싱
            X509CRL crl = parseCRL(data);
            if (crl == null) {
                result.getMetrics().incrementInvalid();
                result.addWarning(String.format("Record %d: Failed to parse CRL data", 
                    context.getRecordNumber()));
                return result;
            }
            
            // CRL 검증 실행
            boolean isValid = verifyCRL(crl, context);
            
            if (isValid) {
                result.getMetrics().incrementValid();
                log.debug("CRL validation successful for record {}", context.getRecordNumber());
            } else {
                result.getMetrics().incrementInvalid();
                result.addWarning(String.format("Record %d: CRL validation failed", 
                    context.getRecordNumber()));
            }
            
            return result;
            
        } catch (Exception e) {
            result.getMetrics().incrementInvalid();
            return ProcessingResult.failure("CRL processing failed: " + e.getMessage())
                .addWarning(String.format("Record %d: CRL processing error - %s", 
                    context.getRecordNumber(), e.getMessage()));
        }
    }
    
    /**
     * CRL 데이터를 파싱하여 X509CRL 객체로 변환
     */
    private X509CRL parseCRL(byte[] data) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            return (X509CRL) factory.generateCRL(inputStream);
        } catch (CertificateException | CRLException e) {
            log.warn("Failed to parse CRL data: {}", e.getMessage());
            return null;
        }
    }

    /**
     * CertificateVerificationStrategy를 사용하여 CRL 검증
     */
    private boolean verifyCRL(X509CRL crl, ProcessingContext context) {
        // VerificationContext 생성
        VerificationContext verificationContext = new VerificationContext(
            context.getRecordNumber(),
            context.getCountryCode(),
            context.getEntryType()
        );
        
        try {
            // Delegate to CRL Verification Strategy
            Boolean verificationResult = crlVerificationStrategy.verify(crl, verificationContext);
            if (verificationResult != null && verificationResult) {
                log.debug("CRL verification passed with strategy: {}", 
                    crlVerificationStrategy.getClass().getSimpleName());
                return true;
            } else {
                log.debug("CRL verification failed with strategy: {}", 
                    crlVerificationStrategy.getClass().getSimpleName());
                return false;
            }
        } catch (Exception e) {
            log.warn("CRL verification strategy {} threw exception: {}", 
                crlVerificationStrategy.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean supports(String attributeName) {
        return CRL_ATTRIBUTES.stream()
            .anyMatch(crlAttr -> attributeName.toLowerCase().contains(crlAttr.toLowerCase()));
    }
    
    @Override
    public int getPriority() {
        return 3; // CRL은 마지막에 처리
    }
}
