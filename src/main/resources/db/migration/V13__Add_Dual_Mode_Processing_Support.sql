-- ============================================================================
-- V13: Add Dual Mode Processing Support
-- ============================================================================
-- Purpose: Add ProcessingMode (AUTO/MANUAL) and manual pause state tracking
-- Date: 2025-10-24
-- ============================================================================

-- Add new columns to uploaded_file table for dual mode processing
ALTER TABLE uploaded_file
ADD COLUMN IF NOT EXISTS processing_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO',
ADD COLUMN IF NOT EXISTS manual_pause_at_step VARCHAR(50),
ADD CONSTRAINT chk_processing_mode CHECK (processing_mode IN ('AUTO', 'MANUAL')),
ADD CONSTRAINT chk_manual_pause_step CHECK (
    (processing_mode = 'MANUAL' AND manual_pause_at_step IS NOT NULL) OR
    (processing_mode = 'AUTO' AND manual_pause_at_step IS NULL)
);

-- Create index for processing_mode filter
CREATE INDEX IF NOT EXISTS idx_uploaded_file_processing_mode
ON uploaded_file(processing_mode);

-- Add a comment explaining the columns
COMMENT ON COLUMN uploaded_file.processing_mode IS
'File processing mode: AUTO (automatic pipeline) or MANUAL (step-by-step user control)';

COMMENT ON COLUMN uploaded_file.manual_pause_at_step IS
'For MANUAL mode only: current pause step where user action is awaited (UPLOAD_COMPLETED, PARSING_STARTED, PARSING_COMPLETED, VALIDATION_STARTED, VALIDATION_COMPLETED, LDAP_SAVING_STARTED)';

-- ============================================================================
-- End of V13 Migration
-- ============================================================================
