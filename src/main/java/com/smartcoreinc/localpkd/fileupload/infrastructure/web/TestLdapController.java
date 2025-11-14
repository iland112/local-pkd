package com.smartcoreinc.localpkd.fileupload.infrastructure.web;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadCrlCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.command.UploadToLdapCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.UploadCrlResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.UploadToLdapResponse;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.UploadCrlUseCase;
import com.smartcoreinc.localpkd.certificatevalidation.application.usecase.UploadToLdapUseCase;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TestLdapController - LDAP 업로드 테스트 컨트롤러
 *
 * <p>certificatevalidation 패키지의 실제 LDAP 업로드 기능을 테스트하기 위한 임시 컨트롤러입니다.</p>
 *
 * <p><b>⚠️ WARNING</b>: 이 컨트롤러는 테스트 목적으로만 사용해야 하며, 프로덕션에서는 제거되어야 합니다.</p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-12 (LDAP DN Structure Testing)
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestLdapController {

    private final CertificateRepository certificateRepository;
    private final CertificateRevocationListRepository crlRepository;
    private final UploadToLdapUseCase uploadToLdapUseCase;
    private final UploadCrlUseCase uploadCrlUseCase;

    /**
     * LDAP 업로드 테스트 (ICAO PKD DN 구조)
     *
     * <p>데이터베이스에서 지정된 uploadId의 인증서를 조회하여 실제 LDAP 서버에 업로드합니다.</p>
     *
     * <h3>요청</h3>
     * <pre>POST /api/test/ldap-upload/{uploadId}?limit=10</pre>
     *
     * <h3>응답 예시</h3>
     * <pre>{@code
     * {
     *   "success": true,
     *   "uploaded": 10,
     *   "failed": 0,
     *   "uploadedDns": [
     *     "cn=Australia,o=csca,c=AU,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com",
     *     "cn=France,o=csca,c=FR,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com",
     *     ...
     *   ]
     * }
     * }</pre>
     *
     * @param uploadId 업로드 ID (UUID)
     * @param limit 업로드할 인증서 개수 (기본값: 10)
     * @return 업로드 결과
     */
    @PostMapping("/ldap-upload/{uploadId}")
    public ResponseEntity<?> testLdapUpload(
            @PathVariable UUID uploadId,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("=== Test LDAP upload started ===");
        log.info("Upload ID: {}, Limit: {}", uploadId, limit);

        try {
            // 1. Get certificates from database
            List<Certificate> allCertificates = certificateRepository.findByUploadId(uploadId);

            if (allCertificates.isEmpty()) {
                log.warn("No certificates found for uploadId: {}", uploadId);
                Map<String, Object> noCertsResult = new HashMap<>();
                noCertsResult.put("success", false);
                noCertsResult.put("message", "No certificates found for uploadId: " + uploadId);
                return ResponseEntity.ok(noCertsResult);
            }

            // Limit the number of certificates to upload
            List<Certificate> certificates = allCertificates.size() > limit
                ? allCertificates.subList(0, limit)
                : allCertificates;

            log.info("Found {} certificates, uploading {} certificates", allCertificates.size(), certificates.size());

            // 2. Create command
            List<UUID> certIds = certificates.stream()
                .map(cert -> cert.getId().getId())
                .collect(Collectors.toList());

            UploadToLdapCommand command = UploadToLdapCommand.builder()
                .uploadId(uploadId)
                .certificateIds(certIds)
                // baseDn not set - will use UploadToLdapUseCase's defaultBaseDn from @Value
                .isBatch(true)
                .build();

            // 3. Execute (certificatevalidation package)
            UploadToLdapResponse response = uploadToLdapUseCase.execute(command);

            log.info("LDAP upload completed: success={}, uploaded={}, failed={}",
                response.isSuccess(), response.getSuccessCount(), response.getFailedCertificateIds().size());

            // 4. Return result (using HashMap to handle potential null values)
            Map<String, Object> result = new HashMap<>();
            result.put("success", response.isSuccess());
            result.put("uploaded", response.getSuccessCount());
            result.put("failed", response.getFailedCertificateIds().size());
            result.put("uploadedDns", response.getUploadedDns());
            result.put("message", response.isSuccess()
                ? "Upload completed successfully"
                : (response.getErrorMessage() != null ? response.getErrorMessage() : "Unknown error"));
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Test LDAP upload failed", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("message", "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            return ResponseEntity.status(500).body(errorResult);
        }
    }

    /**
     * CRL LDAP 업로드 테스트
     *
     * <p>데이터베이스에서 지정된 uploadId의 CRL을 조회하여 실제 LDAP 서버에 업로드합니다.</p>
     *
     * <h3>요청</h3>
     * <pre>POST /api/test/crl-ldap-upload/{uploadId}?limit=5</pre>
     *
     * <h3>응답 예시</h3>
     * <pre>{@code
     * {
     *   "success": true,
     *   "uploaded": 5,
     *   "failed": 0,
     *   "uploadedDns": [
     *     "cn=Korea CSCA,ou=crl,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com",
     *     ...
     *   ]
     * }
     * }</pre>
     *
     * @param uploadId 업로드 ID (UUID)
     * @param limit 업로드할 CRL 개수 (기본값: 5)
     * @return 업로드 결과
     */
    @PostMapping("/crl-ldap-upload/{uploadId}")
    public ResponseEntity<?> testCrlLdapUpload(
            @PathVariable UUID uploadId,
            @RequestParam(defaultValue = "5") int limit) {

        log.info("=== Test CRL LDAP upload started ===");
        log.info("Upload ID: {}, Limit: {}", uploadId, limit);

        try {
            // 1. Get CRLs from database
            List<CertificateRevocationList> allCrls = crlRepository.findByUploadId(uploadId);

            if (allCrls.isEmpty()) {
                log.warn("No CRLs found for uploadId: {}", uploadId);
                Map<String, Object> noCrlsResult = new HashMap<>();
                noCrlsResult.put("success", false);
                noCrlsResult.put("message", "No CRLs found for uploadId: " + uploadId);
                return ResponseEntity.ok(noCrlsResult);
            }

            // Limit the number of CRLs to upload
            List<CertificateRevocationList> crls = allCrls.size() > limit
                ? allCrls.subList(0, limit)
                : allCrls;

            log.info("Found {} CRLs, uploading {} CRLs", allCrls.size(), crls.size());

            // 2. Create command
            List<java.util.UUID> crlIds = crls.stream()
                .map(crl -> crl.getId().getId())
                .collect(Collectors.toList());

            UploadCrlCommand command = UploadCrlCommand.builder()
                .uploadId(uploadId)
                .crlIds(crlIds)
                // baseDn not set - will use UploadCrlUseCase's defaultBaseDn from @Value
                .isBatch(true)
                .build();

            // 3. Execute (certificatevalidation package)
            UploadCrlResponse response = uploadCrlUseCase.execute(command);

            log.info("CRL LDAP upload completed: success={}, uploaded={}, failed={}",
                response.success(), response.successCount(), response.failedCount());

            // 4. Return result (using HashMap to handle potential null values)
            Map<String, Object> result = new HashMap<>();
            result.put("success", response.success());
            result.put("uploaded", response.successCount());
            result.put("failed", response.failedCount());
            result.put("uploadedDns", response.uploadedDns());
            result.put("totalCount", response.totalCount());
            result.put("message", response.message());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Test CRL LDAP upload failed", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("message", "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            return ResponseEntity.status(500).body(errorResult);
        }
    }
}
