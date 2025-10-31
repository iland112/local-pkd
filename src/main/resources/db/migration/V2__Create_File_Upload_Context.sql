-- ============================================================================
-- V2: File Upload Context (Refactored)
-- ============================================================================
-- Purpose: DDD-based File Upload Context schema
-- Domain: File Upload Context (FileUploadedFile Aggregate Root)
-- Date: 2025-10-31
-- ============================================================================

-- ============================================================================
-- uploaded_file: File Upload Aggregate Root
-- ============================================================================
CREATE TABLE IF NOT EXISTS uploaded_file (
    -- Primary Key (JPearl UUID)
    id UUID PRIMARY KEY,

    -- FileName Value Object
    file_name VARCHAR(255) NOT NULL,

    -- FileHash Value Object (SHA-256, 64 hex characters)
    file_hash VARCHAR(64) NOT NULL UNIQUE,

    -- FileSize Value Object
    file_size_bytes BIGINT NOT NULL,
    file_size_display VARCHAR(20),

    -- FileFormat Value Object
    file_format VARCHAR(50) NOT NULL,

    -- FilePath Value Object
    local_file_path VARCHAR(500),

    -- Metadata (extracted from filename)
    collection_number VARCHAR(10),
    version VARCHAR(50),

    -- Checksum Validation
    expected_checksum VARCHAR(255),
    calculated_checksum VARCHAR(255),

    -- UploadStatus Value Object
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',

    -- Upload Timestamp
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Duplicate Detection
    is_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    original_upload_id UUID,  -- FK to uploaded_file (for duplicates)

    -- Version Management
    is_newer_version BOOLEAN DEFAULT FALSE,

    -- Error Handling
    error_message TEXT,

    -- Dual Mode Processing
    processing_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO',
    manual_pause_at_step VARCHAR(50),

    -- Constraints
    CONSTRAINT chk_file_size_positive CHECK (file_size_bytes > 0),
    CONSTRAINT chk_file_size_limit CHECK (file_size_bytes <= 104857600),
    CONSTRAINT chk_processing_mode CHECK (processing_mode IN ('AUTO', 'MANUAL')),
    CONSTRAINT chk_manual_pause_step CHECK (
        (processing_mode = 'MANUAL' AND manual_pause_at_step IS NOT NULL) OR
        (processing_mode = 'AUTO' AND manual_pause_at_step IS NULL)
    ),
    CONSTRAINT fk_original_upload FOREIGN KEY (original_upload_id)
        REFERENCES uploaded_file(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_uploaded_file_hash ON uploaded_file(file_hash);
CREATE INDEX IF NOT EXISTS idx_uploaded_file_uploaded_at ON uploaded_file(uploaded_at DESC);
CREATE INDEX IF NOT EXISTS idx_uploaded_file_status ON uploaded_file(status);
CREATE INDEX IF NOT EXISTS idx_uploaded_file_processing_mode ON uploaded_file(processing_mode);
CREATE INDEX IF NOT EXISTS idx_uploaded_file_is_duplicate ON uploaded_file(is_duplicate) WHERE is_duplicate = TRUE;

-- ============================================================================
-- Comments for Documentation
-- ============================================================================
COMMENT ON TABLE uploaded_file IS 'File Upload Aggregate Root - DDD Pattern';
COMMENT ON COLUMN uploaded_file.id IS 'UploadId (JPearl UUID)';
COMMENT ON COLUMN uploaded_file.file_name IS 'FileName Value Object';
COMMENT ON COLUMN uploaded_file.file_hash IS 'FileHash Value Object (SHA-256)';
COMMENT ON COLUMN uploaded_file.file_size_bytes IS 'FileSize Value Object (bytes)';
COMMENT ON COLUMN uploaded_file.processing_mode IS 'ProcessingMode: AUTO (automatic) or MANUAL (step-by-step)';

-- ============================================================================
-- End of V2: File Upload Context
-- ============================================================================
