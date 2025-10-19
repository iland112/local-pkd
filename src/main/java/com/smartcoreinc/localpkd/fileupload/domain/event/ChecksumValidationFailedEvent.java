package com.smartcoreinc.localpkd.fileupload.domain.event;

import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.shared.domain.DomainEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Checksum Validation Failed Event - 체크섬 검증 실패 도메인 이벤트
 *
 * <p>예상 체크섬과 계산된 체크섬이 일치하지 않을 때 발행되는 이벤트입니다.
 * ICAO PKD 표준에서는 SHA-1 체크섬을 사용하여 파일 무결성을 검증합니다.</p>
 *
 * <h3>이벤트 발행 시점</h3>
 * <ul>
 *   <li>파일 업로드 후 체크섬 검증 수행 시</li>
 *   <li>예상 체크섬(사용자 제공)과 계산된 체크섬(서버 계산)이 불일치할 때</li>
 * </ul>
 *
 * <h3>이벤트 핸들러 사용 예시</h3>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class ChecksumEventHandler {
 *
 *     @EventListener
 *     public void handleChecksumValidationFailed(ChecksumValidationFailedEvent event) {
 *         log.error("Checksum validation failed for file: {} (uploadId: {})",
 *                  event.fileName(), event.uploadId());
 *         log.error("Expected: {}, Calculated: {}",
 *                  event.expectedChecksum(), event.calculatedChecksum());
 *
 *         // 알림 발송, 통계 수집 등
 *     }
 *
 *     @Async
 *     @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 *     public void handleChecksumValidationFailedAsync(ChecksumValidationFailedEvent event) {
 *         // 비동기 처리: 관리자 이메일 발송, 로그 저장 등
 *     }
 * }
 * }</pre>
 *
 * @param eventId 이벤트 ID
 * @param occurredOn 이벤트 발생 일시
 * @param uploadId 업로드 ID
 * @param fileName 파일명
 * @param expectedChecksum 예상 체크섬 (SHA-1, 40자)
 * @param calculatedChecksum 계산된 체크섬 (SHA-1, 40자)
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
public record ChecksumValidationFailedEvent(
        UUID eventId,
        LocalDateTime occurredOn,
        UploadId uploadId,
        String fileName,
        String expectedChecksum,
        String calculatedChecksum
) implements DomainEvent {

    /**
     * Compact 생성자 (DomainEvent 필드 자동 생성)
     *
     * @param uploadId 업로드 ID
     * @param fileName 파일명
     * @param expectedChecksum 예상 체크섬
     * @param calculatedChecksum 계산된 체크섬
     */
    public ChecksumValidationFailedEvent(
            UploadId uploadId,
            String fileName,
            String expectedChecksum,
            String calculatedChecksum
    ) {
        this(
                UUID.randomUUID(),
                LocalDateTime.now(),
                uploadId,
                fileName,
                expectedChecksum,
                calculatedChecksum
        );
    }

    @Override
    public String eventType() {
        return "ChecksumValidationFailed";
    }

    /**
     * 이벤트 요약 메시지
     *
     * @return 요약 문자열
     */
    public String getSummary() {
        return String.format(
                "Checksum mismatch for '%s': expected=%s..., calculated=%s...",
                fileName,
                expectedChecksum.substring(0, 8),
                calculatedChecksum.substring(0, 8)
        );
    }
}
