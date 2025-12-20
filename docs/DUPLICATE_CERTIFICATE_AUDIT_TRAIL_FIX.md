# Duplicate Certificate Audit Trail Fix

**Date**: 2025-12-05
**Migration**: V17__Revert_Parsed_Certificate_PK_For_Audit_Trail.sql
**Status**: ✅ COMPLETED

---

## Problem Description

When uploading ICAO PKD files periodically for updates, duplicate certificates appear in new files. The system was rejecting these uploads with a database constraint violation:

```
ERROR: duplicate key value violates unique constraint "parsed_certificate_pkey"
Detail: Key (fingerprint_sha256)=(5ba9a2069f34cd93fab9dcb39c6a7a7be62141f8bcb608aff815167c813ecf92) already exists.
```

### Root Cause

**V7 Migration** (Previous): Changed `parsed_certificate` primary key from `(parsed_file_id, serial_number)` to `fingerprint_sha256`
- Reason: Serial numbers are not globally unique (same serial can exist across different CAs)
- Side Effect: Made fingerprint_sha256 globally unique across ALL uploads
- Problem: Same certificate cannot appear in multiple ParsedFiles

### User Requirement

> "ICAO PKD 파일을 다운 받을 때 주기적으로 변경된 내역을 업데이트 해야 하므로 증복 인증서가 발생할 수 있음을 감안하자. 중복된 인증서는 저장하지 않더라도 중복 이력은 기록해둬야. 나중에 audit 에 도움이 되지 않을까?"

**Translation**: When periodically downloading PKD files for updates, duplicate certificates will occur. Even if we don't save duplicate Certificate entities, we should record the duplicate history for audit purposes.

---

## Solution

**V17 Migration**: Revert primary key to composite `(parsed_file_id, fingerprint_sha256)`

### Schema Changes

```sql
-- 1. Drop existing primary key constraint (fingerprint_sha256)
ALTER TABLE parsed_certificate
    DROP CONSTRAINT parsed_certificate_pkey;

-- 2. Add new composite primary key on (parsed_file_id, fingerprint_sha256)
ALTER TABLE parsed_certificate
    ADD CONSTRAINT parsed_certificate_pkey PRIMARY KEY (parsed_file_id, fingerprint_sha256);

-- 3. Create index on fingerprint_sha256 for global lookups
CREATE INDEX IF NOT EXISTS idx_parsed_certificate_fingerprint ON parsed_certificate(fingerprint_sha256);

-- 4. Drop the old index on (parsed_file_id, serial_number)
DROP INDEX IF EXISTS idx_parsed_certificate_file_serial;
```

### Design Rationale

The composite PK `(parsed_file_id, fingerprint_sha256)` provides:

1. **Audit Trail**: Same certificate can appear in multiple ParsedFiles (different uploads)
2. **Within-File Uniqueness**: Each ParsedFile can have at most one copy of each certificate
3. **Global Deduplication**: Certificate entities in the `certificate` table remain globally unique

### Two-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ ParsedCertificate (parsed_certificate table)                │
│ - Temporary parsing results for each upload                 │
│ - PK: (parsed_file_id, fingerprint_sha256)                  │
│ - Allows same cert in multiple uploads (audit trail)        │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ deduplicated
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ Certificate (certificate table)                             │
│ - Persistent certificate storage                            │
│ - PK: id (UUID)                                              │
│ - Unique: fingerprint_sha256 (global uniqueness)            │
│ - Duplicate certificates are NOT saved here                 │
└─────────────────────────────────────────────────────────────┘
```

---

## Benefits

### 1. Audit Trail Support

Each upload maintains a complete record of ALL certificates in the file, including duplicates:

```sql
-- Find all uploads containing a specific certificate
SELECT
    pf.upload_id,
    uf.file_name,
    uf.uploaded_at,
    pc.subject_dn
FROM parsed_certificate pc
JOIN parsed_file pf ON pc.parsed_file_id = pf.id
JOIN uploaded_file uf ON pf.upload_id = uf.id
WHERE pc.fingerprint_sha256 = '5ba9a2069f34cd93fab9dcb39c6a7a7be62141f8bcb608aff815167c813ecf92'
ORDER BY uf.uploaded_at DESC;
```

### 2. Periodic Update Support

Users can upload new PKD files without errors:
- Upload 1 (2025-12-05): Contains Cert A, B, C → All saved to `parsed_certificate`
- Upload 2 (2025-12-06): Contains Cert B, C, D → All saved to `parsed_certificate` (including duplicates B, C)
- `certificate` table: Only 4 unique certs (A, B, C, D)
- `parsed_certificate` table: 7 records total (audit trail)

### 3. Compliance & Forensics

Supports audit questions like:
- "Which PKD files contained this certificate?"
- "When did this certificate first appear?"
- "Which files need reprocessing after a validation rule change?"

---

## Testing

### Test Scenario

1. Upload `icaopkd-002-complete-000325.ldif` → Success (first upload)
2. Upload `icaopkd-002-complete-000329.ldif` → Success (contains duplicates)

### Expected Results

**Before V17**:
```
ERROR: duplicate key value violates unique constraint "parsed_certificate_pkey"
```

**After V17**:
```
✅ Both uploads succeed
✅ ParsedFile #1: 28 certificates (including CSCA from Master Lists)
✅ ParsedFile #2: 28 certificates (same CSCAs, tracked for audit)
✅ Certificate table: ~28 unique certificates (duplicates not inserted)
✅ parsed_certificate table: ~56 records total (audit trail complete)
```

### Verification Queries

```sql
-- Check parsed_certificate primary key
SELECT
    pg_get_indexdef(indexrelid) AS index_definition
FROM pg_index
JOIN pg_class ON pg_class.oid = pg_index.indrelid
WHERE pg_class.relname = 'parsed_certificate'
  AND indisprimary;

-- Expected: PRIMARY KEY (parsed_file_id, fingerprint_sha256)

-- Count duplicate certificates across uploads
SELECT
    fingerprint_sha256,
    COUNT(*) AS upload_count,
    string_agg(parsed_file_id::text, ', ') AS parsed_file_ids
FROM parsed_certificate
GROUP BY fingerprint_sha256
HAVING COUNT(*) > 1
ORDER BY upload_count DESC;

-- Show audit trail for specific certificate
SELECT
    uf.file_name,
    uf.uploaded_at,
    pc.subject_dn,
    pc.cert_type
FROM parsed_certificate pc
JOIN parsed_file pf ON pc.parsed_file_id = pf.id
JOIN uploaded_file uf ON pf.upload_id = uf.id
WHERE pc.fingerprint_sha256 = '3e2b3c45eff48658504827f988869ed2865e41b82c41ada9316a1c9a06b87edb'
ORDER BY uf.uploaded_at;
```

---

## Related Code Changes

### LdifParserAdapter.java (Lines 270-307)

```java
// Calculate fingerprint first for duplicate check
String fingerprint = calculateFingerprint(x509Cert);

// Check for duplicate fingerprint for Certificate entity saving
boolean isDuplicate = certificateExistenceService.existsByFingerprintSha256(fingerprint);

if (!isDuplicate) {
    // Create Certificate entity from Master List CSCA (only if not duplicate in DB)
    com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate cert =
        createCertificateFromMasterListCsca(
            parsedFile.getUploadId().getId(),
            savedMasterList.getId().getId(),
            x509Cert
        );
    cscaCerts.add(cert);
} else {
    log.debug("CSCA already exists in database, skipping Certificate entity save: fingerprint={}", fingerprint);
}

// IMPORTANT: Always add CSCA to ParsedFile for validation, even if duplicate
// This allows validation to proceed with existing certificates
CertificateData certData = CertificateData.of(...);
parsedFile.addCertificate(certData);
log.debug("Added CSCA from Master List to ParsedFile: fingerprint={}, duplicate={}", fingerprint, isDuplicate);
```

**Key Change**: Always call `parsedFile.addCertificate()` regardless of duplication status.

---

## Migration History

| Version | Primary Key | Reason | Issue |
|---------|-------------|--------|-------|
| V3 | `(parsed_file_id, serial_number)` | Original design | Serial numbers not globally unique |
| V7 | `fingerprint_sha256` | Fix serial number duplication | Breaks audit trail for periodic updates |
| **V17** | `(parsed_file_id, fingerprint_sha256)` | **Enable audit trail** | **✅ Fixed** |

---

## Impact Analysis

### Positive Impacts

- ✅ Supports periodic PKD file updates without errors
- ✅ Complete audit trail for compliance
- ✅ Forensic analysis of certificate history
- ✅ Allows reprocessing historical files

### Performance Considerations

- Index on `fingerprint_sha256` maintained for global lookups
- Composite PK uses both columns (B-tree index)
- Query performance for "find all uploads with cert X" is efficient

### Storage Impact

- Minimal: Each duplicate certificate adds ~2KB to `parsed_certificate` table
- Example: 100 uploads × 500 certs × 10% duplicates = ~1MB additional storage
- Acceptable tradeoff for audit trail benefits

---

## Documentation Updates

### CLAUDE.md Updates

Added to "Recent Major Refactoring" section:
- ✅ **Duplicate Certificate Audit Trail** (2025-12-05 NEW) - ParsedFile PK changed to composite (parsed_file_id, fingerprint_sha256) to support periodic PKD updates and maintain audit history

Updated "Database Schema":
- Updated `parsed_certificate` PRIMARY KEY definition
- Added audit trail explanation

---

## References

- Migration File: [V17__Revert_Parsed_Certificate_PK_For_Audit_Trail.sql](../src/main/resources/db/migration/V17__Revert_Parsed_Certificate_PK_For_Audit_Trail.sql)
- LdifParserAdapter: [LdifParserAdapter.java:270-307](../src/main/java/com/smartcoreinc/localpkd/fileparsing/infrastructure/adapter/LdifParserAdapter.java)
- User Requirement: Log conversation 2025-12-05
- ICAO PKD Standard: Doc 9303 Part 12 (PKI for Machine Readable Travel Documents)

---

## Author

- **Developer**: Claude (Anthropic) with user kbjung
- **Date**: 2025-12-05
- **Review Status**: ✅ Tested and Approved

---

## Conclusion

The V17 migration successfully resolves the duplicate certificate constraint violation while maintaining data integrity and enabling comprehensive audit trail tracking. This fix aligns with ICAO PKD operational requirements for periodic certificate updates and compliance monitoring.
