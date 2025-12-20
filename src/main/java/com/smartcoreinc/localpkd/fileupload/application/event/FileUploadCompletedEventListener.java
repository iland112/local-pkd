package com.smartcoreinc.localpkd.fileupload.application.event;

import com.smartcoreinc.localpkd.fileupload.domain.event.FileUploadCompletedEvent;
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
import java.util.concurrent.atomic.AtomicLong;

/**
 * File Upload Completed Event Listener
 *
 * <p>파일 업로드 완료 관련 Domain Event를 처리합니다.</p>
 *
 * <h3>처리하는 이벤트</h3>
 * <ul>
 *   <li>{@link FileUploadCompletedEvent} - 파일 업로드 완료</li>
 * </ul>
 *
 * <h3>처리 전략</h3>
 * <ul>
 *   <li><b>동기 처리</b>: 완료 통계 업데이트, 성공 로깅</li>
 *   <li><b>비동기 처리</b>: 완료 알림, 리포트 생성</li>
 * </ul>
 *
 * <h3>통계 수집</h3>
 * <ul>
 *   <li>총 완료 횟수</li>
 *   <li>일자별 완료 횟수</li>
 *   <li>파일 타입별 완료 횟수</li>
 *   <li>평균 처리 시간 (향후)</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadCompletedEventListener {

    // 완료 통계 (In-Memory, 향후 Redis 또는 DB로 이동)
    private final AtomicInteger totalCompletedCount = new AtomicInteger(0);
    private final AtomicLong totalUploadedBytes = new AtomicLong(0);
    private final ConcurrentHashMap<String, Integer> completedByDate = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> lastCompletedByFileName = new ConcurrentHashMap<>();

    // TODO: Future enhancements
    // private final NotificationService notificationService;
    // private final MeterRegistry meterRegistry;
    // private final ReportGenerationService reportService;

    /**
     * 파일 업로드 완료 이벤트 처리 (동기)
     *
     * <p>업로드 완료 시 즉시 성공을 로깅하고 통계를 업데이트합니다.</p>
     *
     * <h4>처리 내용</h4>
     * <ul>
     *   <li>성공 로깅 (INFO 레벨)</li>
     *   <li>완료 통계 업데이트</li>
     *   <li>메트릭 카운터 증가</li>
     * </ul>
     *
     * @param event 파일 업로드 완료 이벤트
     */
    @EventListener
    public void handleFileUploadCompleted(FileUploadCompletedEvent event) {
        log.info("=== [Event] FileUploadCompleted ===");
        log.info("✅ Upload ID: {}", event.uploadId().getId());
        log.info("✅ File name: {}", event.fileName());
        log.info("✅ File hash: {}...", event.fileHash().substring(0, 8));
        log.info("✅ Event occurred at: {}", event.occurredOn());

        // 완료 통계 업데이트
        updateCompletionStatistics(event);

        // TODO: Prometheus 메트릭 증가
        // meterRegistry.counter("file.upload.completed",
        //     "file_type", extractFileType(event.fileName())
        // ).increment();

        // 축하 메시지 (마일스톤)
        int totalCompleted = totalCompletedCount.get();
        if (isMilestone(totalCompleted)) {
            log.info("🎉 Milestone reached: {} files uploaded successfully!", totalCompleted);
        }
    }

    /**
     * 파일 업로드 완료 이벤트 처리 (비동기, 트랜잭션 커밋 후)
     *
     * <p>트랜잭션 커밋 후 비동기적으로 완료 알림 및 리포트를 생성합니다.</p>
     *
     * <h4>처리 내용</h4>
     * <ul>
     *   <li>완료 알림 발송 (선택사항)</li>
     *   <li>일일 리포트 생성 (자동화)</li>
     *   <li>통계 대시보드 업데이트</li>
     * </ul>
     *
     * @param event 파일 업로드 완료 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFileUploadCompletedAsync(FileUploadCompletedEvent event) {
        log.info("=== [Event-Async] FileUploadCompleted (Generating reports) ===");
        log.info("Upload ID: {}", event.uploadId().getId());

        try {
            // TODO: 완료 알림 발송 (옵션)
            // if (notificationEnabled) {
            //     notificationService.sendUploadCompletionNotification(
            //         event.fileName(),
            //         event.uploadId().getId().toString()
            //     );
            // }

            // TODO: 리포트 생성
            // if (shouldGenerateReport()) {
            //     reportService.generateDailyUploadReport();
            // }

            log.info("Upload completion notification and report would be generated here");

            // 통계 요약 로깅
            logStatisticsSummary();

        } catch (Exception e) {
            log.error("Failed to process upload completion for uploadId: {}",
                event.uploadId().getId(), e);
        }
    }

    /**
     * 완료 통계 업데이트
     *
     * @param event 파일 업로드 완료 이벤트
     */
    private void updateCompletionStatistics(FileUploadCompletedEvent event) {
        // 전체 완료 횟수 증가
        int totalCompleted = totalCompletedCount.incrementAndGet();

        // 일자별 완료 횟수 증가
        String dateKey = event.occurredOn().toLocalDate().toString();
        completedByDate.merge(dateKey, 1, Integer::sum);

        // 파일명별 마지막 완료 시간 기록
        lastCompletedByFileName.put(event.fileName(), event.occurredOn());

        log.debug("Completion statistics updated - Total completed: {}, Today: {}",
            totalCompleted, completedByDate.get(dateKey));
    }

    /**
     * 마일스톤 여부 확인
     *
     * @param count 총 완료 횟수
     * @return 마일스톤이면 true
     */
    private boolean isMilestone(int count) {
        // 10, 50, 100, 500, 1000, ... 등
        if (count == 10 || count == 50 || count == 100 || count == 500 || count == 1000) {
            return true;
        }
        // 10000 단위
        return count > 0 && count % 10000 == 0;
    }

    /**
     * 통계 요약 로깅
     */
    private void logStatisticsSummary() {
        log.info("=== Upload Statistics Summary ===");
        log.info("  Total completed uploads: {}", totalCompletedCount.get());

        // 최근 7일간 통계
        log.info("  Recent daily uploads:");
        completedByDate.entrySet().stream()
            .sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey())) // 날짜 역순
            .limit(7)
            .forEach(entry -> {
                log.info("    - {}: {} files", entry.getKey(), entry.getValue());
            });

        // 오늘 통계
        String today = LocalDateTime.now().toLocalDate().toString();
        int todayCount = completedByDate.getOrDefault(today, 0);
        log.info("  Today's uploads: {}", todayCount);
    }

    /**
     * 현재 완료 통계 조회 (모니터링용)
     *
     * @return 총 완료 횟수
     */
    public int getTotalCompletedCount() {
        return totalCompletedCount.get();
    }

    /**
     * 특정 날짜의 완료 횟수 조회
     *
     * @param date 날짜 (YYYY-MM-DD)
     * @return 해당 날짜의 완료 횟수
     */
    public int getCompletedCountByDate(String date) {
        return completedByDate.getOrDefault(date, 0);
    }

    /**
     * 오늘의 완료 횟수 조회
     *
     * @return 오늘의 완료 횟수
     */
    public int getTodayCompletedCount() {
        String today = LocalDateTime.now().toLocalDate().toString();
        return getCompletedCountByDate(today);
    }

    /**
     * 파일의 마지막 완료 시간 조회
     *
     * @param fileName 파일명
     * @return 마지막 완료 시간 (없으면 null)
     */
    public LocalDateTime getLastCompletedTime(String fileName) {
        return lastCompletedByFileName.get(fileName);
    }

    /**
     * 모든 통계 초기화 (테스트/관리용)
     */
    public void resetStatistics() {
        totalCompletedCount.set(0);
        totalUploadedBytes.set(0);
        completedByDate.clear();
        lastCompletedByFileName.clear();
        log.warn("All upload statistics have been reset");
    }

    /**
     * 통계 스냅샷 생성
     *
     * @return 통계 정보 문자열
     */
    public String getStatisticsSnapshot() {
        return String.format(
            "TotalCompleted=%d, TodayCompleted=%d, TotalDays=%d",
            totalCompletedCount.get(),
            getTodayCompletedCount(),
            completedByDate.size()
        );
    }
}
