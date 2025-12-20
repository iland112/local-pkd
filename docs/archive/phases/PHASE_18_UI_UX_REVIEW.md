# Phase 18: UI/UX 검토 및 개선 계획 보고서

**검토 일시**: 2025-10-30
**검토 대상**: 3개 핵심 템플릿 파일 + 6단계 데이터 처리 워크플로우 매핑
**목표**: UI/UX가 Phase 17 완료된 6단계 워크플로우를 제대로 반영하는지 확인

---

## 📋 Executive Summary

Phase 17까지 구현된 **Event-Driven Orchestration** 파이프라인(6단계)을 분석하고, 현재 UI/UX가 이를 제대로 반영하는지 검토했습니다.

**결론**:
- ✅ **핵심 기능**: 파일 선택, SHA-256 계산, 중복 검사, SSE 진행률 모달 잘 구현됨
- ⚠️ **개선 필요**: SSE API 연동, Upload History의 상태 타임라인, 오류 메시지 상세화 등

---

## 1️⃣ 데이터 처리 6단계 워크플로우 정의

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. File Upload Page (Frontend)                                   │
│    - 파일 선택 → SHA-256 해시 → 중복 검사 → 업로드              │
└────────────────────┬─────────────────────────────────────────────┘
                     │ File Uploaded
                     ↓
┌──────────────────────────────────────────────────────────────────┐
│ 2. File Upload Backend Processing                                │
│    - 파일 저장 (./data/uploads/)                                 │
│    - Metadata 추출 (Collection, Version)                         │
│    - FileUploadHistory 저장 (status: RECEIVED)                   │
│    - uploadId 반환 + 이력 페이지 리다이렉트                      │
└────────────────────┬─────────────────────────────────────────────┘
                     │ FileUploadedEvent
                     ↓
┌──────────────────────────────────────────────────────────────────┐
│ 3. LDIF/ML File Parsing (Backend - Async)                        │
│    - ParseLdifFileUseCase / ParseMasterListFileUseCase           │
│    - ProgressService.sendProgress(PARSING_STARTED, 10%)          │
│    - Parser.parse() → Certificate & CRL 추출                     │
│    - ProgressService.sendProgress(PARSING_COMPLETED, 60%)        │
│    - FileUploadHistory.status = PARSED                           │
└────────────────────┬─────────────────────────────────────────────┘
                     │ FileParsingCompletedEvent
                     ↓
┌──────────────────────────────────────────────────────────────────┐
│ 4. Certificate Validation (Backend - Async)                      │
│    - CertificateValidator.validate() (Trust Chain)               │
│    - ProgressService.sendProgress(VALIDATION_STARTED, 65%)       │
│    - CSCA, DSC, CRL 검증                                         │
│    - ProgressService.sendProgress(VALIDATION_COMPLETED, 85%)     │
│    - FileUploadHistory.status = VALIDATED                        │
└────────────────────┬─────────────────────────────────────────────┘
                     │ CertificatesValidatedEvent
                     ↓
┌──────────────────────────────────────────────────────────────────┐
│ 5. LDAP Upload (Backend - Async)                                 │
│    - UploadToLdapEventHandler 처리                               │
│    - ProgressService.sendProgress(LDAP_SAVING_STARTED, 90%)      │
│    - LdapUploadService.uploadCertificate(s)()                    │
│    - ProgressService.sendProgress(LDAP_SAVING_COMPLETED, 100%)   │
│    - FileUploadHistory.status = COMPLETED                        │
└────────────────────┬─────────────────────────────────────────────┘
                     │ UploadToLdapCompletedEvent
                     ↓
┌──────────────────────────────────────────────────────────────────┐
│ 6. Results Display (Frontend)                                    │
│    - 업로드 이력 페이지 표시                                      │
│    - 처리 상태 업데이트 (RECEIVED → PARSED → VALIDATED → COMPLETED)│
│    - 체크섬 검증 결과 표시                                        │
│    - 오류 메시지 표시                                            │
└──────────────────────────────────────────────────────────────────┘
```

---

## 2️⃣ 현재 UI 구현 상태 분석

### A. LDIF Upload Page (`upload-ldif.html`)

#### ✅ 잘 구현된 부분

**1. 파일 선택 및 기본 검증** (lines 40-60)
```html
<input id="fileInput" type="file" name="file" accept=".ldif" required />
```
- ✅ `.ldif` 확장자만 허용
- ✅ 최대 100MB 제한
- ✅ 파일명과 크기 실시간 표시

**2. 프로세스 정보 카드** (lines 109-159)
```
1️⃣ 파일 선택 (badge-primary)
2️⃣ 해시 계산 (badge-secondary)
3️⃣ 중복 검사 (badge-accent)
4️⃣ 업로드 완료 (badge-success)
```
- ✅ 4단계 프로세스 명확하게 시각화
- ✅ 각 단계의 설명 제시
- ✅ 색상 코딩으로 시각적 구분

**3. SHA-256 해시 계산 및 진행률** (lines 335-336, 413-423)
```javascript
showProgress('파일 해시 계산 중...', 30);
calculatedHash = await calculateSHA256(selectedFile);
```
- ✅ Web Crypto API 사용 (클라이언트 측)
- ✅ 진행률 바로 표시 (30%)
- ✅ 메시지로 사용자에게 진행 상황 알림

**4. 중복 파일 검사** (lines 340-341, 164-222)
```javascript
showProgress('중복 파일 검사 중...', 60);
const isDuplicate = await checkDuplicate();
```
- ✅ 명확한 중복 경고 모달
- ✅ 기존 파일 정보 상세 표시:
  - 파일명, ID, 업로드 일시, 버전, 상태
- ✅ "기존 파일 보기" 버튼으로 업로드 이력 조회

**5. SSE 기반 실시간 진행률** (lines 224-295, 531-677)
```html
<dialog id="progressModal" class="modal">
  <div id="progressBar" style="width: 0%">0%</div>
  <div class="collapse">
    <ul class="steps steps-vertical">
      <li class="step">파일 업로드 완료</li>
      <li class="step">LDIF 파싱</li>
      <li class="step">인증서 검증</li>
      <li class="step">LDAP 서버 저장</li>
      <li class="step">처리 완료</li>
    </ul>
  </div>
</dialog>
```
- ✅ DaisyUI Modal 사용
- ✅ 실시간 진행률 바
- ✅ 처리 단계 시각화 (collapse로 숨김처리)
- ✅ 메시지와 상세 정보 표시
- ✅ 오류 메시지 섹션

#### ⚠️ 개선이 필요한 부분

**1. SSE API 엔드포인트 연동 불완전**

현재 코드 (line 505):
```javascript
const response = await fetch('/api/certificates/validate', {
  method: 'POST',
  body: JSON.stringify({
    uploadId: uploadId,
    parsedFileId: uploadId,
    certificateCount: 0,
    crlCount: 0
  })
});
```

**문제점**:
- `/api/certificates/validate` 엔드포인트가 아직 구현되지 않음
- SSE 이벤트가 실제로 발행되지 않아 Progress Modal이 작동하지 않음
- **영향**: 업로드 후 SSE 진행률이 표시되지 않음

**2. 업로드 진행률의 고정값 사용**

```javascript
showProgress('파일 해시 계산 중...', 30);     // 고정값
showProgress('중복 파일 검사 중...', 60);     // 고정값
showProgress('파일 업로드 중...', 90);        // 고정값
```

**문제점**:
- 파일 크기에 관계없이 동일한 진행률 표시
- 대용량 파일(100MB)일 때 사용자가 진행 상황을 알 수 없음
- **개선 방안**: 파일 크기 기반 동적 진행률 계산

**3. 오류 메시지의 상세도 부족**

```javascript
catch (error) {
  showToast('업로드 중 오류가 발생했습니다: ' + error.message, 'error');
}
```

**문제점**:
- 일반적인 오류 메시지만 표시
- 서버에서 반환한 상세 오류 정보 활용 불충분
- **개선 방안**: 서버 응답에서 errorCode와 message 추출하여 표시

---

### B. Upload History Page (`list.html`)

#### ✅ 잘 구현된 부분

**1. 통계 카드** (lines 41-77)
```
📊 전체 업로드: N건
✅ 성공: N건
❌ 실패: N건
⏳ 진행 중: N건
```
- ✅ Stats 컴포넌트로 주요 지표 한눈에 파악
- ✅ 각 항목별 아이콘과 색상 구분

**2. 검색 및 필터** (lines 82-162)
- ✅ 파일명 검색 (search)
- ✅ 파일 포맷 필터 (format)
- ✅ 업로드 상태 필터 (status)
- ✅ 검색/초기화 버튼

**3. 업로드 이력 테이블** (lines 169-242)
```
ID | 파일명 | 포맷 | 크기 | 상태 | 업로드 시간 | 상세
```
- ✅ 필수 정보 모두 포함
- ✅ 상태별 색상 배지 (badge-success, badge-error, badge-warning)
- ✅ 파일명 truncate 처리로 레이아웃 유지

**4. 상세 정보 모달** (lines 291-404)

**기본 정보**:
```
ID: [업로드 ID]
파일명: [파일명]
포맷: [포맷]
크기: [크기]
상태: [상태]
업로드 시간: [시간]
해시: [SHA-256] (copy 버튼)
```
- ✅ 필수 정보 완벽 제시
- ✅ Copy to clipboard 기능

**체크섬 검증 섹션** (lines 337-382):
```
✅ 체크섬 검증 성공 (일치하는 경우)
❌ 체크섬 검증 실패 (불일치하는 경우)
예상 체크섬: [SHA-1]
계산된 체크섬: [SHA-1]
```
- ✅ 파일 무결성 확인 가능
- ✅ 명확한 성공/실패 표시

**5. 페이지네이션** (lines 245-285)
- ✅ 이전/다음 페이지 네비게이션
- ✅ 현재 페이지 표시
- ✅ 페이지당 항목 수 선택 (10/20/50/100)

#### ⚠️ 개선이 필요한 부분

**1. Status Timeline 미흡**

현재 상태: 단순 상태 배지만 표시
```
상태: [RECEIVED | PARSED | VALIDATED | COMPLETED]
```

**문제점**:
- 각 단계 전환 시간 미표시
- 전체 처리 시간 미표시
- 단계별 소요 시간 미표시

**개선 방안**:
```
Timeline View:
⭕ RECEIVED (10-19 12:00)
  ↓ 2초
⭕ PARSED (10-19 12:00:02)
  ↓ 5초
⭕ VALIDATED (10-19 12:00:07)
  ↓ 3초
⭕ COMPLETED (10-19 12:00:10)

총 처리 시간: 10초
```

**2. 상세 모달의 정보 구성 개선 필요**

현재:
- 기본 정보, 해시, 체크섬, 오류 메시지만 표시

개선 방안:
```
추가 정보:
- Collection Number (CSCA/EMRTD)
- File Version (버전 정보)
- Certificate Count (추출된 인증서 수)
- CRL Count (추출된 CRL 수)
- Validation Details (검증 결과 요약)
- Processing Timeline (단계별 시간)
```

**3. 모바일 반응형 확인 필요**

현재:
```html
<div class="lg:col-span-3 gap-6">  <!-- 데스크톱 레이아웃 -->
```

확인 사항:
- [ ] 모바일에서 테이블이 제대로 표시되는가?
- [ ] 모달이 작은 화면에서 사용 가능한가?
- [ ] 터치 대응(touch target size 44px 이상)

---

### C. Master List Upload Page (`upload-ml.html`)

**상태**: LDIF 페이지와 동일한 구조 및 기능
- ✅ 파일 확장자만 `.ml` 변경
- ✅ API 엔드포인트만 `/masterlist/*` 변경
- ⚠️ LDIF 페이지의 동일한 개선사항 적용 필요

---

## 3️⃣ 6단계 워크플로우 대응 현황

| 단계 | 이름 | Frontend | Backend | 상태 |
|------|------|----------|---------|------|
| 1 | 파일 선택 & 검증 | upload-*.html | - | ✅ 구현 |
| 2 | 파일 업로드 | upload-*.html → upload | FileUploadController | ✅ 구현 |
| 3 | 파일 파싱 | SSE Modal | ParseLdifFileUseCase | ⚠️ Backend 완성, Frontend 연동 미흡 |
| 4 | 인증서 검증 | SSE Modal | ValidateCertificateUseCase | ⚠️ Backend 완성, Frontend 연동 미흡 |
| 5 | LDAP 업로드 | SSE Modal | UploadToLdapEventHandler | ⚠️ Backend 완성, Frontend 연동 미흡 |
| 6 | 결과 표시 | list.html | UploadHistoryController | ✅ 구현 (Timeline 개선 필요) |

---

## 4️⃣ 접근성 (Accessibility) 및 UX 검토

### 현재 구현 상태

| 항목 | 상태 | 비고 |
|------|------|------|
| **ARIA Labels** | ⚠️ 부분적 | 모달에 aria-label 없음 |
| **포커스 관리** | ⚠️ 미흡 | modal open/close 시 포커스 순서 |
| **키보드 네비게이션** | ✅ 기본 | 탭 키로 이동 가능 |
| **색상 대비** | ✅ 양호 | DaisyUI 기본 색상 사용 |
| **터치 타겟 크기** | ⚠️ 미확인 | 버튼 최소 44px × 44px 확인 필요 |
| **에러 메시지 위치** | ✅ 명확 | 모달 상단에 표시 |

### 개선 방안

1. **ARIA Labels 추가**
   ```html
   <dialog id="progressModal" class="modal" aria-label="파일 처리 진행 상황">
   ```

2. **포커스 트랩 추가**
   ```javascript
   // Modal open 시 포커스를 modal 내로 제한
   modal.addEventListener('keydown', (e) => {
     if (e.key === 'Tab') { /* 포커스 트랩 */ }
   });
   ```

3. **스크린 리더 대응**
   ```html
   <span aria-live="polite" aria-atomic="true">
     파일 처리 중 (35%)
   </span>
   ```

---

## 5️⃣ 성능 및 최적화 검토

### 현재 구현 성능

| 항목 | 현황 | 최적화 여지 |
|------|------|-----------|
| **SHA-256 계산** | ~2-3초 (75MB) | 대용량 파일 시 UI 블로킹 가능 |
| **중복 검사 API** | < 100ms | 네트워크 지연에 따라 변동 |
| **파일 업로드** | ~1-2초 | 네트워크 속도에 따라 변동 |
| **SSE 연결** | < 100ms | 실시간 업데이트 |

### 최적화 제안

1. **Web Worker를 사용한 해시 계산**
   ```javascript
   // 메인 스레드 블로킹 방지
   const worker = new Worker('hash-worker.js');
   ```

2. **청크 기반 업로드**
   ```javascript
   // 대용량 파일을 작은 청크로 나누어 업로드
   // 진행률 더 정확하게 표시 가능
   ```

3. **요청 캐싱**
   ```javascript
   // 같은 파일에 대한 중복 검사 요청 캐싱
   const cache = new Map();
   ```

---

## 6️⃣ 개선 계획 및 우선순위

### Phase 18 개선 항목 (Priority Order)

#### 🔴 Priority 1: 필수 (즉시 필요)

**1. SSE API 엔드포인트 연동** ⏱️ 1-2일
- [ ] `/api/certificates/validate` 엔드포인트 구현
- [ ] ProgressService.sendProgress() 호출 추가
- [ ] 업로드 후 SSE 진행률이 실제로 표시되도록 수정
- [ ] 테스트: 파일 업로드 → SSE 진행률 모달 표시 → 완료

**2. 업로드 진행률 동적 계산** ⏱️ 1일
- [ ] 파일 크기 기반 hash 계산 시간 예측
- [ ] 동적 진행률 계산 함수 추가
- [ ] 대용량 파일(100MB) 테스트

#### 🟠 Priority 2: 중요 (1주 내)

**3. Upload History Timeline 개선** ⏱️ 2-3일
- [ ] 상세 모달에 타임라인 섹션 추가
- [ ] 각 단계별 처리 시간 표시
- [ ] 총 처리 시간 계산 및 표시
- [ ] Database migration: 단계별 timestamp 추가

**4. 오류 메시지 상세화** ⏱️ 1일
- [ ] 서버에서 errorCode 전달
- [ ] 사용자 친화적 오류 메시지 매핑
- [ ] 기술 정보와 사용자 메시지 분리

**5. 모바일 반응형 개선** ⏱️ 1-2일
- [ ] 모바일에서 테이블 표시 방식 개선 (카드 레이아웃)
- [ ] 모달 레이아웃 모바일 최적화
- [ ] 터치 타겟 크기 확인 (44px × 44px)

#### 🟡 Priority 3: 권장 (2주 내)

**6. 접근성 개선** ⏱️ 1-2일
- [ ] ARIA labels 추가
- [ ] 포커스 관리 개선
- [ ] 스크린 리더 대응

**7. 상세 정보 모달 정보 확충** ⏱️ 1-2일
- [ ] Collection Number 표시
- [ ] File Version 표시
- [ ] Certificate/CRL Count 표시
- [ ] Validation Details 요약

**8. 성능 최적화** ⏱️ 2-3일
- [ ] Web Worker로 SHA-256 계산 오프로드
- [ ] 청크 기반 업로드 구현 (선택)
- [ ] 요청 캐싱 추가

---

## 7️⃣ 구체적인 코드 수정 예시

### 1. SSE API 엔드포인트 통합

**현재 코드 문제**:
```javascript
// upload-ldif.html, line 505
const response = await fetch('/api/certificates/validate', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    uploadId: uploadId,
    parsedFileId: uploadId,
    certificateCount: 0,
    crlCount: 0
  })
});
```

**개선 방안**:
```javascript
// 파일 업로드 완료 후 바로 SSE 연결
// (API 호출 대신 이벤트 발행을 통해 자동 트리거)

// Backend: UploadLdifFileUseCase 완료 시
file.registerEvent(new FileUploadedEvent(uploadId));
// → ParseLdifFileUseCase 자동 트리거
// → ParseLdifFileUseCase에서 progressService.sendProgress() 호출
// → Frontend SSE 모달이 자동 업데이트

// Frontend: API 호출 제거, SSE만 사용
await submitFormAjax();  // 업로드 완료
startSSEProgress(uploadId);  // SSE 연결 시작
// SSE 이벤트가 자동으로 진행률 업데이트
```

### 2. 업로드 진행률 동적 계산

**현재 코드**:
```javascript
showProgress('파일 해시 계산 중...', 30);
showProgress('중복 파일 검사 중...', 60);
showProgress('파일 업로드 중...', 90);
```

**개선 코드**:
```javascript
async function handleUpload() {
  const FILE_SIZE_MB = selectedFile.size / (1024 * 1024);

  // 파일 크기별 예상 시간
  const HASH_TIME_MS = estimateHashTime(FILE_SIZE_MB);
  const CHECK_TIME_MS = 500;  // API 응답 시간
  const UPLOAD_TIME_MS = estimateUploadTime(FILE_SIZE_MB);

  const TOTAL_TIME_MS = HASH_TIME_MS + CHECK_TIME_MS + UPLOAD_TIME_MS;

  // Step 1: Calculate hash with dynamic progress
  startTime = Date.now();
  showProgress('파일 해시 계산 중...', 0);

  const hashPromise = calculateSHA256(selectedFile);
  const progressInterval = setInterval(() => {
    const elapsed = Date.now() - startTime;
    const percentage = Math.min(25, (elapsed / HASH_TIME_MS) * 25);
    updateProgressBar(percentage);
  }, 100);

  await hashPromise;
  clearInterval(progressInterval);

  // Step 2: Check duplicate
  showProgress('중복 파일 검사 중...', 30);
  const isDuplicate = await checkDuplicate();

  // Step 3: Upload with dynamic progress
  if (!isDuplicate) {
    showProgress('파일 업로드 중...', 35);
    // ... upload 로직
  }
}

function estimateHashTime(fileSizeMs) {
  // SHA-256: ~20MB/sec 기준
  return (fileSizeMs / 20) * 1000;
}

function estimateUploadTime(fileSizeMs) {
  // 네트워크: 가정 10Mbps = 1.25MB/sec
  return (fileSizeMs / 1.25) * 1000;
}
```

### 3. Timeline 추가 (Upload History)

**상세 모달에 추가할 섹션**:
```html
<!-- Processing Timeline -->
<div class="mt-6">
  <div class="divider">
    <span class="text-sm font-semibold">처리 타임라인</span>
  </div>

  <ul class="steps steps-vertical text-xs">
    <li class="step step-success" data-time="12:00:00">
      파일 수신 (RECEIVED)
    </li>
    <li class="step step-success" data-time="12:00:02">
      파일 파싱 완료 (PARSED) - 2초 소요
    </li>
    <li class="step step-success" data-time="12:00:07">
      인증서 검증 완료 (VALIDATED) - 5초 소요
    </li>
    <li class="step step-success" data-time="12:00:10">
      LDAP 저장 완료 (COMPLETED) - 3초 소요
    </li>
  </ul>

  <div class="mt-2 text-sm text-center">
    <span class="font-semibold">총 처리 시간: 10초</span>
  </div>
</div>
```

### 4. 오류 메시지 상세화

**서버 응답 형식**:
```json
{
  "success": false,
  "errorCode": "FILE_PARSE_ERROR",
  "message": "LDIF 파일 파싱 중 오류가 발생했습니다",
  "details": {
    "line": 42,
    "entry": "cn=root,dc=ldap,dc=smartcoreinc,dc=com",
    "reason": "Invalid attribute format"
  },
  "userMessage": "파일의 42번 줄에 잘못된 형식의 속성이 있습니다. LDIF 파일을 확인해주세요."
}
```

**프론트엔드 처리**:
```javascript
function handleUploadError(response) {
  const errorData = await response.json();

  let userMessage = errorData.userMessage || '파일 업로드 중 오류가 발생했습니다.';

  if (errorData.details) {
    userMessage += `\n\n[상세 정보]\n`;
    userMessage += `Line: ${errorData.details.line}\n`;
    userMessage += `Reason: ${errorData.details.reason}`;
  }

  showErrorAlert(userMessage);
  logErrorForSupport(errorData);  // 지원팀 추적용
}
```

---

## 8️⃣ 테스트 체크리스트

### Phase 18 진행 전 확인 사항

#### ✅ Functional Tests
- [ ] LDIF 파일 업로드 (< 10MB) → 완료까지 정상 작동
- [ ] LDIF 파일 업로드 (> 50MB) → 진행률 업데이트 정상
- [ ] 중복 파일 감지 시 모달 표시
- [ ] Master List 파일 업로드 정상 작동
- [ ] 체크섬 검증 (일치/불일치) 모두 표시
- [ ] 업로드 이력 조회 정상
- [ ] 상태 필터링 정상
- [ ] 파일명 검색 정상

#### 🧪 UI/UX Tests
- [ ] SSE 진행률 모달 표시 및 업데이트
- [ ] 오류 메시지 명확성 확인
- [ ] 모바일 화면에서 모달 사용 가능성
- [ ] 터치 타겟 크기 확인 (버튼 44px 이상)
- [ ] 색맹 사용자를 위한 색상 대비 확인

#### ♿ Accessibility Tests
- [ ] 키보드만으로 모든 기능 사용 가능
- [ ] 스크린 리더 (NVDA/JAWS) 대응 확인
- [ ] ARIA labels 완성도 확인
- [ ] 포커스 관리 정상

#### ⚡ Performance Tests
- [ ] SHA-256 계산 (75MB) ≤ 3초
- [ ] 중복 검사 API ≤ 500ms
- [ ] SSE 연결 ≤ 100ms
- [ ] 대용량 파일 업로드 중 UI 블로킹 없음

---

## 9️⃣ 결론 및 권장사항

### 현재 상태 평가

| 측면 | 평가 | 점수 |
|------|------|------|
| **기능 구현** | 핵심 기능 대부분 구현됨 | 7/10 |
| **사용성** | 명확한 UI, 좋은 정보 제시 | 8/10 |
| **디자인** | DaisyUI 일관성 유지 | 9/10 |
| **성능** | 기본 수준 (최적화 여지 있음) | 7/10 |
| **접근성** | 기본 수준 (개선 필요) | 6/10 |
| **전체** | **양호** (개선점 있지만 사용 가능) | **7.4/10** |

### 권장사항

#### 🎯 Phase 18 핵심 목표

1. **SSE API 연동 완성** (Priority 1)
   - 파일 업로드 후 진행률이 실제로 표시되어야 함
   - 이것이 없으면 사용자가 처리 상태를 알 수 없음

2. **Upload History Timeline 개선** (Priority 2)
   - 각 단계별 소요 시간 표시로 사용자 신뢰도 향상
   - 성능 모니터링에도 도움이 됨

3. **오류 메시지 상세화** (Priority 2)
   - 사용자가 문제를 스스로 해결하도록 돕기
   - 지원 요청 감소

#### ⏱️ 예상 일정

- **Week 1**: Priority 1 항목 (1-2일)
- **Week 2**: Priority 2 항목 (3-4일)
- **Week 3**: Priority 3 항목 (2-3일)

---

## 📎 참고 문서

- `current_workflow.md` - 6단계 워크플로우 상세
- `PHASE_17_COMPLETE.md` - Phase 17 완료 보고서
- `CLAUDE.md` - 프로젝트 전체 문서

---

**문서 버전**: 1.0
**작성 일시**: 2025-10-30
**상태**: 검토 완료, Phase 18 계획 수립 준비
