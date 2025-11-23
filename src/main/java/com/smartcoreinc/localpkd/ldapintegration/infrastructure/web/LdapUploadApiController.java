package com.smartcoreinc.localpkd.ldapintegration.infrastructure.web;

import com.smartcoreinc.localpkd.ldapintegration.application.command.UploadToLdapCommand;
import com.smartcoreinc.localpkd.ldapintegration.application.response.UploadToLdapResponse;
import com.smartcoreinc.localpkd.ldapintegration.application.usecase.LdapHealthCheckUseCase;
import com.smartcoreinc.localpkd.ldapintegration.application.usecase.UploadToLdapUseCase;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * LdapUploadApiController - LDAP 서버 업로드 REST API
 *
 * <p><b>Endpoint</b>: /api/ldap/*</p>
 *
 * <p><b>기능</b>:</p>
 * <ul>
 *   <li>검증된 인증서/CRL LDAP 업로드 시작</li>
 *   <li>업로드 상태 조회</li>
 * </ul>
 *
 * <p><b>Event-Driven</b>:</p>
 * <ul>
 *   <li>LdapUploadCompletedEvent 발행</li>
 *   <li>SSE를 통한 실시간 진행 상황 전송</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-29
 */
@Slf4j
@RestController
@RequestMapping("/api/ldap")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LdapUploadApiController {

    private final UploadToLdapUseCase uploadToLdapUseCase;

    /**
     * 검증된 인증서/CRL을 LDAP 서버에 업로드
     *
     * <p><b>Request</b>:</p>
     * <pre>
     * POST /api/ldap/upload
     * {
     *   "uploadId": "uuid",
     *   "validCertificateCount": 795,
     *   "validCrlCount": 48,
     *   "batchSize": 100
     * }
     * </pre>
     *
     * <p><b>Response (Success)</b>:</p>
     * <pre>
     * {
     *   "success": true,
     *   "uploadId": "uuid",
     *   "uploadedCertificateCount": 787,
     *   "uploadedCrlCount": 47,
     *   "failedCertificateCount": 8,
     *   "failedCrlCount": 1,
     *   "totalUploaded": 834,
     *   "totalFailed": 9,
     *   "successRate": 98,
     *   "uploadedAt": "2025-10-29T18:00:00",
     *   "durationMillis": 5432,
     *   "message": "LDAP 업로드 완료"
     * }
     * </pre>
     *
     * <p><b>Response (Error)</b>:</p>
     * <pre>
     * {
     *   "success": false,
     *   "errorMessage": "Error message",
     *   "errorCode": "UPLOAD_ERROR"
     * }
     * </pre>
     *
     * @param command LDAP 업로드 명령
     * @return 업로드 결과
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadToLdap(@RequestBody UploadToLdapCommand command) {
        log.info("=== LDAP upload API called ===");
        log.info("UploadId: {}, Certificates: {}, CRLs: {}",
            command.uploadId(), command.validCertificateCount(), command.validCrlCount());

        try {
            // Validate command
            command.validate();

            // Execute use case
            UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

            // Return response
            Map<String, Object> result = new HashMap<>();
            result.put("success", response.success());
            result.put("uploadId", response.uploadId());
            result.put("uploadedCertificateCount", response.uploadedCertificateCount());
            result.put("uploadedCrlCount", response.uploadedCrlCount());
            result.put("failedCertificateCount", response.failedCertificateCount());
            result.put("failedCrlCount", response.failedCrlCount());
            result.put("totalUploaded", response.getTotalUploaded());
            result.put("totalFailed", response.getTotalFailed());
            result.put("successRate", response.getSuccessRate());
            result.put("uploadedAt", response.uploadedAt());
            result.put("durationMillis", response.durationMillis());
            result.put("isAllUploaded", response.isAllUploaded());

            if (response.success()) {
                result.put("message", "LDAP 업로드 완료");
                log.info("LDAP upload completed: uploadId={}, uploaded={}, failed={}",
                    command.uploadId(), response.getTotalUploaded(), response.getTotalFailed());
                return ResponseEntity.ok(result);
            } else {
                result.put("message", "LDAP 업로드 실패");
                result.put("errorMessage", response.errorMessage());
                log.error("LDAP upload failed: {}", response.errorMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }

        } catch (DomainException e) {
            log.error("Domain error during LDAP upload: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(createErrorResponse(
                false,
                e.getMessage(),
                e.getErrorCode()
            ));
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(createErrorResponse(
                false,
                e.getMessage(),
                "VALIDATION_ERROR"
            ));
        } catch (Exception e) {
            log.error("Unexpected error during LDAP upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(createErrorResponse(
                false,
                "LDAP 업로드 중 오류가 발생했습니다: " + e.getMessage(),
                "INTERNAL_ERROR"
            ));
        }
    }

    /**
     * LDAP 업로드 상태 조회
     *
     * <p>업로드 ID에 따른 현재 상태를 조회합니다.</p>
     *
     * @param uploadId 업로드 ID
     * @return 업로드 상태 정보
     */
    @GetMapping("/upload/{uploadId}")
    public ResponseEntity<?> getUploadStatus(@PathVariable UUID uploadId) {
        log.debug("Getting LDAP upload status for uploadId: {}", uploadId);

        try {
            Map<String, Object> result = new HashMap<>();
            result.put("uploadId", uploadId);
            result.put("status", "IN_PROGRESS"); // TODO: 실제 상태 조회 구현
            result.put("message", "LDAP 업로드가 진행 중입니다");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error getting LDAP upload status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(createErrorResponse(
                false,
                "상태 조회 중 오류가 발생했습니다",
                "INTERNAL_ERROR"
            ));
        }
    }

    /**
     * LDAP 서버 헬스 체크 엔드포인트
     *
     * <p>LDAP 서버와의 연결 상태를 확인하고 그 결과를 반환합니다.</p>
     *
     * @return LDAP 연결 상태 정보를 담은 JSON 응답
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkLdapHealth() {
        log.debug("Checking LDAP health status...");
        Map<String, Object> healthStatus = ldapHealthCheckUseCase.execute();
        HttpStatus status = (boolean) healthStatus.getOrDefault("success", false) ?
            HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return new ResponseEntity<>(healthStatus, status);
    }

    /**
     * 에러 응답 생성
     *
     * @param success 성공 여부
     * @param message 메시지
     * @param errorCode 에러 코드
     * @return 에러 응답 맵
     */
    private Map<String, Object> createErrorResponse(boolean success, String message, String errorCode) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("errorCode", errorCode);
        return response;
    }
}
