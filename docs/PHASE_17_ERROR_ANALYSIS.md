# Phase 17 Error Analysis Report

**Generated**: 2025-11-14
**Status**: Critical Issues Found
**Priority**: HIGH

---

## Executive Summary

LDAP 업로드 파이프라인에서 다음과 같은 4가지 **치명적인 문제**가 확인되었습니다:

1. **IssuerName 검증 로직이 너무 엄격** - 0% 성공률
2. **Certificate/CRL Validation 실패** - 모든 항목 invalid로 처리
3. **UploadToLdapUseCase 미구현** - LDAP 서버에 업로드 안됨
4. **0개 데이터로 완료 처리** - 잘못된 완료 이벤트 발행

---

## 문제 상세 분석

### Problem 1: IssuerName Validation - 0% Success Rate ❌

#### 증상
```
2025-11-14 17:10:47 [ERROR] - Failed to create CertificateRevocationList aggregate
DomainException: Issuer name must match format 'CSCA-XX' (e.g., CSCA-QA, CSCA-NZ)
Got: CN=csca-canada,OU=pptc,O=gc,C=CA
```

#### 근본 원인

**파일**: `IssuerName.java` (Line 61)
```java
private static final Pattern CSCA_PATTERN = Pattern.compile("^CSCA-[A-Z]{2}$");
```

**문제점**:
- 정규식이 `CSCA-XX` 형식만 허용
- **실제 LDIF 데이터는 전체 DN (Distinguished Name)**을 전달
- `parseIssuerName()`이 전체 DN을 그대로 IssuerName에 전달

**호출 스택**:
```
ValidateCertificatesUseCase.createCertificateRevocationListAggregate(Line 364)
  → parseIssuerName(crlData.getIssuerDN()) (Line 373)
    → IssuerName.of(dn) (Line 479)
      → CSCA_PATTERN.matcher(normalized).matches() (Line 87)
        → DomainException: "CSCA-XX" format required
```

#### 실제 데이터 샘플

```
✗ CN=csca-canada,OU=pptc,O=gc,C=CA
✗ CN=csca-germany,OU=bsi,O=bund,C=DE
✗ CN=ePassport CSCA 07,OU=MRTD Department,O=Public Service Agency,C=MD
✗ CN=Singapore Passport CA 6,OU=ICA,O=Ministry of Home Affairs,C=SG
✗ CN=OMAN CSCA,OU=Royal Oman Police,O=GOV,C=OM
✗ CN=Passport Country Signing Authority,OU=APO,OU=DFAT,O=GOV,C=AU
```

**모두 동일한 에러로 실패**.

#### 로그 통계

```
2025-11-14 17:10:47 [INFO] CertificateValidationEventHandler:
  - Valid Certificates: 0       ❌ (9829개 invalid)
  - Invalid Certificates: 9829  ❌
  - Valid CRLs: 0               ❌ (32개 invalid)
  - Invalid CRLs: 32            ❌
  - Total Validated: 9861
  - Success Rate: 0%             ❌❌❌
```

---

### Problem 2: IssuerName Extraction Logic Flawed 🔧

#### 근본 원인

**파일**: `ValidateCertificatesUseCase.java` (Line 478-480)

```java
private IssuerName parseIssuerName(String dn) {
    return IssuerName.of(dn);  // ❌ 전체 DN을 그대로 전달
}
```

**문제점**:
- DN 문자열에서 `CN` 컴포넌트만 추출해야 함
- 현재는 전체 DN을 IssuerName.of()에 전달

#### 올바른 처리

DN 문자열: `CN=csca-canada,OU=pptc,O=gc,C=CA`
- ✅ **추출 필요**: `C=CA` → CountryCode
- ✅ **추출 필요**: `CN=csca-canada` → IssuerName 생성 시 사용
- ❌ **현재**: 전체 DN을 그대로 IssuerName.of() 전달

예제 코드로 확인:
```java
// Line 376-378에서 CountryCode는 올바르게 추출
String countryCode = extractFromDn(crlData.getIssuerDN(), "C");  // ✅ 작동
CountryCode country = countryCode != null ? 
    CountryCode.of(countryCode) : CountryCode.of("XX");

// Line 373에서 IssuerName은 전체 DN을 전달 ❌
IssuerName issuerName = parseIssuerName(crlData.getIssuerDN());  // ❌ DN 전체
```

---

### Problem 3: Exception Handling - Silent Failure 🤐

#### 증상

**파일**: `ValidateCertificatesUseCase.java` (Line 187-199)

```java
try {
    CertificateRevocationList crl = createCertificateRevocationListAggregate(
        uploadId, crlData
    );
    crlRepository.save(crl);
} catch (Exception e) {
    log.warn("Failed to create CertificateRevocationList aggregate for: {}",
        crlData.getIssuerDN(), e);  // ❌ 단순 warning만 로깅
    invalidCrlCount++;
    validCrlCount--;  // ❌ 올바른 처리 아님
}
```

**문제점**:
1. Exception 발생 후 계속 진행
2. Warning 로그만 남음
3. `validCrlCount--` 실행 (처음엔 ++ 했는데 역으로 처리)
4. 데이터베이스에 CRL이 저장되지 않음

#### 결과

```
로그:
  - "Valid CRL extracted: CN=csca-canada" (Line 184)
  - "Failed to create CertificateRevocationList" (Line 195) ← 조용한 실패
  
데이터베이스:
  - CRL 저장 안됨 (save() 호출 안 됨)
```

---

### Problem 4: UploadToLdapUseCase - Not Implemented ⚠️

#### 증상

**파일**: `UploadToLdapUseCase.java` (Line 19-73)

```java
/**
 * UploadToLdapUseCase - LDAP 서버 업로드 Use Case (LEGACY - DO NOT USE)
 *
 * <p><b>⚠️ DEPRECATED</b>: This is a legacy simulation implementation from earlier phases.</p>
 * <p><b>Use instead</b>: com.smartcoreinc.localpkd.certificatevalidation.application.usecase.UploadToLdapUseCase</p>
 * ...
 */
@Deprecated(since = "Phase 17", forRemoval = true)
public class UploadToLdapUseCase {
```

**문제점**:
1. 주석에서 "DEPRECATED"라고 명시
2. "legacy simulation implementation"이라고 명시
3. "DO NOT USE"라고 명시
4. **실제로는 LDAP 서버에 데이터를 업로드하지 않음**

#### 코드 검토

`execute()` 메서드 (Line 85-100):
```java
@Transactional
public UploadToLdapResponse execute(UploadToLdapCommand command) {
    log.info("=== LDAP upload started ===");
    log.info("UploadId: {}, Certificates: {}, CRLs: {}, BatchSize: {}",
        command.uploadId(), command.validCertificateCount(), 
        command.validCrlCount(), command.batchSize());

    long startTime = System.currentTimeMillis();

    try {
        // 1. Command 검증
        command.validate();

        // 2. SSE 진행 상황 전송: LDAP_SAVING_STARTED (90%)
        progressService.sendProgress(
            ProcessingProgress.builder()
                .uploadId(command.uploadId())
                // ... (Line 100에서 자름)
```

**실제 LDAP 호출 코드가 없음** - 단순히 로그 기록만 함

---

### Problem 5: Zero Certificates with Completion Event ✗

#### 증상

**로그 분석**:
```
17:10:47 [INFO] UploadToLdapEventHandler: 
  Upload ID: 08502cfc-2f6f-4bbf-92c5-5da3085fa11b, 
  Valid certificates: 0 ❌, Invalid certificates: 9829

17:10:47 [DEBUG] JpaCertificateRepository: 
  Found 0 Certificate(s) for uploadId  ❌

17:10:47 [WARN] UploadToLdapEventHandler:
  No certificates found for upload ID ❌

17:10:47 [DEBUG] ProgressService:
  Sending progress: LDAP_SAVING_COMPLETED, percentage=100% ✓ (?)
```

#### 문제점

**파일**: `UploadToLdapEventHandler.java` (Line 106-109)

```java
if (certificates.isEmpty()) {
    log.warn("No certificates found for upload ID: {}", event.getUploadId());
    return;  // ❌ Early return
}
```

**하지만**:
```
17:10:47 [DEBUG] [c.s.l.s.progress.ProgressService : 121] - 
  Sending progress: ..., stage=LDAP_SAVING_COMPLETED, percentage=100%
```

**누가 LDAP_SAVING_COMPLETED를 발행하는가?**
- `UploadToLdapEventHandler`는 return했는데도 COMPLETED 신호 전송
- `LdapUploadEventHandler`가 처리하는 `UploadToLdapCompletedEvent` 때문일 수 있음

#### 아키텍처 문제

```
CertificatesValidatedEvent
  ↓
UploadToLdapEventHandler.handleCertificatesValidated()
  ├─ certificateRepository.findByUploadId() → 0개 ❌
  ├─ return (early exit)
  └─ (UploadToLdapUseCase 호출 안 됨)

하지만:
  ↓
UploadToLdapCompletedEvent 발행??? (어디서?)
  ↓
LdapUploadEventHandler.handleUploadToLdapCompletedAndMarkAsFinalized()
  ├─ progressService.sendProgress(..., COMPLETED, 100%)
  └─ ✓ 완료 처리
```

---

## 아키텍처 문제 다이어그램

### Current (Broken) Flow

```
FileUploadedEvent
  ↓
FileUploadEventHandler (성공)
  ↓
ParseLdifFileUseCase (성공)
  ↓
FileParsingCompletedEvent
  ↓
LdifParsingEventHandler (성공)
  ↓
ValidateCertificatesUseCase (실패 🔴)
  ├─ IssuerName validation 실패 (모든 CRL)
  ├─ 0개 valid certificate, 0개 valid CRL
  └─ CertificatesValidatedEvent (0개 데이터)
      ↓
      UploadToLdapEventHandler (조용한 실패 🔴)
      ├─ certificateRepository.findByUploadId() → 0개
      ├─ return (early exit)
      └─ UploadToLdapCompletedEvent 발행??? 
          ↓
          LdapUploadEventHandler (부정확한 완료) 🔴
          └─ progressService.sendProgress(..., COMPLETED, 100%)
              ↓
              UI: "✓ 처리 완료: 0개 항목 LDAP 업로드됨" ❌
```

---

## 문제 요약 표

| # | 문제 | 파일 | 라인 | 심각도 | 영향 |
|---|------|------|------|--------|------|
| 1 | IssuerName 검증 너무 엄격 | IssuerName.java | 61 | **CRITICAL** | 0% 성공률 |
| 2 | DN에서 CN 미추출 | ValidateCertificatesUseCase.java | 478-480 | **CRITICAL** | 모든 CRL 실패 |
| 3 | Exception 조용한 처리 | ValidateCertificatesUseCase.java | 187-199 | **HIGH** | 데이터 손실 |
| 4 | UploadToLdapUseCase 미구현 | UploadToLdapUseCase.java | 19-73 | **CRITICAL** | LDAP 업로드 안됨 |
| 5 | 0개 데이터로 완료 처리 | UploadToLdapEventHandler.java | 106-109 | **HIGH** | 잘못된 완료 신호 |

---

## 수정 계획 (Priority Order)

### Phase 1: IssuerName 검증 로직 수정 (CRITICAL)
**목표**: DN에서 CN 추출 후 IssuerName 생성

#### Step 1.1: DN에서 CN 추출
```java
// ValidateCertificatesUseCase.java
private IssuerName parseIssuerName(String dn) {
    // CN=csca-canada,OU=pptc,O=gc,C=CA → "csca-canada"
    String cnValue = extractFromDn(dn, "CN");
    
    if (cnValue == null || cnValue.isBlank()) {
        throw new DomainException("INVALID_ISSUER_NAME",
            "No CN found in issuer DN: " + dn);
    }
    
    // Value Object에 CN만 전달, 국가 코드는 C에서 추출
    return IssuerName.of(cnValue);
}
```

#### Step 1.2: IssuerName 검증 로직 완화
```java
// IssuerName.java
// BEFORE: ^CSCA-[A-Z]{2}$
// AFTER: 더 유연한 검증
private static final Pattern ISSUER_PATTERN = 
    Pattern.compile("^[A-Za-z0-9 \\-_]+$");  // 일반적인 issuer name 허용
```

### Phase 2: UploadToLdapUseCase 실제 구현 (CRITICAL)
**목표**: LDAP 서버에 실제 데이터 업로드

#### Step 2.1: LDAP 호출 추가
```java
// UploadToLdapUseCase.java
// 현재 코드: 로그만 기록
// 필요: UnboundIdLdapConnectionAdapter.uploadCertificateToLdap() 호출
```

### Phase 3: 예외 처리 강화 (HIGH)
**목표**: 데이터 손실 방지

#### Step 3.1: 명확한 오류 추적
```java
// 현재: continue (조용한 실패)
// 필요: 오류 로깅 + 통계 업데이트 분리
```

### Phase 4: 완료 이벤트 로직 수정 (HIGH)
**목표**: 0개 데이터로 완료 처리 금지

#### Step 4.1: 조건부 완료
```java
// UploadToLdapEventHandler.java
if (certificates.isEmpty()) {
    // 부분 실패 이벤트 발행
    publishPartialFailureEvent(event);
    return;
}
```

---

## 추가 권장사항

### 1. 검증 전략 재설계

**현재 문제점**:
- IssuerName을 특정 형식으로만 제한
- 실제 데이터는 다양한 형식

**권장사항**:
- IssuerName을 더 유연하게 설계
- DN의 각 컴포넌트(CN, C, O, OU)를 Value Objects로 분리
- 국가 코드는 C 컴포넌트에서만 추출

### 2. Domain Exception vs IllegalArgumentException

**현재 코드**:
```java
// IssuerName.java
throw new DomainException(...);  // ✓ 비즈니스 규칙

// ValidateCertificatesUseCase.java
throw new IllegalArgumentException(
    "Failed to create CertificateRevocationList aggregate", e);  // ✗ 너무 일반적
```

**권장사항**:
- Domain Layer: `DomainException` 사용
- Application Layer에서 적절한 예외로 변환

### 3. SSE Progress 정확성

**현재 문제**:
- 0개 데이터로 COMPLETED 신호 전송

**권장사항**:
- 실제 업로드된 항목 수를 기반으로 progress 계산
- 부분 실패 시 명확한 메시지 표시

### 4. 테스트 강화

**필요한 테스트**:
- 다양한 DN 형식에 대한 IssuerName 검증
- 0개 데이터 시나리오
- LDAP 업로드 시뮬레이션

---

## 참고 문서

### CLAUDE.md 관련 섹션

**아키텍처**:
- Phase 17: Event-Driven LDAP Upload Pipeline
- Phase 12: Certificate Validation Context
- 코딩 규칙: Value Object 작성 규칙

**Domain Rules (CLAUDE.md 섹션 참조)**:
```
## 🔑 핵심 규칙: LDIF 파일의 DN 구조 유지 및 baseDN 변환만 수행

⚠️ 이 규칙을 반드시 기억하고 모든 CSCA, DSC, CRL 업로드에 적용할 것

DN 변환 규칙 (정규식):
  originalDn.replaceAll("dc=icao,dc=int$", "dc=ldap,dc=smartcoreinc,dc=com")
```

---

## 결론

Phase 17은 이벤트 기반 아키텍처는 완성되었지만, **핵심 비즈니스 로직이 미구현**되어 있습니다:

1. ✅ 이벤트 시스템: 완성
2. ❌ IssuerName 검증: 0% 성공률
3. ❌ LDAP 업로드: 미구현
4. ❌ 오류 처리: 조용한 실패

**즉시 해결이 필요한 상황입니다.**

