# Local PKD Evaluation Project - Development Guide

**Version**: 6.2
**Last Updated**: 2025-12-28
**Status**: Production Ready - PKD Upload Module ✅ + Passive Authentication Module ✅ + Native Image ✅ + Docker Container ✅ + OpenLDAP MMR + HAProxy ✅ + LDAP R/W Separation ✅ + RFC 5280 LDAP Update ✅ + CRL Status Enhancement ✅ + Multi-Profile Support ✅ + **ARM64 Native Image ✅**

---

## 🏛️ System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Windows 11 Pro                                  │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    WSL2 Ubuntu + Docker Desktop                        │  │
│  │                                                                        │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │                    Docker Compose Network                        │  │  │
│  │  │                                                                  │  │  │
│  │  │   ┌─────────────┐      ┌─────────────────────────────────────┐  │  │  │
│  │  │   │  PostgreSQL │      │        OpenLDAP MMR Cluster         │  │  │  │
│  │  │   │   :5432     │      │  ┌───────────┐   ┌───────────┐      │  │  │  │
│  │  │   │  (Data)     │      │  │ OpenLDAP1 │◄─►│ OpenLDAP2 │      │  │  │  │
│  │  │   └──────┬──────┘      │  │  :3891    │   │  :3892    │      │  │  │  │
│  │  │          │             │  └─────┬─────┘   └─────┬─────┘      │  │  │  │
│  │  │          │             │        │   syncrepl    │            │  │  │  │
│  │  │          │             │        └───────┬───────┘            │  │  │  │
│  │  │          │             │                │                    │  │  │  │
│  │  │          │             │         ┌──────┴──────┐             │  │  │  │
│  │  │          │             │         │   HAProxy   │             │  │  │  │
│  │  │          │             │         │    :389     │             │  │  │  │
│  │  │          │             │         │ (LB + Stats │             │  │  │  │
│  │  │          │             │         │   :8404)    │             │  │  │  │
│  │  │          │             │         └──────┬──────┘             │  │  │  │
│  │  │          │             └────────────────┼────────────────────┘  │  │  │
│  │  │          │                              │                       │  │  │
│  │  │   ┌──────┴──────────────────────────────┴──────────────────┐   │  │  │
│  │  │   │              Local PKD Application                      │   │  │  │
│  │  │   │                    :8081                                │   │  │  │
│  │  │   │  ┌─────────────────────────────────────────────────┐   │   │  │  │
│  │  │   │  │              Spring Boot 3.5.5                   │   │   │  │  │
│  │  │   │  │  ┌─────────────┐  ┌──────────────────────────┐  │   │   │  │  │
│  │  │   │  │  │ PKD Upload  │  │ Passive Authentication   │  │   │   │  │  │
│  │  │   │  │  │   Module    │  │       Module             │  │   │   │  │  │
│  │  │   │  │  └─────────────┘  └──────────────────────────┘  │   │   │  │  │
│  │  │   │  └─────────────────────────────────────────────────┘   │   │  │  │
│  │  │   └────────────────────────────────────────────────────────┘   │  │  │
│  │  │                                                                  │  │  │
│  │  │   ┌──────────────┐  ┌──────────────┐                            │  │  │
│  │  │   │   pgAdmin    │  │ phpLDAPadmin │                            │  │  │
│  │  │   │    :5050     │  │    :8080     │                            │  │  │
│  │  │   └──────────────┘  └──────────────┘                            │  │  │
│  │  └──────────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    ePassport Reader Client                             │  │
│  │                   (Windows Native Application)                         │  │
│  │                        → http://<WSL-IP>:8081                         │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```

### LDAP Read/Write Separation Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Application Layer                                │
├─────────────────────────────────┬───────────────────────────────────────┤
│     PKD Upload (Write)          │      PA/Statistics (Read)             │
│  ┌─────────────────────────┐    │   ┌─────────────────────────────┐    │
│  │  UnboundIdLdapAdapter   │    │   │  UnboundIdLdapCscaAdapter   │    │
│  │  (Write Connection)     │    │   │  UnboundIdCrlLdapAdapter    │    │
│  └───────────┬─────────────┘    │   └──────────────┬──────────────┘    │
│              │                  │                   │                   │
│              ▼                  │                   ▼                   │
│   ┌─────────────────────┐       │        ┌─────────────────────┐       │
│   │    OpenLDAP 1       │       │        │      HAProxy        │       │
│   │     :3891           │       │        │       :389          │       │
│   │   (Direct Write)    │       │        │   (Load Balancer)   │       │
│   └─────────────────────┘       │        └──────────┬──────────┘       │
│              │                  │                   │                   │
│              │  MMR Sync        │        ┌──────────┴──────────┐       │
│              │                  │        │                     │       │
│              ▼                  │        ▼                     ▼       │
│   ┌─────────────────────┐       │  ┌───────────┐       ┌───────────┐  │
│   │    OpenLDAP 2       │◄──────┼─►│ OpenLDAP1 │       │ OpenLDAP2 │  │
│   │     :3892           │       │  │  (Read)   │       │  (Read)   │  │
│   └─────────────────────┘       │  └───────────┘       └───────────┘  │
└─────────────────────────────────┴───────────────────────────────────────┘
```

### Data Flow

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   ePassport  │     │  SOD + DGs   │     │   PA API     │     │   LDAP       │
│    Reader    │────►│   Upload     │────►│  Validation  │────►│   Lookup     │
│   (Client)   │     │              │     │              │     │  (CSCA/CRL)  │
└──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
                                                 │
                                                 ▼
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Response   │◄────│   Result     │◄────│  Verify      │
│   JSON/UI    │     │   Store      │     │  Trust Chain │
│              │     │  (Postgres)  │     │  + Hashes    │
└──────────────┘     └──────────────┘     └──────────────┘
```

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
- ✅ **RFC 5280 준수 LDAP 업데이트** (신규/업데이트/스킵 비교 로직)
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
- ✅ CRL 검증 (Two-Tier Caching, 상세 상태 설명)
- ✅ DG1/DG2 파싱 (MRZ, 얼굴 이미지)
- ✅ MRZ 텍스트 파일 업로드 지원
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

### RFC 5280 준수 LDAP 업데이트 (2025-12-25 추가)

LDAP에 동일한 데이터가 이미 존재하는 경우 중복 저장을 방지하고, 변경된 경우만 업데이트합니다.

| 데이터 유형 | 비교 기준 | 동작 |
|------------|----------|------|
| 인증서 (CSCA/DSC) | DN + 바이너리 + description | 신규 ADD, description 변경시 MODIFY, 동일시 SKIP |
| CRL | DN + CRL Number (OID 2.5.29.20) | 신규 ADD, CRL Number 증가시 MODIFY, 동일/이전시 SKIP |
| Master List | DN + CMS 바이너리 | 신규 ADD, 내용 변경시 MODIFY, 동일시 SKIP |

**Progress 메시지 예시**:
```
LDAP 저장 완료 (신규 5개, 업데이트 2개, 동일하여 스킵 3개)
```

상세 문서: `docs/RFC5280_LDAP_UPDATE_GUIDE.md`

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
- POST `/api/pa/parse-dg1` - DG1 바이너리 MRZ 파싱
- POST `/api/pa/parse-mrz-text` - MRZ 텍스트 파싱 (mrz.txt 파일 지원)
- POST `/api/pa/parse-dg2` - 얼굴 이미지 파싱

---

## 🔐 CRL Validation Status (2025-12-26 추가)

CRL 검증 결과를 외부 클라이언트가 명확하게 이해할 수 있도록 상세 설명을 제공합니다.

### CRL Status Values

| Status | Description (EN) | Severity |
|--------|------------------|----------|
| VALID | Certificate is valid and not revoked | SUCCESS |
| REVOKED | Certificate has been revoked | FAILURE |
| CRL_UNAVAILABLE | CRL not available in LDAP | WARNING |
| CRL_EXPIRED | CRL has expired (nextUpdate passed) | WARNING |
| CRL_INVALID | CRL signature verification failed | FAILURE |
| NOT_CHECKED | CRL verification was not performed | INFO |

### API Response Fields

```json
{
  "certificateChainValidation": {
    "crlStatus": "VALID",
    "crlStatusDescription": "Certificate is valid and not revoked",
    "crlStatusDetailedDescription": "인증서가 유효하며 폐기되지 않았습니다...",
    "crlStatusSeverity": "SUCCESS",
    "crlMessage": "CRL 검증 완료"
  }
}
```

---

## 🕐 Timezone Handling (2025-12-26 추가)

모든 시간 표시는 한국 표준시(KST, Asia/Seoul, UTC+9)를 사용합니다.

### 시간대 설정

| 레이어 | 설정 | 위치 |
|--------|------|------|
| PostgreSQL | `TZ: Asia/Seoul`, `PGTZ: Asia/Seoul` | docker-compose.yaml |
| JVM (Container) | `TZ: Asia/Seoul` | docker-compose.yaml |
| API Response | `@JsonFormat(timezone = "Asia/Seoul")` | PassiveAuthenticationResponse.java |
| Frontend | `toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })` | history.html, verify.html, dashboard.html |

### API 응답 형식

```json
{
  "verificationTimestamp": "2025-12-27T11:30:29"
}
```

- ISO 8601 형식 (타임존은 서버 설정에 따라 KST)
- 패턴: `yyyy-MM-dd'T'HH:mm:ss`
- `LocalDateTime` 사용 (타임존 오프셋 제외)

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

### 프로파일별 실행 (2025-12-27 추가)

| 프로파일 | 용도 | LDAP 연결 | 실행 스크립트 |
|---------|------|----------|--------------|
| `local` | 로컬 Docker 개발 | localhost (Write: 3891, Read: 389) | `./scripts/run-local.sh` |
| `remote` | 원격 LDAP 서버 | 192.168.100.10 (Write: 389, Read: 10389) | `./scripts/run-remote.sh` |
| `container` | Docker 컨테이너 배포 | Docker 네트워크 | `./scripts/run-container.sh` |
| `arm64` | Luckfox ARM64 배포 | 192.168.100.10 (Write: 389, Read: 10389) | Docker Compose |

상세 문서: `docs/PROFILE_EXECUTION_GUIDE.md`

### JVM Mode (개발용)

```bash
# 컨테이너 시작
./docker-start.sh --skip-app

# 빌드
./mvnw clean compile

# 테스트
./mvnw test

# Local 프로파일 실행 (권장)
./scripts/run-local.sh

# 또는 수동 실행
./mvnw spring-boot:run
# http://localhost:8081
```

### Native Image Mode (프로덕션용)

```bash
# 컨테이너 시작 (DB만)
./docker-start.sh --skip-app

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

### ARM64 Native Image Mode (Luckfox 배포용) (2025-12-28 추가)

```bash
# ARM64 Native Image 빌드 (Docker Buildx + QEMU, 약 4-5시간 소요)
./scripts/arm64-build.sh

# 빌드 결과물
# - local-pkd-arm64.tar (231MB Docker 이미지)

# Luckfox 장비로 전송 및 배포
scp local-pkd-arm64.tar luckfox@192.168.100.11:/home/luckfox/
ssh luckfox@192.168.100.11 'sudo docker load -i /home/luckfox/local-pkd-arm64.tar'
ssh luckfox@192.168.100.11 'sudo docker compose -f docker-compose.arm64.yaml up -d'
```

**ARM64 빌드 상세**:
- 빌드 도구: Docker Buildx + QEMU user-mode emulation
- 타겟 플랫폼: `linux/arm64` (aarch64)
- GraalVM: GraalVM CE 21.0.2 (ARM64)
- Native Image 크기: 306MB
- Docker 이미지: 231MB (tar)

상세 문서: `docs/ARM64_DEPLOYMENT_GUIDE.md`, `docs/LUCKFOX_SYSTEM_ANALYSIS.md`

### Docker Container Mode (외부 클라이언트 연동)

```bash
# Native Image 빌드 (최초 1회)
./scripts/native-build.sh --skip-tests

# 전체 서비스 시작 (DB + App)
./docker-start.sh

# 이미지 재빌드 시
./docker-start.sh --build
```

**컨테이너 구성**:
- `icao-local-pkd-postgres`: PostgreSQL 15 (port 5432, timezone: Asia/Seoul)
- `icao-local-pkd-pgadmin`: pgAdmin (port 5050)
- `icao-local-pkd-haproxy`: HAProxy LDAP Load Balancer (port 389, 8404)
- `icao-local-pkd-openldap1`: OpenLDAP Master 1 (port 3891, MMR Node 1)
- `icao-local-pkd-openldap2`: OpenLDAP Master 2 (port 3892, MMR Node 2)
- `icao-local-pkd-phpldapadmin`: phpLDAPadmin (port 8080)
- `icao-local-pkd-app`: Local PKD Native Image (port 8081, host network)

**Docker 스크립트**:
- `./docker-start.sh` - 컨테이너 시작 (옵션: `--build`, `--skip-app`, `--skip-ldap`)
- `./docker-stop.sh` - 컨테이너 중지
- `./docker-restart.sh` - 컨테이너 재시작
- `./docker-logs.sh [서비스]` - 로그 확인
- `./docker-health.sh` - 헬스 체크
- `./docker-backup.sh` - 데이터 백업
- `./docker-restore.sh <백업폴더>` - 데이터 복구
- `./docker-clean.sh` - 완전 삭제 (볼륨 포함)
- `./docker-ldap-init.sh` - LDAP ICAO PKD DIT 구조 초기화

**Windows 클라이언트 접속** (ePassport Reader 연동):
```bash
# WSL2 IP 확인
hostname -I  # 예: 172.24.1.6

# UFW 방화벽 허용 (최초 1회)
sudo ufw allow 8081/tcp

# Windows에서 접속
http://172.24.1.6:8081
```

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
| CRL Status Enhancement | ✅ |
| DG1/DG2 Parsing | ✅ |
| MRZ Text File Upload | ✅ |
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

### Docker Containerization ✅ PRODUCTION READY

| Feature | Status |
|---------|--------|
| Dockerfile (Native Image) | ✅ |
| docker-compose.yaml | ✅ |
| PostgreSQL Timezone (Asia/Seoul) | ✅ |
| Host Network Mode | ✅ |
| Windows Client Access | ✅ |
| PA API Integration Guide | ✅ |
| Docker Desktop (Windows 11 Pro) | ✅ |

> **Note**: Podman 스크립트는 `scripts/podman-backup/` 폴더에 백업되어 있습니다.

### ARM64 Native Image ✅ PRODUCTION READY (2025-12-28 추가)

| Feature | Status |
|---------|--------|
| Dockerfile.arm64 (Cross-compile) | ✅ |
| docker-compose.arm64.yaml | ✅ |
| application-arm64.properties | ✅ |
| GraalVM Watchdog 비활성화 | ✅ |
| Luckfox 시스템 분석 | ✅ |

**Luckfox Omni3576 Target System**:
- CPU: 4×Cortex-A72 + 4×Cortex-A53 (8코어)
- RAM: 3.8GB
- OS: Debian 12 (bookworm)
- Storage: 29GB eMMC

**배포 대상 노드**:
| 노드 | IP | 역할 |
|------|-----|------|
| Master | 192.168.100.10 | OpenLDAP Master + HAProxy LB |
| Slave | 192.168.100.11 | OpenLDAP Slave + Docker (App 배포) |

### OpenLDAP Multi-Master Replication + HAProxy ✅ PRODUCTION READY

| Feature | Status |
|---------|--------|
| OpenLDAP MMR (2노드) | ✅ |
| HAProxy Load Balancer | ✅ |
| ICAO PKD Custom Schemas | ✅ |
| phpLDAPadmin | ✅ |
| DIT 초기화 스크립트 | ✅ |

**ICAO PKD Custom Schemas** (`openldap/schemas/icao-pkd.ldif`):
- `pkdDownload` - PKD 다운로드 객체 (CSCA, DSC, DSC_NC)
  - `pkdVersion` - 버전 정보
  - `pkdConformanceText` - DSC_NC 적합성 텍스트
  - `pkdConformanceCode` - DSC_NC 적합성 코드
  - `pkdConformancePolicy` - DSC_NC 적합성 정책 OID
- `pkdMasterList` - Master List CMS 저장
  - `pkdMasterListContent` - CMS SignedData 바이너리

**LDAP 접속 정보**:
- HAProxy (로드밸런싱): `ldap://localhost:389`
- HAProxy Stats UI: `http://localhost:8404/stats`
- OpenLDAP 1 (직접 연결): `ldap://localhost:3891`
- OpenLDAP 2 (직접 연결): `ldap://localhost:3892`

**MMR 설정**:
- 양방향 실시간 복제 (refreshAndPersist)
- HAProxy가 Round-Robin 방식으로 요청 분산
- Health Check: 5초 간격, 3회 실패 시 제외

### LDAP Read/Write 분리 ✅ PRODUCTION READY

| Feature | Status |
|---------|--------|
| Write 전용 연결 (OpenLDAP 1) | ✅ |
| Read 로드밸런싱 (HAProxy) | ✅ |
| 연결 풀 분리 | ✅ |
| 설정 기반 활성화/비활성화 | ✅ |

**Read/Write 분리 아키텍처**:
```
┌─────────────────────────────────────────────────────────────┐
│                    Application                               │
├─────────────────────────────────────────────────────────────┤
│  PKD Upload (Write)          │  PA/Statistics (Read)        │
│  ├─ UnboundIdLdapAdapter     │  ├─ UnboundIdLdapCscaAdapter │
│  └─ → OpenLDAP 1 (:3891)     │  ├─ UnboundIdCrlLdapAdapter  │
│                               │  └─ → HAProxy (:389)         │
└─────────────────────────────────────────────────────────────┘
                                         │
                                         ▼
                              ┌─────────────────┐
                              │    HAProxy      │
                              │  (Round-Robin)  │
                              └────────┬────────┘
                                       │
                    ┌──────────────────┴──────────────────┐
                    ▼                                      ▼
           ┌─────────────┐                        ┌─────────────┐
           │ OpenLDAP 1  │◄────── MMR Sync ──────►│ OpenLDAP 2  │
           │   (:3891)   │                        │   (:3892)   │
           └─────────────┘                        └─────────────┘
```

**설정 예시** (application-local.properties):
```properties
# Write: OpenLDAP 1 직접 연결
app.ldap.write.enabled=true
app.ldap.write.url=ldap://localhost:3891
app.ldap.write.pool-initial-size=5
app.ldap.write.pool-max-size=20

# Read: HAProxy 로드밸런싱
app.ldap.read.enabled=true
app.ldap.read.url=ldap://localhost:389
app.ldap.read.pool-initial-size=3
app.ldap.read.pool-max-size=10
```

**장점**:
- PKD 업로드 시 Write 연결로 일관된 저장 보장
- PA 검증/통계 조회 시 Read 로드밸런싱으로 성능 향상
- 연결 풀 분리로 리소스 효율적 관리

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
./docker-restart.sh
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
| DOCKER_CONFIGURATION_MANUAL | Docker 구성 및 운영 가이드 | docs/DOCKER_CONFIGURATION_MANUAL.md |
| PROFILE_EXECUTION_GUIDE | 프로파일별 실행 가이드 | docs/PROFILE_EXECUTION_GUIDE.md |
| ICAO_9303_PA_CRL_STANDARD | PA + CRL 표준 절차 | docs/ICAO_9303_PA_CRL_STANDARD.md |
| DG1_DG2_PARSING_GUIDE | DG 파싱 가이드 | docs/DG1_DG2_PARSING_GUIDE.md |
| LDAP_BASE_DN_RECOVERY | LDAP 복구 가이드 | docs/LDAP_BASE_DN_RECOVERY.md |
| NATIVE_IMAGE_GUIDE | Native Image 빌드/실행 | docs/NATIVE_IMAGE_GUIDE.md |
| PA_API_INTEGRATION_GUIDE | 외부 클라이언트 PA API 연동 | docs/PA_API_INTEGRATION_GUIDE.md |
| RFC5280_LDAP_UPDATE_GUIDE | RFC 5280 준수 LDAP 업데이트 | docs/RFC5280_LDAP_UPDATE_GUIDE.md |
| ARM64_DEPLOYMENT_GUIDE | ARM64 빌드/배포 가이드 | docs/ARM64_DEPLOYMENT_GUIDE.md |
| LUCKFOX_SYSTEM_ANALYSIS | Luckfox 시스템 환경 분석 | docs/LUCKFOX_SYSTEM_ANALYSIS.md |

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
