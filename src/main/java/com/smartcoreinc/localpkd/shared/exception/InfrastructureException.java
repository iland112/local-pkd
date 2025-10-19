package com.smartcoreinc.localpkd.shared.exception;

/**
 * Infrastructure Exception - 인프라 계층 예외
 *
 * <p>파일 시스템, 데이터베이스, 외부 API 등 인프라 계층에서 발생하는 예외입니다.</p>
 *
 * <h3>사용 시나리오</h3>
 * <ul>
 *   <li>파일 저장 실패</li>
 *   <li>파일 읽기 실패</li>
 *   <li>네트워크 오류</li>
 *   <li>외부 서비스 호출 실패</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
public class InfrastructureException extends RuntimeException {

    private final String errorCode;

    public InfrastructureException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public InfrastructureException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return String.format("InfrastructureException[errorCode=%s, message=%s]",
                errorCode, getMessage());
    }
}
