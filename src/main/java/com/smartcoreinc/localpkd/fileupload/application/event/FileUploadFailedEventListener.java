package com.smartcoreinc.localpkd.fileupload.application.event;

import com.smartcoreinc.localpkd.fileupload.domain.event.FileUploadFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * File Upload Failed Event Listener
 *
 * <p>파일 업로드 실패 관련 Domain Event를 처리합니다.</p>
 *
 * <h3>처리하는 이벤트</h3>
 * <ul>
 *   <li>{@link FileUploadFailedEvent} - 파일 업로드 실패</li>
 * </ul>
 *
 * <h3>처리 전략</h3>
 * <ul>
 *   <li><b>동기 처리</b>: 에러 로깅, 실패 통계 업데이트</li>
 *   <li><b>비동기 처리</b>: 재시도 큐 등록, 관리자 알림</li>
 * </ul>
 *
 * <h3>재시도 정책</h3>
 * <ul>
 *   <li>재시도 가능한 오류: IO Exception, 네트워크 오류 등</li>
 *   <li>재시도 불가능한 오류: Validation 실패, 중복 파일 등</li>
 *   <li>최대 재시도 횟수: 3회</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadFailedEventListener {

    // 실패 통계 (In-Memory, 향후 Redis 또는 DB로 이동)
    private final AtomicInteger totalFailureCount = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Integer> failureByErrorType = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> lastFailureByFileName = new ConcurrentHashMap<>();

    // TODO: Future enhancements
    // private final RetryQueueService retryQueueService;
    // private final NotificationService notificationService;
    // private final MeterRegistry meterRegistry;

    /**
     * 파일 업로드 실패 이벤트 처리 (동기)
     *
     * <p>업로드 실패 시 즉시 에러를 로깅하고 통계를 업데이트합니다.</p>
     *
     * <h4>처리 내용</h4>
     * <ul>
     *   <li>에러 로깅 (ERROR 레벨)</li>
     *   <li>실패 통계 업데이트</li>
     *   <li>메트릭 카운터 증가</li>
     * </ul>
     *
     * @param event 파일 업로드 실패 이벤트
     */
    @EventListener
    public void handleFileUploadFailed(FileUploadFailedEvent event) {
        log.error("=== [Event] FileUploadFailed ===");
        log.error("Upload ID: {}", event.uploadId().getId());
        log.error("File name: {}", event.fileName());
        log.error("Error message: {}", event.errorMessage());
        log.error("Event occurred at: {}", event.occurredOn());

        // 실패 통계 업데이트
        updateFailureStatistics(event);

        // TODO: Prometheus 메트릭 증가
        // meterRegistry.counter("file.upload.failed",
        //     "error_type", extractErrorType(event.errorMessage())
        // ).increment();

        // 오류 타입 분류
        String errorType = classifyErrorType(event.errorMessage());
        log.error("Error type: {}", errorType);

        // 재시도 가능 여부 판단
        boolean retryable = isRetryable(event.errorMessage());
        log.error("Retryable: {}", retryable);

        if (retryable) {
            log.warn("⚠️  This error is retryable. Consider adding to retry queue.");
        }
    }

    /**
     * 파일 업로드 실패 이벤트 처리 (비동기, 트랜잭션 커밋 후)
     *
     * <p>트랜잭션 커밋 후 비동기적으로 재시도 큐 등록 및 알림을 발송합니다.</p>
     *
     * <h4>처리 내용</h4>
     * <ul>
     *   <li>재시도 큐에 등록 (재시도 가능한 경우)</li>
     *   <li>관리자 알림 발송</li>
     *   <li>에러 리포트 생성</li>
     * </ul>
     *
     * @param event 파일 업로드 실패 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFileUploadFailedAsync(FileUploadFailedEvent event) {
        log.info("=== [Event-Async] FileUploadFailed (Processing retry/alert) ===");
        log.info("Upload ID: {}", event.uploadId().getId());

        try {
            // 재시도 가능한 오류인 경우
            if (isRetryable(event.errorMessage())) {
                // TODO: 재시도 큐에 등록
                // retryQueueService.enqueue(new RetryTask(
                //     event.uploadId().getId().toString(),
                //     event.fileName(),
                //     event.errorMessage()
                // ));

                log.info("Upload failure would be added to retry queue here");
            } else {
                // 재시도 불가능한 오류 → 관리자 알림
                // TODO: 알림 발송
                // notificationService.sendUploadFailureAlert(
                //     event.fileName(),
                //     event.errorMessage()
                // );

                log.info("Admin notification would be sent for non-retryable error");
            }

            // 실패 패턴 분석
            analyzeFailurePattern(event);

        } catch (Exception e) {
            log.error("Failed to process upload failure for uploadId: {}",
                event.uploadId().getId(), e);
        }
    }

    /**
     * 실패 통계 업데이트
     *
     * @param event 파일 업로드 실패 이벤트
     */
    private void updateFailureStatistics(FileUploadFailedEvent event) {
        // 전체 실패 횟수 증가
        int totalFailures = totalFailureCount.incrementAndGet();

        // 오류 타입별 실패 횟수 증가
        String errorType = classifyErrorType(event.errorMessage());
        failureByErrorType.merge(errorType, 1, Integer::sum);

        // 파일명별 마지막 실패 시간 기록
        lastFailureByFileName.put(event.fileName(), event.occurredOn());

        log.debug("Failure statistics updated - Total failures: {}, Error type: {} ({})",
            totalFailures, errorType, failureByErrorType.get(errorType));
    }

    /**
     * 오류 타입 분류
     *
     * @param errorMessage 오류 메시지
     * @return 오류 타입
     */
    private String classifyErrorType(String errorMessage) {
        if (errorMessage == null) {
            return "UNKNOWN";
        }

        String lowerMessage = errorMessage.toLowerCase();

        if (lowerMessage.contains("io") || lowerMessage.contains("file")) {
            return "IO_ERROR";
        } else if (lowerMessage.contains("network") || lowerMessage.contains("connection")) {
            return "NETWORK_ERROR";
        } else if (lowerMessage.contains("validation") || lowerMessage.contains("invalid")) {
            return "VALIDATION_ERROR";
        } else if (lowerMessage.contains("duplicate")) {
            return "DUPLICATE_FILE";
        } else if (lowerMessage.contains("size") || lowerMessage.contains("too large")) {
            return "SIZE_ERROR";
        } else if (lowerMessage.contains("permission") || lowerMessage.contains("access denied")) {
            return "PERMISSION_ERROR";
        } else {
            return "OTHER";
        }
    }

    /**
     * 재시도 가능 여부 판단
     *
     * @param errorMessage 오류 메시지
     * @return 재시도 가능하면 true
     */
    private boolean isRetryable(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }

        String errorType = classifyErrorType(errorMessage);

        // 재시도 가능한 오류 타입
        return errorType.equals("IO_ERROR") ||
               errorType.equals("NETWORK_ERROR") ||
               errorType.equals("PERMISSION_ERROR");
    }

    /**
     * 실패 패턴 분석
     *
     * <p>반복적인 실패 패턴을 분석하여 근본 원인을 파악합니다.</p>
     *
     * @param event 파일 업로드 실패 이벤트
     */
    private void analyzeFailurePattern(FileUploadFailedEvent event) {
        log.info("=== Failure Pattern Analysis ===");

        // 동일 파일명의 이전 실패 확인
        LocalDateTime lastFailure = lastFailureByFileName.get(event.fileName());
        if (lastFailure != null) {
            log.warn("  File '{}' has failed before at: {}", event.fileName(), lastFailure);
        }

        // 오류 타입별 통계
        log.info("  Failure statistics by error type:");
        failureByErrorType.forEach((errorType, count) -> {
            log.info("    - {}: {} times", errorType, count);
        });

        // 총 실패 횟수
        log.info("  Total failures: {}", totalFailureCount.get());

        // 권장 조치
        String errorType = classifyErrorType(event.errorMessage());
        String recommendation = getRecommendation(errorType);
        if (recommendation != null) {
            log.info("  Recommendation: {}", recommendation);
        }
    }

    /**
     * 오류 타입별 권장 조치
     *
     * @param errorType 오류 타입
     * @return 권장 조치
     */
    private String getRecommendation(String errorType) {
        return switch (errorType) {
            case "IO_ERROR" -> "Check disk space and file permissions";
            case "NETWORK_ERROR" -> "Check network connectivity and retry";
            case "VALIDATION_ERROR" -> "Fix file content or format";
            case "DUPLICATE_FILE" -> "Remove duplicate or use force upload";
            case "SIZE_ERROR" -> "Compress file or increase size limit";
            case "PERMISSION_ERROR" -> "Check file system permissions";
            default -> null;
        };
    }

    /**
     * 현재 실패 통계 조회 (모니터링용)
     *
     * @return 총 실패 횟수
     */
    public int getTotalFailureCount() {
        return totalFailureCount.get();
    }

    /**
     * 오류 타입별 실패 통계 조회
     *
     * @param errorType 오류 타입
     * @return 해당 타입의 실패 횟수
     */
    public int getFailureCountByType(String errorType) {
        return failureByErrorType.getOrDefault(errorType, 0);
    }
}
