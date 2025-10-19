package com.smartcoreinc.localpkd.fileupload.domain.model;

import lombok.Getter;

/**
 * UploadStatus - 업로드 상태 Enum
 *
 * <p>파일 업로드의 진행 상태를 나타내는 도메인 객체입니다.
 * 업로드 워크플로우의 각 단계를 추적합니다.</p>
 *
 * <h3>상태 전이 흐름</h3>
 * <pre>
 * RECEIVED → VALIDATING → VALIDATED → PARSING → PARSED → UPLOADING_TO_LDAP → COMPLETED
 *    ↓           ↓           ↓           ↓         ↓              ↓
 * FAILED   CHECKSUM_INVALID  FAILED    FAILED   FAILED        FAILED
 *    ↓
 * DUPLICATE_DETECTED
 * </pre>
 *
 * <h3>상태 분류</h3>
 * <ul>
 *   <li>진행 중: RECEIVED, VALIDATING, VALIDATED, PARSING, PARSED, UPLOADING_TO_LDAP</li>
 *   <li>성공: COMPLETED</li>
 *   <li>실패: FAILED, CHECKSUM_INVALID, DUPLICATE_DETECTED</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * UploadStatus status = UploadStatus.RECEIVED;
 *
 * // 상태 확인
 * boolean isInProgress = status.isInProgress();  // true
 * boolean isTerminal = status.isTerminal();      // false
 * boolean isSuccess = status.isSuccess();        // false
 * boolean isFailed = status.isFailed();          // false
 *
 * // 표시명
 * String displayName = status.getDisplayName();  // "수신됨"
 *
 * // 다음 상태 전이 가능 여부
 * boolean canTransition = status.canTransitionTo(UploadStatus.VALIDATING);  // true
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Getter
public enum UploadStatus {

    /**
     * 수신됨 - 파일이 서버에 수신되었으나 검증 전
     */
    RECEIVED("수신됨", false, false, false),

    /**
     * 검증 중 - 파일 형식, 크기, 체크섬 등을 검증 중
     */
    VALIDATING("검증 중", false, false, false),

    /**
     * 검증 완료 - 파일 검증이 성공적으로 완료됨
     */
    VALIDATED("검증 완료", false, false, false),

    /**
     * 체크섬 불일치 - 예상 체크섬과 계산된 체크섬이 일치하지 않음
     */
    CHECKSUM_INVALID("체크섬 불일치", true, false, true),

    /**
     * 중복 감지 - 동일한 파일 해시가 이미 존재함
     */
    DUPLICATE_DETECTED("중복 감지", true, false, true),

    /**
     * 파싱 중 - LDIF 또는 ML 파일을 파싱 중
     */
    PARSING("파싱 중", false, false, false),

    /**
     * 파싱 완료 - 파일 파싱이 성공적으로 완료됨
     */
    PARSED("파싱 완료", false, false, false),

    /**
     * LDAP 업로드 중 - 인증서를 OpenLDAP에 업로드 중
     */
    UPLOADING_TO_LDAP("LDAP 업로드 중", false, false, false),

    /**
     * 완료 - 모든 단계가 성공적으로 완료됨
     */
    COMPLETED("완료", true, true, false),

    /**
     * 실패 - 업로드 과정 중 오류 발생
     */
    FAILED("실패", true, false, true);

    /**
     * 한글 표시명
     */
    private final String displayName;

    /**
     * 종료 상태 여부 (더 이상 상태 전이가 없음)
     */
    private final boolean terminal;

    /**
     * 성공 상태 여부
     */
    private final boolean success;

    /**
     * 실패 상태 여부
     */
    private final boolean failed;

    /**
     * Enum 생성자
     *
     * @param displayName 한글 표시명
     * @param terminal    종료 상태 여부
     * @param success     성공 상태 여부
     * @param failed      실패 상태 여부
     */
    UploadStatus(String displayName, boolean terminal, boolean success, boolean failed) {
        this.displayName = displayName;
        this.terminal = terminal;
        this.success = success;
        this.failed = failed;
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 진행 중 상태 여부 확인
     *
     * @return 종료 상태가 아니면 true
     */
    public boolean isInProgress() {
        return !terminal;
    }

    /**
     * 다음 상태로 전이 가능 여부 확인
     *
     * <p>종료 상태(terminal)에서는 더 이상 전이할 수 없습니다.</p>
     *
     * @param nextStatus 전이하려는 상태
     * @return 전이 가능하면 true
     */
    public boolean canTransitionTo(UploadStatus nextStatus) {
        if (nextStatus == null) {
            return false;
        }

        // 종료 상태에서는 전이 불가
        if (this.terminal) {
            return false;
        }

        // FAILED는 언제든지 전이 가능 (오류 처리)
        if (nextStatus == FAILED) {
            return true;
        }

        // 정상적인 상태 전이 규칙
        return switch (this) {
            case RECEIVED -> nextStatus == VALIDATING ||
                    nextStatus == DUPLICATE_DETECTED ||
                    nextStatus == FAILED;

            case VALIDATING -> nextStatus == VALIDATED ||
                    nextStatus == CHECKSUM_INVALID ||
                    nextStatus == FAILED;

            case VALIDATED -> nextStatus == PARSING ||
                    nextStatus == FAILED;

            case PARSING -> nextStatus == PARSED ||
                    nextStatus == FAILED;

            case PARSED -> nextStatus == UPLOADING_TO_LDAP ||
                    nextStatus == COMPLETED ||  // LDAP 업로드 스킵 가능
                    nextStatus == FAILED;

            case UPLOADING_TO_LDAP -> nextStatus == COMPLETED ||
                    nextStatus == FAILED;

            // 종료 상태에서는 전이 불가
            default -> false;
        };
    }

    /**
     * 문자열 표현
     *
     * @return "UploadStatus[RECEIVED(수신됨)]"
     */
    @Override
    public String toString() {
        return String.format("UploadStatus[%s(%s)]", this.name(), this.displayName);
    }

    // ===== 정적 유틸리티 메서드 =====

    /**
     * 표시명으로부터 UploadStatus 조회
     *
     * @param displayName 한글 표시명
     * @return UploadStatus (찾지 못하면 null)
     */
    public static UploadStatus fromDisplayName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return null;
        }

        for (UploadStatus status : values()) {
            if (status.displayName.equals(displayName.trim())) {
                return status;
            }
        }

        return null;
    }

    /**
     * 이름 문자열로부터 UploadStatus 조회 (대소문자 무관)
     *
     * @param name 상태 이름 (예: "RECEIVED", "received")
     * @return UploadStatus (찾지 못하면 null)
     */
    public static UploadStatus fromName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        try {
            return UploadStatus.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * JPA Persistence용 문자열로부터 복원
     *
     * @param value 상태 이름
     * @return UploadStatus
     * @throws IllegalArgumentException 유효하지 않은 값인 경우
     */
    public static UploadStatus fromStorageValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return RECEIVED;  // 기본값
        }

        return UploadStatus.valueOf(value.trim());
    }

    /**
     * JPA Persistence용 문자열 변환
     *
     * @return 상태 이름
     */
    public String toStorageValue() {
        return this.name();
    }
}
