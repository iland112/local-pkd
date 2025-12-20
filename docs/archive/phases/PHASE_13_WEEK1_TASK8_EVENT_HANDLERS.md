# Phase 13 Week 1 - Task 8: Event Handlers & Event-Driven Architecture

**Completion Date**: 2025-10-25
**Status**: ✅ COMPLETED
**Build Status**: ✅ BUILD SUCCESS (144 source files)

## Overview

Task 8 완료로 Certificate Validation Bounded Context의 **Event-Driven Architecture** 가 완성되었습니다.

**4개의 Domain Events** 와 **1개의 Event Handler** , **1개의 Async Config** 를 구현하여 이벤트 기반 처리 아키텍처를 구축했습니다.

---

## 구현 내용

### 1. Domain Events (4개)

#### CertificateValidatedEvent (기존 구현)
**파일**: `domain/event/CertificateValidatedEvent.java` (119 lines)

**목적**: 인증서 검증 완료 이벤트

**필드**:
```java
- eventId: UUID                    // 이벤트 고유 ID
- occurredOn: LocalDateTime        // 발생 시간
- certificateId: CertificateId     // 검증된 인증서 ID
- validationStatus: CertificateStatus // 검증 결과 상태
```

**발행 시점**: `ValidateCertificateUseCase`에서 검증 완료 후

#### TrustChainVerifiedEvent (신규)
**파일**: `domain/event/TrustChainVerifiedEvent.java` (184 lines)

**목적**: Trust Chain 검증 완료 이벤트

**필드**:
```java
- eventId: UUID                         // 이벤트 고유 ID
- occurredOn: LocalDateTime             // 발생 시간
- endEntityCertificateId: CertificateId // End Entity 인증서 ID
- trustAnchorCertificateId: CertificateId // Trust Anchor (CSCA) ID
- chainDepth: int                       // 체인 깊이 (레벨 수)
- chainValid: boolean                   // 체인 유효 여부
- trustAnchorCountryCode: String        // Trust Anchor 국가 코드
```

**발행 시점**: `VerifyTrustChainUseCase`에서 검증 완료 후

**검증 구조**:
```
End Entity Certificate (Level 0)
  ↓ (signed by)
Intermediate CA / DSC (Level 1)
  ↓ (signed by)
Trust Anchor / CSCA (Level 2)
```

#### CertificateRevokedEvent (신규)
**파일**: `domain/event/CertificateRevokedEvent.java` (221 lines)

**목적**: 인증서 폐기 감지 이벤트

**필드**:
```java
- eventId: UUID                        // 이벤트 고유 ID
- occurredOn: LocalDateTime            // 발생 시간
- certificateId: CertificateId         // 폐기된 인증서 ID
- serialNumber: String                 // 인증서 일련번호
- issuerDn: String                     // 발급자 DN
- revokedAt: LocalDateTime             // 폐기 날짜
- revocationReasonCode: int            // 폐기 사유 코드 (0-6, RFC 5280)
- revocationReason: String             // 폐기 사유 설명
- crlVersion: long                     // CRL 버전
```

**폐기 사유 코드** (RFC 5280):
| 코드 | 사유 | 설명 |
|------|------|------|
| 0 | unspecified | 사유 불명 |
| 1 | keyCompromise | 개인키 유출 |
| 2 | cACompromise | CA 유출 |
| 3 | superseded | 대체됨 |
| 4 | cessationOfOperation | 운영 중단 |
| 5 | certificateHold | 임시 보류 |
| 6 | removeFromCRL | CRL 제거 |

**발행 시점**: `CheckRevocationUseCase`에서 폐기 감지 후

**보안 영향**:
- 이 인증서로 서명된 모든 문서는 신뢰할 수 없음
- 이 인증서를 기반으로 한 Trust Chain도 무효화
- 즉시 검증 거부 필요

#### ValidationFailedEvent (신규)
**파일**: `domain/event/ValidationFailedEvent.java` (276 lines)

**목적**: 인증서 검증 실패 이벤트

**필드**:
```java
- eventId: UUID                           // 이벤트 고유 ID
- occurredOn: LocalDateTime               // 발생 시간
- certificateId: CertificateId            // 검증 실패 인증서 ID
- subjectDn: String                       // 인증서 주체 DN
- primaryFailureReason: FailureReason     // 주요 실패 이유
- failureMessage: String                  // 상세 메시지
- additionalErrors: List<String>          // 부차 오류 목록
- durationMillis: Long                    // 검증 소요 시간
```

**실패 이유 Enum**:
```java
enum FailureReason {
    SIGNATURE_INVALID,      // 서명 검증 실패
    CHAIN_INVALID,          // Trust Chain 검증 실패
    CERTIFICATE_REVOKED,    // 인증서 폐기됨
    EXPIRED,                // 유효기간 만료
    NOT_YET_VALID,          // 아직 유효하지 않음
    INVALID_CONSTRAINTS,    // 제약사항 위반
    UNKNOWN_ERROR           // 알 수 없는 오류
}
```

**발행 시점**: 검증 실패 시 (모든 Use Case에서)

**치명적 실패**:
- REVOKED: 폐기된 인증서
- EXPIRED: 유효기간 만료
- SIGNATURE_INVALID: 서명 무효

---

### 2. Event Handler (1개)

#### CertificateValidationEventHandler
**파일**: `application/event/CertificateValidationEventHandler.java` (460 lines)

**책임**:
- Certificate Validation Bounded Context의 Domain Events 처리
- 동기/비동기 이벤트 핸들러 제공
- 트랜잭션 경계 관리

**처리하는 이벤트**:
1. CertificateValidatedEvent
2. TrustChainVerifiedEvent
3. CertificateRevokedEvent
4. ValidationFailedEvent

**이벤트 처리 흐름**:

```
Domain Event Published
  ↓
동기 핸들러 (BEFORE_COMMIT)
  ├── 읽기 전용 작업
  ├── 로깅 및 통계
  └── 보안 감시 (빠른 응답 필요)
  ↓
트랜잭션 커밋
  ↓
비동기 핸들러 (AFTER_COMMIT)
  ├── LDAP 연동
  ├── 감시 알림
  ├── 메트릭 수집
  └── 외부 시스템 호출
```

#### CertificateValidatedEvent 처리

**동기 핸들러** (`BEFORE_COMMIT`):
```java
@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
public void handleCertificateValidatedSync(CertificateValidatedEvent event) {
    // 1. 검증 통계 업데이트 [skeleton]
    updateValidationStatistics(event);

    // 2. 보안 감시 [skeleton]
    monitorSecurityStatus(event);
}
```

**비동기 핸들러** (`AFTER_COMMIT`):
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async("certificationValidationExecutor")
public void handleCertificateValidatedAsync(CertificateValidatedEvent event) {
    // 1. LDAP 동기화 [skeleton]
    syncToLdap(event);

    // 2. 감시 알림 [skeleton]
    sendMonitoringAlert(event);

    // 3. 메트릭 수집 [skeleton]
    collectMetrics(event);
}
```

#### TrustChainVerifiedEvent 처리

**동기 핸들러**:
- Trust Chain 통계 업데이트
- 체인 구조 분석

**비동기 핸들러**:
- LDAP 신뢰 관계 설정
- Trust Chain 관련 알림

#### CertificateRevokedEvent 처리 (긴급)

**동기 핸들러** (즉시):
- 폐기 통계 업데이트
- 보안 경고 기록

**비동기 핸들러** (긴급 처리):
```java
@Async("certificationValidationExecutor")
public void handleCertificateRevokedAsync(CertificateRevokedEvent event) {
    // 1. LDAP 엔트리 즉시 비활성화
    disableLdapEntry(event);

    // 2. 보안팀 긴급 알림
    sendEmergencySecurityAlert(event);

    // 3. 서명된 문서 추적
    trackSignedDocuments(event);
}
```

#### ValidationFailedEvent 처리

**동기 핸들러**:
- 실패 통계 업데이트
- 심각도 평가
- 오류 분석 기록

**비동기 핸들러**:
- LDAP 상태 업데이트 (조건부)
- 모니터링 설정
- 운영 알림
- 이상 탐지 시스템 알림

---

### 3. Async Configuration (1개)

#### CertificateValidationAsyncConfig
**파일**: `config/CertificateValidationAsyncConfig.java` (119 lines)

**목적**: 비동기 이벤트 처리용 스레드 풀 설정

**스레드 풀 파라미터**:
```
Core Pool Size: 5         // 기본적으로 유지할 스레드 수
Max Pool Size: 20         // 최대 동시 실행 스레드 수
Queue Capacity: 500       // 대기 작업 큐 크기
Keep Alive Time: 60초     // 코어 초과 스레드 유지 시간
Thread Name: "cert-validation-async-"
Rejection Policy: CallerRunsPolicy
```

**거부 정책** (CallerRunsPolicy):
```
큐가 가득 찼을 때:
  → 새 스레드 생성 불가
  → 호출자 스레드에서 직접 실행

장점: 비동기 작업 실패 방지
단점: 성능 저하 가능성
```

**Bean 선언**:
```java
@Bean(name = "certificationValidationExecutor")
public Executor certificationValidationExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("cert-validation-async-");
    executor.setKeepAliveSeconds(60);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);
    executor.setRejectedExecutionHandler(
        new ThreadPoolExecutor.CallerRunsPolicy()
    );
    return executor;
}
```

---

## 아키텍처 패턴

### 1. Domain Event Pattern

```
Domain Layer
  ↓
Domain Event (Value Object)
  ↓
Aggregate Root (이벤트 추가)
  ↓
Repository (이벤트 발행)
  ↓
Event Bus (Spring ApplicationEventPublisher)
  ↓
Event Handlers (Application Layer)
  ├── Synchronous (읽기 전용)
  └── Asynchronous (외부 시스템)
```

### 2. Transactional Event Pattern

```
@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  ↓ (트랜잭션 커밋 전)
  ├── 읽기 전용 작업
  ├── 빠른 응답 필요
  └── 메인 트랜잭션과 동일 트랜잭션
  ↓ (커밋 성공)

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  ↓ (트랜잭션 커밋 후)
  ├── 별도 트랜잭션
  └── 별도 스레드 (@Async)
```

### 3. 이벤트 우선순위

```
High Priority (긴급):
  - CertificateRevokedEvent (폐기 감지)
  - ValidationFailedEvent (치명적 실패)

Normal Priority:
  - CertificateValidatedEvent (검증 완료)
  - TrustChainVerifiedEvent (체인 검증)
```

---

## 트랜잭션 경계

```
Use Case (@Transactional)
  │
  ├── Domain Logic
  ├── Aggregate Modification
  └── Domain Events 추가
      │
      └── Repository.save()
          │
          ├── [트랜잭션 커밋]
          │
          ├── BEFORE_COMMIT 핸들러 실행
          │   └── 읽기 전용 작업
          │
          └── [커밋 완료]
              │
              └── AFTER_COMMIT 핸들러 실행
                  ├── EventBus.publishAll()
                  └── 비동기 핸들러 (@Async)
```

**Key Points**:
- Domain Events는 Repository.save() 시에 자동 발행
- 동기 핸들러는 메인 트랜잭션 내에서 실행
- 비동기 핸들러는 별도 스레드/트랜잭션에서 실행
- 비동기 핸들러 오류는 메인 플로우에 영향 없음

---

## 오류 처리 전략

### Synchronous Handler
```
예외 발생
  ↓
로깅 (ERROR)
  ↓
메인 플로우에 영향 없음 (try-catch)
```

### Asynchronous Handler
```
예외 발생
  ↓
로깅 (WARN/ERROR)
  ↓
메인 플로우에 영향 없음 (별도 스레드)
  ↓
별도 모니터링 필요
```

---

## 통계

### 생성된 파일 (총 6개)

| 파일 | 행 | 설명 |
|------|-----|------|
| TrustChainVerifiedEvent.java | 184 | Trust Chain 검증 완료 이벤트 |
| CertificateRevokedEvent.java | 221 | 인증서 폐기 이벤트 |
| ValidationFailedEvent.java | 276 | 검증 실패 이벤트 |
| CertificateValidationEventHandler.java | 460 | 이벤트 핸들러 (4개 이벤트) |
| CertificateValidationAsyncConfig.java | 119 | 비동기 설정 |
| **합계** | **1,260** | |

### 코드 통계

- **총 새로 추가된 코드**: ~1,260 lines
- **JavaDoc 포함**: 전체 코드의 약 40%
- **Event Handler 메서드**: 8개 (동기 4, 비동기 4)
- **Skeleton 메서드**: 19개 (Phase 11 Sprint 5에서 구현)
- **컴파일된 소스 파일**: 144개
- **빌드 상태**: ✅ BUILD SUCCESS (11.691s)

---

## 이벤트 흐름 예시

### 1. 인증서 검증 완료 흐름

```
ValidateCertificateUseCase.execute()
  ↓
Certificate.recordValidation(validationResult)
  └─ CertificateValidatedEvent 추가
  ↓
repository.save(certificate)
  ├─ [DB 저장]
  ├─ [트랜잭션 커밋]
  │
  ├─ handleCertificateValidatedSync() [동기]
  │  ├─ updateValidationStatistics()
  │  └─ monitorSecurityStatus()
  │
  └─ handleCertificateValidatedAsync() [비동기]
     ├─ syncToLdap()
     ├─ sendMonitoringAlert()
     └─ collectMetrics()
  ↓
클라이언트에 응답
```

### 2. 인증서 폐기 감지 흐름 (긴급)

```
CheckRevocationUseCase.execute()
  ↓
CertificateRevoked 감지
  ├─ Certificate 상태 변경
  └─ CertificateRevokedEvent 추가
  ↓
repository.save(certificate)
  ├─ [DB 저장]
  ├─ [트랜잭션 커밋]
  │
  ├─ handleCertificateRevokedSync() [동기, 즉시]
  │  ├─ updateRevocationStatistics()
  │  └─ recordSecurityAlert() ⚠️ WARN
  │
  └─ handleCertificateRevokedAsync() [비동기, 긴급]
     ├─ disableLdapEntry() 🚨 URGENT
     ├─ sendEmergencySecurityAlert() 🚨 CRITICAL
     └─ trackSignedDocuments() 📊
  ↓
클라이언트에 응답
```

### 3. 검증 실패 흐름

```
ValidateCertificateUseCase.execute()
  ↓
검증 실패 (실패 이유별)
  ├─ ValidationFailedEvent 생성
  └─ Certificate에 오류 정보 기록
  ↓
repository.save(certificate)
  ├─ [DB 저장]
  ├─ [트랜잭션 커밋]
  │
  ├─ handleValidationFailedSync() [동기]
  │  ├─ updateFailureStatistics()
  │  ├─ evaluateFailureSeverity()
  │  └─ recordFailureAnalysis()
  │
  └─ handleValidationFailedAsync() [비동기]
     ├─ updateLdapStatusConditional()
     ├─ setupMonitoring()
     ├─ sendOperationalAlert()
     └─ notifyAnomalyDetection()
  ↓
클라이언트에 오류 응답
```

---

## 모니터링 및 관찰성

### 로그 패턴

**동기 핸들러** (INFO/WARN):
```
=== Certificate Validated Event (Sync) ===
Event: CertificateValidated
Certificate ID: 550e8400-e29b-41d4-a716-446655440000
Status: VALID
Occurred at: 2025-10-25T00:30:00
```

**비동기 핸들러** (DEBUG):
```
=== Certificate Validated Event (Async) ===
Certificate ID: 550e8400-e29b-41d4-a716-446655440000
Certificate validation async handler completed successfully
```

**폐기 감지** (ERROR/WARN):
```
=== Certificate Revoked Event (Sync) ===
Certificate ID: 550e8400-e29b-41d4-a716-446655440000
Revocation Reason: Key Compromise (1)
Revoked At: 2025-10-25T00:30:00
```

### 메트릭 수집 대상 [skeleton]

- 검증 성공/실패 율
- 평균 검증 시간
- Trust Chain 깊이 분포
- 폐기 감지 빈도
- 검증 실패 원인 분석

---

## 주요 설계 결정

### 1. Skeleton Implementation
모든 실제 작업 (LDAP, 알림, 메트릭)은 skeleton으로 표시:
```java
private void updateValidationStatistics(CertificateValidatedEvent event) {
    // TODO: Phase 11 Sprint 5에서 통계 서비스 구현 예정
    log.debug("[SKELETON] Updating validation statistics for: {}",
        event.getCertificateId().getId());
}
```

### 2. Asynchronous Event Processing
```
비동기 스레드 풀: "cert-validation-async-"
  - Core: 5, Max: 20
  - 거부 정책: CallerRunsPolicy (작업 실패 방지)
  - 타임아웃: 60초 (정상 종료)
```

### 3. Transactional Boundary
```
BEFORE_COMMIT 핸들러: 메인 트랜잭션 내
  → 빠른 응답 (읽기 전용)

AFTER_COMMIT 핸들러: 별도 트랜잭션
  → 외부 시스템 연동 (LDAP, 알림)
  → 시간 소요 작업
```

### 4. Error Handling Strategy
```
동기 핸들러: try-catch (메인 흐름 영향 없음)
비동기 핸들러: 별도 스레드 (예외 격리)
긴급 이벤트: 동기 + 비동기 처리 (폐기 감지)
```

---

## Next Steps (Phase 13 Week 2)

### Task 9: REST Controllers
- `/api/validate` - 인증서 검증 API
- `/api/verify-trust-chain` - Trust Chain 검증 API
- `/api/check-revocation` - 폐기 확인 API
- 요청/응답 검증

### Task 10: Integration Tests
- Event Handler 통합 테스트
- 트랜잭션 경계 테스트
- 비동기 처리 테스트
- 이벤트 발행/처리 검증

### Task 11: API Documentation
- OpenAPI/Swagger 문서
- 예제 요청/응답
- 오류 코드 설명

### Phase 11 Sprint 5: Implementation Details
- 실제 LDAP 연동
- 메트릭 수집 서비스
- 알림 시스템
- 모니터링 대시보드

---

## 의존성 및 라이브러리

- **Spring Framework**: @Service, @TransactionalEventListener
- **Spring Boot**: @Async, ThreadPoolTaskExecutor
- **Lombok**: @Slf4j, @RequiredArgsConstructor, @Getter
- **JPA**: Entity, Repository

---

## 코드 품질

### JavaDoc 완성도
- ✅ 모든 이벤트 클래스: 발행 시점, 용도, 필드 설명
- ✅ 모든 핸들러 메서드: 처리 내용, 실행 시점
- ✅ 설정 클래스: 스레드 풀 파라미터 설명

### 예외 처리
- ✅ Synchronous: try-catch로 메인 흐름 보호
- ✅ Asynchronous: 별도 스레드에서 격리
- ✅ 긴급 처리: 폐기 감지 즉시 동기 처리

### 트랜잭션 관리
- ✅ BEFORE_COMMIT: 메인 트랜잭션 내
- ✅ AFTER_COMMIT: 별도 트랜잭션
- ✅ Async: 타임아웃 설정으로 정상 종료 보장

---

**Task 8 완료**

✅ Domain Events: 4개 (CertificateValidated, TrustChainVerified, CertificateRevoked, ValidationFailed)
✅ Event Handler: 8개 메서드 (동기 4, 비동기 4)
✅ Async Configuration: ThreadPool 설정
✅ Build: SUCCESS
✅ 준비 완료 → Task 9: REST Controllers
