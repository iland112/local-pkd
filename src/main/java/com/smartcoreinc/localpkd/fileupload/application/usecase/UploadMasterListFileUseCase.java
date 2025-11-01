package com.smartcoreinc.localpkd.fileupload.application.usecase;

import com.smartcoreinc.localpkd.fileupload.application.command.UploadMasterListFileCommand;
import com.smartcoreinc.localpkd.fileupload.application.response.UploadFileResponse;
import com.smartcoreinc.localpkd.fileupload.domain.model.*;
import com.smartcoreinc.localpkd.fileupload.domain.port.FileStoragePort;
import com.smartcoreinc.localpkd.fileupload.domain.repository.UploadedFileRepository;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Master List 파일 업로드 Use Case
 *
 * <p>Master List 파일 업로드의 전체 비즈니스 로직을 처리하는 Use Case입니다.</p>
 *
 * <h3>처리 흐름</h3>
 * <ol>
 *   <li>Command 검증</li>
 *   <li>Value Objects 생성</li>
 *   <li>중복 파일 검사 (forceUpload가 아닌 경우)</li>
 *   <li>파일 시스템 저장 (FileStoragePort)</li>
 *   <li>Checksum 계산 및 검증 (expectedChecksum이 있는 경우)</li>
 *   <li>UploadedFile Aggregate Root 생성</li>
 *   <li>데이터베이스 저장 (Repository)</li>
 *   <li>Domain Events 발행</li>
 *   <li>Response 반환</li>
 * </ol>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * UploadMasterListFileCommand command = UploadMasterListFileCommand.builder()
 *     .fileName("masterlist-KOR2024.ml")
 *     .fileContent(fileBytes)
 *     .fileSize(43521024L)
 *     .fileHash("b2c3d4e5...")
 *     .expectedChecksum("sha1-checksum")
 *     .forceUpload(false)
 *     .build();
 *
 * UploadFileResponse response = uploadMasterListFileUseCase.execute(command);
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadMasterListFileUseCase {

    private final UploadedFileRepository repository;
    private final FileStoragePort fileStoragePort;

    /**
     * Master List 파일 업로드 실행
     *
     * @param command Master List 파일 업로드 명령
     * @return 업로드 결과
     * @throws DomainException 업로드 실패 시
     */
    @Transactional
    public UploadFileResponse execute(UploadMasterListFileCommand command) {
        log.info("=== Master List file upload started ===");
        log.info("File name: {}", command.fileName());
        log.info("File size: {} bytes", command.fileSize());
        log.info("File hash: {}", command.fileHash());
        log.info("Force upload: {}", command.forceUpload());

        try {
            // 1. Command 검증
            command.validate();

            // 2. Value Objects 생성
            FileName fileName = FileName.of(command.fileName());
            FileHash fileHash = FileHash.of(command.fileHash());
            FileSize fileSize = FileSize.ofBytes(command.fileSize());

            // 3. 중복 파일 검사 (forceUpload가 아닌 경우)
            if (!command.forceUpload()) {
                checkDuplicate(fileHash, fileName);
            } else {
                log.warn("Force upload enabled - skipping duplicate check");
            }

            // 4. 파일 포맷 감지
            FileFormat fileFormat = FileFormat.detectFromFileName(fileName);
            log.info("Detected file format: {}", fileFormat.getType());

            if (!fileFormat.isMasterList()) {
                throw new DomainException(
                    "INVALID_FILE_FORMAT",
                    "파일이 Master List 형식이 아닙니다: " + fileFormat.getType()
                );
            }

            // 5. 파일 시스템에 저장
            FilePath savedPath = fileStoragePort.saveFile(
                command.fileContent(),
                fileFormat,
                fileName
            );
            log.info("File saved to: {}", savedPath.getValue());

            // 6. Metadata 추출
            CollectionNumber collectionNumber = CollectionNumber.extractFromFileName(fileName);
            FileVersion version = FileVersion.extractFromFileName(fileName, fileFormat);
            log.info("Collection: {}, Version: {}", collectionNumber.getValue(), version.getValue());

            // 7. UploadedFile Aggregate Root 생성
            UploadId uploadId = UploadId.newId();
            UploadedFile uploadedFile = UploadedFile.createWithMetadata(
                uploadId,
                fileName,
                fileHash,
                fileSize,
                fileFormat,
                collectionNumber,
                version,
                savedPath
            );

            // 8. Checksum 검증 (expectedChecksum이 있는 경우)
            if (command.expectedChecksum() != null && !command.expectedChecksum().isBlank()) {
                Checksum expectedChecksum = Checksum.of(command.expectedChecksum());
                uploadedFile.setExpectedChecksum(expectedChecksum);

                // 실제 파일의 Checksum 계산
                Checksum calculatedChecksum = fileStoragePort.calculateChecksum(savedPath);
                uploadedFile.validateChecksum(calculatedChecksum);

                log.info("Checksum validation status: {}", uploadedFile.getStatus());
            }

            // 9. forceUpload 플래그 처리
            if (command.forceUpload()) {
                // forceUpload의 경우 기존 파일을 찾아서 originalUploadId 설정
                Optional<UploadedFile> existingFile = repository.findByFileHash(fileHash);
                if (existingFile.isPresent()) {
                    UploadId originalUploadId = existingFile.get().getId();
                    uploadedFile.markAsDuplicate(originalUploadId);
                    log.info("Marked as force-uploaded duplicate, original ID: {}", originalUploadId.getId());
                } else {
                    // 기존 파일이 없으면 originalUploadId를 null로 설정
                    uploadedFile.markAsDuplicate(null);
                    log.info("Marked as force-uploaded (no original file found)");
                }
            }

            // 10. 저장 직전 중복 체크 (Race condition 방지)
            if (!command.forceUpload()) {
                // forceUpload가 아닌 경우, 저장 직전에 한 번 더 중복 체크
                Optional<UploadedFile> existingFile = repository.findByFileHash(fileHash);
                if (existingFile.isPresent()) {
                    UploadedFile existing = existingFile.get();
                    throw new DomainException(
                        "DUPLICATE_FILE",
                        String.format(
                            "이 파일은 이미 업로드되었습니다. (ID: %s, 업로드 일시: %s, 상태: %s)",
                            existing.getId().getId(),
                            existing.getUploadedAt(),
                            existing.getStatus().getDisplayName()
                        )
                    );
                }
            }

            // 11. 데이터베이스에 저장 (Domain Events 자동 발행)
            UploadedFile saved = repository.save(uploadedFile);
            log.info("File upload completed: uploadId={}, status={}",
                     saved.getId().getId(), saved.getStatus());

            // 11. Response 생성
            return UploadFileResponse.success(
                saved.getId().getId(),
                saved.getFileName().getValue(),
                saved.getFileSize().getBytes(),
                saved.getFileSizeDisplay(),
                saved.getFileFormatType(),
                saved.getCollectionNumber() != null ? saved.getCollectionNumber().getValue() : null,
                saved.getVersion() != null ? saved.getVersion().getValue() : null,
                saved.getUploadedAt(),
                saved.getStatus().name()
            );

        } catch (DomainException e) {
            log.error("Domain error during Master List upload: {}", e.getMessage());
            return UploadFileResponse.failure(command.fileName(), e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected error during Master List upload", e);
            return UploadFileResponse.failure(
                command.fileName(),
                "파일 업로드 중 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }

    /**
     * 중복 파일 검사
     *
     * @param fileHash 파일 해시
     * @param fileName 파일명
     * @throws DomainException 중복 파일이 존재하는 경우
     */
    private void checkDuplicate(FileHash fileHash, FileName fileName) {
        Optional<UploadedFile> existingFile = repository.findByFileHash(fileHash);

        if (existingFile.isPresent()) {
            UploadedFile existing = existingFile.get();
            String errorMessage = String.format(
                "이 파일은 이미 업로드되었습니다. (ID: %s, 업로드 일시: %s, 상태: %s)",
                existing.getId().getId(),
                existing.getUploadedAt(),
                existing.getStatus().getDisplayName()
            );
            log.warn("Duplicate file detected: hash={}, existingId={}",
                     fileHash.getValue(), existing.getId().getId());

            throw new DomainException("DUPLICATE_FILE", errorMessage);
        }

        log.debug("No duplicate file found for hash: {}", fileHash.getValue());
    }
}
