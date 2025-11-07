package com.smartcoreinc.localpkd.fileupload.domain.model;

import com.smartcoreinc.localpkd.fileupload.domain.event.DuplicateFileDetectedEvent;
import com.smartcoreinc.localpkd.fileupload.domain.event.FileUploadedEvent;
import com.smartcoreinc.localpkd.shared.domain.AggregateRoot;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Uploaded File - 업로드된 파일 Aggregate Root
 *
 * <p>파일 업로드 도메인의 핵심 엔티티입니다.
 * 파일 업로드, 중복 검사, 메타데이터 관리 등의 비즈니스 로직을 담당합니다.</p>
 *
 * <h3>책임 (Responsibilities)</h3>
 * <ul>
 *   <li>파일 업로드 수명 주기 관리</li>
 *   <li>파일 메타데이터 보관 (파일명, 해시, 크기)</li>
 *   <li>중복 파일 감지</li>
 *   <li>도메인 이벤트 발행 (FileUploadedEvent, DuplicateFileDetectedEvent)</li>
 * </ul>
 *
 * <h3>불변 규칙 (Invariants)</h3>
 * <ul>
 *   <li>UploadId는 변경 불가 (영속성 식별자)</li>
 *   <li>FileName, FileHash, FileSize는 생성 시 검증됨</li>
 *   <li>uploadedAt은 생성 시점에 자동 설정</li>
 * </ul>
 *
 * <h3>사용 예시 - 신규 파일 업로드</h3>
 * <pre>{@code
 * // 1. Value Objects 생성
 * UploadId uploadId = UploadId.newId();
 * FileName fileName = FileName.of("icaopkd-002-complete-009410.ldif");
 * FileHash fileHash = FileHash.of("a1b2c3d4...");
 * FileSize fileSize = FileSize.ofMegaBytes(75);
 *
 * // 2. Aggregate Root 생성
 * UploadedFile uploadedFile = UploadedFile.create(
 *     uploadId,
 *     fileName,
 *     fileHash,
 *     fileSize
 * );
 *
 * // 3. Repository에 저장 (이벤트 발행)
 * uploadedFileRepository.save(uploadedFile);
 * eventBus.publishAll(uploadedFile.getDomainEvents());
 * uploadedFile.clearDomainEvents();
 * }</pre>
 *
 * <h3>사용 예시 - 중복 파일 감지</h3>
 * <pre>{@code
 * // 1. 파일 해시로 기존 파일 조회
 * Optional<UploadedFile> existing = repository.findByFileHash(fileHash);
 *
 * // 2. 중복 파일 감지
 * if (existing.isPresent()) {
 *     UploadedFile existingFile = existing.get();
 *     UploadedFile duplicate = UploadedFile.createDuplicate(
 *         UploadId.newId(),
 *         fileName,
 *         fileHash,
 *         fileSize,
 *         existingFile.getId()
 *     );
 *
 *     // DuplicateFileDetectedEvent 발행됨
 * }
 * }</pre>
 *
 * <h3>사용 예시 - 이벤트 처리</h3>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class FileUploadEventHandler {
 *
 *     @EventListener
 *     public void handleFileUploaded(FileUploadedEvent event) {
 *         log.info("File uploaded: {}", event.uploadId());
 *         // 파일 파싱 트리거
 *     }
 *
 *     @EventListener
 *     public void handleDuplicateDetected(DuplicateFileDetectedEvent event) {
 *         log.warn("Duplicate file: {} (original: {})",
 *             event.duplicateUploadId(), event.originalUploadId());
 *         // 중복 파일 처리 로직
 *     }
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-18
 * @see AggregateRoot
 * @see UploadId
 * @see FileName
 * @see FileHash
 * @see FileSize
 */
@Entity
@Table(name = "uploaded_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadedFile extends AggregateRoot<UploadId> {

    /**
     * 업로드 ID (Primary Key)
     */
    @EmbeddedId
    private UploadId id;

    /**
     * 파일명
     */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "file_name", nullable = false))
    private FileName fileName;

    /**
     * 파일 해시 (SHA-256)
     */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "file_hash", nullable = false, unique = true))
    private FileHash fileHash;

    /**
     * 파일 크기
     */
    @Embedded
    @AttributeOverride(name = "bytes", column = @Column(name = "file_size_bytes", nullable = false))
    private FileSize fileSize;

    /**
     * 업로드 일시
     */
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    /**
     * 중복 파일 여부
     */
    @Column(name = "is_duplicate", nullable = false)
    private boolean isDuplicate;

    /**
     * 원본 파일 ID (중복 파일인 경우)
     */
    @Embedded
    @AttributeOverride(name = "id", column = @Column(name = "original_upload_id"))
    private UploadId originalUploadId;

    // ===== 확장 필드 (Phase 4.1) =====

    /**
     * Collection 번호 (001, 002, 003)
     */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "collection_number", length = 10))
    private CollectionNumber collectionNumber;

    /**
     * 파일 버전
     */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "version", length = 50))
    private FileVersion version;

    /**
     * 파일 포맷
     */
    @Column(name = "file_format", length = 50)
    private String fileFormatType;  // FileFormat.Type의 name() 저장

    /**
     * 파일 저장 경로
     */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "local_file_path", length = 500))
    private FilePath filePath;

    /**
     * 예상 체크섬 (SHA-1, 사용자 제공)
     */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "expected_checksum", length = 40))
    private Checksum expectedChecksum;

    /**
     * 계산된 체크섬 (SHA-1, 서버 계산)
     */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "calculated_checksum", length = 40))
    private Checksum calculatedChecksum;

    /**
     * 업로드 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UploadStatus status;

    /**
     * 신규 버전 여부
     */
    @Column(name = "is_newer_version")
    private Boolean isNewerVersion;

    /**
     * 오류 메시지
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // ===== Dual Mode Processing 필드 (Phase 18) =====

    /**
     * 파일 처리 방식 (AUTO: 자동 처리, MANUAL: 수동 처리)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_mode", nullable = false, length = 20)
    private ProcessingMode processingMode = ProcessingMode.AUTO;

    /**
     * MANUAL 모드에서 사용자 액션 대기 중인 단계
     *
     * <p>MANUAL 모드일 때만 사용됩니다.
     * 값: UPLOAD_COMPLETED, PARSING_STARTED, PARSING_COMPLETED, VALIDATION_STARTED, VALIDATION_COMPLETED, LDAP_SAVING_STARTED</p>
     */
    @Column(name = "manual_pause_at_step", length = 50)
    private String manualPauseAtStep;

    /**
     * 생성자 (protected)
     *
     * <p>직접 호출하지 말고 정적 팩토리 메서드를 사용하세요.</p>
     *
     * @param id 업로드 ID
     * @param fileName 파일명
     * @param fileHash 파일 해시
     * @param fileSize 파일 크기
     * @param isDuplicate 중복 여부
     * @param originalUploadId 원본 파일 ID
     */
    protected UploadedFile(
            UploadId id,
            FileName fileName,
            FileHash fileHash,
            FileSize fileSize,
            boolean isDuplicate,
            UploadId originalUploadId
    ) {
        this.id = id;
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.fileSize = fileSize;
        this.uploadedAt = LocalDateTime.now();
        this.isDuplicate = isDuplicate;
        this.originalUploadId = originalUploadId;
    }

    /**
     * 신규 파일 업로드 생성 (Factory Method) - 기본 버전
     *
     * <p>새로운 파일이 업로드될 때 사용합니다.
     * {@link FileUploadedEvent}를 발행합니다.</p>
     *
     * @param id 업로드 ID
     * @param fileName 파일명
     * @param fileHash 파일 해시
     * @param fileSize 파일 크기
     * @return 업로드된 파일 객체
     */
    public static UploadedFile create(
            UploadId id,
            FileName fileName,
            FileHash fileHash,
            FileSize fileSize
    ) {
        UploadedFile uploadedFile = new UploadedFile(
                id,
                fileName,
                fileHash,
                fileSize,
                false,  // not duplicate
                null    // no original
        );

        // 기본 상태 설정
        uploadedFile.status = UploadStatus.RECEIVED;
        uploadedFile.isNewerVersion = false;

        // 도메인 이벤트 발행
        uploadedFile.addDomainEvent(new FileUploadedEvent(
                id,
                fileName.getValue(),
                fileHash.getValue(),
                fileSize.getBytes(),
                uploadedFile.uploadedAt
        ));

        return uploadedFile;
    }

    /**
     * 신규 파일 업로드 생성 (Factory Method) - 확장 버전
     *
     * <p>모든 메타데이터를 포함한 완전한 파일 업로드 생성.
     * Legacy 시스템을 대체하는 메인 Factory Method입니다.</p>
     *
     * @param id 업로드 ID
     * @param fileName 파일명
     * @param fileHash 파일 해시 (SHA-256)
     * @param fileSize 파일 크기
     * @param fileFormat 파일 포맷
     * @param collectionNumber Collection 번호
     * @param version 파일 버전
     * @param filePath 파일 저장 경로
     * @return 업로드된 파일 객체
     */
    public static UploadedFile createWithMetadata(
            UploadId id,
            FileName fileName,
            FileHash fileHash,
            FileSize fileSize,
            FileFormat fileFormat,
            CollectionNumber collectionNumber,
            FileVersion version,
            FilePath filePath
    ) {
        return createWithMetadata(
            id, fileName, fileHash, fileSize, fileFormat,
            collectionNumber, version, filePath, ProcessingMode.AUTO
        );
    }

    /**
     * 신규 파일 업로드 생성 (Factory Method) - 확장 버전 + ProcessingMode
     *
     * <p>모든 메타데이터와 처리 방식을 포함한 완전한 파일 업로드 생성.
     * AUTO/MANUAL 모드를 지정할 수 있습니다.</p>
     *
     * @param id 업로드 ID
     * @param fileName 파일명
     * @param fileHash 파일 해시 (SHA-256)
     * @param fileSize 파일 크기
     * @param fileFormat 파일 포맷
     * @param collectionNumber Collection 번호
     * @param version 파일 버전
     * @param filePath 파일 저장 경로
     * @param processingMode 파일 처리 방식 (AUTO 또는 MANUAL)
     * @return 업로드된 파일 객체
     */
    public static UploadedFile createWithMetadata(
            UploadId id,
            FileName fileName,
            FileHash fileHash,
            FileSize fileSize,
            FileFormat fileFormat,
            CollectionNumber collectionNumber,
            FileVersion version,
            FilePath filePath,
            ProcessingMode processingMode
    ) {
        UploadedFile uploadedFile = new UploadedFile(
                id,
                fileName,
                fileHash,
                fileSize,
                false,  // not duplicate
                null    // no original
        );

        // 확장 필드 설정
        uploadedFile.collectionNumber = collectionNumber;
        uploadedFile.version = version;
        uploadedFile.fileFormatType = fileFormat.getType().name();
        uploadedFile.filePath = filePath;
        uploadedFile.status = UploadStatus.RECEIVED;
        uploadedFile.isNewerVersion = false;
        uploadedFile.processingMode = processingMode;

        // MANUAL 모드일 경우 초기 pause step 설정
        if (processingMode.isManual()) {
            uploadedFile.manualPauseAtStep = "UPLOAD_COMPLETED";
        }

        // 도메인 이벤트 발행
        uploadedFile.addDomainEvent(new FileUploadedEvent(
                id,
                fileName.getValue(),
                fileHash.getValue(),
                fileSize.getBytes(),
                uploadedFile.uploadedAt,
                processingMode
        ));

        return uploadedFile;
    }

    /**
     * 중복 파일 업로드 생성 (Factory Method)
     *
     * <p>기존 파일과 동일한 해시를 가진 파일이 업로드될 때 사용합니다.
     * {@link DuplicateFileDetectedEvent}를 발행합니다.</p>
     *
     * @param id 업로드 ID (새 파일)
     * @param fileName 파일명 (새 파일)
     * @param fileHash 파일 해시 (동일)
     * @param fileSize 파일 크기 (동일)
     * @param originalUploadId 원본 파일 ID
     * @return 중복 파일 객체
     */
    public static UploadedFile createDuplicate(
            UploadId id,
            FileName fileName,
            FileHash fileHash,
            FileSize fileSize,
            UploadId originalUploadId
    ) {
        UploadedFile duplicateFile = new UploadedFile(
                id,
                fileName,
                fileHash,
                fileSize,
                true,  // duplicate
                originalUploadId
        );

        // 도메인 이벤트 발행
        duplicateFile.addDomainEvent(new DuplicateFileDetectedEvent(
                id,
                originalUploadId,
                fileName.getValue(),
                fileHash.getValue(),
                duplicateFile.uploadedAt
        ));

        return duplicateFile;
    }

    /**
     * 파일 해시 일치 여부 확인
     *
     * <p>다른 파일과 해시가 동일한지 확인합니다.</p>
     *
     * @param other 비교할 파일
     * @return 해시가 동일하면 true
     */
    public boolean hasSameHashAs(UploadedFile other) {
        return this.fileHash.equals(other.fileHash);
    }

    /**
     * 파일 크기가 다른 파일보다 큰지 확인
     *
     * @param other 비교할 파일
     * @return 더 크면 true
     */
    public boolean isLargerThan(UploadedFile other) {
        return this.fileSize.isLargerThan(other.fileSize);
    }

    /**
     * AggregateRoot ID 반환
     *
     * @return 업로드 ID
     */
    @Override
    public UploadId getId() {
        return id;
    }

    /**
     * 파일명 문자열 반환
     *
     * @return 파일명
     */
    public String getFileNameValue() {
        return fileName.getValue();
    }

    /**
     * 파일 해시 문자열 반환
     *
     * @return 파일 해시
     */
    public String getFileHashValue() {
        return fileHash.getValue();
    }

    /**
     * 파일 크기 바이트 반환
     *
     * @return 파일 크기 (바이트)
     */
    public long getFileSizeBytes() {
        return fileSize.getBytes();
    }

    /**
     * 사용자 친화적인 파일 크기 문자열
     *
     * @return 파일 크기 문자열 (예: "10.5 MB")
     */
    public String getFileSizeDisplay() {
        return fileSize.toHumanReadable();
    }

    // ===== 확장 비즈니스 메서드 (Phase 4.1) =====

    /**
     * FileFormat 객체 반환
     *
     * @return FileFormat (null 가능)
     */
    public FileFormat getFileFormat() {
        if (fileFormatType == null) {
            return null;
        }
        return FileFormat.of(FileFormat.Type.valueOf(fileFormatType));
    }

    /**
     * 파일 포맷 표시명 반환
     *
     * @return 포맷 표시명 (예: "eMRTD Complete LDIF")
     */
    public String getFileFormatDisplay() {
        FileFormat format = getFileFormat();
        return format != null ? format.getDisplayName() : "Unknown";
    }

    /**
     * Collection 번호 문자열 반환
     *
     * @return Collection 번호 (예: "002")
     */
    public String getCollectionNumberValue() {
        return collectionNumber != null ? collectionNumber.getValue() : null;
    }

    /**
     * 버전 문자열 반환
     *
     * @return 버전 (예: "009410")
     */
    public String getVersionValue() {
        return version != null ? version.getValue() : null;
    }

    /**
     * 파일 경로 문자열 반환
     *
     * @return 파일 경로
     */
    public String getFilePathValue() {
        return filePath != null ? filePath.getValue() : null;
    }

    /**
     * 예상 체크섬 설정
     *
     * @param expectedChecksum 예상 체크섬 (SHA-1)
     */
    public void setExpectedChecksum(Checksum expectedChecksum) {
        this.expectedChecksum = expectedChecksum;
    }

    /**
     * 체크섬 검증
     *
     * <p>예상 체크섬과 계산된 체크섬을 비교하여 상태를 업데이트합니다.</p>
     *
     * @param calculated 계산된 체크섬 (SHA-1)
     */
    public void validateChecksum(Checksum calculated) {
        this.calculatedChecksum = calculated;
        this.status = UploadStatus.VALIDATING;

        if (expectedChecksum != null && expectedChecksum.doesNotMatch(calculated.getValue())) {
            this.status = UploadStatus.CHECKSUM_INVALID;
            this.errorMessage = String.format(
                "Checksum mismatch: expected=%s, calculated=%s",
                expectedChecksum.getValue(),
                calculated.getValue()
            );
        } else {
            this.status = UploadStatus.VALIDATED;
        }
    }

    /**
     * 중복 파일로 표시
     *
     * <p><b>중요</b>: ForceUpload로 중복 파일이 업로드된 경우,
     * createWithMetadata()에서 이미 FileUploadedEvent가 발행되므로
     * 여기서는 DuplicateFileDetectedEvent만 발행합니다.
     * (FileUploadedEvent를 중복 발행하면 파싱이 2번 실행되어 데이터베이스 충돌 발생)</p>
     *
     * @param originalUploadId 원본 업로드 ID
     */
    public void markAsDuplicate(UploadId originalUploadId) {
        this.isDuplicate = true;
        this.originalUploadId = originalUploadId;
        this.status = UploadStatus.DUPLICATE_DETECTED;

        // 중복 파일 감지 이벤트만 발행
        // FileUploadedEvent는 createWithMetadata()에서 이미 발행됨
        addDomainEvent(new DuplicateFileDetectedEvent(
                this.id,
                originalUploadId,
                this.fileName.getValue(),
                this.fileHash.getValue(),
                LocalDateTime.now()
        ));
    }

    /**
     * 신규 버전으로 표시
     *
     * @param replacedFileId 대체된 파일 ID
     */
    public void markAsNewerVersion(UploadId replacedFileId) {
        this.isNewerVersion = true;
        // replacedFileId는 별도 관리 가능 (현재는 필드 없음)
    }

    /**
     * 상태 변경
     *
     * <p>종료 상태(terminal)에서는 변경할 수 없습니다.</p>
     *
     * @param newStatus 새로운 상태
     * @throws com.smartcoreinc.localpkd.shared.exception.DomainException 종료 상태에서 변경 시도 시
     */
    public void changeStatus(UploadStatus newStatus) {
        if (this.status != null && this.status.isTerminal()) {
            throw new com.smartcoreinc.localpkd.shared.exception.DomainException(
                "INVALID_STATUS_TRANSITION",
                String.format("Cannot change status from terminal state: %s", this.status)
            );
        }

        if (!this.status.canTransitionTo(newStatus)) {
            throw new com.smartcoreinc.localpkd.shared.exception.DomainException(
                "INVALID_STATUS_TRANSITION",
                String.format("Cannot transition from %s to %s", this.status, newStatus)
            );
        }

        this.status = newStatus;
    }

    /**
     * 오류 처리
     *
     * @param errorMessage 오류 메시지
     */
    public void fail(String errorMessage) {
        this.status = UploadStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    /**
     * 업로드 완료
     */
    public void complete() {
        if (this.status != null && this.status.canTransitionTo(UploadStatus.COMPLETED)) {
            this.status = UploadStatus.COMPLETED;
        }
    }

    /**
     * 버전 비교
     *
     * @param other 비교 대상 파일
     * @return this가 더 최신이면 true
     */
    public boolean hasNewerVersionThan(UploadedFile other) {
        if (this.version == null || other.version == null) {
            return false;
        }
        return this.version.isNewerThan(other.version);
    }

    /**
     * 동일 Collection 여부 확인
     *
     * @param other 비교 대상 파일
     * @return 동일 Collection이면 true
     */
    public boolean hasSameCollectionAs(UploadedFile other) {
        if (this.collectionNumber == null || other.collectionNumber == null) {
            return false;
        }
        return this.collectionNumber.equals(other.collectionNumber);
    }

    // ===== Dual Mode Processing 메서드 (Phase 18) =====

    /**
     * 처리 모드 조회
     *
     * @return ProcessingMode (AUTO 또는 MANUAL)
     */
    public ProcessingMode getProcessingMode() {
        return processingMode != null ? processingMode : ProcessingMode.AUTO;
    }

    /**
     * AUTO 모드 여부 확인
     *
     * @return AUTO 모드면 true
     */
    public boolean isAutoMode() {
        return getProcessingMode().isAuto();
    }

    /**
     * MANUAL 모드 여부 확인
     *
     * @return MANUAL 모드면 true
     */
    public boolean isManualMode() {
        return getProcessingMode().isManual();
    }

    /**
     * MANUAL 모드에서 사용자 액션 대기 중인 단계 조회
     *
     * <p>MANUAL 모드일 때만 의미가 있습니다.</p>
     *
     * @return 대기 중인 단계 (예: "UPLOAD_COMPLETED", "PARSING_COMPLETED")
     */
    public String getManualPauseAtStep() {
        return manualPauseAtStep;
    }

    /**
     * MANUAL 모드에서 다음 단계로 진행
     *
     * <p>MANUAL 모드에서만 사용됩니다.
     * 파싱/검증/LDAP 등록 등의 단계 완료 후 사용자 액션을 기다리기 위해 호출됩니다.</p>
     *
     * @param nextStep 다음 대기 단계 (예: "PARSING_STARTED")
     */
    public void setManualPauseAtStep(String nextStep) {
        if (this.isManualMode()) {
            this.manualPauseAtStep = nextStep;
        }
    }

    /**
     * MANUAL 모드 초기화
     *
     * <p>파일 업로드 직후 MANUAL 모드를 설정할 때 호출됩니다.</p>
     */
    public void initializeManualMode() {
        if (this.isManualMode()) {
            this.manualPauseAtStep = "UPLOAD_COMPLETED";
        }
    }

    /**
     * MANUAL 모드에서 파싱 준비 상태로 전환
     *
     * <p>사용자가 "파싱 시작" 버튼을 클릭했을 때 호출됩니다.</p>
     */
    public void markReadyForParsing() {
        if (this.isManualMode()) {
            this.manualPauseAtStep = "PARSING_STARTED";
        }
    }

    /**
     * MANUAL 모드에서 검증 준비 상태로 전환
     *
     * <p>파싱 완료 후 사용자가 "검증 시작" 버튼을 클릭했을 때 호출됩니다.</p>
     */
    public void markReadyForValidation() {
        if (this.isManualMode()) {
            this.manualPauseAtStep = "VALIDATION_STARTED";
        }
    }

    /**
     * MANUAL 모드에서 LDAP 업로드 준비 상태로 전환
     *
     * <p>검증 완료 후 사용자가 "LDAP 업로드" 버튼을 클릭했을 때 호출됩니다.</p>
     */
    public void markReadyForLdapUpload() {
        if (this.isManualMode()) {
            this.manualPauseAtStep = "LDAP_SAVING_STARTED";
        }
    }
}
