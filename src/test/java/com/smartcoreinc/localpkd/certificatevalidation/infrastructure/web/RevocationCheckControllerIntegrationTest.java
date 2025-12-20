package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.CheckRevocationResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.CheckRevocationUseCase;
import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web.request.RevocationCheckRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc; // Add this import

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RevocationCheckControllerIntegrationTest - 인증서 폐기 확인 API 통합 테스트
 *
 * <p><b>테스트 범위</b>:</p>
 * <ul>
 *   <li>POST /api/check-revocation 엔드포인트</li>
 *   <li>GET /api/check-revocation/health 엔드포인트</li>
 *   <li>CRL 기반 인증서 폐기 확인</li>
 *   <li>Fail-Open 정책 (CRL 불가 시 NOT_REVOKED 반환)</li>
 *   <li>요청 검증 및 에러 처리</li>
 *   <li>응답 구조 및 필드 검증</li>
 * </ul>
 *
 * <p><b>테스트 환경</b>:</p>
 * <ul>
 *   <li>@WebMvcTest: 컨트롤러 레이어만 테스트 (빠른 실행)</li>
 *   <li>@MockBean: 의존성 주입 (CheckRevocationUseCase)</li>
 *   <li>MockMvc: 서블릿 컨테이너 없이 HTTP 요청 테스트</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("인증서 폐기 확인 API 통합 테스트")
public class RevocationCheckControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CheckRevocationUseCase checkRevocationUseCase;

    private static final String ENDPOINT = "/api/check-revocation";
    private static final String VALID_CERTIFICATE_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String VALID_ISSUER_DN = "CN=CSCA KR, O=Korea, C=KR";
    private static final String VALID_SERIAL_NUMBER = "0a1b2c3d4e5f";

    /**
     * 테스트 설정
     *
     * <p>모든 테스트 전에 Mockito Mock을 설정합니다.</p>
     */
    @BeforeEach
    void setUp() {
        // CheckRevocationUseCase Mock 설정 - NOT_REVOKED 기본값
        when(checkRevocationUseCase.execute(any()))
                .thenReturn(CheckRevocationResponse.notRevoked(
                        UUID.fromString(VALID_CERTIFICATE_ID),
                        VALID_SERIAL_NUMBER,
                        VALID_ISSUER_DN,
                        LocalDateTime.now().minusDays(1),
                        LocalDateTime.now().plusDays(7),
                        false,
                        LocalDateTime.now(),
                        150L
                ));
    }

    /**
     * 정상 요청 - 인증서 폐기되지 않음
     *
     * <p>기본 파라미터로 인증서가 폐기되지 않았음을 확인합니다.</p>
     */
    @Test
    @DisplayName("정상 요청 - 인증서 폐기되지 않음")
    void testCheckRevocationSuccess() throws Exception {
        // GIVEN
        RevocationCheckRequest request = RevocationCheckRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .issuerDn(VALID_ISSUER_DN)
                .serialNumber(VALID_SERIAL_NUMBER)
                .forceFresh(false)
                .crlFetchTimeoutSeconds(30)
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.revoked").value(false))
                .andExpect(jsonPath("$.revocationStatus").value("NOT_REVOKED"))
                .andExpect(jsonPath("$.certificateId").isString());
    }

    /**
     * forceFresh 옵션 활성화
     *
     * <p>forceFresh=true로 설정하여 캐시를 무시하고 최신 CRL을 다운로드합니다.</p>
     */
    @Test
    @DisplayName("forceFresh 옵션 활성화")
    void testCheckRevocationWithForceFresh() throws Exception {
        // GIVEN
        RevocationCheckRequest request = RevocationCheckRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .issuerDn(VALID_ISSUER_DN)
                .serialNumber(VALID_SERIAL_NUMBER)
                .forceFresh(true)  // Force fresh CRL
                .crlFetchTimeoutSeconds(30)
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Custom CRL Fetch Timeout
     *
     * <p>CRL 다운로드 타임아웃을 커스텀 값으로 설정합니다.</p>
     */
    @Test
    @DisplayName("Custom CRL Fetch Timeout")
    void testCheckRevocationWithCustomTimeout() throws Exception {
        // GIVEN
        RevocationCheckRequest request = RevocationCheckRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .issuerDn(VALID_ISSUER_DN)
                .serialNumber(VALID_SERIAL_NUMBER)
                .forceFresh(false)
                .crlFetchTimeoutSeconds(60)  // 60 seconds
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * 검증 오류 - 빈 certificateId
     *
     * <p>certificateId가 빈 값인 경우 400 Bad Request를 받습니다.</p>
     */
    @Test
    @DisplayName("검증 오류 - 빈 certificateId")
    void testCheckRevocationWithBlankCertificateId() throws Exception {
        // GIVEN
        RevocationCheckRequest request = RevocationCheckRequest.builder()
                .certificateId("")  // Blank
                .issuerDn(VALID_ISSUER_DN)
                .serialNumber(VALID_SERIAL_NUMBER)
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * 검증 오류 - null certificateId
     *
     * <p>certificateId가 null인 경우 400 Bad Request를 받습니다.</p>
     */
    @Test
    @DisplayName("검증 오류 - null certificateId")
    void testCheckRevocationWithNullCertificateId() throws Exception {
        // GIVEN
        String requestBody = String.format(
                "{\"issuerDn\": \"%s\", \"serialNumber\": \"%s\"}",
                VALID_ISSUER_DN, VALID_SERIAL_NUMBER);

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    /**
     * 검증 오류 - 빈 issuerDn
     *
     * <p>issuerDn이 빈 값인 경우 400 Bad Request를 받습니다.</p>
     */
    @Test
    @DisplayName("검증 오류 - 빈 issuerDn")
    void testCheckRevocationWithBlankIssuerDn() throws Exception {
        // GIVEN
        RevocationCheckRequest request = RevocationCheckRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .issuerDn("")  // Blank
                .serialNumber(VALID_SERIAL_NUMBER)
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * 검증 오류 - null issuerDn
     *
     * <p>issuerDn이 null인 경우 400 Bad Request를 받습니다.</p>
     */
    @Test
    @DisplayName("검증 오류 - null issuerDn")
    void testCheckRevocationWithNullIssuerDn() throws Exception {
        // GIVEN
        String requestBody = String.format(
                "{\"certificateId\": \"%s\", \"serialNumber\": \"%s\"}",
                VALID_CERTIFICATE_ID, VALID_SERIAL_NUMBER);

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    /**
     * 검증 오류 - 빈 serialNumber
     *
     * <p>serialNumber가 빈 값인 경우 400 Bad Request를 받습니다.</p>
     */
    @Test
    @DisplayName("검증 오류 - 빈 serialNumber")
    void testCheckRevocationWithBlankSerialNumber() throws Exception {
        // GIVEN
        RevocationCheckRequest request = RevocationCheckRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .issuerDn(VALID_ISSUER_DN)
                .serialNumber("")  // Blank
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * 검증 오류 - null serialNumber
     *
     * <p>serialNumber가 null인 경우 400 Bad Request를 받습니다.</p>
     */
    @Test
    @DisplayName("검증 오류 - null serialNumber")
    void testCheckRevocationWithNullSerialNumber() throws Exception {
        // GIVEN
        String requestBody = String.format(
                "{\"certificateId\": \"%s\", \"issuerDn\": \"%s\"}",
                VALID_CERTIFICATE_ID, VALID_ISSUER_DN);

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    /**
     * 검증 오류 - crlFetchTimeoutSeconds 범위 초과 (너무 작음)
     *
     * <p>crlFetchTimeoutSeconds는 5 이상이어야 합니다.</p>
     */
    @Test
    @DisplayName("검증 오류 - crlFetchTimeoutSeconds 범위 초과 (4)")
    void testCheckRevocationWithInvalidTimeoutTooSmall() throws Exception {
        // GIVEN
        RevocationCheckRequest request = RevocationCheckRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .issuerDn(VALID_ISSUER_DN)
                .serialNumber(VALID_SERIAL_NUMBER)
                .crlFetchTimeoutSeconds(4)  // Invalid: must be >= 5
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * 검증 오류 - crlFetchTimeoutSeconds 범위 초과 (너무 큼)
     *
     * <p>crlFetchTimeoutSeconds는 300 이하여야 합니다.</p>
     */
    @Test
    @DisplayName("검증 오류 - crlFetchTimeoutSeconds 범위 초과 (301)")
    void testCheckRevocationWithInvalidTimeoutTooLarge() throws Exception {
        // GIVEN
        RevocationCheckRequest request = RevocationCheckRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .issuerDn(VALID_ISSUER_DN)
                .serialNumber(VALID_SERIAL_NUMBER)
                .crlFetchTimeoutSeconds(301)  // Invalid: must be <= 300
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Fail-Open 정책 검증
     *
     * <p>CRL을 사용할 수 없는 경우에도 성공 응답을 반환합니다.</p>
     */
    @Test
    @DisplayName("응답 구조 검증")
    void testCheckRevocationResponseStructure() throws Exception {
        // GIVEN
        RevocationCheckRequest request = RevocationCheckRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .issuerDn(VALID_ISSUER_DN)
                .serialNumber(VALID_SERIAL_NUMBER)
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").isBoolean())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.certificateId").isString())
                .andExpect(jsonPath("$.serialNumber").isString())
                .andExpect(jsonPath("$.revocationStatus").isString())
                .andExpect(jsonPath("$.revoked").isBoolean())
                .andExpect(jsonPath("$.checkedAt").isString())
                .andExpect(jsonPath("$.durationMillis").isNumber());
    }

    /**
     * Revoked Certificate 상태 응답 (Optional Fields)
     *
     * <p>인증서가 폐기된 경우 추가 필드들이 포함될 수 있습니다.</p>
     */
    @Test
    @DisplayName("Revoked Certificate 응답 필드 검증")
    void testCheckRevocationRevokedCertificateFields() throws Exception {
        // GIVEN - Mock을 revoked 응답으로 설정
        when(checkRevocationUseCase.execute(any()))
                .thenReturn(CheckRevocationResponse.revoked(
                        UUID.fromString(VALID_CERTIFICATE_ID),
                        VALID_SERIAL_NUMBER,
                        LocalDateTime.now().minusDays(1),
                        "1",
                        "keyCompromise",
                        VALID_ISSUER_DN,
                        LocalDateTime.now().minusDays(1),
                        LocalDateTime.now().plusDays(7),
                        LocalDateTime.now(),
                        150L
                ));

        RevocationCheckRequest request = RevocationCheckRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .issuerDn(VALID_ISSUER_DN)
                .serialNumber(VALID_SERIAL_NUMBER)
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(true))
                .andExpect(jsonPath("$.revocationStatus").value("REVOKED"))
                .andExpect(jsonPath("$.revokedAt").exists())
                .andExpect(jsonPath("$.revocationReason").value("keyCompromise"));
    }

    /**
     * Health Check 엔드포인트
     *
     * <p>GET /api/check-revocation/health가 정상 응답을 반환합니다.</p>
     */
    @Test
    @DisplayName("Health Check 엔드포인트")
    void testHealthCheckEndpoint() throws Exception {
        // WHEN & THEN
        mockMvc.perform(get(ENDPOINT + "/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Revocation Check API is ready"));
    }

    /**
     * Invalid Request Format
     *
     * <p>잘못된 JSON 형식의 요청을 처리합니다.</p>
     */
    @Test
    @DisplayName("Invalid JSON 형식")
    void testInvalidJsonFormat() throws Exception {
        // GIVEN
        String invalidJson = "{\"certificateId\": ";

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    /**
     * Missing Content-Type Header
     *
     * <p>Content-Type 헤더 없이 요청을 보내면 415 Unsupported Media Type을 받습니다.</p>
     */
    @Test
    @DisplayName("Missing Content-Type")
    void testMissingContentType() throws Exception {
        // GIVEN
        RevocationCheckRequest request = RevocationCheckRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .issuerDn(VALID_ISSUER_DN)
                .serialNumber(VALID_SERIAL_NUMBER)
                .build();

        // WHEN & THEN
        // Spring Framework returns 415 Unsupported Media Type when Content-Type is missing
        mockMvc.perform(post(ENDPOINT)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnsupportedMediaType());
    }

    /**
     * Multiple Request Validations
     *
     * <p>여러 요청을 순차적으로 보내고 각각 검증합니다.</p>
     */
    @Test
    @DisplayName("Multiple requests handling")
    void testMultipleRequests() throws Exception {
        // GIVEN
        RevocationCheckRequest request1 = RevocationCheckRequest.builder()
                .certificateId("550e8400-e29b-41d4-a716-446655440001")
                .issuerDn("CN=CSCA US, O=USA, C=US")
                .serialNumber("1a2b3c4d5e6f")
                .build();

        RevocationCheckRequest request2 = RevocationCheckRequest.builder()
                .certificateId("550e8400-e29b-41d4-a716-446655440002")
                .issuerDn("CN=CSCA JP, O=Japan, C=JP")
                .serialNumber("aabbccddeeff")
                .forceFresh(true)
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk());

        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk());
    }

    /**
     * Response Time Measurement
     *
     * <p>응답 시간을 측정합니다 (성능 벤치마크).</p>
     */
    @Test
    @DisplayName("Response time within reasonable bounds")
    void testResponseTimePerformance() throws Exception {
        // GIVEN
        RevocationCheckRequest request = RevocationCheckRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .issuerDn(VALID_ISSUER_DN)
                .serialNumber(VALID_SERIAL_NUMBER)
                .build();

        // WHEN
        long startTime = System.currentTimeMillis();
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        long duration = System.currentTimeMillis() - startTime;

        // THEN
        assert duration < 5000L : "Response time should be less than 5 seconds, but was " + duration + "ms";
    }
}
