# Local PKD Evaluation Project - Development Guide

**Version**: 2.0
**Last Updated**: 2025-11-21
**Status**: Production Ready (Phase 1-18 Complete)

---

## 🎯 Quick Overview

ICAO PKD 파일(Master List .ml, LDIF .ldif)을 업로드하여 인증서를 파싱, 검증 후 OpenLDAP에 저장하는 웹 애플리케이션입니다.

**핵심 기능**:
- ✅ 파일 업로드 (중복 감지, 체크섬 검증)
- ✅ 파일 파싱 (LDIF, Master List CMS)
- ✅ 인증서 검증 (Trust Chain, CRL, 유효기간)
- ✅ OpenLDAP 자동 등록
- ✅ 실시간 진행 상황 (SSE)
- ✅ 업로드 이력 관리

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
│   ├── command/         # UploadLdifFileCommand, UploadMasterListFileCommand
│   ├── query/           # GetUploadHistoryQuery
│   ├── response/        # UploadFileResponse, CheckDuplicateResponse
│   └── usecase/         # 4개 Use Cases (CQRS)
└── infrastructure/
    ├── adapter/         # LocalFileStorageAdapter
    ├── web/             # 3개 Controllers
    └── repository/      # JPA Implementation + Event Publishing

fileparsing/             # File Parsing Context
├── domain/              # ParsedCertificate, Certificate, CertificateRevocationList
├── application/         # ParseFileUseCase, ExtractCertificatesUseCase
└── infrastructure/      # LdifParserAdapter, MasterListParserAdapter

certificatevalidation/   # Certificate Validation Context
├── domain/              # Trust Chain, CRL Checking, Validation Logic
├── application/         # ValidateCertificatesUseCase
└── infrastructure/      # BouncyCastleValidationAdapter

ldapintegration/         # LDAP Integration Context
├── domain/              # LDAP Entry Management
├── application/         # UploadToLdapUseCase
└── infrastructure/      # UnboundIdLdapAdapter

shared/                  # Shared Kernel
├── domain/              # AbstractAggregateRoot, DomainEvent
├── event/               # EventBus, @EventListener, @Async
├── exception/           # DomainException, InfrastructureException
└── progress/            # ProcessingProgress, ProgressService (SSE)
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

// ✅ Infrastructure Layer
throw new InfrastructureException("FILE_SAVE_ERROR", "파일 저장 중 오류: " + e.getMessage());

// ❌ 절대 사용 금지
throw new IllegalArgumentException("Invalid");  // ❌
throw new RuntimeException("Error");  // ❌
```

---

## 🛠️ MCP Tools 활용 가이드 (효율적 개발)

**연결된 MCP 서버**: Filesystem, Context7, Sequential Thinking, Memory

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
    "name": "Phase18",
    "entityType": "Development Phase",
    "observations": ["파일 파싱 성능 최적화 완료", "50% 속도 향상"]
}])

# Relation 생성
mcp__memory__create_relations(relations=[{
    "from": "Phase18",
    "to": "UploadedFile",
    "relationType": "optimizes"
}])

# 검색
mcp__memory__search_nodes(query="performance optimization")
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
| **MASTER_LIST_UPLOAD_REPORT** | Master List 업로드 테스트 결과 | docs/MASTER_LIST_UPLOAD_REPORT_2025-11-21.md |

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
# 184 source files
```

### 3. 테스트 실행

```bash
./mvnw test
# Tests run: 62+, Failures: 0
```

### 4. 애플리케이션 실행

```bash
./mvnw spring-boot:run
# Started LocalPkdApplication in 7.669 seconds
# Tomcat started on port(s): 8081 (http)
```

### 5. Health Check

```bash
curl http://localhost:8081/actuator/health
# {"status":"UP"}
```

---

## 📊 Current Status (2025-11-21)

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

### High Priority TODOs (3개)

1. **UploadToLdapCompletedEvent 발행** (UploadToLdapEventHandler.java:185,186)
2. **CertificateValidationApiController 상태 조회** (line 158)
3. **LdifParsingEventHandler TODO 확인** (line 75)

### Next Phases (Optional)

- **Phase 19**: 고급 검색 & 필터링 (Full-Text Search, Elasticsearch)
- **Phase 20**: 모니터링 & 운영 (Prometheus, Grafana, Alerts)

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

**Document Version**: 2.0
**Status**: PRODUCTION READY ✅
**Last Review**: 2025-11-21

*이 문서는 프로젝트의 핵심 정보만 포함합니다. 상세한 구현 내용은 `docs/` 디렉토리의 개별 문서를 참조하세요.*
