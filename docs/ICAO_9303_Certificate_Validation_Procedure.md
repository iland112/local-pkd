# ICAO Doc 9303 전자여권 인증서 검증 절차

**문서 버전**: 1.0
**작성일**: 2025-11-20
**기준**: ICAO Doc 9303 Part 12 (eMRTD PKI Technical Specifications)

---

## 1. 개요

ICAO Doc 9303은 전자여권(eMRTD - electronic Machine Readable Travel Documents)의 국제 표준을 정의합니다. 본 문서는 전자여권 PKI(Public Key Infrastructure) 구조와 인증서 검증 절차를 설명합니다.

### 1.1 목적

- 전자여권의 진위성 검증
- 여권 데이터 위변조 방지
- 국가 간 신뢰 체인 구축
- 국경 통제 시스템의 보안 강화

### 1.2 관련 표준

- **ICAO Doc 9303**: eMRTD 전체 표준
- **Part 12**: eMRTD PKI 기술 사양
- **RFC 5280**: X.509 인증서 및 CRL 표준
- **RFC 5652**: CMS (Cryptographic Message Syntax) 표준
- **RFC 4055**: RSA 알고리즘
- **ISO/IEC 15946**: ECDSA 알고리즘

---

## 2. PKI 구조

### 2.1 인증서 계층 구조

```
┌─────────────────────────────────────────────────────────────┐
│                   UN CSCA (United Nations)                   │
│               (ICAO Master List 서명용 최상위 CA)              │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ├─────────────────────────────────────┐
                         │                                     │
┌────────────────────────▼────────┐    ┌────────────────────────▼────────┐
│  Country CSCA (한국)             │    │  Country CSCA (미국)             │
│  (자체 서명된 Root Certificate)   │    │  (자체 서명된 Root Certificate)   │
└────────────────────────┬────────┘    └────────────────────────┬────────┘
                         │                                      │
         ┌───────────────┼───────────────┐      ┌──────────────┼──────────┐
         │               │               │      │              │          │
┌────────▼────┐  ┌──────▼──────┐  ┌────▼─────┐ ┌──────▼──────┐ ┌────▼────┐
│   DSC #1    │  │   DSC #2    │  │  DSC #3  │ │   DSC #1    │ │ DSC #2  │
│ (여권 서명)  │  │ (여권 서명)  │  │(여권서명)│ │ (여권 서명)  │ │(여권서명)│
└─────────────┘  └─────────────┘  └──────────┘ └─────────────┘ └─────────┘
       │                │                │            │              │
       ▼                ▼                ▼            ▼              ▼
  [여권 SOD]       [여권 SOD]       [여권 SOD]   [여권 SOD]    [여권 SOD]
  (Security        (Security        (Security    (Security     (Security
   Object)          Object)          Object)      Object)       Object)
```

### 2.2 인증서 유형

#### 2.2.1 CSCA (Country Signing Certification Authority)

**역할**:
- 각 국가의 전자여권 PKI의 Root Certificate
- DSC 및 기타 하위 인증서 발급
- Master List 및 CRL 서명

**특징**:
- 자체 서명 (Self-Signed)
- 장기간 유효 (일반적으로 10년 이상)
- 국가별로 1개 이상 보유 가능
- Out-of-band 방식으로 신뢰 설정 필요

**배포 방법**:
1. **양자 외교적 교환** (Bilateral Diplomatic Exchange)
2. **CSCA Master List** (3개월마다 업데이트)
3. **ICAO PKD** (Public Key Directory)

#### 2.2.2 DSC (Document Signer Certificate)

**역할**:
- 개별 전자여권의 SOD(Security Object Document) 서명
- CSCA에 의해 발급됨

**특징**:
- 중간 인증서 (Intermediate Certificate)
- CSCA가 서명
- 단기간 유효 (권장: 3개월 또는 10만 건 서명 중 먼저 도달하는 시점)
- 여권 칩에 포함됨

**Best Practice**:
- 사용 기간: 최대 3개월
- 서명 건수: 최대 100,000건
- 둘 중 먼저 도달하는 시점에 교체

#### 2.2.3 CRL (Certificate Revocation List)

**역할**:
- 폐기된 인증서 목록
- 손상되거나 유효하지 않은 인증서 식별

**특징**:
- CSCA가 서명
- RFC 5280 표준 준수
- 정기적으로 업데이트 (국가별 정책에 따름)

**폐기 대상**:
- 손상된 CSCA
- 만료된 DSC
- 보안 사고로 인해 폐기된 인증서

#### 2.2.4 Master List

**역할**:
- 여러 국가의 CSCA 인증서를 포함하는 컬렉션
- ICAO 또는 국가별로 발행

**특징**:
- CMS (Cryptographic Message Syntax) 형식
- PKCS#7 유사 구조
- 디지털 서명됨 (UN CSCA 또는 발행국 CSCA)
- 3개월마다 업데이트

**구조**:
```
Master List (CMS SignedData)
├─ SignerInfo (UN CSCA 또는 발행국 CSCA)
├─ Certificates
│  ├─ CSCA Certificate #1 (국가 A)
│  ├─ CSCA Certificate #2 (국가 B)
│  ├─ CSCA Certificate #3 (국가 C)
│  └─ ... (수백 개의 CSCA 인증서)
└─ CRLs (선택사항)
```

---

## 3. 인증서 검증 절차

### 3.1 Trust Chain 검증 프로세스

전자여권 검증은 다음 3단계 Trust Chain을 확인합니다:

```
┌─────────────────────────────────────────────────────────────────┐
│ Step 1: CSCA 인증서 검증 (Trust Anchor 설정)                     │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ▼
        ┌───────────────────────────────────┐
        │ CSCA Certificate                  │
        │ - 자체 서명 검증                   │
        │ - Master List에 포함 여부 확인     │
        │ - CRL 확인 (폐기 여부)             │
        │ - 유효기간 확인                    │
        └───────────────┬───────────────────┘
                        │ ✓ 신뢰됨
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 2: DSC 인증서 검증 (Intermediate Certificate)               │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ▼
        ┌───────────────────────────────────┐
        │ DSC Certificate                   │
        │ - CSCA 서명 검증                   │
        │ - Certificate Chain 검증           │
        │ - CRL 확인 (폐기 여부)             │
        │ - 유효기간 확인                    │
        │ - 용도 확인 (digitalSignature)     │
        └───────────────┬───────────────────┘
                        │ ✓ 유효함
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 3: SOD 서명 검증 (End-Entity Signature)                     │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ▼
        ┌───────────────────────────────────┐
        │ SOD (Security Object Document)    │
        │ - DSC로 서명 검증                  │
        │ - 데이터 그룹 해시 검증            │
        │ - 무결성 확인                      │
        └───────────────┬───────────────────┘
                        │ ✓ 검증 완료
                        ▼
        ┌───────────────────────────────────┐
        │ 여권 데이터 신뢰 확립               │
        │ - 진위성 확인 완료                 │
        │ - 위변조 없음                      │
        └───────────────────────────────────┘
```

### 3.2 상세 검증 단계

#### Step 1: CSCA 인증서 검증

**입력**:
- CSCA Certificate (Master List 또는 PKD에서 획득)
- Master List Signer Certificate (UN CSCA 또는 발행국 CSCA)

**검증 절차**:

1. **Self-Signed 서명 검증**
   ```java
   // CSCA는 자체 서명됨
   X509Certificate cscaCert = ...;
   cscaCert.verify(cscaCert.getPublicKey());
   ```

2. **Master List 포함 여부 확인**
   ```java
   // Master List CMS에서 CSCA 찾기
   CMSSignedData masterList = new CMSSignedData(masterListBytes);
   Store certStore = masterList.getCertificates();
   Collection<X509CertificateHolder> certs = certStore.getMatches(null);

   boolean found = certs.stream()
       .anyMatch(cert -> cert.equals(cscaCert));
   ```

3. **유효기간 검증**
   ```java
   cscaCert.checkValidity(); // throws CertificateExpiredException
   ```

4. **CRL 확인**
   ```java
   // CSCA가 폐기되지 않았는지 확인
   X509CRL crl = loadCRLFromPKD();
   if (crl.isRevoked(cscaCert)) {
       throw new CertificateRevokedException("CSCA revoked");
   }
   ```

5. **기본 제약 조건 검증**
   ```java
   // CSCA는 CA 인증서여야 함
   int basicConstraints = cscaCert.getBasicConstraints();
   if (basicConstraints < 0) {
       throw new CertPathValidatorException("Not a CA certificate");
   }
   ```

**출력**:
- ✓ 신뢰된 CSCA Certificate (Trust Anchor 설정 완료)

---

#### Step 2: DSC 인증서 검증

**입력**:
- DSC Certificate (여권 칩에서 추출)
- CSCA Certificate (Step 1에서 검증됨)

**검증 절차**:

1. **CSCA 서명 검증**
   ```java
   X509Certificate dscCert = ...;
   X509Certificate cscaCert = ...;

   dscCert.verify(cscaCert.getPublicKey());
   ```

2. **Certificate Path 검증**
   ```java
   CertPathValidator validator = CertPathValidator.getInstance("PKIX");
   PKIXParameters params = new PKIXParameters(trustAnchors);
   params.setRevocationEnabled(true);

   CertPath certPath = CertificateFactory.getInstance("X.509")
       .generateCertPath(Arrays.asList(dscCert, cscaCert));

   validator.validate(certPath, params);
   ```

3. **유효기간 검증**
   ```java
   dscCert.checkValidity();
   ```

4. **CRL 확인**
   ```java
   // DSC가 폐기되지 않았는지 확인
   X509CRL crl = loadCRLFromPKD();
   if (crl.isRevoked(dscCert)) {
       throw new CertificateRevokedException("DSC revoked");
   }
   ```

5. **Key Usage 검증**
   ```java
   // DSC는 digitalSignature 용도여야 함
   boolean[] keyUsage = dscCert.getKeyUsage();
   if (keyUsage == null || !keyUsage[0]) { // index 0 = digitalSignature
       throw new CertPathValidatorException("Invalid key usage");
   }
   ```

6. **Issuer 일치 확인**
   ```java
   if (!dscCert.getIssuerDN().equals(cscaCert.getSubjectDN())) {
       throw new CertPathValidatorException("Issuer mismatch");
   }
   ```

**출력**:
- ✓ 유효한 DSC Certificate

---

#### Step 3: SOD 서명 검증 (Passive Authentication)

**입력**:
- SOD (Security Object Document) - 여권 칩의 EF.SOD 파일
- DSC Certificate (Step 2에서 검증됨)

**검증 절차**:

1. **CMS SignedData 파싱**
   ```java
   byte[] sodBytes = readFromChip("EF.SOD");
   CMSSignedData sodCms = new CMSSignedData(sodBytes);
   ```

2. **DSC로 서명 검증**
   ```java
   SignerInformation signer = sodCms.getSignerInfos().getSigners().iterator().next();
   SignerInformationVerifier verifier = new JcaSimpleSignerInfoVerifierBuilder()
       .setProvider("BC")
       .build(dscCert);

   boolean valid = signer.verify(verifier);
   ```

3. **데이터 그룹 해시 검증**
   ```java
   // SOD에 포함된 DataGroup 해시값
   Map<Integer, byte[]> storedHashes = extractHashesFromSOD(sodCms);

   // 여권 칩에서 읽은 DataGroup
   Map<Integer, byte[]> chipDataGroups = readDataGroupsFromChip();

   // 각 DataGroup의 해시 재계산 및 비교
   for (Map.Entry<Integer, byte[]> entry : chipDataGroups.entrySet()) {
       int dgNumber = entry.getKey();
       byte[] dgData = entry.getValue();

       MessageDigest md = MessageDigest.getInstance("SHA-256");
       byte[] calculatedHash = md.digest(dgData);
       byte[] storedHash = storedHashes.get(dgNumber);

       if (!Arrays.equals(calculatedHash, storedHash)) {
           throw new SecurityException("DataGroup " + dgNumber + " hash mismatch");
       }
   }
   ```

4. **무결성 확인**
   ```java
   // LDS Security Object 구조 검증
   LDSSecurityObject ldsSecurityObject = parseLDSSecurityObject(sodCms);

   // 알고리즘 확인
   String hashAlgorithm = ldsSecurityObject.getDigestAlgorithm();
   if (!Arrays.asList("SHA-256", "SHA-384", "SHA-512").contains(hashAlgorithm)) {
       throw new SecurityException("Unsupported hash algorithm: " + hashAlgorithm);
   }
   ```

**출력**:
- ✓ 여권 데이터 진위성 확인 완료
- ✓ 위변조 없음

---

### 3.3 검증 실패 시나리오

| 단계 | 실패 원인 | 조치 |
|------|-----------|------|
| CSCA 검증 | Master List에 없음 | 신뢰할 수 없는 발행국 |
| CSCA 검증 | 자체 서명 실패 | 손상된 인증서 |
| CSCA 검증 | 유효기간 만료 | 업데이트된 CSCA 필요 |
| CSCA 검증 | CRL에 폐기됨 | 더 이상 사용 불가 |
| DSC 검증 | CSCA 서명 불일치 | 위조 가능성 |
| DSC 검증 | CRL에 폐기됨 | 사용 중지된 인증서 |
| DSC 검증 | Key Usage 위반 | 부적절한 용도 |
| SOD 검증 | 서명 불일치 | 여권 데이터 위변조 |
| SOD 검증 | 해시 불일치 | DataGroup 변조됨 |

---

## 4. ICAO PKD (Public Key Directory)

### 4.1 개요

ICAO PKD는 전자여권 인증서 및 CRL의 중앙 집중식 저장소입니다.

**목적**:
- CSCA, DSC, CRL의 안전한 배포
- 국가 간 인증서 교환 간소화
- 비용 효율적인 온라인 소스 제공

**접근 권한**:
- ICAO 회원국만 접근 가능 (Private Service)
- 공개 다운로드: https://pkddownloadsg.icao.int/download

### 4.2 PKD 콘텐츠

| 항목 | 설명 | 형식 |
|------|------|------|
| CSCA Certificates | 각국 Root 인증서 | X.509 |
| DSC Certificates | 문서 서명 인증서 | X.509 |
| CRLs | 폐기 인증서 목록 | RFC 5280 |
| Master Lists | CSCA 컬렉션 | CMS SignedData |

### 4.3 LDIF 파일 구조

ICAO PKD는 LDAP Data Interchange Format (LDIF)으로 데이터를 배포합니다.

**Collection 구분**:
- **Collection 001**: CSCA 인증서 및 CRL (Delta/Complete)
- **Collection 002**: Master List (Delta/Complete)

**LDIF Entry 예시**:

```ldif
# CSCA 인증서 엔트리
dn: c=KR,o=csca,dc=data,dc=download,dc=pkd,dc=icao,dc=int
objectClass: pkiUser
objectClass: pkiCA
c: KR
userCertificate;binary:: MIIFxzCCA6+gAwIBAgIBATA...
certificateRevocationList;binary:: MIIDXzCCAs...

# Master List 엔트리
dn: o=ml,c=FR,dc=data,dc=download,dc=pkd,dc=icao,dc=int
objectClass: pkdMasterList
pkdMasterListContent:: MIMB1mIGCSqGSIb3DQEHAqCD...
```

### 4.4 업데이트 주기

- **Master List**: 3개월마다
- **CSCA/DSC**: 실시간 (발행국이 업로드)
- **CRL**: 발행국 정책에 따름 (일반적으로 매일 또는 매주)

---

## 5. 암호화 알고리즘

ICAO Doc 9303 Part 12에서 허용하는 알고리즘:

### 5.1 디지털 서명 알고리즘

| 알고리즘 | 표준 | 키 크기 | 비고 |
|----------|------|---------|------|
| RSA | RFC 4055 | 2048-4096 bits | 가장 널리 사용됨 |
| ECDSA | ISO/IEC 15946, TR-03111 | 256-521 bits | 효율적, 작은 키 크기 |
| DSA | FIPS 186-4 | 2048-3072 bits | 덜 사용됨 |

### 5.2 해시 알고리즘

| 알고리즘 | 출력 크기 | 권장 사용 |
|----------|-----------|----------|
| SHA-224 | 224 bits | 최소 요구사항 |
| SHA-256 | 256 bits | 권장 |
| SHA-384 | 384 bits | 고보안 |
| SHA-512 | 512 bits | 최고 보안 |

**권장사항**:
- **RSA-2048 + SHA-256**: 표준 보안 수준
- **RSA-4096 + SHA-384**: 고보안 요구사항
- **ECDSA-256 + SHA-256**: 효율성 중시

---

## 6. Local PKD 구현에서의 적용

### 6.1 현재 구현 상태

#### ✅ 완료된 기능

1. **Master List 파싱**
   - CMS SignedData 구조 파싱
   - CSCA 인증서 추출 (28개 인증서 성공)
   - 국가별 분류 (17개국)

2. **데이터베이스 저장**
   - `parsed_certificate` 테이블에 저장
   - 인증서 메타데이터 추출 (Subject, Issuer, Serial Number)

#### ⚠️ 구현 필요 사항

1. **CSCA 인증서 검증**
   - Self-Signed 서명 검증
   - 유효기간 확인
   - Basic Constraints 확인

2. **DSC 인증서 검증**
   - CSCA로 서명 검증
   - Certificate Path 검증
   - Key Usage 검증

3. **CRL 검증**
   - CRL 파싱 및 저장
   - 인증서 폐기 상태 확인

4. **Trust Chain 구축**
   - CSCA → DSC 체인 검증
   - Trust Anchor 관리

5. **OpenLDAP 업로드**
   - 검증된 인증서만 업로드
   - LDAP 스키마 준수

### 6.2 다음 단계 구현 계획

#### Phase 1: CSCA 인증서 검증 (우선순위: 높음)

**목표**: `parsed_certificate` → `certificate` 테이블로 검증 후 이동

**구현 내용**:
1. ValidateCertificatesUseCase 개선
   - `parsed_certificate` 테이블에서 인증서 로드
   - BouncyCastle을 사용한 자체 서명 검증
   - 유효기간 검증
   - Basic Constraints 검증 (CA=true)

2. Certificate 엔티티 생성
   - 검증 성공 시 `certificate` 테이블에 저장
   - `validation_overall_status` = 'VALID'
   - `certificate_type` = 'CSCA'

**예상 결과**:
- 28개 CSCA 인증서 검증 완료
- `certificate` 테이블에 저장

#### Phase 2: DSC 인증서 검증 (우선순위: 중간)

**목표**: DSC-CSCA Trust Chain 검증

**구현 내용**:
1. Certificate Path Validator
   - CSCA를 Trust Anchor로 설정
   - DSC 서명 검증
   - Certificate Chain 구축

2. CertPathValidator 사용
   - Java PKIXParameters 설정
   - Trust Anchor Store 구축

#### Phase 3: CRL 검증 (우선순위: 중간)

**목표**: 폐기된 인증서 식별

**구현 내용**:
1. CRL 파싱
   - LDIF에서 CRL 추출
   - RFC 5280 형식 파싱

2. CRL 검증
   - CSCA 서명 확인
   - 유효기간 확인

3. 폐기 상태 확인
   - 인증서 Serial Number 매칭
   - `validation_not_revoked` 플래그 업데이트

#### Phase 4: OpenLDAP 업로드 (우선순위: 높음)

**목표**: 검증된 인증서를 OpenLDAP에 등록

**구현 내용**:
1. LDAP Entry 생성
   - `objectClass: pkiCA` (CSCA)
   - `objectClass: pkiUser` (DSC)
   - `userCertificate;binary` 속성

2. 배치 업로드
   - 28개 CSCA 인증서 업로드
   - 국가별 DN 구조: `c=KR,o=csca,dc=...`

---

## 7. 참고 자료

### 7.1 공식 문서

- [ICAO Doc 9303](https://www.icao.int/publications/pages/publication.aspx?docnum=9303)
- [ICAO PKD Regulations](https://www.icao.int/sites/default/files/2025-06/ICAO-PKD-Regulations_Version_July2020.pdf)
- [ICAO PKD Official Site](https://www.icao.int/icao-pkd/)

### 7.2 표준 문서

- [RFC 5280 - X.509 Certificate and CRL](https://tools.ietf.org/html/rfc5280)
- [RFC 5652 - Cryptographic Message Syntax (CMS)](https://tools.ietf.org/html/rfc5652)
- [RFC 4055 - RSA Algorithms](https://tools.ietf.org/html/rfc4055)

### 7.3 구현 참고

- [JMRTD Project](https://jmrtd.org/)
- [ZeroPass Documentation](https://github.com/ZeroPass/Port-documentation-and-tools)
- [Bouncy Castle Crypto Library](https://www.bouncycastle.org/)

---

## 8. 용어 정리

| 용어 | 전체 명칭 | 설명 |
|------|-----------|------|
| eMRTD | electronic Machine Readable Travel Document | 전자여권 |
| CSCA | Country Signing Certification Authority | 국가 서명 인증 기관 |
| DSC | Document Signer Certificate | 문서 서명 인증서 |
| SOD | Security Object Document | 보안 객체 문서 |
| CRL | Certificate Revocation List | 인증서 폐기 목록 |
| PKD | Public Key Directory | 공개 키 디렉토리 |
| CMS | Cryptographic Message Syntax | 암호 메시지 구문 |
| PA | Passive Authentication | 수동 인증 |
| AA | Active Authentication | 능동 인증 |
| EAC | Extended Access Control | 확장 접근 제어 |

---

**문서 작성자**: SmartCore Inc.
**문서 관리**: kbjung
**최종 업데이트**: 2025-11-20
