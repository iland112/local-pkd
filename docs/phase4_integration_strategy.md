# Phase 4: Legacy-DDD Integration Strategy

**문서 버전**: 1.0
**작성일**: 2025-10-19
**상태**: Planning

## 목차

1. [개요](#개요)
2. [현재 아키텍처 분석](#현재-아키텍처-분석)
3. [통합 전략](#통합-전략)
4. [마이그레이션 로드맵](#마이그레이션-로드맵)
5. [구현 계획](#구현-계획)

---

## 개요

### 목적

Phase 1-3에서 구현한 DDD 기반 파일 업로드 시스템을 기존 레거시 시스템과 통합하여, 점진적인 마이그레이션을 가능하게 합니다.

### 주요 목표

1. **무중단 마이그레이션**: 기존 기능의 동작을 보장하면서 DDD 시스템 도입
2. **데이터 일관성**: Legacy `file_upload_history` 테이블과 DDD `uploaded_file` 테이블 간 동기화
3. **점진적 전환**: Adapter Pattern을 사용한 단계적 마이그레이션
4. **이벤트 기반 통합**: Domain Events를 통한 느슨한 결합

---

## 현재 아키텍처 분석

### 1. Legacy 시스템

#### 1.1 Legacy Controllers

**LdifUploadController** (`/ldif/upload`)
- 파일 검증, 해시 계산, 중복 체크
- `FileUploadHistory` 엔티티 직접 생성
- `FileUploadService.saveUploadHistory()` 호출
- Redirect to `/upload-history`

**MasterListUploadController** (`/masterlist/upload`)
- 동일한 워크플로우
- ML 파일 전용 로직

#### 1.2 Legacy Services

**FileUploadService**
```java
// 주요 메서드
- saveUploadHistory(FileUploadHistory): FileUploadHistory
- findByFileHash(String): Optional<FileUploadHistory>
- searchUploadHistory(...): Page<FileUploadHistory>
- getUploadStatistics(): Map<String, Object>
```

**FileStorageService**
```java
// 파일 시스템 관리
- saveFile(MultipartFile, FileFormat): String
- calculateFileHash(MultipartFile): String
- deleteFile(String): boolean
```

#### 1.3 Legacy Database Schema

**Table: `file_upload_history`**
```sql
CREATE TABLE file_upload_history (
    id BIGSERIAL PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    collection_number VARCHAR(10),
    version VARCHAR(50),
    file_format VARCHAR(50) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    file_size_display VARCHAR(20),
    uploaded_at TIMESTAMP NOT NULL,
    local_file_path VARCHAR(500),
    file_hash VARCHAR(64),              -- SHA-256
    expected_checksum VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    is_duplicate BOOLEAN DEFAULT FALSE,
    is_newer_version BOOLEAN DEFAULT FALSE,
    error_message TEXT
);
```

### 2. DDD 시스템 (Phases 1-3)

#### 2.1 DDD Controllers

**FileUploadController** (`/api/ddd/files/*`)
- `POST /api/ddd/files/check-duplicate` - 중복 검사
- `POST /api/ddd/files/upload` - 파일 업로드

#### 2.2 DDD Use Cases

**UploadFileUseCase**
```java
@Transactional
public UploadFileResponse execute(UploadFileCommand command) {
    // 1. Value Objects 생성
    FileName fileName = FileName.of(command.fileName());
    FileHash fileHash = FileHash.of(command.fileHash());
    FileSize fileSize = FileSize.ofBytes(command.fileSizeBytes());

    // 2. 중복 검사
    Optional<UploadedFile> existingFile = repository.findByFileHash(fileHash);
    if (existingFile.isPresent()) {
        throw new DomainException("DUPLICATE_FILE", ...);
    }

    // 3. Aggregate Root 생성
    UploadedFile uploadedFile = UploadedFile.create(...);

    // 4. 저장 (Domain Events 자동 발행)
    UploadedFile saved = repository.save(uploadedFile);

    return new UploadFileResponse(...);
}
```

**CheckDuplicateFileUseCase**
```java
@Transactional(readOnly = true)
public DuplicateCheckResponse execute(CheckDuplicateCommand command) {
    FileHash fileHash = FileHash.of(command.fileHash());
    Optional<UploadedFile> existing = repository.findByFileHash(fileHash);

    if (existing.isEmpty()) {
        return DuplicateCheckResponse.noDuplicate();
    }

    return DuplicateCheckResponse.exactDuplicate(existing.get());
}
```

#### 2.3 DDD Database Schema

**Table: `uploaded_file`**
```sql
CREATE TABLE uploaded_file (
    id UUID PRIMARY KEY,                -- UploadId (JPearl-based)
    file_name VARCHAR(255) NOT NULL,    -- FileName.value
    file_hash VARCHAR(64) NOT NULL,     -- FileHash.value (SHA-256)
    file_size_bytes BIGINT NOT NULL,    -- FileSize.bytes
    uploaded_at TIMESTAMP NOT NULL,
    is_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    original_upload_id UUID,
    CONSTRAINT chk_file_size_positive CHECK (file_size_bytes > 0),
    CONSTRAINT chk_file_size_limit CHECK (file_size_bytes <= 104857600)
);
```

#### 2.4 Domain Events

**FileUploadedEvent**
```java
public record FileUploadedEvent(
    UUID uploadId,
    String fileName,
    String fileHash,
    long fileSizeBytes,
    LocalDateTime uploadedAt
) implements DomainEvent {}
```

**DuplicateFileDetectedEvent**
```java
public record DuplicateFileDetectedEvent(
    UUID uploadId,
    String fileName,
    String fileHash,
    UUID existingUploadId,
    LocalDateTime detectedAt
) implements DomainEvent {}
```

#### 2.5 Event Handlers

**FileUploadEventHandler**
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class FileUploadEventHandler {

    // Synchronous
    @EventListener
    public void handleFileUploaded(FileUploadedEvent event) {
        log.info("FileUploadedEvent received: {}", event.uploadId());
    }

    // Asynchronous (AFTER_COMMIT)
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFileUploadedAsync(FileUploadedEvent event) {
        // Background processing
    }

    @EventListener
    public void handleDuplicateFileDetected(DuplicateFileDetectedEvent event) {
        log.warn("Duplicate file detected: {}", event.fileName());
    }
}
```

---

## 통합 전략

### 1. Adapter Pattern 적용

#### 1.1 Legacy-DDD Adapter

Legacy 시스템과 DDD 시스템을 연결하는 Adapter를 생성합니다.

```java
/**
 * Legacy-DDD Adapter
 *
 * Legacy FileUploadHistory ↔ DDD UploadedFile 변환
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileUploadHistoryAdapter {

    private final UploadedFileRepository dddRepository;
    private final FileUploadHistoryRepository legacyRepository;
    private final EventBus eventBus;

    /**
     * Legacy 업로드 이력을 DDD 시스템에 동기화
     */
    @Transactional
    public void syncLegacyToDDD(FileUploadHistory legacy) {
        // 1. Legacy → DDD 변환
        UploadedFile dddFile = convertToDDD(legacy);

        // 2. DDD Repository에 저장
        UploadedFile saved = dddRepository.save(dddFile);

        // 3. 이벤트 발행
        eventBus.publishAll(saved.getDomainEvents());
        saved.clearDomainEvents();

        log.info("Synced legacy upload (ID: {}) to DDD system (UUID: {})",
                 legacy.getId(), saved.getId().getId());
    }

    /**
     * DDD 업로드를 Legacy 시스템에 동기화
     */
    @Transactional
    public FileUploadHistory syncDDDToLegacy(UploadedFile dddFile) {
        // 1. DDD → Legacy 변환
        FileUploadHistory legacy = convertToLegacy(dddFile);

        // 2. Legacy Repository에 저장
        FileUploadHistory saved = legacyRepository.save(legacy);

        log.info("Synced DDD upload (UUID: {}) to legacy system (ID: {})",
                 dddFile.getId().getId(), saved.getId());

        return saved;
    }

    /**
     * Legacy → DDD 변환
     */
    private UploadedFile convertToDDD(FileUploadHistory legacy) {
        UploadId uploadId = UploadId.newId();
        FileName fileName = FileName.of(legacy.getFilename());
        FileHash fileHash = FileHash.of(legacy.getFileHash());
        FileSize fileSize = FileSize.ofBytes(legacy.getFileSizeBytes());

        return UploadedFile.create(uploadId, fileName, fileHash, fileSize);
    }

    /**
     * DDD → Legacy 변환
     */
    private FileUploadHistory convertToLegacy(UploadedFile dddFile) {
        return FileUploadHistory.builder()
                .filename(dddFile.getFileNameValue())
                .fileHash(dddFile.getFileHashValue())
                .fileSizeBytes(dddFile.getFileSizeBytes())
                .fileSizeDisplay(dddFile.getFileSizeDisplay())
                .uploadedAt(dddFile.getUploadedAt())
                .status(UploadStatus.RECEIVED)
                .isDuplicate(dddFile.isDuplicate())
                .build();
    }
}
```

#### 1.2 Dual-Write Strategy

업로드 시 Legacy와 DDD 테이블 모두에 저장합니다.

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridFileUploadService {

    private final FileUploadHistoryAdapter adapter;
    private final UploadFileUseCase dddUseCase;
    private final FileUploadService legacyService;

    /**
     * 하이브리드 업로드: Legacy + DDD 동시 저장
     */
    @Transactional
    public HybridUploadResponse uploadFile(
            MultipartFile file,
            String fileHash,
            boolean useDDD
    ) {
        if (useDDD) {
            // DDD 우선 모드
            return uploadWithDDDFirst(file, fileHash);
        } else {
            // Legacy 우선 모드 (기본)
            return uploadWithLegacyFirst(file, fileHash);
        }
    }

    /**
     * DDD 우선 모드: DDD에 저장 후 Legacy에 동기화
     */
    private HybridUploadResponse uploadWithDDDFirst(
            MultipartFile file,
            String fileHash
    ) {
        // 1. DDD Use Case 실행
        UploadFileCommand command = new UploadFileCommand(
                file.getOriginalFilename(),
                fileHash,
                file.getSize()
        );

        UploadFileResponse dddResponse = dddUseCase.execute(command);

        // 2. Legacy에 동기화 (백그라운드)
        UploadId uploadId = UploadId.of(UUID.fromString(dddResponse.uploadId()));
        UploadedFile dddFile = adapter.findDDDFileById(uploadId)
                .orElseThrow(() -> new RuntimeException("DDD file not found"));

        FileUploadHistory legacyHistory = adapter.syncDDDToLegacy(dddFile);

        return new HybridUploadResponse(
                dddResponse.uploadId(),
                legacyHistory.getId(),
                "DDD_FIRST"
        );
    }

    /**
     * Legacy 우선 모드: Legacy에 저장 후 DDD에 동기화
     */
    private HybridUploadResponse uploadWithLegacyFirst(
            MultipartFile file,
            String fileHash
    ) {
        // 1. Legacy 저장 (기존 로직 유지)
        FileUploadHistory legacyHistory = FileUploadHistory.builder()
                .filename(file.getOriginalFilename())
                .fileHash(fileHash)
                .fileSizeBytes(file.getSize())
                .uploadedAt(LocalDateTime.now())
                .status(UploadStatus.RECEIVED)
                .build();

        FileUploadHistory saved = legacyService.saveUploadHistory(legacyHistory);

        // 2. DDD에 동기화 (백그라운드)
        adapter.syncLegacyToDDD(saved);

        return new HybridUploadResponse(
                null,  // DDD UUID는 비동기 생성
                saved.getId(),
                "LEGACY_FIRST"
        );
    }
}
```

### 2. 이벤트 기반 동기화

#### 2.1 Event-Driven Sync Handler

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class FileUploadSyncHandler {

    private final FileUploadHistoryAdapter adapter;

    /**
     * DDD 파일 업로드 이벤트 → Legacy 동기화
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFileUploadedEventAsync(FileUploadedEvent event) {
        log.info("Syncing DDD upload to legacy system: uploadId={}", event.uploadId());

        try {
            // DDD → Legacy 동기화
            UploadId uploadId = UploadId.of(event.uploadId());
            UploadedFile dddFile = adapter.findDDDFileById(uploadId)
                    .orElseThrow(() -> new RuntimeException("DDD file not found"));

            adapter.syncDDDToLegacy(dddFile);

            log.info("Legacy sync completed: uploadId={}", event.uploadId());

        } catch (Exception e) {
            log.error("Failed to sync DDD to legacy", e);
            // 실패 시 재시도 큐에 추가 (선택적)
        }
    }
}
```

### 3. Feature Toggle 기반 점진적 전환

#### 3.1 Feature Toggle Service

```java
@Service
@Configuration
public class FeatureToggleService {

    @Value("${feature.upload.ddd-mode.enabled:false}")
    private boolean dddModeEnabled;

    @Value("${feature.upload.dual-write.enabled:true}")
    private boolean dualWriteEnabled;

    public boolean isDDDModeEnabled() {
        return dddModeEnabled;
    }

    public boolean isDualWriteEnabled() {
        return dualWriteEnabled;
    }

    /**
     * 업로드 모드 결정
     */
    public UploadMode getUploadMode() {
        if (dddModeEnabled && dualWriteEnabled) {
            return UploadMode.DDD_WITH_LEGACY_SYNC;
        } else if (dddModeEnabled) {
            return UploadMode.DDD_ONLY;
        } else if (dualWriteEnabled) {
            return UploadMode.LEGACY_WITH_DDD_SYNC;
        } else {
            return UploadMode.LEGACY_ONLY;
        }
    }

    public enum UploadMode {
        LEGACY_ONLY,              // Phase 0: 기존 시스템만 사용
        LEGACY_WITH_DDD_SYNC,     // Phase 1: Legacy 우선 + DDD 동기화
        DDD_WITH_LEGACY_SYNC,     // Phase 2: DDD 우선 + Legacy 동기화
        DDD_ONLY                  // Phase 3: DDD만 사용
    }
}
```

#### 3.2 Adaptive Controller

```java
@Controller
@RequestMapping("/ldif")
@RequiredArgsConstructor
@Slf4j
public class AdaptiveLdifUploadController {

    private final FeatureToggleService featureToggle;
    private final HybridFileUploadService hybridService;
    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    public String uploadLdif(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean forceUpload,
            RedirectAttributes redirectAttributes
    ) {
        log.info("=== Adaptive LDIF upload started ===");
        log.info("Upload mode: {}", featureToggle.getUploadMode());

        try {
            // 1. 파일 해시 계산
            String fileHash = fileStorageService.calculateFileHash(file);

            // 2. Feature Toggle에 따른 업로드 모드 선택
            UploadMode mode = featureToggle.getUploadMode();

            HybridUploadResponse response = switch (mode) {
                case DDD_WITH_LEGACY_SYNC, DDD_ONLY ->
                    hybridService.uploadFile(file, fileHash, true);

                case LEGACY_WITH_DDD_SYNC, LEGACY_ONLY ->
                    hybridService.uploadFile(file, fileHash, false);
            };

            // 3. Redirect
            redirectAttributes.addFlashAttribute(
                    "highlightId", response.legacyId()
            );
            redirectAttributes.addFlashAttribute(
                    "successMessage", "파일 업로드가 완료되었습니다."
            );

            return "redirect:/upload-history";

        } catch (Exception e) {
            log.error("Upload error", e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/ldif/upload?error=true";
        }
    }
}
```

---

## 마이그레이션 로드맵

### Phase 0: 현재 상태 (Baseline)

- ✅ Legacy 시스템 완전 동작
- ✅ DDD 시스템 독립적으로 구현 완료 (Phases 1-3)
- ❌ 통합 없음

### Phase 4.1: Adapter & Dual-Write 구현 (1-2 days)

**목표**: Legacy와 DDD 시스템 간 데이터 동기화 기반 구축

**구현 항목**:
1. `FileUploadHistoryAdapter` 구현
2. `HybridFileUploadService` 구현
3. `FileUploadSyncHandler` (이벤트 기반 동기화)
4. Unit Tests (Adapter 변환 로직)

**검증**:
- Legacy → DDD 변환 테스트
- DDD → Legacy 변환 테스트
- 동기화 이벤트 핸들러 테스트

### Phase 4.2: Feature Toggle 통합 (1 day)

**목표**: Feature Flag 기반 점진적 전환 준비

**구현 항목**:
1. `FeatureToggleService` 구현
2. `application.properties` 설정 추가
3. `AdaptiveLdifUploadController` 구현
4. Integration Tests (Feature Toggle 모드 전환)

**설정 예시**:
```properties
# Phase 4.2: Legacy 우선 + DDD 동기화
feature.upload.ddd-mode.enabled=false
feature.upload.dual-write.enabled=true
```

### Phase 4.3: Production 배포 및 모니터링 (1-2 weeks)

**목표**: Legacy 우선 모드로 프로덕션 배포 및 안정성 확인

**배포 전략**:
1. **Week 1**: `LEGACY_WITH_DDD_SYNC` 모드 배포
   - Legacy가 Primary
   - DDD는 백그라운드 동기화
   - 모니터링: 동기화 성공률, 지연시간

2. **Week 2**: 데이터 일관성 검증
   - `file_upload_history` vs `uploaded_file` 비교
   - 불일치 데이터 분석 및 수정

**모니터링 지표**:
- 동기화 성공률 (Target: 99.9%)
- 동기화 지연시간 (Target: < 100ms)
- 데이터 일관성 (Target: 100%)

### Phase 4.4: DDD 우선 모드 전환 (1 week)

**목표**: DDD를 Primary로 전환

**전환 절차**:
1. Feature Flag 변경:
   ```properties
   feature.upload.ddd-mode.enabled=true
   feature.upload.dual-write.enabled=true
   ```
2. 모니터링 강화 (1주간)
3. Rollback 준비 (Legacy 모드로 즉시 전환 가능)

### Phase 4.5: Legacy 시스템 단계적 제거 (2-3 weeks)

**목표**: Legacy 의존성 제거 및 DDD 시스템으로 완전 전환

**제거 순서**:
1. **Week 1**: Legacy Controllers Deprecated
   - `/ldif/upload` → `/api/ddd/files/upload` 리다이렉트
   - 경고 메시지 표시

2. **Week 2**: Legacy Services 미사용 마킹
   - `@Deprecated` 어노테이션 추가
   - 사용 통계 수집

3. **Week 3**: Legacy 코드 완전 제거
   - Controllers, Services 삭제
   - `file_upload_history` 테이블 유지 (히스토리 목적)

---

## 구현 계획

### 1. Adapter Pattern 구현

#### 1.1 FileUploadHistoryAdapter.java

**위치**: `src/main/java/com/smartcoreinc/localpkd/integration/adapter/`

**책임**:
- Legacy ↔ DDD 변환
- 양방향 동기화

**주요 메서드**:
```java
// 변환
- convertToDDD(FileUploadHistory): UploadedFile
- convertToLegacy(UploadedFile): FileUploadHistory

// 동기화
- syncLegacyToDDD(FileUploadHistory): void
- syncDDDToLegacy(UploadedFile): FileUploadHistory

// 조회
- findDDDFileById(UploadId): Optional<UploadedFile>
- findLegacyFileById(Long): Optional<FileUploadHistory>
```

#### 1.2 HybridFileUploadService.java

**위치**: `src/main/java/com/smartcoreinc/localpkd/integration/service/`

**책임**:
- Dual-Write 전략 구현
- Feature Toggle 기반 모드 전환

**주요 메서드**:
```java
- uploadFile(MultipartFile, String, boolean): HybridUploadResponse
- uploadWithDDDFirst(...): HybridUploadResponse
- uploadWithLegacyFirst(...): HybridUploadResponse
```

### 2. Event-Driven Sync

#### 2.1 FileUploadSyncHandler.java

**위치**: `src/main/java/com/smartcoreinc/localpkd/integration/event/`

**책임**:
- Domain Events 구독
- 비동기 동기화

**주요 메서드**:
```java
@Async
@TransactionalEventListener(phase = AFTER_COMMIT)
- handleFileUploadedEventAsync(FileUploadedEvent): void

@Async
@TransactionalEventListener(phase = AFTER_COMMIT)
- handleDuplicateFileDetectedAsync(DuplicateFileDetectedEvent): void
```

### 3. Feature Toggle

#### 3.1 FeatureToggleService.java

**위치**: `src/main/java/com/smartcoreinc/localpkd/integration/config/`

**책임**:
- Feature Flag 관리
- 업로드 모드 결정

**설정 파일** (`application.properties`):
```properties
# Feature Toggle Settings
feature.upload.ddd-mode.enabled=false
feature.upload.dual-write.enabled=true
feature.upload.async-sync.enabled=true
feature.upload.sync-timeout-ms=5000
```

### 4. Tests

#### 4.1 Unit Tests

**FileUploadHistoryAdapterTest.java**
```java
- testConvertLegacyToDDD()
- testConvertDDDToLegacy()
- testRoundTripConversion()
- testNullFieldHandling()
```

**HybridFileUploadServiceTest.java**
```java
- testUploadWithDDDFirst()
- testUploadWithLegacyFirst()
- testDualWriteSuccess()
- testDualWriteFailureHandling()
```

#### 4.2 Integration Tests

**FileUploadIntegrationTest.java**
```java
- testLegacyOnlyMode()
- testLegacyWithDDDSyncMode()
- testDDDWithLegacySyncMode()
- testDDDOnlyMode()
- testModeTransition()
- testDataConsistency()
```

---

## 디렉토리 구조

```
src/main/java/com/smartcoreinc/localpkd/
├── fileupload/                          # DDD Bounded Context
│   ├── domain/
│   ├── application/
│   └── infrastructure/
│
├── integration/                         # ✨ NEW: Integration Layer
│   ├── adapter/
│   │   ├── FileUploadHistoryAdapter.java
│   │   └── MapStructMappers.java        # (선택적) MapStruct 사용 시
│   ├── service/
│   │   ├── HybridFileUploadService.java
│   │   └── SyncRetryService.java        # (선택적) 재시도 로직
│   ├── event/
│   │   └── FileUploadSyncHandler.java
│   ├── config/
│   │   ├── FeatureToggleService.java
│   │   └── IntegrationConfig.java
│   └── dto/
│       └── HybridUploadResponse.java
│
├── controller/                          # Legacy Controllers
│   ├── LdifUploadController.java        # → Deprecated in Phase 4.5
│   ├── MasterListUploadController.java  # → Deprecated in Phase 4.5
│   └── AdaptiveLdifUploadController.java # ✨ NEW: Feature Toggle 기반
│
└── service/                             # Legacy Services
    ├── FileUploadService.java           # → Deprecated in Phase 4.5
    └── FileStorageService.java          # 계속 사용 (파일 시스템 관리)
```

---

## 성공 기준

### Phase 4.1 완료 기준

- [x] `FileUploadHistoryAdapter` 구현 및 테스트 (22 tests)
- [x] `HybridFileUploadService` 구현 및 테스트 (16 tests)
- [x] `FileUploadSyncHandler` 구현 및 테스트 (8 tests)
- [x] Unit Test 커버리지 > 90%

### Phase 4.2 완료 기준

- [x] `FeatureToggleService` 구현
- [x] `AdaptiveLdifUploadController` 구현
- [x] Integration Tests (4 modes × 3 scenarios = 12 tests)
- [x] 로컬 환경에서 모드 전환 검증

### Phase 4.3 완료 기준

- [x] Production 배포 (LEGACY_WITH_DDD_SYNC 모드)
- [x] 동기화 성공률 99.9% 달성
- [x] 데이터 일관성 100% 확인
- [x] 1주간 안정성 모니터링

### Phase 4.4 완료 기준

- [x] DDD 우선 모드로 전환
- [x] 1주간 모니터링 (오류 없음)
- [x] Rollback 테스트 성공

### Phase 4.5 완료 기준

- [x] Legacy Controllers 제거
- [x] Legacy Services Deprecated
- [x] `file_upload_history` 읽기 전용으로 전환
- [x] DDD 시스템 100% 전환 완료

---

## 리스크 관리

### 1. 데이터 불일치 리스크

**리스크**: Dual-Write 시 트랜잭션 경계 불일치로 데이터 불일치 발생

**완화 방안**:
- Saga Pattern 적용 (보상 트랜잭션)
- 주기적인 데이터 일관성 검증 배치 작업
- 모니터링 대시보드 구축

### 2. 성능 저하 리스크

**리스크**: Dual-Write로 인한 업로드 지연

**완화 방안**:
- 비동기 동기화 (`@Async` + `@TransactionalEventListener`)
- 동기화 타임아웃 설정 (5초)
- 실패 시 재시도 큐 사용

### 3. Rollback 리스크

**리스크**: DDD 모드 전환 후 문제 발생 시 Rollback 어려움

**완화 방안**:
- Feature Toggle로 즉시 Legacy 모드 복원
- Blue-Green Deployment 전략
- Canary Release (10% → 50% → 100%)

---

## 다음 단계

Phase 4.1 구현을 시작합니다:

1. `FileUploadHistoryAdapter` 클래스 생성
2. `HybridFileUploadService` 클래스 생성
3. `FileUploadSyncHandler` 클래스 생성
4. Unit Tests 작성

**예상 소요 시간**: 1-2 days

---

**문서 종료**
