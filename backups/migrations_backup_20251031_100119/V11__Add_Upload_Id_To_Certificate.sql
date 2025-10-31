-- V11: Add upload_id Column to Certificate Table
-- Phase 17: Real Use Case Implementation - Task 1.1
-- Author: SmartCore Inc.
-- Date: 2025-10-30

-- ============================================================
-- Add upload_id Column (Cross-Context Reference)
-- ============================================================

-- Add upload_id column to certificate table
-- This links certificates to their source file upload (File Upload Context)
ALTER TABLE certificate
ADD COLUMN upload_id UUID NOT NULL;

-- ============================================================
-- Index for Performance
-- ============================================================

-- Filter/Query: find all certificates from a specific upload
CREATE INDEX idx_certificate_upload_id
    ON certificate(upload_id);

-- ============================================================
-- Comments for Documentation
-- ============================================================

COMMENT ON COLUMN certificate.upload_id IS '원본 업로드 파일 ID (File Upload Context - Cross-Context Reference)';
