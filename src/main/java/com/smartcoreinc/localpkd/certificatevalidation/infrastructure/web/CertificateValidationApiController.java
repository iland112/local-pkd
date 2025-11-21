package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.ValidateCertificatesCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.CertificatesValidatedResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.ValidateCertificatesUseCase;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateStatus;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CertificateValidationApiController - 인증서 검증 REST API
 *
 * <p><b>Endpoint</b>: /api/certificates/*</p>
 *
 * <p><b>기능</b>:</p>
 * <ul>
 *   <li>인증서 검증 시작</li>
 *   <li>CRL 검증</li>
 *   <li>검증 상태 조회</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CertificateValidationApiController {

    private final ValidateCertificatesUseCase validateCertificatesUseCase;
    private final CertificateRepository certificateRepository;

    /**
     * 인증서 검증 시작
     *
     * <p><b>Request</b>:</p>
     * <pre>
     * POST /api/certificates/validate
     * {
     *   "uploadId": "uuid",
     *   "parsedFileId": "uuid",
     *   "certificateCount": 100,
     *   "crlCount": 50
     * }
     * </pre>
     *
     * <p><b>Response (Success)</b>:</p>
     * <pre>
     * {
     *   "success": true,
     *   "uploadId": "uuid",
     *   "validCertificateCount": 98,
     *   "invalidCertificateCount": 2,
     *   "validCrlCount": 48,
     *   "invalidCrlCount": 2,
     *   "successRate": 98.0,
     *   "message": "인증서 검증 완료"
     * }
     * </pre>
     *
     * <p><b>Response (Error)</b>:</p>
     * <pre>
     * {
     *   "success": false,
     *   "errorMessage": "Error message",
     *   "errorCode": "VALIDATION_ERROR"
     * }
     * </pre>
     *
     * @param command 검증 명령
     * @return 검증 결과
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateCertificates(@RequestBody ValidateCertificatesCommand command) {
        log.info("=== Certificate validation API called ===");
        log.info("UploadId: {}, Certificates: {}, CRLs: {}",
            command.uploadId(), command.certificateCount(), command.crlCount());

        try {
            // Validate command
            command.validate();

            // Execute use case
            CertificatesValidatedResponse response = validateCertificatesUseCase.execute(command);

            // Return response
            Map<String, Object> result = new HashMap<>();
            result.put("success", response.success());
            result.put("uploadId", response.uploadId());
            result.put("validCertificateCount", response.validCertificateCount());
            result.put("invalidCertificateCount", response.invalidCertificateCount());
            result.put("validCrlCount", response.validCrlCount());
            result.put("invalidCrlCount", response.invalidCrlCount());
            result.put("successRate", response.getSuccessRate());
            result.put("totalValidated", response.getTotalValidated());
            result.put("totalValid", response.getTotalValid());
            result.put("isAllValid", response.isAllValid());
            result.put("durationMillis", response.durationMillis());
            result.put("validatedAt", response.validatedAt());

            if (response.success()) {
                result.put("message", "인증서 검증 완료");
            } else {
                result.put("message", "인증서 검증 실패");
                result.put("errorMessage", response.errorMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }

            log.info("Certificate validation completed: uploadId={}, success={}",
                command.uploadId(), response.success());

            return ResponseEntity.ok(result);

        } catch (DomainException e) {
            log.error("Domain error during certificate validation: {}", e.getMessage());
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
            log.error("Unexpected error during certificate validation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(createErrorResponse(
                false,
                "인증서 검증 중 오류가 발생했습니다: " + e.getMessage(),
                "INTERNAL_ERROR"
            ));
        }
    }

    /**
     * 검증 상태 조회
     *
     * <p>업로드 ID에 따른 검증 상태를 조회합니다.</p>
     *
     * <p><b>Response</b>:</p>
     * <pre>
     * {
     *   "uploadId": "uuid",
     *   "status": "COMPLETED", // PENDING, IN_PROGRESS, COMPLETED, FAILED
     *   "totalCertificates": 100,
     *   "validCertificates": 95,
     *   "invalidCertificates": 5,
     *   "successRate": 95.0,
     *   "message": "검증 완료"
     * }
     * </pre>
     *
     * @param uploadId 업로드 ID
     * @return 검증 상태 정보
     */
    @GetMapping("/validate/{uploadId}")
    public ResponseEntity<?> getValidationStatus(@PathVariable UUID uploadId) {
        log.debug("Getting validation status for uploadId: {}", uploadId);

        try {
            // 1. 업로드 ID로 인증서 조회
            List<Certificate> certificates = certificateRepository.findByUploadId(uploadId);

            if (certificates.isEmpty()) {
                // 인증서가 없으면 PENDING 상태
                Map<String, Object> result = new HashMap<>();
                result.put("uploadId", uploadId);
                result.put("status", "PENDING");
                result.put("totalCertificates", 0);
                result.put("validCertificates", 0);
                result.put("invalidCertificates", 0);
                result.put("successRate", 0.0);
                result.put("message", "검증 대기 중");
                return ResponseEntity.ok(result);
            }

            // 2. 상태별 인증서 개수 계산
            long totalCount = certificates.size();
            long validCount = certificates.stream()
                .filter(cert -> cert.getStatus() == CertificateStatus.VALID)
                .count();
            long invalidCount = totalCount - validCount;

            // 3. 성공률 계산
            double successRate = totalCount > 0 ? (validCount * 100.0 / totalCount) : 0.0;

            // 4. 전체 상태 판단
            String status;
            String message;

            // 모든 인증서가 검증되었는지 확인 (validationResult가 있는지 확인)
            boolean allValidated = certificates.stream()
                .allMatch(cert -> cert.getValidationResult() != null);

            if (allValidated) {
                status = "COMPLETED";
                message = String.format("검증 완료: %d개 인증서 중 %d개 유효", totalCount, validCount);
            } else {
                status = "IN_PROGRESS";
                long validatedCount = certificates.stream()
                    .filter(cert -> cert.getValidationResult() != null)
                    .count();
                message = String.format("검증 진행 중: %d/%d 완료", validatedCount, totalCount);
            }

            // 5. 응답 생성
            Map<String, Object> result = new HashMap<>();
            result.put("uploadId", uploadId);
            result.put("status", status);
            result.put("totalCertificates", totalCount);
            result.put("validCertificates", validCount);
            result.put("invalidCertificates", invalidCount);
            result.put("successRate", Math.round(successRate * 10.0) / 10.0); // 소수점 1자리
            result.put("message", message);

            log.info("Validation status retrieved: uploadId={}, status={}, valid={}/{}",
                uploadId, status, validCount, totalCount);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error getting validation status for uploadId: {}", uploadId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(createErrorResponse(
                false,
                "상태 조회 중 오류가 발생했습니다: " + e.getMessage(),
                "INTERNAL_ERROR"
            ));
        }
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
