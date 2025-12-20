package com.smartcoreinc.localpkd.fileupload.application.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 파일 업로드 Response
 *
 * <p>파일 업로드 결과를 나타내는 Response 객체입니다.</p>
 *
 * <h3>포함 정보</h3>
 * <ul>
 *   <li>업로드 ID (UUID)</li>
 *   <li>파일명</li>
 *   <li>파일 크기 (bytes & display)</li>
 *   <li>파일 포맷</li>
 *   <li>Collection 번호</li>
 *   <li>버전</li>
 *   <li>업로드 일시</li>
 *   <li>상태</li>
 *   <li>성공 여부</li>
 *   <li>에러 메시지 (optional)</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * UploadFileResponse response = UploadFileResponse.builder()
 *     .uploadId(UUID.randomUUID())
 *     .fileName("icaopkd-002-complete-009410.ldif")
 *     .fileSize(78643200L)
 *     .fileSizeDisplay("75.0 MB")
 *     .fileFormat("EMRTD_COMPLETE_LDIF")
 *     .collectionNumber("002")
 *     .version("009410")
 *     .uploadedAt(LocalDateTime.now())
 *     .status("RECEIVED")
 *     .success(true)
 *     .build();
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Builder
public record UploadFileResponse(
        UUID uploadId,
        String fileName,
        Long fileSize,
        String fileSizeDisplay,
        String fileFormat,          // FileFormat.Type name
        String collectionNumber,
        String version,
        LocalDateTime uploadedAt,
        String status,              // UploadStatus name
        boolean success,
        String errorMessage         // optional
) {
    /**
     * 성공 응답 생성
     */
    public static UploadFileResponse success(
            UUID uploadId,
            String fileName,
            Long fileSize,
            String fileSizeDisplay,
            String fileFormat,
            String collectionNumber,
            String version,
            LocalDateTime uploadedAt,
            String status
    ) {
        return UploadFileResponse.builder()
                .uploadId(uploadId)
                .fileName(fileName)
                .fileSize(fileSize)
                .fileSizeDisplay(fileSizeDisplay)
                .fileFormat(fileFormat)
                .collectionNumber(collectionNumber)
                .version(version)
                .uploadedAt(uploadedAt)
                .status(status)
                .success(true)
                .errorMessage(null)
                .build();
    }

    /**
     * 실패 응답 생성
     */
    public static UploadFileResponse failure(
            String fileName,
            String errorMessage
    ) {
        return UploadFileResponse.builder()
                .uploadId(null)
                .fileName(fileName)
                .fileSize(null)
                .fileSizeDisplay(null)
                .fileFormat(null)
                .collectionNumber(null)
                .version(null)
                .uploadedAt(LocalDateTime.now())
                .status("FAILED")
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
