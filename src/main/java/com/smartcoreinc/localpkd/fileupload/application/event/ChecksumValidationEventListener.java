package com.smartcoreinc.localpkd.fileupload.application.event;

import com.smartcoreinc.localpkd.fileupload.domain.event.ChecksumValidationFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Checksum Validation Event Listener
 *
 * <p>체크섬 검증 관련 Domain Event를 처리합니다.</p>
 *
 * <h3>처리하는 이벤트</h3>
 * <ul>
 *   <li>{@link ChecksumValidationFailedEvent} - 체크섬 검증 실패</li>
 * </ul>
 *
 * <h3>처리 전략</h3>
 * <ul>
 *   <li><b>동기 처리</b>: 즉시 에러 로깅, 메트릭 업데이트</li>
 *   <li><b>비동기 처리</b>: 관리자 알림, 에러 리포트 생성</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChecksumValidationEventListener {

    // TODO: Future enhancements
    // private final NotificationService notificationService;
    // private final MeterRegistry meterRegistry;
    // private final ErrorReportService errorReportService;

    /**
     * 체크섬 검증 실패 이벤트 처리 (동기)
     *
     * <p>체크섬 불일치가 발생했을 때 즉시 에러를 로깅하고 메트릭을 업데이트합니다.</p>
     *
     * <h4>처리 내용</h4>
     * <ul>
     *   <li>에러 로깅 (ERROR 레벨)</li>
     *   <li>체크섬 불일치 상세 정보 기록</li>
     *   <li>메트릭 카운터 증가 (Prometheus)</li>
     * </ul>
     *
     * @param event 체크섬 검증 실패 이벤트
     */
    @EventListener
    public void handleChecksumValidationFailed(ChecksumValidationFailedEvent event) {
        log.error("=== [Event] ChecksumValidationFailed ===");
        log.error("Upload ID: {}", event.uploadId().getId());
        log.error("File name: {}", event.fileName());
        log.error("Expected checksum : {}", event.expectedChecksum());
        log.error("Calculated checksum: {}", event.calculatedChecksum());
        log.error("Event occurred at: {}", event.occurredOn());

        // 체크섬 불일치 요약
        log.error("Summary: {}", event.getSummary());

        // TODO: Prometheus 메트릭 증가
        // meterRegistry.counter("file.upload.checksum.validation.failed",
        //     "file_name", event.fileName()
        // ).increment();

        // 보안 경고: 체크섬 불일치는 파일 변조 가능성을 의미
        if (isPotentialTampering(event)) {
            log.error("⚠️  SECURITY ALERT: Potential file tampering detected!");
            log.error("⚠️  File: {}, UploadId: {}",
                event.fileName(), event.uploadId().getId());
        }
    }

    /**
     * 체크섬 검증 실패 이벤트 처리 (비동기, 트랜잭션 커밋 후)
     *
     * <p>트랜잭션 커밋 후 비동기적으로 알림 및 리포트를 생성합니다.</p>
     *
     * <h4>처리 내용</h4>
     * <ul>
     *   <li>관리자 이메일 알림</li>
     *   <li>에러 리포트 생성 및 저장</li>
     *   <li>모니터링 시스템에 Alert 전송</li>
     * </ul>
     *
     * @param event 체크섬 검증 실패 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleChecksumValidationFailedAsync(ChecksumValidationFailedEvent event) {
        log.info("=== [Event-Async] ChecksumValidationFailed (Sending alerts) ===");
        log.info("Upload ID: {}", event.uploadId().getId());

        try {
            // TODO: 관리자 알림 발송
            // notificationService.sendChecksumMismatchAlert(
            //     event.fileName(),
            //     event.expectedChecksum(),
            //     event.calculatedChecksum()
            // );

            // TODO: 에러 리포트 생성
            // ErrorReport report = errorReportService.createChecksumValidationReport(event);
            // errorReportService.save(report);

            log.info("Checksum validation failure alert would be sent here");

            // 상세 에러 정보 로깅 (비동기)
            logDetailedChecksumError(event);

        } catch (Exception e) {
            log.error("Failed to send checksum validation alert for uploadId: {}",
                event.uploadId().getId(), e);
        }
    }

    /**
     * 파일 변조 가능성 확인
     *
     * <p>체크섬 불일치가 단순 오류인지 변조 가능성이 있는지 판단합니다.</p>
     *
     * @param event 체크섬 검증 실패 이벤트
     * @return 변조 가능성이 있으면 true
     */
    private boolean isPotentialTampering(ChecksumValidationFailedEvent event) {
        // 간단한 휴리스틱: 체크섬이 완전히 다르면 변조 가능성
        String expected = event.expectedChecksum();
        String calculated = event.calculatedChecksum();

        if (expected == null || calculated == null) {
            return false;
        }

        // 앞 8자리가 완전히 다르면 변조 가능성
        if (expected.length() >= 8 && calculated.length() >= 8) {
            String expectedPrefix = expected.substring(0, 8);
            String calculatedPrefix = calculated.substring(0, 8);
            return !expectedPrefix.equals(calculatedPrefix);
        }

        return false;
    }

    /**
     * 상세 에러 정보 로깅
     *
     * @param event 체크섬 검증 실패 이벤트
     */
    private void logDetailedChecksumError(ChecksumValidationFailedEvent event) {
        log.info("=== Checksum Validation Error Details ===");
        log.info("Event ID: {}", event.eventId());
        log.info("Upload ID: {}", event.uploadId().getId());
        log.info("File name: {}", event.fileName());
        log.info("Occurred on: {}", event.occurredOn());
        log.info("Expected checksum (full): {}", event.expectedChecksum());
        log.info("Calculated checksum (full): {}", event.calculatedChecksum());

        // 체크섬 비교 분석
        analyzeChecksumDifference(event.expectedChecksum(), event.calculatedChecksum());
    }

    /**
     * 체크섬 차이 분석
     *
     * @param expected 예상 체크섬
     * @param calculated 계산된 체크섬
     */
    private void analyzeChecksumDifference(String expected, String calculated) {
        if (expected == null || calculated == null) {
            log.info("Checksum analysis: One or both checksums are null");
            return;
        }

        if (expected.equals(calculated)) {
            log.info("Checksum analysis: Checksums are identical (unexpected)");
            return;
        }

        int minLength = Math.min(expected.length(), calculated.length());
        int differenceCount = 0;
        int firstDifferenceIndex = -1;

        for (int i = 0; i < minLength; i++) {
            if (expected.charAt(i) != calculated.charAt(i)) {
                differenceCount++;
                if (firstDifferenceIndex == -1) {
                    firstDifferenceIndex = i;
                }
            }
        }

        log.info("Checksum analysis:");
        log.info("  - Length: expected={}, calculated={}", expected.length(), calculated.length());
        log.info("  - Different characters: {} out of {}", differenceCount, minLength);
        log.info("  - First difference at index: {}", firstDifferenceIndex);
        log.info("  - Difference percentage: {:.2f}%",
            (differenceCount * 100.0) / minLength);
    }
}
