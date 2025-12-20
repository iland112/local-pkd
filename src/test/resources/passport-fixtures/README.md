# Passport Test Fixtures

This directory contains ePassport chip data fixtures for Passive Authentication (PA) integration tests.

## Directory Structure

```
passport-fixtures/
├── README.md                           # This file
├── valid-korean-passport/              # Real Korean passport data ✅
│   ├── passport-metadata.json          # Test metadata and expected results
│   ├── sod.bin                         # Security Object Document (CMS SignedData)
│   ├── dg1.bin                         # Data Group 1 (MRZ - Machine Readable Zone)
│   ├── dg2.bin                         # Data Group 2 (Facial Image)
│   ├── dg14.bin                        # Data Group 14 (Chip Authentication)
│   └── mrz.txt                         # MRZ in text format
│
├── tampered-passport/                  # Modified data for failure tests (TODO)
├── expired-dsc/                        # Expired DSC certificate (TODO)
└── revoked-certificate/                # Revoked certificate (TODO)
```

## Test Scenarios

### 1. Valid Korean Passport ✅
- **Location**: `valid-korean-passport/`
- **Description**: Real ePassport chip data from a valid Korean passport
- **Test Coverage**:
  - ✅ Trust Chain Verification (DSC signed by CSCA003)
  - ✅ SOD Signature Verification
  - ✅ Data Group Hash Verification (DG1, DG2, DG14)
  - ✅ Certificate validity check
  - ✅ CRL check (not revoked)

**Key Characteristics**:
- Issuing Country: Korea (KOR)
- DSC: `CN=DS0120200313 1` (Serial: 295)
- CSCA: `CN=CSCA003` (must exist in LDAP)
- Hash Algorithm: SHA-256
- Data Groups: DG1 (MRZ), DG2 (Photo), DG14 (Chip Auth)
- Note: DG3 hash exists but no actual data (Korean passport characteristic)

### 2. Tampered Passport (TODO)
- **Location**: `tampered-passport/`
- **Description**: Valid passport with modified DG1 or DG2
- **Test Coverage**:
  - Data Group Hash mismatch detection
  - Integrity violation handling

### 3. Expired DSC (TODO)
- **Location**: `expired-dsc/`
- **Description**: Passport signed by expired DSC
- **Test Coverage**:
  - Certificate validity period check
  - Expired certificate rejection

### 4. Revoked Certificate (TODO)
- **Location**: `revoked-certificate/`
- **Description**: Passport with revoked DSC
- **Test Coverage**:
  - CRL checking
  - Revoked certificate rejection

## File Formats

### Binary Files
- **sod.bin**: EF.SOD (Security Object Document) with LDS wrapper (tag 0x77)
  - Contains: CMS SignedData with LDSSecurityObject and DSC
  - Size: ~1.8 KB

- **dg1.bin**: Data Group 1 (Machine Readable Zone)
  - Format: TLV-encoded MRZ data
  - Size: ~93 bytes

- **dg2.bin**: Data Group 2 (Facial Image)
  - Format: JPEG2000 encoded image
  - Size: ~12 KB

- **dg14.bin**: Data Group 14 (Chip Authentication Info)
  - Format: ASN.1 SecurityInfo
  - Size: ~302 bytes

### Metadata File
- **passport-metadata.json**: Test metadata
  - Passport details (number, holder, dates)
  - Data Group information (files, hashes, sizes)
  - DSC certificate details
  - Expected validation results
  - Test scenario descriptions

## Usage in Tests

### Example: Loading Test Fixture

```java
@Test
void shouldVerifyValidKoreanPassport() throws Exception {
    // Load test fixture
    String basePath = "passport-fixtures/valid-korean-passport/";
    byte[] sodBytes = loadTestResource(basePath + "sod.bin");
    byte[] dg1Bytes = loadTestResource(basePath + "dg1.bin");
    byte[] dg2Bytes = loadTestResource(basePath + "dg2.bin");
    byte[] dg14Bytes = loadTestResource(basePath + "dg14.bin");

    // Load metadata
    PassportMetadata metadata = loadMetadata(basePath + "passport-metadata.json");

    // Create command
    PerformPassiveAuthenticationCommand command = new PerformPassiveAuthenticationCommand(
        sodBytes, dg1Bytes, dg2Bytes, null, dg14Bytes, "test-request-id"
    );

    // Execute PA verification
    PassiveAuthenticationResponse response = performPassiveAuthenticationUseCase.execute(command);

    // Assert results
    assertThat(response.status()).isEqualTo(PassiveAuthenticationStatus.SUCCESS);
    assertThat(response.result().isTrustChainValid()).isTrue();
}

private byte[] loadTestResource(String path) throws IOException {
    return Files.readAllBytes(
        Paths.get("src/test/resources/" + path)
    );
}
```

## ICAO 9303 Compliance

All test fixtures follow **ICAO Doc 9303 Part 10** standards:

- **LDS (Logical Data Structure)**: Data Groups organized per ICAO specification
- **SOD Structure**: CMS SignedData with LDSSecurityObject
- **Hash Algorithm**: SHA-256 (OID: 2.16.840.1.101.3.4.2.1)
- **DSC Signature**: RSASSA-PSS (OID: 1.2.840.113549.1.1.10)

## Security & Privacy

⚠️ **IMPORTANT**:
- The `valid-korean-passport` fixtures contain **REAL** ePassport chip data
- Sensitive personal information (name, passport number, photo) is included
- These fixtures are for **TESTING PURPOSES ONLY**
- DO NOT commit real passport photos to public repositories
- Consider anonymizing data for open-source projects

## References

- ICAO Doc 9303 Part 10: Logical Data Structure (LDS)
- ICAO Doc 9303 Part 11: Security Mechanisms for MRTDs
- BSI TR-03110: Advanced Security Mechanisms for Machine Readable Travel Documents

## Test Data Maintenance

To update test fixtures:

1. Extract new ePassport chip data (requires NFC reader)
2. Save binary files (SOD, DG1, DG2, DG14) to appropriate directory
3. Update `passport-metadata.json` with new hashes and certificate info
4. Verify data integrity with RealPassportDataAnalysisTest
5. Update integration tests if needed

---

**Last Updated**: 2025-12-17
**ICAO Standard**: 9303 Part 10/11
**Test Framework**: JUnit 5 + Spring Boot Test
