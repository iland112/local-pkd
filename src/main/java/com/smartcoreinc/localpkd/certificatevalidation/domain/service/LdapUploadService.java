package com.smartcoreinc.localpkd.certificatevalidation.domain.service;

import com.smartcoreinc.localpkd.certificatevalidation.domain.exception.LdapConnectionException;
import com.smartcoreinc.localpkd.certificatevalidation.domain.exception.LdapOperationException;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList;
import com.smartcoreinc.localpkd.certificatevalidation.domain.port.LdapConnectionPort;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class LdapUploadService {

    private final LdapConnectionPort ldapConnectionPort;
    private final ProgressService progressService;

    public UploadResult uploadCertificate(Certificate certificate, String baseDn) {
        log.debug("=== uploadCertificate started ===");
        log.debug("Subject: {}, Base DN: {}",
            certificate.getSubjectInfo().getCommonName(), baseDn);

        try {
            validateInputs(certificate, baseDn);
            ensureConnected();

            String subjectCn = certificate.getSubjectInfo().getCommonName();
            String countryCode = certificate.getSubjectInfo().getCountryCode();
            byte[] certificateDer = certificate.getX509Data().getCertificateBinary();

            if (countryCode == null || countryCode.trim().isEmpty()) {
                log.warn("Country code is null or empty for certificate: {}", subjectCn);
                return UploadResult.failure(
                    certificate.getId().toString(),
                    "Country code is missing from certificate: " + subjectCn
                );
            }

            String ldapDn = ldapConnectionPort.uploadCertificate(
                certificateDer, subjectCn, countryCode, baseDn);

            log.info("Certificate uploaded successfully: {}", ldapDn);
            return UploadResult.success(certificate.getId().toString(), ldapDn);

        } catch (LdapConnectionException | LdapOperationException e) {
            log.error("LDAP error during certificate upload", e);
            return UploadResult.failure(
                certificate.getId().toString(),
                "LDAP Error: " + e.getMessage()
            );
        } catch (Exception e) {
            log.error("Unexpected error during certificate upload", e);
            return UploadResult.failure(
                certificate.getId().toString(),
                "Unexpected Error: " + e.getMessage()
            );
        }
    }

    public BatchUploadResult uploadCertificatesBatch(List<Certificate> certificates, String baseDn) {
        log.debug("=== uploadCertificatesBatch started ===");
        log.debug("Total certificates: {}", certificates.size());

        validateBatchInputs(certificates, baseDn);
        BatchUploadResult batchResult = new BatchUploadResult(certificates.size());

        try {
            ensureConnected();
            log.info("LDAP connected for batch upload");

            int processed = 0;
            for (Certificate cert : certificates) {
                processed++;
                try {
                    String subjectCn = cert.getSubjectInfo().getCommonName();
                    String countryCode = cert.getSubjectInfo().getCountryCode();
                    byte[] certificateDer = cert.getX509Data().getCertificateBinary();

                    if (countryCode == null || countryCode.trim().isEmpty()) {
                        log.warn("Skipping certificate with missing country code: {}", subjectCn);
                        batchResult.addFailure(cert, "Country code is missing");
                        continue;
                    }

                    String ldapDn = ldapConnectionPort.uploadCertificate(
                        certificateDer, subjectCn, countryCode, baseDn);
                    batchResult.addSuccess(cert, ldapDn);

                    progressService.sendProgress(
                        ProcessingProgress.ldapSavingInProgress(
                            cert.getUploadId(),
                            processed, certificates.size(),
                            String.format("LDAP 저장 중: %d/%d (%s)", processed, certificates.size(), subjectCn),
                            90, 100)
                    );

                    if (processed % 10 == 0) {
                        log.debug("Keep-alive: {} items processed", processed);
                        ldapConnectionPort.keepAlive(300);
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
                batchResult.getSuccessCount(), batchResult.getFailureCount(), batchResult.getTotalCount());
            return batchResult;

        } catch (LdapConnectionException e) {
            log.error("LDAP connection failed for batch upload", e);
            batchResult.setConnectionError(e.getMessage());
            return batchResult;
        } finally {
            try {
                ldapConnectionPort.disconnect();
                log.info("LDAP disconnected after batch upload");
            } catch (Exception e) {
                log.warn("Error disconnecting LDAP", e);
            }
        }
    }

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
            return UploadResult.success(crl.getId().toString(), ldapDn);
        } catch (Exception e) {
            log.error("Error uploading CRL", e);
            return UploadResult.failure(crl.getId().toString(), "Error: " + e.getMessage());
        }
    }

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
                processed++;
                try {
                    String issuerName = crl.getIssuerName().getValue();
                    byte[] crlDer = crl.getX509CrlData().getCrlBinary();
                    String ldapDn = ldapConnectionPort.uploadCrl(crlDer, issuerName, baseDn);
                    batchResult.addSuccess(crl, ldapDn);
                    progressService.sendProgress(
                        ProcessingProgress.ldapSavingInProgress(
                            crl.getUploadId(),
                            processed, crls.size(),
                            String.format("LDAP 저장 중: %d/%d (%s)", processed, crls.size(), issuerName),
                            90, 100)
                    );
                    if (processed % 5 == 0) {
                        ldapConnectionPort.keepAlive(300);
                    }
                } catch (Exception e) {
                    log.warn("Failed to upload CRL: {}", crl.getIssuerName().getValue(), e);
                    batchResult.addFailure(crl, e.getMessage());
                }
            }
            log.info("CRL batch upload completed: success={}, failed={}",
                batchResult.getSuccessCount(), batchResult.getFailureCount());
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

    public static class UploadResult {
        private final String entityId;
        private final String ldapDn;
        private final boolean success;
        private final String errorMessage;

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