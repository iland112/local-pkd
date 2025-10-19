# DDD 기반 완전 리팩토링 전략

**문서 버전**: 1.0
**작성일**: 2025-10-19
**접근 방식**: Legacy Code → DDD Clean Architecture (Adapter 없이)

---

## 목차

1. [전략 개요](#전략-개요)
2. [Legacy 코드 분석](#legacy-코드-분석)
3. [DDD 설계](#ddd-설계)
4. [구현 로드맵](#구현-로드맵)
5. [데이터베이스 전략](#데이터베이스-전략)

---

## 전략 개요

### 핵심 원칙

1. **Adapter 패턴 사용 안 함**: Legacy와 DDD 사이 어댑터 계층 제거
2. **완전한 DDD 재작성**: Legacy 코드의 비즈니스 로직을 DDD로 이전
3. **Clean Architecture**: Domain → Application → Infrastructure 계층 분리
4. **CQRS 적용**: Command(업로드) / Query(조회) 분리
5. **Rich Domain Model**: 비즈니스 로직을 Domain Layer에 집중

### 마이그레이션 접근

- **Big Bang 방식 아님**: 기능별 점진적 전환
- **Feature Flag 사용**: 새 DDD 엔드포인트와 Legacy 엔드포인트 병행 운영
- **데이터베이스 통합**: `file_upload_history` 삭제, `uploaded_file` 테이블에 필드 추가

---

## Legacy 코드 분석

### 1. Legacy 기능 목록

#### 1.1 파일 업로드 기능

**LdifUploadController** (`/ldif/upload`):
```java
// 현재 기능
- 파일 검증 (확장자, 크기, 내용)
- SHA-256 해시 계산
- 중복 검사 (파일 해시 기반)
- 파일 포맷 감지 (CSCA_COMPLETE, CSCA_DELTA, EMRTD_COMPLETE, EMRTD_DELTA)
- Collection 번호 추출 (001, 002)
- 버전 추출 (009410 등)
- 파일 시스템 저장
- 업로드 이력 저장
- 업로드 히스토리 페이지로 리다이렉트
```

**MasterListUploadController** (`/masterlist/upload`):
```java
// 현재 기능
- ML 파일 검증 (.ml 확장자)
- SHA-256 해시 계산
- 중복 검사
- 버전 추출 (July2025 형식)
- Collection 기본값 002
- ML_SIGNED_CMS 포맷
- 파일 시스템 저장
- 업로드 이력 저장
```

#### 1.2 업로드 이력 조회 기능

**UploadHistoryController** (`/upload-history`):
```java
// 현재 기능
- 페이징 조회 (20개씩)
- 검색 (파일명, 버전, Collection)
- 필터링 (Status, FileFormat)
- ID 하이라이트
- 성공/오류 메시지 표시
```

#### 1.3 중복 검사 API

**DuplicateCheckController** (`/api/duplicate-check`):
```java
// 현재 기능
- 파일 해시 기반 중복 검사
- 기존 파일 정보 반환
- EXACT_DUPLICATE 경고
```

### 2. Legacy 엔티티 분석

**FileUploadHistory** (Legacy):
```java
// 필드 목록
- id: BIGSERIAL (PK)
- filename: VARCHAR(255)
- collection_number: VARCHAR(10)      // ← DDD에 추가 필요
- version: VARCHAR(50)                // ← DDD에 추가 필요
- file_format: VARCHAR(50)            // ← DDD에 추가 필요
- file_size_bytes: BIGINT
- file_size_display: VARCHAR(20)
- uploaded_at: TIMESTAMP
- local_file_path: VARCHAR(500)       // ← DDD에 추가 필요
- file_hash: VARCHAR(64)
- expected_checksum: VARCHAR(255)     // ← DDD에 추가 필요 (SHA-1)
- status: VARCHAR(30)                 // ← DDD에 추가 필요
- is_duplicate: BOOLEAN
- is_newer_version: BOOLEAN           // ← DDD에 추가 필요
- error_message: TEXT                 // ← DDD에 추가 필요
```

### 3. Legacy 서비스 분석

**FileStorageService**:
```java
// 책임: 파일 시스템 관리
- saveFile(MultipartFile, FileFormat): String         // 파일 저장
- calculateFileHash(MultipartFile): String            // SHA-256 계산
- deleteFile(String): boolean                         // 파일 삭제
- getAvailableDiskSpace(): long                       // 디스크 용량 확인

// ✅ 이 서비스는 Infrastructure Layer로 이동 (DDD 유지)
```

**FileUploadService**:
```java
// 책임: 업로드 이력 관리, 검색
- saveUploadHistory(FileUploadHistory): FileUploadHistory
- findByFileHash(String): Optional<FileUploadHistory>
- getUploadHistory(...): Page<FileUploadHistory>
- searchUploadHistory(...): Page<FileUploadHistory>
- getUploadStatistics(): Map<String, Object>

// ❌ 이 서비스는 DDD Use Cases로 대체
```

---

## DDD 설계

### 1. Bounded Context 정의

**File Upload Context**:
- 파일 업로드 및 관리의 모든 기능을 담당
- LDIF 파일과 Master List 파일의 업로드, 검증, 저장

### 2. Domain Model 확장

#### 2.1 현재 DDD Domain Model (Phase 1-3)

```java
// Aggregate Root
UploadedFile {
    - UploadId id           (UUID)
    - FileName fileName     (Value Object)
    - FileHash fileHash     (Value Object, SHA-256)
    - FileSize fileSize     (Value Object)
    - uploadedAt            (LocalDateTime)
    - isDuplicate           (boolean)
    - originalUploadId      (UUID, nullable)
}

// Value Objects
FileName        // 파일명 검증 (.ldif, .ml)
FileHash        // SHA-256 해시 (64자)
FileSize        // 파일 크기 (1 byte ~ 100MB)
UploadId        // UUID 기반 Entity ID
```

#### 2.2 확장할 Domain Model

```java
// ========================================
// 추가할 Value Objects
// ========================================

/**
 * FileFormat - 파일 포맷 (Enum → Value Object)
 */
public class FileFormat {
    public enum Type {
        CSCA_COMPLETE_LDIF,
        CSCA_DELTA_LDIF,
        EMRTD_COMPLETE_LDIF,
        EMRTD_DELTA_LDIF,
        ML_SIGNED_CMS,
        ML_UNSIGNED
    }

    private final Type type;

    // 비즈니스 메서드
    public boolean isLdif() { ... }
    public boolean isMasterList() { ... }
    public String getStoragePath() { ... }
    public String getFileExtension() { ... }

    // 정적 팩토리 메서드
    public static FileFormat detectFromFileName(FileName fileName) { ... }
}

/**
 * CollectionNumber - Collection 번호 (001, 002, 003)
 */
public class CollectionNumber {
    private final String value;  // "001", "002", "003"

    private CollectionNumber(String value) {
        validate(value);
        this.value = value;
    }

    private void validate(String value) {
        if (!value.matches("^\\d{3}$")) {
            throw new DomainException("INVALID_COLLECTION",
                "Collection number must be 3 digits (e.g., 001, 002)");
        }
    }

    public static CollectionNumber of(String value) {
        return new CollectionNumber(value);
    }

    public static CollectionNumber extractFromFileName(FileName fileName) {
        // 파일명에서 추출: icaopkd-{collection}-...
        Pattern pattern = Pattern.compile("icaopkd-(\\d{3})-");
        Matcher matcher = pattern.matcher(fileName.getValue());

        if (matcher.find()) {
            return new CollectionNumber(matcher.group(1));
        }

        // 기본값
        return new CollectionNumber("002");
    }
}

/**
 * FileVersion - 파일 버전
 */
public class FileVersion {
    private final String value;  // "009410" 또는 "July2025"

    private FileVersion(String value) {
        validate(value);
        this.value = value;
    }

    private void validate(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new DomainException("INVALID_VERSION", "Version cannot be empty");
        }
        if (value.length() > 50) {
            throw new DomainException("INVALID_VERSION", "Version too long");
        }
    }

    public static FileVersion of(String value) {
        return new FileVersion(value);
    }

    public static FileVersion extractFromFileName(FileName fileName, FileFormat format) {
        if (format.isLdif()) {
            // LDIF: -(\\d+)\\.ldif$
            Pattern pattern = Pattern.compile("-(\\d+)\\.ldif$");
            Matcher matcher = pattern.matcher(fileName.getValue());
            if (matcher.find()) {
                return new FileVersion(matcher.group(1));
            }
        } else {
            // ML: masterlist-([A-Za-z]+\\d{4})\\.ml
            Pattern pattern = Pattern.compile("masterlist-([A-Za-z]+\\d{4})\\.ml");
            Matcher matcher = pattern.matcher(fileName.getValue());
            if (matcher.find()) {
                return new FileVersion(matcher.group(1));
            }
        }

        // 파일명 자체를 버전으로 사용
        return new FileVersion(fileName.getBaseName());
    }

    // 버전 비교 (비즈니스 로직)
    public int compareTo(FileVersion other) {
        // 숫자 버전인 경우 (LDIF)
        try {
            int thisVersion = Integer.parseInt(this.value);
            int otherVersion = Integer.parseInt(other.value);
            return Integer.compare(thisVersion, otherVersion);
        } catch (NumberFormatException e) {
            // 문자열 비교 (ML)
            return this.value.compareTo(other.value);
        }
    }

    public boolean isNewerThan(FileVersion other) {
        return this.compareTo(other) > 0;
    }
}

/**
 * FilePath - 파일 저장 경로
 */
public class FilePath {
    private final String value;  // "./data/uploads/ldif/csca-complete/file.ldif"

    private FilePath(String value) {
        validate(value);
        this.value = value;
    }

    private void validate(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new DomainException("INVALID_FILE_PATH", "File path cannot be empty");
        }
        if (value.length() > 500) {
            throw new DomainException("INVALID_FILE_PATH", "File path too long");
        }
    }

    public static FilePath of(String value) {
        return new FilePath(value);
    }

    public boolean exists() {
        return new File(value).exists();
    }

    public long getFileSize() throws IOException {
        return Files.size(Paths.get(value));
    }
}

/**
 * Checksum - SHA-1 체크섬 (ICAO PKD 표준)
 */
public class Checksum {
    private final String value;  // SHA-1 (40자 16진수)

    private Checksum(String value) {
        validate(value);
        this.value = value.toLowerCase();
    }

    private void validate(String value) {
        if (value == null || !value.matches("^[a-fA-F0-9]{40}$")) {
            throw new DomainException("INVALID_CHECKSUM",
                "Checksum must be a valid SHA-1 hash (40 hexadecimal characters)");
        }
    }

    public static Checksum of(String value) {
        return new Checksum(value);
    }

    public boolean matches(String calculated) {
        return this.value.equalsIgnoreCase(calculated);
    }
}

/**
 * UploadStatus - 업로드 상태
 */
public enum UploadStatus {
    RECEIVED("수신됨"),
    VALIDATING("검증 중"),
    VALIDATED("검증 완료"),
    CHECKSUM_INVALID("체크섬 불일치"),
    DUPLICATE_DETECTED("중복 감지"),
    PARSING("파싱 중"),
    PARSED("파싱 완료"),
    UPLOADING_TO_LDAP("LDAP 업로드 중"),
    COMPLETED("완료"),
    FAILED("실패");

    private final String displayName;

    UploadStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CHECKSUM_INVALID;
    }

    public boolean isInProgress() {
        return !isTerminal();
    }
}

// ========================================
// 확장된 Aggregate Root
// ========================================

/**
 * UploadedFile - 확장된 Aggregate Root
 */
@Entity
@Table(name = "uploaded_file")
public class UploadedFile extends AggregateRoot<UploadId> {

    // ===== 기존 필드 =====
    @EmbeddedId
    private UploadId id;

    @Embedded
    private FileName fileName;

    @Embedded
    private FileHash fileHash;

    @Embedded
    private FileSize fileSize;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "is_duplicate", nullable = false)
    private boolean isDuplicate;

    @Column(name = "original_upload_id")
    private UUID originalUploadId;

    // ===== 추가 필드 =====

    @Embedded
    private CollectionNumber collectionNumber;

    @Embedded
    private FileVersion version;

    @Embedded
    private FileFormat fileFormat;

    @Embedded
    private FilePath filePath;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "expected_checksum"))
    private Checksum expectedChecksum;  // nullable

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "calculated_checksum"))
    private Checksum calculatedChecksum;  // nullable

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UploadStatus status;

    @Column(name = "is_newer_version")
    private boolean isNewerVersion;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // ===== 생성자 (정적 팩토리 메서드) =====

    public static UploadedFile create(
            UploadId id,
            FileName fileName,
            FileHash fileHash,
            FileSize fileSize,
            FileFormat fileFormat,
            CollectionNumber collectionNumber,
            FileVersion version,
            FilePath filePath
    ) {
        UploadedFile file = new UploadedFile();
        file.id = id;
        file.fileName = fileName;
        file.fileHash = fileHash;
        file.fileSize = fileSize;
        file.fileFormat = fileFormat;
        file.collectionNumber = collectionNumber;
        file.version = version;
        file.filePath = filePath;
        file.uploadedAt = LocalDateTime.now();
        file.status = UploadStatus.RECEIVED;
        file.isDuplicate = false;
        file.isNewerVersion = false;

        // Domain Event 발행
        file.addDomainEvent(new FileUploadedEvent(
                id.getId(),
                fileName.getValue(),
                fileHash.getValue(),
                fileSize.getBytes(),
                file.uploadedAt
        ));

        return file;
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 체크섬 검증
     */
    public void validateChecksum(Checksum calculated) {
        this.calculatedChecksum = calculated;
        this.status = UploadStatus.VALIDATING;

        if (expectedChecksum != null && !expectedChecksum.matches(calculated.getValue())) {
            this.status = UploadStatus.CHECKSUM_INVALID;
            this.errorMessage = String.format(
                "Checksum mismatch: expected=%s, calculated=%s",
                expectedChecksum.getValue(),
                calculated.getValue()
            );

            addDomainEvent(new ChecksumValidationFailedEvent(
                    id.getId(),
                    fileName.getValue(),
                    expectedChecksum.getValue(),
                    calculated.getValue(),
                    LocalDateTime.now()
            ));
        } else {
            this.status = UploadStatus.VALIDATED;
        }
    }

    /**
     * 중복 파일로 표시
     */
    public void markAsDuplicate(UploadId originalUploadId) {
        this.isDuplicate = true;
        this.originalUploadId = originalUploadId.getId();
        this.status = UploadStatus.DUPLICATE_DETECTED;

        addDomainEvent(new DuplicateFileDetectedEvent(
                id.getId(),
                fileName.getValue(),
                fileHash.getValue(),
                originalUploadId.getId(),
                LocalDateTime.now()
        ));
    }

    /**
     * 신규 버전으로 표시
     */
    public void markAsNewerVersion(UploadId replacedFileId) {
        this.isNewerVersion = true;
        // replacedFileId는 별도 테이블에서 관리 가능
    }

    /**
     * 상태 변경
     */
    public void changeStatus(UploadStatus newStatus) {
        if (this.status.isTerminal()) {
            throw new DomainException(
                "INVALID_STATUS_TRANSITION",
                String.format("Cannot change status from terminal state: %s", this.status)
            );
        }

        this.status = newStatus;
    }

    /**
     * 오류 처리
     */
    public void fail(String errorMessage) {
        this.status = UploadStatus.FAILED;
        this.errorMessage = errorMessage;

        addDomainEvent(new FileUploadFailedEvent(
                id.getId(),
                fileName.getValue(),
                errorMessage,
                LocalDateTime.now()
        ));
    }

    /**
     * 업로드 완료
     */
    public void complete() {
        this.status = UploadStatus.COMPLETED;

        addDomainEvent(new FileUploadCompletedEvent(
                id.getId(),
                fileName.getValue(),
                fileHash.getValue(),
                LocalDateTime.now()
        ));
    }
}
```

### 3. Domain Events 추가

```java
// 새로운 Domain Events

public record ChecksumValidationFailedEvent(
    UUID uploadId,
    String fileName,
    String expectedChecksum,
    String calculatedChecksum,
    LocalDateTime occurredAt
) implements DomainEvent {}

public record FileUploadFailedEvent(
    UUID uploadId,
    String fileName,
    String errorMessage,
    LocalDateTime occurredAt
) implements DomainEvent {}

public record FileUploadCompletedEvent(
    UUID uploadId,
    String fileName,
    String fileHash,
    LocalDateTime occurredAt
) implements DomainEvent {}
```

### 4. Use Cases 설계

#### 4.1 Upload LDIF File Use Case

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class UploadLdifFileUseCase {

    private final UploadedFileRepository repository;
    private final FileStoragePort fileStoragePort;  // Infrastructure Port

    @Transactional
    public UploadLdifFileResponse execute(UploadLdifFileCommand command) {
        // 1. Value Objects 생성
        FileName fileName = FileName.of(command.fileName());
        FileHash fileHash = FileHash.of(command.fileHash());
        FileSize fileSize = FileSize.ofBytes(command.fileSizeBytes());

        // 2. 파일 포맷 감지
        FileFormat fileFormat = FileFormat.detectFromFileName(fileName);
        if (!fileFormat.isLdif()) {
            throw new DomainException("INVALID_FILE_TYPE", "Not an LDIF file");
        }

        // 3. 메타데이터 추출
        CollectionNumber collectionNumber = CollectionNumber.extractFromFileName(fileName);
        FileVersion version = FileVersion.extractFromFileName(fileName, fileFormat);

        // 4. 중복 검사
        checkDuplicate(fileHash);

        // 5. 파일 저장 (Infrastructure)
        FilePath filePath = fileStoragePort.saveFile(
            command.fileContent(),
            fileFormat,
            fileName
        );

        // 6. Aggregate Root 생성
        UploadId uploadId = UploadId.newId();
        UploadedFile uploadedFile = UploadedFile.create(
            uploadId,
            fileName,
            fileHash,
            fileSize,
            fileFormat,
            collectionNumber,
            version,
            filePath
        );

        // 7. 체크섬 검증 (선택적)
        if (command.expectedChecksum() != null) {
            Checksum expected = Checksum.of(command.expectedChecksum());
            uploadedFile.setExpectedChecksum(expected);

            // 실제 체크섬 계산 (Infrastructure)
            Checksum calculated = fileStoragePort.calculateChecksum(filePath);
            uploadedFile.validateChecksum(calculated);
        }

        // 8. 저장
        UploadedFile saved = repository.save(uploadedFile);

        // 9. Response
        return new UploadLdifFileResponse(
            saved.getId().getId().toString(),
            saved.getFileNameValue(),
            saved.getCollectionNumberValue(),
            saved.getVersionValue(),
            saved.getFileFormatDisplay(),
            saved.getFileSizeDisplay(),
            saved.getUploadedAt(),
            saved.getStatus()
        );
    }

    private void checkDuplicate(FileHash fileHash) {
        Optional<UploadedFile> existing = repository.findByFileHash(fileHash);
        if (existing.isPresent()) {
            throw new DomainException(
                "DUPLICATE_FILE",
                String.format("File already exists: %s", existing.get().getId().getId())
            );
        }
    }
}
```

#### 4.2 Upload Master List File Use Case

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class UploadMasterListFileUseCase {

    private final UploadedFileRepository repository;
    private final FileStoragePort fileStoragePort;

    @Transactional
    public UploadMasterListFileResponse execute(UploadMasterListFileCommand command) {
        // LDIF와 유사한 로직
        // FileFormat.ML_SIGNED_CMS 사용
        // Collection 기본값 002
        // ...
    }
}
```

#### 4.3 Get Upload History Use Case (CQRS Query)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class GetUploadHistoryUseCase {

    private final UploadedFileRepository repository;

    @Transactional(readOnly = true)
    public Page<UploadHistoryDto> execute(GetUploadHistoryQuery query) {
        // 검색 조건
        Specification<UploadedFile> spec = UploadedFileSpecifications.builder()
            .fileNameContains(query.searchKeyword())
            .collectionNumberEquals(query.collectionNumber())
            .fileFormatEquals(query.fileFormat())
            .statusEquals(query.status())
            .uploadedBetween(query.startDate(), query.endDate())
            .build();

        // 페이징 조회
        Pageable pageable = PageRequest.of(
            query.page(),
            query.size(),
            Sort.by(Sort.Direction.DESC, "uploadedAt")
        );

        Page<UploadedFile> result = repository.findAll(spec, pageable);

        // DTO 변환
        return result.map(this::toDto);
    }

    private UploadHistoryDto toDto(UploadedFile file) {
        return new UploadHistoryDto(
            file.getId().getId().toString(),
            file.getFileNameValue(),
            file.getCollectionNumberValue(),
            file.getVersionValue(),
            file.getFileFormatDisplay(),
            file.getFileSizeDisplay(),
            file.getUploadedAt(),
            file.getStatus().getDisplayName(),
            file.isDuplicate(),
            file.isNewerVersion(),
            file.getErrorMessage()
        );
    }
}
```

### 5. Infrastructure Layer 설계

#### 5.1 FileStoragePort (Interface)

```java
/**
 * File Storage Port - Domain Layer Interface
 *
 * Infrastructure Layer에서 구현
 */
public interface FileStoragePort {

    /**
     * 파일 저장
     */
    FilePath saveFile(byte[] content, FileFormat format, FileName fileName);

    /**
     * SHA-1 체크섬 계산
     */
    Checksum calculateChecksum(FilePath filePath);

    /**
     * 파일 삭제
     */
    boolean deleteFile(FilePath filePath);

    /**
     * 디스크 여유 공간 확인
     */
    long getAvailableDiskSpace();
}
```

#### 5.2 FileStorageAdapter (Implementation)

```java
/**
 * File Storage Adapter - Infrastructure Layer
 *
 * FileStoragePort 인터페이스 구현
 */
@Component
@Slf4j
public class FileStorageAdapter implements FileStoragePort {

    @Value("${app.upload.directory:./data/uploads}")
    private String uploadDirectory;

    @Override
    public FilePath saveFile(byte[] content, FileFormat format, FileName fileName) {
        try {
            // 디렉토리 생성
            Path directory = createDirectory(format);

            // 파일명 생성 (타임스탬프 추가)
            String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String newFileName = fileName.getBaseName() + "_" + timestamp +
                                fileName.getExtension();

            Path filePath = directory.resolve(newFileName);

            // 파일 저장
            Files.write(filePath, content, StandardOpenOption.CREATE_NEW);

            log.info("File saved: {}", filePath);
            return FilePath.of(filePath.toString());

        } catch (IOException e) {
            throw new InfrastructureException("FILE_SAVE_ERROR",
                "Failed to save file", e);
        }
    }

    @Override
    public Checksum calculateChecksum(FilePath filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] fileBytes = Files.readAllBytes(Paths.get(filePath.getValue()));
            byte[] hashBytes = digest.digest(fileBytes);

            String hex = bytesToHex(hashBytes);
            return Checksum.of(hex);

        } catch (IOException | NoSuchAlgorithmException e) {
            throw new InfrastructureException("CHECKSUM_CALCULATION_ERROR",
                "Failed to calculate checksum", e);
        }
    }

    private Path createDirectory(FileFormat format) throws IOException {
        Path directory = Paths.get(uploadDirectory, format.getStoragePath());
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
        return directory;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
```

### 6. Web Controllers (DDD)

#### 6.1 LDIF Upload Controller

```java
@Controller
@RequestMapping("/ldif")
@RequiredArgsConstructor
@Slf4j
public class LdifUploadWebController {

    private final UploadLdifFileUseCase uploadLdifFileUseCase;

    @GetMapping("/upload")
    public String showUploadPage(Model model) {
        return "ldif/upload-ldif";
    }

    @PostMapping("/upload")
    public String uploadLdif(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String expectedChecksum,
            RedirectAttributes redirectAttributes
    ) {
        log.info("=== LDIF file upload started ===");
        log.info("Filename: {}", file.getOriginalFilename());

        try {
            // Command 생성
            UploadLdifFileCommand command = new UploadLdifFileCommand(
                file.getOriginalFilename(),
                calculateFileHash(file),
                file.getSize(),
                file.getBytes(),
                expectedChecksum
            );

            // Use Case 실행
            UploadLdifFileResponse response = uploadLdifFileUseCase.execute(command);

            // Success
            redirectAttributes.addFlashAttribute("highlightId", response.uploadId());
            redirectAttributes.addFlashAttribute("successMessage",
                "파일 업로드가 완료되었습니다.");

            return "redirect:/upload-history";

        } catch (DomainException e) {
            log.error("Domain error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/ldif/upload";

        } catch (Exception e) {
            log.error("Upload error", e);
            redirectAttributes.addFlashAttribute("error",
                "파일 업로드 중 오류가 발생했습니다.");
            return "redirect:/ldif/upload";
        }
    }

    private String calculateFileHash(MultipartFile file) throws IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(file.getBytes());

        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
```

---

## 구현 로드맵

### Phase 4.1: Domain Model 확장 (1-2 days)

**구현 항목**:
1. Value Objects 생성
   - `FileFormat` (enum → Value Object)
   - `CollectionNumber`
   - `FileVersion`
   - `FilePath`
   - `Checksum`
   - `UploadStatus` (enum)

2. `UploadedFile` Aggregate Root 확장
   - 새 필드 추가
   - 비즈니스 메서드 추가

3. Domain Events 추가
   - `ChecksumValidationFailedEvent`
   - `FileUploadFailedEvent`
   - `FileUploadCompletedEvent`

4. Unit Tests
   - 각 Value Object 테스트 (22 tests)
   - UploadedFile 비즈니스 로직 테스트 (18 tests)

**예상 테스트 수**: 40+ tests

### Phase 4.2: Use Cases 구현 (2-3 days)

**구현 항목**:
1. `UploadLdifFileUseCase`
2. `UploadMasterListFileUseCase`
3. `GetUploadHistoryUseCase` (CQRS Query)
4. `GetUploadStatisticsUseCase`

**DTO/Command/Query 클래스**:
- `UploadLdifFileCommand`
- `UploadLdifFileResponse`
- `UploadMasterListFileCommand`
- `UploadMasterListFileResponse`
- `GetUploadHistoryQuery`
- `UploadHistoryDto`

**예상 테스트 수**: 32+ tests

### Phase 4.3: Infrastructure Layer (1 day)

**구현 항목**:
1. `FileStoragePort` (Interface)
2. `FileStorageAdapter` (Implementation)
3. JPA Specifications for Query
   - `UploadedFileSpecifications`

**예상 테스트 수**: 12+ tests

### Phase 4.4: Web Controllers (1 day)

**구현 항목**:
1. `LdifUploadWebController`
2. `MasterListUploadWebController`
3. `UploadHistoryWebController`
4. `DuplicateCheckRestController` (API)

**예상 테스트 수**: 16+ tests (MockMvc)

### Phase 4.5: Database Migration (1 day)

**구현 항목**:
1. Flyway Migration Script
   - `V7__Extend_Uploaded_File_Table.sql`
   - 새 컬럼 추가
   - 인덱스 추가

2. 데이터 마이그레이션 (선택적)
   - `file_upload_history` → `uploaded_file` 데이터 복사

### Phase 4.6: Integration Tests (1 day)

**구현 항목**:
1. End-to-End 테스트
2. 파일 업로드 → 조회 → 삭제 전체 플로우
3. 중복 검사 시나리오
4. 체크섬 검증 시나리오

**예상 테스트 수**: 10+ tests

### Phase 4.7: Legacy Code 제거 (0.5 day)

**제거 항목**:
1. Legacy Controllers
   - `LdifUploadController`
   - `MasterListUploadController`
   - `UploadHistoryController`
   - `DuplicateCheckController`

2. Legacy Services
   - `FileUploadService`

3. Legacy Entities (선택적)
   - `FileUploadHistory` (테이블은 백업 목적으로 유지 가능)

---

## 데이터베이스 전략

### 옵션 1: 테이블 통합 (권장)

**전략**: `file_upload_history` 삭제, `uploaded_file` 확장

**Flyway Migration** (`V7__Extend_Uploaded_File_Table.sql`):
```sql
-- 1. uploaded_file 테이블에 컬럼 추가
ALTER TABLE uploaded_file
ADD COLUMN collection_number VARCHAR(10),
ADD COLUMN version VARCHAR(50),
ADD COLUMN file_format VARCHAR(50) NOT NULL DEFAULT 'CSCA_COMPLETE_LDIF',
ADD COLUMN file_size_display VARCHAR(20),
ADD COLUMN local_file_path VARCHAR(500),
ADD COLUMN expected_checksum VARCHAR(40),      -- SHA-1
ADD COLUMN calculated_checksum VARCHAR(40),    -- SHA-1
ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
ADD COLUMN is_newer_version BOOLEAN DEFAULT FALSE,
ADD COLUMN error_message TEXT;

-- 2. 인덱스 추가
CREATE INDEX idx_uploaded_file_collection ON uploaded_file(collection_number);
CREATE INDEX idx_uploaded_file_version ON uploaded_file(version);
CREATE INDEX idx_uploaded_file_status ON uploaded_file(status);

-- 3. 제약 조건 추가
ALTER TABLE uploaded_file
ADD CONSTRAINT chk_collection_number CHECK (collection_number ~ '^\\d{3}$');

-- 4. file_upload_history 테이블 백업 (선택적)
CREATE TABLE file_upload_history_backup AS SELECT * FROM file_upload_history;

-- 5. file_upload_history 테이블 삭제 (또는 유지)
-- DROP TABLE file_upload_history;  -- 주석 처리: 일단 유지
```

### 옵션 2: 병행 운영 (임시)

**전략**: 두 테이블 모두 유지, 점진적 전환

- Phase 4.1~4.6: `uploaded_file` 테이블만 사용
- Legacy 테이블은 읽기 전용
- Phase 4.7 이후 `file_upload_history` 삭제

---

## 성공 기준

### Phase 4.1 완료 기준

- [x] 7개 Value Objects 구현 및 테스트
- [x] UploadedFile Aggregate Root 확장
- [x] 3개 Domain Events 추가
- [x] Unit Test 커버리지 > 95%

### Phase 4.2 완료 기준

- [x] 4개 Use Cases 구현
- [x] DTO/Command/Query 클래스 정의
- [x] Use Case 테스트 작성

### Phase 4.3 완료 기준

- [x] FileStorageAdapter 구현
- [x] JPA Specifications 구현
- [x] Infrastructure 테스트

### Phase 4.4 완료 기준

- [x] 4개 Web Controllers 구현
- [x] MockMvc 테스트

### Phase 4.5 완료 기준

- [x] Flyway Migration 실행 성공
- [x] 스키마 변경 확인

### Phase 4.6 완료 기준

- [x] 10개 Integration Tests 통과
- [x] End-to-End 플로우 검증

### Phase 4.7 완료 기준

- [x] Legacy Controllers 삭제
- [x] Legacy Services 삭제
- [x] 전체 빌드 성공

---

## 다음 단계

**Phase 4.1부터 시작**:
1. Value Objects 생성
2. UploadedFile 확장
3. Domain Events 추가
4. Unit Tests 작성

예상 소요 시간: **7-10 days** (전체)

---

**문서 종료**
