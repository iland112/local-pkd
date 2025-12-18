# Phase 4.10: LDAP Test Fixtures & Request Validation

**Phase**: 4.10
**Status**: 🚧 In Progress
**Start Date**: 2025-12-18
**Estimated Effort**: 3-4 hours

---

## 🎯 Objectives

### Primary Goals
1. ✅ Add DSC certificates to test LDAP directory (H2)
2. ✅ Implement request validation (@Valid, custom validators)
3. ✅ Fix 13 remaining Controller test failures
4. ✅ Implement pagination and filtering for history endpoint

### Success Criteria
- [ ] All 20 Controller tests passing
- [ ] Test LDAP contains CSCAs, DSCs, and CRLs
- [ ] Request validation with proper error messages (400 Bad Request)
- [ ] Paginated history endpoint (default: 20 items per page)

---

## 📋 Current Status

### Test Results (Phase 4.9)

**Test Execution**: 20 tests run
- ✅ Passing: 7 tests
- ❌ Failing: 13 tests

**Failure Root Cause**: DSC not found in LDAP
```
ERROR status in response: DSC not found in LDAP
- Subject DN: C=KR,O=Government,OU=MOFA,CN=DS0120200313 1
- Serial Number: 127
```

**Analysis**:
- DSC extraction from SOD: ✅ Working correctly
- LDAP lookup: ❌ Empty test database
- Trust chain verification: ⏸️ Blocked by missing DSC

---

## 🔧 Implementation Tasks

### Task 1: Create Test LDAP Fixtures ⏳

**Objective**: Populate H2 LDAP with test certificates

**Files to Create**:
```
src/test/resources/
├── test-fixtures/
│   ├── korea-csca.pem          # Korean CSCA certificate
│   ├── korea-dsc.pem           # Korean DSC certificate
│   ├── korea-crl.pem           # Korean CRL
│   └── README.md               # Fixture documentation
└── data.sql                    # H2 database initialization
```

**Certificate Requirements**:
- **CSCA**: Self-signed, cA=TRUE, valid trust anchor
- **DSC**: Signed by CSCA, matches SOD Subject DN and Serial
- **CRL**: Issued by CSCA, empty revocation list

**SQL Schema**:
```sql
-- Insert CSCA
INSERT INTO parsed_certificate (
    id, parsed_file_id, certificate_type, country_code,
    subject_dn, issuer_dn, serial_number, fingerprint_sha256,
    not_before, not_after, encoded,
    public_key_algorithm, signature_algorithm,
    validation_status, validated_at
) VALUES (
    'uuid-csca', 'uuid-file', 'CSCA', 'KR',
    'C=KR,O=Government,OU=PKI,CN=CSCA-KOREA',
    'C=KR,O=Government,OU=PKI,CN=CSCA-KOREA',
    'A1B2C3', 'sha256-fingerprint',
    '2020-01-01', '2030-12-31', X'...',
    'RSA', 'SHA256withRSA',
    'VALID', NOW()
);

-- Insert DSC (matches SOD)
INSERT INTO parsed_certificate (
    id, parsed_file_id, certificate_type, country_code,
    subject_dn, issuer_dn, serial_number, fingerprint_sha256,
    not_before, not_after, encoded,
    public_key_algorithm, signature_algorithm,
    validation_status, validated_at
) VALUES (
    'uuid-dsc', 'uuid-file', 'DSC', 'KR',
    'C=KR,O=Government,OU=MOFA,CN=DS0120200313 1',  -- Match SOD
    'C=KR,O=Government,OU=PKI,CN=CSCA-KOREA',
    '7F',  -- Serial 127 in hex (uppercase)
    'sha256-fingerprint',
    '2020-03-13', '2025-03-13', X'...',
    'RSA', 'SHA256withRSA',
    'VALID', NOW()
);
```

**Implementation Steps**:
1. Generate test certificates using OpenSSL or Bouncy Castle
2. Create `data.sql` with INSERT statements
3. Configure H2 to load `data.sql` on startup
4. Verify certificates are accessible via LDAP queries

**Estimated Time**: 1.5 hours

---

### Task 2: Implement Request Validation ⏳

**Objective**: Add Jakarta Bean Validation to API requests

**Files to Modify**:
```
passiveauthentication/
├── application/
│   ├── command/
│   │   └── PerformPassiveAuthenticationCommand.java  # Add @Valid
│   └── response/
│       └── PassiveAuthenticationResponse.java        # Add validation
└── infrastructure/
    └── web/
        ├── PassiveAuthenticationController.java      # Add @Valid
        └── ValidationExceptionHandler.java           # NEW: Handle errors
```

#### 2.1 Command Validation

**PerformPassiveAuthenticationCommand.java**:
```java
public record PerformPassiveAuthenticationCommand(
    @NotNull(message = "SOD is required")
    @Size(min = 100, max = 10000, message = "SOD size must be between 100 and 10000 bytes")
    String sodBase64,

    @NotEmpty(message = "At least one Data Group is required")
    @Size(max = 20, message = "Maximum 20 Data Groups allowed")
    Map<@Min(1) @Max(20) Integer, @NotBlank String> dataGroups,

    @Size(max = 200, message = "Client info too long")
    String clientInfo
) {
    // Compact constructor for additional validation
    public PerformPassiveAuthenticationCommand {
        if (sodBase64 != null && !isValidBase64(sodBase64)) {
            throw new IllegalArgumentException("SOD must be valid Base64");
        }
    }

    private static boolean isValidBase64(String input) {
        try {
            Base64.getDecoder().decode(input);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
```

#### 2.2 Controller Validation

**PassiveAuthenticationController.java**:
```java
@PostMapping("/verify")
public ResponseEntity<PassiveAuthenticationResponse> performPassiveAuthentication(
    @Valid @RequestBody PerformPassiveAuthenticationCommand command  // Add @Valid
) {
    // Implementation remains same
}
```

#### 2.3 Validation Exception Handler

**ValidationExceptionHandler.java** (NEW):
```java
@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );
        return new ErrorResponse("VALIDATION_ERROR", "Request validation failed", errors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return new ErrorResponse("INVALID_ARGUMENT", ex.getMessage(), null);
    }

    public record ErrorResponse(String code, String message, Map<String, String> fieldErrors) {}
}
```

**Estimated Time**: 1 hour

---

### Task 3: Fix Controller Test Failures ⏳

**Objective**: Update tests to work with LDAP fixtures and validation

**Files to Modify**:
```
src/test/java/com/smartcoreinc/localpkd/passiveauthentication/infrastructure/web/
└── PassiveAuthenticationControllerTest.java
```

**Test Updates**:

1. **Add @Sql annotation** to load fixtures:
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/test-fixtures/data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/test-fixtures/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PassiveAuthenticationControllerTest {
    // Tests...
}
```

2. **Update expected responses** (DSC now found):
```java
@Test
void shouldReturnValidStatusWhenAllVerificationsPassed() {
    // Given: Valid SOD and Data Groups
    var command = createValidCommand();

    // When: POST /api/v1/passive-authentication/verify
    var response = restTemplate.postForEntity(url, command, PassiveAuthenticationResponse.class);

    // Then: Status 200 OK, result VALID
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().result()).isEqualTo(VerificationResult.VALID);  // Changed from ERROR
    assertThat(response.getBody().details().dscSubject())
        .isEqualTo("C=KR,O=Government,OU=MOFA,CN=DS0120200313 1");
}
```

3. **Add new validation tests**:
```java
@Test
void shouldReturn400WhenSodIsNull() {
    var command = new PerformPassiveAuthenticationCommand(null, Map.of(1, "base64"), null);
    var response = restTemplate.postForEntity(url, command, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    assertThat(response.getBody().fieldErrors()).containsKey("sodBase64");
}

@Test
void shouldReturn400WhenDataGroupsEmpty() {
    var command = new PerformPassiveAuthenticationCommand("validBase64", Map.of(), null);
    var response = restTemplate.postForEntity(url, command, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
}
```

**Estimated Time**: 1 hour

---

### Task 4: Implement Pagination for History Endpoint ⏳

**Objective**: Add pagination to `/api/v1/passive-authentication/history`

**Files to Modify**:
```
passiveauthentication/
├── application/
│   └── query/
│       └── GetPassiveAuthenticationHistoryQuery.java  # NEW
└── infrastructure/
    ├── web/
    │   └── PassiveAuthenticationController.java
    └── repository/
        └── JpaPassiveAuthenticationAuditLogRepository.java
```

#### 4.1 Query Object

**GetPassiveAuthenticationHistoryQuery.java** (NEW):
```java
public record GetPassiveAuthenticationHistoryQuery(
    @Min(0) int page,
    @Min(1) @Max(100) int size,
    String sortBy,
    String sortDirection,
    String countryCode,
    VerificationResult result
) {
    public GetPassiveAuthenticationHistoryQuery {
        // Default values
        if (page < 0) page = 0;
        if (size < 1 || size > 100) size = 20;
        if (sortBy == null) sortBy = "verifiedAt";
        if (sortDirection == null) sortDirection = "DESC";
    }

    public Pageable toPageable() {
        Sort sort = sortDirection.equalsIgnoreCase("ASC")
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();
        return PageRequest.of(page, size, sort);
    }
}
```

#### 4.2 Controller Update

**PassiveAuthenticationController.java**:
```java
@GetMapping("/history")
public ResponseEntity<Page<PassiveAuthenticationHistoryResponse>> getHistory(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(defaultValue = "verifiedAt") String sortBy,
    @RequestParam(defaultValue = "DESC") String sortDirection,
    @RequestParam(required = false) String countryCode,
    @RequestParam(required = false) VerificationResult result
) {
    var query = new GetPassiveAuthenticationHistoryQuery(
        page, size, sortBy, sortDirection, countryCode, result
    );

    Page<PassiveAuthenticationAuditLog> logs = auditLogRepository.findAll(
        buildSpecification(query), query.toPageable()
    );

    var responses = logs.map(this::toHistoryResponse);
    return ResponseEntity.ok(responses);
}

private Specification<PassiveAuthenticationAuditLog> buildSpecification(
    GetPassiveAuthenticationHistoryQuery query
) {
    return (root, criteriaQuery, cb) -> {
        List<Predicate> predicates = new ArrayList<>();

        if (query.countryCode() != null) {
            predicates.add(cb.equal(root.get("countryCode"), query.countryCode()));
        }
        if (query.result() != null) {
            predicates.add(cb.equal(root.get("result"), query.result()));
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    };
}
```

**Estimated Time**: 0.5 hours

---

## 📊 Test Coverage Goals

### Controller Tests (20 tests target)

**Endpoint Tests** (7 tests):
- ✅ POST /verify - valid request
- ✅ POST /verify - invalid SOD signature
- ✅ POST /verify - data group hash mismatch
- ✅ POST /verify - DSC not found (should now pass)
- ✅ POST /verify - CRL revocation check
- ✅ GET /history - paginated results
- ✅ GET /history - filtered by country code

**Validation Tests** (4 tests):
- ⏳ POST /verify - null SOD (400 Bad Request)
- ⏳ POST /verify - invalid Base64 (400 Bad Request)
- ⏳ POST /verify - empty data groups (400 Bad Request)
- ⏳ POST /verify - too many data groups (400 Bad Request)

**Response Format Tests** (2 tests):
- ✅ Response contains all required fields
- ✅ Response follows OpenAPI schema

**Error Handling Tests** (5 tests):
- ✅ 404 Not Found for non-existent endpoint
- ✅ 405 Method Not Allowed for wrong HTTP method
- ⏳ 400 Bad Request for validation errors
- ✅ 500 Internal Server Error handling
- ✅ 200 OK with ERROR result for business errors

**Client Metadata Tests** (2 tests):
- ✅ Client IP extraction
- ✅ User-Agent extraction

---

## 🚀 Execution Plan

### Step 1: Generate Test Certificates (30 min)
```bash
# Use OpenSSL or Bouncy Castle
cd src/test/resources/test-fixtures/
./generate-test-certs.sh
```

### Step 2: Create data.sql (20 min)
```sql
-- Load generated certificates into H2
INSERT INTO parsed_certificate (...) VALUES (...);
```

### Step 3: Implement Validation (1 hour)
1. Add `@Valid` annotations
2. Create `ValidationExceptionHandler`
3. Write validation tests

### Step 4: Fix Test Failures (1 hour)
1. Add `@Sql` annotations
2. Update expected responses
3. Run tests and verify

### Step 5: Add Pagination (30 min)
1. Create query object
2. Update controller
3. Add pagination tests

### Step 6: Final Verification (20 min)
```bash
./mvnw clean test -Dtest=PassiveAuthenticationControllerTest
# Expected: 20/20 tests passing
```

---

## ✅ Acceptance Criteria

### Functional Requirements
- [ ] DSC found in LDAP for test SOD
- [ ] Trust chain verification succeeds (DSC → CSCA)
- [ ] Request validation rejects invalid inputs
- [ ] Pagination works with default and custom parameters
- [ ] All 20 Controller tests passing

### Non-Functional Requirements
- [ ] Test execution time < 30 seconds
- [ ] No test database pollution (cleanup after each test)
- [ ] Clear error messages for validation failures
- [ ] Pagination performance acceptable (< 100ms)

---

## 🔗 Related Documents

- [SESSION_2025-12-18_PA_PHASE_4_9_DSC_EXTRACTION.md](SESSION_2025-12-18_PA_PHASE_4_9_DSC_EXTRACTION.md) - Phase 4.9 completion
- [SESSION_2025-12-18_PA_PHASE_4_8_H2_SCHEMA_FIX.md](SESSION_2025-12-18_PA_PHASE_4_8_H2_SCHEMA_FIX.md) - H2 schema setup
- [SESSION_2025-12-18_PA_PHASE_4_6_REST_API_CONTROLLER_TESTS.md](SESSION_2025-12-18_PA_PHASE_4_6_REST_API_CONTROLLER_TESTS.md) - Controller tests
- [ICAO Doc 9303](https://www.icao.int/publications/pages/publication.aspx?docnum=9303) - ePassport standards

---

## 📝 Notes

### Design Decisions

1. **H2 in-memory DB**: Fast test execution, no external dependencies
2. **data.sql approach**: Simple, declarative test data
3. **Bean Validation**: Standard Jakarta validation for consistency
4. **Pagination**: Follows Spring Data conventions

### Potential Issues

1. **Certificate Generation**: Need valid key pairs and signatures
   - Solution: Use Bouncy Castle KeyPairGenerator and CertificateBuilder
2. **Serial Number Format**: Must match SOD exactly (hex uppercase)
   - Solution: Validate in test data
3. **H2 Data Cleanup**: Ensure no test pollution
   - Solution: Use `@Sql(executionPhase = AFTER_TEST_METHOD)`

---

**Document Version**: 1.0
**Author**: Claude Sonnet 4.5 (Anthropic)
**Status**: 🚧 In Progress
**Next Review**: After Task 1 completion

🤖 *Generated with [Claude Code](https://claude.com/claude-code)*
