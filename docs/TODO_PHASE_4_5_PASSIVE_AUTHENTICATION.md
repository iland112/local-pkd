# TODO: Passive Authentication Phase 4.5 - UseCase Integration Tests

**Created**: 2025-12-17
**Priority**: HIGH
**Status**: Ready to Start
**Previous Phase**: Phase 4.4 (LDAP Integration Tests) ✅ COMPLETED

---

## Overview

Phase 4.5에서는 Passive Authentication의 핵심 검증 로직을 통합 테스트합니다. Phase 4.4에서 LDAP 데이터 조회가 성공적으로 검증되었으므로, 이제 실제 여권 검증 흐름을 테스트할 차례입니다.

---

## Prerequisites (Already Completed ✅)

- ✅ LDAP 서버에 테스트 데이터 업로드 완료 (KR: CSCA 7개, DSC 216개, CRL 1개)
- ✅ LDAP 연결 및 조회 기능 검증 완료 (LdapCertificateRetrievalIntegrationTest)
- ✅ Domain 모델 구현 완료 (PassportData, DataGroup, SecurityObject 등)
- ✅ Repository 계층 구현 완료 (JpaPassportDataRepository, JpaPassiveAuthenticationAuditLogRepository)

---

## Phase 4.5 Tasks

### 1. Trust Chain Verification Integration Test

**File**: `src/test/java/com/smartcoreinc/localpkd/passiveauthentication/integration/TrustChainVerificationIntegrationTest.java`

**Test Scenarios**:

#### 1.1 Valid DSC Signed by Valid CSCA
```java
@Test
void shouldVerifyValidDscSignedByValidCsca() throws Exception {
    // Given: Valid DSC from LDAP (Korea)
    // Given: Corresponding CSCA from LDAP
    // When: Verify trust chain
    // Then: Verification should succeed
    // Then: Audit log should record SUCCESS
}
```

#### 1.2 DSC with Missing CSCA
```java
@Test
void shouldFailWhenCscaNotFound() throws Exception {
    // Given: DSC with issuer DN that doesn't exist in LDAP
    // When: Verify trust chain
    // Then: Verification should fail with CHAIN_INCOMPLETE error
    // Then: Audit log should record FAILED with error details
}
```

#### 1.3 DSC with Invalid Signature
```java
@Test
void shouldFailWhenDscSignatureInvalid() throws Exception {
    // Given: DSC with corrupted signature
    // Given: Valid CSCA from LDAP
    // When: Verify trust chain
    // Then: Verification should fail with SIGNATURE_INVALID error
}
```

#### 1.4 Expired DSC with Valid CSCA
```java
@Test
void shouldFailWhenDscExpired() throws Exception {
    // Given: Expired DSC from LDAP (filter: description=EXPIRED*)
    // Given: Valid CSCA from LDAP
    // When: Verify trust chain
    // Then: Verification should fail with EXPIRED status
}
```

**Expected Components to Test**:
- `CertificateRepository.findBySubjectDn()` - CSCA lookup by issuer DN
- Bouncy Castle signature verification
- Validity period checks
- Audit log creation

---

### 2. Security Object (SOD) Verification Integration Test

**File**: `src/test/java/com/smartcoreinc/localpkd/passiveauthentication/integration/SodVerificationIntegrationTest.java`

**Test Scenarios**:

#### 2.1 Valid SOD Signed by Valid DSC
```java
@Test
void shouldVerifyValidSodSignedByValidDsc() throws Exception {
    // Given: PassportData with valid SOD (CMS SignedData)
    // Given: Valid DSC from LDAP that signed the SOD
    // When: Verify SOD signature
    // Then: Verification should succeed
    // Then: Audit log should record SOD_VERIFICATION_SUCCESS
}
```

#### 2.2 SOD with Missing DSC
```java
@Test
void shouldFailWhenDscNotFoundForSod() throws Exception {
    // Given: PassportData with SOD
    // Given: DSC issuer not in LDAP
    // When: Verify SOD signature
    // Then: Verification should fail with DSC_NOT_FOUND error
}
```

#### 2.3 SOD with Invalid Signature
```java
@Test
void shouldFailWhenSodSignatureInvalid() throws Exception {
    // Given: PassportData with corrupted SOD signature
    // Given: Valid DSC from LDAP
    // When: Verify SOD signature
    // Then: Verification should fail with SOD_SIGNATURE_INVALID
}
```

**Expected Components to Test**:
- `SecurityObject.verifySignature()` - SOD signature verification
- CMS SignedData parsing (Bouncy Castle CMSSignedData)
- Signer certificate extraction
- Audit log with SOD verification details

---

### 3. Data Group Hash Verification Integration Test

**File**: `src/test/java/com/smartcoreinc/localpkd/passiveauthentication/integration/DataGroupHashVerificationIntegrationTest.java`

**Test Scenarios**:

#### 3.1 Valid Data Group Hashes
```java
@Test
void shouldVerifyDataGroupHashesMatch() throws Exception {
    // Given: PassportData with DG1, DG2, DG14
    // Given: SOD with expected hashes for DG1, DG2, DG14
    // When: Compute SHA-256 hashes of data groups
    // When: Compare with hashes in SOD
    // Then: All hashes should match
    // Then: Audit log should record DG_HASH_VERIFICATION_SUCCESS
}
```

#### 3.2 Tampered Data Group
```java
@Test
void shouldFailWhenDataGroupTampered() throws Exception {
    // Given: PassportData with modified DG1 content
    // Given: SOD with original DG1 hash
    // When: Compute SHA-256 hash of modified DG1
    // When: Compare with hash in SOD
    // Then: Hash mismatch should be detected
    // Then: Audit log should record DG_HASH_MISMATCH with details
}
```

#### 3.3 Missing Data Group
```java
@Test
void shouldFailWhenRequiredDataGroupMissing() throws Exception {
    // Given: PassportData without DG1 (required)
    // Given: SOD expects DG1
    // When: Verify data group hashes
    // Then: Verification should fail with MISSING_DATA_GROUP error
}
```

**Expected Components to Test**:
- `DataGroup.computeHash()` - SHA-256 hash computation
- `SecurityObject.getHashAlgorithm()` - Hash algorithm identification
- `SecurityObject.getDataGroupHashes()` - Expected hash extraction
- Hash comparison logic

---

### 4. CRL Check Integration Test

**File**: `src/test/java/com/smartcoreinc/localpkd/passiveauthentication/integration/CrlCheckIntegrationTest.java`

**Test Scenarios**:

#### 4.1 Certificate Not Revoked
```java
@Test
void shouldPassWhenCertificateNotRevoked() throws Exception {
    // Given: Valid DSC from LDAP
    // Given: CRL from LDAP for DSC's issuer
    // When: Check if DSC is in CRL
    // Then: DSC should not be in revoked list
    // Then: Audit log should record CRL_CHECK_PASSED
}
```

#### 4.2 Certificate Revoked
```java
@Test
void shouldFailWhenCertificateRevoked() throws Exception {
    // Given: Revoked DSC serial number
    // Given: CRL from LDAP containing the DSC serial
    // When: Check if DSC is in CRL
    // Then: DSC should be found in revoked list
    // Then: Audit log should record CERTIFICATE_REVOKED with revocation date
}
```

#### 4.3 CRL Not Available
```java
@Test
void shouldHandleWhenCrlNotAvailable() throws Exception {
    // Given: DSC with issuer that has no CRL in LDAP
    // When: Check revocation status
    // Then: Should handle gracefully (configurable: fail or warn)
    // Then: Audit log should record CRL_NOT_AVAILABLE
}
```

**Expected Components to Test**:
- `CertificateRevocationListRepository.findByIssuerName()` - CRL lookup
- CRL parsing (Bouncy Castle X509CRL)
- Revoked certificate list extraction
- Revocation date extraction

---

### 5. Complete Passive Authentication Flow Integration Test

**File**: `src/test/java/com/smartcoreinc/localpkd/passiveauthentication/integration/PassiveAuthenticationUseCaseIntegrationTest.java`

**Test Scenarios**:

#### 5.1 Valid Passport - Full PA Success
```java
@Test
void shouldSuccessfullyVerifyValidPassport() throws Exception {
    // Given: PassportData with all required data groups (DG1, DG2, DG14, SOD)
    // Given: Valid DSC and CSCA in LDAP
    // Given: CRL available and certificate not revoked
    // When: Execute PerformPassiveAuthenticationUseCase
    // Then: Overall verification result = SUCCESS
    // Then: Trust chain verification = PASSED
    // Then: SOD signature verification = PASSED
    // Then: Data group hash verification = PASSED
    // Then: CRL check = PASSED
    // Then: Multiple audit log entries created with complete trace
}
```

#### 5.2 Invalid Passport - Trust Chain Failure
```java
@Test
void shouldFailWhenTrustChainInvalid() throws Exception {
    // Given: PassportData with valid structure
    // Given: DSC signed by unknown CSCA (not in LDAP)
    // When: Execute PerformPassiveAuthenticationUseCase
    // Then: Overall verification result = FAILED
    // Then: Failure reason = TRUST_CHAIN_INVALID
    // Then: Audit log shows step-by-step verification trace
}
```

#### 5.3 Invalid Passport - Data Tampering
```java
@Test
void shouldFailWhenDataGroupTampered() throws Exception {
    // Given: PassportData with tampered DG1 (MRZ modified)
    // Given: Valid DSC and CSCA in LDAP
    // When: Execute PerformPassiveAuthenticationUseCase
    // Then: Overall verification result = FAILED
    // Then: Failure reason = DATA_GROUP_HASH_MISMATCH
    // Then: Specific data group identified (DG1)
}
```

#### 5.4 Invalid Passport - Revoked Certificate
```java
@Test
void shouldFailWhenCertificateRevoked() throws Exception {
    // Given: PassportData with valid structure and hashes
    // Given: Revoked DSC in CRL
    // When: Execute PerformPassiveAuthenticationUseCase
    // Then: Overall verification result = FAILED
    // Then: Failure reason = CERTIFICATE_REVOKED
    // Then: Revocation date and reason logged
}
```

**Expected Components to Test**:
- `PerformPassiveAuthenticationUseCase.execute()` - Main PA orchestration
- All verification steps executed in correct order
- Comprehensive audit logging
- Transaction management (all audit logs persisted together)

---

## Test Data Requirements

### Sample Passport Data (Test Fixtures)

Create test fixtures in `src/test/resources/test-data/passports/`:

```
test-data/passports/
├── valid-passport-kr.json          # Valid Korean passport with all DGs
├── tampered-dg1-kr.json            # DG1 (MRZ) modified
├── tampered-dg2-kr.json            # DG2 (Photo) modified
├── invalid-sod-signature-kr.json   # Corrupted SOD signature
├── expired-dsc-kr.json             # Signed by expired DSC
├── unknown-issuer-kr.json          # Signed by unknown CSCA
└── revoked-cert-kr.json            # Signed by revoked DSC
```

Each JSON should contain:
```json
{
  "passportNumber": "M12345678",
  "issuingCountry": "KR",
  "dataGroups": {
    "DG1": "<base64-encoded-MRZ>",
    "DG2": "<base64-encoded-photo>",
    "DG14": "<base64-encoded-chip-auth>",
    "SOD": "<base64-encoded-CMS-signed-data>"
  }
}
```

### LDAP Test Data (Already Available ✅)

```
Korea (KR):
- CSCA: 7 certificates (4 VALID, 3 EXPIRED/INVALID)
- DSC: 216 certificates (102 VALID, 114 EXPIRED)
- CRL: 1 entry
```

---

## Implementation Order

### Recommended Sequence:

1. **Start Simple**: Trust Chain Verification (uses existing LDAP data)
2. **Add Complexity**: CRL Check (uses existing CRL data)
3. **Create Fixtures**: Prepare sample passport data (SOD, DGs)
4. **SOD Verification**: Test with sample SOD and real DSC from LDAP
5. **Data Group Hashing**: Test with sample DGs and SOD
6. **Complete Flow**: End-to-end PA verification

---

## Success Criteria

### All Tests Must:
- ✅ Use real LDAP data (CSCA, DSC, CRL from Phase 4.4)
- ✅ Create audit log entries for each verification step
- ✅ Verify audit log contents (step, status, timestamp, details)
- ✅ Handle errors gracefully with appropriate error codes
- ✅ Clean up test data (if any created) after each test
- ✅ Run independently without test order dependencies

### Test Coverage Target:
- **Trust Chain**: 4 scenarios
- **SOD Verification**: 3 scenarios
- **Data Group Hashing**: 3 scenarios
- **CRL Check**: 3 scenarios
- **Complete PA Flow**: 4 scenarios
- **Total**: 17 integration tests minimum

---

## Technical Notes

### Spring Boot Test Configuration

```java
@SpringBootTest
@ActiveProfiles("local")
@Transactional  // Rollback after each test
class PassiveAuthenticationUseCaseIntegrationTest {

    @Autowired
    private PerformPassiveAuthenticationUseCase performPaUseCase;

    @Autowired
    private PassportDataRepository passportDataRepository;

    @Autowired
    private PassiveAuthenticationAuditLogRepository auditLogRepository;

    @Autowired
    private UnboundIdLdapAdapter ldapAdapter;

    @BeforeEach
    void setUp() {
        // Verify LDAP connection
        assertThat(ldapAdapter.testConnection()).isTrue();
    }

    @AfterEach
    void tearDown() {
        // Clean up test data (PassportData, AuditLogs auto-rollback via @Transactional)
    }
}
```

### Bouncy Castle CMS Parsing Example

```java
// Parse SOD (CMS SignedData)
CMSSignedData cms = new CMSSignedData(sodBytes);
SignerInformationStore signers = cms.getSignerInfos();
SignerInformation signer = signers.getSigners().iterator().next();

// Extract signer certificate
X509CertificateHolder certHolder =
    (X509CertificateHolder) cms.getCertificates().getMatches(signer.getSID()).iterator().next();
X509Certificate signerCert = new JcaX509CertificateConverter().getCertificate(certHolder);

// Verify signature
boolean valid = signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(signerCert));
```

### Data Group Hash Computation

```java
// Compute SHA-256 hash of data group
MessageDigest digest = MessageDigest.getInstance("SHA-256");
byte[] hash = digest.digest(dataGroupBytes);

// Compare with expected hash from SOD
byte[] expectedHash = sod.getDataGroupHash(dataGroupNumber);
boolean matches = MessageDigest.isEqual(hash, expectedHash);
```

---

## Dependencies

### Maven Dependencies (Already in pom.xml ✅)

```xml
<!-- Bouncy Castle for CMS/X.509 -->
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.78</version>
</dependency>
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcpkix-jdk18on</artifactId>
    <version>1.78</version>
</dependency>

<!-- UnboundID LDAP SDK -->
<dependency>
    <groupId>com.unboundid</groupId>
    <artifactId>unboundid-ldapsdk</artifactId>
</dependency>

<!-- Test -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Reference Documents

- [ICAO 9303 Part 11](https://www.icao.int/publications/Documents/9303_p11_cons_en.pdf) - Passive Authentication Specification
- [Session 2025-12-17 Report](./SESSION_2025-12-17_PASSIVE_AUTHENTICATION_INTEGRATION_TESTS.md) - Phase 4.4 Results
- [CLAUDE.md](../CLAUDE.md) - Project coding standards and DDD patterns

---

## Next Phase (Phase 4.6)

After completing Phase 4.5, proceed to:

**Phase 4.6: REST API Controller Integration Tests**
- Create REST endpoints for PA verification
- Test request/response format (JSON)
- Validate input (passport data structure)
- Test error handling (400, 404, 500)
- Test concurrent requests
- Performance benchmarking

---

**Status**: ⏳ READY TO START
**Estimated Time**: 8-12 hours
**Complexity**: HIGH (Requires Bouncy Castle expertise + LDAP integration)
**Priority**: HIGH (Core feature of Passive Authentication)

---

**Created by**: Claude Code (Anthropic)
**Session ID**: 2025-12-17-passive-authentication-phase-4-5-planning
