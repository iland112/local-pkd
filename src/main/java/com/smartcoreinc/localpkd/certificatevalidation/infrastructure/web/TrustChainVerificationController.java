package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.VerifyTrustChainCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.VerifyTrustChainResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.VerifyTrustChainUseCase;
import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web.request.TrustChainVerificationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * TrustChainVerificationController - Trust Chain 검증 REST API 컨트롤러
 *
 * <p><b>Endpoint</b>: POST /api/verify-trust-chain</p>
 *
 * <p><b>Trust Chain 구조</b>:</p>
 * <pre>
 * CSCA (Root CA - Trust Anchor)
 *   ↓ (signed by)
 * DSC (Intermediate CA)
 *   ↓ (signed by)
 * Document Signer Certificate (End Entity)
 * </pre>
 *
 * <p><b>요청 예시</b>:</p>
 * <pre>{@code
 * POST /api/verify-trust-chain
 * Content-Type: application/json
 *
 * {
 *   "certificateId": "550e8400-e29b-41d4-a716-446655440000",
 *   "trustAnchorCountryCode": "KR",
 *   "checkRevocation": true,
 *   "validateValidity": true,
 *   "maxChainDepth": 5
 * }
 * }</pre>
 *
 * <p><b>응답 예시 (성공)</b>:</p>
 * <pre>{@code
 * HTTP/1.1 200 OK
 * Content-Type: application/json
 *
 * {
 *   "success": true,
 *   "message": "Trust Chain 검증 완료",
 *   "endEntityCertificateId": "550e8400-e29b-41d4-a716-446655440000",
 *   "trustAnchorCertificateId": "660e8400-e29b-41d4-a716-446655440001",
 *   "trustAnchorCountryCode": "KR",
 *   "chainValid": true,
 *   "chainDepth": 3,
 *   "certificateChain": [
 *     {
 *       "chainLevel": 1,
 *       "certificateId": "550e8400-e29b-41d4-a716-446655440000",
 *       "subjectDn": "CN=example.com, O=Example, C=KR",
 *       "issuerDn": "CN=DSC, O=Example, C=KR",
 *       "certificateType": "DS",
 *       "status": "VALID",
 *       "signatureValid": true
 *     },
 *     {
 *       "chainLevel": 2,
 *       "certificateId": "550e8400-e29b-41d4-a716-446655440001",
 *       "subjectDn": "CN=DSC, O=Example, C=KR",
 *       "issuerDn": "CN=CSCA, O=Example, C=KR",
 *       "certificateType": "DSC",
 *       "status": "VALID",
 *       "signatureValid": true
 *     },
 *     {
 *       "chainLevel": 3,
 *       "certificateId": "660e8400-e29b-41d4-a716-446655440001",
 *       "subjectDn": "CN=CSCA, O=Example, C=KR",
 *       "issuerDn": "CN=CSCA, O=Example, C=KR",
 *       "certificateType": "CSCA",
 *       "status": "VALID",
 *       "signatureValid": true
 *     }
 *   ],
 *   "validatedAt": "2025-10-25T14:30:00",
 *   "durationMillis": 500
 * }
 * }</pre>
 *
 * <p><b>응답 예시 (Trust Anchor 미발견)</b>:</p>
 * <pre>{@code
 * HTTP/1.1 200 OK
 * Content-Type: application/json
 *
 * {
 *   "success": false,
 *   "message": "Trust Anchor를 찾을 수 없습니다",
 *   "endEntityCertificateId": "550e8400-e29b-41d4-a716-446655440000",
 *   "chainValid": false,
 *   "chainDepth": 1
 * }
 * }</pre>
 *
 * <p><b>검증 옵션</b>:</p>
 * <ul>
 *   <li>trustAnchorCountryCode: 신뢰 앵커의 국가 코드 (선택사항, 검증용)</li>
 *   <li>checkRevocation: 체인의 각 인증서에 대해 폐기 확인</li>
 *   <li>validateValidity: 체인의 각 인증서 유효기간 검증</li>
 *   <li>maxChainDepth: 최대 체인 깊이 (1-10, 기본값 5)</li>
 * </ul>
 *
 * @see TrustChainVerificationRequest
 * @see VerifyTrustChainResponse
 * @see VerifyTrustChainUseCase
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Tag(name = "인증서 검증 API")
@Slf4j
@RestController
@RequestMapping("/api/verify-trust-chain")
@RequiredArgsConstructor
public class TrustChainVerificationController {

    private final VerifyTrustChainUseCase verifyTrustChainUseCase;

    @Operation(summary = "인증서 신뢰 체인 검증",
               description = "End-Entity 인증서부터 최상위 CSCA(Trust Anchor)까지의 전체 인증서 체인을 검증합니다.")
    @ApiResponse(responseCode = "200", description = "검증 성공 또는 실패 (상세 내용은 응답 본문 확인)",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = VerifyTrustChainResponse.class)))
    @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터")
    @PostMapping
    public ResponseEntity<VerifyTrustChainResponse> verifyTrustChain(
            @RequestBody TrustChainVerificationRequest request
    ) {
        log.info("=== Trust Chain verification API called ===");
        log.info("Certificate ID: {}", request.getCertificateId());
        log.debug("Request: trustAnchorCountryCode={}, checkRevocation={}, " +
                "validateValidity={}, maxChainDepth={}",
                request.getTrustAnchorCountryCode(), request.isCheckRevocation(),
                request.isValidateValidity(), request.getMaxChainDepth());

        // 1. Request 검증 (GlobalExceptionHandler에서 처리)
        request.validate();

        // 2. Command 생성 (builder 사용 - 모든 요청 파라미터 반영)
        VerifyTrustChainCommand command = VerifyTrustChainCommand.builder()
                .certificateId(request.getCertificateId())
                .trustAnchorCountryCode(request.getTrustAnchorCountryCode())
                .checkRevocation(request.isCheckRevocation())
                .validateValidity(request.isValidateValidity())
                .maxChainDepth(request.getMaxChainDepth())
                .build();

        // 3. Use Case 실행
        VerifyTrustChainResponse response = verifyTrustChainUseCase.execute(command);

        log.info("Trust Chain verification completed: certificateId={}, success={}, chainValid={}",
                request.getCertificateId(), response.success(), response.chainValid());

        // 4. Response 반환 (항상 200 OK, success 필드로 결과 표시)
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "API 상태 확인",
               description = "신뢰 체인 검증 API의 현재 상태를 확인합니다.")
    @ApiResponse(responseCode = "200", description = "API 정상 동작 중")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        log.debug("Trust Chain Verification API health check");
        return ResponseEntity.ok("Trust Chain Verification API is ready");
    }
}
