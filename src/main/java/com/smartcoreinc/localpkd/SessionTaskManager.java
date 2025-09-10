package com.smartcoreinc.localpkd;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisResult;

@Component
public class SessionTaskManager {
    // 세션별 분석 결과 저장 (실제로는 Redis나 DB 사용 권장)
    private final Map<String, LdifAnalysisResult> sessionResults = new ConcurrentHashMap<>();

    public SessionTaskManager() {
    }

    public Map<String, LdifAnalysisResult> getSessionResults() {
        return sessionResults;
    }
    
}
