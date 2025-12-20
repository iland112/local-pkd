package com.smartcoreinc.localpkd.fileupload.application.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Processing Status Response - 파일 처리 상태 조회 응답 DTO
 *
 * <p>파일의 현재 처리 상태를 조회할 때 반환되는 응답입니다.
 * 처리 모드(AUTO/MANUAL), 현재 상태, 진행률 등의 정보를 포함합니다.</p>
 *
 * <h3>응답 예시</h3>
 *
 * <pre>{@code
 * GET /api/processing/status/{uploadId}
 * {
 *   "uploadId": "550e8400-e29b-41d4-a716-446655440000",
 *   "fileName": "icaopkd-002-complete-009410.ldif",
 *   "processingMode": "MANUAL",
 *   "currentStage": "PARSING_COMPLETED",
 *   "currentPercentage": 60,
 *   "uploadedAt": "2025-10-24T10:30:00",
 *   "lastUpdateAt": "2025-10-24T10:35:45",
 *   "parsingStartedAt": "2025-10-24T10:31:00",
 *   "parsingCompletedAt": "2025-10-24T10:33:30",
 *   "validationStartedAt": null,
 *   "validationCompletedAt": null,
 *   "ldapUploadStartedAt": null,
 *   "ldapUploadCompletedAt": null,
 *   "totalProcessingTimeSeconds": 345,
 *   "status": "SUCCESS",
 *   "errorMessage": null,
 *   "manualPauseAtStep": "VALIDATION_STARTED"
 * }
 * }</pre>
 *
 * <h3>MANUAL 모드 상태 관리</h3>
 *
 * <p>MANUAL 모드에서는 사용자가 각 단계를 수동으로 진행합니다:
 * <ol>
 *   <li>파일 업로드 → <strong>manualPauseAtStep = "UPLOAD_COMPLETED"</strong></li>
 *   <li>사용자가 "파싱 시작" 클릭 → <strong>manualPauseAtStep = "PARSING_STARTED"</strong></li>
 *   <li>파싱 완료 → <strong>manualPauseAtStep = "PARSING_COMPLETED"</strong> (사용자 액션 대기)</li>
 *   <li>사용자가 "검증 시작" 클릭 → <strong>manualPauseAtStep = "VALIDATION_STARTED"</strong></li>
 *   <li>검증 완료 → <strong>manualPauseAtStep = "VALIDATION_COMPLETED"</strong> (사용자 액션 대기)</li>
 *   <li>사용자가 "LDAP 업로드" 클릭 → <strong>manualPauseAtStep = "LDAP_SAVING_STARTED"</strong></li>
 *   <li>LDAP 업로드 완료 → <strong>manualPauseAtStep = null</strong> (완료)</li>
 * </ol>
 * </p>
 *
 * <h3>AUTO 모드 상태 관리</h3>
 *
 * <p>AUTO 모드에서는 다음 단계가 자동으로 진행됩니다:
 * <ol>
 *   <li>파일 업로드 → 파싱 자동 시작</li>
 *   <li>파싱 완료 → 검증 자동 시작</li>
 *   <li>검증 완료 → LDAP 업로드 자동 시작</li>
 *   <li>LDAP 업로드 완료 → 처리 완료</li>
 * </ol>
 * </p>
 *
 * @param uploadId 업로드 ID
 * @param fileName 파일명
 * @param processingMode 처리 모드 ("AUTO" 또는 "MANUAL")
 * @param currentStage 현재 처리 단계
 * @param currentPercentage 현재 진행률 (0-100)
 * @param uploadedAt 파일 업로드 일시
 * @param lastUpdateAt 마지막 업데이트 일시
 * @param parsingStartedAt 파싱 시작 일시
 * @param parsingCompletedAt 파싱 완료 일시
 * @param validationStartedAt 검증 시작 일시
 * @param validationCompletedAt 검증 완료 일시
 * @param ldapUploadStartedAt LDAP 업로드 시작 일시
 * @param ldapUploadCompletedAt LDAP 업로드 완료 일시
 * @param totalProcessingTimeSeconds 전체 처리 시간 (초)
 * @param status 처리 상태 ("IN_PROGRESS", "SUCCESS", "FAILED")
 * @param errorMessage 오류 메시지 (실패 시)
 * @param manualPauseAtStep MANUAL 모드에서 사용자 액션 대기 중인 단계
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
public record ProcessingStatusResponse(
        UUID uploadId,
        String fileName,
        String processingMode,
        String currentStage,
        int currentPercentage,
        LocalDateTime uploadedAt,
        LocalDateTime lastUpdateAt,
        LocalDateTime parsingStartedAt,
        LocalDateTime parsingCompletedAt,
        LocalDateTime validationStartedAt,
        LocalDateTime validationCompletedAt,
        LocalDateTime ldapUploadStartedAt,
        LocalDateTime ldapUploadCompletedAt,
        long totalProcessingTimeSeconds,
        String status,
        String errorMessage,
        String manualPauseAtStep
) {

    /**
     * Builder for ProcessingStatusResponse
     */
    public static class Builder {
        private UUID uploadId;
        private String fileName;
        private String processingMode;
        private String currentStage;
        private int currentPercentage;
        private LocalDateTime uploadedAt;
        private LocalDateTime lastUpdateAt;
        private LocalDateTime parsingStartedAt;
        private LocalDateTime parsingCompletedAt;
        private LocalDateTime validationStartedAt;
        private LocalDateTime validationCompletedAt;
        private LocalDateTime ldapUploadStartedAt;
        private LocalDateTime ldapUploadCompletedAt;
        private long totalProcessingTimeSeconds;
        private String status;
        private String errorMessage;
        private String manualPauseAtStep;

        public Builder uploadId(UUID uploadId) {
            this.uploadId = uploadId;
            return this;
        }

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder processingMode(String processingMode) {
            this.processingMode = processingMode;
            return this;
        }

        public Builder currentStage(String currentStage) {
            this.currentStage = currentStage;
            return this;
        }

        public Builder currentPercentage(int currentPercentage) {
            this.currentPercentage = currentPercentage;
            return this;
        }

        public Builder uploadedAt(LocalDateTime uploadedAt) {
            this.uploadedAt = uploadedAt;
            return this;
        }

        public Builder lastUpdateAt(LocalDateTime lastUpdateAt) {
            this.lastUpdateAt = lastUpdateAt;
            return this;
        }

        public Builder parsingStartedAt(LocalDateTime parsingStartedAt) {
            this.parsingStartedAt = parsingStartedAt;
            return this;
        }

        public Builder parsingCompletedAt(LocalDateTime parsingCompletedAt) {
            this.parsingCompletedAt = parsingCompletedAt;
            return this;
        }

        public Builder validationStartedAt(LocalDateTime validationStartedAt) {
            this.validationStartedAt = validationStartedAt;
            return this;
        }

        public Builder validationCompletedAt(LocalDateTime validationCompletedAt) {
            this.validationCompletedAt = validationCompletedAt;
            return this;
        }

        public Builder ldapUploadStartedAt(LocalDateTime ldapUploadStartedAt) {
            this.ldapUploadStartedAt = ldapUploadStartedAt;
            return this;
        }

        public Builder ldapUploadCompletedAt(LocalDateTime ldapUploadCompletedAt) {
            this.ldapUploadCompletedAt = ldapUploadCompletedAt;
            return this;
        }

        public Builder totalProcessingTimeSeconds(long totalProcessingTimeSeconds) {
            this.totalProcessingTimeSeconds = totalProcessingTimeSeconds;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder manualPauseAtStep(String manualPauseAtStep) {
            this.manualPauseAtStep = manualPauseAtStep;
            return this;
        }

        public ProcessingStatusResponse build() {
            return new ProcessingStatusResponse(
                uploadId,
                fileName,
                processingMode,
                currentStage,
                currentPercentage,
                uploadedAt,
                lastUpdateAt,
                parsingStartedAt,
                parsingCompletedAt,
                validationStartedAt,
                validationCompletedAt,
                ldapUploadStartedAt,
                ldapUploadCompletedAt,
                totalProcessingTimeSeconds,
                status,
                errorMessage,
                manualPauseAtStep
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 처리가 진행 중인지 확인
     *
     * @return 진행 중이면 true
     */
    public boolean isInProgress() {
        return "IN_PROGRESS".equals(status);
    }

    /**
     * 처리가 완료되었는지 확인
     *
     * @return 완료되었으면 true
     */
    public boolean isCompleted() {
        return "SUCCESS".equals(status);
    }

    /**
     * 처리가 실패했는지 확인
     *
     * @return 실패했으면 true
     */
    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    /**
     * MANUAL 모드에서 다음 단계를 진행할 수 있는지 확인
     *
     * @return 사용자 액션 대기 중이면 true
     */
    public boolean isWaitingForUserAction() {
        return "MANUAL".equals(processingMode) && manualPauseAtStep != null;
    }

    /**
     * 파싱이 완료되었는지 확인
     *
     * @return 완료되었으면 true
     */
    public boolean isParsingCompleted() {
        return parsingCompletedAt != null;
    }

    /**
     * 검증이 완료되었는지 확인
     *
     * @return 완료되었으면 true
     */
    public boolean isValidationCompleted() {
        return validationCompletedAt != null;
    }

    /**
     * LDAP 업로드가 완료되었는지 확인
     *
     * @return 완료되었으면 true
     */
    public boolean isLdapUploadCompleted() {
        return ldapUploadCompletedAt != null;
    }
}
