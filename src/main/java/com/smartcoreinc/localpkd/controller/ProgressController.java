package com.smartcoreinc.localpkd.controller;

import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "실시간 진행상황 API (SSE)", description = "파일 처리 진행상황을 실시간으로 전송하는 API")
@Slf4j
@RestController
@RequestMapping("/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    @Operation(summary = "SSE 스트림 연결",
               description = "클라이언트가 실시간으로 파일 처리 진행 상황을 수신하기 위해 연결하는 Server-Sent Events 스트림입니다.")
    @ApiResponse(responseCode = "200", description = "SSE 스트림 연결 성공")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress() {
        log.info("=== SSE connection request ===");
        SseEmitter emitter = progressService.createEmitter();
        log.info("SSE emitter created. Active connections: {}",
            progressService.getActiveConnectionCount());
        return emitter;
    }

    @Operation(summary = "특정 업로드의 진행 상황 조회",
               description = "주어진 업로드 ID에 대한 가장 최근의 처리 진행 상황을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "진행 상황 조회 성공")
    @ApiResponse(responseCode = "404", description = "해당 업로드 ID에 대한 진행 상황을 찾을 수 없음")
    @GetMapping("/status/{uploadId}")
    public ResponseEntity<Map<String, Object>> getProgressStatus(@PathVariable UUID uploadId) {
        log.debug("Progress status requested for uploadId: {}", uploadId);

        ProcessingProgress progress = progressService.getRecentProgress(uploadId);

        if (progress == null) {
            return ResponseEntity.status(404).body(Map.of(
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

    @Operation(summary = "활성 SSE 연결 수 조회 (관리용)",
               description = "현재 서버에 연결된 활성 SSE 클라이언트의 수와 캐시된 진행 정보 수를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
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

    @Operation(summary = "하트비트 전송 (테스트용)",
               description = "모든 활성 SSE 연결에 하트비트 이벤트를 전송하여 연결 상태를 테스트합니다.")
    @ApiResponse(responseCode = "200", description = "하트비트 전송 성공")
    @GetMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> sendHeartbeat() {
        progressService.sendHeartbeat();
        return ResponseEntity.ok(Map.of(
            "message", "Heartbeat sent to all connections",
            "activeConnections", progressService.getActiveConnectionCount()
        ));
    }
}
