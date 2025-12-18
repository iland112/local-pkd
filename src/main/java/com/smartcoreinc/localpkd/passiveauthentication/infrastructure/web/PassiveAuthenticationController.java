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

@Tag(name = "Passive Authentication API", description = "전자여권 무결성 검증 REST API (ICAO 9303 기반)")
@RestController
@RequestMapping("/api/v1/pa")
@RequiredArgsConstructor
@Slf4j
public class PassiveAuthenticationController {

    private final PerformPassiveAuthenticationUseCase performPassiveAuthenticationUseCase;
    private final GetPassiveAuthenticationHistoryUseCase getPassiveAuthenticationHistoryUseCase;
    private final com.smartcoreinc.localpkd.passiveauthentication.domain.port.SodParserPort sodParserPort;

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
     * <p><b>Request Body Example:</b>
     * <pre>
     * {
     *   "issuingCountry": "KOR",  // ISO 3166-1 alpha-2 or alpha-3
     *   "documentNumber": "M12345678",
     *   "sod": "MIIGhwYJKoZIhvcNAQcCoIIG...",  // Base64-encoded SOD (PKCS#7)
     *   "dataGroups": {
     *     "DG1": "MRZ data base64...",
     *     "DG2": "Face image base64..."
     *   },
     *   "requestedBy": "border-control-officer-123"  // Optional
     * }
     * </pre>
     *
     * <p><b>Response Example (Success):</b>
     * <pre>
     * {
     *   "verificationId": "550e8400-e29b-41d4-a716-446655440000",
     *   "success": true,
     *   "verifiedAt": "2025-12-18T10:30:00Z",
     *   "details": {
     *     "certificateChainValid": true,
     *     "sodSignatureValid": true,
     *     "dataGroupHashesValid": true,
     *     "verifiedDataGroups": ["DG1", "DG2"],
     *     "hashAlgorithm": "SHA-256",
     *     "signatureAlgorithm": "SHA256withRSA"
     *   }
     * }
     * </pre>
     *
     * @param request Passive Authentication verification request
     * @param httpRequest HTTP servlet request for extracting client metadata
     * @return ResponseEntity with PassiveAuthenticationResponse
     */
    @Operation(
        summary = "전자여권 무결성 검증",
        description = "SOD 서명 검증, 인증서 체인 검증, Data Group 해시 검증을 수행합니다.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "검증 성공 (검증 결과는 success 필드로 확인)",
                content = @Content(schema = @Schema(implementation = PassiveAuthenticationResponse.class))
            ),
            @ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (필수 필드 누락, 잘못된 Base64 인코딩 등)"
            ),
            @ApiResponse(
                responseCode = "500",
                description = "서버 오류 (LDAP 연결 실패, 인증서 파싱 오류 등)"
            )
        }
    )
    @PostMapping("/verify")
    public ResponseEntity<PassiveAuthenticationResponse> verify(
        @Parameter(description = "Passive Authentication 검증 요청", required = true)
        @Valid @RequestBody PassiveAuthenticationRequest request,
        HttpServletRequest httpRequest
    ) {
        log.info("Received Passive Authentication verification request for document: {} from country: {}",
            request.documentNumber(), request.issuingCountry());

        try {
            // Decode Base64-encoded SOD
            byte[] sodBytes = Base64.getDecoder().decode(request.sod());

            // Decode Base64-encoded Data Groups
            Map<DataGroupNumber, byte[]> dataGroupBytes = new HashMap<>();
            request.dataGroups().forEach((dgNumberStr, base64Data) -> {
                DataGroupNumber dgNumber = DataGroupNumber.valueOf(dgNumberStr);
                byte[] dgBytes = Base64.getDecoder().decode(base64Data);
                dataGroupBytes.put(dgNumber, dgBytes);
            });

            // Extract client metadata for audit
            String clientIp = extractClientIpAddress(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");
            String requestedBy = request.requestedBy() != null ? request.requestedBy() : "anonymous";

            // Extract DSC Subject DN and Serial Number from SOD
            com.smartcoreinc.localpkd.passiveauthentication.domain.port.DscInfo dscInfo =
                sodParserPort.extractDscInfo(sodBytes);

            log.info("Extracted DSC from SOD - Subject: {}, Serial: {}",
                dscInfo.subjectDn(), dscInfo.serialNumber());

            // Build command using constructor (Record class)
            PerformPassiveAuthenticationCommand command = new PerformPassiveAuthenticationCommand(
                CountryCode.of(request.issuingCountry()),
                request.documentNumber(),
                sodBytes,
                dscInfo.subjectDn(),
                dscInfo.serialNumber(),
                dataGroupBytes,
                clientIp,
                userAgent,
                requestedBy
            );

            // Execute verification use case
            PassiveAuthenticationResponse response = performPassiveAuthenticationUseCase.execute(command);

            log.info("Passive Authentication verification completed - Status: {}, VerificationId: {}",
                response.status(), response.verificationId());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid Base64 encoding in request: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid Base64 encoding: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Passive Authentication verification failed", e);
            throw e;
        }
    }

    /**
     * Retrieves Passive Authentication verification history.
     *
     * <p><b>Query Parameters:</b>
     * <ul>
     *   <li><b>page:</b> Page number (0-indexed, default: 0)</li>
     *   <li><b>size:</b> Page size (default: 20, max: 100)</li>
     *   <li><b>sort:</b> Sort criteria (e.g., "verifiedAt,desc")</li>
     *   <li><b>issuingCountry:</b> Filter by country (optional, ISO 3166-1 alpha-2)</li>
     *   <li><b>success:</b> Filter by verification result (optional, true/false)</li>
     * </ul>
     *
     * <p><b>Response Example:</b>
     * <pre>
     * {
     *   "content": [
     *     {
     *       "verificationId": "550e8400-e29b-41d4-a716-446655440000",
     *       "success": true,
     *       "verifiedAt": "2025-12-18T10:30:00Z",
     *       ...
     *     }
     *   ],
     *   "totalElements": 150,
     *   "totalPages": 8,
     *   "size": 20,
     *   "number": 0
     * }
     * </pre>
     *
     * @param issuingCountry Optional country filter (ISO 3166-1 alpha-2)
     * @param success Optional verification result filter
     * @param pageable Pagination parameters
     * @return ResponseEntity with Page of PassiveAuthenticationResponse
     */
    @Operation(
        summary = "검증 이력 조회",
        description = "Passive Authentication 검증 이력을 페이징하여 조회합니다.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = @Content(schema = @Schema(implementation = Page.class))
            )
        }
    )
    @GetMapping("/history")
    public ResponseEntity<java.util.List<PassiveAuthenticationResponse>> getHistory(
        @Parameter(description = "발급 국가 (ISO 3166-1 alpha-2)", example = "KR")
        @RequestParam(required = false) String issuingCountry,

        @Parameter(description = "검증 성공 여부", example = "true")
        @RequestParam(required = false) Boolean success,

        @PageableDefault(size = 20, sort = "verifiedAt,desc") Pageable pageable
    ) {
        log.info("Retrieving Passive Authentication history");

        // TODO: Implement filtering and pagination
        // For now, return all verifications
        java.util.List<PassiveAuthenticationResponse> history = getPassiveAuthenticationHistoryUseCase.getAll();

        log.info("Retrieved {} verification records", history.size());

        return ResponseEntity.ok(history);
    }

    /**
     * Retrieves a specific Passive Authentication verification result.
     *
     * <p><b>Response Example:</b>
     * <pre>
     * {
     *   "verificationId": "550e8400-e29b-41d4-a716-446655440000",
     *   "success": true,
     *   "verifiedAt": "2025-12-18T10:30:00Z",
     *   "details": {
     *     "certificateChainValid": true,
     *     "sodSignatureValid": true,
     *     "dataGroupHashesValid": true,
     *     ...
     *   }
     * }
     * </pre>
     *
     * @param verificationId Verification UUID
     * @return ResponseEntity with PassiveAuthenticationResponse
     */
    @Operation(
        summary = "검증 결과 조회",
        description = "특정 검증 ID에 대한 상세 결과를 조회합니다.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = @Content(schema = @Schema(implementation = PassiveAuthenticationResponse.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "검증 결과를 찾을 수 없음"
            )
        }
    )
    @GetMapping("/{verificationId}")
    public ResponseEntity<PassiveAuthenticationResponse> getVerificationResult(
        @Parameter(description = "검증 ID (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable UUID verificationId
    ) {
        log.info("Retrieving verification result for ID: {}", verificationId);

        PassiveAuthenticationResponse response = getPassiveAuthenticationHistoryUseCase.getById(verificationId);

        return ResponseEntity.ok(response);
    }

    /**
     * Extracts client IP address from HTTP request.
     * <p>
     * Checks X-Forwarded-For header first (for proxied requests),
     * then falls back to remote address.
     *
     * @param request HTTP servlet request
     * @return Client IP address
     */
    private String extractClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For may contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Request DTO for Passive Authentication verification.
     *
     * @param issuingCountry Issuing country (ISO 3166-1 alpha-2 or alpha-3, e.g., "KR" or "KOR")
     * @param documentNumber Passport document number (e.g., "M12345678")
     * @param sod Base64-encoded SOD (Security Object Document, PKCS#7 SignedData)
     * @param dataGroups Map of Data Group number to Base64-encoded data (e.g., {"DG1": "...", "DG2": "..."})
     * @param requestedBy Optional requester identifier (e.g., "border-control-officer-123")
     */
    public record PassiveAuthenticationRequest(
        @Parameter(description = "발급 국가 (ISO 3166-1 alpha-2 or alpha-3)", example = "KOR", required = true)
        String issuingCountry,

        @Parameter(description = "여권 번호", example = "M12345678", required = true)
        String documentNumber,

        @Parameter(description = "Base64 인코딩된 SOD (PKCS#7 SignedData)", required = true)
        String sod,

        @Parameter(description = "Base64 인코딩된 Data Groups (DG1, DG2 등)", required = true)
        Map<String, String> dataGroups,

        @Parameter(description = "요청자 식별자 (선택)", example = "border-control-officer-123")
        String requestedBy
    ) {
    }
}
