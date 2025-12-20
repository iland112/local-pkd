package com.smartcoreinc.localpkd.certificatevalidation.application.service;

import com.smartcoreinc.localpkd.certificatevalidation.application.response.LdapBatchUploadResult;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList;
import com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter.LdifConverter;
import com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter.UnboundIdLdapAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * LdapBatchUploadService - LDAP 배치 업로드 서비스
 *
 * <p>인증서 및 CRL을 LDAP 서버에 배치 단위로 업로드합니다.</p>
 * <p>ValidateCertificatesUseCase에서 인터리빙 배치 처리를 위해 사용됩니다.</p>
 *
 * <p><b>주요 기능</b>:</p>
 * <ul>
 *   <li>인증서 배치 LDAP 업로드</li>
 *   <li>CRL 배치 LDAP 업로드</li>
 *   <li>LDIF 변환 및 업로드 통합</li>
 *   <li>부분 실패 처리 (일부 실패해도 계속 진행)</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * List&lt;Certificate&gt; certificates = ...;
 * LdapBatchUploadResult result = ldapBatchUploadService.uploadCertificates(certificates);
 * log.info("LDAP upload: {} success, {} skipped, {} failed",
 *     result.successCount(), result.skippedCount(), result.failedCount());
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LdapBatchUploadService {

    private final UnboundIdLdapAdapter ldapAdapter;
    private final LdifConverter ldifConverter;

    /**
     * 인증서 배치를 LDAP에 업로드
     *
     * <p>각 인증서를 LDIF 형식으로 변환하고 배치 단위로 LDAP에 업로드합니다.</p>
     * <p>업로드 성공 시 각 인증서의 uploadedToLdap 플래그를 true로 설정합니다.</p>
     *
     * @param certificates 업로드할 인증서 목록
     * @return LdapBatchUploadResult 업로드 결과
     */
    public LdapBatchUploadResult uploadCertificates(List<Certificate> certificates) {
        if (certificates == null || certificates.isEmpty()) {
            log.debug("No certificates to upload to LDAP");
            return LdapBatchUploadResult.empty();
        }

        log.info("Uploading {} certificates to LDAP", certificates.size());

        List<String> ldifEntries = new ArrayList<>();
        List<Certificate> validCertificates = new ArrayList<>();
        List<UUID> failedIds = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();
        int conversionFailedCount = 0;

        // 1. LDIF 변환
        for (Certificate cert : certificates) {
            try {
                String ldifEntry = ldifConverter.certificateToLdif(cert);
                ldifEntries.add(ldifEntry);
                validCertificates.add(cert);
            } catch (Exception e) {
                conversionFailedCount++;
                failedIds.add(cert.getId().getId());
                errorMessages.add(String.format("LDIF conversion failed for cert %s: %s",
                    cert.getId().getId(), e.getMessage()));
                log.error("Failed to convert certificate to LDIF: id={}, error={}",
                    cert.getId().getId(), e.getMessage());
            }
        }

        // 2. LDAP 배치 업로드
        int successCount = 0;
        int skippedCount = 0;

        if (!ldifEntries.isEmpty()) {
            try {
                successCount = ldapAdapter.addLdifEntriesBatch(ldifEntries);
                skippedCount = ldifEntries.size() - successCount;

                log.info("LDAP batch upload completed: {} success, {} skipped (duplicates)",
                    successCount, skippedCount);

                // 3. 업로드 성공한 인증서 플래그 설정
                // Note: 중복(skipped)도 이미 LDAP에 존재하므로 uploaded로 표시
                for (Certificate cert : validCertificates) {
                    cert.markAsUploadedToLdap();
                }
                log.debug("Marked {} certificates as uploaded to LDAP", validCertificates.size());

            } catch (Exception e) {
                log.error("LDAP batch upload failed: {}", e.getMessage(), e);
                // 배치 전체 실패 시 모든 ID를 실패로 기록
                for (Certificate cert : validCertificates) {
                    failedIds.add(cert.getId().getId());
                }
                errorMessages.add("LDAP batch upload failed: " + e.getMessage());
                return LdapBatchUploadResult.partial(
                    0,
                    0,
                    certificates.size(),
                    failedIds,
                    errorMessages
                );
            }
        }

        // 4. 결과 반환
        if (conversionFailedCount > 0) {
            return LdapBatchUploadResult.partial(
                successCount,
                skippedCount,
                conversionFailedCount,
                failedIds,
                errorMessages
            );
        }

        return LdapBatchUploadResult.success(successCount, skippedCount);
    }

    /**
     * CRL 배치를 LDAP에 업로드
     *
     * <p>각 CRL을 LDIF 형식으로 변환하고 배치 단위로 LDAP에 업로드합니다.</p>
     *
     * @param crls 업로드할 CRL 목록
     * @return LdapBatchUploadResult 업로드 결과
     */
    public LdapBatchUploadResult uploadCrls(List<CertificateRevocationList> crls) {
        if (crls == null || crls.isEmpty()) {
            log.debug("No CRLs to upload to LDAP");
            return LdapBatchUploadResult.empty();
        }

        log.info("Uploading {} CRLs to LDAP", crls.size());

        List<String> ldifEntries = new ArrayList<>();
        List<UUID> failedIds = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();
        int conversionFailedCount = 0;

        // 1. LDIF 변환
        for (CertificateRevocationList crl : crls) {
            try {
                String ldifEntry = ldifConverter.crlToLdif(crl);
                ldifEntries.add(ldifEntry);
            } catch (Exception e) {
                conversionFailedCount++;
                failedIds.add(crl.getId().getId());
                errorMessages.add(String.format("LDIF conversion failed for CRL %s: %s",
                    crl.getId().getId(), e.getMessage()));
                log.error("Failed to convert CRL to LDIF: id={}, error={}",
                    crl.getId().getId(), e.getMessage());
            }
        }

        // 2. LDAP 배치 업로드
        int successCount = 0;
        int skippedCount = 0;

        if (!ldifEntries.isEmpty()) {
            try {
                successCount = ldapAdapter.addLdifEntriesBatch(ldifEntries);
                skippedCount = ldifEntries.size() - successCount;

                log.info("CRL LDAP batch upload completed: {} success, {} skipped (duplicates)",
                    successCount, skippedCount);

            } catch (Exception e) {
                log.error("CRL LDAP batch upload failed: {}", e.getMessage(), e);
                for (CertificateRevocationList crl : crls) {
                    failedIds.add(crl.getId().getId());
                }
                errorMessages.add("CRL LDAP batch upload failed: " + e.getMessage());
                return LdapBatchUploadResult.partial(
                    0,
                    0,
                    crls.size(),
                    failedIds,
                    errorMessages
                );
            }
        }

        // 3. 결과 반환
        if (conversionFailedCount > 0) {
            return LdapBatchUploadResult.partial(
                successCount,
                skippedCount,
                conversionFailedCount,
                failedIds,
                errorMessages
            );
        }

        return LdapBatchUploadResult.success(successCount, skippedCount);
    }
}
