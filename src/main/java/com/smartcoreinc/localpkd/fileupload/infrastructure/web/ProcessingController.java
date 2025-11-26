package com.smartcoreinc.localpkd.fileupload.infrastructure.web;

import com.smartcoreinc.localpkd.fileparsing.application.command.ParseLdifFileCommand;
import com.smartcoreinc.localpkd.fileparsing.application.command.ParseMasterListFileCommand;
import com.smartcoreinc.localpkd.fileparsing.application.usecase.ParseLdifFileUseCase;
import com.smartcoreinc.localpkd.fileparsing.application.usecase.ParseMasterListFileUseCase;
import com.smartcoreinc.localpkd.fileupload.application.response.ProcessingResponse;
import com.smartcoreinc.localpkd.fileupload.application.response.ProcessingStatusResponse;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import com.smartcoreinc.localpkd.fileupload.domain.model.FilePath;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.fileupload.domain.port.FileStoragePort;
import com.smartcoreinc.localpkd.fileupload.domain.repository.UploadedFileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Processing Controller - 파일 처리 단계 관리 컨트롤러
 *
 * <p>MANUAL 모드에서 사용자가 각 처리 단계(파싱, 검증, LDAP 업로드)를 수동으로 트리거할 수 있는 REST API를 제공합니다.</p>
 *
 * <h3>사용 시나리오</h3>
 *
 * <p><strong>AUTO 모드</strong>: 다음 엔드포인트들은 응답하지 않습니다 (단계가 자동으로 진행됨).</p>
 *
 * <p><strong>MANUAL 모드</strong>: 사용자가 각 단계마다 버튼을 클릭하여 다음과 같이 요청합니다:
 *
 * <pre>{@code
 * // 1. 파일 업로드 후
 * POST /api/processing/parse/{uploadId}
 * → FileUploadedEvent 수신 (MANUAL 모드)
 * → UI에 파싱 시작 버튼 표시
 *
 * // 2. 사용자가 "파싱 시작" 클릭
 * POST /api/processing/parse/{uploadId}
 * → FileParsingStartedEvent 발행
 * → SSE /progress/stream으로 진행 상황 전송
 * → 파싱 완료
 * → ProcessingResponse: status=COMPLETED
 * → UI에 검증 시작 버튼 표시
 *
 * // 3. 사용자가 "검증 시작" 클릭
 * POST /api/processing/validate/{uploadId}
 * → CertificateValidationStartedEvent 발행
 * → SSE로 진행 상황 전송
 * → 검증 완료
 * → ProcessingResponse: status=COMPLETED
 * → UI에 LDAP 업로드 버튼 표시
 *
 * // 4. 사용자가 "LDAP 업로드" 클릭
 * POST /api/processing/upload-to-ldap/{uploadId}
 * → UploadToLdapStartedEvent 발행
 * → SSE로 진행 상황 전송
 * → LDAP 업로드 완료
 * → ProcessingResponse: status=COMPLETED
 * → 처리 완료
 * }</pre>
 * </p>
 *
 * <h3>API 엔드포인트</h3>
 *
 * <table>
 *   <tr>
 *     <th>Method</th>
 *     <th>Endpoint</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td>POST</td>
 *     <td>/api/processing/parse/{uploadId}</td>
 *     <td>파일 파싱 시작 (MANUAL 모드)</td>
 *   </tr>
 *   <tr>
 *     <td>POST</td>
 *     <td>/api/processing/validate/{uploadId}</td>
 *     <td>인증서 검증 시작 (MANUAL 모드)</td>
 *   </tr>
 *   <tr>
 *     <td>POST</td>
 *     <td>/api/processing/upload-to-ldap/{uploadId}</td>
 *     <td>LDAP 서버 업로드 시작 (MANUAL 모드)</td>
 *   </tr>
 *   <tr>
 *     <td>GET</td>
 *     <td>/api/processing/status/{uploadId}</td>
 *     <td>파일 처리 상태 조회 (모든 모드)</td>
 *   </tr>
 * </table>
 *
 * <h3>오류 처리</h3>
 *
 * <table>
 *   <tr>
 *     <th>Status Code</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td>202 Accepted</td>
 *     <td>처리 요청이 성공적으로 접수됨</td>
 *   </tr>
 *   <tr>
 *     <td>400 Bad Request</td>
 *     <td>MANUAL 모드가 아닌 경우</td>
 *   </tr>
 *   <tr>
 *     <td>404 Not Found</td>
 *     <td>해당 uploadId의 파일이 존재하지 않음</td>
 *   </tr>
 *   <tr>
 *     <td>500 Internal Server Error</td>
 *     <td>처리 중 서버 오류 발생</td>
 *   </tr>
 * </table>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
@Tag(name = "파일 처리 관리 API", description = "MANUAL 모드에서의 파일 처리 단계를 관리하는 API")
@Slf4j
@RestController
@RequestMapping("/api/processing")
@RequiredArgsConstructor
public class ProcessingController {

    private final UploadedFileRepository uploadedFileRepository;
    private final ParseLdifFileUseCase parseLdifFileUseCase;
    private final ParseMasterListFileUseCase parseMasterListFileUseCase;
    private final FileStoragePort fileStoragePort;

    // TODO: 다음 Use Cases는 Phase 19에서 구현 예정
    // private final ValidateCertificatesUseCase validateCertificatesUseCase;
    // private final UploadToLdapUseCase uploadToLdapUseCase;

    @Operation(summary = "파일 파싱 시작 (MANUAL 모드)",
               description = "MANUAL 처리 모드로 업로드된 파일의 파싱 단계를 시작합니다.")
    @ApiResponse(responseCode = "202", description = "파싱 요청 성공")
    @ApiResponse(responseCode = "400", description = "MANUAL 모드가 아님")
    @ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음")
    @PostMapping("/parse/{uploadId}")
    public ResponseEntity<ProcessingResponse> parseFile(
            @PathVariable String uploadId
    ) {
        log.info("Parse file request: uploadId={}", uploadId);

        try {
            UUID uploadUUID = UUID.fromString(uploadId);
            UploadId uploadIdVO = new UploadId(uploadUUID);

            // 파일 조회
            Optional<com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile> uploadedFileOpt =
                    uploadedFileRepository.findById(uploadIdVO);

            if (uploadedFileOpt.isEmpty()) {
                log.warn("File not found: uploadId={}", uploadId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ProcessingResponse.fileNotFound(uploadUUID));
            }

            com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile uploadedFile = uploadedFileOpt.get();

            // MANUAL 모드 확인
            if (!uploadedFile.isManualMode()) {
                log.warn("File is not in MANUAL mode: uploadId={}, mode={}",
                        uploadId, uploadedFile.getProcessingMode());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ProcessingResponse.notManualMode(uploadUUID));
            }

            // Read file bytes from storage
            byte[] fileBytes = fileStoragePort.readFile(uploadedFile.getFilePath());
            String fileFormatName = uploadedFile.getFileFormat().getType().name();

            if (uploadedFile.getFileFormat().isLdif()) {
                parseLdifFileUseCase.execute(ParseLdifFileCommand.builder()
                        .uploadId(uploadIdVO.toUUID()) // Corrected here
                        .fileBytes(fileBytes)
                        .fileFormat(fileFormatName)
                        .build());
            } else if (uploadedFile.getFileFormat().isMasterList()) {
                parseMasterListFileUseCase.execute(ParseMasterListFileCommand.builder()
                        .uploadId(uploadIdVO.toUUID()) // Corrected here
                        .fileBytes(fileBytes)
                        .fileFormat(fileFormatName)
                        .build());
            } else {
                log.error("Unsupported file format for parsing: uploadId={}, format={}",
                        uploadId, uploadedFile.getFileFormat());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ProcessingResponse.error(uploadUUID, "PARSING", "Unsupported file format"));
            }

            log.info("File parsing started: uploadId={}", uploadId);
            uploadedFile.markReadyForParsing();
            uploadedFileRepository.save(uploadedFile);

            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ProcessingResponse.parsingStarted(uploadUUID));

        } catch (IllegalArgumentException e) {
            log.error("Invalid uploadId format: {}", uploadId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ProcessingResponse.error(null, "PARSING", "Invalid uploadId format"));
        } catch (Exception e) {
            log.error("Error parsing file: uploadId={}", uploadId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProcessingResponse.error(UUID.fromString(uploadId), "PARSING", e.getMessage()));
        }
    }

    @Operation(summary = "인증서 검증 시작 (MANUAL 모드)",
               description = "MANUAL 처리 모드로 파싱이 완료된 파일의 인증서 검증 단계를 시작합니다.")
    @ApiResponse(responseCode = "202", description = "검증 요청 성공")
    @ApiResponse(responseCode = "400", description = "MANUAL 모드가 아님")
    @ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음")
    @PostMapping("/validate/{uploadId}")
    public ResponseEntity<ProcessingResponse> validateCertificates(
            @PathVariable String uploadId
    ) {
        log.info("Validate certificates request: uploadId={}", uploadId);

        try {
            UUID uploadUUID = UUID.fromString(uploadId);
            UploadId uploadIdVO = new UploadId(uploadUUID);

            // 파일 조회
            Optional<com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile> uploadedFileOpt =
                    uploadedFileRepository.findById(uploadIdVO);

            if (uploadedFileOpt.isEmpty()) {
                log.warn("File not found: uploadId={}", uploadId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ProcessingResponse.fileNotFound(uploadUUID));
            }

            com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile uploadedFile = uploadedFileOpt.get();

            // MANUAL 모드 확인
            if (!uploadedFile.isManualMode()) {
                log.warn("File is not in MANUAL mode: uploadId={}, mode={}",
                        uploadId, uploadedFile.getProcessingMode());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ProcessingResponse.notManualMode(uploadUUID));
            }

            // TODO: ValidateCertificatesUseCase 호출 (Phase 19)
            // validateCertificatesUseCase.execute(new ValidateCertificatesCommand(uploadId));

            log.info("Certificate validation started: uploadId={}", uploadId);
            uploadedFile.markReadyForValidation();
            uploadedFileRepository.save(uploadedFile);

            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ProcessingResponse.validationStarted(uploadUUID));

        } catch (IllegalArgumentException e) {
            log.error("Invalid uploadId format: {}", uploadId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ProcessingResponse.error(null, "VALIDATION", "Invalid uploadId format"));
        } catch (Exception e) {
            log.error("Error validating certificates: uploadId={}", uploadId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProcessingResponse.error(UUID.fromString(uploadId), "VALIDATION", e.getMessage()));
        }
    }

    @Operation(summary = "LDAP 서버에 업로드 시작 (MANUAL 모드)",
               description = "MANUAL 처리 모드로 검증이 완료된 파일의 인증서/CRL을 LDAP 서버에 업로드하는 단계를 시작합니다.")
    @ApiResponse(responseCode = "202", description = "LDAP 업로드 요청 성공")
    @ApiResponse(responseCode = "400", description = "MANUAL 모드가 아님")
    @ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음")
    @PostMapping("/upload-to-ldap/{uploadId}")
    public ResponseEntity<ProcessingResponse> uploadToLdap(
            @PathVariable String uploadId
    ) {
        log.info("Upload to LDAP request: uploadId={}", uploadId);

        try {
            UUID uploadUUID = UUID.fromString(uploadId);
            UploadId uploadIdVO = new UploadId(uploadUUID);

            // 파일 조회
            Optional<com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile> uploadedFileOpt =
                    uploadedFileRepository.findById(uploadIdVO);

            if (uploadedFileOpt.isEmpty()) {
                log.warn("File not found: uploadId={}", uploadId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ProcessingResponse.fileNotFound(uploadUUID));
            }

            com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile uploadedFile = uploadedFileOpt.get();

            // MANUAL 모드 확인
            if (!uploadedFile.isManualMode()) {
                log.warn("File is not in MANUAL mode: uploadId={}, mode={}",
                        uploadId, uploadedFile.getProcessingMode());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ProcessingResponse.notManualMode(uploadUUID));
            }

            // TODO: UploadToLdapUseCase 호출 (Phase 19)
            // uploadToLdapUseCase.execute(new UploadToLdapCommand(uploadId));

            log.info("LDAP upload started: uploadId={}", uploadId);
            uploadedFile.markReadyForLdapUpload();
            uploadedFileRepository.save(uploadedFile);

            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ProcessingResponse.ldapUploadStarted(uploadUUID));

        } catch (IllegalArgumentException e) {
            log.error("Invalid uploadId format: {}", uploadId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ProcessingResponse.error(null, "LDAP_SAVING", "Invalid uploadId format"));
        } catch (Exception e) {
            log.error("Error uploading to LDAP: uploadId={}", uploadId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProcessingResponse.error(UUID.fromString(uploadId), "LDAP_SAVING", e.getMessage()));
        }
    }

    @Operation(summary = "파일 처리 상태 조회",
               description = "특정 파일의 현재 처리 상태를 조회합니다. AUTO/MANUAL 모든 모드에서 사용할 수 있습니다.")
    @ApiResponse(responseCode = "200", description = "상태 조회 성공")
    @ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음")
    @GetMapping("/status/{uploadId}")
    public ResponseEntity<ProcessingStatusResponse> getProcessingStatus(
            @PathVariable String uploadId
    ) {
        log.debug("Get processing status request: uploadId={}", uploadId);

        try {
            UUID uploadUUID = UUID.fromString(uploadId);
            UploadId uploadIdVO = new UploadId(uploadUUID);

            // 파일 조회
            Optional<com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile> uploadedFileOpt =
                    uploadedFileRepository.findById(uploadIdVO);

            if (uploadedFileOpt.isEmpty()) {
                log.warn("File not found: uploadId={}", uploadId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile uploadedFile = uploadedFileOpt.get();

            // TODO: 처리 상태를 데이터베이스에서 조회하여 응답 생성 (Phase 19)
            // 현재는 기본적인 응답만 구성
            ProcessingStatusResponse response = ProcessingStatusResponse.builder()
                    .uploadId(uploadUUID)
                    .fileName(uploadedFile.getFileNameValue())
                    .processingMode(uploadedFile.getProcessingMode().name())
                    .currentStage("UPLOAD_COMPLETED")  // TODO: Actual stage from database
                    .currentPercentage(5)              // TODO: Calculate based on actual stages
                    .uploadedAt(uploadedFile.getUploadedAt())
                    .lastUpdateAt(LocalDateTime.now())
                    .status("IN_PROGRESS")             // TODO: From database
                    .manualPauseAtStep(uploadedFile.getManualPauseAtStep())
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid uploadId format: {}", uploadId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error getting processing status: uploadId={}", uploadId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}