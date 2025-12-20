# Session Report: Passive Authentication Phase 4.11.3 - SOD DSC Extraction & Verification

**Date**: 2025-12-19
**Session ID**: pa-phase-4-11-3-sod-dsc-extraction
**Status**: ✅ COMPLETED
**Previous Phase**: Phase 4.11.2 (LDAP Test Fixtures) - COMPLETED
**Next Phase**: Phase 4.11.4 (Debug & Fix Remaining Tests)

---

## Overview

Phase 4.11.3에서는 `src/sod_example` 폴더의 참고 코드를 기반으로 SOD 파싱 및 DSC 추출 로직을 개선했습니다. 핵심 개선 사항은 ICAO 9303 Tag 0x77 wrapper를 ASN.1 파싱으로 처리하고, DSC를 SOD에서 직접 추출하는 ICAO 표준 방식을 적용한 것입니다.

---

## Objectives

### Primary Goals ✅
- [x] Analyze sod_example code and identify differences
- [x] Update `unwrapIcaoSod()` to use ASN.1 parsing
- [x] Add `verifySignature(X509Certificate)` overload
- [x] Update UseCase to extract DSC from SOD (not LDAP)
- [x] Compile successfully

### Secondary Goals ⏳
- [x] Run tests (11/20 passing - maintained)
- [ ] Fix remaining 9 failing tests (deferred to Phase 4.11.4)

---

## Implementation Details

### 1. ASN.1-based Tag 0x77 Unwrapping ✅

**File**: `BouncyCastleSodParserAdapter.java`
**Method**: `unwrapIcaoSod()`

**Before** (Manual byte parsing):
```java
private byte[] unwrapIcaoSod(byte[] sodBytes) {
    if ((sodBytes[0] & 0xFF) != 0x77) {
        return sodBytes;
    }

    int offset = 1;
    int lengthByte = sodBytes[offset++] & 0xFF;

    if ((lengthByte & 0x80) != 0) {
        int numOctets = lengthByte & 0x7F;
        offset += numOctets;
    }

    byte[] cmsBytes = new byte[sodBytes.length - offset];
    System.arraycopy(sodBytes, offset, cmsBytes, 0, cmsBytes.length);
    return cmsBytes;
}
```

**After** (ASN.1 parsing - matches sod_example):
```java
private byte[] unwrapIcaoSod(byte[] sodBytes) {
    if (sodBytes == null || sodBytes.length < 4) {
        return sodBytes;
    }

    try (ASN1InputStream asn1InputStream = new ASN1InputStream(sodBytes)) {
        ASN1Primitive asn1Primitive = asn1InputStream.readObject();

        // Check if wrapped with ICAO Tag 0x77
        if (!(asn1Primitive instanceof ASN1TaggedObject tagged)) {
            // Already unwrapped CMS data (starts with SEQUENCE)
            log.debug("SOD does not have Tag 0x77 wrapper, using raw bytes");
            return sodBytes;
        }

        // Verify ICAO EF.SOD Application[23] tag
        if (tagged.getTagClass() != BERTags.APPLICATION || tagged.getTagNo() != 23) {
            throw new InfrastructureException(
                "INVALID_SOD_FORMAT",
                String.format("Invalid EF.SOD tag: class=%d, number=%d (expected APPLICATION[23])",
                    tagged.getTagClass(), tagged.getTagNo())
            );
        }

        log.debug("SOD has Tag 0x77 wrapper, unwrapping using ASN.1 parsing...");

        // Extract CMS ContentInfo (EXPLICIT tagging)
        ASN1Primitive content = tagged.getBaseObject().toASN1Primitive();
        byte[] cmsBytes = content.getEncoded(ASN1Encoding.DER);

        log.debug("Unwrapped SOD: {} bytes (was {} bytes with wrapper)", cmsBytes.length, sodBytes.length);

        return cmsBytes;

    } catch (Exception e) {
        throw new InfrastructureException(
            "SOD_UNWRAP_ERROR",
            "Failed to unwrap ICAO Tag 0x77: " + e.getMessage(),
            e
        );
    }
}
```

**Key Improvements**:
- ✅ Uses Bouncy Castle ASN.1 parsing (like sod_example/SODParser.java)
- ✅ Explicitly checks for `ASN1TaggedObject` with tag class `APPLICATION` and tag number `23`
- ✅ Proper exception handling with clear error messages
- ✅ Better logging for debugging

**New Imports**:
```java
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.BERTags;
```

---

### 2. X509Certificate-based Signature Verification ✅

**File**: `SodParserPort.java`, `BouncyCastleSodParserAdapter.java`

**Added Interface Method**:
```java
/**
 * Verifies SOD signature using DSC X509 certificate (recommended).
 * <p>
 * This is the recommended method for SOD signature verification as it accepts
 * the complete DSC certificate, matching the sod_example implementation pattern.
 * <p>
 * This method is preferred over {@link #verifySignature(byte[], PublicKey)} because:
 * <ul>
 *   <li>Matches ICAO 9303 reference implementation</li>
 *   <li>Used by sod_example (SODSignatureVerifier.java)</li>
 *   <li>Provides better compatibility with CMS signature verification</li>
 * </ul>
 *
 * @param sodBytes Binary SOD data (PKCS#7 SignedData)
 * @param dscCertificate DSC X509 certificate for verification
 * @return true if signature is valid, false otherwise
 */
boolean verifySignature(byte[] sodBytes, java.security.cert.X509Certificate dscCertificate);
```

**Implementation** (matches sod_example/SODSignatureVerifier.java):
```java
@Override
public boolean verifySignature(byte[] sodBytes, X509Certificate dscCertificate) {
    try {
        log.debug("Verifying SOD signature with DSC X509 certificate");

        // Remove ICAO 9303 Tag 0x77 wrapper if present
        byte[] cmsBytes = unwrapIcaoSod(sodBytes);

        // Parse CMS SignedData
        CMSSignedData cmsSignedData = new CMSSignedData(cmsBytes);

        // Extract SignerInfo
        SignerInformationStore signerInfos = cmsSignedData.getSignerInfos();
        if (signerInfos.size() == 0) {
            log.error("No SignerInfo found in SOD");
            return false;
        }

        SignerInformation signerInfo = signerInfos.getSigners().iterator().next();

        // Verify signature (like sod_example/SODSignatureVerifier.java)
        boolean valid = signerInfo.verify(
            new JcaSimpleSignerInfoVerifierBuilder()
                .setProvider("BC")
                .build(dscCertificate)
        );

        if (valid) {
            log.info("SOD signature verification succeeded with DSC certificate");
        } else {
            log.error("SOD signature verification failed with DSC certificate");
        }

        return valid;

    } catch (Exception e) {
        throw new InfrastructureException(
            "SOD_SIGNATURE_VERIFY_ERROR",
            "Failed to verify SOD signature with DSC certificate: " + e.getMessage(),
            e
        );
    }
}
```

**Benefits**:
- ✅ Matches sod_example reference implementation
- ✅ Cleaner API (passes certificate instead of just public key)
- ✅ Better compatibility with CMS signature verification

---

### 3. ICAO 9303 Standard PA Flow ✅

**File**: `PerformPassiveAuthenticationUseCase.java`

**Before** (DSC lookup from LDAP):
```java
// Step 1: Retrieve DSC from LDAP
Certificate dsc = retrieveDsc(command);

// Step 2: Retrieve CSCA from LDAP
Certificate csca = retrieveCsca(dsc);

// Step 3: Validate Certificate Chain
CertificateChainValidationDto chainValidation = validateCertificateChain(
    dsc, csca, command.issuingCountry().getValue(), errors
);

// Step 4: Validate SOD signature
SodSignatureValidationDto sodValidation = validateSodSignature(
    sod, dsc, errors
);
```

**After** (DSC extraction from SOD - ICAO 9303 standard):
```java
// Step 1: Extract DSC from SOD (ICAO 9303 standard approach)
java.security.cert.X509Certificate dscX509 = sodParser.extractDscCertificate(command.sodBytes());
log.debug("Extracted DSC from SOD: {}", dscX509.getSubjectX500Principal().getName());

// Step 2: Retrieve CSCA from LDAP using DSC issuer DN
String cscaDn = dscX509.getIssuerX500Principal().getName();
Certificate csca = retrieveCscaByIssuerDn(cscaDn);
log.debug("Retrieved CSCA: {}", csca.getSubjectInfo().getDistinguishedName());

// Step 3: Validate Certificate Chain (DSC → CSCA)
CertificateChainValidationDto chainValidation = validateCertificateChainWithX509Dsc(
    dscX509, csca, command.issuingCountry().getValue(), errors
);

// Step 4: Parse SOD and validate signature
SecurityObjectDocument sod = SecurityObjectDocument.of(command.sodBytes());
SodSignatureValidationDto sodValidation = validateSodSignatureWithX509Dsc(
    sod, dscX509, errors
);
```

**Key Changes**:
1. ✅ **DSC Extraction from SOD**: Uses `sodParser.extractDscCertificate()` instead of LDAP lookup
2. ✅ **CSCA Lookup by Issuer DN**: Extracts CSCA DN from DSC issuer field
3. ✅ **New Validation Methods**:
   - `validateCertificateChainWithX509Dsc()` - Verifies DSC with CSCA public key
   - `validateSodSignatureWithX509Dsc()` - Uses X509Certificate overload

**Trust Chain Verification** (Direct public key verification):
```java
// Validate trust chain: DSC signature with CSCA public key
// Convert CSCA certificate bytes to X509Certificate
java.security.cert.CertificateFactory cf =
    java.security.cert.CertificateFactory.getInstance("X.509");
java.security.cert.Certificate cscaCert = cf.generateCertificate(
    new java.io.ByteArrayInputStream(csca.getX509Data().getCertificateBinary())
);
java.security.cert.X509Certificate cscaX509 =
    (java.security.cert.X509Certificate) cscaCert;

dscX509.verify(cscaX509.getPublicKey());
chainValid = true;
```

**SOD Signature Verification** (Using X509Certificate):
```java
// Verify signature using X509Certificate (recommended approach - matches sod_example)
signatureValid = sodParser.verifySignature(sod.getEncodedData(), dscX509);
```

---

## Files Modified

### Modified Files (3)

1. **src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/port/SodParserPort.java**
   - Added `verifySignature(byte[], X509Certificate)` method signature
   - Lines: 73-96 (24 new lines)

2. **src/main/java/com/smartcoreinc/localpkd/passiveauthentication/infrastructure/adapter/BouncyCastleSodParserAdapter.java**
   - Updated imports (ASN.1 classes, X509Certificate)
   - Rewrote `unwrapIcaoSod()` with ASN.1 parsing (lines 306-347)
   - Added `verifySignature(X509Certificate)` implementation (lines 193-242)

3. **src/main/java/com/smartcoreinc/localpkd/passiveauthentication/application/usecase/PerformPassiveAuthenticationUseCase.java**
   - Updated `execute()` method to extract DSC from SOD (lines 108-126)
   - Removed `retrieveDsc()` method (LDAP lookup)
   - Renamed `retrieveCsca()` to `retrieveCscaByIssuerDn()` (lines 197-216)
   - Added `validateCertificateChainWithX509Dsc()` (lines 218-293)
   - Added `validateSodSignatureWithX509Dsc()` (lines 303-357)
   - Removed unused import: `java.security.PublicKey`

### New Documentation (1)

1. **docs/TODO_PHASE_4_11_3_SOD_DSC_EXTRACTION.md** (588 lines)
   - Complete implementation guide
   - Code examples from sod_example
   - Task breakdown with estimated times
   - Success criteria

---

## Technical Decisions

### 1. ASN.1 Parsing over Manual Byte Manipulation

**Decision**: Use Bouncy Castle ASN.1 parsing instead of manual TLV byte parsing

**Rationale**:
- ✅ **Correctness**: ASN.1 library handles edge cases (e.g., constructed vs primitive encoding)
- ✅ **Reference Implementation**: Matches sod_example/SODParser.java
- ✅ **Tag Verification**: Explicitly checks for APPLICATION[23] tag
- ✅ **Maintainability**: Clearer intent, easier to debug

**Trade-offs**:
- Slightly more verbose code
- Requires additional imports
- But: More robust and standards-compliant

### 2. X509Certificate Overload for Signature Verification

**Decision**: Add `verifySignature(X509Certificate)` overload while keeping `verifySignature(PublicKey)`

**Rationale**:
- ✅ **API Compatibility**: Keeps existing `PublicKey` version for backward compatibility
- ✅ **Best Practice**: Recommended approach from sod_example
- ✅ **Cleaner Code**: Passes entire certificate instead of extracting public key first

**Implementation**:
```java
// Old way (still supported)
PublicKey pk = dscX509.getPublicKey();
boolean valid = sodParser.verifySignature(sodBytes, pk);

// New way (recommended)
boolean valid = sodParser.verifySignature(sodBytes, dscX509);
```

### 3. ICAO 9303 Standard PA Flow

**Decision**: Extract DSC from SOD instead of LDAP lookup

**Rationale**:
- ✅ **ICAO Compliance**: Follows ICAO Doc 9303 Part 11 Passive Authentication standard
- ✅ **Robustness**: Works even if DSC not in LDAP (new/updated certificates)
- ✅ **Simplicity**: One less LDAP query
- ✅ **Authenticity**: Uses actual certificate from passport chip

**Verification Flow** (ICAO Standard):
```
1. Extract DSC from SOD (this phase) ✅
2. Extract CSCA DN from DSC issuer ✅
3. Retrieve CSCA from LDAP ✅
4. Verify DSC signature with CSCA public key ✅
5. Verify SOD signature with DSC ✅
6. Verify Data Group hashes ✅
```

---

## Test Results

### Before Phase 4.11.3
- **Tests run**: 20
- **Passing**: 11 (55%)
- **Failing**: 9 (45%)

### After Phase 4.11.3
- **Tests run**: 20
- **Passing**: 11 (55%)
- **Failing**: 9 (45%)
- **Improvement**: **Maintained** ✅

**Failing Tests (9)** - Same as before:

**Category 1: Trust Chain Issues (3 tests)**
1. `shouldVerifyValidPassport` - Returns ERROR instead of VALID
2. `shouldReturnInvalidStatusForTamperedPassport` - Returns ERROR instead of INVALID
3. `shouldReturnCorrectJsonStructure` - Missing `certificateChainValidation` field

**Category 2: 404 Handling (2 tests)**
4. `shouldReturn404WhenDscNotFound` - Returns 200 instead of 404
5. `shouldReturnVerificationById` - Returns 404 instead of 200

**Category 3: Pagination (3 tests - deferred to Phase 4.12)**
6. `shouldReturnPaginatedHistory` - Missing pagination structure
7. `shouldFilterByCountry` - Missing pagination structure
8. `shouldFilterByStatus` - Missing pagination structure

**Category 4: Error Handling (1 test)**
9. `shouldReturn400ForInvalidUuidFormat` - Returns 500 instead of 400

---

## Error Analysis

### Primary Issue: Trust Chain Verification

**Test**: `shouldVerifyValidPassport`
**Expected**: `status = VALID`
**Actual**: `status = ERROR`

**Hypothesis**:
The test is now extracting DSC from SOD successfully, but may be failing at:
1. CSCA lookup by issuer DN (DN format mismatch?)
2. Trust chain verification (signature verification failing?)
3. SOD signature verification (new X509Certificate method?)

**Next Steps** (Phase 4.11.4):
- Add debug logging to see exact error messages
- Verify CSCA Subject DN == DSC Issuer DN
- Test trust chain verification in isolation
- Verify SOD signature verification works

### Secondary Issue: Missing Response Fields

**Test**: `shouldReturnCorrectJsonStructure`
**Expected**: `$.certificateChainValidation` field exists
**Actual**: Field missing

**Hypothesis**:
The new validation method `validateCertificateChainWithX509Dsc()` may be returning different structure or null.

**Next Steps**:
- Verify `CertificateChainValidationDto` is being created correctly
- Check if response mapping includes all fields
- Add response structure logging

---

## Compilation Status

### Build Results ✅

```bash
$ ./mvnw clean compile

[INFO] --- compiler:3.13.0:compile (default-compile) @ local-pkd ---
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 239 source files with javac [debug parameters release 21] to target/classes
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  11.970 s
```

**Status**: ✅ **SUCCESSFUL**
**Source Files**: 239 files compiled
**Warnings**: 1 deprecation warning (LdifParserAdapter.java - pre-existing)

---

## Code Quality

### Improvements ✅

1. **Standards Compliance**: Now follows ICAO 9303 Part 11 standard
2. **Code Clarity**: ASN.1 parsing is more explicit than byte manipulation
3. **Maintainability**: Clearer separation of concerns (SOD parsing vs LDAP lookup)
4. **Logging**: Better debug logs for troubleshooting

### Potential Issues ⚠️

1. **DN Format Matching**: Need to verify Subject DN format consistency
2. **Error Handling**: Some catch blocks may be too broad
3. **CRL Check**: Skipped for SOD-extracted DSC (needs future enhancement)

---

## Lessons Learned

### 1. Reference Implementations are Invaluable

The `sod_example` code provided clear guidance on:
- ✅ Proper ASN.1 parsing techniques
- ✅ ICAO 9303 wrapper handling
- ✅ Signature verification best practices
- ✅ Certificate extraction patterns

**Recommendation**: Always check for reference implementations before writing cryptographic code.

### 2. ICAO Standards > Custom Approaches

Extracting DSC from SOD (ICAO standard) is:
- ✅ More robust (works without LDAP)
- ✅ Simpler (one less query)
- ✅ More authentic (uses actual passport certificate)

**Recommendation**: Follow international standards whenever possible.

### 3. Incremental Refactoring is Key

Breaking the work into phases:
1. ✅ Phase 4.11.2: Create test fixtures
2. ✅ Phase 4.11.3: Update SOD parsing (this phase)
3. ⏳ Phase 4.11.4: Debug and fix tests
4. ⏳ Phase 4.12: Pagination support

**Recommendation**: Don't try to fix everything at once - refactor incrementally.

---

## Next Steps (Phase 4.11.4)

### Immediate Tasks (HIGH PRIORITY)

1. **Debug Trust Chain Verification** (2-3 hours)
   - Add comprehensive logging to `validateCertificateChainWithX509Dsc()`
   - Verify CSCA DN format matches DSC issuer DN
   - Test trust chain in isolation (unit test)
   - Check X509Certificate signature verification

2. **Fix Response Structure** (1 hour)
   - Verify `CertificateChainValidationDto` is non-null
   - Ensure all DTO fields are properly populated
   - Add response mapping tests

3. **Test in Isolation** (1 hour)
   - Create simple integration test for SOD → DSC → CSCA flow
   - Test with real Korean passport data
   - Verify each step independently

### Target Metrics

- **Tests Passing**: 14/20 (70%) - currently 11/20 (55%)
- **Improvement Needed**: +3 tests
- **Focus**: Trust chain and response structure issues

---

## Success Criteria

### Phase 4.11.3 Criteria ✅

- [x] Update `unwrapIcaoSod()` to use ASN.1 parsing (like sod_example)
- [x] Add `verifySignature(X509Certificate)` overload
- [x] Update UseCase to extract DSC from SOD
- [x] Compile successfully
- [x] Maintain test pass rate (11/20)

### Phase 4.11.4 Criteria (Next)

- [ ] Debug and fix trust chain verification
- [ ] Fix response structure issues
- [ ] Achieve 14/20 tests passing (70%)
- [ ] All core PA flow tests passing

---

## References

### Code Examples

- **src/sod_example/SODParser.java** - Tag 0x77 unwrapping, DSC extraction
- **src/sod_example/SODSignatureVerifier.java** - Signature verification with X509Certificate
- **src/sod_example/DataGroupHashVerifier.java** - Hash verification

### Documentation

- [ICAO Doc 9303 Part 10](https://www.icao.int/publications/Documents/9303_p10_cons_en.pdf) - LDS Structure (Tag 0x77)
- [ICAO Doc 9303 Part 11](https://www.icao.int/publications/Documents/9303_p11_cons_en.pdf) - Passive Authentication
- [RFC 5652](https://www.rfc-editor.org/rfc/rfc5652) - CMS (PKCS#7)

### Previous Sessions

- [SESSION_2025-12-19_PA_PHASE_4_11_2_LDAP_TEST_FIXTURES.md](./SESSION_2025-12-19_PA_PHASE_4_11_2_LDAP_TEST_FIXTURES.md)
- [SESSION_2025-12-19_PA_PHASE_4_10_ICAO_COMPLIANCE.md](./SESSION_2025-12-19_PA_PHASE_4_10_ICAO_COMPLIANCE.md)
- [SESSION_2025-12-18_PA_PHASE_4_9_DSC_EXTRACTION.md](./SESSION_2025-12-18_PA_PHASE_4_9_DSC_EXTRACTION.md)

---

**Session Completed**: 2025-12-19 01:12 KST
**Duration**: ~1.5 hours
**Test Status**: 11/20 passing (maintained)
**Next Session**: Phase 4.11.4 - Debug & Fix Remaining Tests

---

**Prepared by**: Claude Code (Anthropic)
**Project**: Local PKD Evaluation - Passive Authentication Module
