package com.smartcoreinc.localpkd.certificatevalidation.application.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * VerifyTrustChainResponse - Trust Chain 검증 응답 DTO
 *
 * <p><b>용도</b>:</p>
 * <ul>
 *   <li>Trust Chain 검증 결과를 Presentation Layer로 전달</li>
 *   <li>인증서 체인 구조 및 검증 결과 제공</li>
 *   <li>Trust Anchor 정보 제공</li>
 * </ul>
 *
 * <p><b>Trust Chain 구조</b>:</p>
 * <pre>
 * Trust Anchor (CSCA)
 *   ↓ (signs)
 * Intermediate CA (DSC)
 *   ↓ (signs)
 * End Entity Certificate
 * </pre>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * VerifyTrustChainResponse response = verifyTrustChainUseCase.execute(command);
 *
 * if (response.success()) {
 *     log.info("Trust chain verified successfully");
 *     response.certificateChain().forEach(cert ->
 *         log.info("- Level {}: {}", cert.chainLevel(), cert.subjectDn())
 *     );
 * } else {
 *     log.error("Trust chain verification failed: {}", response.message());
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
@Builder
public record VerifyTrustChainResponse(
    /**
     * 검증 성공 여부
     * <p>true: Trust Chain 검증 성공, false: 검증 실패</p>
     */
    boolean success,

    /**
     * 응답 메시지
     * <p>성공 시: "Trust Chain 검증이 완료되었습니다."</p>
     * <p>실패 시: "Trust Chain 검증에 실패했습니다: ..."</p>
     */
    String message,

    /**
     * End Entity 인증서 ID (검증 대상)
     */
    UUID endEntityCertificateId,

    /**
     * End Entity 인증서 주체 DN
     */
    String endEntitySubjectDn,

    /**
     * Trust Anchor 인증서 ID
     * <p>체인의 최상위 인증서 (CSCA)</p>
     */
    UUID trustAnchorCertificateId,

    /**
     * Trust Anchor 인증서 주체 DN
     */
    String trustAnchorSubjectDn,

    /**
     * Trust Anchor 국가 코드
     * <p>CSCA의 국가 코드 (예: "KR", "US")</p>
     */
    String trustAnchorCountryCode,

    /**
     * 인증서 체인 목록
     * <p>End Entity → Intermediate CA → Root CA 순서</p>
     * <p>각 체인 항목은 레벨, 인증서 ID, DN 정보 포함</p>
     */
    List<CertificateChainDto> certificateChain,

    /**
     * Trust Chain 검증 결과
     * <p>true: 체인 유효, false: 체인 무효</p>
     */
    boolean chainValid,

    /**
     * 체인 깊이
     * <p>인증서 체인의 전체 깊이 (End Entity부터 Root CA까지)</p>
     */
    int chainDepth,

    /**
     * 검증 수행 시간
     */
    LocalDateTime validatedAt,

    /**
     * 검증 소요 시간 (밀리초)
     */
    Long durationMillis,

    /**
     * 검증 오류 목록
     * <p>검증 실패 시 오류 상세 정보를 제공합니다.</p>
     */
    List<ValidationErrorDto> validationErrors
) {

    /**
     * CertificateChainDto - 인증서 체인 항목
     */
    @Builder
    public record CertificateChainDto(
        /**
         * 체인 레벨
         * <p>0: End Entity, 1: Intermediate CA, 2: Root CA</p>
         */
        int chainLevel,

        /**
         * 인증서 ID
         */
        UUID certificateId,

        /**
         * 인증서 주체 DN
         */
        String subjectDn,

        /**
         * 인증서 발급자 DN
         */
        String issuerDn,

        /**
         * 인증서 타입
         * <p>CSCA, DSC, DSC_NC, DS, UNKNOWN 중 하나</p>
         */
        String certificateType,

        /**
         * 인증서 상태
         * <p>VALID, EXPIRED, NOT_YET_VALID, REVOKED, INVALID 중 하나</p>
         */
        String status,

        /**
         * 서명 검증 결과
         * <p>true: 서명 유효, false: 서명 무효</p>
         */
        boolean signatureValid
    ) {}

    /**
     * ValidationErrorDto - 검증 오류 상세 정보
     */
    @Builder
    public record ValidationErrorDto(
        String errorCode,
        String errorMessage,
        String severity,
        LocalDateTime occurredAt
    ) {}

    /**
     * 성공 응답 생성
     *
     * @param endEntityCertificateId End Entity 인증서 ID
     * @param endEntitySubjectDn End Entity 인증서 주체 DN
     * @param trustAnchorCertificateId Trust Anchor 인증서 ID
     * @param trustAnchorSubjectDn Trust Anchor 인증서 주체 DN
     * @param trustAnchorCountryCode Trust Anchor 국가 코드
     * @param certificateChain 인증서 체인 목록
     * @param validatedAt 검증 수행 시간
     * @param durationMillis 검증 소요 시간
     * @return VerifyTrustChainResponse
     */
    public static VerifyTrustChainResponse success(
        UUID endEntityCertificateId, String endEntitySubjectDn,
        UUID trustAnchorCertificateId, String trustAnchorSubjectDn,
        String trustAnchorCountryCode,
        List<CertificateChainDto> certificateChain,
        LocalDateTime validatedAt, Long durationMillis
    ) {
        return VerifyTrustChainResponse.builder()
            .success(true)
            .message("Trust Chain 검증이 완료되었습니다.")
            .endEntityCertificateId(endEntityCertificateId)
            .endEntitySubjectDn(endEntitySubjectDn)
            .trustAnchorCertificateId(trustAnchorCertificateId)
            .trustAnchorSubjectDn(trustAnchorSubjectDn)
            .trustAnchorCountryCode(trustAnchorCountryCode)
            .certificateChain(certificateChain)
            .chainValid(true)
            .chainDepth(certificateChain.size())
            .validatedAt(validatedAt)
            .durationMillis(durationMillis)
            .validationErrors(List.of())
            .build();
    }

    /**
     * 실패 응답 생성
     *
     * @param endEntityCertificateId End Entity 인증서 ID
     * @param endEntitySubjectDn End Entity 인증서 주체 DN
     * @param certificateChain 인증서 체인 목록 (부분 체인 가능)
     * @param validatedAt 검증 수행 시간
     * @param durationMillis 검증 소요 시간
     * @param validationErrors 검증 오류 목록
     * @return VerifyTrustChainResponse
     */
    public static VerifyTrustChainResponse failure(
        UUID endEntityCertificateId, String endEntitySubjectDn,
        List<CertificateChainDto> certificateChain,
        LocalDateTime validatedAt, Long durationMillis,
        List<ValidationErrorDto> validationErrors
    ) {
        String errorMessage = validationErrors.isEmpty() ?
            "Trust Chain 검증에 실패했습니다." :
            "Trust Chain 검증에 실패했습니다: " + validationErrors.get(0).errorMessage();

        return VerifyTrustChainResponse.builder()
            .success(false)
            .message(errorMessage)
            .endEntityCertificateId(endEntityCertificateId)
            .endEntitySubjectDn(endEntitySubjectDn)
            .trustAnchorCertificateId(null)
            .trustAnchorSubjectDn(null)
            .trustAnchorCountryCode(null)
            .certificateChain(certificateChain)
            .chainValid(false)
            .chainDepth(certificateChain.size())
            .validatedAt(validatedAt)
            .durationMillis(durationMillis)
            .validationErrors(validationErrors)
            .build();
    }

    /**
     * Trust Anchor를 찾을 수 없는 경우의 실패 응답
     *
     * @param endEntityCertificateId End Entity 인증서 ID
     * @param endEntitySubjectDn End Entity 인증서 주체 DN
     * @param requestedCountryCode 요청한 Trust Anchor 국가 코드
     * @param validatedAt 검증 수행 시간
     * @param durationMillis 검증 소요 시간
     * @return VerifyTrustChainResponse
     */
    public static VerifyTrustChainResponse trustAnchorNotFound(
        UUID endEntityCertificateId, String endEntitySubjectDn,
        String requestedCountryCode,
        LocalDateTime validatedAt, Long durationMillis
    ) {
        String message = requestedCountryCode != null ?
            "Trust Anchor를 찾을 수 없습니다 (요청 국가 코드: " + requestedCountryCode + ")" :
            "Trust Anchor를 찾을 수 없습니다";

        ValidationErrorDto error = ValidationErrorDto.builder()
            .errorCode("TRUST_ANCHOR_NOT_FOUND")
            .errorMessage(message)
            .severity("ERROR")
            .occurredAt(validatedAt)
            .build();

        return VerifyTrustChainResponse.builder()
            .success(false)
            .message(message)
            .endEntityCertificateId(endEntityCertificateId)
            .endEntitySubjectDn(endEntitySubjectDn)
            .trustAnchorCertificateId(null)
            .trustAnchorSubjectDn(null)
            .trustAnchorCountryCode(requestedCountryCode)
            .certificateChain(List.of())
            .chainValid(false)
            .chainDepth(0)
            .validatedAt(validatedAt)
            .durationMillis(durationMillis)
            .validationErrors(List.of(error))
            .build();
    }
}
