package com.smartcoreinc.localpkd.certificatevalidation.application.usecase;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.ValidateCertificatesCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.CertificatesValidatedResponse;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.*;
import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CertificatesValidatedEvent;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import com.smartcoreinc.localpkd.fileparsing.domain.model.CertificateData;
import com.smartcoreinc.localpkd.fileparsing.domain.model.CrlData;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.domain.repository.ParsedFileRepository;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
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
import java.util.Optional;

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
    private final ParsedFileRepository parsedFileRepository;
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

            // 2. ParsedFile 조회 (UploadId로)
            UploadId uploadId = new UploadId(command.uploadId());
            Optional<ParsedFile> parsedFileOpt = parsedFileRepository.findByUploadId(uploadId);

            if (parsedFileOpt.isEmpty()) {
                String errorMsg = "파싱된 파일을 찾을 수 없습니다. UploadId: " + command.uploadId();
                log.error(errorMsg);
                throw new DomainException("PARSED_FILE_NOT_FOUND", errorMsg);
            }

            ParsedFile parsedFile = parsedFileOpt.get();
            log.info("Found parsed file: id={}, certificates={}, crls={}",
                parsedFile.getId().getId(), command.certificateCount(), command.crlCount());

            // 3. SSE 진행 상황 전송: VALIDATION_IN_PROGRESS (70%)
            progressService.sendProgress(
                ProcessingProgress.builder()
                    .uploadId(command.uploadId())
                    .stage(ProcessingStage.VALIDATION_IN_PROGRESS)
                    .percentage(70)
                    .message("인증서 변환 및 검증 중")
                    .processedCount(0)
                    .totalCount(command.getTotalCount())
                    .build()
            );

            // 4. ParsedFile의 CertificateData 목록에서 검증 결과 추출
            log.info("Extracting validation results from {} parsed certificates...", command.certificateCount());
            List<CertificateData> certificateDataList = parsedFile.getCertificates();

            int validCertificateCount = 0;
            int invalidCertificateCount = 0;

            for (int i = 0; i < certificateDataList.size(); i++) {
                CertificateData certData = certificateDataList.get(i);

                // ParsedFile에 이미 저장된 isValid() 플래그 사용
                if (certData.isValid()) {
                    validCertificateCount++;
                    log.debug("Valid certificate extracted: {}", certData.getSubjectDN());
                } else {
                    invalidCertificateCount++;
                    log.debug("Invalid certificate extracted: {}", certData.getSubjectDN());
                }

                // SSE 진행 상황 업데이트 (70-85% 범위)
                int processed = validCertificateCount + invalidCertificateCount;
                int percentage = 70 + (int) ((processed / (double) command.getTotalCount()) * 15);
                progressService.sendProgress(
                    ProcessingProgress.builder()
                        .uploadId(command.uploadId())
                        .stage(ProcessingStage.VALIDATION_IN_PROGRESS)
                        .percentage(Math.min(85, percentage))
                        .message(String.format("인증서 검증 완료 (%d/%d)", processed, command.getTotalCount()))
                        .processedCount(processed)
                        .totalCount(command.getTotalCount())
                        .build()
                );
            }

            // 5. ParsedFile의 CrlData 목록에서 검증 결과 추출
            log.info("Extracting validation results from {} parsed CRLs...", command.crlCount());
            List<CrlData> crlDataList = parsedFile.getCrls();

            int validCrlCount = 0;
            int invalidCrlCount = 0;

            for (CrlData crlData : crlDataList) {
                // ParsedFile에 이미 저장된 isValid() 플래그 사용
                if (crlData.isValid()) {
                    validCrlCount++;
                    log.debug("Valid CRL extracted: {}", crlData.getIssuerDN());
                } else {
                    invalidCrlCount++;
                    log.debug("Invalid CRL extracted: {}", crlData.getIssuerDN());
                }

                // SSE 진행 상황 업데이트 (80-85% 범위)
                int processed = validCertificateCount + invalidCertificateCount + validCrlCount + invalidCrlCount;
                int percentage = 80 + (int) ((processed / (double) command.getTotalCount()) * 5);
                progressService.sendProgress(
                    ProcessingProgress.builder()
                        .uploadId(command.uploadId())
                        .stage(ProcessingStage.VALIDATION_IN_PROGRESS)
                        .percentage(Math.min(85, percentage))
                        .message(String.format("CRL 검증 완료 (%d/%d)", processed, command.getTotalCount()))
                        .processedCount(processed)
                        .totalCount(command.getTotalCount())
                        .build()
                );
            }

            // 6. CertificatesValidatedEvent 생성 및 발행
            int totalValid = validCertificateCount + validCrlCount;
            int totalInvalid = invalidCertificateCount + invalidCrlCount;

            CertificatesValidatedEvent event = new CertificatesValidatedEvent(
                command.uploadId(),
                validCertificateCount,
                invalidCertificateCount,
                validCrlCount,
                invalidCrlCount,
                LocalDateTime.now()
            );

            eventPublisher.publishEvent(event);
            log.info("CertificatesValidatedEvent published: uploadId={}, valid={}, invalid={}",
                command.uploadId(), totalValid, totalInvalid);

            // 7. SSE 진행 상황 전송: VALIDATION_COMPLETED (85%)
            progressService.sendProgress(
                ProcessingProgress.builder()
                    .uploadId(command.uploadId())
                    .stage(ProcessingStage.VALIDATION_COMPLETED)
                    .percentage(85)
                    .message(String.format("인증서 및 CRL 검증 완료: %d 유효, %d 실패",
                        totalValid, totalInvalid))
                    .processedCount(validCertificateCount + invalidCertificateCount + validCrlCount + invalidCrlCount)
                    .totalCount(command.getTotalCount())
                    .build()
            );

            // 8. Response 반환
            long durationMillis = System.currentTimeMillis() - startTime;
            log.info("Certificate validation completed in {}ms: valid={}, invalid={}", durationMillis, totalValid, totalInvalid);
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

    /**
     * CertificateData를 Certificate Aggregate로 변환
     *
     * <p>파싱된 인증서 데이터를 도메인 Aggregate로 변환합니다.</p>
     *
     * @param certData 파싱된 인증서 데이터
     * @return Certificate Aggregate
     */
    private Certificate convertToCertificate(CertificateData certData) {
        // TODO: Phase 4에서 실제 Value Objects 생성 로직 구현
        // 현재는 기본 생성만 수행

        CertificateType certificateType = convertCertificateType(certData.getCertificateType());

        log.debug("Certificate data converted: type={}, subject={}, fingerprint={}",
            certificateType, certData.getSubjectDN(), certData.getFingerprintSha256());

        // TODO: 실제 Certificate Aggregate 생성 로직 구현
        return null;  // 임시 처리
    }

    /**
     * CrlData를 CertificateRevocationList Aggregate로 변환
     *
     * <p>파싱된 CRL 데이터를 도메인 Aggregate로 변환합니다.</p>
     *
     * @param crlData 파싱된 CRL 데이터
     * @return CertificateRevocationList Aggregate
     */
    private CertificateRevocationList convertToCrl(CrlData crlData) {
        // TODO: Phase 4에서 실제 Value Objects 생성 로직 구현
        // 현재는 기본 처리만 수행

        log.debug("CRL data converted: issuer={}, thisUpdate={}",
            crlData.getIssuerDN(), crlData.getThisUpdate());

        // TODO: 실제 CertificateRevocationList Aggregate 생성 로직 구현
        return null;  // 임시 처리
    }

    /**
     * 문자열 인증서 타입을 도메인 CertificateType Enum으로 변환
     *
     * @param certTypeStr 파싱된 인증서 타입 문자열 (CSCA, DSC, DSC_NC 등)
     * @return CertificateType Enum
     */
    private CertificateType convertCertificateType(String certTypeStr) {
        if (certTypeStr == null || certTypeStr.isEmpty()) {
            return CertificateType.UNKNOWN;
        }

        try {
            return CertificateType.valueOf(certTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown certificate type: {}", certTypeStr);
            return CertificateType.UNKNOWN;
        }
    }
}
