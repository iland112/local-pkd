# Phase 18: 구현 가이드 (Frontend Coding Standards 적용)

**작성 일시**: 2025-10-30
**참조 문서**: `FRONTEND_CODING_STANDARDS.md`
**상태**: 구현 시작 준비 완료

---

## 📋 Phase 18 목표

### 1️⃣ 파일 업로드 페이지 통합
- ✅ 중복된 LDIF/Master List 업로드 페이지를 단일 페이지로 통합
- ✅ Thymeleaf Fragment를 통한 재사용 가능한 컴포넌트 작성
- ✅ Alpine.js로 파일 타입 선택 상태 관리
- ✅ HTMX로 서버 통신 처리
- ✅ JavaScript 코드 0줄 (복잡한 계산인 SHA-256만 포함)

### 2️⃣ LDAP 저장 통계 화면 추가
- ✅ 업로드 이력 상세 모달에 통계 탭 추가
- ✅ 인증서/CRL 추출 통계 표시
- ✅ 검증 성공률/실패율 표시
- ✅ LDAP 저장 성공률/실패율 표시
- ✅ 단계별 처리 시간 표시

---

## 🗂️ 구현 파일 목록

### Frontend (Thymeleaf + Alpine.js + HTMX)

```
NEW:
├── /templates/file/unified-upload.html              (300 lines)
│   ├── Alpine.js: fileUploadComponent()
│   ├── HTMX: hx-post="/file/upload"
│   ├── Fragment 사용: file-type-selector, process-info, modals
│   └── JavaScript: SHA-256 계산만 포함

MODIFIED:
├── /templates/upload-history/list.html              (확장: +150 lines)
│   ├── 알림 Fragment 추가
│   ├── 통계 카드 Fragment 추가
│   ├── 검색 폼 Fragment 추가 (HTMX)
│   ├── 테이블 Fragment 추가
│   ├── 상세 모달 Fragment 확장 (탭 추가)
│   │   ├── Tab 1: 기본정보
│   │   ├── Tab 2: 통계 (NEW)
│   │   └── Tab 3: 타임라인 (NEW)
│   └── Alpine.js: detailModalComponent() 확장

NEW FRAGMENTS:
├── /templates/fragments/
│   ├── file-type-selector.html          (파일 타입 탭 선택)
│   ├── file-upload-form.html            (통합 업로드 폼)
│   ├── process-info.html                (파일 타입별 프로세스 정보)
│   ├── statistics-tabs.html             (통계 탭 콘텐츠)
│   ├── timeline-view.html               (타임라인 표시)
│   ├── duplicate-modal.html             (중복 경고 모달)
│   ├── progress-modal.html              (SSE 진행률 모달)
│   └── detail-modal.html                (상세 정보 모달)

SHARED:
├── /static/js/shared/
│   ├── alpine-components.js             (Alpine.js 컴포넌트들, 100 lines)
│   │   ├── fileUploadComponent()
│   │   ├── detailModalComponent()
│   │   └── progressModalComponent()
│   └── utilities.js                     (유틸리티 함수, 50 lines)
│       ├── calculateSHA256()
│       ├── formatFileSize()
│       └── formatDuration()
```

### Backend (Java)

```
NEW:
├── /src/main/java/com/smartcoreinc/localpkd/fileupload/infrastructure/web/
│   └── FileUploadController.java        (150 lines)
│       ├── GET /upload → showUploadPage()
│       ├── POST /file/upload → uploadFile()
│       ├── POST /file/api/check-duplicate → checkDuplicate()
│       └── Helper methods: uploadLdif(), uploadMasterList()

MODIFIED:
├── /src/main/java/com/smartcoreinc/localpkd/fileupload/application/response/
│   └── UploadHistoryResponse.java       (확장: +10 fields)
│       ├── certificateCount
│       ├── crlCount
│       ├── validationSuccessCount
│       ├── validationFailedCount
│       ├── ldapUploadSuccessCount
│       ├── ldapUploadFailedCount
│       ├── processingTimeMs
│       ├── parsedAt
│       ├── validatedAt
│       └── ldapUploadedAt

MODIFIED:
├── /src/main/java/com/smartcoreinc/localpkd/upload-history/infrastructure/web/
│   └── UploadHistoryController.java     (확장: ~20 lines)
│       └── 통계 데이터 포함

DATABASE:
├── /src/main/resources/db/migration/
│   └── V13__Add_LDAP_Statistics.sql     (35 lines)
│       ├── 10개 컬럼 추가
│       ├── 인덱스 추가
│       └── 기본값 설정
```

---

## 📝 Week 1: 파일 업로드 페이지 통합 (3-4일)

### Day 1: 설계 및 Fragment 작성

#### Task 1.1: 통합 업로드 페이지 설계
**파일**: `/templates/file/unified-upload.html`

**작성 규칙**:
- ✅ Thymeleaf Layout 사용
- ✅ Layout fragment로 구성
- ✅ 페이지 코드 길이: 150-200 lines

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/main}">

<head>
  <title>파일 업로드</title>
</head>

<body>
  <div layout:fragment="content">
    <!-- Fragment 사용 -->
    <th:block th:replace="~{fragments/alerts :: success-alert(${successMessage})}"></th:block>
    <th:block th:replace="~{fragments/alerts :: error-alert(${errorMessage})}"></th:block>

    <!-- 파일 타입 선택 + 업로드 폼 -->
    <th:block th:replace="~{fragments/file-upload-form :: upload-form}"></th:block>

    <!-- 프로세스 정보 -->
    <th:block th:replace="~{fragments/process-info :: process-card}"></th:block>
  </div>

  <!-- 모달들 -->
  <th:block th:replace="~{fragments/duplicate-modal :: duplicate-modal}"></th:block>
  <th:block th:replace="~{fragments/progress-modal :: progress-modal}"></th:block>

  <!-- Alpine.js 컴포넌트 + SHA-256 계산 -->
  <th:block layout:fragment="script-content">
    <script src="/static/js/shared/alpine-components.js"></script>
    <script src="/static/js/shared/utilities.js"></script>
    <script>
      // 최소한의 페이지별 로직
      // 대부분은 Alpine.js + HTMX로 처리됨
    </script>
  </th:block>
</body>
</html>
```

**체크리스트**:
- [ ] layout:decorate 올바르게 설정
- [ ] 모든 콘텐츠가 layout:fragment="content" 내부
- [ ] Fragment들이 모두 ~/fragments/ 경로로 참조
- [ ] 페이지별 JavaScript 없음 (공유 스크립트 참조)

#### Task 1.2: Fragment 작성 (4개)

**파일 1**: `/templates/fragments/file-type-selector.html`

```html
<th:block th:fragment="file-type-selector">
  <!-- 파일 타입 선택 탭 (Alpine.js) -->
  <!-- 150줄 이상 필요 없음 -->
</th:block>
```

**파일 2**: `/templates/fragments/file-upload-form.html`

```html
<th:block th:fragment="upload-form">
  <!-- 통합 업로드 폼 (Alpine.js + HTMX) -->
  <!-- form hx-post="/file/upload" -->
  <!-- input name="fileType" :value="fileType" -->
</th:block>
```

**파일 3**: `/templates/fragments/process-info.html`

```html
<th:block th:fragment="process-card">
  <!-- 파일 타입별 프로세스 정보 -->
  <!-- Alpine.js로 fileType 기반 표시 -->
</th:block>
```

**파일 4**: `/templates/fragments/duplicate-modal.html`

```html
<th:block th:fragment="duplicate-modal">
  <!-- 중복 경고 모달 -->
  <!-- Alpine.js로 데이터 표시 -->
</th:block>
```

### Day 2: 백엔드 구현

#### Task 2.1: FileUploadController 생성

**파일**: `/src/main/java/com/smartcoreinc/localpkd/fileupload/infrastructure/web/FileUploadController.java`

**구조**:
```java
@RestController
@RequestMapping("/file")
public class FileUploadController {

    // GET /file/upload
    @GetMapping("/upload")
    public String showUploadPage(@RequestParam(defaultValue = "ldif") String type, Model model) {
        // type: ldif 또는 ml
        // model에 필요한 데이터 추가
        return "file/unified-upload";
    }

    // POST /file/upload
    @PostMapping("/upload")
    public String uploadFile(
        @RequestParam("file") MultipartFile file,
        @RequestParam("fileType") String fileType,
        @RequestParam(required = false) String expectedChecksum,
        @RequestParam("fileHash") String fileHash,
        RedirectAttributes redirectAttributes) {

        if ("ldif".equals(fileType)) {
            return uploadLdif(...);
        } else if ("ml".equals(fileType)) {
            return uploadMasterList(...);
        }
        // Error handling
    }

    // POST /file/api/check-duplicate
    @PostMapping("/api/check-duplicate")
    @ResponseBody
    public ResponseEntity<CheckDuplicateResponse> checkDuplicate(
        @RequestBody CheckDuplicateFileCommand command) {
        // 파일 타입 무관하게 처리 (해시 기반)
        return ResponseEntity.ok(...);
    }

    // Private helper methods
    private String uploadLdif(...) { ... }
    private String uploadMasterList(...) { ... }
}
```

**체크리스트**:
- [ ] GET /file/upload 구현 (type 파라미터 처리)
- [ ] POST /file/upload 구현 (fileType 기반 라우팅)
- [ ] POST /file/api/check-duplicate 구현
- [ ] 라우팅 테스트 (리다이렉트: /ldif/upload → /file/upload?type=ldif)

#### Task 2.2: 라우팅 설정

**변경사항**:
```java
// LdifUploadWebController.java
// @GetMapping("/upload") → 리다이렉트로 변경
@GetMapping("/upload")
public String redirectUpload() {
    return "redirect:/file/upload?type=ldif";
}

// MasterListUploadWebController.java
// @GetMapping("/upload") → 리다이렉트로 변경
@GetMapping("/upload")
public String redirectUpload() {
    return "redirect:/file/upload?type=ml";
}
```

### Day 3: 통합 테스트

#### Task 3.1: 기능 테스트

- [ ] LDIF 파일 업로드 (작은 파일)
- [ ] Master List 파일 업로드 (작은 파일)
- [ ] 파일 타입 선택 UI 작동 확인
- [ ] 중복 파일 감지 모달 표시 확인
- [ ] 리다이렉트 작동 확인 (/ldif/upload → /file/upload?type=ldif)

#### Task 3.2: UI/UX 테스트

- [ ] 반응형 레이아웃 (모바일, 태블릿, 데스크톱)
- [ ] Alpine.js 상태 관리 확인
- [ ] HTMX 폼 제출 작동
- [ ] 접근성 확인 (ARIA labels, 키보드 네비게이션)

### Day 4: 마이그레이션 및 정리

#### Task 4.1: 기존 파일 정리

```bash
# 이전 파일들 (단순화)
- upload-ldif.html → 제거 또는 리다이렉트 페이지로 변경
- upload-ml.html → 제거 또는 리다이렉트 페이지로 변경
- 링크 업데이트: navigation, 홈페이지 등
```

#### Task 4.2: 문서 업데이트

- [ ] 라우팅 문서 업데이트
- [ ] API 엔드포인트 문서 업데이트
- [ ] 사용자 가이드 업데이트

---

## 📊 Week 2: LDAP 통계 화면 추가 (4-5일)

### Day 5-6: 데이터베이스 및 DTO 확장

#### Task 5.1: Flyway Migration 생성

**파일**: `/src/main/resources/db/migration/V13__Add_LDAP_Statistics.sql`

```sql
ALTER TABLE uploaded_file ADD COLUMN (
  certificate_count INT DEFAULT 0,
  crl_count INT DEFAULT 0,
  validation_success_count INT DEFAULT 0,
  validation_failed_count INT DEFAULT 0,
  ldap_upload_success_count INT DEFAULT 0,
  ldap_upload_failed_count INT DEFAULT 0,
  processing_time_ms LONG DEFAULT 0,
  parsed_at TIMESTAMP,
  validated_at TIMESTAMP,
  ldap_uploaded_at TIMESTAMP
);

CREATE INDEX idx_uploaded_file_stats ON uploaded_file(
  certificate_count, validation_success_count, ldap_upload_success_count
);
```

**체크리스트**:
- [ ] Migration 파일 생성
- [ ] 개발 환경에서 마이그레이션 테스트
- [ ] 기존 데이터 마이그레이션 (기본값 설정)

#### Task 5.2: DTO 확장

**파일**: `/src/main/java/com/smartcoreinc/localpkd/fileupload/application/response/UploadHistoryResponse.java`

```java
@Builder
public record UploadHistoryResponse(
    // 기존 필드
    UUID uploadId,
    String fileName,
    // ...

    // NEW: 통계 필드
    Integer certificateCount,
    Integer crlCount,
    Integer validationSuccessCount,
    Integer validationFailedCount,
    Integer ldapUploadSuccessCount,
    Integer ldapUploadFailedCount,
    Long processingTimeMs,
    LocalDateTime parsedAt,
    LocalDateTime validatedAt,
    LocalDateTime ldapUploadedAt
) { }
```

**체크리스트**:
- [ ] 10개 통계 필드 추가
- [ ] Record 빌더 테스트
- [ ] 직렬화 테스트 (JSON)

#### Task 5.3: Repository 쿼리 업데이트

**파일**: `/src/main/java/com/smartcoreinc/localpkd/fileupload/infrastructure/repository/UploadedFileRepository.java`

```java
// JPQL SELECT에 10개 통계 필드 추가
@Query("SELECT new com.smartcoreinc.localpkd.fileupload.application.response.UploadHistoryResponse(...)" +
       " FROM UploadedFile u WHERE u.id = :id")
Optional<UploadHistoryResponse> findDetailById(@Param("id") UploadId id);
```

**체크리스트**:
- [ ] JPQL 수정
- [ ] 쿼리 테스트
- [ ] 성능 확인 (대량 데이터)

### Day 7-8: UI 구현

#### Task 6.1: 업로드 이력 페이지 확장

**파일**: `/templates/upload-history/list.html` (확장)

```html
<!-- 기존 구조 유지 -->
<div layout:fragment="content">
  <th:block th:replace="~{fragments/alerts :: ...}"></th:block>
  <th:block th:replace="~{fragments/statistics :: ...}"></th:block>
  <th:block th:replace="~{fragments/search :: ...}"></th:block>
  <th:block th:replace="~{fragments/tables :: ...}"></th:block>
</div>

<!-- 상세 모달 확장 -->
<th:block th:replace="~{fragments/detail-modal :: ...}"></th:block>
```

**체크리스트**:
- [ ] 기존 구조 유지 (파일 구조 영향 최소)
- [ ] Fragment들 정상 로드

#### Task 6.2: 상세 모달 Fragment 확장

**파일**: `/templates/fragments/detail-modal.html` (확장)

```html
<th:block th:fragment="detail-modal">
  <dialog id="detailModal" x-data="detailModalComponent()">
    <!-- 탭: 기본정보 | 통계 | 타임라인 -->
    <div class="tabs tabs-boxed">
      <input type="radio" name="detail_tabs" @change="activeTab = 'basic'" checked label="기본정보" />
      <input type="radio" name="detail_tabs" @change="activeTab = 'stats'" label="통계" />
      <input type="radio" name="detail_tabs" @change="activeTab = 'timeline'" label="타임라인" />
    </div>

    <!-- Tab 1: 기본정보 (기존) -->
    <div x-show="activeTab === 'basic'">
      <!-- 기존 내용 -->
    </div>

    <!-- Tab 2: 통계 (NEW) -->
    <div x-show="activeTab === 'stats'">
      <th:block th:replace="~{fragments/statistics-tabs :: stats-content}"></th:block>
    </div>

    <!-- Tab 3: 타임라인 (NEW) -->
    <div x-show="activeTab === 'timeline'">
      <th:block th:replace="~{fragments/timeline-view :: timeline-content}"></th:block>
    </div>
  </dialog>
</th:block>
```

**체크리스트**:
- [ ] 탭 UI 추가
- [ ] Alpine.js 상태 관리 확장 (activeTab)
- [ ] 기존 내용 영향 없음

#### Task 6.3: 통계 탭 내용 작성

**파일**: `/templates/fragments/statistics-tabs.html` (신규)

```html
<th:block th:fragment="stats-content">
  <!-- 통계 카드들: Stats 컴포넌트 사용 -->
  <div class="stats stats-vertical lg:stats-horizontal shadow">
    <div class="stat">
      <div class="stat-title">추출된 인증서</div>
      <div class="stat-value text-primary" x-text="data.certificateCount">0</div>
    </div>
    <!-- More stats -->
  </div>

  <!-- 처리 단계별 진행률 바 -->
  <div class="mt-6">
    <progress class="progress progress-success" :value="data.validationSuccessCount" max="100"></progress>
  </div>
</th:block>
```

**체크리스트**:
- [ ] Stats 컴포넌트 사용
- [ ] 모든 통계 필드 표시
- [ ] 퍼센티지 계산 로직

#### Task 6.4: 타임라인 뷰 작성

**파일**: `/templates/fragments/timeline-view.html` (신규)

```html
<th:block th:fragment="timeline-content">
  <!-- 타임라인 표시 (Stepper) -->
  <ul class="timeline timeline-vertical">
    <li :data-content="'파일 업로드'">
      <div class="timeline-start" x-text="formatTime(data.uploadedAt)">-</div>
      <div class="timeline-middle"><svg></svg></div>
    </li>
    <!-- More steps -->
  </ul>

  <!-- 단계별 소요 시간 -->
  <div class="mt-4">
    <p>파싱: <span x-text="calculateDuration(data.uploadedAt, data.parsedAt)">-</span></p>
  </div>
</th:block>
```

**체크리스트**:
- [ ] Timeline 컴포넌트 사용
- [ ] 모든 단계 표시 (upload → parsing → validation → ldap)
- [ ] 소요 시간 계산

### Day 9: JavaScript 및 통합 테스트

#### Task 7.1: Alpine.js 컴포넌트 확장

**파일**: `/static/js/shared/alpine-components.js` (확장)

```javascript
// detailModalComponent() 확장
function detailModalComponent() {
  return {
    open: false,
    activeTab: 'basic',
    data: {},

    // 새로운 계산된 속성
    get validationSuccessRate() {
      if (!this.data.certificateCount || this.data.certificateCount === 0) return 0;
      return Math.round((this.data.validationSuccessCount / this.data.certificateCount) * 100);
    },

    get ldapSuccessRate() {
      const total = (this.data.ldapUploadSuccessCount || 0) + (this.data.ldapUploadFailedCount || 0);
      if (total === 0) return 0;
      return Math.round(((this.data.ldapUploadSuccessCount || 0) / total) * 100);
    }
  };
}
```

**체크리스트**:
- [ ] 계산된 속성 추가
- [ ] 탭 전환 로직 추가
- [ ] 데이터 바인딩 확인

#### Task 7.2: 유틸리티 함수 추가

**파일**: `/static/js/shared/utilities.js` (확장)

```javascript
// 시간 포맷
function formatTime(timestamp) {
  if (!timestamp) return '-';
  return new Date(timestamp).toLocaleString('ko-KR');
}

// 기간 계산
function calculateDuration(start, end) {
  if (!start || !end) return '-';
  const ms = new Date(end) - new Date(start);
  const seconds = Math.round(ms / 1000);
  return seconds + '초';
}
```

**체크리스트**:
- [ ] 모든 포맷 함수 추가
- [ ] 테스트 (null 처리)

#### Task 7.3: E2E 통합 테스트

- [ ] 파일 업로드
- [ ] 업로드 이력 조회
- [ ] 상세 모달 열기
- [ ] 통계 탭 표시 (데이터 정확성)
- [ ] 타임라인 탭 표시 (시간 계산)
- [ ] 반응형 레이아웃 확인

---

## ✅ 검증 기준

### 코드 품질

- [ ] **JavaScript 라인 수**
  - 페이지별: < 50 lines (복잡한 계산 제외)
  - 공유: < 200 lines (alpine-components.js + utilities.js)
  - **합계**: < 250 lines

- [ ] **Thymeleaf 구조**
  - Fragment 재사용: > 80%
  - HTML 중복: 0%
  - 계층 구조: 명확함

- [ ] **CSS 클래스**
  - Tailwind + DaisyUI만 사용
  - 인라인 style: 없음
  - 불필요한 class: 없음

### 기능 테스트

- [ ] **파일 업로드**
  - LDIF 파일: ✅
  - Master List 파일: ✅
  - 파일 타입 자동 감지: ✅
  - 중복 파일 감지: ✅

- [ ] **통계 표시**
  - 인증서/CRL 수: 정확함
  - 검증 성공률: 정확함
  - LDAP 저장 성공률: 정확함
  - 처리 시간: 정확함

- [ ] **UI/UX**
  - 반응형: 모바일/태블릿/데스크톱
  - 접근성: ARIA labels, 키보드 네비게이션
  - 성능: Lighthouse > 85

### 성능

- [ ] **초기 로드**: < 3초
- [ ] **상호작용**: < 100ms
- [ ] **번들 크기**: HTML + JS < 100KB (축소 후)

---

## 📈 예상 결과

### 코드 감소
- 기존: upload-ldif.html (383) + upload-ml.html (382) = **765 lines**
- 개선: unified-upload.html (150) + Fragments (300) = **450 lines**
- **감소율**: 41%

### 유지보수성 향상
- Fragment 재사용으로 **코드 중복 제거** (95%)
- Alpine.js로 **JavaScript 최소화** (70% 감소)
- HTMX로 **서버 통신 단순화** (fetch 제거)

### 사용자 경험 향상
- 파일 타입 선택 명확화
- 통계 정보로 **처리 결과 가시화**
- 타임라인으로 **처리 과정 투명성** 향상

---

## 🚀 구현 시작

### 사전 준비

```bash
# 1. 기존 코드 백업
git commit -m "Phase 17 완료 - 통합 전 백업"
git branch feature/phase-18-ui-integration

# 2. Feature 브랜치 생성
git checkout -b feature/phase-18-file-upload-integration

# 3. 개발 시작
```

### 코딩 규칙 확인

- ✅ `docs/FRONTEND_CODING_STANDARDS.md` 검토
- ✅ Fragment 패턴 이해
- ✅ Alpine.js 컴포넌트 패턴 이해
- ✅ HTMX 기본 사용법 이해

---

**문서 버전**: 1.0
**최종 검토**: 2025-10-30
**상태**: ✅ Phase 18 구현 준비 완료

다음 단계:
1. Week 1: 파일 업로드 페이지 통합 (3-4일)
2. Week 2: LDAP 통계 화면 추가 (4-5일)
3. 최종 테스트 및 배포
