package com.smartcoreinc.localpkd.ldif.service.processing;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CrlAttributeProcessingStrategy implements BinaryAttributeProcessingStrategy {
    
    private static final List<String> CRL_ATTRIBUTES = Arrays.asList(
        "certificateRevocationList", "authorityRevocationList"
    );
    
    @Override
    public ProcessingResult process(String attributeName, byte[] data, ProcessingContext context) {
        ProcessingResult result = ProcessingResult.success("CRL processed");
        result.getMetrics().incrementProcessed();
        
        try {
            // TODO: CRL 검증 로직 구현
            log.debug("Processing CRL attribute '{}' with {} bytes for record {}", 
                attributeName, data.length, context.getRecordNumber());
            
            // 현재는 기본적인 구조 검증만
            if (data.length > 0 && data[0] == 0x30) {
                result.getMetrics().incrementValid();
                log.debug("CRL data appears to have valid DER structure");
            } else {
                result.getMetrics().incrementInvalid();
                result.addWarning(String.format("Record %d: CRL data does not have valid DER structure", 
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
