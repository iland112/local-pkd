package com.smartcoreinc.localpkd.sse.event;

import java.util.Map;

public record ParsingEvent(
    String sessionId,
    double progress,
    int processedEntries,
    int totalEntries,
    String fileName,
    String currentDN,
    String message,
    int errorCount,
    int warningCount,
    Map<String, Integer> entryTypeStats,
    Map<String, Object> metadata
) {

    public boolean isCompleted() {
        return progress >= 1.0;
    }

    public boolean hasErrors() {
        return errorCount > 0;
    }

    public String getProgressPercentage() {
        return String.format("%.1f%%", progress * 100);
    }
}
