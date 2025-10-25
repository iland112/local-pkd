package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.ValidateCertificateCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.ValidateCertificateResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.ValidateCertificateUseCase;
import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web.request.CertificateValidationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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
@Slf4j
@RestController
@RequestMapping("/api/validate")
@RequiredArgsConstructor
public class CertificateValidationController {

    private final ValidateCertificateUseCase validateCertificateUseCase;

    /**
     * 인증서 검증 API
     *
     * <p>요청된 인증서에 대해 선택된 검증 옵션들을 수행합니다.</p>
     *
     * @param request 검증 요청 (certificateId 필수, 최소 1개 이상의 검증 옵션 필수)
     * @return 검증 결과 응답
     *   - HTTP 200: 검증 완료 (success true/false 필드로 결과 표시)
     *   - HTTP 400: 요청 파라미터 오류
     *   - HTTP 500: 서버 오류
     *
     * @apiNote
     * 모든 검증 옵션을 false로 설정할 수 없습니다. 최소 1개 이상의 검증이 필요합니다.
     *
     * @example
     * <pre>{@code
     * POST /api/validate
     * {
     *   "certificateId": "550e8400-e29b-41d4-a716-446655440000",
     *   "validateSignature": true,
     *   "validateChain": false,
     *   "checkRevocation": false,
     *   "validateValidity": true,
     *   "validateConstraints": false
     * }
     *
     * Response: 200 OK
     * {
     *   "success": true,
     *   "message": "인증서 검증 완료",
     *   "certificateId": "550e8400-e29b-41d4-a716-446655440000",
     *   ...
     * }
     * }</pre>
     */
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

    /**
     * 인증서 검증 API (Health Check)
     *
     * <p>이 엔드포인트의 가용성을 확인합니다.</p>
     *
     * @return 상태 메시지
     *
     * @example
     * <pre>{@code
     * GET /api/validate/health
     *
     * Response: 200 OK
     * "Certificate Validation API is ready"
     * }</pre>
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        log.debug("Certificate Validation API health check");
        return ResponseEntity.ok("Certificate Validation API is ready");
    }
}
