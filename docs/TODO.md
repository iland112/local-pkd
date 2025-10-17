# TODO - File Upload Manager 개발 계획

## 📊 진행 상황 요약

- **완료:** 6개 항목
- **진행 중:** 0개 항목
- **예정:** 15개 항목

---

## 🔴 우선순위 1 (High Priority) - 다음 Sprint

### 1. 파일 업로드 이력 조회 페이지 구현 ⭐⭐⭐
**예상 소요 시간:** 2-3일

#### 백엔드 작업
- [ ] Controller 메서드 구현
  ```java
  @GetMapping("/upload-history")
  public String getUploadHistory(
      @RequestParam(required = false) String format,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) LocalDate startDate,
      @RequestParam(required = false) LocalDate endDate,
      @PageableDefault(size = 20) Pageable pageable,
      Model model
  )
  ```

- [ ] Service 메서드 구현
  ```java
  public Page<FileUploadHistory> searchUploadHistory(
      FileSearchCriteria criteria,
      Pageable pageable
  )
  ```

- [ ] Repository 쿼리 메서드 추가
  ```java
  @Query("SELECT f FROM FileUploadHistory f WHERE ...")
  Page<FileUploadHistory> findBySearchCriteria(
      @Param("format") FileFormat format,
      @Param("status") UploadStatus status,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      Pageable pageable
  )
  ```

#### 프론트엔드 작업
- [ ] upload-history.html 페이지 생성
- [ ] 테이블 레이아웃 구현
  - 파일명, 포맷, 크기, 상태, 업로드 시간 표시
  - 정렬 가능한 헤더 (Sortable table headers)
  - 행 클릭 시 상세 정보 모달

- [ ] 필터 UI 구현
  - 파일 포맷 드롭다운 (All, LDIF, Master List)
  - 업로드 상태 드롭다운 (All, Success, Failed, Pending)
  - 날짜 범위 선택기 (Date picker)

- [ ] 페이지네이션 구현
  - 이전/다음 버튼
  - 페이지 번호 표시
  - 페이지당 항목 수 선택 (10, 20, 50, 100)

- [ ] 상세 정보 모달
  - 전체 메타데이터 표시
  - 체크섬 정보
  - 에러 메시지 (실패한 경우)
  - JSON 메타데이터 뷰어

#### 테스트
- [ ] 단위 테스트 작성
- [ ] 통합 테스트 작성
- [ ] E2E 테스트 시나리오 작성

---

### 2. 중복 파일 업로드 처리 UI ⭐⭐⭐
**예상 소요 시간:** 1-2일

#### 백엔드 작업
- [ ] 중복 파일 검사 API 엔드포인트
  ```java
  @PostMapping("/check-duplicate")
  public ResponseEntity<DuplicateCheckResponse> checkDuplicate(
      @RequestParam("fileHash") String fileHash
  )
  ```

- [ ] 강제 재업로드 옵션 추가
  ```java
  @PostMapping("/force-upload")
  public ResponseEntity<FileUploadResponse> forceUpload(
      @RequestParam("file") MultipartFile file,
      @RequestParam("overwrite") boolean overwrite
  )
  ```

#### 프론트엔드 작업
- [ ] 업로드 전 중복 검사 로직 추가
  ```javascript
  async function checkDuplicateBeforeUpload(file) {
      const fileHash = await calculateFileHash(file);
      const response = await fetch(`/check-duplicate?fileHash=${fileHash}`);
      if (response.isDuplicate) {
          showDuplicateModal(response.existingUpload);
      }
  }
  ```

- [ ] 중복 파일 경고 모달 구현
  - 기존 업로드 정보 표시
  - 메타데이터 비교 테이블
  - 선택 옵션 버튼
    - ❌ 취소
    - 🔄 강제 재업로드
    - 📊 기존 이력 보기

- [ ] 사용자 선택에 따른 처리 로직
  - 취소: 업로드 중단
  - 강제 재업로드: overwrite=true 파라미터와 함께 업로드
  - 기존 이력 보기: 이력 페이지로 이동

#### 테스트
- [ ] 중복 검사 로직 테스트
- [ ] 강제 재업로드 테스트
- [ ] 모달 UI 테스트

---

### 3. 체크섬 검증 결과 표시 UI ⭐⭐
**예상 소요 시간:** 1일

#### 백엔드 작업
- [ ] 체크섬 검증 결과를 응답에 포함
  ```java
  public class FileUploadResponse {
      private boolean checksumProvided;
      private boolean checksumMatched;
      private String expectedChecksum;
      private String calculatedChecksum;
      private String checksumAlgorithm; // "SHA-1"
  }
  ```

#### 프론트엔드 작업
- [ ] 체크섬 검증 결과 섹션 추가
  ```html
  <div id="checksum-verification" class="mt-4 p-4 rounded-lg border">
      <h4 class="font-semibold mb-2">🔐 체크섬 검증</h4>
      <!-- 검증 결과 표시 -->
  </div>
  ```

- [ ] 일치/불일치에 따른 시각적 피드백
  - ✅ 일치 시: 녹색 배경, 성공 아이콘
  - ❌ 불일치 시: 빨간색 배경, 경고 아이콘
  - ℹ️ 체크섬 미제공 시: 회색 배경, 정보 아이콘

- [ ] 체크섬 값 복사 버튼 구현
  ```javascript
  function copyToClipboard(text) {
      navigator.clipboard.writeText(text);
      showNotification("체크섬이 클립보드에 복사되었습니다.", "success");
  }
  ```

- [ ] 체크섬 불일치 시 상세 안내
  - 파일이 손상되었을 가능성
  - ICAO 공식 파일이 아닐 가능성
  - 재다운로드 권장 메시지

#### 테스트
- [ ] 체크섬 일치 시나리오 테스트
- [ ] 체크섬 불일치 시나리오 테스트
- [ ] 클립보드 복사 기능 테스트

---

## 🟡 우선순위 2 (Medium Priority) - 차기 Sprint

### 4. 파일 스토리지 구현 및 다운로드 기능 ⭐⭐
**예상 소요 시간:** 2-3일

#### 설계 결정
- [ ] 스토리지 전략 선택
  - Option A: 로컬 파일 시스템 (개발/테스트 환경)
  - Option B: AWS S3 (프로덕션 환경)
  - Option C: Hybrid (설정 가능)

#### 백엔드 작업
- [ ] FileStorageService 인터페이스 정의
  ```java
  public interface FileStorageService {
      String storeFile(MultipartFile file, FileMetadata metadata);
      Resource loadFileAsResource(String storedFileName);
      void deleteFile(String storedFileName);
      boolean fileExists(String storedFileName);
  }
  ```

- [ ] LocalFileStorageService 구현
  ```java
  @Service
  @Profile("local")
  public class LocalFileStorageService implements FileStorageService {
      private final Path fileStorageLocation;
      // 구현...
  }
  ```

- [ ] S3FileStorageService 구현 (선택사항)
  ```java
  @Service
  @Profile("prod")
  public class S3FileStorageService implements FileStorageService {
      private final AmazonS3 s3Client;
      // 구현...
  }
  ```

- [ ] 파일 다운로드 엔드포인트
  ```java
  @GetMapping("/download/{id}")
  public ResponseEntity<Resource> downloadFile(@PathVariable Long id)
  ```

- [ ] 파일 저장 경로 설정
  ```yaml
  file:
    storage:
      location: ./uploads
      max-size: 100MB
  ```

#### 프론트엔드 작업
- [ ] 다운로드 버튼 추가 (업로드 이력 페이지)
- [ ] 다운로드 진행률 표시
- [ ] 파일명 자동 설정 (원본 파일명)

#### 테스트
- [ ] 파일 저장 테스트
- [ ] 파일 다운로드 테스트
- [ ] 대용량 파일 처리 테스트

---

### 5. 업로드 통계 대시보드 ⭐⭐
**예상 소요 시간:** 3-4일

#### 백엔드 작업
- [ ] 통계 데이터 집계 Service
  ```java
  public class UploadStatisticsService {
      public UploadStats getDailyStats(LocalDate date);
      public UploadStats getMonthlyStats(YearMonth month);
      public Map<FileFormat, Long> getFormatDistribution();
      public SuccessRateStats getSuccessRate();
  }
  ```

- [ ] 통계 API 엔드포인트
  ```java
  @GetMapping("/statistics/daily")
  @GetMapping("/statistics/monthly")
  @GetMapping("/statistics/format-distribution")
  @GetMapping("/statistics/success-rate")
  ```

- [ ] 통계 데이터 Export API
  ```java
  @GetMapping("/statistics/export")
  public ResponseEntity<byte[]> exportStatistics(
      @RequestParam String format // "csv" or "json"
  )
  ```

#### 프론트엔드 작업
- [ ] 대시보드 페이지 생성 (`/dashboard`)
- [ ] Chart.js를 활용한 차트 구현
  - 📊 일별 업로드 추이 (선 그래프)
  - 📈 월별 업로드 추이 (막대 그래프)
  - 🥧 파일 포맷 분포 (파이 차트)
  - 📉 성공/실패 비율 (도넛 차트)

- [ ] 통계 카드 구현
  ```
  ┌──────────────┬──────────────┬──────────────┐
  │ 전체 업로드  │ 오늘 업로드  │ 성공률       │
  │   1,234      │     45       │   98.5%      │
  └──────────────┴──────────────┴──────────────┘
  ```

- [ ] Export 버튼 구현
  - CSV 다운로드
  - JSON 다운로드

#### 테스트
- [ ] 통계 집계 로직 테스트
- [ ] 차트 렌더링 테스트
- [ ] Export 기능 테스트

---

### 6. 배치 업로드 기능 ⭐
**예상 소요 시간:** 3-4일

#### 백엔드 작업
- [ ] 배치 업로드 엔드포인트
  ```java
  @PostMapping("/batch-upload")
  public ResponseEntity<BatchUploadResponse> batchUpload(
      @RequestParam("files") List<MultipartFile> files
  )
  ```

- [ ] 비동기 처리 구현
  ```java
  @Async
  public CompletableFuture<FileUploadResult> processFileAsync(
      MultipartFile file
  )
  ```

- [ ] 배치 업로드 진행 상태 관리
  ```java
  public class BatchUploadProgress {
      private int totalFiles;
      private int processedFiles;
      private int successCount;
      private int failureCount;
      private List<String> errors;
  }
  ```

#### 프론트엔드 작업
- [ ] 드래그 앤 드롭 UI 구현
  ```javascript
  dropZone.addEventListener('drop', (e) => {
      e.preventDefault();
      const files = Array.from(e.dataTransfer.files);
      handleBatchUpload(files);
  });
  ```

- [ ] 파일 목록 표시
  - 선택된 파일 리스트
  - 각 파일별 진행 상태 표시
  - 개별 파일 제거 버튼

- [ ] 전체 진행률 표시
  ```
  ┌─────────────────────────────────────────────┐
  │ 배치 업로드 진행 중... (3/5)                │
  │ ████████████░░░░░░░░░░░░░ 60%               │
  │                                             │
  │ ✅ file1.ldif - 완료                        │
  │ ✅ file2.ldif - 완료                        │
  │ ⏳ file3.ldif - 업로드 중... 45%            │
  │ ⏸️ file4.ldif - 대기 중                     │
  │ ⏸️ file5.ldif - 대기 중                     │
  └─────────────────────────────────────────────┘
  ```

- [ ] 일시정지/재개/취소 버튼

#### 테스트
- [ ] 배치 업로드 테스트
- [ ] 대용량 파일 배치 업로드 테스트
- [ ] 오류 처리 테스트

---

## 🟢 우선순위 3 (Low Priority) - 향후 계획

### 7. 파일 비교 기능
**예상 소요 시간:** 4-5일

- [ ] 파일 비교 알고리즘 구현
- [ ] 비교 결과 UI 구현
- [ ] Delta 파일 분석 기능

### 8. 알림 시스템
**예상 소요 시간:** 2-3일

- [ ] 브라우저 알림 API 통합
- [ ] 이메일 알림 설정 (SMTP)
- [ ] 알림 템플릿 작성

### 9. API 문서화
**예상 소요 시간:** 1-2일

- [ ] Swagger/OpenAPI 설정
- [ ] API 엔드포인트 문서화
- [ ] 예제 코드 작성

---

## 🔧 기술 개선 사항

### 성능 최적화
- [ ] 파일 업로드 스트리밍 처리
- [ ] 파일 해시 계산 비동기 처리
- [ ] 데이터베이스 인덱스 최적화
- [ ] Redis 캐싱 도입
  - 업로드 통계 캐싱
  - 최근 업로드 목록 캐싱

### 보안 강화
- [ ] 파일 업로드 권한 관리 (Spring Security)
- [ ] 파일 타입 화이트리스트 검증
- [ ] 파일 크기 제한 강제
- [ ] 악성 파일 스캔 통합 (ClamAV)
- [ ] CSRF 토큰 검증 강화

### 모니터링 및 로깅
- [ ] Actuator 설정
- [ ] Prometheus 메트릭 수집
- [ ] Grafana 대시보드 구성
- [ ] 구조화된 로깅 (JSON 로그)
- [ ] 에러 트래킹 (Sentry)

### 테스트 커버리지 향상
- [ ] 단위 테스트 커버리지 80% 이상
- [ ] 통합 테스트 작성
- [ ] E2E 테스트 자동화
- [ ] 성능 테스트 (JMeter)

---

## 📅 Sprint 계획

### Sprint 1 (1주차)
- ✅ 파일 업로드 이력 추적 시스템 구축
- ✅ Flyway 마이그레이션 완료
- ✅ UI/UX 개선 (LDIF, ML 페이지)

### Sprint 2 (2주차) - **현재**
- 🔲 파일 업로드 이력 조회 페이지 구현
- 🔲 중복 파일 업로드 처리 UI
- 🔲 체크섬 검증 결과 표시 UI

### Sprint 3 (3주차)
- 🔲 파일 스토리지 및 다운로드 기능
- 🔲 업로드 통계 대시보드
- 🔲 배치 업로드 기능 (시작)

### Sprint 4 (4주차)
- 🔲 배치 업로드 기능 (완료)
- 🔲 성능 최적화
- 🔲 보안 강화

---

## 📝 참고 사항

### 기술 스택
- **Backend:** Spring Boot 3.x, Java 17+
- **Database:** PostgreSQL 15.x
- **Migration:** Flyway
- **Frontend:** Thymeleaf, HTMX, Tailwind CSS, Chart.js
- **Real-time:** Server-Sent Events (SSE)
- **Storage:** Local FileSystem (향후 S3)

### 코딩 컨벤션
- Java: Google Java Style Guide
- JavaScript: Airbnb JavaScript Style Guide
- SQL: 대문자 키워드, snake_case 컬럼명

### Git 브랜치 전략
- `main`: 프로덕션 브랜치
- `develop`: 개발 브랜치
- `feature/*`: 기능 개발 브랜치
- `hotfix/*`: 긴급 수정 브랜치

### 커밋 메시지 컨벤션
```
feat: 새로운 기능 추가
fix: 버그 수정
docs: 문서 수정
style: 코드 포맷팅
refactor: 코드 리팩토링
test: 테스트 코드
chore: 빌드 설정, 패키지 매니저 설정
```

---

**최종 업데이트:** 2025-10-17
**다음 리뷰 예정:** 2025-10-24
