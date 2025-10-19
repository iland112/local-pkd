package com.smartcoreinc.localpkd.shared.exception;

import lombok.Getter;

/**
 * Domain Exception - 도메인 로직 위반 예외
 *
 * <p>DDD에서 비즈니스 규칙 위반 시 발생하는 기본 예외 클래스입니다.
 * 모든 도메인 예외는 이 클래스를 상속받아야 합니다.</p>
 *
 * <h3>특징</h3>
 * <ul>
 *   <li>RuntimeException 상속 (Unchecked Exception)</li>
 *   <li>에러 코드 지원 (국제화 및 클라이언트 처리 용이)</li>
 *   <li>명확한 의미 전달 (예외 타입만으로 문제 파악)</li>
 * </ul>
 *
 * <h3>설계 원칙</h3>
 * <ol>
 *   <li><b>명확한 예외명</b>: 예외 타입만으로 문제 파악 가능</li>
 *   <li><b>의미 있는 메시지</b>: 사용자/개발자가 이해하기 쉬운 메시지</li>
 *   <li><b>에러 코드</b>: 국제화 및 API 응답에 활용</li>
 * </ol>
 *
 * <h3>사용 예시 - 구체적인 예외 클래스</h3>
 * <pre>{@code
 * // 1. 파일 크기 초과
 * public class FileSizeLimitExceededException extends DomainException {
 *     public FileSizeLimitExceededException(long actualSize, long maxSize) {
 *         super(
 *             "FILE_SIZE_LIMIT_EXCEEDED",
 *             String.format("File size %d bytes exceeds limit %d bytes",
 *                 actualSize, maxSize)
 *         );
 *     }
 * }
 *
 * // 2. 잘못된 파일명
 * public class InvalidFileNameException extends DomainException {
 *     public InvalidFileNameException(String reason) {
 *         super("INVALID_FILE_NAME", "Invalid file name: " + reason);
 *     }
 * }
 *
 * // 3. 중복 파일
 * public class DuplicateFileException extends DomainException {
 *     public DuplicateFileException(String fileName) {
 *         super(
 *             "DUPLICATE_FILE",
 *             "File already exists: " + fileName
 *         );
 *     }
 * }
 * }</pre>
 *
 * <h3>사용 예시 - Domain Model에서 사용</h3>
 * <pre>{@code
 * @Entity
 * public class UploadedFile extends AggregateRoot<UploadId> {
 *
 *     // Domain Method - 비즈니스 규칙 검증
 *     public void validateSize(long maxSize) {
 *         if (this.size.getBytes() > maxSize) {
 *             // 도메인 예외 발생
 *             throw new FileSizeLimitExceededException(
 *                 this.size.getBytes(),
 *                 maxSize
 *             );
 *         }
 *     }
 *
 *     public void validateFileName() {
 *         if (this.fileName.value().isBlank()) {
 *             throw new InvalidFileNameException("Filename cannot be blank");
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>사용 예시 - Exception Handler</h3>
 * <pre>{@code
 * @RestControllerAdvice
 * @Slf4j
 * public class DomainExceptionHandler {
 *
 *     @ExceptionHandler(DomainException.class)
 *     public ResponseEntity<ErrorResponse> handleDomainException(
 *             DomainException ex) {
 *
 *         log.warn("Domain exception: {} - {}",
 *             ex.getErrorCode(), ex.getMessage());
 *
 *         ErrorResponse response = ErrorResponse.builder()
 *             .errorCode(ex.getErrorCode())
 *             .message(ex.getMessage())
 *             .timestamp(LocalDateTime.now())
 *             .build();
 *
 *         return ResponseEntity
 *             .status(HttpStatus.BAD_REQUEST)
 *             .body(response);
 *     }
 *
 *     @ExceptionHandler(FileSizeLimitExceededException.class)
 *     public ResponseEntity<ErrorResponse> handleFileSizeLimit(
 *             FileSizeLimitExceededException ex) {
 *
 *         // 특정 예외는 다른 HTTP 상태 코드 반환 가능
 *         return ResponseEntity
 *             .status(HttpStatus.PAYLOAD_TOO_LARGE)
 *             .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
 *     }
 * }
 * }</pre>
 *
 * <h3>에러 코드 명명 규칙</h3>
 * <ul>
 *   <li>대문자 스네이크 케이스: FILE_SIZE_LIMIT_EXCEEDED</li>
 *   <li>컨텍스트 포함: UPLOAD_FILE_NOT_FOUND</li>
 *   <li>의미 명확: INVALID_FILE_NAME (X: BAD_NAME)</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 */
@Getter
public class DomainException extends RuntimeException {

    /**
     * 에러 코드
     *
     * <p>국제화(i18n) 메시지 키 또는 API 응답 에러 코드로 사용됩니다.</p>
     *
     * <h4>예시</h4>
     * <ul>
     *   <li>FILE_SIZE_LIMIT_EXCEEDED</li>
     *   <li>INVALID_FILE_NAME</li>
     *   <li>DUPLICATE_FILE</li>
     *   <li>TRUST_CHAIN_VALIDATION_FAILED</li>
     * </ul>
     */
    private final String errorCode;

    /**
     * 도메인 예외 생성자
     *
     * @param errorCode 에러 코드 (대문자 스네이크 케이스)
     * @param message 에러 메시지 (사용자 친화적)
     */
    public DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 도메인 예외 생성자 (원인 포함)
     *
     * @param errorCode 에러 코드
     * @param message 에러 메시지
     * @param cause 원인 예외
     */
    public DomainException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 문자열 표현
     *
     * @return 에러 코드와 메시지
     */
    @Override
    public String toString() {
        return String.format("[%s] %s", errorCode, getMessage());
    }
}
