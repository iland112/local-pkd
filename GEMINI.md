# Gemini's Guide to the Local PKD Project

**Version**: 2.0
**Last Updated**: 2025-11-22
**Purpose**: This document serves as my central knowledge base for understanding and working on the Local PKD project. It's based on the excellent `CLAUDE.md` guide and my own analysis of the source code.

---\n

## 🎯 Project Overview

This is a Spring Boot web application designed to process ICAO Public Key Directory (PKD) files. It allows users to upload Master Lists (.ml) and LDIF (.ldif) files, which are then parsed, validated, and stored in an OpenLDAP directory.

**Key Features**:
- ✅ File Upload (duplicate detection, checksum verification)
- ✅ File Parsing (LDIF, Master List CMS)
- ✅ Certificate Validation (Trust Chain, CRL, validity period)
- ✅ Automatic OpenLDAP Registration
- ✅ Real-time Progress (SSE)
- ✅ Upload History Management

**Tech Stack**:
- Backend: Spring Boot 3.5.5, Java 21, PostgreSQL 15.14
- DDD Libraries: JPearl 2.0.1, MapStruct 1.6.3
- Frontend: Thymeleaf, Alpine.js 3.14.8, HTMX 2.0.4, DaisyUI 5.0
- Certificate: Bouncy Castle 1.70, UnboundID LDAP SDK

---\n

## 🏗️ DDD Architecture (Current Structure)

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

---\n

## 📋 Critical Coding Rules (Must Comply)

### 1. Value Object Writing Rules

```java
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // Required for JPA
public class CollectionNumber {
    private String value;  // ❌ non-final (JPA cannot set value)

    // ✅ Static Factory Method
    public static CollectionNumber of(String value) {
        return new CollectionNumber(value);
    }

    // ✅ Private Constructor + Validation
    private CollectionNumber(String value) {
        validate(value);
        this.value = value;
    }

    // ✅ Business Rule Validation
    private void validate(String value) {
        if (value == null || !value.matches("^\\d{3}$")) {
            throw new DomainException("INVALID_COLLECTION", "...");
        }
    }
}
```

**Key Requirements**:
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` - Essential for Hibernate
- Fields must be **non-final** - for JPA reflection value injection
- `@Embeddable` annotation
- Static factory methods (of, from, extractFrom)
- Self-validation (validation at creation time)
- Value-based equality (`@EqualsAndHashCode`)

### 2. Aggregate Root Writing Rules

```java
@Entity
@Table(name = "uploaded_file")
public class UploadedFile extends AbstractAggregateRoot<UploadId> {
    @Embedded
    private UploadId id;  // JPearl type-safe ID

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "file_name"))
    private FileName fileName;

    // ✅ Protected default constructor (for JPA)
    protected UploadedFile() {}

    // ✅ Static Factory Method (for publishing Domain Events)
    public static UploadedFile create(...) {
        UploadedFile file = new UploadedFile(...);
        file.registerEvent(new FileUploadedEvent(file.getId()));
        return file;
    }
}
```

### 3. Exception Handling Rules

```java
// ✅ Domain Layer
throw new DomainException("INVALID_FILE_FORMAT", "파일 형식이 올바르지 않습니다");

// ✅ Infrastructure Layer
throw new InfrastructureException("FILE_SAVE_ERROR", "파일 저장 중 오류: " + e.getMessage());

// ❌ Absolutely Forbidden
throw new IllegalArgumentException("Invalid");  // ❌
throw new RuntimeException("Error");  // ❌
```

---\n

## 🛠️ Tools Usage Guide (Efficient Development)

**Connected Tools**: Filesystem, Context7, Sequential Thinking, Memory

### 1. Filesystem Operations

```python
# ✅ Read File (handles large files)
mcp__filesystem__read_text_file(path, head=100)  # first 100 lines
mcp__filesystem__read_text_file(path, tail=50)   # last 50 lines

# ✅ Write File (always use absolute path)
mcp__filesystem__write_file(path="/absolute/path/file.java", content="...")

# ✅ Directory Search
mcp__filesystem__search_files(path="/src", pattern="*.java", excludePatterns=["*Test.java"])

# ✅ Get File Info
mcp__filesystem__get_file_info(path="/path/file.java")
```

### 2. Context7 - Library Documentation Lookup

```python
# Step 1: Resolve Library ID
mcp__context7__resolve_library_id(libraryName="spring boot")

# Step 2: Get Documentation
mcp__context7__get_library_docs(
    context7CompatibleLibraryID="/spring/boot",
    topic="actuator",
    page=1
)
```

### 3. Sequential Thinking - Complex Problem Analysis

```python
mcp__sequential_thinking__sequentialthinking(
    thought="Step 1: Analyze problem...",
    thoughtNumber=1,
    totalThoughts=5,
    nextThoughtNeeded=true
)
```

### 4. Memory - Store Project Knowledge

```python
# Create Entity
mcp__memory__create_entities(entities=[{
    "name": "Phase18",
    "entityType": "Development Phase",
    "observations": ["File parsing performance optimized", "50% speed increase"]
}])

# Create Relation
mcp__memory__create_relations(relations=[{
    "from": "Phase18",
    "to": "UploadedFile",
    "relationType": "optimizes"
}])

# Search
mcp__memory__search_nodes(query="performance optimization")
```

---\n

## 📚 Key Documents (Must Read)

| Document | Purpose | Location |
|------|------|----------|
| **PROJECT_SUMMARY** | Project-wide overview (DB, API, completed Phases) | docs/PROJECT_SUMMARY_2025-11-21.md |
| **TODO_ANALYSIS** | Analysis of 105 TODOs (High/Medium/Low priority) | docs/TODO_ANALYSIS.md |
| **CODE_CLEANUP_REPORT** | Recent code cleanup details (removed files, build results) | docs/CODE_CLEANUP_REPORT_2025-11-21.md |
| **PHASE_17** | Completed Event-Driven LDAP Upload Report | docs/PHASE_17_COMPLETE.md |
| **PHASE_DSC_NC** | Non-Conformant Certificate Implementation Completed | docs/PHASE_DSC_NC_IMPLEMENTATION_COMPLETE.md |
| **MASTER_LIST_UPLOAD_REPORT** | Master List Upload Test Results | docs/MASTER_LIST_UPLOAD_REPORT_2025-11-21.md |

**Archive**: `docs/archive/phases/` (Phase 1-16 documents 50개)

---\n

## 💾 Database Schema (Current State)

### Key Tables (3개)

```sql
-- 1. uploaded_file (File Upload History)
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

-- 2. parsed_certificate (Parsed Certificate)
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

**Flyway Migrations**: V1 ~ V13 (Completed)

---\n

## 🚀 Build & Run

### 1. Start Containers (PostgreSQL)

```bash
./podman-start.sh
# PostgreSQL: localhost:5432 (postgres/secret)
# pgAdmin: http://localhost:5050
```

### 2. Build (Maven)

```bash
./mvnw clean compile
# BUILD SUCCESS in ~7s
# 184 source files
```

### 3. Run Tests

```bash
./mvnw test
# Tests run: 62+, Failures: 0
```

### 4. Run Application

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

---\n

## 📊 Current Status (2025-11-22)

### Completed Phases ✅

| Phase | Content | Status |
|------|------|------|
| Phase 1-4 | Project Setup, DDD Foundation | ✅ |
| Phase 5-10 | File Upload, Parsing, Validation | ✅ |
| Phase 11-13 | Certificate/CRL Aggregates, Trust Chain | ✅ |
| Phase 14-16 | LDAP Integration, Event-Driven | ✅ |
| Phase 17 | Event-Driven LDAP Upload Pipeline | ✅ |
| Phase 18 | UI Improvements, Dashboard | ✅ |
| Phase DSC_NC | Non-Conformant Certificate Support | ✅ |

### High Priority TODOs (0개 - All Resolved)

Based on my analysis and previous actions, the 3 high-priority TODOs mentioned in CLAUDE.md are now considered resolved:
1.  Publish `UploadToLdapCompletedEvent` in `UploadToLdapEventHandler.java`. (Already implemented)
2.  Implement the status check endpoint in `CertificateValidationApiController.java`. (Already implemented)
3.  Address the `TODO` in `LdifParsingEventHandler.java`. (No outstanding TODOs found, and trigger logic implemented)

### Next Phases (Optional)

- **Phase 19**: Advanced Search & Filtering (Full-Text Search, Elasticsearch)
- **Phase 20**: Monitoring & Operations (Prometheus, Grafana, Alerts)

---\n

## 🔧 Troubleshooting

### 1. Build Errors

```bash
# Port conflict (8081)
lsof -ti:8081 | xargs kill -9

# Restart containers
./podman-restart.sh

# Full cleanup and restart
./podman-clean.sh && ./podman-start.sh
```

### 2. Flyway Migration Errors

```bash
# Check migration history
psql -h localhost -U postgres -d icao_local_pkd
\dt flyway_schema_history

# Rerun migrations
./mvnw flyway:clean flyway:migrate
```

### 3. Value Object JPA Errors

```
Error: Unable to instantiate value object
```

**Solution**: Check for `@NoArgsConstructor(access = AccessLevel.PROTECTED)` and non-final fields.

---\n

## 📞 Support

**When issues arise**:
1. Search relevant documents with `mcp__filesystem__search_files`
2. Look up Spring Boot official documentation with Context7
3. Analyze problems using Sequential Thinking
4. Save solutions to Memory

**Project Owner**: kbjung
**Development Team**: SmartCore Inc.
**AI Assistant**: Gemini

---\n

**Document Version**: 2.0
**Status**: PRODUCTION READY ✅
**Last Review**: 2025-11-22

*This document contains only core project information. Refer to individual documents in the `docs/` directory for detailed implementation.*
