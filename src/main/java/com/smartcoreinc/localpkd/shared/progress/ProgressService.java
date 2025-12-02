package com.smartcoreinc.localpkd.shared.progress;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor; // Import Executor

/**
 * ProgressService - 파일 처리 진행 상황 관리 서비스
 *
 * <p>SSE (Server-Sent Events)를 통해 클라이언트에 실시간으로 진행 상황을 전송합니다.</p>
 *
 * <h3>주요 기능</h3>
 * <ul>
 *   <li>SSE 연결 관리 (등록/제거) - uploadId 기반</li>
 *   <li>특정 uploadId에 대한 진행 상황 전송</li>
 *   <li>최근 진행 상황 캐싱 (연결 시점 이전 상태 제공)</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // SSE 연결 등록
 * SseEmitter emitter = progressService.createEmitter(uploadId);
 *
 * // 진행 상황 전송
 * progressService.sendProgress(
 *     ProcessingProgress.parsingInProgress(uploadId, 50, 100, "인증서 파싱 중...")
 * );
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-22
 */
@Slf4j
@Service
@RequiredArgsConstructor // Lombok for constructor injection of final fields
public class ProgressService {

    /**
     * SSE Emitter 타임아웃 (10분)
     * 대용량 파일 파싱 시 충분한 시간 확보
     */
    private static final long SSE_TIMEOUT = 10 * 60 * 1000L;

    /**
     * 활성 SSE 연결 맵 (uploadId -> SseEmitter)
     */
    private final Map<UUID, SseEmitter> uploadIdToEmitters = new ConcurrentHashMap<>();

    /**
     * 최근 진행 상황 캐시 (uploadId → ProcessingProgress)
     * 클라이언트가 나중에 연결하더라도 최근 상태를 확인할 수 있도록
     */
    private final Map<UUID, ProcessingProgress> recentProgressCache = new ConcurrentHashMap<>();
    
    // Inject the taskExecutor defined in FileUploadAsyncConfig
    private final Executor taskExecutor;

    /**
     * SSE Emitter 생성 및 등록
     *
     * @param uploadId 연결될 업로드 ID
     * @return 새로 생성된 SseEmitter
     */
    public SseEmitter createEmitter(UUID uploadId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // 이전 Emitter가 있으면 완료 처리
        SseEmitter existingEmitter = uploadIdToEmitters.put(uploadId, emitter);
        if (existingEmitter != null) {
            existingEmitter.complete();
            log.debug("Replaced existing SSE emitter for uploadId: {}", uploadId);
        }

        // 연결 초기화
        emitter.onCompletion(() -> {
            log.debug("SSE connection for uploadId {} completed", uploadId);
            uploadIdToEmitters.remove(uploadId);
            // 완료된 진행 상황은 캐시에 유지될 수 있음 (scheduleProgressCacheRemoval 에 의해 제거될 것)
        });

        emitter.onTimeout(() -> {
            log.debug("SSE connection for uploadId {} timed out", uploadId);
            uploadIdToEmitters.remove(uploadId);
            emitter.complete();
        });

        emitter.onError((ex) -> {
            log.warn("SSE connection for uploadId {} error: {}", uploadId, ex.getMessage());
            uploadIdToEmitters.remove(uploadId);
            emitter.complete(); // 에러 발생 시 Emitter 완료 처리
        });

        log.info("New SSE connection established for uploadId: {}. Total connections: {}", uploadId, uploadIdToEmitters.size());

        // 연결 확인 이벤트 전송
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("{\"message\":\"SSE connection established for " + uploadId + "\"}"));
        } catch (IOException e) {
            log.error("Failed to send connection event for uploadId {}", uploadId, e);
            uploadIdToEmitters.remove(uploadId);
            emitter.complete();
        }

        // 연결 시점에 최신 진행 상황 전송 (만약 있다면)
        ProcessingProgress cachedProgress = recentProgressCache.get(uploadId);
        if (cachedProgress != null) {
            try {
                emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(cachedProgress.toJson()));
                log.debug("Sent cached progress to new emitter for uploadId: {}", uploadId);
            } catch (IOException e) {
                log.warn("Failed to send cached progress to new emitter for uploadId {}: {}", uploadId, e.getMessage());
                uploadIdToEmitters.remove(uploadId);
                emitter.complete();
            }
        }

        return emitter;
    }

    /**
     * 진행 상황 전송 (특정 uploadId에 연결된 클라이언트에만 전송)
     *
     * @param progress 진행 상황
     */
    public void sendProgress(ProcessingProgress progress) {
        if (progress == null) {
            log.warn("Attempted to send null progress");
            return;
        }

        // 캐시에 저장
        recentProgressCache.put(progress.getUploadId(), progress);

        // 완료 또는 실패 시 일정 시간 후 캐시에서 제거
        if (progress.isCompleted() || progress.isFailed()) {
            scheduleProgressCacheRemoval(progress.getUploadId());
        }

        log.debug("Sending progress: uploadId={}, stage={}, percentage={}%",
            progress.getUploadId(), progress.getStage(), progress.getPercentage());

        SseEmitter targetEmitter = uploadIdToEmitters.get(progress.getUploadId());
        if (targetEmitter != null) {
            try {
                targetEmitter.send(SseEmitter.event()
                    .name("progress")
                    .data(progress.toJson()));
            } catch (IOException e) {
                log.warn("Failed to send progress to client for uploadId {}: {}", progress.getUploadId(), e.getMessage());
                // 에러 발생 시 해당 emitter 제거 및 완료 처리
                uploadIdToEmitters.remove(progress.getUploadId());
                // Try to complete emitter, but ignore if already unusable
                try {
                    targetEmitter.complete();
                } catch (Exception completionException) {
                    log.trace("Emitter already unusable for uploadId {}: {}", progress.getUploadId(), completionException.getMessage());
                }
            }
        }
        else {
            log.debug("No active SSE emitter found for uploadId: {}", progress.getUploadId());
        }
    }

    /**
     * 특정 uploadId의 최근 진행 상황 조회
     *
     * @param uploadId 업로드 ID
     * @return 최근 진행 상황 (없으면 null)
     */
    public ProcessingProgress getRecentProgress(UUID uploadId) {
        return recentProgressCache.get(uploadId);
    }

    /**
     * 모든 최근 진행 상황 조회 (디버깅용)
     *
     * @return 모든 진행 상황 Map
     */
    public Map<UUID, ProcessingProgress> getAllRecentProgress() {
        return Map.copyOf(recentProgressCache);
    }

    /**
     * 활성 연결 수 조회
     *
     * @return 활성 SSE 연결 수
     */
    public int getActiveConnectionCount() {
        return uploadIdToEmitters.size();
    }

    /**
     * 진행 상황 캐시 제거 예약 (10초 후)
     *
     * @param uploadId 업로드 ID
     */
    private void scheduleProgressCacheRemoval(UUID uploadId) {
        taskExecutor.execute(() -> { // Use the injected taskExecutor
            try {
                Thread.sleep(10_000); // 10초 대기
                recentProgressCache.remove(uploadId);
                log.debug("Progress cache removed for uploadId: {}", uploadId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Progress cache removal interrupted for uploadId: {}", uploadId);
            }
        });
    }

    /**
     * 하트비트 전송 (연결 유지용)
     *
     * 30초마다 자동으로 실행되어 모든 활성 SSE 연결에 heartbeat 전송
     * 대용량 파일 처리 시 SSE 연결이 타임아웃되는 것을 방지
     */
    @Scheduled(fixedRate = 30000) // 30초마다 실행
    public void sendHeartbeat() {
        if (uploadIdToEmitters.isEmpty()) {
            return; // 활성 연결이 없으면 생략
        }

        log.debug("Sending heartbeat to {} active SSE connections", uploadIdToEmitters.size());

        // 모든 연결된 emitter에 하트비트 전송
        uploadIdToEmitters.forEach((uploadId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                    .name("heartbeat")
                    .data("{\"timestamp\":" + System.currentTimeMillis() + "}"));
            } catch (Exception e) {
                // 연결이 이미 끊어진 경우(AsyncRequestNotUsableException 포함) 조용히 emitter만 제거
                log.debug("Failed to send heartbeat to uploadId {}, removing emitter (reason: {})",
                    uploadId, e.getClass().getSimpleName());
                uploadIdToEmitters.remove(uploadId);
            }
        });
    }

    /**
     * 모든 SSE 연결 종료
     */
    public void closeAllConnections() {
        log.info("Closing all SSE connections. Total: {}", uploadIdToEmitters.size());
        uploadIdToEmitters.forEach((uploadId, emitter) -> {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("Error closing emitter for uploadId {}: {}", uploadId, e.getMessage());
            }
        });
        uploadIdToEmitters.clear();
        recentProgressCache.clear();
    }
}