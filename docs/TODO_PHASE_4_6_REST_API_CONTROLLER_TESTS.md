# TODO: Passive Authentication Phase 4.6 - REST API Controller Tests

**Created**: 2025-12-18
**Priority**: HIGH
**Status**: Ready to Start
**Previous Phase**: Phase 4.5 (UseCase Integration Tests) ✅ COMPLETED

---

## Overview

Phase 4.6에서는 Passive Authentication REST API Controller의 통합 테스트를 구현합니다. Phase 4.5에서 UseCase 레이어가 성공적으로 검증되었으므로, 이제 HTTP 레이어의 요청/응답 처리, 검증, 에러 핸들링을 테스트할 차례입니다.

---

## Prerequisites (Already Completed ✅)

- ✅ PassiveAuthenticationController 구현 완료 (3 endpoints)
- ✅ PassiveAuthenticationRequest/Response DTOs 구현 완료
- ✅ PerformPassiveAuthenticationUseCase 통합 테스트 완료 (Phase 4.5)
- ✅ OpenAPI/Swagger 문서 어노테이션 적용 완료
- ✅ LDAP 서버 테스트 데이터 준비 완료 (KR: CSCA 7개, DSC 216개)

---

## Phase 4.6 Tasks

### 1. Controller Endpoint Integration Tests

**File**: `src/test/java/com/smartcoreinc/localpkd/passiveauthentication/infrastructure/web/PassiveAuthenticationControllerTest.java`

**Test Framework**: Spring Boot Test with MockMvc

#### 1.1 POST /api/v1/pa/verify - Valid Request Tests

```java
@Test
@DisplayName("POST /verify - Valid passport data should return 200 OK with VALID status")
void shouldVerifyValidPassport() throws Exception {
    // Given: Valid request with SOD and Data Groups
    PassiveAuthenticationRequest request = new PassiveAuthenticationRequest(
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

    // When: POST /api/v1/pa/verify
    mockMvc.perform(post("/api/v1/pa/verify")
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
    mockMvc.perform(post("/api/v1/pa/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        // Then: Response should be 200 OK but status INVALID
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INVALID"))
        .andExpect(jsonPath("$.errors").isNotEmpty())
        .andExpect(jsonPath("$.dataGroupValidation.invalidGroups").value(greaterThan(0)))
        .andDo(print());
}
```

#### 1.2 GET /api/v1/pa/history - Pagination Tests

```java
@Test
@DisplayName("GET /history - Should return paginated verification history")
void shouldReturnPaginatedHistory() throws Exception {
    // Given: Multiple PA verifications exist in database
    // (Performed in @BeforeEach setup)

    // When: GET /api/v1/pa/history?page=0&size=10
    mockMvc.perform(get("/api/v1/pa/history")
            .param("page", "0")
            .param("size", "10"))
        // Then: Response should be 200 OK with Page structure
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(lessThanOrEqualTo(10)))
        .andExpect(jsonPath("$.pageable.pageNumber").value(0))
        .andExpect(jsonPath("$.pageable.pageSize").value(10))
        .andExpect(jsonPath("$.totalElements").exists())
        .andDo(print());
}

@Test
@DisplayName("GET /history - Should filter by country code")
void shouldFilterByCountry() throws Exception {
    // When: GET /api/v1/pa/history?issuingCountry=KOR
    mockMvc.perform(get("/api/v1/pa/history")
            .param("issuingCountry", "KOR"))
        // Then: All results should be KOR
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].issuingCountry").value(everyItem(equalTo("KOR"))))
        .andDo(print());
}

@Test
@DisplayName("GET /history - Should filter by status")
void shouldFilterByStatus() throws Exception {
    // When: GET /api/v1/pa/history?status=VALID
    mockMvc.perform(get("/api/v1/pa/history")
            .param("status", "VALID"))
        // Then: All results should be VALID
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].status").value(everyItem(equalTo("VALID"))))
        .andDo(print());
}
```

#### 1.3 GET /api/v1/pa/{verificationId} - Single Result Tests

```java
@Test
@DisplayName("GET /{id} - Should return verification result by ID")
void shouldReturnVerificationById() throws Exception {
    // Given: Perform a verification and get its ID
    UUID verificationId = performVerificationAndGetId();

    // When: GET /api/v1/pa/{verificationId}
    mockMvc.perform(get("/api/v1/pa/{verificationId}", verificationId))
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
    mockMvc.perform(get("/api/v1/pa/{verificationId}", nonExistentId))
        // Then: Response should be 404 Not Found
        .andExpect(status().isNotFound())
        .andDo(print());
}
```

---

### 2. Request/Response Validation Tests

**File**: Same as above, additional test methods

#### 2.1 Request Validation Tests

```java
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
    mockMvc.perform(post("/api/v1/pa/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidRequest))
        // Then: Response should be 400 Bad Request
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorMessage").value(containsString("발급 국가 코드는 필수입니다")))
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
    mockMvc.perform(post("/api/v1/pa/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        // Then: Response should be 400 Bad Request
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorMessage").value(containsString("ISO 3166-1 alpha-3")))
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
    mockMvc.perform(post("/api/v1/pa/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidRequest))
        // Then: Response should be 400 Bad Request
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorMessage").value(containsString("Invalid Data Group key: DG99")))
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
    mockMvc.perform(post("/api/v1/pa/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        // Then: Response should be 400 Bad Request
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorMessage").value(containsString("최소 하나의 Data Group이 필요합니다")))
        .andDo(print());
}
```

#### 2.2 Response Format Tests

```java
@Test
@DisplayName("POST /verify - Response should have correct JSON structure")
void shouldReturnCorrectJsonStructure() throws Exception {
    // Given: Valid request
    PassiveAuthenticationRequest request = buildValidRequest();

    // When: POST /api/v1/pa/verify
    mockMvc.perform(post("/api/v1/pa/verify")
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
    MvcResult result = mockMvc.perform(post("/api/v1/pa/verify")
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
```

---

### 3. Error Handling Tests

**File**: Same as above

#### 3.1 HTTP Error Status Tests

```java
@Test
@DisplayName("POST /verify - Malformed JSON should return 400 Bad Request")
void shouldReturn400ForMalformedJson() throws Exception {
    // Given: Malformed JSON
    String malformedJson = "{issuingCountry: KOR, documentNumber: "; // Invalid JSON

    // When: POST /api/v1/pa/verify
    mockMvc.perform(post("/api/v1/pa/verify")
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
    mockMvc.perform(post("/api/v1/pa/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        // Then: Response should be 400 Bad Request
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andDo(print());
}

@Test
@DisplayName("GET /{id} - Invalid UUID format should return 400")
void shouldReturn400ForInvalidUuidFormat() throws Exception {
    // Given: Invalid UUID format
    String invalidUuid = "not-a-uuid";

    // When: GET /api/v1/pa/{verificationId}
    mockMvc.perform(get("/api/v1/pa/{verificationId}", invalidUuid))
        // Then: Response should be 400 Bad Request
        .andExpect(status().isBadRequest())
        .andDo(print());
}

@Test
@DisplayName("POST /verify - DSC not found in LDAP should return 404")
void shouldReturn404WhenDscNotFound() throws Exception {
    // Given: Request with DSC that doesn't exist in LDAP
    PassiveAuthenticationRequest request = new PassiveAuthenticationRequest(
        "KOR",
        "M12345678",
        Base64.getEncoder().encodeToString(sodBytes),
        Map.of("DG1", Base64.getEncoder().encodeToString(dg1Bytes)),
        null
    );

    // Mock: Clear all DSC certificates from database
    certificateRepository.deleteAllByType(CertificateType.DSC);

    // When: POST /api/v1/pa/verify
    mockMvc.perform(post("/api/v1/pa/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        // Then: Response should be 404 Not Found
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("DSC_NOT_FOUND"))
        .andExpect(jsonPath("$.errorMessage").value(containsString("DSC 인증서를 OpenLDAP에서 찾을 수 없습니다")))
        .andDo(print());
}

@Test
@DisplayName("POST /verify - Unexpected exception should return 500 Internal Server Error")
void shouldReturn500ForUnexpectedException() throws Exception {
    // Given: Mock UseCase to throw unexpected exception
    when(performPassiveAuthenticationUseCase.execute(any()))
        .thenThrow(new RuntimeException("Unexpected database error"));

    PassiveAuthenticationRequest request = buildValidRequest();

    // When: POST /api/v1/pa/verify
    mockMvc.perform(post("/api/v1/pa/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        // Then: Response should be 500 Internal Server Error
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.errorMessage").value(containsString("PA 검증 중 오류가 발생했습니다")))
        .andDo(print());
}
```

#### 3.2 Error Message Tests

```java
@Test
@DisplayName("Error response should contain errorCode and errorMessage")
void shouldReturnErrorCodeAndMessage() throws Exception {
    // Given: Invalid request
    String invalidRequest = "{}"; // Missing all required fields

    // When: POST /api/v1/pa/verify
    mockMvc.perform(post("/api/v1/pa/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidRequest))
        // Then: Error response should have errorCode and errorMessage
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").exists())
        .andExpect(jsonPath("$.errorMessage").exists())
        .andExpect(jsonPath("$.errorMessage").isNotEmpty())
        .andDo(print());
}
```

---

### 4. Client Metadata Extraction Tests

**File**: Same as above

#### 4.1 IP Address Extraction Tests

```java
@Test
@DisplayName("Should extract client IP address from RemoteAddr")
void shouldExtractClientIpFromRemoteAddr() throws Exception {
    // Given: Valid request
    PassiveAuthenticationRequest request = buildValidRequest();

    // When: POST with specific RemoteAddr
    mockMvc.perform(post("/api/v1/pa/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
            .with(req -> {
                req.setRemoteAddr("192.168.1.100");
                return req;
            }))
        .andExpect(status().isOk())
        .andDo(print());

    // Then: Verify audit log contains correct IP
    // (Check via repository or UseCase capture)
}

@Test
@DisplayName("Should extract client IP from X-Forwarded-For header")
void shouldExtractClientIpFromXForwardedFor() throws Exception {
    // Given: Valid request with X-Forwarded-For header
    PassiveAuthenticationRequest request = buildValidRequest();

    // When: POST with X-Forwarded-For
    mockMvc.perform(post("/api/v1/pa/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Forwarded-For", "203.0.113.1, 198.51.100.1")
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andDo(print());

    // Then: Should extract first IP (203.0.113.1)
    // Verify via ArgumentCaptor on UseCase
}
```

#### 4.2 User Agent Extraction Tests

```java
@Test
@DisplayName("Should extract User-Agent header")
void shouldExtractUserAgent() throws Exception {
    // Given: Valid request
    PassiveAuthenticationRequest request = buildValidRequest();
    String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

    // When: POST with User-Agent header
    mockMvc.perform(post("/api/v1/pa/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .header("User-Agent", userAgent)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andDo(print());

    // Then: Verify audit log contains User-Agent
}
```

---

### 5. Security Tests (Optional, if authentication/authorization is implemented)

**File**: `src/test/java/com/smartcoreinc/localpkd/passiveauthentication/infrastructure/web/PassiveAuthenticationSecurityTest.java`

#### 5.1 Authentication Tests

```java
@Test
@DisplayName("Unauthenticated request should return 401 Unauthorized")
void shouldReturn401ForUnauthenticatedRequest() throws Exception {
    // Given: No authentication token
    PassiveAuthenticationRequest request = buildValidRequest();

    // When: POST without authentication
    mockMvc.perform(post("/api/v1/pa/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        // Then: Response should be 401 Unauthorized
        .andExpect(status().isUnauthorized())
        .andDo(print());
}

@Test
@DisplayName("Invalid API key should return 401 Unauthorized")
void shouldReturn401ForInvalidApiKey() throws Exception {
    // Given: Invalid API key
    PassiveAuthenticationRequest request = buildValidRequest();

    // When: POST with invalid API key
    mockMvc.perform(post("/api/v1/pa/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-API-Key", "invalid-key-12345")
            .content(objectMapper.writeValueAsString(request)))
        // Then: Response should be 401 Unauthorized
        .andExpect(status().isUnauthorized())
        .andDo(print());
}
```

#### 5.2 Authorization Tests

```java
@Test
@DisplayName("User without PA_VERIFY permission should return 403 Forbidden")
void shouldReturn403ForInsufficientPermissions() throws Exception {
    // Given: User with READ_ONLY permission
    PassiveAuthenticationRequest request = buildValidRequest();

    // When: POST with read-only user
    mockMvc.perform(post("/api/v1/pa/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer readonly-user-token")
            .content(objectMapper.writeValueAsString(request)))
        // Then: Response should be 403 Forbidden
        .andExpect(status().isForbidden())
        .andDo(print());
}
```

#### 5.3 Rate Limiting Tests

```java
@Test
@DisplayName("Exceeding rate limit should return 429 Too Many Requests")
void shouldReturn429WhenRateLimitExceeded() throws Exception {
    // Given: Valid request
    PassiveAuthenticationRequest request = buildValidRequest();

    // When: Send 100 requests rapidly
    for (int i = 0; i < 100; i++) {
        mockMvc.perform(post("/api/v1/pa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    // Then: Next request should be rate-limited
    mockMvc.perform(post("/api/v1/pa/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("X-RateLimit-Retry-After"))
        .andDo(print());
}
```

---

### 6. API Documentation Generation

**File**: `src/main/java/com/smartcoreinc/localpkd/passiveauthentication/infrastructure/web/PassiveAuthenticationController.java`

#### 6.1 OpenAPI Configuration

**File**: `src/main/java/com/smartcoreinc/localpkd/config/OpenApiConfig.java`

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Passive Authentication API")
                .version("1.0.0")
                .description("전자여권 무결성 검증 REST API (ICAO 9303 기반)")
                .contact(new Contact()
                    .name("SmartCore Inc.")
                    .email("support@smartcoreinc.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0")))
            .servers(List.of(
                new Server().url("http://localhost:8081").description("Local Development"),
                new Server().url("https://api.smartcoreinc.com").description("Production")
            ))
            .externalDocs(new ExternalDocumentation()
                .description("ICAO Doc 9303 Part 11")
                .url("https://www.icao.int/publications/Documents/9303_p11_cons_en.pdf"));
    }
}
```

#### 6.2 Swagger UI Access Test

```java
@Test
@DisplayName("Swagger UI should be accessible at /swagger-ui.html")
void shouldAccessSwaggerUi() throws Exception {
    mockMvc.perform(get("/swagger-ui.html"))
        .andExpect(status().isOk())
        .andDo(print());
}

@Test
@DisplayName("OpenAPI spec should be accessible at /v3/api-docs")
void shouldAccessOpenApiSpec() throws Exception {
    mockMvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.openapi").value("3.0.1"))
        .andExpect(jsonPath("$.info.title").value("Passive Authentication API"))
        .andExpect(jsonPath("$.paths./api/v1/pa/verify.post").exists())
        .andExpect(jsonPath("$.paths./api/v1/pa/history.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/pa/{verificationId}.get").exists())
        .andDo(print());
}
```

---

## Test Data Setup

### Test Fixtures Required

```java
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
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

        // Ensure LDAP has required certificates
        List<Certificate> cscas = certificateRepository.findAllByType(CertificateType.CSCA);
        assertThat(cscas).isNotEmpty();
    }

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

    private UUID performVerificationAndGetId() throws Exception {
        PassiveAuthenticationRequest request = buildValidRequest();
        
        MvcResult result = mockMvc.perform(post("/api/v1/pa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(response);
        return UUID.fromString(jsonNode.get("verificationId").asText());
    }
}
```

---

## Success Criteria

### All Tests Must:
- ✅ Use MockMvc for HTTP layer testing
- ✅ Verify request/response JSON structure
- ✅ Test all HTTP status codes (200, 400, 404, 500)
- ✅ Validate request DTOs with @Valid constraints
- ✅ Test pagination and filtering
- ✅ Extract client metadata (IP, User-Agent)
- ✅ Generate OpenAPI documentation
- ✅ Run independently without order dependencies
- ✅ Clean up test data via @Transactional rollback

### Test Coverage Target:
- **Controller Endpoints**: 3 endpoints × 3 scenarios = 9 tests
- **Request Validation**: 5 validation rules = 5 tests
- **Error Handling**: 5 error cases = 5 tests
- **Client Metadata**: 3 extraction scenarios = 3 tests
- **Security** (Optional): 4 scenarios = 4 tests
- **API Documentation**: 2 accessibility tests = 2 tests
- **Total**: 28+ integration tests minimum

---

## Maven Dependencies

### Required (Already in pom.xml ✅)

```xml
<!-- Spring Boot Test -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- Spring Boot Web (for MockMvc) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Validation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- OpenAPI/Swagger -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.7.0</version>
</dependency>

<!-- Jackson for JSON -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

---

## Implementation Order

### Recommended Sequence:

1. **Start Simple**: POST /verify with valid request (1 test)
2. **Add Validation**: Request validation tests (5 tests)
3. **Error Handling**: HTTP error status tests (5 tests)
4. **GET Endpoints**: /history and /{id} tests (6 tests)
5. **Client Metadata**: IP and User-Agent extraction (3 tests)
6. **Response Format**: JSON structure validation (2 tests)
7. **API Documentation**: Swagger UI and OpenAPI spec (2 tests)
8. **Security** (Optional): Authentication/Authorization (4 tests)

---

## Reference Documents

- [Spring Boot Testing Guide](https://docs.spring.io/spring-boot/reference/testing/index.html)
- [MockMvc Documentation](https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-framework.html)
- [SpringDoc OpenAPI](https://springdoc.org/)
- [Session 2025-12-17 Report](./SESSION_2025-12-17_PA_PHASE_4_5_INTEGRATION_TESTS.md) - Phase 4.5 Results
- [CLAUDE.md](../CLAUDE.md) - Project coding standards

---

## Next Phase (Phase 4.7)

After completing Phase 4.6, proceed to:

**Phase 4.7: Performance Testing & Optimization**
- Load testing (JMeter, Gatling)
- Concurrent request handling
- Database query optimization
- LDAP connection pooling
- Response time benchmarking
- Memory profiling

---

**Status**: ⏳ READY TO START
**Estimated Time**: 6-8 hours
**Complexity**: MEDIUM (Standard Spring Boot MVC testing)
**Priority**: HIGH (Essential for production readiness)

---

**Created by**: Claude Code (Anthropic)
**Session ID**: 2025-12-18-pa-phase-4-6-rest-api-controller-tests
