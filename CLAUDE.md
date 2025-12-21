# Local PKD Evaluation Project - Development Guide

**Version**: 5.1
**Last Updated**: 2025-12-22
**Status**: Production Ready - PKD Upload Module ✅ + Passive Authentication Module ✅ + Native Image ✅

---

## 🎯 Quick Overview

### 1. PKD Upload Module (완료 ✅)
ICAO PKD 파일(Master List .ml, LDIF .ldif)을 업로드하여 인증서를 파싱, 검증 후 OpenLDAP에 저장하는 웹 애플리케이션입니다.

**핵심 기능**:
- ✅ 파일 업로드 (중복 감지, 서버 측 체크섬 검증)
- ✅ 비동기 파일 처리 (즉시 uploadId 반환)
- ✅ 파일 파싱 (LDIF, Master List CMS)
- ✅ 인증서 검증 (Trust Chain, CRL, 유효기간)
- ✅ OpenLDAP 자동 등록 (검증 상태 포함)
- ✅ 실시간 진행 상황 (uploadId별 SSE 스트림)
- ✅ 업로드 이력 관리 (단계별 상태 추적)
- ✅ PKD 통계 대시보드 (차트, 국가별 통계)

### 2. Passive Authentication Module (완료 ✅)
ePassport 검증을 위한 Passive Authentication (PA) 기능입니다.

**핵심 기능**:
- ✅ ICAO 9303 표준 준수 PA 검증
- ✅ SOD 파싱 (Tag 0x77 unwrapping, DSC 추출)
- ✅ Trust Chain 검증 (CSCA → DSC)
- ✅ Data Group 해시 검증
- ✅ CRL 검증 (Two-Tier Caching)
- ✅ DG1/DG2 파싱 (MRZ, 얼굴 이미지)
- ✅ PA 검증 UI (실시간 검증, 결과 시각화)
- ✅ PA 이력 페이지 (필터링, 상세 조회)
- ✅ PA 통계 대시보드

**Tech Stack**:
- Backend: Spring Boot 3.5.5, Java 21, PostgreSQL 15.14
- DDD Libraries: JPearl 2.0.1, MapStruct 1.6.3
- Frontend: Thymeleaf, Alpine.js 3.14.8, HTMX 2.0.4, DaisyUI 5.0
- Certificate: Bouncy Castle 1.70, UnboundID LDAP SDK

---

## 🏗️ DDD Architecture

### Bounded Contexts (5개)

```
fileupload/              # File Upload Context (PKD 파일 업로드)
├── domain/              # UploadedFile (Aggregate), Value Objects (11개)
├── application/         # Use Cases, Commands, AsyncUploadProcessor
└── infrastructure/      # Controllers, Adapters, Repositories

fileparsing/             # File Parsing Context (PKD 파일 파싱)
├── domain/              # ParsedFile, ParsedCertificate, CRL
├── application/         # ParseLdifFileUseCase, ParseMasterListFileUseCase
└── infrastructure/      # LdifParserAdapter, MasterListParserAdapter

certificatevalidation/   # Certificate Validation Context (PKD 인증서 검증)
├── domain/              # Trust Chain, CRL Checking, Certificate
├── application/         # ValidateCertificatesUseCase, UploadToLdapUseCase
└── infrastructure/      # BouncyCastleValidationAdapter, UnboundIdLdapConnectionAdapter

passiveauthentication/   # Passive Authentication Context (ePassport 검증)
├── domain/              # PassportData (Aggregate), DataGroup, SOD (Value Objects)
├── application/         # PerformPassiveAuthenticationUseCase
└── infrastructure/      # SodParserAdapter, DG Parsers, Controller

shared/                  # Shared Kernel
├── domain/              # AbstractAggregateRoot, DomainEvent
├── exception/           # DomainException, InfrastructureException
├── progress/            # ProcessingProgress, ProgressService (SSE)
└── util/                # HashingUtil
```

---

## 📋 Critical Coding Rules (필수 준수)

### 1. Value Object 작성 규칙

```java
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA용 (필수!)
public class CollectionNumber {
    private String value;  // ❌ final 금지 (JPA가 값 설정 불가)

    public static CollectionNumber of(String value) {
        return new CollectionNumber(value);
    }

    private CollectionNumber(String value) {
        validate(value);
        this.value = value;
    }
}
```

**핵심 요구사항**:
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` - Hibernate 필수
- 필드는 **non-final** - JPA 리플렉션 값 주입용
- 정적 팩토리 메서드 (of, from, extractFrom)
- Self-validation (생성 시점 검증)

### 2. 예외 처리 규칙

```java
// ✅ Domain Layer
throw new DomainException("INVALID_FILE_FORMAT", "파일 형식이 올바르지 않습니다");

// ✅ Application Layer
throw new BusinessException("DUPLICATE_FILE", "중복 파일이 감지되었습니다", details);

// ✅ Infrastructure Layer
throw new InfrastructureException("FILE_SAVE_ERROR", "파일 저장 중 오류");

// ❌ 절대 사용 금지
throw new IllegalArgumentException("Invalid");
throw new RuntimeException("Error");
```

### 3. Async Processing 규칙

```java
@Async("taskExecutor")  // 명시적 Executor 지정
public void processLdif(UploadId uploadId, ...) {
    try {
        // 비즈니스 로직 실행
    } catch (Exception e) {
        progressService.sendProgress(
            ProcessingProgress.failed(uploadId.getId(), ProcessingStage.UPLOAD_COMPLETED, e.getMessage())
        );
    }
}
```

---

## 🌳 LDAP DIT Structure (ICAO PKD 표준)

### DIT 구조

| Item | LDAP DN | ObjectClass |
|------|---------|-------------|
| CSCA | `o=csca,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}` | inetOrgPerson, pkdDownload |
| DSC | `o=dsc,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}` | inetOrgPerson, pkdDownload |
| DSC NC | `o=dsc,c={COUNTRY},dc=nc-data,dc=download,dc=pkd,{baseDN}` | inetOrgPerson, pkdDownload |
| CRL | `o=crl,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}` | cRLDistributionPoint |
| Master List | `o=ml,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}` | pkdMasterList |

### Certificate Validation (Two-Pass)

**Pass 1**: CSCA Validation (Self-Signed)
- Self-Signed Signature 검증
- Validity Period 검증
- Basic Constraints 검증

**Pass 2**: DSC Validation (Trust Chain)
- CSCA 조회 → DSC 서명 검증
- Validity Period 검증

---

## 📄 ICAO 9303 SOD Structure

SOD (Security Object Document)는 ePassport의 무결성을 보장하기 위한 핵심 데이터 구조입니다.

```
Tag 0x77 (Application 23) - EF.SOD wrapper
  └─ CMS SignedData (Tag 0x30)
       ├─ encapContentInfo (LDSSecurityObject)
       │   └─ dataGroupHashValues (DG1, DG2, ... hashes)
       ├─ certificates [0]
       │   └─ DSC certificate (X.509)
       └─ signerInfos
           └─ signature
```

### Passive Authentication Workflow

```
1. Client → API: SOD + Data Groups
2. unwrapIcaoSod(SOD) → Extract CMS SignedData
3. extractDscCertificate(SOD) → Extract DSC from certificates [0]
4. LDAP Lookup: Find CSCA by Subject DN
5. Verify DSC Trust Chain: dscCert.verify(cscaPublicKey)
6. Verify SOD Signature: CMSSignedData.verifySignatures(dscPublicKey)
7. Compare Data Group Hashes
8. Check CRL (Optional)
9. Result: VALID / INVALID / ERROR
```

---

## 📑 DG1/DG2 Parsing

### DG1: Machine Readable Zone (MRZ)

**TD3 Format (88 chars)**:
```
P<KORHONG<GILDONG<<<<<<<<<<<<<<<<<<<<<<
M12345678KOR8001019M2501012<<<<<<<<<<<<<<
```

**Parsing Output**:
```json
{
  "surname": "HONG",
  "givenNames": "GILDONG",
  "documentNumber": "M12345678",
  "nationality": "KOR",
  "dateOfBirth": "1980-01-01"
}
```

### DG2: Face Image

Face images are wrapped in ISO/IEC 19794-5 containers with JPEG data.

**REST API Endpoints**:
- POST `/api/pa/parse-dg1` - MRZ 파싱
- POST `/api/pa/parse-dg2` - 얼굴 이미지 파싱

---

## 💾 Database Schema

### 주요 테이블

```sql
-- 파일 업로드 이력
uploaded_file (id, file_name, file_hash, status, uploaded_at, ...)

-- 파싱된 인증서
parsed_certificate (id, upload_id, certificate_type, country_code, validation_status, ...)

-- CRL
certificate_revocation_list (id, upload_id, issuer_name, country_code, ...)

-- PA 검증 기록
passport_data (id, verification_id, status, dg1, dg2, sod, ...)

-- PA 감사 로그
passive_authentication_audit_log (id, verification_id, timestamp, ...)
```

---

## 🚀 Build & Run

### JVM Mode (개발용)

```bash
# 컨테이너 시작
./podman-start.sh

# 빌드
./mvnw clean compile

# 테스트
./mvnw test

# 실행
./mvnw spring-boot:run
# http://localhost:8081
```

### Native Image Mode (프로덕션용)

```bash
# 컨테이너 시작
./podman-start.sh

# Native Image 빌드 (5-10분 소요)
./scripts/native-build.sh --skip-tests

# Native Image 실행
./scripts/native-run.sh
# http://localhost:8081
```

**Native Image 장점**:
- 빠른 시작: ~0.1초 (JVM: ~5초)
- 낮은 메모리: ~100MB (JVM: ~500MB)
- 단일 실행 파일: `target/local-pkd`

---

## 📊 Project Status

### PKD Upload Module ✅ PRODUCTION READY

| Feature | Status |
|---------|--------|
| File Upload (LDIF, ML) | ✅ |
| Async Processing | ✅ |
| Certificate Parsing | ✅ |
| Certificate Validation | ✅ |
| LDAP Upload | ✅ |
| SSE Progress | ✅ |
| Statistics Dashboard | ✅ |

### Passive Authentication Module ✅ PRODUCTION READY

| Feature | Status |
|---------|--------|
| SOD Parsing (ICAO 9303) | ✅ |
| DSC Extraction | ✅ |
| Trust Chain Verification | ✅ |
| Data Group Hash Verification | ✅ |
| CRL Checking | ✅ |
| DG1/DG2 Parsing | ✅ |
| PA Verification UI | ✅ |
| PA History UI | ✅ |
| PA Dashboard | ✅ |

### UI Structure (2025-12-21)

**Homepage (`/`)**:
- Feature Cards (PKD 업로드, PA 검증, PA 이력)
- PostgreSQL/LDAP 연결 상태 카드 (테스트 버튼 포함)
- 표준 준수 배지 (ICAO Doc 9303, RFC 5652, RFC 5280, ISO 19794-5)

**PKD 업로드 메뉴**:
- 파일 업로드 (`/file/upload`)
- 업로드 이력 (`/upload-history`)
- PKD 통계 (`/file/dashboard`)

**PA 검증 메뉴**:
- PA 수행 (`/pa/verify`)
- PA 이력 (`/pa/history`)
- PA 통계 (`/pa/dashboard`)

### GraalVM Native Image ✅ PRODUCTION READY

| Feature | Status |
|---------|--------|
| Native Image Build | ✅ |
| BouncyCastle Reflection Config | ✅ |
| Thymeleaf Pure Fragment Pattern | ✅ |
| Build/Run Scripts | ✅ |

### Future Enhancements (Optional)

- ⏳ 실시간 검증 진행 상황 (SSE 기반)
- ⏳ 배치 검증 지원 (여러 여권 동시 검증)
- ⏳ 검증 리포트 내보내기 (PDF, CSV)
- ⏳ Active Authentication 지원

---

## 🔧 Troubleshooting

### 빌드 오류

```bash
# 포트 충돌
lsof -ti:8081 | xargs kill -9

# 컨테이너 재시작
./podman-restart.sh
```

### Value Object JPA 오류

**해결책**: `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 확인, 필드는 non-final

### LDAP Base DN 삭제 복구

```bash
./scripts/restore-ldap.sh
# 비밀번호: core
```

### WSL2 Windows 접근

```bash
# WSL IP 확인
hostname -I

# Windows에서 접속
http://<WSL-IP>:8081
```

---

## 📁 Key Documents

| 문서 | 용도 | 위치 |
|------|--------|------|
| ICAO_9303_PA_CRL_STANDARD | PA + CRL 표준 절차 | docs/ICAO_9303_PA_CRL_STANDARD.md |
| DG1_DG2_PARSING_GUIDE | DG 파싱 가이드 | docs/DG1_DG2_PARSING_GUIDE.md |
| LDAP_BASE_DN_RECOVERY | LDAP 복구 가이드 | docs/LDAP_BASE_DN_RECOVERY.md |
| NATIVE_IMAGE_GUIDE | Native Image 빌드/실행 | docs/NATIVE_IMAGE_GUIDE.md |

**세션 문서**: `docs/SESSION_*.md` (개발 이력)
**아카이브**: `docs/archive/phases/` (Phase 1-19 문서)

---

## 🎓 Architecture Patterns

- **Domain-Driven Design (DDD)**: 5 Bounded Contexts, Value Objects, Aggregates
- **Hexagonal Architecture**: Ports & Adapters
- **CQRS**: Command/Query 분리
- **Event-Driven Architecture**: Domain Events, @TransactionalEventListener
- **Async Processing**: @Async, SSE (Server-Sent Events)

---

**프로젝트 소유자**: kbjung
**개발 팀**: SmartCore Inc.

*상세한 구현 내용은 `docs/` 디렉토리의 개별 문서를 참조하세요.*
