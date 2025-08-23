package com.smartcoreinc.localpkd.ldif.service.processing;

import java.util.ArrayList;
import java.util.List;

public class ProcessingResult {
    private final boolean success;
    private final String message;
    private final List<String> warnings = new ArrayList<>();
    private final ProcessingMetrics metrics = new ProcessingMetrics();
    
    private ProcessingResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    public static ProcessingResult success(String message) {
        return new ProcessingResult(true, message);
    }
    
    public static ProcessingResult failure(String message) {
        return new ProcessingResult(false, message);
    }
    
    public ProcessingResult addWarning(String warning) {
        warnings.add(warning);
        return this;
    }
    
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public List<String> getWarnings() { return warnings; }
    public ProcessingMetrics getMetrics() { return metrics; }
    
    public static class ProcessingMetrics {
        private int processedItems = 0;
        private int validItems = 0;
        private int invalidItems = 0;
        
        public void incrementProcessed() { processedItems++; }
        public void incrementValid() { validItems++; }
        public void incrementInvalid() { invalidItems++; }
        
        public int getProcessedItems() { return processedItems; }
        public int getValidItems() { return validItems; }
        public int getInvalidItems() { return invalidItems; }
    }
}
