package com.smartcoreinc.localpkd.fileupload.application.dto;

/**
 * Upload File Command - 파일 업로드 커맨드
 *
 * <p>파일 업로드 요청을 나타내는 Command DTO입니다.
 * Controller에서 생성되어 Use Case로 전달됩니다.</p>
 *
 * <h3>사용 예시 - Controller에서 생성</h3>
 * <pre>{@code
 * @RestController
 * @RequiredArgsConstructor
 * public class FileUploadController {
 *
 *     private final UploadFileUseCase uploadFileUseCase;
 *
 *     @PostMapping("/api/files/upload")
 *     public ResponseEntity<UploadFileResponse> uploadFile(
 *         @RequestParam("file") MultipartFile file,
 *         @RequestParam(required = false) String expectedChecksum
 *     ) throws IOException {
 *         // 1. 파일 해시 계산
 *         String fileHash = calculateFileHash(file);
 *
 *         // 2. Command 생성
 *         UploadFileCommand command = new UploadFileCommand(
 *             file.getOriginalFilename(),
 *             fileHash,
 *             file.getSize(),
 *             expectedChecksum
 *         );
 *
 *         // 3. Use Case 실행
 *         UploadFileResponse response = uploadFileUseCase.execute(command);
 *
 *         return ResponseEntity.ok(response);
 *     }
 * }
 * }</pre>
 *
 * @param fileName 파일명 (확장자 포함)
 * @param fileHash 파일 해시 (SHA-256, 64자리 16진수)
 * @param fileSizeBytes 파일 크기 (바이트)
 * @param expectedChecksum 예상 체크섬 (선택사항, SHA-1)
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 */
public record UploadFileCommand(
        String fileName,
        String fileHash,
        long fileSizeBytes,
        String expectedChecksum
) {
    /**
     * 기본 생성자 (expectedChecksum 없이)
     *
     * @param fileName 파일명
     * @param fileHash 파일 해시
     * @param fileSizeBytes 파일 크기
     */
    public UploadFileCommand(String fileName, String fileHash, long fileSizeBytes) {
        this(fileName, fileHash, fileSizeBytes, null);
    }
}
