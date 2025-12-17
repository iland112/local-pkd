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

    -- SecurityObjectDocument (Embedded)
    sod_encoded BYTEA NOT NULL,
    hash_algorithm VARCHAR(20),
    signature_algorithm VARCHAR(50),

    -- PassiveAuthenticationResult (Embedded)
    verification_status VARCHAR(20) NOT NULL,
    certificate_chain_valid BOOLEAN,
    sod_signature_valid BOOLEAN,
    total_data_groups INT,
    valid_data_groups INT,
    invalid_data_groups INT,
    errors JSONB,

    -- RequestMetadata (Embedded)
    request_ip_address VARCHAR(45),
    request_user_agent TEXT,
    requested_by VARCHAR(100),

    -- Processing Timestamps
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    processing_duration_ms BIGINT,

    -- Raw Request Data
    raw_request_data JSONB
);

-- ============================================================================
-- Table: passport_data_group
-- Description: Data Group hash verification details (DG1~DG16)
-- ============================================================================
CREATE TABLE passport_data_group (
    -- Foreign Key to passport_data
    passport_data_id UUID NOT NULL REFERENCES passport_data(id) ON DELETE CASCADE,

    -- Data Group Information
    data_group_number VARCHAR(10) NOT NULL,
    content BYTEA,

    -- Hash Values
    expected_hash VARCHAR(128),
    actual_hash VARCHAR(128),

    -- Validation Results
    is_valid BOOLEAN NOT NULL DEFAULT FALSE,
    hash_mismatch_detected BOOLEAN NOT NULL DEFAULT FALSE
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

    -- Verification Step Information
    step VARCHAR(50) NOT NULL,
    step_status VARCHAR(20) NOT NULL,

    -- Timestamps and Execution Time
    timestamp TIMESTAMP NOT NULL,
    execution_time_ms BIGINT,

    -- Log Information
    log_level VARCHAR(10) NOT NULL,
    message TEXT,
    details TEXT
);

-- ============================================================================
-- Indexes for passport_data
-- ============================================================================
CREATE INDEX idx_passport_status
    ON passport_data(verification_status);

CREATE INDEX idx_passport_started_at
    ON passport_data(started_at DESC);

CREATE INDEX idx_passport_completed_at
    ON passport_data(completed_at DESC)
    WHERE completed_at IS NOT NULL;

-- GIN index for JSONB errors
CREATE INDEX idx_passport_errors_gin
    ON passport_data USING GIN(errors)
    WHERE errors IS NOT NULL;

-- ============================================================================
-- Indexes for passport_data_group
-- ============================================================================
CREATE INDEX idx_passport_data_group_passport_data_id
    ON passport_data_group(passport_data_id);

CREATE INDEX idx_passport_data_group_data_group_number
    ON passport_data_group(data_group_number);

CREATE INDEX idx_passport_data_group_is_hash_match
    ON passport_data_group(is_valid)
    WHERE is_valid = FALSE;

CREATE UNIQUE INDEX idx_passport_data_group_unique_dg_per_passport
    ON passport_data_group(passport_data_id, data_group_number);

-- ============================================================================
-- Indexes for passport_verification_audit_log
-- ============================================================================
CREATE INDEX idx_passport_audit_log_passport_data_id
    ON passport_verification_audit_log(passport_data_id);

CREATE INDEX idx_passport_audit_log_step
    ON passport_verification_audit_log(step);

CREATE INDEX idx_passport_audit_log_step_status
    ON passport_verification_audit_log(step_status);

CREATE INDEX idx_passport_audit_log_log_level
    ON passport_verification_audit_log(log_level);

CREATE INDEX idx_passport_audit_log_timestamp
    ON passport_verification_audit_log(timestamp DESC);

-- ============================================================================
-- Comments
-- ============================================================================
COMMENT ON TABLE passport_data IS 'Passive Authentication verification aggregate root - stores SOD and verification result';
COMMENT ON TABLE passport_data_group IS 'Data Group hash verification details (DG1~DG16)';
COMMENT ON TABLE passport_verification_audit_log IS 'Step-by-step audit trail for verification process';

COMMENT ON COLUMN passport_data.sod_encoded IS 'PKCS#7 SignedData (SOD) binary data';
COMMENT ON COLUMN passport_data.verification_status IS 'Overall verification status: VALID, INVALID, or ERROR';
COMMENT ON COLUMN passport_data.errors IS 'JSONB array of PassiveAuthenticationError objects';
COMMENT ON COLUMN passport_data_group.data_group_number IS 'Data Group number (1~16 as per ICAO 9303)';
COMMENT ON COLUMN passport_data_group.is_valid IS 'TRUE if actual_hash matches expected_hash';
COMMENT ON COLUMN passport_verification_audit_log.details IS 'Text details for the verification step';
