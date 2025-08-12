package com.smartcoreinc.localpkd.sse;

public interface ProgressListener {
    void onProgress(Progress progress, int processedCount, int totalCount, String message);
}
