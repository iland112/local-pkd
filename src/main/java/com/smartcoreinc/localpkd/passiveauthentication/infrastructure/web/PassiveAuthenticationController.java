package com.smartcoreinc.localpkd.passiveauthentication.infrastructure.web;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCode;
import com.smartcoreinc.localpkd.passiveauthentication.application.command.PerformPassiveAuthenticationCommand;
import com.smartcoreinc.localpkd.passiveauthentication.application.response.PassiveAuthenticationResponse;
import com.smartcoreinc.localpkd.passiveauthentication.application.usecase.GetPassiveAuthenticationHistoryUseCase;
import com.smartcoreinc.localpkd.passiveauthentication.application.usecase.PerformPassiveAuthenticationUseCase;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroupNumber;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST API Controller for Passive Authentication (PA) verification.
 *
 * <p>Provides endpoints for:
 * <ul>
 *   <li>Verifying ePassport data integrity (POST /api/v1/pa/verify)</li>
 *   <li>Retrieving verification history (GET /api/v1/pa/history)</li>
 *   <li>Getting specific verification result (GET /api/v1/pa/{verificationId})</li>
 * </ul>
 *
 * <p><b>API Endpoint Naming Convention:</b>
 * <ul>
 *   <li>Base path: {@code /api/v1/pa} (abbreviation of Passive Authentication)</li>
 *   <li>All operations are under the "pa" prefix</li>
 * </ul>
 *
 * <p><b>ICAO 9303 Compliance:</b>
 * <ul>
 *   <li>Validates certificate chain (DSC → CSCA)</li>
 *   <li>Verifies SOD signature with DSC public key</li>
 *   <li>Compares Data Group hashes (SOD vs. actual)</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-12-12
 * @see <a href="https://www.icao.int/publications/Documents/9303_p11_cons_en.pdf">ICAO Doc 9303 Part 11</a>
 */
@Tag(name = "Passive Authentication API", description = "전자여권 무결성 검증 REST API (ICAO 9303 기반)")
@RestController
@RequestMapping("/api/v1/pa")
@RequiredArgsConstructor
@Slf4j
public class PassiveAuthenticationController {

    private final PerformPassiveAuthenticationUseCase performPassiveAuthenticationUseCase;
    private final GetPassiveAuthenticationHistoryUseCase getPassiveAuthenticationHistoryUseCase;

    /**
     * Performs Passive Authentication verification for ePassport data.
     *
     * <p><b>Verification Steps:</b>
     * <ol>
     *   <li><b>Certificate Chain Validation:</b> Verifies DSC → CSCA trust chain, checks CRL</li>
     *   <li><b>SOD Signature Verification:</b> Verifies SOD (PKCS#7 SignedData) with DSC public key</li>
     *   <li><b>Data Group Hash Verification:</b> Compares hashes in SOD with actual Data Group hashes</li>
     * </ol>
     *
     * <p><b>Request Processing:</b>
     * <ul>
     *   <li>Decodes Base64-encoded SOD and Data Groups</li>
     *   <li>Retrieves DSC/CSCA certificates from OpenLDAP</li>
     *   <li>Performs cryptographic verification</li>
     *   <li>Saves verification result and audit logs to database</li>
     * </ul>
     *
     * <p><b>Response Codes:</b>
     * <ul>
     *   <li><b>200 OK:</b> Verification completed (VALID or INVALID status in response body)</li>
     *   <li><b>400 Bad Request:</b> Invalid request format or missing required fields</li>
     *   <li><b>404 Not Found:</b> DSC/CSCA certificate not found in OpenLDAP</li>
     *   <li><b>500 Internal Server Error:</b> Unexpected error during verification</li>
     * </ul>
     *
     * @param request PA verification request containing SOD and Data Groups
     * @param httpRequest HTTP servlet request for extracting client metadata (IP, User Agent)
     * @return PA verification response with detailed validation results
     */
    @Operation(
        summary = "전자여권 PA 검증 수행",
        description = "SOD와 Data Groups를 검증하여 전자여권 데이터 무결성을 확인합니다. " +
                      "ICAO 9303 표준에 따라 인증서 체인, SOD 서명, Data Group 해시를 검증합니다."
    )
    @ApiResponse(responseCode = "200", description = "검증 완료 (결과는 response의 status 필드 참조)",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = PassiveAuthenticationResponse.class)))
    @ApiResponse(responseCode = "400", description = "잘못된 요청 (필수 필드 누락, 잘못된 형식)",
        content = @Content(mediaType = "application/json",
            schema = @Schema(example = "{\"success\": false, \"errorMessage\": \"SOD는 필수입니다\", \"errorCode\": \"VALIDATION_ERROR\"}")))
    @ApiResponse(responseCode = "404", description = "DSC/CSCA 인증서를 찾을 수 없음",
        content = @Content(mediaType = "application/json",
            schema = @Schema(example = "{\"success\": false, \"errorMessage\": \"DSC 인증서를 OpenLDAP에서 찾을 수 없습니다\", \"errorCode\": \"DSC_NOT_FOUND\"}")))
    @ApiResponse(responseCode = "500", description = "서버 내부 오류",
        content = @Content(mediaType = "application/json",
            schema = @Schema(example = "{\"success\": false, \"errorMessage\": \"PA 검증 중 오류가 발생했습니다\", \"errorCode\": \"INTERNAL_ERROR\"}")))
    @PostMapping("/verify")
    public ResponseEntity<PassiveAuthenticationResponse> performPassiveAuthentication(
        @Parameter(
            description = "PA 검증 요청 데이터 (SOD, Data Groups 포함)",
            required = true,
            schema = @Schema(implementation = PassiveAuthenticationRequest.class)
        )
        @Valid @RequestBody PassiveAuthenticationRequest request,
        HttpServletRequest httpRequest
    ) {
        log.info("PA verification request received: country={}, documentNumber={}, dataGroupCount={}",
            request.issuingCountry(), request.documentNumber(), request.getDataGroupCount());

        // Validate Data Group keys
        request.validateDataGroupKeys();

        // Decode Base64-encoded SOD
        byte[] sodBytes = Base64.getDecoder().decode(request.sod());

        // Decode Base64-encoded Data Groups and convert to DataGroupNumber map
        Map<DataGroupNumber, byte[]> dataGroupBytes = new HashMap<>();
        request.dataGroups().forEach((key, value) -> {
            DataGroupNumber dgNumber = DataGroupNumber.fromString(key);
            dataGroupBytes.put(dgNumber, Base64.getDecoder().decode(value));
        });

        // Extract client metadata for audit
        String clientIp = extractClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String requestedBy = request.requestedBy() != null ? request.requestedBy() : "anonymous";

        // TODO: Extract DSC Subject DN and Serial Number from SOD
        // For now, using placeholder values
        String dscSubjectDn = "CN=PLACEHOLDER";
        String dscSerialNumber = "000000";

        // Build command using constructor (Record class)
        PerformPassiveAuthenticationCommand command = new PerformPassiveAuthenticationCommand(
            CountryCode.of(request.issuingCountry()),
            request.documentNumber(),
            sodBytes,
            dscSubjectDn,
            dscSerialNumber,
            dataGroupBytes,
            clientIp,
            userAgent,
            requestedBy
        );

        // Execute use case
        PassiveAuthenticationResponse response = performPassiveAuthenticationUseCase.execute(command);

        log.info("PA verification completed: verificationId={}, status={}, processingTimeMs={}",
            response.verificationId(), response.status(), response.processingDurationMs());

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves Passive Authentication verification history.
     *
     * <p><b>Features:</b>
     * <ul>
     *   <li>Pagination support (default: page 0, size 20)</li>
     *   <li>Filter by issuing country (optional)</li>
     *   <li>Filter by verification status (optional)</li>
     *   <li>Sorted by verification timestamp (descending)</li>
     * </ul>
     *
     * @param issuingCountry Filter by issuing country code (optional)
     * @param status Filter by verification status (VALID/INVALID/ERROR) (optional)
     * @param pageable Pagination parameters
     * @return Paginated verification history
     */
    @Operation(
        summary = "PA 검증 이력 조회",
        description = "과거에 수행된 PA 검증 결과를 페이징하여 조회합니다. " +
                      "국가 코드, 검증 상태로 필터링할 수 있습니다."
    )
    @ApiResponse(responseCode = "200", description = "이력 조회 성공",
        content = @Content(mediaType = "application/json"))
    @ApiResponse(responseCode = "500", description = "서버 내부 오류",
        content = @Content(mediaType = "application/json"))
    @GetMapping("/history")
    public ResponseEntity<Page<PassiveAuthenticationResponse>> getVerificationHistory(
        @Parameter(
            description = "발급 국가 코드 필터 (ISO 3166-1 alpha-3)",
            example = "KOR",
            required = false
        )
        @RequestParam(required = false) String issuingCountry,

        @Parameter(
            description = "검증 상태 필터 (VALID/INVALID/ERROR)",
            example = "VALID",
            required = false
        )
        @RequestParam(required = false) String status,

        @Parameter(
            description = "페이징 파라미터 (page, size, sort)",
            example = "page=0&size=20&sort=startedAt,desc"
        )
        @PageableDefault(size = 20, sort = "startedAt") Pageable pageable
    ) {
        log.info("PA verification history request: country={}, status={}, page={}",
            issuingCountry, status, pageable.getPageNumber());

        // TODO: Implement paginated history query with filters
        // For now, returning all history
        var allHistory = getPassiveAuthenticationHistoryUseCase.getAll();

        log.warn("PA verification history: pagination not yet implemented, returning all {} results",
            allHistory.size());

        // Convert to Page (simplified implementation)
        Page<PassiveAuthenticationResponse> history = new org.springframework.data.domain.PageImpl<>(
            allHistory, pageable, allHistory.size()
        );

        return ResponseEntity.ok(history);
    }

    /**
     * Retrieves a specific Passive Authentication verification result by ID.
     *
     * @param verificationId Verification ID (UUID)
     * @return PA verification response
     */
    @Operation(
        summary = "특정 PA 검증 결과 조회",
        description = "검증 ID로 특정 PA 검증 결과의 상세 정보를 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = PassiveAuthenticationResponse.class)))
    @ApiResponse(responseCode = "404", description = "검증 결과를 찾을 수 없음",
        content = @Content(mediaType = "application/json"))
    @ApiResponse(responseCode = "500", description = "서버 내부 오류",
        content = @Content(mediaType = "application/json"))
    @GetMapping("/{verificationId}")
    public ResponseEntity<PassiveAuthenticationResponse> getVerificationById(
        @Parameter(
            description = "검증 ID (UUID)",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true
        )
        @PathVariable UUID verificationId
    ) {
        log.info("PA verification detail request: verificationId={}", verificationId);

        PassiveAuthenticationResponse response = getPassiveAuthenticationHistoryUseCase.getById(verificationId);

        log.info("PA verification detail retrieved: status={}",
            response.status());

        return ResponseEntity.ok(response);
    }

    /**
     * Extracts client IP address from HTTP request.
     * Considers X-Forwarded-For header for proxied requests.
     *
     * @param request HTTP servlet request
     * @return Client IP address
     */
    private String extractClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Return first IP in comma-separated list
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
