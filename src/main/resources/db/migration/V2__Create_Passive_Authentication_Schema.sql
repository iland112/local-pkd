-- ============================================================================
-- Passive Authentication Schema - Phase 2
-- ============================================================================
-- Description: Creates tables for ePassport Passive Authentication verification
-- Version: 2
-- Date: 2025-12-12
-- Author: Claude (Anthropic)
-- ============================================================================

-- ============================================================================
-- Table: passport_data
-- Description: Main aggregate root for Passive Authentication verification
-- ============================================================================
CREATE TABLE passport_data (
    -- Primary Key (JPearl EntityId)
    id UUID PRIMARY KEY,

    -- SOD (Security Object Document) Data
    sod_bytes BYTEA NOT NULL,
    sod_hash_algorithm VARCHAR(50) NOT NULL,
    sod_signature_algorithm VARCHAR(50) NOT NULL,

    -- DSC (Document Signer Certificate) Reference
    dsc_fingerprint_sha256 VARCHAR(64) NOT NULL,

    -- Verification Result
    status VARCHAR(30) NOT NULL, -- PENDING, VALID, INVALID, ERROR
    is_signature_valid BOOLEAN,
    are_hashes_valid BOOLEAN,
    validation_errors JSONB,

    -- Audit Metadata
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- Table: passport_data_group
-- Description: Data Group hash verification details (DG1~DG16)
-- ============================================================================
CREATE TABLE passport_data_group (
    -- Primary Key
    id UUID PRIMARY KEY,

    -- Foreign Key to passport_data
    passport_data_id UUID NOT NULL REFERENCES passport_data(id) ON DELETE CASCADE,

    -- Data Group Information
    data_group_number INT NOT NULL CHECK (data_group_number BETWEEN 1 AND 16),
    hash_algorithm VARCHAR(50) NOT NULL,

    -- Hash Values
    computed_hash BYTEA NOT NULL,
    sod_hash BYTEA NOT NULL,
    is_hash_match BOOLEAN NOT NULL,

    -- Audit Metadata
    verified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- Table: passport_verification_audit_log
-- Description: Step-by-step audit trail for verification process
-- ============================================================================
CREATE TABLE passport_verification_audit_log (
    -- Primary Key
    id UUID PRIMARY KEY,

    -- Foreign Key to passport_data
    passport_data_id UUID NOT NULL REFERENCES passport_data(id) ON DELETE CASCADE,

    -- Audit Information
    step VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL, -- SUCCESS, FAILURE, WARNING, INFO
    message TEXT,
    details JSONB,

    -- Timestamp
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- Indexes for passport_data
-- ============================================================================
CREATE INDEX idx_passport_data_status
    ON passport_data(status);

CREATE INDEX idx_passport_data_dsc_fingerprint
    ON passport_data(dsc_fingerprint_sha256);

CREATE INDEX idx_passport_data_started_at
    ON passport_data(started_at DESC);

CREATE INDEX idx_passport_data_completed_at
    ON passport_data(completed_at DESC)
    WHERE completed_at IS NOT NULL;

CREATE INDEX idx_passport_data_in_progress
    ON passport_data(started_at)
    WHERE completed_at IS NULL;

-- GIN index for JSONB validation_errors
CREATE INDEX idx_passport_data_validation_errors_gin
    ON passport_data USING GIN(validation_errors)
    WHERE validation_errors IS NOT NULL;

-- ============================================================================
-- Indexes for passport_data_group
-- ============================================================================
CREATE INDEX idx_passport_data_group_passport_data_id
    ON passport_data_group(passport_data_id);

CREATE INDEX idx_passport_data_group_data_group_number
    ON passport_data_group(data_group_number);

CREATE INDEX idx_passport_data_group_is_hash_match
    ON passport_data_group(is_hash_match)
    WHERE is_hash_match = FALSE;

CREATE UNIQUE INDEX idx_passport_data_group_unique_dg_per_passport
    ON passport_data_group(passport_data_id, data_group_number);

-- ============================================================================
-- Indexes for passport_verification_audit_log
-- ============================================================================
CREATE INDEX idx_passport_audit_log_passport_data_id
    ON passport_verification_audit_log(passport_data_id);

CREATE INDEX idx_passport_audit_log_step
    ON passport_verification_audit_log(step);

CREATE INDEX idx_passport_audit_log_status
    ON passport_verification_audit_log(status);

CREATE INDEX idx_passport_audit_log_created_at
    ON passport_verification_audit_log(created_at DESC);

-- GIN index for JSONB details
CREATE INDEX idx_passport_audit_log_details_gin
    ON passport_verification_audit_log USING GIN(details)
    WHERE details IS NOT NULL;

-- ============================================================================
-- Comments
-- ============================================================================
COMMENT ON TABLE passport_data IS 'Passive Authentication verification aggregate root - stores SOD and verification result';
COMMENT ON TABLE passport_data_group IS 'Data Group hash verification details (DG1~DG16)';
COMMENT ON TABLE passport_verification_audit_log IS 'Step-by-step audit trail for verification process';

COMMENT ON COLUMN passport_data.sod_bytes IS 'PKCS#7 SignedData (SOD) binary data';
COMMENT ON COLUMN passport_data.dsc_fingerprint_sha256 IS 'SHA-256 fingerprint of DSC certificate used for signature verification';
COMMENT ON COLUMN passport_data.validation_errors IS 'JSONB array of ValidationError objects';
COMMENT ON COLUMN passport_data_group.data_group_number IS 'Data Group number (1~16 as per ICAO 9303)';
COMMENT ON COLUMN passport_data_group.is_hash_match IS 'TRUE if computed_hash matches sod_hash';
COMMENT ON COLUMN passport_verification_audit_log.details IS 'JSONB object with step-specific details';
