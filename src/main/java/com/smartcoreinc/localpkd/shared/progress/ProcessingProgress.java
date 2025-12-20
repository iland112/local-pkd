package com.smartcoreinc.localpkd.shared.progress;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ProcessingProgress - 파일 처리 진행 상태 Value Object
 *
 * <p>파일 업로드 후 파싱, 검증, LDAP 저장 프로세스의 실시간 진행 상태를 나타냅니다.</p>
 *
 * <h3>비즈니스 규칙</h3>
 * <ul>
 *   <li>uploadId는 필수 (null 불가)</li>
 *   <li>percentage는 0-100 범위</li>
 *   <li>processedCount <= totalCount</li>
 *   <li>COMPLETED 단계는 percentage = 100</li>
 *   <li>FAILED 단계는 errorMessage 필수</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * ProcessingProgress progress = ProcessingProgress.parsingInProgress(
 *     uploadId,
 *     50,    // 50개 처리
 *     100,   // 총 100개
 *     "CSCA 인증서 파싱 중..."
 * );
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-22
 */
@Getter
@Builder
public class ProcessingProgress {

    /**
     * 업로드 ID (파일 식별자)
     */
    private final UUID uploadId;

    /**
     * 현재 처리 단계
     */
    private final ProcessingStage stage;

    /**
     * 진행률 (0-100)
     */
    private final int percentage;

    /**
     * 처리된 항목 수 (인증서, 레코드 등)
     */
    private final int processedCount;

    /**
     * 전체 항목 수
     */
    private final int totalCount;

    /**
     * 진행 상태 메시지
     */
    private final String message;

    /**
     * 에러 메시지 (실패 시)
     */
    private final String errorMessage;

    /**
     * 상세 정보 (파일명, 타입 등)
     */
    private final String details;

    /**
     * 업데이트 시간
     */
    @Builder.Default
    private final LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * 완료 여부
     */
    public boolean isCompleted() {
        return stage.isCompleted();
    }

    /**
     * 실패 여부
     */
    public boolean isFailed() {
        return stage.isFailed();
    }

    /**
     * 진행 중 여부
     */
    public boolean isInProgress() {
        return stage.isInProgress();
    }

    /**
     * SSE 이벤트 데이터로 변환 (JSON 형식)
     */
    public String toJson() {
        // String.format 대신 ObjectMapper를 사용하는 것이 더 견고하고 안전함 (향후 고려)
        return String.format(
            "{\"uploadId\":\"%s\",\"stage\":\"%s\",\"stageName\":\"%s\",\"status\":\"%s\",\"percentage\":%d,\"processedCount\":%d,\"totalCount\":%d,\"message\":\"%s\",\"errorMessage\":\"%s\",\"details\":\"%s\",\"updatedAt\":\"%s\",\"step\":\"%s\"}", // Added 'status' field
            uploadId,
            stage.name(),
            escapeJson(stage.getDisplayName()),
            stage.name(), // <-- Added this for 'status'
            percentage,
            processedCount,
            totalCount,
            escapeJson(message),
            escapeJson(errorMessage),
            escapeJson(details),
            updatedAt,
            determineStep(stage) // Call helper to determine step
        );
    }

    /**
     * JSON 문자열 이스케이프
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\") // 백슬래시를 이스케이프
                    .replace("\"", "\\\"") // 큰따옴표를 이스케이프
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    /**
     * ProcessingStage에 따라 프론트엔드가 예상하는 'step' 문자열을 결정합니다.
     */
    private String determineStep(ProcessingStage stage) {
        switch (stage) {
            case UPLOAD_COMPLETED:
                return "UPLOAD";
            case PARSING_STARTED:
            case PARSING_IN_PROGRESS:
            case PARSING_COMPLETED:
                return "PARSE";
            case VALIDATION_STARTED:
            case VALIDATION_IN_PROGRESS:
            case VALIDATION_COMPLETED:
                return "VALIDATE";
            case LDAP_SAVING_STARTED:
            case LDAP_SAVING_IN_PROGRESS:
            case LDAP_SAVING_COMPLETED:
                return "LDAP_UPLOAD";
            case MANUAL_PAUSE: // New case
                return "PAUSE";
            case COMPLETED:
                return "FINALIZED";
            case FAILED:
                return "FAILED"; // Frontend expects "FAILED" status, not necessarily a step
            default:
                return "UNKNOWN";
        }
    }

    // ==================== Static Factory Methods ====================

    /**
     * 업로드 완료 단계
     */
    public static ProcessingProgress uploadCompleted(UUID uploadId, String fileName) {
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.UPLOAD_COMPLETED)
            .percentage(5) // 5%로 변경
            .processedCount(0)
            .totalCount(0)
            .message("파일 업로드 완료")
            .details(fileName)
            .build();
    }

    /**
     * 파싱 시작
     */
    public static ProcessingProgress parsingStarted(UUID uploadId, String fileName) {
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.PARSING_STARTED)
            .percentage(10) // 10%로 변경
            .processedCount(0)
            .totalCount(0)
            .message("파일 파싱 시작")
            .details(fileName)
            .build();
    }

    /**
     * 파싱 진행 중
     */
    public static ProcessingProgress parsingInProgress(
            UUID uploadId, int processedCount, int totalCount, String message, int minPercent, int maxPercent) {
        int percentage = calculatePercentage(processedCount, totalCount, minPercent, maxPercent);
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.PARSING_IN_PROGRESS)
            .percentage(percentage)
            .processedCount(processedCount)
            .totalCount(totalCount)
            .message(message)
            .build();
    }

    /**
     * 파싱 완료
     */
    public static ProcessingProgress parsingCompleted(UUID uploadId, int totalCount) {
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.PARSING_COMPLETED)
            .percentage(50) // 50%로 변경
            .processedCount(totalCount)
            .totalCount(totalCount)
            .message(String.format("파일 파싱 완료 (총 %d개)", totalCount))
            .build();
    }

    /**
     * 검증 시작
     */
    public static ProcessingProgress validationStarted(UUID uploadId, int totalCount) {
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.VALIDATION_STARTED)
            .percentage(55) // 55%로 변경 (파싱 완료 50% 이후)
            .processedCount(0)
            .totalCount(totalCount)
            .message("인증서 검증 시작")
            .build();
    }

    /**
     * 검증 진행 중
     */
    public static ProcessingProgress validationInProgress(
            UUID uploadId, int processedCount, int totalCount, String message, int minPercent, int maxPercent) {
        int percentage = calculatePercentage(processedCount, totalCount, minPercent, maxPercent);
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.VALIDATION_IN_PROGRESS)
            .percentage(percentage)
            .processedCount(processedCount)
            .totalCount(totalCount)
            .message(message)
            .build();
    }

    /**
     * 검증 완료
     */
    public static ProcessingProgress validationCompleted(UUID uploadId, int totalCount) {
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.VALIDATION_COMPLETED)
            .percentage(85) // 85%로 변경
            .processedCount(totalCount)
            .totalCount(totalCount)
            .message(String.format("인증서 검증 완료 (총 %d개)", totalCount))
            .build();
    }

    /**
     * LDAP 저장 시작
     */
    public static ProcessingProgress ldapSavingStarted(UUID uploadId, int totalCount) {
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.LDAP_SAVING_STARTED)
            .percentage(90) // 90%로 변경
            .processedCount(0)
            .totalCount(totalCount)
            .message("LDAP 저장 시작")
            .build();
    }

    /**
     * LDAP 저장 진행 중
     */
    public static ProcessingProgress ldapSavingInProgress(
            UUID uploadId, int processedCount, int totalCount, String message, int minPercent, int maxPercent) {
        int percentage = calculatePercentage(processedCount, totalCount, minPercent, maxPercent);
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.LDAP_SAVING_IN_PROGRESS)
            .percentage(percentage)
            .processedCount(processedCount)
            .totalCount(totalCount)
            .message(message)
            .build();
    }

    /**
     * LDAP 저장 완료
     */
    public static ProcessingProgress ldapSavingCompleted(UUID uploadId, int totalCount) {
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.LDAP_SAVING_COMPLETED)
            .percentage(100) // 100%로 변경
            .processedCount(totalCount)
            .totalCount(totalCount)
            .message(String.format("LDAP 저장 완료 (총 %d개)", totalCount))
            .build();
    }

    /**
     * 처리 완료
     */
    public static ProcessingProgress completed(UUID uploadId, int totalCount) {
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.COMPLETED)
            .percentage(100)
            .processedCount(totalCount)
            .totalCount(totalCount)
            .message("모든 처리 완료")
            .build();
    }

    /**
     * 처리 실패
     */
    public static ProcessingProgress failed(UUID uploadId, ProcessingStage failedStage, String errorMessage) {
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.FAILED)
            .percentage(0)
            .processedCount(0)
            .totalCount(0)
            .message(failedStage.getDisplayName() + " 실패")
            .errorMessage(errorMessage)
            .build();
    }

    /**
     * 수동 모드에서 특정 단계 후 대기
     */
    public static ProcessingProgress manualPause(UUID uploadId, ProcessingStage previousStage) {
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.MANUAL_PAUSE)
            .percentage(previousStage.getBasePercentage()) // Use previous stage's percentage
            .processedCount(0)
            .totalCount(0)
            .message(previousStage.getDisplayName() + " 완료, 수동 트리거 대기 중")
            .details("다음 단계는 수동 사용자 작업이 필요합니다.")
            .build();
    }

    /**
     * 진행률 계산 (범위 내에서)
     *
     * @param current 현재 처리 수
     * @param total 전체 수
     * @param minPercent 최소 퍼센트
     * @param maxPercent 최대 퍼센트
     * @return 계산된 퍼센트
     */
    private static int calculatePercentage(int current, int total, int minPercent, int maxPercent) {
        if (total == 0) {
            return minPercent;
        }
        double ratio = (double) current / total;
        int range = maxPercent - minPercent;
        return minPercent + (int) (ratio * range);
    }
}