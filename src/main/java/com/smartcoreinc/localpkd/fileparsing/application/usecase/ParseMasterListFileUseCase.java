package com.smartcoreinc.localpkd.fileparsing.application.usecase;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.*;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.fileparsing.application.command.ParseMasterListFileCommand;
import com.smartcoreinc.localpkd.fileparsing.application.response.ParseFileResponse;
import com.smartcoreinc.localpkd.fileparsing.domain.model.*;
import com.smartcoreinc.localpkd.fileparsing.domain.port.FileParserPort;
import com.smartcoreinc.localpkd.fileparsing.domain.port.MasterListParser;
import com.smartcoreinc.localpkd.fileparsing.domain.repository.MasterListRepository;
import com.smartcoreinc.localpkd.fileparsing.domain.repository.ParsedFileRepository;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProcessingStage;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.security.auth.x500.X500Principal;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ParseMasterListFileUseCase - Master List 파일 파싱 Use Case
 *
 * <p><b>Application Service</b>: Master List (CMS) 파일을 파싱하여 인증서를 추출합니다.</p>
 *
 * <p><b>파싱 프로세스</b>:</p>
 * <ol>
 *   <li>Command 검증</li>
 *   <li>ParsedFile Aggregate Root 생성 (RECEIVED 상태)</li>
 *   <li>파싱 시작 (PARSING 상태로 전환, FileParsingStartedEvent 발행)</li>
 *   <li>FileParserPort를 통해 CMS 파일 파싱</li>
 *   <li>파싱 완료 (PARSED 상태로 전환, CertificatesExtractedEvent, FileParsingCompletedEvent 발행)</li>
 *   <li>Repository 저장 (Domain Events 자동 발행)</li>
 *   <li>Response 반환</li>
 * </ol>
 *
 * <p><b>Event-Driven Architecture</b>:</p>
 * <ul>
 *   <li>FileParsingStartedEvent → ProgressService (SSE: PARSING_STARTED)</li>
 *   <li>CertificatesExtractedEvent → Certificate Validation Context</li>
 *   <li>FileParsingCompletedEvent → ProgressService (SSE: PARSING_COMPLETED)</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * ParseMasterListFileCommand command = ParseMasterListFileCommand.builder()
 *     .uploadId(uploadId)
 *     .fileBytes(mlBytes)
 *     .fileFormat("ML_SIGNED_CMS")
 *     .build();
 *
 * ParseFileResponse response = parseMasterListFileUseCase.execute(command);
 *
 * if (response.success()) {
 *     log.info("Parsing completed: {} certificates", response.certificateCount());
 * } else {
 *     log.error("Parsing failed: {}", response.errorMessage());
 * }
 * </pre>
 */
@Slf4j
@Service
public class ParseMasterListFileUseCase {

    private final ParsedFileRepository repository;
    private final FileParserPort fileParserPort;
    private final ProgressService progressService;
    private final MasterListRepository masterListRepository;
    @SuppressWarnings("unused")  // Reserved for future certificate persistence during parsing
    private final CertificateRepository certificateRepository;
    private final MasterListParser masterListParser;

    /**
     * Constructor with @Qualifier to specify which FileParserPort bean to inject
     *
     * <p>Phase 3 Refactoring: Added MasterListRepository, CertificateRepository, MasterListParser</p>
     */
    public ParseMasterListFileUseCase(
            ParsedFileRepository repository,
            @Qualifier("masterListParserAdapter") FileParserPort fileParserPort,
            ProgressService progressService,
            MasterListRepository masterListRepository,
            CertificateRepository certificateRepository,
            MasterListParser masterListParser
    ) {
        this.repository = repository;
        this.fileParserPort = fileParserPort;
        this.progressService = progressService;
        this.masterListRepository = masterListRepository;
        this.certificateRepository = certificateRepository;
        this.masterListParser = masterListParser;
    }

    /**
     * Master List 파일 파싱 실행
     *
     * @param command ParseMasterListFileCommand
     * @return ParseFileResponse
     */
    @Transactional
    public ParseFileResponse execute(ParseMasterListFileCommand command) {
        log.info("=== Master List file parsing started ===");
        log.info("UploadId: {}, Format: {}, Size: {}",
            command.uploadId(), command.fileFormat(), command.getFileSizeDisplay());

        try {
            // 1. Command 검증
            command.validate();

            // 2. Value Objects 생성
            ParsedFileId parsedFileId = ParsedFileId.newId();
            UploadId uploadId = new UploadId(command.uploadId());
            FileFormat fileFormat = FileFormat.of(FileFormat.Type.valueOf(command.fileFormat()));

            // 3. ParsedFile Aggregate Root 생성 (RECEIVED 상태)
            ParsedFile parsedFile = ParsedFile.create(
                parsedFileId,
                uploadId,
                fileFormat
            );

            // 4. 파싱 시작 (PARSING 상태로 전환, FileParsingStartedEvent 발행)
            parsedFile.startParsing();

            // 5. Repository 저장 (FileParsingStartedEvent 발행)
            repository.save(parsedFile);

            log.info("Parsing started: parsedFileId={}", parsedFileId.getId());

            // 6. SSE 진행 상황 전송: PARSING_STARTED (10%)
            progressService.sendProgress(
                ProcessingProgress.parsingStarted(uploadId.getId(), command.fileFormat())
            );

            // 7. FileParserPort를 통해 파일 파싱
            try {
                fileParserPort.parse(command.fileBytes(), fileFormat, parsedFile);

                // ===========================
                // Phase 3: Create MasterList Entity and Extract CSCA Certificates from ML File
                // ===========================
                // Note: ML file is a collection of CSCA certificates signed by ICAO/UN root.
                // We create a MasterList entity to satisfy database constraints and enable traceability.

                // 7-1. MasterListParser를 사용하여 CSCA 인증서 추출
                MasterListParseResult parseResult = masterListParser.parse(command.fileBytes());
                log.info("MasterListParser extracted {} CSCAs from ML file", parseResult.getCscaCount());

                // 7-2. MasterList 엔티티 생성
                // ML file은 ICAO/UN의 글로벌 Master List이므로 특별한 country code 사용
                MasterList masterList = MasterList.create(
                    MasterListId.newId(),
                    uploadId,
                    CountryCode.of("ZZ"), // ZZ = International/UN (ICAO ML file)
                    MasterListVersion.unknown(), // ML files don't have version in command
                    CmsBinaryData.of(command.fileBytes()),
                    parseResult.getSignerInfo() != null ? parseResult.getSignerInfo() : SignerInfo.empty(),
                    parseResult.getCscaCount()
                );

                // 7-3. MasterList 저장 (MasterListCreatedEvent 발행)
                MasterList savedMasterList = masterListRepository.save(masterList);
                log.info("Created MasterList entity for ML file: id={}, cscaCount={}",
                    savedMasterList.getId().getId(), savedMasterList.getCscaCount());

                // 7-4. 개별 CSCA Certificate 엔티티 생성
                List<Certificate> cscaCertificates = new ArrayList<>();
                for (MasterListParseResult.ParsedCsca parsedCsca : parseResult.getCscaCertificates()) {
                    try {
                        // Create Certificate with masterListId from the created MasterList
                        Certificate cert = createCertificateFromParsedCsca(
                                uploadId.getId(),
                                savedMasterList.getId().getId(), // Use MasterList ID
                                parsedCsca
                        );
                        cscaCertificates.add(cert);
                    } catch (Exception e) {
                        log.warn("Failed to create Certificate from ParsedCsca: {}", e.getMessage());
                        // Continue with other certificates
                    }
                }

                // 7-5. Certificate 엔티티 일괄 저장
                // ❌ REMOVED: Parsing phase should NOT save Certificate entities (DDD architecture violation)
                // Certificate entities should only be created and saved by ValidateCertificatesUseCase
                // List<Certificate> savedCertificates = certificateRepository.saveAll(cscaCertificates);
                // log.info("Saved {} CSCA certificates from ML file to certificate table", savedCertificates.size());
                log.info("Skipping Certificate entity save during parsing (will be saved during validation phase)");

                // ===========================
                // End of Phase 3 Logic
                // ===========================

                // 8. 파싱 완료 (통계 계산, CertificatesExtractedEvent, FileParsingCompletedEvent 발행)
                int totalEntries = parsedFile.getCertificates().size()
                                 + parsedFile.getCrls().size()
                                 + parsedFile.getErrors().size();
                parsedFile.completeParsing(totalEntries);

                log.info("Parsing completed: {} certificates, {} errors",
                    parsedFile.getCertificates().size(),
                    parsedFile.getErrors().size());

                // 9. SSE 진행 상황 전송: PARSING_COMPLETED (60%)
                progressService.sendProgress(
                    ProcessingProgress.parsingCompleted(
                        uploadId.getId(),
                        totalEntries
                    )
                );

            } catch (FileParserPort.ParsingException e) {
                // 파싱 실패 (FAILED 상태로 전환, ParsingFailedEvent 발행)
                log.error("Parsing failed: {}", e.getMessage(), e);
                parsedFile.failParsing(e.getMessage());

                // SSE 진행 상황 전송: FAILED
                progressService.sendProgress(
                    ProcessingProgress.failed(
                        uploadId.getId(),
                        ProcessingStage.PARSING_IN_PROGRESS,
                        e.getMessage()
                    )
                );
            }

            // 10. Repository 저장 (모든 Domain Events 발행)
            ParsedFile saved = repository.save(parsedFile);

            // 11. Response 생성
            return ParseFileResponse.success(
                saved.getId().getId(),
                saved.getUploadId().getId(),
                saved.getFileFormat().toString(),
                saved.getStatus().name(),
                saved.getParsingStartedAt(),
                saved.getParsingCompletedAt(),
                saved.getCertificates().size(),
                saved.getCrls().size(),
                saved.getErrors().size(),
                saved.getStatistics().getDurationMillis()
            );

        } catch (DomainException e) {
            log.error("Domain error during Master List parsing: {}", e.getMessage());
            return ParseFileResponse.failure(
                command.uploadId(),
                command.fileFormat(),
                e.getMessage()
            );
        } catch (Exception e) {
            log.error("Unexpected error during Master List parsing", e);
            return ParseFileResponse.failure(
                command.uploadId(),
                command.fileFormat(),
                "파일 파싱 중 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }

    // ===========================
    // Phase 3: Helper Methods
    // ===========================

    /**
     * ParsedCsca로부터 Certificate 엔티티 생성
     *
     * <p>X509Certificate에서 필요한 value objects를 추출하여 Certificate aggregate를 생성합니다.</p>
     *
     * @param uploadId 업로드 ID
     * @param masterListId Master List ID
     * @param parsedCsca 파싱된 CSCA 데이터
     * @return Certificate 엔티티
     * @throws CertificateException 인증서 처리 오류
     */
    private Certificate createCertificateFromParsedCsca(
            java.util.UUID uploadId,
            java.util.UUID masterListId,
            MasterListParseResult.ParsedCsca parsedCsca
    ) throws CertificateException {
        X509Certificate x509Cert = parsedCsca.getX509Certificate();

        // 1. X509Data 생성
        X509Data x509Data = X509Data.of(
                x509Cert.getEncoded(),
                x509Cert.getPublicKey(),
                x509Cert.getSerialNumber().toString(16).toUpperCase(),
                parsedCsca.getFingerprintSha256()
        );

        // 2. SubjectInfo 생성
        X500Principal subjectPrincipal = x509Cert.getSubjectX500Principal();
        String subjectDn = subjectPrincipal.getName();
        SubjectInfo subjectInfo = SubjectInfo.of(
                subjectDn,
                extractDnComponent(subjectDn, "C"),
                extractDnComponent(subjectDn, "O"),
                extractDnComponent(subjectDn, "OU"),
                extractDnComponent(subjectDn, "CN")
        );

        // 3. IssuerInfo 생성
        X500Principal issuerPrincipal = x509Cert.getIssuerX500Principal();
        String issuerDn = issuerPrincipal.getName();
        boolean isCA = x509Cert.getBasicConstraints() != -1; // -1 means not a CA
        IssuerInfo issuerInfo = IssuerInfo.of(
                issuerDn,
                extractDnComponent(issuerDn, "C"),
                extractDnComponent(issuerDn, "O"),
                extractDnComponent(issuerDn, "OU"),
                extractDnComponent(issuerDn, "CN"),
                isCA
        );

        // 4. ValidityPeriod 생성
        ValidityPeriod validity = ValidityPeriod.of(
                x509Cert.getNotBefore().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                x509Cert.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
        );

        // 5. Signature Algorithm
        String signatureAlgorithm = x509Cert.getSigAlgName();

        // 6. Certificate 생성 (createFromMasterList factory method)
        return Certificate.createFromMasterList(
                uploadId,
                masterListId,
                x509Data,
                subjectInfo,
                issuerInfo,
                validity,
                signatureAlgorithm
        );
    }

    /**
     * DN에서 특정 컴포넌트 추출
     *
     * <p>예시: "CN=CSCA KR, O=Korea, C=KR"에서 "C" 추출 → "KR"</p>
     *
     * @param dn Distinguished Name
     * @param component 추출할 컴포넌트 (C, O, OU, CN 등)
     * @return 추출된 값 (없으면 null)
     */
    private String extractDnComponent(String dn, String component) {
        if (dn == null || dn.isEmpty()) {
            return null;
        }

        // RFC 2253 형식: "CN=..., O=..., C=..."
        // 정규식: (?:^|,)\s*COMPONENT\s*=\s*([^,]+)
        Pattern pattern = Pattern.compile(
                "(?:^|,)\\s*" + Pattern.quote(component) + "\\s*=\\s*([^,]+)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(dn);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }
}
