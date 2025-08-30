package com.smartcoreinc.localpkd.sse;

import java.util.HashMap;
import java.util.Map;

public record ProgressEvent(Progress progress,
                            int processedCount,
                            int totalCount,
                            String message,
                            Map<String, Object> metadata) {
    
    // Constructor without metadata for backward compatibility
    public ProgressEvent(Progress progress, int processedCount, int totalCount, String message) {
        this(progress, processedCount, totalCount, message, new HashMap<>());
    }
    
    // Helper method to get task ID from metadata
    public String getTaskId() {
        return (String) metadata.get("taskId");
    }
    
    // Helper method to get session ID from metadata
    public String getSessionId() {
        return (String) metadata.get("sessionId");
    }

}