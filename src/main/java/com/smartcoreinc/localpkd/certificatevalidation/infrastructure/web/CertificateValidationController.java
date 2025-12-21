package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.ValidateCertificateCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.ValidateCertificateResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.ValidateCertificateUseCase;
import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web.request.CertificateValidationRequest;
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
 * CertificateValidationController - 인증서 검증 REST API 컨트롤러
 *
 * <p><b>Endpoint</b>: POST /api/validate</p>
 *
 * <p><b>요청 예시</b>:</p>
 * <pre>{@code
 * POST /api/validate
 * Content-Type: application/json
 *
 * {
 *   "certificateId": "550e8400-e29b-41d4-a716-446655440000",
 *   "validateSignature": true,
 *   "validateChain": true,
 *   "checkRevocation": true,
 *   "validateValidity": true,
 *   "validateConstraints": true
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
 *   "message": "인증서 검증 완료",
 *   "certificateId": "550e8400-e29b-41d4-a716-446655440000",
 *   "subjectDn": "CN=example.com, O=Example Corp, C=KR",
 *   "issuerDn": "CN=DSC, O=Example Corp, C=KR",
 *   "serialNumber": "0a1b2c3d4e5f",
 *   "fingerprint": "a1b2c3d4...",
 *   "overallStatus": "VALID",
 *   "signatureValid": true,
 *   "chainValid": true,
 *   "notRevoked": true,
 *   "validityValid": true,
 *   "constraintsValid": true,
 *   "validatedAt": "2025-10-25T14:30:00",
 *   "durationMillis": 250
 * }
 * }</pre>
 *
 * <p><b>응답 예시 (검증 실패)</b>:</p>
 * <pre>{@code
 * HTTP/1.1 200 OK
 * Content-Type: application/json
 *
 * {
 *   "success": false,
 *   "message": "인증서 검증 실패",
 *   "certificateId": "550e8400-e29b-41d4-a716-446655440000",
 *   "overallStatus": "INVALID",
 *   "signatureValid": false,
 *   "validationErrors": [
 *     {
 *       "errorCode": "SIGNATURE_VERIFICATION_FAILED",
 *       "errorMessage": "서명 검증 실패"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p><b>검증 옵션</b>:</p>
 * <ul>
 *   <li>validateSignature: 디지털 서명 검증</li>
 *   <li>validateChain: Trust Chain 검증</li>
 *   <li>checkRevocation: 폐기 여부 확인 (CRL)</li>
 *   <li>validateValidity: 유효기간 검증</li>
 *   <li>validateConstraints: 기본 제약사항 검증</li>
 * </ul>
 *
 * @see ValidateCertificateRequest
 * @see ValidateCertificateResponse
 * @see ValidateCertificateUseCase
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Tag(name = "인증서 검증 API", description = "인증서의 유효성을 검증하는 API")
@Slf4j
@RestController
@RequestMapping("/api/validate")
@RequiredArgsConstructor
public class CertificateValidationController {

    private final ValidateCertificateUseCase validateCertificateUseCase;

    @Operation(summary = "인증서 검증",
               description = "주어진 ID의 인증서에 대해 다양한 검증(서명, 체인, 폐기, 유효기간, 제약사항)을 수행합니다.")
    @ApiResponse(responseCode = "200", description = "검증 성공 또는 실패 (상세 내용은 응답 본문 확인)",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = ValidateCertificateResponse.class)))
    @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터")
    @PostMapping
    public ResponseEntity<ValidateCertificateResponse> validateCertificate(
            @RequestBody CertificateValidationRequest request
    ) {
        log.info("=== Certificate validation API called ===");
        log.info("Certificate ID: {}", request.getCertificateId());
        log.debug("Request: validateSignature={}, validateChain={}, checkRevocation={}, " +
                "validateValidity={}, validateConstraints={}",
                request.isValidateSignature(), request.isValidateChain(), request.isCheckRevocation(),
                request.isValidateValidity(), request.isValidateConstraints());

        // 1. Request 검증 (GlobalExceptionHandler에서 처리)
        request.validate();

        // 2. Command 생성 (builder 사용)
        ValidateCertificateCommand command = ValidateCertificateCommand.builder()
                .certificateId(request.getCertificateId())
                .validateSignature(request.isValidateSignature())
                .validateChain(request.isValidateChain())
                .checkRevocation(request.isCheckRevocation())
                .validateValidity(request.isValidateValidity())
                .validateConstraints(request.isValidateConstraints())
                .build();

        // 3. Use Case 실행
        ValidateCertificateResponse response = validateCertificateUseCase.execute(command);

        log.info("Certificate validation completed: certificateId={}, success={}",
                request.getCertificateId(), response.success());

        // 4. Response 반환 (항상 200 OK, success 필드로 결과 표시)
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "API 상태 확인",
               description = "인증서 검증 API의 현재 상태를 확인합니다.")
    @ApiResponse(responseCode = "200", description = "API 정상 동작 중")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        log.debug("Certificate Validation API health check");
        return ResponseEntity.ok("Certificate Validation API is ready");
    }
}
