# Phase 10: File Parsing (LDIF & Master List) - DDD Implementation

**작성일**: 2025-10-23
**상태**: 🚧 In Progress
**담당**: SmartCore Development Team

---

## 📋 목차

1. [개요](#개요)
2. [설계 원칙](#설계-원칙)
3. [아키텍처 설계](#아키텍처-설계)
4. [Domain Layer](#domain-layer)
5. [Application Layer](#application-layer)
6. [Infrastructure Layer](#infrastructure-layer)
7. [SSE 통합](#sse-통합)
8. [구현 계획](#구현-계획)

---

## 개요

### 목적

업로드된 LDIF 및 Master List 파일을 파싱하여 인증서 및 CRL 데이터를 추출하고, SSE를 통해 실시간 진행 상황을 사용자에게 전달합니다.

### 범위

- **LDIF Parser**: CSCA Complete/Delta, eMRTD Complete/Delta 파일 파싱
- **Master List Parser**: Signed CMS 파일 파싱
- **SSE Integration**: 파싱 진행률 실시간 전송
- **Error Handling**: 파싱 오류 상세 추적 및 보고

### Legacy vs DDD

| 항목 | Legacy | DDD (Phase 10) |
|------|--------|----------------|
| **아키텍처** | Anemic Domain Model | Rich Domain Model |
| **패턴** | Transaction Script | Domain-Driven Design |
| **책임 분리** | Service Layer에 모든 로직 | Domain → Application → Infrastructure |
| **재사용성** | 낮음 (의존성 강함) | 높음 (Port & Adapter) |
| **테스트 용이성** | 어려움 | 쉬움 (각 레이어 독립 테스트) |
| **SSE 통합** | ❌ 없음 | ✅ 있음 (ProcessingProgress) |

---

## 설계 원칙

### 1. DDD Patterns

- **Aggregate Root**: ParsedFile (파싱 결과의 일관성 경계)
- **Value Objects**: CertificateData, CrlData, ParsingStatistics
- **Domain Events**: FileParsingStartedEvent, FileParsingCompletedEvent, ParsingFailedEvent
- **Repository**: ParsedFileRepository (Domain → Infrastructure 의존성 역전)

### 2. Hexagonal Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  Application Layer                          │
│  (Use Cases: ParseLdifFileUseCase, ParseMlFileUseCase)      │
└────────────┬────────────────────────────────┬───────────────┘
             │                                │
       ┌─────▼─────┐                    ┌────▼──────┐
       │  Domain   │                    │ Progress  │
       │   Layer   │                    │  Service  │
       └─────┬─────┘                    └────┬──────┘
             │                                │
    ┌────────▼────────┐              ┌───────▼────────┐
    │  FileParserPort │              │  SSE Emitter   │
    │  (Interface)    │              │  Broadcasting  │
    └────────┬────────┘              └────────────────┘
             │
    ┌────────▼────────────────┐
    │ Infrastructure Adapters │
    │  - LdifParserAdapter    │
    │  - MlParserAdapter      │
    └─────────────────────────┘
```

### 3. SOLID Principles

- **Single Responsibility**: 각 Parser는 하나의 파일 포맷만 처리
- **Open/Closed**: 새로운 파일 포맷 추가 시 기존 코드 수정 불필요
- **Liskov Substitution**: FileParserPort 구현체는 교체 가능
- **Interface Segregation**: 역할별 인터페이스 분리
- **Dependency Inversion**: Domain이 Infrastructure에 의존하지 않음

---

## 아키텍처 설계

### Bounded Context: File Parsing

```
fileparsing/
├── domain/                          # Domain Layer
│   ├── model/
│   │   ├── ParsedFile.java          # Aggregate Root
│   │   ├── ParsedFileId.java        # Entity ID (JPearl)
│   │   ├── CertificateData.java     # Value Object
│   │   ├── CrlData.java             # Value Object
│   │   ├── ParsingStatistics.java   # Value Object
│   │   ├── ParsingStatus.java       # Value Object (Enum)
│   │   └── ParsingError.java        # Value Object
│   ├── event/
│   │   ├── FileParsingStartedEvent.java
│   │   ├── FileParsingCompletedEvent.java
│   │   ├── CertificatesExtractedEvent.java
│   │   └── ParsingFailedEvent.java
│   ├── port/                        # Hexagonal Ports
│   │   └── FileParserPort.java      # Interface for parsing
│   └── repository/
│       └── ParsedFileRepository.java # Repository Interface
│
├── application/                     # Application Layer
│   ├── command/
│   │   ├── ParseLdifFileCommand.java
│   │   └── ParseMasterListFileCommand.java
│   ├── response/
│   │   ├── ParseFileResponse.java
│   │   └── ParsingProgressResponse.java
│   └── usecase/
│       ├── ParseLdifFileUseCase.java
│       └── ParseMasterListFileUseCase.java
│
└── infrastructure/                  # Infrastructure Layer
    ├── adapter/
    │   ├── LdifParserAdapter.java   # FileParserPort 구현
    │   └── MasterListParserAdapter.java
    ├── parser/                      # Low-level parsing logic
    │   ├── LdifEntryParser.java
    │   ├── CmsSignatureParser.java
    │   └── CertificateExtractor.java
    └── repository/
        ├── JpaParsedFileRepository.java
        └── SpringDataParsedFileRepository.java
```

---

## Domain Layer

### 1. Aggregate Root: ParsedFile

```java
package com.smartcoreinc.localpkd.fileparsing.domain.model;

import com.smartcoreinc.localpkd.shared.domain.AbstractAggregateRoot;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.fileparsing.domain.event.*;
import io.github.wimdeblauwe.jpearl.AbstractEntityId;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ParsedFile - 파싱된 파일 Aggregate Root
 *
 * <p><b>Aggregate Boundary</b>: 파일 파싱 결과 및 추출된 인증서/CRL의 일관성 보장</p>
 *
 * <p><b>Business Rules</b>:</p>
 * <ul>
 *   <li>파싱은 RECEIVED → PARSING → PARSED/FAILED 순서로만 진행</li>
 *   <li>파싱 완료 후에는 추출된 인증서/CRL 수정 불가</li>
 *   <li>파싱 실패 시 오류 정보 필수 기록</li>
 * </ul>
 *
 * <p><b>Example</b>:</p>
 * <pre>
 * ParsedFile parsedFile = ParsedFile.create(
 *     ParsedFileId.newId(),
 *     uploadId,
 *     FileFormat.CSCA_COMPLETE_LDIF
 * );
 *
 * parsedFile.startParsing();
 * // → FileParsingStartedEvent 발행
 *
 * parsedFile.addCertificate(certificateData);
 * parsedFile.completeParsing(statistics);
 * // → FileParsingCompletedEvent, CertificatesExtractedEvent 발행
 * </pre>
 */
@Entity
@Table(name = "parsed_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParsedFile extends AbstractAggregateRoot<ParsedFileId> {

    @EmbeddedId
    private ParsedFileId id;

    /**
     * 원본 업로드 파일 ID (외부 컨텍스트 참조)
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "id", column = @Column(name = "upload_id", nullable = false))
    })
    private UploadId uploadId;

    /**
     * 파일 포맷
     */
    @Embedded
    private FileFormat fileFormat;

    /**
     * 파싱 상태
     */
    @Embedded
    private ParsingStatus status;

    /**
     * 파싱 시작 시간
     */
    @Column(name = "parsing_started_at")
    private LocalDateTime parsingStartedAt;

    /**
     * 파싱 완료 시간
     */
    @Column(name = "parsing_completed_at")
    private LocalDateTime parsingCompletedAt;

    /**
     * 파싱 통계
     */
    @Embedded
    private ParsingStatistics statistics;

    /**
     * 추출된 인증서 목록 (Embedded Collection)
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "parsed_certificate",
        joinColumns = @JoinColumn(name = "parsed_file_id")
    )
    private List<CertificateData> certificates = new ArrayList<>();

    /**
     * 추출된 CRL 목록 (Embedded Collection)
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "parsed_crl",
        joinColumns = @JoinColumn(name = "parsed_file_id")
    )
    private List<CrlData> crls = new ArrayList<>();

    /**
     * 파싱 오류 목록
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "parsing_error",
        joinColumns = @JoinColumn(name = "parsed_file_id")
    )
    private List<ParsingError> errors = new ArrayList<>();

    // ========== Static Factory Methods ==========

    /**
     * ParsedFile 생성 (파싱 전 상태)
     */
    public static ParsedFile create(
        ParsedFileId id,
        UploadId uploadId,
        FileFormat fileFormat
    ) {
        ParsedFile parsedFile = new ParsedFile();
        parsedFile.id = id;
        parsedFile.uploadId = uploadId;
        parsedFile.fileFormat = fileFormat;
        parsedFile.status = ParsingStatus.received();
        parsedFile.statistics = ParsingStatistics.empty();

        return parsedFile;
    }

    // ========== Business Methods ==========

    /**
     * 파싱 시작
     */
    public void startParsing() {
        validateTransitionTo(ParsingStatus.PARSING);

        this.status = ParsingStatus.parsing();
        this.parsingStartedAt = LocalDateTime.now();

        // Domain Event 발행
        addDomainEvent(new FileParsingStartedEvent(
            this.id.getId(),
            this.uploadId.getId(),
            this.fileFormat.getType(),
            this.parsingStartedAt
        ));
    }

    /**
     * 인증서 추가
     */
    public void addCertificate(CertificateData certificate) {
        if (!this.status.isParsing()) {
            throw new IllegalStateException(
                "파싱 중 상태에서만 인증서를 추가할 수 있습니다."
            );
        }

        this.certificates.add(certificate);
    }

    /**
     * CRL 추가
     */
    public void addCrl(CrlData crl) {
        if (!this.status.isParsing()) {
            throw new IllegalStateException(
                "파싱 중 상태에서만 CRL을 추가할 수 있습니다."
            );
        }

        this.crls.add(crl);
    }

    /**
     * 파싱 오류 추가
     */
    public void addError(ParsingError error) {
        this.errors.add(error);
    }

    /**
     * 파싱 완료
     */
    public void completeParsing(ParsingStatistics statistics) {
        validateTransitionTo(ParsingStatus.PARSED);

        this.status = ParsingStatus.parsed();
        this.parsingCompletedAt = LocalDateTime.now();
        this.statistics = statistics;

        // Domain Events 발행
        addDomainEvent(new FileParsingCompletedEvent(
            this.id.getId(),
            this.uploadId.getId(),
            this.certificates.size(),
            this.crls.size(),
            statistics.getTotalProcessed(),
            this.parsingCompletedAt
        ));

        if (!this.certificates.isEmpty()) {
            addDomainEvent(new CertificatesExtractedEvent(
                this.id.getId(),
                this.uploadId.getId(),
                this.certificates.size(),
                this.crls.size()
            ));
        }
    }

    /**
     * 파싱 실패
     */
    public void failParsing(String errorMessage) {
        this.status = ParsingStatus.failed();
        this.parsingCompletedAt = LocalDateTime.now();

        addDomainEvent(new ParsingFailedEvent(
            this.id.getId(),
            this.uploadId.getId(),
            errorMessage,
            this.parsingCompletedAt
        ));
    }

    // ========== Private Methods ==========

    private void validateTransitionTo(ParsingStatus targetStatus) {
        if (!this.status.canTransitionTo(targetStatus)) {
            throw new IllegalStateException(
                String.format(
                    "파싱 상태 전환 불가: %s → %s",
                    this.status.name(),
                    targetStatus.name()
                )
            );
        }
    }
}
```

### 2. Value Objects

#### ParsedFileId (JPearl)

```java
package com.smartcoreinc.localpkd.fileparsing.domain.model;

import io.github.wimdeblauwe.jpearl.AbstractEntityId;
import javax.persistence.Embeddable;
import java.util.UUID;

/**
 * ParsedFileId - 타입 안전한 엔티티 ID
 */
@Embeddable
public class ParsedFileId extends AbstractEntityId<UUID> {

    protected ParsedFileId() {
    }

    public ParsedFileId(UUID id) {
        super(id);
    }

    public static ParsedFileId newId() {
        return new ParsedFileId(UUID.randomUUID());
    }

    public static ParsedFileId of(UUID id) {
        return new ParsedFileId(id);
    }

    public static ParsedFileId of(String id) {
        return new ParsedFileId(UUID.fromString(id));
    }
}
```

#### CertificateData

```java
package com.smartcoreinc.localpkd.fileparsing.domain.model;

import lombok.*;
import javax.persistence.Embeddable;
import javax.persistence.Column;
import javax.persistence.Lob;
import java.time.LocalDateTime;

/**
 * CertificateData - 추출된 인증서 데이터 Value Object
 *
 * <p><b>Immutability</b>: 생성 후 변경 불가</p>
 *
 * <p><b>Self-validation</b>: 생성 시 필수 필드 검증</p>
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CertificateData {

    /**
     * 인증서 타입 (CSCA, DSC, DSC_NC)
     */
    @Column(name = "cert_type", length = 20, nullable = false)
    private String certificateType;

    /**
     * 발급 국가 코드 (ISO 3166-1 alpha-2)
     */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    /**
     * Subject DN
     */
    @Column(name = "subject_dn", length = 500, nullable = false)
    private String subjectDN;

    /**
     * Issuer DN
     */
    @Column(name = "issuer_dn", length = 500, nullable = false)
    private String issuerDN;

    /**
     * Serial Number (Hex)
     */
    @Column(name = "serial_number", length = 100, nullable = false)
    private String serialNumber;

    /**
     * 유효 시작일
     */
    @Column(name = "not_before", nullable = false)
    private LocalDateTime notBefore;

    /**
     * 유효 종료일
     */
    @Column(name = "not_after", nullable = false)
    private LocalDateTime notAfter;

    /**
     * 인증서 바이너리 (DER 인코딩)
     */
    @Lob
    @Column(name = "certificate_binary", nullable = false)
    private byte[] certificateBinary;

    /**
     * SHA-256 Fingerprint
     */
    @Column(name = "fingerprint_sha256", length = 64)
    private String fingerprintSha256;

    /**
     * 유효 여부
     */
    @Column(name = "is_valid", nullable = false)
    private boolean valid;

    // Static Factory Method
    public static CertificateData of(
        String certificateType,
        String countryCode,
        String subjectDN,
        String issuerDN,
        String serialNumber,
        LocalDateTime notBefore,
        LocalDateTime notAfter,
        byte[] certificateBinary,
        String fingerprintSha256,
        boolean valid
    ) {
        CertificateData data = new CertificateData();
        data.certificateType = certificateType;
        data.countryCode = countryCode;
        data.subjectDN = subjectDN;
        data.issuerDN = issuerDN;
        data.serialNumber = serialNumber;
        data.notBefore = notBefore;
        data.notAfter = notAfter;
        data.certificateBinary = certificateBinary;
        data.fingerprintSha256 = fingerprintSha256;
        data.valid = valid;

        // Validation
        data.validate();

        return data;
    }

    private void validate() {
        if (certificateType == null || certificateType.isBlank()) {
            throw new IllegalArgumentException("certificateType must not be blank");
        }
        if (subjectDN == null || subjectDN.isBlank()) {
            throw new IllegalArgumentException("subjectDN must not be blank");
        }
        if (issuerDN == null || issuerDN.isBlank()) {
            throw new IllegalArgumentException("issuerDN must not be blank");
        }
        if (serialNumber == null || serialNumber.isBlank()) {
            throw new IllegalArgumentException("serialNumber must not be blank");
        }
        if (notBefore == null) {
            throw new IllegalArgumentException("notBefore must not be null");
        }
        if (notAfter == null) {
            throw new IllegalArgumentException("notAfter must not be null");
        }
        if (certificateBinary == null || certificateBinary.length == 0) {
            throw new IllegalArgumentException("certificateBinary must not be empty");
        }
    }
}
```

#### ParsingStatistics

```java
package com.smartcoreinc.localpkd.fileparsing.domain.model;

import lombok.*;
import javax.persistence.Embeddable;
import javax.persistence.Column;

/**
 * ParsingStatistics - 파싱 통계 Value Object
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParsingStatistics {

    @Column(name = "total_entries")
    private int totalEntries;

    @Column(name = "total_processed")
    private int totalProcessed;

    @Column(name = "certificate_count")
    private int certificateCount;

    @Column(name = "crl_count")
    private int crlCount;

    @Column(name = "valid_count")
    private int validCount;

    @Column(name = "invalid_count")
    private int invalidCount;

    @Column(name = "error_count")
    private int errorCount;

    @Column(name = "duration_millis")
    private long durationMillis;

    public static ParsingStatistics empty() {
        ParsingStatistics stats = new ParsingStatistics();
        stats.totalEntries = 0;
        stats.totalProcessed = 0;
        stats.certificateCount = 0;
        stats.crlCount = 0;
        stats.validCount = 0;
        stats.invalidCount = 0;
        stats.errorCount = 0;
        stats.durationMillis = 0;
        return stats;
    }

    public static ParsingStatistics of(
        int totalEntries,
        int totalProcessed,
        int certificateCount,
        int crlCount,
        int validCount,
        int invalidCount,
        int errorCount,
        long durationMillis
    ) {
        ParsingStatistics stats = new ParsingStatistics();
        stats.totalEntries = totalEntries;
        stats.totalProcessed = totalProcessed;
        stats.certificateCount = certificateCount;
        stats.crlCount = crlCount;
        stats.validCount = validCount;
        stats.invalidCount = invalidCount;
        stats.errorCount = errorCount;
        stats.durationMillis = durationMillis;
        return stats;
    }

    public double getSuccessRate() {
        if (totalProcessed == 0) return 0.0;
        return (double) validCount / totalProcessed * 100.0;
    }
}
```

#### ParsingStatus (Enum Value Object)

```java
package com.smartcoreinc.localpkd.fileparsing.domain.model;

import lombok.Getter;

/**
 * ParsingStatus - 파싱 상태 Value Object
 */
@Getter
public enum ParsingStatus {
    RECEIVED("수신됨"),
    PARSING("파싱 중"),
    PARSED("파싱 완료"),
    FAILED("파싱 실패");

    private final String displayName;

    ParsingStatus(String displayName) {
        this.displayName = displayName;
    }

    public static ParsingStatus received() {
        return RECEIVED;
    }

    public static ParsingStatus parsing() {
        return PARSING;
    }

    public static ParsingStatus parsed() {
        return PARSED;
    }

    public static ParsingStatus failed() {
        return FAILED;
    }

    public boolean isParsing() {
        return this == PARSING;
    }

    public boolean isParsed() {
        return this == PARSED;
    }

    public boolean isFailed() {
        return this == FAILED;
    }

    /**
     * 상태 전환 가능 여부 검증
     */
    public boolean canTransitionTo(ParsingStatus target) {
        return switch (this) {
            case RECEIVED -> target == PARSING;
            case PARSING -> target == PARSED || target == FAILED;
            case PARSED, FAILED -> false; // 종료 상태에서는 전환 불가
        };
    }
}
```

---

## 구현 계획

### Sprint Plan

| Sprint | 작업 | 예상 시간 | 우선순위 |
|--------|------|-----------|----------|
| **Sprint 1** | Domain Layer 구현 (Aggregates, Value Objects, Events) | 1일 | ⭐⭐⭐ |
| **Sprint 2** | Application Layer 구현 (Commands, Use Cases) | 1일 | ⭐⭐⭐ |
| **Sprint 3** | Infrastructure Layer 구현 (Adapters, Parsers) | 2일 | ⭐⭐⭐ |
| **Sprint 4** | SSE 통합 (ProcessingProgress 전송) | 0.5일 | ⭐⭐ |
| **Sprint 5** | 테스트 작성 (Unit, Integration) | 1일 | ⭐⭐ |
| **Sprint 6** | 문서화 및 배포 | 0.5일 | ⭐ |

**총 예상 시간**: 6일

---

## Next Steps

1. ✅ Phase 10 설계 문서 작성 (완료)
2. ⏳ Domain Layer 구현 시작
3. ⏳ Application Layer 구현
4. ⏳ Infrastructure Layer 구현
5. ⏳ SSE 통합
6. ⏳ 테스트 작성

---

**Document Version**: 1.0
**Last Updated**: 2025-10-23
**Status**: 설계 완료, 구현 시작 대기
