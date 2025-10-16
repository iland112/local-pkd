-- ================================================================
-- ICAO PKD Database - Additional Indexes and Constraints
-- Version: 3.0
-- Description: 성능 최적화를 위한 추가 인덱스 및 제약 조건
-- ================================================================

-- ================================================================
-- 1. 복합 인덱스 추가
-- ================================================================

-- PKD Files: 파일 타입과 상태 조합 조회 최적화
CREATE INDEX IF NOT EXISTS idx_pkd_files_type_status 
ON pkd_files(file_type, upload_status, uploaded_at DESC);

-- PKD Files: Collection과 Delta 조합 조회 최적화
CREATE INDEX IF NOT EXISTS idx_pkd_files_collection_delta 
ON pkd_files(collection_number, is_delta, version_number DESC) 
WHERE collection_number IS NOT NULL;

-- PKD Files: 에러 발생 파일 조회 최적화
CREATE INDEX IF NOT EXISTS idx_pkd_files_errors 
ON pkd_files(error_count) 
WHERE error_count > 0;

-- Certificates: 국가별 타입 및 상태 조회 최적화
CREATE INDEX IF NOT EXISTS idx_cert_country_type_status 
ON certificates(country_code, cert_type, status, is_latest) 
WHERE is_latest = TRUE;

-- Certificates: 만료 예정 인증서 조회 최적화
CREATE INDEX IF NOT EXISTS idx_cert_expiring 
ON certificates(not_after, status) 
WHERE status = 'VALID' AND is_latest = TRUE;

-- Certificates: LDAP 미동기화 인증서 조회 최적화
CREATE INDEX IF NOT EXISTS idx_cert_ldap_pending 
ON certificates(ldap_synced, cert_type, country_code) 
WHERE ldap_synced = FALSE;

-- CRL Entries: 폐기 날짜 기반 조회 최적화
CREATE INDEX IF NOT EXISTS idx_crl_entries_revocation_date 
ON crl_entries(revocation_date DESC);

-- CRL Entries: 영향 받은 인증서 조회 최적화
CREATE INDEX IF NOT EXISTS idx_crl_entries_affected_cert 
ON crl_entries(affected_cert_id) 
WHERE affected_cert_id IS NOT NULL;

-- Deviations: 활성 편차 조회 최적화
CREATE INDEX IF NOT EXISTS idx_deviations_active 
ON deviations(status, severity, country_code) 
WHERE status = 'ACTIVE';

-- Country Statistics: 최신 통계 조회 최적화
CREATE INDEX IF NOT EXISTS idx_country_stats_latest 
ON country_statistics(country_code, stats_date DESC);

-- ================================================================
-- 2. 부분 인덱스 (Partial Index) - PostgreSQL 특화
-- ================================================================

-- 처리 대기 중인 파일만 인덱싱
CREATE INDEX IF NOT EXISTS idx_pkd_files_pending 
ON pkd_files(uploaded_at ASC) 
WHERE upload_status = 'UPLOADED' 
  AND (parse_status IS NULL OR parse_status = 'FAILED');

-- 실패한 파일만 인덱싱
CREATE INDEX IF NOT EXISTS idx_pkd_files_failed 
ON pkd_files(uploaded_at DESC) 
WHERE upload_status = 'FAILED' 
   OR parse_status = 'FAILED' 
   OR verify_status = 'FAILED' 
   OR ldap_status = 'FAILED';

-- 적용 완료된 파일만 인덱싱
CREATE INDEX IF NOT EXISTS idx_pkd_files_applied 
ON pkd_files(collection_number, version_number DESC) 
WHERE upload_status = 'APPLIED';

-- 유효한 인증서만 인덱싱
CREATE INDEX IF NOT EXISTS idx_cert_valid_only 
ON certificates(country_code, cert_type, not_after) 
WHERE status = 'VALID' AND is_latest = TRUE;

-- 폐기된 인증서만 인덱싱
CREATE INDEX IF NOT EXISTS idx_cert_revoked_only 
ON certificates(country_code, revoked_at DESC) 
WHERE status = 'REVOKED' AND is_latest = TRUE;

-- ================================================================
-- 3. 전문 검색 인덱스 (Full-Text Search) - 선택사항
-- ================================================================

-- 인증서 DN 전문 검색 (GIN 인덱스)
CREATE INDEX IF NOT EXISTS idx_cert_subject_dn_gin 
ON certificates USING gin(to_tsvector('english', subject_dn));

CREATE INDEX IF NOT EXISTS idx_cert_issuer_dn_gin 
ON certificates USING gin(to_tsvector('english', issuer_dn));

-- Deviation 설명 전문 검색
CREATE INDEX IF NOT EXISTS idx_deviation_desc_gin 
ON deviations USING gin(to_tsvector('english', description));

-- ================================================================
-- 4. 추가 제약 조건
-- ================================================================

-- PKD Files: version_number는 숫자 문자열이어야 함
ALTER TABLE pkd_files ADD CONSTRAINT chk_version_number_format 
CHECK (version_number IS NULL OR version_number ~ '^\d+$');

-- PKD Files: collection_number는 001, 002, 003 중 하나
ALTER TABLE pkd_files ADD CONSTRAINT chk_collection_number_values 
CHECK (collection_number IS NULL OR collection_number IN ('001', '002', '003'));

-- PKD Files: file_size는 양수
ALTER TABLE pkd_files ADD CONSTRAINT chk_file_size_positive 
CHECK (file_size > 0);

-- Certificates: serial_number는 비어있지 않아야 함
ALTER TABLE certificates ADD CONSTRAINT chk_serial_number_not_empty 
CHECK (serial_number IS NOT NULL AND serial_number <> '');

-- Certificates: fingerprint는 고정 길이
ALTER TABLE certificates ADD CONSTRAINT chk_fingerprint_sha1_length 
CHECK (LENGTH(fingerprint_sha1) = 40);

ALTER TABLE certificates ADD CONSTRAINT chk_fingerprint_sha256_length 
CHECK (LENGTH(fingerprint_sha256) = 64);

-- Certificates: not_before < not_after
ALTER TABLE certificates ADD CONSTRAINT chk_cert_validity_period 
CHECK (not_before < not_after);

-- Certificates: version은 양수
ALTER TABLE certificates ADD CONSTRAINT chk_cert_version_positive 
CHECK (version > 0);

-- CRL Lists: this_update <= next_update
ALTER TABLE crl_lists ADD CONSTRAINT chk_crl_update_order 
CHECK (this_update <= next_update);

-- CRL Lists: total_revocations는 음수가 아님
ALTER TABLE crl_lists ADD CONSTRAINT chk_crl_revocations_non_negative 
CHECK (total_revocations >= 0);

-- Country Statistics: 모든 카운트는 음수가 아님
ALTER TABLE country_statistics ADD CONSTRAINT chk_stats_non_negative 
CHECK (
    total_csca >= 0 AND valid_csca >= 0 AND expired_csca >= 0 AND revoked_csca >= 0 AND
    total_dsc >= 0 AND valid_dsc >= 0 AND expired_dsc >= 0 AND revoked_dsc >= 0 AND
    total_revocations >= 0 AND total_deviations >= 0 AND critical_deviations >= 0
);

-- Country Statistics: 합계 검증
ALTER TABLE country_statistics ADD CONSTRAINT chk_stats_csca_sum 
CHECK (valid_csca + expired_csca + revoked_csca <= total_csca);

ALTER TABLE country_statistics ADD CONSTRAINT chk_stats_dsc_sum 
CHECK (valid_dsc + expired_dsc + revoked_dsc <= total_dsc);

-- ================================================================
-- 5. 테이블 파티셔닝 준비 (선택사항 - 대용량 데이터)
-- ================================================================

-- PKD Files를 연도별로 파티셔닝 (예시 - 실제 적용 시 주의)
-- CREATE TABLE pkd_files_2024 PARTITION OF pkd_files 
--   FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');

-- CREATE TABLE pkd_files_2025 PARTITION OF pkd_files 
--   FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');

-- ================================================================
-- 6. 통계 정보 갱신
-- ================================================================

-- PostgreSQL 통계 정보 업데이트 (쿼리 최적화에 중요)
ANALYZE pkd_files;
ANALYZE certificates;
ANALYZE crl_lists;
ANALYZE crl_entries;
ANALYZE deviations;
ANALYZE country_statistics;

-- ================================================================
-- 7. 코멘트 추가 (문서화)
-- ================================================================

COMMENT ON INDEX idx_pkd_files_type_status IS '파일 타입과 상태별 조회 최적화';
COMMENT ON INDEX idx_pkd_files_collection_delta IS 'Collection별 Delta 파일 조회 최적화';
COMMENT ON INDEX idx_cert_country_type_status IS '국가별 인증서 타입 및 상태 조회 최적화';
COMMENT ON INDEX idx_cert_expiring IS '만료 예정 인증서 조회 최적화';
COMMENT ON INDEX idx_pkd_files_pending IS '처리 대기 중인 파일 조회 최적화';
COMMENT ON INDEX idx_cert_valid_only IS '유효한 인증서만 인덱싱';

-- ================================================================
-- End of Migration
-- ================================================================