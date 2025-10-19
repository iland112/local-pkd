# Phase 7: Event Listeners Implementation

**Date**: 2025-10-19
**Status**: ✅ **COMPLETED**

---

## Overview

Phase 7에서는 Domain Events를 활용한 Event Listeners를 구현했습니다.
실제 비즈니스 로직을 가진 3개의 전문 Event Listener를 추가하여, 파일 업로드 프로세스의 각 단계에서 발생하는 이벤트에 대응합니다.

---

## Implementation Summary

### Implemented Event Listeners (3)

| Listener | Event | Purpose |
|----------|-------|---------|
| **ChecksumValidationEventListener** | ChecksumValidationFailedEvent | 체크섬 검증 실패 처리, 보안 경고, 알림 |
| **FileUploadFailedEventListener** | FileUploadFailedEvent | 업로드 실패 처리, 재시도 큐, 통계 |
| **FileUploadCompletedEventListener** | FileUploadCompletedEvent | 업로드 완료 처리, 통계 수집, 리포트 |

### Existing Handler (From Phase 3)

| Handler | Events | Purpose |
|---------|--------|---------|
| **FileUploadEventHandler** | FileUploadedEvent, DuplicateFileDetectedEvent | 기본 이벤트 로깅 및 향후 파싱 트리거 |

---

## 1. ChecksumValidationEventListener

**파일**: `ChecksumValidationEventListener.java` (NEW)
**이벤트**: `ChecksumValidationFailedEvent`

### 기능

#### 동기 처리 (@EventListener)
- **에러 로깅**: ERROR 레벨로 즉시 기록
- **보안 경고**: 파일 변조 가능성 감지
- **메트릭 업데이트**: Prometheus counter 증가 (TODO)

#### 비동기 처리 (@TransactionalEventListener + @Async)
- **관리자 알림**: 이메일/Slack 알림 발송 (TODO)
- **에러 리포트**: 상세 리포트 생성 및 저장 (TODO)
- **체크섬 분석**: 불일치 패턴 분석 및 로깅

### 주요 메서드

```java
@EventListener
public void handleChecksumValidationFailed(ChecksumValidationFailedEvent event)
```
- 즉시 에러 로깅
- 보안 위협 감지 (`isPotentialTampering()`)

```java
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleChecksumValidationFailedAsync(ChecksumValidationFailedEvent event)
```
- 비동기 알림 발송
- 상세 에러 분석 (`analyzeChecksumDifference()`)

### 보안 기능

**파일 변조 감지**:
```java
private boolean isPotentialTampering(ChecksumValidationFailedEvent event) {
    // 앞 8자리가 완전히 다르면 변조 가능성
    if (expected.length() >= 8 && calculated.length() >= 8) {
        String expectedPrefix = expected.substring(0, 8);
        String calculatedPrefix = calculated.substring(0, 8);
        return !expectedPrefix.equals(calculatedPrefix);
    }
    return false;
}
```

**체크섬 차이 분석**:
```java
private void analyzeChecksumDifference(String expected, String calculated) {
    // 다른 문자 수 계산
    // 첫 번째 차이 위치 찾기
    // 차이 비율 계산
    log.info("Different characters: {} out of {}", differenceCount, minLength);
    log.info("Difference percentage: {:.2f}%", (differenceCount * 100.0) / minLength);
}
```

---

## 2. FileUploadFailedEventListener

**파일**: `FileUploadFailedEventListener.java` (NEW)
**이벤트**: `FileUploadFailedEvent`

### 기능

#### 동기 처리 (@EventListener)
- **에러 로깅**: ERROR 레벨로 즉시 기록
- **실패 통계 업데이트**: 타입별/날짜별 통계
- **재시도 가능 여부 판단**: 오류 타입 분류

#### 비동기 처리 (@TransactionalEventListener + @Async)
- **재시도 큐 등록**: 재시도 가능한 오류만 (TODO)
- **관리자 알림**: 재시도 불가능한 오류 알림 (TODO)
- **실패 패턴 분석**: 반복 실패 감지 및 권장 조치

### 오류 타입 분류

```java
private String classifyErrorType(String errorMessage) {
    // IO_ERROR: I/O 오류, 파일 시스템 오류
    // NETWORK_ERROR: 네트워크 연결 오류
    // VALIDATION_ERROR: 파일 검증 실패
    // DUPLICATE_FILE: 중복 파일
    // SIZE_ERROR: 파일 크기 초과
    // PERMISSION_ERROR: 권한 부족
    // OTHER: 기타
}
```

### 재시도 정책

**재시도 가능한 오류**:
- IO_ERROR
- NETWORK_ERROR
- PERMISSION_ERROR

**재시도 불가능한 오류**:
- VALIDATION_ERROR (파일 내용 오류)
- DUPLICATE_FILE (중복 파일)
- SIZE_ERROR (크기 초과)

### 실패 통계 (In-Memory)

```java
private final AtomicInteger totalFailureCount = new AtomicInteger(0);
private final ConcurrentHashMap<String, Integer> failureByErrorType;
private final ConcurrentHashMap<String, LocalDateTime> lastFailureByFileName;
```

**통계 메서드**:
- `getTotalFailureCount()`: 총 실패 횟수
- `getFailureCountByType(String)`: 타입별 실패 횟수

### 권장 조치 제공

```java
private String getRecommendation(String errorType) {
    return switch (errorType) {
        case "IO_ERROR" -> "Check disk space and file permissions";
        case "NETWORK_ERROR" -> "Check network connectivity and retry";
        case "VALIDATION_ERROR" -> "Fix file content or format";
        case "DUPLICATE_FILE" -> "Remove duplicate or use force upload";
        case "SIZE_ERROR" -> "Compress file or increase size limit";
        case "PERMISSION_ERROR" -> "Check file system permissions";
        default -> null;
    };
}
```

---

## 3. FileUploadCompletedEventListener

**파일**: `FileUploadCompletedEventListener.java` (NEW)
**이벤트**: `FileUploadCompletedEvent`

### 기능

#### 동기 처리 (@EventListener)
- **성공 로깅**: INFO 레벨로 완료 기록
- **완료 통계 업데이트**: 일자별/전체 통계
- **마일스톤 축하**: 10, 50, 100, 500, 1000, ... 달성 시

#### 비동기 처리 (@TransactionalEventListener + @Async)
- **완료 알림**: 선택적 알림 발송 (TODO)
- **일일 리포트 생성**: 자동화된 리포트 (TODO)
- **통계 요약**: 최근 7일간 통계 로깅

### 완료 통계 (In-Memory)

```java
private final AtomicInteger totalCompletedCount = new AtomicInteger(0);
private final AtomicLong totalUploadedBytes = new AtomicLong(0);
private final ConcurrentHashMap<String, Integer> completedByDate;
private final ConcurrentHashMap<String, LocalDateTime> lastCompletedByFileName;
```

**통계 메서드**:
- `getTotalCompletedCount()`: 총 완료 횟수
- `getCompletedCountByDate(String)`: 특정 날짜 완료 횟수
- `getTodayCompletedCount()`: 오늘 완료 횟수
- `getLastCompletedTime(String)`: 파일별 마지막 완료 시간
- `getStatisticsSnapshot()`: 통계 스냅샷

### 마일스톤 기능

```java
private boolean isMilestone(int count) {
    // 10, 50, 100, 500, 1000
    if (count == 10 || count == 50 || count == 100 || count == 500 || count == 1000) {
        return true;
    }
    // 10000 단위
    return count > 0 && count % 10000 == 0;
}
```

마일스톤 달성 시:
```
🎉 Milestone reached: 100 files uploaded successfully!
```

### 통계 요약 로깅

```java
private void logStatisticsSummary() {
    log.info("=== Upload Statistics Summary ===");
    log.info("  Total completed uploads: {}", totalCompletedCount.get());
    log.info("  Recent daily uploads:");
    // 최근 7일간 통계
    log.info("  Today's uploads: {}", todayCount);
}
```

---

## Architecture & Design Patterns

### 1. Event-Driven Architecture

**이벤트 발행**:
```
UploadedFile (Aggregate)
  → addDomainEvent()
  → repository.save()
  → ApplicationEventPublisher.publishEvent()
  → Event Listeners
```

**동기 vs 비동기**:
- **@EventListener**: 동기 처리 (즉시 실행, 트랜잭션 내)
- **@TransactionalEventListener + @Async**: 비동기 처리 (트랜잭션 커밋 후)

### 2. Separation of Concerns

각 Listener는 단일 책임:
- **ChecksumValidationEventListener**: 체크섬 검증 실패 처리
- **FileUploadFailedEventListener**: 업로드 실패 처리 및 재시도
- **FileUploadCompletedEventListener**: 완료 통계 및 알림

### 3. In-Memory Statistics

현재는 In-Memory 통계 사용:
- `AtomicInteger`, `AtomicLong`: Thread-safe 카운터
- `ConcurrentHashMap`: Thread-safe 맵

**향후 개선**:
- Redis 또는 Database로 영구 저장
- Spring Cache 통합
- Prometheus/Grafana 메트릭 연동

### 4. Error Classification

오류 메시지 기반 자동 분류:
- 패턴 매칭으로 오류 타입 추출
- 재시도 가능 여부 자동 판단
- 권장 조치 자동 제공

---

## Event Flow Diagram

```
[User uploads file]
        ↓
[UploadLdifFileUseCase.execute()]
        ↓
[UploadedFile.create() - FileUploadedEvent]
        ↓
[repository.save()]
        ↓
[ApplicationEventPublisher.publishEvent()]
        ↓
    ┌───┴───────────────────────────┐
    │   (Synchronous)               │
    ├───→ FileUploadEventHandler    │
    └───→ (Logging, immediate work) │
        │                           │
        │ [Transaction Commit]      │
        ↓                           │
    ┌───┴───────────────────────────┤
    │   (Asynchronous)              │
    ├───→ FileUploadEventHandler    │
    └───→ (Parsing trigger - TODO)  │

[If checksum validation fails]
        ↓
[UploadedFile.validateChecksum() - ChecksumValidationFailedEvent]
        ↓
    ┌───┴───────────────────────────────────┐
    │   (Synchronous)                       │
    ├───→ ChecksumValidationEventListener   │
    └───→ (Error logging, security alert)   │
        │                                   │
        │ [Transaction Commit]              │
        ↓                                   │
    ┌───┴───────────────────────────────────┤
    │   (Asynchronous)                      │
    ├───→ ChecksumValidationEventListener   │
    └───→ (Admin alert, error report)       │

[If upload fails]
        ↓
[FileUploadFailedEvent]
        ↓
    ┌───┴───────────────────────────────┐
    │   (Synchronous)                   │
    ├───→ FileUploadFailedEventListener │
    └───→ (Error logging, statistics)   │
        │                               │
        │ [Transaction Commit]          │
        ↓                               │
    ┌───┴───────────────────────────────┤
    │   (Asynchronous)                  │
    ├───→ FileUploadFailedEventListener │
    └───→ (Retry queue, admin alert)    │

[If upload completes]
        ↓
[FileUploadCompletedEvent]
        ↓
    ┌───┴─────────────────────────────────────┐
    │   (Synchronous)                         │
    ├───→ FileUploadCompletedEventListener    │
    └───→ (Success logging, statistics)       │
        │                                     │
        │ [Transaction Commit]                │
        ↓                                     │
    ┌───┴─────────────────────────────────────┤
    │   (Asynchronous)                        │
    ├───→ FileUploadCompletedEventListener    │
    └───→ (Notification, report generation)   │
```

---

## Code Examples

### 1. Checksum Validation Failed

**Trigger**:
```java
// In UploadedFile Aggregate
public void validateChecksum(Checksum calculatedChecksum) {
    if (!this.expectedChecksum.equals(calculatedChecksum)) {
        addDomainEvent(new ChecksumValidationFailedEvent(
            this.id,
            this.fileName.getValue(),
            this.expectedChecksum.getValue(),
            calculatedChecksum.getValue()
        ));
        this.status = UploadStatus.FAILED;
        this.errorMessage = "Checksum validation failed";
    }
}
```

**Listener Output**:
```
ERROR [Event] ChecksumValidationFailed
ERROR Upload ID: 123e4567-e89b-12d3-a456-426614174000
ERROR File name: icaopkd-002-complete-009410.ldif
ERROR Expected checksum : a1b2c3d4e5f6...
ERROR Calculated checksum: x9y8z7w6v5u4...
ERROR Summary: Checksum mismatch for 'icaopkd...': expected=a1b2c3d4..., calculated=x9y8z7w6...
ERROR ⚠️  SECURITY ALERT: Potential file tampering detected!
```

### 2. Upload Failed (Retryable)

**Trigger**:
```java
catch (IOException e) {
    addDomainEvent(new FileUploadFailedEvent(
        uploadId,
        fileName.getValue(),
        "IO error during file save: " + e.getMessage()
    ));
}
```

**Listener Output**:
```
ERROR [Event] FileUploadFailed
ERROR Upload ID: 456e7890-e89b-12d3-a456-426614174001
ERROR File name: icaopkd-001-delta-001234.ldif
ERROR Error message: IO error during file save: Disk full
ERROR Error type: IO_ERROR
ERROR Retryable: true
WARN  ⚠️  This error is retryable. Consider adding to retry queue.

INFO  [Event-Async] FileUploadFailed (Processing retry/alert)
INFO  Upload failure would be added to retry queue here
INFO  === Failure Pattern Analysis ===
INFO    Failure statistics by error type:
INFO      - IO_ERROR: 5 times
INFO      - NETWORK_ERROR: 2 times
INFO    Total failures: 7
INFO    Recommendation: Check disk space and file permissions
```

### 3. Upload Completed

**Trigger**:
```java
public void markAsCompleted() {
    this.status = UploadStatus.COMPLETED;
    addDomainEvent(new FileUploadCompletedEvent(
        this.id,
        this.fileName.getValue(),
        this.fileHash.getValue()
    ));
}
```

**Listener Output**:
```
INFO  [Event] FileUploadCompleted
INFO  ✅ Upload ID: 789a0123-e89b-12d3-a456-426614174002
INFO  ✅ File name: masterlist-Germany2024.ml
INFO  ✅ File hash: f1e2d3c4...
INFO  ✅ Event occurred at: 2025-10-19T14:05:00
INFO  🎉 Milestone reached: 100 files uploaded successfully!

INFO  [Event-Async] FileUploadCompleted (Generating reports)
INFO  === Upload Statistics Summary ===
INFO    Total completed uploads: 100
INFO    Recent daily uploads:
INFO      - 2025-10-19: 15 files
INFO      - 2025-10-18: 32 files
INFO      - 2025-10-17: 28 files
INFO    Today's uploads: 15
```

---

## Testing

### Build Test ✅

```bash
./mvnw clean compile -DskipTests
```

**Result**:
```
BUILD SUCCESS
Total time:  18.385 s
Compiled:    68 source files (65 → 68, +3 Event Listeners)
```

### Application Startup Test ✅

```bash
./mvnw spring-boot:run
```

**Result**:
```
Started LocalPkdApplication
Health: {"status":"UP"}
```

### Manual Event Testing

**Test Scenario 1**: Checksum Validation Failure
```java
// In test or actual upload flow
Checksum expected = Checksum.of("a1b2c3d4e5f6...");
Checksum calculated = Checksum.of("x9y8z7w6v5u4...");
uploadedFile.validateChecksum(calculated);
```

**Expected Output**: Security alert, checksum analysis, async alert

**Test Scenario 2**: Upload Failure (IO Error)
```java
try {
    fileStoragePort.saveFile(...);
} catch (IOException e) {
    throw new InfrastructureException("FILE_SAVE_ERROR",
        "IO error during file save: " + e.getMessage());
}
```

**Expected Output**: Error classification, retry queue recommendation, statistics update

**Test Scenario 3**: Upload Completion
```java
uploadedFile.markAsCompleted();
repository.save(uploadedFile);
```

**Expected Output**: Success logging, statistics update, milestone check

---

## Files Created/Modified

### Created Files (3)
1. `ChecksumValidationEventListener.java` - Checksum 검증 실패 처리
2. `FileUploadFailedEventListener.java` - 업로드 실패 처리 및 재시도
3. `FileUploadCompletedEventListener.java` - 업로드 완료 통계 및 알림

### Source File Count
- Before: 65 files
- After: 68 files
- Change: +3 (+4.6%)

---

## Performance Considerations

### In-Memory Statistics

**Pros**:
- ✅ 빠른 읽기/쓰기
- ✅ Thread-safe (Atomic, Concurrent)
- ✅ 구현 간단

**Cons**:
- ❌ 애플리케이션 재시작 시 손실
- ❌ 스케일아웃 불가 (인스턴스별 별도)
- ❌ 메모리 사용량 증가 (많은 데이터 시)

**Future Improvements**:
- Redis를 사용한 영구 저장
- Spring Cache 통합
- Database 테이블로 이동

### Async Processing

**Thread Pool 설정** (필요 시):
```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("EventAsync-");
        executor.initialize();
        return executor;
    }
}
```

---

## Future Enhancements

### 1. Notification Service Integration
```java
// TODO 구현 예정
private final NotificationService notificationService;

notificationService.sendChecksumMismatchAlert(
    event.fileName(),
    event.expectedChecksum(),
    event.calculatedChecksum()
);
```

### 2. Retry Queue Service
```java
// TODO 구현 예정
private final RetryQueueService retryQueueService;

if (isRetryable(event.errorMessage())) {
    retryQueueService.enqueue(new RetryTask(
        event.uploadId().getId().toString(),
        event.fileName(),
        event.errorMessage()
    ));
}
```

### 3. Prometheus Metrics
```java
// TODO 구현 예정
private final MeterRegistry meterRegistry;

meterRegistry.counter("file.upload.completed",
    "file_type", extractFileType(event.fileName())
).increment();
```

### 4. Error Report Service
```java
// TODO 구현 예정
private final ErrorReportService errorReportService;

ErrorReport report = errorReportService.createChecksumValidationReport(event);
errorReportService.save(report);
```

### 5. Daily Report Generation
```java
// TODO 구현 예정
private final ReportGenerationService reportService;

if (shouldGenerateReport()) {
    reportService.generateDailyUploadReport();
}
```

---

## Best Practices Applied

### 1. Synchronous vs Asynchronous

**Synchronous** (@EventListener):
- 즉시 처리 필요한 작업
- 트랜잭션 내에서 실행
- 에러 로깅, 메트릭 업데이트

**Asynchronous** (@TransactionalEventListener + @Async):
- 시간이 걸리는 작업
- 트랜잭션 커밋 후 실행
- 알림 발송, 리포트 생성

### 2. Thread-Safe Statistics

- `AtomicInteger`, `AtomicLong`: Lock-free 카운터
- `ConcurrentHashMap`: Thread-safe 맵
- No synchronization overhead

### 3. Error Classification

- 자동화된 오류 타입 분류
- 재시도 가능 여부 판단
- 명확한 권장 조치 제공

### 4. Logging Levels

- **ERROR**: 실패, 검증 오류
- **WARN**: 재시도 권장, 중복 파일
- **INFO**: 완료, 통계, 마일스톤
- **DEBUG**: 상세 통계

---

## Conclusion

Phase 7 Event Listeners Implementation이 성공적으로 완료되었습니다.

### Summary
- ✅ 3개 전문 Event Listener 구현
- ✅ 동기/비동기 이벤트 처리
- ✅ In-Memory 통계 수집
- ✅ 오류 분류 및 재시도 판단
- ✅ Build & Application 실행 성공

### Impact
- **Observability**: 이벤트 기반 로깅 및 통계
- **Reliability**: 재시도 가능한 오류 자동 판단
- **Security**: 파일 변조 감지 및 경고
- **Monitoring**: 완료 통계 및 마일스톤

### Next Steps (Optional)
- Notification Service 통합
- Retry Queue Service 구현
- Prometheus Metrics 연동
- Redis 기반 영구 통계 저장
- Daily Report 자동 생성

---

**Document Version**: 1.0
**Created**: 2025-10-19
**Status**: ✅ **COMPLETED**
