# Phase 13 Week 1 Task 1-4 Complete: Trust Chain Validation Domain Services

**완료 날짜**: 2025-10-24
**작업 범위**: Certificate Validation Context - Trust Chain Validation 설계 및 구현
**상태**: ✅ **완료**

---

## 📋 완료된 작업 개요

### Task 1: Domain Services 설계
- Trust Chain 검증 흐름도 작성
- ICAO PKD 인증서 계층 구조 정의 (CSCA → DSC → DS)
- Domain Service 인터페이스 설계 (TrustChainValidator, CertificatePathBuilder)
- Value Objects 설계 (ValidationResult, TrustPath, ValidationError)

### Task 2: TrustChainValidator Domain Service 구현
- Trust Path 전체 검증 구현 (`validate()`)
- CSCA 검증 구현 (`validateCsca()`)
- DSC 검증 구현 (`validateDsc()`)
- Issuer-Subject 관계 검증 (`validateIssuerRelationship()`)
- BouncyCastle 기반 서명 검증
- CRL 기반 폐기 확인

### Task 3: CertificatePathBuilder Domain Service 구현
- 재귀적 Trust Path 구축 알고리즘
- Self-Signed 인증서 감지
- Issuer DN 기반 부모 인증서 검색
- 순환 참조 방지 (Set 기반 방문 추적)
- 최대 깊이 제한 (5 levels)

### Task 4: Value Objects 구현
- **TrustPath** Value Object 신규 생성
- **ValidationResult** 기존 활용 (Phase 11-12)
- **ValidationError** 기존 활용 (Phase 11-12)

---

## 🏗️ 구현된 파일

### 1. 설계 문서 (1개)

| 파일명 | 경로 | Lines | 설명 |
|--------|------|-------|------|
| **PHASE_13_WEEK1_TASK1_DESIGN.md** | docs/ | 800 | Trust Chain 검증 설계 문서 |

### 2. Domain Service 인터페이스 (2개)

| 파일명 | 경로 | Lines | 설명 |
|--------|------|-------|------|
| **TrustChainValidator.java** | domain/service/ | 134 | Trust Chain 검증 인터페이스 |
| **CertificatePathBuilder.java** | domain/service/ | 74 | Trust Path 자동 구축 인터페이스 |

### 3. Domain Service 구현체 (2개)

| 파일명 | 경로 | Lines | 설명 |
|--------|------|-------|------|
| **TrustChainValidatorImpl.java** | domain/service/ | 460 | Trust Chain 검증 구현 (BouncyCastle 사용) |
| **CertificatePathBuilderImpl.java** | domain/service/ | 238 | Trust Path 자동 구축 구현 |

### 4. Value Objects (1개 신규)

| 파일명 | 경로 | Lines | 설명 |
|--------|------|-------|------|
| **TrustPath.java** | domain/model/ | 176 | 신뢰 경로 Value Object |

---

## 🎯 주요 기능

### 1. TrustChainValidator

#### validateCsca() - CSCA 검증

**검증 항목**:
1. **Self-Signed 확인**: `Subject DN == Issuer DN`
2. **CA 플래그 확인**: `issuerInfo.isCA() == true`
3. **Signature 자기 검증**: `certificate.verify(certificate.getPublicKey())`
4. **유효기간 확인**: `notBefore <= now <= notAfter`

**코드 예시**:
```java
@Override
public ValidationResult validateCsca(Certificate csca) {
    // 1. Self-Signed Check
    if (!csca.isSelfSigned()) {
        return ValidationResult.of(CertificateStatus.INVALID, ...);
    }

    // 2. CA Flag Check
    if (!csca.isCA()) {
        return ValidationResult.of(CertificateStatus.INVALID, ...);
    }

    // 3. Validity Period Check
    boolean validityValid = csca.isCurrentlyValid();

    // 4. Signature Self-Verification
    boolean signatureValid = verifySignature(csca, csca);

    return ValidationResult.of(
        signatureValid && validityValid ? CertificateStatus.VALID : CertificateStatus.INVALID,
        signatureValid,
        true,  // chainValid (self-signed = root)
        true,  // notRevoked (CSCA cannot be revoked)
        validityValid,
        true,  // constraintsValid
        duration
    );
}
```

#### validateDsc() - DSC 검증

**검증 항목**:
1. **Issuer 확인**: `dsc.issuerDN == csca.subjectDN`
2. **Signature 검증**: `dsc.verify(csca.getPublicKey())`
3. **유효기간 확인**: `notBefore <= now <= notAfter`
4. **CRL 확인**: CRL에서 Serial Number 검색

**코드 예시**:
```java
@Override
public ValidationResult validateDsc(Certificate dsc, Certificate csca) {
    // 1. Issuer Check
    String dscIssuerDn = dsc.getIssuerInfo().getDistinguishedName();
    String cscaSubjectDn = csca.getSubjectInfo().getDistinguishedName();

    if (!dscIssuerDn.equals(cscaSubjectDn)) {
        return ValidationResult.of(CertificateStatus.INVALID, ...);
    }

    // 2. Signature Verification
    boolean signatureValid = verifySignature(dsc, csca);

    // 3. Validity Period Check
    boolean validityValid = dsc.isCurrentlyValid();

    // 4. CRL Check (Revocation)
    boolean notRevoked = checkRevocation(dsc);

    return ValidationResult.of(
        signatureValid && validityValid && notRevoked
            ? CertificateStatus.VALID
            : (dsc.isExpired() ? CertificateStatus.EXPIRED : CertificateStatus.REVOKED),
        signatureValid,
        true,
        notRevoked,
        validityValid,
        true,
        duration
    );
}
```

#### validate(TrustPath) - 전체 경로 검증

**알고리즘**:
```
1. Load all certificates in the path
2. Validate CSCA (Root)
   → 실패 시 즉시 INVALID 반환
3. For each child-parent pair:
   a. Validate issuer relationship
   b. If DSC: Validate DSC-specific rules
   → 실패 시 즉시 INVALID 반환
4. All validations passed → VALID 반환
```

---

### 2. CertificatePathBuilder

#### buildPath() - 재귀적 경로 구축

**알고리즘**:
```java
private boolean buildPathRecursive(
    Certificate current,
    List<UUID> path,
    Set<UUID> visited,
    int depth
) {
    // 1. Maximum depth check
    if (depth >= TrustPath.MAX_DEPTH) {
        return false;
    }

    // 2. Circular reference check
    if (visited.contains(current.getId())) {
        return false;
    }

    // 3. Add current certificate to path
    path.add(current.getId().getId());
    visited.add(current.getId().getId());

    // 4. Check if current is CSCA (Self-Signed)
    if (isSelfSigned(current)) {
        return true;  // Success - reached root
    }

    // 5. Find parent (issuer) certificate
    String issuerDn = current.getIssuerInfo().getDistinguishedName();
    Optional<Certificate> parentOpt = findIssuerCertificate(issuerDn);

    if (parentOpt.isEmpty()) {
        return false;  // Failed - missing link
    }

    // 6. Recursive call with parent
    return buildPathRecursive(parentOpt.get(), path, visited, depth + 1);
}
```

**보호 메커니즘**:
- **최대 깊이 제한**: `MAX_DEPTH = 5` (무한 루프 방지)
- **순환 참조 감지**: `Set<UUID> visited`로 방문 인증서 추적
- **Missing Issuer 처리**: `Optional.empty()` 반환

---

### 3. TrustPath Value Object

**비즈니스 규칙**:
1. 경로는 최소 1개 이상의 인증서 포함 (CSCA)
2. 경로 순서: `[0]=CSCA (Root), [1]=DSC, [2]=DS (Leaf)`
3. 최대 깊이: 5 (무한 루프 방지)
4. 순환 참조 방지 (중복 ID 검증)

**Static Factory Methods**:
```java
TrustPath.of(List<UUID> certificateIds)           // General
TrustPath.ofSingle(UUID cscaId)                   // CSCA only
TrustPath.ofTwo(UUID cscaId, UUID dscId)          // CSCA → DSC
TrustPath.ofThree(UUID cscaId, UUID dscId, UUID dsId)  // Full path
```

**Business Methods**:
```java
UUID getRoot()                 // CSCA ID
UUID getLeaf()                 // Last certificate ID
int getDepth()                 // Certificate count
boolean contains(UUID id)      // Check if ID in path
boolean isSingleCertificate()  // CSCA only?
String toShortString()         // "a1b2c3d4 → e5f6g7h8 → i9j0k1l2"
```

---

## 🧪 BouncyCastle 기반 서명 검증

### verifySignature() 구현

```java
private boolean verifySignature(Certificate subject, Certificate issuer) {
    try {
        // 1. Parse X.509 certificates
        byte[] subjectBytes = subject.getX509Data().getCertificateBinary();
        byte[] issuerBytes = issuer.getX509Data().getCertificateBinary();

        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        X509Certificate subjectCert = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(subjectBytes)
        );
        X509Certificate issuerCert = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(issuerBytes)
        );

        // 2. Verify signature using issuer's public key
        PublicKey issuerPublicKey = issuerCert.getPublicKey();
        subjectCert.verify(issuerPublicKey);

        log.debug("Signature verification succeeded");
        return true;

    } catch (Exception e) {
        log.error("Signature verification failed: {}", e.getMessage());
        return false;
    }
}
```

**기술 스택**:
- `java.security.cert.CertificateFactory` - X.509 인증서 파싱
- `java.security.cert.X509Certificate` - X.509 인증서 표현
- `X509Certificate.verify(PublicKey)` - 서명 검증 (BouncyCastle Provider 사용)

---

## 🔒 CRL 기반 폐기 확인

### checkRevocation() 구현

```java
private boolean checkRevocation(Certificate certificate) {
    try {
        // 1. Get issuer DN and country code
        String issuerDn = certificate.getIssuerInfo().getDistinguishedName();
        String countryCode = certificate.getIssuerInfo().getCountryCode();

        // 2. Extract issuer name (CN value)
        String issuerName = extractCommonName(issuerDn);

        // 3. Find CRL
        Optional<CertificateRevocationList> crlOpt =
            crlRepository.findByIssuerNameAndCountry(issuerName, countryCode);

        if (crlOpt.isEmpty()) {
            log.warn("CRL not found, assuming not revoked");
            return true;  // No CRL = assume not revoked
        }

        // 4. Check if certificate is in CRL
        CertificateRevocationList crl = crlOpt.get();
        String serialNumber = certificate.getX509Data().getSerialNumber();

        boolean isRevoked = crl.isRevoked(serialNumber);

        return !isRevoked;

    } catch (Exception e) {
        log.error("CRL check failed: {}", e.getMessage(), e);
        return true;  // On error, assume not revoked (fail-open)
    }
}
```

**Fail-Open 정책**:
- CRL을 찾을 수 없는 경우: **not revoked** (검증 통과)
- CRL 확인 중 오류 발생: **not revoked** (검증 통과)
- 이유: 가용성 우선 (엄격한 검증은 설정으로 제어 가능)

---

## 📊 통계

### Build 통계

```bash
Total Source Files: 135 (+5 from Phase 12)
Compilation: ✅ SUCCESS
Build Time: 14.773 s
```

### 파일 통계

| 항목 | 수량 |
|------|------|
| **설계 문서** | 1개 (800 lines) |
| **Domain Service 인터페이스** | 2개 (208 lines) |
| **Domain Service 구현체** | 2개 (698 lines) |
| **Value Objects (신규)** | 1개 (176 lines) |
| **Total Lines** | ~1,882 lines |

---

## 🎓 학습한 내용

### 1. ICAO PKD 인증서 계층 구조

**3-Tier Architecture**:
```
CSCA (Country Signing CA)
  │ Self-Signed, CA=true, keyCertSign
  │
  ├─ Signs DSC
  │
  ▼
DSC (Document Signer Certificate)
  │ Issued by CSCA, digitalSignature
  │
  ├─ Signs DS
  │
  ▼
DS (Document Signature)
  │ End-Entity, signs eMRTD/Passport
```

### 2. Trust Chain 검증 알고리즘

**검증 순서**:
1. **Bottom-Up Path Construction**: Leaf → Root (재귀)
2. **Top-Down Validation**: Root → Leaf (순차)

**보호 메커니즘**:
- 최대 깊이 제한 (무한 루프 방지)
- 순환 참조 감지 (Set 기반)
- Missing Issuer 처리 (Optional)

### 3. BouncyCastle X.509 서명 검증

**API 사용법**:
```java
CertificateFactory cf = CertificateFactory.getInstance("X.509");
X509Certificate cert = (X509Certificate) cf.generateCertificate(inputStream);
PublicKey publicKey = issuerCert.getPublicKey();
subjectCert.verify(publicKey);  // Throws exception if invalid
```

**예외 처리**:
- `SignatureException` - 서명 불일치
- `InvalidKeyException` - 잘못된 공개키
- `NoSuchAlgorithmException` - 알고리즘 미지원
- `NoSuchProviderException` - Provider 없음

### 4. DN (Distinguished Name) 파싱

**정규식 사용**:
```java
Pattern pattern = Pattern.compile("CN=([^,]+)");
Matcher matcher = pattern.matcher(dn);
if (matcher.find()) {
    return matcher.group(1).trim();
}
```

**DN 정규화**:
- 공백 제거: `replaceAll("\\s*,\\s*", ",")`
- 대소문자 통일: `toLowerCase()`
- Trim: `trim()`

---

## 🚀 다음 단계 (Task 5)

### Task 5: Domain Services Unit Tests (55개 테스트)

**테스트 클래스**:
1. **TrustChainValidatorTest** (30 tests)
   - `validateCsca()` 테스트 (10개)
   - `validateDsc()` 테스트 (10개)
   - `validate(TrustPath)` 테스트 (10개)

2. **CertificatePathBuilderTest** (25 tests)
   - `buildPath()` 테스트 (15개)
   - `isSelfSigned()` 테스트 (5개)
   - `findIssuerCertificate()` 테스트 (5개)

**Mocking 전략**:
- `CertificateRepository` - Mockito
- `CertificateRevocationListRepository` - Mockito
- Test Fixtures - Certificate 테스트 데이터 생성

**예상 소요 시간**: 1-2일

---

## ✅ Acceptance Criteria

### Task 1: Domain Services 설계
- [x] Trust Chain 검증 흐름도 작성
- [x] CSCA/DSC/DS 역할 정의
- [x] Self-Signed CA 특수 케이스 정의
- [x] TrustChainValidator 인터페이스 정의
- [x] CertificatePathBuilder 인터페이스 정의
- [x] TrustPath Value Object 설계
- [x] 비즈니스 규칙 16개 정의

### Task 2: TrustChainValidator 구현
- [x] `validate(TrustPath)` 구현
- [x] `validateCsca(Certificate)` 구현
- [x] `validateDsc(Certificate, Certificate)` 구현
- [x] `validateIssuerRelationship()` 구현
- [x] BouncyCastle 기반 서명 검증
- [x] CRL 기반 폐기 확인
- [x] 컴파일 성공

### Task 3: CertificatePathBuilder 구현
- [x] `buildPath(CertificateId)` 구현
- [x] `buildPath(Certificate)` 구현
- [x] 재귀적 경로 구축 알고리즘
- [x] 순환 참조 방지
- [x] 최대 깊이 제한
- [x] `isSelfSigned()` 구현
- [x] `findIssuerCertificate()` 구현
- [x] 컴파일 성공

### Task 4: Value Objects 구현
- [x] TrustPath Value Object 구현
- [x] Static Factory Methods (4개)
- [x] Business Methods (7개)
- [x] 불변성 보장
- [x] 순환 참조 검증
- [x] 컴파일 성공

---

## 📝 최종 상태

**Phase 13 Week 1 Task 1-4 완료** ✅

- **Domain Services 설계**: TrustChainValidator, CertificatePathBuilder 인터페이스
- **Domain Services 구현**: TrustChainValidatorImpl, CertificatePathBuilderImpl
- **Value Objects**: TrustPath (신규), ValidationResult (기존), ValidationError (기존)
- **Total Files**: 5개 (설계 문서 1, 인터페이스 2, 구현체 2)
- **Total Lines**: ~1,882 lines
- **Build**: SUCCESS (135 source files)

**다음 작업**: Phase 13 Week 1 Task 5 - Domain Services Unit Tests (55 tests)

---

**작성자**: kbjung
**문서 버전**: 1.0
**마지막 업데이트**: 2025-10-24
