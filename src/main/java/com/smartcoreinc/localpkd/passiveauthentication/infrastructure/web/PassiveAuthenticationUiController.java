package com.smartcoreinc.localpkd.passiveauthentication.infrastructure.web;

import com.smartcoreinc.localpkd.passiveauthentication.domain.port.SodParserPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PA UI Controller
 *
 * Provides UI pages and supporting APIs for Passive Authentication verification.
 */
@Tag(name = "PA UI", description = "Passive Authentication UI 페이지 및 지원 API")
@Controller
@RequestMapping("/pa")
@RequiredArgsConstructor
@Slf4j
public class PassiveAuthenticationUiController {

    private final SodParserPort sodParserPort;

    /**
     * PA Verify Page
     */
    @GetMapping("/verify")
    public String verifyPage() {
        return "pa/verify";
    }

    /**
     * PA History Page
     */
    @GetMapping("/history")
    public String historyPage() {
        return "pa/history";
    }

    /**
     * PA Dashboard Page
     */
    @GetMapping("/dashboard")
    public String dashboardPage() {
        return "pa/dashboard";
    }

    /**
     * Get Test Data Files (SOD, DG1, DG2)
     *
     * Returns Base64-encoded test data for UI testing.
     *
     * @param scenario Test scenario name (korean-passport, valid-scenario, expired-scenario)
     * @return TestDataResponse with Base64-encoded binary data
     */
    @Operation(
        summary = "테스트 데이터 로드",
        description = "UI 테스트를 위한 실제 여권 칩 데이터 파일 제공 (SOD, DG1, DG2)"
    )
    @GetMapping("/api/test-data/{scenario}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTestData(
        @Parameter(description = "테스트 시나리오", example = "korean-passport")
        @PathVariable String scenario
    ) {
        log.info("Loading test data for scenario: {}", scenario);

        try {
            Map<String, Object> response = new HashMap<>();

            // Determine file paths based on scenario
            String basePath = switch (scenario) {
                case "korean-passport" -> "data/temp/";
                case "valid-scenario" -> "test-data/valid/";
                case "expired-scenario" -> "test-data/expired/";
                default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
            };

            // Load SOD (required)
            byte[] sodBytes = loadTestFile(basePath + "sod.bin");
            response.put("sod", Base64.getEncoder().encodeToString(sodBytes));

            // Load DG1 (optional)
            try {
                byte[] dg1Bytes = loadTestFile(basePath + "dg1.bin");
                response.put("dg1", Base64.getEncoder().encodeToString(dg1Bytes));
            } catch (IOException e) {
                log.debug("DG1 not found for scenario: {}", scenario);
            }

            // Load DG2 (optional)
            try {
                byte[] dg2Bytes = loadTestFile(basePath + "dg2.bin");
                response.put("dg2", Base64.getEncoder().encodeToString(dg2Bytes));
            } catch (IOException e) {
                log.debug("DG2 not found for scenario: {}", scenario);
            }

            response.put("scenario", scenario);
            response.put("success", true);

            log.info("Test data loaded successfully for scenario: {}", scenario);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to load test data for scenario: {}", scenario, e);
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "success", false,
                    "error", e.getMessage()
                ));
        }
    }

    /**
     * Parse SOD and extract metadata
     *
     * Extracts DSC info, hash algorithm, signature algorithm, and data group list
     * without performing full verification.
     *
     * @param request SodParseRequest with Base64-encoded SOD
     * @return SodInfo metadata
     */
    @Operation(
        summary = "SOD 파싱 (메타데이터 추출)",
        description = "검증 없이 SOD에서 DSC 정보, 해시 알고리즘, Data Group 목록 추출"
    )
    @PostMapping("/api/parse-sod")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> parseSod(
        @RequestBody Map<String, String> request
    ) {
        try {
            String sodBase64 = request.get("sodBase64");
            if (sodBase64 == null || sodBase64.isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "sodBase64 is required"));
            }

            byte[] sodBytes = Base64.getDecoder().decode(sodBase64);

            // Extract DSC info
            var dscInfo = sodParserPort.extractDscInfo(sodBytes);

            // Extract hash algorithm
            String hashAlgorithm = sodParserPort.extractHashAlgorithm(sodBytes);

            // Extract signature algorithm
            String signatureAlgorithm = sodParserPort.extractSignatureAlgorithm(sodBytes);

            // Parse data group hashes
            var dataGroupHashes = sodParserPort.parseDataGroupHashes(sodBytes);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("dscSubject", dscInfo.subjectDn());
            response.put("dscSerial", dscInfo.serialNumber());
            response.put("hashAlgorithm", hashAlgorithm);
            response.put("signatureAlgorithm", signatureAlgorithm);
            response.put("dataGroups", dataGroupHashes.keySet().stream()
                .map(dg -> dg.getValue())
                .sorted()
                .toList());

            log.info("SOD parsed successfully - DSC: {}, DGs: {}",
                dscInfo.subjectDn(), response.get("dataGroups"));

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid Base64 encoding", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid Base64 encoding: " + e.getMessage()));
        } catch (Exception e) {
            log.error("SOD parsing failed", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "SOD parsing failed: " + e.getMessage()));
        }
    }

    /**
     * Load test file from classpath or filesystem
     */
    private byte[] loadTestFile(String path) throws IOException {
        try {
            // Try classpath first
            ClassPathResource resource = new ClassPathResource(path);
            if (resource.exists()) {
                return StreamUtils.copyToByteArray(resource.getInputStream());
            }
        } catch (IOException e) {
            // Fall through to filesystem
        }

        // Try filesystem (for development)
        java.nio.file.Path fsPath = java.nio.file.Path.of(path);
        if (java.nio.file.Files.exists(fsPath)) {
            return java.nio.file.Files.readAllBytes(fsPath);
        }

        throw new IOException("Test file not found: " + path);
    }

    /**
     * Request DTO for SOD parsing
     */
    public record SodParseRequest(
        @NotBlank(message = "SOD Base64 is required")
        String sodBase64
    ) {}

    /**
     * Response DTO for test data
     */
    public record TestDataResponse(
        String sod,
        String dg1,
        String dg2,
        String scenario
    ) {}

    /**
     * Response DTO for SOD info
     */
    public record SodInfoResponse(
        String dscSubject,
        String dscSerial,
        String hashAlgorithm,
        String signatureAlgorithm,
        List<Integer> dataGroups
    ) {}
}
