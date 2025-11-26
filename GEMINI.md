# Gemini's Guide to the Local PKD Project

**Version**: 2.1
**Last Updated**: 2025-11-26
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
├── domain/              # Aggregate (ParsedFile) + VOs (CertificateData, CrlData)
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
mcp__memory__create_entities(entities=[
    {
        "name": "Phase18",
        "entityType": "Development Phase",
        "observations": ["File parsing performance optimized", "50% speed increase"]
    }
])

# Create Relation
mcp__memory__create_relations(relations=[
    {
        "from": "Phase18",
        "to": "UploadedFile",
        "relationType": "optimizes"
    }
])

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
| **PHASE_DSC_NC** | Non-Conformant Certificate Support | docs/PHASE_DSC_NC_IMPLEMENTATION_COMPLETE.md |
| **MASTER_LIST_UPLOAD_REPORT** | Master List Upload Test Results | docs/MASTER_LIST_UPLOAD_REPORT_2025-11-21.md |

**Archive**: `docs/archive/phases/` (Phase 1-16 documents 50개)

---\n

## 💾 Database Schema (Current State)

### Key Tables (4개)

```sql
-- 1. uploaded_file (File Upload Aggregate)
CREATE TABLE uploaded_file (
    id UUID PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_hash VARCHAR(64) NOT NULL UNIQUE,
    file_size_bytes BIGINT NOT NULL,
    file_format VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    processing_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO'
);

-- 2. parsed_file (File Parsing Aggregate)
CREATE TABLE parsed_file (
    id UUID PRIMARY KEY,
    upload_id UUID NOT NULL REFERENCES uploaded_file(id) ON DELETE CASCADE,
    file_format VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    certificate_count INT DEFAULT 0,
    crl_count INT DEFAULT 0,
    error_count INT DEFAULT 0,
    parsing_started_at TIMESTAMP,
    parsing_completed_at TIMESTAMP
);

-- 3. parsed_certificate (Embedded in ParsedFile)
-- This is an @ElementCollection table, not an aggregate.
CREATE TABLE parsed_certificate (
    parsed_file_id UUID NOT NULL REFERENCES parsed_file(id) ON DELETE CASCADE,
    cert_type VARCHAR(20) NOT NULL,
    country_code VARCHAR(3),
    subject_dn VARCHAR(500) NOT NULL,
    issuer_dn VARCHAR(500) NOT NULL,
    serial_number VARCHAR(100) NOT NULL,
    not_before TIMESTAMP NOT NULL,
    not_after TIMESTAMP NOT NULL,
    certificate_binary BYTEA NOT NULL,
    fingerprint_sha256 VARCHAR(64) NOT NULL,
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (fingerprint_sha256)
);

-- 4. certificate (Certificate Validation Aggregate)
CREATE TABLE certificate (
    id UUID PRIMARY KEY,
    upload_id UUID NOT NULL REFERENCES uploaded_file(id) ON DELETE CASCADE,
    x509_fingerprint_sha256 VARCHAR(64) NOT NULL UNIQUE,
    subject_dn VARCHAR(500) NOT NULL,
    issuer_dn VARCHAR(500) NOT NULL,
    issuer_country_code VARCHAR(3) NOT NULL,
    not_before TIMESTAMP NOT NULL,
    not_after TIMESTAMP NOT NULL,
    certificate_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL -- (VALID, EXPIRED, REVOKED, etc.)
);
```

**Indexes**: Multiple indexes exist for performance on columns like `file_hash`, `status`, `uploaded_at`, `fingerprint_sha256`, etc.

**Flyway Migrations**: V1 ~ V7 (Completed)

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

## 📊 Current Status (2025-11-26)

### ✅ Completed Tasks

1.  **Resolved Certificate Validation Crashes & Database Issues**:
    *   **Initial Problem:** Application crashed with `NullPointerException` during certificate validation and later `IllegalArgumentException` (Public Key Null) when processing Master List files. Database transactions were aborting due to `duplicate key` violations, leading to `UnexpectedRollbackException`.
    *   **Root Causes**:
        *   Incorrect helper method call in `ValidateCertificatesUseCase` catch blocks when creating dummy error certificates.
        *   Lack of robust handling for concurrent duplicate certificate insertions within the same transaction, leading to database constraint violations that rolled back the entire transaction.
        *   Compiler errors related to "local variables referenced from a lambda expression must be final or effectively final" due to complex variable scoping in nested `try-catch` blocks.
    *   **Solutions Implemented**:
        *   Corrected `createCertificateFromData` helper method call in `ValidateCertificatesUseCase`.
        *   Introduced `X509Data.ofIncomplete()` to handle incomplete certificate data for error logging.
        *   Implemented an in-batch duplicate certificate check using `processedFingerprints` `Set` in `ValidateCertificatesUseCase`.
        *   Enhanced `ValidateCertificatesUseCase` to gracefully handle `DataIntegrityViolationException` (duplicate key errors) by converting failed `INSERT` attempts into `UPDATE` operations on existing records.
        *   Refactored certificate saving/updating logic into a new helper method (`handleCertificateSaveOrUpdate`) to resolve "effectively final" compiler errors and improve code modularity.

2.  **Major Codebase Cleanup & Refactoring**:
    *   **Problem:** Discovered a significant amount of obsolete code (13 files) from a previous, incomplete refactoring phase, particularly related to LDAP integration within the `certificatevalidation` context. These files were still active and incorrectly wired, causing issues and preventing the proper execution of new LDAP logic.
    *   **Solution Implemented**:
        *   Identified and safely deleted 13 obsolete Java files (including old LDAP-related commands, responses, use cases, services, and event handlers from the `certificatevalidation` context, and duplicate enum definitions from `common.enums`).
        *   Created a new, correct `CertificateValidatedEventHandler.java` in the `ldapintegration` context to properly listen for `CertificatesValidatedEvent` and trigger the correct `UploadToLdapUseCase` (from `ldapintegration`).
        *   Fixed compilation errors resulting from file deletions (e.g., removing a dangling event reference in `Certificate.java`).
        *   Updated the new event handler to correctly use the `record`-based API of `UploadToLdapCommand` and `UploadToLdapResponse`.

### ⚠️ Outstanding Issues & TODO List

1.  **LDAP Save Failure (Auto Mode):** Despite all previous fixes, the application UI indicates completion, but logs still show errors like `ERROR: current transaction is aborted, commands ignored until end of transaction block` (though the root duplicate key cause is now handled), and certificates are not being saved to LDAP in auto mode. **Investigation needed to pinpoint the new root cause of LDAP save failure and transaction abortion.**
2.  **Persistent Lombok Annotation Processing Issues:**
    *   **Problem:** Repeated `cannot find symbol variable log` and `cannot find symbol method builder()` errors appear across multiple files (`RecordValidationUseCase`, `ValidateCertificateUseCase`, `ValidateCertificatesUseCase`, `CheckRevocationUseCase`, `CertificateRevocationListEventHandler`, `GetCertificateStatisticsUseCase`). This indicates a persistent problem with Lombok's annotation processor being correctly invoked or processed by the Maven compiler plugin.
    *   **Action:** Investigate Maven's verbose output (`mvnw clean install -X`) to confirm Lombok processor execution. If present, further debugging of Lombok's interaction with the compiler is required. If not, the `maven-compiler-plugin` configuration might need adjustment to ensure Lombok is always run.
3.  **`CertificateRevocationList.java` Structural Error:**
    *   **Problem:** `CertificateRevocationList is not abstract and does not override abstract method getId() in ...Entity`. This indicates a structural issue where the `CertificateRevocationList` class (or its parent `AggregateRoot` / `Entity`) is not correctly implementing the `getId()` method.
    *   **Action:** Review the `getId()` implementation in `CertificateRevocationList.java` and its inheritance hierarchy (`AggregateRoot`, `Entity`) to ensure proper method overriding and return types.
4.  **Domain Model API Mismatches:**
    *   **Problem:** `invalid method reference cannot find symbol method isValid(T)` (in `ParsedFile.java`) and `cannot find symbol method getValue()` (in `FileFormat.java`). These suggest breaking API changes or Lombok processing failures for getter methods in `CertificateData`, `CrlData`, and `FileName` models.
    *   **Action:** Investigate the specific `ParsedFile.java` and `FileFormat.java` files, along with `CertificateData`, `CrlData`, and `FileName` to understand why these methods are not found. This might be another symptom of Lombok failure or an API change during refactoring that was not fully propagated.

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

**Document Version**: 2.1
**Status**: IN PROGRESS ⚠️
**Last Review**: 2025-11-26

*This document contains only core project information. Refer to individual documents in the `docs/` directory for detailed implementation.*