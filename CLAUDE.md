# Local PKD Evaluation Project - Development Guide

**Version**: 4.0
**Last Updated**: 2025-12-17
**Status**: Production Ready (PKD Upload Complete) + Passive Authentication Phase 4.5 (In Progress)

---

## 🎯 Quick Overview

### 1. PKD Upload Module (완료 ✅)
ICAO PKD 파일(Master List .ml, LDIF .ldif)을 업로드하여 인증서를 파싱, 검증 후 OpenLDAP에 저장하는 웹 애플리케이션입니다.

**핵심 기능**:
- ✅ 파일 업로드 (중복 감지, 서버 측 체크섬 검증)
- ✅ 비동기 파일 처리 (즉시 uploadId 반환)
- ✅ 파일 파싱 (LDIF, Master List CMS)
- ✅ 인증서 검증 (Trust Chain, CRL, 유효기간)
- ✅ OpenLDAP 자동 등록 (검증 상태 포함)
- ✅ 실시간 진행 상황 (uploadId별 SSE 스트림)
- ✅ 수동/자동 처리 모드 (Manual/Auto Mode)
- ✅ 업로드 이력 관리 (단계별 상태 추적)
- ✅ 단계별 진행 상태 UI (Upload → Parse → Validate → LDAP)
- ✅ 업로드 상태 자동 업데이트 (RECEIVED → PARSING → PARSED → UPLOADING_TO_LDAP → COMPLETED)
- ✅ 업로드 이력 페이지 4단계 상태 표시 (파싱/검증/LDAP 각각 체크마크)
- ✅ LDAP 검증 상태 기록 (VALID/INVALID/EXPIRED + 오류 메시지)
- ✅ 업로드 상세정보 통계 표시 (파싱/검증 통계, DaisyUI stats 컴포넌트)

### 2. Passive Authentication Module (진행 중 ⏳)
ePassport 검증을 위한 Passive Authentication (PA) 기능을 구현합니다.

**완료된 기능**:
- ✅ Phase 1: Domain Layer (16 files, ~2,500 LOC)
- ✅ Phase 2: Infrastructure Layer (5 files, ~940 LOC)
- ✅ Phase 3: Application Layer (Use Cases, DTOs)
- ✅ Phase 4.4: LDAP Integration Tests (6 tests, 100% pass)

**진행 중**:
- ⏳ Phase 4.5: PA UseCase Integration Tests (17 tests)
  - Trust Chain Verification (4 scenarios)
  - SOD Signature Verification (3 scenarios)
  - Data Group Hash Verification (3 scenarios)
  - CRL Check (3 scenarios)
  - Complete PA Flow (4 scenarios)

**Tech Stack**:
- Backend: Spring Boot 3.5.5, Java 21, PostgreSQL 15.14
- DDD Libraries: JPearl 2.0.1, MapStruct 1.6.3
- Frontend: Thymeleaf, Alpine.js 3.14.8, HTMX 2.0.4, DaisyUI 5.0
- Certificate: Bouncy Castle 1.70, UnboundID LDAP SDK

---

## 🏗️ DDD Architecture (현재 구조)

### Bounded Contexts (5개)

```
fileupload/              # File Upload Context (PKD 파일 업로드)
├── domain/
│   ├── model/           # Aggregates (UploadedFile) + Value Objects (11개)
│   ├── event/           # FileUploadedEvent, DuplicateFileDetectedEvent
│   ├── port/            # FileStoragePort (Hexagonal)
│   └── repository/      # UploadedFileRepository (Interface)
├── application/
│   ├── command/         # UploadLdifFileCommand, UploadMasterListFileCommand, CheckDuplicateFileCommand
│   ├── query/           # GetUploadHistoryQuery
│   ├── response/        # UploadFileResponse, CheckDuplicateResponse, ProcessingResponse
│   ├── service/         # AsyncUploadProcessor
│   ├── event/           # FileUploadEventHandler
│   └── usecase/         # 4개 Use Cases (CQRS)
└── infrastructure/
    ├── adapter/         # LocalFileStorageAdapter
    ├── web/             # UnifiedFileUploadController, ProcessingController (Manual Mode)
    └── repository/      # JPA Implementation + Event Publishing

fileparsing/             # File Parsing Context (PKD 파일 파싱)
├── domain/              # ParsedFile, ParsedCertificate, CertificateRevocationList
├── application/         # ParseLdifFileUseCase, ParseMasterListFileUseCase
└── infrastructure/      # LdifParserAdapter, MasterListParserAdapter

certificatevalidation/   # Certificate Validation Context (PKD 인증서 검증)
├── domain/              # Trust Chain, CRL Checking, Validation Logic, Certificate
├── application/         # ValidateCertificatesUseCase, UploadToLdapUseCase
└── infrastructure/      # BouncyCastleValidationAdapter, UnboundIdLdapConnectionAdapter

passiveauthentication/   # Passive Authentication Context (ePassport 검증) ⭐ NEW
├── domain/
│   ├── model/           # PassportData (Aggregate), DataGroup, SecurityObjectDocument (Value Objects)
│   ├── service/         # PassiveAuthenticationService (Domain Service)
│   ├── port/            # SodParserPort (Hexagonal)
│   └── repository/      # PassportDataRepository, PassiveAuthenticationAuditLogRepository
├── application/
│   ├── command/         # PerformPassiveAuthenticationCommand
│   ├── response/        # PassiveAuthenticationResponse, PassportVerificationDetailsResponse
│   └── usecase/         # PerformPassiveAuthenticationUseCase
└── infrastructure/
    ├── adapter/         # BouncyCastleSodParserAdapter
    ├── web/             # PassiveAuthenticationController (REST API)
    └── repository/      # JpaPassportDataRepository, JpaPassiveAuthenticationAuditLogRepository

shared/                  # Shared Kernel
├── domain/              # AbstractAggregateRoot, DomainEvent
├── event/               # EventBus, @EventListener, @Async
├── exception/           # DomainException, InfrastructureException, BusinessException
├── progress/            # ProcessingProgress, ProgressService (SSE), ProgressController
└── util/                # HashingUtil (SHA-256 checksum)
```

---

## 🆕 Recent Major Refactoring (2025-11-26)

### 1. Async Upload Processing Architecture

**이전 구조**: 동기식 업로드 → 긴 응답 시간
**새로운 구조**: 비동기 업로드 → 즉시 202 Accepted + uploadId 반환

```java
// UnifiedFileUploadController.java
@PostMapping("/upload")
public ResponseEntity<?> uploadFile(...) {
    UploadId uploadId = UploadId.newId(); // 즉시 ID 생성

    if (fileName.endsWith(".ldif")) {
        asyncUploadProcessor.processLdif(uploadId, ...); // 백그라운드 처리
    } else if (fileName.endsWith(".ml")) {
        asyncUploadProcessor.processMasterList(uploadId, ...);
    }

    return ResponseEntity.accepted()
        .body(new UploadAcceptedResponse("File processing started.", uploadId));
}
```

**AsyncUploadProcessor** (NEW):
- 서버 측 SHA-256 체크섬 계산 (클라이언트 부담 제거)
- 중복 파일 자동 감지 (forceUpload=false 시)
- @Async("taskExecutor") 비동기 처리
- UseCase로 위임하여 도메인 로직 실행

### 2. uploadId-specific SSE Streaming

**이전**: 모든 클라이언트에 브로드캐스트 → 혼선 발생
**개선**: 각 uploadId별 독립적인 SSE 스트림

```java
// ProgressController.java
@GetMapping("/stream/{uploadId}")
public SseEmitter streamProgress(@PathVariable UUID uploadId) {
    return progressService.createEmitter(uploadId);
}

// ProgressService.java
private final Map<UUID, SseEmitter> uploadIdToEmitters = new ConcurrentHashMap<>();
private final Map<UUID, ProcessingProgress> recentProgressCache = new ConcurrentHashMap<>();
```

**클라이언트 연결** (upload.html):
```javascript
this.sseEventSource = new EventSource(`/progress/stream/${this.uploadId}`);
this.sseEventSource.addEventListener('progress', (event) => this.handleProgressEvent(event));
```

### 3. Event Flow Simplification

**이전**: FileUploadedEvent → LdifParsingStartedEvent → CertificateValidationStartedEvent → ... (복잡한 이벤트 체인)
**개선**: 직접 메서드 호출 체인 (단순화)

```java
// FileUploadEventHandler.java
@Async("taskExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleFileUploadedAsync(FileUploadedEvent event) {
    // 1. Parse File (직접 호출)
    ParseFileResponse parseResponse = parseFile(uploadedFile);

    // 2. Validate Certificates (직접 체인)
    if (parseResponse.success() && parseResponse.certificateCount() > 0) {
        ParsedFile parsedFile = parsedFileRepository.findByUploadId(...);
        validateCertificates(uploadId, parsedFile.getId(), ...);
    }

    // TODO: 3. Upload to LDAP (Phase 19에서 체인 완성 예정)
}
```

**장점**:
- 이벤트 발행/구독 오버헤드 제거
- 트랜잭션 경계 명확화
- 디버깅 용이성 향상
- 코드 흐름 직관적

### 4. Manual/Auto Processing Mode

**새로운 기능**: 사용자가 단계별 처리를 수동으로 제어

**AUTO 모드** (기본):
- 업로드 → 파싱 → 검증 → LDAP 업로드 (자동 진행)
- FileUploadEventHandler가 체인 실행

**MANUAL 모드**:
- 업로드 후 각 단계마다 사용자가 버튼 클릭
- ProcessingController REST API로 단계 트리거
- SSE로 진행 상황 실시간 수신

```java
// ProcessingController.java
@PostMapping("/parse/{uploadId}")      // 파싱 시작
@PostMapping("/validate/{uploadId}")   // 검증 시작
@PostMapping("/upload-to-ldap/{uploadId}")  // LDAP 업로드 시작
```

**UI 제어** (upload.html):
```html
<button @click="triggerParse()">파싱 시작</button>
<button :disabled="!parsingCompleted" @click="triggerValidate()">검증 시작</button>
<button :disabled="!validationCompleted" @click="triggerLdapUpload()">LDAP 저장</button>
```

### 5. Enhanced UI with Stage-specific Progress

**4단계 진행 상황 시각화**:
1. **Upload Stage** (파일 업로드)
2. **Parse Stage** (파싱)
3. **Validate Stage** (인증서 검증)
4. **LDAP Stage** (LDAP 서버 저장)

**각 단계별 정보**:
- Progress bar (0-100%)
- Status icon (진행 중 / 완료 / 실패)
- Message (현재 작업 내용)
- Error messages (실패 시)

**Alpine.js State Management**:
```javascript
uploadStage: { active: false, message: '', percentage: 0, status: '' },
parseStage: { active: false, message: '', percentage: 0, status: '' },
validateStage: { active: false, message: '', percentage: 0, status: '' },
ldapStage: { active: false, message: '', percentage: 0, status: '' },

handleProgressEvent(event) {
    const progress = JSON.parse(event.data);
    switch (progress.step) {
        case 'PARSE': this.parseStage = { ... }; break;
        case 'VALIDATE': this.validateStage = { ... }; break;
        // ...
    }
}
```

---

## 📋 Critical Coding Rules (필수 준수)

### 1. Value Object 작성 규칙

```java
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA용 (필수!)
public class CollectionNumber {
    private String value;  // ❌ final 금지 (JPA가 값 설정 불가)

    // ✅ 정적 팩토리 메서드
    public static CollectionNumber of(String value) {
        return new CollectionNumber(value);
    }

    // ✅ Private 생성자 + 검증
    private CollectionNumber(String value) {
        validate(value);
        this.value = value;
    }

    // ✅ 비즈니스 규칙 검증
    private void validate(String value) {
        if (value == null || !value.matches("^\\d{3}$")) {
            throw new DomainException("INVALID_COLLECTION", "...");
        }
    }
}
```

**핵심 요구사항**:
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` - Hibernate 필수
- 필드는 **non-final** - JPA 리플렉션 값 주입용
- `@Embeddable` 어노테이션
- 정적 팩토리 메서드 (of, from, extractFrom)
- Self-validation (생성 시점 검증)
- 값 기반 동등성 (`@EqualsAndHashCode`)

### 2. Aggregate Root 작성 규칙

```java
@Entity
@Table(name = "uploaded_file")
public class UploadedFile extends AbstractAggregateRoot<UploadId> {
    @EmbeddedId
    private UploadId id;  // JPearl 타입 안전 ID

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "file_name"))
    private FileName fileName;

    // ✅ Protected 기본 생성자 (JPA용)
    protected UploadedFile() {}

    // ✅ 정적 팩토리 메서드 (Domain Event 발행)
    public static UploadedFile create(...) {
        UploadedFile file = new UploadedFile(...);
        file.registerEvent(new FileUploadedEvent(file.getId()));
        return file;
    }
}
```

### 3. 예외 처리 규칙

```java
// ✅ Domain Layer
throw new DomainException("INVALID_FILE_FORMAT", "파일 형식이 올바르지 않습니다");

// ✅ Application Layer
throw new BusinessException("DUPLICATE_FILE", "중복 파일이 감지되었습니다", details);

// ✅ Infrastructure Layer
throw new InfrastructureException("FILE_SAVE_ERROR", "파일 저장 중 오류: " + e.getMessage());

// ❌ 절대 사용 금지
throw new IllegalArgumentException("Invalid");  // ❌
throw new RuntimeException("Error");  // ❌
```

### 4. Async Processing 규칙

```java
// ✅ @Async 메서드 작성
@Async("taskExecutor")  // 명시적 Executor 지정
public void processLdif(UploadId uploadId, ...) {
    try {
        // 비즈니스 로직 실행
        UploadLdifFileCommand command = ...;
        uploadLdifFileUseCase.execute(command);
    } catch (Exception e) {
        // SSE로 실패 상태 전송
        progressService.sendProgress(
            ProcessingProgress.failed(uploadId.getId(), ProcessingStage.UPLOAD_COMPLETED, e.getMessage())
        );
    }
}
```

**주의사항**:
- 반환 타입은 `void` 또는 `CompletableFuture<T>`
- Exception 처리 필수 (ProgressService로 실패 전송)
- @TransactionalEventListener와 함께 사용 시 phase 명시

---

## 🛠️ MCP Tools 활용 가이드 (효율적 개발)

**연결된 MCP 서버**: Serena (코드 분석), Filesystem, Context7, Sequential Thinking, Memory, Playwright

**⚠️ CRITICAL**: 모든 작업 시작 전 반드시 Serena MCP를 활성화하여 사용하세요.

### 0. Serena MCP - Semantic Code Analysis (최우선 사용)

```java
// ✅ 프로젝트 활성화 (작업 시작 시 필수)
mcp__serena__activate_project(project="local-pkd")

// ✅ 심볼 검색 (클래스, 메서드, 필드 찾기)
mcp__serena__find_symbol(
    name_path_pattern="UploadedFile",  // 클래스명
    relative_path="",                   // 전체 검색
    include_body=false,                 // 시그니처만
    depth=1                             // 메서드 포함
)

// ✅ 파일 심볼 개요 (파일 구조 파악)
mcp__serena__get_symbols_overview(
    relative_path="src/main/java/com/smartcoreinc/localpkd/fileupload/domain/model/UploadedFile.java",
    depth=1
)

// ✅ 심볼 참조 찾기 (어디서 사용되는지)
mcp__serena__find_referencing_symbols(
    name_path="UploadedFile/create",
    relative_path="src/main/java/com/smartcoreinc/localpkd/fileupload/domain/model/UploadedFile.java"
)

// ✅ 패턴 검색 (코드 내용 검색)
mcp__serena__search_for_pattern(
    substring_pattern="@SpringBootTest",
    relative_path="src/test/java",
    restrict_search_to_code_files=true
)

// ✅ 심볼 본문 교체 (메서드/클래스 전체 교체)
mcp__serena__replace_symbol_body(
    name_path="UploadedFile/create",
    relative_path="...",
    body="public static UploadedFile create(...) { ... }"
)

// ✅ 파일 읽기 (일반 파일)
mcp__serena__read_file(
    relative_path="src/main/resources/application.properties",
    start_line=0,
    end_line=50
)
```

**사용 우선순위**:

1. **심볼 기반 작업** → Serena MCP 사용 (클래스, 메서드 찾기/수정)
2. **일반 파일 작업** → Filesystem MCP 사용
3. **외부 문서** → Context7 MCP 사용

### 1. Filesystem 작업

```python
# ✅ 파일 읽기 (대용량 파일도 처리)
mcp__filesystem__read_text_file(path, head=100)  # 앞 100줄
mcp__filesystem__read_text_file(path, tail=50)   # 뒤 50줄

# ✅ 파일 쓰기 (항상 절대 경로 사용)
mcp__filesystem__write_file(path="/absolute/path/file.java", content="...")

# ✅ 디렉토리 검색
mcp__filesystem__search_files(path="/src", pattern="*.java", excludePatterns=["*Test.java"])

# ✅ 파일 정보 조회
mcp__filesystem__get_file_info(path="/path/file.java")
```

### 2. Context7 - 라이브러리 문서 조회

```java
# Step 1: 라이브러리 ID 확인
mcp__context7__resolve_library_id(libraryName="spring boot")

# Step 2: 문서 조회
mcp__context7__get_library_docs(
    context7CompatibleLibraryID="/spring/boot",
    topic="actuator",
    page=1
)
```

### 3. Sequential Thinking - 복잡한 문제 분석

```java
mcp__sequential_thinking__sequentialthinking(
    thought="1단계: 문제 분석...",
    thoughtNumber=1,
    totalThoughts=5,
    nextThoughtNeeded=true
)
```

### 4. Memory - 프로젝트 지식 저장

```java
# Entity 생성
mcp__memory__create_entities(entities=[{
    "name": "AsyncRefactoring2025-11",
    "entityType": "Development Phase",
    "observations": ["비동기 업로드 처리 구현", "uploadId별 SSE 스트림 분리", "이벤트 체인 단순화"]
}])

# Relation 생성
mcp__memory__create_relations(relations=[{
    "from": "AsyncRefactoring2025-11",
    "to": "ProgressService",
    "relationType": "refactors"
}])

# 검색
mcp__memory__search_nodes(query="async processing")
```

### 5. Playwright - E2E 테스트

```java
# 브라우저 시작
mcp__playwright__browser_navigate(url="http://localhost:8081/file/upload")

# 파일 업로드 시뮬레이션
mcp__playwright__browser_file_upload(paths=["/path/to/test.ldif"])

# SSE 연결 확인
mcp__playwright__browser_snapshot()  # UI 상태 캡처
```

---

## 📚 Key Documents (읽어야 할 문서)

| 문서 | 용도 | 위치 |
|------|--------|------|
| **PROJECT_SUMMARY** | 프로젝트 전체 개요 (DB, API, 완료 Phase) | docs/PROJECT_SUMMARY_2025-11-21.md |
| **TODO_ANALYSIS** | 105개 TODO 분석 (High/Medium/Low 우선순위) | docs/TODO_ANALYSIS.md |
| **CODE_CLEANUP_REPORT** | 최근 코드 정리 내역 (제거 파일, 빌드 결과) | docs/CODE_CLEANUP_REPORT_2025-11-21.md |
| **PHASE_17** | Event-Driven LDAP Upload 완료 보고서 | docs/PHASE_17_COMPLETE.md |
| **PHASE_DSC_NC** | Non-Conformant Certificate 구현 완료 | docs/PHASE_DSC_NC_IMPLEMENTATION_COMPLETE.md |
| **PHASE_19** | LDAP 검증 상태 기록 구현 완료 (NEW) | docs/MASTER_LIST_LDAP_VALIDATION_STATUS.md |
| **MASTER_LIST_UPLOAD_REPORT** | Master List 업로드 테스트 결과 | docs/MASTER_LIST_UPLOAD_REPORT_2025-11-21.md |
| **MASTER_LIST_STORAGE_ANALYSIS** | Master List 구조 및 저장 전략 분석 | docs/MASTER_LIST_LDAP_STORAGE_ANALYSIS.md |

**아카이브**: `docs/archive/phases/` (Phase 1-16 문서 50개)

---

## 🌳 LDAP DIT Structure & Processing Rules (ICAO PKD 표준)

### 1. LDAP Directory Information Tree (DIT) 구조

#### 1.1 ML File CSCAs (ICAO/UN Root 서명 인증서 모음)
```
DN: cn={SUBJECT-DN}+sn={SERIAL},o=csca,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}

예시:
cn=CN\=CSCA-KOREA\,O\=Government\,C\=KR+sn=A1B2C3D4,o=csca,c=KR,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com

ObjectClasses:
- inetOrgPerson
- pkdDownload
- pkdMasterList
- organizationalPerson
- top
- person

Attributes:
- cn: {SUBJECT-DN}
- sn: {SERIAL-NUMBER}
- userCertificate;binary: {BASE64-ENCODED-CERTIFICATE}
- pkdVersion: 1150
- description: {VALIDATION-STATUS} (VALID/INVALID/EXPIRED + error messages)
```

**처리 규칙**:
- ✅ ML 파일에서 520개 CSCA 인증서 추출
- ✅ `certificate` 테이블에 저장 (`masterListId = null`, `sourceType = MASTER_LIST`)
- ✅ LDAP에 개별 인증서로 업로드 (`o=csca`)
- ❌ `master_list` 테이블 생성 금지 (ML 파일은 Master List가 아님)

**구현 위치**: ParseMasterListFileUseCase.java:145-184, LdifConverter.java:79-143

---

#### 1.2 LDIF Master List (국가별 CMS SignedData)
```
DN: cn={CSCA-DN},o=ml,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}

예시:
cn=CN\=CSCA-FRANCE\,O\=Gouv\,C\=FR,o=ml,c=FR,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com

ObjectClasses:
- top
- person
- pkdMasterList
- pkdDownload

Attributes:
- cn: {CSCA-DN}
- sn: {SERIAL-NUMBER}
- pkdMasterListContent: {BASE64-ENCODED-CMS-BINARY}
- pkdVersion: 70
```

**처리 규칙**:
- ✅ LDIF 파일에서 국가별 Master List 추출
- ✅ `master_list` 테이블에 저장 (CMS 바이너리 보존)
- ✅ Master List에서 개별 CSCA 추출 → `certificate` 테이블 저장 (`masterListId = non-null`, `sourceType = MASTER_LIST`)
- ✅ LDAP에 Master List CMS 바이너리 업로드 (`o=ml`)
- ⚠️ 개별 CSCA는 통계/분석용으로만 사용, LDAP에 중복 업로드 (현재 구현)
  - **참고**: LDIF Master List CSCAs는 이미 Master List binary에 포함되어 있으므로 개별 업로드 불필요
  - 현재는 Master List binary + 개별 CSCAs 모두 업로드 (향후 최적화 가능)

**구현 위치**: LdifParserAdapter.java:166-242, LdifConverter.java:225-277, UploadToLdapUseCase.java:213-262

---

#### 1.3 DSC (Document Signer Certificates)
```
DN: cn={SUBJECT-DN}+sn={SERIAL},o=dsc,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}

예시:
cn=OU\=Identity Services Passport CA\,OU\=Passports\,O\=Government of New Zealand\,C\=NZ+sn=42E575AF,o=dsc,c=NZ,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com

ObjectClasses:
- inetOrgPerson
- pkdDownload
- organizationalPerson
- top
- person

Attributes:
- cn: {SUBJECT-DN}
- sn: {SERIAL-NUMBER}
- userCertificate;binary: {BASE64-ENCODED-CERTIFICATE}
- pkdVersion: 1150
- description: {VALIDATION-STATUS}
```

**처리 규칙**:
- ✅ LDIF 파일에서 DSC 인증서 추출
- ✅ `certificate` 테이블에 저장 (`sourceType = LDIF_DSC`)
- ✅ LDAP에 업로드 (`o=dsc`)
- ✅ Trust Chain 검증 필수 (CSCA 조회 후 서명 검증)

**구현 위치**: LdifParserAdapter.java, LdifConverter.java:79-143

---

#### 1.4 DSC Non-Conformant (비표준 DSC)
```
DN: cn={SUBJECT-DN}+sn={SERIAL},o=dsc,c={COUNTRY},dc=nc-data,dc=download,dc=pkd,{baseDN}

차이점:
- dc=nc-data (비표준 데이터 계층)
- sourceType = LDIF_DSC_NC
```

**처리 규칙**:
- ✅ `dc=nc-data` 계층으로 분리 저장
- ✅ 검증 규칙은 일반 DSC와 동일

**구현 위치**: LdifConverter.java:96

---

#### 1.5 CRL (Certificate Revocation Lists)
```
DN: cn={ISSUER-NAME},o=crl,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}

예시:
cn=CN\=CSCA-KOREA\,O\=Government\,C\=KR,o=crl,c=KR,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com

ObjectClasses:
- top
- cRLDistributionPoint

Attributes:
- cn: {ISSUER-NAME}
- certificateRevocationList;binary: {BASE64-ENCODED-CRL}
```

**처리 규칙**:
- ✅ LDIF 파일에서 CRL 추출
- ✅ `certificate_revocation_list` 테이블에 저장
- ✅ LDAP에 업로드 (`o=crl`)

**구현 위치**: LdifParserAdapter.java, LdifConverter.java:152-191

---

### 2. File Parsing Rules (ML vs LDIF)

#### 2.1 ML File (.ml) - ICAO/UN Root 서명 CSCA 모음

**파일 특성**:
- ICAO/UN Root CA가 서명한 전 세계 CSCA 인증서 520개 모음
- CMS SignedData (PKCS#7) 형식
- 개별 국가의 Master List가 **아님**

**파싱 프로세스**:
```java
// ParseMasterListFileUseCase.java:145-184
1. MasterListParser로 CMS SignedData 파싱
2. 520개 CSCA 인증서 추출
3. Certificate.createFromMasterList(uploadId, null, ...) 호출
   - masterListId = null (MasterList 엔티티 없음)
   - sourceType = MASTER_LIST
4. certificate 테이블에 일괄 저장 (saveAll)
5. master_list 테이블에는 저장 ❌
```

**데이터 저장**:
- ✅ `certificate` 테이블: 520개 CSCA (개별 레코드)
- ❌ `master_list` 테이블: 저장 안 함

**LDAP 업로드**:
- ✅ 개별 CSCA 인증서 → `o=csca,c={COUNTRY}`

---

#### 2.2 LDIF File (.ldif) - 국가별 Master List + DSC + CRL

**파일 특성**:
- 국가별 Master List (CMS SignedData) + DSC + CRL 포함
- Master List는 각 국가 CSCA가 서명
- LDIF 형식 (LDAP Data Interchange Format)

**파싱 프로세스**:
```java
// LdifParserAdapter.java:166-242
1. LDIF 엔트리 순회
2. Master List 발견 시:
   a. MasterList 엔티티 생성 (CMS binary 보존)
   b. master_list 테이블에 저장
   c. CMS SignedData 파싱하여 개별 CSCA 추출
   d. Certificate.createFromMasterList(uploadId, masterListId, ...) 호출
      - masterListId = Master List ID (non-null)
      - sourceType = MASTER_LIST
   e. certificate 테이블에 저장 (통계/분석용)
   f. ParsedFile에도 CertificateData 추가 (검증용)
3. DSC 발견 시:
   a. Certificate.createFromLdif(uploadId, DSC, ...) 호출
      - sourceType = LDIF_DSC
   b. certificate 테이블에 저장
4. CRL 발견 시:
   a. CertificateRevocationList 엔티티 생성
   b. certificate_revocation_list 테이블에 저장
```

**데이터 저장**:
- ✅ `master_list` 테이블: 27개 국가별 Master List (CMS binary)
- ✅ `certificate` 테이블:
  - 28개 CSCA (Master List에서 추출, `masterListId = non-null`)
  - N개 DSC (`sourceType = LDIF_DSC`)
- ✅ `certificate_revocation_list` 테이블: N개 CRL

**LDAP 업로드**:
- ✅ Master List CMS binary → `o=ml,c={COUNTRY}`
- ✅ 개별 CSCA → `o=csca,c={COUNTRY}` (현재 중복 업로드)
- ✅ DSC → `o=dsc,c={COUNTRY}`
- ✅ CRL → `o=crl,c={COUNTRY}`

---

### 3. Certificate Validation Rules (Two-Pass Validation)

#### 3.1 Pass 1: CSCA Validation (Self-Signed)

**대상**: `certificateType = CSCA`

**검증 항목**:
```java
// ValidateCertificatesUseCase.java:368-453
1. ✅ Self-Signed Signature 검증
   - x509Cert.verify(x509Cert.getPublicKey())
   - 자기 자신의 공개키로 서명 검증

2. ✅ Validity Period 검증
   - x509Cert.checkValidity()
   - notBefore <= 현재시간 <= notAfter

3. ✅ Basic Constraints 검증
   - x509Cert.getBasicConstraints() != -1
   - CA 인증서 여부 확인 (cA=TRUE)
```

**검증 결과**:
- ✅ **VALID**: 모든 검증 통과
- ⚠️ **INVALID**: 하나 이상의 검증 실패
  - SIGNATURE_INVALID: 서명 검증 실패
  - VALIDITY_INVALID: 유효기간 만료
  - CONSTRAINTS_INVALID: Basic Constraints 위반
- ⏰ **EXPIRED**: 유효기간 만료

**데이터베이스 기록**:
- `status`: VALID/INVALID/EXPIRED
- `validation_errors`: JSON array of ValidationError
- `validated_at`: 검증 완료 시각

---

#### 3.2 Pass 2: DSC Validation (Trust Chain)

**대상**: `certificateType = DSC` or `DSC_NC`

**검증 항목**:
```java
// ValidateCertificatesUseCase.java:473-534
1. ✅ Trust Chain 검증
   a. Issuer DN으로 CSCA 조회
      - certificateRepository.findBySubjectDn(issuerDN)
   b. CSCA 공개키로 DSC 서명 검증
      - x509Cert.verify(cscaX509.getPublicKey())
   c. CSCA 미발견 시 INVALID 처리
      - ValidationError.critical("CHAIN_INCOMPLETE", "CSCA not found")

2. ✅ Validity Period 검증
   - x509Cert.checkValidity()

3. ✅ Basic Constraints 검증
   - getBasicConstraints() >= 0 (CA 가능)
   - 또는 == -1 (End-Entity 인증서)
```

**검증 순서**:
1. **Pass 1 먼저 실행** → 모든 CSCA 검증 완료
2. **Pass 2 실행** → DSC가 CSCA를 찾아서 Trust Chain 검증

**검증 결과**:
- ✅ **VALID**: Trust Chain + Validity + Constraints 모두 통과
- ⚠️ **INVALID**: 하나 이상 실패
  - CHAIN_INCOMPLETE: CSCA 미발견
  - SIGNATURE_INVALID: CSCA 서명 검증 실패
  - VALIDITY_INVALID: 유효기간 문제
  - CONSTRAINTS_INVALID: Basic Constraints 위반

---

### 4. Certificate Source Type 구분

```java
public enum CertificateSourceType {
    MASTER_LIST,    // ML 파일 CSCA (masterListId=null) 또는 LDIF Master List CSCA (masterListId=non-null)
    LDIF_DSC,       // LDIF 파일 DSC
    LDIF_CSCA       // LDIF 파일 개별 CSCA (현재 미사용)
}
```

**구분 기준**:

| Source Type | masterListId | 파일 유형 | LDAP DN | 설명 |
|-------------|--------------|-----------|---------|------|
| MASTER_LIST (null) | null | ML file | o=csca | ICAO/UN Root 서명 CSCA |
| MASTER_LIST (non-null) | UUID | LDIF Master List | o=csca (현재 중복) | 국가별 Master List CSCA |
| LDIF_DSC | - | LDIF file | o=dsc | Document Signer Certificate |

**활용**:
```java
// Certificate 엔티티
public boolean isFromMasterList() {
    return sourceType == CertificateSourceType.MASTER_LIST;
}

public boolean isFromLdif() {
    return sourceType != null && sourceType.isFromLdif();
}

// 구분 로직
if (cert.getMasterListId() == null) {
    // ML file CSCA → LDAP 개별 업로드 필요
} else {
    // LDIF Master List CSCA → 이미 Master List binary에 포함 (개별 업로드 선택적)
}
```

---

### 5. LDAP Upload Strategy Summary

| Item | Source | Database Table | LDAP DN | Upload Status |
|------|--------|----------------|---------|---------------|
| ML file CSCA (520개) | ML file | `certificate` | `o=csca,c={COUNTRY}` | ✅ Individual Upload |
| LDIF Master List CMS | LDIF file | `master_list` | `o=ml,c={COUNTRY}` | ✅ Binary Upload |
| LDIF Master List CSCA | LDIF Master List | `certificate` | `o=csca,c={COUNTRY}` | ⚠️ Duplicate Upload (선택적) |
| DSC | LDIF file | `certificate` | `o=dsc,c={COUNTRY}` | ✅ Individual Upload |
| CRL | LDIF file | `certificate_revocation_list` | `o=crl,c={COUNTRY}` | ✅ Individual Upload |

**최적화 권장사항** (향후):
- LDIF Master List CSCAs는 이미 Master List binary (`o=ml`)에 포함되어 있으므로 개별 업로드 (`o=csca`) 불필요
- 현재는 통계/분석 및 검증 용도로 모두 업로드 중
- 필요 시 `masterListId != null` 조건으로 필터링 가능

**구현 위치**: UploadToLdapUseCase.java:108-163

---

## 💾 Database Schema (현재 상태)

### 주요 테이블 (3개)

```sql
-- 1. uploaded_file (파일 업로드 이력)
CREATE TABLE uploaded_file (
    id UUID PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_hash VARCHAR(64) NOT NULL UNIQUE,
    file_size_bytes BIGINT NOT NULL CHECK (file_size_bytes > 0 AND file_size_bytes <= 104857600),
    file_format VARCHAR(50) NOT NULL,
    collection_number VARCHAR(10),
    version VARCHAR(50),
    uploaded_at TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL,
    processing_mode VARCHAR(10) DEFAULT 'AUTO',  -- NEW: AUTO/MANUAL
    is_duplicate BOOLEAN DEFAULT FALSE
);

-- 2. parsed_certificate (파싱된 인증서)
CREATE TABLE parsed_certificate (
    id UUID PRIMARY KEY,
    upload_id UUID NOT NULL REFERENCES uploaded_file(id),
    certificate_type VARCHAR(20) NOT NULL,  -- CSCA, DSC, DSC_NC
    country_code VARCHAR(3) NOT NULL,
    subject VARCHAR(500),
    issuer VARCHAR(500),
    serial_number VARCHAR(100),
    not_before TIMESTAMP,
    not_after TIMESTAMP,
    encoded BYTEA NOT NULL,
    validation_status VARCHAR(20) DEFAULT 'PENDING'
);

-- 3. certificate_revocation_list (CRL)
CREATE TABLE certificate_revocation_list (
    id UUID PRIMARY KEY,
    upload_id UUID NOT NULL REFERENCES uploaded_file(id),
    issuer_name VARCHAR(500) NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    this_update TIMESTAMP NOT NULL,
    next_update TIMESTAMP,
    encoded BYTEA NOT NULL
);
```

**Indexes**: file_hash (unique), uploaded_at, status, country_code, validation_status

**Flyway Migrations**: V1 ~ V13 (완료)

---

## 🚀 Build & Run

### 1. 컨테이너 시작 (PostgreSQL)

```bash
./podman-start.sh
# PostgreSQL: localhost:5432 (postgres/secret)
# pgAdmin: http://localhost:5050
```

### 2. 빌드 (Maven)

```bash
./mvnw clean compile
# BUILD SUCCESS in ~7s
# 184+ source files
```

### 3. 테스트 실행

```bash
./mvnw test
# Tests run: 202+, Failures: 0
```

### 4. 애플리케이션 실행

```bash
./mvnw spring-boot:run
# Started LocalPkdApplication in ~8 seconds
# Tomcat started on port(s): 8081 (http)
# WSL2: accessible from Windows at http://172.x.x.x:8081
```

### 5. Health Check

```bash
curl http://localhost:8081/actuator/health
# {"status":"UP"}
```

### 6. WSL2 Network Access (Windows)

**문제**: localhost:8081이 Windows Chrome에서 접근 안 됨
**해결**: `application.properties`에 `server.address=0.0.0.0` 설정 완료

**Windows에서 접속**:
```bash
# WSL IP 확인
hostname -I  # 예: 172.24.1.6

# Windows Chrome에서
http://172.24.1.6:8081
```

---

## 📊 Current Status (2025-12-05)

### Completed Phases ✅

| Phase | 내용 | 상태 |
|-------|------|------|
| Phase 1-4 | Project Setup, DDD Foundation | ✅ |
| Phase 5-10 | File Upload, Parsing, Validation | ✅ |
| Phase 11-13 | Certificate/CRL Aggregates, Trust Chain | ✅ |
| Phase 14-16 | LDAP Integration, Event-Driven | ✅ |
| Phase 17 | Event-Driven LDAP Upload Pipeline | ✅ |
| Phase 18 | UI Improvements, Dashboard | ✅ |
| Phase DSC_NC | Non-Conformant Certificate Support | ✅ |
| **Async Refactoring** | **비동기 업로드, SSE 개선, Manual Mode** | ✅ |
| **Phase 19** | **LDAP 검증 상태 기록 (description attribute)** | ✅ |
| **Upload Status Tracking** | **단계별 상태 자동 업데이트, 업로드 이력 4단계 표시** | ✅ **NEW** |

### Recent Refactoring (2025-11-26 ~ 2025-12-05) ✅

1. ✅ **AsyncUploadProcessor 도입** - 즉시 uploadId 반환, 백그라운드 처리
2. ✅ **uploadId별 SSE 스트림** - 개별 진행 상황 추적
3. ✅ **이벤트 체인 단순화** - 직접 메서드 호출로 변경
4. ✅ **Manual/Auto Mode** - 단계별 수동 제어 기능
5. ✅ **UI 대폭 개선** - 4단계 진행 상황 시각화
6. ✅ **서버 측 체크섬** - 클라이언트 부담 제거
7. ✅ **WSL2 네트워크 지원** - Windows Chrome 접근 가능
8. ✅ **실제 LDAP 업로드 구현** (2025-11-27) - ICAO PKD LDIF 형식 준수, 시뮬레이션 제거
9. ✅ **LDAP 검증 상태 기록** (2025-11-28) - description attribute에 VALID/INVALID/EXPIRED + 오류 메시지 포함
10. ✅ **업로드 상태 자동 업데이트** (2025-12-03) - UploadedFile 엔티티 상태가 처리 단계별로 자동 업데이트 (RECEIVED → PARSING → PARSED → UPLOADING_TO_LDAP → COMPLETED)
11. ✅ **업로드 이력 페이지 개선** (2025-12-03) - 파싱/검증/LDAP 각각의 상태를 개별 컬럼으로 표시, 완료된 단계는 체크마크 표시
12. ✅ **SSE 진행 상황 상세화** (2025-12-03) - 각 단계별 인증서 타입, 유효성 통계를 포함한 상세 정보 표시
13. ✅ **중복 인증서 감사 추적 지원** (2025-12-05) - parsed_certificate PK를 (parsed_file_id, fingerprint_sha256)로 변경하여 주기적 PKD 업데이트 시 중복 인증서 이력 추적 가능
14. ✅ **데이터베이스 마이그레이션 통합** (2025-12-05) - 10개 마이그레이션 파일 (V1-V17, 958 라인)을 단일 V1__Initial_Schema.sql (465 라인)로 통합, ALTER 문 완전 제거, 32개 누락 컬럼 추가, SSE 오류 수정 (상세 내역: [SESSION_2025-12-05_MIGRATION_CONSOLIDATION.md](docs/SESSION_2025-12-05_MIGRATION_CONSOLIDATION.md))
15. ✅ **업로드 통계 기능 구현** (2025-12-05) - 업로드 상세정보 dialog에 파싱 통계(인증서 타입별, CRL, Master List) 및 검증 통계(총 검증, 유효, 무효, 만료) 추가, 4개 repository에 uploadId 기반 count 메서드 구현, DaisyUI stats 컴포넌트로 시각화 (상세 내역: [SESSION_2025-12-05_UPLOAD_STATISTICS.md](docs/SESSION_2025-12-05_UPLOAD_STATISTICS.md))
16. ✅ **CRL 영속화 및 UI 오류 수정** (2025-12-11 **NEW**) - CRL이 파싱되지만 DB에 저장되지 않던 문제 해결 (ValidateCertificatesUseCase.java에 CRL 영속화 로직 구현, 배치 저장, SSE 진행 상황 추가), 대시보드 차트 인스턴스 미선언 오류 수정, 차트 생성/색상 업데이트 메서드에 에러 핸들링 추가, 업로드 이력 페이지 darkMode 변수 참조 오류 수정 (4개 UI 오류 해결) (상세 내역: [SESSION_2025-12-11_CRL_PERSISTENCE_AND_UI_FIXES.md](docs/SESSION_2025-12-11_CRL_PERSISTENCE_AND_UI_FIXES.md))

### Current Phase: Passive Authentication Phase 4.5

**목표**: PA UseCase Integration Tests 구현 (17 tests)

**진행 상황**:
- ⏳ Phase 4.5.1: Trust Chain Verification Tests (4 scenarios)
- ⏳ Phase 4.5.2: SOD Verification Tests (3 scenarios)
- ⏳ Phase 4.5.3: Data Group Hash Verification Tests (3 scenarios)
- ⏳ Phase 4.5.4: CRL Check Tests (3 scenarios)
- ⏳ Phase 4.5.5: Complete PA Flow Tests (4 scenarios)

**상세 내역**: [TODO_PHASE_4_5_PASSIVE_AUTHENTICATION.md](docs/TODO_PHASE_4_5_PASSIVE_AUTHENTICATION.md)

### PKD Upload Module - Remaining TODOs (Optional)

1. ✅ ~~**FileUploadEventHandler.java:92** - LDAP 업로드 체인 연결~~ **COMPLETED (2025-11-27)**
2. ✅ ~~**LdifConverter - LDAP 검증 상태 기록**~~ **COMPLETED (2025-11-28)**
3. **ProcessingController.java:141-143** - Manual Mode Use Cases 구현 (Low Priority)
4. **ProcessingController.java:358-369** - 처리 상태 DB 조회 구현 (Low Priority)
5. **LdifConverter** - 단위 테스트 작성 (Optional)
6. **UploadToLdapUseCase** - 통합 테스트 작성 (Optional)

### Future Enhancements (Optional)

**PKD Module**:
- Manual Mode 완성 (ValidateCertificatesUseCase, UploadToLdapUseCase 호출)
- 고급 검색 & 필터링 (Full-Text Search, Elasticsearch)
- 모니터링 & 운영 (Prometheus, Grafana, Alerts)
- LDAP 검증 상태 모니터링 Dashboard (Validation Statistics)

**PA Module**:
- Phase 4.6: REST API Controller Integration Tests
- Phase 4.7: Performance Testing & Optimization
- Phase 5: UI Integration (Dashboard, Search)
- Phase 6: Active Authentication Support

---

## 🔧 Troubleshooting

### 1. 빌드 오류

```bash
# 포트 충돌 (8081)
lsof -ti:8081 | xargs kill -9

# 컨테이너 재시작
./podman-restart.sh

# 완전 초기화
./podman-clean.sh && ./podman-start.sh
```

### 2. Flyway Migration 오류

```bash
# 마이그레이션 히스토리 확인
psql -h localhost -U postgres -d icao_local_pkd
\dt flyway_schema_history

# 마이그레이션 재실행
./mvnw flyway:clean flyway:migrate
```

### 3. Value Object JPA 오류

```
Error: Unable to instantiate value object
```

**해결책**: `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 확인, 필드는 non-final

### 4. SSE 연결 오류

```
SSE connection failed
```

**확인 사항**:
1. ProgressController `/progress/stream/{uploadId}` 엔드포인트 작동 확인
2. uploadId가 올바른 UUID 형식인지 확인
3. 브라우저 개발자 도구 Network 탭에서 EventStream 연결 상태 확인
4. CORS 설정 확인 (필요 시)

### 5. WSL2 Windows 접근 문제

```
Windows Chrome: "사이트에 연결할 수 없음"
```

**해결**:
1. `application.properties`에 `server.address=0.0.0.0` 설정 확인
2. WSL IP 주소 확인: `hostname -I`
3. Windows 방화벽에서 8081 포트 허용 확인
4. Windows에서 `http://<WSL-IP>:8081` 접속

### 6. LDAP Base DN 삭제 복구

```
LDAP Error: No such object (32)
Apache Directory Studio: Base DN이 사라짐
```

**긴급 복구 (30초)**:

```bash
cd /home/kbjung/projects/java/smartcore/local-pkd
./scripts/restore-ldap.sh
# 비밀번호 입력: core
```

**또는 한 줄 명령어**:

```bash
ldapadd -x -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" -w "core" \
    -f scripts/restore-base-dn.ldif
```

**상세 매뉴얼**:

- 빠른 참조: [scripts/QUICK_RECOVERY.txt](scripts/QUICK_RECOVERY.txt)
- 전체 가이드: [scripts/RECOVERY_MANUAL.md](scripts/RECOVERY_MANUAL.md)
- 기술 문서: [docs/LDAP_BASE_DN_RECOVERY.md](docs/LDAP_BASE_DN_RECOVERY.md)

**복구 후 확인**:

```bash
# Base DN 존재 확인
ldapsearch -x -H ldap://192.168.100.10:389 \
    -b "dc=ldap,dc=smartcoreinc,dc=com" -s base "(objectClass=*)" dn

# Apache Directory Studio에서 F5로 새로고침
```

**백업 생성 (권장)**:

```bash
mkdir -p ~/ldap-backups
ldapsearch -x -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" -w "core" \
    -b "dc=ldap,dc=smartcoreinc,dc=com" -LLL "(objectClass=*)" \
    > ~/ldap-backups/backup-$(date +%Y%m%d-%H%M%S).ldif
```

---

## 📞 Support

**문제 발생 시**:
1. MCP Tools로 관련 문서 검색 (`mcp__filesystem__search_files`)
2. Context7로 Spring Boot 공식 문서 조회
3. Sequential Thinking으로 문제 분석
4. Memory에 해결책 저장

**프로젝트 소유자**: kbjung
**개발 팀**: SmartCore Inc.
**AI Assistant**: Claude (Anthropic)

---

## 🎓 Learning Resources

### Architecture Patterns Used

- **Domain-Driven Design (DDD)**: 4 Bounded Contexts, Value Objects, Aggregates
- **Hexagonal Architecture**: Ports & Adapters (FileStoragePort, LdapConnectionPort)
- **CQRS**: Command/Query 분리 (UseCase 패턴)
- **Event-Driven Architecture**: Domain Events, @TransactionalEventListener
- **Async Processing**: @Async, CompletableFuture, ThreadPoolTaskExecutor
- **Server-Sent Events (SSE)**: Real-time progress streaming
- **Strategy Pattern**: File format detection (LDIF vs Master List)

### Key Design Decisions

1. **즉시 응답 (202 Accepted)**: 사용자 경험 향상
2. **uploadId별 격리**: 동시 업로드 지원, 진행 상황 혼선 방지
3. **직접 메서드 체인**: 이벤트 오버헤드 제거, 트랜잭션 명확화
4. **Manual Mode 지원**: 테스트 및 디버깅 편의성
5. **서버 측 체크섬**: 보안 강화, 클라이언트 부담 경감

---

**Document Version**: 4.0
**Status**: PKD Module (PRODUCTION READY ✅) + PA Module (PHASE 4.5 IN PROGRESS ⏳)
**Last Review**: 2025-12-17

*이 문서는 프로젝트의 핵심 정보와 최신 아키텍처 변경사항을 포함합니다. 상세한 구현 내용은 `docs/` 디렉토리의 개별 문서를 참조하세요.*

---

## 📁 Key Documents

### Latest Phase Documents

| 문서 | 용도 | 위치 |
|------|--------|------|
| **TODO_PHASE_4_5** | Phase 4.5 작업 계획 및 가이드 | [docs/TODO_PHASE_4_5_PASSIVE_AUTHENTICATION.md](docs/TODO_PHASE_4_5_PASSIVE_AUTHENTICATION.md) |
| **SESSION_2025-12-17** | Phase 4.4 LDAP Integration 완료 보고서 | [docs/SESSION_2025-12-17_PASSIVE_AUTHENTICATION_INTEGRATION_TESTS.md](docs/SESSION_2025-12-17_PASSIVE_AUTHENTICATION_INTEGRATION_TESTS.md) |
| **SESSION_2025-12-12** | Phase 1-2 완료 + Lombok 이슈 해결 | [docs/SESSION_2025-12-12_LOMBOK_FIX_AND_PA_PHASE2.md](docs/SESSION_2025-12-12_LOMBOK_FIX_AND_PA_PHASE2.md) |
| **PA_PHASE_1_COMPLETE** | Phase 1 Domain Layer 완료 보고서 | [docs/PA_PHASE_1_COMPLETE.md](docs/PA_PHASE_1_COMPLETE.md) |

### PKD Module Documents

| 문서 | 용도 | 위치 |
|------|--------|------|
| **PROJECT_SUMMARY** | 프로젝트 전체 개요 (DB, API, 완료 Phase) | [docs/PROJECT_SUMMARY_2025-11-21.md](docs/PROJECT_SUMMARY_2025-11-21.md) |
| **CODE_CLEANUP_REPORT** | 최근 코드 정리 내역 (제거 파일, 빌드 결과) | [docs/CODE_CLEANUP_REPORT_2025-11-21.md](docs/CODE_CLEANUP_REPORT_2025-11-21.md) |
| **MASTER_LIST_STORAGE** | Master List 구조 및 저장 전략 분석 | [docs/MASTER_LIST_LDAP_STORAGE_ANALYSIS.md](docs/MASTER_LIST_LDAP_STORAGE_ANALYSIS.md) |
| **LDAP_BASE_DN_RECOVERY** | LDAP Base DN 복구 가이드 | [docs/LDAP_BASE_DN_RECOVERY.md](docs/LDAP_BASE_DN_RECOVERY.md) |

**아카이브**: `docs/archive/phases/` (Phase 1-19 문서 50개)