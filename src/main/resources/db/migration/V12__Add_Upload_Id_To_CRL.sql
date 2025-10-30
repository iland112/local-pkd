-- V12: Add upload_id Column to CRL Table
-- Phase 17: Real Use Case Implementation - Task 1.2
-- Author: SmartCore Inc.
-- Date: 2025-10-30

-- ============================================================
-- Add upload_id Column (Cross-Context Reference)
-- ============================================================

-- Add upload_id column to certificate_revocation_list table
-- This links CRLs to their source file upload (File Upload Context)
ALTER TABLE certificate_revocation_list
ADD COLUMN upload_id UUID NOT NULL;

-- ============================================================
-- Index for Performance
-- ============================================================

-- Filter/Query: find all CRLs from a specific upload
CREATE INDEX idx_crl_upload_id
    ON certificate_revocation_list(upload_id);

-- ============================================================
-- Comments for Documentation
-- ============================================================

COMMENT ON COLUMN certificate_revocation_list.upload_id IS '원본 업로드 파일 ID (File Upload Context - Cross-Context Reference)';
