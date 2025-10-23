package com.smartcoreinc.localpkd.config;

import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * SchedulingConfig - Spring Scheduling 설정
 *
 * <p>SSE 하트비트 등 주기적인 작업을 스케줄링합니다.</p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-22
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SchedulingConfig {

    private final ProgressService progressService;

    /**
     * SSE 하트비트 전송 (30초마다)
     *
     * <p>SSE 연결을 유지하고 클라이언트가 연결 상태를 확인할 수 있도록 합니다.</p>
     */
    @Scheduled(fixedRate = 30000) // 30초
    public void sendSseHeartbeat() {
        int activeConnections = progressService.getActiveConnectionCount();

        if (activeConnections > 0) {
            log.debug("Sending SSE heartbeat to {} active connection(s)", activeConnections);
            progressService.sendHeartbeat();
        }
    }
}
