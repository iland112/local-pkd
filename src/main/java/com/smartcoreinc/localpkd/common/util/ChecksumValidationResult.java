package com.smartcoreinc.localpkd.common.util;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 체크섬 검증 결과
 *
 * @author SmartCore Inc.
 * @version 1.0
 */
@Getter
@Builder
@ToString
public class ChecksumValidationResult {

    /**
     * 검증 성공 여부
     */
    private final boolean valid;

    /**
     * 계산된 체크섬
     */
    private final String calculatedChecksum;

    /**
     * 기대되는 체크섬 (ICAO 공식)
     */
    private final String expectedChecksum;

    /**
     * 검증 수행 여부
     * false인 경우 expectedChecksum이 제공되지 않아 검증하지 않음
     */
    @Builder.Default
    private final boolean validated = true;

    /**
     * 오류 메시지 (검증 실패 시)
     */
    private final String errorMessage;

    /**
     * 체크섬 계산 소요 시간 (밀리초)
     */
    private final Long elapsedTimeMs;

    /**
     * 검증 실패 결과 생성
     *
     * @param errorMessage 오류 메시지
     * @return 실패 결과
     */
    public static ChecksumValidationResult error(String errorMessage) {
        return ChecksumValidationResult.builder()
                .valid(false)
                .validated(false)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 검증하지 않은 결과 생성 (expectedChecksum이 없는 경우)
     *
     * @param calculatedChecksum 계산된 체크섬
     * @return 검증하지 않은 결과
     */
    public static ChecksumValidationResult notValidated(String calculatedChecksum) {
        return ChecksumValidationResult.builder()
                .valid(true)  // 검증하지 않았으므로 실패는 아님
                .validated(false)
                .calculatedChecksum(calculatedChecksum)
                .build();
    }

    /**
     * 체크섬 일치 여부
     *
     * @return 일치 여부
     */
    public boolean matches() {
        return valid && validated;
    }

    /**
     * 검증 오류 발생 여부
     *
     * @return 오류 발생 여부
     */
    public boolean hasError() {
        return errorMessage != null;
    }

    /**
     * 검증 결과 요약 메시지
     *
     * @return 요약 메시지
     */
    public String getSummary() {
        if (hasError()) {
            return "체크섬 계산 실패: " + errorMessage;
        }

        if (!validated) {
            return "체크섬 검증 미수행 (계산된 값: " + calculatedChecksum + ")";
        }

        if (valid) {
            return "체크섬 일치 확인 ✓";
        } else {
            return String.format("체크섬 불일치 (기대: %s, 실제: %s)",
                    expectedChecksum, calculatedChecksum);
        }
    }

    /**
     * 체크섬 계산 소요 시간 (초)
     *
     * @return 소요 시간 (초)
     */
    public double getElapsedTimeSeconds() {
        if (elapsedTimeMs == null) {
            return 0.0;
        }
        return elapsedTimeMs / 1000.0;
    }
}
