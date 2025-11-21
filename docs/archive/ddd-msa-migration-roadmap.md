# DDD & MSA Migration Roadmap

## 개요

이 문서는 Local PKD 프로젝트를 현재의 Layered Monolith 구조에서 **DDD (Domain-Driven Design) 기반 Modular Monolith**로 전환하는 상세 로드맵을 제시합니다.

**목표**:
- 도메인 중심의 명확한 책임 분리
- 향후 MSA 전환 용이성
- 테스트 가능성 및 유지보수성 향상

**기간**: 8주 (Phase 1~8)
**상태**: Planning & Design

---

## 현재 상태 (As-Is)

### 문제점

1. **FileUploadService의 과도한 책임**
   - 파일 수신, 검증, 체크섬 계산, 중복 검사, 버전 관리 모두 포함
   - 단일 책임 원칙 위반 (600+ 라인)

2. **도메인 로직 분산**
   - 비즈니스 규칙이 Service 레이어에 산재
   - 테스트 어려움 (통합 테스트 위주)

3. **확장성 제한**
   - 새로운 파일 타입 추가 시 기존 코드 수정 필요
   - Open-Closed Principle 위반

4. **컨텍스트 간 경계 불명확**
   - Upload, Parsing, Validation, LDAP가 모두 혼재
   - 의존성 관리 어려움

---

## Phase 1: 도메인 모델 설계 (Week 1-2)

### Sprint 9: Domain Model Design

#### 목표
- 6개 Bounded Context 정의 및 문서화
- Aggregate Root, Value Object, Domain Event 설계
- Repository Interface 정의

#### Tasks

##### Task 1: Bounded Context 정의 (✅ Completed)

**결과물**: CLAUDE.md에 6개 컨텍스트 문서화 완료
1. File Upload Context
2. File Parsing Context
3. Certificate Validation Context
4. LDAP Integration Context
5. Storage Context
6. Audit & History Context

##### Task 2: Aggregate Root 설계

**목표**: 각 컨텍스트의 핵심 엔티티를 Aggregate Root로 설계

**File Upload Context**:
```java
/**
 * Aggregate Root: UploadedFile
 * 책임: 파일 업로드 생명주기 관리
 */
public class UploadedFile extends AggregateRoot {
    private UploadId id;
    private FileName fileName;
    private FileSize size;
    private FileHash hash;
    private UploadStatus status;
    private UploadedAt uploadedAt;
    private List<DomainEvent> domainEvents;

    // Domain Methods (비즈니스 규칙)
    public void validateSize(long maxSize) {
        if (size.getBytes() > maxSize) {
            throw new FileSizeLimitExceededException();
        }
    }

    public void calculateHash(FileHashCalculator calculator) {
        this.hash = calculator.calculate(this);
        addDomainEvent(new FileHashCalculatedEvent(this.id, this.hash));
    }

    public void markAsDuplicate(UploadedFile existingFile) {
        this.status = UploadStatus.DUPLICATE_DETECTED;
        addDomainEvent(new DuplicateFileDetectedEvent(this.id, existingFile.getId()));
    }

    public void markAsReceived() {
        this.status = UploadStatus.RECEIVED;
        addDomainEvent(new FileUploadedEvent(this.id, this.fileName));
    }
}
```

**File Parsing Context**:
```java
/**
 * Aggregate Root: ParsedFile
 * 책임: 파일 파싱 및 인증서 추출
 */
public class ParsedFile extends AggregateRoot {
    private ParseId id;
    private UploadId uploadId;  // 외부 컨텍스트 참조 (ID만)
    private FileFormat format;
    private ParsedContent content;
    private ParseStatus status;
    private ParsedAt parsedAt;

    public void parse(FileParser parser) {
        this.content = parser.parse(this);
        this.status = ParseStatus.PARSING_COMPLETED;
        addDomainEvent(new FileParsingCompletedEvent(this.id));
    }

    public List<Certificate> extractCertificates() {
        return content.getCertificates();
    }
}
```

**Certificate Validation Context**:
```java
/**
 * Aggregate Root: Certificate
 * 책임: 인증서 검증 및 Trust Chain 관리
 */
public class Certificate extends AggregateRoot {
    private CertificateId id;
    private X509Data x509Data;
    private IssuerInfo issuer;
    private SubjectInfo subject;
    private ValidityPeriod validity;
    private ValidationStatus status;

    public void validateTrustChain(TrustChainValidator validator) {
        boolean isValid = validator.validate(this);
        if (isValid) {
            this.status = ValidationStatus.TRUST_CHAIN_VERIFIED;
            addDomainEvent(new TrustChainVerifiedEvent(this.id));
        } else {
            this.status = ValidationStatus.TRUST_CHAIN_INVALID;
            addDomainEvent(new ValidationFailedEvent(this.id));
        }
    }

    public boolean isExpired() {
        return validity.isExpired(LocalDateTime.now());
    }
}
```

**LDAP Integration Context**:
```java
/**
 * Aggregate Root: LdapEntry
 * 책임: LDAP 디렉토리 엔트리 관리
 */
public class LdapEntry extends AggregateRoot {
    private EntryId id;
    private DistinguishedName dn;
    private CertificateId certificateId;
    private LdapAttributes attributes;
    private SyncStatus status;

    public void uploadToLdap(LdapConnection connection) {
        connection.add(this.dn, this.attributes);
        this.status = SyncStatus.UPLOADED;
        addDomainEvent(new LdapUploadCompletedEvent(this.id));
    }

    public void updateAttributes(LdapAttributes newAttributes) {
        this.attributes = this.attributes.merge(newAttributes);
        addDomainEvent(new LdapAttributesUpdatedEvent(this.id));
    }
}
```

**Storage Context**:
```java
/**
 * Aggregate Root: StoredFile
 * 책임: 파일 시스템 저장소 관리
 */
public class StoredFile extends AggregateRoot {
    private StorageId id;
    private FilePath path;
    private StorageLocation location;
    private StorageMetadata metadata;

    public void moveToPermStorage(Path permanentPath) {
        Files.move(this.path.toPath(), permanentPath);
        this.location = StorageLocation.PERMANENT;
        this.path = new FilePath(permanentPath);
        addDomainEvent(new FileMovedToPermanentStorageEvent(this.id));
    }

    public void cleanupTemp() {
        if (this.location == StorageLocation.TEMPORARY) {
            Files.delete(this.path.toPath());
            addDomainEvent(new TempFileDeletedEvent(this.id));
        }
    }
}
```

**Audit & History Context**:
```java
/**
 * Aggregate Root: UploadHistory
 * 책임: 업로드 이력 및 통계 관리
 */
public class UploadHistory extends AggregateRoot {
    private HistoryId id;
    private UploadId uploadId;
    private Timeline timeline;
    private ProcessingSteps steps;
    private Statistics stats;

    public void recordStep(ProcessingStep step) {
        this.steps.add(step);
        this.timeline.addEvent(step.toEvent());
    }

    public Statistics calculateStatistics() {
        return Statistics.from(this.steps);
    }
}
```

**작업 항목**:
- [ ] 각 Aggregate Root 클래스 스켈레톤 작성
- [ ] Aggregate 간 참조 규칙 정의 (ID 참조만 허용)
- [ ] Aggregate 경계 명확화 (Transaction boundary)

##### Task 3: Value Object 식별 및 목록 작성

**목표**: 불변성과 동등성이 필요한 개념을 Value Object로 설계

**공통 Value Objects**:
```java
// 식별자 Value Objects
public record UploadId(UUID value) implements ValueObject {}
public record ParseId(UUID value) implements ValueObject {}
public record CertificateId(UUID value) implements ValueObject {}

// 시간 Value Objects
public record UploadedAt(LocalDateTime value) implements ValueObject {}
public record ParsedAt(LocalDateTime value) implements ValueObject {}
public record ValidityPeriod(LocalDateTime notBefore, LocalDateTime notAfter) {
    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(notAfter);
    }
}
```

**File Upload Context Value Objects**:
```java
public record FileName(String value, FileExtension extension) {
    public FileName {
        if (value == null || value.isBlank()) {
            throw new InvalidFileNameException("Filename cannot be blank");
        }
    }
}

public enum FileExtension {
    LDIF(".ldif"),
    ML(".ml");

    private final String value;
}

public record FileHash(String sha256, String sha1) {
    public FileHash {
        if (!isValidSHA256(sha256)) {
            throw new InvalidHashException("Invalid SHA-256");
        }
    }
}

public record FileSize(long bytes) {
    public String toDisplayString() {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / 1024 / 1024) + " MB";
    }
}
```

**File Parsing Context Value Objects**:
```java
public record FileFormat(FormatType type, boolean isDelta, String collection) {}

public enum FormatType {
    CSCA_LDIF,
    EMRTD_LDIF,
    NON_CONFORMANT_LDIF,
    MASTER_LIST
}

public record ParsedContent(List<Certificate> certificates, Metadata metadata) {}
```

**Certificate Validation Context Value Objects**:
```java
public record X509Data(byte[] encoded, PublicKey publicKey) {}

public record IssuerInfo(String commonName, String country) {}

public record SubjectInfo(String commonName, String country) {}
```

**LDAP Integration Context Value Objects**:
```java
public record DistinguishedName(String value) {
    public DistinguishedName {
        if (!isValidDN(value)) {
            throw new InvalidDNException("Invalid DN format");
        }
    }
}

public record LdapAttributes(Map<String, List<String>> attributes) {}
```

**Storage Context Value Objects**:
```java
public record FilePath(Path absolutePath, String relativePath) {}

public record StorageLocation(StorageType type, String basePath) {}

public enum StorageType {
    TEMPORARY,
    PERMANENT
}

public record StorageMetadata(long size, LocalDateTime createdAt) {}
```

**작업 항목**:
- [ ] Value Object 목록 완성 (30+ classes)
- [ ] Validation 규칙 정의
- [ ] Immutability 보장 (Java Record 활용)
- [ ] Equality 및 HashCode 구현

##### Task 4: Domain Event 정의

**목표**: 컨텍스트 간 통신을 위한 도메인 이벤트 정의

**Base Interface**:
```java
public interface DomainEvent {
    UUID eventId();
    LocalDateTime occurredOn();
    String eventType();
}
```

**File Upload Context Events**:
```java
public record FileUploadedEvent(
    UUID eventId,
    LocalDateTime occurredOn,
    UploadId uploadId,
    FileName fileName,
    FileSize size
) implements DomainEvent {
    @Override
    public String eventType() {
        return "FileUploaded";
    }
}

public record FileHashCalculatedEvent(
    UUID eventId,
    LocalDateTime occurredOn,
    UploadId uploadId,
    FileHash hash
) implements DomainEvent {}

public record DuplicateFileDetectedEvent(
    UUID eventId,
    LocalDateTime occurredOn,
    UploadId uploadId,
    UploadId existingFileId
) implements DomainEvent {}

public record FileValidationFailedEvent(
    UUID eventId,
    LocalDateTime occurredOn,
    UploadId uploadId,
    String reason
) implements DomainEvent {}
```

**File Parsing Context Events**:
```java
public record FileParsingStartedEvent(
    UUID eventId,
    LocalDateTime occurredOn,
    ParseId parseId,
    UploadId uploadId
) implements DomainEvent {}

public record FileParsingCompletedEvent(
    UUID eventId,
    LocalDateTime occurredOn,
    ParseId parseId,
    int certificateCount
) implements DomainEvent {}

public record CertificatesExtractedEvent(
    UUID eventId,
    LocalDateTime occurredOn,
    ParseId parseId,
    List<CertificateId> certificateIds
) implements DomainEvent {}

public record ParsingFailedEvent(
    UUID eventId,
    LocalDateTime occurredOn,
    ParseId parseId,
    String reason
) implements DomainEvent {}
```

**Certificate Validation Context Events**:
```java
public record CertificateValidatedEvent(
    UUID eventId,
    LocalDateTime occurredOn,
    CertificateId certificateId,
    ValidationStatus status
) implements DomainEvent {}

public record TrustChainVerifiedEvent(
    UUID eventId,
    LocalDateTime occurredOn,
    CertificateId certificateId
) implements DomainEvent {}

public record ValidationFailedEvent(
    UUID eventId,
    LocalDateTime occurredOn,
    CertificateId certificateId,
    String reason
) implements DomainEvent {}
```

**LDAP Integration Context Events**:
```java
public record LdapUploadStartedEvent(
    UUID eventId,
    LocalDateTime occurredOn,
    EntryId entryId,
    CertificateId certificateId
) implements DomainEvent {}

public record LdapUploadCompletedEvent(
    UUID eventId,
    LocalDateTime occurredOn,
    EntryId entryId
) implements DomainEvent {}

public record LdapSyncFailedEvent(
    UUID eventId,
    LocalDateTime occurredOn,
    EntryId entryId,
    String reason
) implements DomainEvent {}
```

**이벤트 플로우**:
```
FileUploadedEvent
  → 파싱 시작 트리거
    → FileParsingStartedEvent
      → FileParsingCompletedEvent
        → 검증 시작 트리거
          → CertificateValidatedEvent
            → LDAP 업로드 트리거
              → LdapUploadCompletedEvent
                → 이력 기록
```

**작업 항목**:
- [ ] 20+ Domain Event 클래스 작성
- [ ] Event Schema 정의 (JSON 직렬화)
- [ ] Event Store 설계 (선택사항)

##### Task 5: Repository Interface 정의

**목표**: Domain Layer에서 Repository Interface 정의 (구현은 Infrastructure Layer)

**Base Repository Interface**:
```java
public interface Repository<T extends AggregateRoot, ID> {
    Optional<T> findById(ID id);
    T save(T aggregate);
    void delete(T aggregate);
}
```

**File Upload Context Repository**:
```java
public interface UploadedFileRepository extends Repository<UploadedFile, UploadId> {
    Optional<UploadedFile> findByFileHash(FileHash hash);
    List<UploadedFile> findRecentUploads(int limit);
    long countByStatus(UploadStatus status);
}
```

**File Parsing Context Repository**:
```java
public interface ParsedFileRepository extends Repository<ParsedFile, ParseId> {
    Optional<ParsedFile> findByUploadId(UploadId uploadId);
    List<ParsedFile> findByStatus(ParseStatus status);
}
```

**Certificate Validation Context Repository**:
```java
public interface CertificateRepository extends Repository<Certificate, CertificateId> {
    List<Certificate> findByIssuer(IssuerInfo issuer);
    List<Certificate> findExpiredCertificates(LocalDateTime now);
}
```

**LDAP Integration Context Repository**:
```java
public interface LdapEntryRepository extends Repository<LdapEntry, EntryId> {
    Optional<LdapEntry> findByCertificateId(CertificateId certificateId);
    List<LdapEntry> findBySyncStatus(SyncStatus status);
}
```

**작업 항목**:
- [ ] 각 컨텍스트별 Repository Interface 정의
- [ ] Custom Query Method 정의
- [ ] Specification Pattern 적용 검토

#### Deliverables (Week 1-2)

- [x] CLAUDE.md 업데이트 (Bounded Context 정의)
- [ ] Domain Model UML 다이어그램
- [ ] Aggregate 설계 문서 (6개 컨텍스트)
- [ ] Value Object 목록 (30+ classes)
- [ ] Domain Event 목록 (20+ events)
- [ ] Repository Interface 정의 (6개)
- [ ] Context Map (컨텍스트 간 관계도)

---

## Phase 2: Shared Kernel 구현 (Week 2)

### Sprint 10: Shared Kernel Implementation

#### 목표
모든 컨텍스트에서 공유할 기본 클래스 및 인프라 구현

#### Tasks

##### Task 1: AggregateRoot 추상 클래스

```java
package com.smartcoreinc.localpkd.shared.domain;

public abstract class AggregateRoot extends Entity {
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected void addDomainEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }
}
```

##### Task 2: Entity 베이스 클래스

```java
package com.smartcoreinc.localpkd.shared.domain;

public abstract class Entity<ID> {
    protected ID id;

    protected Entity(ID id) {
        Objects.requireNonNull(id, "ID cannot be null");
        this.id = id;
    }

    public ID getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entity<?> entity)) return false;
        return id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
```

##### Task 3: ValueObject 마커 인터페이스

```java
package com.smartcoreinc.localpkd.shared.domain;

/**
 * Value Object 마커 인터페이스
 * Java Record로 구현 권장
 */
public interface ValueObject {
    // Marker interface
    // Record는 자동으로 equals, hashCode, toString 제공
}
```

##### Task 4: EventBus 구현

```java
package com.smartcoreinc.localpkd.shared.event;

@Component
public class EventBus {
    private final ApplicationEventPublisher eventPublisher;

    public EventBus(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publish(DomainEvent event) {
        eventPublisher.publishEvent(event);
    }

    public void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
    }
}
```

##### Task 5: 공통 Exception 클래스

```java
package com.smartcoreinc.localpkd.shared.exception;

public class DomainException extends RuntimeException {
    private final String errorCode;

    public DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

// Specific exceptions
public class FileSizeLimitExceededException extends DomainException {}
public class InvalidFileNameException extends DomainException {}
public class DuplicateFileException extends DomainException {}
```

#### Deliverables (Week 2)

- [ ] `shared/domain/` 패키지 구현
- [ ] `shared/event/` 패키지 구현
- [ ] `shared/exception/` 패키지 구현
- [ ] 단위 테스트 (JUnit 5 + AssertJ)
- [ ] 사용 가이드 문서

---

## Phase 3-8: 각 Bounded Context 구현

상세 내용은 CLAUDE.md 참조:
- Phase 3: File Upload Context (Week 3)
- Phase 4: File Parsing Context (Week 4)
- Phase 5: Certificate Validation Context (Week 5)
- Phase 6: LDAP Integration Context (Week 6)
- Phase 7: Storage & Audit Contexts (Week 7)
- Phase 8: Event-Driven Architecture (Week 8)

---

## 다음 단계 (Immediate Actions)

### 우선순위 1: Phase 1 완료 (현재)

1. [ ] Aggregate Root 스켈레톤 클래스 작성
2. [ ] Value Object 목록 작성 및 검증
3. [ ] Domain Event 목록 작성
4. [ ] Repository Interface 정의

### 우선순위 2: Phase 2 시작 (다음 주)

1. [ ] Shared Kernel 패키지 생성
2. [ ] AggregateRoot, Entity, ValueObject 구현
3. [ ] EventBus 구현 및 테스트

### 우선순위 3: Phase 3 준비

1. [ ] File Upload Context 디렉토리 구조 생성
2. [ ] 기존 FileUploadService 분석
3. [ ] 마이그레이션 계획 수립

---

## 성공 지표 (KPIs)

1. **코드 품질**
   - 각 Aggregate 클래스: < 200 라인
   - 순환 복잡도: < 10
   - 테스트 커버리지: > 80%

2. **아키텍처 준수**
   - Dependency Rule 위반: 0건
   - 컨텍스트 간 직접 참조: 0건 (ID 참조만)
   - Domain Layer 외부 의존성: 0건

3. **성능**
   - 기존 대비 응답 시간: ±10% 이내
   - 메모리 사용량: +20% 이내

---

## 참고 자료

- [CLAUDE.md](../CLAUDE.md) - 전체 아키텍처 문서
- [DDD Reference](https://www.domainlanguage.com/ddd/) - Eric Evans
- [Implementing DDD](https://vaughnvernon.com/) - Vaughn Vernon

---

**문서 버전**: 1.0
**작성일**: 2025-10-18
**작성자**: Development Team
**상태**: Active Planning
