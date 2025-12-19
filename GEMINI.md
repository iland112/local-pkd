# Gemini's Guide to the Local PKD Project

**Version**: 3.0
**Last Updated**: 2025-12-06
**Purpose**: This document serves as my central knowledge base for understanding and working on the Local PKD project. It's based on the excellent `CLAUDE.md` guide and my own analysis of the source code and recent documentation.

---

## 🎯 Project Overview

This is a Spring Boot web application designed to process ICAO Public Key Directory (PKD) files. It allows users to upload Master Lists (.ml) and LDIF (.ldif) files, which are then parsed, validated, and stored in an OpenLDAP directory. The system supports asynchronous processing, real-time progress updates via SSE, and both automatic and manual processing modes.

**Key Features**:
- ✅ **Async File Upload**: Immediately returns an `uploadId` for tracking.
- ✅ **Duplicate Detection**: Server-side checksum verification to prevent redundant processing.
- ✅ **Multi-format Parsing**: Handles LDIF and Master List (CMS) files.
- ✅ **Comprehensive Certificate Validation**: Includes Trust Chain, CRL, and validity period checks.
- ✅ **Automatic OpenLDAP Registration**: Stores validated certificates and their status in OpenLDAP.
- ✅ **Real-time Progress**: Per-upload SSE streams for detailed progress tracking (Upload → Parse → Validate → LDAP).
- ✅ **Dual Processing Modes**: `AUTO` for fully automated workflow and `MANUAL` for step-by-step user control.
- ✅ **Upload History & Statistics**: Provides a detailed history with parsing and validation statistics for each upload.
- ✅ **Audit Trail**: Supports tracking duplicate certificates across different uploads.

**Tech Stack**:
- Backend: Spring Boot 3.5.5, Java 21, PostgreSQL 15.14
- DDD Libraries: JPearl 2.0.1, MapStruct 1.6.3
- Frontend: Thymeleaf, Alpine.js 3.14.8, HTMX 2.0.4, DaisyUI 5.0
- Certificate: Bouncy Castle 1.70, UnboundID LDAP SDK

---

## 🏗️ DDD Architecture (As of 2025-12-05)

### Bounded Contexts (4)

```
fileupload/              # File Upload Context
├── domain/
│   ├── model/           # Aggregates (UploadedFile) + Value Objects (11)
│   ├── event/           # FileUploadedEvent, DuplicateFileDetectedEvent
│   ├── port/            # FileStoragePort (Hexagonal)
│   └── repository/      # UploadedFileRepository (Interface)
├── application/
│   ├── command/         # UploadLdifFileCommand, UploadMasterListFileCommand
│   ├── query/           # GetUploadHistoryQuery
│   ├── service/         # AsyncUploadProcessor (Manages async workflow)
│   └── usecase/         # CQRS Use Cases
└── infrastructure/
    ├── adapter/         # LocalFileStorageAdapter
    ├── web/             # UnifiedFileUploadController, ProcessingController (Manual Mode)
    └── repository/      # JPA Implementation + Event Publishing

fileparsing/             # File Parsing Context
├── domain/              # ParsedFile, ParsedCertificate, CertificateRevocationList
├── application/         # ParseLdifFileUseCase, ParseMasterListFileUseCase
└── infrastructure/      # LdifParserAdapter, MasterListParserAdapter

certificatevalidation/   # Certificate Validation Context
├── domain/              # Certificate, Trust Chain, CRL Checking, Validation Logic
├── application/         # ValidateCertificatesUseCase, UploadToLdapUseCase
└── infrastructure/      # BouncyCastleValidationAdapter, UnboundIdLdapConnectionAdapter

ldapintegration/         # LDAP Integration Context (DEPRECATED - Merged into certificatevalidation)

shared/                  # Shared Kernel
├── domain/              # AbstractAggregateRoot, DomainEvent
├── event/               # EventBus, @EventListener, @Async
├── exception/           # DomainException, BusinessException, InfrastructureException
├── progress/            # ProcessingProgress, ProgressService (SSE), ProgressController
└── util/                  # HashingUtil (SHA-256 checksum)
```

### Recent Architectural Changes

- **Asynchronous Processing**: The `AsyncUploadProcessor` service was introduced to handle file uploads asynchronously, immediately returning a `202 Accepted` response with an `uploadId`.
- **Simplified Event Flow**: The complex event chain for processing steps has been replaced by a more direct and streamlined method invocation chain within an `@Async` method, improving clarity and debugging.
- **uploadId-specific SSE Streams**: Progress updates are now sent to streams specific to each `uploadId`, preventing broadcast confusion and isolating progress tracking.

---

## 📋 Critical Coding Rules (Must Comply)

### 1. Value Object Writing Rules

- Use `@Embeddable`, `@Getter`, `@EqualsAndHashCode`.
- Provide a `@NoArgsConstructor(access = AccessLevel.PROTECTED)` for JPA.
- Fields must be **non-final** to allow JPA to set their values via reflection.
- Use **static factory methods** (e.g., `of()`) for creation, with validation in a private constructor.

### 2. Aggregate Root Writing Rules

- Extend `AbstractAggregateRoot<ID_TYPE>`.
- Use `@Entity` and `@Table` annotations.
- Use `@EmbeddedId` for type-safe JPearl IDs.
- Provide a `protected` no-argument constructor for JPA.
- Use a **static factory method** (e.g., `create()`) to encapsulate object creation and domain event registration (`registerEvent()`).

### 3. Exception Handling Rules

- **Domain Layer**: `throw new DomainException(...)`
- **Application Layer**: `throw new BusinessException(...)`
- **Infrastructure Layer**: `throw new InfrastructureException(...)`
- Avoid generic exceptions like `IllegalArgumentException` or `RuntimeException`.

---

## 🛠️ Tools Usage Guide (Efficient Development)

**Connected Tools**: Filesystem, Context7, Sequential Thinking, Memory

### 1. Filesystem Operations
```python
# Read File (handles large files)
mcp__filesystem__read_text_file(path, head=100)
mcp__filesystem__read_text_file(path, tail=50)

# Write File (always use absolute path)
mcp__filesystem__write_file(path="/absolute/path/file.java", content="...")

# Search Directory
mcp__filesystem__search_files(path="/src", pattern="*.java", excludePatterns=["*Test.java"])
```

### 2. Context7 - Library Documentation Lookup
```python
# 1. Resolve Library ID
mcp__context7__resolve_library_id(libraryName="spring boot")

# 2. Get Documentation
mcp__context7__get_library_docs(context7CompatibleLibraryID="/spring/boot", topic="actuator")
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

---

## 📚 Key Documents (Must Read)

| Document | Purpose | Location |
|---|---|---|
| **CLAUDE.md** | The primary, most up-to-date development guide. | `CLAUDE.md` |
| **SESSION_2025-12-05_MIGRATION_CONSOLIDATION.md** | Report on consolidating DB migrations and fixing schema issues. | `docs/SESSION_2025-12-05_MIGRATION_CONSOLIDATION.md` |
| **SESSION_2025-12-05_UPLOAD_STATISTICS.md**| Report on implementing the upload statistics feature. | `docs/SESSION_2025-12-05_UPLOAD_STATISTICS.md` |
| **DUPLICATE_CERTIFICATE_AUDIT_TRAIL_FIX.md** | Details on the fix for tracking duplicate certificates. | `docs/DUPLICATE_CERTIFICATE_AUDIT_TRAIL_FIX.md` |
| **MIGRATION_CONSOLIDATION_REPORT.md** | Technical report on the database migration consolidation. | `docs/MIGRATION_CONSOLIDATION_REPORT.md` |
| **PROJECT_SUMMARY_2025-11-21.md** | Project overview as of late November. Good for historical context. | `docs/PROJECT_SUMMARY_2025-11-21.md` |

**Archive**: `docs/archive/phases/` (Phase 1-16 documents) and `docs/migration-archive/` (old V1-V17 migrations)

---

## 💾 Database Schema (Consolidated as of 2025-12-05)

Following a major consolidation, the database schema is now defined in a single, clean Flyway migration file `V1__Initial_Schema.sql`. All `ALTER` statements have been removed from the initial setup.

### Key Tables

```sql
-- 1. uploaded_file (File Upload Aggregate)
CREATE TABLE uploaded_file (
    id UUID PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    file_format VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL,
    is_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    processing_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO'
    -- ... and other metadata columns
);

-- 2. parsed_certificate (For parsing results, supports audit trails)
CREATE TABLE parsed_certificate (
    parsed_file_id UUID NOT NULL REFERENCES parsed_file(id) ON DELETE CASCADE,
    fingerprint_sha256 VARCHAR(64) NOT NULL,
    -- ... other certificate data columns
    PRIMARY KEY (parsed_file_id, fingerprint_sha256) -- Composite PK for audit trail
);
CREATE INDEX idx_parsed_certificate_fingerprint ON parsed_certificate(fingerprint_sha256);


-- 3. certificate (Deduplicated, validated certificates)
CREATE TABLE certificate (
    id UUID PRIMARY KEY,
    upload_id UUID NOT NULL REFERENCES uploaded_file(id),
    x509_fingerprint_sha256 VARCHAR(64) NOT NULL UNIQUE,
    -- ... full certificate and validation status columns (approx. 29+)
    validation_overall_status VARCHAR(30)
);

-- 4. certificate_revocation_list (CRL Aggregate)
CREATE TABLE certificate_revocation_list (
    id UUID PRIMARY KEY,
    upload_id UUID NOT NULL REFERENCES uploaded_file(id),
    -- ... CRL data columns
);
```
**Flyway Migrations**: All previous migrations (V1-V17) have been consolidated into `V1__Initial_Schema.sql`. The old files are archived in `docs/migration-archive/`.

---

## 🚀 Build & Run

### 1. Start Containers
`./podman-start.sh`

### 2. Build
`./mvnw clean compile`

### 3. Run Tests
`./mvnw test`

### 4. Run Application
`./mvnw spring-boot:run`

### 5. Health Check
`curl http://localhost:8081/actuator/health`

---

## 📊 Current Status (2025-12-07)

The project is stable and production-ready. The most recent work (as of 2025-12-07) focused on refactoring the dashboard's frontend logic and enhancing its UI/UX.

### ✅ Recently Completed Tasks (Dec 2025)

1.  **Dashboard UI/UX Refactoring and Enhancement**:
    *   **Centralized State Management**: Refactored the dashboard's frontend logic by consolidating all Alpine.js state and functions into a single `dashboardApp` component. This resolved multiple reactivity issues, including the dark mode toggle button not appearing.
    *   **Global Scope**: Moved the `x-data` directive to the `<body>` tag in `layout/main.html` to create a global component scope, allowing shared state across all page fragments.
    *   **Simplified Scripts**: Removed the global `Alpine.store` from `_scripts.html` to eliminate state management conflicts and simplify the overall logic.
    *   **Enhanced Chart Visualization**: Re-implemented and stabilized the Chart.js integration, successfully displaying certificate statistics using doughnut and pie charts.
    *   **Layout Fixes**: Removed redundant status text from the navigation bar and ensured all dashboard components load and display data correctly.

2.  **UI Bug Fix (upload.html)**: Fixed an issue where Alpine.js directives were displayed as plain text due to an invalid HTML comment placed within a `div` tag's attributes.
3.  **Database Migration Consolidation**:
    *   **Reduced 10 migration files (958 lines) into a single `V1__Initial_Schema.sql` (465 lines).**
    *   Eliminated all `ALTER` statements from the initial schema setup.
    *   Fixed numerous Hibernate schema validation errors by adding ~32 missing columns to `certificate` and `certificate_revocation_list` tables.
    *   Archived old migration scripts to `docs/migration-archive/`.

4.  **Upload Statistics Feature**:
    *   Implemented a feature to show detailed **parsing and validation statistics** in the upload history view.
    *   Added new count methods to 4 repositories (`ParsedCertificate`, `CRL`, `MasterList`, `Certificate`).
    *   Enhanced `GetUploadHistoryUseCase` to gather and return statistics.
    *   Updated the frontend (`list.html`) to display the stats using DaisyUI components.

5.  **Critical Bug Fixes**:
    *   **SSE `AsyncRequestNotUsableException`**: Gracefully handled exceptions that occurred when the SSE heartbeat tried to write to a connection already closed by the client.
    *   **Duplicate Certificate Handling**: Fixed a bug where duplicate certificates were not being added to the `ParsedFile`, preventing validation. Now, all certificates are included in the parsing results for validation, while only new ones are persisted as `Certificate` entities.
    *   **Audit Trail Support**: Changed the primary key of `parsed_certificate` to a composite key `(parsed_file_id, fingerprint_sha256)` to allow tracking the same certificate across multiple uploads, as requested by the user.

### ⚠️ Outstanding Issues & TODO List

The high-priority issues from the last review (2025-11-26) have been resolved. The remaining tasks are of lower priority or are planned future enhancements.

1.  **Manual Mode Implementation**: The `ProcessingController` endpoints for manual control (`/parse`, `/validate`, etc.) are placeholders and need to be wired to their respective use cases. (Planned for Phase 20).
2.  **Testing**: Unit and integration tests for `LdifConverter` and `UploadToLdapUseCase` are marked as optional but recommended.
3.  **Performance Optimization**: N+1 query problems may exist in the new statistics feature; investigation and potential caching strategies are noted as future enhancements.

---

## 📄 ICAO 9303 SOD (Security Object Document) 구조 및 파싱 가이드

### SOD 개요

SOD (Security Object Document)는 ePassport의 데이터 무결성을 보장하기 위한 핵심 데이터 구조입니다.

- **표준**: ICAO Doc 9303 Part 10 (Logical Data Structure) & Part 11 (Passive Authentication)
- **형식**: PKCS#7 CMS SignedData (RFC 5652)
- **용도**: Passive Authentication (PA)
- **서명자**: Document Signer Certificate (DSC)

### EF.SOD 파일 구조 (ICAO 9303 Part 10)

```
Tag 0x77 (Application[23]) - ICAO EF.SOD wrapper
  ├─ Length (TLV encoding)
  │   ├─ Short form: 0x00-0x7F (length in lower 7 bits)
  │   └─ Long form: 0x80-0xFF (number of octets in lower 7 bits)
  │
  └─ Value: CMS SignedData (Tag 0x30 SEQUENCE)
       ├─ version (INTEGER)
       ├─ digestAlgorithms (SET OF DigestAlgorithmIdentifier)
       ├─ encapContentInfo (EncapsulatedContentInfo)
       │   ├─ eContentType: id-icao-ldsSecurityObject (2.23.136.1.1.1)
       │   └─ eContent: LDSSecurityObject (OCTET STRING)
       │       ├─ version (INTEGER)
       │       ├─ hashAlgorithm (DigestAlgorithmIdentifier)
       │       └─ dataGroupHashValues (SEQUENCE OF DataGroupHash)
       │           ├─ DataGroup 1 (MRZ) hash
       │           ├─ DataGroup 2 (Face image) hash
       │           └─ ... (other Data Groups)
       │
       ├─ certificates [0] IMPLICIT SEQUENCE OF Certificate
       │   └─ DSC certificate (X.509) ← **여기서 DSC 추출**
       │
       └─ signerInfos (SET OF SignerInfo)
           └─ SignerInfo
               ├─ digestAlgorithm (e.g., SHA-256 OID)
               ├─ signatureAlgorithm (e.g., RSA-PSS OID)
               └─ signature (DSC's signature)
```

### ASN.1 TLV Length 인코딩

**Short Form (0-127 bytes)**:
```
77 14 [20 bytes of data...]
   └─ 0x14 = 20
```

**Long Form (128+ bytes)**:
```
77 82 07 3D [1853 bytes...]
   │  │  └─ 0x073D = 1853 bytes (big-endian)
   │  └─ 2 octets follow (0x82 = 0x80 | 0x02)
   └─ Long form indicator
```

### 핵심 파싱 로직

**1. Tag 0x77 Unwrapping**:
```java
// ICAO 9303 Tag 0x77 (Application[23]) wrapper 제거
ASN1Primitive asn1 = new ASN1InputStream(sodBytes).readObject();
if (asn1 instanceof ASN1TaggedObject tagged) {
    // BERTags.APPLICATION, tagNo=23 확인
    ASN1Primitive content = tagged.getBaseObject().toASN1Primitive();
    byte[] cmsBytes = content.getEncoded(ASN1Encoding.DER);
}
```

**2. DSC 추출 (SOD certificates[0]에서)**:
```java
CMSSignedData cms = new CMSSignedData(cmsBytes);
X509CertificateHolder holder = cms.getCertificates().getMatches(null).iterator().next();
X509Certificate dsc = CertificateFactory.getInstance("X.509", "BC")
    .generateCertificate(new ByteArrayInputStream(holder.getEncoded()));
```

**3. 서명 검증**:
```java
SignerInformation signerInfo = cms.getSignerInfos().getSigners().iterator().next();
boolean valid = signerInfo.verify(
    new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(dscCertificate)
);
```

### 알고리즘 OID 매핑

**Hash Algorithms (digestAlgorithm)**:

| OID | Algorithm |
|-----|-----------|
| 2.16.840.1.101.3.4.2.1 | SHA-256 |
| 2.16.840.1.101.3.4.2.2 | SHA-384 |
| 2.16.840.1.101.3.4.2.3 | SHA-512 |

**Encryption Algorithms (encryptionAlgOID)**:

| OID | Algorithm |
|-----|-----------|
| 1.2.840.113549.1.1.1 | RSA |
| 1.2.840.113549.1.1.10 | RSA-PSS |
| 1.2.840.10045.2.1 | ECDSA |

### ⚠️ 주의사항 및 일반적인 실수

1. **extractSignatureAlgorithm 오류**:
   - ❌ `signerInfo.getDigestAlgorithmID()` - 이것은 해시 알고리즘!
   - ✅ `signerInfo.getEncryptionAlgOID()` - 이것이 서명(암호화) 알고리즘

2. **Tag 0x77 처리**:
   - EF.SOD 파일은 항상 Tag 0x77 (Application[23])로 시작
   - CMS 데이터 추출 전에 반드시 unwrapping 필요

3. **DSC 추출 방식**:
   - ✅ SOD의 certificates[0]에서 직접 추출 (ICAO 표준)
   - ❌ LDAP에서 DSC를 검색하는 방식은 불필요

4. **CSCA 조회**:
   - DSC의 Issuer DN으로 LDAP에서 CSCA 검색
   - DSC.verify(CSCA.getPublicKey()) 로 Trust Chain 검증

### Passive Authentication 전체 흐름

```
1. Client → API: SOD + Data Groups
2. unwrapIcaoSod(SOD) → CMS SignedData 추출
3. extractDscCertificate(SOD) → DSC 추출 (from certificates[0])
4. DSC Issuer DN → LDAP에서 CSCA 검색
5. DSC.verify(CSCA.publicKey) → Trust Chain 검증
6. verifySignature(SOD, DSC) → SOD 서명 검증
7. parseDataGroupHashes(SOD) → 예상 해시값 추출
8. 각 DG 실제 해시 계산 후 비교
9. Result: VALID / INVALID / ERROR
```

---

**Document Version**: 4.0
**Status**: STABLE
**Last Review**: 2025-12-19
