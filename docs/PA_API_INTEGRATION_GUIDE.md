# Passive Authentication API 연동 가이드

**Version**: 1.0  
**Last Updated**: 2025-12-23  
**대상**: 외부 ePassport Reader 연동 시스템 개발자

---

## 📋 목차

1. [개요](#1-개요)
2. [사전 요구사항](#2-사전-요구사항)
3. [API 엔드포인트 목록](#3-api-엔드포인트-목록)
4. [PA 검증 API](#4-pa-검증-api)
5. [보조 API](#5-보조-api)
6. [데이터 형식](#6-데이터-형식)
7. [에러 처리](#7-에러-처리)
8. [Spring Boot 클라이언트 예제](#8-spring-boot-클라이언트-예제)
9. [Thymeleaf 통합 예제](#9-thymeleaf-통합-예제)
10. [보안 고려사항](#10-보안-고려사항)
11. [FAQ](#11-faq)

---

## 1. 개요

이 문서는 외부 ePassport Reader가 연결된 Spring Boot + Thymeleaf 웹 애플리케이션에서 Local PKD의 Passive Authentication (PA) API를 호출하는 방법을 설명합니다.

### 1.1 Passive Authentication이란?

Passive Authentication은 ICAO 9303 표준에 따른 전자여권 무결성 검증 절차입니다:

```
┌─────────────────────────────────────────────────────────────────┐
│                    PA 검증 프로세스                              │
├─────────────────────────────────────────────────────────────────┤
│  1. 인증서 체인 검증 (DSC → CSCA)                                │
│     └── DSC가 신뢰할 수 있는 CSCA에 의해 서명되었는지 확인        │
│                                                                  │
│  2. SOD 서명 검증                                                │
│     └── SOD(Security Object Document)의 디지털 서명 검증         │
│                                                                  │
│  3. Data Group 해시 검증                                         │
│     └── SOD에 저장된 해시와 실제 DG 해시 비교                    │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 시스템 아키텍처

```
┌──────────────────────┐      HTTP/JSON       ┌──────────────────────┐
│  외부 시스템          │  ◄──────────────────► │  Local PKD Server    │
│  (ePassport Reader)  │                       │  (PA API)            │
│                      │                       │                      │
│  - Spring Boot       │                       │  - 인증서 체인 검증   │
│  - Thymeleaf         │                       │  - SOD 서명 검증      │
│  - ePassport SDK     │                       │  - DG 해시 검증       │
└──────────────────────┘                       │  - OpenLDAP (PKD)    │
                                               └──────────────────────┘
```

---

## 2. 사전 요구사항

### 2.1 서버 요구사항

- Local PKD 서버 실행 중 (`http://localhost:8081` 또는 설정된 주소)
- OpenLDAP에 CSCA/DSC 인증서 및 CRL 등록 완료

### 2.2 클라이언트 요구사항

- Java 17+
- Spring Boot 3.x
- `spring-boot-starter-web` (RestTemplate/WebClient)
- ePassport Reader SDK (APDU 통신용)

### 2.3 Maven 의존성

```xml
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Thymeleaf -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>
    
    <!-- JSON Processing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
```

---

## 3. API 엔드포인트 목록

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/pa/verify` | PA 검증 수행 (메인 API) |
| `GET` | `/api/pa/history` | 검증 이력 조회 |
| `GET` | `/api/pa/{verificationId}` | 특정 검증 결과 조회 |
| `GET` | `/api/pa/{verificationId}/datagroups` | 검증 ID별 DG 데이터 조회 |
| `POST` | `/api/pa/parse-dg1` | DG1 (MRZ) 파싱 |
| `POST` | `/api/pa/parse-dg2` | DG2 (얼굴 이미지) 파싱 |
| `POST` | `/api/pa/parse-sod` | SOD 파싱 (메타데이터 추출) |

---

## 4. PA 검증 API

### 4.1 엔드포인트

```
POST /api/pa/verify
Content-Type: application/json
```

### 4.2 Request Body

```json
{
  "issuingCountry": "KOR",
  "documentNumber": "M12345678",
  "sod": "MIIGBwYJKoZIhvcNAQcCoII...",
  "dataGroups": {
    "DG1": "YV9oZWFkZXIuLi4=",
    "DG2": "iVBORw0KGgoAAAANS..."
  },
  "requestedBy": "border-control-app-v1"
}
```

### 4.3 Request 필드 상세

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `issuingCountry` | String | 선택* | ISO 3166-1 alpha-2 또는 alpha-3 국가 코드 (예: "KR", "KOR"). SOD의 DSC에서 자동 추출 가능 |
| `documentNumber` | String | 선택* | 여권 번호 (1-20자). 제공하지 않으면 "UNKNOWN"으로 기록 |
| `sod` | String | **필수** | Base64 인코딩된 SOD (EF.SOD 또는 PKCS#7 SignedData) |
| `dataGroups` | Map | **필수** | Data Group 번호를 키로, Base64 인코딩된 바이너리를 값으로 하는 Map |
| `requestedBy` | String | 선택 | 감사 추적용 요청자 식별자 |

> **\*주의**: `issuingCountry`와 `documentNumber`는 선택 필드이지만, 제공하면 검증 이력 조회 시 유용합니다.

### 4.4 Data Groups 키 형식

```
DG1, DG2, DG3, ..., DG16
```

- 최소 1개 이상의 Data Group 필수
- 일반적으로 `DG1` (MRZ)과 `DG2` (얼굴 이미지) 제공

### 4.5 Response 구조

```json
{
  "status": "VALID",
  "verificationId": "550e8400-e29b-41d4-a716-446655440000",
  "verificationTimestamp": "2025-12-23T10:30:00Z",
  "issuingCountry": "KOR",
  "documentNumber": "M12345678",
  "certificateChainValidation": {
    "valid": true,
    "dscSubject": "C=KR,O=Government of Korea,OU=MOFA,CN=Document Signer 001",
    "dscSerialNumber": "1234567890ABCDEF",
    "cscaSubject": "C=KR,O=Government of Korea,OU=MOFA,CN=CSCA Korea",
    "cscaSerialNumber": "FEDCBA0987654321",
    "notBefore": "2023-01-01T00:00:00Z",
    "notAfter": "2028-01-01T00:00:00Z",
    "crlChecked": true,
    "revoked": false,
    "crlStatus": "VALID",
    "crlMessage": null,
    "validationErrors": null
  },
  "sodSignatureValidation": {
    "valid": true,
    "signatureAlgorithm": "SHA256withRSA",
    "hashAlgorithm": "SHA-256",
    "validationErrors": null
  },
  "dataGroupValidation": {
    "totalGroups": 2,
    "validGroups": 2,
    "invalidGroups": 0,
    "details": {
      "DG1": {
        "valid": true,
        "expectedHash": "a1b2c3d4e5f6...",
        "actualHash": "a1b2c3d4e5f6..."
      },
      "DG2": {
        "valid": true,
        "expectedHash": "f6e5d4c3b2a1...",
        "actualHash": "f6e5d4c3b2a1..."
      }
    }
  },
  "processingDurationMs": 245,
  "errors": []
}
```

### 4.6 Response 필드 상세

#### 최상위 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `status` | Enum | `VALID`, `INVALID`, `ERROR` 중 하나 |
| `verificationId` | UUID | 검증 고유 ID (이력 조회용) |
| `verificationTimestamp` | DateTime | 검증 완료 시각 (ISO 8601) |
| `issuingCountry` | String | 여권 발급 국가 |
| `documentNumber` | String | 여권 번호 |
| `processingDurationMs` | Long | 처리 시간 (밀리초) |
| `errors` | Array | 에러 목록 (status가 INVALID/ERROR일 때) |

#### `certificateChainValidation` 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `valid` | boolean | 인증서 체인 검증 성공 여부 |
| `dscSubject` | String | DSC Subject DN |
| `dscSerialNumber` | String | DSC 일련번호 (16진수) |
| `cscaSubject` | String | CSCA Subject DN |
| `cscaSerialNumber` | String | CSCA 일련번호 (16진수) |
| `notBefore` | DateTime | DSC 유효기간 시작 |
| `notAfter` | DateTime | DSC 유효기간 종료 |
| `crlChecked` | boolean | CRL 확인 여부 |
| `revoked` | boolean | 인증서 폐기 여부 |
| `crlStatus` | String | CRL 상태 (아래 참조) |
| `crlMessage` | String | CRL 상세 메시지 |
| `validationErrors` | String | 검증 오류 메시지 |

**CRL 상태 값:**

| 상태 | 설명 |
|------|------|
| `VALID` | CRL 확인 완료, 인증서 유효 |
| `REVOKED` | 인증서가 폐기됨 |
| `CRL_UNAVAILABLE` | LDAP에서 CRL을 찾을 수 없음 |
| `CRL_EXPIRED` | CRL이 만료됨 |
| `CRL_INVALID` | CRL 서명 검증 실패 |

#### `sodSignatureValidation` 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `valid` | boolean | SOD 서명 검증 성공 여부 |
| `signatureAlgorithm` | String | 서명 알고리즘 (예: SHA256withRSA) |
| `hashAlgorithm` | String | 해시 알고리즘 (예: SHA-256) |
| `validationErrors` | String | 검증 오류 메시지 |

#### `dataGroupValidation` 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `totalGroups` | int | 전체 검증 DG 수 |
| `validGroups` | int | 유효한 DG 수 |
| `invalidGroups` | int | 무효한 DG 수 |
| `details` | Map | DG별 상세 검증 결과 |

---

## 5. 보조 API

### 5.1 DG1 (MRZ) 파싱

```
POST /api/pa/parse-dg1
Content-Type: application/json
```

**Request:**
```json
{
  "dg1Base64": "YV9oZWFkZXIuLi4="
}
```

**Response:**
```json
{
  "documentType": "P",
  "issuingCountry": "KOR",
  "surname": "HONG",
  "givenNames": "GILDONG",
  "documentNumber": "M12345678",
  "nationality": "KOR",
  "dateOfBirth": "800101",
  "sex": "M",
  "dateOfExpiry": "300101",
  "personalNumber": ""
}
```

### 5.2 DG2 (얼굴 이미지) 파싱

```
POST /api/pa/parse-dg2
Content-Type: application/json
```

**Request:**
```json
{
  "dg2Base64": "iVBORw0KGgoAAAANS..."
}
```

**Response:**
```json
{
  "faceCount": 1,
  "faceImages": [
    {
      "imageFormat": "JPEG",
      "imageSize": 12345,
      "imageWidth": 320,
      "imageHeight": 400,
      "imageDataUrl": "data:image/jpeg;base64,/9j/4AAQSkZJR..."
    }
  ]
}
```

### 5.3 SOD 파싱

```
POST /api/pa/parse-sod
Content-Type: application/json
```

**Request:**
```json
{
  "sodBase64": "MIIGBwYJKoZIhvcNAQcCoII..."
}
```

**Response:**
```json
{
  "dscSubject": "C=KR,O=Government,CN=DS 001",
  "dscSerial": "1234567890ABCDEF",
  "hashAlgorithm": "SHA-256",
  "signatureAlgorithm": "SHA256withRSA",
  "dataGroups": [1, 2, 15]
}
```

### 5.4 검증 이력 조회

```
GET /api/pa/history?page=0&size=20&issuingCountry=KR&status=VALID
```

**Query Parameters:**

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `page` | int | 0 | 페이지 번호 (0부터 시작) |
| `size` | int | 20 | 페이지 크기 |
| `issuingCountry` | String | - | 국가 필터 |
| `status` | String | - | 상태 필터 (VALID/INVALID/ERROR) |

---

## 6. 데이터 형식

### 6.1 SOD 데이터 형식

ePassport의 EF.SOD 파일에서 읽은 바이너리를 Base64로 인코딩합니다.

```java
// ePassport Reader에서 SOD 읽기 (예시)
byte[] efSod = passportReader.readFile(FileType.EF_SOD);

// Base64 인코딩
String sodBase64 = Base64.getEncoder().encodeToString(efSod);
```

**SOD 구조 (참고):**
```
Tag 0x77 (Application 23) - ICAO EF.SOD wrapper (일부 여권)
  └─ CMS SignedData (Tag 0x30)
       ├─ encapContentInfo (LDSSecurityObject)
       │   └─ dataGroupHashValues
       ├─ certificates [0]
       │   └─ DSC certificate
       └─ signerInfos
           └─ signature
```

> **참고**: Local PKD API는 Tag 0x77 wrapper를 자동으로 처리합니다.

### 6.2 Data Group 데이터 형식

각 Data Group의 전체 바이너리를 Base64로 인코딩합니다.

```java
// DG1 읽기
byte[] dg1 = passportReader.readFile(FileType.DG1);
String dg1Base64 = Base64.getEncoder().encodeToString(dg1);

// DG2 읽기
byte[] dg2 = passportReader.readFile(FileType.DG2);
String dg2Base64 = Base64.getEncoder().encodeToString(dg2);
```

### 6.3 인코딩 주의사항

- **표준 Base64** 사용 (`java.util.Base64.getEncoder()`)
- URL-safe Base64가 아닌 **Standard Base64** 사용
- 줄바꿈 없이 단일 문자열로 인코딩

---

## 7. 에러 처리

### 7.1 HTTP 상태 코드

| 코드 | 설명 |
|------|------|
| `200` | 검증 완료 (결과는 `status` 필드로 확인) |
| `400` | 잘못된 요청 (필수 필드 누락, 잘못된 Base64 등) |
| `404` | 리소스 없음 (검증 ID 조회 시) |
| `500` | 서버 오류 (LDAP 연결 실패 등) |

### 7.2 에러 응답 구조

**400 Bad Request:**
```json
{
  "timestamp": "2025-12-23T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid Base64 encoding: Illegal base64 character"
}
```

**검증 실패 (status: INVALID):**
```json
{
  "status": "INVALID",
  "verificationId": "...",
  "errors": [
    {
      "code": "CSCA_NOT_FOUND",
      "message": "CSCA certificate not found in LDAP for issuer: C=XX,O=Unknown",
      "severity": "CRITICAL",
      "timestamp": "2025-12-23T10:30:00Z"
    }
  ]
}
```

### 7.3 주요 에러 코드

| 코드 | 심각도 | 설명 |
|------|--------|------|
| `CSCA_NOT_FOUND` | CRITICAL | LDAP에서 CSCA 인증서를 찾을 수 없음 |
| `DSC_VALIDATION_FAILED` | CRITICAL | DSC 서명 검증 실패 |
| `DSC_EXPIRED` | CRITICAL | DSC 유효기간 만료 |
| `DSC_NOT_YET_VALID` | CRITICAL | DSC 유효기간 미도래 |
| `DSC_REVOKED` | CRITICAL | DSC가 폐기됨 |
| `SOD_PARSE_ERROR` | CRITICAL | SOD 파싱 오류 |
| `SOD_SIGNATURE_INVALID` | CRITICAL | SOD 서명 무효 |
| `DG_HASH_MISMATCH` | CRITICAL | Data Group 해시 불일치 |
| `CRL_UNAVAILABLE` | WARNING | CRL을 찾을 수 없음 |
| `CRL_EXPIRED` | WARNING | CRL 만료됨 |

---

## 8. Spring Boot 클라이언트 예제

### 8.1 Configuration

```java
@Configuration
public class PaApiClientConfig {

    @Value("${pa-api.base-url:http://localhost:8081}")
    private String baseUrl;

    @Bean
    public RestTemplate paApiRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setUriTemplateHandler(
            new DefaultUriBuilderFactory(baseUrl)
        );
        return restTemplate;
    }
}
```

### 8.2 DTO 클래스

```java
// Request DTO
public record PaVerificationRequest(
    String issuingCountry,
    String documentNumber,
    String sod,
    Map<String, String> dataGroups,
    String requestedBy
) {
    public static PaVerificationRequest of(
        String country,
        String docNumber,
        byte[] sodBytes,
        Map<String, byte[]> dataGroupBytes,
        String requestedBy
    ) {
        String sodBase64 = Base64.getEncoder().encodeToString(sodBytes);
        
        Map<String, String> dgBase64 = new HashMap<>();
        dataGroupBytes.forEach((key, value) -> 
            dgBase64.put(key, Base64.getEncoder().encodeToString(value))
        );
        
        return new PaVerificationRequest(
            country, docNumber, sodBase64, dgBase64, requestedBy
        );
    }
}

// Response DTO
public record PaVerificationResponse(
    String status,
    UUID verificationId,
    LocalDateTime verificationTimestamp,
    String issuingCountry,
    String documentNumber,
    CertificateChainValidation certificateChainValidation,
    SodSignatureValidation sodSignatureValidation,
    DataGroupValidation dataGroupValidation,
    Long processingDurationMs,
    List<PaError> errors
) {
    public boolean isValid() {
        return "VALID".equals(status);
    }
}

// Nested DTOs
public record CertificateChainValidation(
    boolean valid,
    String dscSubject,
    String dscSerialNumber,
    String cscaSubject,
    String cscaSerialNumber,
    LocalDateTime notBefore,
    LocalDateTime notAfter,
    boolean crlChecked,
    boolean revoked,
    String crlStatus,
    String crlMessage,
    String validationErrors
) {}

public record SodSignatureValidation(
    boolean valid,
    String signatureAlgorithm,
    String hashAlgorithm,
    String validationErrors
) {}

public record DataGroupValidation(
    int totalGroups,
    int validGroups,
    int invalidGroups,
    Map<String, DataGroupDetail> details
) {}

public record DataGroupDetail(
    boolean valid,
    String expectedHash,
    String actualHash
) {}

public record PaError(
    String code,
    String message,
    String severity,
    LocalDateTime timestamp
) {}
```

### 8.3 Service 클래스

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PassiveAuthenticationService {

    private final RestTemplate paApiRestTemplate;

    /**
     * ePassport 데이터로 PA 검증 수행
     */
    public PaVerificationResponse verify(
        String country,
        String documentNumber,
        byte[] sodBytes,
        Map<String, byte[]> dataGroups
    ) {
        PaVerificationRequest request = PaVerificationRequest.of(
            country,
            documentNumber,
            sodBytes,
            dataGroups,
            "passport-reader-app"
        );

        try {
            ResponseEntity<PaVerificationResponse> response = paApiRestTemplate.postForEntity(
                "/api/pa/verify",
                request,
                PaVerificationResponse.class
            );

            PaVerificationResponse result = response.getBody();
            log.info("PA verification completed: status={}, id={}",
                result.status(), result.verificationId());

            return result;

        } catch (HttpClientErrorException e) {
            log.error("PA verification failed: {}", e.getResponseBodyAsString());
            throw new PaVerificationException("PA verification request failed", e);
        }
    }

    /**
     * ePassport Reader에서 읽은 데이터로 PA 검증
     */
    public PaVerificationResponse verifyFromReader(PassportData passportData) {
        Map<String, byte[]> dataGroups = new HashMap<>();
        
        if (passportData.getDg1() != null) {
            dataGroups.put("DG1", passportData.getDg1());
        }
        if (passportData.getDg2() != null) {
            dataGroups.put("DG2", passportData.getDg2());
        }
        // 필요한 다른 DG 추가

        return verify(
            passportData.getIssuingCountry(),
            passportData.getDocumentNumber(),
            passportData.getSod(),
            dataGroups
        );
    }
}
```

### 8.4 Controller 예제

```java
@Controller
@RequiredArgsConstructor
@RequestMapping("/passport")
public class PassportVerificationController {

    private final PassiveAuthenticationService paService;
    private final PassportReaderService readerService;

    @GetMapping("/scan")
    public String showScanPage() {
        return "passport/scan";
    }

    @PostMapping("/verify")
    @ResponseBody
    public ResponseEntity<?> verifyPassport() {
        try {
            // 1. ePassport Reader에서 데이터 읽기
            PassportData passportData = readerService.readPassport();

            // 2. PA API 호출
            PaVerificationResponse result = paService.verifyFromReader(passportData);

            // 3. 결과 반환
            return ResponseEntity.ok(result);

        } catch (ReaderException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Passport reading failed: " + e.getMessage()));
        } catch (PaVerificationException e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Verification failed: " + e.getMessage()));
        }
    }
}
```

---

## 9. Thymeleaf 통합 예제

### 9.1 검증 페이지 (scan.html)

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>여권 검증</title>
    <script src="https://unpkg.com/htmx.org@2.0.4"></script>
    <script src="https://unpkg.com/alpinejs@3.14.8" defer></script>
</head>
<body>
    <div x-data="passportVerifier()">
        <!-- 스캔 버튼 -->
        <button @click="scanAndVerify()" 
                :disabled="isLoading"
                class="btn btn-primary">
            <span x-show="isLoading">검증 중...</span>
            <span x-show="!isLoading">여권 스캔 및 검증</span>
        </button>

        <!-- 결과 표시 -->
        <div x-show="result" class="mt-4">
            <!-- 상태 배지 -->
            <div :class="{
                'badge badge-success': result?.status === 'VALID',
                'badge badge-error': result?.status === 'INVALID',
                'badge badge-warning': result?.status === 'ERROR'
            }">
                <span x-text="result?.status"></span>
            </div>

            <!-- 인증서 체인 검증 -->
            <div class="card mt-2">
                <h3>인증서 체인 검증</h3>
                <p x-show="result?.certificateChainValidation?.valid" class="text-success">
                    ✓ DSC → CSCA 체인 유효
                </p>
                <p x-show="!result?.certificateChainValidation?.valid" class="text-error">
                    ✗ 인증서 체인 검증 실패
                </p>
                <p>DSC: <span x-text="result?.certificateChainValidation?.dscSubject"></span></p>
                <p>CSCA: <span x-text="result?.certificateChainValidation?.cscaSubject"></span></p>
            </div>

            <!-- SOD 서명 검증 -->
            <div class="card mt-2">
                <h3>SOD 서명 검증</h3>
                <p x-show="result?.sodSignatureValidation?.valid" class="text-success">
                    ✓ SOD 서명 유효
                </p>
                <p>알고리즘: <span x-text="result?.sodSignatureValidation?.signatureAlgorithm"></span></p>
            </div>

            <!-- Data Group 해시 검증 -->
            <div class="card mt-2">
                <h3>Data Group 해시 검증</h3>
                <p>
                    유효: <span x-text="result?.dataGroupValidation?.validGroups"></span> / 
                    전체: <span x-text="result?.dataGroupValidation?.totalGroups"></span>
                </p>
            </div>

            <!-- 에러 표시 -->
            <div x-show="result?.errors?.length > 0" class="alert alert-error mt-2">
                <h4>오류 목록</h4>
                <template x-for="error in result?.errors">
                    <p>
                        <strong x-text="error.code"></strong>: 
                        <span x-text="error.message"></span>
                    </p>
                </template>
            </div>
        </div>
    </div>

    <script>
        function passportVerifier() {
            return {
                isLoading: false,
                result: null,
                
                async scanAndVerify() {
                    this.isLoading = true;
                    this.result = null;
                    
                    try {
                        const response = await fetch('/passport/verify', {
                            method: 'POST'
                        });
                        this.result = await response.json();
                    } catch (error) {
                        this.result = {
                            status: 'ERROR',
                            errors: [{ code: 'REQUEST_FAILED', message: error.message }]
                        };
                    } finally {
                        this.isLoading = false;
                    }
                }
            };
        }
    </script>
</body>
</html>
```

### 9.2 결과 Fragment (result-fragment.html)

```html
<div th:fragment="verification-result" th:with="result=${verificationResult}">
    <!-- 상태 표시 -->
    <div th:switch="${result.status}">
        <span th:case="'VALID'" class="badge badge-success">✓ 검증 성공</span>
        <span th:case="'INVALID'" class="badge badge-error">✗ 검증 실패</span>
        <span th:case="'ERROR'" class="badge badge-warning">⚠ 오류 발생</span>
    </div>

    <!-- 여권 정보 -->
    <table class="table">
        <tr>
            <th>발급 국가</th>
            <td th:text="${result.issuingCountry}">-</td>
        </tr>
        <tr>
            <th>여권 번호</th>
            <td th:text="${result.documentNumber}">-</td>
        </tr>
        <tr>
            <th>검증 ID</th>
            <td th:text="${result.verificationId}">-</td>
        </tr>
        <tr>
            <th>처리 시간</th>
            <td th:text="${result.processingDurationMs + 'ms'}">-</td>
        </tr>
    </table>

    <!-- 상세 검증 결과 -->
    <div th:if="${result.certificateChainValidation != null}">
        <h4>인증서 체인</h4>
        <p th:class="${result.certificateChainValidation.valid} ? 'text-success' : 'text-error'">
            <span th:if="${result.certificateChainValidation.valid}">✓</span>
            <span th:unless="${result.certificateChainValidation.valid}">✗</span>
            DSC → CSCA 체인 검증
        </p>
    </div>
</div>
```

---

## 10. 보안 고려사항

### 10.1 네트워크 보안

- 프로덕션 환경에서는 **HTTPS** 사용 필수
- 방화벽으로 PA API 서버 접근 제한
- API 호출 시 인증 토큰 사용 고려

### 10.2 데이터 보안

- 여권 데이터는 개인정보이므로 로깅 시 주의
- SOD, DG2 등 민감 데이터는 메모리에서 즉시 삭제
- 검증 결과만 저장하고 원본 데이터는 저장하지 않음

### 10.3 감사 추적

- `requestedBy` 필드로 요청자 식별
- PA API 서버에서 모든 검증 이력 저장
- 정기적인 감사 로그 검토

---

## 11. FAQ

### Q1: SOD에서 Tag 0x77이 있는 경우와 없는 경우 처리?

A: Local PKD API는 두 경우 모두 자동으로 처리합니다. EF.SOD 바이너리를 그대로 Base64 인코딩하여 전송하면 됩니다.

### Q2: issuingCountry를 제공하지 않으면?

A: SOD 내의 DSC에서 국가 코드를 자동 추출합니다. 하지만 이력 조회의 편의를 위해 제공하는 것이 좋습니다.

### Q3: 어떤 Data Group을 제공해야 하나요?

A: 최소 1개 이상 필요하며, 일반적으로 DG1 (MRZ)과 DG2 (얼굴 이미지)를 제공합니다. SOD에 포함된 모든 DG를 제공하면 더 완전한 검증이 가능합니다.

### Q4: CRL 검증 실패 시에도 VALID가 될 수 있나요?

A: CRL을 찾을 수 없거나 만료된 경우 (`CRL_UNAVAILABLE`, `CRL_EXPIRED`) 경고만 표시되고, 다른 검증이 모두 통과하면 VALID가 될 수 있습니다. 단, `REVOKED` 상태는 무조건 INVALID입니다.

### Q5: 검증 시간이 너무 오래 걸립니다.

A: LDAP 연결 상태를 확인하세요. 첫 번째 요청은 연결 수립으로 인해 느릴 수 있습니다. 연속 요청은 커넥션 풀을 활용하여 더 빠릅니다.

---

## 📞 지원

- **이슈 등록**: GitHub Issues
- **API 문서**: Swagger UI (`/swagger-ui.html`)
- **기술 지원**: SmartCore Inc.

---

*이 문서는 Local PKD v5.1 기준으로 작성되었습니다.*
