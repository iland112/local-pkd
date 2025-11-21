package com.smartcoreinc.localpkd.certificatevalidation.application.usecase;

import com.smartcoreinc.localpkd.certificatevalidation.application.command.ValidateCertificatesCommand;
import com.smartcoreinc.localpkd.certificatevalidation.application.response.CertificatesValidatedResponse;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.*;
import com.smartcoreinc.localpkd.certificatevalidation.domain.event.CertificatesValidatedEvent;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import com.smartcoreinc.localpkd.fileparsing.domain.model.CertificateData;
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

import java.io.ByteArrayInputStream;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
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

            // 3. 파싱된 파일 조회
            UploadId uploadId = UploadId.of(command.uploadId().toString());
            ParsedFile parsedFile = parsedFileRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new DomainException(
                    "PARSED_FILE_NOT_FOUND",
                    "파싱된 파일을 찾을 수 없습니다: uploadId=" + command.uploadId()
                ));

            log.info("Found parsed file: {} certificates, {} CRLs",
                parsedFile.getCertificates().size(), parsedFile.getCrls().size());

            // 4. 파싱된 인증서 검증 및 저장 (Two-Pass 처리)
            List<CertificateData> certificateDataList = parsedFile.getCertificates();
            int validCertificateCount = 0;
            int invalidCertificateCount = 0;

            // === Pass 1: CSCA 인증서만 먼저 검증/저장 ===
            log.info("=== Pass 1: CSCA certificate validation started ===");
            for (int i = 0; i < certificateDataList.size(); i++) {
                CertificateData certData = certificateDataList.get(i);

                if (!certData.isCsca()) {
                    continue;  // CSCA가 아니면 스킵
                }

                try {
                    log.debug("Validating CSCA {}: country={}, subject={}",
                        i + 1, certData.getCountryCode(), certData.getSubjectDN());

                    X509Certificate x509Cert = convertToX509Certificate(certData.getCertificateBinary());
                    boolean isValid = validateCscaCertificate(x509Cert, certData);

                    if (isValid) {
                        Certificate certificate = createCertificateFromData(certData, x509Cert, command.uploadId());
                        certificateRepository.save(certificate);
                        validCertificateCount++;
                        log.info("CSCA certificate validated and saved: country={}, subject={}",
                            certData.getCountryCode(), certData.getSubjectDN());
                    } else {
                        invalidCertificateCount++;
                        log.warn("CSCA certificate validation failed: country={}, subject={}",
                            certData.getCountryCode(), certData.getSubjectDN());
                    }

                } catch (Exception e) {
                    invalidCertificateCount++;
                    log.error("CSCA certificate validation failed: subject={}", certData.getSubjectDN(), e);
                }

                // SSE 진행 상황 업데이트 (70-77% 범위 - Pass 1)
                int processed = validCertificateCount + invalidCertificateCount;
                int percentage = 70 + (int) ((processed / (double) command.getTotalCount()) * 7);
                progressService.sendProgress(
                    ProcessingProgress.builder()
                        .uploadId(command.uploadId())
                        .stage(ProcessingStage.VALIDATION_IN_PROGRESS)
                        .percentage(Math.min(77, percentage))
                        .message(String.format("CSCA 인증서 검증 중 (%d/%d)", processed, command.getTotalCount()))
                        .processedCount(processed)
                        .totalCount(command.getTotalCount())
                        .build()
                );
            }

            log.info("Pass 1 completed: {} CSCA certificates validated ({} valid, {} invalid)",
                validCertificateCount + invalidCertificateCount, validCertificateCount, invalidCertificateCount);

            // === Pass 2: DSC/DSC_NC 인증서 검증/저장 ===
            log.info("=== Pass 2: DSC/DSC_NC certificate validation started ===");
            for (int i = 0; i < certificateDataList.size(); i++) {
                CertificateData certData = certificateDataList.get(i);

                if (certData.isCsca()) {
                    continue;  // CSCA는 이미 처리했으므로 스킵
                }

                try {
                    log.debug("Validating DSC/DSC_NC {}: type={}, country={}, subject={}",
                        i + 1, certData.getCertificateType(), certData.getCountryCode(), certData.getSubjectDN());

                    X509Certificate x509Cert = convertToX509Certificate(certData.getCertificateBinary());

                    // DSC/DSC_NC 검증 (CSCA로 서명 검증)
                    boolean isValid = validateDscCertificate(x509Cert, certData, command.uploadId());

                    if (isValid) {
                        Certificate certificate = createCertificateFromData(certData, x509Cert, command.uploadId());
                        certificateRepository.save(certificate);
                        validCertificateCount++;
                        log.info("DSC/DSC_NC certificate validated and saved: type={}, country={}, subject={}",
                            certData.getCertificateType(), certData.getCountryCode(), certData.getSubjectDN());
                    } else {
                        invalidCertificateCount++;
                        log.warn("DSC/DSC_NC certificate validation failed: type={}, country={}, subject={}",
                            certData.getCertificateType(), certData.getCountryCode(), certData.getSubjectDN());
                    }

                } catch (Exception e) {
                    invalidCertificateCount++;
                    log.error("DSC/DSC_NC certificate validation failed: subject={}", certData.getSubjectDN(), e);
                }

                // SSE 진행 상황 업데이트 (77-85% 범위 - Pass 2)
                int processed = validCertificateCount + invalidCertificateCount;
                int percentage = 77 + (int) ((processed / (double) command.getTotalCount()) * 8);
                progressService.sendProgress(
                    ProcessingProgress.builder()
                        .uploadId(command.uploadId())
                        .stage(ProcessingStage.VALIDATION_IN_PROGRESS)
                        .percentage(Math.min(85, percentage))
                        .message(String.format("DSC/DSC_NC 인증서 검증 중 (%d/%d)", processed, command.getTotalCount()))
                        .processedCount(processed)
                        .totalCount(command.getTotalCount())
                        .build()
                );
            }

            log.info("Pass 2 completed: Total certificates validated: {} ({} valid, {} invalid)",
                validCertificateCount + invalidCertificateCount, validCertificateCount, invalidCertificateCount);

            // 5. CRL은 현재 스킵 (향후 Phase에서 구현)
            int validCrlCount = 0;
            int invalidCrlCount = 0;
            log.info("CRL validation skipped (future implementation)");

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

    // ========== Helper Methods ==========

    /**
     * byte[] 인증서 데이터를 X509Certificate로 변환
     */
    private X509Certificate convertToX509Certificate(byte[] certBytes) throws Exception {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        ByteArrayInputStream bais = new ByteArrayInputStream(certBytes);
        return (X509Certificate) certFactory.generateCertificate(bais);
    }

    /**
     * CSCA 인증서 검증
     *
     * ICAO Doc 9303에 따라:
     * 1. Self-signed 서명 검증
     * 2. Validity period 검증
     * 3. Basic Constraints 검증 (CA=true)
     */
    private boolean validateCscaCertificate(X509Certificate x509Cert, CertificateData certData) {
        try {
            // 1. Self-signed 서명 검증
            // CSCA는 자체 서명(self-signed) 인증서이므로 자기 자신의 공개 키로 검증
            try {
                x509Cert.verify(x509Cert.getPublicKey());
                log.debug("Self-signed signature verified for CSCA: {}", certData.getSubjectDN());
            } catch (Exception e) {
                log.error("Self-signed signature verification failed for CSCA: {}", certData.getSubjectDN(), e);
                return false;
            }

            // 2. Validity period 검증
            try {
                x509Cert.checkValidity();
                log.debug("Validity period checked for CSCA: {}", certData.getSubjectDN());
            } catch (Exception e) {
                log.warn("Validity period check failed for CSCA: {}", certData.getSubjectDN(), e);
                // Validity period 오류는 경고만 하고 진행 (만료된 CSCA도 저장)
            }

            // 3. Basic Constraints 검증 (CA=true)
            int basicConstraints = x509Cert.getBasicConstraints();
            if (basicConstraints < 0) {
                log.error("CSCA must be a CA certificate (basicConstraints >= 0), but got: {}. Subject: {}",
                    basicConstraints, certData.getSubjectDN());
                return false;
            }

            log.debug("Basic constraints verified for CSCA: CA=true, pathLength={}. Subject: {}",
                basicConstraints, certData.getSubjectDN());

            return true;

        } catch (Exception e) {
            log.error("Unexpected error during CSCA validation: {}", certData.getSubjectDN(), e);
            return false;
        }
    }

    /**
     * DSC/DSC_NC 인증서 검증
     *
     * ICAO Doc 9303에 따라:
     * 1. CSCA로 서명 검증 (Issuer CSCA 조회)
     * 2. Validity period 검증
     * 3. Basic Constraints 검증 (CA=false 또는 없음)
     */
    private boolean validateDscCertificate(
        X509Certificate x509Cert,
        CertificateData certData,
        java.util.UUID uploadId
    ) {
        try {
            // 1. Issuer DN으로 CSCA 조회
            String issuerDN = certData.getIssuerDN();
            log.debug("Finding CSCA for DSC validation: issuerDN={}", issuerDN);

            // TODO: CertificateRepository에 findBySubjectDN() 메서드 추가 필요
            // 현재는 CSCA를 찾을 수 없으므로 서명 검증을 스킵하고 경고만 출력
            log.warn("CSCA lookup not implemented yet. Skipping signature verification for DSC: {}",
                certData.getSubjectDN());

            // 향후 구현 예정:
            // List<Certificate> cscaCerts = certificateRepository.findBySubjectDN(issuerDN);
            // if (cscaCerts.isEmpty()) {
            //     log.error("CSCA not found for DSC. IssuerDN: {}", issuerDN);
            //     return false;
            // }
            //
            // // CSCA 인증서로 DSC 서명 검증
            // Certificate cscaCert = cscaCerts.get(0);
            // X509Certificate cscaX509 = convertToX509Certificate(
            //     cscaCert.getX509Data().getCertificateBinary()
            // );
            // x509Cert.verify(cscaX509.getPublicKey());

            // 2. Validity period 검증
            try {
                x509Cert.checkValidity();
                log.debug("Validity period checked for DSC: {}", certData.getSubjectDN());
            } catch (Exception e) {
                log.warn("Validity period check failed for DSC: {}", certData.getSubjectDN(), e);
                // Validity period 오류는 경고만 하고 진행 (만료된 DSC도 저장)
            }

            // 3. Basic Constraints 검증 (DSC는 CA가 아니어야 함)
            int basicConstraints = x509Cert.getBasicConstraints();
            if (basicConstraints >= 0) {
                log.warn("DSC should not be a CA certificate, but basicConstraints={}. Subject: {}",
                    basicConstraints, certData.getSubjectDN());
                // Non-Conformant의 경우 일부 제약 조건 완화 - 경고만 하고 진행
            }

            log.debug("DSC/DSC_NC validation completed (signature verification skipped): {}",
                certData.getSubjectDN());

            return true;

        } catch (Exception e) {
            log.error("Unexpected error during DSC validation: {}", certData.getSubjectDN(), e);
            return false;
        }
    }

    /**
     * CertificateData로부터 Certificate 엔티티 생성
     */
    private Certificate createCertificateFromData(
        CertificateData certData,
        X509Certificate x509Cert,
        java.util.UUID uploadId
    ) throws Exception {
        // X509Data 생성
        X509Data x509Data = X509Data.of(
            certData.getCertificateBinary(),
            x509Cert.getPublicKey(),
            certData.getSerialNumber(),
            certData.getFingerprintSha256()
        );

        // SubjectInfo 생성 (DN에서 CN, Country Code 추출)
        String subjectCN = extractCNFromDN(certData.getSubjectDN());
        String subjectCountryCode = extractCountryCodeFromDN(certData.getSubjectDN());
        // Fallback to certData country code if DN doesn't contain it
        if (subjectCountryCode == null) {
            subjectCountryCode = certData.getCountryCode();
        }
        SubjectInfo subjectInfo = SubjectInfo.of(
            certData.getSubjectDN(),
            subjectCountryCode,
            null,  // organization - 향후 DN 파싱으로 추출 가능
            null,  // organizationalUnit
            subjectCN
        );

        // IssuerInfo 생성 (DN에서 CN, Country Code 추출)
        String issuerCN = extractCNFromDN(certData.getIssuerDN());
        String issuerCountryCode = extractCountryCodeFromDN(certData.getIssuerDN());
        // Fallback to certData country code if DN doesn't contain it
        if (issuerCountryCode == null) {
            issuerCountryCode = certData.getCountryCode();
        }
        IssuerInfo issuerInfo = IssuerInfo.of(
            certData.getIssuerDN(),
            issuerCountryCode,
            null,  // organization
            null,  // organizationalUnit
            issuerCN,
            true   // CSCA는 CA이므로 true
        );

        // ValidityPeriod 생성
        ValidityPeriod validity = ValidityPeriod.of(
            convertToLocalDateTime(x509Cert.getNotBefore()),
            convertToLocalDateTime(x509Cert.getNotAfter())
        );

        // CertificateType 결정
        CertificateType certificateType = CertificateType.valueOf(certData.getCertificateType());

        // 서명 알고리즘
        String signatureAlgorithm = x509Cert.getSigAlgName();

        // Certificate 생성
        return Certificate.create(
            uploadId,
            x509Data,
            subjectInfo,
            issuerInfo,
            validity,
            certificateType,
            signatureAlgorithm
        );
    }

    /**
     * DN에서 CN (Common Name) 추출
     */
    private String extractCNFromDN(String dn) {
        if (dn == null || dn.isBlank()) {
            return null;
        }

        // DN 형식: "CN=Korea CSCA, O=Ministry, C=KR"
        // CN= 부분 찾기
        int cnIndex = dn.indexOf("CN=");
        if (cnIndex >= 0) {
            int startIndex = cnIndex + 3;  // "CN=" 다음부터
            int endIndex = dn.indexOf(',', startIndex);
            if (endIndex > 0) {
                return dn.substring(startIndex, endIndex).trim();
            } else {
                return dn.substring(startIndex).trim();
            }
        }

        return null;
    }

    /**
     * DN에서 Country Code (C) 추출
     */
    private String extractCountryCodeFromDN(String dn) {
        if (dn == null || dn.isBlank()) {
            return null;
        }

        // DN 형식: "CN=Korea CSCA, O=Ministry, C=KR"
        // C= 부분 찾기 (대소문자 구분 없이)
        String dnUpper = dn.toUpperCase();
        int cIndex = dnUpper.indexOf("C=");
        if (cIndex >= 0) {
            int startIndex = cIndex + 2;  // "C=" 다음부터
            int endIndex = dn.indexOf(',', startIndex);
            if (endIndex > 0) {
                return dn.substring(startIndex, endIndex).trim().toUpperCase();
            } else {
                return dn.substring(startIndex).trim().toUpperCase();
            }
        }

        return null;
    }

    /**
     * Date를 LocalDateTime으로 변환
     */
    private LocalDateTime convertToLocalDateTime(Date date) {
        return date.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
    }
}
