# Local PKD Evaluation Project - Development Guide

**Version**: 3.2
**Last Updated**: 2025-11-28
**Status**: Production Ready (Phase 1-19 Complete + LDAP Validation Status)

---

## 🎯 Quick Overview

ICAO PKD 파일(Master List .ml, LDIF .ldif)을 업로드하여 인증서를 파싱, 검증 후 OpenLDAP에 저장하는 웹 애플리케이션입니다.

**핵심 기능**:
- ✅ 파일 업로드 (중복 감지, 서버 측 체크섬 검증)
- ✅ 비동기 파일 처리 (즉시 uploadId 반환)
- ✅ 파일 파싱 (LDIF, Master List CMS)
- ✅ 인증서 검증 (Trust Chain, CRL, 유효기간)
- ✅ OpenLDAP 자동 등록 (검증 상태 포함)
- ✅ 실시간 진행 상황 (uploadId별 SSE 스트림)
- ✅ 수동/자동 처리 모드 (Manual/Auto Mode)
- ✅ 업로드 이력 관리
- ✅ 단계별 진행 상태 UI (Upload → Parse → Validate → LDAP)
- ✅ LDAP 검증 상태 기록 (VALID/INVALID/EXPIRED + 오류 메시지)

**Tech Stack**:
- Backend: Spring Boot 3.5.5, Java 21, PostgreSQL 15.14
- DDD Libraries: JPearl 2.0.1, MapStruct 1.6.3
- Frontend: Thymeleaf, Alpine.js 3.14.8, HTMX 2.0.4, DaisyUI 5.0
- Certificate: Bouncy Castle 1.70, UnboundID LDAP SDK

---

## 🏗️ DDD Architecture (현재 구조)

### Bounded Contexts (4개)

```
fileupload/              # File Upload Context
├── domain/
│   ├── model/           # Aggregates (UploadedFile) + Value Objects (11개)
│   ├── event/           # FileUploadedEvent, DuplicateFileDetectedEvent
│   ├── port/            # FileStoragePort (Hexagonal)
│   └── repository/      # UploadedFileRepository (Interface)
├── application/
│   ├── command/         # UploadLdifFileCommand, UploadMasterListFileCommand, CheckDuplicateFileCommand
│   ├── query/           # GetUploadHistoryQuery
│   ├── response/        # UploadFileResponse, CheckDuplicateResponse, ProcessingResponse
│   ├── service/         # AsyncUploadProcessor (NEW)
│   ├── event/           # FileUploadEventHandler (REFACTORED)
│   └── usecase/         # 4개 Use Cases (CQRS)
└── infrastructure/
    ├── adapter/         # LocalFileStorageAdapter
    ├── web/             # UnifiedFileUploadController, ProcessingController (Manual Mode)
    └── repository/      # JPA Implementation + Event Publishing

fileparsing/             # File Parsing Context
├── domain/              # ParsedFile, ParsedCertificate, CertificateRevocationList
├── application/         # ParseLdifFileUseCase, ParseMasterListFileUseCase
└── infrastructure/      # LdifParserAdapter, MasterListParserAdapter

certificatevalidation/   # Certificate Validation Context
├── domain/              # Trust Chain, CRL Checking, Validation Logic, Certificate
├── application/         # ValidateCertificatesUseCase, UploadToLdapUseCase
└── infrastructure/      # BouncyCastleValidationAdapter, UnboundIdLdapConnectionAdapter

ldapintegration/         # LDAP Integration Context (Deprecated - Merged into certificatevalidation)
├── domain/              # LDAP Entry Management
├── application/         # Event Handlers
└── infrastructure/      # UnboundIdLdapAdapter

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

**연결된 MCP 서버**: Filesystem, Context7, Sequential Thinking, Memory, Playwright

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

```python
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

```python
mcp__sequential_thinking__sequentialthinking(
    thought="1단계: 문제 분석...",
    thoughtNumber=1,
    totalThoughts=5,
    nextThoughtNeeded=true
)
```

### 4. Memory - 프로젝트 지식 저장

```python
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

```python
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
|------|------|------|
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

## 📊 Current Status (2025-11-28)

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
| **Phase 19** | **LDAP 검증 상태 기록 (description attribute)** | ✅ **NEW** |

### Recent Refactoring (2025-11-26 ~ 2025-11-28) ✅

1. ✅ **AsyncUploadProcessor 도입** - 즉시 uploadId 반환, 백그라운드 처리
2. ✅ **uploadId별 SSE 스트림** - 개별 진행 상황 추적
3. ✅ **이벤트 체인 단순화** - 직접 메서드 호출로 변경
4. ✅ **Manual/Auto Mode** - 단계별 수동 제어 기능
5. ✅ **UI 대폭 개선** - 4단계 진행 상황 시각화
6. ✅ **서버 측 체크섬** - 클라이언트 부담 제거
7. ✅ **WSL2 네트워크 지원** - Windows Chrome 접근 가능
8. ✅ **실제 LDAP 업로드 구현** (2025-11-27) - ICAO PKD LDIF 형식 준수, 시뮬레이션 제거
9. ✅ **LDAP 검증 상태 기록** (2025-11-28 NEW) - description attribute에 VALID/INVALID/EXPIRED + 오류 메시지 포함

### Remaining TODOs

1. ✅ ~~**FileUploadEventHandler.java:92** - LDAP 업로드 체인 연결~~ **COMPLETED (2025-11-27)**
2. ✅ ~~**LdifConverter - LDAP 검증 상태 기록**~~ **COMPLETED (2025-11-28)**
3. **ProcessingController.java:141-143** - Manual Mode Use Cases 구현 (Phase 20 예정)
4. **ProcessingController.java:358-369** - 처리 상태 DB 조회 구현
5. **LdifConverter** - 단위 테스트 작성 (Optional)
6. **UploadToLdapUseCase** - 통합 테스트 작성 (Optional)

### Next Steps (Optional)

- **Phase 20**: Manual Mode 완성 (ValidateCertificatesUseCase, UploadToLdapUseCase 호출)
- **Phase 21**: 고급 검색 & 필터링 (Full-Text Search, Elasticsearch)
- **Phase 22**: 모니터링 & 운영 (Prometheus, Grafana, Alerts)
- **Phase 23**: LDAP 검증 상태 모니터링 Dashboard (Validation Statistics)

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

**Document Version**: 3.2
**Status**: PRODUCTION READY ✅
**Last Review**: 2025-11-28

*이 문서는 프로젝트의 핵심 정보와 최신 아키텍처 변경사항을 포함합니다. 상세한 구현 내용은 `docs/` 디렉토리의 개별 문서를 참조하세요.*
