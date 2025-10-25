package com.smartcoreinc.localpkd.certificatevalidation.domain.event;

import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFileId;
import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * CrlsExtractedEvent - CRL 추출 완료 Domain Event
 *
 * <p><b>발행 시점</b>: LDIF 파일에서 CRL(Certificate Revocation List)이 추출되어
 * 데이터베이스에 저장될 때</p>
 *
 * <p><b>목적</b>:</p>
 * <ul>
 *   <li>CRL 추출 및 저장 완료 알림</li>
 *   <li>인증서 검증 워크플로우 연동</li>
 *   <li>감사(Audit) 로그 기록</li>
 *   <li>통계 및 모니터링</li>
 * </ul>
 *
 * <p><b>포함 정보</b>:</p>
 * <ul>
 *   <li>parsedFileId: 파싱된 파일의 ID</li>
 *   <li>totalCrlCount: 추출된 CRL 총 개수</li>
 *   <li>successCount: 성공적으로 저장된 CRL 개수</li>
 *   <li>failureCount: 저장 실패한 CRL 개수</li>
 *   <li>crlIssuerNames: 추출된 CSCA 발급자 목록</li>
 *   <li>totalRevokedCertificates: 포함된 폐기 인증서 총 개수</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * CrlsExtractedEvent event = new CrlsExtractedEvent(
 *     parsedFileId,
 *     69,                                           // totalCrlCount
 *     69,                                           // successCount
 *     0,                                            // failureCount
 *     List.of("CSCA-QA", "CSCA-NZ", "CSCA-US"),   // crlIssuerNames
 *     47832                                         // totalRevokedCertificates
 * );
 *
 * // 이벤트 발행
 * applicationEventPublisher.publishEvent(event);
 * }</pre>
 *
 * @see com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile
 * @see com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-23
 */
public class CrlsExtractedEvent implements DomainEvent {

    /**
     * 이벤트 고유 식별자
     */
    private final UUID eventId;

    /**
     * 이벤트 발생 시간
     */
    private final LocalDateTime occurredOn;

    /**
     * 파싱된 파일의 ID
     */
    private final ParsedFileId parsedFileId;

    /**
     * 추출된 CRL 총 개수
     */
    private final int totalCrlCount;

    /**
     * 성공적으로 저장된 CRL 개수
     */
    private final int successCount;

    /**
     * 저장 실패한 CRL 개수
     */
    private final int failureCount;

    /**
     * 추출된 CSCA 발급자 목록 (예: CSCA-QA, CSCA-NZ, CSCA-US)
     */
    private final List<String> crlIssuerNames;

    /**
     * 포함된 폐기 인증서 총 개수
     *
     * <p>모든 CRL에 포함된 폐기 인증서의 합계</p>
     */
    private final long totalRevokedCertificates;

    /**
     * CrlsExtractedEvent 생성
     *
     * @param parsedFileId 파싱된 파일의 ID (null 불가)
     * @param totalCrlCount 추출된 CRL 총 개수 (0 이상)
     * @param successCount 성공적으로 저장된 CRL 개수 (0 이상)
     * @param failureCount 저장 실패한 CRL 개수 (0 이상)
     * @param crlIssuerNames 추출된 CSCA 발급자 목록 (null 불가, 빈 리스트 가능)
     * @param totalRevokedCertificates 포함된 폐기 인증서 총 개수 (0 이상)
     * @throws IllegalArgumentException 필수 필드가 null이거나 값이 음수인 경우
     */
    public CrlsExtractedEvent(
        ParsedFileId parsedFileId,
        int totalCrlCount,
        int successCount,
        int failureCount,
        List<String> crlIssuerNames,
        long totalRevokedCertificates
    ) {
        if (parsedFileId == null) {
            throw new IllegalArgumentException("parsedFileId cannot be null");
        }
        if (totalCrlCount < 0) {
            throw new IllegalArgumentException("totalCrlCount must be non-negative, got: " + totalCrlCount);
        }
        if (successCount < 0) {
            throw new IllegalArgumentException("successCount must be non-negative, got: " + successCount);
        }
        if (failureCount < 0) {
            throw new IllegalArgumentException("failureCount must be non-negative, got: " + failureCount);
        }
        if (crlIssuerNames == null) {
            throw new IllegalArgumentException("crlIssuerNames cannot be null");
        }
        if (totalRevokedCertificates < 0) {
            throw new IllegalArgumentException("totalRevokedCertificates must be non-negative, got: " + totalRevokedCertificates);
        }

        this.eventId = UUID.randomUUID();
        this.occurredOn = LocalDateTime.now();
        this.parsedFileId = parsedFileId;
        this.totalCrlCount = totalCrlCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.crlIssuerNames = Collections.unmodifiableList(crlIssuerNames);
        this.totalRevokedCertificates = totalRevokedCertificates;
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
        return "CrlsExtracted";
    }

    // ========== Getters ==========

    public ParsedFileId getParsedFileId() {
        return parsedFileId;
    }

    public int getTotalCrlCount() {
        return totalCrlCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public List<String> getCrlIssuerNames() {
        return crlIssuerNames;
    }

    public long getTotalRevokedCertificates() {
        return totalRevokedCertificates;
    }

    /**
     * 성공률 계산 (%)
     *
     * @return 성공한 CRL의 비율 (0-100)
     */
    public double getSuccessRate() {
        if (totalCrlCount == 0) {
            return 100.0;
        }
        return (double) successCount / totalCrlCount * 100;
    }

    /**
     * 고유한 발급자(CSCA) 개수
     *
     * @return 발급자 개수
     */
    public int getIssuerCount() {
        return crlIssuerNames.size();
    }

    /**
     * CRL 추출이 완전히 성공했는지 확인
     *
     * @return 실패한 CRL이 없으면 true
     */
    public boolean isFullySuccessful() {
        return failureCount == 0 && successCount == totalCrlCount;
    }

    // ========== equals & hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CrlsExtractedEvent that = (CrlsExtractedEvent) o;
        return Objects.equals(eventId, that.eventId) &&
               Objects.equals(parsedFileId, that.parsedFileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, parsedFileId);
    }

    @Override
    public String toString() {
        return String.format(
            "CrlsExtractedEvent[eventId=%s, parsedFileId=%s, total=%d, success=%d, failure=%d, issuers=%d, revoked=%d, occurredOn=%s]",
            eventId, parsedFileId != null ? parsedFileId.getId() : "null",
            totalCrlCount, successCount, failureCount, crlIssuerNames.size(),
            totalRevokedCertificates, occurredOn
        );
    }
}
