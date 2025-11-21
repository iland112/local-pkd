# Phase 9: SSE (Server-Sent Events) for Real-time Processing Progress

**완료 날짜**: 2025-10-22
**소요 시간**: 4시간
**빌드 상태**: ✅ SUCCESS (73 source files)

---

## 📋 목차

1. [개요](#개요)
2. [구현 배경](#구현-배경)
3. [아키텍처](#아키텍처)
4. [구현 상세](#구현-상세)
5. [API 명세](#api-명세)
6. [프론트엔드 통합](#프론트엔드-통합)
7. [사용 가이드](#사용-가이드)
8. [테스트](#테스트)
9. [문제 해결](#문제-해결)

---

## 개요

Phase 9에서는 **파일 업로드 후 처리 과정(파싱, 검증, LDAP 저장)의 진행 상황을 실시간으로 클라이언트에 전송**하는 SSE (Server-Sent Events) 시스템을 구현했습니다.

### 핵심 목표

1. ✅ 파일 파싱 진행률 실시간 표시
2. ✅ 인증서 검증 진행 상황 표시
3. ✅ LDAP 저장 진행 상황 표시
4. ✅ DDD 패턴 준수 (Domain-Driven Design)
5. ✅ Spring MVC 호환 (WebFlux 대신 SseEmitter 사용)

### 왜 SSE인가?

- **파일 업로드 자체는 빠름** (~1초) → SSE 불필요
- **시간이 오래 걸리는 부분**:
  - LDIF 파일 파싱 (대용량: 수십 초 ~ 수분)
  - 인증서 검증 (CSCA, DSC, CRL Trust Chain)
  - LDAP 서버에 수백~수천 개 인증서 저장

→ **사용자는 진행 상황을 실시간으로 확인하고 싶어 함!**

---

## 구현 배경

### 기존 문제점

1. **블랙박스 처리**: 업로드 후 "처리 중..." 만 표시
2. **불안감**: 사용자가 진행 여부를 알 수 없음
3. **디버깅 어려움**: 어느 단계에서 멈췄는지 파악 불가

### Phase 9 해결책

1. **실시간 진행 상황**: SSE로 0~100% 진행률 표시
2. **상세 정보**: 현재 처리 중인 파일/인증서 표시
3. **에러 추적**: 실패 시점 및 원인 즉시 표시

---

## 아키텍처

### 전체 흐름도

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client (Browser)                         │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  upload-ldif.html / upload-ml.html                       │   │
│  │                                                           │   │
│  │  ┌────────────────────────────────────────────────┐     │   │
│  │  │  Progress Modal (DaisyUI)                      │     │   │
│  │  │  - Progress Bar (0-100%)                       │     │   │
│  │  │  - Current Stage Text                          │     │   │
│  │  │  - Processed Count (X/Y)                       │     │   │
│  │  │  - Details (Current Item)                      │     │   │
│  │  │  - Error Message (if failed)                   │     │   │
│  │  └────────────────────────────────────────────────┘     │   │
│  │                                                           │   │
│  │  JavaScript:                                             │   │
│  │  - EventSource('/progress/stream')                      │   │
│  │  - addEventListener('progress', updateUI)               │   │
│  │  - Auto-reconnect on error                              │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────┬───────────────────────────────────────┘
                          │ SSE Connection
                          │ (text/event-stream)
┌─────────────────────────▼───────────────────────────────────────┐
│                      Server (Spring Boot)                        │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  ProgressController (Infrastructure Layer)              │   │
│  │  GET /progress/stream → SseEmitter                      │   │
│  │  GET /progress/status/{uploadId} → JSON                 │   │
│  │  GET /progress/connections → Admin Info                 │   │
│  └────────────────────┬─────────────────────────────────────┘   │
│                       │                                          │
│  ┌────────────────────▼─────────────────────────────────────┐   │
│  │  ProgressService (Application Layer)                    │   │
│  │  - createEmitter(): Register SSE connection             │   │
│  │  - sendProgress(ProcessingProgress): Broadcast          │   │
│  │  - getRecentProgress(uploadId): Query cache             │   │
│  │  - sendHeartbeat(): Keep-alive (every 30s)              │   │
│  └────────────────────┬─────────────────────────────────────┘   │
│                       │                                          │
│  ┌────────────────────▼─────────────────────────────────────┐   │
│  │  ProcessingProgress (Domain Layer)                       │   │
│  │  - Value Object (Immutable)                              │   │
│  │  - Static Factory Methods:                               │   │
│  │    • uploadCompleted()                                   │   │
│  │    • parsingInProgress(current, total, item)            │   │
│  │    • validationInProgress(...)                           │   │
│  │    • ldapSavingInProgress(...)                           │   │
│  │    • completed() / failed(error)                         │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  ProcessingStage (Domain Layer)                          │   │
│  │  Enum: 12 stages                                         │   │
│  │  - UPLOAD_COMPLETED (5%)                                 │   │
│  │  - PARSING_STARTED (10%)                                 │   │
│  │  - PARSING_IN_PROGRESS (20-50%)                          │   │
│  │  - PARSING_COMPLETED (60%)                               │   │
│  │  - VALIDATION_STARTED (65%)                              │   │
│  │  - VALIDATION_IN_PROGRESS (70-80%)                       │   │
│  │  - VALIDATION_COMPLETED (85%)                            │   │
│  │  - LDAP_SAVING_STARTED (90%)                             │   │
│  │  - LDAP_SAVING_IN_PROGRESS (92-98%)                      │   │
│  │  - LDAP_SAVING_COMPLETED (100%)                          │   │
│  │  - COMPLETED (100%)                                      │   │
│  │  - FAILED (0%)                                           │   │
│  └──────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────────┘
```

### 레이어 구조 (DDD)

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│  - upload-ldif.html (SSE EventSource + Progress Modal)      │
│  - upload-ml.html (SSE EventSource + Progress Modal)        │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                 Infrastructure Layer                         │
│  - ProgressController (REST API, SSE endpoint)              │
│  - SchedulingConfig (Heartbeat scheduler)                   │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                  Application Layer                           │
│  - ProgressService (SSE 연결 관리, 브로드캐스트)              │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                    Domain Layer                              │
│  - ProcessingProgress (Value Object)                         │
│  - ProcessingStage (Enum)                                    │
└─────────────────────────────────────────────────────────────┘
```

---

## 구현 상세

### 1. Domain Layer

#### ProcessingStage.java

**위치**: `src/main/java/com/smartcoreinc/localpkd/shared/progress/ProcessingStage.java`

**목적**: 파일 처리 단계를 타입 안전하게 정의

```java
@Getter
public enum ProcessingStage {
    UPLOAD_COMPLETED("파일 업로드 완료", 5, StageCategory.UPLOAD),
    PARSING_STARTED("파일 파싱 시작", 10, StageCategory.PARSING),
    PARSING_IN_PROGRESS("파일 파싱 중", 30, StageCategory.PARSING),
    PARSING_COMPLETED("파일 파싱 완료", 60, StageCategory.PARSING),
    VALIDATION_STARTED("인증서 검증 시작", 65, StageCategory.VALIDATION),
    VALIDATION_IN_PROGRESS("인증서 검증 중", 75, StageCategory.VALIDATION),
    VALIDATION_COMPLETED("인증서 검증 완료", 85, StageCategory.VALIDATION),
    LDAP_SAVING_STARTED("LDAP 저장 시작", 90, StageCategory.LDAP_SAVE),
    LDAP_SAVING_IN_PROGRESS("LDAP 저장 중", 95, StageCategory.LDAP_SAVE),
    LDAP_SAVING_COMPLETED("LDAP 저장 완료", 100, StageCategory.LDAP_SAVE),
    COMPLETED("처리 완료", 100, StageCategory.COMPLETE),
    FAILED("처리 실패", 0, StageCategory.FAILED);

    private final String displayName;
    private final int basePercentage;
    private final StageCategory category;
}
```

**핵심 기능**:
- 각 단계별 기본 진행률 제공
- 카테고리별 그룹화 (PARSING, VALIDATION, LDAP_SAVE)
- 한글 표시명 포함

#### ProcessingProgress.java

**위치**: `src/main/java/com/smartcoreinc/localpkd/shared/progress/ProcessingProgress.java`

**목적**: 진행 상황을 나타내는 불변 Value Object

```java
@Getter
@Builder
public class ProcessingProgress {
    private final UUID uploadId;              // 파일 식별자
    private final ProcessingStage stage;      // 현재 단계
    private final int percentage;             // 진행률 (0-100)
    private final int processedCount;         // 처리된 항목 수
    private final int totalCount;             // 전체 항목 수
    private final String message;             // 상태 메시지
    private final String errorMessage;        // 에러 메시지 (실패 시)
    private final String details;             // 상세 정보 (현재 파일명 등)
    private final LocalDateTime updatedAt;    // 업데이트 시간
}
```

**Static Factory Methods**:
```java
// 파싱 진행 중
ProcessingProgress.parsingInProgress(uploadId, 50, 100, "CSCA 인증서 파싱 중...");

// 검증 진행 중
ProcessingProgress.validationInProgress(uploadId, 30, 100, "DSC 인증서 검증 중...");

// LDAP 저장 진행 중
ProcessingProgress.ldapSavingInProgress(uploadId, 200, 500, "cn=KOR001,ou=CSCA");

// 완료
ProcessingProgress.completed(uploadId, 500);

// 실패
ProcessingProgress.failed(uploadId, ProcessingStage.PARSING_IN_PROGRESS, "파싱 오류 발생");
```

**JSON 변환**:
```java
public String toJson() {
    return String.format(
        "{\"uploadId\":\"%s\",\"stage\":\"%s\",\"percentage\":%d,...}",
        uploadId, stage.name(), percentage, ...
    );
}
```

---

### 2. Application Layer

#### ProgressService.java

**위치**: `src/main/java/com/smartcoreinc/localpkd/shared/progress/ProgressService.java`

**목적**: SSE 연결 관리 및 진행 상황 브로드캐스트

**핵심 메서드**:

```java
@Service
public class ProgressService {
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Map<UUID, ProcessingProgress> recentProgressCache = new ConcurrentHashMap<>();

    /**
     * SSE 연결 생성
     */
    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5분 타임아웃

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> { emitters.remove(emitter); emitter.complete(); });
        emitter.onError((ex) -> emitters.remove(emitter));

        emitters.add(emitter);

        // 연결 확인 이벤트 전송
        emitter.send(SseEmitter.event()
            .name("connected")
            .data("{\"message\":\"SSE connection established\"}"));

        return emitter;
    }

    /**
     * 진행 상황 브로드캐스트 (모든 클라이언트에 전송)
     */
    public void sendProgress(ProcessingProgress progress) {
        // 캐시에 저장
        recentProgressCache.put(progress.getUploadId(), progress);

        // 모든 연결된 클라이언트에 전송
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(progress.toJson()));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    /**
     * 하트비트 전송 (연결 유지)
     */
    public void sendHeartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("heartbeat")
                    .data("{\"timestamp\":" + System.currentTimeMillis() + "}"));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}
```

**특징**:
- **Thread-safe**: `CopyOnWriteArrayList`, `ConcurrentHashMap` 사용
- **자동 정리**: 연결 종료 시 자동으로 emitter 제거
- **캐싱**: 최근 진행 상황 저장 (나중에 연결한 클라이언트도 조회 가능)
- **타임아웃**: 5분 후 자동 종료

---

### 3. Infrastructure Layer

#### ProgressController.java

**위치**: `src/main/java/com/smartcoreinc/localpkd/controller/ProgressController.java`

**목적**: SSE 엔드포인트 제공

**API Endpoints**:

##### 1. SSE 스트림 연결

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamProgress() {
    return progressService.createEmitter();
}
```

**Response**:
```
Content-Type: text/event-stream

event: connected
data: {"message":"SSE connection established"}

event: progress
data: {"uploadId":"123e4567-e89b-12d3-a456-426614174000","stage":"PARSING_IN_PROGRESS","percentage":35,...}

event: heartbeat
data: {"timestamp":1729603200000}
```

##### 2. 진행 상황 조회

```java
@GetMapping("/status/{uploadId}")
public ResponseEntity<Map<String, Object>> getProgressStatus(@PathVariable UUID uploadId) {
    ProcessingProgress progress = progressService.getRecentProgress(uploadId);
    // ... JSON 응답 생성
}
```

**Response Example**:
```json
{
  "exists": true,
  "uploadId": "123e4567-e89b-12d3-a456-426614174000",
  "stage": "PARSING_IN_PROGRESS",
  "stageName": "파일 파싱 중",
  "percentage": 35,
  "processedCount": 50,
  "totalCount": 100,
  "message": "파일 파싱 중 (50/100)",
  "details": "CSCA 인증서 파싱 중...",
  "isCompleted": false,
  "isFailed": false
}
```

##### 3. 활성 연결 수 조회 (관리용)

```java
@GetMapping("/connections")
public ResponseEntity<Map<String, Object>> getConnections() {
    return ResponseEntity.ok(Map.of(
        "activeConnections", progressService.getActiveConnectionCount(),
        "cachedProgressCount", progressService.getAllRecentProgress().size()
    ));
}
```

#### SchedulingConfig.java

**위치**: `src/main/java/com/smartcoreinc/localpkd/config/SchedulingConfig.java`

**목적**: 주기적인 하트비트 전송

```java
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SchedulingConfig {
    private final ProgressService progressService;

    @Scheduled(fixedRate = 30000) // 30초마다
    public void sendSseHeartbeat() {
        int activeConnections = progressService.getActiveConnectionCount();
        if (activeConnections > 0) {
            progressService.sendHeartbeat();
        }
    }
}
```

---

### 4. Frontend Integration

#### Progress Modal (DaisyUI)

**HTML 구조**:

```html
<dialog id="progressModal" class="modal">
  <div class="modal-box max-w-2xl">
    <h3 class="font-bold text-lg text-primary mb-4">
      <i class="fas fa-spinner fa-spin mr-2"></i>
      파일 처리 중
    </h3>

    <!-- Progress Bar -->
    <div class="w-full bg-base-300 rounded-full h-6">
      <div id="progressBar" class="bg-primary h-full text-white" style="width: 0%">
        0%
      </div>
    </div>

    <!-- Message -->
    <div class="alert alert-info mb-4">
      <span id="progressMessage">처리 중...</span>
    </div>

    <!-- Processed Count -->
    <span id="progressCount">50 / 100</span>

    <!-- Details -->
    <div id="progressDetails">CSCA 인증서 파싱 중...</div>

    <!-- Error -->
    <div id="progressError" class="alert alert-error hidden">...</div>

    <!-- Processing Steps -->
    <ul class="steps steps-vertical">
      <li class="step step-primary">파일 업로드 완료</li>
      <li class="step">LDIF 파싱</li>
      <li class="step">인증서 검증</li>
      <li class="step">LDAP 서버 저장</li>
      <li class="step">처리 완료</li>
    </ul>
  </div>
</dialog>
```

#### JavaScript (EventSource)

```javascript
let sseEventSource = null;

/**
 * SSE 연결 시작
 */
function startSSEProgress(uploadId) {
  currentUploadId = uploadId;

  // Modal 표시
  document.getElementById('progressModal').showModal();

  // SSE 연결
  sseEventSource = new EventSource('/progress/stream');

  // 연결 성공
  sseEventSource.addEventListener('connected', function(e) {
    console.log('SSE connected:', e.data);
  });

  // 진행 상황 업데이트
  sseEventSource.addEventListener('progress', function(e) {
    const progress = JSON.parse(e.data);

    if (progress.uploadId === currentUploadId) {
      updateProgressUI(progress);

      // 완료 시 리다이렉트
      if (progress.stage === 'COMPLETED') {
        setTimeout(() => {
          window.location.href = '/upload-history?id=' + currentUploadId;
        }, 2000);
      }
    }
  });

  // 하트비트
  sseEventSource.addEventListener('heartbeat', function(e) {
    console.debug('Heartbeat:', e.data);
  });

  // 에러 처리 (자동 재연결)
  sseEventSource.onerror = function(error) {
    console.error('SSE error:', error);
    setTimeout(() => {
      if (sseEventSource.readyState === EventSource.CLOSED) {
        startSSEProgress(currentUploadId);
      }
    }, 3000);
  };
}

/**
 * UI 업데이트
 */
function updateProgressUI(progress) {
  // Progress Bar
  const progressBar = document.getElementById('progressBar');
  progressBar.style.width = progress.percentage + '%';
  progressBar.textContent = progress.percentage + '%';

  // Message
  document.getElementById('progressMessage').textContent = progress.message;

  // Count
  if (progress.totalCount > 0) {
    document.getElementById('progressCount').textContent =
      `${progress.processedCount} / ${progress.totalCount}`;
  }

  // Color
  if (progress.stage === 'COMPLETED') {
    progressBar.classList.add('bg-success');
  } else if (progress.stage === 'FAILED') {
    progressBar.classList.add('bg-error');
  }
}
```

---

## API 명세

### Base URL

```
http://localhost:8081/progress
```

### Endpoints

| Method | Path | Description | Response Type |
|--------|------|-------------|---------------|
| GET | `/stream` | SSE 스트림 연결 | text/event-stream |
| GET | `/status/{uploadId}` | 진행 상황 조회 | application/json |
| GET | `/connections` | 활성 연결 수 조회 | application/json |
| GET | `/heartbeat` | 하트비트 테스트 | application/json |

### SSE Event Types

| Event Name | Description | Data Format |
|------------|-------------|-------------|
| `connected` | 연결 성공 | `{"message":"SSE connection established"}` |
| `progress` | 진행 상황 업데이트 | ProcessingProgress JSON |
| `heartbeat` | 연결 유지 (30초마다) | `{"timestamp":1729603200000}` |

---

## 프론트엔드 통합

### 파일 업로드 후 SSE 시작

```javascript
// 1. 파일 업로드 (FormData)
const formData = new FormData();
formData.append('file', selectedFile);
formData.append('fileHash', calculatedHash);

// 2. 업로드 요청
const response = await fetch('/ldif/upload', {
  method: 'POST',
  body: formData
});

// 3. 성공 시 uploadId 추출
if (response.ok) {
  const uploadId = extractUploadIdFromResponse(response);

  // 4. SSE Progress 시작
  startSSEProgress(uploadId);
}
```

### DaisyUI Modal 통합

```html
<!-- 업로드 버튼 -->
<button onclick="handleUpload()" class="btn btn-primary">
  <i class="fas fa-upload"></i>
  업로드
</button>

<!-- Progress Modal (자동 표시) -->
<dialog id="progressModal" class="modal">
  <!-- Progress UI -->
</dialog>
```

---

## 사용 가이드

### Backend - 진행 상황 전송

#### 1. ProgressService 주입

```java
@Service
@RequiredArgsConstructor
public class LdifParserService {
    private final ProgressService progressService;
}
```

#### 2. 파싱 시작

```java
UUID uploadId = ...; // UploadedFile.getId().getId()

progressService.sendProgress(
    ProcessingProgress.parsingStarted(uploadId, fileName)
);
```

#### 3. 파싱 진행 중 (루프 내)

```java
List<Certificate> certificates = ...;
int totalCount = certificates.size();

for (int i = 0; i < certificates.size(); i++) {
    Certificate cert = certificates.get(i);

    // 인증서 파싱
    parseCertificate(cert);

    // 진행 상황 전송 (10개마다)
    if (i % 10 == 0) {
        progressService.sendProgress(
            ProcessingProgress.parsingInProgress(
                uploadId,
                i + 1,
                totalCount,
                "처리 중: " + cert.getSubjectDN()
            )
        );
    }
}
```

#### 4. 파싱 완료

```java
progressService.sendProgress(
    ProcessingProgress.parsingCompleted(uploadId, totalCount)
);
```

#### 5. 검증 단계 (동일 패턴)

```java
// 검증 시작
progressService.sendProgress(
    ProcessingProgress.validationStarted(uploadId, totalCount)
);

// 검증 진행 중
for (int i = 0; i < certificates.size(); i++) {
    validateCertificate(certificates.get(i));

    if (i % 5 == 0) {
        progressService.sendProgress(
            ProcessingProgress.validationInProgress(
                uploadId, i + 1, totalCount, "검증 중..."
            )
        );
    }
}

// 검증 완료
progressService.sendProgress(
    ProcessingProgress.validationCompleted(uploadId, totalCount)
);
```

#### 6. LDAP 저장 (동일 패턴)

```java
progressService.sendProgress(
    ProcessingProgress.ldapSavingStarted(uploadId, totalCount)
);

for (int i = 0; i < entries.size(); i++) {
    ldapTemplate.bind(entries.get(i));

    if (i % 20 == 0) {
        progressService.sendProgress(
            ProcessingProgress.ldapSavingInProgress(
                uploadId, i + 1, totalCount, "저장 중..."
            )
        );
    }
}

progressService.sendProgress(
    ProcessingProgress.ldapSavingCompleted(uploadId, totalCount)
);
```

#### 7. 완료

```java
progressService.sendProgress(
    ProcessingProgress.completed(uploadId, totalCount)
);
```

#### 8. 실패 처리

```java
try {
    // 처리 로직
} catch (Exception e) {
    progressService.sendProgress(
        ProcessingProgress.failed(
            uploadId,
            ProcessingStage.PARSING_IN_PROGRESS,
            "파싱 오류: " + e.getMessage()
        )
    );
}
```

---

## 테스트

### 1. SSE 연결 테스트

```bash
# curl로 SSE 스트림 확인
curl -N http://localhost:8081/progress/stream
```

**예상 출력**:
```
event: connected
data: {"message":"SSE connection established"}

event: heartbeat
data: {"timestamp":1729603200000}
```

### 2. 진행 상황 조회 테스트

```bash
# 특정 uploadId의 상태 조회
curl http://localhost:8081/progress/status/123e4567-e89b-12d3-a456-426614174000
```

### 3. 연결 수 확인

```bash
curl http://localhost:8081/progress/connections
```

**예상 출력**:
```json
{
  "activeConnections": 2,
  "cachedProgressCount": 5,
  "cachedUploadIds": ["123e4567-...", "234f5678-..."]
}
```

### 4. 브라우저 테스트

1. `/ldif/upload` 페이지 열기
2. LDIF 파일 선택
3. "업로드" 버튼 클릭
4. Progress Modal이 자동으로 표시됨
5. 진행률 바가 실시간으로 업데이트됨
6. 완료 시 자동으로 `/upload-history`로 리다이렉트

### 5. 동시 접속 테스트

```javascript
// Browser Console에서 실행
for (let i = 0; i < 5; i++) {
  const es = new EventSource('/progress/stream');
  es.onmessage = (e) => console.log(`Connection ${i}:`, e.data);
}
```

---

## 문제 해결

### 1. SSE 연결이 끊어짐

**증상**: 몇 초 후 EventSource가 `readyState=2` (CLOSED)

**원인**:
- 프록시/로드밸런서 타임아웃
- 방화벽이 긴 연결 차단

**해결책**:
```javascript
// 자동 재연결 로직 (이미 구현됨)
sseEventSource.onerror = function(error) {
  setTimeout(() => {
    if (sseEventSource.readyState === EventSource.CLOSED) {
      console.log('Reconnecting...');
      startSSEProgress(currentUploadId);
    }
  }, 3000);
};
```

### 2. 진행 상황이 표시되지 않음

**증상**: Modal은 열리지만 progress bar가 0%에서 멈춤

**원인**: `uploadId`가 일치하지 않음

**해결책**:
```javascript
// uploadId 로깅 추가
sseEventSource.addEventListener('progress', function(e) {
  const progress = JSON.parse(e.data);
  console.log('Received progress for:', progress.uploadId);
  console.log('Current uploadId:', currentUploadId);

  if (progress.uploadId === currentUploadId) {
    updateProgressUI(progress);
  }
});
```

### 3. 하트비트가 전송되지 않음

**증상**: 30초 후에도 heartbeat 이벤트 없음

**원인**: Spring Scheduling이 활성화되지 않음

**해결책**:
```java
// SchedulingConfig.java에 @EnableScheduling 확인
@Configuration
@EnableScheduling  // ← 이것이 있는지 확인
public class SchedulingConfig {
    // ...
}
```

### 4. 메모리 누수 (Emitter가 제거되지 않음)

**증상**: 시간이 지날수록 서버 메모리 증가

**원인**: onCompletion/onTimeout/onError 핸들러 누락

**해결책**:
```java
// ProgressService.java에서 이미 구현됨
emitter.onCompletion(() -> emitters.remove(emitter));
emitter.onTimeout(() -> { emitters.remove(emitter); emitter.complete(); });
emitter.onError((ex) -> emitters.remove(emitter));
```

### 5. CORS 오류 (다른 도메인에서 접속 시)

**증상**: `Access-Control-Allow-Origin` 오류

**해결책**:
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/progress/**")
            .allowedOrigins("http://localhost:3000")
            .allowedMethods("GET")
            .allowCredentials(true);
    }
}
```

---

## 성능 고려사항

### 1. 진행 상황 전송 빈도

**문제**: 너무 자주 전송하면 네트워크/CPU 부하

**권장**:
- **파싱**: 10개 항목마다 1회
- **검증**: 5개 항목마다 1회
- **LDAP 저장**: 20개 항목마다 1회

```java
// 좋은 예
if (i % 10 == 0) {
    progressService.sendProgress(...);
}

// 나쁜 예 (매번 전송)
progressService.sendProgress(...);  // 루프 내에서 매번
```

### 2. 캐시 크기 제한

**문제**: `recentProgressCache`가 무한정 증가

**해결책**: 완료/실패 후 10초 뒤 자동 제거 (이미 구현됨)

```java
private void scheduleProgressCacheRemoval(UUID uploadId) {
    new Thread(() -> {
        try {
            Thread.sleep(10_000);
            recentProgressCache.remove(uploadId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }).start();
}
```

### 3. 동시 연결 수 제한

**문제**: 수천 개의 SSE 연결 시 메모리 부족

**해결책**: 연결 수 제한 (선택적)

```java
public SseEmitter createEmitter() {
    if (emitters.size() >= MAX_CONNECTIONS) {
        throw new IllegalStateException("Too many connections");
    }
    // ...
}
```

---

## 다음 단계 (Phase 10 이후)

1. **Parser 통합**: LDIF Parser에서 `ProgressService.sendProgress()` 호출
2. **Validator 통합**: Certificate Validator에서 진행 상황 전송
3. **LDAP Service 통합**: LDAP 저장 시 진행 상황 전송
4. **WebSocket 고려**: 양방향 통신이 필요한 경우 WebSocket으로 전환
5. **Progress History**: 완료된 진행 상황을 DB에 저장 (감사 목적)

---

## 참고 자료

- **SSE (Server-Sent Events)**: https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events
- **Spring SseEmitter**: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/SseEmitter.html
- **EventSource API**: https://developer.mozilla.org/en-US/docs/Web/API/EventSource
- **DaisyUI Modal**: https://daisyui.com/components/modal/
- **DaisyUI Progress**: https://daisyui.com/components/progress/

---

**Document Version**: 1.0
**Last Updated**: 2025-10-22
**Author**: SmartCore Inc. (Claude AI Assistant)
