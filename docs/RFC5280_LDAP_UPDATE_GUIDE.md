# RFC 5280 준수 LDAP 업데이트 가이드

**Version**: 1.0
**Last Updated**: 2025-12-25
**Author**: SmartCore Inc.

---

## 개요

이 문서는 ICAO Doc 9303 및 RFC 5280 표준에 따른 PKD 데이터의 LDAP 업데이트 로직을 설명합니다.

---

## 1. RFC 5280 인증서 식별 기준

### 1.1 인증서 고유 식별자

RFC 5280 (Internet X.509 PKI Certificate and CRL Profile)에 따르면, 인증서는 다음 조합으로 고유하게 식별됩니다:

```
Certificate Unique ID = Issuer DN + Serial Number
```

- **Issuer DN**: 인증서를 발급한 CA의 Distinguished Name
- **Serial Number**: CA가 발급한 고유 일련번호

### 1.2 LDAP DN 구조 (ICAO PKD 표준)

| 인증서 유형 | LDAP DN 패턴 |
|------------|-------------|
| CSCA | `cn={SerialNumber},o=csca,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}` |
| DSC | `cn={SerialNumber},o=dsc,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}` |
| DSC_NC | `cn={SerialNumber},o=dsc,c={COUNTRY},dc=nc-data,dc=download,dc=pkd,{baseDN}` |
| CRL | `cn={CRLNumber},o=crl,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}` |
| Master List | `cn={SerialNumber},o=ml,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}` |

---

## 2. ICAO Doc 9303 PKD 표준

### 2.1 CSCA (Country Signing CA)

- **정의**: 각 국가의 최상위 인증 기관
- **유효 기간**: 일반적으로 3-5년
- **갱신 주기**: 만료 90일 전 사전 공지 권장
- **Self-Signed**: 자체 서명 인증서

### 2.2 DSC (Document Signer Certificate)

- **정의**: CSCA가 발급한 여권 서명 인증서
- **유효 기간**: 일반적으로 1-3년
- **Trust Chain**: CSCA → DSC
- **용도**: SOD (Security Object Document) 서명

### 2.3 CRL (Certificate Revocation List)

- **정의**: 폐기된 인증서 목록
- **CRL Number Extension** (OID 2.5.29.20): 단조 증가하는 고유 번호
- **갱신 규칙**: 새 CRL Number > 기존 CRL Number인 경우만 교체

### 2.4 Master List

- **정의**: 국가별 신뢰할 수 있는 CSCA 인증서 목록
- **갱신 주기**: 분기별 (약 3개월)
- **CMS 형식**: Signed Data 구조로 CSCA 인증서 포함
- **비교 기준**: CMS 바이너리 전체 비교

---

## 3. LDAP 업데이트 로직

### 3.1 인증서 (CSCA/DSC) 업데이트 알고리즘

```java
public CertAddResult addOrUpdateCertificateEntry(String ldifEntryText) {
    // 1. DN 추출 (Issuer DN + Serial Number 기반)
    String dn = extractDn(ldifEntryText);

    // 2. 기존 엔트리 조회
    Optional<CertificateEntryInfo> existing = getCertificateFromLdap(dn);

    if (existing.isEmpty()) {
        // 3a. 신규 인증서: ADD 연산
        return ADD;
    }

    // 3b. 바이너리 비교
    if (Arrays.equals(existing.certBinary, newCertBinary)) {
        // 3c. description(검증 상태)만 비교
        if (existing.description.equals(newDescription)) {
            return SKIPPED;  // 완전 동일
        } else {
            // 3d. description 업데이트: MODIFY 연산
            modifyCertificateDescription(dn, newDescription);
            return UPDATED;
        }
    }

    // 3e. 바이너리가 다른 경우 (이론적으로 발생하지 않아야 함)
    return UPDATED;
}
```

### 3.2 CRL 업데이트 알고리즘 (RFC 5280 CRL Number 비교)

```java
public CrlAddResult addOrUpdateCrlEntry(String ldifEntryText) {
    // 1. DN 추출
    String dn = extractDn(ldifEntryText);

    // 2. 기존 CRL 조회
    Optional<CrlEntryInfo> existing = getCrlFromLdap(dn);

    if (existing.isEmpty()) {
        // 3a. 신규 CRL: ADD 연산
        return ADDED;
    }

    // 3b. CRL Number 비교 (RFC 5280 2.5.29.20)
    BigInteger existingCrlNumber = extractCrlNumber(existing.crlBinary);
    BigInteger newCrlNumber = extractCrlNumber(newCrlBinary);

    if (newCrlNumber.compareTo(existingCrlNumber) > 0) {
        // 3c. 새 CRL이 더 최신: MODIFY 연산
        modifyCrlEntry(dn, newCrlBinary);
        return UPDATED;
    } else {
        // 3d. 기존 CRL이 더 최신이거나 동일: SKIP
        return SKIPPED;
    }
}
```

### 3.3 Master List 업데이트 알고리즘

```java
public MasterListAddResult addOrUpdateMasterListEntry(String ldifEntryText) {
    // 1. DN 추출
    String dn = extractDn(ldifEntryText);

    // 2. 기존 Master List 조회
    Optional<byte[]> existingMl = getMasterListFromLdap(dn);

    if (existingMl.isEmpty()) {
        // 3a. 신규: ADD 연산
        return ADDED;
    }

    // 3b. CMS 바이너리 비교
    if (Arrays.equals(existingMl.get(), newMlBinary)) {
        // 3c. 동일: SKIP
        return SKIPPED;
    } else {
        // 3d. 다름: MODIFY 연산
        modifyMasterListEntry(dn, newMlBinary);
        return UPDATED;
    }
}
```

---

## 4. 구현 세부 사항

### 4.1 주요 클래스 및 메서드

| 클래스 | 메서드 | 설명 |
|--------|--------|------|
| `UnboundIdLdapAdapter` | `addOrUpdateCertificateEntriesBatch()` | 인증서 배치 업로드 (RFC 5280 비교) |
| `UnboundIdLdapAdapter` | `addOrUpdateCrlEntriesBatch()` | CRL 배치 업로드 (CRL Number 비교) |
| `UnboundIdLdapAdapter` | `addOrUpdateMasterListEntry()` | Master List 업로드 (바이너리 비교) |
| `UploadToLdapUseCase` | `execute()` | LDAP 업로드 통합 처리 |

### 4.2 결과 타입

```java
// 인증서 결과
public enum CertAddResult {
    ADDED,      // 신규 추가
    UPDATED,    // description 업데이트
    SKIPPED,    // 동일하여 스킵
    ERROR       // 오류
}

// CRL 결과
public record CrlBatchResult(int added, int updated, int skipped, int errors) {
    public int totalSuccess() { return added + updated; }
}

// Master List 결과
public enum MasterListAddResult {
    ADDED,      // 신규 추가
    UPDATED,    // 내용 업데이트
    SKIPPED,    // 동일하여 스킵
    ERROR       // 오류
}
```

### 4.3 Progress 메시지 형식

**LDAP 저장 완료 메시지**:
```
LDAP 저장 완료 (신규 5개, 업데이트 2개, 동일하여 스킵 3개)
```

**상세 통계**:
```
인증서: 8개 (신규 3, 업데이트 2, 동일하여 스킵 3)
MasterList: 2개 (신규 1, 동일하여 스킵 1)
```

---

## 5. 참조 표준

1. **RFC 5280** - Internet X.509 Public Key Infrastructure Certificate and Certificate Revocation List (CRL) Profile
   - Section 5.2.3: CRL Number Extension (OID 2.5.29.20)
   - Section 4.1.2.2: Serial Number

2. **ICAO Doc 9303** - Machine Readable Travel Documents
   - Part 12: Public Key Infrastructure for MRTDs

3. **RFC 5652** - Cryptographic Message Syntax (CMS)
   - Master List는 CMS SignedData 형식 사용

---

## 6. 변경 이력

| 버전 | 날짜 | 변경 내용 |
|------|------|----------|
| 1.0 | 2025-12-25 | 최초 작성 - RFC 5280 준수 LDAP 업데이트 로직 문서화 |
