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
        log.info("UploadId: {}, Certificates: {}, CRLs: {}, BatchSize: {}",
            command.uploadId(), command.validCertificateCount(), command.validCrlCount(), command.batchSize());

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

            log.info("Uploading {} certificates to LDAP ({} CSCAs from Master List, {} from LDIF)...",
                    certificates.size(), masterListCscaCount, certificates.size() - masterListCscaCount);
            int uploadedCertificateCount = 0;
            int failedCertificateCount = 0;

            // 인증서 LDAP 업로드 (including CSCAs from Master List)
            for (int i = 0; i < certificates.size(); i++) {
                com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate cert = certificates.get(i);
                try {
                    // Convert to LDIF format (CSCAs will use o=csca)
                    String ldifEntry = ldifConverter.certificateToLdif(cert);

                    // Upload to LDAP
                    boolean success = ldapAdapter.addLdifEntry(ldifEntry);

                    if (success) {
                        uploadedCertificateCount++;
                        log.debug("Certificate uploaded to LDAP: id={}, type={}, source={}, country={}",
                                cert.getId().getId(), cert.getCertificateType(),
                                cert.isFromMasterList() ? "MasterList" : "LDIF",
                                cert.getSubjectInfo().getCountryCode());
                    } else {
                        failedCertificateCount++;
                        log.warn("Certificate upload skipped (duplicate): id={}", cert.getId().getId());
                    }

                    // Send progress every 10 items or at the end
                    if ((i + 1) % 10 == 0 || (i + 1) == certificates.size()) {
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
                    log.error("Failed to upload certificate to LDAP: id={}", cert.getId().getId(), e);
                }
            }

            // 4. 모든 CRL 조회
            java.util.List<com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList> crls =
                    crlRepository.findByUploadId(command.uploadId());

            log.info("Uploading {} CRLs to LDAP...", crls.size());
            int uploadedCrlCount = 0;
            int failedCrlCount = 0;

            // CRL LDAP 업로드
            for (int i = 0; i < crls.size(); i++) {
                com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList crl = crls.get(i);
                try {
                    // Convert to LDIF format
                    String ldifEntry = ldifConverter.crlToLdif(crl);

                    // Upload to LDAP
                    boolean success = ldapAdapter.addLdifEntry(ldifEntry);

                    if (success) {
                        uploadedCrlCount++;
                        log.debug("CRL uploaded to LDAP: id={}, issuer={}, country={}",
                                crl.getId().getId(), crl.getIssuerName().getValue(), crl.getCountryCode().getValue());
                    } else {
                        failedCrlCount++;
                        log.warn("CRL upload skipped (duplicate): id={}", crl.getId().getId());
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
                    log.error("Failed to upload CRL to LDAP: id={}", crl.getId().getId(), e);
                }
            }

            // 5. Upload Master Lists from LDIF file
            java.util.List<com.smartcoreinc.localpkd.fileparsing.domain.model.MasterList> masterLists =
                    masterListRepository.findAllByUploadId(
                        new com.smartcoreinc.localpkd.fileupload.domain.model.UploadId(command.uploadId())
                    );

            log.info("Uploading {} Master Lists to LDAP...", masterLists.size());
            int uploadedMasterListCount = 0;
            int failedMasterListCount = 0;

            // Master List LDAP 업로드
            for (int i = 0; i < masterLists.size(); i++) {
                com.smartcoreinc.localpkd.fileparsing.domain.model.MasterList masterList = masterLists.get(i);
                try {
                    // Convert to LDIF format
                    String ldifEntry = ldifConverter.masterListToLdif(masterList);

                    // Upload to LDAP
                    boolean success = ldapAdapter.addLdifEntry(ldifEntry);

                    if (success) {
                        uploadedMasterListCount++;
                        log.debug("Master List uploaded to LDAP: id={}, country={}, cscaCount={}",
                                masterList.getId().getId(),
                                masterList.getCountryCode().getValue(),
                                masterList.getCscaCount());
                    } else {
                        failedMasterListCount++;
                        log.warn("Master List upload skipped (duplicate): id={}", masterList.getId().getId());
                    }

                    // Send progress (99% - before completion)
                    if ((i + 1) % 5 == 0 || (i + 1) == masterLists.size()) {
                        progressService.sendProgress(
                                ProcessingProgress.builder()
                                        .uploadId(command.uploadId())
                                        .stage(ProcessingStage.LDAP_SAVING_IN_PROGRESS)
                                        .percentage(99)
                                        .message(String.format("Master List LDAP 저장 중 (%d/%d)", i + 1, masterLists.size()))
                                        .processedCount(i + 1)
                                        .totalCount(masterLists.size())
                                        .build()
                        );
                    }

                } catch (Exception e) {
                    failedMasterListCount++;
                    log.error("Failed to upload Master List to LDAP: id={}", masterList.getId().getId(), e);
                }
            }

            // 6. LdapUploadCompletedEvent 생성 및 발행
            int totalUploaded = uploadedCertificateCount + uploadedCrlCount + uploadedMasterListCount;
            int totalFailed = failedCertificateCount + failedCrlCount + failedMasterListCount;

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

            // 6. SSE 진행 상황 전송: LDAP_SAVING_COMPLETED (100%)
            progressService.sendProgress(
                ProcessingProgress.builder()
                    .uploadId(command.uploadId())
                    .stage(ProcessingStage.LDAP_SAVING_COMPLETED)
                    .percentage(100)
                    .message(String.format("LDAP 저장 완료: %d 인증서 (%d CSCAs from MasterList), %d CRLs, %d Master Lists (%d 성공, %d 실패)",
                        uploadedCertificateCount,
                        masterListCscaCount,
                        uploadedCrlCount,
                        uploadedMasterListCount,
                        totalUploaded,
                        totalFailed))
                    .processedCount(totalUploaded + totalFailed)
                    .totalCount(command.getTotalCount())
                    .build()
            );

            // 8. Response 반환
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
