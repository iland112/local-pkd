package com.smartcoreinc.localpkd.fileupload.application.dto;

/**
 * Check Duplicate Command - 중복 파일 검사 커맨드
 *
 * <p>파일 중복 검사 요청을 나타내는 Command DTO입니다.
 * 파일 업로드 전에 실행되어 중복 파일 여부를 확인합니다.</p>
 *
 * <h3>사용 예시 - Controller에서 생성</h3>
 * <pre>{@code
 * @RestController
 * @RequiredArgsConstructor
 * public class DuplicateCheckController {
 *
 *     private final CheckDuplicateFileUseCase checkDuplicateUseCase;
 *
 *     @PostMapping("/api/files/check-duplicate")
 *     public ResponseEntity<DuplicateCheckResponse> checkDuplicate(
 *         @RequestBody CheckDuplicateRequest request
 *     ) {
 *         // Command 생성
 *         CheckDuplicateCommand command = new CheckDuplicateCommand(
 *             request.fileName(),
 *             request.fileHash(),
 *             request.fileSizeBytes()
 *         );
 *
 *         // Use Case 실행
 *         DuplicateCheckResponse response = checkDuplicateUseCase.execute(command);
 *
 *         return ResponseEntity.ok(response);
 *     }
 * }
 * }</pre>
 *
 * @param fileName 파일명
 * @param fileHash 파일 해시 (SHA-256)
 * @param fileSizeBytes 파일 크기 (바이트)
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 */
public record CheckDuplicateCommand(
        String fileName,
        String fileHash,
        long fileSizeBytes
) {
}
