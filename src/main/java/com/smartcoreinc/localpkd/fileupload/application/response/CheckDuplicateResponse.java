package com.smartcoreinc.localpkd.fileupload.application.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 중복 파일 검사 Response
 *
 * <p>중복 파일 검사 결과를 나타내는 Response 객체입니다.</p>
 *
 * <h3>경고 유형 (warningType)</h3>
 * <ul>
 *   <li>EXACT_DUPLICATE: 정확히 동일한 파일 (해시 일치)</li>
 *   <li>SAME_FILE_NEWER_VERSION: 동일 파일의 신규 버전</li>
 *   <li>CHECKSUM_MISMATCH: 체크섬 불일치</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // 중복 없음
 * CheckDuplicateResponse response1 = CheckDuplicateResponse.noDuplicate();
 *
 * // 정확한 중복
 * CheckDuplicateResponse response2 = CheckDuplicateResponse.exactDuplicate(
 *     existingUploadId,
 *     "icaopkd-002-complete-009410.ldif",
 *     LocalDateTime.now(),
 *     "009410",
 *     "COMPLETED"
 * );
 *
 * // 신규 버전
 * CheckDuplicateResponse response3 = CheckDuplicateResponse.newerVersion(
 *     existingUploadId,
 *     "icaopkd-002-complete-009410.ldif",
 *     LocalDateTime.now(),
 *     "009410",
 *     "009411"  // new version
 * );
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Builder
public record CheckDuplicateResponse(
        boolean isDuplicate,
        String message,
        String warningType,         // EXACT_DUPLICATE, SAME_FILE_NEWER_VERSION, CHECKSUM_MISMATCH
        UUID existingFileId,        // optional
        String existingFileName,    // optional
        LocalDateTime existingUploadDate,  // optional
        String existingVersion,     // optional
        String existingStatus,      // optional
        boolean canForceUpload
) {
    /**
     * 중복 없음
     */
    public static CheckDuplicateResponse noDuplicate() {
        return CheckDuplicateResponse.builder()
                .isDuplicate(false)
                .message("파일이 중복되지 않았습니다.")
                .warningType(null)
                .existingFileId(null)
                .existingFileName(null)
                .existingUploadDate(null)
                .existingVersion(null)
                .existingStatus(null)
                .canForceUpload(false)
                .build();
    }

    /**
     * 정확한 중복 (해시 일치)
     */
    public static CheckDuplicateResponse exactDuplicate(
            UUID existingFileId,
            String existingFileName,
            LocalDateTime existingUploadDate,
            String existingVersion,
            String existingStatus
    ) {
        return CheckDuplicateResponse.builder()
                .isDuplicate(true)
                .message("이 파일은 이전에 이미 업로드되었습니다.")
                .warningType("EXACT_DUPLICATE")
                .existingFileId(existingFileId)
                .existingFileName(existingFileName)
                .existingUploadDate(existingUploadDate)
                .existingVersion(existingVersion)
                .existingStatus(existingStatus)
                .canForceUpload(false)
                .build();
    }

    /**
     * 동일 파일의 신규 버전
     */
    public static CheckDuplicateResponse newerVersion(
            UUID existingFileId,
            String existingFileName,
            LocalDateTime existingUploadDate,
            String existingVersion,
            String newVersion
    ) {
        return CheckDuplicateResponse.builder()
                .isDuplicate(false)
                .message(String.format(
                        "동일한 파일의 신규 버전입니다. (기존: %s → 신규: %s)",
                        existingVersion, newVersion
                ))
                .warningType("SAME_FILE_NEWER_VERSION")
                .existingFileId(existingFileId)
                .existingFileName(existingFileName)
                .existingUploadDate(existingUploadDate)
                .existingVersion(existingVersion)
                .existingStatus(null)
                .canForceUpload(true)
                .build();
    }

    /**
     * 체크섬 불일치
     */
    public static CheckDuplicateResponse checksumMismatch(
            String expectedChecksum,
            String calculatedChecksum
    ) {
        return CheckDuplicateResponse.builder()
                .isDuplicate(false)
                .message(String.format(
                        "체크섬이 일치하지 않습니다. (예상: %s, 계산: %s)",
                        expectedChecksum, calculatedChecksum
                ))
                .warningType("CHECKSUM_MISMATCH")
                .existingFileId(null)
                .existingFileName(null)
                .existingUploadDate(null)
                .existingVersion(null)
                .existingStatus(null)
                .canForceUpload(true)
                .build();
    }
}
