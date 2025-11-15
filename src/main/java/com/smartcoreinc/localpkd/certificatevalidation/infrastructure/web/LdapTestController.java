package com.smartcoreinc.localpkd.certificatevalidation.infrastructure.web;

import com.smartcoreinc.localpkd.certificatevalidation.domain.exception.LdapConnectionException;
import com.smartcoreinc.localpkd.certificatevalidation.domain.exception.LdapOperationException;
import com.smartcoreinc.localpkd.certificatevalidation.domain.port.LdapConnectionPort;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList;
import com.smartcoreinc.localpkd.certificatevalidation.infrastructure.adapter.UnboundIdLdapConnectionAdapter;
import com.unboundid.ldif.LDIFReader;
import com.unboundid.ldif.LDIFRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

/**
 * LDAP Test Controller - LDAP 업로드 테스트 엔드포인트
 *
 * <p><b>목적</b>: UnboundID LDAP 어댑터 테스트 및 수동 모드 업로드</p>
 *
 * <p><b>테스트 시나리오</b>:
 * <ul>
 *   <li>LDAP 연결 테스트</li>
 *   <li>CRL 데이터 LDAP 업로드</li>
 *   <li>LDAP 검색 및 검증</li>
 * </ul>
 * </p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-14 (Phase 17 Manual Test)
 */
@Slf4j
@RestController
@RequestMapping("/api/ldap-test")
@RequiredArgsConstructor
public class LdapTestController {

    private final LdapConnectionPort ldapConnectionPort;
    private final CertificateRevocationListRepository crlRepository;

    /**
     * LDAP 연결 상태 확인
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkLdapHealth() {
        log.info("=== LDAP Health Check Started ===");

        try {
            // Try to establish connection
            ldapConnectionPort.connect();

            boolean isConnected = ldapConnectionPort.isConnected();
            String status = ldapConnectionPort.getConnectionStatus();

            ldapConnectionPort.disconnect();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("connected", isConnected);
            response.put("connectionStatus", status);
            response.put("message", "LDAP connection successful");

            log.info("✅ LDAP Health Check: SUCCESS");
            return ResponseEntity.ok(response);

        } catch (LdapConnectionException e) {
            log.error("❌ LDAP Health Check FAILED", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "FAILURE");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * PostgreSQL의 CRL 데이터를 LDAP에 업로드
     *
     * <p><b>테스트 프로세스</b>:
     * <ol>
     *   <li>PostgreSQL에서 CRL 데이터 조회</li>
     *   <li>LDAP 연결</li>
     *   <li>각 CRL을 LDAP에 업로드</li>
     *   <li>업로드 결과 반환</li>
     * </ol>
     * </p>
     */
    @PostMapping("/upload-crls")
    public ResponseEntity<Map<String, Object>> uploadCrlsToLdap() {
        log.info("=== CRL Upload to LDAP Started (Manual Test) ===");

        try {
            // 1. PostgreSQL에서 CRL 데이터 조회
            List<CertificateRevocationList> crls = crlRepository.findAll();
            log.info("Found {} CRLs in PostgreSQL", crls.size());

            if (crls.isEmpty()) {
                return ResponseEntity.status(400).body(Map.of(
                    "status", "EMPTY",
                    "message", "No CRLs found in PostgreSQL"
                ));
            }

            // 2. LDAP 연결
            ldapConnectionPort.connect();
            log.info("✅ Connected to LDAP");

            // 3. 각 CRL을 LDAP에 업로드
            List<Map<String, String>> uploadResults = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;

            String baseDn = "dc=ldap,dc=smartcoreinc,dc=com";

            for (CertificateRevocationList crl : crls) {
                try {
                    // CRL 바이너리 데이터 추출
                    byte[] crlDer = crl.getCrlBinary();
                    String issuerName = crl.getIssuerName().getValue();

                    if (crlDer == null || crlDer.length == 0) {
                        log.warn("CRL {} has no binary content", crl.getId());
                        uploadResults.add(Map.of(
                            "issuer", issuerName,
                            "status", "SKIPPED",
                            "reason", "No binary content"
                        ));
                        continue;
                    }

                    // LDAP에 업로드
                    String dn = ldapConnectionPort.uploadCrl(crlDer, issuerName, baseDn);

                    uploadResults.add(Map.of(
                        "id", crl.getId().toString(),
                        "issuer", issuerName,
                        "status", "SUCCESS",
                        "dn", dn
                    ));
                    successCount++;
                    log.info("✅ CRL uploaded: {}", issuerName);

                } catch (LdapOperationException e) {
                    failureCount++;
                    uploadResults.add(Map.of(
                        "id", crl.getId().toString(),
                        "issuer", crl.getIssuerName().getValue(),
                        "status", "FAILURE",
                        "error", e.getMessage()
                    ));
                    log.error("❌ Failed to upload CRL: {}", crl.getIssuerName().getValue(), e);
                }
            }

            // 4. 연결 해제
            ldapConnectionPort.disconnect();
            log.info("✅ Disconnected from LDAP");

            // 5. 결과 반환
            Map<String, Object> response = new HashMap<>();
            response.put("status", failureCount == 0 ? "SUCCESS" : "PARTIAL");
            response.put("totalCount", crls.size());
            response.put("successCount", successCount);
            response.put("failureCount", failureCount);
            response.put("uploads", uploadResults);
            response.put("message", String.format(
                "CRL upload completed: %d success, %d failure",
                successCount, failureCount
            ));

            log.info("=== CRL Upload Completed: {} success, {} failure ===",
                successCount, failureCount);

            return ResponseEntity.ok(response);

        } catch (LdapConnectionException e) {
            log.error("❌ LDAP connection error during CRL upload", e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "FAILURE",
                "message", "LDAP connection error: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Unexpected error during CRL upload", e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "FAILURE",
                "message", "Unexpected error: " + e.getMessage()
            ));
        }
    }

    /**
     * LDAP에서 CRL 검색
     */
    @GetMapping("/search-crls")
    public ResponseEntity<Map<String, Object>> searchCrlsInLdap() {
        log.info("=== CRL Search in LDAP Started ===");

        try {
            // LDAP 연결
            ldapConnectionPort.connect();

            String baseDn = "dc=ldap,dc=smartcoreinc,dc=com";

            // 샘플 검색 (CSCA-related)
            List<LdapConnectionPort.LdapEntry> results =
                ldapConnectionPort.searchCrls("CSCA", baseDn);

            ldapConnectionPort.disconnect();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("count", results.size());
            response.put("results", results.stream()
                .map(entry -> Map.of(
                    "dn", entry.getDn(),
                    "cn", entry.getCn()
                ))
                .toList());

            log.info("✅ CRL Search Completed: {} results found", results.size());
            return ResponseEntity.ok(response);

        } catch (LdapConnectionException e) {
            log.error("❌ LDAP connection error during CRL search", e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "FAILURE",
                "message", "LDAP connection error: " + e.getMessage()
            ));
        } catch (LdapOperationException e) {
            log.error("❌ LDAP operation error during CRL search", e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "FAILURE",
                "message", "LDAP operation error: " + e.getMessage()
            ));
        }
    }

    /**
     * LDIF 파일을 LDAP에 업로드
     *
     * <p><b>프로세스</b>:</p>
     * <ol>
     *   <li>LDIF 파일 수신 (multipart/form-data)</li>
     *   <li>UnboundID adapter의 uploadLdifFile() 호출</li>
     *   <li>배치 처리로 모든 엔트리 LDAP 추가</li>
     *   <li>결과 반환 (성공/실패 통계)</li>
     * </ol>
     * </p>
     */
    @PostMapping("/upload-ldif")
    public ResponseEntity<Map<String, Object>> uploadLdifFile(
            @RequestParam("file") MultipartFile file) {
        log.info("=== LDIF file upload started ===");
        log.info("Filename: {}, Size: {} bytes", file.getOriginalFilename(), file.getSize());

        try {
            // 1. 파일 검증
            if (file.isEmpty()) {
                return ResponseEntity.status(400).body(Map.of(
                    "status", "FAILURE",
                    "message", "File is empty"
                ));
            }

            // 2. LDAP 연결
            ldapConnectionPort.connect();
            log.info("✅ Connected to LDAP");

            // 3. UnboundIdLdapConnectionAdapter 캐스팅 (uploadLdifFile 메서드 호출용)
            if (!(ldapConnectionPort instanceof UnboundIdLdapConnectionAdapter)) {
                ldapConnectionPort.disconnect();
                return ResponseEntity.status(400).body(Map.of(
                    "status", "FAILURE",
                    "message", "LDAP adapter does not support LDIF upload"
                ));
            }

            UnboundIdLdapConnectionAdapter adapter = (UnboundIdLdapConnectionAdapter) ldapConnectionPort;

            // 4. LDIF 파일 업로드
            Map<String, Object> uploadResult = adapter.uploadLdifFile(file.getInputStream());

            // 5. LDAP 연결 해제
            ldapConnectionPort.disconnect();
            log.info("✅ Disconnected from LDAP");

            // 6. 결과 반환
            log.info("=== LDIF file upload completed ===");
            log.info("Upload result: {}", uploadResult);

            return ResponseEntity.ok(uploadResult);

        } catch (IOException e) {
            log.error("❌ File read error", e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "FAILURE",
                "message", "File read error: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ LDIF upload error", e);
            try {
                ldapConnectionPort.disconnect();
            } catch (Exception ex) {
                log.error("Error disconnecting LDAP", ex);
            }
            return ResponseEntity.status(500).body(Map.of(
                "status", "FAILURE",
                "message", "LDIF upload error: " + e.getMessage()
            ));
        }
    }

    /**
     * LDAP 데이터 통계 조회
     *
     * <p>현재 LDAP에 저장된 데이터를 분석합니다.</p>
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getLdapStats() {
        log.info("=== LDAP Statistics Started ===");

        try {
            ldapConnectionPort.connect();

            String baseDn = "dc=ldap,dc=smartcoreinc,dc=com";

            // CRL 개수 조회
            List<LdapConnectionPort.LdapEntry> crlResults =
                ldapConnectionPort.searchCrls("*", baseDn);

            ldapConnectionPort.disconnect();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("totalCrlCount", crlResults.size());
            response.put("message", String.format(
                "LDAP contains %d CRLs",
                crlResults.size()
            ));

            log.info("✅ LDAP Stats: {} CRLs",
                crlResults.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error getting LDAP stats", e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "FAILURE",
                "message", "Error: " + e.getMessage()
            ));
        }
    }
}
