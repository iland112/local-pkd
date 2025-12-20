package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RevocationCheckRequest - 인증서 폐기 여부 확인 API 요청
 *
 * <p><b>Endpoint</b>: POST /api/check-revocation</p>
 *
 * <p><b>요청 예시</b>:</p>
 * <pre>{@code
 * POST /api/check-revocation
 * Content-Type: application/json
 *
 * {
 *   "certificateId": "550e8400-e29b-41d4-a716-446655440000",
 *   "issuerDn": "CN=CSCA KR, O=Korea, C=KR",
 *   "serialNumber": "0a1b2c3d4e5f",
 *   "forceFresh": false,
 *   "crlFetchTimeoutSeconds": 30
 * }
 * }</pre>
 *
 * <p><b>폐기 확인 방식</b>:</p>
 * <ul>
 *   <li>발급자 DN 기반 CRL 조회</li>
 *   <li>일련번호 기반 폐기 목록 검사</li>
 *   <li>Fail-Open 정책: CRL 실패 시 NOT_REVOKED</li>
 * </ul>
 *
 * @see RevocationCheckResponse
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevocationCheckRequest {

    /**
     * 폐기 여부를 확인할 인증서 UUID
     * <p>필수 항목</p>
     */
    @JsonProperty("certificateId")
    private String certificateId;

    /**
     * 인증서 발급자 DN (Issuer Distinguished Name)
     * <p>필수 항목</p>
     * <p>예: "CN=CSCA KR, O=Korea, C=KR"</p>
     */
    @JsonProperty("issuerDn")
    private String issuerDn;

    /**
     * 인증서 일련번호 (Serial Number)
     * <p>필수 항목</p>
     * <p>16진수 문자열 형식</p>
     */
    @JsonProperty("serialNumber")
    private String serialNumber;

    /**
     * 신규 CRL 강제 다운로드 여부
     * <p>기본값: false (캐시된 CRL 사용)</p>
     * <p>true: 항상 최신 CRL 다운로드 (성능 저하)</p>
     */
    @JsonProperty("forceFresh")
    @Builder.Default
    private boolean forceFresh = false;

    /**
     * CRL 다운로드 타임아웃 (초)
     * <p>기본값: 30초</p>
     * <p>범위: 5~300초</p>
     */
    @JsonProperty("crlFetchTimeoutSeconds")
    @Builder.Default
    private int crlFetchTimeoutSeconds = 30;

    /**
     * 요청 검증
     *
     * @throws IllegalArgumentException 필수 필드가 invalid인 경우
     */
    public void validate() {
        if (certificateId == null || certificateId.isBlank()) {
            throw new IllegalArgumentException("certificateId must not be null or empty");
        }

        if (issuerDn == null || issuerDn.isBlank()) {
            throw new IllegalArgumentException("issuerDn must not be null or empty");
        }

        if (serialNumber == null || serialNumber.isBlank()) {
            throw new IllegalArgumentException("serialNumber must not be null or empty");
        }

        if (crlFetchTimeoutSeconds < 5 || crlFetchTimeoutSeconds > 300) {
            throw new IllegalArgumentException(
                "crlFetchTimeoutSeconds must be between 5 and 300, but got: " +
                crlFetchTimeoutSeconds);
        }
    }
}
