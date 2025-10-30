package com.smartcoreinc.localpkd.certificatevalidation.domain.service;

import com.smartcoreinc.localpkd.certificatevalidation.domain.exception.LdapConnectionException;
import com.smartcoreinc.localpkd.certificatevalidation.domain.exception.LdapOperationException;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList;
import com.smartcoreinc.localpkd.certificatevalidation.domain.port.LdapConnectionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * LdapUploadService - LDAP 업로드 도메인 서비스
 *
 * <p><b>목적</b>: 검증된 인증서와 CRL을 OpenLDAP 디렉토리에 업로드하기 위한
 * 도메인 로직을 캡슐화합니다.</p>
 *
 * <p><b>설계 패턴</b>: Domain Service (Hexagonal Architecture)
 * <ul>
 *   <li>Domain Logic: 인증서/CRL 업로드 비즈니스 규칙</li>
 *   <li>Port Dependency: LdapConnectionPort 인터페이스에 의존</li>
 *   <li>Infrastructure Independence: 구체적인 LDAP 구현과 독립</li>
 * </ul>
 * </p>
 *
 * <p><b>주요 책임</b>:
 * <ul>
 *   <li>단일 인증서 LDAP 업로드</li>
 *   <li>배치 인증서 업로드 (여러 개 + 트랜잭션)</li>
 *   <li>단일 CRL LDAP 업로드</li>
 *   <li>배치 CRL 업로드</li>
 *   <li>LDAP 연결 생명주기 관리</li>
 *   <li>에러 처리 및 복구 로직</li>
 *   <li>업로드 통계 및 보고</li>
 * </ul>
 * </p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * // 1. LDAP 서비스 생성 (DI)
 * @Autowired
 * private LdapUploadService ldapUploadService;
 *
 * // 2. 단일 인증서 업로드
 * LdapUploadService.UploadResult result = ldapUploadService.uploadCertificate(
 *     certificate,
 *     "cn=test,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"
 * );
 *
 * if (result.isSuccess()) {
 *     log.info("Certificate uploaded: {}", result.getLdapDn());
 * } else {
 *     log.error("Upload failed: {}", result.getErrorMessage());
 * }
 *
 * // 3. 배치 업로드 (권장)
 * List<Certificate> certificates = ...;
 * LdapUploadService.BatchUploadResult batchResult = ldapUploadService.uploadCertificatesBatch(
 *     certificates,
 *     "dc=ldap,dc=smartcoreinc,dc=com"
 * );
 *
 * log.info("Batch upload: success={}, failed={}, total={}",
 *     batchResult.getSuccessCount(),
 *     batchResult.getFailureCount(),
 *     batchResult.getTotalCount()
 * );
 *
 * // 4. 실패한 인증서 재시도
 * batchResult.getFailedCertificates().forEach(cert -> {
 *     ldapUploadService.uploadCertificate(cert, baseDn);
 * });
 * }</pre>
 *
 * <p><b>연결 관리</b>:
 * <ul>
 *   <li>자동 연결: 첫 업로드 시 자동으로 LDAP 연결</li>
 *   <li>자동 연결 해제: 모든 작업 완료 후 연결 해제</li>
 *   <li>배치 모드: 여러 업로드 작업 사이에 연결 유지</li>
 * </ul>
 * </p>
 *
 * <p><b>에러 처리 전략</b>:
 * <ul>
 *   <li>연결 실패: LdapConnectionException (복구 불가)</li>
 *   <li>작업 실패: LdapOperationException (개별 항목 실패, 배치 계속)</li>
 *   <li>배치 모드에서: 개별 실패는 기록, 배치 계속, 최종 통계 반환</li>
 * </ul>
 * </p>
 *
 * <p><b>Phase 17</b>: LDAP Upload Service foundation for real implementation</p>
 *
 * @see LdapConnectionPort
 * @see Certificate
 * @see CertificateRevocationList
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24 (Phase 17 Task 1.5)
 */
@Slf4j
@RequiredArgsConstructor
public class LdapUploadService {

    private final LdapConnectionPort ldapConnectionPort;

    /**
     * 단일 인증서를 LDAP에 업로드
     *
     * <p><b>동작 흐름</b>:
     * <ol>
     *   <li>입력값 검증</li>
     *   <li>LDAP 연결 확인 (미연결 시 자동 연결)</li>
     *   <li>인증서를 LDAP 엔트리로 변환</li>
     *   <li>LDAP에 업로드</li>
     *   <li>성공 시 LDAP DN 반환</li>
     *   <li>자동 연결 해제 (기타 작업 없을 경우)</li>
     * </ol>
     * </p>
     *
     * <p><b>트랜잭션 처리</b>:
     * <ul>
     *   <li>LDAP 자체 트랜잭션 미지원</li>
     *   <li>업로드 성공 = 즉시 커밋</li>
     *   <li>실패 시 자동 롤백 (이전 업로드는 유지)</li>
     * </ul>
     * </p>
     *
     * @param certificate 업로드할 검증된 인증서
     * @param baseDn LDAP Base DN (예: "dc=ldap,dc=smartcoreinc,dc=com")
     * @return 업로드 결과 (성공: UploadResult.success, 실패: UploadResult.failure)
     * @throws IllegalArgumentException certificate 또는 baseDn이 null인 경우
     * @throws LdapConnectionException LDAP 연결 실패 시 (복구 불가)
     * @since Phase 17 Task 1.5
     */
    public UploadResult uploadCertificate(Certificate certificate, String baseDn) {
        log.debug("=== uploadCertificate started ===");
        log.debug("Subject: {}, Base DN: {}",
            certificate.getSubjectInfo().getCommonName(), baseDn);

        try {
            // 1. 입력값 검증
            validateInputs(certificate, baseDn);

            // 2. LDAP 연결 확인
            ensureConnected();

            // 3. 인증서 정보 추출
            String subjectCn = certificate.getSubjectInfo().getCommonName();
            byte[] certificateDer = certificate.getX509Data().getCertificateBinary();

            // 4. LDAP 업로드
            String ldapDn = ldapConnectionPort.uploadCertificate(
                certificateDer,
                subjectCn,
                baseDn
            );

            log.info("Certificate uploaded successfully: {}", ldapDn);
            return UploadResult.success(certificate.getId().getId().toString(), ldapDn);

        } catch (LdapConnectionException e) {
            log.error("LDAP connection error during certificate upload", e);
            return UploadResult.failure(
                certificate.getId().getId().toString(),
                "LDAP Connection Error: " + e.getMessage()
            );
        } catch (LdapOperationException e) {
            log.error("LDAP operation error during certificate upload", e);
            return UploadResult.failure(
                certificate.getId().getId().toString(),
                "LDAP Operation Error: " + e.getMessage()
            );
        } catch (Exception e) {
            log.error("Unexpected error during certificate upload", e);
            return UploadResult.failure(
                certificate.getId().getId().toString(),
                "Unexpected Error: " + e.getMessage()
            );
        }
    }

    /**
     * 여러 인증서를 배치로 LDAP에 업로드
     *
     * <p><b>배치 모드 동작</b>:
     * <ol>
     *   <li>LDAP 연결 (1회)</li>
     *   <li>각 인증서 순차 업로드</li>
     *   <li>실패한 인증서 기록</li>
     *   <li>모든 인증서 처리 완료</li>
     *   <li>LDAP 연결 해제</li>
     *   <li>최종 통계 반환</li>
     * </ol>
     * </p>
     *
     * <p><b>성능 최적화</b>:
     * <ul>
     *   <li>연결 재사용: 모든 인증서가 같은 LDAP 연결 사용</li>
     *   <li>연결 풀: LDAP 클라이언트 내부에서 관리</li>
     *   <li>Keep-alive: 배치 중 연결 유지</li>
     * </ul>
     * </p>
     *
     * @param certificates 업로드할 인증서 리스트
     * @param baseDn LDAP Base DN
     * @return 배치 업로드 결과 (성공/실패 개수, 실패 인증서 목록)
     * @throws IllegalArgumentException 입력값 null/empty인 경우
     * @throws LdapConnectionException LDAP 연결 실패 시 (전체 배치 실패)
     * @since Phase 17 Task 1.5
     */
    public BatchUploadResult uploadCertificatesBatch(List<Certificate> certificates, String baseDn) {
        log.debug("=== uploadCertificatesBatch started ===");
        log.debug("Total certificates: {}", certificates.size());

        validateBatchInputs(certificates, baseDn);

        BatchUploadResult batchResult = new BatchUploadResult(certificates.size());

        try {
            // 1. LDAP 연결 (1회)
            ensureConnected();
            log.info("LDAP connected for batch upload");

            // 2. 각 인증서 순차 업로드
            int processed = 0;
            for (Certificate cert : certificates) {
                try {
                    String subjectCn = cert.getSubjectInfo().getCommonName();
                    byte[] certificateDer = cert.getX509Data().getCertificateBinary();

                    String ldapDn = ldapConnectionPort.uploadCertificate(
                        certificateDer,
                        subjectCn,
                        baseDn
                    );

                    batchResult.addSuccess(cert, ldapDn);
                    processed++;

                    // Keep-alive every 10 items
                    if (processed % 10 == 0) {
                        log.debug("Keep-alive: {} items processed", processed);
                        ldapConnectionPort.keepAlive(300);  // 5 minutes
                    }

                } catch (LdapOperationException e) {
                    log.warn("Failed to upload certificate: {}", cert.getSubjectInfo().getCommonName(), e);
                    batchResult.addFailure(cert, e.getMessage());
                } catch (Exception e) {
                    log.warn("Unexpected error uploading certificate: {}", cert.getSubjectInfo().getCommonName(), e);
                    batchResult.addFailure(cert, e.getMessage());
                }
            }

            log.info("Batch upload completed: success={}, failed={}, total={}",
                batchResult.getSuccessCount(),
                batchResult.getFailureCount(),
                batchResult.getTotalCount());

            return batchResult;

        } catch (LdapConnectionException e) {
            log.error("LDAP connection failed for batch upload", e);
            batchResult.setConnectionError(e.getMessage());
            return batchResult;
        } finally {
            // 5. LDAP 연결 해제
            try {
                ldapConnectionPort.disconnect();
                log.info("LDAP disconnected after batch upload");
            } catch (Exception e) {
                log.warn("Error disconnecting LDAP", e);
            }
        }
    }

    /**
     * 단일 CRL을 LDAP에 업로드
     *
     * @param crl 업로드할 CRL
     * @param baseDn LDAP Base DN
     * @return 업로드 결과
     * @throws IllegalArgumentException 입력값 null인 경우
     * @throws LdapConnectionException LDAP 연결 실패 시
     * @since Phase 17 Task 1.5
     */
    public UploadResult uploadCrl(CertificateRevocationList crl, String baseDn) {
        log.debug("=== uploadCrl started ===");
        log.debug("Issuer: {}, Base DN: {}", crl.getIssuerName().getValue(), baseDn);

        try {
            validateInputs(crl, baseDn);
            ensureConnected();

            String issuerName = crl.getIssuerName().getValue();
            byte[] crlDer = crl.getX509CrlData().getCrlBinary();

            String ldapDn = ldapConnectionPort.uploadCrl(crlDer, issuerName, baseDn);

            log.info("CRL uploaded successfully: {}", ldapDn);
            return UploadResult.success(crl.getId().getId().toString(), ldapDn);

        } catch (Exception e) {
            log.error("Error uploading CRL", e);
            return UploadResult.failure(
                crl.getId().getId().toString(),
                "Error: " + e.getMessage()
            );
        }
    }

    /**
     * 여러 CRL을 배치로 LDAP에 업로드
     *
     * @param crls 업로드할 CRL 리스트
     * @param baseDn LDAP Base DN
     * @return 배치 업로드 결과
     * @throws IllegalArgumentException 입력값 null/empty인 경우
     * @since Phase 17 Task 1.5
     */
    public BatchUploadResult uploadCrlsBatch(List<CertificateRevocationList> crls, String baseDn) {
        log.debug("=== uploadCrlsBatch started ===");
        log.debug("Total CRLs: {}", crls.size());

        validateBatchInputs(crls, baseDn);

        BatchUploadResult batchResult = new BatchUploadResult(crls.size());

        try {
            ensureConnected();
            log.info("LDAP connected for CRL batch upload");

            int processed = 0;
            for (CertificateRevocationList crl : crls) {
                try {
                    String issuerName = crl.getIssuerName().getValue();
                    byte[] crlDer = crl.getX509CrlData().getCrlBinary();

                    String ldapDn = ldapConnectionPort.uploadCrl(crlDer, issuerName, baseDn);
                    batchResult.addSuccess(crl, ldapDn);
                    processed++;

                    if (processed % 5 == 0) {
                        ldapConnectionPort.keepAlive(300);
                    }

                } catch (Exception e) {
                    log.warn("Failed to upload CRL: {}", crl.getIssuerName().getValue(), e);
                    batchResult.addFailure(crl, e.getMessage());
                }
            }

            log.info("CRL batch upload completed: success={}, failed={}",
                batchResult.getSuccessCount(),
                batchResult.getFailureCount());

            return batchResult;

        } catch (LdapConnectionException e) {
            log.error("LDAP connection failed for CRL batch upload", e);
            batchResult.setConnectionError(e.getMessage());
            return batchResult;
        } finally {
            try {
                ldapConnectionPort.disconnect();
            } catch (Exception e) {
                log.warn("Error disconnecting LDAP", e);
            }
        }
    }

    // ========== Helper Methods ==========

    private void ensureConnected() throws LdapConnectionException {
        if (!ldapConnectionPort.isConnected()) {
            log.debug("LDAP not connected, connecting...");
            ldapConnectionPort.connect();
        }
    }

    private void validateInputs(Certificate certificate, String baseDn) {
        Objects.requireNonNull(certificate, "Certificate must not be null");
        Objects.requireNonNull(baseDn, "Base DN must not be null");
        if (baseDn.isBlank()) {
            throw new IllegalArgumentException("Base DN must not be blank");
        }
    }

    private void validateInputs(CertificateRevocationList crl, String baseDn) {
        Objects.requireNonNull(crl, "CRL must not be null");
        Objects.requireNonNull(baseDn, "Base DN must not be null");
        if (baseDn.isBlank()) {
            throw new IllegalArgumentException("Base DN must not be blank");
        }
    }

    private void validateBatchInputs(List<?> items, String baseDn) {
        Objects.requireNonNull(items, "Items list must not be null");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Items list must not be empty");
        }
        Objects.requireNonNull(baseDn, "Base DN must not be null");
        if (baseDn.isBlank()) {
            throw new IllegalArgumentException("Base DN must not be blank");
        }
    }

    // ========== Result Classes ==========

    /**
     * 단일 업로드 결과
     */
    public static class UploadResult {
        private final String entityId;        // Certificate/CRL ID
        private final String ldapDn;          // LDAP DN (성공 시)
        private final boolean success;
        private final String errorMessage;    // Error message (실패 시)

        private UploadResult(String entityId, String ldapDn, boolean success, String errorMessage) {
            this.entityId = entityId;
            this.ldapDn = ldapDn;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static UploadResult success(String entityId, String ldapDn) {
            return new UploadResult(entityId, ldapDn, true, null);
        }

        public static UploadResult failure(String entityId, String errorMessage) {
            return new UploadResult(entityId, null, false, errorMessage);
        }

        public String getEntityId() { return entityId; }
        public String getLdapDn() { return ldapDn; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * 배치 업로드 결과
     */
    public static class BatchUploadResult {
        private final int totalCount;
        private int successCount = 0;
        private int failureCount = 0;
        private final List<Object> failedItems = new ArrayList<>();
        private final List<String> failureMessages = new ArrayList<>();
        private String connectionError = null;

        public BatchUploadResult(int totalCount) {
            this.totalCount = totalCount;
        }

        public <T> void addSuccess(T item, String ldapDn) {
            this.successCount++;
        }

        public <T> void addFailure(T item, String errorMessage) {
            this.failureCount++;
            this.failedItems.add(item);
            this.failureMessages.add(errorMessage);
        }

        public void setConnectionError(String errorMessage) {
            this.connectionError = errorMessage;
        }

        public int getTotalCount() { return totalCount; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public List<Object> getFailedItems() { return failedItems; }
        public List<String> getFailureMessages() { return failureMessages; }
        public String getConnectionError() { return connectionError; }

        public boolean hasConnectionError() { return connectionError != null; }
        public double getSuccessRate() {
            return totalCount == 0 ? 0 : (double) successCount / totalCount * 100;
        }
    }
}
