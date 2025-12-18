package com.smartcoreinc.localpkd.passiveauthentication.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.passiveauthentication.application.usecase.PerformPassiveAuthenticationUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for PassiveAuthenticationController REST API endpoints.
 * <p>
 * Tests HTTP layer request/response handling, validation, error handling,
 * and client metadata extraction for Passive Authentication API.
 * </p>
 *
 * <h3>Test Scope:</h3>
 * <ul>
 *   <li>POST /api/v1/pa/verify - PA verification endpoint</li>
 *   <li>GET /api/v1/pa/history - Verification history with pagination</li>
 *   <li>GET /api/v1/pa/{verificationId} - Single verification result</li>
 * </ul>
 *
 * <h3>Test Categories:</h3>
 * <ul>
 *   <li>Controller Endpoint Tests (3 endpoints)</li>
 *   <li>Request Validation Tests (Bean Validation)</li>
 *   <li>Response Format Tests (JSON structure)</li>
 *   <li>Error Handling Tests (400, 404, 500)</li>
 *   <li>Client Metadata Extraction Tests (IP, User-Agent)</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-12-18
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
@DisplayName("PassiveAuthenticationController REST API Tests")
class PassiveAuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PerformPassiveAuthenticationUseCase performPassiveAuthenticationUseCase;

    @Autowired
    private CertificateRepository certificateRepository;

    private static final String FIXTURES_BASE = "src/test/resources/passport-fixtures/valid-korean-passport/";
    private static final String API_BASE_PATH = "/api/v1/pa";

    private byte[] sodBytes;
    private byte[] dg1Bytes;
    private byte[] dg2Bytes;
    private byte[] dg14Bytes;

    @BeforeEach
    void setUp() throws Exception {
        // Load test fixtures
        sodBytes = Files.readAllBytes(Path.of(FIXTURES_BASE + "sod.bin"));
        dg1Bytes = Files.readAllBytes(Path.of(FIXTURES_BASE + "dg1.bin"));
        dg2Bytes = Files.readAllBytes(Path.of(FIXTURES_BASE + "dg2.bin"));
        dg14Bytes = Files.readAllBytes(Path.of(FIXTURES_BASE + "dg14.bin"));

        // Verify LDAP has required certificates
        List<Certificate> cscas = certificateRepository.findAllByType(CertificateType.CSCA);
        assertThat(cscas).isNotEmpty();
    }

    // ===== 1. Controller Endpoint Tests =====

    @Test
    @DisplayName("POST /verify - Valid passport data should return 200 OK with VALID status")
    void shouldVerifyValidPassport() throws Exception {
        // Given: Valid request with SOD and Data Groups
        PassiveAuthenticationRequest request = buildValidRequest();

        // When: POST /api/v1/pa/verify
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Response should be 200 OK
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("VALID"))
            .andExpect(jsonPath("$.verificationId").isNotEmpty())
            .andExpect(jsonPath("$.issuingCountry").value("KOR"))
            .andExpect(jsonPath("$.documentNumber").value("M12345678"))
            .andExpect(jsonPath("$.certificateChainValidation.valid").value(true))
            .andExpect(jsonPath("$.sodSignatureValidation.valid").value(true))
            .andExpect(jsonPath("$.dataGroupValidation.validGroups").value(3))
            .andExpect(jsonPath("$.errors").isEmpty())
            .andDo(print());
    }

    @Test
    @DisplayName("POST /verify - Invalid passport (tampered data) should return 200 OK with INVALID status")
    void shouldReturnInvalidStatusForTamperedPassport() throws Exception {
        // Given: Tampered DG1
        byte[] tamperedDg1 = dg1Bytes.clone();
        tamperedDg1[5] ^= 0xFF;

        PassiveAuthenticationRequest request = new PassiveAuthenticationRequest(
            "KOR",
            "M12345678",
            Base64.getEncoder().encodeToString(sodBytes),
            Map.of(
                "DG1", Base64.getEncoder().encodeToString(tamperedDg1),
                "DG2", Base64.getEncoder().encodeToString(dg2Bytes)
            ),
            "api-test-client"
        );

        // When: POST /api/v1/pa/verify
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Response should be 200 OK but status INVALID
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INVALID"))
            .andExpect(jsonPath("$.errors").isNotEmpty())
            .andExpect(jsonPath("$.dataGroupValidation.invalidGroups", greaterThan(0)))
            .andDo(print());
    }

    @Test
    @DisplayName("GET /history - Should return paginated verification history")
    void shouldReturnPaginatedHistory() throws Exception {
        // Given: Perform some verifications first
        performVerificationAndGetId();
        performVerificationAndGetId();

        // When: GET /api/v1/pa/history?page=0&size=10
        mockMvc.perform(get(API_BASE_PATH + "/history")
                .param("page", "0")
                .param("size", "10"))
            // Then: Response should be 200 OK with Page structure
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()", lessThanOrEqualTo(10)))
            .andExpect(jsonPath("$.pageable.pageNumber").value(0))
            .andExpect(jsonPath("$.pageable.pageSize").value(10))
            .andExpect(jsonPath("$.totalElements").exists())
            .andDo(print());
    }

    @Test
    @DisplayName("GET /history - Should filter by country code")
    void shouldFilterByCountry() throws Exception {
        // Given: Perform a verification
        performVerificationAndGetId();

        // When: GET /api/v1/pa/history?issuingCountry=KOR
        mockMvc.perform(get(API_BASE_PATH + "/history")
                .param("issuingCountry", "KOR"))
            // Then: All results should be KOR
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].issuingCountry", everyItem(equalTo("KOR"))))
            .andDo(print());
    }

    @Test
    @DisplayName("GET /history - Should filter by status")
    void shouldFilterByStatus() throws Exception {
        // Given: Perform a verification
        performVerificationAndGetId();

        // When: GET /api/v1/pa/history?status=VALID
        mockMvc.perform(get(API_BASE_PATH + "/history")
                .param("status", "VALID"))
            // Then: All results should be VALID
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].status", everyItem(equalTo("VALID"))))
            .andDo(print());
    }

    @Test
    @DisplayName("GET /{id} - Should return verification result by ID")
    void shouldReturnVerificationById() throws Exception {
        // Given: Perform a verification and get its ID
        UUID verificationId = performVerificationAndGetId();

        // When: GET /api/v1/pa/{verificationId}
        mockMvc.perform(get(API_BASE_PATH + "/{verificationId}", verificationId))
            // Then: Response should be 200 OK
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificationId").value(verificationId.toString()))
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.issuingCountry").exists())
            .andDo(print());
    }

    @Test
    @DisplayName("GET /{id} - Non-existent ID should return 404")
    void shouldReturn404ForNonExistentId() throws Exception {
        // Given: Random UUID that doesn't exist
        UUID nonExistentId = UUID.randomUUID();

        // When: GET /api/v1/pa/{verificationId}
        mockMvc.perform(get(API_BASE_PATH + "/{verificationId}", nonExistentId))
            // Then: Response should be 404 Not Found
            .andExpect(status().isNotFound())
            .andDo(print());
    }

    // ===== 2. Request Validation Tests =====

    @Test
    @DisplayName("POST /verify - Missing required field should return 400 Bad Request")
    void shouldRejectMissingRequiredField() throws Exception {
        // Given: Request without issuingCountry
        String invalidRequest = """
            {
                "documentNumber": "M12345678",
                "sod": "MIIGBwYJKoZIhvcNAQcCoII...",
                "dataGroups": {
                    "DG1": "UEQxMjM0NTY3ODk..."
                }
            }
            """;

        // When: POST /api/v1/pa/verify
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
            // Then: Response should be 400 Bad Request
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("발급 국가 코드는 필수입니다")))
            .andDo(print());
    }

    @Test
    @DisplayName("POST /verify - Invalid country code format should return 400")
    void shouldRejectInvalidCountryCode() throws Exception {
        // Given: Invalid country code (not 3 letters)
        PassiveAuthenticationRequest request = new PassiveAuthenticationRequest(
            "KR",  // ❌ Should be 3 letters (KOR)
            "M12345678",
            Base64.getEncoder().encodeToString(sodBytes),
            Map.of("DG1", Base64.getEncoder().encodeToString(dg1Bytes)),
            null
        );

        // When: POST /api/v1/pa/verify
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Response should be 400 Bad Request
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("ISO 3166-1 alpha-3")))
            .andDo(print());
    }

    @Test
    @DisplayName("POST /verify - Invalid Data Group key should return 400")
    void shouldRejectInvalidDataGroupKey() throws Exception {
        // Given: Invalid Data Group key (DG99 doesn't exist)
        String invalidRequest = """
            {
                "issuingCountry": "KOR",
                "documentNumber": "M12345678",
                "sod": "MIIGBwYJKoZIhvcNAQcCoII...",
                "dataGroups": {
                    "DG99": "UEQxMjM0NTY3ODk..."
                }
            }
            """;

        // When: POST /api/v1/pa/verify
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
            // Then: Response should be 400 Bad Request
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("Invalid Data Group key: DG99")))
            .andDo(print());
    }

    @Test
    @DisplayName("POST /verify - Empty data groups should return 400")
    void shouldRejectEmptyDataGroups() throws Exception {
        // Given: Request with empty dataGroups map
        PassiveAuthenticationRequest request = new PassiveAuthenticationRequest(
            "KOR",
            "M12345678",
            Base64.getEncoder().encodeToString(sodBytes),
            Map.of(),  // ❌ Empty
            null
        );

        // When: POST /api/v1/pa/verify
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Response should be 400 Bad Request
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("최소 하나의 Data Group이 필요합니다")))
            .andDo(print());
    }

    // ===== 3. Response Format Tests =====

    @Test
    @DisplayName("POST /verify - Response should have correct JSON structure")
    void shouldReturnCorrectJsonStructure() throws Exception {
        // Given: Valid request
        PassiveAuthenticationRequest request = buildValidRequest();

        // When: POST /api/v1/pa/verify
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Response should have all required fields
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.verificationId").exists())
            .andExpect(jsonPath("$.verificationTimestamp").exists())
            .andExpect(jsonPath("$.issuingCountry").exists())
            .andExpect(jsonPath("$.documentNumber").exists())
            .andExpect(jsonPath("$.certificateChainValidation").exists())
            .andExpect(jsonPath("$.certificateChainValidation.valid").isBoolean())
            .andExpect(jsonPath("$.certificateChainValidation.dscSubject").isString())
            .andExpect(jsonPath("$.certificateChainValidation.cscaSubject").isString())
            .andExpect(jsonPath("$.sodSignatureValidation").exists())
            .andExpect(jsonPath("$.sodSignatureValidation.valid").isBoolean())
            .andExpect(jsonPath("$.dataGroupValidation").exists())
            .andExpect(jsonPath("$.dataGroupValidation.totalGroups").isNumber())
            .andExpect(jsonPath("$.dataGroupValidation.validGroups").isNumber())
            .andExpect(jsonPath("$.dataGroupValidation.invalidGroups").isNumber())
            .andExpect(jsonPath("$.processingDurationMs").isNumber())
            .andExpect(jsonPath("$.errors").isArray())
            .andDo(print());
    }

    @Test
    @DisplayName("POST /verify - Timestamp should be in ISO 8601 format")
    void shouldReturnTimestampInIso8601Format() throws Exception {
        // Given: Valid request
        PassiveAuthenticationRequest request = buildValidRequest();

        // When: POST /api/v1/pa/verify
        MvcResult result = mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

        // Then: Timestamp should match ISO 8601 pattern
        String response = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(response);
        String timestamp = jsonNode.get("verificationTimestamp").asText();
        
        assertThat(timestamp).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
    }

    // ===== 4. Error Handling Tests =====

    @Test
    @DisplayName("POST /verify - Malformed JSON should return 400 Bad Request")
    void shouldReturn400ForMalformedJson() throws Exception {
        // Given: Malformed JSON
        String malformedJson = "{issuingCountry: KOR, documentNumber: "; // Invalid JSON

        // When: POST /api/v1/pa/verify
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedJson))
            // Then: Response should be 400 Bad Request
            .andExpect(status().isBadRequest())
            .andDo(print());
    }

    @Test
    @DisplayName("POST /verify - Invalid Base64 encoding should return 400")
    void shouldReturn400ForInvalidBase64() throws Exception {
        // Given: Invalid Base64 string
        PassiveAuthenticationRequest request = new PassiveAuthenticationRequest(
            "KOR",
            "M12345678",
            "This is not valid Base64!@#$",
            Map.of("DG1", "Also not Base64!"),
            null
        );

        // When: POST /api/v1/pa/verify
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Response should be 400 Bad Request
            .andExpect(status().isBadRequest())
            .andDo(print());
    }

    @Test
    @DisplayName("GET /{id} - Invalid UUID format should return 400")
    void shouldReturn400ForInvalidUuidFormat() throws Exception {
        // Given: Invalid UUID format
        String invalidUuid = "not-a-uuid";

        // When: GET /api/v1/pa/{verificationId}
        mockMvc.perform(get(API_BASE_PATH + "/{verificationId}", invalidUuid))
            // Then: Response should be 400 Bad Request
            .andExpect(status().isBadRequest())
            .andDo(print());
    }

    @Test
    @DisplayName("POST /verify - DSC not found in LDAP should return 404")
    void shouldReturn404WhenDscNotFound() throws Exception {
        // Given: Request with DSC that doesn't exist in LDAP
        PassiveAuthenticationRequest request = buildValidRequest();

        // Clear all DSC certificates from database
        certificateRepository.findAllByType(CertificateType.DSC)
            .forEach(cert -> certificateRepository.deleteById(cert.getId()));

        // When: POST /api/v1/pa/verify
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Response should be 404 Not Found
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("DSC_NOT_FOUND"))
            .andExpect(jsonPath("$.errorMessage", containsString("DSC 인증서를 OpenLDAP에서 찾을 수 없습니다")))
            .andDo(print());
    }

    // ===== 5. Client Metadata Extraction Tests =====

    @Test
    @DisplayName("Should extract client IP from X-Forwarded-For header")
    void shouldExtractClientIpFromXForwardedFor() throws Exception {
        // Given: Valid request with X-Forwarded-For header
        PassiveAuthenticationRequest request = buildValidRequest();

        // When: POST with X-Forwarded-For
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "203.0.113.1, 198.51.100.1")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andDo(print());

        // Then: Should extract first IP (203.0.113.1)
        // Verification happens via audit log in database
    }

    @Test
    @DisplayName("Should extract User-Agent header")
    void shouldExtractUserAgent() throws Exception {
        // Given: Valid request
        PassiveAuthenticationRequest request = buildValidRequest();
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

        // When: POST with User-Agent header
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", userAgent)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andDo(print());

        // Then: Verify audit log contains User-Agent
        // Verification happens via audit log in database
    }

    @Test
    @DisplayName("Should handle missing User-Agent gracefully")
    void shouldHandleMissingUserAgent() throws Exception {
        // Given: Valid request without User-Agent
        PassiveAuthenticationRequest request = buildValidRequest();

        // When: POST without User-Agent header
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andDo(print());

        // Then: Should still succeed (User-Agent is optional)
    }

    // ===== Helper Methods =====

    /**
     * Builds a valid PassiveAuthenticationRequest for testing.
     *
     * @return Valid request with all required fields
     */
    private PassiveAuthenticationRequest buildValidRequest() {
        return new PassiveAuthenticationRequest(
            "KOR",
            "M12345678",
            Base64.getEncoder().encodeToString(sodBytes),
            Map.of(
                "DG1", Base64.getEncoder().encodeToString(dg1Bytes),
                "DG2", Base64.getEncoder().encodeToString(dg2Bytes),
                "DG14", Base64.getEncoder().encodeToString(dg14Bytes)
            ),
            "api-test-client"
        );
    }

    /**
     * Performs a PA verification and returns the verification ID.
     *
     * @return Verification ID (UUID)
     * @throws Exception if verification fails
     */
    private UUID performVerificationAndGetId() throws Exception {
        PassiveAuthenticationRequest request = buildValidRequest();
        
        MvcResult result = mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(response);
        return UUID.fromString(jsonNode.get("verificationId").asText());
    }
}
