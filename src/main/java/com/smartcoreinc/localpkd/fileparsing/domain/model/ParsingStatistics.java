package com.smartcoreinc.localpkd.fileparsing.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * ParsingStatistics - 파싱 통계 Value Object
 *
 * <p><b>DDD Value Object 패턴</b>:</p>
 * <ul>
 *   <li>Immutability: 생성 후 변경 불가</li>
 *   <li>Value equality: 모든 필드 값으로 동등성 판단</li>
 *   <li>Self-contained business logic: 통계 계산 로직 포함</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * // 빈 통계 (초기화)
 * ParsingStatistics stats = ParsingStatistics.empty();
 *
 * // 전체 통계 생성
 * ParsingStatistics stats = ParsingStatistics.of(
 *     1000,  // totalEntries
 *     995,   // totalProcessed
 *     800,   // certificateCount
 *     50,    // crlCount
 *     780,   // validCount
 *     20,    // invalidCount
 *     5,     // errorCount
 *     15234  // durationMillis
 * );
 *
 * // 성공률 계산
 * double successRate = stats.getSuccessRate();  // 78.39%
 *
 * // 처리율 계산
 * double processingRate = stats.getProcessingRate();  // 99.5%
 * </pre>
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParsingStatistics implements ValueObject {

    /**
     * 전체 엔트리 수 (파일에 포함된 총 엔트리 수)
     */
    @Column(name = "total_entries")
    private int totalEntries;

    /**
     * 처리된 엔트리 수 (성공 + 실패)
     */
    @Column(name = "total_processed")
    private int totalProcessed;

    /**
     * 추출된 인증서 개수
     */
    @Column(name = "certificate_count")
    private int certificateCount;

    /**
     * 추출된 CRL 개수
     */
    @Column(name = "crl_count")
    private int crlCount;

    /**
     * 유효한 인증서 개수
     */
    @Column(name = "valid_count")
    private int validCount;

    /**
     * 무효한 인증서 개수
     */
    @Column(name = "invalid_count")
    private int invalidCount;

    /**
     * 오류 발생 개수
     */
    @Column(name = "error_count")
    private int errorCount;

    /**
     * 파싱 소요 시간 (밀리초)
     */
    @Column(name = "duration_millis")
    private long durationMillis;

    // ========== Static Factory Methods ==========

    /**
     * 빈 통계 생성 (초기화용)
     *
     * @return 모든 값이 0인 ParsingStatistics
     */
    public static ParsingStatistics empty() {
        ParsingStatistics stats = new ParsingStatistics();
        stats.totalEntries = 0;
        stats.totalProcessed = 0;
        stats.certificateCount = 0;
        stats.crlCount = 0;
        stats.validCount = 0;
        stats.invalidCount = 0;
        stats.errorCount = 0;
        stats.durationMillis = 0;
        return stats;
    }

    /**
     * ParsingStatistics 생성
     *
     * @param totalEntries 전체 엔트리 수
     * @param totalProcessed 처리된 엔트리 수
     * @param certificateCount 인증서 개수
     * @param crlCount CRL 개수
     * @param validCount 유효 인증서 개수
     * @param invalidCount 무효 인증서 개수
     * @param errorCount 오류 개수
     * @param durationMillis 소요 시간 (밀리초)
     * @return ParsingStatistics
     * @throws IllegalArgumentException 음수 값이 전달된 경우
     */
    public static ParsingStatistics of(
        int totalEntries,
        int totalProcessed,
        int certificateCount,
        int crlCount,
        int validCount,
        int invalidCount,
        int errorCount,
        long durationMillis
    ) {
        ParsingStatistics stats = new ParsingStatistics();
        stats.totalEntries = totalEntries;
        stats.totalProcessed = totalProcessed;
        stats.certificateCount = certificateCount;
        stats.crlCount = crlCount;
        stats.validCount = validCount;
        stats.invalidCount = invalidCount;
        stats.errorCount = errorCount;
        stats.durationMillis = durationMillis;

        // Validation
        stats.validate();

        return stats;
    }

    // ========== Business Logic Methods ==========

    /**
     * 성공률 계산 (유효한 인증서 / 전체 처리된 엔트리)
     *
     * @return 성공률 (0.0 ~ 100.0)
     */
    public double getSuccessRate() {
        if (totalProcessed == 0) {
            return 0.0;
        }
        return (double) validCount / totalProcessed * 100.0;
    }

    /**
     * 처리율 계산 (처리된 엔트리 / 전체 엔트리)
     *
     * @return 처리율 (0.0 ~ 100.0)
     */
    public double getProcessingRate() {
        if (totalEntries == 0) {
            return 0.0;
        }
        return (double) totalProcessed / totalEntries * 100.0;
    }

    /**
     * 오류율 계산 (오류 발생 / 전체 엔트리)
     *
     * @return 오류율 (0.0 ~ 100.0)
     */
    public double getErrorRate() {
        if (totalEntries == 0) {
            return 0.0;
        }
        return (double) errorCount / totalEntries * 100.0;
    }

    /**
     * 유효율 계산 (유효 인증서 / 전체 인증서)
     *
     * @return 유효율 (0.0 ~ 100.0)
     */
    public double getValidityRate() {
        int totalCerts = validCount + invalidCount;
        if (totalCerts == 0) {
            return 0.0;
        }
        return (double) validCount / totalCerts * 100.0;
    }

    /**
     * 초당 처리 속도 (엔트리/초)
     *
     * @return 처리 속도
     */
    public double getEntriesPerSecond() {
        if (durationMillis == 0) {
            return 0.0;
        }
        return (double) totalProcessed / (durationMillis / 1000.0);
    }

    /**
     * 소요 시간 (초)
     *
     * @return 소요 시간 (초 단위)
     */
    public double getDurationSeconds() {
        return durationMillis / 1000.0;
    }

    /**
     * 파싱 성공 여부 (오류율 < 5%)
     *
     * @return 성공 여부
     */
    public boolean isSuccessful() {
        return getErrorRate() < 5.0;
    }

    // ========== Validation ==========

    private void validate() {
        if (totalEntries < 0) {
            throw new IllegalArgumentException("totalEntries must not be negative");
        }
        if (totalProcessed < 0) {
            throw new IllegalArgumentException("totalProcessed must not be negative");
        }
        if (certificateCount < 0) {
            throw new IllegalArgumentException("certificateCount must not be negative");
        }
        if (crlCount < 0) {
            throw new IllegalArgumentException("crlCount must not be negative");
        }
        if (validCount < 0) {
            throw new IllegalArgumentException("validCount must not be negative");
        }
        if (invalidCount < 0) {
            throw new IllegalArgumentException("invalidCount must not be negative");
        }
        if (errorCount < 0) {
            throw new IllegalArgumentException("errorCount must not be negative");
        }
        if (durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis must not be negative");
        }

        // Business rule: totalProcessed should not exceed totalEntries
        if (totalProcessed > totalEntries) {
            throw new IllegalArgumentException(
                String.format("totalProcessed (%d) cannot exceed totalEntries (%d)",
                    totalProcessed, totalEntries)
            );
        }
    }

    @Override
    public String toString() {
        return String.format(
            "ParsingStatistics[entries=%d, processed=%d, certs=%d, crls=%d, valid=%d, invalid=%d, errors=%d, duration=%.2fs, successRate=%.1f%%]",
            totalEntries,
            totalProcessed,
            certificateCount,
            crlCount,
            validCount,
            invalidCount,
            errorCount,
            getDurationSeconds(),
            getSuccessRate()
        );
    }
}
