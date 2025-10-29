package com.smartcoreinc.localpkd.certificatevalidation.application.usecase;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.ValidateCertificatesCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.CertificatesValidatedResponse;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList;
import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CertificatesValidatedEvent;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
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
import java.util.List;

/**
 * ValidateCertificatesUseCase - 인증서 검증 Use Case
 *
 * <p><b>Application Service</b>: 파싱된 인증서들을 검증합니다.</p>
 *
 * <p><b>검증 프로세스</b>:</p>
 * <ol>
 *   <li>Command 검증</li>
 *   <li>파싱된 인증서/CRL 조회</li>
 *   <li>각 인증서 검증 (만료 여부, 유효성, Trust Chain 등)</li>
 *   <li>각 CRL 검증</li>
 *   <li>검증 결과 기록</li>
 *   <li>CertificatesValidatedEvent 발행</li>
 *   <li>Response 반환</li>
 * </ol>
 *
 * <p><b>Event-Driven Architecture</b>:</p>
 * <ul>
 *   <li>CertificatesValidatedEvent → ProgressService (SSE: VALIDATION_COMPLETED)</li>
 *   <li>CertificatesValidatedEvent → LdapIntegrationService (LDAP 업로드 트리거)</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * ValidateCertificatesCommand command = ValidateCertificatesCommand.builder()
 *     .uploadId(uploadId)
 *     .parsedFileId(parsedFileId)
 *     .certificateCount(800)
 *     .crlCount(50)
 *     .build();
 *
 * CertificatesValidatedResponse response = validateCertificatesUseCase.execute(command);
 *
 * if (response.success()) {
 *     log.info("Validation completed: {} valid, {} invalid",
 *         response.validCertificateCount(), response.invalidCertificateCount());
 * } else {
 *     log.error("Validation failed: {}", response.errorMessage());
 * }
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidateCertificatesUseCase {

    private final CertificateRepository certificateRepository;
    private final CertificateRevocationListRepository crlRepository;
    private final ProgressService progressService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 인증서 검증 실행
     *
     * @param command ValidateCertificatesCommand
     * @return CertificatesValidatedResponse
     */
    @Transactional
    public CertificatesValidatedResponse execute(ValidateCertificatesCommand command) {
        log.info("=== Certificate validation started ===");
        log.info("UploadId: {}, Certificates: {}, CRLs: {}",
            command.uploadId(), command.certificateCount(), command.crlCount());

        long startTime = System.currentTimeMillis();

        try {
            // 1. Command 검증
            command.validate();

            // 2. SSE 진행 상황 전송: VALIDATION_IN_PROGRESS (70%)
            progressService.sendProgress(
                ProcessingProgress.builder()
                    .uploadId(command.uploadId())
                    .stage(ProcessingStage.VALIDATION_IN_PROGRESS)
                    .percentage(70)
                    .message("인증서 검증 중")
                    .processedCount(0)
                    .totalCount(command.getTotalCount())
                    .build()
            );

            // 3. 파싱된 인증서 조회 및 검증
            log.info("Validating {} certificates...", command.certificateCount());
            List<Certificate> certificates = certificateRepository.findByType(
                com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType.DSC
            );

            int validCertificateCount = 0;
            int invalidCertificateCount = 0;

            for (Certificate cert : certificates) {
                try {
                    // TODO: Phase 4에서 실제 검증 로직 구현
                    // 현재는 상태 확인만 수행
                    if (cert.isValid()) {
                        validCertificateCount++;
                        log.debug("Certificate validated: {}", cert.getId().getId());
                    } else {
                        invalidCertificateCount++;
                        log.debug("Certificate invalid: {}", cert.getId().getId());
                    }

                } catch (Exception e) {
                    invalidCertificateCount++;
                    log.error("Certificate validation failed: {}", cert.getId().getId(), e);
                }

                // SSE 진행 상황 업데이트 (70-85% 범위)
                int processed = validCertificateCount + invalidCertificateCount;
                int percentage = 70 + (int) ((processed / (double) command.getTotalCount()) * 15);
                progressService.sendProgress(
                    ProcessingProgress.builder()
                        .uploadId(command.uploadId())
                        .stage(ProcessingStage.VALIDATION_IN_PROGRESS)
                        .percentage(Math.min(85, percentage))
                        .message(String.format("인증서 검증 중 (%d/%d)", processed, command.getTotalCount()))
                        .processedCount(processed)
                        .totalCount(command.getTotalCount())
                        .build()
                );
            }

            // 4. 파싱된 CRL 조회 및 검증
            log.info("Validating {} CRLs...", command.crlCount());
            List<CertificateRevocationList> crls = crlRepository.findAll();

            int validCrlCount = 0;
            int invalidCrlCount = 0;

            for (CertificateRevocationList crl : crls) {
                try {
                    // TODO: Phase 4에서 실제 CRL 검증 로직 구현
                    // 현재는 상태 확인만 수행
                    if (crl.isValid()) {
                        validCrlCount++;
                        log.debug("CRL validated: {}", crl.getId().getId());
                    } else {
                        invalidCrlCount++;
                        log.debug("CRL invalid: {}", crl.getId().getId());
                    }

                } catch (Exception e) {
                    invalidCrlCount++;
                    log.error("CRL validation failed: {}", crl.getId().getId(), e);
                }

                // SSE 진행 상황 업데이트 (80-85% 범위)
                int processed = validCertificateCount + invalidCertificateCount + validCrlCount + invalidCrlCount;
                int percentage = 80 + (int) ((processed / (double) command.getTotalCount()) * 5);
                progressService.sendProgress(
                    ProcessingProgress.builder()
                        .uploadId(command.uploadId())
                        .stage(ProcessingStage.VALIDATION_IN_PROGRESS)
                        .percentage(Math.min(85, percentage))
                        .message(String.format("CRL 검증 중 (%d/%d)", processed, command.getTotalCount()))
                        .processedCount(processed)
                        .totalCount(command.getTotalCount())
                        .build()
                );
            }

            // 5. CertificatesValidatedEvent 생성 및 발행
            CertificatesValidatedEvent event = new CertificatesValidatedEvent(
                command.uploadId(),
                validCertificateCount,
                invalidCertificateCount,
                validCrlCount,
                invalidCrlCount,
                LocalDateTime.now()
            );

            eventPublisher.publishEvent(event);
            log.info("CertificatesValidatedEvent published: uploadId={}", command.uploadId());

            // 6. SSE 진행 상황 전송: VALIDATION_COMPLETED (85%)
            progressService.sendProgress(
                ProcessingProgress.builder()
                    .uploadId(command.uploadId())
                    .stage(ProcessingStage.VALIDATION_COMPLETED)
                    .percentage(85)
                    .message(String.format("인증서 검증 완료: %d 유효, %d 실패",
                        validCertificateCount + validCrlCount,
                        invalidCertificateCount + invalidCrlCount))
                    .processedCount(validCertificateCount + invalidCertificateCount + validCrlCount + invalidCrlCount)
                    .totalCount(command.getTotalCount())
                    .build()
            );

            // 7. Response 반환
            long durationMillis = System.currentTimeMillis() - startTime;
            return CertificatesValidatedResponse.success(
                command.uploadId(),
                validCertificateCount,
                invalidCertificateCount,
                validCrlCount,
                invalidCrlCount,
                LocalDateTime.now(),
                durationMillis
            );

        } catch (DomainException e) {
            log.error("Domain error during certificate validation: {}", e.getMessage());
            progressService.sendProgress(
                ProcessingProgress.failed(
                    command.uploadId(),
                    ProcessingStage.FAILED,
                    "인증서 검증 중 도메인 오류: " + e.getMessage()
                )
            );
            return CertificatesValidatedResponse.failure(command.uploadId(), e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected error during certificate validation", e);
            progressService.sendProgress(
                ProcessingProgress.failed(
                    command.uploadId(),
                    ProcessingStage.FAILED,
                    "인증서 검증 중 오류가 발생했습니다: " + e.getMessage()
                )
            );
            return CertificatesValidatedResponse.failure(
                command.uploadId(),
                "인증서 검증 중 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }
}
