package com.smartcoreinc.localpkd.certificatevalidation.application.command;

import lombok.Builder;

/**
 * CheckRevocationCommand - 인증서 폐기 여부 확인 명령 (CQRS Write Side)
 *
 * <p><b>용도</b>:</p>
 * <ul>
 *   <li>Certificate Revocation List (CRL) 기반 폐기 여부 확인</li>
 *   <li>일련번호 기반 인증서 폐기 상태 조회</li>
 *   <li>폐기 정보 업데이트</li>
 * </ul>
 *
 * <p><b>폐기 확인 프로세스</b>:</p>
 * <ol>
 *   <li>발급자 DN을 통해 해당 CRL 조회</li>
 *   <li>일련번호 기반 폐기 목록 검사</li>
 *   <li>폐기된 경우 폐기 날짜 및 사유 기록</li>
 *   <li>검증 결과 업데이트</li>
 * </ol>
 *
 * <p><b>Fail-Open 정책</b>:</p>
 * <ul>
 *   <li>CRL을 가져올 수 없는 경우: 폐기되지 않은 것으로 간주 (가용성 우선)</li>
 *   <li>시스템 오류 시: 로그 기록 후 계속 진행</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * CheckRevocationCommand command = CheckRevocationCommand.builder()
 *     .certificateId(UUID.randomUUID())
 *     .issuerDn("CN=CSCA KR, O=Korea, C=KR")
 *     .serialNumber("0a1b2c3d4e5f")
 *     .forceFresh(false)  // 캐시된 CRL 허용
 *     .build();
 *
 * CheckRevocationResponse response = checkRevocationUseCase.execute(command);
 * }</pre>
 *
 * @see CheckRevocationUseCase
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Builder
public record CheckRevocationCommand(
    /**
     * 폐기 여부를 확인할 인증서 UUID
     * null이 아니어야 함
     */
    String certificateId,

    /**
     * 인증서 발급자 DN (Issuer Distinguished Name)
     * CRL 조회를 위해 필요
     * 예: "CN=CSCA KR, O=Korea, C=KR"
     */
    String issuerDn,

    /**
     * 인증서 일련번호 (Serial Number)
     * CRL에서 폐기 목록 검사에 사용
     * 16진수 문자열 형식
     */
    String serialNumber,

    /**
     * 신규 CRL 강제 다운로드 여부
     * true: 항상 최신 CRL 다운로드
     * false: 캐시된 CRL 사용 (성능 우선)
     * 기본값: false
     */
    boolean forceFresh,

    /**
     * CRL 다운로드 타임아웃 (초)
     * 네트워크 지연으로 인한 무한 대기 방지
     * 기본값: 30초
     */
    int crlFetchTimeoutSeconds,

    /**
     * 요청자 정보 (감사용, 선택사항)
     */
    String requestedBy
) {

    /**
     * 명령 검증
     *
     * @throws IllegalArgumentException 필수 필드가 null이거나 형식이 맞지 않는 경우
     */
    public void validate() {
        if (certificateId == null || certificateId.isBlank()) {
            throw new IllegalArgumentException("certificateId must not be blank");
        }

        if (issuerDn == null || issuerDn.isBlank()) {
            throw new IllegalArgumentException("issuerDn must not be blank");
        }

        if (serialNumber == null || serialNumber.isBlank()) {
            throw new IllegalArgumentException("serialNumber must not be blank");
        }

        if (crlFetchTimeoutSeconds < 5 || crlFetchTimeoutSeconds > 300) {
            throw new IllegalArgumentException(
                "crlFetchTimeoutSeconds must be between 5 and 300, but got: " + crlFetchTimeoutSeconds);
        }
    }

    /**
     * 기본 옵션으로 Command 생성
     *
     * @param certificateId 검사할 인증서 ID
     * @param issuerDn 발급자 DN
     * @param serialNumber 일련번호
     * @return CheckRevocationCommand
     */
    public static CheckRevocationCommand withDefaults(String certificateId, String issuerDn, String serialNumber) {
        return CheckRevocationCommand.builder()
            .certificateId(certificateId)
            .issuerDn(issuerDn)
            .serialNumber(serialNumber)
            .forceFresh(false)
            .crlFetchTimeoutSeconds(30)
            .build();
    }

    /**
     * 신규 CRL 강제 다운로드 Command 생성
     *
     * @param certificateId 검사할 인증서 ID
     * @param issuerDn 발급자 DN
     * @param serialNumber 일련번호
     * @return CheckRevocationCommand
     */
    public static CheckRevocationCommand withForceFresh(String certificateId, String issuerDn, String serialNumber) {
        return CheckRevocationCommand.builder()
            .certificateId(certificateId)
            .issuerDn(issuerDn)
            .serialNumber(serialNumber)
            .forceFresh(true)
            .crlFetchTimeoutSeconds(30)
            .build();
    }
}
