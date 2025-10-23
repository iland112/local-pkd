package com.smartcoreinc.localpkd.shared.progress;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ProgressService - 파일 처리 진행 상황 관리 서비스
 *
 * <p>SSE (Server-Sent Events)를 통해 클라이언트에 실시간으로 진행 상황을 전송합니다.</p>
 *
 * <h3>주요 기능</h3>
 * <ul>
 *   <li>SSE 연결 관리 (등록/제거)</li>
 *   <li>진행 상황 브로드캐스트 (모든 연결된 클라이언트에 전송)</li>
 *   <li>특정 uploadId에 대한 진행 상황 전송</li>
 *   <li>최근 진행 상황 캐싱 (연결 시점 이전 상태 제공)</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // SSE 연결 등록
 * SseEmitter emitter = progressService.createEmitter();
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
public class ProgressService {

    /**
     * SSE Emitter 타임아웃 (5분)
     */
    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    /**
     * 활성 SSE 연결 목록
     */
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * 최근 진행 상황 캐시 (uploadId → ProcessingProgress)
     * 클라이언트가 나중에 연결하더라도 최근 상태를 확인할 수 있도록
     */
    private final Map<UUID, ProcessingProgress> recentProgressCache = new ConcurrentHashMap<>();

    /**
     * SSE Emitter 생성 및 등록
     *
     * @return 새로 생성된 SseEmitter
     */
    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // 연결 초기화
        emitter.onCompletion(() -> {
            log.debug("SSE connection completed");
            emitters.remove(emitter);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE connection timed out");
            emitters.remove(emitter);
            emitter.complete();
        });

        emitter.onError((ex) -> {
            log.warn("SSE connection error: {}", ex.getMessage());
            emitters.remove(emitter);
        });

        emitters.add(emitter);
        log.info("New SSE connection established. Total connections: {}", emitters.size());

        // 연결 확인 이벤트 전송
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("{\"message\":\"SSE connection established\"}"));
        } catch (IOException e) {
            log.error("Failed to send connection event", e);
            emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * 진행 상황 전송 (모든 연결된 클라이언트에 브로드캐스트)
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

        // 모든 클라이언트에 전송
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(progress.toJson()));
            } catch (IOException e) {
                log.warn("Failed to send progress to client: {}", e.getMessage());
                emitters.remove(emitter);
            }
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
        return emitters.size();
    }

    /**
     * 진행 상황 캐시 제거 예약 (10초 후)
     *
     * @param uploadId 업로드 ID
     */
    private void scheduleProgressCacheRemoval(UUID uploadId) {
        new Thread(() -> {
            try {
                Thread.sleep(10_000); // 10초 대기
                recentProgressCache.remove(uploadId);
                log.debug("Progress cache removed for uploadId: {}", uploadId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Progress cache removal interrupted for uploadId: {}", uploadId);
            }
        }).start();
    }

    /**
     * 하트비트 전송 (연결 유지용)
     *
     * Spring @Scheduled로 주기적으로 호출 가능
     */
    public void sendHeartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("heartbeat")
                    .data("{\"timestamp\":" + System.currentTimeMillis() + "}"));
            } catch (IOException e) {
                log.debug("Failed to send heartbeat, removing emitter");
                emitters.remove(emitter);
            }
        }
    }

    /**
     * 모든 SSE 연결 종료
     */
    public void closeAllConnections() {
        log.info("Closing all SSE connections. Total: {}", emitters.size());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("Error closing emitter: {}", e.getMessage());
            }
        }
        emitters.clear();
        recentProgressCache.clear();
    }
}
