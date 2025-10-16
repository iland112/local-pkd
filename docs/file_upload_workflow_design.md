# ICAO PKD 파일 업로드 워크플로우 설계

**작성일**: 2025-10-16
**목적**: 사용자가 ICAO PKD에서 수동 다운로드한 파일을 시스템에 업로드하는 워크플로우 설계

---

## 1. 사용자 시나리오

### 1.1 현재 제약사항
- ✅ ICAO PKD 다운로드 페이지는 CAPTCHA 보호
- ✅ 자동 다운로드 불가능
- ✅ 사용자가 수동으로 파일 다운로드 필요

### 1.2 업로드 프로세스
```
[ICAO PKD 웹사이트]
        ↓ (수동 다운로드)
[사용자 로컬 PC]
        ↓ (웹 업로드)
[Local PKD 시스템]
        ↓ (검증 & 처리)
[OpenLDAP + PostgreSQL]
```

---

## 2. 파일 업로드 워크플로우 상세 설계

### 2.1 Phase 1: 파일 수신 및 기본 검증

```java
public class FileUploadService {

    /**
     * 1단계: 파일 수신
     * - 파일명 검증 (FileFormat.isValidFilename)
     * - 파일 크기 검증 (최대 100MB)
     * - 파일 확장자 검증 (.ldif, .ml)
     */
    public FileMetadata receiveFile(MultipartFile file) {
        // 파일명으로부터 메타데이터 추출
        FileMetadata metadata = FileMetadata.fromFilename(file.getOriginalFilename());

        // 파일 크기 설정
        metadata.setFileSizeBytes(file.getSize());
        metadata.setFileSizeDisplay(FileMetadata.formatFileSize(file.getSize()));

        // 로컬 저장
        String localPath = saveToTemporary(file);
        metadata.setLocalFilePath(localPath);
        metadata.setDownloadedAt(LocalDateTime.now());

        return metadata;
    }
}
```

### 2.2 Phase 2: 체크섬 검증

```java
/**
 * 2단계: SHA-1 체크섬 계산 및 검증
 * - 업로드된 파일의 SHA-1 계산
 * - ICAO 공식 체크섬과 비교 (선택적)
 * - 검증 결과 기록
 */
public ChecksumValidationResult validateChecksum(FileMetadata metadata) {
    // SHA-1 계산
    String calculatedChecksum = calculateSHA1(metadata.getLocalFilePath());
    metadata.setSha1Checksum(calculatedChecksum);

    // ICAO 공식 체크섬과 비교 (사용자가 입력한 경우)
    if (metadata.getExpectedChecksum() != null) {
        boolean matches = calculatedChecksum.equals(metadata.getExpectedChecksum());
        return new ChecksumValidationResult(matches, calculatedChecksum);
    }

    return ChecksumValidationResult.notValidated(calculatedChecksum);
}
```

### 2.3 Phase 3: 중복 및 버전 관리

```java
/**
 * 3단계: 중복 검사 및 버전 비교
 * - 동일 파일명 존재 여부 확인
 * - 버전 비교 (새로운 버전인지)
 * - 중복 파일 처리 정책 적용
 */
public DuplicateCheckResult checkDuplicate(FileMetadata metadata) {
    // 데이터베이스에서 동일 Collection의 최신 버전 조회
    FileMetadata latestVersion = fileRepository.findLatestByCollection(
        metadata.getCollectionNumber()
    );

    if (latestVersion == null) {
        return DuplicateCheckResult.newFile();
    }

    // 버전 비교
    int versionComparison = compareVersions(
        metadata.getVersion(),
        latestVersion.getVersion()
    );

    if (versionComparison > 0) {
        return DuplicateCheckResult.newerVersion(latestVersion);
    } else if (versionComparison == 0) {
        // 체크섬으로 정확히 동일한 파일인지 확인
        if (metadata.getSha1Checksum().equals(latestVersion.getSha1Checksum())) {
            return DuplicateCheckResult.exactDuplicate(latestVersion);
        } else {
            return DuplicateCheckResult.sameVersionDifferentContent(latestVersion);
        }
    } else {
        return DuplicateCheckResult.olderVersion(latestVersion);
    }
}
```

### 2.4 Phase 4: 파일 파싱 및 저장

```java
/**
 * 4단계: 파일 파싱 및 데이터 저장
 * - LDIF: LdifCompleteParser 또는 LdifDeltaParser 사용
 * - ML: MlSignedCmsParser 사용
 * - OpenLDAP에 저장
 * - PostgreSQL에 메타데이터 저장
 */
public ParseResult parseAndStore(FileMetadata metadata) {
    ParseResult result;

    if (metadata.isLdif()) {
        if (metadata.isDelta()) {
            result = ldifDeltaParser.parse(metadata.getLocalFilePath());
        } else {
            result = ldifCompleteParser.parse(metadata.getLocalFilePath());
        }
    } else if (metadata.isSignedCms()) {
        result = mlSignedCmsParser.parse(metadata.getLocalFilePath());
    }

    // 파싱 성공 시 영구 저장소로 이동
    if (result.isSuccess()) {
        String permanentPath = moveToPermanentStorage(metadata);
        metadata.setLocalFilePath(permanentPath);

        // 메타데이터 DB 저장
        fileMetadataRepository.save(metadata);
    }

    return result;
}
```

---

## 3. 데이터 모델 확장

### 3.1 FileUploadHistory (업로드 이력)

```java
@Entity
@Table(name = "file_upload_history")
public class FileUploadHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 파일 정보
    private String filename;
    private String collectionNumber;
    private String version;
    private FileFormat fileFormat;

    // 업로드 정보
    private LocalDateTime uploadedAt;
    private String uploadedBy;  // 사용자 ID

    // 검증 정보
    private String calculatedChecksum;
    private String expectedChecksum;
    private Boolean checksumValid;

    // 처리 결과
    private UploadStatus status;  // PENDING, VALIDATING, PARSING, SUCCESS, FAILED
    private String errorMessage;
    private Integer entriesProcessed;
    private Integer entriesFailed;

    // 중복 체크 결과
    private Boolean isDuplicate;
    private Boolean isNewerVersion;
    private Long replacedFileId;  // 대체된 이전 파일 ID
}
```

### 3.2 UploadStatus Enum

```java
public enum UploadStatus {
    RECEIVED("파일 수신 완료"),
    VALIDATING("파일 검증 중"),
    CHECKSUM_INVALID("체크섬 불일치"),
    DUPLICATE_DETECTED("중복 파일 감지"),
    PARSING("파싱 진행 중"),
    STORING("데이터 저장 중"),
    SUCCESS("처리 완료"),
    FAILED("처리 실패"),
    ROLLBACK("롤백됨");

    private final String description;
}
```

---

## 4. 웹 UI 개선사항

### 4.1 업로드 페이지 추가 기능

#### A. 체크섬 입력 (선택적)
```html
<!-- ICAO 공식 체크섬 입력 (선택 사항) -->
<div class="mb-4">
    <label class="block text-sm font-medium text-gray-700 mb-2">
        <i class="fas fa-fingerprint text-blue-500 mr-2"></i>
        ICAO 공식 SHA-1 체크섬 (선택사항)
    </label>
    <input
        type="text"
        name="expectedChecksum"
        placeholder="82f8106001664427a7d686017aa49dc3fd3722f1"
        pattern="[a-fA-F0-9]{40}"
        class="input input-bordered w-full"
    />
    <p class="mt-1 text-xs text-gray-500">
        ICAO PKD 다운로드 페이지의 Checksum을 입력하면 자동 검증됩니다.
    </p>
</div>
```

#### B. 파일 정보 미리보기
```html
<!-- 업로드 전 파일 정보 표시 -->
<div id="file-preview" class="mt-4 p-4 bg-blue-50 rounded-lg hidden">
    <h4 class="font-semibold text-blue-800 mb-2">파일 정보 미리보기</h4>
    <dl class="grid grid-cols-2 gap-2 text-sm">
        <dt class="text-gray-600">Collection:</dt>
        <dd id="preview-collection" class="font-medium"></dd>

        <dt class="text-gray-600">Format:</dt>
        <dd id="preview-format" class="font-medium"></dd>

        <dt class="text-gray-600">Version:</dt>
        <dd id="preview-version" class="font-medium"></dd>

        <dt class="text-gray-600">Type:</dt>
        <dd id="preview-type" class="font-medium"></dd>
    </dl>
</div>
```

#### C. 중복 파일 경고
```html
<!-- 중복 파일 감지 시 표시 -->
<div class="alert alert-warning" th:if="${duplicateWarning}">
    <i class="fas fa-exclamation-triangle"></i>
    <div>
        <h4 class="font-bold">중복 파일 감지</h4>
        <p th:text="${duplicateWarning.message}"></p>
        <div class="mt-2">
            <button class="btn btn-sm btn-warning" onclick="forceUpload()">
                강제 업로드
            </button>
            <button class="btn btn-sm btn-ghost" onclick="cancelUpload()">
                취소
            </button>
        </div>
    </div>
</div>
```

### 4.2 업로드 대시보드

```html
<!-- 최근 업로드 파일 목록 -->
<div class="card bg-base-100 shadow-xl mt-6">
    <div class="card-body">
        <h2 class="card-title">
            <i class="fas fa-history text-blue-600"></i>
            최근 업로드 파일
        </h2>

        <div class="overflow-x-auto">
            <table class="table table-zebra w-full">
                <thead>
                    <tr>
                        <th>파일명</th>
                        <th>Collection</th>
                        <th>Version</th>
                        <th>크기</th>
                        <th>업로드 시간</th>
                        <th>상태</th>
                        <th>작업</th>
                    </tr>
                </thead>
                <tbody>
                    <tr th:each="file : ${recentUploads}">
                        <td th:text="${file.filename}"></td>
                        <td>
                            <span class="badge" th:text="${file.collectionCategory}"></span>
                        </td>
                        <td th:text="${file.version}"></td>
                        <td th:text="${file.fileSizeDisplay}"></td>
                        <td th:text="${#temporals.format(file.uploadedAt, 'yyyy-MM-dd HH:mm')}"></td>
                        <td>
                            <span class="badge badge-success" th:if="${file.status == 'SUCCESS'}">
                                <i class="fas fa-check mr-1"></i> 완료
                            </span>
                            <span class="badge badge-error" th:if="${file.status == 'FAILED'}">
                                <i class="fas fa-times mr-1"></i> 실패
                            </span>
                            <span class="badge badge-warning" th:if="${file.status == 'PARSING'}">
                                <i class="fas fa-spinner animate-spin mr-1"></i> 처리 중
                            </span>
                        </td>
                        <td>
                            <button class="btn btn-xs btn-ghost" onclick="viewDetails(${file.id})">
                                <i class="fas fa-eye"></i> 상세
                            </button>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    </div>
</div>
```

---

## 5. 구현 우선순위

### Phase 1: 기본 기능 (필수)
1. ✅ FileMetadata 도메인 클래스 (완료)
2. 🔲 FileUploadHistory 엔티티
3. 🔲 SHA-1 체크섬 계산 유틸리티
4. 🔲 기본 업로드 서비스 구현

### Phase 2: 검증 기능
5. 🔲 체크섬 검증 로직
6. 🔲 중복 파일 감지
7. 🔲 버전 비교 로직
8. 🔲 파일 무결성 검증

### Phase 3: UI 개선
9. 🔲 파일 정보 미리보기
10. 🔲 체크섬 입력 필드 추가
11. 🔲 업로드 이력 대시보드
12. 🔲 중복 파일 경고 UI

### Phase 4: 고급 기능
13. 🔲 Delta 파일 순차 적용 로직
14. 🔲 롤백 기능
15. 🔲 파일 비교 기능
16. 🔲 업로드 스케줄러 (선택적)

---

## 6. 보안 고려사항

### 6.1 파일 업로드 보안
- ✅ 파일 크기 제한 (100MB)
- ✅ 파일 확장자 검증 (.ldif, .ml)
- 🔲 MIME 타입 검증
- 🔲 안티바이러스 스캔 (선택적)
- 🔲 업로드 속도 제한

### 6.2 데이터 무결성
- ✅ SHA-1 체크섬 검증
- 🔲 파일 서명 검증 (.ml 파일)
- 🔲 LDIF 구문 검증
- 🔲 트랜잭션 관리 (롤백 가능)

### 6.3 접근 제어
- 🔲 사용자 인증 (Spring Security)
- 🔲 업로드 권한 관리
- 🔲 감사 로그 (audit log)

---

## 7. 사용자 가이드 추가

### 7.1 업로드 가이드 페이지
```markdown
# ICAO PKD 파일 업로드 가이드

## 1단계: ICAO PKD에서 파일 다운로드
1. https://pkddownloadsg.icao.int/download 접속
2. CAPTCHA 입력하여 페이지 진입
3. 원하는 파일의 "Download" 버튼 클릭
4. 파일 다운로드 시 **Checksum 값을 복사**해두세요

## 2단계: Local PKD 시스템에 업로드
1. 시스템 업로드 페이지로 이동
2. "파일 선택" 버튼 클릭
3. (선택사항) ICAO Checksum 입력
4. "파일 업로드 및 분석" 버튼 클릭

## 3단계: 업로드 결과 확인
- 실시간 진행률 확인
- 체크섬 검증 결과 확인
- 파싱 결과 및 통계 확인
```

---

## 8. 다음 단계

1. **FileUploadHistory 엔티티 생성**
2. **FileUploadService 구현**
3. **ChecksumValidator 유틸리티 생성**
4. **업로드 페이지 UI 개선**
5. **업로드 대시보드 구현**

---

## 참고 자료
- [ICAO PKD Download](https://pkddownloadsg.icao.int/download)
- [FileMetadata.java](../src/main/java/com/smartcoreinc/localpkd/common/domain/FileMetadata.java)
- [FileFormat.java](../src/main/java/com/smartcoreinc/localpkd/common/enums/FileFormat.java)
