# Session 2025-12-20: Event-Driven Architecture for Async LDAP Uploads

## Overview

MSA 전환 대비 비동기 LDAP 업로드 아키텍처를 구현했습니다. 현재는 Spring ApplicationEventPublisher를 사용하고, 향후 RabbitMQ로 전환할 수 있도록 설계되었습니다.

## Architecture Design

### Current (Monolithic)

```
ValidateCertificatesUseCase
    ↓ publishEvent()
ApplicationEventPublisher (Spring)
    ↓ @EventListener
AsyncLdapUploadHandler
    ↓ ldapUploadExecutor (별도 스레드 풀)
LdapBatchUploadService → LDAP Server
```

### Future (MSA with RabbitMQ)

```
ValidateCertificatesUseCase
    ↓ convertAndSend()
RabbitTemplate
    ↓ AMQP
Exchange: ldap.upload
    ↓ Routing Key: ldap.upload.{certificate|crl}
Queue: ldap.upload.queue
    ↓ @RabbitListener
AsyncLdapUploadHandler
    ↓
LdapBatchUploadService → LDAP Server
```

## Implementation Details

### 1. LdapBatchUploadEvent (Domain Event)

**파일**: `src/main/java/com/smartcoreinc/localpkd/ldapintegration/domain/event/LdapBatchUploadEvent.java`

**주요 특징**:

- `DomainEvent` 인터페이스 구현 (eventId, occurredOn, eventType)
- `Serializable` 구현 (RabbitMQ JSON 직렬화 대비)
- Builder 패턴으로 이벤트 생성
- UploadType enum으로 인증서/CRL 구분
- batchId로 멱등성 보장

```java
public class LdapBatchUploadEvent implements DomainEvent, Serializable {
    private final UUID batchId;      // 멱등성 키
    private final UUID uploadId;     // 파일 업로드 추적용
    private final UploadType uploadType;  // CERTIFICATE or CRL
    private final List<UUID> targetIds;   // 업로드할 인증서/CRL ID 목록
    private final int batchNumber;   // 진행률 계산용
    private final int totalBatches;  // 진행률 계산용

    public enum UploadType {
        CERTIFICATE("certificate"),
        CRL("crl");

        public String getRoutingKey() {
            return "ldap.upload." + routingKey;  // RabbitMQ 라우팅 키
        }
    }
}
```

### 2. AsyncLdapUploadHandler (Event Handler)

**파일**: `src/main/java/com/smartcoreinc/localpkd/ldapintegration/application/event/AsyncLdapUploadHandler.java`

**주요 특징**:

- `@Async("ldapUploadExecutor")` - 전용 스레드 풀 사용
- `@EventListener` - Spring 이벤트 구독
- `@Transactional(propagation = REQUIRES_NEW)` - 독립 트랜잭션
- 멱등성 체크 (processedBatches Map)
- SSE 진행 상황 전송

```java
@Async("ldapUploadExecutor")
@EventListener
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void handleLdapBatchUpload(LdapBatchUploadEvent event) {
    UUID batchId = event.getBatchId();

    // 멱등성 체크
    if (processedBatches.containsKey(batchId)) {
        log.info("Batch already processed, skipping: batchId={}", batchId);
        return;
    }

    // 인증서/CRL 조회 및 LDAP 업로드
    if (event.getUploadType() == UploadType.CERTIFICATE) {
        List<Certificate> certificates = certificateRepository.findAllById(...);
        LdapBatchUploadResult result = ldapBatchUploadService.uploadCertificates(certificates);
    } else {
        List<CertificateRevocationList> crls = crlRepository.findAllById(...);
        LdapBatchUploadResult result = ldapBatchUploadService.uploadCrls(crls);
    }

    // 처리 완료 기록
    processedBatches.put(batchId, LocalDateTime.now());
}
```

### 3. LdapUploadAsyncConfig (Thread Pool)

**파일**: `src/main/java/com/smartcoreinc/localpkd/ldapintegration/config/LdapUploadAsyncConfig.java`

**스레드 풀 설정**:

| 설정 | 값 | 설명 |
|------|-----|------|
| Core Pool Size | 4 | 기본 스레드 수 |
| Max Pool Size | 8 | 최대 스레드 수 |
| Queue Capacity | 200 | 대기 작업 큐 크기 |
| Keep Alive | 120초 | 유휴 스레드 유지 시간 |
| Rejection Policy | CallerRunsPolicy | 큐 가득 시 호출자 스레드에서 실행 |

```java
@Bean(name = "ldapUploadExecutor")
public Executor ldapUploadExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("ldap-upload-");
    executor.setKeepAliveSeconds(120);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(120);
    executor.setRejectedExecutionHandler(new CallerRunsPolicy());
    return executor;
}
```

### 4. Repository Changes

**CertificateRepository.java**:

```java
List<Certificate> findAllById(List<CertificateId> ids);
```

**CertificateRevocationListRepository.java**:

```java
List<CertificateRevocationList> findAllById(List<CrlId> ids);
```

### 5. ValidateCertificatesUseCase Changes

**변경 전 (동기 호출)**:

```java
// 배치 저장 후 즉시 LDAP 업로드
certificateRepository.saveAll(cscaBatch);
LdapBatchUploadResult ldapResult = ldapBatchUploadService.uploadCertificates(cscaBatch);
```

**변경 후 (비동기 이벤트 발행)**:

```java
// 배치 저장 후 비동기 이벤트 발행
certificateRepository.saveAll(cscaBatch);
cscaBatchNumber++;
List<UUID> batchIds = cscaBatch.stream()
    .map(cert -> cert.getId().getId())
    .toList();
LdapBatchUploadEvent ldapEvent = LdapBatchUploadEvent.forCertificates(
    command.uploadId(), batchIds, cscaBatchNumber, estimatedCscaBatches);
eventPublisher.publishEvent(ldapEvent);
```

## Benefits

### 1. Thread Separation

- 인증서 검증: `cert-validation-async-*` 스레드 풀
- LDAP 업로드: `ldap-upload-*` 스레드 풀
- 검증이 LDAP 응답을 기다리지 않음

### 2. Performance

- 검증과 LDAP 업로드가 병렬 실행
- LDAP 서버 응답 지연이 검증에 영향 없음
- 전체 처리 시간 단축

### 3. MSA Ready

- RabbitMQ 전환 시 Event → Message 변환만 필요
- Exchange/Routing Key 설계 완료
- Serializable 구현으로 JSON 직렬화 가능

### 4. Idempotency

- batchId 기반 중복 처리 방지
- 네트워크 재시도 시에도 안전

## Test Results

```
CertificateValidation tests: 13 passed
Related tests: 28 passed
BUILD SUCCESS
```

## Files Changed

### New Files (3)

1. `LdapBatchUploadEvent.java` - 도메인 이벤트 (225 lines)
2. `AsyncLdapUploadHandler.java` - 비동기 핸들러 (180 lines)
3. `LdapUploadAsyncConfig.java` - 스레드 풀 설정 (85 lines)

### Modified Files (5)

1. `ValidateCertificatesUseCase.java` - 비동기 이벤트 발행 (+66, -27 lines)
2. `CertificateRepository.java` - findAllById 추가 (+12 lines)
3. `JpaCertificateRepository.java` - findAllById 구현 (+14 lines)
4. `CertificateRevocationListRepository.java` - findAllById 추가 (+12 lines)
5. `JpaCertificateRevocationListRepository.java` - findAllById 구현 (+14 lines)

## Commit

```
6217b56 feat: Implement Event-Driven architecture for async LDAP uploads
```

## Future Work

### RabbitMQ 전환 시 변경 사항

1. **Publisher 변경**:
   ```java
   // Before
   eventPublisher.publishEvent(ldapEvent);

   // After
   rabbitTemplate.convertAndSend("ldap.upload", event.getUploadType().getRoutingKey(), ldapEvent);
   ```

2. **Consumer 변경**:
   ```java
   // Before
   @EventListener
   public void handleLdapBatchUpload(LdapBatchUploadEvent event)

   // After
   @RabbitListener(queues = "ldap.upload.queue")
   public void handleLdapBatchUpload(LdapBatchUploadEvent event)
   ```

3. **DLQ 설정**:
   - Dead Letter Queue: `ldap.upload.dlq`
   - 실패 메시지 재처리 로직 추가

---

**Date**: 2025-12-20
**Author**: Claude Code
**Status**: COMPLETED
