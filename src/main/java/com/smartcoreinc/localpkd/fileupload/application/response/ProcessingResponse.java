package com.smartcoreinc.localpkd.fileupload.application.response;

import java.util.UUID;

/**
 * Processing Response - 파일 처리 단계 실행 응답 DTO
 *
 * <p>파일 처리 단계(파싱, 검증, LDAP 업로드) 실행 요청에 대한 응답입니다.
 * 요청이 성공적으로 접수되었는지 여부와 처리 상태를 포함합니다.</p>
 *
 * <h3>사용 시나리오</h3>
 *
 * <p><strong>AUTO 모드</strong>: 파일 업로드 후 자동으로 처리되므로 이 응답이 반환되지 않습니다.</p>
 *
 * <p><strong>MANUAL 모드</strong>: 각 단계마다 사용자가 버튼을 클릭하면 다음과 같이 처리됩니다:</p>
 *
 * <pre>{@code
 * POST /api/processing/parse/{uploadId}
 * {
 *   "uploadId": "550e8400-e29b-41d4-a716-446655440000",
 *   "step": "PARSING",
 *   "status": "IN_PROGRESS",
 *   "message": "파일 파싱을 시작했습니다.",
 *   "nextStep": "VALIDATION",
 *   "success": true,
 *   "errorMessage": null
 * }
 * }</pre>
 *
 * <h3>12단계 처리 프로세스</h3>
 * <ol>
 *   <li><strong>UPLOAD_COMPLETED</strong> (5%): 파일 업로드 완료</li>
 *   <li><strong>PARSING_STARTED</strong> (10%): 파일 파싱 시작</li>
 *   <li><strong>PARSING_IN_PROGRESS</strong> (20-50%): 파일 파싱 중</li>
 *   <li><strong>PARSING_COMPLETED</strong> (60%): 파일 파싱 완료</li>
 *   <li><strong>VALIDATION_STARTED</strong> (65%): 인증서 검증 시작</li>
 *   <li><strong>VALIDATION_IN_PROGRESS</strong> (65-85%): 인증서 검증 중</li>
 *   <li><strong>VALIDATION_COMPLETED</strong> (85%): 인증서 검증 완료</li>
 *   <li><strong>LDAP_SAVING_STARTED</strong> (90%): LDAP 서버 저장 시작</li>
 *   <li><strong>LDAP_SAVING_IN_PROGRESS</strong> (90-100%): LDAP 서버 저장 중</li>
 *   <li><strong>LDAP_SAVING_COMPLETED</strong> (100%): LDAP 서버 저장 완료</li>
 *   <li><strong>COMPLETED</strong> (100%): 처리 완료</li>
 *   <li><strong>FAILED</strong> (0%): 처리 실패</li>
 * </ol>
 *
 * @param uploadId 업로드 ID
 * @param step 현재 처리 단계 (예: "PARSING", "VALIDATION", "LDAP_SAVING")
 * @param status 처리 상태 (예: "IN_PROGRESS", "COMPLETED", "FAILED")
 * @param message 처리 상태 메시지 (한국어)
 * @param nextStep 다음 단계 (예: "VALIDATION", "LDAP_SAVING")
 * @param success 성공 여부
 * @param errorMessage 오류 메시지 (실패 시)
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
public record ProcessingResponse(
        UUID uploadId,
        String step,
        String status,
        String message,
        String nextStep,
        boolean success,
        String errorMessage
) {

    /**
     * 파싱 시작 성공 응답
     *
     * @param uploadId 업로드 ID
     * @return ProcessingResponse
     */
    public static ProcessingResponse parsingStarted(UUID uploadId) {
        return new ProcessingResponse(
            uploadId,
            "PARSING",
            "IN_PROGRESS",
            "파일 파싱을 시작했습니다.",
            "VALIDATION",
            true,
            null
        );
    }

    /**
     * 파싱 완료 응답
     *
     * @param uploadId 업로드 ID
     * @return ProcessingResponse
     */
    public static ProcessingResponse parsingCompleted(UUID uploadId) {
        return new ProcessingResponse(
            uploadId,
            "PARSING",
            "COMPLETED",
            "파일 파싱이 완료되었습니다.",
            "VALIDATION",
            true,
            null
        );
    }

    /**
     * 검증 시작 성공 응답
     *
     * @param uploadId 업로드 ID
     * @return ProcessingResponse
     */
    public static ProcessingResponse validationStarted(UUID uploadId) {
        return new ProcessingResponse(
            uploadId,
            "VALIDATION",
            "IN_PROGRESS",
            "인증서 검증을 시작했습니다.",
            "LDAP_SAVING",
            true,
            null
        );
    }

    /**
     * 검증 완료 응답
     *
     * @param uploadId 업로드 ID
     * @return ProcessingResponse
     */
    public static ProcessingResponse validationCompleted(UUID uploadId) {
        return new ProcessingResponse(
            uploadId,
            "VALIDATION",
            "COMPLETED",
            "인증서 검증이 완료되었습니다.",
            "LDAP_SAVING",
            true,
            null
        );
    }

    /**
     * LDAP 업로드 시작 성공 응답
     *
     * @param uploadId 업로드 ID
     * @return ProcessingResponse
     */
    public static ProcessingResponse ldapUploadStarted(UUID uploadId) {
        return new ProcessingResponse(
            uploadId,
            "LDAP_SAVING",
            "IN_PROGRESS",
            "LDAP 서버에 저장을 시작했습니다.",
            "COMPLETED",
            true,
            null
        );
    }

    /**
     * LDAP 업로드 완료 응답
     *
     * @param uploadId 업로드 ID
     * @return ProcessingResponse
     */
    public static ProcessingResponse ldapUploadCompleted(UUID uploadId) {
        return new ProcessingResponse(
            uploadId,
            "LDAP_SAVING",
            "COMPLETED",
            "LDAP 서버에 저장이 완료되었습니다.",
            "COMPLETED",
            true,
            null
        );
    }

    /**
     * 오류 응답
     *
     * @param uploadId 업로드 ID
     * @param step 처리 단계
     * @param errorMessage 오류 메시지
     * @return ProcessingResponse
     */
    public static ProcessingResponse error(UUID uploadId, String step, String errorMessage) {
        return new ProcessingResponse(
            uploadId,
            step,
            "FAILED",
            "처리 중 오류가 발생했습니다.",
            null,
            false,
            errorMessage
        );
    }

    /**
     * MANUAL 모드 아닌 경우 응답
     *
     * @param uploadId 업로드 ID
     * @return ProcessingResponse
     */
    public static ProcessingResponse notManualMode(UUID uploadId) {
        return new ProcessingResponse(
            uploadId,
            null,
            "REJECTED",
            "이 파일은 수동 처리 모드가 아닙니다.",
            null,
            false,
            "MANUAL 모드에서만 개별 단계를 트리거할 수 있습니다."
        );
    }

    /**
     * 파일을 찾을 수 없음 응답
     *
     * @param uploadId 업로드 ID
     * @return ProcessingResponse
     */
    public static ProcessingResponse fileNotFound(UUID uploadId) {
        return new ProcessingResponse(
            uploadId,
            null,
            "NOT_FOUND",
            "업로드 파일을 찾을 수 없습니다.",
            null,
            false,
            "해당 uploadId의 파일이 존재하지 않습니다."
        );
    }
}
