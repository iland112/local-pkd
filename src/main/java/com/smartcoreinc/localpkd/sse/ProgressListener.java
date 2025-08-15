package com.smartcoreinc.localpkd.sse;

import java.util.Map;

public interface ProgressListener {
    void onProgress(Progress progress, int processedCount, int totalCount, String message);
}
