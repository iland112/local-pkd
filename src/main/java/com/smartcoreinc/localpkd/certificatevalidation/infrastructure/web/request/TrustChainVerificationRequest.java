package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TrustChainVerificationRequest - Trust Chain 검증 API 요청
 *
 * <p><b>Endpoint</b>: POST /api/verify-trust-chain</p>
 *
 * <p><b>요청 예시</b>:</p>
 * <pre>{@code
 * POST /api/verify-trust-chain
 * Content-Type: application/json
 *
 * {
 *   "certificateId": "550e8400-e29b-41d4-a716-446655440000",
 *   "trustAnchorCountryCode": "KR",
 *   "checkRevocation": false,
 *   "validateValidity": true,
 *   "maxChainDepth": 5
 * }
 * }</pre>
 *
 * <p><b>Trust Chain 검증</b>:</p>
 * <ul>
 *   <li>End Entity Certificate부터 Trust Anchor (CSCA)까지의 경로 구축</li>
 *   <li>각 인증서의 서명 검증</li>
 *   <li>Trust Anchor 도달 확인</li>
 *   <li>선택적으로 유효기간 및 폐기 여부 확인</li>
 * </ul>
 *
 * @see TrustChainVerificationResponse
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrustChainVerificationRequest {

    /**
     * 검증할 인증서 UUID (End Entity Certificate)
     * <p>필수 항목</p>
     */
    @JsonProperty("certificateId")
    private String certificateId;

    /**
     * Trust Anchor 국가 코드 (선택사항)
     * <p>예: "KR", "US", "JP"</p>
     * <p>null이면 모든 CSCA 허용</p>
     */
    @JsonProperty("trustAnchorCountryCode")
    private String trustAnchorCountryCode;

    /**
     * CRL 확인 여부
     * <p>기본값: false (성능 이유)</p>
     */
    @JsonProperty("checkRevocation")
    @Builder.Default
    private boolean checkRevocation = false;

    /**
     * 유효기간 검증 여부
     * <p>기본값: true</p>
     */
    @JsonProperty("validateValidity")
    @Builder.Default
    private boolean validateValidity = true;

    /**
     * 최대 체인 깊이
     * <p>무한 루프 방지용 (기본값: 5)</p>
     * <p>최소: 1, 최대: 10</p>
     */
    @JsonProperty("maxChainDepth")
    @Builder.Default
    private int maxChainDepth = 5;

    /**
     * 요청 검증
     *
     * @throws IllegalArgumentException 필수 필드가 invalid인 경우
     */
    public void validate() {
        if (certificateId == null || certificateId.isBlank()) {
            throw new IllegalArgumentException("certificateId must not be null or empty");
        }

        if (maxChainDepth < 1 || maxChainDepth > 10) {
            throw new IllegalArgumentException(
                "maxChainDepth must be between 1 and 10, but got: " + maxChainDepth);
        }

        // trustAnchorCountryCode는 선택사항이지만, 2자리 코드 형식 검증
        if (trustAnchorCountryCode != null &&
            !trustAnchorCountryCode.matches("^[A-Z]{2}$")) {
            throw new IllegalArgumentException(
                "trustAnchorCountryCode must be a 2-letter country code (e.g., KR, US), " +
                "but got: " + trustAnchorCountryCode);
        }
    }
}
