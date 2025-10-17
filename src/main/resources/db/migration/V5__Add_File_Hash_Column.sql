-- V5__Add_File_Hash_Column.sql
-- Add file_hash column for duplicate detection using SHA-256

-- Add file_hash column to file_upload_history table
ALTER TABLE file_upload_history
ADD COLUMN IF NOT EXISTS file_hash VARCHAR(64);

-- Add index for file_hash to improve duplicate check performance
CREATE INDEX IF NOT EXISTS idx_file_hash ON file_upload_history(file_hash);

-- Add comment to the column
COMMENT ON COLUMN file_upload_history.file_hash IS 'SHA-256 hash of the uploaded file for duplicate detection';