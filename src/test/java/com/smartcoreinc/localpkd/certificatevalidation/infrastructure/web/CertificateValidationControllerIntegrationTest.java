package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.ValidateCertificateResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.ValidateCertificateUseCase;
import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web.request.CertificateValidationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest; // Add this import
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc; // Add this import


import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CertificateValidationControllerIntegrationTest - 인증서 검증 API 통합 테스트
 *
 * <p><b>테스트 범위</b>:</p>
 * <ul>
 *   <li>POST /api/validate 엔드포인트</li>
 *   <li>GET /api/validate/health 엔드포인트</li>
 *   <li>요청 검증 및 에러 처리</li>
 *   <li>응답 구조 및 필드 검증</li>
 * </ul>
 *
 * <p><b>테스트 환경</b>:</p>
 * <ul>
 *   <li>@WebMvcTest: 컨트롤러 레이어만 테스트 (빠른 실행)</li>
 *   <li>@MockBean: 의존성 주입 (ValidateCertificateUseCase)</li>
 *   <li>MockMvc: 서블릿 컨테이너 없이 HTTP 요청 테스트</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("인증서 검증 API 통합 테스트")
public class CertificateValidationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ValidateCertificateUseCase validateCertificateUseCase;

    private static final String ENDPOINT = "/api/validate";
    private static final String VALID_CERTIFICATE_ID = "550e8400-e29b-41d4-a716-446655440000";

    /**
     * 테스트 설정
     *
     * <p>모든 테스트 전에 Mockito Mock을 설정합니다.</p>
     */
    @BeforeEach
    void setUp() {
        // ValidateCertificateUseCase Mock 설정
        when(validateCertificateUseCase.execute(any()))
                .thenReturn(ValidateCertificateResponse.success(
                        UUID.fromString(VALID_CERTIFICATE_ID),
                        "CN=Test",
                        "CN=TestCA",
                        "123456",
                        "fingerprint",
                        true,
                        true,
                        true,
                        true,
                        true,
                        LocalDateTime.now(),
                        100L
                ));
    }

    /**
     * 정상 요청 - 기본 검증 옵션
     *
     * <p>모든 기본 검증 옵션으로 요청을 보내고 200 OK 응답을 받습니다.</p>
     */
    @Test
    @DisplayName("정상 요청 - 기본 검증 옵션")
    void testValidateCertificateSuccess() throws Exception {
        // GIVEN
        CertificateValidationRequest request = CertificateValidationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .validateSignature(true)
                .validateChain(false)
                .checkRevocation(false)
                .validateValidity(true)
                .validateConstraints(true)
                .build();

        // WHEN & THEN
        MvcResult result = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(anything()))
                .andExpect(jsonPath("$.certificateId", startsWith("550e8400")))
                .andExpect(jsonPath("$.message").isString())
                .andReturn();

        // Verify response is valid JSON
        String content = result.getResponse().getContentAsString();
        ValidateCertificateResponse response = objectMapper.readValue(content, ValidateCertificateResponse.class);
        assertThat(response.certificateId()).isNotNull();
    }

    /**
     * 모든 검증 옵션 활성화
     *
     * <p>signature, chain, revocation, validity, constraints 모두 검증합니다.</p>
     */
    @Test
    @DisplayName("모든 검증 옵션 활성화")
    void testValidateCertificateWithAllOptions() throws Exception {
        // GIVEN
        CertificateValidationRequest request = CertificateValidationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .validateSignature(true)
                .validateChain(true)
                .checkRevocation(true)
                .validateValidity(true)
                .validateConstraints(true)
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certificateId").isString());
    }

    /**
     * 최소 필수 검증 옵션
     *
     * <p>최소 1개의 검증 옵션만 활성화합니다 (validateValidity=true).</p>
     */
    @Test
    @DisplayName("최소 필수 검증 옵션")
    void testValidateCertificateWithMinimalOptions() throws Exception {
        // GIVEN
        CertificateValidationRequest request = CertificateValidationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .validateSignature(false)
                .validateChain(false)
                .checkRevocation(false)
                .validateValidity(true)  // At least 1 enabled
                .validateConstraints(false)
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    /**
     * 검증 오류 - 빈 certificateId
     *
     * <p>certificateId가 빈 값인 경우 400 Bad Request를 받습니다.</p>
     */
    @Test
    @DisplayName("검증 오류 - 빈 certificateId")
    void testValidateCertificateWithBlankCertificateId() throws Exception {
        // GIVEN
        CertificateValidationRequest request = CertificateValidationRequest.builder()
                .certificateId("")  // Blank
                .validateSignature(true)
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
    void testValidateCertificateWithNullCertificateId() throws Exception {
        // GIVEN
        String requestBody = "{\"validateSignature\": true}";

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    /**
     * 검증 오류 - 모든 검증 옵션 비활성화
     *
     * <p>최소 1개 이상의 검증 옵션이 필요합니다.</p>
     */
    @Test
    @DisplayName("검증 오류 - 모든 검증 옵션 비활성화")
    void testValidateCertificateWithNoValidationOptions() throws Exception {
        // GIVEN
        CertificateValidationRequest request = CertificateValidationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .validateSignature(false)
                .validateChain(false)
                .checkRevocation(false)
                .validateValidity(false)
                .validateConstraints(false)
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
    void testValidateCertificateResponseStructure() throws Exception {
        // GIVEN
        CertificateValidationRequest request = CertificateValidationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .validateSignature(true)
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").isBoolean())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.certificateId").isString())
                .andExpect(jsonPath("$.validatedAt").isString());
    }

    /**
     * Health Check 엔드포인트
     *
     * <p>GET /api/validate/health가 정상 응답을 반환합니다.</p>
     */
    @Test
    @DisplayName("Health Check 엔드포인트")
    void testHealthCheckEndpoint() throws Exception {
        // WHEN & THEN
        mockMvc.perform(get(ENDPOINT + "/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Certificate Validation API is ready"));
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
        CertificateValidationRequest request = CertificateValidationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .validateSignature(true)
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
        CertificateValidationRequest request1 = CertificateValidationRequest.builder()
                .certificateId("550e8400-e29b-41d4-a716-446655440001")
                .validateSignature(true)
                .build();

        CertificateValidationRequest request2 = CertificateValidationRequest.builder()
                .certificateId("550e8400-e29b-41d4-a716-446655440002")
                .validateSignature(true)
                .validateValidity(true)
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
     * Request with Different Certificate IDs
     *
     * <p>다양한 형식의 certificateId를 테스트합니다.</p>
     */
    @Test
    @DisplayName("Different Certificate ID formats")
    void testDifferentCertificateIdFormats() throws Exception {
        // Test with valid UUID
        CertificateValidationRequest request = CertificateValidationRequest.builder()
                .certificateId(UUID.randomUUID().toString())
                .validateSignature(true)
                .build();

        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
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
        CertificateValidationRequest request = CertificateValidationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .validateSignature(true)
                .build();

        // WHEN
        long startTime = System.currentTimeMillis();
        MvcResult result = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        long duration = System.currentTimeMillis() - startTime;

        // THEN
        assertThat(duration).isLessThan(5000L);  // Should complete within 5 seconds
    }
}
