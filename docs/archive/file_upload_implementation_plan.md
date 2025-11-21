# 파일 업로드 기능 구현 계획

**작성일**: 2025-10-17
**우선순위**: 높음 (High Priority)
**예상 소요 시간**: 4-6시간

---

## 📋 목차

1. [구현 개요](#구현-개요)
2. [아키텍처 설계](#아키텍처-설계)
3. [구현 상세](#구현-상세)
4. [파일 저장 전략](#파일-저장-전략)
5. [구현 순서](#구현-순서)
6. [테스트 계획](#테스트-계획)

---

## 구현 개요

### 목적
LDIF 및 Master List 파일의 실제 업로드 기능을 구현하여 중복 체크 시스템과 통합

### 범위
- **LDIF 파일 업로드**: POST /ldif/upload
- **ML 파일 업로드**: POST /masterlist/upload
- 파일 저장 및 메타데이터 추출
- 업로드 이력 생성
- 중복 체크 연동
- 기본적인 에러 처리

### 제외 사항 (향후 구현)
- 파일 파싱 로직 (LDIF/ML 내용 분석)
- X.509 인증서 검증
- OpenLDAP 저장
- SSE 실시간 진행 상황 (기본 구조만 구현)

---

## 아키텍처 설계

### 컴포넌트 구조

```
┌─────────────────────────────────────────────┐
│      Presentation Layer (Thymeleaf)        │
│  - upload-ldif.html (기존)                │
│  - upload-ml.html (기존)                   │
└─────────────────────────────────────────────┘
                    ↓ HTMX POST
┌─────────────────────────────────────────────┐
│         Controller Layer (NEW)              │
│  - LdifUploadController                    │
│  - MasterListUploadController              │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│          Service Layer (NEW/UPDATE)         │
│  - FileUploadService (업데이트)            │
│  - FileStorageService (신규)               │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│       Repository Layer (기존)               │
│  - FileUploadHistoryRepository             │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│      File System + Database                 │
│  - ./data/uploads/{collection}/{version}/  │
│  - file_upload_history 테이블              │
└─────────────────────────────────────────────┘
```

### 데이터 플로우

```
사용자 파일 선택
    ↓
프론트엔드 중복 체크 (기존)
    ↓
파일 업로드 (POST)
    ↓
Controller: 파일 수신
    ├─ 유효성 검사
    ├─ SHA-256 해시 계산
    └─ 중복 체크 (forceUpload 확인)
    ↓
Service: 파일 저장
    ├─ 파일 시스템에 저장
    ├─ 메타데이터 추출
    └─ 이력 생성
    ↓
Response: 성공/실패
    ├─ 성공 → 결과 페이지
    └─ 실패 → 에러 메시지
```

---

## 구현 상세

### 1. LdifUploadController

**파일 경로**: `/src/main/java/com/smartcoreinc/localpkd/controller/LdifUploadController.java`

```java
package com.smartcoreinc.localpkd.controller;

import com.smartcoreinc.localpkd.common.entity.FileUploadHistory;
import com.smartcoreinc.localpkd.common.enums.FileFormat;
import com.smartcoreinc.localpkd.common.enums.UploadStatus;
import com.smartcoreinc.localpkd.service.FileStorageService;
import com.smartcoreinc.localpkd.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/ldif")
@RequiredArgsConstructor
public class LdifUploadController {

    private final FileUploadService fileUploadService;
    private final FileStorageService fileStorageService;

    /**
     * LDIF 업로드 페이지 표시
     */
    @GetMapping("/upload")
    public String showUploadPage(Model model) {
        return "ldif/upload-ldif";
    }

    /**
     * LDIF 파일 업로드 처리
     */
    @PostMapping("/upload")
    public String uploadLdif(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "forceUpload", defaultValue = "false") boolean forceUpload,
            @RequestParam(value = "expectedChecksum", required = false) String expectedChecksum,
            Model model
    ) {
        log.info("LDIF file upload started: filename={}, size={}, forceUpload={}",
                 file.getOriginalFilename(), file.getSize(), forceUpload);

        try {
            // 1. 파일 유효성 검사
            validateFile(file);

            // 2. 파일 해시 계산
            String fileHash = fileStorageService.calculateFileHash(file);
            log.debug("File hash calculated: {}", fileHash);

            // 3. 중복 체크 (forceUpload가 false인 경우)
            if (!forceUpload) {
                Optional<FileUploadHistory> existingFile =
                    fileUploadService.findByFileHash(fileHash);

                if (existingFile.isPresent()) {
                    log.warn("Duplicate file detected: hash={}", fileHash);
                    model.addAttribute("error", "중복된 파일입니다. 이미 업로드된 파일입니다.");
                    return "ldif/upload-ldif";
                }
            }

            // 4. 파일 저장
            String savedPath = fileStorageService.saveFile(file, FileFormat.CSCA_COMPLETE_LDIF);
            log.info("File saved to: {}", savedPath);

            // 5. 메타데이터 추출
            String collectionNumber = extractCollectionNumber(file.getOriginalFilename());
            String version = extractVersion(file.getOriginalFilename());

            // 6. 업로드 이력 생성
            FileUploadHistory history = FileUploadHistory.builder()
                    .filename(file.getOriginalFilename())
                    .collectionNumber(collectionNumber)
                    .version(version)
                    .fileFormat(detectFileFormat(file.getOriginalFilename()))
                    .fileSizeBytes(file.getSize())
                    .fileSizeDisplay(formatFileSize(file.getSize()))
                    .uploadedAt(LocalDateTime.now())
                    .localFilePath(savedPath)
                    .fileHash(fileHash)
                    .expectedChecksum(expectedChecksum)
                    .status(UploadStatus.RECEIVED)
                    .isDuplicate(forceUpload) // 강제 업로드인 경우 중복으로 표시
                    .build();

            FileUploadHistory savedHistory = fileUploadService.saveUploadHistory(history);
            log.info("Upload history created: id={}", savedHistory.getId());

            // 7. 성공 메시지
            model.addAttribute("success", "파일 업로드가 완료되었습니다.");
            model.addAttribute("uploadId", savedHistory.getId());

            return "redirect:/upload-history?id=" + savedHistory.getId();

        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            return "ldif/upload-ldif";

        } catch (Exception e) {
            log.error("Upload error", e);
            model.addAttribute("error", "파일 업로드 중 오류가 발생했습니다: " + e.getMessage());
            return "ldif/upload-ldif";
        }
    }

    /**
     * 파일 유효성 검사
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }

        if (!file.getOriginalFilename().toLowerCase().endsWith(".ldif")) {
            throw new IllegalArgumentException("LDIF 파일만 업로드할 수 있습니다.");
        }

        long maxSize = 100 * 1024 * 1024; // 100MB
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(
                String.format("파일 크기가 너무 큽니다. 최대 크기: %s", formatFileSize(maxSize))
            );
        }
    }

    /**
     * Collection 번호 추출
     * 파일명 패턴: icaopkd-{collection}-{type}-{version}.ldif
     */
    private String extractCollectionNumber(String filename) {
        String pattern = "icaopkd-(\\d{3})-";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(filename.toLowerCase());

        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * 버전 추출
     */
    private String extractVersion(String filename) {
        String pattern = "-(\\d+)\\.ldif$";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(filename.toLowerCase());

        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * 파일 포맷 감지
     */
    private FileFormat detectFileFormat(String filename) {
        String lower = filename.toLowerCase();

        if (lower.contains("001") && lower.contains("complete")) {
            return FileFormat.CSCA_COMPLETE_LDIF;
        } else if (lower.contains("001") && lower.contains("delta")) {
            return FileFormat.CSCA_DELTA_LDIF;
        } else if (lower.contains("002") && lower.contains("complete")) {
            return FileFormat.EMRTD_COMPLETE_LDIF;
        } else if (lower.contains("002") && lower.contains("delta")) {
            return FileFormat.EMRTD_DELTA_LDIF;
        }

        return FileFormat.CSCA_COMPLETE_LDIF; // 기본값
    }

    /**
     * 파일 크기 포맷팅
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %siB", bytes / Math.pow(1024, exp), pre);
    }
}
```

### 2. FileStorageService

**파일 경로**: `/src/main/java/com/smartcoreinc/localpkd/service/FileStorageService.java`

```java
package com.smartcoreinc.localpkd.service;

import com.smartcoreinc.localpkd.common.enums.FileFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class FileStorageService {

    @Value("${app.upload.directory:./data/uploads}")
    private String uploadDirectory;

    /**
     * 파일 저장
     */
    public String saveFile(MultipartFile file, FileFormat format) throws IOException {
        // 저장 디렉토리 생성
        Path uploadPath = createUploadDirectory(format);

        // 파일명 생성 (타임스탬프 포함)
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = timestamp + "_" + file.getOriginalFilename();

        // 파일 저장
        Path targetPath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        log.info("File saved successfully: {}", targetPath);
        return targetPath.toString();
    }

    /**
     * 업로드 디렉토리 생성
     */
    private Path createUploadDirectory(FileFormat format) throws IOException {
        String subDir = format.name().toLowerCase();
        Path uploadPath = Paths.get(uploadDirectory, subDir);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            log.info("Created upload directory: {}", uploadPath);
        }

        return uploadPath;
    }

    /**
     * 파일 해시 계산 (SHA-256)
     */
    public String calculateFileHash(MultipartFile file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = file.getBytes();
            byte[] hashBytes = digest.digest(fileBytes);

            // 바이트 배열을 16진수 문자열로 변환
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not found", e);
            throw new RuntimeException("파일 해시 계산 실패", e);
        }
    }

    /**
     * 파일 삭제
     */
    public void deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            Files.deleteIfExists(path);
            log.info("File deleted: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", filePath, e);
        }
    }
}
```

### 3. FileUploadService 업데이트

**기존 메서드에 추가**:

```java
/**
 * 업로드 이력 저장
 */
public FileUploadHistory saveUploadHistory(FileUploadHistory history) {
    log.debug("Saving upload history: filename={}", history.getFilename());
    return uploadHistoryRepository.save(history);
}
```

### 4. application.yml 설정 추가

```yaml
app:
  upload:
    directory: ./data/uploads
    max-file-size: 104857600  # 100MB in bytes
```

---

## 파일 저장 전략

### 디렉토리 구조

```
./data/uploads/
├── csca_complete_ldif/
│   ├── 20251017_103000_icaopkd-001-complete-009410.ldif
│   └── 20251017_104500_icaopkd-001-complete-009411.ldif
├── csca_delta_ldif/
│   └── ...
├── emrtd_complete_ldif/
│   └── ...
└── master_list/
    └── ...
```

### 파일명 규칙

```
{timestamp}_{original_filename}
```

예시:
- `20251017_103000_icaopkd-001-complete-009410.ldif`
- `20251017_150000_masterlist-July2025.ml`

### 파일 메타데이터

데이터베이스에 저장:
- `filename`: 원본 파일명
- `local_file_path`: 실제 저장 경로
- `file_hash`: SHA-256 해시
- `file_size_bytes`: 파일 크기
- `uploaded_at`: 업로드 시간

---

## 구현 순서

### Step 1: FileStorageService 구현 (30분)
- [x] 파일 저장 메서드
- [x] 해시 계산 메서드
- [x] 디렉토리 생성 로직
- [x] 파일 삭제 메서드

### Step 2: LdifUploadController 구현 (1시간)
- [ ] GET /ldif/upload 엔드포인트
- [ ] POST /ldif/upload 엔드포인트
- [ ] 파일 유효성 검사
- [ ] 중복 체크 연동
- [ ] 메타데이터 추출
- [ ] 이력 저장

### Step 3: MasterListUploadController 구현 (1시간)
- [ ] LDIF와 유사한 구조
- [ ] ML 특화 검증 로직
- [ ] 파일 포맷 감지

### Step 4: 에러 처리 및 로깅 (30분)
- [ ] 예외 처리 강화
- [ ] 로깅 추가
- [ ] 사용자 친화적 에러 메시지

### Step 5: 테스트 (1-2시간)
- [ ] 단위 테스트
- [ ] 통합 테스트
- [ ] E2E 테스트

---

## 테스트 계획

### 1. 단위 테스트

**FileStorageServiceTest**:
- [ ] `saveFile()` - 파일 저장 성공
- [ ] `calculateFileHash()` - 해시 계산 정확성
- [ ] `createUploadDirectory()` - 디렉토리 생성

**LdifUploadControllerTest**:
- [ ] `validateFile()` - 유효성 검사
- [ ] `extractCollectionNumber()` - 메타데이터 추출
- [ ] `detectFileFormat()` - 포맷 감지

### 2. 통합 테스트

- [ ] 파일 업로드 → 저장 → 이력 생성 전체 플로우
- [ ] 중복 파일 업로드 시 거부
- [ ] 강제 업로드 (forceUpload=true) 동작
- [ ] 잘못된 파일 형식 업로드 시 에러

### 3. E2E 테스트

- [ ] 브라우저에서 LDIF 파일 선택
- [ ] 중복 체크 모달 표시 확인
- [ ] 업로드 성공 후 이력 페이지로 리다이렉트
- [ ] 업로드 이력에서 파일 확인

---

## 주의 사항

1. **파일 크기 제한**: Spring Boot의 기본 제한 확인 및 설정
2. **경로 보안**: Path Traversal 공격 방지
3. **동시성**: 동일 파일 동시 업로드 시 처리
4. **디스크 공간**: 업로드 전 디스크 공간 확인
5. **트랜잭션**: 파일 저장과 DB 저장의 원자성

---

## 다음 단계 (Phase 2)

업로드 기능 완성 후:
1. 파일 파싱 기능 구현
2. SSE 실시간 진행 상황
3. OpenLDAP 저장
4. 체크섬 검증
5. 성능 최적화

---

**작성일**: 2025-10-17
**다음 업데이트**: 구현 완료 시
