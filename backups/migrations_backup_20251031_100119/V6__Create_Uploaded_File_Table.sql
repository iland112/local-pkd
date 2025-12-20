-- V6__Create_Uploaded_File_Table.sql
-- Create DDD-based uploaded_file table for File Upload Context
-- This table follows Domain-Driven Design principles with Value Objects and Aggregate Root
-- Updated: 2025-10-19 - Phase 4.1 확장 필드 추가

-- Create uploaded_file table (DDD Aggregate Root)
CREATE TABLE uploaded_file (
    -- Primary Key: UploadId (JPearl-based UUID)
    id UUID PRIMARY KEY,

    -- Value Objects (Embedded) - Phase 1-3
    file_name VARCHAR(255) NOT NULL,        -- FileName.value
    file_hash VARCHAR(64) NOT NULL,         -- FileHash.value (SHA-256, lowercase)
    file_size_bytes BIGINT NOT NULL,        -- FileSize.bytes

    -- Aggregate Root fields - Phase 1-3
    uploaded_at TIMESTAMP NOT NULL,

    -- Duplicate detection fields - Phase 1-3
    is_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    original_upload_id UUID,                -- Reference to original file (if duplicate)

    -- ===== Phase 4.1 확장 필드 =====

    -- Collection & Version
    collection_number VARCHAR(10),          -- CollectionNumber.value (001, 002, 003)
    version VARCHAR(50),                    -- FileVersion.value (009410 또는 July2025)

    -- File Format
    file_format VARCHAR(50),                -- FileFormat.Type.name() (EMRTD_COMPLETE_LDIF 등)
    file_size_display VARCHAR(20),          -- FileSize.toHumanReadable() (캐시용)

    -- File Path
    local_file_path VARCHAR(500),           -- FilePath.value (서버 저장 경로)

    -- Checksum (SHA-1)
    expected_checksum VARCHAR(40),          -- Checksum.value (사용자 제공, SHA-1)
    calculated_checksum VARCHAR(40),        -- Checksum.value (서버 계산, SHA-1)

    -- Upload Status
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',  -- UploadStatus enum

    -- Version Management
    is_newer_version BOOLEAN DEFAULT FALSE,

    -- Error Handling
    error_message TEXT,

    -- ===== Constraints =====
    CONSTRAINT chk_file_size_positive CHECK (file_size_bytes > 0),
    CONSTRAINT chk_file_size_limit CHECK (file_size_bytes <= 104857600),  -- 100 MB
    CONSTRAINT chk_collection_number CHECK (collection_number IS NULL OR collection_number ~ '^[0-9]{3}$'),
    CONSTRAINT chk_expected_checksum_format CHECK (expected_checksum IS NULL OR length(expected_checksum) = 40),
    CONSTRAINT chk_calculated_checksum_format CHECK (calculated_checksum IS NULL OR length(calculated_checksum) = 40),
    CONSTRAINT chk_status_values CHECK (status IN (
        'RECEIVED', 'VALIDATING', 'VALIDATED', 'CHECKSUM_INVALID', 'DUPLICATE_DETECTED',
        'PARSING', 'PARSED', 'UPLOADING_TO_LDAP', 'COMPLETED', 'FAILED'
    )),
    CONSTRAINT fk_original_upload FOREIGN KEY (original_upload_id)
        REFERENCES uploaded_file(id) ON DELETE SET NULL
);

-- Unique constraint on file_hash for duplicate detection
CREATE UNIQUE INDEX idx_uploaded_file_hash_unique ON uploaded_file(file_hash);

-- Performance indexes - Phase 1-3
CREATE INDEX idx_uploaded_file_uploaded_at ON uploaded_file(uploaded_at DESC);
CREATE INDEX idx_uploaded_file_is_duplicate ON uploaded_file(is_duplicate);
CREATE INDEX idx_uploaded_file_original_id ON uploaded_file(original_upload_id)
    WHERE original_upload_id IS NOT NULL;

-- Performance indexes - Phase 4.1
CREATE INDEX idx_uploaded_file_collection ON uploaded_file(collection_number);
CREATE INDEX idx_uploaded_file_version ON uploaded_file(version);
CREATE INDEX idx_uploaded_file_status ON uploaded_file(status);
CREATE INDEX idx_uploaded_file_file_format ON uploaded_file(file_format);

-- Comments for documentation - Phase 1-3
COMMENT ON TABLE uploaded_file IS 'DDD Aggregate Root: 업로드된 파일 (File Upload Context) - Phase 4.1 Extended';
COMMENT ON COLUMN uploaded_file.id IS 'UploadId (JPearl-based UUID, type-safe entity identifier)';
COMMENT ON COLUMN uploaded_file.file_name IS 'FileName Value Object: 파일명 (최대 255자, 특수문자 제한)';
COMMENT ON COLUMN uploaded_file.file_hash IS 'FileHash Value Object: SHA-256 해시 (64자 소문자 16진수)';
COMMENT ON COLUMN uploaded_file.file_size_bytes IS 'FileSize Value Object: 파일 크기 바이트 (0 < size <= 100MB)';
COMMENT ON COLUMN uploaded_file.uploaded_at IS '업로드 일시';
COMMENT ON COLUMN uploaded_file.is_duplicate IS '중복 파일 여부';
COMMENT ON COLUMN uploaded_file.original_upload_id IS '원본 파일 ID (중복 파일인 경우)';

-- Comments for Phase 4.1 fields
COMMENT ON COLUMN uploaded_file.collection_number IS 'CollectionNumber VO: ICAO PKD Collection (001: CSCA, 002: eMRTD, 003: 예약)';
COMMENT ON COLUMN uploaded_file.version IS 'FileVersion VO: 파일 버전 (LDIF: 숫자, ML: 날짜 형식)';
COMMENT ON COLUMN uploaded_file.file_format IS 'FileFormat: 파일 포맷 타입 (EMRTD_COMPLETE_LDIF 등)';
COMMENT ON COLUMN uploaded_file.file_size_display IS 'FileSize 표시용: 사람이 읽기 쉬운 크기 (예: "75.0 MB")';
COMMENT ON COLUMN uploaded_file.local_file_path IS 'FilePath VO: 서버 파일 시스템 저장 경로';
COMMENT ON COLUMN uploaded_file.expected_checksum IS 'Checksum VO: 예상 체크섬 (SHA-1, 40자, 사용자 제공)';
COMMENT ON COLUMN uploaded_file.calculated_checksum IS 'Checksum VO: 계산된 체크섬 (SHA-1, 40자, 서버 계산)';
COMMENT ON COLUMN uploaded_file.status IS 'UploadStatus Enum: 업로드 진행 상태 (RECEIVED → VALIDATING → ... → COMPLETED/FAILED)';
COMMENT ON COLUMN uploaded_file.is_newer_version IS '기존 파일보다 최신 버전 여부';
COMMENT ON COLUMN uploaded_file.error_message IS '업로드 실패 시 오류 메시지';

-- Statistics view for monitoring
CREATE OR REPLACE VIEW v_uploaded_file_stats AS
SELECT
    COUNT(*) as total_uploads,
    COUNT(*) FILTER (WHERE is_duplicate = FALSE) as unique_files,
    COUNT(*) FILTER (WHERE is_duplicate = TRUE) as duplicate_files,
    SUM(file_size_bytes) as total_size_bytes,
    AVG(file_size_bytes) as avg_size_bytes,
    MAX(file_size_bytes) as max_size_bytes,
    MIN(uploaded_at) as first_upload_at,
    MAX(uploaded_at) as last_upload_at
FROM uploaded_file;

COMMENT ON VIEW v_uploaded_file_stats IS '업로드 파일 통계 뷰';
