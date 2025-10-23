package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * ValidityPeriod - 인증서 유효기간 Value Object
 *
 * <p><b>DDD Value Object Pattern</b>:</p>
 * <ul>
 *   <li>Immutability: 생성 후 변경 불가</li>
 *   <li>Self-validation: 생성 시 비즈니스 규칙 검증</li>
 *   <li>Value equality: 필드 값으로 동등성 판단</li>
 * </ul>
 *
 * <p><b>비즈니스 규칙</b>:</p>
 * <ul>
 *   <li>notBefore는 notAfter보다 반드시 이전이어야 함</li>
 *   <li>유효기간은 최소 1초 이상이어야 함</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * LocalDateTime now = LocalDateTime.now();
 * LocalDateTime notBefore = now.minusYears(5);
 * LocalDateTime notAfter = now.plusYears(5);
 *
 * ValidityPeriod validity = ValidityPeriod.of(notBefore, notAfter);
 *
 * // 유효 여부 확인
 * boolean isExpired = validity.isExpired();           // false
 * boolean isNotYetValid = validity.isNotYetValid();   // false
 * boolean isCurrent = validity.isCurrentlyValid();    // true
 *
 * // 만료까지 남은 일수
 * long daysRemaining = validity.daysUntilExpiration();  // ~1825
 * }</pre>
 *
 * @see ValueObject
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-23
 */
@Embeddable
public class ValidityPeriod implements ValueObject {

    /**
     * 인증서 유효 시작일
     */
    @Column(name = "not_before", nullable = false)
    private LocalDateTime notBefore;

    /**
     * 인증서 유효 종료일
     */
    @Column(name = "not_after", nullable = false)
    private LocalDateTime notAfter;

    /**
     * JPA용 기본 생성자 (protected)
     */
    protected ValidityPeriod() {
    }

    /**
     * ValidityPeriod 생성 (Static Factory Method)
     *
     * @param notBefore 유효 시작일
     * @param notAfter 유효 종료일
     * @return ValidityPeriod
     * @throws IllegalArgumentException 유효하지 않은 기간인 경우
     */
    public static ValidityPeriod of(LocalDateTime notBefore, LocalDateTime notAfter) {
        if (notBefore == null) {
            throw new IllegalArgumentException("notBefore cannot be null");
        }
        if (notAfter == null) {
            throw new IllegalArgumentException("notAfter cannot be null");
        }
        if (!notBefore.isBefore(notAfter)) {
            throw new IllegalArgumentException(
                String.format("notBefore (%s) must be before notAfter (%s)",
                    notBefore, notAfter)
            );
        }

        // 최소 1초 이상의 유효기간 검증
        long durationSeconds = ChronoUnit.SECONDS.between(notBefore, notAfter);
        if (durationSeconds < 1) {
            throw new IllegalArgumentException(
                "Validity period must be at least 1 second"
            );
        }

        ValidityPeriod period = new ValidityPeriod();
        period.notBefore = notBefore;
        period.notAfter = notAfter;

        return period;
    }

    // ========== Getters ==========

    public LocalDateTime getNotBefore() {
        return notBefore;
    }

    public LocalDateTime getNotAfter() {
        return notAfter;
    }

    // ========== Business Logic Methods ==========

    /**
     * 인증서 만료 여부
     *
     * @return 만료 여부
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(notAfter);
    }

    /**
     * 인증서 유효기간 이전 여부
     *
     * @return 유효기간 이전 여부
     */
    public boolean isNotYetValid() {
        return LocalDateTime.now().isBefore(notBefore);
    }

    /**
     * 현재 시점에 유효한 인증서 여부
     *
     * @return 현재 유효 여부
     */
    public boolean isCurrentlyValid() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(notBefore) && !now.isAfter(notAfter);
    }

    /**
     * 만료까지 남은 일수
     *
     * @return 일수 (정수, 소수점 이하 버림)
     */
    public long daysUntilExpiration() {
        return ChronoUnit.DAYS.between(LocalDateTime.now(), notAfter);
    }

    /**
     * 만료까지 남은 시간
     *
     * @return 시간 (정수)
     */
    public long hoursUntilExpiration() {
        return ChronoUnit.HOURS.between(LocalDateTime.now(), notAfter);
    }

    /**
     * 조만간 만료 예정 여부 (기본 30일)
     *
     * @return 조만간 만료 예정 여부
     */
    public boolean isExpiringSoon() {
        return isExpiringSoon(30);
    }

    /**
     * 조만간 만료 예정 여부 (경고 범위)
     *
     * @param daysThreshold 경고 범위 (일)
     * @return 조만간 만료 예정 여부
     */
    public boolean isExpiringSoon(int daysThreshold) {
        long daysRemaining = daysUntilExpiration();
        return daysRemaining >= 0 && daysRemaining <= daysThreshold;
    }

    /**
     * 유효기간 길이 (일)
     *
     * @return 유효기간 (일 단위)
     */
    public long validityLengthInDays() {
        return ChronoUnit.DAYS.between(notBefore, notAfter);
    }

    /**
     * 유효기간 길이 (연)
     *
     * @return 유효기간 (연 단위, 소수점 이하 1자리)
     */
    public double validityLengthInYears() {
        long days = validityLengthInDays();
        return (double) days / 365.0;
    }

    /**
     * 유효기간 경과율 (%)
     *
     * @return 경과율 (0-100, 범위 초과 가능)
     */
    public double expirationProgress() {
        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(notBefore)) {
            return 0.0;
        }

        long totalSeconds = ChronoUnit.SECONDS.between(notBefore, notAfter);
        long elapsedSeconds = ChronoUnit.SECONDS.between(notBefore, now);

        return (double) elapsedSeconds / totalSeconds * 100.0;
    }

    // ========== equals & hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidityPeriod that = (ValidityPeriod) o;
        return Objects.equals(notBefore, that.notBefore) &&
               Objects.equals(notAfter, that.notAfter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(notBefore, notAfter);
    }

    @Override
    public String toString() {
        return String.format("ValidityPeriod[%s ~ %s]", notBefore, notAfter);
    }
}
