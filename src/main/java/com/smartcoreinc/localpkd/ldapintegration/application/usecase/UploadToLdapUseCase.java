package com.smartcoreinc.localpkd.ldapintegration.application.usecase;

import com.smartcoreinc.localpkd.ldapintegration.application.command.UploadToLdapCommand;
import com.smartcoreinc.localpkd.ldapintegration.application.response.UploadToLdapResponse;
import com.smartcoreinc.localpkd.ldapintegration.domain.event.LdapUploadCompletedEvent;
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

            // ✅ 인증서 LDAP 배치 업로드 (including CSCAs from Master List)
            List<String> certBatch = new ArrayList<>();
            List<com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate> certBatchObjects = new ArrayList<>();
            for (int i = 0; i < certificates.size(); i++) {
                com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate cert = certificates.get(i);
                try {
                    // Convert to LDIF format (CSCAs will use o=csca)
                    String ldifEntry = ldifConverter.certificateToLdif(cert);
                    certBatch.add(ldifEntry);
                    certBatchObjects.add(cert);

                    // ✅ 배치 크기에 도달하거나 마지막 항목이면 배치 업로드
                    if (certBatch.size() >= command.batchSize() || (i + 1) == certificates.size()) {
                        log.info("Uploading certificate batch: {} entries", certBatch.size());
                        int successCount = ldapAdapter.addLdifEntriesBatch(certBatch);
                        uploadedCertificateCount += successCount;
                        skippedCertificateCount += (certBatch.size() - successCount);
                        log.info("Certificate batch uploaded: {} success, {} skipped",
                            successCount, certBatch.size() - successCount);

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

                    // ✅ 배치 크기에 도달하거나 마지막 항목이면 배치 업로드
                    if (crlBatch.size() >= command.batchSize() || (i + 1) == crls.size()) {
                        log.info("Uploading CRL batch: {} entries", crlBatch.size());
                        int successCount = ldapAdapter.addLdifEntriesBatch(crlBatch);
                        uploadedCrlCount += successCount;
                        skippedCrlCount += (crlBatch.size() - successCount);
                        log.info("CRL batch uploaded: {} success, {} skipped",
                            successCount, crlBatch.size() - successCount);
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

            for (int i = 0; i < masterLists.size(); i++) {
                com.smartcoreinc.localpkd.fileparsing.domain.model.MasterList ml = masterLists.get(i);
                try {
                    // Get country code and signer DN for this Master List
                    String countryCode = ml.getCountryCode() != null ? ml.getCountryCode().getValue() : "XX";
                    String cscaDn = ml.getSignerDn() != null ? ml.getSignerDn() : "CN=Unknown";
                    String serialNumber = "1";  // Default serial for Master List entry

                    // Convert Master List to LDIF format for o=ml,c={COUNTRY}
                    String ldifEntry = ldifConverter.masterListForCountryToLdif(ml, countryCode, cscaDn, serialNumber);

                    // Upload single Master List entry
                    List<String> mlBatch = new ArrayList<>();
                    mlBatch.add(ldifEntry);
                    int successCount = ldapAdapter.addLdifEntriesBatch(mlBatch);

                    if (successCount > 0) {
                        uploadedMasterListCount++;
                        log.debug("Master List uploaded: country={}, mlId={}", countryCode, ml.getId().getId());
                    } else {
                        skippedMasterListCount++;
                        log.debug("Master List skipped (duplicate): country={}, mlId={}", countryCode, ml.getId().getId());
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

            log.info("Master List LDAP upload completed: {} uploaded, {} skipped, {} failed",
                    uploadedMasterListCount, skippedMasterListCount, failedMasterListCount);

            // 6. LDAP 업로드 결과 통계 계산
            // Calculate totals first
            int totalUploaded = uploadedCertificateCount + uploadedCrlCount + uploadedMasterListCount;
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

            // 통계 메시지 포맷팅 (실제 LDAP 업로드 수 + 중복 스킵 수)
            StringBuilder detailsMsg = new StringBuilder();
            detailsMsg.append(String.format("인증서: %d개 (신규 %d, 중복 %d)", 
                    uploadedCertificateCount + skippedCertificateCount, 
                    uploadedCertificateCount, 
                    skippedCertificateCount));
            if (uploadedCrlCount > 0 || skippedCrlCount > 0) {
                detailsMsg.append(String.format(", CRL: %d개 (신규 %d, 중복 %d)", 
                        uploadedCrlCount + skippedCrlCount, 
                        uploadedCrlCount, 
                        skippedCrlCount));
            }
            if (uploadedMasterListCount > 0 || skippedMasterListCount > 0) {
                detailsMsg.append(String.format(", MasterList: %d개 (신규 %d, 중복 %d)", 
                        uploadedMasterListCount + skippedMasterListCount, 
                        uploadedMasterListCount, 
                        skippedMasterListCount));
            }
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
            progressService.sendProgress(
                ProcessingProgress.builder()
                    .uploadId(command.uploadId())
                    .stage(ProcessingStage.LDAP_SAVING_COMPLETED)
                    .percentage(100)
                    .message(String.format("LDAP 저장 완료 (총 %d개)", totalUploaded))
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
