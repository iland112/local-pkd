package com.smartcoreinc.localpkd.fileupload.application.usecase;

import com.smartcoreinc.localpkd.fileupload.application.dto.UploadFileCommand;
import com.smartcoreinc.localpkd.fileupload.application.dto.UploadFileResponse;
import com.smartcoreinc.localpkd.fileupload.domain.model.*;
import com.smartcoreinc.localpkd.fileupload.domain.repository.UploadedFileRepository;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Upload File Use Case - 파일 업로드 유스케이스
 *
 * <p>파일 업로드 비즈니스 로직을 담당하는 Application Service입니다.
 * Domain Layer와 Infrastructure Layer 사이의 조정자 역할을 수행합니다.</p>
 *
 * <h3>책임 (Responsibilities)</h3>
 * <ul>
 *   <li>파일 업로드 요청 처리</li>
 *   <li>Value Objects 생성 및 검증</li>
 *   <li>중복 파일 검사</li>
 *   <li>Aggregate Root 생성 및 저장</li>
 *   <li>트랜잭션 관리</li>
 * </ul>
 *
 * <h3>비즈니스 규칙</h3>
 * <ol>
 *   <li>파일명, 해시, 크기는 필수값</li>
 *   <li>중복 파일(동일 해시)은 업로드 불가</li>
 *   <li>파일 업로드 성공 시 FileUploadedEvent 발행</li>
 * </ol>
 *
 * <h3>사용 예시 - Controller에서 호출</h3>
 * <pre>{@code
 * @RestController
 * @RequiredArgsConstructor
 * public class FileUploadController {
 *
 *     private final UploadFileUseCase uploadFileUseCase;
 *
 *     @PostMapping("/api/files/upload")
 *     public ResponseEntity<UploadFileResponse> uploadFile(
 *         @RequestParam("file") MultipartFile file
 *     ) throws IOException {
 *         // 1. 파일 해시 계산
 *         String fileHash = calculateFileHash(file);
 *
 *         // 2. Command 생성
 *         UploadFileCommand command = new UploadFileCommand(
 *             file.getOriginalFilename(),
 *             fileHash,
 *             file.getSize()
 *         );
 *
 *         try {
 *             // 3. Use Case 실행
 *             UploadFileResponse response = uploadFileUseCase.execute(command);
 *             return ResponseEntity.ok(response);
 *         } catch (DomainException e) {
 *             return ResponseEntity.badRequest().build();
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>처리 흐름</h3>
 * <pre>
 * 1. Command 수신
 * 2. Value Objects 생성 (FileName, FileHash, FileSize)
 *    → 검증 실패 시 DomainException 발생
 * 3. 중복 파일 검사 (FileHash로 조회)
 *    → 중복 발견 시 DomainException 발생
 * 4. UploadId 생성 (UUID 기반)
 * 5. UploadedFile Aggregate Root 생성
 *    → FileUploadedEvent 추가
 * 6. Repository에 저장
 *    → 도메인 이벤트 발행
 * 7. Response 생성 및 반환
 * </pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 * @see UploadFileCommand
 * @see UploadFileResponse
 * @see UploadedFile
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadFileUseCase {

    private final UploadedFileRepository repository;

    /**
     * 파일 업로드 실행
     *
     * <p>파일 업로드 요청을 처리하고 결과를 반환합니다.
     * 트랜잭션 내에서 실행되며, 예외 발생 시 롤백됩니다.</p>
     *
     * @param command 파일 업로드 커맨드
     * @return 파일 업로드 응답
     * @throws DomainException 검증 실패 또는 중복 파일인 경우
     */
    @Transactional
    public UploadFileResponse execute(UploadFileCommand command) {
        log.info("=== Upload file use case started ===");
        log.info("File name: {}", command.fileName());
        log.info("File hash: {}", command.fileHash().substring(0, 8) + "...");
        log.info("File size: {} bytes", command.fileSizeBytes());

        // 1. Value Objects 생성 (검증 포함)
        FileName fileName = FileName.of(command.fileName());
        FileHash fileHash = FileHash.of(command.fileHash());
        FileSize fileSize = FileSize.ofBytes(command.fileSizeBytes());

        log.debug("Value objects created successfully");

        // 2. 중복 파일 검사
        Optional<UploadedFile> existingFile = repository.findByFileHash(fileHash);
        if (existingFile.isPresent()) {
            log.warn("Duplicate file detected: hash={}", fileHash.getShortHash());
            throw new DomainException(
                    "DUPLICATE_FILE",
                    String.format("File with hash '%s' already exists (ID: %s)",
                            fileHash.getShortHash(),
                            existingFile.get().getId().getId())
            );
        }

        log.debug("No duplicate file found");

        // 3. Aggregate Root 생성
        UploadId uploadId = UploadId.newId();
        UploadedFile uploadedFile = UploadedFile.create(
                uploadId,
                fileName,
                fileHash,
                fileSize
        );

        log.debug("Aggregate root created: uploadId={}", uploadId.getId());

        // 4. 저장 (Domain Events 자동 발행)
        UploadedFile saved = repository.save(uploadedFile);

        log.info("File uploaded successfully: uploadId={}", saved.getId().getId());

        // 5. Response 생성
        return new UploadFileResponse(
                saved.getId().getId().toString(),
                saved.getFileNameValue(),
                saved.getFileHashValue(),
                saved.getFileSizeBytes(),
                saved.getFileSizeDisplay(),
                saved.getUploadedAt(),
                false  // not duplicate
        );
    }
}
