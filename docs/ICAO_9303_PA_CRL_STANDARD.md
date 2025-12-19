# ICAO 9303 Passive Authentication + CRL Standard Procedure

**Document Version**: 1.0
**Last Updated**: 2025-12-19
**Status**: Reference Documentation
**Related Phases**: Phase 4.12 (CRL Checking Implementation)

---

## 📖 Overview

This document describes the **ICAO Doc 9303 Part 11 and Part 12** standard procedures for Passive Authentication (PA) including Certificate Revocation List (CRL) checking for ePassport verification.

### Document Sources

- **ICAO Doc 9303 Part 11**: Security Mechanisms for MRTDs
- **ICAO Doc 9303 Part 12**: Public Key Infrastructure for MRTDs
- **RFC 5280**: Internet X.509 Public Key Infrastructure Certificate and CRL Profile
- **RFC 5652**: Cryptographic Message Syntax (CMS)

---

## 🔐 ICAO 9303 Part 11 - Passive Authentication Standard Procedure

### Step 1: Read SOD from Contactless IC

**ICAO Requirement** (SHALL):
```
The Inspection System SHALL read the Document Security Object (SOD)
from the contactless IC.

Requirements:
- SOD MUST contain the Document Signer Certificate (DSC)
- Read from ePassport's contactless integrated circuit chip
- SOD format: PKCS#7 CMS SignedData (RFC 5652)
```

**Implementation**:
```java
// Client sends SOD bytes to API
byte[] sodBytes = request.getSod(); // Base64 decoded
```

---

### Step 2: Build and Validate Certification Path

**ICAO Requirement** (SHALL):
```
The Inspection System SHALL build and validate a certification path
from a Trust Anchor to the Document Signer Certificate used to sign
the Document Security Object (SOD) according to Doc 9303-12.

Trust Chain:
  Trust Anchor (CSCA) → Document Signer Certificate (DSC) → SOD
```

**Implementation**:
```java
// 2.1 Unwrap ICAO 9303 Tag 0x77 wrapper
byte[] cmsBytes = unwrapIcaoSod(sodBytes);

// 2.2 Extract DSC from SOD certificates [0]
X509Certificate dscCert = extractDscCertificate(cmsBytes);

// 2.3 Get CSCA DN from DSC Issuer field
String cscaSubjectDn = dscCert.getIssuerX500Principal().getName();

// 2.4 LDAP lookup: Find CSCA by Subject DN
X509Certificate cscaCert = ldapAdapter.findCscaBySubjectDn(cscaSubjectDn, countryCode);

// 2.5 Verify DSC Trust Chain
dscCert.verify(cscaCert.getPublicKey());

// 2.6 Check DSC validity period
dscCert.checkValidity();
```

**Trust Chain Validation**:
```
┌─────────────────────────────────────────┐
│ Trust Anchor: CSCA                      │
│ - Self-signed root certificate          │
│ - Issued by Country Authority           │
│ - Stored in ICAO PKD (LDAP)            │
└────────────┬────────────────────────────┘
             │ signs
             ↓
┌─────────────────────────────────────────┐
│ Document Signer Certificate (DSC)       │
│ - Issued by CSCA                        │
│ - Embedded in SOD                       │
│ - Signs ePassport data                  │
└────────────┬────────────────────────────┘
             │ signs
             ↓
┌─────────────────────────────────────────┐
│ Security Object Document (SOD)          │
│ - Contains Data Group hashes            │
│ - PKCS#7 CMS SignedData                │
│ - Stored in ePassport chip             │
└─────────────────────────────────────────┘
```

---

### Step 3: Verify DSC Signature on SOD

**ICAO Requirement** (SHALL):
```
The Inspection System SHALL use the verified Document Signer Public Key
to verify the signature of the Document Security Object (SOD).
```

**Implementation**:
```java
// 3.1 Parse CMS SignedData
CMSSignedData cmsSignedData = new CMSSignedData(cmsBytes);

// 3.2 Verify SOD signature using DSC public key
SignerInformationVerifier verifier =
    new JcaSimpleSignerInfoVerifierBuilder()
        .build(dscCert.getPublicKey());

SignerInformation signer = cmsSignedData.getSignerInfos().iterator().next();
boolean signatureValid = signer.verify(verifier);

if (!signatureValid) {
    throw new DomainException("SOD_SIGNATURE_INVALID",
        "SOD signature verification failed");
}
```

---

### Step 4: Verify Data Group Hashes

**ICAO Requirement** (SHALL):
```
The Inspection System MAY read relevant Data Groups from the contactless IC.
The Inspection System SHALL ensure that the contents of the Data Group are
authentic and unchanged by hashing the contents and comparing the result
with the corresponding hash value in the Document Security Object (SOD).
```

**Implementation**:
```java
// 4.1 Extract expected hashes from SOD
Map<Integer, byte[]> expectedHashes = parseDataGroupHashes(sodBytes);

// 4.2 Compute actual hashes from client Data Groups
for (Map.Entry<Integer, String> dgEntry : request.getDataGroups().entrySet()) {
    int dgNumber = dgEntry.getKey();
    byte[] dgBytes = Base64.getDecoder().decode(dgEntry.getValue());

    // 4.3 Hash the Data Group
    MessageDigest digest = MessageDigest.getInstance(hashAlgorithm); // SHA-256/384/512
    byte[] actualHash = digest.digest(dgBytes);

    // 4.4 Compare with expected hash
    byte[] expectedHash = expectedHashes.get(dgNumber);
    if (!MessageDigest.isEqual(expectedHash, actualHash)) {
        throw new DomainException("DATA_GROUP_HASH_MISMATCH",
            "Data Group " + dgNumber + " hash verification failed");
    }
}
```

---

## 📋 ICAO 9303 Part 12 - Certificate Revocation List (CRL) Checking

### CRL Definition

**ICAO Doc 9303 Part 12**:
```
Certificate Revocation List (CRL):
- List of revoked certificates (CSCA, DSC, MLSC, DLSC, etc.)
- Signed by valid CSCA of issuing authority
- Format: X.509 CRL (RFC 5280)
- Distribution: ICAO PKD (Public Key Directory) via LDAP
```

### CRL Purpose

1. **Revocation Notification**: States notify all countries of compromised certificates
2. **Trust Verification**: Ensures Passive Authentication doesn't trust invalid data
3. **Security Enhancement**: Detects technical errors or malicious certificates

### CRL Structure (LDAP)

**DN Format**:
```
cn={ISSUER-NAME},o=crl,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}

Example:
cn=CN\=CSCA-KOREA\,O\=Government\,C\=KR,o=crl,c=KR,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com

ObjectClasses:
- top
- cRLDistributionPoint

Attributes:
- cn: {ISSUER-NAME} (CSCA Subject DN)
- certificateRevocationList;binary: {BASE64-ENCODED-CRL}
```

---

## 🔍 CRL Checking in PA Workflow

### Standard PKI Certificate Validation (RFC 5280)

**CRL Check Position**:
```
1. Build Certification Path (Trust Chain)
   ↓
2. Verify Certificate Signatures
   ↓
3. Check Certificate Validity Periods (notBefore, notAfter)
   ↓
4. ✅ **Check Certificate Revocation Status (CRL)** ← INSERT HERE
   ↓
5. Verify End-Entity Certificate (DSC)
   ↓
6. Use DSC for SOD Verification
```

**Rationale**:
- **After Trust Chain Validation** (CSCA verified)
- **Before Using DSC** (Prevent using revoked certificate)
- **Industry Best Practice** (RFC 5280 recommendation)

---

### CRL Checking Procedure

#### Step 1: Retrieve CRL from LDAP

**LDAP Search**:
```java
// 1.1 Build LDAP filter (RFC 4515 escaping required)
String escapedCscaDn = LdapFilterEscaper.escape(cscaSubjectDn);
String filter = String.format(
    "(&(objectClass=cRLDistributionPoint)(cn=%s))",
    escapedCscaDn
);

// 1.2 Search in LDAP
String baseDn = String.format(
    "o=crl,c=%s,dc=data,dc=download,dc=pkd,%s",
    countryCode,
    ldapBaseDn
);

SearchResult result = ldapConnection.search(baseDn, SearchScope.SUB, filter);

// 1.3 Extract CRL binary
byte[] crlBytes = result.getAttribute("certificateRevocationList;binary");
```

#### Step 2: Verify CRL Signature

**CRL Signature Verification**:
```java
// 2.1 Parse X.509 CRL
CertificateFactory cf = CertificateFactory.getInstance("X.509");
X509CRL crl = (X509CRL) cf.generateCRL(new ByteArrayInputStream(crlBytes));

// 2.2 Verify CRL signature using CSCA public key
crl.verify(cscaCert.getPublicKey());

// 2.3 Check CRL issuer matches CSCA
String crlIssuer = crl.getIssuerX500Principal().getName();
String cscaSubject = cscaCert.getSubjectX500Principal().getName();

if (!crlIssuer.equals(cscaSubject)) {
    throw new DomainException("CRL_ISSUER_MISMATCH",
        "CRL issuer does not match CSCA subject");
}
```

#### Step 3: Check CRL Freshness

**CRL Validity Period Check**:
```java
// 3.1 Get CRL validity dates
Date thisUpdate = crl.getThisUpdate();  // CRL issue date
Date nextUpdate = crl.getNextUpdate();  // CRL expiration date
Date now = new Date();

// 3.2 Validate CRL is current
if (now.before(thisUpdate)) {
    throw new CrlNotYetValidException("CRL not yet valid");
}

if (now.after(nextUpdate)) {
    throw new CrlExpiredException("CRL has expired");
}
```

#### Step 4: Check Certificate Revocation

**DSC Revocation Check**:
```java
// 4.1 Get DSC serial number
BigInteger dscSerialNumber = dscCert.getSerialNumber();

// 4.2 Check if DSC is in revoked list
X509CRLEntry revokedEntry = crl.getRevokedCertificate(dscSerialNumber);

// 4.3 Handle revocation
if (revokedEntry != null) {
    Date revocationDate = revokedEntry.getRevocationDate();
    CRLReason reason = getCRLReason(revokedEntry);

    throw new CertificateRevokedException(
        String.format("DSC has been revoked on %s. Reason: %s",
            revocationDate, reason)
    );
}

// 4.4 DSC is not revoked - proceed
```

---

## 📊 Complete PA + CRL Workflow (Implementation)

### Full Verification Flow

```
┌──────────────────────────────────────────────────────────────────────┐
│ ICAO 9303 Part 11 Passive Authentication + CRL Checking             │
└──────────────────────────────────────────────────────────────────────┘

1. Client → API: SOD + Data Groups (DG1, DG2, ...)
   POST /api/pa/verify
   {
     "sod": "base64...",
     "dataGroups": {
       "1": "base64...",  // MRZ
       "2": "base64..."   // Face image
     },
     "countryCode": "KOR"
   }
   ↓
2. Unwrap ICAO 9303 Tag 0x77
   byte[] cmsBytes = unwrapIcaoSod(sodBytes);
   ↓
3. Extract DSC from SOD
   X509Certificate dscCert = extractDscCertificate(cmsBytes);
   ⚠️ ICAO Standard: DSC embedded in SOD certificates [0]
   ↓
4. Get CSCA DN from DSC Issuer
   String cscaSubjectDn = dscCert.getIssuerX500Principal().getName();
   ↓
5. LDAP Lookup: Find CSCA
   X509Certificate cscaCert = ldapAdapter.findCscaBySubjectDn(cscaSubjectDn, countryCode);
   DN: cn={CSCA-DN},o=csca,c={COUNTRY},dc=data,dc=download,dc=pkd,...
   ↓
6. ✅ Verify DSC Trust Chain
   dscCert.verify(cscaCert.getPublicKey());
   dscCert.checkValidity();
   ↓
7. 🆕 **CRL Check for DSC** (Phase 4.12):
   ┌────────────────────────────────────────────────────────┐
   │ 7.1 LDAP Lookup: Find CRL by CSCA Issuer DN           │
   │     DN: cn={CSCA-DN},o=crl,c={COUNTRY},...            │
   │                                                        │
   │ 7.2 Verify CRL Signature                              │
   │     crl.verify(cscaCert.getPublicKey())               │
   │                                                        │
   │ 7.3 Check CRL Freshness                               │
   │     thisUpdate <= now <= nextUpdate                   │
   │                                                        │
   │ 7.4 Check DSC Revocation                              │
   │     if (crl.isRevoked(dscSerialNumber))               │
   │         throw CertificateRevokedException()           │
   │                                                        │
   │ Result: VALID / REVOKED / CRL_UNAVAILABLE            │
   └────────────────────────────────────────────────────────┘
   ↓
8. ✅ Verify SOD Signature
   CMSSignedData cmsSignedData = new CMSSignedData(cmsBytes);
   cmsSignedData.verify(dscCert.getPublicKey());
   ↓
9. Extract Data Group Hashes from SOD
   Map<Integer, byte[]> expectedHashes = parseDataGroupHashes(sodBytes);
   {
     1: [hash of DG1],
     2: [hash of DG2],
     ...
   }
   ↓
10. Compute Client's Data Group Hashes
    for each DataGroup:
      actualHash = SHA-256/384/512(dgBytes)
    ↓
11. Compare Hashes
    for each DataGroup:
      if (expectedHash != actualHash)
        throw DataGroupHashMismatchException()
    ↓
12. ✅ Result: VALID / INVALID / ERROR / REVOKED
    {
      "valid": true,
      "trustChainValid": true,
      "crlCheckResult": "VALID",
      "sodSignatureValid": true,
      "dataGroupHashesValid": true,
      "verifiedDataGroups": [1, 2],
      "countryCode": "KOR",
      "verifiedAt": "2025-12-19T10:30:00Z"
    }
```

---

## 🔧 Implementation Requirements (Phase 4.12)

### 1. CRL LDAP Adapter

**Port Interface**:
```java
public interface CrlLdapPort {
    /**
     * Retrieves CRL for given CSCA from LDAP directory.
     *
     * @param cscaSubjectDn CSCA Subject DN (RFC 4514 format)
     * @param countryCode ISO 3166-1 alpha-2 country code
     * @return X509CRL or empty if not found
     */
    Optional<X509CRL> findCrlByCsca(String cscaSubjectDn, String countryCode);
}
```

**Adapter Implementation**:
```java
@Component
public class UnboundIdCrlLdapAdapter implements CrlLdapPort {

    @Override
    public Optional<X509CRL> findCrlByCsca(String cscaSubjectDn, String countryCode) {
        // 1. Build LDAP filter (RFC 4515 escaping)
        String filter = buildCrlFilter(cscaSubjectDn);

        // 2. Search LDAP
        String baseDn = buildCrlBaseDn(countryCode);
        SearchResult result = ldapConnection.search(baseDn, SearchScope.SUB, filter);

        // 3. Extract and parse CRL
        byte[] crlBytes = result.getAttribute("certificateRevocationList;binary");
        X509CRL crl = parseCrl(crlBytes);

        return Optional.of(crl);
    }
}
```

---

### 2. CRL Verification Service

**Domain Service**:
```java
public class CrlVerificationService {

    /**
     * Verifies certificate against CRL.
     *
     * @param certificate Certificate to check
     * @param crl CRL to check against
     * @param issuerCert CRL issuer certificate (CSCA)
     * @return CrlCheckResult
     */
    public CrlCheckResult verifyCertificate(
            X509Certificate certificate,
            X509CRL crl,
            X509Certificate issuerCert) {

        // 1. Verify CRL signature
        crl.verify(issuerCert.getPublicKey());

        // 2. Check CRL freshness
        validateCrlFreshness(crl);

        // 3. Check certificate revocation
        if (crl.isRevoked(certificate)) {
            X509CRLEntry entry = crl.getRevokedCertificate(certificate.getSerialNumber());
            return CrlCheckResult.revoked(entry.getRevocationDate());
        }

        return CrlCheckResult.valid();
    }

    private void validateCrlFreshness(X509CRL crl) {
        Date now = new Date();
        Date thisUpdate = crl.getThisUpdate();
        Date nextUpdate = crl.getNextUpdate();

        if (now.before(thisUpdate) || now.after(nextUpdate)) {
            throw new CrlExpiredException(
                String.format("CRL expired. thisUpdate=%s, nextUpdate=%s, now=%s",
                    thisUpdate, nextUpdate, now)
            );
        }
    }
}
```

---

### 3. CRL Caching Strategy

**Cache Requirements**:
```
- CRLs are periodically updated (typically daily/weekly)
- Cache validity: until nextUpdate time
- Cache key: CSCA Subject DN + Country Code
- Cache storage: In-memory + Database backup
```

**Implementation**:
```java
@Service
public class CrlCacheService {

    private final Map<String, CachedCrl> crlCache = new ConcurrentHashMap<>();
    private final CrlRepository crlRepository;

    public X509CRL getCrl(String cscaSubjectDn, String countryCode) {
        String cacheKey = buildCacheKey(cscaSubjectDn, countryCode);

        // 1. Check in-memory cache
        CachedCrl cached = crlCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.getCrl();
        }

        // 2. Check database cache
        Optional<CertificateRevocationList> dbCrl =
            crlRepository.findLatestByCscaDnAndCountry(cscaSubjectDn, countryCode);

        if (dbCrl.isPresent() && !isCrlExpired(dbCrl.get())) {
            X509CRL crl = parseCrl(dbCrl.get().getEncoded());
            crlCache.put(cacheKey, new CachedCrl(crl, crl.getNextUpdate()));
            return crl;
        }

        // 3. Fetch from LDAP
        X509CRL crl = crlLdapAdapter.findCrlByCsca(cscaSubjectDn, countryCode)
            .orElseThrow(() -> new CrlNotFoundException("CRL not found for CSCA: " + cscaSubjectDn));

        // 4. Update caches
        updateCache(cacheKey, crl, cscaSubjectDn, countryCode);

        return crl;
    }
}
```

---

### 4. Integration into PassiveAuthenticationService

**Modified Service**:
```java
@Service
public class PassiveAuthenticationService {

    private final CrlLdapPort crlLdapPort;
    private final CrlVerificationService crlVerificationService;

    public void verifyPassport(
            X509Certificate dscCert,
            X509Certificate cscaCert,
            String countryCode) {

        // ... (existing trust chain verification)

        // ✅ NEW: CRL Check (Step 7)
        Optional<X509CRL> crlOpt = crlLdapPort.findCrlByCsca(
            cscaCert.getSubjectX500Principal().getName(),
            countryCode
        );

        if (crlOpt.isPresent()) {
            CrlCheckResult crlResult = crlVerificationService.verifyCertificate(
                dscCert,
                crlOpt.get(),
                cscaCert
            );

            if (crlResult.isRevoked()) {
                throw new CertificateRevokedException(
                    "DSC has been revoked on " + crlResult.getRevocationDate()
                );
            }
        } else {
            // Log warning: CRL not available
            // Decision: Continue or Fail? (Configurable)
        }

        // ... (continue with SOD verification)
    }
}
```

---

## 🎯 Phase 4.12 Implementation Checklist

### Task Breakdown

- [ ] **Task 1**: CRL LDAP Adapter
  - [ ] Create `CrlLdapPort` interface
  - [ ] Implement `UnboundIdCrlLdapAdapter`
  - [ ] Add RFC 4515 LDAP filter escaping
  - [ ] Handle CRL binary attribute parsing

- [ ] **Task 2**: CRL Verification Service
  - [ ] Create `CrlVerificationService` domain service
  - [ ] Implement CRL signature verification
  - [ ] Implement CRL freshness check
  - [ ] Implement certificate revocation check
  - [ ] Create `CrlCheckResult` value object

- [ ] **Task 3**: CRL Caching
  - [ ] Design cache key strategy
  - [ ] Implement in-memory cache (ConcurrentHashMap)
  - [ ] Implement database cache fallback
  - [ ] Add cache expiration logic (nextUpdate)
  - [ ] Create `CrlCacheService`

- [ ] **Task 4**: Integration
  - [ ] Integrate CRL check into `PassiveAuthenticationService`
  - [ ] Update `PerformPassiveAuthenticationUseCase`
  - [ ] Add CRL check result to `PassiveAuthenticationResponse`
  - [ ] Update audit log with CRL check details

- [ ] **Task 5**: Testing
  - [ ] Unit tests for `CrlVerificationService`
  - [ ] Unit tests for `CrlCacheService`
  - [ ] Integration tests for `UnboundIdCrlLdapAdapter`
  - [ ] Integration tests for complete PA + CRL flow
  - [ ] Test scenarios:
    - [ ] Valid certificate (not revoked)
    - [ ] Revoked certificate
    - [ ] CRL not available
    - [ ] Expired CRL
    - [ ] Invalid CRL signature

- [ ] **Task 6**: Documentation
  - [ ] Update OpenAPI/Swagger docs
  - [ ] Add CRL check to PA workflow diagram
  - [ ] Update CLAUDE.md
  - [ ] Create session report

---

## 📚 References

### ICAO Documents
- [ICAO Doc 9303 Part 11 - Security Mechanisms for MRTDs](https://www.icao.int/sites/default/files/publications/DocSeries/9303_p11_cons_en.pdf)
- [ICAO Doc 9303 Part 12 - PKI for MRTDs](https://www.icao.int/sites/default/files/publications/DocSeries/9303_p12_cons_en.pdf)
- [ICAO PKD - ePassport Basics](https://www.icao.int/icao-pkd/epassport-basics)

### RFC Standards
- [RFC 5280 - X.509 PKI Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280)
- [RFC 5652 - Cryptographic Message Syntax (CMS)](https://www.rfc-editor.org/rfc/rfc5652)
- [RFC 4514 - LDAP DN String Representation](https://www.rfc-editor.org/rfc/rfc4514)
- [RFC 4515 - LDAP Search Filter String Representation](https://www.rfc-editor.org/rfc/rfc4515)

### Implementation References
- [ZeroPass - ICAO 9303 Overview](https://github.com/ZeroPass/Port-documentation-and-tools/blob/master/Overview%20of%20ICAO%209303.md)
- [Regula Forensics - Certificates for Electronic Identity](https://regulaforensics.com/blog/certificates-for-electronic-document-verification/)
- [Keesing Platform - PKI Understanding](https://platform.keesingtechnologies.com/understanding-public-key-infrastructure-part-2/)

---

## 📝 Change Log

| Date | Version | Changes |
|------|---------|---------|
| 2025-12-19 | 1.0 | Initial documentation - ICAO 9303 PA + CRL standard procedure |

---

**Status**: ✅ Reference Documentation Complete
**Next Phase**: Phase 4.12 Implementation
