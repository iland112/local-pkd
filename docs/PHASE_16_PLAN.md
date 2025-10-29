# Phase 16: End-to-End Integration - Implementation Plan

**시작 날짜**: 2025-10-29
**예상 기간**: 1-2주
**목표**: 모든 Phase 연결 및 완전한 E2E 워크플로우 구현

---

## 개요

Phase 1-15에서 구현한 모든 컴포넌트를 연결하여 완전한 End-to-End 워크플로우를 구성합니다:

```
File Upload (Phase 4-5)
    ↓ FileUploadedEvent
LDIF Parsing (Phase 10)
    ↓ FileParsingCompletedEvent
Certificate Validation (Phase 11-13)
    ↓ CertificatesValidatedEvent
LDAP Upload (Phase 14-15)
    ↓ LdapUploadCompletedEvent
SSE Progress Updates (Phase 9)
```

---

## Task 1: Event-Driven Orchestration (Week 1, Day 1-3)

### 목표
각 Phase의 Domain Events를 연결하여 자동 워크플로우 구성

### 구현할 Event Handlers

#### 1.1. FileUploadEventHandler 개선
**위치**: `fileupload/application/event/FileUploadEventHandler.java` (이미 존재)

**추가 기능**:
- `FileUploadedEvent` 수신 시 자동으로 파싱 트리거
- `ProgressService`를 통한 SSE 진행 상황 업데이트

```java
@EventListener
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleFileUploadedAndTriggerParsing(FileUploadedEvent event) {
    UUID uploadId = event.uploadId();

    // 1. SSE: 파싱 시작 알림
    progressService.sendProgress(
        ProcessingProgress.parsingStarted(uploadId, "LDIF 파일 파싱 시작")
    );

    // 2. LDIF 파싱 트리거
    try {
        ParseLdifFileCommand command = ParseLdifFileCommand.builder()
            .uploadId(uploadId)
            .fileName(event.fileName())
            .filePath(event.filePath())
            .build();

        parseLdifFileUseCase.execute(command);

    } catch (Exception e) {
        progressService.sendProgress(
            ProcessingProgress.failed(uploadId, "파싱 실패: " + e.getMessage())
        );
        eventPublisher.publishEvent(new FileUploadFailedEvent(uploadId, e.getMessage()));
    }
}
```

#### 1.2. LdifParsingEventHandler (NEW)
**위치**: `ldifparsing/application/event/LdifParsingEventHandler.java` (신규 생성)

**기능**:
- `FileParsingCompletedEvent` 수신 시 인증서 검증 트리거
- SSE 진행 상황 업데이트

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class LdifParsingEventHandler {

    private final ProgressService progressService;
    private final ValidateCertificatesUseCase validateCertificatesUseCase;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleParsingCompletedAndTriggerValidation(FileParsingCompletedEvent event) {
        UUID uploadId = event.uploadId();

        // SSE: 검증 시작 알림
        progressService.sendProgress(
            ProcessingProgress.validationStarted(uploadId, "인증서 검증 시작")
        );

        // 인증서 검증 트리거
        try {
            ValidateCertificatesCommand command = ValidateCertificatesCommand.builder()
                .uploadId(uploadId)
                .certificateIds(event.extractedCertificateIds())
                .crlIds(event.extractedCrlIds())
                .build();

            validateCertificatesUseCase.execute(command);

        } catch (Exception e) {
            progressService.sendProgress(
                ProcessingProgress.failed(uploadId, "검증 실패: " + e.getMessage())
            );
        }
    }
}
```

#### 1.3. CertificateValidationEventHandler (NEW)
**위치**: `certificatevalidation/application/event/CertificateValidationEventHandler.java` (신규 생성)

**기능**:
- `CertificatesValidatedEvent` 수신 시 LDAP 업로드 트리거
- SSE 진행 상황 업데이트

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class CertificateValidationEventHandler {

    private final ProgressService progressService;
    private final UploadToLdapUseCase uploadToLdapUseCase;

    @EventListener
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleValidationCompletedAndTriggerLdapUpload(CertificatesValidatedEvent event) {
        UUID uploadId = event.uploadId();

        // SSE: LDAP 업로드 시작 알림
        progressService.sendProgress(
            ProcessingProgress.ldapSavingStarted(uploadId, "LDAP 서버 업로드 시작")
        );

        // LDAP 업로드 트리거
        try {
            UploadToLdapCommand command = UploadToLdapCommand.builder()
                .uploadId(uploadId)
                .certificateIds(event.validatedCertificateIds())
                .crlIds(event.validatedCrlIds())
                .build();

            uploadToLdapUseCase.execute(command);

        } catch (Exception e) {
            progressService.sendProgress(
                ProcessingProgress.failed(uploadId, "LDAP 업로드 실패: " + e.getMessage())
            );
        }
    }
}
```

#### 1.4. LdapUploadEventHandler (NEW)
**위치**: `ldapintegration/application/event/LdapUploadEventHandler.java` (신규 생성)

**기능**:
- `LdapUploadCompletedEvent` 수신 시 최종 완료 처리
- SSE 완료 알림

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class LdapUploadEventHandler {

    private final ProgressService progressService;
    private final UploadedFileRepository uploadedFileRepository;

    @EventListener
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLdapUploadCompleted(LdapUploadCompletedEvent event) {
        UUID uploadId = event.uploadId();

        // SSE: 완료 알림
        progressService.sendProgress(
            ProcessingProgress.completed(
                uploadId,
                String.format("처리 완료: %d개 인증서, %d개 CRL 업로드됨",
                    event.uploadedCertificateCount(),
                    event.uploadedCrlCount())
            )
        );

        // UploadedFile 상태 업데이트 (RECEIVED → COMPLETED)
        uploadedFileRepository.findById(UploadId.of(uploadId.toString()))
            .ifPresent(file -> {
                file.markAsCompleted();
                uploadedFileRepository.save(file);
            });
    }
}
```

### 예상 결과물
- 4개 Event Handler 클래스
- 자동 워크플로우 연결
- SSE 진행 상황 전파
- 약 400 lines

---

## Task 2: Missing Domain Events 추가 (Week 1, Day 3-4)

### 2.1. FileParsingCompletedEvent
**위치**: `ldifparsing/domain/event/FileParsingCompletedEvent.java`

```java
public record FileParsingCompletedEvent(
    UUID uploadId,
    String fileName,
    List<UUID> extractedCertificateIds,
    List<UUID> extractedCrlIds,
    int totalCertificates,
    int totalCrls,
    LocalDateTime completedAt
) implements DomainEvent {

    @Override
    public LocalDateTime occurredAt() {
        return completedAt;
    }
}
```

### 2.2. CertificatesValidatedEvent
**위치**: `certificatevalidation/domain/event/CertificatesValidatedEvent.java`

```java
public record CertificatesValidatedEvent(
    UUID uploadId,
    List<UUID> validatedCertificateIds,
    List<UUID> validatedCrlIds,
    int validCount,
    int invalidCount,
    LocalDateTime completedAt
) implements DomainEvent {

    @Override
    public LocalDateTime occurredAt() {
        return completedAt;
    }
}
```

### 2.3. LdapUploadCompletedEvent
**위치**: `ldapintegration/domain/event/LdapUploadCompletedEvent.java`

```java
public record LdapUploadCompletedEvent(
    UUID uploadId,
    int uploadedCertificateCount,
    int uploadedCrlCount,
    int failedCount,
    LocalDateTime completedAt
) implements DomainEvent {

    @Override
    public LocalDateTime occurredAt() {
        return completedAt;
    }
}
```

### 예상 결과물
- 3개 Domain Event 클래스
- 약 150 lines

---

## Task 3: Use Cases 연결 (Week 1, Day 4-5)

### 3.1. ParseLdifFileUseCase 개선
**기능**: FileParsingCompletedEvent 발행 추가

```java
@Transactional
public ParseLdifFileResponse execute(ParseLdifFileCommand command) {
    // 기존 파싱 로직...

    // Event 발행
    eventPublisher.publishEvent(new FileParsingCompletedEvent(
        command.uploadId(),
        command.fileName(),
        extractedCertificateIds,
        extractedCrlIds,
        totalCertificates,
        totalCrls,
        LocalDateTime.now()
    ));

    return response;
}
```

### 3.2. ValidateCertificatesUseCase (NEW)
**위치**: `certificatevalidation/application/usecase/ValidateCertificatesUseCase.java`

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class ValidateCertificatesUseCase {

    private final CertificateRepository certificateRepository;
    private final TrustChainValidator trustChainValidator;
    private final ProgressService progressService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ValidateCertificatesResponse execute(ValidateCertificatesCommand command) {
        UUID uploadId = command.uploadId();
        List<UUID> certificateIds = command.certificateIds();

        int validCount = 0;
        int invalidCount = 0;
        List<UUID> validatedIds = new ArrayList<>();

        for (int i = 0; i < certificateIds.size(); i++) {
            UUID certId = certificateIds.get(i);

            // SSE 진행률 업데이트
            progressService.sendProgress(
                ProcessingProgress.validationInProgress(
                    uploadId,
                    i + 1,
                    certificateIds.size(),
                    "인증서 검증 중"
                )
            );

            // 검증 로직
            Certificate cert = certificateRepository.findById(CertificateId.of(certId.toString()))
                .orElseThrow();

            boolean isValid = trustChainValidator.validate(cert);

            if (isValid) {
                validCount++;
                validatedIds.add(certId);
            } else {
                invalidCount++;
            }
        }

        // SSE 완료
        progressService.sendProgress(
            ProcessingProgress.validationCompleted(uploadId, "인증서 검증 완료")
        );

        // Event 발행
        eventPublisher.publishEvent(new CertificatesValidatedEvent(
            uploadId,
            validatedIds,
            command.crlIds(),
            validCount,
            invalidCount,
            LocalDateTime.now()
        ));

        return new ValidateCertificatesResponse(validCount, invalidCount);
    }
}
```

### 3.3. UploadToLdapUseCase (NEW)
**위치**: `ldapintegration/application/usecase/UploadToLdapUseCase.java`

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class UploadToLdapUseCase {

    private final SpringLdapUploadAdapter ldapUploadAdapter;
    private final CertificateRepository certificateRepository;
    private final ProgressService progressService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UploadToLdapResponse execute(UploadToLdapCommand command) {
        UUID uploadId = command.uploadId();
        List<UUID> certificateIds = command.certificateIds();

        int uploadedCertCount = 0;
        int failedCount = 0;

        for (int i = 0; i < certificateIds.size(); i++) {
            UUID certId = certificateIds.get(i);

            // SSE 진행률 업데이트
            progressService.sendProgress(
                ProcessingProgress.ldapSavingInProgress(
                    uploadId,
                    i + 1,
                    certificateIds.size(),
                    "LDAP 업로드 중"
                )
            );

            // LDAP 업로드
            Certificate cert = certificateRepository.findById(CertificateId.of(certId.toString()))
                .orElseThrow();

            try {
                ldapUploadAdapter.uploadCertificate(cert);
                uploadedCertCount++;
            } catch (Exception e) {
                log.error("Failed to upload certificate {}: {}", certId, e.getMessage());
                failedCount++;
            }
        }

        // SSE 완료
        progressService.sendProgress(
            ProcessingProgress.ldapSavingCompleted(uploadId, "LDAP 업로드 완료")
        );

        // Event 발행
        eventPublisher.publishEvent(new LdapUploadCompletedEvent(
            uploadId,
            uploadedCertCount,
            command.crlIds().size(),
            failedCount,
            LocalDateTime.now()
        ));

        return new UploadToLdapResponse(uploadedCertCount, failedCount);
    }
}
```

### 예상 결과물
- 2개 새로운 Use Cases
- 기존 Use Case 개선
- Commands & Responses
- 약 600 lines

---

## Task 4: End-to-End Integration Tests (Week 2, Day 1-3)

### 4.1. FileUploadToLdapE2ETest
**위치**: `test/.../integration/FileUploadToLdapE2ETest.java`

```java
@SpringBootTest
@AutoConfigureEmbeddedLdap
@Slf4j
class FileUploadToLdapE2ETest {

    @Autowired
    private UploadLdifFileUseCase uploadLdifFileUseCase;

    @Autowired
    private LdapTemplate ldapTemplate;

    @Autowired
    private ProgressService progressService;

    @Test
    @DisplayName("E2E: LDIF 파일 업로드부터 LDAP 저장까지 전체 워크플로우")
    void testCompleteWorkflow() throws Exception {
        // Given: LDIF 파일 준비
        byte[] ldifContent = loadTestLdifFile();
        String fileHash = calculateHash(ldifContent);

        UploadLdifFileCommand command = UploadLdifFileCommand.builder()
            .fileName("test-002-complete.ldif")
            .fileContent(ldifContent)
            .fileSize((long) ldifContent.length)
            .fileHash(fileHash)
            .forceUpload(false)
            .build();

        // When: 파일 업로드 (전체 워크플로우 트리거)
        UploadFileResponse response = uploadLdifFileUseCase.execute(command);
        UUID uploadId = response.uploadId();

        // Then: SSE 진행 상황 확인
        await()
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .until(() -> {
                Optional<ProcessingProgress> progress = progressService.getProgress(uploadId);
                return progress.isPresent() &&
                       progress.get().stage() == ProcessingStage.COMPLETED;
            });

        // Then: LDAP에 인증서 저장 확인
        List<LdapEntry> entries = ldapTemplate.search(
            query().where("objectClass").is("pkiCA"),
            (Attributes attrs) -> new LdapEntry(attrs)
        );

        assertThat(entries).isNotEmpty();
        assertThat(entries.size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("E2E: 중복 파일 업로드 시 워크플로우 차단")
    void testDuplicateFileBlocksWorkflow() {
        // 동일 파일 2번 업로드 시도
        // 두 번째는 DomainException 발생 확인
    }

    @Test
    @DisplayName("E2E: 파싱 실패 시 워크플로우 중단")
    void testParsingFailureStopsWorkflow() {
        // 잘못된 LDIF 파일 업로드
        // PARSING_FAILED 상태 확인
        // 후속 단계 실행되지 않음 확인
    }

    @Test
    @DisplayName("E2E: 검증 실패한 인증서는 LDAP 업로드 제외")
    void testInvalidCertificatesNotUploadedToLdap() {
        // Trust chain 검증 실패 인증서 포함 파일 업로드
        // validCount < totalCount 확인
        // LDAP에 유효한 인증서만 저장 확인
    }
}
```

### 4.2. SSE Progress Tracking E2E Test
**위치**: `test/.../integration/SseProgressTrackingE2ETest.java`

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureEmbeddedLdap
class SseProgressTrackingE2ETest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("E2E: SSE로 전체 워크플로우 진행률 추적")
    void testSseProgressTracking() throws Exception {
        // SSE 연결
        EventSource eventSource = new EventSource.Builder(
            new URI("http://localhost:" + port + "/progress/stream")
        ).build();

        List<ProcessingProgress> receivedProgress = new CopyOnWriteArrayList<>();

        eventSource.addEventListener("progress", event -> {
            ProcessingProgress progress = parseProgress(event.getData());
            receivedProgress.add(progress);
        });

        eventSource.start();

        // 파일 업로드 (워크플로우 시작)
        uploadTestFile();

        // SSE 이벤트 대기
        await()
            .atMost(30, TimeUnit.SECONDS)
            .until(() -> receivedProgress.stream()
                .anyMatch(p -> p.stage() == ProcessingStage.COMPLETED));

        // 모든 단계의 진행률 수신 확인
        assertThat(receivedProgress).extracting(ProcessingProgress::stage)
            .containsSequence(
                ProcessingStage.UPLOAD_COMPLETED,
                ProcessingStage.PARSING_STARTED,
                ProcessingStage.PARSING_IN_PROGRESS,
                ProcessingStage.PARSING_COMPLETED,
                ProcessingStage.VALIDATION_STARTED,
                ProcessingStage.VALIDATION_IN_PROGRESS,
                ProcessingStage.VALIDATION_COMPLETED,
                ProcessingStage.LDAP_SAVING_STARTED,
                ProcessingStage.LDAP_SAVING_IN_PROGRESS,
                ProcessingStage.LDAP_SAVING_COMPLETED,
                ProcessingStage.COMPLETED
            );

        eventSource.close();
    }
}
```

### 예상 결과물
- 2개 E2E Test 클래스
- 10개 통합 테스트 케이스
- 약 800 lines

---

## Task 5: Configuration & Documentation (Week 2, Day 4-5)

### 5.1. Async Configuration 최적화
**위치**: `config/AsyncConfiguration.java`

```java
@Configuration
@EnableAsync
public class AsyncConfiguration implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-event-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("Async method {} threw exception: {}",
                method.getName(), ex.getMessage(), ex);
        };
    }
}
```

### 5.2. Event Publishing Strategy
**위치**: `config/EventConfiguration.java`

```java
@Configuration
public class EventConfiguration {

    @Bean
    public ApplicationEventMulticaster applicationEventMulticaster(
            @Qualifier("asyncEventExecutor") Executor executor) {
        SimpleApplicationEventMulticaster eventMulticaster =
            new SimpleApplicationEventMulticaster();
        eventMulticaster.setTaskExecutor(executor);
        return eventMulticaster;
    }

    @Bean("asyncEventExecutor")
    public Executor asyncEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("event-");
        executor.initialize();
        return executor;
    }
}
```

### 5.3. Documentation
- **PHASE_16_COMPLETE.md**: Phase 16 완료 리포트
- **E2E_WORKFLOW.md**: End-to-End 워크플로우 가이드
- **EVENT_FLOW.md**: 이벤트 흐름 다이어그램
- **TROUBLESHOOTING.md**: 트러블슈팅 가이드

### 예상 결과물
- 2개 Configuration 클래스
- 4개 Documentation 파일
- 약 1,000 lines (docs 포함)

---

## 전체 예상 결과물

| Task | 파일 수 | 예상 LOC | 기간 |
|------|---------|----------|------|
| Task 1: Event Handlers | 4 | 400 | Day 1-3 |
| Task 2: Domain Events | 3 | 150 | Day 3-4 |
| Task 3: Use Cases | 5 | 600 | Day 4-5 |
| Task 4: E2E Tests | 2 | 800 | Week 2 Day 1-3 |
| Task 5: Config & Docs | 6 | 1,000 | Week 2 Day 4-5 |
| **Total** | **20** | **~3,000** | **10 days** |

---

## 성공 기준

### 기능적 성공 기준
- ✅ 파일 업로드 시 자동으로 전체 워크플로우 실행
- ✅ 각 단계별 SSE 진행률 업데이트
- ✅ 오류 발생 시 워크플로우 중단 및 사용자 알림
- ✅ LDAP에 검증된 인증서/CRL만 업로드
- ✅ 모든 E2E 테스트 통과

### 비기능적 성공 기준
- ✅ 전체 워크플로우 처리 시간 < 1분 (100MB LDIF 기준)
- ✅ SSE 진행률 업데이트 지연 < 500ms
- ✅ 동시 업로드 지원 (최대 5개 파일)
- ✅ 메모리 사용량 < 2GB
- ✅ 오류 복구 가능 (재시도 메커니즘)

---

## 알려진 제약사항

1. **Domain Integration Pending**
   - Phase 15 Task 3에서 stubbed된 domain 통합 필요
   - `convertCertificateToLdapEntry()` 메서드 구현 필요

2. **LDAP Schema**
   - 실제 ICAO PKD LDAP 스키마 확인 필요
   - DN 구조, objectClass 정의 필요

3. **Performance Optimization**
   - 대용량 파일(100MB+) 파싱 최적화 필요
   - Batch LDAP upload 성능 개선 필요

---

## Next Steps (Phase 17)

Phase 16 완료 후:
- **Phase 17**: Production Deployment & Monitoring
  - Docker/Podman 배포
  - 모니터링 대시보드 (Prometheus + Grafana)
  - 로깅 개선 (ELK Stack)
  - Health checks & Metrics

---

**Document Version**: 1.0
**Created**: 2025-10-29
**Status**: Phase 16 계획 수립 완료
