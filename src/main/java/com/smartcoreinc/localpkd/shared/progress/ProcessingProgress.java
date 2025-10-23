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
        return String.format(
            "{\"uploadId\":\"%s\",\"stage\":\"%s\",\"stageName\":\"%s\",\"percentage\":%d," +
            "\"processedCount\":%d,\"totalCount\":%d,\"message\":\"%s\"," +
            "\"errorMessage\":\"%s\",\"details\":\"%s\",\"updatedAt\":\"%s\"}",
            uploadId,
            stage.name(),
            escapeJson(stage.getDisplayName()),
            percentage,
            processedCount,
            totalCount,
            escapeJson(message),
            escapeJson(errorMessage),
            escapeJson(details),
            updatedAt
        );
    }

    /**
     * JSON 문자열 이스케이프
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    // ==================== Static Factory Methods ====================

    /**
     * 업로드 완료 단계
     */
    public static ProcessingProgress uploadCompleted(UUID uploadId, String fileName) {
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.UPLOAD_COMPLETED)
            .percentage(5)
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
            .percentage(10)
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
            UUID uploadId, int processedCount, int totalCount, String currentItem) {
        int percentage = calculatePercentage(processedCount, totalCount, 20, 50);
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.PARSING_IN_PROGRESS)
            .percentage(percentage)
            .processedCount(processedCount)
            .totalCount(totalCount)
            .message(String.format("파일 파싱 중 (%d/%d)", processedCount, totalCount))
            .details(currentItem)
            .build();
    }

    /**
     * 파싱 완료
     */
    public static ProcessingProgress parsingCompleted(UUID uploadId, int totalCount) {
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.PARSING_COMPLETED)
            .percentage(60)
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
            .percentage(65)
            .processedCount(0)
            .totalCount(totalCount)
            .message("인증서 검증 시작")
            .build();
    }

    /**
     * 검증 진행 중
     */
    public static ProcessingProgress validationInProgress(
            UUID uploadId, int processedCount, int totalCount, String currentCert) {
        int percentage = calculatePercentage(processedCount, totalCount, 70, 80);
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.VALIDATION_IN_PROGRESS)
            .percentage(percentage)
            .processedCount(processedCount)
            .totalCount(totalCount)
            .message(String.format("인증서 검증 중 (%d/%d)", processedCount, totalCount))
            .details(currentCert)
            .build();
    }

    /**
     * 검증 완료
     */
    public static ProcessingProgress validationCompleted(UUID uploadId, int totalCount) {
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.VALIDATION_COMPLETED)
            .percentage(85)
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
            .percentage(90)
            .processedCount(0)
            .totalCount(totalCount)
            .message("LDAP 저장 시작")
            .build();
    }

    /**
     * LDAP 저장 진행 중
     */
    public static ProcessingProgress ldapSavingInProgress(
            UUID uploadId, int processedCount, int totalCount, String currentEntry) {
        int percentage = calculatePercentage(processedCount, totalCount, 92, 98);
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.LDAP_SAVING_IN_PROGRESS)
            .percentage(percentage)
            .processedCount(processedCount)
            .totalCount(totalCount)
            .message(String.format("LDAP 저장 중 (%d/%d)", processedCount, totalCount))
            .details(currentEntry)
            .build();
    }

    /**
     * LDAP 저장 완료
     */
    public static ProcessingProgress ldapSavingCompleted(UUID uploadId, int totalCount) {
        return ProcessingProgress.builder()
            .uploadId(uploadId)
            .stage(ProcessingStage.LDAP_SAVING_COMPLETED)
            .percentage(100)
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
