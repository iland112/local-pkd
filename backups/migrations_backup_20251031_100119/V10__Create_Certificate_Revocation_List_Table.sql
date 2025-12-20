-- V10: Create Certificate Revocation List (CRL) Table
-- Phase 12 Week 4: CRL Distribution Strategy
-- Author: SmartCore Inc.
-- Date: 2025-10-24
--
-- Description:
--   ICAO PKD Master List에서 추출된 CRL(Certificate Revocation List) 데이터를 저장합니다.
--   LDIF 파일의 cRLDistributionPoint 엔트리에서 certificateRevocationList;binary 필드로부터
--   Base64 디코딩된 X.509 CRL 바이너리 데이터를 저장합니다.
--
--   기존의 외부 CRL 다운로드 방식(X.509 CRL Distribution Points 확장) 대신,
--   LDIF 파일에 이미 포함된 CRL 데이터를 직접 추출하여 사용합니다.
--
-- Structure:
--   - 각 CSCA별(예: CSCA-QA, CSCA-NZ) 1개의 CRL 저장
--   - CRL 이진 데이터 (DER 인코딩)
--   - 폐기된 인증서 수 (통계용)
--   - 유효기간 (thisUpdate, nextUpdate)
--   - 폐기된 인증서 일련번호 목록

-- ============================================================
-- Certificate Revocation List (CRL) Table (Aggregate Root)
-- ============================================================
CREATE TABLE IF NOT EXISTS certificate_revocation_list (
    -- Primary Key (JPearl UUID)
    id UUID PRIMARY KEY,

    -- CSCA 발급자명 (예: CSCA-QA, CSCA-NZ, CSCA-US)
    -- Value Object: IssuerName (Embedded)
    issuer_name VARCHAR(255) NOT NULL,

    -- ISO 3166-1 alpha-2 국가 코드 (예: QA, NZ, US)
    -- Value Object: CountryCode (Embedded)
    country_code VARCHAR(2) NOT NULL,

    -- X509CrlData (Embedded Value Object)
    -- Base64 디코딩된 X.509 CRL 바이너리 데이터 (DER 인코딩)
    crl_binary BYTEA NOT NULL,

    -- 폐기된 인증서 개수 (통계 및 성능 최적화)
    -- 값: X509CrlData.revokedCount
    revoked_count INT NOT NULL DEFAULT 0,

    -- ValidityPeriod (Embedded Value Object)
    -- CRL 발행 시간 (thisUpdate)
    this_update TIMESTAMP NOT NULL,

    -- CRL 다음 갱신 시간 (nextUpdate)
    next_update TIMESTAMP NOT NULL,

    -- RevokedCertificates (Embedded Value Object)
    -- 세미콜론으로 구분된 폐기된 인증서 일련번호 목록 (16진수)
    -- 예: "01234567890ABCDEF;FEDCBA0987654321"
    revoked_serial_numbers TEXT NOT NULL DEFAULT '',

    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT chk_crl_country_code_length CHECK (length(country_code) = 2),
    CONSTRAINT chk_crl_next_update_after_this_update CHECK (next_update > this_update),
    CONSTRAINT chk_crl_revoked_count_non_negative CHECK (revoked_count >= 0)
);

-- ============================================================
-- Unique Constraint: 국가별 1개의 CRL만 저장
-- ============================================================
-- 실제로는 CSCA와 CountryCode가 1:1 매핑이므로,
-- CSCA(issuer_name)와 CountryCode의 조합으로 유일성 보장
CREATE UNIQUE INDEX IF NOT EXISTS idx_crl_unique_issuer_country
    ON certificate_revocation_list(issuer_name, country_code);

-- ============================================================
-- Primary Lookup Indexes
-- ============================================================

-- Lookup: 특정 CSCA와 국가 코드로 CRL 조회 (가장 자주 사용)
-- 쿼리: findByIssuerNameAndCountry()
CREATE INDEX IF NOT EXISTS idx_crl_issuer_country
    ON certificate_revocation_list(issuer_name, country_code);

-- Lookup: 특정 CSCA의 모든 CRL 조회
-- 쿼리: findByIssuerName()
CREATE INDEX IF NOT EXISTS idx_crl_issuer
    ON certificate_revocation_list(issuer_name);

-- Lookup: 특정 국가의 모든 CRL 조회
-- 쿼리: findByCountryCode()
CREATE INDEX IF NOT EXISTS idx_crl_country
    ON certificate_revocation_list(country_code);

-- ============================================================
-- Expiration Monitoring Indexes
-- ============================================================

-- Sort: 만료 시간순으로 정렬 (만료 예정 CRL 찾기)
-- 쿼리: findValidCrls() - 유효한 CRL만 조회
CREATE INDEX IF NOT EXISTS idx_crl_next_update
    ON certificate_revocation_list(next_update DESC);

-- Filter: 만료되지 않은 CRL만 (일반 인덱스)
-- NOTE: WHERE next_update > CURRENT_TIMESTAMP removed due to PostgreSQL immutability requirement
CREATE INDEX IF NOT EXISTS idx_crl_valid
    ON certificate_revocation_list(next_update DESC);

-- ============================================================
-- Timestamp Indexes for Auditing
-- ============================================================

-- Sort: 생성 시간순 (최신순 조회)
CREATE INDEX IF NOT EXISTS idx_crl_created_at
    ON certificate_revocation_list(created_at DESC);

-- Sort: 수정 시간순 (최근 변경 CRL)
CREATE INDEX IF NOT EXISTS idx_crl_updated_at
    ON certificate_revocation_list(updated_at DESC);

-- ============================================================
-- Statistics View (for Monitoring & Metrics)
-- ============================================================
CREATE OR REPLACE VIEW v_crl_stats AS
SELECT
    COUNT(*) AS total_crls,
    COUNT(*) FILTER (WHERE next_update > CURRENT_TIMESTAMP) AS valid_crls,
    COUNT(*) FILTER (WHERE next_update <= CURRENT_TIMESTAMP) AS expired_crls,
    COUNT(*) FILTER (WHERE revoked_count > 0) AS crls_with_revocations,
    COALESCE(SUM(revoked_count), 0) AS total_revoked_certificates,
    AVG(revoked_count) FILTER (WHERE revoked_count > 0) AS avg_revoked_per_crl,
    MAX(revoked_count) AS max_revoked_in_single_crl,
    COUNT(DISTINCT country_code) AS country_count,
    STRING_AGG(DISTINCT country_code, ', ' ORDER BY country_code) AS country_codes,
    MIN(this_update) AS oldest_crl_update,
    MAX(this_update) AS newest_crl_update,
    MIN(next_update) AS earliest_crl_expiration,
    MAX(next_update) AS latest_crl_expiration,
    (AVG(EXTRACT(EPOCH FROM next_update)) - EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)) / 86400 AS avg_days_until_expiration
FROM certificate_revocation_list;

-- ============================================================
-- Comments for Documentation
-- ============================================================
COMMENT ON TABLE certificate_revocation_list IS 'X.509 CRL (Certificate Revocation List) Aggregate Root - Certificate Validation Context (Phase 12 Week 4)';
COMMENT ON COLUMN certificate_revocation_list.id IS 'CRL 고유 식별자 (JPearl UUID)';
COMMENT ON COLUMN certificate_revocation_list.issuer_name IS 'CSCA 발급자명 (예: CSCA-QA, CSCA-NZ) - IssuerName Value Object';
COMMENT ON COLUMN certificate_revocation_list.country_code IS 'ISO 3166-1 alpha-2 국가 코드 (예: QA, NZ, US) - CountryCode Value Object';
COMMENT ON COLUMN certificate_revocation_list.crl_binary IS 'DER-encoded X.509 CRL 바이너리 데이터 (LDIF에서 Base64 디코딩)';
COMMENT ON COLUMN certificate_revocation_list.revoked_count IS '폐기된 인증서 개수 (통계용)';
COMMENT ON COLUMN certificate_revocation_list.this_update IS 'CRL 발행 시간 (X509CRL.getThisUpdate())';
COMMENT ON COLUMN certificate_revocation_list.next_update IS 'CRL 다음 갱신 시간 (X509CRL.getNextUpdate())';
COMMENT ON COLUMN certificate_revocation_list.revoked_serial_numbers IS '폐기된 인증서 일련번호 목록 (세미콜론 구분, 16진수)';
COMMENT ON COLUMN certificate_revocation_list.created_at IS 'CRL 저장 일시';
COMMENT ON COLUMN certificate_revocation_list.updated_at IS 'CRL 마지막 수정 일시';

COMMENT ON VIEW v_crl_stats IS 'CRL 통계 뷰 - 폐기 현황, 만료 현황, 국가별 분포';
COMMENT ON INDEX idx_crl_unique_issuer_country IS '유일성 보장: CSCA와 국가 코드 조합';
COMMENT ON INDEX idx_crl_issuer_country IS '성능 최적화: 빠른 CRL 조회 (CSCA + 국가)';
COMMENT ON INDEX idx_crl_next_update IS '성능 최적화: 만료 시간순 정렬';
COMMENT ON INDEX idx_crl_valid IS '성능 최적화: 유효한 CRL만 빠르게 조회';

-- ============================================================
-- Summary
-- ============================================================
--
-- 이 마이그레이션은 다음을 생성합니다:
--
-- 1. certificate_revocation_list 테이블
--    - ICAO PKD LDIF 파일에서 추출된 CRL 저장
--    - 각 CSCA별 1개의 CRL (issuer_name + country_code = unique)
--    - 폐기된 인증서 목록 포함
--
-- 2. 인덱스 (7개)
--    - 고유성 보장: issuer_name + country_code
--    - 조회 성능: issuer_name, country_code, next_update
--    - 타임스탬프: created_at, updated_at
--
-- 3. 통계 뷰 (v_crl_stats)
--    - 총 CRL 개수, 유효/만료 현황
--    - 국가별 분포, 폐기된 인증서 통계
--    - 만료 예정 일시 계산
--
-- ============================================================
