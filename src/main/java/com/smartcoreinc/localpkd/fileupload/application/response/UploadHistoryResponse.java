package com.smartcoreinc.localpkd.fileupload.application.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 업로드 이력 Response
 *
 * <p>업로드된 파일의 이력 정보를 나타내는 Response 객체입니다.</p>
 *
 * <h3>포함 정보</h3>
 * <ul>
 *   <li>업로드 ID (UUID)</li>
 *   <li>파일명</li>
 *   <li>파일 크기 (bytes & display)</li>
 *   <li>파일 해시 (SHA-256)</li>
 *   <li>파일 포맷</li>
 *   <li>Collection 번호</li>
 *   <li>버전</li>
 *   <li>업로드 일시</li>
 *   <li>상태</li>
 *   <li>중복 여부</li>
 *   <li>신규 버전 여부</li>
 *   <li>예상 체크섬 (SHA-1, optional)</li>
 *   <li>계산된 체크섬 (SHA-1, optional)</li>
 *   <li>에러 메시지 (optional)</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * UploadHistoryResponse response = UploadHistoryResponse.builder()
 *     .uploadId(UUID.randomUUID())
 *     .fileName("icaopkd-002-complete-009410.ldif")
 *     .fileSize(78643200L)
 *     .fileSizeDisplay("75.0 MB")
 *     .fileHash("a1b2c3d4...")
 *     .fileFormat("EMRTD_COMPLETE_LDIF")
 *     .collectionNumber("002")
 *     .version("009410")
 *     .uploadedAt(LocalDateTime.now())
 *     .status("COMPLETED")
 *     .isDuplicate(false)
 *     .isNewerVersion(false)
 *     .errorMessage(null)
 *     .build();
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Builder
public record UploadHistoryResponse(
        UUID uploadId,
        String fileName,
        Long fileSize,
        String fileSizeDisplay,
        String fileHash,
        String fileFormat,          // FileFormat.Type name
        String collectionNumber,
        String version,
        LocalDateTime uploadedAt,
        String status,              // UploadStatus name
        Boolean isDuplicate,
        Boolean isNewerVersion,
        String expectedChecksum,    // optional - SHA-1 expected checksum
        String calculatedChecksum,  // optional - SHA-1 calculated checksum
        String errorMessage         // optional
) {
    /**
     * UploadedFile Aggregate로부터 생성
     */
    public static UploadHistoryResponse from(
            UUID uploadId,
            String fileName,
            Long fileSize,
            String fileSizeDisplay,
            String fileHash,
            String fileFormat,
            String collectionNumber,
            String version,
            LocalDateTime uploadedAt,
            String status,
            Boolean isDuplicate,
            Boolean isNewerVersion,
            String expectedChecksum,
            String calculatedChecksum,
            String errorMessage
    ) {
        return UploadHistoryResponse.builder()
                .uploadId(uploadId)
                .fileName(fileName)
                .fileSize(fileSize)
                .fileSizeDisplay(fileSizeDisplay)
                .fileHash(fileHash)
                .fileFormat(fileFormat)
                .collectionNumber(collectionNumber)
                .version(version)
                .uploadedAt(uploadedAt)
                .status(status)
                .isDuplicate(isDuplicate)
                .isNewerVersion(isNewerVersion)
                .expectedChecksum(expectedChecksum)
                .calculatedChecksum(calculatedChecksum)
                .errorMessage(errorMessage)
                .build();
    }
}
