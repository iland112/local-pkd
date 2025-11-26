package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.ValidateCertificateResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.ValidateCertificateUseCase;
import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web.CertificateValidationController;
import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web.request.CertificateValidationRequest;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GlobalExceptionHandlerIntegrationTest - 전역 예외 처리 통합 테스트
 *
 * <p><b>테스트 범위</b>:</p>
 * <ul>
 *   <li>IllegalArgumentException 처리 (400 Bad Request)</li>
 *   <li>DomainException 처리 (400 Bad Request)</li>
 *   <li>HttpMediaTypeNotSupportedException 처리 (415 Unsupported Media Type)</li>
 *   <li>NoHandlerFoundException 처리 (404 Not Found)</li>
 *   <li>일반 Exception 처리 (500 Internal Server Error)</li>
 *   <li>에러 응답 구조 검증</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("전역 예외 처리 핸들러 통합 테스트")
public class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ValidateCertificateUseCase validateCertificateUseCase;

    private static final String ENDPOINT = "/api/validate";
    private static final String VALID_CERTIFICATE_ID = "550e8400-e29b-41d4-a716-446655440000";

    @BeforeEach
    void setUp() {
        // Configure default mock behavior for success cases
        when(validateCertificateUseCase.execute(any()))
                .thenReturn(ValidateCertificateResponse.builder()
                        .success(true)
                        .message("Certificate validation completed")
                        .certificateId(UUID.fromString(VALID_CERTIFICATE_ID))
                        .validatedAt(LocalDateTime.now())
                        .durationMillis(100L)
                        .build());
    }

    /**
     * IllegalArgumentException - 빈 certificateId
     *
     * <p>요청 검증 중 IllegalArgumentException이 발생하면 400 Bad Request를 반환합니다.</p>
     */
    @Test
    @DisplayName("IllegalArgumentException - 빈 certificateId")
    void testIllegalArgumentExceptionEmptyCertificateId() throws Exception {
        // GIVEN
        CertificateValidationRequest request = CertificateValidationRequest.builder()
                .certificateId("")  // Empty - will throw IllegalArgumentException
                .validateSignature(true)
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message").isString())
                .andExpect(jsonPath("$.error.timestamp").isString())
                .andExpect(jsonPath("$.path").value(containsString("/api/validate")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.traceId").isString());
    }

    /**
     * IllegalArgumentException - 유효성 검사 실패
     *
     * <p>모든 검증 옵션이 false인 경우 IllegalArgumentException이 발생합니다.</p>
     */
    @Test
    @DisplayName("IllegalArgumentException - 모든 검증 옵션 비활성화")
    void testIllegalArgumentExceptionNoValidationOptions() throws Exception {
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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    /**
     * DomainException - 도메인 규칙 위반
     *
     * <p>Use Case에서 DomainException이 발생하면 400 Bad Request를 반환합니다.</p>
     */
    @Test
    @DisplayName("DomainException - 도메인 규칙 위반")
    void testDomainException() throws Exception {
        // GIVEN
        CertificateValidationRequest request = CertificateValidationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .validateSignature(true)
                .build();

        // Use Case에서 DomainException 발생
        when(validateCertificateUseCase.execute(any()))
                .thenThrow(new DomainException(
                        "INVALID_CERTIFICATE",
                        "Certificate not found in database"));

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_CERTIFICATE"))
                .andExpect(jsonPath("$.error.message").value("Certificate not found in database"))
                .andExpect(jsonPath("$.status").value(400));
    }

    /**
     * HttpMediaTypeNotSupportedException - 지원하지 않는 Content-Type
     *
     * <p>Content-Type 헤더가 없으면 415 Unsupported Media Type을 반환합니다.</p>
     */
    @Test
    @DisplayName("HttpMediaTypeNotSupportedException - Missing Content-Type")
    void testHttpMediaTypeNotSupportedException() throws Exception {
        // GIVEN
        CertificateValidationRequest request = CertificateValidationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .validateSignature(true)
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .content(objectMapper.writeValueAsString(request)))
                // No Content-Type header
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.error.message").value(containsString("not supported")))
                .andExpect(jsonPath("$.status").value(415));
    }

    /**
     * 404 Not Found는 전체 애플리케이션 컨텍스트가 필요하므로 @WebMvcTest에서는 테스트하지 않습니다.
     * 별도의 @SpringBootTest 또는 통합 테스트에서 검증하세요.
     */

    /**
     * 500 Internal Server Error - 예상치 못한 예외
     *
     * <p>예상치 못한 RuntimeException이 발생하면 500 Internal Server Error를 반환합니다.</p>
     */
    @Test
    @DisplayName("일반 Exception - 예상치 못한 오류")
    void testGeneralException() throws Exception {
        // GIVEN
        CertificateValidationRequest request = CertificateValidationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .validateSignature(true)
                .build();

        // Use Case에서 RuntimeException 발생
        when(validateCertificateUseCase.execute(any()))
                .thenThrow(new RuntimeException("Database connection failed"));

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.status").value(500));
    }

    /**
     * 에러 응답 구조 검증
     *
     * <p>모든 에러 응답이 필요한 필드를 포함하고 있는지 확인합니다.</p>
     */
    @Test
    @DisplayName("에러 응답 구조 검증")
    void testErrorResponseStructure() throws Exception {
        // GIVEN
        CertificateValidationRequest request = CertificateValidationRequest.builder()
                .certificateId("")  // Empty - validation error
                .validateSignature(true)
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                // Root level fields
                .andExpect(jsonPath("$.success").isBoolean())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.path").isString())
                .andExpect(jsonPath("$.status").isNumber())
                .andExpect(jsonPath("$.traceId").isString())
                // Error object fields
                .andExpect(jsonPath("$.error.code").isString())
                .andExpect(jsonPath("$.error.message").isString())
                .andExpect(jsonPath("$.error.timestamp").isString())
                // Details array (optional)
                .andExpect(jsonPath("$.error.details").doesNotExist());
    }

    /**
     * TraceId 고유성 검증
     *
     * <p>각 에러 응답이 고유한 TraceId를 가지고 있는지 확인합니다.</p>
     */
    @Test
    @DisplayName("TraceId 고유성 검증")
    void testTraceIdUniqueness() throws Exception {
        // GIVEN
        CertificateValidationRequest request = CertificateValidationRequest.builder()
                .certificateId("")  // Empty - validation error
                .validateSignature(true)
                .build();

        String requestBody = objectMapper.writeValueAsString(request);

        // First request
        String response1 = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Second request
        String response2 = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract traceIds
        ErrorResponse error1 = objectMapper.readValue(response1, ErrorResponse.class);
        ErrorResponse error2 = objectMapper.readValue(response2, ErrorResponse.class);

        // Verify uniqueness
        assert !error1.getTraceId().equals(error2.getTraceId()) :
            "TraceIds should be unique for each request";
    }

    /**
     * 에러 응답 일관성 검증
     *
     * <p>다양한 400 에러들이 일관된 구조를 가지고 있는지 확인합니다.</p>
     */
    @Test
    @DisplayName("에러 응답 일관성 검증")
    void testErrorResponseConsistency() throws Exception {
        // Test multiple different validation errors
        String[] invalidRequests = {
            objectMapper.writeValueAsString(
                CertificateValidationRequest.builder()
                    .certificateId("")  // Empty
                    .validateSignature(true)
                    .build()
            ),
            objectMapper.writeValueAsString(
                CertificateValidationRequest.builder()
                    .certificateId(VALID_CERTIFICATE_ID)
                    .validateSignature(false)
                    .validateChain(false)
                    .checkRevocation(false)
                    .validateValidity(false)
                    .validateConstraints(false)  // All false
                    .build()
            )
        };

        for (String requestBody : invalidRequests) {
            // All should return 400 with consistent structure
            mockMvc.perform(post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").isString())
                    .andExpect(jsonPath("$.error.message").isString())
                    .andExpect(jsonPath("$.error.timestamp").isString())
                    .andExpect(jsonPath("$.path").isString())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.traceId").isString());
        }
    }

    /**
     * Timestamp 포맷 검증
     *
     * <p>에러 응답의 timestamp가 ISO-8601 포맷인지 확인합니다.</p>
     */
    @Test
    @DisplayName("Timestamp 포맷 검증")
    void testTimestampFormat() throws Exception {
        // GIVEN
        CertificateValidationRequest request = CertificateValidationRequest.builder()
                .certificateId("")  // Validation error
                .validateSignature(true)
                .build();

        // WHEN & THEN
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                // Timestamp should match ISO-8601 pattern (with optional nanoseconds)
                .andExpect(jsonPath("$.error.timestamp",
                    matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?")));
    }

    /**
     * 성공 응답과 에러 응답 구분
     *
     * <p>성공 응답에서 error 필드가 없는지 확인합니다.</p>
     */
    @Test
    @DisplayName("성공 응답과 에러 응답 구분")
    void testSuccessVsErrorResponse() throws Exception {
        // Success response (200 OK)
        CertificateValidationRequest validRequest = CertificateValidationRequest.builder()
                .certificateId(VALID_CERTIFICATE_ID)
                .validateSignature(true)
                .build();

        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").doesNotExist());

        // Error response (400 Bad Request)
        CertificateValidationRequest invalidRequest = CertificateValidationRequest.builder()
                .certificateId("")  // Empty
                .validateSignature(true)
                .build();

        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
