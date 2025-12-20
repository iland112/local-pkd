package com.smartcoreinc.localpkd.fileupload.application.usecase;

import com.smartcoreinc.localpkd.fileupload.application.command.CheckDuplicateFileCommand;
import com.smartcoreinc.localpkd.fileupload.application.response.CheckDuplicateResponse;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileHash;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadedFile;
import com.smartcoreinc.localpkd.fileupload.domain.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 중복 파일 검사 Use Case
 *
 * <p>파일 업로드 전 중복 여부를 검사하는 Use Case입니다.</p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckDuplicateFileUseCase {

    private final UploadedFileRepository repository;

    @Transactional(readOnly = true)
    public CheckDuplicateResponse execute(CheckDuplicateFileCommand command) {
        log.debug("=== Duplicate file check started ===");
        log.debug("File name: {}", command.fileName());
        log.debug("File hash: {}", command.fileHash());

        try {
            command.validate();
            FileHash fileHash = FileHash.of(command.fileHash());
            Optional<UploadedFile> existingFile = repository.findByFileHash(fileHash);

            if (existingFile.isPresent()) {
                UploadedFile existing = existingFile.get();
                log.info("Duplicate file found: id={}", existing.getId().getId());

                return CheckDuplicateResponse.exactDuplicate(
                    existing.getId().getId(),
                    existing.getFileName().getValue(),
                    existing.getUploadedAt(),
                    existing.getVersion() != null ? existing.getVersion().getValue() : null,
                    existing.getStatus().getDisplayName()
                );
            }

            log.debug("No duplicate file found");
            return CheckDuplicateResponse.noDuplicate();

        } catch (Exception e) {
            log.error("Error during duplicate check", e);
            return CheckDuplicateResponse.noDuplicate();
        }
    }
}
