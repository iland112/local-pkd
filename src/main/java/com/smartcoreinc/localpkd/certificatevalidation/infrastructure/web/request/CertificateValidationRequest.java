package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CertificateValidationRequest - 인증서 검증 API 요청
 *
 * <p><b>Endpoint</b>: POST /api/validate</p>
 *
 * <p><b>요청 예시</b>:</p>
 * <pre>{@code
 * POST /api/validate
 * Content-Type: application/json
 *
 * {
 *   "certificateId": "550e8400-e29b-41d4-a716-446655440000",
 *   "validateSignature": true,
 *   "validateChain": false,
 *   "checkRevocation": false,
 *   "validateValidity": true,
 *   "validateConstraints": true
 * }
 * }</pre>
 *
 * <p><b>검증 항목</b>:</p>
 * <ul>
 *   <li>validateSignature: 서명 검증 (기본값: true)</li>
 *   <li>validateChain: Trust Chain 검증 (기본값: false)</li>
 *   <li>checkRevocation: CRL 확인 (기본값: false)</li>
 *   <li>validateValidity: 유효기간 검증 (기본값: true)</li>
 *   <li>validateConstraints: 제약사항 검증 (기본값: true)</li>
 * </ul>
 *
 * @see CertificateValidationResponse
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificateValidationRequest {

    /**
     * 검증할 인증서 UUID
     * <p>필수 항목</p>
     */
    @JsonProperty("certificateId")
    private String certificateId;

    /**
     * 서명 검증 여부
     * <p>기본값: true</p>
     */
    @JsonProperty("validateSignature")
    @Builder.Default
    private boolean validateSignature = true;

    /**
     * Trust Chain 검증 여부
     * <p>기본값: false</p>
     */
    @JsonProperty("validateChain")
    @Builder.Default
    private boolean validateChain = false;

    /**
     * CRL 확인 여부
     * <p>기본값: false</p>
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
     * 제약사항 검증 여부
     * <p>기본값: true</p>
     */
    @JsonProperty("validateConstraints")
    @Builder.Default
    private boolean validateConstraints = true;

    /**
     * 요청 검증
     *
     * @throws IllegalArgumentException certificateId가 null 또는 빈 문자열인 경우
     */
    public void validate() {
        if (certificateId == null || certificateId.isBlank()) {
            throw new IllegalArgumentException("certificateId must not be null or empty");
        }

        // 최소 하나의 검증 옵션이 활성화되어야 함
        if (!validateSignature && !validateChain && !checkRevocation &&
            !validateValidity && !validateConstraints) {
            throw new IllegalArgumentException(
                "At least one validation option must be enabled");
        }
    }
}
