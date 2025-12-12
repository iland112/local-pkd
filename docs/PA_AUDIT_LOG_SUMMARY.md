# PA(Passive Authentication) Audit & Log 기능 요약

**작성일**: 2025-12-12
**상태**: Planning Complete ✅

---

## 📊 Audit & Log 요구사항

### 목적
1. **감사 추적 (Audit Trail)**: 모든 검증 요청 및 결과를 완전히 기록
2. **보안 감사**: IP, User Agent, 요청자 정보 추적
3. **성능 분석**: 처리 시간 측정 및 모니터링
4. **오류 분석**: 검증 실패 원인 상세 추적
5. **재검증 지원**: 원본 요청 데이터 보존

---

## 🗄️ Database Schema (3개 테이블)

### 1. passport_verification (메인 검증 결과 + Audit)

**추가된 Audit 필드**:
```sql
-- ✅ Timing (처리 시간 추적)
started_at TIMESTAMP NOT NULL DEFAULT NOW()
completed_at TIMESTAMP
processing_duration_ms BIGINT

-- ✅ Request Metadata (누가, 어디서, 언제)
request_ip_address VARCHAR(45)  -- IPv6 지원
request_user_agent TEXT
requested_by VARCHAR(100)  -- User ID, API Key, System Name

-- ✅ Raw Data (재검증 가능)
raw_request_data JSONB  -- 원본 요청 JSON
sod_encoded BYTEA  -- SOD 바이너리
```

**새로운 인덱스**:
- `idx_pv_started_at` - 시작 시각 (DESC)
- `idx_pv_completed_at` - 완료 시각 (DESC)
- `idx_pv_requested_by` - 요청자
- `idx_pv_request_ip` - IP 주소

### 2. passport_data_group_validation (DG 검증 상세)

**추가된 필드**:
```sql
validated_at TIMESTAMP NOT NULL DEFAULT NOW()  -- 검증 시각
```

### 3. ✅ passport_verification_audit_log (NEW: 상세 로그)

**완전히 새로운 테이블**:
```sql
CREATE TABLE passport_verification_audit_log (
    id UUID PRIMARY KEY,
    verification_id UUID NOT NULL,

    -- Log Metadata
    log_timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    log_level VARCHAR(10) NOT NULL,  -- DEBUG, INFO, WARN, ERROR
    log_sequence INTEGER NOT NULL,  -- 로그 순서 (1, 2, 3, ...)

    -- Verification Step
    verification_step VARCHAR(50) NOT NULL,
    step_status VARCHAR(20),  -- STARTED, IN_PROGRESS, COMPLETED, FAILED

    -- Log Content
    message TEXT NOT NULL,
    details JSONB,  -- 구조화된 추가 정보

    -- Error Info
    error_code VARCHAR(50),
    error_message TEXT,
    stack_trace TEXT
);
```

**인덱스 (6개)**:
- `verification_id`, `log_timestamp`, `log_level`, `verification_step`, `step_status`, `(verification_id, log_sequence)`

---

## 🏗️ Domain Model 변경

### PassportData Aggregate (확장)

**추가된 필드**:
```java
// Audit: Timing
private LocalDateTime startedAt;
private LocalDateTime completedAt;
private Long processingDurationMs;

// Audit: Request Metadata
@Embedded
private RequestMetadata requestMetadata;

// Audit: Raw Data
private String rawRequestDataJson;

// Audit: Logs (One-to-Many)
@OneToMany(mappedBy = "verification", cascade = CascadeType.ALL)
private List<VerificationAuditLog> auditLogs = new ArrayList<>();

@Transient
private int logSequence = 0;
```

**추가된 메서드**:
```java
// 검증 시작
public void startVerification(RequestMetadata metadata, String rawRequestData);

// 검증 완료
public void completeVerification();

// Audit 로그 추가
public void addAuditLog(VerificationStep, StepStatus, LogLevel, message, details);

// 에러 로그 추가
public void addErrorLog(VerificationStep, errorCode, errorMessage, exception);
```

**비즈니스 로직에 Audit 통합**:
```java
public PassiveAuthenticationResult performPassiveAuthentication(...) {
    // 1. Certificate Chain
    addAuditLog(CERTIFICATE_CHAIN, STARTED, INFO, "Starting...", details);
    validateCertificateChain(...);
    addAuditLog(CERTIFICATE_CHAIN, COMPLETED, INFO, "Completed", details);

    // 2. SOD Signature
    addAuditLog(SOD_SIGNATURE, STARTED, INFO, "Starting...", details);
    validateSodSignature(...);
    addAuditLog(SOD_SIGNATURE, COMPLETED, INFO, "Completed", details);

    // 3. Data Group Hash
    addAuditLog(DATA_GROUP_HASH, STARTED, INFO, "Starting...", details);
    validateDataGroupHashes(...);
    addAuditLog(DATA_GROUP_HASH, COMPLETED, INFO, "Completed", details);
}
```

---

## 🆕 새로운 Domain Objects

### Value Objects (4개)

1. **RequestMetadata** (Embeddable)
   ```java
   @Embeddable
   public class RequestMetadata {
       private String ipAddress;
       private String userAgent;
       private String requestedBy;
   }
   ```

2. **VerificationStep** (Enum)
   ```java
   public enum VerificationStep {
       VERIFICATION_STARTED,
       CERTIFICATE_CHAIN,
       SOD_SIGNATURE,
       DATA_GROUP_HASH,
       VERIFICATION_COMPLETED
   }
   ```

3. **StepStatus** (Enum)
   ```java
   public enum StepStatus {
       STARTED,
       IN_PROGRESS,
       COMPLETED,
       FAILED
   }
   ```

4. **LogLevel** (Enum)
   ```java
   public enum LogLevel {
       DEBUG, INFO, WARN, ERROR
   }
   ```

### Entity (1개)

**VerificationAuditLog** (새로운 Entity)
```java
@Entity
@Table(name = "passport_verification_audit_log")
public class VerificationAuditLog {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_id")
    private PassportData verification;

    private LocalDateTime logTimestamp;
    private LogLevel logLevel;
    private Integer logSequence;
    private VerificationStep verificationStep;
    private StepStatus stepStatus;
    private String message;
    private String detailsJson;
    private String errorCode;
    private String errorMessage;
    private String stackTrace;

    public static VerificationAuditLog create(...);
    public static VerificationAuditLog createError(...);
}
```

---

## 📈 Audit & Log 활용 시나리오

### 1. 검증 실패 원인 추적
```sql
SELECT log_timestamp, verification_step, step_status, message, details
FROM passport_verification_audit_log
WHERE verification_id = '...'
ORDER BY log_sequence;
```

### 2. 성능 분석
```sql
SELECT
    AVG(processing_duration_ms) AS avg_duration_ms,
    MAX(processing_duration_ms) AS max_duration_ms,
    MIN(processing_duration_ms) AS min_duration_ms
FROM passport_verification
WHERE verification_status = 'VALID'
  AND completed_at >= NOW() - INTERVAL '7 days';
```

### 3. 보안 감사 (특정 IP 추적)
```sql
SELECT started_at, document_number, verification_status, requested_by
FROM passport_verification
WHERE request_ip_address = '192.168.1.100'
ORDER BY started_at DESC;
```

### 4. 에러 패턴 분석
```sql
SELECT error_code, COUNT(*) AS error_count
FROM passport_verification_audit_log
WHERE log_level = 'ERROR'
  AND log_timestamp >= NOW() - INTERVAL '30 days'
GROUP BY error_code
ORDER BY error_count DESC
LIMIT 10;
```

### 5. 재검증 (Raw Data 활용)
```sql
SELECT id, raw_request_data, sod_encoded
FROM passport_verification
WHERE document_number = 'M12345678'
  AND started_at >= NOW() - INTERVAL '90 days'
ORDER BY started_at DESC
LIMIT 1;
```

---

## 🔄 검증 프로세스 흐름 (Audit 포함)

```
1. 검증 요청 도착
   ↓
2. PassportData.startVerification(metadata, rawRequest)
   - started_at 기록
   - requestMetadata 저장
   - rawRequestDataJson 저장
   - Log: VERIFICATION_STARTED
   ↓
3. performPassiveAuthentication()
   - Log: CERTIFICATE_CHAIN STARTED
   - Certificate Chain 검증
   - Log: CERTIFICATE_CHAIN COMPLETED
   - Log: SOD_SIGNATURE STARTED
   - SOD Signature 검증
   - Log: SOD_SIGNATURE COMPLETED
   - Log: DATA_GROUP_HASH STARTED
   - Data Group Hash 검증 (각 DG별 로그)
   - Log: DATA_GROUP_HASH COMPLETED
   ↓
4. PassportData.completeVerification()
   - completed_at 기록
   - processingDurationMs 계산
   - Log: VERIFICATION_COMPLETED
   ↓
5. Repository.save(passportData)
   - passport_verification 저장 (Audit 필드 포함)
   - passport_data_group_validation 저장 (각 DG)
   - passport_verification_audit_log 저장 (모든 로그, CASCADE)
```

---

## 📋 구현 Phase 업데이트

### Phase 1: Domain Layer (6-8 hours)
**추가 작업**:
- ✅ RequestMetadata Value Object
- ✅ VerificationStep, StepStatus, LogLevel Enum
- ✅ VerificationAuditLog Entity
- ✅ VerificationAuditLogRepository Interface
- ✅ PassportData Aggregate에 Audit 기능 통합

### Phase 2: Infrastructure Layer (6-8 hours)
**추가 작업**:
- ✅ V18 Migration에 `passport_verification_audit_log` 테이블 추가
- ✅ Audit 필드 인덱스 생성
- ✅ JSONB 타입 지원 확인

### Phase 3: Application Layer (5-7 hours)
**추가 작업**:
- ✅ PerformPassiveAuthenticationCommand에 RequestMetadata 추가
- ✅ PassiveAuthenticationResponse에 Audit 정보 포함
- ✅ UseCase에서 startVerification(), completeVerification() 호출

### Phase 4: Web Layer (3-4 hours)
**추가 작업**:
- ✅ Controller에서 HttpServletRequest로 IP/User Agent 추출
- ✅ RequestMetadata 생성 및 UseCase 전달

### Phase 5: Testing (6-8 hours)
**추가 작업**:
- ✅ Audit Log 생성 테스트
- ✅ 처리 시간 측정 테스트
- ✅ Raw Data 재검증 테스트
- ✅ 에러 로그 생성 테스트

---

## ✅ 핵심 이점

1. **완전한 감사 추적**: 모든 검증 요청과 처리 과정이 시계열로 기록됨
2. **보안 강화**: IP, User Agent, 요청자 정보로 의심 활동 추적 가능
3. **성능 모니터링**: 처리 시간 측정으로 병목 지점 파악
4. **오류 분석**: 검증 실패 원인을 단계별로 추적하여 디버깅 용이
5. **재검증 지원**: 원본 요청 데이터 보존으로 언제든 재검증 가능
6. **규정 준수**: 감사 로그 보존으로 컴플라이언스 요구사항 충족

---

## 📊 예상 데이터 볼륨

**하루 1,000건 검증 가정**:

| 테이블 | 레코드/일 | 레코드/년 | 예상 크기 (1년) |
|--------|----------|----------|----------------|
| passport_verification | 1,000 | 365,000 | ~150 MB |
| passport_data_group_validation | 3,000 | 1,095,000 | ~100 MB |
| passport_verification_audit_log | 7,000 | 2,555,000 | ~500 MB |

**총 예상 크기 (1년)**: ~750 MB

**권장 보존 정책**:
- passport_verification: 3년
- passport_data_group_validation: 3년
- passport_verification_audit_log: 1년 (압축 후 3년)

---

**Document Version**: 1.0
**Status**: Audit & Log Design Complete ✅
