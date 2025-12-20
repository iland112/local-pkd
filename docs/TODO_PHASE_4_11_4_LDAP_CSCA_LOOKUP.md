# TODO: Phase 4.11.4 - LDAP 기반 CSCA 조회 구현

**작성일**: 2025-12-19
**상태**: In Progress
**목표**: PA 인증을 위한 CSCA를 LDAP에서 조회하도록 아키텍처 수정

---

## 📋 배경

### 문제점
- ❌ 현재 `PerformPassiveAuthenticationUseCase`가 DBMS `certificate` 테이블에서 CSCA 조회
- ❌ `CertificateRepository.findBySubjectDn()` 사용 (잘못된 데이터 소스)
- ❌ LDAP 조회 로직 누락

### ICAO 9303 표준 요구사항
```
PA Workflow:
1. SOD에서 DSC 추출
2. DSC Issuer DN 확인
3. LDAP에서 CSCA 조회 ← 여기가 핵심!
4. DSC Trust Chain 검증
5. SOD 서명 검증
6. Data Group Hash 검증
```

### 올바른 아키텍처
- **PKD Upload Module**: DBMS `certificate` 테이블 (업로드 이력, 통계만)
- **PA Module**: LDAP 전용 (실시간 검증, 실제 인증서 저장소)

---

## ✅ Task List

### Task 1: Delete incorrect DBMS test data files
**Status**: Pending
**Priority**: High

**Files to delete**:
- [ ] `src/test/resources/data.sql`
- [ ] `src/test/resources/db/migration/V100__Insert_Test_CSCA.sql`
- [ ] `src/test/resources/db/migration/V101__Insert_Test_DSC.sql`

**Reason**: PA는 DBMS 대신 LDAP 사용

---

### Task 2: Verify CSCA003 exists in OpenLDAP
**Status**: Pending
**Priority**: High

**Expected LDAP Entry**:
```
DN: cn=CN\3DCSCA003\2COU\3DMOFA\2CO\3DGovernment\2CC\3DKR+sn=101,o=csca,c=KR,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com

Attributes:
- cn: CN=CSCA003,OU=MOFA,O=Government,C=KR
- sn: 101
- userCertificate;binary: (X.509 DER bytes)
- objectClass: pkdDownload, inetOrgPerson, organizationalPerson, person, top
```

**Verification Command**:
```bash
ldapsearch -x -H ldap://192.168.100.10:389 \
  -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" -w "core" \
  -b "dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com" \
  "(&(objectClass=pkdDownload)(cn=*CSCA003*))" cn sn
```

---

### Task 3: Create LdapCscaRepository interface (Port)
**Status**: Pending
**Priority**: High

**File**: `src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/port/LdapCscaRepository.java`

**Interface**:
```java
package com.smartcoreinc.localpkd.passiveauthentication.domain.port;

import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * LDAP에서 CSCA 인증서를 조회하는 Port 인터페이스
 *
 * <p>ICAO 9303 Part 11 Passive Authentication에서 요구하는
 * CSCA 조회 기능을 정의합니다.</p>
 *
 * @since Phase 4.11.4
 */
public interface LdapCscaRepository {

    /**
     * Subject DN으로 CSCA 인증서를 조회합니다.
     *
     * @param subjectDn CSCA Subject DN (예: "CN=CSCA003,OU=MOFA,O=Government,C=KR")
     * @return CSCA X.509 인증서 (존재하지 않으면 Optional.empty())
     */
    Optional<X509Certificate> findBySubjectDn(String subjectDn);
}
```

**Location**: `passiveauthentication/domain/port/`

---

### Task 4: Implement UnboundIdLdapCscaAdapter
**Status**: Pending
**Priority**: High

**File**: `src/main/java/com/smartcoreinc/localpkd/passiveauthentication/infrastructure/adapter/UnboundIdLdapCscaAdapter.java`

**Implementation Details**:
- LDAP Base DN: `dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com`
- Search Filter: `(&(objectClass=pkdDownload)(o=csca)(cn={escaped-dn}))`
- Attribute: `userCertificate;binary`
- DN Escaping: RFC 4514 (`,` → `\2C`, `=` → `\3D`)

**Dependencies**:
- Reuse `UnboundIdLdapConnectionAdapter` (already exists)
- `LDAPConnectionFactory` from existing config

**Key Methods**:
```java
@Component
public class UnboundIdLdapCscaAdapter implements LdapCscaRepository {

    private final UnboundIdLdapConnectionAdapter ldapConnection;

    @Override
    public Optional<X509Certificate> findBySubjectDn(String subjectDn) {
        String escapedDn = escapeDn(subjectDn);
        String filter = String.format("(&(objectClass=pkdDownload)(o=csca)(cn=%s))", escapedDn);

        SearchRequest searchRequest = new SearchRequest(
            "dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com",
            SearchScope.SUB,
            filter,
            "userCertificate;binary"
        );

        SearchResult result = ldapConnection.search(searchRequest);
        // Parse and return X509Certificate
    }

    private String escapeDn(String dn) {
        // RFC 4514 escaping: , → \2C, = → \3D, etc.
    }
}
```

---

### Task 5: Refactor PerformPassiveAuthenticationUseCase
**Status**: Pending
**Priority**: High

**File**: `src/main/java/com/smartcoreinc/localpkd/passiveauthentication/application/usecase/PerformPassiveAuthenticationUseCase.java`

**Changes**:

**Before**:
```java
private final CertificateRepository certificateRepository;

private Certificate retrieveCscaByIssuerDn(String issuerDn) {
    Optional<Certificate> csca = certificateRepository.findBySubjectDn(issuerDn)
        .stream()
        .filter(cert -> cert.getCertificateType() == CertificateType.CSCA)
        .findFirst();
    return csca.orElseThrow(...);
}
```

**After**:
```java
private final LdapCscaRepository ldapCscaRepository;

private X509Certificate retrieveCscaFromLdap(String issuerDn) {
    log.debug("Looking up CSCA from LDAP with DN: {}", issuerDn);

    // Try exact match
    Optional<X509Certificate> csca = ldapCscaRepository.findBySubjectDn(issuerDn);

    if (csca.isPresent()) {
        return csca.get();
    }

    // Try normalized DN (RFC 2253)
    String normalizedDn = normalizeDn(issuerDn);
    csca = ldapCscaRepository.findBySubjectDn(normalizedDn);

    return csca.orElseThrow(() ->
        new PassiveAuthenticationException("CSCA_NOT_FOUND",
            "CSCA not found in LDAP: " + issuerDn)
    );
}
```

**DN Normalization**: Keep existing logic from Phase 4.11.3

---

### Task 6: Remove CertificateRepository dependency
**Status**: Pending
**Priority**: Medium

**Changes**:
1. Remove `CertificateRepository` from UseCase constructor
2. Remove all references to `certificate` table in PA module
3. PA should only access:
   - `passport_data` (검증된 여권 데이터)
   - `passive_authentication_audit_log` (감사 로그)

---

### Task 7: Update PassiveAuthenticationControllerTest
**Status**: Pending
**Priority**: High

**Test Strategy**: LDAP Integration Test

**Configuration**:
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PassiveAuthenticationControllerTest {

    @Autowired
    private LdapCscaRepository ldapCscaRepository;

    @Test
    void shouldVerifyValidPassport() {
        // Given: SOD with DSC signed by CSCA003
        // When: Perform PA
        // Then: CSCA003 should be found in LDAP
        //       Trust chain should be verified
        //       Result should be VALID
    }
}
```

**Remove**:
- DBMS test data setup
- `data.sql` dependencies
- Certificate entity mocking

---

### Task 8: Configure LDAP connection for test profile
**Status**: Pending
**Priority**: High

**File**: `src/test/resources/application-test.properties`

**Add**:
```properties
# LDAP Configuration for PA Tests
ldap.url=ldap://192.168.100.10:389
ldap.base-dn=dc=ldap,dc=smartcoreinc,dc=com
ldap.pkd-base-dn=dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
ldap.username=cn=admin,dc=ldap,dc=smartcoreinc,dc=com
ldap.password=core
ldap.pool-size=5
ldap.connection-timeout-ms=5000
```

**Verify**: LDAP connection works from test environment

---

### Task 9: Run tests and verify CSCA lookup succeeds
**Status**: Pending
**Priority**: High

**Test Execution**:
```bash
./mvnw test -Dtest=PassiveAuthenticationControllerTest
```

**Expected Results**:
- ✅ CSCA003 found in LDAP
- ✅ Trust chain verified
- ✅ `shouldVerifyValidPassport` test passes
- ✅ Test count: 12/20 passing (target: +1 from current 11/20)

**Debug Output**:
```
DEBUG [PA UseCase] Looking up CSCA from LDAP with DN: CN=CSCA003,OU=MOFA,O=Government,C=KR
DEBUG [LDAP Adapter] Searching LDAP with filter: (&(objectClass=pkdDownload)(o=csca)(cn=CN\3DCSCA003\2COU\3DMOFA\2CO\3DGovernment\2CC\3DKR))
DEBUG [LDAP Adapter] Found 1 entry
DEBUG [PA UseCase] CSCA retrieved successfully
DEBUG [PA Service] Verifying DSC trust chain with CSCA public key
INFO  [PA UseCase] Passive Authentication completed: VALID
```

---

### Task 10: Document architecture decision
**Status**: Pending
**Priority**: Medium

**File**: `docs/ARCHITECTURE_PA_LDAP_VS_DBMS.md`

**Content**:
```markdown
# PA Module Architecture Decision: LDAP vs DBMS

## Decision
PA (Passive Authentication) Module uses **LDAP only** for CSCA lookup, not DBMS.

## Rationale

### ICAO 9303 Standard
- ICAO 9303 Part 11 requires CSCA lookup from PKD (Public Key Directory)
- PKD is implemented as OpenLDAP server
- Real-time verification needs live certificate data

### Bounded Context Separation
- **PKD Upload Module**: DBMS for upload history/statistics
- **PA Module**: LDAP for real-time certificate validation

### Data Flow
PKD Upload → Parse → Validate → LDAP Store
                                    ↓
PA Verification ← LDAP Lookup ← DSC Issuer DN

## Implementation
- Port: `LdapCscaRepository`
- Adapter: `UnboundIdLdapCscaAdapter`
- UseCase: `PerformPassiveAuthenticationUseCase`

## Alternatives Considered
1. ❌ DBMS `certificate` table: Violates DDD bounded context
2. ❌ Dual lookup (LDAP + DBMS): Unnecessary complexity
3. ✅ LDAP only: Follows ICAO standard, clear separation

## Date
2025-12-19
```

---

## 🎯 Success Criteria

- [ ] All test data files deleted
- [ ] CSCA003 verified in LDAP
- [ ] `LdapCscaRepository` interface created
- [ ] `UnboundIdLdapCscaAdapter` implemented
- [ ] UseCase refactored to use LDAP
- [ ] `CertificateRepository` removed from PA
- [ ] Controller test updated
- [ ] LDAP config added to test profile
- [ ] Tests run successfully (12/20 passing)
- [ ] Architecture documented

---

## 📊 Progress

**Total Tasks**: 10
**Completed**: 0
**In Progress**: 0
**Pending**: 10

**Estimated Time**: 3-4 hours
**Target Completion**: 2025-12-19

---

## 🔗 Related Documents

- [CLAUDE.md - ICAO 9303 PA Workflow](../CLAUDE.md#icao-9303-passive-authentication-workflow)
- [Phase 4.11.3 Session Report](./SESSION_2025-12-19_PA_PHASE_4_11_3_SOD_DSC_EXTRACTION.md)
- [PKD Upload Module Architecture](./MASTER_LIST_LDAP_STORAGE_ANALYSIS.md)
