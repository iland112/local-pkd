package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.CheckRevocationCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.CheckRevocationResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.CheckRevocationUseCase;
import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web.request.RevocationCheckRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * RevocationCheckController - 인증서 폐기 여부 확인 REST API 컨트롤러
 *
 * <p><b>Endpoint</b>: POST /api/check-revocation</p>
 *
 * <p><b>폐기 확인 방식</b>:</p>
 * <ul>
 *   <li>발급자 DN 기반 CRL 조회</li>
 *   <li>일련번호 기반 폐기 목록 검사</li>
 *   <li>Fail-Open 정책: CRL 실패 시 NOT_REVOKED 반환</li>
 * </ul>
 *
 * <p><b>요청 예시</b>:</p>
 * <pre>{@code
 * POST /api/check-revocation
 * Content-Type: application/json
 *
 * {
 *   "certificateId": "550e8400-e29b-41d4-a716-446655440000",
 *   "issuerDn": "CN=CSCA KR, O=Korea, C=KR",
 *   "serialNumber": "0a1b2c3d4e5f",
 *   "forceFresh": false,
 *   "crlFetchTimeoutSeconds": 30
 * }
 * }</pre>
 *
 * <p><b>응답 예시 (NOT_REVOKED)</b>:</p>
 * <pre>{@code
 * HTTP/1.1 200 OK
 * Content-Type: application/json
 *
 * {
 *   "success": true,
 *   "message": "인증서 폐기 확인 완료",
 *   "certificateId": "550e8400-e29b-41d4-a716-446655440000",
 *   "serialNumber": "0a1b2c3d4e5f",
 *   "revocationStatus": "NOT_REVOKED",
 *   "revoked": false,
 *   "crlIssuerDn": "CN=CSCA KR, O=Korea, C=KR",
 *   "crlLastUpdate": "2025-10-24T12:00:00",
 *   "crlNextUpdate": "2025-10-31T12:00:00",
 *   "crlFromCache": false,
 *   "checkedAt": "2025-10-25T14:30:00",
 *   "durationMillis": 150
 * }
 * }</pre>
 *
 * <p><b>응답 예시 (REVOKED)</b>:</p>
 * <pre>{@code
 * HTTP/1.1 200 OK
 * Content-Type: application/json
 *
 * {
 *   "success": true,
 *   "message": "인증서가 폐기되었습니다",
 *   "certificateId": "550e8400-e29b-41d4-a716-446655440000",
 *   "serialNumber": "0a1b2c3d4e5f",
 *   "revocationStatus": "REVOKED",
 *   "revoked": true,
 *   "revokedAt": "2025-10-20T10:00:00",
 *   "revocationReasonCode": 1,
 *   "revocationReason": "keyCompromise",
 *   "crlIssuerDn": "CN=CSCA KR, O=Korea, C=KR",
 *   "crlLastUpdate": "2025-10-24T12:00:00",
 *   "crlNextUpdate": "2025-10-31T12:00:00",
 *   "checkedAt": "2025-10-25T14:30:00",
 *   "durationMillis": 150
 * }
 * }</pre>
 *
 * <p><b>응답 예시 (Fail-Open - CRL 불가)</b>:</p>
 * <pre>{@code
 * HTTP/1.1 200 OK
 * Content-Type: application/json
 *
 * {
 *   "success": true,
 *   "message": "CRL을 사용할 수 없습니다. 기본값으로 폐기되지 않은 것으로 간주합니다 (Fail-Open 정책)",
 *   "certificateId": "550e8400-e29b-41d4-a716-446655440000",
 *   "serialNumber": "0a1b2c3d4e5f",
 *   "revocationStatus": "CRL_UNAVAILABLE",
 *   "revoked": false,
 *   "checkedAt": "2025-10-25T14:30:00",
 *   "durationMillis": 5000
 * }
 * }</pre>
 *
 * <p><b>Fail-Open 정책 설명</b>:</p>
 * <p>CRL을 조회할 수 없는 경우 (네트워크 오류, 타임아웃 등) 기본값으로 NOT_REVOKED를 반환합니다.
 * 이는 보안보다는 시스템 가용성을 우선하는 전략입니다.</p>
 *
 * @see RevocationCheckRequest
 * @see CheckRevocationResponse
 * @see CheckRevocationUseCase
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Tag(name = "인증서 검증 API")
@Slf4j
@RestController
@RequestMapping("/api/check-revocation")
@RequiredArgsConstructor
public class RevocationCheckController {

    private final CheckRevocationUseCase checkRevocationUseCase;

    @Operation(summary = "인증서 폐기 여부 확인",
               description = "발급자 DN과 일련번호를 통해 인증서가 CRL(인증서 폐기 목록)에 포함되어 있는지 확인합니다.")
    @ApiResponse(responseCode = "200", description = "확인 성공 (상세 내용은 응답 본문 확인)",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = CheckRevocationResponse.class)))
    @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터")
    @PostMapping
    public ResponseEntity<CheckRevocationResponse> checkRevocation(
            @RequestBody RevocationCheckRequest request
    ) {
        log.info("=== Certificate revocation check API called ===");
        log.info("Certificate ID: {}", request.getCertificateId());
        log.debug("Request: issuerDn={}, serialNumber={}, forceFresh={}, crlFetchTimeoutSeconds={}",
                request.getIssuerDn(), request.getSerialNumber(), request.isForceFresh(),
                request.getCrlFetchTimeoutSeconds());

        long startTime = System.currentTimeMillis();

        // 1. Request 검증 (GlobalExceptionHandler에서 처리)
        request.validate();

        // 2. Command 생성 (builder 사용)
        CheckRevocationCommand command = CheckRevocationCommand.builder()
                .certificateId(request.getCertificateId())
                .issuerDn(request.getIssuerDn())
                .serialNumber(request.getSerialNumber())
                .forceFresh(request.isForceFresh())
                .crlFetchTimeoutSeconds(request.getCrlFetchTimeoutSeconds())
                .requestedBy("REST_API")
                .build();

        // 3. Use Case 실행
        CheckRevocationResponse response = checkRevocationUseCase.execute(command);

        log.info("Revocation check completed: certificateId={}, revoked={}, status={}",
                request.getCertificateId(), response.revoked(), response.revocationStatus());

        // 4. Response 반환 (항상 200 OK)
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "API 상태 확인",
               description = "폐기 확인 API의 현재 상태를 확인합니다.")
    @ApiResponse(responseCode = "200", description = "API 정상 동작 중")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        log.debug("Revocation Check API health check");
        return ResponseEntity.ok("Revocation Check API is ready");
    }
}
