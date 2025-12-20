# Archived Migration Files

**Date Archived**: 2025-12-05
**Reason**: Schema consolidation for cleaner production deployment

---

## Overview

These migration files (V1-V17) have been consolidated into a single `V1__Initial_Schema.sql` file in the parent directory.

## Original Migration History

| Version | File | Description | Lines |
|---------|------|-------------|-------|
| V1 | V1__Create_Core_Schema.sql | Legacy tables (backward compatibility) | 42 |
| V2 | V2__Create_File_Upload_Context.sql | uploaded_file table (DDD) | 91 |
| V3 | V3__Create_File_Parsing_Context.sql | parsed_file, parsed_certificate, parsed_crl, parsing_error | 191 |
| V4 | V4__Create_Certificate_Validation_Context.sql | certificate, certificate_validation_error, certificate_revocation_list | 236 |
| V5 | V5__Create_Performance_Indexes_And_Views.sql | Performance indexes and views | 136 |
| V7 | V7__Change_Parsed_Certificate_Primary_Key.sql | Changed parsed_certificate PK to fingerprint_sha256 | 23 |
| V14 | V14__Add_Master_List_And_Refactor_Certificate.sql | Added master_list table, certificate validation fields | 197 |
| V15 | V15__Add_All_Attributes_To_Certificate.sql | Added all_attributes JSONB to certificate | 1 |
| V16 | V16__Add_all_attributes_to_parsed_certificate.sql | Added all_attributes JSONB to parsed_certificate | 4 |
| V17 | V17__Revert_Parsed_Certificate_PK_For_Audit_Trail.sql | Reverted parsed_certificate PK to (parsed_file_id, fingerprint_sha256) | 37 |

**Total**: 958 lines across 10 files

---

## Consolidation Strategy

### 1. Eliminated ALTER Statements

**Before** (V7 → V17):
```sql
-- V7: Change PK
ALTER TABLE parsed_certificate DROP CONSTRAINT parsed_certificate_pkey;
ALTER TABLE parsed_certificate ADD CONSTRAINT parsed_certificate_pkey PRIMARY KEY (fingerprint_sha256);

-- V17: Revert PK
ALTER TABLE parsed_certificate DROP CONSTRAINT parsed_certificate_pkey;
ALTER TABLE parsed_certificate ADD CONSTRAINT parsed_certificate_pkey PRIMARY KEY (parsed_file_id, fingerprint_sha256);
```

**After** (V1 consolidated):
```sql
CREATE TABLE parsed_certificate (
    ...
    PRIMARY KEY (parsed_file_id, fingerprint_sha256)
);
```

### 2. Integrated Column Additions

**Before** (V14, V15, V16):
```sql
-- V14
ALTER TABLE certificate ADD COLUMN source_type VARCHAR(20);
ALTER TABLE certificate ADD COLUMN master_list_id UUID;
-- ... (multiple ALTER statements)

-- V15
ALTER TABLE certificate ADD COLUMN all_attributes JSONB;

-- V16
ALTER TABLE parsed_certificate ADD COLUMN all_attributes JSONB;
```

**After** (V1 consolidated):
```sql
CREATE TABLE certificate (
    ...
    source_type VARCHAR(20) NOT NULL,
    master_list_id UUID REFERENCES master_list(id),
    all_attributes JSONB,
    ...
);

CREATE TABLE parsed_certificate (
    ...
    all_attributes JSONB,
    ...
);
```

### 3. Removed Data Migration Logic

Eliminated UPDATE statements from V14 that were only needed for migrating existing data:
```sql
-- V14 (removed in consolidated):
UPDATE certificate SET source_type = CASE ... END WHERE source_type IS NULL;
UPDATE certificate SET validation_status = CASE ... END WHERE validation_status IS NULL;
```

Fresh database initialization doesn't need these.

---

## Benefits of Consolidation

1. **Cleaner Production Deployment**:
   - Single migration file vs. 10 separate files
   - No ALTER statement overhead
   - Faster initial schema creation

2. **Easier Maintenance**:
   - Single source of truth for schema
   - No need to trace through multiple migration files
   - Reduced complexity for new developers

3. **Performance**:
   - Direct CREATE TABLE statements are faster than CREATE + multiple ALTERs
   - Reduced database locks during initialization

4. **Testing**:
   - Simpler to test with fresh database containers
   - Consistent schema regardless of migration history

---

## Migration Path for Existing Databases

If you have an existing database with the old migrations applied:

1. **Keep the current database** - it already has the correct schema
2. **New deployments** - use the consolidated V1__Initial_Schema.sql
3. **Schema verification** - both paths result in identical schema

---

## Verification

You can verify that the consolidated schema matches the original by comparing:

```bash
# Original schema (after applying V1-V17)
pg_dump --schema-only icao_local_pkd > schema_original.sql

# Consolidated schema (after applying V1 consolidated)
pg_dump --schema-only icao_local_pkd_fresh > schema_consolidated.sql

# Compare
diff schema_original.sql schema_consolidated.sql
```

---

## Restoration

If you need to restore the original migration history:

```bash
# Move files back from archive
mv archive/V*.sql ../

# Delete consolidated migration
rm V1__Initial_Schema.sql
```

---

## Notes

- These files are kept for historical reference and audit purposes
- Do NOT apply these migrations to new databases
- The consolidated V1 migration contains all changes from these files
- Flyway baseline version is set to 0 to allow the consolidated V1 to run

---

**Archived by**: Claude (Anthropic) + kbjung
**Project**: Local PKD Evaluation Project
**Organization**: SmartCore Inc.
