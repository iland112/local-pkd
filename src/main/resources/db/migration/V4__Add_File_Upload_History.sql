-- ================================================
-- V4: 파일 업로드 이력 테이블 추가
-- ================================================
-- 작성일: 2025-10-16
-- 설명: ICAO PKD 파일 업로드 및 처리 이력 관리

-- ================================================
-- 1. file_upload_history 테이블 생성
-- ================================================
CREATE TABLE IF NOT EXISTS file_upload_history (
    -- Primary Key
    id BIGSERIAL PRIMARY KEY,

    -- 파일 정보
    filename VARCHAR(255) NOT NULL,
    collection_number VARCHAR(3),
    version VARCHAR(50),
    file_format VARCHAR(50),
    file_size_bytes BIGINT,
    file_size_display VARCHAR(20),

    -- 업로드 정보
    uploaded_at TIMESTAMP NOT NULL,
    uploaded_by VARCHAR(100),
    local_file_path VARCHAR(500),

    -- 체크섬 검증 정보
    calculated_checksum VARCHAR(40),
    expected_checksum VARCHAR(40),
    checksum_validated BOOLEAN,
    checksum_valid BOOLEAN,
    checksum_elapsed_time_ms BIGINT,

    -- 처리 상태 정보
    status VARCHAR(50) NOT NULL DEFAULT 'RECEIVED',
    error_message VARCHAR(1000),
    entries_processed INTEGER,
    entries_failed INTEGER,
    processing_started_at TIMESTAMP,
    processing_completed_at TIMESTAMP,
    total_processing_time_seconds BIGINT,

    -- 중복 체크 정보
    is_duplicate BOOLEAN DEFAULT FALSE,
    is_newer_version BOOLEAN DEFAULT FALSE,
    replaced_file_id BIGINT,

    -- 추가 메타데이터
    description VARCHAR(1000),
    is_deprecated BOOLEAN DEFAULT FALSE,
    remarks VARCHAR(1000),

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ================================================
-- 2. 인덱스 생성
-- ================================================

-- 상태별 조회 인덱스
CREATE INDEX idx_upload_status ON file_upload_history(status);

-- 업로드 일시 조회 인덱스
CREATE INDEX idx_upload_date ON file_upload_history(uploaded_at DESC);

-- Collection + Version 조회 인덱스
CREATE INDEX idx_collection_version ON file_upload_history(collection_number, version);

-- 체크섬 조회 인덱스
CREATE INDEX idx_checksum ON file_upload_history(calculated_checksum);

-- 파일명 조회 인덱스
CREATE INDEX idx_filename ON file_upload_history(filename);

-- 중복 파일 조회 인덱스
CREATE INDEX idx_duplicate ON file_upload_history(is_duplicate) WHERE is_duplicate = TRUE;

-- 최신 버전 조회 인덱스
CREATE INDEX idx_newer_version ON file_upload_history(collection_number, version DESC, uploaded_at DESC);

-- ================================================
-- 3. Foreign Key 제약조건
-- ================================================

-- replaced_file_id는 같은 테이블의 id를 참조
ALTER TABLE file_upload_history
ADD CONSTRAINT fk_replaced_file
FOREIGN KEY (replaced_file_id)
REFERENCES file_upload_history(id)
ON DELETE SET NULL;

-- ================================================
-- 4. Check 제약조건
-- ================================================

-- 파일 크기는 양수
ALTER TABLE file_upload_history
ADD CONSTRAINT chk_file_size_positive
CHECK (file_size_bytes IS NULL OR file_size_bytes > 0);

-- 엔트리 수는 0 이상
ALTER TABLE file_upload_history
ADD CONSTRAINT chk_entries_non_negative
CHECK (entries_processed IS NULL OR entries_processed >= 0);

-- 실패한 엔트리는 처리된 엔트리보다 작거나 같음
ALTER TABLE file_upload_history
ADD CONSTRAINT chk_entries_failed_valid
CHECK (
    entries_failed IS NULL OR
    entries_processed IS NULL OR
    entries_failed <= entries_processed
);

-- 체크섬은 40자 (SHA-1) 또는 NULL
ALTER TABLE file_upload_history
ADD CONSTRAINT chk_checksum_length
CHECK (
    (calculated_checksum IS NULL OR length(calculated_checksum) = 40) AND
    (expected_checksum IS NULL OR length(expected_checksum) = 40)
);

-- Collection 번호는 001, 002, 003 중 하나
ALTER TABLE file_upload_history
ADD CONSTRAINT chk_collection_number
CHECK (
    collection_number IS NULL OR
    collection_number IN ('001', '002', '003')
);

-- ================================================
-- 5. 트리거 생성 (updated_at 자동 갱신)
-- ================================================

CREATE OR REPLACE FUNCTION update_file_upload_history_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_file_upload_history_updated_at
BEFORE UPDATE ON file_upload_history
FOR EACH ROW
EXECUTE FUNCTION update_file_upload_history_updated_at();

-- ================================================
-- 6. 코멘트 추가
-- ================================================

COMMENT ON TABLE file_upload_history IS 'ICAO PKD 파일 업로드 및 처리 이력';

COMMENT ON COLUMN file_upload_history.id IS '이력 ID';
COMMENT ON COLUMN file_upload_history.filename IS '원본 파일명';
COMMENT ON COLUMN file_upload_history.collection_number IS 'Collection 번호 (001: eMRTD, 002: CSCA, 003: Non-Conformant)';
COMMENT ON COLUMN file_upload_history.version IS '파일 버전';
COMMENT ON COLUMN file_upload_history.file_format IS '파일 포맷 (FileFormat enum)';
COMMENT ON COLUMN file_upload_history.file_size_bytes IS '파일 크기 (bytes)';
COMMENT ON COLUMN file_upload_history.file_size_display IS '파일 크기 (human-readable)';

COMMENT ON COLUMN file_upload_history.uploaded_at IS '업로드 일시';
COMMENT ON COLUMN file_upload_history.uploaded_by IS '업로드 사용자 ID';
COMMENT ON COLUMN file_upload_history.local_file_path IS '로컬 파일 경로';

COMMENT ON COLUMN file_upload_history.calculated_checksum IS '계산된 SHA-1 체크섬';
COMMENT ON COLUMN file_upload_history.expected_checksum IS 'ICAO 공식 SHA-1 체크섬';
COMMENT ON COLUMN file_upload_history.checksum_validated IS '체크섬 검증 수행 여부';
COMMENT ON COLUMN file_upload_history.checksum_valid IS '체크섬 일치 여부';
COMMENT ON COLUMN file_upload_history.checksum_elapsed_time_ms IS '체크섬 계산 소요 시간 (ms)';

COMMENT ON COLUMN file_upload_history.status IS '업로드 상태 (UploadStatus enum)';
COMMENT ON COLUMN file_upload_history.error_message IS '오류 메시지';
COMMENT ON COLUMN file_upload_history.entries_processed IS '처리된 엔트리 수';
COMMENT ON COLUMN file_upload_history.entries_failed IS '실패한 엔트리 수';
COMMENT ON COLUMN file_upload_history.processing_started_at IS '처리 시작 시간';
COMMENT ON COLUMN file_upload_history.processing_completed_at IS '처리 완료 시간';
COMMENT ON COLUMN file_upload_history.total_processing_time_seconds IS '총 처리 시간 (초)';

COMMENT ON COLUMN file_upload_history.is_duplicate IS '중복 파일 여부';
COMMENT ON COLUMN file_upload_history.is_newer_version IS '새 버전 여부';
COMMENT ON COLUMN file_upload_history.replaced_file_id IS '대체된 이전 파일 ID';

COMMENT ON COLUMN file_upload_history.description IS 'ICAO 공식 설명';
COMMENT ON COLUMN file_upload_history.is_deprecated IS 'Deprecated 여부';
COMMENT ON COLUMN file_upload_history.remarks IS '비고 (관리자 메모)';

COMMENT ON COLUMN file_upload_history.created_at IS '생성 일시';
COMMENT ON COLUMN file_upload_history.updated_at IS '수정 일시';

-- ================================================
-- 7. 샘플 데이터 (개발/테스트용, 프로덕션에서는 제거)
-- ================================================

-- 샘플 데이터는 여기에 추가하지 않음
-- 실제 파일 업로드를 통해 데이터 생성

-- ================================================
-- Migration 완료
-- ================================================
