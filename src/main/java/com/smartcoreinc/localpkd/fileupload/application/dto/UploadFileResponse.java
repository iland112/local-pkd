package com.smartcoreinc.localpkd.fileupload.application.dto;

import java.time.LocalDateTime;

/**
 * Upload File Response - 파일 업로드 응답
 *
 * <p>파일 업로드 결과를 나타내는 Response DTO입니다.
 * Use Case에서 생성되어 Controller로 반환됩니다.</p>
 *
 * <h3>사용 예시 - Use Case에서 반환</h3>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class UploadFileUseCase {
 *
 *     @Transactional
 *     public UploadFileResponse execute(UploadFileCommand command) {
 *         // ... 비즈니스 로직 ...
 *
 *         UploadedFile saved = repository.save(uploadedFile);
 *
 *         return new UploadFileResponse(
 *             saved.getId().getId().toString(),
 *             saved.getFileNameValue(),
 *             saved.getFileHashValue(),
 *             saved.getFileSizeBytes(),
 *             saved.getFileSizeDisplay(),
 *             saved.getUploadedAt(),
 *             false  // not duplicate
 *         );
 *     }
 * }
 * }</pre>
 *
 * <h3>사용 예시 - Controller에서 JSON 응답</h3>
 * <pre>{@code
 * {
 *   "uploadId": "550e8400-e29b-41d4-a716-446655440000",
 *   "fileName": "icaopkd-002-complete-009410.ldif",
 *   "fileHash": "a1b2c3d4e5f67890...",
 *   "fileSizeBytes": 78643200,
 *   "fileSizeDisplay": "75.0 MB",
 *   "uploadedAt": "2025-10-18T20:15:30",
 *   "isDuplicate": false
 * }
 * }</pre>
 *
 * @param uploadId 업로드 ID (UUID 문자열)
 * @param fileName 파일명
 * @param fileHash 파일 해시 (SHA-256)
 * @param fileSizeBytes 파일 크기 (바이트)
 * @param fileSizeDisplay 파일 크기 (사용자 친화적 표현, 예: "75.0 MB")
 * @param uploadedAt 업로드 일시
 * @param isDuplicate 중복 파일 여부
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 */
public record UploadFileResponse(
        String uploadId,
        String fileName,
        String fileHash,
        long fileSizeBytes,
        String fileSizeDisplay,
        LocalDateTime uploadedAt,
        boolean isDuplicate
) {
}
