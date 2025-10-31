-- ============================================================================
-- V1: Core PKD Schema (Refactored)
-- ============================================================================
-- Purpose: Initialize core database schema for ICAO PKD Local Evaluation
-- Date: 2025-10-31
-- Status: Clean refactored version
-- ============================================================================

-- ============================================================================
-- Drop existing objects (for fresh setup)
-- ============================================================================
DROP TABLE IF EXISTS file_upload_history CASCADE;
DROP TABLE IF EXISTS certificates CASCADE;

-- ============================================================================
-- Legacy file_upload_history table (kept for backward compatibility)
-- ============================================================================
CREATE TABLE file_upload_history (
    id BIGSERIAL PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    collection_number VARCHAR(10),
    version VARCHAR(50),
    file_format VARCHAR(50) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    file_size_display VARCHAR(20),
    uploaded_at TIMESTAMP NOT NULL,
    local_file_path VARCHAR(500),
    file_hash VARCHAR(64),
    expected_checksum VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    is_duplicate BOOLEAN DEFAULT FALSE,
    is_newer_version BOOLEAN DEFAULT FALSE,
    error_message TEXT
);

CREATE INDEX idx_upload_status ON file_upload_history(status);
CREATE INDEX idx_upload_date ON file_upload_history(uploaded_at DESC);
CREATE INDEX idx_file_hash ON file_upload_history(file_hash);

-- ============================================================================
-- End of V1: Core Schema
-- ============================================================================
