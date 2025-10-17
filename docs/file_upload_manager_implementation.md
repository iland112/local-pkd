# File Upload Manager 시스템 설계 및 구현

## 📋 목차
- [프로젝트 개요](#프로젝트-개요)
- [시스템 아키텍처](#시스템-아키텍처)
- [데이터베이스 설계](#데이터베이스-설계)
- [구현 내역](#구현-내역)
- [UI/UX 개선](#uiux-개선)
- [다음 작업 (TODO)](#다음-작업-todo)

---

## 프로젝트 개요

### 목적
ICAO PKD (Public Key Directory) 파일을 효율적으로 관리하고 업로드 이력을 추적하는 시스템 구축

### 주요 기능
- LDIF 파일 업로드 및 파싱
- Master List 파일 업로드 및 분석
- 파일 업로드 이력 관리
- 중복 업로드 방지
- 실시간 진행률 표시 (SSE)
- 체크섬 검증

---

## 시스템 아키텍처

### 전체 구조
```
┌─────────────────────────────────────────────────────────────┐
│                         Frontend                             │
│  ┌──────────────┐           ┌──────────────┐               │
│  │ LDIF Upload  │           │  ML Upload   │               │
│  │    Page      │           │    Page      │               │
│  └──────┬───────┘           └──────┬───────┘               │
│         │                           │                        │
│         └───────────┬───────────────┘                        │
│                     │                                        │
│              ┌──────▼──────┐                                │
│              │   HTMX +    │                                │
│              │     SSE     │                                │
│              └──────┬──────┘                                │
└─────────────────────┼──────────────────────────────────────┘
                      │
┌─────────────────────▼──────────────────────────────────────┐
│                    Backend Layer                            │
│  ┌───────────────────────────────────────────────────────┐ │
│  │              FileUploadService                        │ │
│  │  - uploadFile()                                       │ │
│  │  - validateDuplicateUpload()                          │ │
│  │  - saveUploadHistory()                                │ │
│  │  - calculateChecksum()                                │ │
│  └───────────────────┬───────────────────────────────────┘ │
│                      │                                      │
│  ┌───────────────────▼───────────────────────────────────┐ │
│  │          FileUploadHistoryRepository                  │ │
│  │  - findByOriginalFileName()                           │ │
│  │  - findByFileHash()                                   │ │
│  │  - findRecentUploads()                                │ │
│  └───────────────────┬───────────────────────────────────┘ │
└────────────────────┬─┴────────────────────────────────────┘
                     │
┌────────────────────▼─────────────────────────────────────┐
│                  Database Layer                           │
│  ┌──────────────────────────────────────────────────────┐│
│  │          file_upload_history (Table)                 ││
│  │  - id (BIGSERIAL)                                    ││
│  │  - original_file_name (VARCHAR)                      ││
│  │  - file_format (VARCHAR)                             ││
│  │  - file_size (BIGINT)                                ││
│  │  - file_hash (VARCHAR) [UNIQUE]                      ││
│  │  - upload_status (VARCHAR)                           ││
│  │  - uploaded_at (TIMESTAMP)                           ││
│  │  - metadata (JSONB)                                  ││
│  └──────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────┘
```

### 기술 스택
- **Backend**: Spring Boot 3.x, Java 17+
- **Database**: PostgreSQL 15.x
- **Migration**: Flyway
- **Frontend**: Thymeleaf, HTMX, Tailwind CSS
- **Real-time**: Server-Sent Events (SSE)

---

## 데이터베이스 설계

### ERD
```
┌─────────────────────────────────────────────────────────┐
│              file_upload_history                         │
├─────────────────────────────────────────────────────────┤
│ PK  id                    BIGSERIAL                     │
│     original_file_name    VARCHAR(500)   NOT NULL       │
│     file_format          VARCHAR(50)    NOT NULL       │
│     file_type            VARCHAR(50)                    │
│     file_version         INTEGER                        │
│     file_size            BIGINT          NOT NULL       │
│     file_hash            VARCHAR(64)     UNIQUE         │
│     expected_checksum    VARCHAR(64)                    │
│     calculated_checksum  VARCHAR(64)                    │
│     checksum_matched     BOOLEAN                        │
│     upload_status        VARCHAR(50)     NOT NULL       │
│     error_message        TEXT                           │
│     uploaded_at          TIMESTAMP       NOT NULL       │
│     processed_at         TIMESTAMP                      │
│     metadata             JSONB                          │
│     created_at           TIMESTAMP       DEFAULT NOW()  │
│     updated_at           TIMESTAMP       DEFAULT NOW()  │
├─────────────────────────────────────────────────────────┤
│ Indexes:                                                │
│   - idx_file_hash                                       │
│   - idx_upload_status                                   │
│   - idx_uploaded_at                                     │
│   - idx_file_format_type                                │
└─────────────────────────────────────────────────────────┘
```

### 주요 컬럼 설명

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `id` | BIGSERIAL | 기본키 (자동 증가) |
| `original_file_name` | VARCHAR(500) | 원본 파일명 |
| `file_format` | VARCHAR(50) | 파일 포맷 (CSCA_COMPLETE_LDIF, ML_SIGNED_CMS 등) |
| `file_type` | VARCHAR(50) | 파일 타입 (COMPLETE, DELTA) |
| `file_version` | INTEGER | 파일 버전 |
| `file_size` | BIGINT | 파일 크기 (bytes) |
| `file_hash` | VARCHAR(64) | SHA-256 해시값 (중복 방지용) |
| `expected_checksum` | VARCHAR(64) | ICAO 공식 체크섬 (사용자 입력) |
| `calculated_checksum` | VARCHAR(64) | 계산된 체크섬 |
| `checksum_matched` | BOOLEAN | 체크섬 일치 여부 |
| `upload_status` | VARCHAR(50) | 업로드 상태 (PENDING, SUCCESS, FAILED) |
| `error_message` | TEXT | 오류 메시지 |
| `uploaded_at` | TIMESTAMP | 업로드 시작 시간 |
| `processed_at` | TIMESTAMP | 처리 완료 시간 |
| `metadata` | JSONB | 추가 메타데이터 (JSON 형식) |

---

## 구현 내역

### 1. Flyway 마이그레이션

#### V4__Add_File_Upload_History.sql
파일 업로드 이력을 추적하기 위한 테이블 및 인덱스 생성

**주요 기능:**
- 업로드 이력 테이블 생성
- 파일 해시 기반 중복 방지
- 체크섬 검증 기능
- JSONB를 활용한 유연한 메타데이터 저장
- 성능 최적화를 위한 인덱스 생성

**실행 결과:**
```
✅ Successfully applied 3 migrations to schema "public"
✅ Migration to version v4 completed successfully
```

### 2. Entity 클래스 구현

#### FileUploadHistory.java
```java
@Entity
@Table(name = "file_upload_history")
public class FileUploadHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_file_name", nullable = false, length = 500)
    private String originalFileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_format", nullable = false, length = 50)
    private FileFormat fileFormat;

    // ... 기타 필드
}
```

**특징:**
- JPA 어노테이션을 활용한 ORM 매핑
- Enum을 활용한 타입 안전성 확보
- 파일 메타데이터 자동 추출
- 빌더 패턴 적용

### 3. Repository 구현

#### FileUploadHistoryRepository.java
```java
public interface FileUploadHistoryRepository extends JpaRepository<FileUploadHistory, Long> {
    Optional<FileUploadHistory> findByFileHash(String fileHash);

    List<FileUploadHistory> findByOriginalFileNameAndUploadStatus(
        String originalFileName,
        UploadStatus uploadStatus
    );

    @Query("SELECT f FROM FileUploadHistory f WHERE f.uploadedAt >= :startDate")
    List<FileUploadHistory> findRecentUploads(@Param("startDate") LocalDateTime startDate);
}
```

**주요 메서드:**
- 파일 해시 기반 중복 검사
- 업로드 상태별 조회
- 최근 업로드 이력 조회

### 4. Service 레이어 구현

#### FileUploadService.java
```java
@Service
@Transactional
public class FileUploadService {

    public FileUploadHistory uploadFile(
        MultipartFile file,
        String expectedChecksum
    ) throws IOException {
        // 1. 파일 해시 계산
        String fileHash = calculateFileHash(file);

        // 2. 중복 검사
        validateDuplicateUpload(fileHash);

        // 3. 파일 메타데이터 추출
        FileMetadata metadata = extractMetadata(file.getOriginalFilename());

        // 4. 체크섬 검증 (선택적)
        boolean checksumMatched = verifyChecksum(file, expectedChecksum);

        // 5. 업로드 이력 저장
        return saveUploadHistory(file, fileHash, metadata, checksumMatched);
    }
}
```

**핵심 기능:**
- SHA-256 기반 파일 해시 계산
- 파일명 패턴 매칭을 통한 메타데이터 추출
- 중복 업로드 방지
- 체크섬 자동 검증

### 5. 예외 처리

#### 커스텀 예외 클래스
```java
// 중복 파일 업로드 예외
public class DuplicateFileUploadException extends RuntimeException {
    private final FileUploadHistory existingUpload;
}

// 지원하지 않는 파일 포맷 예외
public class UnsupportedFileFormatException extends RuntimeException {
    private final String fileName;
}
```

---

## UI/UX 개선

### 1. LDIF 페이지 개선 내역

#### 자동 알림 숨김
```javascript
function setupAutoHideAlerts() {
    const alerts = document.querySelectorAll(".auto-hide-alert");
    alerts.forEach((alert) => {
        const isError = alert.classList.contains("border-red-500");
        const timeout = isError ? 10000 : 5000;
        setTimeout(() => fadeOutElement(alert), timeout);
    });
}
```

**특징:**
- 성공 메시지: 5초 후 자동 숨김
- 오류 메시지: 10초 후 자동 숨김
- 부드러운 페이드 아웃 효과

#### 파일 정보 미리보기
```javascript
function parseAndDisplayFileMetadata(filename) {
    // icaopkd-{collection}-{type}-{version}.ldif 패턴 매칭
    const ldifPattern = /icaopkd-(\d{3})-(complete|delta)-(\d+)\.ldif/i;
    const match = filename.toLowerCase().match(ldifPattern);

    if (match) {
        // Collection, Type, Version 정보 표시
        displayFilePreview(match[1], match[2], match[3]);
    }
}
```

**기능:**
- 파일명에서 자동으로 Collection, Type, Version 추출
- 시각적 배지로 정보 표시
- 비표준 파일명 경고

#### 개선된 파일 크기 검증
```javascript
function setupFileSizeValidation() {
    const maxSize = 100 * 1024 * 1024; // 100MB

    fileInput.addEventListener("change", function () {
        if (file.size > maxSize) {
            showFileError(`파일이 너무 큽니다. 최대 크기는 ${formatFileSize(maxSize)}입니다.`);
            this.value = "";
            return;
        }
    });
}
```

#### SSE 연결 관리 개선
```javascript
let sseRetryCount = 0;
let maxRetryAttempts = 3;

function handleParsingSSEError(event) {
    sseRetryCount++;

    if (sseRetryCount <= maxRetryAttempts) {
        // 재연결 시도
        setTimeout(() => attemptSSEReconnection(), 2000);
    } else {
        // 최대 재시도 횟수 초과
        showReconnectionFailedMessage();
    }
}
```

**특징:**
- 최대 3회 자동 재연결 시도
- 2초 간격으로 재연결
- 사용자에게 상태 피드백

#### 페이지 가시성 처리
```javascript
document.addEventListener("visibilitychange", function() {
    if (document.hidden) {
        console.log("Page hidden - SSE may be paused by browser");
    } else {
        console.log("Page visible - SSE should resume");
        if (isParsingInProgress && sseRetryCount < maxRetryAttempts) {
            setTimeout(() => attemptSSEReconnection(), 1000);
        }
    }
});
```

**기능:**
- 페이지가 백그라운드로 이동 시 감지
- 다시 포그라운드로 돌아올 때 SSE 연결 재확인
- 파싱 중일 경우 자동 재연결

### 2. Master List 페이지 개선

ML 페이지에도 LDIF 페이지와 동일한 개선사항 적용:

- ✅ 자동 알림 숨김 기능
- ✅ 개선된 파일 크기 검증 (100MB 제한)
- ✅ SSE 재연결 로직 (최대 3회 시도)
- ✅ 페이지 가시성 변경 처리
- ✅ 부드러운 페이드 아웃 효과
- ✅ 개선된 에러 처리

### 3. 공통 UI/UX 개선사항

#### CSS 애니메이션
```css
@keyframes fadeIn {
    from {
        opacity: 0;
        transform: translateY(10px);
    }
    to {
        opacity: 1;
        transform: translateY(0);
    }
}

.animate-fadeIn {
    animation: fadeIn 0.3s ease-out;
}
```

#### 반응형 디자인
```css
@media (max-width: 768px) {
    .grid {
        grid-template-columns: 1fr;
    }
    .container {
        padding: 1rem;
    }
}
```

---

## 다음 작업 (TODO)

### 🔴 우선순위 높음 (High Priority)

#### 1. 파일 업로드 이력 조회 UI 구현
**목표:** 업로드된 파일 목록을 확인할 수 있는 페이지 구현

**작업 내용:**
- [ ] 업로드 이력 조회 페이지 생성 (`/upload-history`)
- [ ] 페이지네이션 구현 (한 페이지당 20개)
- [ ] 필터링 기능 추가
  - 파일 포맷별 필터 (LDIF, Master List)
  - 업로드 상태별 필터 (성공, 실패, 진행 중)
  - 날짜 범위 필터
- [ ] 정렬 기능 (업로드 날짜, 파일명, 파일 크기)
- [ ] 상세 정보 모달 구현

**예상 UI 구조:**
```
┌─────────────────────────────────────────────────────────┐
│  파일 업로드 이력                              [새로고침] │
├─────────────────────────────────────────────────────────┤
│  필터: [모든 포맷 ▼] [모든 상태 ▼] [날짜 범위]         │
├─────────────────────────────────────────────────────────┤
│  파일명            | 포맷 | 크기 | 상태 | 업로드 시간  │
│  icaopkd-001-...  | LDIF | 45MB | 성공 | 2025-10-17... │
│  masterlist.ml    | ML   | 2MB  | 성공 | 2025-10-16... │
│  ...                                                     │
├─────────────────────────────────────────────────────────┤
│  < 이전  1 2 3 4 5  다음 >                              │
└─────────────────────────────────────────────────────────┘
```

#### 2. 중복 파일 업로드 처리 UI
**목표:** 중복 파일 업로드 시 사용자에게 명확한 피드백 제공

**작업 내용:**
- [ ] 중복 파일 감지 시 경고 모달 표시
- [ ] 기존 업로드 정보 표시
  - 이전 업로드 날짜
  - 업로드 상태
  - 파일 메타데이터 비교
- [ ] 사용자 선택 옵션 제공
  - 업로드 취소
  - 강제 재업로드 (기존 이력 유지)
  - 기존 이력 삭제 후 새로 업로드

**예상 UI:**
```
┌───────────────────────────────────────────────────┐
│  ⚠️  중복 파일 감지                                │
├───────────────────────────────────────────────────┤
│  이 파일은 이전에 업로드된 적이 있습니다.         │
│                                                   │
│  📄 파일명: icaopkd-001-complete-20240315.ldif    │
│  📅 이전 업로드: 2025-10-15 14:30:22              │
│  ✅ 상태: 업로드 성공                             │
│  🔒 해시: 82f810600166442...                      │
│                                                   │
│  어떻게 처리하시겠습니까?                         │
│                                                   │
│  [취소]  [강제 재업로드]  [기존 이력 보기]        │
└───────────────────────────────────────────────────┘
```

#### 3. 체크섬 검증 결과 표시
**목표:** ICAO 공식 체크섬과 계산된 체크섬 비교 결과를 시각적으로 표시

**작업 내용:**
- [ ] 체크섬 검증 결과 섹션 추가
- [ ] 일치 시 성공 메시지 표시
- [ ] 불일치 시 경고 메시지 및 상세 정보 표시
- [ ] 체크섬 값 복사 버튼 추가

**예상 UI:**
```
┌───────────────────────────────────────────────────┐
│  🔐 체크섬 검증 결과                               │
├───────────────────────────────────────────────────┤
│  예상 체크섬 (ICAO):                               │
│  82f8106001664427a7d686017aa49dc3fd3722f1  [복사] │
│                                                   │
│  계산된 체크섬:                                    │
│  82f8106001664427a7d686017aa49dc3fd3722f1  [복사] │
│                                                   │
│  ✅ 체크섬이 일치합니다!                           │
│  이 파일은 ICAO 공식 파일과 동일합니다.           │
└───────────────────────────────────────────────────┘
```

### 🟡 우선순위 중간 (Medium Priority)

#### 4. 파일 다운로드 기능
**작업 내용:**
- [ ] 업로드된 파일 다운로드 기능 추가
- [ ] 파일 스토리지 구현 (로컬 또는 S3)
- [ ] 다운로드 권한 관리

#### 5. 업로드 통계 대시보드
**작업 내용:**
- [ ] 업로드 통계 페이지 생성
- [ ] 차트를 활용한 시각화
  - 일별/월별 업로드 추이
  - 파일 포맷별 분포
  - 성공/실패 비율
- [ ] 통계 데이터 Export 기능 (CSV, JSON)

#### 6. 배치 업로드 기능
**작업 내용:**
- [ ] 여러 파일 동시 업로드 지원
- [ ] 드래그 앤 드롭 UI 구현
- [ ] 업로드 큐 관리
- [ ] 전체 진행률 표시

### 🟢 우선순위 낮음 (Low Priority)

#### 7. 파일 비교 기능
**작업 내용:**
- [ ] 두 파일 간 차이점 분석
- [ ] Delta 파일과 Complete 파일 비교
- [ ] 변경 사항 하이라이트

#### 8. 알림 기능
**작업 내용:**
- [ ] 업로드 완료 알림 (브라우저 알림)
- [ ] 이메일 알림 설정
- [ ] 실패 시 관리자 알림

#### 9. API 문서화
**작업 내용:**
- [ ] Swagger/OpenAPI 적용
- [ ] REST API 문서 작성
- [ ] API 사용 예제 작성

---

## 기술적 개선 사항

### 성능 최적화
- [ ] 대용량 파일 처리 최적화 (스트리밍)
- [ ] 파일 해시 계산 비동기 처리
- [ ] 데이터베이스 쿼리 최적화
- [ ] 캐싱 전략 수립 (Redis)

### 보안 강화
- [ ] 파일 업로드 권한 관리
- [ ] 파일 타입 검증 강화
- [ ] 악성 파일 스캔 통합
- [ ] CSRF 토큰 검증

### 모니터링
- [ ] 업로드 성공률 모니터링
- [ ] 에러 로그 수집 및 분석
- [ ] 성능 메트릭 수집
- [ ] 알림 시스템 구축

---

## 결론

현재까지 구축된 File Upload Manager 시스템은 다음과 같은 핵심 기능을 제공합니다:

✅ **완료된 기능:**
- 파일 업로드 이력 추적
- 중복 업로드 방지
- 체크섬 검증
- 실시간 진행률 표시
- 파일 메타데이터 자동 추출
- 개선된 UI/UX (자동 알림, SSE 재연결, 페이드 효과)

🚧 **개발 중:**
- 업로드 이력 조회 UI
- 중복 파일 처리 UI
- 체크섬 검증 결과 표시

📋 **계획 중:**
- 파일 다운로드
- 통계 대시보드
- 배치 업로드
- 파일 비교

이 시스템은 확장 가능하고 유지보수가 용이한 구조로 설계되었으며, 향후 요구사항에 따라 지속적으로 개선될 예정입니다.

---

**문서 작성일:** 2025-10-17
**작성자:** Development Team
**버전:** 1.0.0
