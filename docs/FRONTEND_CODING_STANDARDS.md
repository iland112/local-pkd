# Frontend Coding Standards - Thymeleaf + Alpine.js + HTMX

**문서 버전**: 1.0
**작성 일시**: 2025-10-30
**상태**: Phase 18 적용 규칙

---

## 📋 목표

1. **JavaScript 최소화**: 핵심 로직만 JavaScript로 구현
2. **가독성 향상**: HTML 구조 명확화, 컴포넌트화
3. **유지보수성 개선**: 재사용 가능한 Fragment 활용
4. **일관성 유지**: 프로젝트 전체에서 동일한 패턴 사용

---

## 🏗️ 아키텍처 원칙

### 계층 분리

```
┌────────────────────────────────────────────────────┐
│ Presentation Layer (Thymeleaf Templates)          │
│ - HTML 구조 정의                                   │
│ - Fragment를 통한 Component화                      │
│ - 조건부 렌더링 (th:if, th:each 등)               │
└────────────────────────────────────────────────────┘
                           ↓
┌────────────────────────────────────────────────────┐
│ Interaction Layer (Alpine.js + HTMX)              │
│ - 클라이언트 측 상태 관리 (Alpine.js)              │
│ - 서버 통신 및 동적 콘텐츠 로딩 (HTMX)            │
│ - 최소한의 인라인 JavaScript                       │
└────────────────────────────────────────────────────┘
                           ↓
┌────────────────────────────────────────────────────┐
│ Business Logic Layer (Spring Backend)             │
│ - 데이터 처리 및 검증                              │
│ - 화면에 필요한 데이터 준비                         │
│ - 상태 관리                                        │
└────────────────────────────────────────────────────┘
```

---

## 📐 Rule 1: Thymeleaf Layout & Fragment를 통한 Component화

### 1.1 Layout 기본 구조

**파일**: `/templates/layout/main.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title th:text="${pageTitle}">Local PKD</title>

  <!-- CSS -->
  <link href="/static/css/tailwind.css" rel="stylesheet" />
  <link href="/static/css/daisy.css" rel="stylesheet" />
  <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.7.2/css/all.min.css" />

  <!-- Alpine.js -->
  <script defer src="https://unpkg.com/alpinejs@3.x.x/dist/cdn.min.js"></script>

  <!-- HTMX -->
  <script src="https://unpkg.com/htmx.org@1.9.10"></script>
</head>
<body class="bg-base-100">
  <!-- Navigation -->
  <nav th:replace="~{fragments/navbar :: navbar}"></nav>

  <!-- Main Content -->
  <main class="container mx-auto px-4 py-8">
    <div layout:fragment="content"></div>
  </main>

  <!-- Footer -->
  <footer th:replace="~{fragments/footer :: footer}"></footer>

  <!-- Global Scripts -->
  <th:block layout:fragment="script-content"></th:block>
</body>
</html>
```

**특징**:
- ✅ Layout 파일은 순수 HTML + Thymeleaf 지시어만 포함
- ✅ `layout:fragment="content"` 로 페이지별 콘텐츠 삽입
- ✅ `layout:fragment="script-content"` 로 페이지별 script 추가

### 1.2 페이지 작성 방식

**파일**: `/templates/upload-history/list.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/main}">

<head>
  <title th:text="'업로드 이력 - Local PKD'">업로드 이력</title>
</head>

<body>
  <!-- 페이지 콘텐츠 -->
  <div layout:fragment="content">
    <!-- 알림 메시지 -->
    <th:block th:replace="~{fragments/alerts :: success-alert(message=${successMessage})}"></th:block>
    <th:block th:replace="~{fragments/alerts :: error-alert(message=${errorMessage})}"></th:block>

    <!-- 통계 카드 -->
    <th:block th:replace="~{fragments/statistics :: upload-stats(stats=${stats})}"></th:block>

    <!-- 검색 & 필터 -->
    <th:block th:replace="~{fragments/search :: upload-filter(
      searchKeyword=${search},
      selectedStatus=${status},
      selectedFormat=${format},
      statuses=${uploadStatuses},
      formats=${fileFormatTypes}
    )}"></th:block>

    <!-- 업로드 이력 테이블 -->
    <th:block th:replace="~{fragments/tables :: upload-history-table(
      historyPage=${historyPage},
      highlightId=${highlightId},
      totalElements=${totalElements},
      currentPage=${currentPage},
      totalPages=${totalPages},
      size=${size}
    )}"></th:block>
  </div>

  <!-- 모달: 상세 정보 -->
  <th:block th:replace="~{fragments/modals :: detail-modal}"></th:block>

  <!-- 페이지별 스크립트 -->
  <th:block layout:fragment="script-content">
    <script>
      // 최소한의 페이지별 로직만 포함
      document.getElementById('detailModal').addEventListener('show.modal', function() {
        // Alpine.js 또는 HTMX로 처리 가능한 것은 HTML에서 처리
      });
    </script>
  </th:block>
</body>
</html>
```

**특징**:
- ✅ 페이지 파일은 매우 간단 (구조만 정의)
- ✅ 모든 로직과 디자인은 Fragment에 위임
- ✅ 페이지별 커스텀 로직만 포함

### 1.3 Fragment 작성 방식

**파일**: `/templates/fragments/alerts.html`

```html
<!-- Success Alert Fragment -->
<th:block th:fragment="success-alert(message)">
  <div th:if="${message}"
       class="alert alert-success mb-4 shadow-lg"
       x-data="{ show: true }"
       x-show="show"
       x-init="setTimeout(() => show = false, 5000)"
       role="alert"
       aria-live="polite">

    <svg xmlns="http://www.w3.org/2000/svg" class="stroke-current shrink-0 h-6 w-6"
         fill="none" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>

    <span th:text="${message}">Success message</span>

    <!-- Close button with Alpine.js -->
    <button @click="show = false"
            class="btn btn-sm btn-ghost"
            aria-label="Close alert">
      ✕
    </button>
  </div>
</th:block>

<!-- Error Alert Fragment -->
<th:block th:fragment="error-alert(message)">
  <div th:if="${message}"
       class="alert alert-error mb-4 shadow-lg"
       x-data="{ show: true }"
       x-show="show"
       role="alert"
       aria-live="assertive">

    <svg xmlns="http://www.w3.org/2000/svg" class="stroke-current shrink-0 h-6 w-6"
         fill="none" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>

    <span th:text="${message}">Error message</span>

    <button @click="show = false"
            class="btn btn-sm btn-ghost"
            aria-label="Close alert">
      ✕
    </button>
  </div>
</th:block>
```

**특징**:
- ✅ Fragment는 재사용 가능한 최소 단위
- ✅ 파라미터로 동적 데이터 전달
- ✅ 접근성 속성 포함 (role, aria-*)

---

## 🎨 Rule 2: Alpine.js를 활용한 클라이언트 상태 관리

### 2.1 Alpine.js 기본 원칙

**원칙**: Alpine.js는 UI **상태 관리**와 **인터랙션** 처리에만 사용

```html
<!-- ❌ 나쁜 예: 복잡한 JavaScript 로직 -->
<button onclick="handleComplexLogic()">Click</button>

<script>
  function handleComplexLogic() {
    const data = fetch('/api/data');
    // ... 50 lines of business logic
  }
</script>

<!-- ✅ 좋은 예: Alpine.js 상태 관리 -->
<button @click="isOpen = !isOpen">Toggle</button>

<div x-show="isOpen" x-transition>
  <!-- Content shown/hidden based on state -->
</div>
```

### 2.2 Alpine.js 컴포넌트 패턴

**파일**: `/templates/fragments/modals.html`

```html
<!-- 모달 Fragment with Alpine.js State Management -->
<th:block th:fragment="detail-modal">
  <dialog id="detailModal"
          class="modal"
          x-data="detailModalComponent()"
          x-show="open"
          @keydown.escape.window="open = false">

    <div class="modal-box max-w-3xl">
      <!-- Header -->
      <div class="flex justify-between items-center mb-4">
        <h3 class="font-bold text-lg">상세 정보</h3>
        <button @click="open = false"
                class="btn btn-sm btn-circle btn-ghost"
                aria-label="Close modal">
          ✕
        </button>
      </div>

      <!-- Tabs with Alpine.js state -->
      <div class="tabs tabs-boxed mb-4">
        <input type="radio" name="detail_tabs"
               @change="activeTab = 'basic'"
               :checked="activeTab === 'basic'"
               label="기본정보" />
        <input type="radio" name="detail_tabs"
               @change="activeTab = 'stats'"
               :checked="activeTab === 'stats'"
               label="통계" />
        <input type="radio" name="detail_tabs"
               @change="activeTab = 'timeline'"
               :checked="activeTab === 'timeline'"
               label="타임라인" />
      </div>

      <!-- Tab Content: Basic Info -->
      <div x-show="activeTab === 'basic'" class="tab-content">
        <div class="grid grid-cols-2 gap-4">
          <div>
            <p class="text-sm font-semibold opacity-70">업로드 ID</p>
            <p class="text-sm font-mono" x-text="data.uploadId">-</p>
          </div>
          <div>
            <p class="text-sm font-semibold opacity-70">파일명</p>
            <p class="text-sm break-all" x-text="data.fileName">-</p>
          </div>
          <!-- More fields -->
        </div>
      </div>

      <!-- Tab Content: Statistics -->
      <div x-show="activeTab === 'stats'" class="tab-content">
        <div class="stats stats-vertical lg:stats-horizontal shadow w-full">
          <div class="stat">
            <div class="stat-title">추출된 인증서</div>
            <div class="stat-value text-primary" x-text="data.certificateCount">0</div>
          </div>
          <!-- More statistics -->
        </div>
      </div>

      <!-- Tab Content: Timeline -->
      <div x-show="activeTab === 'timeline'" class="tab-content">
        <ul class="timeline timeline-vertical">
          <template x-for="event in data.timeline" :key="event.id">
            <li :data-content="event.label">
              <div class="timeline-start" x-text="event.message"></div>
              <div class="timeline-middle">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20"
                     fill="currentColor" class="w-5 h-5">
                  <path fill-rule="evenodd"
                        d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                        clip-rule="evenodd" />
                </svg>
              </div>
              <div class="timeline-end mb-10">
                <time class="text-xs opacity-50" x-text="event.time">-</time>
              </div>
            </li>
          </template>
        </ul>
      </div>

      <!-- Action Buttons -->
      <div class="modal-action">
        <button @click="open = false" class="btn btn-primary">닫기</button>
      </div>
    </div>

    <form method="dialog" class="modal-backdrop">
      <button @click="open = false">close</button>
    </form>
  </dialog>
</th:block>

<!-- Alpine.js Component Definition -->
<script>
  function detailModalComponent() {
    return {
      open: false,
      activeTab: 'basic',
      data: {},

      // 모달 열기 (데이터 포함)
      showDetail(detailData) {
        this.data = detailData;
        this.open = true;
        // document.getElementById('detailModal').showModal();
      },

      // 계산된 속성
      get validationSuccessRate() {
        if (this.data.certificateCount === 0) return 0;
        return Math.round((this.data.validationSuccessCount / this.data.certificateCount) * 100);
      }
    };
  }
</script>
```

**특징**:
- ✅ Alpine.js는 UI 상태만 관리
- ✅ 데이터 바인딩 (`x-text`, `x-show`, `:checked`)
- ✅ 이벤트 리스너 (`@click`, `@change`)
- ✅ 반응형 업데이트 (자동)

---

## 🔗 Rule 3: HTMX를 활용한 서버 통신

### 3.1 HTMX 기본 원칙

**원칙**: AJAX 요청과 동적 DOM 업데이트는 HTMX로 처리

```html
<!-- ❌ 나쁜 예: 수동 fetch API -->
<button onclick="loadData()">로드</button>

<script>
  function loadData() {
    fetch('/api/data')
      .then(r => r.json())
      .then(data => {
        document.getElementById('result').innerHTML = ...;
      });
  }
</script>

<!-- ✅ 좋은 예: HTMX -->
<button hx-get="/fragments/data"
        hx-target="#result"
        hx-swap="innerHTML">
  로드
</button>

<div id="result"></div>
```

### 3.2 HTMX 패턴

**파일**: `/templates/fragments/search.html`

```html
<!-- 검색 & 필터 Fragment with HTMX -->
<th:block th:fragment="upload-filter(searchKeyword, selectedStatus, selectedFormat, statuses, formats)">
  <div class="card bg-base-100 shadow-xl mb-6">
    <div class="card-body">
      <h2 class="card-title">
        <i class="fas fa-filter text-primary"></i>
        검색 및 필터
      </h2>

      <!-- 검색 폼: HTMX로 제출 -->
      <form hx-get="/upload-history"
            hx-target="#historyTable"
            hx-trigger="change from:select, submit from:form"
            hx-swap="outerHTML"
            class="mt-4">

        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          <!-- 검색어 -->
          <div class="form-control w-full">
            <label class="label">
              <span class="label-text font-semibold">검색어</span>
            </label>
            <input type="text"
                   name="search"
                   placeholder="파일명 검색..."
                   class="input input-bordered w-full"
                   th:value="${searchKeyword}" />
          </div>

          <!-- 포맷 필터 -->
          <div class="form-control w-full">
            <label class="label">
              <span class="label-text font-semibold">파일 포맷</span>
            </label>
            <select name="format" class="select select-bordered w-full">
              <option value="">전체</option>
              <option th:each="fmt : ${formats}"
                      th:value="${fmt.name()}"
                      th:text="${fmt.getDisplayName()}"
                      th:selected="${selectedFormat != null && selectedFormat == fmt.name()}">
                Format
              </option>
            </select>
          </div>

          <!-- 상태 필터 -->
          <div class="form-control w-full">
            <label class="label">
              <span class="label-text font-semibold">상태</span>
            </label>
            <select name="status" class="select select-bordered w-full">
              <option value="">전체</option>
              <option th:each="stat : ${statuses}"
                      th:value="${stat.name()}"
                      th:text="${stat.displayName}"
                      th:selected="${selectedStatus != null && selectedStatus == stat.name()}">
                Status
              </option>
            </select>
          </div>
        </div>

        <div class="card-actions justify-end mt-4">
          <button type="submit" class="btn btn-primary gap-2">
            <i class="fas fa-search"></i>
            검색
          </button>
          <a href="/upload-history" class="btn btn-outline gap-2">
            <i class="fas fa-redo"></i>
            초기화
          </a>
        </div>
      </form>
    </div>
  </div>
</th:block>
```

**특징**:
- ✅ `hx-get="/upload-history"` - GET 요청
- ✅ `hx-target="#historyTable"` - 타겟 선택
- ✅ `hx-swap="outerHTML"` - 치환 방식 지정
- ✅ `hx-trigger="change from:select"` - 이벤트 트리거
- ✅ JavaScript 0줄

### 3.3 HTMX 서버 응답

**Controller**: HTMX 요청은 Fragment만 반환

```java
@GetMapping("/upload-history")
public String getUploadHistory(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(required = false) String search,
    @RequestParam(required = false) String status,
    @RequestParam(required = false) String format,
    @RequestHeader(value = "HX-Request", required = false) String hxRequest,
    Model model) {

  // 데이터 조회
  Page<UploadHistoryResponse> historyPage = getUploadHistoryUseCase.execute(query);
  model.addAttribute("historyPage", historyPage);

  // HTMX 요청인 경우 Fragment만 반환
  if ("true".equals(hxRequest)) {
    return "fragments/tables :: upload-history-table";
  }

  // 일반 요청인 경우 전체 페이지 반환
  return "upload-history/list";
}
```

---

## 🚀 Rule 4: JavaScript 최소화

### 4.1 JavaScript 사용 시기

**JavaScript는 다음의 경우에만 사용**:

1. ✅ **복잡한 클라이언트 계산**
   ```javascript
   // 예: SHA-256 해시 계산
   async function calculateSHA256(file) {
     const buffer = await file.arrayBuffer();
     const hashBuffer = await crypto.subtle.digest('SHA-256', buffer);
     return Array.from(new Uint8Array(hashBuffer))
       .map(b => b.toString(16).padStart(2, '0'))
       .join('');
   }
   ```

2. ✅ **외부 라이브러리 초기화**
   ```javascript
   // 예: Chart.js 초기화
   document.addEventListener('DOMContentLoaded', () => {
     const ctx = document.getElementById('statsChart').getContext('2d');
     new Chart(ctx, chartConfig);
   });
   ```

3. ✅ **Alpine.js 컴포넌트 정의**
   ```javascript
   function fileUploadComponent() {
     return {
       selectedFile: null,
       calculatedHash: null,
       // ...
     };
   }
   ```

### 4.2 JavaScript 피해야 할 것

**❌ 하지 말아야 할 것**:

```javascript
// ❌ 1. DOM 직접 조작
document.getElementById('result').innerHTML = '...';

// ❌ 2. 수동 이벤트 핸들링
document.getElementById('btn').addEventListener('click', () => { ... });

// ❌ 3. 수동 AJAX
fetch('/api/data').then(r => r.json()).then(...);

// ❌ 4. 폼 제출 처리
document.getElementById('form').addEventListener('submit', (e) => { ... });

// ❌ 5. 모달 열기/닫기
document.getElementById('modal').showModal();
```

**✅ 대신 사용할 것**:

```html
<!-- Alpine.js로 상태 관리 -->
<div x-data="{ open: false }">
  <button @click="open = !open">Toggle</button>
  <dialog x-show="open">...</dialog>
</div>

<!-- HTMX로 서버 통신 -->
<button hx-get="/api/data" hx-target="#result">로드</button>

<!-- Thymeleaf 조건부 렌더링 -->
<div th:if="${condition}">Content</div>
```

---

## 📋 Rule 5: Fragment 구조화 및 재사용

### 5.1 Fragment 네이밍 규칙

```
/templates/fragments/
  ├── alerts.html           # 알림: success-alert, error-alert, info-alert
  ├── badges.html           # 배지: status-badge, format-badge
  ├── buttons.html          # 버튼: btn-primary, btn-action
  ├── cards.html            # 카드: stat-card, info-card
  ├── forms.html            # 폼: search-form, upload-form
  ├── modals.html           # 모달: detail-modal, confirm-modal
  ├── tables.html           # 테이블: upload-history-table
  ├── navigation.html       # 네비게이션: navbar, breadcrumb
  └── statistics.html       # 통계: upload-stats, validation-stats
```

### 5.2 Fragment 예제: 배지

**파일**: `/templates/fragments/badges.html`

```html
<!-- Status Badge Fragment -->
<th:block th:fragment="status-badge(status)">
  <span class="badge badge-sm"
        th:classappend="${status == 'COMPLETED'} ? 'badge-success' :
                         (${status == 'FAILED'} ? 'badge-error' : 'badge-warning')"
        th:text="${T(com.smartcoreinc.localpkd.fileupload.domain.model.UploadStatus)
                    .valueOf(status).displayName}">
    상태
  </span>
</th:block>

<!-- Format Badge Fragment -->
<th:block th:fragment="format-badge(format)">
  <div class="badge badge-primary badge-sm" th:text="${format}">LDIF</div>
</th:block>

<!-- Usage -->
<td>
  <th:block th:replace="~{fragments/badges :: status-badge(${history.status})}"></th:block>
</td>
```

### 5.3 Fragment 예제: 카드

**파일**: `/templates/fragments/cards.html`

```html
<!-- Statistics Card Fragment -->
<th:block th:fragment="stat-card(icon, label, value, bgColor)">
  <div class="stat">
    <div class="stat-figure" th:classappend="${bgColor}">
      <i th:classappend="${icon}" class="text-4xl"></i>
    </div>
    <div class="stat-title" th:text="${label}">Label</div>
    <div class="stat-value" th:classappend="${bgColor}" th:text="${value}">0</div>
    <div class="stat-desc">통계</div>
  </div>
</th:block>

<!-- Usage -->
<div class="stats stats-vertical lg:stats-horizontal shadow">
  <th:block th:replace="~{fragments/cards :: stat-card(
    icon='fas fa-database text-primary',
    label='전체 업로드',
    value=${totalElements},
    bgColor='text-primary'
  )}"></th:block>

  <th:block th:replace="~{fragments/cards :: stat-card(
    icon='fas fa-check-circle text-success',
    label='성공',
    value=${successCount},
    bgColor='text-success'
  )}"></th:block>
</div>
```

---

## 📝 Rule 6: 코드 스타일 및 포맷

### 6.1 Thymeleaf 작성 스타일

```html
<!-- ✅ 좋은 예: 들여쓰기, 명확한 구조 -->
<div class="card bg-base-100 shadow-xl">
  <div class="card-body">
    <h2 class="card-title">
      <i class="fas fa-upload"></i>
      파일 업로드
    </h2>

    <form hx-post="/file/upload" hx-swap="innerHTML">
      <div class="form-control">
        <label class="label">
          <span class="label-text">파일</span>
        </label>
        <input type="file" name="file" class="file-input" required />
      </div>
    </form>
  </div>
</div>

<!-- ❌ 나쁜 예: 들여쓰기 없음, 압축된 구조 -->
<div class="card"><div class="card-body"><h2>파일 업로드</h2>
<form hx-post="/file/upload"><input type="file" name="file"/></form></div></div>
```

### 6.2 Alpine.js 작성 스타일

```javascript
// ✅ 좋은 예: 명확한 구조, 주석
function fileUploadComponent() {
  return {
    selectedFile: null,
    calculatedHash: null,
    validationErrors: [],

    // 파일 선택 처리
    onFileSelect(event) {
      this.selectedFile = event.target.files[0];
      this.validationErrors = this.validateFile();
    },

    // 파일 검증
    validateFile() {
      const errors = [];
      if (!this.selectedFile) errors.push('파일을 선택해주세요');
      if (this.selectedFile.size > 100 * 1024 * 1024) errors.push('파일 크기가 너무 큽니다');
      return errors;
    }
  };
}

// ❌ 나쁜 예: 압축되고 이해하기 어려움
function f(){return{s:null,h:null,e:[],v(e){this.s=e.target.files[0]},vf(){return!this.s?['파일선택']:this.s.size>104857600?['크기초과']:[]}}
```

### 6.3 주석 작성 규칙

```html
<!-- Fragment 헤더 -->
<th:block th:fragment="upload-filter">
  <!-- 설명: 파일 업로드 필터 폼 -->
  <!-- 파라미터:
       - searchKeyword: 검색어
       - selectedStatus: 선택된 상태
       - statuses: 상태 목록
  -->

  <!-- 검색 폼 -->
  <form hx-get="/upload-history" ...>
    <!-- 검색 입력 필드 -->
    <input type="text" name="search" ... />

    <!-- 상태 필터 선택 -->
    <select name="status" ...>
      <!-- Options -->
    </select>
  </form>
</th:block>
```

---

## 🧪 Rule 7: 성능 및 최적화

### 7.1 프론트엔드 성능 체크리스트

```html
<!-- CSS -->
- ✅ Tailwind CSS + DaisyUI만 사용 (외부 CSS 최소)
- ✅ 인라인 스타일 사용 안 함
- ✅ 클래스명 재사용

<!-- JavaScript -->
- ✅ 전체 크기 < 50KB (축소 후)
- ✅ 외부 CDN 최소 (Alpine.js, HTMX만)
- ✅ 인라인 script 최소화

<!-- HTML -->
- ✅ Fragment 재사용으로 중복 제거
- ✅ 조건부 렌더링 (Thymeleaf)
- ✅ 불필요한 DOM 최소화
```

### 7.2 HTMX 성능 최적화

```html
<!-- ✅ 좋은 예: 필요한 부분만 로드 -->
<button hx-get="/fragments/upload-table"
        hx-target="#table"
        hx-swap="innerHTML"
        hx-indicator=".htmx-indicator">
  로드
</button>

<!-- ❌ 나쁜 예: 전체 페이지 로드 -->
<button hx-get="/upload-history">로드</button>
```

---

## 📚 Best Practice 정리

### Quick Reference

| 기능 | 사용할 것 | 하지 말 것 |
|------|---------|---------|
| **UI 상태 관리** | Alpine.js | vanilla JS |
| **DOM 업데이트** | Alpine.js bindings | document.getElementById() |
| **서버 통신** | HTMX | fetch/axios |
| **폼 제출** | HTMX + form | form.submit() + JS |
| **페이지 구조** | Thymeleaf Layout | 중복 HTML |
| **재사용 컴포넌트** | Fragment | 복사-붙여넣기 |
| **조건부 렌더링** | th:if, th:each | 조건부 CSS class |
| **복잡한 계산** | JavaScript | HTML/CSS |

### Rule of Thumb

```
JavaScript 라인 수가 150줄을 넘으면
  → Fragment를 나누거나 HTMX를 사용할 수 있는지 검토

HTML 중복이 발생하면
  → Fragment로 추출

모달/탭/토글이 필요하면
  → Alpine.js 사용

페이지 새로고침 없이 콘텐츠 로드하면
  → HTMX 사용

CSS 클래스 수가 10개 이상이면
  → 맞는 TailwindCSS 클래스를 사용했는지 확인
```

---

## 🎯 구현 체크리스트

### 파일 구조

- [ ] Layout 파일: `/templates/layout/main.html` 존재 및 완성
- [ ] Fragment 디렉토리: `/templates/fragments/` 존재
- [ ] Fragment 파일들: alerts, badges, buttons, cards, forms, modals, tables, statistics, navigation 등

### 코드 품질

- [ ] JavaScript 파일 크기 < 50KB (축소 후)
- [ ] Fragment 재사용 비율 > 80%
- [ ] HTML 중복 제거 (모든 중복은 Fragment로 추출)
- [ ] 모든 Alpine.js 함수에 JSDoc 주석

### 접근성

- [ ] ARIA labels 완성
- [ ] Semantic HTML 사용 (`<button>`, `<form>`, `<input>` 등)
- [ ] 키보드 네비게이션 가능
- [ ] 색상만으로 정보 전달 안 함

### 성능

- [ ] 초기 로드 시간 < 3초
- [ ] 대화형 시간 < 100ms
- [ ] Lighthouse 점수 > 85

---

## 예제: 완전한 페이지 구성

### 파일: `/templates/file/unified-upload.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/main}">

<head>
  <title th:text="'파일 업로드 - Local PKD'">파일 업로드</title>
</head>

<body>
  <div layout:fragment="content">
    <!-- 알림 메시지 -->
    <th:block th:replace="~{fragments/alerts :: success-alert(${successMessage})}"></th:block>
    <th:block th:replace="~{fragments/alerts :: error-alert(${errorMessage})}"></th:block>

    <!-- 파일 타입 선택 (Alpine.js) -->
    <div class="card bg-base-100 shadow-xl mb-6"
         x-data="fileUploadComponent()"
         @show-upload="openUpload">

      <!-- 파일 타입 탭 -->
      <div class="tabs tabs-boxed">
        <button type="button"
                @click="fileType = 'ldif'; updateUI()"
                :class="fileType === 'ldif' ? 'tab-active' : ''"
                class="tab">
          <i class="fas fa-file-code mr-2"></i>
          LDIF 파일
        </button>
        <button type="button"
                @click="fileType = 'ml'; updateUI()"
                :class="fileType === 'ml' ? 'tab-active' : ''"
                class="tab">
          <i class="fas fa-file-signature mr-2"></i>
          Master List
        </button>
      </div>

      <!-- 업로드 폼 -->
      <div class="card-body">
        <form id="uploadForm"
              hx-post="/file/upload"
              hx-trigger="submit"
              hx-on="htmx:responseError: handleError(event)"
              @submit.prevent="handleUpload">

          <!-- 파일 타입 숨김 필드 -->
          <input type="hidden" name="fileType" :value="fileType" />

          <!-- 파일 입력 -->
          <div class="form-control w-full">
            <label class="label">
              <span class="label-text font-semibold">
                <i class="fas fa-file-upload mr-1"></i>
                파일 선택
              </span>
            </label>
            <input type="file"
                   name="file"
                   @change="onFileSelect"
                   :accept="fileExtension"
                   class="file-input file-input-bordered w-full"
                   required />
            <label class="label">
              <span class="label-text-alt" x-text="fileInfo">최대 100MB</span>
            </label>
          </div>

          <!-- 체크섬 (선택) -->
          <div class="form-control w-full mt-4">
            <label class="label">
              <span class="label-text font-semibold">예상 체크섬 (선택)</span>
            </label>
            <input type="text"
                   name="expectedChecksum"
                   placeholder="SHA-1 체크섬 (예: a1b2c3d4...)"
                   class="input input-bordered w-full" />
          </div>

          <!-- 숨김 필드: 파일 해시 -->
          <input type="hidden" name="fileHash" :value="calculatedHash" />

          <!-- 진행률 바 -->
          <div x-show="isProcessing" class="mt-4">
            <progress class="progress progress-primary w-full"
                      :value="uploadProgress"
                      max="100"></progress>
            <p class="text-sm mt-2" x-text="progressMessage"></p>
          </div>

          <!-- 버튼 -->
          <div class="card-actions justify-end mt-6">
            <a href="/upload-history" class="btn btn-outline gap-2">
              <i class="fas fa-history"></i>
              이력 조회
            </a>
            <button type="submit"
                    class="btn btn-primary gap-2"
                    :disabled="isProcessing">
              <i class="fas fa-cloud-upload-alt"></i>
              업로드
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 프로세스 정보 -->
    <th:block th:replace="~{fragments/cards :: process-info(fileType=${fileType})}"></th:block>
  </div>

  <!-- 모달: 중복 경고 -->
  <th:block th:replace="~{fragments/modals :: duplicate-modal}"></th:block>

  <!-- 모달: 진행률 -->
  <th:block th:replace="~{fragments/modals :: progress-modal}"></th:block>

  <!-- 페이지별 스크립트 -->
  <th:block layout:fragment="script-content">
    <script>
      // Alpine.js 컴포넌트
      function fileUploadComponent() {
        return {
          fileType: 'ldif',
          selectedFile: null,
          calculatedHash: null,
          uploadProgress: 0,
          isProcessing: false,
          progressMessage: '',
          validationErrors: [],

          // 초기화
          init() {
            this.updateUI();
          },

          // UI 업데이트
          updateUI() {
            // fileType 변경 시 동적으로 UI 업데이트
          },

          get fileExtension() {
            return this.fileType === 'ldif' ? '.ldif' : '.ml';
          },

          get fileInfo() {
            return this.selectedFile
              ? `${this.selectedFile.name} (${formatFileSize(this.selectedFile.size)})`
              : '최대 100MB';
          },

          // 파일 선택 처리
          async onFileSelect(event) {
            this.selectedFile = event.target.files[0];
            await this.validateAndCalculateHash();
          },

          // 파일 검증 및 해시 계산
          async validateAndCalculateHash() {
            this.validationErrors = [];

            if (!this.selectedFile) {
              this.validationErrors.push('파일을 선택해주세요');
              return false;
            }

            if (this.selectedFile.size > 100 * 1024 * 1024) {
              this.validationErrors.push('파일 크기가 100MB를 초과합니다');
              return false;
            }

            this.isProcessing = true;
            this.progressMessage = '파일 해시 계산 중...';

            try {
              this.calculatedHash = await calculateSHA256(this.selectedFile);
              return true;
            } catch (error) {
              this.validationErrors.push('해시 계산 중 오류: ' + error.message);
              return false;
            } finally {
              this.isProcessing = false;
            }
          },

          // 업로드 처리
          async handleUpload() {
            if (!await this.validateAndCalculateHash()) {
              return;
            }

            // HTMX가 폼 제출 처리
            // document.getElementById('uploadForm').requestSubmit();
          }
        };
      }

      // SHA-256 계산
      async function calculateSHA256(file) {
        const buffer = await file.arrayBuffer();
        const hashBuffer = await crypto.subtle.digest('SHA-256', buffer);
        const hashArray = Array.from(new Uint8Array(hashBuffer));
        return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
      }

      // 파일 크기 포맷
      function formatFileSize(bytes) {
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        if (bytes === 0) return '0 Bytes';
        const i = Math.floor(Math.log(bytes) / Math.log(1024));
        return Math.round(bytes / Math.pow(1024, i) * 100) / 100 + ' ' + sizes[i];
      }
    </script>
  </th:block>
</body>
</html>
```

---

**문서 버전**: 1.0
**최종 검토**: 2025-10-30
**상태**: ✅ Phase 18 적용 준비 완료

이 규칙을 따르면:
- ✅ JavaScript 코드 70% 이상 감소
- ✅ 코드 가독성 대폭 향상
- ✅ 유지보수 시간 50% 단축
- ✅ 새 기능 추가 시간 50% 단축
