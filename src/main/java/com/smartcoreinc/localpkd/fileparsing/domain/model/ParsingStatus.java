package com.smartcoreinc.localpkd.fileparsing.domain.model;

import lombok.Getter;

/**
 * ParsingStatus - 파싱 상태 Value Object (Enum)
 *
 * <p><b>비즈니스 규칙</b>:</p>
 * <ul>
 *   <li>RECEIVED → PARSING: 파싱 시작 가능</li>
 *   <li>PARSING → PARSED: 파싱 성공 완료</li>
 *   <li>PARSING → FAILED: 파싱 실패</li>
 *   <li>PARSED, FAILED: 종료 상태 (더 이상 전환 불가)</li>
 * </ul>
 *
 * <p><b>상태 전이 다이어그램</b>:</p>
 * <pre>
 *          ┌──────────┐
 *          │ RECEIVED │ (초기 상태)
 *          └─────┬────┘
 *                │
 *                ▼
 *          ┌──────────┐
 *          │ PARSING  │ (진행 중)
 *          └─────┬────┘
 *                │
 *         ┌──────┴──────┐
 *         ▼             ▼
 *    ┌────────┐   ┌─────────┐
 *    │ PARSED │   │ FAILED  │ (종료 상태)
 *    └────────┘   └─────────┘
 * </pre>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * ParsingStatus status = ParsingStatus.received();
 *
 * // 상태 전환 검증
 * if (status.canTransitionTo(ParsingStatus.PARSING)) {
 *     status = ParsingStatus.parsing();
 * }
 *
 * // 상태 확인
 * if (status.isParsing()) {
 *     // 파싱 진행 중...
 * }
 * </pre>
 */
@Getter
public enum ParsingStatus {
    /**
     * 수신됨 - 파일이 업로드되었으나 아직 파싱 시작 전
     */
    RECEIVED("수신됨"),

    /**
     * 파싱 중 - 파일 파싱 진행 중
     */
    PARSING("파싱 중"),

    /**
     * 파싱 완료 - 파일 파싱 성공적으로 완료
     */
    PARSED("파싱 완료"),

    /**
     * 파싱 실패 - 파일 파싱 중 오류 발생
     */
    FAILED("파싱 실패");

    private final String displayName;

    ParsingStatus(String displayName) {
        this.displayName = displayName;
    }

    // ========== Static Factory Methods ==========

    /**
     * RECEIVED 상태 생성
     */
    public static ParsingStatus received() {
        return RECEIVED;
    }

    /**
     * PARSING 상태 생성
     */
    public static ParsingStatus parsing() {
        return PARSING;
    }

    /**
     * PARSED 상태 생성
     */
    public static ParsingStatus parsed() {
        return PARSED;
    }

    /**
     * FAILED 상태 생성
     */
    public static ParsingStatus failed() {
        return FAILED;
    }

    // ========== Business Logic Methods ==========

    /**
     * 파싱 중 상태 여부
     */
    public boolean isParsing() {
        return this == PARSING;
    }

    /**
     * 파싱 완료 상태 여부
     */
    public boolean isParsed() {
        return this == PARSED;
    }

    /**
     * 파싱 실패 상태 여부
     */
    public boolean isFailed() {
        return this == FAILED;
    }

    /**
     * 종료 상태 여부 (PARSED 또는 FAILED)
     */
    public boolean isTerminal() {
        return this == PARSED || this == FAILED;
    }

    /**
     * 상태 전환 가능 여부 검증
     *
     * <p><b>전환 규칙</b>:</p>
     * <ul>
     *   <li>RECEIVED → PARSING: ✅ 허용</li>
     *   <li>PARSING → PARSED: ✅ 허용</li>
     *   <li>PARSING → FAILED: ✅ 허용</li>
     *   <li>PARSED → *: ❌ 불가 (종료 상태)</li>
     *   <li>FAILED → *: ❌ 불가 (종료 상태)</li>
     * </ul>
     *
     * @param target 전환하려는 목표 상태
     * @return 전환 가능 여부
     */
    public boolean canTransitionTo(ParsingStatus target) {
        return switch (this) {
            case RECEIVED -> target == PARSING;
            case PARSING -> target == PARSED || target == FAILED;
            case PARSED, FAILED -> false; // 종료 상태에서는 전환 불가
        };
    }

    /**
     * 상태 전환 검증 (예외 발생)
     *
     * @param target 전환하려는 목표 상태
     * @throws IllegalStateException 전환 불가능한 경우
     */
    public void validateTransitionTo(ParsingStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                String.format(
                    "파싱 상태 전환 불가: %s (%s) → %s (%s)",
                    this.name(),
                    this.displayName,
                    target.name(),
                    target.displayName
                )
            );
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
