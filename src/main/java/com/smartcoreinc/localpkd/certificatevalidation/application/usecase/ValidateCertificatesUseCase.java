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

            // 4. ParsedFile의 CertificateData 목록에서 검증 결과 추출 및 Certificate Aggregate 생성
            log.info("Extracting validation results and creating Certificate aggregates from {} parsed certificates...", command.certificateCount());
            List<CertificateData> certificateDataList = parsedFile.getCertificates();

            int validCertificateCount = 0;
            int invalidCertificateCount = 0;

            for (int i = 0; i < certificateDataList.size(); i++) {
                CertificateData certData = certificateDataList.get(i);

                // ParsedFile에 이미 저장된 isValid() 플래그 사용
                if (certData.isValid()) {
                    validCertificateCount++;
                    log.debug("Valid certificate extracted: {}", certData.getSubjectDN());

                    // NEW: Certificate Aggregate Root 생성 및 저장
                    try {
                        // ✅ Phase 17 Task 7.1: Duplicate detection before save
                        // Check if certificate with same fingerprint already exists
                        if (certificateRepository.existsByFingerprint(certData.getFingerprintSha256())) {
                            log.warn("Duplicate certificate detected (already in database), skipping: fingerprint={}, subject={}",
                                certData.getFingerprintSha256(), certData.getSubjectDN());
                            validCertificateCount--;
                            invalidCertificateCount++;
                            continue;
                        }

                        Certificate certificate = createCertificateAggregate(
                            uploadId, certData
                        );
                        certificateRepository.save(certificate);
                        log.debug("Certificate aggregate saved: id={}, subject={}",
                            certificate.getId().getId(), certData.getSubjectDN());
                    } catch (Exception e) {
                        log.warn("Failed to create Certificate aggregate for: {}",
                            certData.getSubjectDN(), e);
                        invalidCertificateCount++;
                        validCertificateCount--;
                    }
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

            // 5. ParsedFile의 CrlData 목록에서 검증 결과 추출 및 CertificateRevocationList Aggregate 생성
            log.info("Extracting validation results and creating CertificateRevocationList aggregates from {} parsed CRLs...", command.crlCount());
            List<CrlData> crlDataList = parsedFile.getCrls();

            int validCrlCount = 0;
            int invalidCrlCount = 0;

            // ✅ Phase 17 Task 7.1: Pre-load existing CRLs for this upload to detect duplicates
            List<CertificateRevocationList> existingCrls = crlRepository.findByUploadId(uploadId.getId());

            for (CrlData crlData : crlDataList) {
                // ParsedFile에 이미 저장된 isValid() 플래그 사용
                if (crlData.isValid()) {
                    validCrlCount++;
                    log.debug("Valid CRL extracted: {}", crlData.getIssuerDN());

                    // NEW: CertificateRevocationList Aggregate Root 생성 및 저장
                    try {
                        // ✅ Phase 17 Task 7.1: Duplicate detection for CRL before save
                        // Check if CRL with same issuer name already exists
                        String issuerDn = crlData.getIssuerDN();
                        String issuerName = parseIssuerName(issuerDn).getValue();

                        boolean crlExists = existingCrls.stream()
                            .anyMatch(crl -> crl.getIssuerName().getValue().equals(issuerName));

                        if (crlExists) {
                            log.warn("Duplicate CRL detected (already in database), skipping: issuerName={}, issuerDn={}",
                                issuerName, issuerDn);
                            validCrlCount--;
                            invalidCrlCount++;
                            continue;
                        }

                        CertificateRevocationList crl = createCertificateRevocationListAggregate(
                            uploadId, crlData
                        );
                        crlRepository.save(crl);

                        // Add to list so subsequent duplicates are detected
                        existingCrls.add(crl);

                        log.debug("CertificateRevocationList aggregate saved: id={}, issuer={}",
                            crl.getId().getId(), crlData.getIssuerDN());
                    } catch (Exception e) {
                        log.warn("Failed to create CertificateRevocationList aggregate for: {}",
                            crlData.getIssuerDN(), e);
                        invalidCrlCount++;
                        validCrlCount--;
                    }
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
     * CertificateData로부터 Certificate Aggregate 생성
     *
     * <p>파싱된 인증서 데이터를 도메인 Aggregate Root로 변환합니다.</p>
     *
     * @param uploadId 업로드 파일 ID (JPearl type)
     * @param certData 파싱된 인증서 데이터
     * @return Certificate Aggregate Root
     * @throws IllegalArgumentException 필수 정보가 부족한 경우
     */
    private Certificate createCertificateAggregate(UploadId uploadId, CertificateData certData) {
        log.debug("Creating Certificate aggregate: subject={}", certData.getSubjectDN());

        try {
            // UploadId에서 UUID 추출
            java.util.UUID uploadUuid = uploadId.getId();

            // 1. CertificateType 변환
            CertificateType certificateType = convertCertificateType(certData.getCertificateType());

            // 2. X509Data Value Object 생성 (PublicKey 없이는 제약)
            // 임시로 간단한 바이너리 데이터로 생성
            X509Data x509Data = X509Data.of(
                certData.getCertificateBinary(),
                null,  // PublicKey는 X.509 파싱에서 추출 필요 - 현재 null 사용
                certData.getSerialNumber(),
                certData.getFingerprintSha256()
            );

            // 3. SubjectInfo Value Object 생성
            SubjectInfo subjectInfo = parseSubjectInfo(certData.getSubjectDN(), certData.getCountryCode());

            // 4. IssuerInfo Value Object 생성
            IssuerInfo issuerInfo = parseIssuerInfo(certData.getIssuerDN());

            // 5. ValidityPeriod Value Object 생성
            ValidityPeriod validity = parseValidityPeriod(
                certData.getNotBefore(),
                certData.getNotAfter()
            );

            // 6. Certificate Aggregate Root 생성
            Certificate certificate = Certificate.create(
                uploadUuid,
                x509Data,
                subjectInfo,
                issuerInfo,
                validity,
                certificateType,
                "SHA256WithRSA"  // 기본값
            );

            log.debug("Certificate aggregate created: id={}, type={}, subject={}",
                certificate.getId().getId(), certificateType, certData.getSubjectDN());

            return certificate;

        } catch (Exception e) {
            log.error("Failed to create Certificate aggregate for: {}", certData.getSubjectDN(), e);
            throw new IllegalArgumentException(
                "Failed to create Certificate aggregate: " + e.getMessage(), e);
        }
    }

    /**
     * CrlData로부터 CertificateRevocationList Aggregate 생성
     *
     * <p>파싱된 CRL 데이터를 도메인 Aggregate Root로 변환합니다.</p>
     *
     * @param uploadId 업로드 파일 ID (JPearl type)
     * @param crlData 파싱된 CRL 데이터
     * @return CertificateRevocationList Aggregate Root
     * @throws IllegalArgumentException 필수 정보가 부족한 경우
     */
    private CertificateRevocationList createCertificateRevocationListAggregate(
            UploadId uploadId, CrlData crlData) {
        log.debug("Creating CertificateRevocationList aggregate: issuer={}", crlData.getIssuerDN());

        try {
            // UploadId에서 UUID 추출
            java.util.UUID uploadUuid = uploadId.getId();

            // 1. IssuerName Value Object 생성
            IssuerName issuerName = parseIssuerName(crlData.getIssuerDN());

            // 2. CountryCode Value Object 생성 (invalid 형식은 null로 처리)
            String countryCode = extractFromDn(crlData.getIssuerDN(), "C");
            // CountryCode.ofOrNull()을 사용하여 invalid 형식은 gracefully 처리
            CountryCode country = CountryCode.ofOrNull(countryCode);
            // 만약 countryCode가 null이면 country도 null이 됨 (nullable 컬럼이므로 OK)

            // 3. ValidityPeriod Value Object 생성
            java.time.LocalDateTime nextUpdate = crlData.getNextUpdate();
            if (nextUpdate == null) {
                nextUpdate = crlData.getThisUpdate().plusMonths(1);
            }
            ValidityPeriod validity = parseValidityPeriod(
                crlData.getThisUpdate(),
                nextUpdate
            );

            // 4. X509CrlData Value Object 생성
            // Note: X509CrlData.of() takes only (byte[], int) parameters
            // Using default version number 1
            X509CrlData x509CrlData = X509CrlData.of(
                crlData.getCrlBinary(),
                1  // CRL version
            );

            // 5. Empty RevokedCertificates (나중에 추가 가능)
            RevokedCertificates revokedCerts = RevokedCertificates.empty();

            // 6. CertificateRevocationList Aggregate Root 생성
            CertificateRevocationList crl = CertificateRevocationList.create(
                uploadUuid,
                CrlId.newId(),  // Using CrlId instead of CertificateRevocationListId
                issuerName,
                country,
                validity,
                x509CrlData,
                revokedCerts
            );

            log.debug("CertificateRevocationList aggregate created: id={}, issuer={}",
                crl.getId().getId(), crlData.getIssuerDN());

            return crl;

        } catch (Exception e) {
            log.error("Failed to create CertificateRevocationList aggregate for: {}", crlData.getIssuerDN(), e);
            throw new IllegalArgumentException(
                "Failed to create CertificateRevocationList aggregate: " + e.getMessage(), e);
        }
    }

    /**
     * DN 문자열로부터 SubjectInfo Value Object 파싱
     *
     * @param dn Distinguished Name (예: CN=Korea CSCA, O=Ministry, C=KR)
     * @param countryCodeOverride 국가 코드 (DN에 없으면 사용)
     * @return SubjectInfo Value Object
     */
    private SubjectInfo parseSubjectInfo(String dn, String countryCodeOverride) {
        String countryCode = extractFromDn(dn, "C");
        if (countryCode == null && countryCodeOverride != null) {
            countryCode = countryCodeOverride;
        }

        String organization = extractFromDn(dn, "O");
        String organizationalUnit = extractFromDn(dn, "OU");
        String commonName = extractFromDn(dn, "CN");

        return SubjectInfo.of(
            dn,
            countryCode,
            organization,
            organizationalUnit,
            commonName
        );
    }

    /**
     * DN 문자열로부터 IssuerInfo Value Object 파싱
     *
     * @param dn Distinguished Name
     * @return IssuerInfo Value Object
     */
    private IssuerInfo parseIssuerInfo(String dn) {
        String countryCode = extractFromDn(dn, "C");
        String organization = extractFromDn(dn, "O");
        String organizationalUnit = extractFromDn(dn, "OU");
        String commonName = extractFromDn(dn, "CN");

        return IssuerInfo.of(
            dn,
            countryCode,
            organization,
            organizationalUnit,
            commonName,
            false  // isCA는 X.509 인증서 확장에서 추출해야 함
        );
    }

    /**
     * DN 문자열로부터 IssuerName Value Object 파싱
     *
     * <p><b>✅ Phase 17 Fix</b>: DN에서 CN (Common Name) 컴포넌트만 추출</p>
     *
     * <p><b>ICAO DOC 9303 DN 형식 예시</b>:</p>
     * <ul>
     *   <li>CN=csca-canada,OU=pptc,O=gc,C=CA</li>
     *   <li>CN=ePassport CSCA 07,OU=MRTD Department,O=Public Service Agency,C=MD</li>
     *   <li>CN=Singapore Passport CA 6,OU=ICA,O=Ministry of Home Affairs,C=SG</li>
     * </ul>
     * <p>전체 DN이 아닌 <b>CN 값만</b> IssuerName으로 사용합니다.
     * 다른 RDN 컴포넌트(C, O, OU)는 해당 Value Objects에서 별도로 추출합니다.</p>
     *
     * @param dn Distinguished Name (예: CN=csca-canada,OU=pptc,O=gc,C=CA)
     * @return IssuerName Value Object (CN 값만 사용)
     * @throws DomainException CN 컴포넌트가 없거나 유효하지 않은 경우
     */
    private IssuerName parseIssuerName(String dn) {
        // DN에서 CN (Common Name) 컴포넌트만 추출
        String cnValue = extractFromDn(dn, "CN");

        if (cnValue == null || cnValue.isBlank()) {
            throw new DomainException(
                "INVALID_ISSUER_NAME",
                "No CN (Common Name) found in issuer DN: " + dn
            );
        }

        // ✅ CN 값으로 IssuerName 생성 (유효성만 검증, Trust Chain 제외)
        return IssuerName.of(cnValue);
    }

    /**
     * ValidityPeriod Value Object 파싱
     *
     * @param notBefore 유효 시작일
     * @param notAfter 유효 종료일
     * @return ValidityPeriod Value Object
     */
    private ValidityPeriod parseValidityPeriod(java.time.LocalDateTime notBefore, java.time.LocalDateTime notAfter) {
        return ValidityPeriod.of(notBefore, notAfter);
    }

    /**
     * DN에서 특정 RDN 속성 추출
     *
     * <p>예: DN = "CN=Korea CSCA,O=Ministry,C=KR"에서 "C"를 추출하면 "KR" 반환</p>
     *
     * @param dn Distinguished Name
     * @param attribute RDN 속성명 (예: "CN", "O", "C")
     * @return 속성값 (없으면 null)
     */
    private String extractFromDn(String dn, String attribute) {
        if (dn == null || dn.isEmpty()) {
            return null;
        }

        // DN 구성 요소를 쉼표로 분할
        String[] components = dn.split(",");

        for (String component : components) {
            component = component.trim();

            // 각 컴포넌트에서 attribute=value 파싱
            if (component.startsWith(attribute + "=")) {
                String value = component.substring((attribute + "=").length());
                // 따옴표 제거
                return value.replaceAll("^\"|\"$", "").trim();
            }
        }

        return null;
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
