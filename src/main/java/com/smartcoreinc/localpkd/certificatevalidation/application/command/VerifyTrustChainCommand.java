package com.smartcoreinc.localpkd.certificatevalidation.application.command;

import lombok.Builder;

/**
 * VerifyTrustChainCommand - Trust Chain 검증 명령 (CQRS Write Side)
 *
 * <p><b>용도</b>:</p>
 * <ul>
 *   <li>X.509 인증서 체인 검증 (CSCA → DSC → Document)</li>
 *   <li>인증서 계층 구조 검증 (Root CA → Intermediate CA → End Entity)</li>
 *   <li>Trust Anchor 기반 신뢰 검증</li>
 * </ul>
 *
 * <p><b>Trust Chain 검증 프로세스</b>:</p>
 * <ol>
 *   <li>End Entity 인증서 (DSC 또는 DS) 식별</li>
 *   <li>Issuer DN을 통해 상위 인증서 (CSCA) 조회</li>
 *   <li>각 인증서의 서명을 상위 인증서의 공개키로 검증</li>
 *   <li>Root CA (CSCA)가 Trust Anchor에 포함되어 있는지 확인</li>
 *   <li>각 인증서의 유효기간 검증</li>
 *   <li>각 인증서의 폐기 여부 확인 (선택)</li>
 * </ol>
 *
 * <p><b>ICAO PKD Trust Chain 예시</b>:</p>
 * <pre>
 * CSCA (Country Signing CA)
 *   ↓ (signs)
 * DSC (Document Signer Certificate)
 *   ↓ (signs)
 * ePassport Document
 * </pre>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * VerifyTrustChainCommand command = VerifyTrustChainCommand.builder()
 *     .certificateId(dscId)  // DSC 인증서
 *     .trustAnchorCountryCode("KR")  // 한국 CSCA를 Trust Anchor로 사용
 *     .checkRevocation(false)  // CRL 확인 비활성화
 *     .build();
 *
 * VerifyTrustChainResponse response = verifyTrustChainUseCase.execute(command);
 * }</pre>
 *
 * @see com.smartcoreinc.localpkd.certificatevalidation.application.usecase.VerifyTrustChainUseCase
 * @see com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate
 * @see com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
@Builder
public record VerifyTrustChainCommand(
    /**
     * 검증할 인증서의 ID (End Entity Certificate)
     * <p>일반적으로 DSC 또는 DS 인증서입니다.</p>
     */
    String certificateId,

    /**
     * Trust Anchor로 사용할 국가 코드 (선택)
     * <p>지정된 국가의 CSCA만 Trust Anchor로 인정합니다.</p>
     * <p>null인 경우 모든 CSCA를 Trust Anchor로 인정합니다.</p>
     */
    String trustAnchorCountryCode,

    /**
     * 폐기 여부 확인 (CRL Check)
     * <p>체인의 각 인증서가 폐기되지 않았는지 확인합니다.</p>
     */
    boolean checkRevocation,

    /**
     * 유효기간 검증 여부
     * <p>체인의 각 인증서가 현재 시간 기준으로 유효한지 확인합니다.</p>
     */
    boolean validateValidity,

    /**
     * 최대 체인 깊이
     * <p>인증서 체인의 최대 깊이를 제한합니다 (무한 루프 방지).</p>
     * <p>일반적으로 ICAO PKD는 2-3 단계입니다 (CSCA → DSC → Document).</p>
     */
    int maxChainDepth
) {

    /**
     * 기본 검증 옵션으로 Command 생성
     *
     * <p>기본값:</p>
     * <ul>
     *   <li>trustAnchorCountryCode: null (모든 CSCA 허용)</li>
     *   <li>checkRevocation: false (성능 이슈)</li>
     *   <li>validateValidity: true</li>
     *   <li>maxChainDepth: 5</li>
     * </ul>
     *
     * @param certificateId 검증할 인증서의 ID
     * @return VerifyTrustChainCommand
     */
    public static VerifyTrustChainCommand withDefaults(String certificateId) {
        return VerifyTrustChainCommand.builder()
            .certificateId(certificateId)
            .trustAnchorCountryCode(null)
            .checkRevocation(false)
            .validateValidity(true)
            .maxChainDepth(5)
            .build();
    }

    /**
     * 특정 국가의 Trust Anchor만 허용하는 Command 생성
     *
     * @param certificateId 검증할 인증서의 ID
     * @param trustAnchorCountryCode Trust Anchor 국가 코드 (예: "KR", "US")
     * @return VerifyTrustChainCommand
     */
    public static VerifyTrustChainCommand withTrustAnchor(String certificateId, String trustAnchorCountryCode) {
        return VerifyTrustChainCommand.builder()
            .certificateId(certificateId)
            .trustAnchorCountryCode(trustAnchorCountryCode)
            .checkRevocation(false)
            .validateValidity(true)
            .maxChainDepth(5)
            .build();
    }

    /**
     * Command 검증
     *
     * <p>필수 필드 및 비즈니스 규칙을 검증합니다.</p>
     *
     * @throws IllegalArgumentException 검증 실패 시
     */
    public void validate() {
        if (certificateId == null || certificateId.isBlank()) {
            throw new IllegalArgumentException("certificateId must not be blank");
        }

        if (maxChainDepth < 1) {
            throw new IllegalArgumentException("maxChainDepth must be at least 1, but got: " + maxChainDepth);
        }

        if (maxChainDepth > 10) {
            throw new IllegalArgumentException("maxChainDepth must not exceed 10, but got: " + maxChainDepth);
        }

        // trustAnchorCountryCode는 선택 사항이므로 null 허용
        if (trustAnchorCountryCode != null && !trustAnchorCountryCode.matches("^[A-Z]{2}$")) {
            throw new IllegalArgumentException(
                "trustAnchorCountryCode must be a 2-letter country code (e.g., KR, US), but got: " + trustAnchorCountryCode);
        }
    }
}
