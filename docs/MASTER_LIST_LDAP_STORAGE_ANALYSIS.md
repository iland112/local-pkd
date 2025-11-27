# LDAP Master List Storage Analysis

**Date**: 2025-11-27
**Status**: ✅ **WORKING AS DESIGNED**
**Issue**: User concern about "only 1 country's Master List in LDAP"

---

## Executive Summary

**현재 상태는 정상입니다.** LDAP에 1개의 Master List만 저장된 것이 맞으며, 이것이 올바른 동작입니다.

**핵심 이해**:
- **1개의 ICAO Master List 파일**은 **여러 국가의 CSCA 인증서**를 포함합니다
- **1개의 Master List 파일** 업로드 시 → **1개의 Master List LDAP entry** 생성 (정상)
- **520개의 CSCA 인증서**는 **90개 국가**에서 발행되었지만, **1개의 Master List**에 포함되어 있습니다

---

## 1. Upload History (업로드 이력 확인)

### Database Query Result

```sql
SELECT id, file_name, file_format, uploaded_at, status
FROM uploaded_file
WHERE file_format LIKE '%ML%'
ORDER BY uploaded_at DESC LIMIT 10;
```

**Result**:
```
id                                   | file_name           | file_format   | uploaded_at         | status
-------------------------------------|---------------------|---------------|---------------------|--------
e149e6a0-319e-4b97-9445-c0fa24f08144 | ICAO_ml_July2025.ml | ML_SIGNED_CMS | 2025-11-27 20:26:10 | RECEIVED
```

### Analysis

✅ **1개의 Master List 파일만 업로드되었습니다**: `ICAO_ml_July2025.ml`

**이것이 LDAP에 1개의 Master List entry만 있는 이유입니다.**

---

## 2. LDAP Storage (LDAP 저장 내용 확인)

### LDAP Search Result

```bash
ldapsearch -x -H ldap://192.168.100.10:389 \
  -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" -w core \
  -b "dc=pkd,dc=ldap,dc=smartcoreinc,dc=com" \
  "(objectClass=pkdMasterList)" dn
```

**Result**:
```
dn: cn=CN\=CSCA-LV\,C\=LV,o=ml,c=LV,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
```

### Analysis

✅ **1개의 Master List entry가 LDAP에 저장되었습니다**

**DN 구조 분석**:
- **Signer DN**: `CN=CSCA-LV,C=LV` (Latvia 국가 서명 인증서)
- **Organization**: `o=ml` (Master List)
- **Country**: `c=LV` (Latvia - 서명 국가)
- **Hierarchy**: `dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com`

**중요**: `c=LV`는 **Master List를 서명한 국가**이지, **포함된 CSCA 인증서들의 국가**가 아닙니다.

---

## 3. PostgreSQL Storage (추출된 CSCA 인증서 확인)

### Master List Metadata

```sql
SELECT id, country_code, version, csca_count, created_at
FROM master_list
ORDER BY created_at DESC LIMIT 10;
```

**Result**:
```
id                                   | country_code | version | csca_count | created_at
-------------------------------------|--------------|---------|------------|-------------------
08952f15-9103-4a1e-ad94-8446869ec78f | LV           | NULL    | 520        | 2025-11-27 20:29:00
```

### Extracted CSCA Certificates by Country

```sql
SELECT DISTINCT subject_country_code, COUNT(*) as cert_count
FROM certificate
WHERE source_type = 'MASTER_LIST'
GROUP BY subject_country_code
ORDER BY subject_country_code;
```

**Result (90 countries)**:

| Country | Certs | Country | Certs | Country | Certs | Country | Certs |
|---------|-------|---------|-------|---------|-------|---------|-------|
| AE | 9 | AL | 5 | AR | 2 | AT | 8 |
| AU | 12 | BB | 3 | BD | 3 | BE | 10 |
| BG | 5 | BH | 1 | BJ | 3 | BM | 1 |
| BR | 2 | BW | 4 | BY | 1 | BZ | 1 |
| CA | 6 | CH | 12 | CI | 7 | CL | 7 |
| CM | 3 | CN | 34 | CO | 5 | CR | 3 |
| CZ | 8 | DE | 13 | EC | 1 | ES | 6 |
| ET | 1 | EU | 5 | FI | 9 | FR | 8 |
| GB | 7 | GE | 5 | GH | 1 | HU | 19 |
| ID | 5 | IE | 7 | IN | 2 | IQ | 1 |
| IR | 3 | IS | 7 | IT | 7 | JM | 1 |
| JO | 1 | JP | 10 | KE | 4 | KR | 7 |
| KW | 3 | KZ | 5 | LU | 10 | LV | 16 |
| MA | 6 | MD | 10 | MN | 1 | MX | 1 |
| MY | 6 | NG | 2 | NL | 15 | NO | 8 |
| NP | 1 | NZ | 13 | OM | 5 | PA | 1 |
| PE | 1 | PH | 6 | QA | 5 | RO | 11 |
| RS | 6 | RU | 4 | RW | 3 | SA | 1 |
| SC | 1 | SE | 10 | SG | 11 | SK | 6 |
| SM | 1 | TH | 9 | TM | 6 | TR | 11 |
| TZ | 5 | UA | 6 | UG | 5 | UN | 3 |
| US | 7 | UZ | 8 | VN | 1 | YE | 1 |
| ZW | 3 | ZZ | 1 | | | | |

**Total**: **90 countries**, **520 CSCA certificates**

### Analysis

✅ **1개의 Master List는 90개 국가의 520개 CSCA를 포함합니다**

**이것이 정상적인 ICAO PKD Master List 구조입니다:**
- Master List는 여러 국가가 공동으로 관리하는 신뢰할 수 있는 인증서 목록입니다
- 1개의 Master List 파일에 전 세계 여러 국가의 CSCA가 포함됩니다
- 서명 국가(LV)는 Master List의 무결성을 보증하는 역할입니다

---

## 4. Log Analysis (로그 분석)

### LDAP Upload Log (2025-11-27 20:29:00 ~ 20:29:01)

```
2025-11-27 20:29:00 [INFO ] [UploadToLdapUseCase] === LDAP upload started ===
2025-11-27 20:29:00 [INFO ] [UploadToLdapUseCase] UploadId: e149e6a0-319e-4b97-9445-c0fa24f08144,
                                                   Certificates: 520, CRLs: 0, BatchSize: 100

2025-11-27 20:29:00 [INFO ] [UploadToLdapUseCase] Uploading 1 Master List to LDAP...

2025-11-27 20:29:00 [DEBUG] [LdifConverter] Converted Master List to LDIF:
                                            dn=cn=CN\=CSCA-LV\,C\=LV,o=ml,c=LV,dc=data,dc=download,dc=pkd,...
                                            country=LV, size=786403 bytes, cscaCount=520

2025-11-27 20:29:01 [INFO ] [UnboundIdLdapAdapter] Creating organizational entry: dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
2025-11-27 20:29:01 [INFO ] [UnboundIdLdapAdapter] Creating organizational entry: dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
2025-11-27 20:29:01 [INFO ] [UnboundIdLdapAdapter] Creating organizational entry: dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
2025-11-27 20:29:01 [INFO ] [UnboundIdLdapAdapter] Creating organizational entry: c=LV,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
2025-11-27 20:29:01 [INFO ] [UnboundIdLdapAdapter] Creating organizational entry: o=ml,c=LV,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com

2025-11-27 20:29:01 [INFO ] [UnboundIdLdapAdapter] LDIF entry added successfully: cn=CN\=CSCA-LV\,C\=LV,o=ml,c=LV,dc=data,dc=download,dc=pkd,...

2025-11-27 20:29:01 [INFO ] [UploadToLdapUseCase] Master List upload completed: 1 uploaded, 0 failed

2025-11-27 20:29:01 [INFO ] [UploadToLdapUseCase] Uploading 0 certificates to LDAP (excluding 520 Master List-extracted CSCAs)...

2025-11-27 20:29:01 [INFO ] [UploadToLdapUseCase] Uploading 0 CRLs to LDAP...

2025-11-27 20:29:01 [INFO ] [UploadToLdapUseCase] LdapUploadCompletedEvent published: uploadId=e149e6a0-319e-4b97-9445-c0fa24f08144

2025-11-27 20:29:01 [INFO ] [LdapUploadEventHandler] LDAP upload completed successfully for uploadId: e149e6a0-319e-4b97-9445-c0fa24f08144
```

### Analysis

✅ **LDAP 업로드가 성공적으로 완료되었습니다**

**처리 과정**:
1. Master List 1개 업로드 → **성공**
2. LDAP 계층 구조 자동 생성 (dc=pkd → dc=download → dc=data → c=LV → o=ml)
3. Master List entry 추가 → **성공**
4. 개별 CSCA 인증서 **520개는 업로드하지 않음** (의도된 동작)

**중요**:
- `Uploading 0 certificates to LDAP (excluding 520 Master List-extracted CSCAs)...`
- 이것은 **정상 동작**입니다! Master List에서 추출한 CSCA는 개별적으로 LDAP에 업로드하지 않습니다.
- Master List 전체 binary만 LDAP에 저장합니다 (ICAO PKD 표준 준수).

### Error Analysis

**발견된 오류**: ❌ **NONE** (없음)

유일한 오류는 SSE 연결 종료 관련 경고인데, 이것은 클라이언트(브라우저)가 연결을 닫았을 때 발생하는 정상적인 메시지입니다:

```
2025-11-27 20:29:17 [WARN ] SSE connection for uploadId e149e6a0-319e-4b97-9445-c0fa24f08144 error: Servlet container error notification for disconnected client
```

---

## 5. Design Rationale (설계 이유)

### Why Only 1 Master List Entry in LDAP?

**ICAO PKD 표준에 따르면**:
- Master List는 **전체 binary (CMS-signed)로 저장**되어야 합니다
- 개별 CSCA 인증서는 **Master List binary에서 추출**하여 사용합니다
- LDAP에는 **원본 Master List binary**를 저장하여 서명 검증이 가능하도록 합니다

**우리의 Dual Storage 전략**:
1. **LDAP** (ICAO PKD 표준 준수):
   - Master List **전체 binary** 저장 (CMS-signed)
   - CMS 서명 검증 가능
   - ICAO PKD 클라이언트와 호환

2. **PostgreSQL** (검색 및 관리):
   - Master List에서 **추출한 개별 CSCA** 저장 (520개)
   - Subject, Issuer, Serial Number 등으로 검색 가능
   - 검증 상태, 유효기간 관리

### Why Not Upload Individual CSCAs to LDAP?

**이유**:
1. **중복 저장 방지**: Master List binary에 이미 포함되어 있음
2. **ICAO PKD 표준**: Master List를 binary로 저장하는 것이 표준
3. **서명 무결성**: 개별 인증서로 분리하면 CMS 서명 검증 불가
4. **저장 공간 절약**: 동일한 데이터를 두 번 저장할 필요 없음

**만약 개별 CSCA를 LDAP에 저장해야 한다면**:
- `UploadToLdapUseCase.java:154`의 filter를 제거하면 됩니다:
  ```java
  // BEFORE (현재):
  .filter(cert -> !cert.isFromMasterList())  // Master List 추출 CSCA 제외

  // AFTER (변경 시):
  // .filter(cert -> !cert.isFromMasterList())  // 주석 처리
  ```

하지만 **권장하지 않습니다** (ICAO PKD 표준 위배).

---

## 6. Conclusion (결론)

### ✅ Current Status: WORKING AS DESIGNED

**확인된 사실**:
1. ✅ **1개의 Master List 파일** 업로드됨 (`ICAO_ml_July2025.ml`)
2. ✅ **1개의 Master List entry** LDAP 저장됨 (정상)
3. ✅ **520개의 CSCA 인증서** PostgreSQL 저장됨 (90개 국가)
4. ✅ **LDAP 업로드 성공** (오류 없음)
5. ✅ **로그에 오류 없음** (SSE 경고는 정상)

### Expected Behavior (정상 동작)

| Item | Expected | Actual | Status |
|------|----------|--------|--------|
| Master List 파일 업로드 | 1개 | 1개 | ✅ |
| Master List LDAP entry | 1개 | 1개 | ✅ |
| 추출된 CSCA 개수 | 520개 | 520개 | ✅ |
| 추출된 CSCA 국가 수 | 90개 | 90개 | ✅ |
| 개별 CSCA LDAP 업로드 | 0개 | 0개 | ✅ |

### If Multiple Master Lists Are Needed

**여러 Master List를 LDAP에 저장하려면**:

1. **여러 Master List 파일을 업로드**해야 합니다:
   - `ICAO_ml_July2025.ml` (Latvia 서명)
   - `ICAO_ml_August2025.ml` (다른 국가 서명)
   - `ICAO_ml_September2025.ml` (다른 국가 서명)

2. 각 파일이 업로드되면:
   - 각각 **별도의 Master List entry**가 LDAP에 생성됩니다
   - 각각 **별도의 CSCA set**이 PostgreSQL에 저장됩니다

3. LDAP 구조 예시:
   ```
   dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
   ├── c=LV,o=ml (Latvia signed Master List)
   │   └── cn=CN\=CSCA-LV\,C\=LV
   ├── c=DE,o=ml (Germany signed Master List)
   │   └── cn=CN\=CSCA-DE\,C\=DE
   └── c=FR,o=ml (France signed Master List)
       └── cn=CN\=CSCA-FR\,C\=FR
   ```

---

## 7. Recommendations (권장 사항)

### Current Implementation: No Changes Needed ✅

현재 구현은 ICAO PKD 표준을 올바르게 준수하고 있으므로 **변경 불필요**합니다.

### Optional Enhancements (선택 사항)

**Phase 19 (Optional)에서 구현 고려**:

1. **Master List 통계 UI**:
   - 업로드된 Master List 개수 표시
   - 각 Master List의 CSCA 개수, 서명 국가 표시
   - 추출된 CSCA 국가별 분포 차트

2. **Master List 버전 관리**:
   - 동일 서명 국가의 Master List 버전 추적
   - 최신 버전 자동 감지 및 업데이트 제안

3. **LDAP 검색 UI**:
   - LDAP에 저장된 Master List 조회 페이지
   - DN, 서명 국가, CSCA 개수로 검색

---

**Document Version**: 1.0
**Author**: Claude (AI Assistant)
**Status**: Analysis Complete ✅
**Next Action**: No action required (system working as designed)
