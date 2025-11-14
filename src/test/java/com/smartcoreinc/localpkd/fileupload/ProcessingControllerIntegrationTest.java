package com.smartcoreinc.localpkd.fileupload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcoreinc.localpkd.fileupload.application.response.ProcessingResponse;
import com.smartcoreinc.localpkd.fileupload.application.response.ProcessingStatusResponse;
import com.smartcoreinc.localpkd.fileupload.domain.model.*;
import com.smartcoreinc.localpkd.fileupload.domain.repository.UploadedFileRepository;
import com.smartcoreinc.localpkd.fileparsing.application.usecase.ParseLdifFileUseCase;
import com.smartcoreinc.localpkd.fileparsing.application.usecase.ParseMasterListFileUseCase;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.ValidateCertificatesUseCase;
import com.smartcoreinc.localpkd.ldapintegration.application.usecase.UploadToLdapUseCase;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Processing Controller Integration Test - API 통합 테스트 (Step 2)
 *
 * <p>MANUAL 모드 파일 처리 단계별 REST API 엔드포인트의 기능을 테스트합니다.</p>
 *
 * <h3>테스트 범위</h3>
 * <ul>
 *   <li>✅ POST /api/processing/parse/{uploadId} - 파일 파싱 시작</li>
 *   <li>✅ POST /api/processing/validate/{uploadId} - 인증서 검증 시작</li>
 *   <li>✅ POST /api/processing/upload-to-ldap/{uploadId} - LDAP 업로드 시작</li>
 *   <li>✅ GET /api/processing/status/{uploadId} - 처리 상태 조회</li>
 * </ul>
 *
 * <h3>테스트 케이스</h3>
 * <ol>
 *   <li>Parse API - MANUAL 모드: 202 ACCEPTED</li>
 *   <li>Parse API - AUTO 모드: 400 BAD REQUEST</li>
 *   <li>Parse API - 파일 없음: 404 NOT FOUND</li>
 *   <li>Parse API - 잘못된 ID: 400 BAD REQUEST</li>
 *   <li>Validate API - MANUAL 모드: 202 ACCEPTED</li>
 *   <li>Validate API - AUTO 모드: 400 BAD REQUEST</li>
 *   <li>LDAP Upload API - MANUAL 모드: 202 ACCEPTED</li>
 *   <li>LDAP Upload API - AUTO 모드: 400 BAD REQUEST</li>
 *   <li>Status API - MANUAL 모드: 200 OK with details</li>
 *   <li>Status API - AUTO 모드: 200 OK with processing mode</li>
 *   <li>E2E 워크플로우: Parse → Validate → LDAP Upload → Status</li>
 * </ol>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-30
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Processing Controller Integration Test (Step 2: API Integration Testing)")
@Transactional
class ProcessingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    private UUID manualModeUploadId;
    private UUID autoModeUploadId;
    private String uniqueHash1;
    private String uniqueHash2;

    @BeforeEach
    @Transactional
    void setUp() {
        // Generate unique hashes for each test
        uniqueHash1 = (UUID.randomUUID().toString() + UUID.randomUUID().toString())
            .replace("-", "").substring(0, 64);
        uniqueHash2 = (UUID.randomUUID().toString() + UUID.randomUUID().toString())
            .replace("-", "").substring(0, 64);

        // Create MANUAL mode test file
        manualModeUploadId = UUID.randomUUID();
        UploadId manualUploadIdVO = new UploadId(manualModeUploadId);
        UploadedFile manualFile = UploadedFile.createWithMetadata(
            manualUploadIdVO,
            FileName.of("test-manual-002-complete-000001.ldif"),
            FileHash.of(uniqueHash1),
            FileSize.ofBytes(1000L),
            FileFormat.of(FileFormat.Type.CSCA_COMPLETE_LDIF),
            null, null, null,
            ProcessingMode.MANUAL
        );
        uploadedFileRepository.save(manualFile);
        log.info("MANUAL mode test file created: uploadId={}", manualModeUploadId);

        // Create AUTO mode test file
        autoModeUploadId = UUID.randomUUID();
        UploadId autoUploadIdVO = new UploadId(autoModeUploadId);
        UploadedFile autoFile = UploadedFile.createWithMetadata(
            autoUploadIdVO,
            FileName.of("test-auto-002-complete-000001.ldif"),
            FileHash.of(uniqueHash2),
            FileSize.ofBytes(1000L),
            FileFormat.of(FileFormat.Type.CSCA_COMPLETE_LDIF),
            null, null, null,
            ProcessingMode.AUTO
        );
        uploadedFileRepository.save(autoFile);
        log.info("AUTO mode test file created: uploadId={}", autoModeUploadId);
    }

    // ==================== Parse API Tests ====================

    @Test
    @DisplayName("✅ API 1.1: POST /api/processing/parse - MANUAL 모드 성공")
    void testParseAPI_ManualMode_Success() throws Exception {
        // When: Parse API 호출 (MANUAL mode file)
        MvcResult result = mockMvc.perform(post("/api/processing/parse/" + manualModeUploadId))
            .andExpect(status().isAccepted())  // 202 ACCEPTED
            .andExpect(content().contentType("application/json"))
            .andReturn();

        // Then: Response 검증
        String responseContent = result.getResponse().getContentAsString();
        log.info("Parse API Response (MANUAL): {}", responseContent);

        ProcessingResponse response = objectMapper.readValue(responseContent, ProcessingResponse.class);
        assertTrue(response.success(), "Response should indicate success");
        assertEquals("PARSING", response.step(), "Step should be PARSING");
        assertEquals("IN_PROGRESS", response.status(), "Status should be IN_PROGRESS");
        assertEquals(manualModeUploadId, response.uploadId(), "uploadId should match");
        assertNotNull(response.message(), "Message should not be null");
        assertEquals("VALIDATION", response.nextStep(), "Next step should be VALIDATION");
    }

    @Test
    @DisplayName("❌ API 1.2: POST /api/processing/parse - AUTO 모드 거부 (400)")
    void testParseAPI_AutoMode_Rejected() throws Exception {
        // When: Parse API 호출 (AUTO mode file)
        MvcResult result = mockMvc.perform(post("/api/processing/parse/" + autoModeUploadId))
            .andExpect(status().isBadRequest())  // 400 BAD REQUEST
            .andExpect(content().contentType("application/json"))
            .andReturn();

        // Then: Error response 검증
        String responseContent = result.getResponse().getContentAsString();
        log.info("Parse API Response (AUTO mode rejection): {}", responseContent);

        ProcessingResponse response = objectMapper.readValue(responseContent, ProcessingResponse.class);
        assertFalse(response.success(), "Response should indicate failure");
        assertEquals("REJECTED", response.status(), "Status should be REJECTED");
        assertNotNull(response.errorMessage(), "Error message should be provided");
        assertTrue(response.errorMessage().contains("MANUAL"),
            "Error message should mention MANUAL mode requirement");
    }

    @Test
    @DisplayName("❌ API 1.3: POST /api/processing/parse - 파일 없음 (404)")
    void testParseAPI_FileNotFound() throws Exception {
        // Given: Non-existent uploadId
        UUID nonExistentId = UUID.randomUUID();

        // When: Parse API 호출
        MvcResult result = mockMvc.perform(post("/api/processing/parse/" + nonExistentId))
            .andExpect(status().isNotFound())  // 404 NOT FOUND
            .andExpect(content().contentType("application/json"))
            .andReturn();

        // Then: Response 검증
        String responseContent = result.getResponse().getContentAsString();
        ProcessingResponse response = objectMapper.readValue(responseContent, ProcessingResponse.class);
        assertFalse(response.success(), "Response should indicate failure");
        assertEquals("NOT_FOUND", response.status(), "Status should be NOT_FOUND");
    }

    @Test
    @DisplayName("❌ API 1.4: POST /api/processing/parse - 잘못된 ID 형식 (400)")
    void testParseAPI_InvalidIdFormat() throws Exception {
        // When: Parse API 호출 (invalid UUID format)
        mockMvc.perform(post("/api/processing/parse/invalid-uuid-format"))
            .andExpect(status().isBadRequest())  // 400 BAD REQUEST
            .andExpect(content().contentType("application/json"))
            .andReturn();
    }

    // ==================== Validate API Tests ====================

    @Test
    @DisplayName("✅ API 2.1: POST /api/processing/validate - MANUAL 모드 성공")
    void testValidateAPI_ManualMode_Success() throws Exception {
        // When: Validate API 호출
        MvcResult result = mockMvc.perform(post("/api/processing/validate/" + manualModeUploadId))
            .andExpect(status().isAccepted())  // 202 ACCEPTED
            .andExpect(content().contentType("application/json"))
            .andReturn();

        // Then: Response 검증
        String responseContent = result.getResponse().getContentAsString();
        log.info("Validate API Response (MANUAL): {}", responseContent);

        ProcessingResponse response = objectMapper.readValue(responseContent, ProcessingResponse.class);
        assertTrue(response.success(), "Response should indicate success");
        assertEquals("VALIDATION", response.step(), "Step should be VALIDATION");
        assertEquals("IN_PROGRESS", response.status(), "Status should be IN_PROGRESS");
        assertEquals(manualModeUploadId, response.uploadId(), "uploadId should match");
        assertEquals("LDAP_SAVING", response.nextStep(), "Next step should be LDAP_SAVING");
    }

    @Test
    @DisplayName("❌ API 2.2: POST /api/processing/validate - AUTO 모드 거부 (400)")
    void testValidateAPI_AutoMode_Rejected() throws Exception {
        // When: Validate API 호출 (AUTO mode file)
        MvcResult result = mockMvc.perform(post("/api/processing/validate/" + autoModeUploadId))
            .andExpect(status().isBadRequest())  // 400 BAD REQUEST
            .andReturn();

        // Then: Response 검증
        String responseContent = result.getResponse().getContentAsString();
        ProcessingResponse response = objectMapper.readValue(responseContent, ProcessingResponse.class);
        assertFalse(response.success(), "Response should indicate failure");
        assertEquals("REJECTED", response.status(), "Status should be REJECTED");
    }

    @Test
    @DisplayName("❌ API 2.3: POST /api/processing/validate - 파일 없음 (404)")
    void testValidateAPI_FileNotFound() throws Exception {
        // Given: Non-existent uploadId
        UUID nonExistentId = UUID.randomUUID();

        // When: Validate API 호출
        MvcResult result = mockMvc.perform(post("/api/processing/validate/" + nonExistentId))
            .andExpect(status().isNotFound())  // 404 NOT FOUND
            .andReturn();

        // Then: Response 검증
        String responseContent = result.getResponse().getContentAsString();
        ProcessingResponse response = objectMapper.readValue(responseContent, ProcessingResponse.class);
        assertEquals("NOT_FOUND", response.status(), "Status should be NOT_FOUND");
    }

    // ==================== LDAP Upload API Tests ====================

    @Test
    @DisplayName("✅ API 3.1: POST /api/processing/upload-to-ldap - MANUAL 모드 성공")
    void testLdapUploadAPI_ManualMode_Success() throws Exception {
        // When: LDAP Upload API 호출
        MvcResult result = mockMvc.perform(post("/api/processing/upload-to-ldap/" + manualModeUploadId))
            .andExpect(status().isAccepted())  // 202 ACCEPTED
            .andExpect(content().contentType("application/json"))
            .andReturn();

        // Then: Response 검증
        String responseContent = result.getResponse().getContentAsString();
        log.info("LDAP Upload API Response (MANUAL): {}", responseContent);

        ProcessingResponse response = objectMapper.readValue(responseContent, ProcessingResponse.class);
        assertTrue(response.success(), "Response should indicate success");
        assertEquals("LDAP_SAVING", response.step(), "Step should be LDAP_SAVING");
        assertEquals("IN_PROGRESS", response.status(), "Status should be IN_PROGRESS");
        assertEquals(manualModeUploadId, response.uploadId(), "uploadId should match");
        assertEquals("COMPLETED", response.nextStep(), "Next step should be COMPLETED");
    }

    @Test
    @DisplayName("❌ API 3.2: POST /api/processing/upload-to-ldap - AUTO 모드 거부 (400)")
    void testLdapUploadAPI_AutoMode_Rejected() throws Exception {
        // When: LDAP Upload API 호출 (AUTO mode file)
        MvcResult result = mockMvc.perform(post("/api/processing/upload-to-ldap/" + autoModeUploadId))
            .andExpect(status().isBadRequest())  // 400 BAD REQUEST
            .andReturn();

        // Then: Response 검증
        String responseContent = result.getResponse().getContentAsString();
        ProcessingResponse response = objectMapper.readValue(responseContent, ProcessingResponse.class);
        assertFalse(response.success(), "Response should indicate failure");
        assertEquals("REJECTED", response.status(), "Status should be REJECTED");
    }

    @Test
    @DisplayName("❌ API 3.3: POST /api/processing/upload-to-ldap - 파일 없음 (404)")
    void testLdapUploadAPI_FileNotFound() throws Exception {
        // Given: Non-existent uploadId
        UUID nonExistentId = UUID.randomUUID();

        // When: LDAP Upload API 호출
        MvcResult result = mockMvc.perform(post("/api/processing/upload-to-ldap/" + nonExistentId))
            .andExpect(status().isNotFound())  // 404 NOT FOUND
            .andReturn();

        // Then: Response 검증
        String responseContent = result.getResponse().getContentAsString();
        ProcessingResponse response = objectMapper.readValue(responseContent, ProcessingResponse.class);
        assertEquals("NOT_FOUND", response.status(), "Status should be NOT_FOUND");
    }

    // ==================== Status API Tests ====================

    @Test
    @DisplayName("✅ API 4.1: GET /api/processing/status - MANUAL 모드 상태 조회")
    void testStatusAPI_ManualMode() throws Exception {
        // When: Status API 호출
        MvcResult result = mockMvc.perform(get("/api/processing/status/" + manualModeUploadId))
            .andExpect(status().isOk())  // 200 OK
            .andExpect(content().contentType("application/json"))
            .andReturn();

        // Then: Response 검증
        String responseContent = result.getResponse().getContentAsString();
        log.info("Status API Response (MANUAL): {}", responseContent);

        ProcessingStatusResponse response = objectMapper.readValue(responseContent, ProcessingStatusResponse.class);
        assertEquals(manualModeUploadId, response.uploadId(), "uploadId should match");
        assertTrue(response.fileName().contains("test-manual"), "fileName should contain test-manual");
        assertEquals("MANUAL", response.processingMode(), "processingMode should be MANUAL");
        assertNotNull(response.currentStage(), "currentStage should not be null");
        assertThat(response.currentPercentage(), allOf(greaterThanOrEqualTo(0), lessThanOrEqualTo(100)));
        assertNotNull(response.uploadedAt(), "uploadedAt should not be null");
    }

    @Test
    @DisplayName("✅ API 4.2: GET /api/processing/status - AUTO 모드 상태 조회")
    void testStatusAPI_AutoMode() throws Exception {
        // When: Status API 호출
        MvcResult result = mockMvc.perform(get("/api/processing/status/" + autoModeUploadId))
            .andExpect(status().isOk())  // 200 OK
            .andReturn();

        // Then: Response 검증
        String responseContent = result.getResponse().getContentAsString();
        log.info("Status API Response (AUTO): {}", responseContent);

        ProcessingStatusResponse response = objectMapper.readValue(responseContent, ProcessingStatusResponse.class);
        assertEquals(autoModeUploadId, response.uploadId(), "uploadId should match");
        assertEquals("AUTO", response.processingMode(), "processingMode should be AUTO");
    }

    @Test
    @DisplayName("❌ API 4.3: GET /api/processing/status - 파일 없음 (404)")
    void testStatusAPI_FileNotFound() throws Exception {
        // Given: Non-existent uploadId
        UUID nonExistentId = UUID.randomUUID();

        // When: Status API 호출
        mockMvc.perform(get("/api/processing/status/" + nonExistentId))
            .andExpect(status().isNotFound());  // 404 NOT FOUND
    }

    @Test
    @DisplayName("❌ API 4.4: GET /api/processing/status - 잘못된 ID 형식 (400)")
    void testStatusAPI_InvalidIdFormat() throws Exception {
        // When: Status API 호출 (invalid UUID format)
        mockMvc.perform(get("/api/processing/status/invalid-uuid-format"))
            .andExpect(status().isBadRequest());  // 400 BAD REQUEST
    }

    // ==================== E2E Workflow Tests ====================

    @Test
    @DisplayName("✅ E2E 1: 완전한 MANUAL 모드 워크플로우 검증")
    void testE2E_CompleteManualModeWorkflow() throws Exception {
        log.info("=== E2E Test: Complete MANUAL Mode Workflow ===");
        log.info("uploadId: {}", manualModeUploadId);

        // Step 1: 초기 상태 확인
        MvcResult statusResult1 = mockMvc.perform(get("/api/processing/status/" + manualModeUploadId))
            .andExpect(status().isOk())
            .andReturn();
        String statusContent1 = statusResult1.getResponse().getContentAsString();
        ProcessingStatusResponse status1 = objectMapper.readValue(statusContent1, ProcessingStatusResponse.class);
        log.info("Initial Status: processingMode={}, currentStage={}", status1.processingMode(), status1.currentStage());
        assertEquals("MANUAL", status1.processingMode(), "Should be MANUAL mode");

        // Step 2: Parse API 호출
        MvcResult parseResult = mockMvc.perform(post("/api/processing/parse/" + manualModeUploadId))
            .andExpect(status().isAccepted())
            .andReturn();
        String parseContent = parseResult.getResponse().getContentAsString();
        ProcessingResponse parseResponse = objectMapper.readValue(parseContent, ProcessingResponse.class);
        log.info("Parse Response: step={}, status={}, nextStep={}",
            parseResponse.step(), parseResponse.status(), parseResponse.nextStep());
        assertTrue(parseResponse.success(), "Parse should succeed");
        assertEquals("PARSING", parseResponse.step(), "Should be PARSING step");
        assertEquals("VALIDATION", parseResponse.nextStep(), "Next should be VALIDATION");

        // Step 3: Validate API 호출
        MvcResult validateResult = mockMvc.perform(post("/api/processing/validate/" + manualModeUploadId))
            .andExpect(status().isAccepted())
            .andReturn();
        String validateContent = validateResult.getResponse().getContentAsString();
        ProcessingResponse validateResponse = objectMapper.readValue(validateContent, ProcessingResponse.class);
        log.info("Validate Response: step={}, status={}, nextStep={}",
            validateResponse.step(), validateResponse.status(), validateResponse.nextStep());
        assertTrue(validateResponse.success(), "Validate should succeed");
        assertEquals("VALIDATION", validateResponse.step(), "Should be VALIDATION step");
        assertEquals("LDAP_SAVING", validateResponse.nextStep(), "Next should be LDAP_SAVING");

        // Step 4: LDAP Upload API 호출
        MvcResult ldapResult = mockMvc.perform(post("/api/processing/upload-to-ldap/" + manualModeUploadId))
            .andExpect(status().isAccepted())
            .andReturn();
        String ldapContent = ldapResult.getResponse().getContentAsString();
        ProcessingResponse ldapResponse = objectMapper.readValue(ldapContent, ProcessingResponse.class);
        log.info("LDAP Upload Response: step={}, status={}, nextStep={}",
            ldapResponse.step(), ldapResponse.status(), ldapResponse.nextStep());
        assertTrue(ldapResponse.success(), "LDAP upload should succeed");
        assertEquals("LDAP_SAVING", ldapResponse.step(), "Should be LDAP_SAVING step");
        assertEquals("COMPLETED", ldapResponse.nextStep(), "Next should be COMPLETED");

        // Step 5: 최종 상태 확인
        MvcResult statusResult2 = mockMvc.perform(get("/api/processing/status/" + manualModeUploadId))
            .andExpect(status().isOk())
            .andReturn();
        String statusContent2 = statusResult2.getResponse().getContentAsString();
        ProcessingStatusResponse status2 = objectMapper.readValue(statusContent2, ProcessingStatusResponse.class);
        log.info("Final Status: currentStage={}, currentPercentage={}, status={}",
            status2.currentStage(), status2.currentPercentage(), status2.status());

        log.info("=== E2E Test Completed Successfully ===");
    }

    @Test
    @DisplayName("✅ E2E 2: AUTO 모드는 API 호출 불가 검증")
    void testE2E_AutoModeAPIRejection() throws Exception {
        log.info("=== E2E Test: AUTO Mode API Rejection ===");

        // Step 1: Parse API - 거부됨
        MvcResult parseResult = mockMvc.perform(post("/api/processing/parse/" + autoModeUploadId))
            .andExpect(status().isBadRequest())
            .andReturn();
        String parseContent = parseResult.getResponse().getContentAsString();
        ProcessingResponse parseResponse = objectMapper.readValue(parseContent, ProcessingResponse.class);
        assertFalse(parseResponse.success(), "Parse should be rejected for AUTO mode");
        assertEquals("REJECTED", parseResponse.status());

        // Step 2: Validate API - 거부됨
        MvcResult validateResult = mockMvc.perform(post("/api/processing/validate/" + autoModeUploadId))
            .andExpect(status().isBadRequest())
            .andReturn();
        String validateContent = validateResult.getResponse().getContentAsString();
        ProcessingResponse validateResponse = objectMapper.readValue(validateContent, ProcessingResponse.class);
        assertFalse(validateResponse.success(), "Validate should be rejected for AUTO mode");

        // Step 3: LDAP Upload API - 거부됨
        MvcResult ldapResult = mockMvc.perform(post("/api/processing/upload-to-ldap/" + autoModeUploadId))
            .andExpect(status().isBadRequest())
            .andReturn();
        String ldapContent = ldapResult.getResponse().getContentAsString();
        ProcessingResponse ldapResponse = objectMapper.readValue(ldapContent, ProcessingResponse.class);
        assertFalse(ldapResponse.success(), "LDAP upload should be rejected for AUTO mode");

        // Step 4: Status API - 정상 작동 (AUTO 모드도 상태 조회 가능)
        mockMvc.perform(get("/api/processing/status/" + autoModeUploadId))
            .andExpect(status().isOk());

        log.info("=== E2E Test Completed: All AUTO mode API calls properly rejected ===");
    }

    @Test
    @DisplayName("✅ E2E 3: 오류 처리 종합 검증")
    void testE2E_ErrorHandling() throws Exception {
        log.info("=== E2E Test: Error Handling ===");

        // Test 1: Non-existent file for all endpoints
        UUID nonExistentId = UUID.randomUUID();

        MvcResult parseResult = mockMvc.perform(post("/api/processing/parse/" + nonExistentId))
            .andExpect(status().isNotFound())
            .andReturn();
        ProcessingResponse parseResponse = objectMapper.readValue(
            parseResult.getResponse().getContentAsString(), ProcessingResponse.class);
        assertEquals("NOT_FOUND", parseResponse.status());

        MvcResult validateResult = mockMvc.perform(post("/api/processing/validate/" + nonExistentId))
            .andExpect(status().isNotFound())
            .andReturn();
        ProcessingResponse validateResponse = objectMapper.readValue(
            validateResult.getResponse().getContentAsString(), ProcessingResponse.class);
        assertEquals("NOT_FOUND", validateResponse.status());

        MvcResult ldapResult = mockMvc.perform(post("/api/processing/upload-to-ldap/" + nonExistentId))
            .andExpect(status().isNotFound())
            .andReturn();
        ProcessingResponse ldapResponse = objectMapper.readValue(
            ldapResult.getResponse().getContentAsString(), ProcessingResponse.class);
        assertEquals("NOT_FOUND", ldapResponse.status());

        mockMvc.perform(get("/api/processing/status/" + nonExistentId))
            .andExpect(status().isNotFound());

        // Test 2: Invalid UUID format for all endpoints
        String invalidId = "invalid-uuid-format";

        mockMvc.perform(post("/api/processing/parse/" + invalidId))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/processing/validate/" + invalidId))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/processing/upload-to-ldap/" + invalidId))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/processing/status/" + invalidId))
            .andExpect(status().isBadRequest());

        log.info("=== E2E Test Completed: All error handling verified ===");
    }
}
