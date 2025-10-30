package com.smartcoreinc.localpkd.fileupload.infrastructure.web;

import com.smartcoreinc.localpkd.fileupload.application.response.ProcessingResponse;
import com.smartcoreinc.localpkd.fileupload.application.response.ProcessingStatusResponse;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.fileupload.domain.repository.UploadedFileRepository;
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
@Slf4j
@RestController
@RequestMapping("/api/processing")
@RequiredArgsConstructor
public class ProcessingController {

    private final UploadedFileRepository uploadedFileRepository;
    // TODO: 다음 Use Cases는 Phase 19에서 구현 예정
    // private final ParseFileUseCase parseFileUseCase;
    // private final ValidateCertificatesUseCase validateCertificatesUseCase;
    // private final UploadToLdapUseCase uploadToLdapUseCase;

    /**
     * 파일 파싱 시작 (MANUAL 모드)
     *
     * <p>사용자가 "파싱 시작" 버튼을 클릭했을 때 호출되는 엔드포인트입니다.
     * MANUAL 모드일 경우에만 처리됩니다.</p>
     *
     * <h3>요청</h3>
     * <pre>POST /api/processing/parse/{uploadId}</pre>
     *
     * <h3>응답 (202 Accepted)</h3>
     * <pre>{@code
     * {
     *   "uploadId": "550e8400-e29b-41d4-a716-446655440000",
     *   "step": "PARSING",
     *   "status": "IN_PROGRESS",
     *   "message": "파일 파싱을 시작했습니다.",
     *   "nextStep": "VALIDATION",
     *   "success": true,
     *   "errorMessage": null
     * }
     * }</pre>
     *
     * <h3>오류 응답 (400 Bad Request)</h3>
     * <pre>{@code
     * {
     *   "uploadId": "550e8400-e29b-41d4-a716-446655440000",
     *   "step": null,
     *   "status": "REJECTED",
     *   "message": "이 파일은 수동 처리 모드가 아닙니다.",
     *   "nextStep": null,
     *   "success": false,
     *   "errorMessage": "MANUAL 모드에서만 개별 단계를 트리거할 수 있습니다."
     * }
     * }</pre>
     *
     * @param uploadId 업로드 ID (UUID 문자열)
     * @return ResponseEntity<ProcessingResponse>
     */
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

            // TODO: ParseFileUseCase 호출 (Phase 19)
            // parseFileUseCase.execute(new ParseFileCommand(uploadId));

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

    /**
     * 인증서 검증 시작 (MANUAL 모드)
     *
     * <p>파일 파싱이 완료된 후 사용자가 "검증 시작" 버튼을 클릭했을 때 호출되는 엔드포인트입니다.
     * MANUAL 모드일 경우에만 처리됩니다.</p>
     *
     * <h3>요청</h3>
     * <pre>POST /api/processing/validate/{uploadId}</pre>
     *
     * <h3>응답 (202 Accepted)</h3>
     * <pre>{@code
     * {
     *   "uploadId": "550e8400-e29b-41d4-a716-446655440000",
     *   "step": "VALIDATION",
     *   "status": "IN_PROGRESS",
     *   "message": "인증서 검증을 시작했습니다.",
     *   "nextStep": "LDAP_SAVING",
     *   "success": true,
     *   "errorMessage": null
     * }
     * }</pre>
     *
     * @param uploadId 업로드 ID (UUID 문자열)
     * @return ResponseEntity<ProcessingResponse>
     */
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

    /**
     * LDAP 서버에 업로드 시작 (MANUAL 모드)
     *
     * <p>인증서 검증이 완료된 후 사용자가 "LDAP 업로드" 버튼을 클릭했을 때 호출되는 엔드포인트입니다.
     * MANUAL 모드일 경우에만 처리됩니다.</p>
     *
     * <h3>요청</h3>
     * <pre>POST /api/processing/upload-to-ldap/{uploadId}</pre>
     *
     * <h3>응답 (202 Accepted)</h3>
     * <pre>{@code
     * {
     *   "uploadId": "550e8400-e29b-41d4-a716-446655440000",
     *   "step": "LDAP_SAVING",
     *   "status": "IN_PROGRESS",
     *   "message": "LDAP 서버에 저장을 시작했습니다.",
     *   "nextStep": "COMPLETED",
     *   "success": true,
     *   "errorMessage": null
     * }
     * }</pre>
     *
     * @param uploadId 업로드 ID (UUID 문자열)
     * @return ResponseEntity<ProcessingResponse>
     */
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

    /**
     * 파일 처리 상태 조회
     *
     * <p>특정 파일의 현재 처리 상태를 조회합니다.
     * AUTO/MANUAL 모든 모드에서 사용할 수 있습니다.</p>
     *
     * <h3>요청</h3>
     * <pre>GET /api/processing/status/{uploadId}</pre>
     *
     * <h3>응답 (200 OK)</h3>
     * <pre>{@code
     * {
     *   "uploadId": "550e8400-e29b-41d4-a716-446655440000",
     *   "fileName": "icaopkd-002-complete-009410.ldif",
     *   "processingMode": "MANUAL",
     *   "currentStage": "PARSING_COMPLETED",
     *   "currentPercentage": 60,
     *   "uploadedAt": "2025-10-24T10:30:00",
     *   "lastUpdateAt": "2025-10-24T10:35:45",
     *   "parsingStartedAt": "2025-10-24T10:31:00",
     *   "parsingCompletedAt": "2025-10-24T10:33:30",
     *   "validationStartedAt": null,
     *   "validationCompletedAt": null,
     *   "ldapUploadStartedAt": null,
     *   "ldapUploadCompletedAt": null,
     *   "totalProcessingTimeSeconds": 345,
     *   "status": "IN_PROGRESS",
     *   "errorMessage": null,
     *   "manualPauseAtStep": "VALIDATION_STARTED"
     * }
     * }</pre>
     *
     * @param uploadId 업로드 ID (UUID 문자열)
     * @return ResponseEntity<ProcessingStatusResponse>
     */
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
