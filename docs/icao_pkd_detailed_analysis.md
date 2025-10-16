# ICAO PKD 데이터 구조 상세 분석

## 1. ICAO PKD 개요

### 1.1 PKD (Public Key Directory)의 목적
```
ICAO PKD는 전자여권(ePassport) 검증을 위한 공개키 인프라(PKI) 저장소

목적:
- 여권 검증 시 필요한 인증서 제공
- 회원국 간 인증서 신뢰 체인 구축
- 인증서 폐기 정보(CRL) 관리
- 표준 편차(Deviation) 관리
```

---

## 2. 4가지 데이터 유형 상세 분석

### 2.1 Master List (CSCA)

#### 특징
```
CSCA = Country Signing Certificate Authority
역할: 각 국가의 최상위 인증 기관
용도: DS 인증서 발급 및 서명
```

#### 파일 형태

**A. .ml 파일 (전체 회원국 통합)**
```
파일명 예시: icaopkd-001-complete-000000.ml
구조: CMS (Cryptographic Message Syntax)
서명: UN/ICAO Root CA 서명
내용: 
  - 모든 회원국의 CSCA 인증서
  - 주기적으로 전체 업데이트
  - 신규 가입국 포함
  - 만료된 인증서 포함 (이력 관리용)

발행 주기: 분기별 또는 반기별
크기: 수 MB ~ 수십 MB

특이사항:
  - 디지털 서명 필수 검증
  - UN/ICAO Root CA로 신뢰 체인 검증
  - ASN.1/DER 인코딩
  - 중복 인증서 가능 (버전 관리)
```

**B. .ldif 파일 (국가별 업데이트)**
```
파일명 예시: 
  - ML_AE.ldif  (아랍에미리트)
  - ML_KR.ldif  (대한민국)
  - ML_US.ldif  (미국)

구조: LDIF (LDAP Data Interchange Format)
서명: 없음 또는 선택적
내용:
  - 특정 국가의 CSCA 인증서만
  - 신규 발급/갱신/만료 인증서
  - .ml 파일 대비 작은 크기
  - 증분 업데이트 (Delta Update)

발행 주기: 수시 (변경 발생 시)
크기: 수 KB ~ 수백 KB

LDIF 구조 예시:
dn: cn=CSCA-KR-001,ou=CSCA,c=KR,dc=ml-data,dc=download,dc=pkd
objectClass: top
objectClass: pkiCA
objectClass: cscaCertificateObject
cn: CSCA-KR-001
countryCode: KR
cACertificate;binary:: MIIFxjCCA66gAwIBAgI...
serialNumber: 123456789
notBefore: 20230101000000Z
notAfter: 20330101000000Z
```

#### 관계
```
.ml 파일 (완전판)
    ↓
[정기 업데이트]
    ↓
.ldif 파일 (증분 업데이트)
    ↓
[병합]
    ↓
Local PKD (최신 상태 유지)
```

---

### 2.2 DSC (Document Signer Certificate)

#### 특징
```
DS = Document Signer
역할: 실제 여권 데이터에 서명하는 인증서
발급자: 각 국가의 CSCA
용도: 여권 칩 내 데이터 서명
```

#### 파일 형태

**LDIF 파일만 존재**
```
파일명 예시:
  - CscaDS_AE.ldif  (아랍에미리트 DS)
  - CscaDS_KR.ldif  (대한민국 DS)
  - CscaDS_JP.ldif  (일본 DS)

발행 주기: 매우 빈번 (주/월 단위)
크기: 수백 KB ~ 수 MB
수량: 국가당 수십~수백 개

LDIF 구조 예시:
dn: cn=DS-KR-001,ou=DS,c=KR,dc=ml-data,dc=download,dc=pkd
objectClass: top
objectClass: pkiCA
objectClass: documentSignerCertificateObject
cn: DS-KR-001
countryCode: KR
issuerDN: C=KR,O=MOFA,CN=CSCA-KR-001
cACertificate;binary:: MIIFxjCCA66gAwIBAgI...
serialNumber: 987654321
notBefore: 20240101000000Z
notAfter: 20250101000000Z
```

#### 특이사항
```
- CSCA에 비해 수량 훨씬 많음
- 유효기간 짧음 (1-2년)
- 교체 빈번
- 여권 발급 지점별로 다를 수 있음
- 반드시 CSCA로 서명 검증 가능
```

---

### 2.3 CRL (Certificate Revocation List)

#### 특징
```
CRL = 인증서 폐기 목록
역할: 폐기된 인증서 목록 관리
용도: 인증서 유효성 실시간 확인
```

#### 파일 형태

```
파일명 예시:
  - CRL_KR.ldif
  - CRL_US.ldif
  - 또는 .crl 확장자 (DER/PEM 인코딩)

구조: 
  - LDIF 형식
  - 또는 X.509 CRL (ASN.1)

내용:
  - 폐기된 인증서 일련번호 목록
  - 폐기 사유
  - 폐기 일시
  - 다음 업데이트 시간

발행 주기: 매일 또는 실시간
크기: 가변적 (폐기 건수에 따라)

LDIF 구조 예시:
dn: cn=CRL-001,ou=CRL,c=KR,dc=ml-data,dc=download,dc=pkd
objectClass: top
objectClass: cRLDistributionPoint
cn: CRL-001
countryCode: KR
certificateRevocationList;binary:: MIICxjCCAa4CAQ...
thisUpdate: 20241201000000Z
nextUpdate: 20241202000000Z
revokedCertificates: 123456789,987654321,555555555
```

#### 사용 시나리오
```
여권 검증 프로세스:
1. 여권 칩에서 DS 인증서 추출
2. CSCA로 DS 인증서 검증
3. CRL 확인 → 폐기 여부 확인
4. 폐기되지 않았으면 여권 유효
```

---

### 2.4 Deviation List

#### 특징
```
Deviation = 표준 편차/예외 사항
역할: ICAO 표준에서 벗어난 구현 사항 기록
용도: 상호 운용성 문제 해결
```

#### 파일 형태

```
파일명 예시:
  - DeviationList.ldif
  - Deviation_KR.ldif

구조: LDIF 또는 텍스트

내용:
  - 국가별 표준 미준수 사항
  - 알려진 문제점
  - 해결 방법 또는 우회 방법
  - 영향 범위

발행 주기: 수시 (문제 발견 시)
크기: 작음 (수 KB)

LDIF 구조 예시:
dn: cn=DEV-001,ou=Deviation,c=KR,dc=ml-data,dc=download,dc=pkd
objectClass: top
objectClass: deviationObject
cn: DEV-001
countryCode: KR
deviationType: CERTIFICATE_ENCODING
description: Uses non-standard OID for passport number
severity: LOW
workaround: Parse alternative OID 2.5.4.97.1
reportedDate: 20240101
status: ACKNOWLEDGED
```

#### 예시 케이스
```
편차 유형:
1. 인코딩 문제
   - UTF-8 vs ISO-8859-1
   - OID 비표준 사용

2. 인증서 구조
   - 확장 필드 누락
   - 필수 속성 미포함

3. 암호화 알고리즘
   - 구형 알고리즘 사용
   - 키 길이 부족

4. 프로토콜 구현
   - PACE 미지원
   - BAC만 지원
```

---

## 3. 파일 간 관계도

```
┌─────────────────────────────────────────────────────┐
│                   ICAO PKD                          │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────────────────────────────────┐          │
│  │  Master List (.ml)                   │          │
│  │  - 전체 회원국 CSCA                   │          │
│  │  - UN/ICAO 서명                       │          │
│  │  - 분기/반기 업데이트                  │          │
│  └──────────┬───────────────────────────┘          │
│             │ 완전판                                │
│             ↓                                       │
│  ┌──────────────────────────────────────┐          │
│  │  Master List (.ldif)                 │          │
│  │  - 국가별 CSCA 증분 업데이트          │          │
│  │  - ML_KR.ldif, ML_US.ldif...        │          │
│  └──────────┬───────────────────────────┘          │
│             │ 발급                                  │
│             ↓                                       │
│  ┌──────────────────────────────────────┐          │
│  │  DSC (.ldif)                         │          │
│  │  - 국가별 문서 서명 인증서             │          │
│  │  - CscaDS_KR.ldif, CscaDS_US.ldif   │          │
│  │  - CSCA로 서명됨                      │          │
│  └──────────┬───────────────────────────┘          │
│             │                                       │
│             │ 검증                                  │
│  ┌──────────┴───────────────────────────┐          │
│  │  CRL (.ldif / .crl)                  │          │
│  │  - 폐기된 인증서 목록                  │          │
│  │  - 실시간/일일 업데이트                │          │
│  └──────────────────────────────────────┘          │
│                                                     │
│  ┌──────────────────────────────────────┐          │
│  │  Deviation List (.ldif)              │          │
│  │  - 표준 편차 정보                      │          │
│  │  - 호환성 문제 해결책                  │          │
│  └──────────────────────────────────────┘          │
│                                                     │
└─────────────────────────────────────────────────────┘

         ↓ 다운로드 및 적용

┌─────────────────────────────────────────────────────┐
│              Local PKD (우리 시스템)                  │
├─────────────────────────────────────────────────────┤
│                                                     │
│  LDAP Directory                                     │
│  ├── dc=ml-data,dc=download,dc=pkd                 │
│      ├── c=KR                                       │
│      │   ├── ou=CSCA                                │
│      │   │   └── cn=CSCA-KR-001, ...               │
│      │   ├── ou=DS                                  │
│      │   │   └── cn=DS-KR-001, ...                 │
│      │   └── ou=CRL                                 │
│      │       └── cn=CRL-KR-001, ...                │
│      ├── c=US                                       │
│      │   └── ...                                    │
│      └── ou=Deviation                               │
│          └── cn=DEV-001, ...                        │
│                                                     │
└─────────────────────────────────────────────────────┘
```

---

## 4. 데이터 업데이트 시나리오

### 4.1 초기 구축
```
1단계: 전체 Master List 다운로드
   - icaopkd-001-complete-000000.ml 다운로드
   - 서명 검증
   - 전체 CSCA 인증서 파싱
   - LDAP에 저장

2단계: 국가별 DSC 다운로드
   - CscaDS_*.ldif 다운로드 (모든 국가)
   - LDAP에 저장
   - CSCA로 검증

3단계: CRL 다운로드
   - CRL_*.ldif 다운로드
   - LDAP에 저장

4단계: Deviation List 다운로드
   - DeviationList.ldif 다운로드
   - 참조용 저장
```

### 4.2 일일 업데이트
```
1. 증분 Master List 확인
   - ML_*.ldif 신규 파일 확인
   - 변경된 국가만 다운로드
   - 기존 데이터와 병합

2. DSC 업데이트
   - CscaDS_*.ldif 신규/변경 확인
   - 다운로드 및 적용

3. CRL 업데이트 (중요!)
   - 매일 또는 실시간 업데이트
   - 폐기 인증서 즉시 반영

4. Deviation 확인
   - 신규 편차 정보 확인
   - 시스템 설정 조정
```

### 4.3 긴급 업데이트
```
보안 사고 발생 시:
1. 영향 받은 국가의 CRL 즉시 다운로드
2. 해당 인증서 즉시 폐기 처리
3. 관련 DSC 검증 중단
4. 신규 인증서 배포 대기
```

---

## 5. 파일별 처리 전략

### 5.1 Master List (.ml)

```java
처리 순서:
1. 파일 다운로드
2. CMS 서명 검증 (UN/ICAO Root)
3. ASN.1 파싱
4. 개별 CSCA 추출
5. 중복 확인
6. 유효성 검증
7. LDAP 저장
8. 통계 생성

주의사항:
- 서명 검증 실패 시 전체 거부
- 중복 인증서는 버전 관리
- 만료 인증서도 이력상 보관
```

### 5.2 Master List (.ldif)

```java
처리 순서:
1. 파일 다운로드
2. LDIF 파싱
3. 국가 코드 추출
4. 기존 데이터와 비교
5. 신규/변경/삭제 감지
6. 병합 전략 적용
7. LDAP 저장/업데이트
8. 변경 로그 기록

병합 전략:
- 신규: 추가
- 변경: 이전 버전 보관 후 업데이트
- 삭제: 논리적 삭제 (상태 변경)
```

### 5.3 DSC (.ldif)

```java
처리 순서:
1. 파일 다운로드
2. LDIF 파싱
3. 발급 CSCA 확인
4. CSCA로 서명 검증
5. 유효기간 확인
6. CRL 대조 (폐기 여부)
7. LDAP 저장
8. 통계 업데이트

검증 체인:
DSC → CSCA → UN/ICAO Root
```

### 5.4 CRL (.ldif)

```java
처리 순서:
1. 파일 다운로드
2. LDIF 또는 CRL 파싱
3. 폐기 인증서 목록 추출
4. 기존 CRL과 병합
5. LDAP 업데이트
6. 영향 받는 DSC 상태 변경
7. 알림 발송 (필요 시)

실시간 처리:
- CRL 업데이트 시 즉시 반영
- 캐시 무효화
- 검증 프로세스 재실행
```

### 5.5 Deviation List (.ldif)

```java
처리 순서:
1. 파일 다운로드
2. LDIF 파싱
3. 편차 유형 분류
4. 심각도 평가
5. 해결 방법 매핑
6. 설정 파일 생성
7. 참조 DB 저장

활용:
- 파싱 시 예외 처리
- 검증 규칙 조정
- 호환성 모드 활성화
```

---

## 6. 데이터베이스 스키마 제안

### 6.1 통합 파일 테이블

```sql
CREATE TABLE pkd_files (
    id BIGSERIAL PRIMARY KEY,
    file_id VARCHAR(36) UNIQUE NOT NULL,
    
    -- 파일 정보
    file_type VARCHAR(20) NOT NULL,  -- ML, DSC, CRL, DEVIATION
    file_format VARCHAR(10) NOT NULL, -- ml, ldif, crl
    original_filename VARCHAR(255) NOT NULL,
    stored_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    file_hash VARCHAR(64),  -- SHA-256
    
    -- 메타데이터
    country_code VARCHAR(2),  -- NULL이면 전체
    issue_date TIMESTAMP,
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    
    -- 처리 상태
    upload_status VARCHAR(20) NOT NULL,
    parse_status VARCHAR(20),
    verify_status VARCHAR(20),
    ldap_status VARCHAR(20),
    
    -- 타임스탬프
    downloaded_at TIMESTAMP,
    parsed_at TIMESTAMP,
    applied_at TIMESTAMP,
    
    -- 통계
    total_entries INTEGER,
    valid_entries INTEGER,
    invalid_entries INTEGER,
    
    -- 감사
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_file_type ON pkd_files(file_type);
CREATE INDEX idx_country ON pkd_files(country_code);
CREATE INDEX idx_status ON pkd_files(upload_status, parse_status);
```

### 6.2 인증서 테이블

```sql
CREATE TABLE certificates (
    id BIGSERIAL PRIMARY KEY,
    cert_id VARCHAR(36) UNIQUE NOT NULL,
    
    -- 인증서 유형
    cert_type VARCHAR(10) NOT NULL,  -- CSCA, DSC
    country_code VARCHAR(2) NOT NULL,
    
    -- 인증서 정보
    subject_dn TEXT NOT NULL,
    issuer_dn TEXT NOT NULL,
    serial_number VARCHAR(100) NOT NULL,
    fingerprint_sha1 VARCHAR(40) NOT NULL,
    fingerprint_sha256 VARCHAR(64) NOT NULL,
    
    -- 유효기간
    not_before TIMESTAMP NOT NULL,
    not_after TIMESTAMP NOT NULL,
    
    -- 상태
    status VARCHAR(20) NOT NULL,  -- VALID, EXPIRED, REVOKED
    revocation_date TIMESTAMP,
    revocation_reason VARCHAR(100),
    
    -- 원본 데이터
    certificate_der BYTEA NOT NULL,
    
    -- 출처
    source_file_id VARCHAR(36),
    
    -- 감사
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_fingerprint UNIQUE(fingerprint_sha256),
    CONSTRAINT fk_source_file FOREIGN KEY(source_file_id) 
        REFERENCES pkd_files(file_id)
);

CREATE INDEX idx_cert_type ON certificates(cert_type);
CREATE INDEX idx_cert_country ON certificates(country_code);
CREATE INDEX idx_cert_status ON certificates(status);
CREATE INDEX idx_cert_serial ON certificates(serial_number);
```

### 6.3 CRL 항목 테이블

```sql
CREATE TABLE crl_entries (
    id BIGSERIAL PRIMARY KEY,
    
    country_code VARCHAR(2) NOT NULL,
    serial_number VARCHAR(100) NOT NULL,
    revocation_date TIMESTAMP NOT NULL,
    revocation_reason VARCHAR(100),
    
    -- CRL 정보
    crl_file_id VARCHAR(36),
    crl_issue_date TIMESTAMP,
    crl_next_update TIMESTAMP,
    
    -- 감사
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_crl_entry UNIQUE(country_code, serial_number),
    CONSTRAINT fk_crl_file FOREIGN KEY(crl_file_id) 
        REFERENCES pkd_files(file_id)
);

CREATE INDEX idx_crl_country ON crl_entries(country_code);
CREATE INDEX idx_crl_serial ON crl_entries(serial_number);
```

### 6.4 편차 테이블

```sql
CREATE TABLE deviations (
    id BIGSERIAL PRIMARY KEY,
    deviation_id VARCHAR(36) UNIQUE NOT NULL,
    
    country_code VARCHAR(2) NOT NULL,
    deviation_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,  -- LOW, MEDIUM, HIGH, CRITICAL
    
    description TEXT NOT NULL,
    workaround TEXT,
    
    status VARCHAR(20) NOT NULL,  -- ACTIVE, RESOLVED, ACKNOWLEDGED
    reported_date TIMESTAMP,
    resolved_date TIMESTAMP,
    
    -- 출처
    source_file_id VARCHAR(36),
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_deviation_file FOREIGN KEY(source_file_id) 
        REFERENCES pkd_files(file_id)
);

CREATE INDEX idx_deviation_country ON deviations(country_code);
CREATE INDEX idx_deviation_status ON deviations(status);
```

---

## 7. 처리 우선순위

### Phase 1: Master List (.ml) - 현재 구현됨 ✅
```
- CMS 파싱
- 서명 검증
- CSCA 추출
- LDAP 저장
```

### Phase 2: Master List (.ldif) - 증분 업데이트
```
- LDIF 파싱
- 병합 로직
- 변경 감지
- 증분 적용
```

### Phase 3: DSC (.ldif)
```
- LDIF 파싱
- CSCA 검증
- 대량 처리
- 성능 최적화
```

### Phase 4: CRL (.ldif)
```
- CRL 파싱
- 실시간 업데이트
- 자동 적용
- 알림 시스템
```

### Phase 5: Deviation List
```
- 편차 파싱
- 규칙 적용
- 예외 처리
- 문서화
```

---

## 8. 추가 질문사항

더 정확한 설계를 위해 확인이 필요한 사항들:

1. **.ml 파일과 .ldif ML 파일의 정확한 관계**
   - .ml 파일 적용 후 .ldif를 증분 업데이트로 사용하는가?
   - 아니면 .ldif만으로 전체 업데이트 가능한가?

2. **DSC 파일 구조**
   - CscaDS_XX.ldif 파일 하나에 여러 DSC가 포함되는가?
   - 국가별로 파일이 분리되는가?

3. **CRL 업데이트 빈도**
   - 실시간 업데이트가 필요한가?
   - 배치 처리로 충분한가?

4. **Deviation 적용 방식**
   - 자동으로 규칙을 적용하는가?
   - 수동으로 설정 조정하는가?

5. **우선 구현 범위**
   - 4가지 모두 필요한가?
   - 우선순위가 있는가?

이 정보를 바탕으로 더 정확한 아키텍처를 설계하겠습니다!