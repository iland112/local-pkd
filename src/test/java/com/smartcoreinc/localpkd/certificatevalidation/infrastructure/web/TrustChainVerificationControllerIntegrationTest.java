package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.VerifyTrustChainResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.VerifyTrustChainUseCase;
import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web.request.TrustChainVerificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc; // Keep this
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TrustChainVerificationControllerIntegrationTest - Trust Chain 검증 API 통합 테스트
 *
 * <p><b>테스트 범위</b>:</p>
 * <ul>
 *   <li>POST /api/verify-trust-chain 엔드포인트</li>
 *   <li>GET /api/verify-trust-chain/health 엔드포인트</li>
 *   <li>Trust Chain 구축 및 검증</li>
 *   <li>요청 검증 및 에러 처리</li>
 *   <li>응답 구조 및 필드 검증</li>
 * </ul>
 *
 * <p><b>테스트 환경</b>:</p>
 * <ul>
 *   <li>@SpringBootTest: 전체 애플리케이션 컨텍스트 로드</li>
 *   <li>@MockBean: 의존성 주입 (VerifyTrustChainUseCase)</li>
 *   <li>MockMvc: 서블릿 컨테이너 없이 HTTP 요청 테스트</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Trust Chain 검증 API 통합 테스트")
public class TrustChainVerificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VerifyTrustChainUseCase verifyTrustChainUseCase;

    private static final String ENDPOINT = "/api/verify-trust-chain";
    private static final String VALID_CERTIFICATE_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String VALID_COUNTRY_CODE = "KR";

    /**
     * 테스트 설정
     *
     * <p>모든 테스트 전에 Mockito Mock을 설정합니다.</p>
     */
    @BeforeEach
    void setUp() {
        // VerifyTrustChainUseCase Mock 설정
        when(verifyTrustChainUseCase.execute(any()))
                .thenReturn(VerifyTrustChainResponse.success(
                        UUID.fromString(VALID_CERTIFICATE_ID),
                        "CN=EndEntity",
                        UUID.randomUUID(),
                        "CN=TrustAnchor",
                        VALID_COUNTRY_CODE,
                        new ArrayList<>(),
                        LocalDateTime.now(),
                        500L
                ));
    }

    /**
     * 정상 요청 - 기본 Trust Chain 검증
     *
     * <p>기본 파라미터로 Trust Chain을 검증합니다.</p>
     */
    @Test
    @DisplayName("정상 요청 - 기본 Trust Chain 검증")
    void testVerifyTrustChainSuccess() throws Exception {
        // GIVEN
        TrustChainVerificationRequest request = TrustChainVerificationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .trustAnchorCountryCode(VALID_COUNTRY_CODE)
                .checkRevocation(false)
                .validateValidity(true)
                .maxChainDepth(5)
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(containsString("Trust Chain")))
                .andExpect(jsonPath("$.chainValid").value(true))
                .andExpect(jsonPath("$.chainDepth").value(3));
    }

    /**
     * 모든 검증 옵션 활성화
     *
     * <p>revocation check와 validity validation을 모두 수행합니다.</p>
     */
    @Test
    @DisplayName("모든 검증 옵션 활성화")
    void testVerifyTrustChainWithAllOptions() throws Exception {
        // GIVEN
        TrustChainVerificationRequest request = TrustChainVerificationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .trustAnchorCountryCode(VALID_COUNTRY_CODE)
                .checkRevocation(true)
                .validateValidity(true)
                .maxChainDepth(5)
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.chainValid").value(true));
    }

    /**
     * Trust Anchor 국가 코드 없이 검증
     *
     * <p>trustAnchorCountryCode는 선택사항입니다.</p>
     */
    @Test
    @DisplayName("Trust Anchor 국가 코드 없이 검증")
    void testVerifyTrustChainWithoutCountryCode() throws Exception {
        // GIVEN
        TrustChainVerificationRequest request = TrustChainVerificationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .trustAnchorCountryCode(null)  // Optional
                .checkRevocation(false)
                .validateValidity(true)
                .maxChainDepth(5)
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
    void testVerifyTrustChainWithBlankCertificateId() throws Exception {
        // GIVEN
        TrustChainVerificationRequest request = TrustChainVerificationRequest.builder()
                .certificateId("")  // Blank
                .trustAnchorCountryCode(VALID_COUNTRY_CODE)
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
    void testVerifyTrustChainWithNullCertificateId() throws Exception {
        // GIVEN
        String requestBody = "{\"trustAnchorCountryCode\": \"KR\"}";

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    /**
     * 검증 오류 - maxChainDepth 범위 초과 (너무 작음)
     *
     * <p>maxChainDepth는 1 이상이어야 합니다.</p>
     */
    @Test
    @DisplayName("검증 오류 - maxChainDepth 범위 초과 (0)")
    void testVerifyTrustChainWithInvalidMaxChainDepthZero() throws Exception {
        // GIVEN
        TrustChainVerificationRequest request = TrustChainVerificationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .trustAnchorCountryCode(VALID_COUNTRY_CODE)
                .maxChainDepth(0)  // Invalid: must be >= 1
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * 검증 오류 - maxChainDepth 범위 초과 (너무 큼)
     *
     * <p>maxChainDepth는 10 이하여야 합니다.</p>
     */
    @Test
    @DisplayName("검증 오류 - maxChainDepth 범위 초과 (11)")
    void testVerifyTrustChainWithInvalidMaxChainDepthTooLarge() throws Exception {
        // GIVEN
        TrustChainVerificationRequest request = TrustChainVerificationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .trustAnchorCountryCode(VALID_COUNTRY_CODE)
                .maxChainDepth(11)  // Invalid: must be <= 10
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * 검증 오류 - 잘못된 국가 코드 형식
     *
     * <p>trustAnchorCountryCode는 2자리 대문자여야 합니다.</p>
     */
    @Test
    @DisplayName("검증 오류 - 잘못된 국가 코드 형식 (1자리)")
    void testVerifyTrustChainWithInvalidCountryCodeLength() throws Exception {
        // GIVEN
        TrustChainVerificationRequest request = TrustChainVerificationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .trustAnchorCountryCode("K")  // Invalid: must be 2 chars
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * 검증 오류 - 잘못된 국가 코드 형식 (소문자)
     *
     * <p>trustAnchorCountryCode는 대문자여야 합니다.</p>
     */
    @Test
    @DisplayName("검증 오류 - 잘못된 국가 코드 형식 (소문자)")
    void testVerifyTrustChainWithLowercaseCountryCode() throws Exception {
        // GIVEN
        TrustChainVerificationRequest request = TrustChainVerificationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .trustAnchorCountryCode("kr")  // Invalid: must be uppercase
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * 응답 구조 검증
     *
     * <p>응답에 필요한 모든 필드가 포함되어 있는지 확인합니다.</p>
     */
    @Test
    @DisplayName("응답 구조 검증")
    void testVerifyTrustChainResponseStructure() throws Exception {
        // GIVEN
        TrustChainVerificationRequest request = TrustChainVerificationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .trustAnchorCountryCode(VALID_COUNTRY_CODE)
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").isBoolean())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.endEntityCertificateId").isString())
                .andExpect(jsonPath("$.chainValid").isBoolean())
                .andExpect(jsonPath("$.chainDepth").isNumber())
                .andExpect(jsonPath("$.certificateChain").isArray())
                .andExpect(jsonPath("$.validatedAt").isString())
                .andExpect(jsonPath("$.durationMillis").isNumber());
    }

    /**
     * Health Check 엔드포인트
     *
     * <p>GET /api/verify-trust-chain/health가 정상 응답을 반환합니다.</p>
     */
    @Test
    @DisplayName("Health Check 엔드포인트")
    void testHealthCheckEndpoint() throws Exception {
        // WHEN & THEN
        mockMvc.perform(get(ENDPOINT + "/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Trust Chain Verification API is ready"));
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
        TrustChainVerificationRequest request = TrustChainVerificationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .trustAnchorCountryCode(VALID_COUNTRY_CODE)
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
        TrustChainVerificationRequest request1 = TrustChainVerificationRequest.builder()
                .certificateId("550e8400-e29b-41d4-a716-446655440001")
                .trustAnchorCountryCode("KR")
                .build();

        TrustChainVerificationRequest request2 = TrustChainVerificationRequest.builder()
                .certificateId("550e8400-e29b-41d4-a716-446655440002")
                .trustAnchorCountryCode("US")
                .checkRevocation(true)
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
        TrustChainVerificationRequest request = TrustChainVerificationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .trustAnchorCountryCode(VALID_COUNTRY_CODE)
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
