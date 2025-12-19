# TODO: Passive Authentication Phase 4.11.3 - SOD DSC Extraction & Verification

**Status**: 🚧 IN PROGRESS
**Priority**: HIGH
**Estimated Time**: 2-3 hours
**Previous Phase**: Phase 4.11.2 (LDAP Test Fixtures) - COMPLETED

---

## Overview

Phase 4.11.3는 SOD 파일에서 DSC 인증서를 올바르게 추출하고 서명 검증을 수행하는 것에 초점을 맞춥니다. `src/sod_example` 폴더의 작동하는 예제 코드를 참고하여 현재 구현을 수정합니다.

**Key Insight**: 예제 코드는 ICAO 9303 Tag 0x77 wrapper를 올바르게 처리하고, CMS SignedData에서 DSC를 추출하며, SOD 서명을 검증합니다.

---

## Current Issues

### 1. SOD Parsing Errors

**Error Message**:
```json
{
  "code": "PA_EXECUTION_ERROR",
  "message": "SOD data does not appear to be valid PKCS#7 SignedData (expected tag 0x30)"
}
```

**Root Cause**:
- `unwrapIcaoSod()` 메서드가 제대로 작동하지 않거나
- Tag 0x77 wrapper 처리가 예제 코드와 다르게 구현됨

### 2. DSC Extraction Issues

**Current Implementation**:
```java
// BouncyCastleSodParserAdapter.extractDscCertificate()
public Optional<X509Certificate> extractDscCertificate(byte[] sodBytes) {
    try {
        byte[] cmsBytes = unwrapIcaoSod(sodBytes);
        CMSSignedData cmsSignedData = new CMSSignedData(cmsBytes);

        Store<X509CertificateHolder> certStore = cmsSignedData.getCertificates();
        X509CertificateHolder certHolder = certStore.getMatches(null).iterator().next();

        // ❌ Problem: Returns holder instead of X509Certificate
        return Optional.of(new JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certHolder));
    } catch (Exception e) {
        return Optional.empty();
    }
}
```

**Expected Implementation** (from sod_example):
```java
private X509Certificate extractDscCertificate(CMSSignedData cmsSignedData) throws Exception {
    Store<X509CertificateHolder> certStore = cmsSignedData.getCertificates();
    X509CertificateHolder certHolder = certStore.getMatches(null).iterator().next();

    CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
    try (ByteArrayInputStream bis = new ByteArrayInputStream(certHolder.getEncoded())) {
        return (X509Certificate) certFactory.generateCertificate(bis);
    }
}
```

### 3. Tag 0x77 Wrapper Handling

**Current Implementation**:
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

**Expected Implementation** (from sod_example):
```java
private byte[] extractCmsFromEfSod(byte[] efSodBytes) throws Exception {
    try (ASN1InputStream asn1InputStream = new ASN1InputStream(efSodBytes)) {
        ASN1Primitive asn1Primitive = asn1InputStream.readObject();
        if (!(asn1Primitive instanceof ASN1TaggedObject tagged)) {
            throw new IllegalArgumentException("EF.SOD is not ApplicationSpecific");
        }

        // ICAO EF.SOD = Application[23] (Tag Number 23)
        if (tagged.getTagClass() != BERTags.APPLICATION || tagged.getTagNo() != 23) {
            throw new IllegalArgumentException("EF.SOD is not tagged with [APPLICATION 23]");
        }

        // EXPLICIT tagging: baseObject == CMS ContentInfo (SEQUENCE)
        ASN1Primitive content = tagged.getBaseObject().toASN1Primitive();
        return content.getEncoded(ASN1Encoding.DER);
    }
}
```

**Key Difference**: 예제 코드는 ASN.1 파싱을 사용하여 Tag 0x77을 처리하고, Application[23] tag를 명시적으로 검증합니다.

---

## Tasks

### Task 1: Fix Tag 0x77 Wrapper Unwrapping ✅

**File**: `BouncyCastleSodParserAdapter.java`
**Method**: `unwrapIcaoSod()`

**Action**:
- [ ] Replace manual byte parsing with ASN.1 parsing (like sod_example)
- [ ] Use `ASN1InputStream` to read SOD bytes
- [ ] Check for `ASN1TaggedObject` with tag class `APPLICATION` and tag number `23`
- [ ] Extract CMS ContentInfo using `getBaseObject().toASN1Primitive()`
- [ ] Return DER-encoded bytes

**Implementation**:
```java
private byte[] unwrapIcaoSod(byte[] sodBytes) {
    try (ASN1InputStream asn1InputStream = new ASN1InputStream(sodBytes)) {
        ASN1Primitive asn1Primitive = asn1InputStream.readObject();

        // Check if wrapped with ICAO Tag 0x77
        if (!(asn1Primitive instanceof ASN1TaggedObject tagged)) {
            // Already unwrapped CMS data
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

        // Extract CMS ContentInfo
        ASN1Primitive content = tagged.getBaseObject().toASN1Primitive();
        return content.getEncoded(ASN1Encoding.DER);
    } catch (Exception e) {
        throw new InfrastructureException(
            "SOD_UNWRAP_ERROR",
            "Failed to unwrap ICAO Tag 0x77: " + e.getMessage()
        );
    }
}
```

**Required Imports**:
```java
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.BERTags;
import org.bouncycastle.asn1.ASN1Encoding;
```

---

### Task 2: Fix DSC Certificate Extraction ✅

**File**: `BouncyCastleSodParserAdapter.java`
**Method**: `extractDscCertificate()`

**Action**:
- [ ] Use `CertificateFactory` instead of `JcaX509CertificateConverter`
- [ ] Create `ByteArrayInputStream` from `certHolder.getEncoded()`
- [ ] Generate certificate using `certFactory.generateCertificate()`
- [ ] Add proper exception handling

**Implementation**:
```java
@Override
public Optional<X509Certificate> extractDscCertificate(byte[] sodBytes) {
    try {
        byte[] cmsBytes = unwrapIcaoSod(sodBytes);
        CMSSignedData cmsSignedData = new CMSSignedData(cmsBytes);

        Store<X509CertificateHolder> certStore = cmsSignedData.getCertificates();
        if (certStore.getMatches(null).isEmpty()) {
            return Optional.empty();
        }

        X509CertificateHolder certHolder = certStore.getMatches(null).iterator().next();

        // Use CertificateFactory (like sod_example)
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        try (ByteArrayInputStream bis = new ByteArrayInputStream(certHolder.getEncoded())) {
            X509Certificate dscCert = (X509Certificate) certFactory.generateCertificate(bis);
            return Optional.of(dscCert);
        }
    } catch (Exception e) {
        throw new InfrastructureException(
            "DSC_EXTRACTION_ERROR",
            "Failed to extract DSC from SOD: " + e.getMessage()
        );
    }
}
```

**Required Imports**:
```java
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
```

---

### Task 3: Update SOD Signature Verification ✅

**File**: `BouncyCastleSodParserAdapter.java`
**Method**: `verifySignature()`

**Action**:
- [ ] Extract `SignerInformation` from `CMSSignedData`
- [ ] Use `JcaSimpleSignerInfoVerifierBuilder` (like sod_example)
- [ ] Verify signature with DSC certificate
- [ ] Return boolean result

**Implementation**:
```java
@Override
public boolean verifySignature(byte[] sodBytes, X509Certificate dscCertificate) {
    try {
        byte[] cmsBytes = unwrapIcaoSod(sodBytes);
        CMSSignedData cmsSignedData = new CMSSignedData(cmsBytes);

        // Extract signer information
        SignerInformation signerInfo = cmsSignedData
            .getSignerInfos()
            .getSigners()
            .iterator()
            .next();

        // Verify signature (like sod_example)
        boolean valid = signerInfo.verify(
            new JcaSimpleSignerInfoVerifierBuilder()
                .setProvider("BC")
                .build(dscCertificate)
        );

        return valid;
    } catch (Exception e) {
        throw new InfrastructureException(
            "SOD_SIGNATURE_VERIFICATION_ERROR",
            "Failed to verify SOD signature: " + e.getMessage()
        );
    }
}
```

**Required Imports**:
```java
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
```

---

### Task 4: Update PassiveAuthenticationService ✅

**File**: `PassiveAuthenticationService.java`
**Method**: `verifyPassport()`

**Action**:
- [ ] Use `sodParserPort.extractDscCertificate()` to get DSC from SOD
- [ ] Remove DSC LDAP lookup (ICAO 9303 compliance)
- [ ] Extract CSCA DN from DSC issuer field
- [ ] Lookup CSCA in LDAP by issuer DN
- [ ] Verify DSC signature with CSCA public key
- [ ] Verify SOD signature with DSC

**Implementation**:
```java
public PassportVerificationDetails verifyPassport(
    byte[] sodBytes,
    Map<DataGroupNumber, byte[]> dataGroups
) {
    try {
        // 1. Extract DSC from SOD (ICAO 9303 standard)
        X509Certificate dscCert = sodParserPort.extractDscCertificate(sodBytes)
            .orElseThrow(() -> new PassiveAuthenticationDomainException(
                "DSC_EXTRACTION_FAILED",
                "Failed to extract DSC certificate from SOD"
            ));

        // 2. Extract CSCA DN from DSC issuer
        String cscaDn = dscCert.getIssuerX500Principal().getName();

        // 3. Retrieve CSCA from LDAP
        Certificate cscaCert = retrieveCscaBySubjectDn(cscaDn)
            .orElseThrow(() -> new PassiveAuthenticationDomainException(
                "CSCA_NOT_FOUND",
                String.format("CSCA not found in LDAP: %s", cscaDn)
            ));

        X509Certificate cscaX509 = cscaCert.getX509Data().getX509Certificate();

        // 4. Verify DSC trust chain (DSC signed by CSCA)
        try {
            dscCert.verify(cscaX509.getPublicKey());
        } catch (Exception e) {
            throw new PassiveAuthenticationDomainException(
                "TRUST_CHAIN_INVALID",
                "DSC signature verification failed with CSCA public key: " + e.getMessage()
            );
        }

        // 5. Verify SOD signature (SOD signed by DSC)
        boolean sodValid = sodParserPort.verifySignature(sodBytes, dscCert);
        if (!sodValid) {
            throw new PassiveAuthenticationDomainException(
                "SOD_SIGNATURE_INVALID",
                "SOD signature verification failed with DSC"
            );
        }

        // 6. Verify Data Group hashes
        Map<DataGroupNumber, byte[]> sodHashes = sodParserPort.parseDataGroupHashes(sodBytes);
        String hashAlgorithm = sodParserPort.extractHashAlgorithm(sodBytes);

        verifyDataGroupHashes(dataGroups, sodHashes, hashAlgorithm);

        // 7. Check CRL (optional)
        // ... (existing CRL check logic)

        return PassportVerificationDetails.valid(/* ... */);

    } catch (PassiveAuthenticationDomainException e) {
        return PassportVerificationDetails.invalid(e.getMessage());
    }
}
```

---

### Task 5: Add Integration Test for SOD Parsing ✅

**File**: `BouncyCastleSodParserAdapterTest.java` (new)
**Location**: `src/test/java/com/smartcoreinc/localpkd/passiveauthentication/infrastructure/adapter/`

**Action**:
- [ ] Create integration test for SOD parsing
- [ ] Use real Korean passport SOD file
- [ ] Test Tag 0x77 unwrapping
- [ ] Test DSC extraction
- [ ] Test signature verification
- [ ] Test Data Group hash parsing

**Implementation**:
```java
@SpringBootTest
class BouncyCastleSodParserAdapterTest {

    @Autowired
    private SodParserPort sodParserPort;

    private byte[] koreanSodBytes;

    @BeforeEach
    void setUp() throws IOException {
        koreanSodBytes = Files.readAllBytes(
            Paths.get("src/test/resources/passport-fixtures/valid-korean-passport/sod.bin")
        );
    }

    @Test
    @DisplayName("Should extract DSC certificate from Korean passport SOD")
    void shouldExtractDscFromSod() {
        // When
        Optional<X509Certificate> dscOpt = sodParserPort.extractDscCertificate(koreanSodBytes);

        // Then
        assertThat(dscOpt).isPresent();
        X509Certificate dsc = dscOpt.get();

        assertThat(dsc.getSubjectX500Principal().getName())
            .contains("CN=DS0120200313 1");
        assertThat(dsc.getIssuerX500Principal().getName())
            .contains("CN=CSCA003");
    }

    @Test
    @DisplayName("Should verify SOD signature with DSC")
    void shouldVerifySodSignature() throws Exception {
        // Given
        X509Certificate dsc = sodParserPort.extractDscCertificate(koreanSodBytes).orElseThrow();

        // When
        boolean valid = sodParserPort.verifySignature(koreanSodBytes, dsc);

        // Then
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("Should parse Data Group hashes from SOD")
    void shouldParseDataGroupHashes() {
        // When
        Map<DataGroupNumber, byte[]> hashes = sodParserPort.parseDataGroupHashes(koreanSodBytes);

        // Then
        assertThat(hashes).isNotEmpty();
        assertThat(hashes).containsKey(DataGroupNumber.DG1);
        assertThat(hashes).containsKey(DataGroupNumber.DG2);
    }
}
```

---

### Task 6: Update Test Data (If Needed) ✅

**Files**:
- `V100__Insert_Test_CSCA.sql`
- `V101__Insert_Test_DSC.sql`

**Action**:
- [ ] Verify CSCA subject DN matches DSC issuer DN
- [ ] Verify serial numbers are in correct format
- [ ] Verify certificate encodings are valid Base64

**Verification Commands**:
```bash
# Check CSCA subject DN
openssl x509 -in src/test/resources/test-data/certificates/korean-csca.pem \
  -subject -noout

# Check DSC issuer DN
openssl x509 -in src/test/resources/test-data/certificates/korean-dsc.pem \
  -issuer -noout

# Should match!
```

---

## Testing Strategy

### Unit Tests (BouncyCastleSodParserAdapterTest)

1. **Tag 0x77 Unwrapping**
   - Valid SOD with Tag 0x77 → unwrapped successfully
   - Invalid tag → exception thrown
   - Already unwrapped CMS → returned as-is

2. **DSC Extraction**
   - Valid SOD → DSC certificate extracted
   - SOD without certificates → empty Optional
   - Corrupted SOD → exception thrown

3. **Signature Verification**
   - Valid SOD + correct DSC → true
   - Valid SOD + wrong DSC → false
   - Invalid signature → false

### Integration Tests (PassiveAuthenticationControllerTest)

**Target**: Fix 3 failing tests
1. `shouldVerifyValidPassport` - Complete PA flow
2. `shouldReturnInvalidStatusForTamperedPassport` - Tampered data detection
3. `shouldReturn404WhenDscNotFound` - Proper 404 handling

**Expected Results**:
- Before: 11/20 tests passing (55%)
- After: 14/20 tests passing (70%)
- Improvement: +3 tests (+15%)

---

## Success Criteria

### Must Have ✅

- [ ] Tag 0x77 unwrapping using ASN.1 parsing (like sod_example)
- [ ] DSC extraction returns valid X509Certificate
- [ ] SOD signature verification works with extracted DSC
- [ ] PassiveAuthenticationService uses extracted DSC (no LDAP lookup)
- [ ] Trust chain verification works (DSC → CSCA)
- [ ] 14/20 tests passing (70%)

### Should Have ⏳

- [ ] Integration tests for SOD parsing
- [ ] Proper error messages for SOD parsing failures
- [ ] Logging for debugging

### Nice to Have 🎁

- [ ] Performance benchmarks for SOD parsing
- [ ] Support for multiple DSC certificates in SOD

---

## References

### Code Examples

- **src/sod_example/SODParser.java** - Tag 0x77 unwrapping, DSC extraction
- **src/sod_example/SODSignatureVerifier.java** - Signature verification
- **src/sod_example/DataGroupHashVerifier.java** - Hash verification

### Documentation

- [ICAO Doc 9303 Part 10](https://www.icao.int/publications/Documents/9303_p10_cons_en.pdf) - LDS Structure
- [ICAO Doc 9303 Part 11](https://www.icao.int/publications/Documents/9303_p11_cons_en.pdf) - Passive Authentication
- [RFC 5652](https://www.rfc-editor.org/rfc/rfc5652) - CMS (PKCS#7)

### Previous Sessions

- [SESSION_2025-12-19_PA_PHASE_4_11_2_LDAP_TEST_FIXTURES.md](./SESSION_2025-12-19_PA_PHASE_4_11_2_LDAP_TEST_FIXTURES.md)
- [SESSION_2025-12-19_PA_PHASE_4_10_ICAO_COMPLIANCE.md](./SESSION_2025-12-19_PA_PHASE_4_10_ICAO_COMPLIANCE.md)
- [SESSION_2025-12-18_PA_PHASE_4_9_DSC_EXTRACTION.md](./SESSION_2025-12-18_PA_PHASE_4_9_DSC_EXTRACTION.md)

---

## Estimated Timeline

| Task | Estimated Time | Dependency |
|------|---------------|-----------|
| Fix unwrapIcaoSod() | 30 min | None |
| Fix extractDscCertificate() | 20 min | Task 1 |
| Update verifySignature() | 20 min | Task 2 |
| Update PassiveAuthenticationService | 40 min | Task 3 |
| Create integration tests | 30 min | Task 4 |
| Run all tests | 10 min | Task 5 |
| Debug failures | 30 min | Task 6 |
| **Total** | **3 hours** | |

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
