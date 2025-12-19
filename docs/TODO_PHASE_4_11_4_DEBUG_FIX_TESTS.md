# TODO: Passive Authentication Phase 4.11.4 - Debug & Fix Remaining Tests

**Status**: 🚧 IN PROGRESS
**Priority**: HIGH
**Estimated Time**: 3-4 hours
**Previous Phase**: Phase 4.11.3 (SOD DSC Extraction) - COMPLETED

---

## Overview

Phase 4.11.4는 Trust Chain 검증 및 Response 구조 문제를 디버깅하고 수정하여 통과하는 테스트를 11/20 (55%)에서 14/20 (70%)로 향상시키는 것에 초점을 맞춥니다.

**Current Issues**:
- ✅ SOD parsing working correctly
- ✅ DSC extraction from SOD working
- ❌ Trust chain verification failing (ERROR instead of VALID)
- ❌ Response structure incomplete (missing certificateChainValidation)
- ❌ 404 handling not working

---

## Current Test Status

### Passing Tests (11/20) ✅

**Category 1: Endpoint Tests (4 tests)**
1. ✅ `shouldPerformPassiveAuthentication` - POST /api/passive-authentication works
2. ✅ `shouldReturnVerificationHistory` - GET /api/passive-authentication/history works
3. ✅ `shouldAcceptPassportMetadata` - Metadata in request body accepted
4. ✅ `shouldReturn404WhenCountryCodeNotFound` - Invalid country code → 404

**Category 2: Request Validation (4 tests)**
5. ✅ `shouldReturn400ForMissingSodBytes` - Missing SOD → 400
6. ✅ `shouldReturn400ForMissingDataGroups` - Missing DG → 400
7. ✅ `shouldReturn400ForInvalidCountryCode` - Invalid country code → 400
8. ✅ `shouldReturn400ForInvalidBase64` - Invalid Base64 → 400

**Category 3: Client Metadata (3 tests)**
9. ✅ `shouldExtractClientIpAddress` - IP address extracted
10. ✅ `shouldExtractUserAgent` - User-Agent extracted
11. ✅ `shouldHandleMissingMetadata` - Missing metadata handled

---

### Failing Tests (9/20) ❌

**Category 1: Trust Chain Issues (3 tests)** ⚠️ HIGH PRIORITY
1. ❌ `shouldVerifyValidPassport` - Returns ERROR instead of VALID
2. ❌ `shouldReturnInvalidStatusForTamperedPassport` - Returns ERROR instead of INVALID
3. ❌ `shouldReturnCorrectJsonStructure` - Missing `certificateChainValidation` field

**Category 2: 404 Handling (2 tests)** ⚠️ MEDIUM PRIORITY
4. ❌ `shouldReturn404WhenDscNotFound` - Returns 200 instead of 404
5. ❌ `shouldReturnVerificationById` - Returns 404 instead of 200

**Category 3: Pagination (3 tests)** ⏳ LOW PRIORITY (Deferred to Phase 4.12)
6. ❌ `shouldReturnPaginatedHistory` - Missing pagination structure
7. ❌ `shouldFilterByCountry` - Missing pagination structure
8. ❌ `shouldFilterByStatus` - Missing pagination structure

**Category 4: Error Handling (1 test)** ⚠️ MEDIUM PRIORITY
9. ❌ `shouldReturn400ForInvalidUuidFormat` - Returns 500 instead of 400

---

## Tasks

### Task 1: Debug Trust Chain Verification ⚠️ HIGH PRIORITY

**Goal**: Fix `shouldVerifyValidPassport` test

**Issue**: Returns `status = ERROR` instead of `VALID`

**Hypothesis**:
1. CSCA lookup by issuer DN failing (DN format mismatch)
2. Trust chain signature verification failing
3. Exception being caught and converted to ERROR status

**Actions**:

#### 1.1 Add Comprehensive Logging

**File**: `PerformPassiveAuthenticationUseCase.java`
**Method**: `execute()`, `validateCertificateChainWithX509Dsc()`

```java
// Add logging at each step
log.info("=== Passive Authentication Started ===");
log.debug("1. Extracting DSC from SOD...");
java.security.cert.X509Certificate dscX509 = sodParser.extractDscCertificate(command.sodBytes());
log.info("Extracted DSC: Subject={}, Serial={}",
    dscX509.getSubjectX500Principal().getName(),
    dscX509.getSerialNumber().toString(16)
);

log.debug("2. Extracting CSCA DN from DSC issuer...");
String cscaDn = dscX509.getIssuerX500Principal().getName();
log.info("DSC Issuer DN: {}", cscaDn);

log.debug("3. Retrieving CSCA from LDAP...");
Certificate csca = retrieveCscaByIssuerDn(cscaDn);
log.info("Retrieved CSCA: Subject={}, Serial={}",
    csca.getSubjectInfo().getDistinguishedName(),
    csca.getSerialNumber()
);

log.debug("4. Validating certificate chain...");
CertificateChainValidationDto chainValidation = validateCertificateChainWithX509Dsc(...);
log.info("Chain validation result: valid={}", chainValidation.valid());
```

#### 1.2 Verify DN Format Consistency

**Problem**: DSC issuer DN may have different format than CSCA subject DN

**Example**:
- DSC Issuer: `C=KR,O=Government of Korea,CN=CSCA003`
- CSCA Subject: `CN=CSCA003,O=Government of Korea,C=KR`

**Solution**: Normalize DNs before comparison

**File**: `PerformPassiveAuthenticationUseCase.java`

```java
private Certificate retrieveCscaByIssuerDn(String issuerDn) {
    // Normalize DN format (RFC 2253)
    X500Principal issuerPrincipal = new X500Principal(issuerDn);
    String normalizedDn = issuerPrincipal.getName(X500Principal.RFC2253);

    log.debug("Looking up CSCA with normalized DN: {}", normalizedDn);

    // Try multiple DN formats
    Optional<Certificate> csca = certificateRepository.findBySubjectDnIgnoreCase(normalizedDn);

    if (csca.isEmpty()) {
        // Try reverse order
        String reverseDn = issuerPrincipal.getName(X500Principal.CANONICAL);
        log.debug("Trying reverse DN format: {}", reverseDn);
        csca = certificateRepository.findBySubjectDnIgnoreCase(reverseDn);
    }

    return csca.orElseThrow(() -> new PassiveAuthenticationBusinessException(
        "CSCA_NOT_FOUND",
        String.format("CSCA not found for issuer DN: %s", issuerDn)
    ));
}
```

#### 1.3 Fix Trust Chain Validation

**File**: `PerformPassiveAuthenticationUseCase.java`
**Method**: `validateCertificateChainWithX509Dsc()`

**Current Issue**: May be throwing exception instead of returning validation DTO

```java
private CertificateChainValidationDto validateCertificateChainWithX509Dsc(
    java.security.cert.X509Certificate dscX509,
    Certificate csca,
    String issuingCountry,
    List<String> errors
) {
    boolean chainValid = false;
    boolean crlCheckPassed = true;
    String chainErrorMessage = null;

    try {
        log.debug("Verifying DSC signature with CSCA public key...");

        // Convert CSCA certificate bytes to X509Certificate
        java.security.cert.CertificateFactory cf =
            java.security.cert.CertificateFactory.getInstance("X.509");
        java.security.cert.Certificate cscaCert = cf.generateCertificate(
            new java.io.ByteArrayInputStream(csca.getX509Data().getCertificateBinary())
        );
        java.security.cert.X509Certificate cscaX509 =
            (java.security.cert.X509Certificate) cscaCert;

        // Verify DSC signature with CSCA public key
        dscX509.verify(cscaX509.getPublicKey());
        chainValid = true;
        log.info("Trust chain validation PASSED");

    } catch (Exception e) {
        chainValid = false;
        chainErrorMessage = "Trust chain verification failed: " + e.getMessage();
        errors.add(chainErrorMessage);
        log.error("Trust chain validation FAILED: {}", e.getMessage(), e);
    }

    // IMPORTANT: Always return DTO, never throw exception
    return new CertificateChainValidationDto(
        chainValid,
        csca.getSubjectInfo().getDistinguishedName(),
        csca.getSerialNumber(),
        crlCheckPassed,
        chainErrorMessage
    );
}
```

#### 1.4 Check Response Mapping

**File**: `PerformPassiveAuthenticationUseCase.java`
**Method**: `execute()`

**Ensure all DTOs are included in final response**:

```java
// Build final response
return new PassiveAuthenticationResponseDto(
    verificationId,
    VerificationStatus.VALID, // or INVALID/ERROR
    chainValidation,  // ❗ Ensure this is non-null
    sodValidation,    // ❗ Ensure this is non-null
    dataGroupValidation,  // ❗ Ensure this is non-null
    null, // crlCheckDto (optional)
    command.passportMetadata()
);
```

---

### Task 2: Fix Response Structure ⚠️ HIGH PRIORITY

**Goal**: Fix `shouldReturnCorrectJsonStructure` test

**Issue**: Missing `certificateChainValidation` field in JSON response

**Actions**:

#### 2.1 Verify DTO Serialization

**File**: `PassiveAuthenticationResponseDto.java`

```java
public record PassiveAuthenticationResponseDto(
    @JsonProperty("verificationId") UUID verificationId,
    @JsonProperty("verificationStatus") VerificationStatus verificationStatus,
    @JsonProperty("certificateChainValidation") CertificateChainValidationDto certificateChainValidation, // ❗ Check this
    @JsonProperty("sodSignatureValidation") SodSignatureValidationDto sodSignatureValidation,
    @JsonProperty("dataGroupHashValidation") DataGroupHashValidationDto dataGroupHashValidation,
    @JsonProperty("crlCheck") CrlCheckDto crlCheck,
    @JsonProperty("passportMetadata") PassportMetadata passportMetadata
) {
    // ❗ Ensure all fields are non-null when needed
}
```

#### 2.2 Add Response Validation Test

**File**: `PerformPassiveAuthenticationUseCaseTest.java` (new)

```java
@Test
@DisplayName("Should return complete response structure for valid passport")
void shouldReturnCompleteResponseStructure() {
    // Given
    PerformPassiveAuthenticationCommand command = createValidCommand();

    // When
    PassiveAuthenticationResponseDto response = useCase.execute(command);

    // Then
    assertThat(response.verificationId()).isNotNull();
    assertThat(response.verificationStatus()).isEqualTo(VerificationStatus.VALID);

    // Certificate chain validation
    assertThat(response.certificateChainValidation()).isNotNull();
    assertThat(response.certificateChainValidation().valid()).isTrue();
    assertThat(response.certificateChainValidation().cscaSubject()).isNotBlank();

    // SOD signature validation
    assertThat(response.sodSignatureValidation()).isNotNull();
    assertThat(response.sodSignatureValidation().valid()).isTrue();

    // Data group hash validation
    assertThat(response.dataGroupHashValidation()).isNotNull();
    assertThat(response.dataGroupHashValidation().allHashesValid()).isTrue();
}
```

---

### Task 3: Fix 404 Handling ⚠️ MEDIUM PRIORITY

**Goal**: Fix `shouldReturn404WhenDscNotFound` and `shouldReturnVerificationById` tests

**Issue 1**: `shouldReturn404WhenDscNotFound` returns 200 instead of 404
**Issue 2**: `shouldReturnVerificationById` returns 404 instead of 200

#### 3.1 Remove DSC Not Found Exception (ICAO Compliance)

**Reason**: DSC is now extracted from SOD, not looked up in LDAP

**File**: `PerformPassiveAuthenticationUseCase.java`

```java
// REMOVE THIS (obsolete since Phase 4.11.3)
private Certificate retrieveDsc(PerformPassiveAuthenticationCommand command) {
    // This method should not exist anymore
    // DSC is extracted from SOD, not looked up
}
```

**Update Test**: Remove or modify `shouldReturn404WhenDscNotFound`

```java
// OPTION 1: Remove test (DSC lookup no longer used)
// @Test
// void shouldReturn404WhenDscNotFound() { ... }

// OPTION 2: Change to CSCA not found scenario
@Test
@DisplayName("Should return ERROR status when CSCA not found")
void shouldReturnErrorWhenCscaNotFound() {
    // Given
    PerformPassiveAuthenticationCommand command = createValidCommand();
    // Mock CSCA lookup to return empty
    when(certificateRepository.findBySubjectDnIgnoreCase(any())).thenReturn(Optional.empty());

    // When
    ResponseEntity<?> response = controller.performPassiveAuthentication(request, mockHttpRequest);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK); // ❗ 200 OK

    PassiveAuthenticationResponse body = (PassiveAuthenticationResponse) response.getBody();
    assertThat(body.verificationResult().verificationStatus()).isEqualTo(VerificationStatus.ERROR);
    assertThat(body.verificationResult().errorMessage()).contains("CSCA not found");
}
```

#### 3.2 Fix Verification By ID

**File**: `PassiveAuthenticationController.java`
**Endpoint**: `GET /api/passive-authentication/{verificationId}`

**Issue**: Returns 404 when verification record exists

**Debug**:

```java
@GetMapping("/{verificationId}")
public ResponseEntity<PassiveAuthenticationDetailsResponse> getVerificationById(
    @PathVariable UUID verificationId
) {
    log.debug("Retrieving verification by ID: {}", verificationId);

    Optional<PassportData> passportData = passportDataRepository.findById(verificationId);

    if (passportData.isEmpty()) {
        log.warn("Verification not found: {}", verificationId);
        return ResponseEntity.notFound().build();
    }

    log.info("Verification found: {}", verificationId);

    // Build response
    PassiveAuthenticationDetailsResponse response = buildDetailsResponse(passportData.get());
    return ResponseEntity.ok(response);
}
```

**Check**: Verify `passportDataRepository.findById()` is working

```java
// Add test
@Test
@DisplayName("Should save and retrieve verification by ID")
void shouldSaveAndRetrieveVerificationById() {
    // Given
    PerformPassiveAuthenticationCommand command = createValidCommand();

    // When
    PassiveAuthenticationResponseDto response = useCase.execute(command);
    UUID verificationId = response.verificationId();

    // Then
    Optional<PassportData> saved = passportDataRepository.findById(verificationId);
    assertThat(saved).isPresent();
}
```

---

### Task 4: Fix Invalid UUID Error Handling ⚠️ MEDIUM PRIORITY

**Goal**: Fix `shouldReturn400ForInvalidUuidFormat` test

**Issue**: Returns 500 instead of 400 for invalid UUID format

**File**: `GlobalExceptionHandler.java`

**Add Handler**:

```java
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
    IllegalArgumentException ex,
    WebRequest request
) {
    log.warn("Invalid argument: {}", ex.getMessage());

    ErrorResponse errorResponse = new ErrorResponse(
        "INVALID_ARGUMENT",
        ex.getMessage(),
        request.getDescription(false)
    );

    return ResponseEntity.badRequest().body(errorResponse);
}

@ExceptionHandler(MethodArgumentTypeMismatchException.class)
public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
    MethodArgumentTypeMismatchException ex,
    WebRequest request
) {
    log.warn("Method argument type mismatch: parameter={}, value={}, expectedType={}",
        ex.getName(), ex.getValue(), ex.getRequiredType());

    String message = String.format(
        "Invalid value '%s' for parameter '%s'. Expected type: %s",
        ex.getValue(), ex.getName(), ex.getRequiredType().getSimpleName()
    );

    ErrorResponse errorResponse = new ErrorResponse(
        "INVALID_PARAMETER_TYPE",
        message,
        request.getDescription(false)
    );

    return ResponseEntity.badRequest().body(errorResponse);
}
```

**Required Import**:
```java
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
```

---

## Testing Strategy

### Step 1: Run Tests Before Changes

```bash
./mvnw test -Dtest=PassiveAuthenticationControllerTest
```

**Expected**: 11/20 passing

### Step 2: Apply Task 1 (Trust Chain Debug)

```bash
./mvnw test -Dtest=PassiveAuthenticationControllerTest#shouldVerifyValidPassport
```

**Expected**: Check logs for root cause

### Step 3: Apply Task 2 (Response Structure)

```bash
./mvnw test -Dtest=PassiveAuthenticationControllerTest#shouldReturnCorrectJsonStructure
```

**Expected**: Test passes

### Step 4: Apply Task 3 (404 Handling)

```bash
./mvnw test -Dtest=PassiveAuthenticationControllerTest#shouldReturnVerificationById
```

**Expected**: Test passes

### Step 5: Apply Task 4 (Invalid UUID)

```bash
./mvnw test -Dtest=PassiveAuthenticationControllerTest#shouldReturn400ForInvalidUuidFormat
```

**Expected**: Test passes

### Step 6: Run Full Test Suite

```bash
./mvnw test -Dtest=PassiveAuthenticationControllerTest
```

**Expected**: 14/20 passing (70%)

---

## Success Criteria

### Must Have ✅

- [ ] `shouldVerifyValidPassport` - Returns VALID status
- [ ] `shouldReturnInvalidStatusForTamperedPassport` - Returns INVALID status
- [ ] `shouldReturnCorrectJsonStructure` - All fields present
- [ ] `shouldReturnVerificationById` - Returns 200 with data
- [ ] 14/20 tests passing (70%)

### Should Have ⏳

- [ ] `shouldReturn400ForInvalidUuidFormat` - Returns 400 instead of 500
- [ ] Comprehensive debug logging for troubleshooting

### Nice to Have 🎁

- [ ] Remove obsolete DSC lookup code
- [ ] Add DN normalization utility
- [ ] Add unit tests for trust chain validation

---

## Deliverables

### Code Changes

1. **PerformPassiveAuthenticationUseCase.java**
   - Enhanced logging
   - DN normalization in CSCA lookup
   - Robust trust chain validation

2. **GlobalExceptionHandler.java**
   - Invalid UUID format handler

3. **Tests**
   - Update `shouldReturn404WhenDscNotFound` (remove or modify)
   - New unit tests for trust chain validation

### Documentation

1. **SESSION_2025-12-19_PA_PHASE_4_11_4_DEBUG_FIX_TESTS.md**
   - Session report with root cause analysis
   - Test results before/after
   - Lessons learned

---

## Estimated Timeline

| Task | Estimated Time | Dependency |
|------|---------------|-----------|
| Add logging | 30 min | None |
| Debug trust chain | 60 min | Logging |
| Fix DN normalization | 30 min | Debug |
| Fix response structure | 30 min | Trust chain |
| Fix 404 handling | 30 min | None |
| Fix UUID error | 20 min | None |
| Run all tests | 10 min | All tasks |
| Debug failures | 40 min | Test run |
| **Total** | **4 hours** | |

---

## Next Phase

**Phase 4.12**: Pagination & Search Functionality
- Implement pagination for verification history
- Add country/status filtering
- Create search API
- Target: 17/20 tests passing (85%)

---

**Created**: 2025-12-19
**Status**: IN PROGRESS
**Priority**: HIGH
**Estimated Completion**: 2025-12-19 (same day)
