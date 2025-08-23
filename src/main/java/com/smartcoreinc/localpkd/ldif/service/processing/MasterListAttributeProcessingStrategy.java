package com.smartcoreinc.localpkd.ldif.service.processing;

import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.ldif.service.MasterListValidationResult;
import com.smartcoreinc.localpkd.ldif.service.verification.MasterListVerificationStrategy;
import com.smartcoreinc.localpkd.ldif.service.verification.VerificationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MasterListAttributeProcessingStrategy implements BinaryAttributeProcessingStrategy {
    
    private static final String MASTER_LIST_ATTRIBUTE = "pkdMasterListContent";
    
    private final MasterListVerificationStrategy masterListVerificationStrategy;
    
    public MasterListAttributeProcessingStrategy(MasterListVerificationStrategy masterListVerificationStrategy) {
        this.masterListVerificationStrategy = masterListVerificationStrategy;
    }
    
    @Override
    public ProcessingResult process(String attributeName, byte[] data, ProcessingContext context) {
        ProcessingResult result = ProcessingResult.success("Master List processed");
        result.getMetrics().incrementProcessed();
        
        try {
            VerificationContext verificationContext = new VerificationContext(
                context.getRecordNumber(), 
                context.getCountryCode()
            );

            MasterListValidationResult masterListResult = masterListVerificationStrategy.verify(
                data, verificationContext
            );

            if (masterListResult.isValid()) {
                result.getMetrics().incrementValid();
                log.info("Valid Master List found in record {} for country {}: {} CSCA certificates extracted",
                    context.getRecordNumber(), context.getCountryCode(), 
                    masterListResult.getCscaCertificates().size());
                
                // 컨텍스트에 추출된 인증서 정보 저장
                context.setSharedData("extractedCscaCount", masterListResult.getCscaCertificates().size());
                
            } else {
                result.getMetrics().incrementInvalid();
                result.addWarning(String.format("Record %d: Invalid Master List for country %s - %s",
                    context.getRecordNumber(), context.getCountryCode(), masterListResult.getErrorMessage()));
                log.warn("Invalid Master List in record {} for country {}: {}",
                    context.getRecordNumber(), context.getCountryCode(), masterListResult.getErrorMessage());
            }
            
            return result;
            
        } catch (Exception e) {
            result.getMetrics().incrementInvalid();
            return ProcessingResult.failure("Master List processing failed: " + e.getMessage())
                .addWarning(String.format("Record %d: Master List processing error - %s", 
                    context.getRecordNumber(), e.getMessage()));
        }
    }
    
    @Override
    public boolean supports(String attributeName) {
        return attributeName.contains(MASTER_LIST_ATTRIBUTE);
    }
    
    @Override
    public int getPriority() {
        return 2; // Master List는 인증서 처리 후에
    }
}