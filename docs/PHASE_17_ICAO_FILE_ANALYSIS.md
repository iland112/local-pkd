=================================================================
                    ICAO PKD FILE STRUCTURE ANALYSIS
                         Phase 17 Task 8
=================================================================

## 🎯 CRITICAL FINDINGS

### 1️⃣ icaopkd-001 (79MB) - DSC + CRL Collection
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Root DN: dc=data,dc=download,dc=pkd,dc=icao,dc=int

CONTAINS TWO SECTIONS:
  ├─ o=dsc (DSC - Document Signing Certificates)
  │  └─ Entry Format:
  │     dn: cn=...,o=dsc,c=XX,dc=data,dc=download,dc=pkd,dc=icao,dc=int
  │     objectClass: inetOrgPerson, pkdDownload, organizationalPerson
  │     userCertificate;binary:: <X.509 DER-encoded certificate>
  │     pkdVersion: <version number>
  │     cn: <subject CN>
  │     sn: <serial number>
  │
  └─ o=crl (CRL - Certificate Revocation Lists) [🆕 DISCOVERED]
     └─ Entry Format:
        dn: cn=...,o=crl,c=XX,dc=data,dc=download,dc=pkd,dc=icao,dc=int
        objectClass: cRLDistributionPoint, pkdDownload, top
        certificateRevocationList;binary:: <CRL DER-encoded binary>
        pkdVersion: <version number>
        cn: <issuer CN>
        pkdConformanceText: <warnings if any>
        pkdConformanceCode: <WARN:CRL.ISS.11 etc>

STATISTICS:
  ✅ DSC Entries: ~1,150 (counted via pkdVersion max ~1150)
  ✅ CRL Entries: 28 (exact count via grep)
  ✅ Total Size: 79MB (uncompressed LDIF)

BINARY FORMATS:
  - userCertificate;binary:: = X.509 certificate (SubjectPublicKeyInfo format)
  - certificateRevocationList;binary:: = X.509 CRL (DER-encoded)
  - Both use Base64 encoding in LDIF (wrapped at ~76 characters)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

### 2️⃣ icaopkd-002 (10MB) - Master List Collection
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Root DN: dc=data,dc=download,dc=pkd,dc=icao,dc=int (Same as icaopkd-001)

CONTAINS SINGLE SECTION:
  └─ o=ml (Master List - CMS Signed)
     └─ Entry Format:
        dn: cn=CN\=CSCA-XXX\,...,o=ml,c=XX,dc=data,dc=download,dc=pkd,dc=icao,dc=int
        objectClass: top, person, pkdMasterList, pkdDownload
        pkdMasterListContent:: <CMS-signed Master List binary>
        pkdVersion: <version number>
        cn: <CSCA CN>
        sn: <serial number>

STATISTICS:
  ✅ ML Entries: ~70 (estimated from pkdVersion max ~70)
  ✅ Total Size: 10MB
  ✅ Format: CMS (Cryptographic Message Syntax) - DIFFERENT from X.509 certs

⚠️  IMPORTANT: This is NOT for DSC/CRL testing! DO NOT use for Phase 17 CRL test.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

### 3️⃣ icaopkd-003 (2MB) - DSC-NC (Non-Conforming) Collection
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Root DN: dc=nc-data,dc=download,dc=pkd,dc=icao,dc=int [DIFFERENT ROOT!]

KEY DIFFERENCE: Uses "nc-data" instead of "data"
  - Indicates "Non-Conforming" certificates
  - Certificates with warnings/errors

CONTAINS SINGLE SECTION:
  └─ o=dsc (DSC - Non-Conforming Documents Signing Certificates)
     └─ Entry Format:
        dn: cn=...,o=dsc,c=XX,dc=nc-data,dc=download,dc=pkd,dc=icao,dc=int
        objectClass: inetOrgPerson, pkdDownload, organizationalPerson
        userCertificate;binary:: <X.509 certificate with errors>
        pkdConformanceText: ERR:CSCA:Extension:BasicConstraints:...
        pkdVersion: <version number>

STATISTICS:
  ✅ DSC-NC Entries: ~16 (estimated from pkdVersion max ~16)
  ✅ Total Size: 2MB
  ✅ Format: Same as icaopkd-001 DSC, but with conformance errors

⚠️  CAUTION: These are non-conforming certificates. May need special handling.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 📊 DELTA FILES ANALYSIS

Each collection has 10 delta files (from current version - 9 to current):

### icaopkd-001 Deltas
  - icaopkd-001-delta-009400.ldif through 009409.ldif
  - Range: Version 009400 → 009409
  - Size progression: Mix of small (12 bytes) and large (~1.3MB) files
  - Pattern: Most deltas are incremental updates; some versions are empty (just headers)

### icaopkd-002 Deltas  
  - icaopkd-002-delta-000316.ldif through 000325.ldif
  - Range: Version 000316 → 000325
  - Size progression: Smaller than icaopkd-001 (mostly 10-200KB)

### icaopkd-003 Deltas
  - icaopkd-003-delta-000081.ldif through 000090.ldif
  - Range: Version 000081 → 000090
  - Size progression: Very small (mostly empty/12 bytes, occasional updates)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 🔑 KEY INSIGHTS FOR PHASE 17 TESTING

### ✅ CORRECT FILE FOR CRL TESTING
Use: icaopkd-001-complete-009409.ldif (79MB)
  - Contains BOTH DSC (o=dsc) and CRL (o=crl) entries
  - CRL count: 28 entries
  - Matches the "34 CRLs extracted" from previous session
    (Some DSC entries may also have been counted, need verification)

### ❌ WRONG FILES
  - icaopkd-002: Master List only (ML with CMS format), NOT for CRL
  - icaopkd-003: DSC-NC (non-conforming), separate collection

### 📈 EXPANSION STRATEGY
1. Current test with icaopkd-001-complete-009409.ldif (full DSC+CRL)
2. Delta tests with icaopkd-001-delta-*.ldif (incremental updates)
3. Future: ML testing with icaopkd-002 (different parser needed)
4. Future: NC testing with icaopkd-003 (error handling testing)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 🏗️ DN TRANSFORMATION RULE

All entries when uploaded to OpenLDAP must transform:
  FROM: dn: cn=...,o=XXX,c=YY,dc=data,dc=download,dc=pkd,dc=icao,dc=int
  TO:   dn: cn=...,o=XXX,c=YY,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
              (only baseDN changes ↑                                ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑)

For NC entries:
  FROM: dn: cn=...,o=dsc,c=YY,dc=nc-data,dc=download,dc=pkd,dc=icao,dc=int
  TO:   dn: cn=...,o=dsc,c=YY,dc=nc-data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com

✅ DN structure (o=, c=, dc=data/nc-data/download/pkd) MUST be preserved exactly!

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 📝 PARSING REQUIREMENTS

### For LDIF Parser
1. ✅ DSC entries: Extract userCertificate;binary::
2. ✅ CRL entries: Extract certificateRevocationList;binary::
3. ✅ ML entries: Extract pkdMasterListContent::
4. ✅ Decode Base64 to binary DER format
5. ✅ Preserve all DN components exactly as-is

### For Validation Pipeline
1. DSC → X.509 certificate validation (Trust Chain)
2. CRL → CRL parsing and revocation checking
3. ML → CMS signature verification

### For LDAP Upload Pipeline
1. Transform DN baseDN only (dc=icao,dc=int → dc=ldap,dc=smartcoreinc,dc=com)
2. Create LDAP entries with transformed DN
3. Copy all original attributes/values exactly
4. Create index/search capabilities for:
   - By country (c=)
   - By organization unit (o=dsc, o=crl, o=ml)
   - By subject/issuer CN

=================================================================
