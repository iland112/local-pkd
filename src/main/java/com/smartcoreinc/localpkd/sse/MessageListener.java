package com.smartcoreinc.localpkd.sse;

import java.util.Map;

public interface MessageListener {
    void onMessage(Map<String, Object> messageMap);
}
