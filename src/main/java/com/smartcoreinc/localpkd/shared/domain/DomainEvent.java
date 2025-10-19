package com.smartcoreinc.localpkd.shared.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain Event 인터페이스
 *
 * <p>도메인에서 발생하는 모든 이벤트의 기본 인터페이스입니다.
 * DDD의 Event Storming에서 식별된 도메인 이벤트를 구현할 때 사용합니다.</p>
 *
 * <h3>특징</h3>
 * <ul>
 *   <li>불변성: 이벤트는 한번 발생하면 변경되지 않음</li>
 *   <li>시간 기록: 이벤트 발생 시각 기록</li>
 *   <li>고유성: UUID로 각 이벤트 식별</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * public record FileUploadedEvent(
 *     UUID eventId,
 *     LocalDateTime occurredOn,
 *     UploadId uploadId,
 *     FileName fileName
 * ) implements DomainEvent {
 *
 *     public FileUploadedEvent(UploadId uploadId, FileName fileName) {
 *         this(UUID.randomUUID(), LocalDateTime.now(), uploadId, fileName);
 *     }
 *
 *     @Override
 *     public String eventType() {
 *         return "FileUploaded";
 *     }
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 */
public interface DomainEvent {

    /**
     * 이벤트 고유 식별자
     *
     * @return 이벤트 UUID
     */
    UUID eventId();

    /**
     * 이벤트 발생 시각
     *
     * @return 이벤트 발생 시각
     */
    LocalDateTime occurredOn();

    /**
     * 이벤트 타입
     *
     * <p>이벤트를 구분하기 위한 문자열 타입입니다.
     * 일반적으로 클래스명을 사용합니다.</p>
     *
     * @return 이벤트 타입 문자열 (예: "FileUploaded", "FileParsingCompleted")
     */
    String eventType();
}
