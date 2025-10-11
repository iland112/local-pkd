package com.smartcoreinc.localpkd.sse.event;

import java.util.Map;

public record LdapSaveEvent(
    String sessionId,
    double progress,
    int processedEntries,
    int totalEntries,
    int successCount,
    int failureCount,
    String status,  // "in-progress", "completed", "error", "cancelled"
    String message,
    Map<String, Object> metadata
) {
    public boolean isCompleted() {
        return progress >= 1.0 || "completed".equals(status);
    }
    
    public boolean hasErrors() {
        return "error".equals(status) || failureCount > 0;
    }
    
    public boolean isCancelled() {
        return "cancelled".equals(status);
    }
    
    public String getProgressPercentage() {
        return String.format("%.1f%%", progress * 100);
    }
    
    public double getSuccessRate() {
        return processedEntries > 0 ? (double) successCount / processedEntries * 100 : 0.0;
    }
}
