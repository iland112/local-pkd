# Phase 4.2: Legacy Code Removal & DDD Migration Plan

**작성일**: 2025-10-19
**목적**: Legacy 코드를 DDD로 완전 대체 및 제거

---

## 제거할 Legacy 코드

### Controllers (5개)
1. ❌ `LdifUploadController.java` → DDD Use Case로 대체
2. ❌ `MasterListUploadController.java` → DDD Use Case로 대체
3. ❌ `UploadHistoryController.java` → DDD Query Service로 대체
4. ❌ `DuplicateCheckController.java` → DDD Use Case로 대체
5. ✅ `DatabaseHealthController.java` → **유지** (인프라 헬스체크)

### Services (2개)
1. ❌ `FileUploadService.java` → DDD Use Cases로 대체
2. ⚠️ `FileStorageService.java` → **DDD Infrastructure로 리팩토링** (Port/Adapter 패턴)

### Entities (Legacy)
1. ❌ `FileUploadHistory.java` → `UploadedFile` (DDD)로 완전 대체
2. ❌ `file_upload_history` 테이블 → **삭제** (V1~V5 마이그레이션도 정리 필요)

### DTOs (Legacy)
1. ❌ `DuplicateCheckRequest.java` → DDD Command로 대체
2. ❌ `DuplicateCheckResponse.java` → DDD Response로 대체

---

## 새로 작성할 DDD 코드

### 1. Application Layer - Use Cases

#### `UploadLdifFileUseCase.java`
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class UploadLdifFileUseCase {

    private final UploadedFileRepository repository;
    private final FileStoragePort fileStoragePort;

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
        UploadedFile uploadedFile = UploadedFile.createWithMetadata(
            uploadId, fileName, fileHash, fileSize,
            fileFormat, collectionNumber, version, filePath
        );

        // 7. 체크섬 검증 (선택적)
        if (command.expectedChecksum() != null) {
            Checksum expected = Checksum.of(command.expectedChecksum());
            uploadedFile.setExpectedChecksum(expected);

            Checksum calculated = fileStoragePort.calculateChecksum(filePath);
            uploadedFile.validateChecksum(calculated);
        }

        // 8. 저장 (Domain Events 자동 발행)
        UploadedFile saved = repository.save(uploadedFile);

        return new UploadLdifFileResponse(/* ... */);
    }
}
```

#### `UploadMasterListFileUseCase.java`
- LDIF와 유사, ML 전용 로직

#### `CheckDuplicateFileUseCase.java`
- 기존 `DuplicateCheckController` 로직을 Use Case로 이동

#### `GetUploadHistoryUseCase.java` (CQRS Query)
- 기존 `UploadHistoryController` 로직을 Query Service로 이동

### 2. Infrastructure Layer - Ports & Adapters

#### `FileStoragePort.java` (Interface - Domain Layer)
```java
public interface FileStoragePort {
    FilePath saveFile(byte[] content, FileFormat format, FileName fileName);
    Checksum calculateChecksum(FilePath filePath);
    boolean deleteFile(FilePath filePath);
    long getAvailableDiskSpace();
}
```

#### `LocalFileStorageAdapter.java` (Implementation - Infrastructure Layer)
```java
@Component
@Slf4j
public class LocalFileStorageAdapter implements FileStoragePort {

    @Value("${app.upload.directory:./data/uploads}")
    private String uploadDirectory;

    @Override
    public FilePath saveFile(byte[] content, FileFormat format, FileName fileName) {
        // Legacy FileStorageService 로직을 여기로 이동
    }

    @Override
    public Checksum calculateChecksum(FilePath filePath) {
        // SHA-1 체크섬 계산
    }
}
```

### 3. Web Layer - DDD Controllers

#### `LdifUploadWebController.java`
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
        try {
            UploadLdifFileCommand command = new UploadLdifFileCommand(
                file.getOriginalFilename(),
                calculateFileHash(file),
                file.getSize(),
                file.getBytes(),
                expectedChecksum
            );

            UploadLdifFileResponse response = uploadLdifFileUseCase.execute(command);

            redirectAttributes.addFlashAttribute("highlightId", response.uploadId());
            redirectAttributes.addFlashAttribute("successMessage", "업로드 완료");

            return "redirect:/upload-history";

        } catch (DomainException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/ldif/upload";
        }
    }
}
```

#### `MasterListUploadWebController.java`
- LDIF와 유사

#### `UploadHistoryWebController.java`
- CQRS Query Service 사용

---

## 제거 순서 (안전한 마이그레이션)

### Step 1: Infrastructure Layer 작성
1. `FileStoragePort` 인터페이스 생성
2. `LocalFileStorageAdapter` 구현 (Legacy `FileStorageService` 로직 이동)

### Step 2: Application Layer 작성
1. Commands & Responses (DTOs) 생성
2. Use Cases 구현 (4개)

### Step 3: Web Layer 작성
1. 새로운 DDD Controllers 생성
2. 기존 템플릿 유지 (엔드포인트만 변경)

### Step 4: Legacy 제거
1. Legacy Controllers 삭제 (5개 중 4개)
2. Legacy Services 삭제 (2개)
3. Legacy DTOs 삭제 (2개)
4. Legacy Entities 삭제 (1개)

### Step 5: 데이터베이스 정리
1. `file_upload_history` 테이블 Drop
2. V1~V5 Flyway 스크립트 정리 (선택적)

---

## 작업 예상 시간

- **Step 1**: 1 hour (Infrastructure)
- **Step 2**: 2-3 hours (Use Cases)
- **Step 3**: 1-2 hours (Controllers)
- **Step 4**: 30 mins (Legacy 제거)
- **Step 5**: 30 mins (DB 정리)

**총 예상**: 5-7 hours

---

## 검증 계획

1. ✅ 컴파일 성공
2. ✅ 모든 Use Case 테스트 통과
3. ✅ 파일 업로드 End-to-End 테스트
4. ✅ 중복 검사 동작 확인
5. ✅ 업로드 이력 조회 동작 확인

---

**다음 단계**: Step 1부터 시작
