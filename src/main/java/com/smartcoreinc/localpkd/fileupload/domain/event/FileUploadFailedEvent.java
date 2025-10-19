package com.smartcoreinc.localpkd.fileupload.domain.event;

import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.shared.domain.DomainEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * File Upload Failed Event - 파일 업로드 실패 도메인 이벤트
 *
 * <p>파일 업로드 과정 중 오류가 발생했을 때 발행되는 이벤트입니다.</p>
 *
 * <h3>이벤트 발행 시점</h3>
 * <ul>
 *   <li>파일 저장 실패 시</li>
 *   <li>파일 파싱 실패 시</li>
 *   <li>LDAP 업로드 실패 시</li>
 *   <li>기타 처리 과정 중 예외 발생 시</li>
 * </ul>
 *
 * @param eventId 이벤트 ID
 * @param occurredOn 이벤트 발생 일시
 * @param uploadId 업로드 ID
 * @param fileName 파일명
 * @param errorMessage 오류 메시지
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
public record FileUploadFailedEvent(
        UUID eventId,
        LocalDateTime occurredOn,
        UploadId uploadId,
        String fileName,
        String errorMessage
) implements DomainEvent {

    /**
     * Compact 생성자
     *
     * @param uploadId 업로드 ID
     * @param fileName 파일명
     * @param errorMessage 오류 메시지
     */
    public FileUploadFailedEvent(
            UploadId uploadId,
            String fileName,
            String errorMessage
    ) {
        this(
                UUID.randomUUID(),
                LocalDateTime.now(),
                uploadId,
                fileName,
                errorMessage
        );
    }

    @Override
    public String eventType() {
        return "FileUploadFailed";
    }
}
