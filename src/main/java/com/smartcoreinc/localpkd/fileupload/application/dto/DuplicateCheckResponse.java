package com.smartcoreinc.localpkd.fileupload.application.dto;

import java.time.LocalDateTime;

/**
 * Duplicate Check Response - 중복 파일 검사 응답
 *
 * <p>파일 중복 검사 결과를 나타내는 Response DTO입니다.
 * Use Case에서 생성되어 Controller로 반환됩니다.</p>
 *
 * <h3>응답 타입</h3>
 * <ul>
 *   <li><b>NO_DUPLICATE</b>: 중복 파일 없음 (업로드 가능)</li>
 *   <li><b>EXACT_DUPLICATE</b>: 정확히 동일한 파일 존재 (업로드 불가)</li>
 * </ul>
 *
 * <h3>사용 예시 - Use Case에서 생성 (중복 없음)</h3>
 * <pre>{@code
 * public DuplicateCheckResponse execute(CheckDuplicateCommand command) {
 *     FileHash fileHash = FileHash.of(command.fileHash());
 *     Optional<UploadedFile> existing = repository.findByFileHash(fileHash);
 *
 *     if (existing.isEmpty()) {
 *         return DuplicateCheckResponse.noDuplicate();
 *     }
 *     // ...
 * }
 * }</pre>
 *
 * <h3>사용 예시 - Use Case에서 생성 (중복 발견)</h3>
 * <pre>{@code
 * if (existing.isPresent()) {
 *     UploadedFile existingFile = existing.get();
 *     return DuplicateCheckResponse.exactDuplicate(
 *         existingFile.getId().getId().toString(),
 *         existingFile.getFileNameValue(),
 *         existingFile.getUploadedAt()
 *     );
 * }
 * }</pre>
 *
 * <h3>사용 예시 - JSON 응답 (중복 없음)</h3>
 * <pre>{@code
 * {
 *   "isDuplicate": false,
 *   "warningType": null,
 *   "message": "파일이 중복되지 않았습니다.",
 *   "existingFileId": null,
 *   "existingFileName": null,
 *   "existingUploadDate": null
 * }
 * }</pre>
 *
 * <h3>사용 예시 - JSON 응답 (중복 발견)</h3>
 * <pre>{@code
 * {
 *   "isDuplicate": true,
 *   "warningType": "EXACT_DUPLICATE",
 *   "message": "이 파일은 이전에 이미 업로드되었습니다.",
 *   "existingFileId": "550e8400-e29b-41d4-a716-446655440000",
 *   "existingFileName": "icaopkd-002-complete-009410.ldif",
 *   "existingUploadDate": "2025-10-17T10:30:00"
 * }
 * }</pre>
 *
 * @param isDuplicate 중복 여부
 * @param warningType 경고 타입 (EXACT_DUPLICATE, null)
 * @param message 사용자 메시지
 * @param existingFileId 기존 파일 ID (중복인 경우)
 * @param existingFileName 기존 파일명 (중복인 경우)
 * @param existingUploadDate 기존 파일 업로드 일시 (중복인 경우)
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 */
public record DuplicateCheckResponse(
        boolean isDuplicate,
        String warningType,
        String message,
        String existingFileId,
        String existingFileName,
        LocalDateTime existingUploadDate
) {

    /**
     * 중복 없음 응답 생성
     *
     * @return 중복 없음 응답
     */
    public static DuplicateCheckResponse noDuplicate() {
        return new DuplicateCheckResponse(
                false,
                null,
                "파일이 중복되지 않았습니다.",
                null,
                null,
                null
        );
    }

    /**
     * 정확한 중복 파일 응답 생성
     *
     * @param existingFileId 기존 파일 ID
     * @param existingFileName 기존 파일명
     * @param existingUploadDate 기존 파일 업로드 일시
     * @return 중복 파일 응답
     */
    public static DuplicateCheckResponse exactDuplicate(
            String existingFileId,
            String existingFileName,
            LocalDateTime existingUploadDate
    ) {
        return new DuplicateCheckResponse(
                true,
                "EXACT_DUPLICATE",
                "이 파일은 이전에 이미 업로드되었습니다.",
                existingFileId,
                existingFileName,
                existingUploadDate
        );
    }
}
