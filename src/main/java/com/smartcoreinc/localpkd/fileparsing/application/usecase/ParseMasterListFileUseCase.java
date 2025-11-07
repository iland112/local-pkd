package com.smartcoreinc.localpkd.fileparsing.application.usecase;

import com.smartcoreinc.localpkd.fileparsing.application.command.ParseMasterListFileCommand;
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

    /**
     * Constructor with @Qualifier to specify which FileParserPort bean to inject
     */
    public ParseMasterListFileUseCase(
            ParsedFileRepository repository,
            @Qualifier("masterListParserAdapter") FileParserPort fileParserPort,
            ProgressService progressService
    ) {
        this.repository = repository;
        this.fileParserPort = fileParserPort;
        this.progressService = progressService;
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

            // 5. 파싱 시작 (FileParsingStartedEvent는 나중에 발행)
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
}
