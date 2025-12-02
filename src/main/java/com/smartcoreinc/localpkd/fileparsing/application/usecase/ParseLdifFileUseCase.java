package com.smartcoreinc.localpkd.fileparsing.application.usecase;

import com.smartcoreinc.localpkd.fileparsing.application.command.ParseLdifFileCommand;
import com.smartcoreinc.localpkd.fileparsing.application.response.ParseFileResponse;
import com.smartcoreinc.localpkd.fileparsing.domain.model.*;
import com.smartcoreinc.localpkd.fileparsing.domain.port.FileParserPort;
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

/**
 * ParseLdifFileUseCase - LDIF 파일 파싱 Use Case
 *
 * <p><b>Application Service</b>: LDIF 파일을 파싱하여 인증서와 CRL을 추출합니다.</p>
 *
 * <p><b>파싱 프로세스</b>:</p>
 * <ol>
 *   <li>Command 검증</li>
 *   <li>ParsedFile Aggregate Root 생성 (RECEIVED 상태)</li>
 *   <li>파싱 시작 (PARSING 상태로 전환, FileParsingStartedEvent 발행)</li>
 *   <li>FileParserPort를 통해 파일 파싱</li>
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
 * ParseLdifFileCommand command = ParseLdifFileCommand.builder()
 *     .uploadId(uploadId)
 *     .fileBytes(ldifBytes)
 *     .fileFormat("CSCA_COMPLETE_LDIF")
 *     .build();
 *
 * ParseFileResponse response = parseLdifFileUseCase.execute(command);
 *
 * if (response.success()) {
 *     log.info("Parsing completed: {} certificates, {} CRLs",
 *         response.certificateCount(), response.crlCount());
 * } else {
 *     log.error("Parsing failed: {}", response.errorMessage());
 * }
 * </pre>
 */
@Slf4j
@Service
public class ParseLdifFileUseCase {

    private final ParsedFileRepository repository;
    private final FileParserPort fileParserPort;
    private final ProgressService progressService;
    private final com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository certificateRepository;

    /**
     * Constructor with @Qualifier to specify which FileParserPort bean to inject
     */
    public ParseLdifFileUseCase(
            ParsedFileRepository repository,
            @Qualifier("ldifParserAdapter") FileParserPort fileParserPort,
            ProgressService progressService,
            com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository certificateRepository
    ) {
        this.repository = repository;
        this.fileParserPort = fileParserPort;
        this.progressService = progressService;
        this.certificateRepository = certificateRepository;
    }

    /**
     * LDIF 파일 파싱 실행
     *
     * @param command ParseLdifFileCommand
     * @return ParseFileResponse
     */
    @Transactional
    public ParseFileResponse execute(ParseLdifFileCommand command) {
        log.info("=== LDIF file parsing started ===");
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

                // 8. 파싱 완료 (통계 계산, CertificatesExtractedEvent, FileParsingCompletedEvent 발행)
                int totalEntries = parsedFile.getCertificates().size()
                                 + parsedFile.getCrls().size()
                                 + parsedFile.getErrors().size();
                parsedFile.completeParsing(totalEntries);

                log.info("Parsing completed: {} certificates, {} CRLs, {} errors",
                    parsedFile.getCertificates().size(),
                    parsedFile.getCrls().size(),
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

            // 11. Response 생성 - Certificate 테이블 및 ParsedFile 기준으로 실제 개수 계산
            // LDIF 파일의 경우:
            //  - Master List에서 추출한 CSCA들은 Certificate 테이블에 직접 저장됨
            //  - NC-DATA(DSC_NC) 등의 경우에는 ParsedFile에만 존재할 수 있으므로,
            //    DB 기준 개수가 0이어도 ParsedFile 기준 개수가 0이 아니면 그 값을 사용한다.
            int dbCertificateCount = certificateRepository.findByUploadId(uploadId.getId()).size();
            int parsedFileCertCount = saved.getCertificates().size();
            int parsedFileCrlCount = saved.getCrls().size();

            int effectiveCertificateCount = dbCertificateCount > 0 ? dbCertificateCount : parsedFileCertCount;
            int effectiveCrlCount = parsedFileCrlCount; // CRL은 아직 DB에 별도 저장하지 않으므로 ParsedFile 기준 사용

            log.info("LDIF parsing completed: dbCertificateCount={}, effectiveCertificateCount={}, crlCount={}, parsedFileCertCount={}",
                dbCertificateCount, effectiveCertificateCount, effectiveCrlCount, parsedFileCertCount);

            return ParseFileResponse.success(
                saved.getId().getId(),
                saved.getUploadId().getId(),
                saved.getFileFormat().toString(),
                saved.getStatus().name(),
                saved.getParsingStartedAt(),
                saved.getParsingCompletedAt(),
                effectiveCertificateCount,  // 검증/후속 단계에서 사용할 인증서 개수
                effectiveCrlCount,
                saved.getErrors().size(),
                saved.getStatistics().getDurationMillis()
            );

        } catch (DomainException e) {
            log.error("Domain error during LDIF parsing: {}", e.getMessage());
            return ParseFileResponse.failure(
                command.uploadId(),
                command.fileFormat(),
                e.getMessage()
            );
        } catch (Exception e) {
            log.error("Unexpected error during LDIF parsing", e);
            return ParseFileResponse.failure(
                command.uploadId(),
                command.fileFormat(),
                "파일 파싱 중 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }
}
