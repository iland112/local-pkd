package com.smartcoreinc.localpkd.fileupload.domain.model;

import com.smartcoreinc.localpkd.fileupload.domain.event.DuplicateFileDetectedEvent;
import com.smartcoreinc.localpkd.fileupload.domain.event.FileUploadedEvent;
import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * UploadedFile Aggregate Root 테스트
 *
 * @author SmartCore Inc.
 * @since 2025-10-18
 */
@DisplayName("UploadedFile Aggregate Root 테스트")
class UploadedFileTest {

    private static final String VALID_FILE_NAME = "icaopkd-002-complete-009410.ldif";
    private static final String VALID_HASH = "a1b2c3d4e5f67890123456789abcdef01234567890abcdef0123456789abcdef";
    private static final long VALID_SIZE_BYTES = 78_643_200L;  // 75 MB

    @Test
    @DisplayName("신규 파일 업로드 생성 - 정상")
    void create_WithValidData_Success() {
        // given
        UploadId uploadId = UploadId.newId();
        FileName fileName = FileName.of(VALID_FILE_NAME);
        FileHash fileHash = FileHash.of(VALID_HASH);
        FileSize fileSize = FileSize.ofBytes(VALID_SIZE_BYTES);

        // when
        UploadedFile uploadedFile = UploadedFile.create(
                uploadId, fileName, fileHash, fileSize
        );

        // then
        assertThat(uploadedFile).isNotNull();
        assertThat(uploadedFile.getId()).isEqualTo(uploadId);
        assertThat(uploadedFile.getFileName()).isEqualTo(fileName);
        assertThat(uploadedFile.getFileHash()).isEqualTo(fileHash);
        assertThat(uploadedFile.getFileSize()).isEqualTo(fileSize);
        assertThat(uploadedFile.isDuplicate()).isFalse();
        assertThat(uploadedFile.getOriginalUploadId()).isNull();
        assertThat(uploadedFile.getUploadedAt()).isNotNull();
    }

    @Test
    @DisplayName("신규 파일 업로드 생성 시 FileUploadedEvent 발행")
    void create_PublishesFileUploadedEvent() {
        // given
        UploadId uploadId = UploadId.newId();
        FileName fileName = FileName.of(VALID_FILE_NAME);
        FileHash fileHash = FileHash.of(VALID_HASH);
        FileSize fileSize = FileSize.ofBytes(VALID_SIZE_BYTES);

        // when
        UploadedFile uploadedFile = UploadedFile.create(
                uploadId, fileName, fileHash, fileSize
        );

        // then
        List<DomainEvent> events = uploadedFile.getDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(FileUploadedEvent.class);

        FileUploadedEvent event = (FileUploadedEvent) events.get(0);
        assertThat(event.uploadId()).isEqualTo(uploadId);
        assertThat(event.fileName()).isEqualTo(VALID_FILE_NAME);
        assertThat(event.fileHash()).isEqualTo(VALID_HASH);
        assertThat(event.fileSizeBytes()).isEqualTo(VALID_SIZE_BYTES);
    }

    @Test
    @DisplayName("중복 파일 업로드 생성 - 정상")
    void createDuplicate_WithValidData_Success() {
        // given
        UploadId duplicateId = UploadId.newId();
        UploadId originalId = UploadId.newId();
        FileName fileName = FileName.of(VALID_FILE_NAME);
        FileHash fileHash = FileHash.of(VALID_HASH);
        FileSize fileSize = FileSize.ofBytes(VALID_SIZE_BYTES);

        // when
        UploadedFile duplicateFile = UploadedFile.createDuplicate(
                duplicateId, fileName, fileHash, fileSize, originalId
        );

        // then
        assertThat(duplicateFile).isNotNull();
        assertThat(duplicateFile.getId()).isEqualTo(duplicateId);
        assertThat(duplicateFile.isDuplicate()).isTrue();
        assertThat(duplicateFile.getOriginalUploadId()).isEqualTo(originalId);
    }

    @Test
    @DisplayName("중복 파일 생성 시 DuplicateFileDetectedEvent 발행")
    void createDuplicate_PublishesDuplicateFileDetectedEvent() {
        // given
        UploadId duplicateId = UploadId.newId();
        UploadId originalId = UploadId.newId();
        FileName fileName = FileName.of(VALID_FILE_NAME);
        FileHash fileHash = FileHash.of(VALID_HASH);
        FileSize fileSize = FileSize.ofBytes(VALID_SIZE_BYTES);

        // when
        UploadedFile duplicateFile = UploadedFile.createDuplicate(
                duplicateId, fileName, fileHash, fileSize, originalId
        );

        // then
        List<DomainEvent> events = duplicateFile.getDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(DuplicateFileDetectedEvent.class);

        DuplicateFileDetectedEvent event = (DuplicateFileDetectedEvent) events.get(0);
        assertThat(event.duplicateUploadId()).isEqualTo(duplicateId);
        assertThat(event.originalUploadId()).isEqualTo(originalId);
        assertThat(event.fileName()).isEqualTo(VALID_FILE_NAME);
        assertThat(event.fileHash()).isEqualTo(VALID_HASH);
    }

    @Test
    @DisplayName("동일한 해시를 가진 파일 확인")
    void hasSameHashAs_WithSameHash_ReturnsTrue() {
        // given
        FileName fileName1 = FileName.of("file1.ldif");
        FileName fileName2 = FileName.of("file2.ldif");
        FileHash sameHash = FileHash.of(VALID_HASH);
        FileSize fileSize = FileSize.ofBytes(VALID_SIZE_BYTES);

        UploadedFile file1 = UploadedFile.create(
                UploadId.newId(), fileName1, sameHash, fileSize
        );
        UploadedFile file2 = UploadedFile.create(
                UploadId.newId(), fileName2, sameHash, fileSize
        );

        // when & then
        assertThat(file1.hasSameHashAs(file2)).isTrue();
    }

    @Test
    @DisplayName("다른 해시를 가진 파일 확인")
    void hasSameHashAs_WithDifferentHash_ReturnsFalse() {
        // given
        FileName fileName = FileName.of(VALID_FILE_NAME);
        FileHash hash1 = FileHash.of(VALID_HASH);
        FileHash hash2 = FileHash.of("b2c3d4e5f67890123456789abcdef01234567890abcdef01234567890abcdef0");
        FileSize fileSize = FileSize.ofBytes(VALID_SIZE_BYTES);

        UploadedFile file1 = UploadedFile.create(
                UploadId.newId(), fileName, hash1, fileSize
        );
        UploadedFile file2 = UploadedFile.create(
                UploadId.newId(), fileName, hash2, fileSize
        );

        // when & then
        assertThat(file1.hasSameHashAs(file2)).isFalse();
    }

    @Test
    @DisplayName("파일 크기 비교 - 더 큰 파일")
    void isLargerThan_WhenLarger_ReturnsTrue() {
        // given
        FileName fileName = FileName.of(VALID_FILE_NAME);
        FileHash fileHash = FileHash.of(VALID_HASH);

        UploadedFile largeFile = UploadedFile.create(
                UploadId.newId(), fileName, fileHash, FileSize.ofMegaBytes(100)
        );
        UploadedFile smallFile = UploadedFile.create(
                UploadId.newId(), fileName, fileHash, FileSize.ofMegaBytes(50)
        );

        // when & then
        assertThat(largeFile.isLargerThan(smallFile)).isTrue();
    }

    @Test
    @DisplayName("도메인 이벤트 정리")
    void clearDomainEvents_RemovesAllEvents() {
        // given
        UploadedFile uploadedFile = UploadedFile.create(
                UploadId.newId(),
                FileName.of(VALID_FILE_NAME),
                FileHash.of(VALID_HASH),
                FileSize.ofBytes(VALID_SIZE_BYTES)
        );

        assertThat(uploadedFile.getDomainEvents()).hasSize(1);

        // when
        uploadedFile.clearDomainEvents();

        // then
        assertThat(uploadedFile.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("파일명 문자열 값 반환")
    void getFileNameValue_ReturnsStringValue() {
        // given
        UploadedFile uploadedFile = UploadedFile.create(
                UploadId.newId(),
                FileName.of(VALID_FILE_NAME),
                FileHash.of(VALID_HASH),
                FileSize.ofBytes(VALID_SIZE_BYTES)
        );

        // when
        String fileNameValue = uploadedFile.getFileNameValue();

        // then
        assertThat(fileNameValue).isEqualTo(VALID_FILE_NAME);
    }

    @Test
    @DisplayName("파일 해시 문자열 값 반환")
    void getFileHashValue_ReturnsStringValue() {
        // given
        UploadedFile uploadedFile = UploadedFile.create(
                UploadId.newId(),
                FileName.of(VALID_FILE_NAME),
                FileHash.of(VALID_HASH),
                FileSize.ofBytes(VALID_SIZE_BYTES)
        );

        // when
        String fileHashValue = uploadedFile.getFileHashValue();

        // then
        assertThat(fileHashValue).isEqualTo(VALID_HASH);
    }

    @Test
    @DisplayName("파일 크기 바이트 값 반환")
    void getFileSizeBytes_ReturnsBytesValue() {
        // given
        UploadedFile uploadedFile = UploadedFile.create(
                UploadId.newId(),
                FileName.of(VALID_FILE_NAME),
                FileHash.of(VALID_HASH),
                FileSize.ofBytes(VALID_SIZE_BYTES)
        );

        // when
        long sizeBytes = uploadedFile.getFileSizeBytes();

        // then
        assertThat(sizeBytes).isEqualTo(VALID_SIZE_BYTES);
    }

    @Test
    @DisplayName("사람이 읽기 쉬운 파일 크기 표현 반환")
    void getFileSizeDisplay_ReturnsHumanReadableFormat() {
        // given
        UploadedFile uploadedFile = UploadedFile.create(
                UploadId.newId(),
                FileName.of(VALID_FILE_NAME),
                FileHash.of(VALID_HASH),
                FileSize.ofMegaBytes(75)
        );

        // when
        String sizeDisplay = uploadedFile.getFileSizeDisplay();

        // then
        assertThat(sizeDisplay).isEqualTo("75.0 MB");
    }
}
