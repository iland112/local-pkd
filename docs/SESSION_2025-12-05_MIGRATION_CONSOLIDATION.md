# Session Report: Database Migration Consolidation & Schema Fixes

**Date**: 2025-12-05
**Duration**: ~4 hours
**Status**: ✅ COMPLETED & TESTED
**Result**: All tests passed successfully

---

## 📋 Executive Summary

This session focused on consolidating fragmented database migrations and fixing critical schema validation issues. We successfully reduced 10 migration files (958 lines) into a single consolidated schema (465 lines), eliminating all ALTER statements and resolving multiple Hibernate schema validation errors.

---

## 🔧 Issues Resolved

### 1. SSE AsyncRequestNotUsableException After Upload Completion

**Problem**:
- Error occurred 2 seconds after successful LDAP upload completion
- Heartbeat scheduler tried to send to already-closed SSE connection
- Logged as ERROR level, causing confusion

**Root Cause**:
```
14:05:25 - LDAP upload completes
14:05:25 - Client receives completion, closes SSE connection
14:05:27 - Heartbeat scheduler runs (every 2 seconds)
14:05:27 - Tries to send heartbeat → IOException
14:05:27 - Spring wraps as AsyncRequestNotUsableException
14:05:27 - GlobalExceptionHandler logs as ERROR
```

**Solution**:

**File 1**: `ProgressService.java`
- Removed `emitter.complete()` calls from error handlers (lines 99-105, 173-180)
- Connection already closed, calling complete() throws exception
- Changed log levels from WARN to DEBUG

**File 2**: `GlobalExceptionHandler.java`
- Added specific `@ExceptionHandler` for `AsyncRequestNotUsableException` (lines 269-278)
- Logs at DEBUG level instead of ERROR
- No response returned (connection already closed)

**Impact**: ✅ SSE disconnection is now handled gracefully as normal behavior

---

### 2. "At Least One Certificate" Validation Error

**Problem**:
- User uploaded `icaopkd-002-complete-000329.ldif`
- Validation button showed: "At least one certificate or CRL must be present"
- All CSCAs were marked as "duplicate" or "Master List Signer (not CA)"

**Root Cause**:
```java
// LdifParserAdapter.java:274 (OLD)
if (certificateExistenceService.existsByFingerprintSha256(fingerprint)) {
    log.debug("CSCA already exists, skipping");
    continue; // ❌ Skipped adding to ParsedFile entirely
}
```

**Solution**:

**File**: `LdifParserAdapter.java` (lines 270-307)
```java
boolean isDuplicate = certificateExistenceService.existsByFingerprintSha256(fingerprint);

if (!isDuplicate) {
    // Save Certificate entity (only if not duplicate in DB)
    cert = createCertificateFromMasterListCsca(...);
    cscaCerts.add(cert);
} else {
    log.debug("CSCA already exists in database, skipping Certificate entity save");
}

// ✅ ALWAYS add to ParsedFile for validation (even if duplicate)
parsedFile.addCertificate(certData);
log.debug("Added CSCA from Master List to ParsedFile: fingerprint={}, duplicate={}", fingerprint, isDuplicate);
```

**Impact**: ✅ Validation now works even when certificates already exist in database

---

### 3. Duplicate Certificate Primary Key Violation (Audit Trail)

**Problem**:
- Upload `icaopkd-002-complete-000325.ldif` → Success
- Upload `icaopkd-002-complete-000329.ldif` → Error
- Error: `duplicate key value violates unique constraint "parsed_certificate_pkey"`
- Key: `fingerprint_sha256` (globally unique)

**User Requirement**:
> "ICAO PKD 파일을 다운 받을 때 주기적으로 변경된 내역을 업데이트 해야 하므로 증복 인증서가 발생할 수 있음을 감안하자. 중복된 인증서는 저장하지 않더라도 중복 이력은 기록해둬야. 나중에 audit 에 도움이 되지 않을까?"

**Solution**:

**File**: `V17__Revert_Parsed_Certificate_PK_For_Audit_Trail.sql` (NEW)
```sql
-- 1. Drop existing primary key constraint (fingerprint_sha256)
ALTER TABLE parsed_certificate
    DROP CONSTRAINT parsed_certificate_pkey;

-- 2. Add new composite primary key on (parsed_file_id, fingerprint_sha256)
ALTER TABLE parsed_certificate
    ADD CONSTRAINT parsed_certificate_pkey PRIMARY KEY (parsed_file_id, fingerprint_sha256);

-- 3. Create index on fingerprint_sha256 for global lookups
CREATE INDEX IF NOT EXISTS idx_parsed_certificate_fingerprint ON parsed_certificate(fingerprint_sha256);
```

**Documentation**: `DUPLICATE_CERTIFICATE_AUDIT_TRAIL_FIX.md` (NEW)

**Impact**: ✅ Same certificate can appear in multiple uploads (audit trail support)

---

### 4. Database Migration Consolidation

**Problem**:
- 10 migration files (V1-V17) accumulated over development
- 958 total lines of SQL
- 15+ ALTER statements
- Difficult to understand final schema

**Goal**:
- Consolidate all migrations into single V1__Initial_Schema.sql
- Eliminate ALTER statements
- Clean initial schema for production deployment

**Solution**:

**Files**:
- Created: `V1__Initial_Schema.sql` (465 lines, 51% reduction)
- Archived: Moved old migrations to `docs/migration-archive/`
- Created: `archive/README.md` (restoration procedures)
- Created: `MIGRATION_CONSOLIDATION_REPORT.md` (technical report)

**Changes**:
- All CREATE TABLE statements with final structure
- No ALTER statements
- All indexes defined at creation
- All constraints defined at creation
- Composite PK for parsed_certificate: `(parsed_file_id, fingerprint_sha256)`

**Impact**: ✅ Clean, maintainable schema; 51% code reduction

---

### 5. Flyway Duplicate Migration Error

**Problem**:
```
FlywayException: Found more than one migration with version 1
Offenders:
-> archive/V1__Create_Core_Schema.sql (SQL)
-> V1__Initial_Schema.sql (SQL)
```

**Root Cause**:
- Created consolidated `V1__Initial_Schema.sql`
- Moved old migrations to `src/main/resources/db/migration/archive/`
- Flyway scans subdirectories by default
- Found two V1 migrations

**Solution**:
```bash
# Move archive OUTSIDE of migration directory
mkdir -p docs/migration-archive
mv src/main/resources/db/migration/archive/* docs/migration-archive/
rmdir src/main/resources/db/migration/archive
```

**Impact**: ✅ Flyway only scans migration directory, archive excluded

---

### 6. Hibernate Schema Validation Errors

**Problem 1**: Missing columns in `certificate` table
```
ERROR: column c.issuer_common_name does not exist
```

**Root Cause**:
- Consolidated V1 schema missing 29 columns from Certificate entity
- Value Objects (X509Data, SubjectInfo, IssuerInfo, ValidationResult) not fully mapped

**Solution**:

**File**: `V1__Initial_Schema.sql` (certificate table, lines 235-332)

**Added X509Data columns**:
```sql
-- ========== X509Data Value Object ==========
x509_certificate_binary BYTEA NOT NULL,
x509_serial_number VARCHAR(100) NOT NULL,
x509_fingerprint_sha256 VARCHAR(64) NOT NULL UNIQUE,
```

**Added SubjectInfo columns**:
```sql
-- ========== SubjectInfo Value Object ==========
subject_organization VARCHAR(255),
subject_organizational_unit VARCHAR(255),
subject_common_name VARCHAR(255),
```

**Added IssuerInfo columns**:
```sql
-- ========== IssuerInfo Value Object ==========
issuer_organization VARCHAR(255),
issuer_organizational_unit VARCHAR(255),
issuer_common_name VARCHAR(255),
issuer_is_ca BOOLEAN,
```

**Added ValidationResult columns**:
```sql
-- ========== ValidationResult Value Object ==========
validation_overall_status VARCHAR(30),
validation_signature_valid BOOLEAN,
validation_chain_valid BOOLEAN,
validation_not_revoked BOOLEAN,
validation_validity_valid BOOLEAN,
validation_constraints_valid BOOLEAN,
validation_validated_at TIMESTAMP,
validation_duration_millis BIGINT,
```

**Added other columns**:
```sql
signature_algorithm VARCHAR(50),
uploaded_to_ldap BOOLEAN NOT NULL DEFAULT FALSE,
uploaded_to_ldap_at TIMESTAMP,
```

**Updated indexes**:
```sql
-- Changed column references
CREATE INDEX idx_certificate_fingerprint ON certificate(x509_fingerprint_sha256);
CREATE INDEX idx_certificate_validated_at ON certificate(validation_validated_at DESC);
CREATE INDEX idx_certificate_upload_type_status ON certificate(upload_id, source_type, validation_overall_status);
```

**Impact**: ✅ All Certificate entity fields properly mapped

---

**Problem 2**: Missing VIEW column reference
```
SQL State  : 42703
Error Code : 0
Message    : ERROR: column c.validation_status does not exist
Line       : 435 (v_certificate_validation_summary)
```

**Root Cause**:
- VIEW `v_certificate_validation_summary` referenced old `validation_status` column
- Certificate table now uses `validation_overall_status`

**Solution**:

**File**: `V1__Initial_Schema.sql` (lines 435-445)
```sql
CREATE OR REPLACE VIEW v_certificate_validation_summary AS
SELECT
    c.upload_id,
    c.source_type,
    c.certificate_type,
    c.validation_overall_status,  -- ✅ Updated
    COUNT(*) AS certificate_count,
    COUNT(DISTINCT c.subject_country_code) AS country_count
FROM certificate c
GROUP BY c.upload_id, c.source_type, c.certificate_type, c.validation_overall_status
ORDER BY c.upload_id, c.source_type, c.validation_overall_status;
```

**Impact**: ✅ VIEW uses correct column names

---

**Problem 3**: Missing columns in `certificate_revocation_list` table
```
ERROR: missing column [revoked_serial_numbers] in table [certificate_revocation_list]
ERROR: missing column [updated_at] in table [certificate_revocation_list]
```

**Root Cause**:
- CertificateRevocationList entity expects 3 columns not in consolidated schema
- X509CrlData Value Object: `revoked_count`
- RevokedCertificates Value Object: `revoked_serial_numbers`
- Metadata: `updated_at`

**Solution**:

**File**: `V1__Initial_Schema.sql` (certificate_revocation_list table, lines 376-414)

**Added X509CrlData columns**:
```sql
-- ========== X509CrlData Value Object ==========
crl_binary BYTEA NOT NULL,
revoked_count INT NOT NULL DEFAULT 0,  -- ✅ Added
```

**Added RevokedCertificates columns**:
```sql
-- ========== RevokedCertificates Value Object ==========
revoked_serial_numbers TEXT NOT NULL DEFAULT '',  -- ✅ Added
```

**Added Metadata columns**:
```sql
-- ========== Metadata ==========
created_at TIMESTAMP NOT NULL,
updated_at TIMESTAMP NOT NULL,  -- ✅ Added
```

**Impact**: ✅ All CertificateRevocationList entity fields properly mapped

---

## 📊 Final Statistics

### Migration Consolidation

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| **Migration Files** | 10 files (V1-V17) | 1 file (V1) | -90% |
| **Total Lines** | 958 lines | 465 lines | -51% |
| **ALTER Statements** | 15+ statements | 0 statements | -100% |
| **Schema Clarity** | Fragmented | Unified | ✅ |

### Schema Completeness

| Table | Missing Columns | Added | Status |
|-------|----------------|-------|--------|
| `certificate` | 29 columns | 29 columns | ✅ Complete |
| `certificate_revocation_list` | 3 columns | 3 columns | ✅ Complete |
| `parsed_certificate` | PK issue | Composite PK | ✅ Fixed |
| Views | 1 column ref | 1 update | ✅ Fixed |

### Testing Results

✅ **All Tests Passed**
- Flyway Migration: **SUCCESS**
- Hibernate Schema Validation: **SUCCESS**
- Application Startup: **SUCCESS**
- User Acceptance Testing: **SUCCESS**

---

## 📁 Files Modified

### Source Code
1. `src/main/java/com/smartcoreinc/localpkd/shared/progress/ProgressService.java`
   - Lines 99-105, 173-180: SSE error handling

2. `src/main/java/com/smartcoreinc/localpkd/certificatevalidation/infrastructure/exception/GlobalExceptionHandler.java`
   - Lines 13, 269-278: AsyncRequestNotUsableException handler

3. `src/main/java/com/smartcoreinc/localpkd/fileparsing/infrastructure/adapter/LdifParserAdapter.java`
   - Lines 270-307: Duplicate certificate handling

### Database Migrations
4. `src/main/resources/db/migration/V1__Initial_Schema.sql` (NEW - Consolidated)
   - 465 lines
   - All tables with complete schemas
   - All indexes
   - All views

5. `src/main/resources/db/migration/V17__Revert_Parsed_Certificate_PK_For_Audit_Trail.sql` (Archived)
   - Moved to docs/migration-archive/

### Documentation
6. `docs/DUPLICATE_CERTIFICATE_AUDIT_TRAIL_FIX.md` (NEW)
   - Audit trail solution documentation

7. `docs/MIGRATION_CONSOLIDATION_REPORT.md` (NEW)
   - Technical consolidation report

8. `docs/migration-archive/README.md` (NEW)
   - Archive documentation and restoration procedures

9. `docs/SESSION_2025-12-05_MIGRATION_CONSOLIDATION.md` (NEW - This file)
   - Complete session report

### Archived Files (Moved to `docs/migration-archive/`)
- V1__Create_Core_Schema.sql
- V2__Create_File_Upload_Context.sql
- V3__Create_File_Parsing_Context.sql
- V4__Create_Certificate_Validation_Context.sql
- V5__Create_Performance_Indexes_And_Views.sql
- V7__Change_Parsed_Certificate_Primary_Key.sql
- V14__Add_Master_List_And_Refactor_Certificate.sql
- V15__Add_All_Attributes_To_Certificate.sql
- V16__Add_all_attributes_to_parsed_certificate.sql
- V17__Revert_Parsed_Certificate_PK_For_Audit_Trail.sql

---

## 🎯 Key Takeaways

### Technical Lessons

1. **SSE Connection Lifecycle**
   - Client disconnection is normal behavior
   - Don't call `emitter.complete()` in error handlers
   - Log at DEBUG level, not ERROR

2. **ParsedFile vs Certificate Entity**
   - ParsedFile: Temporary parsing results (includes duplicates)
   - Certificate: Persistent storage (deduplicated)
   - Validation uses ParsedFile (needs all certificates)

3. **Audit Trail Pattern**
   - Use composite PK: `(context_id, entity_fingerprint)`
   - Allows same entity in multiple contexts
   - Essential for periodic data updates

4. **Migration Consolidation Strategy**
   - Consolidate before production deployment
   - Eliminate ALTER statements where possible
   - Archive old migrations for reference
   - Document restoration procedures

5. **Hibernate Schema Validation**
   - Every `@Column`, `@Embedded`, `@AttributeOverride` must match schema
   - Views can cause SQL errors if referencing wrong columns
   - Check ALL entity fields against schema

### Process Improvements

1. **Systematic Debugging**
   - Read log files carefully
   - Check entity definitions
   - Compare with archived migrations
   - Verify each fix incrementally

2. **Documentation First**
   - Document user requirements clearly
   - Create comprehensive reports
   - Archive old code with README
   - Update CLAUDE.md regularly

3. **Testing Strategy**
   - Clean build after each fix
   - Test migration on fresh database
   - Verify Hibernate validation
   - User acceptance testing

---

## 🔄 Next Steps (Optional)

### Immediate
- ✅ Update CLAUDE.md with consolidation status
- ✅ Commit all changes with detailed message
- ✅ Update project version to reflect stability

### Future Enhancements
- Consider adding migration checksum validation
- Implement automated schema comparison tests
- Add monitoring for SSE connection metrics
- Create audit trail reporting queries

---

## 📝 Git Commit Message Template

```
feat: Consolidate database migrations and fix schema validation

BREAKING CHANGE: Database migrations consolidated from V1-V17 into single V1

- Consolidate 10 migration files (958 lines) into V1__Initial_Schema.sql (465 lines)
- Fix SSE AsyncRequestNotUsableException after upload completion
- Fix "at least one certificate" validation error in LDIF parsing
- Add audit trail support with composite PK for parsed_certificate
- Fix 32 missing columns in certificate and certificate_revocation_list tables
- Move old migrations to docs/migration-archive/

Issues Resolved:
- SSE error logging (ProgressService, GlobalExceptionHandler)
- LDIF duplicate certificate handling (LdifParserAdapter)
- Parsed certificate PK for audit trail (V17 migration)
- Flyway duplicate migration error (archive relocation)
- Hibernate schema validation (29 certificate cols, 3 CRL cols)
- VIEW column references (v_certificate_validation_summary)

Files Changed:
- Modified: ProgressService.java, GlobalExceptionHandler.java, LdifParserAdapter.java
- Created: V1__Initial_Schema.sql (consolidated)
- Archived: 10 migration files to docs/migration-archive/
- Created: DUPLICATE_CERTIFICATE_AUDIT_TRAIL_FIX.md, MIGRATION_CONSOLIDATION_REPORT.md, SESSION_2025-12-05_MIGRATION_CONSOLIDATION.md

Testing: All tests passed (Flyway, Hibernate, UAT)

Co-Authored-By: Claude <noreply@anthropic.com>
```

---

**Document Version**: 1.0
**Status**: COMPLETED ✅
**Last Updated**: 2025-12-05
**Author**: SmartCore Inc. + Claude (Anthropic)
