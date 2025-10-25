package com.smartcoreinc.localpkd.certificatevalidation.domain.event;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ValidationFailedEvent - 인증서 검증 실패 Domain Event
 *
 * <p><b>발행 시점</b>: Certificate의 검증이 실패하여 하나 이상의 검증 규칙을 위반할 때</p>
 *
 * <p><b>검증 실패 사유</b>:</p>
 * <ul>
 *   <li>SIGNATURE_INVALID: 서명 검증 실패</li>
 *   <li>CHAIN_INVALID: Trust Chain 검증 실패</li>
 *   <li>CERTIFICATE_REVOKED: 인증서 폐기됨</li>
 *   <li>EXPIRED: 유효기간 만료</li>
 *   <li>NOT_YET_VALID: 아직 유효하지 않음</li>
 *   <li>INVALID_CONSTRAINTS: 제약사항 위반</li>
 *   <li>UNKNOWN_ERROR: 알 수 없는 오류</li>
 * </ul>
 *
 * <p><b>용도</b>:</p>
 * <ul>
 *   <li>검증 실패 원인 기록</li>
 *   <li>보안 경고 생성</li>
 *   <li>오류 분석 및 디버깅</li>
 *   <li>감사 로그 및 통계</li>
 *   <li>알림 및 모니터링</li>
 * </ul>
 *
 * <p><b>보안 영향</b>:</p>
 * <ul>
 *   <li>이 인증서는 신뢰할 수 없음</li>
 *   <li>이 인증서로 서명된 문서는 거부</li>
 *   <li>추가 조사 필요</li>
 * </ul>
 *
 * @see com.smartcoreinc.localpkd.certificatevalidation.application.usecase.ValidateCertificateUseCase
 * @see DomainEvent
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Getter
public class ValidationFailedEvent implements DomainEvent {

    /**
     * 검증 실패 이유 코드
     */
    public enum FailureReason {
        SIGNATURE_INVALID("서명 검증 실패"),
        CHAIN_INVALID("Trust Chain 검증 실패"),
        CERTIFICATE_REVOKED("인증서 폐기됨"),
        EXPIRED("유효기간 만료"),
        NOT_YET_VALID("아직 유효하지 않음"),
        INVALID_CONSTRAINTS("제약사항 위반"),
        UNKNOWN_ERROR("알 수 없는 오류");

        private final String description;

        FailureReason(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 이벤트 고유 식별자
     */
    private final UUID eventId;

    /**
     * 이벤트 발생 시간
     */
    private final LocalDateTime occurredOn;

    /**
     * 검증 실패한 인증서의 ID
     */
    private final CertificateId certificateId;

    /**
     * 인증서 주체 DN (Subject Distinguished Name)
     */
    private final String subjectDn;

    /**
     * 주요 실패 이유
     */
    private final FailureReason primaryFailureReason;

    /**
     * 실패 상세 메시지
     */
    private final String failureMessage;

    /**
     * 부차 실패 사유 목록
     * <p>여러 검증이 동시에 실패할 수 있음</p>
     */
    private final List<String> additionalErrors;

    /**
     * 검증 소요 시간 (밀리초)
     */
    private final Long durationMillis;

    /**
     * ValidationFailedEvent 생성
     *
     * @param certificateId 검증 실패 인증서 ID
     * @param subjectDn 인증서 주체 DN
     * @param primaryFailureReason 주요 실패 이유
     * @param failureMessage 상세 메시지
     * @param additionalErrors 부차 오류 목록
     * @param durationMillis 검증 소요 시간
     * @throws IllegalArgumentException 필수 필드가 null인 경우
     */
    public ValidationFailedEvent(
        CertificateId certificateId,
        String subjectDn,
        FailureReason primaryFailureReason,
        String failureMessage,
        List<String> additionalErrors,
        Long durationMillis
    ) {
        if (certificateId == null) {
            throw new IllegalArgumentException("certificateId cannot be null");
        }
        if (subjectDn == null || subjectDn.isBlank()) {
            throw new IllegalArgumentException("subjectDn cannot be null or blank");
        }
        if (primaryFailureReason == null) {
            throw new IllegalArgumentException("primaryFailureReason cannot be null");
        }
        if (failureMessage == null || failureMessage.isBlank()) {
            throw new IllegalArgumentException("failureMessage cannot be null or blank");
        }

        this.eventId = UUID.randomUUID();
        this.occurredOn = LocalDateTime.now();
        this.certificateId = certificateId;
        this.subjectDn = subjectDn;
        this.primaryFailureReason = primaryFailureReason;
        this.failureMessage = failureMessage;
        this.additionalErrors = additionalErrors != null ? additionalErrors : List.of();
        this.durationMillis = durationMillis != null ? durationMillis : 0L;
    }

    // ========== DomainEvent Implementation ==========

    @Override
    public UUID eventId() {
        return eventId;
    }

    @Override
    public LocalDateTime occurredOn() {
        return occurredOn;
    }

    @Override
    public String eventType() {
        return "ValidationFailed";
    }

    // ========== Utility Methods ==========

    /**
     * 모든 오류 메시지 (주요 + 부차)를 반환합니다
     *
     * @return 전체 오류 메시지 리스트
     */
    public List<String> getAllErrorMessages() {
        List<String> allErrors = new java.util.ArrayList<>();
        allErrors.add(failureMessage);
        allErrors.addAll(additionalErrors);
        return allErrors;
    }

    /**
     * 부차 오류 수를 반환합니다
     *
     * @return 부차 오류 수
     */
    public int getAdditionalErrorCount() {
        return additionalErrors.size();
    }

    /**
     * 검증 실패가 치명적인지 여부를 반환합니다
     *
     * <p>REVOKED, EXPIRED 등은 즉시 거부해야 함</p>
     *
     * @return 치명적 실패 여부
     */
    public boolean isCriticalFailure() {
        return primaryFailureReason == FailureReason.CERTIFICATE_REVOKED ||
               primaryFailureReason == FailureReason.EXPIRED ||
               primaryFailureReason == FailureReason.SIGNATURE_INVALID;
    }

    // ========== equals & hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationFailedEvent that = (ValidationFailedEvent) o;
        return Objects.equals(eventId, that.eventId) &&
               Objects.equals(certificateId, that.certificateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, certificateId);
    }

    @Override
    public String toString() {
        return String.format(
            "ValidationFailedEvent[eventId=%s, certificateId=%s, reason=%s, message=%s, additionalErrors=%d, duration=%dms, occurredOn=%s]",
            eventId,
            certificateId != null ? certificateId.getId() : "null",
            primaryFailureReason.name(),
            failureMessage,
            additionalErrors.size(),
            durationMillis,
            occurredOn
        );
    }
}
