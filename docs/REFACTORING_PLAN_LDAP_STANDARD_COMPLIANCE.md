# LDAP Standard Compliance Refactoring Plan

**Date**: 2025-11-27
**Status**: Planning
**Goal**: .ml 파일을 국가별 Master List로 분리하여 ICAO PKD LDAP 표준 준수

---

## 현재 상황 분석

### 파일 형식 이해

#### 1. .ml 파일 (ICAO_ml_July2025.ml)
- **형식**: CMS SignedData
- **크기**: 768 KB
- **구조**: 
  ```
  SEQUENCE (pkcs7-signedData)
  ├─ OBJECT: pkcs7-signedData
  ├─ SEQUENCE
  │  ├─ INTEGER: 03 (version)
  │  ├─ SET (digest algorithms: SHA-256)
  │  ├─ SEQUENCE (signed content)
  │  │  ├─ OBJECT: 2.23.136.1.1.2 (icao-mrtd-masterList)
  │  │  └─ OCTET STRING (782 KB)
  │  │     └─ SEQUENCE of X.509 Certificates (520 CSCAs from 90 countries)
  │  └─ SignerInfos (Latvia signature)
  ```
- **내용**: 
  - Latvia (LV)가 서명한 컨테이너
  - 90개 국가의 520개 CSCA 인증서 포함
  - 전체가 하나의 CMS SignedData

#### 2. .ldif 파일 (icaopkd-002-complete-000325.ldif)
- **형식**: LDAP Data Interchange Format
- **내용**: 이미 분리된 국가별 Master List entries
- **구조 예시** (France):
  ```ldif
  dn: cn=CN\=CSCA-FRANCE\,O\=Gouv\,C\=FR,o=ml,c=FR,dc=data,dc=download,dc=pkd,dc=icao,dc=int
  pkdVersion: 70
  sn: 1
  cn: CN=CSCA-FRANCE,O=Gouv,C=FR
  objectClass: top
  objectClass: person
  objectClass: pkdMasterList
  objectClass: pkdDownload
  pkdMasterListContent:: <base64 encoded CMS binary>
  ```

### 현재 구현 문제점

**우리의 현재 구현**:
```
.ml 파일 업로드
    ↓
1개의 Master List entry 생성 (전체)
    ↓
1개의 LDAP entry 저장
    dn: cn=CN\=CSCA-LV\,C\=LV,o=ml,c=LV,dc=data,dc=download,dc=pkd,...
    pkdMasterListContent: <전체 .ml 파일 binary>
```

**기대되는 구현** (.ldif 파일 기준):
```
.ml 파일 업로드
    ↓
국가별로 Master List 분리 (90개)
    ↓
90개의 LDAP entries 저장
    dn: cn=...,o=ml,c=FR,dc=data,dc=download,dc=pkd,...
    dn: cn=...,o=ml,c=DE,dc=data,dc=download,dc=pkd,...
    dn: cn=...,o=ml,c=KR,dc=data,dc=download,dc=pkd,...
    ...
```

---

## 핵심 질문

### pkdMasterListContent의 정체

**질문**: .ldif 파일의 각 국가별 entry에 있는 `pkdMasterListContent`는?

**Option A**: 그 국가의 CSCA만 포함하는 별도 CMS binary
- France entry의 `pkdMasterListContent` = France CSCAs만 포함
- Korea entry의 `pkdMasterListContent` = Korea CSCAs만 포함

**Option B**: 전체 .ml 파일과 동일한 CMS binary (모든 국가 포함)
- 모든 국가의 entry가 동일한 `pkdMasterListContent` 값을 가짐
- DN만 다르고 binary는 동일

**이것을 확인해야 합니다!**

---

## Refactoring 시나리오

### Scenario A: 국가별 CMS binary 분리 (Option A가 맞다면)

#### A-1. 데이터 구조 변경

**MasterList Domain Model**:
```java
// BEFORE (현재)
public class MasterList {
    private MasterListId id;
    private UploadId uploadId;
    private CountryCode countryCode;  // 서명 국가 (LV)
    private MasterListVersion version;
    private CmsBinaryData cmsBinary;   // 전체 .ml 파일
    private SignerInfo signerInfo;
    private int cscaCount;             // 전체 CSCA 개수 (520)
}

// AFTER (변경 필요)
public class MasterList {
    private MasterListId id;
    private UploadId uploadId;
    private CountryCode countryCode;  // 해당 Master List의 국가 (FR, DE, KR, ...)
    private MasterListVersion version;
    private CmsBinaryData cmsBinary;   // 그 국가의 CSCA만 포함하는 CMS binary
    private SignerInfo signerInfo;     // 원본 서명자 정보 (LV)
    private int cscaCount;             // 그 국가의 CSCA 개수
    
    // NEW: 원본 .ml 파일 추적
    private UploadId originalMasterListUploadId;  // 원본 .ml 파일의 uploadId
}
```

#### A-2. 파싱 로직 변경

**MasterListParserAdapter**:
```java
// BEFORE (현재)
public MasterListParseResult parse(byte[] masterListBytes) {
    CMSSignedData signedData = new CMSSignedData(masterListBytes);
    List<X509Certificate> allCscas = extractAllCertificates(signedData);  // 520개
    
    return new MasterListParseResult(
        "LV",  // 서명 국가
        null,
        CmsBinaryData.of(masterListBytes),  // 전체 binary
        signerInfo,
        520,  // 전체 개수
        allCscas
    );
}

// AFTER (변경 필요)
public List<MasterListParseResult> parseByCountry(byte[] masterListBytes) {
    CMSSignedData signedData = new CMSSignedData(masterListBytes);
    List<X509Certificate> allCscas = extractAllCertificates(signedData);  // 520개
    
    // 1. 국가별로 그룹화
    Map<String, List<X509Certificate>> byCountry = groupByCountry(allCscas);
    // Result: { "FR": [12 certs], "DE": [13 certs], "KR": [7 certs], ... }
    
    // 2. 각 국가별로 새로운 CMS SignedData 생성
    List<MasterListParseResult> results = new ArrayList<>();
    for (Map.Entry<String, List<X509Certificate>> entry : byCountry.entrySet()) {
        String countryCode = entry.getKey();
        List<X509Certificate> countryCscas = entry.getValue();
        
        // 3. 그 국가의 CSCA만 포함하는 CMS binary 생성
        byte[] countryCmsBinary = createCountryCmsBinary(countryCscas, signerInfo);
        
        results.add(new MasterListParseResult(
            countryCode,  // FR, DE, KR, ...
            null,
            CmsBinaryData.of(countryCmsBinary),  // 국가별 binary
            signerInfo,   // 원본 서명자 (LV)
            countryCscas.size(),
            countryCscas
        ));
    }
    
    return results;  // 90개 MasterListParseResult
}

private byte[] createCountryCmsBinary(List<X509Certificate> cscas, SignerInfo signerInfo) {
    // TODO: 새로운 CMS SignedData 생성
    // - SignedData 구조 생성
    // - OCTET STRING에 CSCA SEQUENCE 포함
    // - SignerInfo는 원본 사용 (또는 재서명)
}
```

**문제점**:
- ❌ CMS SignedData 재생성이 매우 복잡함
- ❌ 원본 서명이 무효화됨 (재서명 필요할 수도 있음)
- ❌ ICAO PKD 표준에서 이런 분리가 허용되는지 불명확

#### A-3. Database Schema 변경

**master_list 테이블**:
```sql
-- BEFORE
CREATE TABLE master_list (
    id UUID PRIMARY KEY,
    upload_id UUID NOT NULL,  -- .ml 파일 uploadId
    country_code VARCHAR(3),  -- LV (서명 국가)
    version VARCHAR(50),
    csca_count INTEGER,       -- 520 (전체)
    cms_binary BYTEA NOT NULL,
    ...
);

-- AFTER
CREATE TABLE master_list (
    id UUID PRIMARY KEY,
    upload_id UUID NOT NULL,              -- .ml 파일 uploadId
    country_code VARCHAR(3) NOT NULL,     -- FR, DE, KR, ... (해당 ML의 국가)
    version VARCHAR(50),
    csca_count INTEGER,                   -- 그 국가의 CSCA 개수
    cms_binary BYTEA NOT NULL,            -- 그 국가의 CMS binary
    signer_info JSONB,
    original_signer_country VARCHAR(3),   -- LV (원본 서명자)
    original_upload_id UUID,              -- 원본 .ml 파일 추적
    created_at TIMESTAMP NOT NULL
);

-- 1개 .ml 파일 → 90개 master_list 레코드
```

#### A-4. LDAP 저장 로직 변경

**LdifConverter**:
```java
// BEFORE (현재)
public String convertMasterListToLdif(MasterList masterList) {
    String dn = String.format("cn=%s,o=ml,c=%s,dc=data,dc=download,dc=pkd,%s",
        escapedSignerDn,
        "LV",  // 서명 국가
        ldapProperties.getBase());
    
    return String.format(
        "dn: %s\n" +
        "pkdVersion: 70\n" +
        "sn: 1\n" +
        "cn: %s\n" +
        "objectClass: top\n" +
        "objectClass: person\n" +
        "objectClass: pkdMasterList\n" +
        "objectClass: pkdDownload\n" +
        "pkdMasterListContent:: %s\n",
        dn,
        signerDn,
        base64EncodedCms  // 전체 .ml binary
    );
}

// AFTER (변경)
public String convertMasterListToLdif(MasterList masterList) {
    String dn = String.format("cn=%s,o=ml,c=%s,dc=data,dc=download,dc=pkd,%s",
        escapedSignerDn,
        masterList.getCountryCode(),  // FR, DE, KR, ...
        ldapProperties.getBase());
    
    return String.format(
        "dn: %s\n" +
        "pkdVersion: %s\n" +
        "sn: 1\n" +
        "cn: %s\n" +
        "objectClass: top\n" +
        "objectClass: person\n" +
        "objectClass: pkdMasterList\n" +
        "objectClass: pkdDownload\n" +
        "pkdMasterListContent:: %s\n",
        dn,
        masterList.getVersion(),
        signerDn,
        base64EncodedCms  // 그 국가의 CMS binary
    );
}
```

**UploadToLdapUseCase**:
```java
// BEFORE
Optional<MasterList> masterListOpt = masterListRepository.findByUploadId(uploadId);
if (masterListOpt.isPresent()) {
    MasterList masterList = masterListOpt.get();
    String ldif = ldifConverter.convertMasterListToLdif(masterList);
    ldapAdapter.addLdifEntry(ldif);  // 1개 entry
}

// AFTER
List<MasterList> masterLists = masterListRepository.findAllByUploadId(uploadId);
for (MasterList masterList : masterLists) {  // 90개
    String ldif = ldifConverter.convertMasterListToLdif(masterList);
    ldapAdapter.addLdifEntry(ldif);  // 90개 entries
}
```

---

### Scenario B: DN만 분리 (Option B가 맞다면)

**만약 `pkdMasterListContent`가 모든 entry에서 동일하다면**:

#### B-1. 간단한 접근법

**동일한 CMS binary, 다른 DN**:
```java
public void uploadMasterListToLdap(MasterList masterList) {
    // 1. 모든 국가 추출
    List<String> countries = extractCountriesFromMasterList(masterList);
    // Result: [FR, DE, KR, AT, BE, ...]
    
    // 2. 각 국가별로 LDAP entry 생성 (동일한 binary)
    for (String countryCode : countries) {
        String dn = String.format("cn=%s,o=ml,c=%s,dc=data,dc=download,dc=pkd,%s",
            "CSCA-" + countryCode,
            countryCode,
            ldapProperties.getBase());
        
        String ldif = String.format(
            "dn: %s\n" +
            "objectClass: pkdMasterList\n" +
            "pkdMasterListContent:: %s\n",  // 동일한 전체 .ml binary
            dn,
            base64Encode(masterList.getCmsBinary())
        );
        
        ldapAdapter.addLdifEntry(ldif);
    }
}
```

**장점**:
- ✅ 구현 간단
- ✅ 원본 CMS 서명 유지
- ✅ Database 변경 최소화

**단점**:
- ❌ 동일한 binary를 90번 중복 저장 (LDAP 저장 공간 낭비)
- ❌ ICAO PKD 표준에 맞지 않을 수도 있음

---

## 다음 단계

### 1. pkdMasterListContent 확인 (최우선)

**방법 1**: Python으로 LDIF 파싱
```python
import ldif
import base64
import hashlib

with open('data/download/icaopkd-002-complete-000325.ldif', 'rb') as f:
    parser = ldif.LDIFParser(f)
    
    france_content = None
    germany_content = None
    
    for dn, entry in parser.parse():
        if 'c=FR' in dn and 'o=ml' in dn:
            france_content = entry.get('pkdMasterListContent', [None])[0]
        if 'c=DE' in dn and 'o=ml' in dn:
            germany_content = entry.get('pkdMasterListContent', [None])[0]
    
    # SHA-256 해시 비교
    if france_content and germany_content:
        hash_fr = hashlib.sha256(france_content).hexdigest()
        hash_de = hashlib.sha256(germany_content).hexdigest()
        
        if hash_fr == hash_de:
            print("Option B: 동일한 binary (전체 .ml 파일)")
        else:
            print("Option A: 국가별로 다른 binary")
```

**방법 2**: 사용자에게 직접 질문

### 2. 구현 방향 결정

**Option A가 맞다면** → Scenario A 구현 (복잡, 시간 소요)
**Option B가 맞다면** → Scenario B 구현 (간단, 빠름)

### 3. Phase 3 완료 후 별도 Phase로 진행

**Phase 19 또는 Phase 20**:
- Master List 국가별 분리 refactoring
- ICAO PKD LDAP 표준 완전 준수
- 통합 테스트

---

## 리스크 및 고려사항

### 기술적 리스크

1. **CMS 재생성 복잡도** (Scenario A):
   - Bouncy Castle로 CMS SignedData 생성 가능하지만 복잡
   - 원본 서명 유지 vs 재서명 문제

2. **ICAO PKD 표준 준수**:
   - 실제 ICAO PKD 서버의 구조 확인 필요
   - .ldif 파일이 정확히 어떻게 생성되었는지 불명확

3. **Database Migration**:
   - 기존 1개 master_list 레코드 → 90개로 분리
   - Certificate의 master_list_id FK 관계 유지

### 대안

**현재 구현 유지 + LDIF 파일 지원**:
- .ml 파일: 전체를 1개 Master List로 저장 (현재 방식)
- .ldif 파일: 이미 분리된 Master List entries 그대로 import

**점진적 마이그레이션**:
- Phase 3: 현재 구현 완료 (1개 Master List)
- Phase 19: .ldif 파일 import 지원 추가
- Phase 20: .ml 파일 국가별 분리 (선택적)

---

**Document Version**: 1.0
**Author**: Claude (AI Assistant)
**Status**: Awaiting User Decision
**Next Action**: pkdMasterListContent 구조 확인 필요
