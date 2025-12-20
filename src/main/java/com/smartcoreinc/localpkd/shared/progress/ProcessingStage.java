package com.smartcoreinc.localpkd.shared.progress;

import lombok.Getter;

/**
 * ProcessingStage - 파일 처리 단계를 나타내는 Enum
 *
 * <p>파일 업로드 후 파싱, 검증, LDAP 저장까지의 전체 프로세스 단계를 정의합니다.</p>
 *
 * <h3>Processing Flow</h3>
 * <pre>
 * UPLOAD_COMPLETED (5%)
 *   ↓
 * PARSING_STARTED (10%)
 *   ↓
 * PARSING_IN_PROGRESS (20-50%)
 *   ↓
 * PARSING_COMPLETED (60%)
 *   ↓
 * VALIDATION_STARTED (65%)
 *   ↓
 * VALIDATION_IN_PROGRESS (70-80%)
 *   ↓
 * VALIDATION_COMPLETED (85%)
 *   ↓
 * LDAP_SAVING_STARTED (90%)
 *   ↓
 * LDAP_SAVING_IN_PROGRESS (92-98%)
 *   ↓
 * LDAP_SAVING_COMPLETED (100%)
 *   ↓
 * COMPLETED (100%)
 * </pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-22
 */
@Getter
public enum ProcessingStage {

    // 파일 업로드 완료
    UPLOAD_COMPLETED("파일 업로드 완료", 5, StageCategory.UPLOAD),

    // 파싱 단계 (10-60%)
    PARSING_STARTED("파일 파싱 시작", 10, StageCategory.PARSING),
    PARSING_IN_PROGRESS("파일 파싱 중", 30, StageCategory.PARSING),
    PARSING_COMPLETED("파일 파싱 완료", 60, StageCategory.PARSING),

    // 검증 단계 (65-85%)
    VALIDATION_STARTED("인증서 검증 시작", 65, StageCategory.VALIDATION),
    VALIDATION_IN_PROGRESS("인증서 검증 중", 75, StageCategory.VALIDATION),
    VALIDATION_COMPLETED("인증서 검증 완료", 85, StageCategory.VALIDATION),

    // LDAP 저장 단계 (90-100%)
    LDAP_SAVING_STARTED("LDAP 저장 시작", 90, StageCategory.LDAP_SAVE),
    LDAP_SAVING_IN_PROGRESS("LDAP 저장 중", 95, StageCategory.LDAP_SAVE),
    LDAP_SAVING_COMPLETED("LDAP 저장 완료", 100, StageCategory.LDAP_SAVE),

    // 수동 일시 중지
    MANUAL_PAUSE("수동 처리 대기 중", 0, StageCategory.PAUSE), // New entry

    // 완료/실패
    COMPLETED("처리 완료", 100, StageCategory.COMPLETE),
    FAILED("처리 실패", 0, StageCategory.FAILED);

    private final String displayName;
    private final int basePercentage;
    private final StageCategory category;

    ProcessingStage(String displayName, int basePercentage, StageCategory category) {
        this.displayName = displayName;
        this.basePercentage = basePercentage;
        this.category = category;
    }

    /**
     * 단계 카테고리
     */
    public enum StageCategory {
        UPLOAD,
        PARSING,
        VALIDATION,
        LDAP_SAVE,
        COMPLETE,
        FAILED,
        PAUSE // New entry
    }

    /**
     * 파싱 중인 단계인지 확인
     */
    public boolean isParsing() {
        return category == StageCategory.PARSING;
    }

    /**
     * 검증 중인 단계인지 확인
     */
    public boolean isValidation() {
        return category == StageCategory.VALIDATION;
    }

    /**
     * LDAP 저장 중인 단계인지 확인
     */
    public boolean isLdapSaving() {
        return category == StageCategory.LDAP_SAVE;
    }

    /**
     * 완료 단계인지 확인
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }

    /**
     * 실패 단계인지 확인
     */
    public boolean isFailed() {
        return this == FAILED;
    }

    /**
     * 진행 중인 단계인지 확인 (완료/실패 제외)
     */
    public boolean isInProgress() {
        return !isCompleted() && !isFailed() && this != MANUAL_PAUSE;
    }
}
