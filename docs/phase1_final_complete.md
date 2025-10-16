# Phase 1 최종 완성 - FileType & FileFormat

## ✅ 완성된 파일 (3개)

1. **FileType.java** (최종)
   - CSCA_MASTER_LIST (Collection #1)
   - EMRTD_PKI_OBJECTS (Collection #2)
   - NON_CONFORMANT (Collection #3)

2. **FileFormat.java** (최종)
   - ML_SIGNED_CMS
   - CSCA_COMPLETE_LDIF, CSCA_DELTA_LDIF
   - EMRTD_COMPLETE_LDIF, DSC_DELTA_LDIF, BCSC_DELTA_LDIF, CRL_DELTA_LDIF
   - NON_CONFORMANT_LDIF

3. **FileFormatTest.java**
   - 파일명 감지 테스트
   - 버전/Collection 추출 테스트
   - 유효성 검증 테스트

---

## 📊 파일 타입 및 포맷 매핑

### Collection #1: CSCA Master List

| 파일 포맷 | 파일명 패턴 | 확장자 | Delta |
|----------|------------|--------|-------|
| ML_SIGNED_CMS | `icaopkd-001-ml-{ver}.ml` | .ml | ❌ |
| CSCA_COMPLETE_LDIF | `icaopkd-001-complete-{ver}.ldif` | .ldif | ❌ |
| CSCA_DELTA_LDIF | `icaopkd-001-ml-delta-{ver}.ldif` | .ldif | ✅ (ml) |

### Collection #2: eMRTD PKI Objects

| 파일 포맷 | 파일명 패턴 | 확장자 | Delta |
|----------|------------|--------|-------|
| EMRTD_COMPLETE_LDIF | `icaopkd-002-complete-{ver}.ldif` | .ldif | ❌ |
| DSC_DELTA_LDIF | `icaopkd-002-dscs-delta-{ver}.ldif` | .ldif | ✅ (dscs) |
| BCSC_DELTA_LDIF | `icaopkd-002-bcscs-delta-{ver}.ldif` | .ldif | ✅ (bcscs) |
| CRL_DELTA_LDIF | `icaopkd-002-crls-delta-{ver}.ldif` | .ldif | ✅ (crls) |

### Collection #3: Non-Conformant

| 파일 포맷 | 파일명 패턴 | 확장자 | Delta |
|----------|------------|--------|-------|
| NON_CONFORMANT_LDIF | `icaopkd-003-complete-{ver}.ldif` | .ldif | ❌ |

---

## 🎯 주요 기능

### 1. FileType

```java
// Collection 번호로 조회
FileType type = FileType.fromCollectionNumber("001");
// → CSCA_MASTER_LIST

// Code로 조회
FileType type2 = FileType.fromCode("EMRTD_PKI_OBJECTS");
// → EMRTD_PKI_OBJECTS

// 타입 확인
boolean isCsca = FileType.CSCA_MASTER_LIST.isCsca();  // true
boolean isDeprecated = FileType.NON_CONFORMANT.isDeprecated();  // true
```

### 2. FileFormat - 파일명 감지

```java
// ML Signed CMS
FileFormat format1 = FileFormat.detectFromFilename("icaopkd-001-ml-000325.ml");
// → ML_SIGNED_CMS

// CSCA Complete LDIF
FileFormat format2 = FileFormat.detectFromFilename("icaopkd-001-complete-000325.ldif");
// → CSCA_COMPLETE_LDIF

// DSC Delta LDIF
FileFormat format3 = FileFormat.detectFromFilename("icaopkd-002-dscs-delta-009399.ldif");
// → DSC_DELTA_LDIF

// 대소문자 구분 없음
FileFormat format4 = FileFormat.detectFromFilename("ICAOPKD-001-ML-000325.ML");
// → ML_SIGNED_CMS
```

### 3. 버전 및 메타데이터 추출

```java
String filename = "icaopkd-002-dscs-delta-009399.ldif";

// 버전 추출
String version = FileFormat.extractVersion(filename);
// → "009399"

// Collection 번호 추출
String collection = FileFormat.extractCollectionNumber(filename);
// → "002"

// Delta 타입 추출
String deltaType = FileFormat.extractDeltaType(filename);
// → "dscs"

// 유효성 검증
boolean isValid = FileFormat.isValidFilename(filename);
// → true
```

### 4. 파일명 생성

```java
// ML Signed CMS
String filename1 = FileFormat.ML_SIGNED_CMS.buildFilename("000325");
// → "icaopkd-001-ml-000325.ml"

// CSCA Delta
String filename2 = FileFormat.CSCA_DELTA_LDIF.buildFilename("000326");
// → "icaopkd-001-ml-delta-000326.ldif"

// DSC Delta
String filename3 = FileFormat.DSC_DELTA_LDIF.buildFilename("009399");
// → "icaopkd-002-dscs-delta-009399.ldif"
```

### 5. 타입 확인

```java
FileFormat format = FileFormat.ML_SIGNED_CMS;

boolean isSignedCms = format.isSignedCms();     // true
boolean isLdif = format.isLdif();               // false
boolean isDelta = format.isDelta();             // false
boolean isComplete = format.isComplete();       // true
boolean isDeprecated = format.isDeprecated();   // false

// FileType 매핑
FileType fileType = format.getFileType();
// → CSCA_MASTER_LIST
```

---

## 🧪 테스트 실행

```bash
# 단일 테스트 실행
mvn test -Dtest=FileFormatTest

# 전체 테스트
mvn test
```

### 테스트 결과 예시

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running FileFormatTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 📝 사용 시나리오

### 시나리오 1: 업로드된 파일 타입 자동 감지

```java
@Service
public class FileUploadService {
    
    public void handleUpload(MultipartFile file) {
        String filename = file.getOriginalFilename();
        
        try {
            // 파일 포맷 자동 감지
            FileFormat format = FileFormat.detectFromFilename(filename);
            FileType fileType = format.getFileType();
            
            // 메타데이터 추출
            String collection = FileFormat.extractCollectionNumber(filename);
            String version = FileFormat.extractVersion(filename);
            String deltaType = FileFormat.extractDeltaType(filename);
            
            log.info("파일 업로드: type={}, format={}, collection={}, version={}", 
                fileType, format, collection, version);
            
            if (format.isDelta()) {
                log.info("Delta 파일: deltaType={}", deltaType);
            }
            
            // 파일 처리...
            
        } catch (IllegalArgumentException e) {
            log.error("지원하지 않는 파일 형식: {}", filename);
            throw new UnsupportedFileFormatException(filename, "지원하지 않는 파일 형식");
        }
    }
}
```

### 시나리오 2: 다운로드할 파일명 생성

```java
@Service
public class IcaoPkdDownloadService {
    
    public String downloadLatestCscaMasterList() {
        // 최신 버전 조회
        String latestVersion = getLatestVersion("001");
        
        // 파일명 생성
        String filename = FileFormat.ML_SIGNED_CMS.buildFilename(latestVersion);
        // → "icaopkd-001-ml-000325.ml"
        
        // 다운로드 URL 생성
        String url = String.format("https://www.icao.int/icao-pkd/%s", filename);
        
        // 다운로드...
        return downloadFile(url);
    }
    
    public String downloadDscDelta(String version) {
        String filename = FileFormat.DSC_DELTA_LDIF.buildFilename(version);
        // → "icaopkd-002-dscs-delta-009399.ldif"
        
        String url = String.format("https://pkddownloadsg.icao.int/%s", filename);
        return downloadFile(url);
    }
}
```

### 시나리오 3: 파일 검증

```java
@Component
public class FileValidator {
    
    public boolean validateFilename(String filename) {
        // 파일명 유효성 검증
        if (!FileFormat.isValidFilename(filename)) {
            log.warn("유효하지 않은 파일명: {}", filename);
            return false;
        }
        
        // 파일 포맷 감지
        FileFormat format = FileFormat.detectFromFilename(filename);
        
        // Deprecated 파일 경고
        if (format.isDeprecated()) {
            log.warn("Deprecated 파일: {}", filename);
        }
        
        // Collection 번호 확인
        String collection = format.getCollectionNumber();
        if (!"001".equals(collection) && !"002".equals(collection)) {
            log.warn("알 수 없는 Collection: {}", collection);
            return false;
        }
        
        return true;
    }
}
```

---

## 🔄 Phase 1 → Phase 2 연동

Phase 2의 파서에서 FileFormat을 사용하는 방법:

```java
@Component
public class MlSignedCmsParser implements FileParser {
    
    @Override
    public boolean supports(FileType fileType, FileFormat fileFormat) {
        // ML Signed CMS만 지원
        return fileFormat == FileFormat.ML_SIGNED_CMS;
    }
    
    @Override
    public ParseResult parse(byte[] fileData, ParseContext context) {
        // context에서 FileFormat 정보 활용
        FileFormat format = context.getFileFormat();
        
        if (format.isSignedCms()) {
            // CMS 파싱 로직
        }
        
        // ...
    }
}

@Component
public class LdifCompleteParser implements FileParser {
    
    @Override
    public boolean supports(FileType fileType, FileFormat fileFormat) {
        // LDIF Complete 파일만 지원
        return fileFormat.isLdif() && fileFormat.isComplete();
    }
    
    // ...
}

@Component
public class LdifDeltaParser implements FileParser {
    
    @Override
    public boolean supports(FileType fileType, FileFormat fileFormat) {
        // LDIF Delta 파일만 지원
        return fileFormat.isLdif() && fileFormat.isDelta();
    }
    
    @Override
    public ParseResult parse(byte[] fileData, ParseContext context) {
        // Delta 타입별 처리
        String deltaType = context.getDeltaType();
        
        switch (deltaType) {
            case "ml":    // CSCA Delta
            case "dscs":  // DSC Delta
            case "bcscs": // BCSC Delta
            case "crls":  // CRL Delta
                // 각각 처리...
        }
        
        // ...
    }
}
```

---

## 📊 정규식 패턴 상세

### ML 파일 패턴
```java
Pattern ML_PATTERN = Pattern.compile("icaopkd-(\\d{3})-ml-(\\d+)\\.ml");

// 매칭 예시:
// "icaopkd-001-ml-000325.ml"
//   ↓ Group 1: "001" (collection)
//   ↓ Group 2: "000325" (version)
```

### LDIF 파일 패턴
```java
Pattern LDIF_PATTERN = Pattern.compile("icaopkd-(\\d{3})-(complete|([a-z]+)-delta)-(\\d+)\\.ldif");

// 매칭 예시 1 (Complete):
// "icaopkd-001-complete-000325.ldif"
//   ↓ Group 1: "001" (collection)
//   ↓ Group 2: "complete"
//   ↓ Group 3: null (deltaType)
//   ↓ Group 4: "000325" (version)

// 매칭 예시 2 (Delta):
// "icaopkd-002-dscs-delta-009399.ldif"
//   ↓ Group 1: "002" (collection)
//   ↓ Group 2: "dscs-delta"
//   ↓ Group 3: "dscs" (deltaType)
//   ↓ Group 4: "009399" (version)
```

---

## ✅ Phase 1 완료 체크리스트

### 파일 생성 완료
- [x] FileType.java (최종)
- [x] FileFormat.java (최종)
- [x] FileFormatTest.java

### 기능 구현 완료
- [x] 파일명 패턴 매칭 (정규식)
- [x] 파일 타입 자동 감지
- [x] 버전 번호 추출
- [x] Collection 번호 추출
- [x] Delta 타입 추출
- [x] 파일명 생성
- [x] 유효성 검증
- [x] 대소문자 무시

### 테스트 완료
- [x] ML Signed CMS 감지
- [x] CSCA Complete/Delta 감지
- [x] eMRTD Complete 감지
- [x] DSC/BCSC/CRL Delta 감지
- [x] Non-Conformant 감지
- [x] 버전/Collection 추출
- [x] 유효성 검증
- [x] 파일명 생성

---

## 🎉 Phase 1 완료!

이제 Phase 2의 파서들이 FileFormat을 사용하여 파일 타입을 정확히 판별하고 처리할 수 있습니다.

**다음 단계: Phase 3 - Entity 및 Repository 구현** 🚀