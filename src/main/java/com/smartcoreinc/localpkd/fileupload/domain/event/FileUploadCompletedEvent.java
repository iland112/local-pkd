package com.smartcoreinc.localpkd.fileupload.domain.event;

import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.shared.domain.DomainEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * File Upload Completed Event - 파일 업로드 완료 도메인 이벤트
 *
 * <p>파일 업로드의 모든 단계가 성공적으로 완료되었을 때 발행되는 이벤트입니다.</p>
 *
 * <h3>이벤트 발행 시점</h3>
 * <ul>
 *   <li>파일 저장, 파싱, 검증, LDAP 업로드가 모두 완료되었을 때</li>
 *   <li>UploadStatus가 COMPLETED로 변경될 때</li>
 * </ul>
 *
 * @param eventId 이벤트 ID
 * @param occurredOn 이벤트 발생 일시
 * @param uploadId 업로드 ID
 * @param fileName 파일명
 * @param fileHash 파일 해시 (SHA-256)
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
public record FileUploadCompletedEvent(
        UUID eventId,
        LocalDateTime occurredOn,
        UploadId uploadId,
        String fileName,
        String fileHash
) implements DomainEvent {

    /**
     * Compact 생성자
     *
     * @param uploadId 업로드 ID
     * @param fileName 파일명
     * @param fileHash 파일 해시
     */
    public FileUploadCompletedEvent(
            UploadId uploadId,
            String fileName,
            String fileHash
    ) {
        this(
                UUID.randomUUID(),
                LocalDateTime.now(),
                uploadId,
                fileName,
                fileHash
        );
    }

    @Override
    public String eventType() {
        return "FileUploadCompleted";
    }
}
