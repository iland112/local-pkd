# Passive Authentication (PA) Implementation Plan

**작성일**: 2025-12-12
**업데이트**: 2025-12-12 (Phase 1 Complete)
**목적**: ICAO 9303 표준 기반 전자 여권 무결성 검증 REST API 구현
**상태**: ✅ Phase 1 Complete (Domain Layer) | ⏳ Phase 2-5 Pending

---

## 🎯 Implementation Progress

| Phase | Component | Status | Files | Notes |
|-------|-----------|--------|-------|-------|
| **Phase 1** | Domain Layer | ✅ COMPLETE | 17 files | [Details](PA_PHASE_1_COMPLETE.md) |
| **Phase 2** | Infrastructure Layer | ⏳ Pending | - | Next session |
| **Phase 3** | Application Layer | ⏳ Pending | - | After Phase 2 |
| **Phase 4** | Web Layer | ⏳ Pending | - | After Phase 3 |
| **Phase 5** | Testing | ⏳ Pending | - | After Phase 4 |

**Quick Stats**:
- ✅ 17 Java files created
- ✅ ~2,500 lines of code
- ✅ BUILD SUCCESS (0 errors)
- ✅ Naming consistency achieved (PassiveAuthentication prefix)

---

## 📚 Background Research

### PA(Passive Authentication)란?

전자 여권(eMRTD) 칩의 데이터 무결성을 검증하는 ICAO 9303 표준 보안 메커니즘입니다.

**검증 내용**:
- ✅ SOD(Security Object Document)와 LDS(Logical Data Structure)가 변조되지 않았음을 증명
- ✅ 발급 국가의 정당한 권한으로 데이터가 저장되었음을 확인
- ❌ 칩 복제 방지 불가 (AA/CA가 담당)

**검증 프로세스 3단계**:

1. **Certificate Chain Validation**
   - Trust Anchor (CSCA) → Document Signer Certificate (DSC) 체인 검증
   - CRL을 통한 인증서 폐기 확인

2. **SOD Signature Verification**
   - DSC 공개키로 SOD(PKCS#7 SignedData) 서명 검증
   - 서명 알고리즘: SHA256withRSA, SHA384withRSA 등

3. **Data Group Hash Verification**
   - SOD에 포함된 각 Data Group의 해시값 추출
   - 실제 Data Group을 해싱하여 SOD의 해시값과 비교
   - 해시 알고리즘: SHA-256, SHA-384, SHA-512

---

## 🏗️ Architecture Design

### New Bounded Context: `passiveauthentication`

**선택 이유**:
- PA(Passive Authentication)는 인증서 검증과 별개의 업무 도메인
- 향후 AA(Active Authentication), EAC(Extended Access Control) 확장 시 명확한 구분
- 단일 책임 원칙 (SRP) 준수

**디렉토리 구조**:

```
passiveauthentication/
├── domain/
│   ├── model/
│   │   ├── PassportData (Aggregate Root)
│   │   ├── PassportDataId (JPearl ID)
│   │   ├── SecurityObjectDocument (Value Object)
│   │   ├── DataGroup (Value Object)
│   │   ├── DataGroupNumber (Enum: DG1~DG16)
│   │   ├── DataGroupHash (Value Object)
│   │   ├── PassiveAuthenticationResult (Value Object)
│   │   ├── VerificationStatus (Enum: VALID, INVALID, ERROR)
│   │   └── VerificationError (Value Object)
│   ├── port/
│   │   └── SodParserPort (Hexagonal Architecture)
│   ├── repository/
│   │   └── PassportDataRepository
│   └── service/
│       └── PassiveAuthenticationService
├── application/
│   ├── command/
│   │   └── PerformPassiveAuthenticationCommand
│   ├── response/
│   │   ├── PassiveAuthenticationResponse
│   │   ├── CertificateChainValidationDto
│   │   ├── SodSignatureValidationDto
│   │   └── DataGroupValidationDto
│   └── usecase/
│       ├── PerformPassiveAuthenticationUseCase
│       └── GetPassiveAuthenticationHistoryUseCase
└── infrastructure/
    ├── adapter/
    │   └── BouncyCastleSodParserAdapter
    ├── repository/
    │   └── JpaPassportDataRepository
    └── web/
        └── PassiveAuthenticationController
```

---

## 🔧 Domain Model Design

### 1. PassportData (Aggregate Root)

```java
@Entity
@Table(name = "passport_verification")
public class PassportData extends AbstractAggregateRoot<PassportDataId> {

    @EmbeddedId
    private PassportDataId id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "issuing_country"))
    private CountryCode issuingCountry;

    @Column(name = "document_number", length = 20)
    private String documentNumber;

    @Embedded
    private SecurityObjectDocument sod;

    @ElementCollection
    @CollectionTable(name = "passport_data_group_validation")
    private Map<DataGroupNumber, DataGroup> dataGroups;

    @Embedded
    private PassiveAuthenticationResult verificationResult;

    // ✅ Audit: Timing Information
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "processing_duration_ms")
    private Long processingDurationMs;

    // ✅ Audit: Request Metadata
    @Embedded
    private RequestMetadata requestMetadata;

    // ✅ Audit: Raw Request Data (for re-verification)
    @Column(name = "raw_request_data", columnDefinition = "JSONB")
    private String rawRequestDataJson;

    // ✅ Audit: Detailed Logs (One-to-Many)
    @OneToMany(mappedBy = "verification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VerificationAuditLog> auditLogs = new ArrayList<>();

    @Transient
    private int logSequence = 0;  // Auto-increment for log_sequence

    // Business Logic with Audit
    public PassiveAuthenticationResult performPassiveAuthentication(
        Certificate dscCertificate,
        Certificate cscaCertificate,
        SodParserPort sodParser
    ) {
        // 1. Verify certificate chain
        addAuditLog(VerificationStep.CERTIFICATE_CHAIN, StepStatus.STARTED, LogLevel.INFO,
            "Starting certificate chain validation",
            Map.of("dscSubject", dscCertificate.getSubjectDn()));

        validateCertificateChain(dscCertificate, cscaCertificate);

        addAuditLog(VerificationStep.CERTIFICATE_CHAIN, StepStatus.COMPLETED, LogLevel.INFO,
            "Certificate chain validation completed successfully",
            Map.of("chainValid", true));

        // 2. Verify SOD signature
        addAuditLog(VerificationStep.SOD_SIGNATURE, StepStatus.STARTED, LogLevel.INFO,
            "Starting SOD signature verification",
            Map.of("hashAlgorithm", sod.getHashAlgorithm()));

        validateSodSignature(dscCertificate, sodParser);

        addAuditLog(VerificationStep.SOD_SIGNATURE, StepStatus.COMPLETED, LogLevel.INFO,
            "SOD signature verification succeeded",
            Map.of("signatureValid", true));

        // 3. Verify data group hashes
        addAuditLog(VerificationStep.DATA_GROUP_HASH, StepStatus.STARTED, LogLevel.INFO,
            "Starting data group hash validation",
            Map.of("totalDataGroups", dataGroups.size()));

        validateDataGroupHashes(sodParser);

        addAuditLog(VerificationStep.DATA_GROUP_HASH, StepStatus.COMPLETED, LogLevel.INFO,
            "Data group hash validation completed",
            Map.of("validDataGroups", verificationResult.getValidDataGroups()));

        return this.verificationResult;
    }

    // ✅ Audit: Start Verification
    public void startVerification(RequestMetadata metadata, String rawRequestData) {
        this.startedAt = LocalDateTime.now();
        this.requestMetadata = metadata;
        this.rawRequestDataJson = rawRequestData;

        addAuditLog(VerificationStep.VERIFICATION_STARTED, StepStatus.STARTED, LogLevel.INFO,
            String.format("Passport verification started for %s-%s",
                issuingCountry.getValue(), documentNumber),
            Map.of("issuingCountry", issuingCountry.getValue(),
                   "documentNumber", documentNumber));
    }

    // ✅ Audit: Complete Verification
    public void completeVerification() {
        this.completedAt = LocalDateTime.now();
        this.processingDurationMs = Duration.between(startedAt, completedAt).toMillis();

        addAuditLog(VerificationStep.VERIFICATION_COMPLETED, StepStatus.COMPLETED, LogLevel.INFO,
            "Passport verification completed successfully",
            Map.of("status", verificationResult.getStatus(),
                   "processingDurationMs", processingDurationMs,
                   "totalDataGroups", verificationResult.getTotalDataGroups(),
                   "validDataGroups", verificationResult.getValidDataGroups()));
    }

    // ✅ Audit: Add Log Entry
    public void addAuditLog(
        VerificationStep step,
        StepStatus stepStatus,
        LogLevel level,
        String message,
        Map<String, Object> details
    ) {
        logSequence++;
        VerificationAuditLog log = VerificationAuditLog.create(
            this.id,
            logSequence,
            step,
            stepStatus,
            level,
            message,
            details
        );
        this.auditLogs.add(log);
    }

    // ✅ Audit: Add Error Log
    public void addErrorLog(
        VerificationStep step,
        String errorCode,
        String errorMessage,
        Exception exception
    ) {
        logSequence++;
        VerificationAuditLog log = VerificationAuditLog.createError(
            this.id,
            logSequence,
            step,
            errorCode,
            errorMessage,
            exception
        );
        this.auditLogs.add(log);
    }
}
```

### 2. SecurityObjectDocument (Value Object)

```java
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecurityObjectDocument {

    @Column(name = "sod_encoded", columnDefinition = "BYTEA")
    private byte[] encodedData;  // PKCS#7 SignedData

    @Column(name = "hash_algorithm", length = 20)
    private String hashAlgorithm;  // SHA-256, SHA-384, SHA-512

    @Column(name = "signature_algorithm", length = 50)
    private String signatureAlgorithm;  // SHA256withRSA

    public static SecurityObjectDocument of(byte[] sodBytes) {
        validate(sodBytes);
        return new SecurityObjectDocument(sodBytes);
    }

    private SecurityObjectDocument(byte[] encodedData) {
        this.encodedData = encodedData;
        // Parse to extract algorithms
    }

    private static void validate(byte[] sodBytes) {
        if (sodBytes == null || sodBytes.length == 0) {
            throw new DomainException("INVALID_SOD", "SOD data cannot be empty");
        }
    }
}
```

### 3. DataGroup (Value Object)

```java
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DataGroup {

    @Enumerated(EnumType.STRING)
    @Column(name = "data_group_number", length = 10)
    private DataGroupNumber number;  // DG1~DG16

    @Column(name = "content", columnDefinition = "BYTEA")
    private byte[] content;

    @Embedded
    private DataGroupHash expectedHash;  // From SOD

    @Embedded
    private DataGroupHash actualHash;  // Calculated

    public static DataGroup of(DataGroupNumber number, byte[] content) {
        return new DataGroup(number, content);
    }

    private DataGroup(DataGroupNumber number, byte[] content) {
        validate(number, content);
        this.number = number;
        this.content = content;
    }

    public DataGroupHash calculateHash(String algorithm) {
        // Use DigestCalculator
        return DataGroupHash.calculate(this.content, algorithm);
    }

    public boolean isValid() {
        return expectedHash.equals(actualHash);
    }
}
```

### 4. PassiveAuthenticationResult (Value Object)

```java
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PassiveAuthenticationResult {

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 20)
    private VerificationStatus status;  // VALID, INVALID, ERROR

    @Column(name = "certificate_chain_valid")
    private boolean certificateChainValid;

    @Column(name = "sod_signature_valid")
    private boolean sodSignatureValid;

    @Column(name = "total_data_groups")
    private int totalDataGroups;

    @Column(name = "valid_data_groups")
    private int validDataGroups;

    @Column(name = "invalid_data_groups")
    private int invalidDataGroups;

    @Column(name = "errors", columnDefinition = "JSONB")
    private String errorsJson;  // List<VerificationError> serialized

    public static PassiveAuthenticationResult valid(
        int totalDataGroups,
        int validDataGroups
    ) {
        return new PassiveAuthenticationResult(
            VerificationStatus.VALID,
            true,
            true,
            totalDataGroups,
            validDataGroups,
            0,
            "[]"
        );
    }

    public static PassiveAuthenticationResult invalid(
        List<VerificationError> errors
    ) {
        return new PassiveAuthenticationResult(
            VerificationStatus.INVALID,
            false,
            false,
            0,
            0,
            0,
            serializeErrors(errors)
        );
    }
}
```

---

## 🔌 Infrastructure Layer Design

### BouncyCastleSodParserAdapter

```java
@Component
public class BouncyCastleSodParserAdapter implements SodParserPort {

    @Override
    public Map<DataGroupNumber, DataGroupHash> parseDataGroupHashes(byte[] sodBytes) {
        try {
            CMSSignedData cmsSignedData = new CMSSignedData(sodBytes);

            // Extract LDSSecurityObject
            ContentInfo contentInfo = cmsSignedData.getContentInfo();
            ASN1Encodable content = contentInfo.getContent();

            // Parse hashes
            LDSSecurityObject ldsSecurityObject = LDSSecurityObject.getInstance(content);
            Map<DataGroupNumber, DataGroupHash> hashes = new HashMap<>();

            for (DataGroupHash hash : ldsSecurityObject.getDatagroupHash()) {
                int dgNumber = hash.getDataGroupNumber();
                byte[] hashValue = hash.getDataGroupHashValue().getOctets();

                hashes.put(
                    DataGroupNumber.fromInt(dgNumber),
                    DataGroupHash.of(hashValue)
                );
            }

            return hashes;

        } catch (Exception e) {
            throw new InfrastructureException(
                "SOD_PARSE_ERROR",
                "Failed to parse SOD: " + e.getMessage()
            );
        }
    }

    @Override
    public boolean verifySignature(byte[] sodBytes, PublicKey dscPublicKey) {
        try {
            CMSSignedData cmsSignedData = new CMSSignedData(sodBytes);

            SignerInformationStore signerInfos = cmsSignedData.getSignerInfos();
            SignerInformation signerInfo = signerInfos.getSigners().iterator().next();

            SignerInformationVerifier verifier = new JcaSimpleSignerInfoVerifierBuilder()
                .setProvider("BC")
                .build(dscPublicKey);

            return signerInfo.verify(verifier);

        } catch (Exception e) {
            throw new InfrastructureException(
                "SOD_SIGNATURE_VERIFY_ERROR",
                "Failed to verify SOD signature: " + e.getMessage()
            );
        }
    }

    @Override
    public String extractHashAlgorithm(byte[] sodBytes) {
        try {
            CMSSignedData cmsSignedData = new CMSSignedData(sodBytes);
            LDSSecurityObject ldsSecurityObject = extractLdsSecurityObject(cmsSignedData);

            AlgorithmIdentifier hashAlgorithm = ldsSecurityObject.getDigestAlgorithmIdentifier();
            return hashAlgorithm.getAlgorithm().getId();  // e.g., "2.16.840.1.101.3.4.2.1" (SHA-256)

        } catch (Exception e) {
            throw new InfrastructureException(
                "HASH_ALGORITHM_EXTRACT_ERROR",
                "Failed to extract hash algorithm: " + e.getMessage()
            );
        }
    }
}
```

---

## 🌐 REST API Design

### POST /api/v1/passport/verify

**Request**:
```json
{
  "issuingCountry": "KR",
  "documentNumber": "M12345678",
  "sod": "MIIGBwYJKoZIhvcNAQcCoII...",
  "dataGroups": {
    "DG1": "UEQxMjM0NTY3ODk...",
    "DG2": "iVBORw0KGgoAAAANS...",
    "DG15": "MIIDXzCCAkegAwIB..."
  }
}
```

**Response (Success)**:
```json
{
  "status": "VALID",
  "verificationId": "550e8400-e29b-41d4-a716-446655440000",
  "verificationTimestamp": "2025-12-12T10:30:00Z",
  "issuingCountry": "KR",
  "documentNumber": "M12345678",
  "certificateChainValidation": {
    "valid": true,
    "dscSubject": "CN=DS-KOREA,O=Government,C=KR",
    "dscSerialNumber": "A1B2C3D4",
    "cscaSubject": "CN=CSCA-KOREA,O=Government,C=KR",
    "validityPeriod": {
      "notBefore": "2023-01-01T00:00:00Z",
      "notAfter": "2028-12-31T23:59:59Z"
    },
    "crlChecked": true,
    "revoked": false
  },
  "sodSignatureValidation": {
    "valid": true,
    "signatureAlgorithm": "SHA256withRSA",
    "hashAlgorithm": "SHA-256"
  },
  "dataGroupValidation": {
    "totalGroups": 3,
    "validGroups": 3,
    "invalidGroups": 0,
    "details": {
      "DG1": {
        "valid": true,
        "expectedHash": "a1b2c3d4e5f67890...",
        "actualHash": "a1b2c3d4e5f67890..."
      },
      "DG2": {
        "valid": true,
        "expectedHash": "1234567890abcdef...",
        "actualHash": "1234567890abcdef..."
      },
      "DG15": {
        "valid": true,
        "expectedHash": "fedcba0987654321...",
        "actualHash": "fedcba0987654321..."
      }
    }
  }
}
```

**Response (Failure)**:
```json
{
  "status": "INVALID",
  "verificationId": "550e8400-e29b-41d4-a716-446655440001",
  "verificationTimestamp": "2025-12-12T10:35:00Z",
  "issuingCountry": "KR",
  "documentNumber": "M12345678",
  "errors": [
    {
      "code": "SOD_SIGNATURE_INVALID",
      "message": "SOD signature verification failed with DSC public key",
      "severity": "CRITICAL",
      "timestamp": "2025-12-12T10:35:00.123Z"
    },
    {
      "code": "DG1_HASH_MISMATCH",
      "message": "DG1 hash does not match SOD (expected: a1b2c3d4..., actual: ffffffff...)",
      "severity": "CRITICAL",
      "timestamp": "2025-12-12T10:35:00.456Z"
    }
  ]
}
```

### Additional Endpoints

**GET /api/v1/passport/verify/history**
- 검증 이력 조회 (페이징)
- 필터: issuingCountry, status, dateRange

**GET /api/v1/passport/verify/{verificationId}**
- 특정 검증 결과 상세 조회

---

## 💾 Database Schema (Audit & Log 완전 지원)

### passport_verification 테이블 (메인 검증 결과 + Audit 정보)

```sql
CREATE TABLE passport_verification (
    -- Primary Key
    id UUID PRIMARY KEY,

    -- Passport Information
    issuing_country VARCHAR(3) NOT NULL,
    document_number VARCHAR(20) NOT NULL,

    -- ✅ Audit: Verification Timing (처리 시간 추적)
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    processing_duration_ms BIGINT,  -- 처리 시간 (밀리초)

    -- Verification Result
    verification_status VARCHAR(20) NOT NULL,  -- VALID, INVALID, ERROR

    -- Certificate Chain Validation
    dsc_subject VARCHAR(500),
    dsc_serial_number VARCHAR(100),
    csca_subject VARCHAR(500),
    certificate_chain_valid BOOLEAN,
    crl_checked BOOLEAN,
    revoked BOOLEAN,

    -- SOD Signature Validation
    sod_signature_valid BOOLEAN,
    hash_algorithm VARCHAR(20),  -- SHA-256, SHA-384, SHA-512
    signature_algorithm VARCHAR(50),  -- SHA256withRSA, etc.

    -- Data Group Validation Summary
    total_data_groups INTEGER,
    valid_data_groups INTEGER,
    invalid_data_groups INTEGER,

    -- Errors (JSONB for flexibility)
    errors JSONB,

    -- ✅ Audit: Request Metadata (누가, 어디서, 언제 요청했는지)
    request_ip_address VARCHAR(45),  -- IPv6 지원 (최대 45자)
    request_user_agent TEXT,  -- User Agent 문자열
    requested_by VARCHAR(100),  -- User ID, API Key, or System Name

    -- ✅ Audit: Raw Data Preservation (재검증 가능성)
    raw_request_data JSONB,  -- 원본 요청 JSON 전체 보존
    sod_encoded BYTEA,  -- SOD 바이너리 원본

    -- Constraints
    CONSTRAINT chk_verification_status CHECK (verification_status IN ('VALID', 'INVALID', 'ERROR'))
);

-- Indexes for Performance & Audit Queries
CREATE INDEX idx_pv_country_timestamp ON passport_verification (issuing_country, started_at);
CREATE INDEX idx_pv_document_number ON passport_verification (document_number);
CREATE INDEX idx_pv_status ON passport_verification (verification_status);
CREATE INDEX idx_pv_started_at ON passport_verification (started_at DESC);
CREATE INDEX idx_pv_completed_at ON passport_verification (completed_at DESC);
CREATE INDEX idx_pv_requested_by ON passport_verification (requested_by);
CREATE INDEX idx_pv_request_ip ON passport_verification (request_ip_address);

-- Comments for Audit Trail
COMMENT ON COLUMN passport_verification.started_at IS 'Audit: Verification start timestamp';
COMMENT ON COLUMN passport_verification.completed_at IS 'Audit: Verification completion timestamp';
COMMENT ON COLUMN passport_verification.processing_duration_ms IS 'Audit: Processing time in milliseconds';
COMMENT ON COLUMN passport_verification.request_ip_address IS 'Audit: Client IP address (IPv4/IPv6)';
COMMENT ON COLUMN passport_verification.requested_by IS 'Audit: User ID, API Key, or System Name';
COMMENT ON COLUMN passport_verification.raw_request_data IS 'Audit: Full original request JSON for re-verification';
```

### passport_data_group_validation 테이블 (개별 DG 검증 상세)

```sql
CREATE TABLE passport_data_group_validation (
    id UUID PRIMARY KEY,
    verification_id UUID NOT NULL REFERENCES passport_verification(id) ON DELETE CASCADE,

    -- Data Group Information
    data_group_number VARCHAR(10) NOT NULL,  -- DG1, DG2, ..., DG16
    is_valid BOOLEAN NOT NULL,

    -- Hash Comparison
    expected_hash VARCHAR(128),  -- From SOD
    actual_hash VARCHAR(128),  -- Calculated
    hash_mismatch_detected BOOLEAN DEFAULT FALSE,

    -- ✅ Audit: Validation Timestamp
    validated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT chk_dg_number CHECK (data_group_number ~ '^DG(1[0-6]|[1-9])$')
);

CREATE INDEX idx_pdgv_verification_id ON passport_data_group_validation (verification_id);
CREATE INDEX idx_pdgv_is_valid ON passport_data_group_validation (is_valid);
CREATE INDEX idx_pdgv_validated_at ON passport_data_group_validation (validated_at DESC);

COMMENT ON TABLE passport_data_group_validation IS 'Audit: Individual Data Group hash validation results';
```

### ✅ passport_verification_audit_log 테이블 (NEW: 상세 검증 과정 로그)

```sql
CREATE TABLE passport_verification_audit_log (
    -- Primary Key
    id UUID PRIMARY KEY,
    verification_id UUID NOT NULL REFERENCES passport_verification(id) ON DELETE CASCADE,

    -- ✅ Audit: Log Metadata
    log_timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    log_level VARCHAR(10) NOT NULL,  -- INFO, WARN, ERROR, DEBUG
    log_sequence INTEGER NOT NULL,  -- 로그 순서 (1, 2, 3, ...)

    -- ✅ Verification Step Tracking
    verification_step VARCHAR(50) NOT NULL,  -- CERTIFICATE_CHAIN, SOD_SIGNATURE, DATA_GROUP_HASH, etc.
    step_status VARCHAR(20),  -- STARTED, IN_PROGRESS, COMPLETED, FAILED

    -- ✅ Log Content
    message TEXT NOT NULL,
    details JSONB,  -- 구조화된 추가 정보 (예: {"dgNumber": "DG1", "expectedHash": "...", "actualHash": "..."})

    -- ✅ Error Information (if any)
    error_code VARCHAR(50),
    error_message TEXT,
    stack_trace TEXT,  -- Java Exception Stack Trace (ERROR 레벨만)

    -- Constraints
    CONSTRAINT chk_log_level CHECK (log_level IN ('DEBUG', 'INFO', 'WARN', 'ERROR')),
    CONSTRAINT chk_step_status CHECK (step_status IN ('STARTED', 'IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

-- Indexes for Audit Log Queries
CREATE INDEX idx_pval_verification_id ON passport_verification_audit_log (verification_id);
CREATE INDEX idx_pval_log_timestamp ON passport_verification_audit_log (log_timestamp DESC);
CREATE INDEX idx_pval_log_level ON passport_verification_audit_log (log_level);
CREATE INDEX idx_pval_verification_step ON passport_verification_audit_log (verification_step);
CREATE INDEX idx_pval_step_status ON passport_verification_audit_log (step_status);
CREATE INDEX idx_pval_sequence ON passport_verification_audit_log (verification_id, log_sequence);

COMMENT ON TABLE passport_verification_audit_log IS 'Audit: Detailed step-by-step verification process logs';
COMMENT ON COLUMN passport_verification_audit_log.log_sequence IS 'Sequential order of logs within a verification (1, 2, 3, ...)';
COMMENT ON COLUMN passport_verification_audit_log.verification_step IS 'Which verification step this log belongs to';
COMMENT ON COLUMN passport_verification_audit_log.details IS 'Structured additional data (JSONB)';
```

### Audit Log 사용 예시

**검증 과정의 상세 로그 예시**:

```sql
-- Verification ID: 550e8400-e29b-41d4-a716-446655440000

-- Log 1: 검증 시작
INSERT INTO passport_verification_audit_log VALUES (
    '...', '550e8400-...', NOW(), 'INFO', 1,
    'VERIFICATION_STARTED', 'STARTED',
    'Passport verification started for KR-M12345678',
    '{"issuingCountry": "KR", "documentNumber": "M12345678"}',
    NULL, NULL, NULL
);

-- Log 2: Certificate Chain 검증 시작
INSERT INTO passport_verification_audit_log VALUES (
    '...', '550e8400-...', NOW(), 'INFO', 2,
    'CERTIFICATE_CHAIN', 'STARTED',
    'Starting certificate chain validation',
    '{"dscSubject": "CN=DS-KOREA,O=Government,C=KR"}',
    NULL, NULL, NULL
);

-- Log 3: Certificate Chain 검증 완료
INSERT INTO passport_verification_audit_log VALUES (
    '...', '550e8400-...', NOW(), 'INFO', 3,
    'CERTIFICATE_CHAIN', 'COMPLETED',
    'Certificate chain validation completed successfully',
    '{"chainValid": true, "crlChecked": true, "revoked": false}',
    NULL, NULL, NULL
);

-- Log 4: SOD 서명 검증 시작
INSERT INTO passport_verification_audit_log VALUES (
    '...', '550e8400-...', NOW(), 'INFO', 4,
    'SOD_SIGNATURE', 'STARTED',
    'Starting SOD signature verification',
    '{"hashAlgorithm": "SHA-256", "signatureAlgorithm": "SHA256withRSA"}',
    NULL, NULL, NULL
);

-- Log 5: SOD 서명 검증 완료
INSERT INTO passport_verification_audit_log VALUES (
    '...', '550e8400-...', NOW(), 'INFO', 5,
    'SOD_SIGNATURE', 'COMPLETED',
    'SOD signature verification succeeded',
    '{"signatureValid": true}',
    NULL, NULL, NULL
);

-- Log 6: Data Group 해시 검증 (DG1)
INSERT INTO passport_verification_audit_log VALUES (
    '...', '550e8400-...', NOW(), 'INFO', 6,
    'DATA_GROUP_HASH', 'COMPLETED',
    'DG1 hash validation passed',
    '{"dgNumber": "DG1", "expectedHash": "a1b2c3...", "actualHash": "a1b2c3...", "match": true}',
    NULL, NULL, NULL
);

-- Log 7: 검증 완료
INSERT INTO passport_verification_audit_log VALUES (
    '...', '550e8400-...', NOW(), 'INFO', 7,
    'VERIFICATION_COMPLETED', 'COMPLETED',
    'Passport verification completed successfully',
    '{"status": "VALID", "processingDurationMs": 1234, "totalDataGroups": 3, "validDataGroups": 3}',
    NULL, NULL, NULL
);
```

### Flyway Migrations

**V18__Create_Passport_Verification_Schema.sql**:
- `passport_verification` 테이블 생성 (Audit 필드 포함)
- `passport_data_group_validation` 테이블 생성
- `passport_verification_audit_log` 테이블 생성 (**NEW**)
- 모든 인덱스 및 제약조건 생성
- 테이블 및 컬럼 주석 추가

---

## 🔍 Audit & Log 활용 시나리오

### 1. 검증 실패 원인 추적

```sql
-- 특정 검증 실패 건의 상세 로그 조회
SELECT
    log_timestamp,
    verification_step,
    step_status,
    message,
    details
FROM passport_verification_audit_log
WHERE verification_id = '550e8400-e29b-41d4-a716-446655440000'
ORDER BY log_sequence;
```

### 2. 성능 분석

```sql
-- 평균 처리 시간 및 최대/최소 처리 시간
SELECT
    AVG(processing_duration_ms) AS avg_duration_ms,
    MAX(processing_duration_ms) AS max_duration_ms,
    MIN(processing_duration_ms) AS min_duration_ms,
    COUNT(*) AS total_verifications
FROM passport_verification
WHERE verification_status = 'VALID'
  AND completed_at >= NOW() - INTERVAL '7 days';
```

### 3. 보안 감사 (특정 IP의 요청 추적)

```sql
-- 특정 IP 주소에서의 모든 검증 요청 조회
SELECT
    started_at,
    document_number,
    verification_status,
    requested_by,
    processing_duration_ms
FROM passport_verification
WHERE request_ip_address = '192.168.1.100'
ORDER BY started_at DESC;
```

### 4. 에러 패턴 분석

```sql
-- 가장 많이 발생한 에러 코드 TOP 10
SELECT
    error_code,
    COUNT(*) AS error_count,
    MAX(log_timestamp) AS last_occurrence
FROM passport_verification_audit_log
WHERE log_level = 'ERROR'
  AND log_timestamp >= NOW() - INTERVAL '30 days'
GROUP BY error_code
ORDER BY error_count DESC
LIMIT 10;
```

### 5. 재검증 (Raw Data 활용)

```sql
-- 원본 요청 데이터 조회하여 재검증
SELECT
    id,
    raw_request_data,
    sod_encoded
FROM passport_verification
WHERE document_number = 'M12345678'
  AND started_at >= NOW() - INTERVAL '90 days'
ORDER BY started_at DESC
LIMIT 1;
```

---

## 📋 Implementation Phases

### Phase 1: Domain Layer ✅ (with Audit Support)

**작업 내용**:

1. **Core Value Objects**
   - [DataGroupNumber.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/model/DataGroupNumber.java) (Enum: DG1~DG16)
   - [DataGroupHash.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/model/DataGroupHash.java)
   - [SecurityObjectDocument.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/model/SecurityObjectDocument.java)
   - [DataGroup.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/model/DataGroup.java)
   - [VerificationError.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/model/VerificationError.java)
   - [PassiveAuthenticationResult.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/model/PassiveAuthenticationResult.java)
   - [VerificationStatus.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/model/VerificationStatus.java) (Enum: VALID, INVALID, ERROR)

2. **✅ Audit Value Objects & Enums (NEW)**
   - [RequestMetadata.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/model/RequestMetadata.java) - IP, User Agent, Requested By
   - [VerificationStep.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/model/VerificationStep.java) (Enum: VERIFICATION_STARTED, CERTIFICATE_CHAIN, SOD_SIGNATURE, DATA_GROUP_HASH, VERIFICATION_COMPLETED)
   - [StepStatus.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/model/StepStatus.java) (Enum: STARTED, IN_PROGRESS, COMPLETED, FAILED)
   - [LogLevel.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/model/LogLevel.java) (Enum: DEBUG, INFO, WARN, ERROR)

3. **Aggregate Root**
   - [PassportData.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/model/PassportData.java) - **Audit 기능 포함**
   - [PassportDataId.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/model/PassportDataId.java) (JPearl ID)

4. **✅ Audit Entity (NEW)**
   - [VerificationAuditLog.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/model/VerificationAuditLog.java) - 상세 검증 과정 로그

5. **Domain Service**
   - [PassiveAuthenticationService.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/service/PassiveAuthenticationService.java)

6. **Repository Interfaces**
   - [PassportDataRepository.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/repository/PassportDataRepository.java)
   - [VerificationAuditLogRepository.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/repository/VerificationAuditLogRepository.java) (**NEW**)

**예상 소요 시간**: 6-8 hours (Audit 기능 추가로 +2h)

---

### Phase 2: Infrastructure Layer ✅

**작업 내용**:
1. Port 인터페이스 정의
   - [SodParserPort.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/port/SodParserPort.java)

2. Bouncy Castle Adapter 구현
   - [BouncyCastleSodParserAdapter.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/infrastructure/adapter/BouncyCastleSodParserAdapter.java)
   - SOD(CMSSignedData) 파싱
   - Data Group 해시 추출
   - 서명 검증 로직
   - 해시 알고리즘 추출

3. JPA Repository 구현
   - [JpaPassportDataRepository.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/infrastructure/repository/JpaPassportDataRepository.java)

4. Database Migration
   - [V18__Create_Passport_Verification_Schema.sql](src/main/resources/db/migration/V18__Create_Passport_Verification_Schema.sql)

**예상 소요 시간**: 6-8 hours

**기술 참고**:
- Bouncy Castle API: `org.bouncycastle.cms.*`
- `CMSSignedData`, `SignerInformation`, `LDSSecurityObject`

---

### Phase 3: Application Layer ✅

**작업 내용**:
1. Command 생성
   - [PerformPassiveAuthenticationCommand.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/application/command/PerformPassiveAuthenticationCommand.java)

2. Response DTOs 생성
   - [PassiveAuthenticationResponse.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/application/response/PassiveAuthenticationResponse.java)
   - [CertificateChainValidationDto.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/application/response/CertificateChainValidationDto.java)
   - [SodSignatureValidationDto.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/application/response/SodSignatureValidationDto.java)
   - [DataGroupValidationDto.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/application/response/DataGroupValidationDto.java)

3. Use Case 구현
   - [PerformPassiveAuthenticationUseCase.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/application/usecase/PerformPassiveAuthenticationUseCase.java)
     - Certificate Chain Validation (기존 BouncyCastleValidationAdapter 재사용)
     - CRL 체크 (기존 CertificateRevocationListRepository 재사용)
     - SOD Signature Validation
     - Data Group Hash Validation
   - [GetPassiveAuthenticationHistoryUseCase.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/application/usecase/GetPassiveAuthenticationHistoryUseCase.java)

**예상 소요 시간**: 5-7 hours

**재사용 컴포넌트**:
- `CertificateRepository` (DSC/CSCA 조회)
- `BouncyCastleValidationAdapter` (Trust Chain 검증)
- `CertificateRevocationListRepository` (CRL 체크)

---

### Phase 4: Web Layer ✅

**작업 내용**:
1. Controller 구현
   - [PassiveAuthenticationController.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/infrastructure/web/PassiveAuthenticationController.java)
   - `POST /api/v1/passport/verify`
   - `GET /api/v1/passport/verify/history`
   - `GET /api/v1/passport/verify/{verificationId}`

2. Request DTOs
   - [PassiveAuthenticationRequest.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/infrastructure/web/request/PassiveAuthenticationRequest.java)
   - Validation 어노테이션 추가 (@NotNull, @Size, @Pattern 등)

3. Exception Handling
   - [PassiveAuthenticationException.java](src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/exception/PassiveAuthenticationException.java)
   - GlobalExceptionHandler 확장 (기존 파일 수정)

**예상 소요 시간**: 3-4 hours

---

### Phase 5: Testing ✅

**작업 내용**:

1. **Unit Tests** (JUnit 5 + Mockito)
   - `DataGroupTest.java` - DataGroup Value Object 테스트
   - `SecurityObjectDocumentTest.java` - SOD 파싱 테스트
   - `PassportDataTest.java` - Aggregate Root 비즈니스 로직 테스트
   - `PassiveAuthenticationServiceTest.java` - Domain Service 테스트
   - `PerformPassiveAuthenticationUseCaseTest.java` - Use Case 테스트 (Mock 사용)

2. **Integration Tests** (Spring Boot Test)
   - `BouncyCastleSodParserAdapterTest.java` - 실제 SOD 파일로 파싱 테스트
   - `PassiveAuthenticationControllerTest.java` - REST API 통합 테스트
   - `PassiveAuthenticationE2ETest.java` - End-to-End PA 검증 테스트

3. **Test Data 준비**
   - 샘플 SOD 파일 (ICAO 테스트 데이터)
   - 샘플 Data Groups (DG1, DG2, DG15)
   - 테스트용 DSC/CSCA 인증서

**예상 소요 시간**: 6-8 hours

**테스트 커버리지 목표**: 80% 이상

---

## 🔗 Integration with Existing Code

### ✅ LDAP Certificate Usage (핵심!)

**PA 검증에 필요한 모든 인증서는 OpenLDAP에서 조회합니다!**

```
전자여권 SOD
    ↓
SOD 분석 (DSC 정보 추출: Subject DN, Serial Number)
    ↓
OpenLDAP에서 DSC 조회 ⬅️ CertificateRepository.findBySubjectDnAndSerial()
    ↓
DSC의 Issuer DN으로 CSCA 조회 ⬅️ CertificateRepository.findBySubjectDn()
    ↓
CRL 조회 ⬅️ CertificateRevocationListRepository.findByIssuerName()
    ↓
PA 검증 수행
```

**LDAP DN 예시**:
- **DSC**: `cn=CN\=DS-KOREA\,O\=Government\,C\=KR+sn=A1B2C3D4,o=dsc,c=KR,dc=data,dc=download,dc=pkd,...`
- **CSCA**: `cn=CN\=CSCA-KOREA\,O\=Government\,C\=KR+sn=1234ABCD,o=csca,c=KR,dc=data,dc=download,dc=pkd,...`
- **CRL**: `cn=CN\=CSCA-KOREA\,O\=Government\,C\=KR,o=crl,c=KR,dc=data,dc=download,dc=pkd,...`

**중요**:
- ✅ SOD에는 DSC 인증서가 **포함되지 않음** (Subject DN과 Serial Number만 포함)
- ✅ 따라서 **반드시 LDAP에서 실제 DSC 인증서를 조회**해야 함
- ✅ CSCA도 LDAP에서 조회하여 Trust Chain 검증
- ✅ CRL도 LDAP에서 조회하여 폐기 여부 확인

---

### 재사용 가능한 컴포넌트

1. **Certificate Validation** (`certificatevalidation` context)
   - ✅ `CertificateRepository` - **OpenLDAP에서 CSCA, DSC 조회**
   - ✅ `BouncyCastleValidationAdapter` - Trust Chain 검증
   - ✅ `Certificate` 엔티티 - 인증서 도메인 모델
   - ✅ `CertificateRevocationListRepository` - **OpenLDAP에서 CRL 조회**

2. **Shared Kernel**
   - ✅ `CountryCode` (Value Object)
   - ✅ `DomainException`, `BusinessException`, `InfrastructureException`
   - ✅ `AbstractAggregateRoot` (JPearl)

3. **Infrastructure**
   - ✅ Bouncy Castle 1.70
   - ✅ UnboundID LDAP SDK - **OpenLDAP 연결용**

### 통합 포인트

```java
@Service
@Transactional
public class PerformPassiveAuthenticationUseCase {

    private final PassportDataRepository passportDataRepository;
    private final CertificateRepository certificateRepository;  // ← 기존 재사용
    private final CertificateRevocationListRepository crlRepository;  // ← 기존 재사용
    private final BouncyCastleValidationAdapter validationAdapter;  // ← 기존 재사용
    private final SodParserPort sodParser;
    private final PassiveAuthenticationService paService;

    public PassiveAuthenticationResponse execute(PerformPassiveAuthenticationCommand command) {
        // 1. DSC 조회 (OpenLDAP에서)
        Certificate dsc = certificateRepository.findBySubjectDnAndCountryCode(
            command.dscSubject(),
            command.issuingCountry()
        );

        // 2. CSCA 조회
        Certificate csca = certificateRepository.findBySubjectDn(dsc.getIssuerDn());

        // 3. Certificate Chain 검증 (기존 로직 재사용)
        validationAdapter.validateTrustChain(dsc, csca);

        // 4. CRL 체크 (기존 로직 재사용)
        crlRepository.findByIssuerNameAndCountryCode(csca.getSubjectDn(), command.issuingCountry())
            .ifPresent(crl -> validationAdapter.checkRevocation(dsc, crl));

        // 5. PA 검증 (새로운 로직)
        PassportData passportData = PassportData.create(command, dsc, csca);
        PassiveAuthenticationResult result = paService.performPassiveAuthentication(
            passportData,
            sodParser
        );

        // 6. 결과 저장
        passportDataRepository.save(passportData);

        return PassiveAuthenticationResponse.from(result);
    }
}
```

---

## 📊 Estimated Effort

| Phase | 작업 내용 | 예상 시간 | 우선순위 |
|-------|-----------|-----------|---------|
| **Phase 1** | Domain Layer (Value Objects, Aggregate) | 4-6h | HIGH |
| **Phase 2** | Infrastructure (SOD Parser, JPA) | 6-8h | HIGH |
| **Phase 3** | Application Layer (Use Case, DTOs) | 5-7h | HIGH |
| **Phase 4** | Web Layer (REST API, Exception) | 3-4h | MEDIUM |
| **Phase 5** | Testing (Unit, Integration, E2E) | 6-8h | MEDIUM |
| **문서화** | API 문서, 사용자 가이드 | 2-3h | LOW |

**총 예상 시간**: 26-36 hours (3-5 days)

---

## 🎯 Success Criteria

1. ✅ **Functional Requirements**
   - PA 검증 3단계 모두 정상 작동
   - REST API로 여권 검증 가능
   - 검증 결과 데이터베이스 저장
   - 검증 이력 조회 가능

2. ✅ **Non-Functional Requirements**
   - 테스트 커버리지 80% 이상
   - API 응답 시간 < 2초
   - DDD 아키텍처 준수
   - Bouncy Castle 기반 구현

3. ✅ **Documentation**
   - REST API 문서 (Swagger/OpenAPI)
   - 구현 가이드 작성
   - 테스트 데이터 준비 방법

---

## 🔍 Risk Analysis

### 잠재적 위험 요소

1. **SOD 파싱 복잡도**
   - **위험**: ICAO LDS 1.7/1.8 스펙 차이
   - **대응**: Bouncy Castle API 정확히 이해, 샘플 데이터 확보

2. **해시 알고리즘 다양성**
   - **위험**: SHA-256, SHA-384, SHA-512, SHA-1(구버전) 지원 필요
   - **대응**: 알고리즘 동적 선택 로직 구현

3. **인증서 조회 실패**
   - **위험**: OpenLDAP에 DSC/CSCA 없을 수 있음
   - **대응**: 명확한 에러 메시지, 404 처리

4. **대용량 Data Group 처리**
   - **위험**: DG2(얼굴 이미지) 크기 큰 경우
   - **대응**: Request size limit 설정, 스트리밍 검토

---

## 📖 References

### ICAO 9303 Documents
- [ICAO Doc 9303 Part 11 - Security Mechanisms for MRTDs](https://www.icao.int/sites/default/files/publications/DocSeries/9303_p11_cons_en.pdf)
- [ICAO Doc 9303 Part 12 - PKI for eMRTDs](https://www.icao.int/sites/default/files/publications/DocSeries/9303_p2_cons_en.pdf)
- [ICAO PKD ePassport Validation Roadmap](https://www.icao.int/icao-pkd/epassport-validation-roadmap-tool-system-requirements)

### Technical Resources
- [Regula Forensics - RFID Chips Security Mechanisms](https://docs.regulaforensics.com/develop/doc-reader-sdk/overview/security-mechanisms-for-electronic-documents/)
- [Keesing Platform - Understanding PKI Part 2](https://platform.keesingtechnologies.com/understanding-public-key-infrastructure-part-2/)
- [Entrust ePassport PKI Solutions](https://www.entrust.com/digital-security/certificate-solutions/products/pki/epassport)
- [JMRTD - Java Machine Readable Travel Documents](https://jmrtd.org/certificates.shtml)
- [ZeroPass PyMRTD - Python ICAO 9303 Implementation](https://github.com/ZeroPass/pymrtd)

### Implementation Examples
- [Kinegram DocVal Server - eMRTD Security](https://kinegram.digital/knowledge-base/docval-server-emrtd-security-mechanisms/)
- [Innovatrics NFC Passport Authentication](https://developers.innovatrics.com/digital-onboarding/docs/functionalities/document/nfc-authentication/)

---

## 🚀 Next Steps

1. **Phase 1 시작**: Domain Layer 구현
   - DataGroupNumber enum 생성
   - Value Objects 생성 (DDD 규칙 준수)
   - PassportData Aggregate Root 생성

2. **테스트 데이터 확보**
   - ICAO 샘플 SOD 파일 다운로드
   - 테스트용 Data Groups 준비

3. **Bouncy Castle API 연구**
   - CMSSignedData 사용법 숙지
   - LDSSecurityObject 파싱 예제 확인

---

**Document Version**: 1.0
**Status**: Planning Complete ✅
**Next Action**: Phase 1 Implementation

---

## Sources

- [Post-quantum solution for passive authentication - ICAO](https://www.icao.int/sites/default/files/2025-06/Thesis-Post-quantum-solution-for-passive-authentication-Siebren-Lepstra-October-2024_0.pdf)
- [Understanding Public Key Infrastructure (Part 2) - Keesing Platform](https://platform.keesingtechnologies.com/understanding-public-key-infrastructure-part-2/)
- [Operational and Technical Security of Electronic Passports - Frontex](https://www.frontex.europa.eu/assets/Publications/Research/Operational_and_Technical_Security_of_Electronic_Pasports.pdf)
- [Authenticity of ePassports - Inverid](https://www.inverid.com/blog/authenticity-electronic-passports)
- [ICAO Doc 9303 Part 2](https://www.icao.int/sites/default/files/publications/DocSeries/9303_p2_cons_en.pdf)
- [ICAO PKD - ePassport Validation Roadmap](https://www.icao.int/icao-pkd/epassport-validation-roadmap-tool-system-requirements)
- [RFID Chips - Developer Documentation - Regula Forensics](https://docs.regulaforensics.com/develop/doc-reader-sdk/overview/security-mechanisms-for-electronic-documents/)
- [Entrust ePassport Solutions](https://www.entrust.com/digital-security/certificate-solutions/products/pki/epassport)
- [JMRTD Certificates](https://jmrtd.org/certificates.shtml)
- [ICAO 9303 Part 11 - Security Mechanisms for MRTDs](https://www.icao.int/sites/default/files/publications/DocSeries/9303_p11_cons_en.pdf)
- [GitHub - ZeroPass/pymrtd](https://github.com/ZeroPass/pymrtd)
- [DocVal Server - eMRTD Security Mechanisms](https://kinegram.digital/knowledge-base/docval-server-emrtd-security-mechanisms/)
- [Innovatrics NFC Passport Authentication](https://developers.innovatrics.com/digital-onboarding/docs/functionalities/document/nfc-authentication/)
