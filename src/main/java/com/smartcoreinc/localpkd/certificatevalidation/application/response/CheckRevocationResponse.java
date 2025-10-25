package com.smartcoreinc.localpkd.certificatevalidation.application.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CheckRevocationResponse - 인증서 폐기 여부 확인 응답 DTO
 *
 * <p><b>용도</b>:</p>
 * <ul>
 *   <li>Certificate Revocation List (CRL) 기반 폐기 확인 결과 제공</li>
 *   <li>폐기 상태, 폐기 날짜, 폐기 사유 등 상세 정보 제공</li>
 *   <li>CRL 다운로드 실패 시 Fail-Open 정책에 따른 결과 제공</li>
 * </ul>
 *
 * <p><b>폐기 상태 해석</b>:</p>
 * <ul>
 *   <li>NOT_REVOKED: 인증서가 폐기되지 않았음</li>
 *   <li>REVOKED: 인증서가 폐기됨 (폐기 날짜와 사유 포함)</li>
 *   <li>CRL_UNAVAILABLE: CRL을 가져올 수 없음 (NOT_REVOKED로 처리, Fail-Open)</li>
 *   <li>CRL_FETCH_TIMEOUT: CRL 다운로드 타임아웃 (NOT_REVOKED로 처리, Fail-Open)</li>
 * </ul>
 *
 * <p><b>Fail-Open 정책</b>:</p>
 * <pre>
 * CRL을 가져올 수 없는 경우, 인증서가 폐기되지 않은 것으로 간주합니다.
 * 이는 가용성을 우선시하는 보안 정책입니다.
 * (시스템 불가용보다 보안 위험을 선택)
 * </pre>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * CheckRevocationResponse response = checkRevocationUseCase.execute(command);
 *
 * if ("REVOKED".equals(response.revocationStatus())) {
 *     log.error("Certificate is revoked: revokedAt={}, reason={}",
 *         response.revokedAt(), response.revocationReason());
 * } else if ("NOT_REVOKED".equals(response.revocationStatus())) {
 *     log.info("Certificate is not revoked");
 * }
 * }</pre>
 *
 * @see CheckRevocationCommand
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Builder
public record CheckRevocationResponse(
    /**
     * 폐기 확인 성공 여부
     * <p>true: CRL 확인 완료 (폐기 여부 판정 가능)</p>
     * <p>false: CRL 확인 실패 (Fail-Open으로 NOT_REVOKED로 처리)</p>
     */
    boolean success,

    /**
     * 응답 메시지
     * <p>성공 시: "인증서 폐기 여부 확인이 완료되었습니다."</p>
     * <p>실패 시: "CRL을 가져올 수 없습니다: ..."</p>
     */
    String message,

    /**
     * 인증서 ID
     */
    UUID certificateId,

    /**
     * 인증서 일련번호
     */
    String serialNumber,

    /**
     * 폐기 상태
     * <p>NOT_REVOKED: 폐기되지 않음</p>
     * <p>REVOKED: 폐기됨</p>
     * <p>CRL_UNAVAILABLE: CRL 사용 불가 (NOT_REVOKED로 처리)</p>
     * <p>CRL_FETCH_TIMEOUT: CRL 다운로드 타임아웃 (NOT_REVOKED로 처리)</p>
     */
    String revocationStatus,

    /**
     * 폐기 여부
     * <p>true: 인증서가 폐기됨</p>
     * <p>false: 인증서가 폐기되지 않음</p>
     */
    boolean revoked,

    /**
     * 폐기 날짜
     * <p>인증서가 폐기된 날짜 (UTC)</p>
     * <p>폐기되지 않은 경우 null</p>
     */
    LocalDateTime revokedAt,

    /**
     * 폐기 사유 코드
     * <p>예: unspecified(0), keyCompromise(1), cACompromise(2), superseded(3)</p>
     * <p>폐기되지 않은 경우 null</p>
     */
    String revocationReasonCode,

    /**
     * 폐기 사유 설명
     * <p>사람이 읽을 수 있는 형식의 폐기 사유</p>
     * <p>폐기되지 않은 경우 null</p>
     */
    String revocationReason,

    /**
     * CRL 발급자 DN
     * <p>폐기 여부를 확인한 CRL의 발급자</p>
     */
    String crlIssuerDn,

    /**
     * CRL 갱신 날짜
     * <p>사용된 CRL의 갱신 날짜</p>
     */
    LocalDateTime crlLastUpdate,

    /**
     * CRL 다음 갱신 날짜
     * <p>CRL의 다음 갱신 예정 날짜</p>
     */
    LocalDateTime crlNextUpdate,

    /**
     * CRL 다운로드 캐시 사용 여부
     * <p>true: 캐시된 CRL 사용 (성능 우선)</p>
     * <p>false: 신규 CRL 다운로드</p>
     */
    boolean crlFromCache,

    /**
     * 확인 수행 시간
     */
    LocalDateTime checkedAt,

    /**
     * 확인 소요 시간 (밀리초)
     */
    Long durationMillis
) {

    /**
     * 성공 응답 (NOT_REVOKED) 생성
     *
     * @param certificateId 인증서 ID
     * @param serialNumber 일련번호
     * @param crlIssuerDn CRL 발급자 DN
     * @param crlLastUpdate CRL 마지막 갱신 시간
     * @param crlNextUpdate CRL 다음 갱신 시간
     * @param crlFromCache 캐시 사용 여부
     * @param checkedAt 확인 시간
     * @param durationMillis 소요 시간
     * @return CheckRevocationResponse
     */
    public static CheckRevocationResponse notRevoked(
        UUID certificateId, String serialNumber,
        String crlIssuerDn, LocalDateTime crlLastUpdate, LocalDateTime crlNextUpdate,
        boolean crlFromCache, LocalDateTime checkedAt, Long durationMillis
    ) {
        return CheckRevocationResponse.builder()
            .success(true)
            .message("인증서 폐기 여부 확인이 완료되었습니다.")
            .certificateId(certificateId)
            .serialNumber(serialNumber)
            .revocationStatus("NOT_REVOKED")
            .revoked(false)
            .revokedAt(null)
            .revocationReasonCode(null)
            .revocationReason(null)
            .crlIssuerDn(crlIssuerDn)
            .crlLastUpdate(crlLastUpdate)
            .crlNextUpdate(crlNextUpdate)
            .crlFromCache(crlFromCache)
            .checkedAt(checkedAt)
            .durationMillis(durationMillis)
            .build();
    }

    /**
     * 성공 응답 (REVOKED) 생성
     *
     * @param certificateId 인증서 ID
     * @param serialNumber 일련번호
     * @param revokedAt 폐기 날짜
     * @param revocationReasonCode 폐기 사유 코드
     * @param revocationReason 폐기 사유 설명
     * @param crlIssuerDn CRL 발급자 DN
     * @param crlLastUpdate CRL 마지막 갱신 시간
     * @param crlNextUpdate CRL 다음 갱신 시간
     * @param checkedAt 확인 시간
     * @param durationMillis 소요 시간
     * @return CheckRevocationResponse
     */
    public static CheckRevocationResponse revoked(
        UUID certificateId, String serialNumber,
        LocalDateTime revokedAt, String revocationReasonCode, String revocationReason,
        String crlIssuerDn, LocalDateTime crlLastUpdate, LocalDateTime crlNextUpdate,
        LocalDateTime checkedAt, Long durationMillis
    ) {
        return CheckRevocationResponse.builder()
            .success(true)
            .message("인증서가 폐기되었습니다.")
            .certificateId(certificateId)
            .serialNumber(serialNumber)
            .revocationStatus("REVOKED")
            .revoked(true)
            .revokedAt(revokedAt)
            .revocationReasonCode(revocationReasonCode)
            .revocationReason(revocationReason)
            .crlIssuerDn(crlIssuerDn)
            .crlLastUpdate(crlLastUpdate)
            .crlNextUpdate(crlNextUpdate)
            .crlFromCache(false)
            .checkedAt(checkedAt)
            .durationMillis(durationMillis)
            .build();
    }

    /**
     * Fail-Open 응답 (CRL 사용 불가) 생성
     *
     * <p>Fail-Open 정책에 따라 CRL을 가져올 수 없는 경우 NOT_REVOKED로 처리합니다.</p>
     *
     * @param certificateId 인증서 ID
     * @param serialNumber 일련번호
     * @param errorMessage CRL 다운로드 오류 메시지
     * @param checkedAt 확인 시간
     * @param durationMillis 소요 시간
     * @return CheckRevocationResponse
     */
    public static CheckRevocationResponse failOpenCrlUnavailable(
        UUID certificateId, String serialNumber,
        String errorMessage, LocalDateTime checkedAt, Long durationMillis
    ) {
        return CheckRevocationResponse.builder()
            .success(false)
            .message("CRL을 가져올 수 없습니다: " + errorMessage + " (NOT_REVOKED로 처리됨)")
            .certificateId(certificateId)
            .serialNumber(serialNumber)
            .revocationStatus("CRL_UNAVAILABLE")
            .revoked(false)  // Fail-Open: NOT_REVOKED로 처리
            .revokedAt(null)
            .revocationReasonCode(null)
            .revocationReason(null)
            .crlIssuerDn(null)
            .crlLastUpdate(null)
            .crlNextUpdate(null)
            .crlFromCache(false)
            .checkedAt(checkedAt)
            .durationMillis(durationMillis)
            .build();
    }

    /**
     * Fail-Open 응답 (CRL 타임아웃) 생성
     *
     * <p>Fail-Open 정책에 따라 CRL 다운로드 타임아웃 시 NOT_REVOKED로 처리합니다.</p>
     *
     * @param certificateId 인증서 ID
     * @param serialNumber 일련번호
     * @param timeoutSeconds 타임아웃 초
     * @param checkedAt 확인 시간
     * @param durationMillis 소요 시간
     * @return CheckRevocationResponse
     */
    public static CheckRevocationResponse failOpenCrlTimeout(
        UUID certificateId, String serialNumber,
        int timeoutSeconds, LocalDateTime checkedAt, Long durationMillis
    ) {
        return CheckRevocationResponse.builder()
            .success(false)
            .message("CRL 다운로드 타임아웃 (" + timeoutSeconds + "초): NOT_REVOKED로 처리됨")
            .certificateId(certificateId)
            .serialNumber(serialNumber)
            .revocationStatus("CRL_FETCH_TIMEOUT")
            .revoked(false)  // Fail-Open: NOT_REVOKED로 처리
            .revokedAt(null)
            .revocationReasonCode(null)
            .revocationReason(null)
            .crlIssuerDn(null)
            .crlLastUpdate(null)
            .crlNextUpdate(null)
            .crlFromCache(false)
            .checkedAt(checkedAt)
            .durationMillis(durationMillis)
            .build();
    }
}
