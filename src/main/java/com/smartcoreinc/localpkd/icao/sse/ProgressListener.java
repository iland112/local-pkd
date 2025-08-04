package com.smartcoreinc.localpkd.icao.sse;

import java.util.Map;

public interface ProgressListener {
    void onProgress(Progress progress, Map<String, Integer> counts, String message);
}
