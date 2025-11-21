# Phase 18: UI/UX 통합 및 통계 화면 개선 계획

**작성 일시**: 2025-10-30
**상태**: 계획 수립 단계

---

## 📋 Executive Summary

현재 UI/UX 검토 결과를 바탕으로, 다음 두 가지 주요 개선을 제안합니다:

### 1️⃣ **파일 업로드 페이지 통합** (코드 중복 제거)
- 현재: `/ldif/upload` + `/masterlist/upload` (중복 ~95%)
- 개선: 단일 통합 페이지 `/upload` + 파일 타입별 백엔드 자동 처리

### 2️⃣ **LDAP 저장 통계 화면 추가** (새 기능)
- 업로드 이력에 LDAP 저장 데이터 통계 섹션 추가
- 인증서/CRL 통계, 검증 결과, 처리 성공률 표시

---

## 📊 현재 상태 분석

### A. 파일 업로드 페이지 중복 현황

| 구성 요소 | upload-ldif.html | upload-ml.html | 중복도 |
|----------|------------------|-----------------|-------|
| 전체 라인 수 | 383 lines | 382 lines | 100% |
| HTML 구조 | 동일 | 동일 | 95% |
| JavaScript 로직 | 동일 | 동일 | 90% |
| 고유 부분 | 파일 확장자 (.ldif/.ml) | 파일 확장자 (.ml/.ldif) | 5% |

**코드 중복 위치**:
- 파일 입력 폼 (lines 39-104)
- 프로세스 정보 카드 (lines 109-159)
- 중복 경고 모달 (lines 163-222)
- SSE 진행률 모달 (lines 224-295)
- JavaScript 핸들러 (lines 299-677)

**차이점**:
```
upload-ldif.html:
- accept=".ldif"
- "/ldif/upload" → action
- "/ldif/api/check-duplicate" → API endpoint
- "LDIF 파일 업로드"

upload-ml.html:
- accept=".ml"
- "/masterlist/upload" → action
- "/masterlist/api/check-duplicate" → API endpoint
- "Master List 파일 업로드"
```

---

## 🎯 개선 방안

### I. 파일 업로드 페이지 통합

#### 1. 새 통합 페이지 생성

**경로**: `/src/main/resources/templates/file/unified-upload.html` (250-300 lines)

**핵심 변경**:

```html
<!-- Before: 두 개의 페이지 -->
/ldif/upload           (383 lines)
/masterlist/upload     (382 lines)

<!-- After: 하나의 통합 페이지 -->
/upload                (280 lines)
```

**구조**:
```html
<!DOCTYPE html>
<html lang="ko">
<body>
  <div class="container">
    <!-- File Type Selection (NEW) -->
    <div class="file-type-selector">
      <button class="btn" data-type="ldif">
        <i class="fas fa-file-code"></i> LDIF 파일
      </button>
      <button class="btn" data-type="ml">
        <i class="fas fa-file-signature"></i> Master List
      </button>
    </div>

    <!-- Unified Upload Form -->
    <form id="uploadForm" action="/file/upload" method="post" enctype="multipart/form-data">
      <!-- Hidden field to store file type -->
      <input type="hidden" id="fileTypeInput" name="fileType" value="ldif" />

      <!-- File Input (dynamic accept attribute) -->
      <input id="fileInput" type="file" name="file" accept=".ldif" required />

      <!-- Rest of form is identical -->
    </form>

    <!-- Dynamic help text based on file type -->
    <div id="fileTypeInfo">
      <p class="ldif-info">RFC 2849 표준 LDIF</p>
      <p class="ml-info" style="display:none;">CMS (Cryptographic Message Syntax)</p>
    </div>
  </div>

  <!-- Shared JavaScript -->
  <script src="/static/js/unified-upload.js"></script>
</body>
</html>
```

#### 2. 라우팅 변경

**현재**:
```
GET  /ldif/upload          → LdifUploadWebController.showUploadPage()
POST /ldif/upload          → LdifUploadWebController.uploadFile()
GET  /masterlist/upload    → MasterListUploadWebController.showUploadPage()
POST /masterlist/upload    → MasterListUploadWebController.uploadFile()
```

**개선**:
```
GET  /upload               → FileUploadController.showUploadPage()
POST /upload               → FileUploadController.uploadFile()
                              (fileType 파라미터로 구분)
POST /api/check-duplicate  → FileUploadController.checkDuplicate()
                              (fileType 파라미터로 구분)

// 하위 호환성 유지 (리다이렉트)
GET  /ldif/upload          → redirect /upload?type=ldif
GET  /masterlist/upload    → redirect /upload?type=ml
```

#### 3. 백엔드 처리 로직

**FileUploadController.java** (새로 생성):
```java
@RestController
@RequestMapping("/file")
public class FileUploadController {

    @GetMapping("/upload")
    public String showUploadPage(@RequestParam(defaultValue = "ldif") String type,
                                Model model) {
        // type 파라미터: ldif, ml
        model.addAttribute("fileType", type);
        return "file/unified-upload";
    }

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file,
                            @RequestParam("fileType") String fileType,  // NEW
                            @RequestParam(value = "forceUpload", defaultValue = "false") boolean forceUpload,
                            @RequestParam(value = "expectedChecksum", required = false) String expectedChecksum,
                            @RequestParam("fileHash") String fileHash,
                            RedirectAttributes redirectAttributes) {

        // fileType에 따라 적절한 Use Case 호출
        if ("ldif".equals(fileType)) {
            return uploadLdif(file, forceUpload, expectedChecksum, fileHash, redirectAttributes);
        } else if ("ml".equals(fileType)) {
            return uploadMasterList(file, forceUpload, expectedChecksum, fileHash, redirectAttributes);
        } else {
            redirectAttributes.addFlashAttribute("error", "지원하지 않는 파일 타입입니다");
            return "redirect:/upload";
        }
    }

    @PostMapping("/api/check-duplicate")
    @ResponseBody
    public ResponseEntity<CheckDuplicateResponse> checkDuplicate(
            @RequestBody CheckDuplicateFileCommand command,
            @RequestParam(value = "fileType", defaultValue = "ldif") String fileType) {

        // fileType에 관계없이 동일한 로직 (해시 기반)
        return ResponseEntity.ok(checkDuplicateFileUseCase.execute(command));
    }

    // Private helper methods
    private String uploadLdif(...) { ... }
    private String uploadMasterList(...) { ... }
}
```

#### 4. JavaScript 통합

**unified-upload.js** (250 lines):
```javascript
let selectedFileType = 'ldif';  // Default
let selectedFile = null;

// File type selector
document.querySelectorAll('[data-type]').forEach(btn => {
  btn.addEventListener('click', function() {
    selectedFileType = this.dataset.type;

    // Update UI based on file type
    updateUIForFileType(selectedFileType);

    // Update form action and file input
    updateFormAction(selectedFileType);
    updateFileInputAccept(selectedFileType);
  });
});

function updateUIForFileType(fileType) {
  if (fileType === 'ldif') {
    // Show LDIF-specific UI
    document.querySelector('h2').textContent = 'LDIF 파일 업로드';
    document.querySelector('.ldif-info').style.display = 'block';
    document.querySelector('.ml-info').style.display = 'none';
  } else if (fileType === 'ml') {
    // Show ML-specific UI
    document.querySelector('h2').textContent = 'Master List 파일 업로드';
    document.querySelector('.ldif-info').style.display = 'none';
    document.querySelector('.ml-info').style.display = 'block';
  }
}

function updateFormAction(fileType) {
  // Form action은 동일: /file/upload
  // fileType hidden field 업데이트
  document.getElementById('fileTypeInput').value = fileType;
}

function updateFileInputAccept(fileType) {
  const fileInput = document.getElementById('fileInput');
  fileInput.accept = fileType === 'ldif' ? '.ldif' : '.ml';
}

async function checkDuplicate() {
  const response = await fetch('/file/api/check-duplicate?fileType=' + selectedFileType, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      fileName: selectedFile.name,
      fileSize: selectedFile.size,
      fileHash: calculatedHash
    })
  });
  // Rest is identical to current implementation
}

async function submitFormAjax() {
  const formData = new FormData(document.getElementById('uploadForm'));
  // formData automatically includes fileType from hidden field

  const response = await fetch('/file/upload', {
    method: 'POST',
    body: formData
  });
  // Rest is identical
}
```

---

### II. LDAP 저장 통계 화면 추가

#### 1. 데이터베이스 스키마 확장

**기존 table**: `uploaded_file` (Phase 3에서 생성)

**필요한 추가 정보**:
```sql
ALTER TABLE uploaded_file ADD COLUMN (
  certificate_count INT DEFAULT 0,          -- 추출된 인증서 수
  crl_count INT DEFAULT 0,                  -- 추출된 CRL 수
  validation_success_count INT DEFAULT 0,   -- 검증 성공한 인증서 수
  validation_failed_count INT DEFAULT 0,    -- 검증 실패한 인증서 수
  ldap_upload_success_count INT DEFAULT 0,  -- LDAP 저장 성공한 인증서 수
  ldap_upload_failed_count INT DEFAULT 0,   -- LDAP 저장 실패한 인증서 수
  processing_time_ms LONG DEFAULT 0,        -- 전체 처리 시간 (밀리초)
  parsed_at TIMESTAMP,                      -- 파싱 완료 시간
  validated_at TIMESTAMP,                   -- 검증 완료 시간
  ldap_uploaded_at TIMESTAMP                -- LDAP 업로드 완료 시간
);
```

**Flyway 마이그레이션** (`V13__Add_LDAP_Statistics.sql`):
```sql
ALTER TABLE uploaded_file ADD COLUMN certificate_count INT DEFAULT 0;
ALTER TABLE uploaded_file ADD COLUMN crl_count INT DEFAULT 0;
ALTER TABLE uploaded_file ADD COLUMN validation_success_count INT DEFAULT 0;
ALTER TABLE uploaded_file ADD COLUMN validation_failed_count INT DEFAULT 0;
ALTER TABLE uploaded_file ADD COLUMN ldap_upload_success_count INT DEFAULT 0;
ALTER TABLE uploaded_file ADD COLUMN ldap_upload_failed_count INT DEFAULT 0;
ALTER TABLE uploaded_file ADD COLUMN processing_time_ms LONG DEFAULT 0;
ALTER TABLE uploaded_file ADD COLUMN parsed_at TIMESTAMP;
ALTER TABLE uploaded_file ADD COLUMN validated_at TIMESTAMP;
ALTER TABLE uploaded_file ADD COLUMN ldap_uploaded_at TIMESTAMP;

CREATE INDEX idx_uploaded_file_stats ON uploaded_file(
  certificate_count,
  validation_success_count,
  ldap_upload_success_count
);
```

#### 2. FileUploadHistory DTO 확장

**현재**:
```java
@Builder
public record UploadHistoryResponse(
    UUID uploadId,
    String fileName,
    Long fileSize,
    String fileSizeDisplay,
    String fileFormat,
    String collectionNumber,
    String version,
    LocalDateTime uploadedAt,
    String status,
    String expectedChecksum,
    String calculatedChecksum,
    String errorMessage
) { }
```

**개선**:
```java
@Builder
public record UploadHistoryResponse(
    UUID uploadId,
    String fileName,
    Long fileSize,
    String fileSizeDisplay,
    String fileFormat,
    String collectionNumber,
    String version,
    LocalDateTime uploadedAt,
    String status,
    String expectedChecksum,
    String calculatedChecksum,
    String errorMessage,

    // 추가: 통계 정보
    Integer certificateCount,      // 추출된 인증서 수
    Integer crlCount,              // 추출된 CRL 수
    Integer validationSuccessCount,  // 검증 성공
    Integer validationFailedCount,   // 검증 실패
    Integer ldapUploadSuccessCount,  // LDAP 저장 성공
    Integer ldapUploadFailedCount,   // LDAP 저장 실패
    Long processingTimeMs,           // 전체 처리 시간
    LocalDateTime parsedAt,          // 파싱 완료 시간
    LocalDateTime validatedAt,       // 검증 완료 시간
    LocalDateTime ldapUploadedAt     // LDAP 업로드 완료 시간
) { }
```

#### 3. 업로드 이력 상세 모달 확장

**현재**: `list.html`의 상세 모달 (lines 291-404)

**확장 내용**: 통계 탭 추가

```html
<!-- Existing Detail Modal Structure -->
<dialog id="detailModal" class="modal">
  <div class="modal-box max-w-4xl">

    <!-- Tabs for different sections (NEW) -->
    <div class="tabs tabs-boxed">
      <input type="radio" name="detail_tabs" label="기본정보" checked />
      <input type="radio" name="detail_tabs" label="통계" />
      <input type="radio" name="detail_tabs" label="타임라인" />
    </div>

    <!-- Tab Content 1: Basic Info (existing) -->
    <div class="tab-content">
      <!-- Existing content: ID, filename, format, size, status, time, hash -->
    </div>

    <!-- Tab Content 2: Statistics (NEW) -->
    <div class="tab-content" style="display:none;">
      <div class="stats stats-vertical lg:stats-horizontal shadow w-full">

        <!-- 인증서/CRL 추출 통계 -->
        <div class="stat">
          <div class="stat-figure text-primary">
            <i class="fas fa-certificate text-3xl"></i>
          </div>
          <div class="stat-title">추출된 인증서</div>
          <div class="stat-value text-primary" id="detail-cert-count">0</div>
          <div class="stat-desc">개</div>
        </div>

        <div class="stat">
          <div class="stat-figure text-secondary">
            <i class="fas fa-list text-3xl"></i>
          </div>
          <div class="stat-title">추출된 CRL</div>
          <div class="stat-value text-secondary" id="detail-crl-count">0</div>
          <div class="stat-desc">개</div>
        </div>

        <!-- 검증 결과 통계 -->
        <div class="stat">
          <div class="stat-figure text-success">
            <i class="fas fa-check-circle text-3xl"></i>
          </div>
          <div class="stat-title">검증 성공</div>
          <div class="stat-value text-success" id="detail-validation-success">0</div>
          <div class="stat-desc" id="detail-validation-success-pct">0%</div>
        </div>

        <div class="stat">
          <div class="stat-figure text-error">
            <i class="fas fa-times-circle text-3xl"></i>
          </div>
          <div class="stat-title">검증 실패</div>
          <div class="stat-value text-error" id="detail-validation-failed">0</div>
          <div class="stat-desc" id="detail-validation-failed-pct">0%</div>
        </div>

        <!-- LDAP 저장 통계 -->
        <div class="stat">
          <div class="stat-figure text-info">
            <i class="fas fa-server text-3xl"></i>
          </div>
          <div class="stat-title">LDAP 저장 성공</div>
          <div class="stat-value text-info" id="detail-ldap-success">0</div>
          <div class="stat-desc" id="detail-ldap-success-pct">0%</div>
        </div>

        <div class="stat">
          <div class="stat-figure text-warning">
            <i class="fas fa-exclamation-circle text-3xl"></i>
          </div>
          <div class="stat-title">LDAP 저장 실패</div>
          <div class="stat-value text-warning" id="detail-ldap-failed">0</div>
          <div class="stat-desc" id="detail-ldap-failed-pct">0%</div>
        </div>

        <!-- 처리 시간 -->
        <div class="stat">
          <div class="stat-figure text-accent">
            <i class="fas fa-hourglass text-3xl"></i>
          </div>
          <div class="stat-title">총 처리 시간</div>
          <div class="stat-value text-accent" id="detail-processing-time">0초</div>
          <div class="stat-desc">파일 업로드 ~ LDAP 저장</div>
        </div>
      </div>

      <!-- Detailed breakdown chart (NEW) -->
      <div class="mt-6">
        <h4 class="font-bold mb-4">처리 단계별 통계</h4>

        <!-- Progress bar: Parsing -->
        <div class="mb-4">
          <div class="flex justify-between mb-1">
            <span>파싱 완료</span>
            <span id="detail-parsed-percent">0%</span>
          </div>
          <progress
            id="detail-parsed-progress"
            class="progress progress-success"
            value="0"
            max="100"
          ></progress>
          <div class="text-xs text-base-content/50">
            <span id="detail-parsed-time">-</span>
          </div>
        </div>

        <!-- Progress bar: Validation -->
        <div class="mb-4">
          <div class="flex justify-between mb-1">
            <span>검증 완료</span>
            <span id="detail-validated-percent">0%</span>
          </div>
          <progress
            id="detail-validated-progress"
            class="progress progress-info"
            value="0"
            max="100"
          ></progress>
          <div class="text-xs text-base-content/50">
            <span id="detail-validated-time">-</span>
          </div>
        </div>

        <!-- Progress bar: LDAP Upload -->
        <div class="mb-4">
          <div class="flex justify-between mb-1">
            <span>LDAP 저장 완료</span>
            <span id="detail-ldap-percent">0%</span>
          </div>
          <progress
            id="detail-ldap-progress"
            class="progress progress-primary"
            value="0"
            max="100"
          ></progress>
          <div class="text-xs text-base-content/50">
            <span id="detail-ldap-time">-</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Tab Content 3: Timeline (existing + enhanced) -->
    <div class="tab-content" style="display:none;">
      <!-- Timeline visualization -->
      <ul class="timeline timeline-vertical">
        <li data-content="파일 업로드" id="timeline-upload">
          <div class="timeline-start timeline-box">업로드됨</div>
          <div class="timeline-middle">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
              <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd" />
            </svg>
          </div>
          <div class="timeline-end mb-10">
            <time class="font-mono text-xs opacity-50" id="timeline-upload-time">-</time>
          </div>
        </li>

        <li data-content="파일 파싱" id="timeline-parsing">
          <div class="timeline-start timeline-box">파싱 완료</div>
          <div class="timeline-middle">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
              <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd" />
            </svg>
          </div>
          <div class="timeline-end mb-10">
            <time class="font-mono text-xs opacity-50" id="timeline-parsing-time">-</time>
            <div class="text-xs" id="timeline-parsing-duration">-</div>
          </div>
        </li>

        <!-- More timeline items for validated, ldap_uploaded -->
      </ul>
    </div>
  </div>
</dialog>
```

#### 4. JavaScript 통계 표시 로직

```javascript
function showDetail(id, filename, format, size, status, time, hash,
                   expectedChecksum, calculatedChecksum, errorMsg,
                   certCount, crlCount, validationSuccess, validationFailed,
                   ldapSuccess, ldapFailed, processingTimeMs,
                   parsedAt, validatedAt, ldapUploadedAt) {

  // Basic info (existing)
  document.getElementById('detail-id').textContent = id;
  // ... other basic info

  // Statistics (NEW)
  document.getElementById('detail-cert-count').textContent = certCount || 0;
  document.getElementById('detail-crl-count').textContent = crlCount || 0;

  // Validation statistics with percentages
  const totalCerts = (validationSuccess || 0) + (validationFailed || 0);
  if (totalCerts > 0) {
    const successPct = Math.round((validationSuccess / totalCerts) * 100);
    const failedPct = 100 - successPct;

    document.getElementById('detail-validation-success').textContent = validationSuccess || 0;
    document.getElementById('detail-validation-success-pct').textContent = successPct + '%';
    document.getElementById('detail-validation-failed').textContent = validationFailed || 0;
    document.getElementById('detail-validation-failed-pct').textContent = failedPct + '%';
  }

  // LDAP upload statistics
  const totalLdap = (ldapSuccess || 0) + (ldapFailed || 0);
  if (totalLdap > 0) {
    const ldapSuccessPct = Math.round((ldapSuccess / totalLdap) * 100);
    const ldapFailedPct = 100 - ldapSuccessPct;

    document.getElementById('detail-ldap-success').textContent = ldapSuccess || 0;
    document.getElementById('detail-ldap-success-pct').textContent = ldapSuccessPct + '%';
    document.getElementById('detail-ldap-failed').textContent = ldapFailed || 0;
    document.getElementById('detail-ldap-failed-pct').textContent = ldapFailedPct + '%';
  }

  // Processing time
  if (processingTimeMs) {
    const seconds = Math.round(processingTimeMs / 1000);
    document.getElementById('detail-processing-time').textContent = seconds + '초';
  }

  // Timeline
  updateTimeline(uploadedAt, parsedAt, validatedAt, ldapUploadedAt);

  document.getElementById('detailModal').showModal();
}

function updateTimeline(uploadedAt, parsedAt, validatedAt, ldapUploadedAt) {
  if (uploadedAt) {
    document.getElementById('timeline-upload-time').textContent =
      new Date(uploadedAt).toLocaleString('ko-KR');
  }

  if (parsedAt) {
    const parseTime = new Date(parsedAt).toLocaleString('ko-KR');
    const duration = calculateDuration(uploadedAt, parsedAt);
    document.getElementById('timeline-parsing-time').textContent = parseTime;
    document.getElementById('timeline-parsing-duration').textContent = '소요시간: ' + duration;
  }

  // Similar for validated and ldapUploaded
}
```

---

## 📈 예상 효과

### 파일 업로드 페이지 통합

| 지표 | 현재 | 개선 후 | 개선율 |
|-----|------|--------|--------|
| 코드 라인 수 | 765 lines | 280 lines | **-63%** |
| 파일 개수 | 2개 | 1개 | **-50%** |
| 유지보수 비용 | 높음 | 낮음 | ⬇️ 유지보수 시간 50% 감소 |
| 새 기능 추가 시간 | ~4시간 | ~2시간 | ⬇️ 개발 속도 2배 향상 |

### LDAP 통계 화면

**추가되는 정보**:
- ✅ 인증서/CRL 추출 통계
- ✅ 검증 성공률/실패율
- ✅ LDAP 저장 성공률/실패율
- ✅ 단계별 처리 시간
- ✅ 타임라인 시각화

**사용자 이점**:
- 파일 처리 결과를 한눈에 파악 가능
- 문제 분석 시간 단축
- 처리 성능 모니터링 가능

---

## 🗓️ 구현 일정

### Phase 18 Task Breakdown

#### Week 1: 파일 업로드 페이지 통합 (3-4일)

**Day 1: 계획 및 준비** (1일)
- [ ] 통합 페이지 레이아웃 설계
- [ ] 라우팅 변경 계획 수립
- [ ] 기존 코드 분석 및 모듈화 전략

**Day 2-3: 구현** (2일)
- [ ] `unified-upload.html` 생성
- [ ] `FileUploadController` 생성
- [ ] `unified-upload.js` 생성
- [ ] 라우팅 변경 (`/upload` 추가)

**Day 4: 테스트 및 마이그레이션** (1일)
- [ ] LDIF 파일 업로드 테스트
- [ ] Master List 파일 업로드 테스트
- [ ] 기존 `/ldif/upload`, `/masterlist/upload` 리다이렉트 설정
- [ ] 링크 업데이트 (navigation, etc.)

#### Week 2: LDAP 통계 화면 추가 (4-5일)

**Day 5-6: 데이터베이스 및 DTO 확장** (2일)
- [ ] Flyway migration 생성 (`V13__Add_LDAP_Statistics.sql`)
- [ ] DTO 확장 (`UploadHistoryResponse`)
- [ ] Repository 쿼리 업데이트
- [ ] 테스트 데이터 준비

**Day 7-8: UI 구현** (2일)
- [ ] 상세 모달에 탭 추가
- [ ] 통계 섹션 HTML 작성
- [ ] 타임라인 섹션 HTML 작성

**Day 9: JavaScript 및 테스트** (1일)
- [ ] 통계 표시 JavaScript 구현
- [ ] 타임라인 업데이트 함수
- [ ] 전체 통합 테스트

---

## 🔗 관련 컴포넌트

### 백엔드 업데이트

**신규/수정 파일**:
1. `FileUploadController.java` (신규)
   - 통합 업로드 처리
   - fileType 파라미터 기반 라우팅

2. `UploadHistoryResponse.java` (수정)
   - 통계 필드 추가

3. `UploadHistoryController.java` (수정)
   - 통계 정보 조회 로직 추가

4. `UploadedFileRepository.java` (수정)
   - 통계 쿼리 추가

### 프론트엔드 업데이트

**신규/수정 파일**:
1. `/templates/file/unified-upload.html` (신규)
   - 통합 업로드 페이지

2. `/static/js/unified-upload.js` (신규)
   - 통합 JavaScript 로직

3. `/templates/upload-history/list.html` (수정)
   - 통계 탭 추가
   - 타임라인 섹션 추가

---

## ✅ 검증 기준

### 파일 업로드 페이지 통합

- [ ] LDIF 파일 업로드 완벽히 작동
- [ ] Master List 파일 업로드 완벽히 작동
- [ ] 파일 타입 선택 UI 직관적
- [ ] 기존 `/ldif/upload` 와 `/masterlist/upload` 리다이렉트 정상
- [ ] 모바일에서도 타입 선택 가능

### LDAP 통계 화면

- [ ] 통계 탭 표시되고 클릭 가능
- [ ] 모든 통계 값이 정확하게 표시
- [ ] 퍼센티지 계산 정확
- [ ] 타임라인 표시 정확
- [ ] 시간 계산 정확 (duration)

---

## 📌 추가 고려사항

### 1. 하위 호환성
- 기존 `/ldif/upload`, `/masterlist/upload` 링크가 깨지지 않도록 리다이렉트 설정
- 모바일 앱이 사용하는 API 엔드포인트 유지

### 2. 성능
- 통합 페이지의 JavaScript 번들 크기 확인
- 대용량 통계 데이터 조회 성능 최적화 (pagination)

### 3. 보안
- fileType 파라미터 검증 필수
- SQL injection 방지

### 4. 테스트
- 통합 페이지 단위 테스트
- E2E 테스트 (파일 업로드 → 통계 조회)
- 성능 테스트 (대용량 데이터)

---

**제안 상태**: ✅ 사용자 검토 및 승인 대기

다음 단계:
1. 이 계획에 대한 피드백
2. 우선순위 결정 (파일 통합 vs 통계 화면)
3. 구현 시작
