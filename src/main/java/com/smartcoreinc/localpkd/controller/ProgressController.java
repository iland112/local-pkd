package com.smartcoreinc.localpkd.controller;

import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

/**
 * ProgressController - SSE 진행 상황 API
 *
 * <p>파일 처리 진행 상황을 SSE (Server-Sent Events)로 실시간 전송합니다.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>GET /progress/stream - SSE 스트림 연결</li>
 *   <li>GET /progress/status/{uploadId} - 최근 진행 상황 조회</li>
 *   <li>GET /progress/connections - 활성 연결 수 조회 (관리용)</li>
 * </ul>
 *
 * <h3>HTMX SSE 통합</h3>
 * <pre>{@code
 * <div hx-ext="sse" sse-connect="/progress/stream">
 *   <div sse-swap="progress" hx-swap="innerHTML">
 *     <!-- 진행 상황 표시 영역 -->
 *   </div>
 * </div>
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-22
 */
@Slf4j
@RestController
@RequestMapping("/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    /**
     * SSE 스트림 연결
     *
     * <p>클라이언트가 이 엔드포인트에 연결하면 실시간으로 진행 상황을 수신할 수 있습니다.</p>
     *
     * @return SseEmitter
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress() {
        log.info("=== SSE connection request ===");
        SseEmitter emitter = progressService.createEmitter();
        log.info("SSE emitter created. Active connections: {}",
            progressService.getActiveConnectionCount());
        return emitter;
    }

    /**
     * 특정 uploadId의 최근 진행 상황 조회
     *
     * <p>SSE 연결 전에 현재 상태를 확인하거나, 연결 없이 상태만 조회할 때 사용합니다.</p>
     *
     * @param uploadId 업로드 ID
     * @return 최근 진행 상황 (JSON)
     */
    @GetMapping("/status/{uploadId}")
    public ResponseEntity<Map<String, Object>> getProgressStatus(@PathVariable UUID uploadId) {
        log.debug("Progress status requested for uploadId: {}", uploadId);

        ProcessingProgress progress = progressService.getRecentProgress(uploadId);

        if (progress == null) {
            return ResponseEntity.ok(Map.of(
                "exists", false,
                "message", "No progress found for uploadId: " + uploadId
            ));
        }

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("exists", true);
        response.put("uploadId", progress.getUploadId().toString());
        response.put("stage", progress.getStage().name());
        response.put("stageName", progress.getStage().getDisplayName());
        response.put("percentage", progress.getPercentage());
        response.put("processedCount", progress.getProcessedCount());
        response.put("totalCount", progress.getTotalCount());
        response.put("message", progress.getMessage());
        response.put("errorMessage", progress.getErrorMessage() != null ? progress.getErrorMessage() : "");
        response.put("details", progress.getDetails() != null ? progress.getDetails() : "");
        response.put("isCompleted", progress.isCompleted());
        response.put("isFailed", progress.isFailed());
        response.put("updatedAt", progress.getUpdatedAt().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * 활성 SSE 연결 수 조회 (관리/모니터링용)
     *
     * @return 연결 정보
     */
    @GetMapping("/connections")
    public ResponseEntity<Map<String, Object>> getConnections() {
        int activeConnections = progressService.getActiveConnectionCount();
        Map<UUID, ProcessingProgress> allProgress = progressService.getAllRecentProgress();

        return ResponseEntity.ok(Map.of(
            "activeConnections", activeConnections,
            "cachedProgressCount", allProgress.size(),
            "cachedUploadIds", allProgress.keySet()
        ));
    }

    /**
     * 하트비트 엔드포인트 (테스트용)
     *
     * <p>SSE 연결이 정상인지 테스트할 때 사용합니다.</p>
     *
     * @return 성공 메시지
     */
    @GetMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> sendHeartbeat() {
        progressService.sendHeartbeat();
        return ResponseEntity.ok(Map.of(
            "message", "Heartbeat sent to all connections",
            "activeConnections", progressService.getActiveConnectionCount()
        ));
    }
}
