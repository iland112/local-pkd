package com.smartcoreinc.localpkd.ldapintegration.application.usecase;

import com.smartcoreinc.localpkd.ldapintegration.application.command.UploadToLdapCommand;
import com.smartcoreinc.localpkd.ldapintegration.application.response.UploadToLdapResponse;
import com.smartcoreinc.localpkd.ldapintegration.domain.event.LdapUploadCompletedEvent;
import com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter.UnboundIdLdapAdapter;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProcessingStage;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * UploadToLdapUseCase - LDAP 서버 업로드 Use Case
 *
 * <p><b>Application Service</b>: 검증된 인증서 및 CRL을 LDAP 서버에 업로드합니다.</p>
 * <p><b>Note</b>: ALL certificates (valid AND invalid) are uploaded to LDAP with their validation status.</p>
 *
 * <p><b>업로드 프로세스</b>:</p>
 * <ol>
 *   <li>Command 검증</li>
 *   <li>검증된 인증서/CRL 조회</li>
 *   <li>배치 단위로 LDAP에 업로드</li>
 *   <li>업로드 결과 기록</li>
 *   <li>LdapUploadCompletedEvent 발행</li>
 *   <li>Response 반환</li>
 * </ol>
 *
 * <p><b>Event-Driven Architecture</b>:</p>
 * <ul>
 *   <li>LdapUploadCompletedEvent → ProgressService (SSE: LDAP_SAVING_COMPLETED)</li>
 *   <li>LdapUploadCompletedEvent → LdapUploadEventHandler (최종 완료 처리)</li>
 * </ul>
 *
 * <p><b>배치 처리</b>:</p>
 * <ul>
 *   <li>기본 배치 크기: 100개</li>
 *   <li>네트워크 부하 분산</li>
 *   <li>대량 데이터 처리 효율성</li>
 *   <li>부분 실패 처리 (일부 실패해도 계속 진행)</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * UploadToLdapCommand command = UploadToLdapCommand.create(
 *     uploadId,
 *     795,  // validCertificateCount
 *     48    // validCrlCount
 * );
 *
 * UploadToLdapResponse response = uploadToLdapUseCase.execute(command);
 *
 * if (response.success()) {
 *     log.info("LDAP upload completed: {} uploaded, {} failed",
 *         response.getTotalUploaded(), response.getTotalFailed());
 * } else {
 *     log.error("LDAP upload failed: {}", response.errorMessage());
 * }
 * </pre>
 *
 */
@Slf4j
@Service("ldapintegrationUploadToLdapUseCase")
@RequiredArgsConstructor
public class UploadToLdapUseCase {

    private final ProgressService progressService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository certificateRepository;
    private final com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository crlRepository;
    private final com.smartcoreinc.localpkd.fileparsing.domain.repository.MasterListRepository masterListRepository;
    private final com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter.UnboundIdLdapAdapter ldapAdapter;
    private final com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter.LdifConverter ldifConverter;

    /**
     * LDAP 서버 업로드 실행
     *
     * @param command UploadToLdapCommand
     * @return UploadToLdapResponse
     */
    @Transactional
    public UploadToLdapResponse execute(UploadToLdapCommand command) {
        log.info("=== LDAP upload started ===");
        log.info("UploadId: {}, Certificates: {}, CRLs: {}, MasterLists: {}, BatchSize: {}",
            command.uploadId(), command.validCertificateCount(), command.validCrlCount(),
            command.masterListCount(), command.batchSize());

        long startTime = System.currentTimeMillis();

        try {
            // 1. Command 검증
            command.validate();

            // 2. SSE 진행 상황 전송: LDAP_SAVING_STARTED (90%)
            progressService.sendProgress(
                ProcessingProgress.builder()
                    .uploadId(command.uploadId())
                    .stage(ProcessingStage.LDAP_SAVING_STARTED)
                    .percentage(90)
                    .message("LDAP 서버 저장 시작")
                    .build()
            );

            // 3. Upload all certificates (including CSCAs from Master List)
            java.util.List<com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate> certificates =
                    certificateRepository.findByUploadId(command.uploadId());

            // Count CSCAs from Master List for logging
            long masterListCscaCount = certificates.stream()
                    .filter(cert -> cert.isFromMasterList())
                    .filter(cert -> cert.getCertificateType() == com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType.CSCA)
                    .count();

            log.info("Uploading {} certificates to LDAP ({} CSCAs from Master List, {} from LDIF) with batch size {}...",
                    certificates.size(), masterListCscaCount, certificates.size() - masterListCscaCount, command.batchSize());
            int uploadedCertificateCount = 0;
            int skippedCertificateCount = 0;
            int failedCertificateCount = 0;

            // ✅ 인증서 LDAP 배치 업로드 (RFC 5280 준수 - DN 기반 비교 및 업데이트)
            // including CSCAs from Master List
            List<String> certBatch = new ArrayList<>();
            List<com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate> certBatchObjects = new ArrayList<>();
            int updatedCertificateCount = 0;

            for (int i = 0; i < certificates.size(); i++) {
                com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate cert = certificates.get(i);
                try {
                    // Convert to LDIF format (CSCAs will use o=csca)
                    String ldifEntry = ldifConverter.certificateToLdif(cert);
                    certBatch.add(ldifEntry);
                    certBatchObjects.add(cert);

                    // ✅ 배치 크기에 도달하거나 마지막 항목이면 RFC 5280 비교 후 업로드
                    if (certBatch.size() >= command.batchSize() || (i + 1) == certificates.size()) {
                        log.info("Uploading certificate batch: {} entries (RFC 5280 comparison)", certBatch.size());
                        UnboundIdLdapAdapter.CertBatchResult batchResult =
                            ldapAdapter.addOrUpdateCertificateEntriesBatch(certBatch);
                        uploadedCertificateCount += batchResult.added();
                        updatedCertificateCount += batchResult.updated();
                        skippedCertificateCount += batchResult.skipped();
                        log.info("Certificate batch uploaded: {} added, {} updated, {} skipped",
                            batchResult.added(), batchResult.updated(), batchResult.skipped());

                        // ✅ Update uploaded_to_ldap flag for successfully uploaded certificates
                        // Note: Even skipped entries (duplicates) are considered uploaded
                        for (com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate batchCert : certBatchObjects) {
                            batchCert.markAsUploadedToLdap();
                        }
                        log.debug("Marked {} certificates as uploaded to LDAP", certBatchObjects.size());

                        certBatch.clear();
                        certBatchObjects.clear();
                    }

                    // Send progress every 100 items or at the end
                    if ((i + 1) % 100 == 0 || (i + 1) == certificates.size()) {
                        int percentage = 90 + ((i + 1) * 5 / certificates.size());  // 90-95%
                        progressService.sendProgress(
                                ProcessingProgress.builder()
                                        .uploadId(command.uploadId())
                                        .stage(ProcessingStage.LDAP_SAVING_IN_PROGRESS)
                                        .percentage(Math.min(95, percentage))
                                        .message(String.format("인증서 LDAP 저장 중 (%d/%d)", i + 1, certificates.size()))
                                        .processedCount(i + 1)
                                        .totalCount(certificates.size())
                                        .build()
                        );
                    }

                } catch (Exception e) {
                    failedCertificateCount++;
                    log.error("Failed to convert certificate to LDIF: id={}", cert.getId().getId(), e);
                }
            }

            // 4. 모든 CRL 조회
            java.util.List<com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList> crls =
                    crlRepository.findByUploadId(command.uploadId());

            log.info("Uploading {} CRLs to LDAP with batch size {}...", crls.size(), command.batchSize());
            int uploadedCrlCount = 0;
            int skippedCrlCount = 0;
            int failedCrlCount = 0;

            // ✅ CRL LDAP 배치 업로드
            List<String> crlBatch = new ArrayList<>();
            for (int i = 0; i < crls.size(); i++) {
                com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList crl = crls.get(i);
                try {
                    // Convert to LDIF format
                    String ldifEntry = ldifConverter.crlToLdif(crl);
                    crlBatch.add(ldifEntry);

                    // ✅ 배치 크기에 도달하거나 마지막 항목이면 RFC 5280 CRL Number 비교 후 업로드
                    if (crlBatch.size() >= command.batchSize() || (i + 1) == crls.size()) {
                        log.info("Uploading CRL batch: {} entries (RFC 5280 CRL Number comparison)", crlBatch.size());
                        UnboundIdLdapAdapter.CrlBatchResult batchResult = ldapAdapter.addOrUpdateCrlEntriesBatch(crlBatch);
                        uploadedCrlCount += batchResult.totalSuccess();
                        skippedCrlCount += batchResult.skipped();
                        log.info("CRL batch uploaded: {} added, {} updated, {} skipped (already latest)",
                            batchResult.added(), batchResult.updated(), batchResult.skipped());
                        crlBatch.clear();
                    }

                    // Send progress every 10 items or at the end
                    if ((i + 1) % 10 == 0 || (i + 1) == crls.size()) {
                        int percentage = 95 + ((i + 1) * 4 / Math.max(crls.size(), 1));  // 95-99%
                        progressService.sendProgress(
                                ProcessingProgress.builder()
                                        .uploadId(command.uploadId())
                                        .stage(ProcessingStage.LDAP_SAVING_IN_PROGRESS)
                                        .percentage(Math.min(99, percentage))
                                        .message(String.format("CRL LDAP 저장 중 (%d/%d)", i + 1, crls.size()))
                                        .processedCount(i + 1)
                                        .totalCount(crls.size())
                                        .build()
                        );
                    }

                } catch (Exception e) {
                    failedCrlCount++;
                    log.error("Failed to convert CRL to LDIF: id={}", crl.getId().getId(), e);
                }
            }

            // 5. Master List CMS binary upload to o=ml,c={COUNTRY}
            // LDIF files contain country-specific Master Lists that should be uploaded to LDAP
            java.util.List<com.smartcoreinc.localpkd.fileparsing.domain.model.MasterList> masterLists =
                    masterListRepository.findAllByUploadId(
                        new com.smartcoreinc.localpkd.fileupload.domain.model.UploadId(command.uploadId()));

            log.info("Uploading {} Master Lists to LDAP...", masterLists.size());
            int uploadedMasterListCount = 0;
            int skippedMasterListCount = 0;
            int failedMasterListCount = 0;

            int updatedMasterListCount = 0;

            for (int i = 0; i < masterLists.size(); i++) {
                com.smartcoreinc.localpkd.fileparsing.domain.model.MasterList ml = masterLists.get(i);
                try {
                    // Get country code and signer DN for this Master List
                    String countryCode = ml.getCountryCode() != null ? ml.getCountryCode().getValue() : "XX";
                    String cscaDn = ml.getSignerDn() != null ? ml.getSignerDn() : "CN=Unknown";
                    String serialNumber = "1";  // Default serial for Master List entry

                    // Convert Master List to LDIF format for o=ml,c={COUNTRY}
                    String ldifEntry = ldifConverter.masterListForCountryToLdif(ml, countryCode, cscaDn, serialNumber);

                    // ✅ Master List 추가/업데이트 (바이너리 비교)
                    UnboundIdLdapAdapter.MasterListAddResult result =
                        ldapAdapter.addOrUpdateMasterListEntry(ldifEntry);

                    switch (result) {
                        case ADDED -> {
                            uploadedMasterListCount++;
                            log.debug("Master List added: country={}, mlId={}", countryCode, ml.getId().getId());
                        }
                        case UPDATED -> {
                            updatedMasterListCount++;
                            log.debug("Master List updated: country={}, mlId={}", countryCode, ml.getId().getId());
                        }
                        case SKIPPED -> {
                            skippedMasterListCount++;
                            log.debug("Master List skipped (identical): country={}, mlId={}", countryCode, ml.getId().getId());
                        }
                        case ERROR -> {
                            failedMasterListCount++;
                            log.warn("Master List upload error: country={}, mlId={}", countryCode, ml.getId().getId());
                        }
                    }

                    // Send progress every 10 items or at the end
                    if ((i + 1) % 10 == 0 || (i + 1) == masterLists.size()) {
                        int percentage = 95 + ((i + 1) * 4 / Math.max(masterLists.size(), 1));
                        progressService.sendProgress(
                                ProcessingProgress.builder()
                                        .uploadId(command.uploadId())
                                        .stage(ProcessingStage.LDAP_SAVING_IN_PROGRESS)
                                        .percentage(Math.min(99, percentage))
                                        .message(String.format("Master List LDAP 저장 중 (%d/%d)", i + 1, masterLists.size()))
                                        .processedCount(i + 1)
                                        .totalCount(masterLists.size())
                                        .build()
                        );
                    }

                } catch (Exception e) {
                    failedMasterListCount++;
                    log.error("Failed to upload Master List to LDAP: mlId={}", ml.getId().getId(), e);
                }
            }

            log.info("Master List LDAP upload completed: {} added, {} updated, {} skipped, {} failed",
                    uploadedMasterListCount, updatedMasterListCount, skippedMasterListCount, failedMasterListCount);

            // 6. LDAP 업로드 결과 통계 계산
            // Calculate totals first (업데이트도 성공으로 카운트)
            int totalUpdated = updatedCertificateCount + updatedMasterListCount;
            int totalUploaded = uploadedCertificateCount + uploadedCrlCount + uploadedMasterListCount + totalUpdated;
            int totalSkipped = skippedCertificateCount + skippedCrlCount + skippedMasterListCount;
            int totalFailed = failedCertificateCount + failedCrlCount + failedMasterListCount;

            // ✅ 실제 LDAP에 업로드된 인증서 타입별 통계 (업로드 과정에서 추적)
            // 주의: uploadedCertificateCount는 실제 LDAP에 성공적으로 추가된 수 (중복 제외)
            // DB 전체 수가 아닌 실제 업로드 수를 표시
            log.info("LDAP upload completed: Certificates: {} (new), CRLs: {} (new), MasterLists: {} (new)",
                    uploadedCertificateCount, uploadedCrlCount, uploadedMasterListCount);
            log.info("Skipped (duplicates): {} certificates, {} CRLs, {} Master Lists", 
                    skippedCertificateCount, skippedCrlCount, skippedMasterListCount);
            log.info("Failed: {} certificates, {} CRLs, {} Master Lists", 
                    failedCertificateCount, failedCrlCount, failedMasterListCount);

            // DB에 저장된 전체 인증서 수 (참고용 로그)
            java.util.List<com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate> allCertificates =
                    certificateRepository.findByUploadId(command.uploadId());

            long cscaTotalCount = allCertificates.stream()
                    .filter(cert -> cert.getCertificateType() == com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType.CSCA)
                    .count();
            long dscTotalCount = allCertificates.stream()
                    .filter(cert -> cert.getCertificateType() == com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType.DSC)
                    .count();
            long dscNcTotalCount = allCertificates.stream()
                    .filter(cert -> cert.getCertificateType() == com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType.DSC_NC)
                    .count();

            log.info("DB total certificates (this upload): CSCA: {} (from MasterList: {}), DSC: {}, DSC_NC: {}",
                    cscaTotalCount, masterListCscaCount, dscTotalCount, dscNcTotalCount);

            // 통계 메시지 포맷팅 (신규/업데이트/동일하여 스킵)
            StringBuilder detailsMsg = new StringBuilder();

            // 인증서 레이블 결정: ML 파일(CSCA만 있음)이면 "CSCA", DSC/CRL LDIF 파일이면 "DSC"
            // DSC 또는 DSC_NC가 하나라도 있으면 DSC/CRL LDIF 파일로 간주
            String certLabel = (dscTotalCount > 0 || dscNcTotalCount > 0) ? "DSC" : "CSCA";

            // 인증서 통계 (CSCA, DSC, DSC_NC 포함)
            int certTotal = uploadedCertificateCount + updatedCertificateCount + skippedCertificateCount;
            if (certTotal > 0) {
                StringBuilder certParts = new StringBuilder();
                if (uploadedCertificateCount > 0) certParts.append(String.format("신규 %d", uploadedCertificateCount));
                if (updatedCertificateCount > 0) {
                    if (certParts.length() > 0) certParts.append(", ");
                    certParts.append(String.format("업데이트 %d", updatedCertificateCount));
                }
                if (skippedCertificateCount > 0) {
                    if (certParts.length() > 0) certParts.append(", ");
                    certParts.append(String.format("동일하여 스킵 %d", skippedCertificateCount));
                }
                detailsMsg.append(String.format("%s: %d개 (%s)", certLabel, certTotal, certParts));
            }

            // CRL 통계
            int crlTotal = uploadedCrlCount + skippedCrlCount;
            if (crlTotal > 0) {
                if (detailsMsg.length() > 0) detailsMsg.append(", ");
                StringBuilder crlParts = new StringBuilder();
                if (uploadedCrlCount > 0) crlParts.append(String.format("신규 %d", uploadedCrlCount));
                if (skippedCrlCount > 0) {
                    if (crlParts.length() > 0) crlParts.append(", ");
                    crlParts.append(String.format("동일하여 스킵 %d", skippedCrlCount));
                }
                detailsMsg.append(String.format("CRL: %d개 (%s)", crlTotal, crlParts));
            }

            // MasterList 통계
            int mlTotal = uploadedMasterListCount + updatedMasterListCount + skippedMasterListCount;
            if (mlTotal > 0) {
                if (detailsMsg.length() > 0) detailsMsg.append(", ");
                StringBuilder mlParts = new StringBuilder();
                if (uploadedMasterListCount > 0) mlParts.append(String.format("신규 %d", uploadedMasterListCount));
                if (updatedMasterListCount > 0) {
                    if (mlParts.length() > 0) mlParts.append(", ");
                    mlParts.append(String.format("업데이트 %d", updatedMasterListCount));
                }
                if (skippedMasterListCount > 0) {
                    if (mlParts.length() > 0) mlParts.append(", ");
                    mlParts.append(String.format("동일하여 스킵 %d", skippedMasterListCount));
                }
                detailsMsg.append(String.format("MasterList: %d개 (%s)", mlTotal, mlParts));
            }

            // 실패 통계
            if (totalFailed > 0) {
                if (detailsMsg.length() > 0) detailsMsg.append(", ");
                detailsMsg.append(String.format("실패: %d개", totalFailed));
            }

            // 7. LdapUploadCompletedEvent 생성 및 발행

            LdapUploadCompletedEvent event = new LdapUploadCompletedEvent(
                command.uploadId(),
                uploadedCertificateCount,
                uploadedCrlCount,
                uploadedMasterListCount,
                totalFailed,
                LocalDateTime.now()
            );

            eventPublisher.publishEvent(event);
            log.info("LdapUploadCompletedEvent published: uploadId={}", command.uploadId());

            // 8. SSE 진행 상황 전송: LDAP_SAVING_COMPLETED (100%)
            // 메시지: 신규/업데이트/스킵 정보를 명확하게 표시
            int totalNew = uploadedCertificateCount + uploadedCrlCount + uploadedMasterListCount;
            StringBuilder summaryMsg = new StringBuilder("LDAP 저장 완료");
            if (totalNew > 0 || totalUpdated > 0 || totalSkipped > 0) {
                summaryMsg.append(" (");
                boolean first = true;
                if (totalNew > 0) {
                    summaryMsg.append(String.format("신규 %d개", totalNew));
                    first = false;
                }
                if (totalUpdated > 0) {
                    if (!first) summaryMsg.append(", ");
                    summaryMsg.append(String.format("업데이트 %d개", totalUpdated));
                    first = false;
                }
                if (totalSkipped > 0) {
                    if (!first) summaryMsg.append(", ");
                    summaryMsg.append(String.format("동일하여 스킵 %d개", totalSkipped));
                }
                summaryMsg.append(")");
            }

            progressService.sendProgress(
                ProcessingProgress.builder()
                    .uploadId(command.uploadId())
                    .stage(ProcessingStage.LDAP_SAVING_COMPLETED)
                    .percentage(100)
                    .message(summaryMsg.toString())
                    .details(detailsMsg.toString())
                    .processedCount(totalUploaded + totalFailed)
                    .totalCount(command.getTotalCount())
                    .build()
            );

            // 9. Response 반환
            long durationMillis = System.currentTimeMillis() - startTime;
            return UploadToLdapResponse.success(
                command.uploadId(),
                uploadedCertificateCount,
                uploadedCrlCount,
                uploadedMasterListCount,
                failedCertificateCount,
                failedCrlCount,
                failedMasterListCount,
                LocalDateTime.now(),
                durationMillis
            );

        } catch (DomainException e) {
            log.error("Domain error during LDAP upload: {}", e.getMessage());
            progressService.sendProgress(
                ProcessingProgress.failed(
                    command.uploadId(),
                    ProcessingStage.FAILED,
                    "LDAP 업로드 중 도메인 오류: " + e.getMessage()
                )
            );
            return UploadToLdapResponse.failure(command.uploadId(), e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected error during LDAP upload", e);
            progressService.sendProgress(
                ProcessingProgress.failed(
                    command.uploadId(),
                    ProcessingStage.FAILED,
                    "LDAP 업로드 중 오류가 발생했습니다: " + e.getMessage()
                )
            );
            return UploadToLdapResponse.failure(
                command.uploadId(),
                "LDAP 업로드 중 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }

}
