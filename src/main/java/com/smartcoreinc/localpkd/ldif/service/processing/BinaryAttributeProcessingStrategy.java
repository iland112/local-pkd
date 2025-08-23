package com.smartcoreinc.localpkd.ldif.service.processing;

public interface BinaryAttributeProcessingStrategy {
    ProcessingResult process(String attributeName, byte[] data, ProcessingContext context);
    boolean supports(String attributeName);
    int getPriority(); // 우선순위 (낮을수록 먼저 처리)
}
