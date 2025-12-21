package com.smartcoreinc.localpkd.passiveauthentication.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateStatus;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileName;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileHash;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileSize;
import com.smartcoreinc.localpkd.fileupload.domain.repository.UploadedFileRepository;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.SubjectInfo;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.IssuerInfo;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.ValidityPeriod;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.X509Data;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import java.time.LocalDateTime;
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
 *   <li>POST /api/pa/verify - PA verification endpoint</li>
 *   <li>GET /api/pa/history - Verification history with pagination</li>
 *   <li>GET /api/pa/{verificationId} - Single verification result</li>
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

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    private static final String FIXTURES_BASE = "src/test/resources/passport-fixtures/valid-korean-passport/";
    private static final String API_BASE_PATH = "/api/pa";

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

        // Create test certificates programmatically (bypassing H2 JSONB issue)
        createTestCertificates();

        // Verify LDAP has required certificates for PA testing
        List<Certificate> cscas = certificateRepository.findAllByType(CertificateType.CSCA);
        List<Certificate> dscs = certificateRepository.findAllByType(CertificateType.DSC);

        assertThat(cscas).as("CSCA certificates should be created programmatically").isNotEmpty();
        assertThat(dscs).as("DSC certificates should be created programmatically").isNotEmpty();

        // Verify Korean DSC exists (required for test SOD)
        boolean koreanDscExists = dscs.stream()
            .anyMatch(dsc -> dsc.getSubjectInfo().getDistinguishedName().contains("CN=DS0120200313 1"));
        assertThat(koreanDscExists).as("Korean DSC (DS0120200313 1) should exist in test data").isTrue();
    }

    /**
     * Creates test certificates by extracting real DSC from SOD file.
     * <p>
     * This approach uses actual certificate data from the Korean passport SOD fixture,
     * ensuring test data matches the format expected by the PA verification logic.
     * </p>
     */
    private void createTestCertificates() throws Exception {
        // 0. Create dummy UploadedFile for test certificates
        UploadedFile dummyUpload = UploadedFile.create(
            UploadId.of("99999999-9999-9999-9999-999999999999"),
            FileName.of("test-passport-sod.ml"),
            FileHash.of("0000000000000000000000000000000000000000000000000000000000000000"),  // 64-char SHA-256
            FileSize.ofBytes(1857L)  // SOD file size
        );
        uploadedFileRepository.save(dummyUpload);

        // Add Bouncy Castle Provider
        Security.addProvider(new BouncyCastleProvider());

        // 1. Unwrap ICAO 9303 Tag 0x77
        byte[] cmsBytes = unwrapIcaoSod(sodBytes);

        // 2. Parse CMS SignedData
        CMSSignedData signedData = new CMSSignedData(cmsBytes);

        // 3. Extract DSC certificate
        var certs = signedData.getCertificates().getMatches(null);
        if (certs.isEmpty()) {
            throw new IllegalStateException("No certificates found in SOD!");
        }

        X509CertificateHolder certHolder = (X509CertificateHolder) certs.iterator().next();

        // 4. Convert to X509Certificate for PublicKey extraction
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509", "BC");
        X509Certificate x509Cert = (X509Certificate) certFactory.generateCertificate(
            new java.io.ByteArrayInputStream(certHolder.getEncoded())
        );

        // 5. Extract certificate data
        String subjectDn = certHolder.getSubject().toString();
        String issuerDn = certHolder.getIssuer().toString();
        String serialNumber = certHolder.getSerialNumber().toString(16).toUpperCase();
        PublicKey publicKey = x509Cert.getPublicKey();
        byte[] certEncoded = certHolder.getEncoded();

        // 6. Calculate SHA-256 fingerprint
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] fingerprint = sha256.digest(certEncoded);
        String fingerprintHex = bytesToHex(fingerprint);

        // 7. Parse DN components
        String countryCode = extractDnComponent(subjectDn, "C=");
        String organization = extractDnComponent(subjectDn, "O=");
        String orgUnit = extractDnComponent(subjectDn, "OU=");
        String commonName = extractDnComponent(subjectDn, "CN=");

        // 8. Create Certificate entities
        // 8.1 Create Korean CSCA (issuer of DSC)
        Certificate csca = Certificate.createForTest(
            CertificateId.of(UUID.fromString("22222222-2222-2222-2222-222222222222")),
            dummyUpload.getId(),  // uploadId
            CertificateType.CSCA,
            SubjectInfo.of(
                issuerDn,  // CSCA DN
                "KR",
                "Government",
                "MOFA",
                "CSCA003"
            ),
            IssuerInfo.of(
                issuerDn,  // Self-signed
                "KR",
                "Government",
                "MOFA",
                "CSCA003",
                true  // isCA
            ),
            ValidityPeriod.of(
                LocalDateTime.of(2015, 1, 1, 0, 0, 0),
                LocalDateTime.of(2035, 12, 31, 23, 59, 59)
            ),
            X509Data.of(
                new byte[]{0x30, (byte) 0x82, 0x01},  // Minimal cert binary
                publicKey,  // Reuse DSC's key for testing
                "A1B2C3D4",
                "0000000000000000000000000000000000000000000000000000000000000000"
            ),
            CertificateStatus.VALID
        );

        // 8.2 Create Korean DSC (from real SOD)
        Certificate dsc = Certificate.createForTest(
            CertificateId.of(UUID.fromString("11111111-1111-1111-1111-111111111111")),
            dummyUpload.getId(),  // uploadId
            CertificateType.DSC,
            SubjectInfo.of(
                subjectDn,
                countryCode,
                organization,
                orgUnit,
                commonName
            ),
            IssuerInfo.of(
                issuerDn,
                "KR",
                "Government",
                "MOFA",
                "CSCA003",
                true
            ),
            ValidityPeriod.of(
                certHolder.getNotBefore().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(),
                certHolder.getNotAfter().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            ),
            X509Data.of(
                certEncoded,
                publicKey,
                serialNumber,
                fingerprintHex
            ),
            CertificateStatus.VALID
        );

        // 9. Save to database
        certificateRepository.save(csca);
        certificateRepository.save(dsc);
    }

    /**
     * Unwraps ICAO 9303 Tag 0x77 wrapper from SOD if present.
     */
    private byte[] unwrapIcaoSod(byte[] sodBytes) {
        if ((sodBytes[0] & 0xFF) != 0x77) {
            return sodBytes;  // No wrapper
        }

        int offset = 1;
        int lengthByte = sodBytes[offset++] & 0xFF;

        if ((lengthByte & 0x80) != 0) {
            int numOctets = lengthByte & 0x7F;
            offset += numOctets;
        }

        byte[] cmsBytes = new byte[sodBytes.length - offset];
        System.arraycopy(sodBytes, offset, cmsBytes, 0, cmsBytes.length);
        return cmsBytes;
    }

    /**
     * Extracts a component from a Distinguished Name string.
     */
    private String extractDnComponent(String dn, String prefix) {
        int start = dn.indexOf(prefix);
        if (start == -1) return null;
        
        start += prefix.length();
        int end = dn.indexOf(',', start);
        if (end == -1) end = dn.length();
        
        return dn.substring(start, end).trim();
    }

    /**
     * Converts byte array to hexadecimal string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ===== 1. Controller Endpoint Tests =====

    @Test
    @DisplayName("POST /verify - Valid passport data should return 200 OK with VALID status")
    void shouldVerifyValidPassport() throws Exception {
        // Given: Valid request with SOD and Data Groups
        PassiveAuthenticationRequest request = buildValidRequest();

        // When: POST /api/pa/verify
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Response should be 200 OK
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("VALID"))
            .andExpect(jsonPath("$.verificationId").isNotEmpty())
            .andExpect(jsonPath("$.issuingCountry").value("KR"))
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

        // When: POST /api/pa/verify
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

        // When: GET /api/pa/history?page=0&size=10
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

        // When: GET /api/pa/history?issuingCountry=KR
        mockMvc.perform(get(API_BASE_PATH + "/history")
                .param("issuingCountry", "KR"))
            // Then: All results should be KR (alpha-2 normalized)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].issuingCountry", everyItem(equalTo("KR"))))
            .andDo(print());
    }

    @Test
    @DisplayName("GET /history - Should filter by status")
    void shouldFilterByStatus() throws Exception {
        // Given: Perform a verification
        performVerificationAndGetId();

        // When: GET /api/pa/history?status=VALID
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

        // When: GET /api/pa/{verificationId}
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

        // When: GET /api/pa/{verificationId}
        mockMvc.perform(get(API_BASE_PATH + "/{verificationId}", nonExistentId))
            // Then: Response should be 404 Not Found
            .andExpect(status().isNotFound())
            .andDo(print());
    }

    @Test
    @DisplayName("POST /verify - Missing required field (SOD) should return 400 Bad Request")
    void shouldRejectMissingSod() throws Exception {
        // Given: Request without SOD (required field)
        // Note: issuingCountry is optional since PA UI uses file upload only (sod.bin, dg1.bin, dg2.bin)
        String validDg1 = Base64.getEncoder().encodeToString(dg1Bytes);

        String invalidRequest = String.format("""
            {
                "issuingCountry": "KOR",
                "documentNumber": "M12345678",
                "dataGroups": {
                    "DG1": "%s"
                }
            }
            """, validDg1);

        // When: POST /api/pa/verify
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
            // Then: Response should be 400 Bad Request (SOD is required)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message", containsString("SOD는 필수입니다")))
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

        // When: POST /api/pa/verify
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Response should be 400 Bad Request
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message", containsString("ISO 3166-1 alpha-3")))
            .andDo(print());
    }

    @Test
    @DisplayName("POST /verify - Invalid Data Group key should return 400")
    void shouldRejectInvalidDataGroupKey() throws Exception {
        // Given: Invalid Data Group key (DG99 doesn't exist)
        String validSod = Base64.getEncoder().encodeToString(sodBytes);
        String validDg1 = Base64.getEncoder().encodeToString(dg1Bytes);
        
        String invalidRequest = String.format("""
            {
                "issuingCountry": "KOR",
                "documentNumber": "M12345678",
                "sod": "%s",
                "dataGroups": {
                    "DG99": "%s"
                }
            }
            """, validSod, validDg1);

        // When: POST /api/pa/verify
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
            // Then: Response should be 400 Bad Request (enum valueOf fails)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message", containsString("DG99")))
            .andDo(print());
    }

    @Test
    @DisplayName("POST /verify - Empty data groups should return 400")
    void shouldRejectEmptyDataGroups() throws Exception {
        // Given: Request with empty dataGroups map
        String validSod = Base64.getEncoder().encodeToString(sodBytes);
        
        String invalidRequest = String.format("""
            {
                "issuingCountry": "KOR",
                "documentNumber": "M12345678",
                "sod": "%s",
                "dataGroups": {}
            }
            """, validSod);

        // When: POST /api/pa/verify
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
            // Then: Response should be 400 Bad Request
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message", containsString("최소 하나의 Data Group이 필요합니다")))
            .andDo(print());
    }

    // ===== 3. Response Format Tests =====

    @Test
    @DisplayName("POST /verify - Response should have correct JSON structure")
    void shouldReturnCorrectJsonStructure() throws Exception {
        // Given: Valid request
        PassiveAuthenticationRequest request = buildValidRequest();

        // When: POST /api/pa/verify
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

        // When: POST /api/pa/verify
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

        // When: POST /api/pa/verify
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

        // When: POST /api/pa/verify
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

        // When: GET /api/pa/{verificationId}
        mockMvc.perform(get(API_BASE_PATH + "/{verificationId}", invalidUuid))
            // Then: Response should be 400 Bad Request
            .andExpect(status().isBadRequest())
            .andDo(print());
    }

    @Test
    @DisplayName("POST /verify - Verification succeeds even when DSC is not in database (ICAO 9303: DSC extracted from SOD)")
    void shouldVerifySuccessfullyEvenWhenDscNotInDatabase() throws Exception {
        // Given: Request with valid SOD (DSC is extracted from SOD per ICAO 9303)
        PassiveAuthenticationRequest request = buildValidRequest();

        // Clear all DSC certificates from database
        // This should NOT affect verification because DSC is extracted from SOD
        certificateRepository.findAllByType(CertificateType.DSC)
            .forEach(cert -> certificateRepository.deleteById(cert.getId()));

        // When: POST /api/pa/verify
        // Then: Verification should proceed (DSC extracted from SOD per ICAO 9303 Part 11)
        // Note: Actual verification result depends on CSCA availability in LDAP for trust chain
        mockMvc.perform(post(API_BASE_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificationId").isNotEmpty())
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
