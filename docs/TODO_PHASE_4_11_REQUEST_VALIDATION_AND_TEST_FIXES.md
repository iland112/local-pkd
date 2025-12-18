# Phase 4.11: Request Validation & Functional Test Fixes

**Status**: Ready to Start
**Priority**: High
**Estimated Time**: 2-3 hours
**Date**: 2025-12-18

---

## 📋 Phase Overview

Phase 4.10에서 테스트 인프라를 완성했고, 이제 20개 테스트 중 13개의 functional failures를 수정해야 합니다.

**Current Test Status**:
- ✅ Tests run: 20
- ✅ Errors: 0 (infrastructure완료!)
- ⏳ Failures: 13 (functional issues)

**Root Causes of Failures**:
1. DSC certificates not found in H2 test database
2. Request validation not implemented (@Valid)
3. Test data mismatches
4. Missing Country Code support (alpha-3)

---

## 🎯 Phase Objectives

### 1. Fix Test Data Issues (Priority 1)
**Time**: 1 hour

#### 1.1 Complete Test Certificate Creation
**File**: `PassiveAuthenticationControllerTest.java`

**Current Issue**:
- CSCA와 DSC를 생성하지만 H2 database에서 조회가 안됨
- `certificateRepository.findAllByType()` 실패

**Fix Required**:
```java
// Current implementation extracts real DSC from SOD
// But certificates may not be persisted correctly
// Check:
// 1. Transaction management (@Transactional)
// 2. Certificate IDs (UUID conflicts?)
// 3. Repository save() actually persisting?
```

**Action Items**:
- [ ] Debug why certificates are not persisted in setUp()
- [ ] Verify `@Transactional` scope
- [ ] Check if `certificateRepository.save()` is working
- [ ] Add assertions after save to verify persistence

#### 1.2 Verify Test Fixture Files Exist
**Files**: `src/test/resources/passport-fixtures/valid-korean-passport/`
- [ ] sod.bin (1857 bytes)
- [ ] dg1.bin
- [ ] dg2.bin
- [ ] dg14.bin

**If missing**: Re-extract from Phase 4.9 documentation or use dummy data.

---

### 2. Implement Request Validation (Priority 2)
**Time**: 30 minutes

#### 2.1 Add Bean Validation Annotations
**File**: `PassiveAuthenticationRequest.java`

```java
public record PassiveAuthenticationRequest(
    @NotBlank(message = "발급 국가 코드는 필수입니다")
    @Pattern(regexp = "^[A-Z]{3}$", message = "국가 코드는 ISO 3166-1 alpha-3 형식이어야 합니다 (예: KOR)")
    String issuingCountry,

    @NotBlank(message = "여권 번호는 필수입니다")
    String documentNumber,

    @NotBlank(message = "SOD는 필수입니다")
    String sod,  // Base64-encoded

    @NotNull(message = "Data Groups는 필수입니다")
    @Size(min = 1, message = "최소 하나의 Data Group이 필요합니다")
    Map<@Pattern(regexp = "^DG([1-9]|1[0-6])$", message = "Invalid Data Group key") String,
        @NotBlank(message = "Data Group 값은 필수입니다") String> dataGroups,

    String clientId  // Optional
) {}
```

#### 2.2 Enable Validation in Controller
**File**: `PassiveAuthenticationController.java`

```java
@PostMapping("/verify")
public ResponseEntity<PassiveAuthenticationResponse> verify(
    @Valid @RequestBody PassiveAuthenticationRequest request,  // Add @Valid
    HttpServletRequest httpRequest
) {
    // ...
}
```

#### 2.3 Add Validation Exception Handler
**File**: `GlobalExceptionHandler.java` (if exists) or create new

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
    Map<String, String> errors = ex.getBindingResult()
        .getFieldErrors()
        .stream()
        .collect(Collectors.toMap(
            FieldError::getField,
            error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value"
        ));

    return ResponseEntity.badRequest().body(
        new ErrorResponse("VALIDATION_ERROR", "Request validation failed", errors)
    );
}
```

**Test Coverage**:
- [ ] `shouldRejectMissingRequiredField()` - 4 tests
- [ ] `shouldRejectInvalidCountryCode()` - country code validation
- [ ] `shouldRejectInvalidDataGroupKey()` - DG key format
- [ ] `shouldRejectEmptyDataGroups()` - empty map validation

---

### 3. Fix Remaining Functional Test Failures (Priority 3)
**Time**: 1 hour

#### 3.1 Trust Chain Verification Tests
**Expected Failures**: 7 tests

**Root Cause**: DSC not found in LDAP (H2 database)

**Fix Strategy**:
1. Verify certificates are persisted in `setUp()`
2. Add debug logging to check certificate count
3. Ensure CSCA Subject DN matches DSC Issuer DN
4. Check country code extraction from DN

```java
@BeforeEach
void setUp() throws Exception {
    // ... create certificates ...

    // DEBUG: Verify persistence
    List<Certificate> allCerts = certificateRepository.findAll();
    System.out.println("DEBUG: Persisted certificates: " + allCerts.size());

    allCerts.forEach(cert -> {
        System.out.println("  - Type: " + cert.getCertificateType());
        System.out.println("    Subject: " + cert.getSubjectInfo().getDistinguishedName());
        System.out.println("    Serial: " + cert.getX509Data().getSerialNumber());
    });
}
```

#### 3.2 SOD Signature Verification Tests
**Expected Failures**: 2 tests

**Root Cause**: DSC public key mismatch

**Fix**: Ensure DSC public key matches the one in SOD

#### 3.3 Error Handling Tests
**Expected Failures**: 2 tests

**Tests**:
- `shouldReturn400ForMalformedJson()` - should pass after validation
- `shouldReturn400ForInvalidBase64()` - needs Base64 validation

#### 3.4 Client Metadata Extraction Tests
**Expected Failures**: 2 tests

**Tests**:
- `shouldExtractClientIpFromXForwardedFor()`
- `shouldExtractUserAgent()`

**Note**: These may already pass, just need verification.

---

## 🔍 Debugging Strategy

### Step 1: Understand Current Failures
```bash
./mvnw test -Dtest=PassiveAuthenticationControllerTest 2>&1 | \
  grep -E "(shouldVerify|shouldReturn|shouldReject|shouldExtract)" | \
  grep ERROR
```

### Step 2: Run Individual Test
```bash
./mvnw test -Dtest=PassiveAuthenticationControllerTest#shouldVerifyValidPassport
```

### Step 3: Check Surefire Reports
```bash
cat target/surefire-reports/com.smartcoreinc.localpkd.passiveauthentication.infrastructure.web.PassiveAuthenticationControllerTest.txt
```

### Step 4: Enable Debug Logging
**File**: `application-test.properties`
```properties
logging.level.com.smartcoreinc.localpkd.passiveauthentication=DEBUG
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.springframework.transaction=DEBUG
```

---

## 📊 Success Criteria

### Test Results Goal
```
Tests run: 20
Failures: 0
Errors: 0
Skipped: 0
```

### Coverage Requirements
- ✅ All 7 Controller Endpoint Tests pass
- ✅ All 4 Request Validation Tests pass
- ✅ All 2 Response Format Tests pass
- ✅ All 5 Error Handling Tests pass
- ✅ All 3 Client Metadata Tests pass (or skip if not critical)

---

## 📝 Implementation Checklist

### Phase 4.11 Tasks
- [ ] **Task 1**: Debug certificate persistence in setUp()
  - [ ] Add debug logging
  - [ ] Verify @Transactional scope
  - [ ] Check repository.save() behavior
  - [ ] Verify findAllByType() returns data

- [ ] **Task 2**: Implement Request Validation
  - [ ] Add @Valid annotations
  - [ ] Add Bean Validation constraints
  - [ ] Create ValidationExceptionHandler
  - [ ] Test 4 validation scenarios

- [ ] **Task 3**: Fix Trust Chain Tests (7 tests)
  - [ ] Ensure CSCA/DSC match
  - [ ] Verify Subject DN → Issuer DN linkage
  - [ ] Check country code extraction

- [ ] **Task 4**: Fix SOD Signature Tests (2 tests)
  - [ ] Verify DSC public key correctness
  - [ ] Check signature algorithm match

- [ ] **Task 5**: Fix Error Handling Tests (2 tests)
  - [ ] Malformed JSON handling
  - [ ] Invalid Base64 handling

- [ ] **Task 6**: Verify Client Metadata Tests (3 tests)
  - [ ] X-Forwarded-For extraction
  - [ ] User-Agent extraction
  - [ ] Missing User-Agent handling

- [ ] **Task 7**: Run Full Test Suite
  ```bash
  ./mvnw test -Dtest=PassiveAuthenticationControllerTest
  ```

- [ ] **Task 8**: Create Phase 4.11 Completion Report
  - [ ] Test results summary
  - [ ] Code changes summary
  - [ ] Lessons learned

---

## 🔗 Related Files

### Production Code
- `PassiveAuthenticationRequest.java` - Add validation annotations
- `PassiveAuthenticationController.java` - Add @Valid
- `GlobalExceptionHandler.java` - Validation error handling
- `Certificate.java` - createForTest() method (already modified)

### Test Code
- `PassiveAuthenticationControllerTest.java` - Main test file
- `ExtractDscFromSodTest.java` - DSC extraction utility
- `application-test.properties` - Test configuration
- `schema-h2.sql` - H2 schema overrides

### Documentation
- `TODO_PHASE_4_10_LDAP_TEST_FIXTURES.md` - Previous phase
- `SESSION_2025-12-18_PA_PHASE_4_10.md` - Session report (to be created)

---

## 🚀 Quick Start Commands

```bash
# Run PA Controller tests
./mvnw test -Dtest=PassiveAuthenticationControllerTest

# Run single test
./mvnw test -Dtest=PassiveAuthenticationControllerTest#shouldVerifyValidPassport

# Check test reports
cat target/surefire-reports/com.smartcoreinc.localpkd.passiveauthentication.infrastructure.web.PassiveAuthenticationControllerTest.txt

# Clean build
./mvnw clean test -Dtest=PassiveAuthenticationControllerTest
```

---

## 📌 Notes

### Phase 4.10 Achievements Recap
- ✅ H2 JSONB compatibility resolved
- ✅ 5 test compilation errors fixed
- ✅ Real DSC extraction from SOD implemented
- ✅ Certificate.createForTest() enhanced
- ✅ Test infrastructure complete (0 errors!)

### Phase 4.11 Focus
- Fix functional test failures (13 → 0)
- Implement request validation
- Achieve 100% test pass rate
- Complete Phase 4 of PA implementation

### Next Phases After 4.11
- **Phase 4.12**: Performance Testing & Optimization
- **Phase 4.13**: Integration with PKD Upload Module
- **Phase 5**: UI Implementation
- **Phase 6**: Production Deployment

---

**Created**: 2025-12-18
**Author**: Claude Sonnet 4.5
**Session**: Phase 4.10 → 4.11 Transition
