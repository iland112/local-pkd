package com.smartcoreinc.localpkd.sse;

public record ProgressEvent(Progress progress,
                            int processedCount,
                            int totalCount,
                            String message) {

}