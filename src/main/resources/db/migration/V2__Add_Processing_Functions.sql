-- ================================================================
-- ICAO PKD Database - Processing Functions
-- Version: 2.0
-- Description: 인증서 처리 및 통계 관리 함수
-- ================================================================

-- ================================================================
-- 1. 만료 인증서 자동 표시 함수
-- ================================================================
CREATE OR REPLACE FUNCTION mark_expired_certificates()
RETURNS INTEGER AS $$
DECLARE
    updated_count INTEGER;
BEGIN
    UPDATE certificates
    SET status = 'EXPIRED',
        updated_at = CURRENT_TIMESTAMP
    WHERE is_latest = TRUE
      AND status = 'VALID'
      AND not_after < CURRENT_TIMESTAMP;
    
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    
    RETURN updated_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION mark_expired_certificates() IS '만료된 인증서를 자동으로 EXPIRED 상태로 변경';

-- ================================================================
-- 2. 인증서 버전 관리 함수
-- ================================================================
CREATE OR REPLACE FUNCTION update_certificate_version(
    p_fingerprint_sha256 VARCHAR(64),
    p_new_cert_id VARCHAR(36)
)
RETURNS VOID AS $$
BEGIN
    -- 이전 버전을 latest가 아니도록 설정
    UPDATE certificates
    SET is_latest = FALSE,
        replaced_by = p_new_cert_id,
        updated_at = CURRENT_TIMESTAMP
    WHERE fingerprint_sha256 = p_fingerprint_sha256
      AND is_latest = TRUE
      AND cert_id != p_new_cert_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION update_certificate_version(VARCHAR, VARCHAR) IS '인증서 버전 업데이트 - 새 버전으로 교체';

-- ================================================================
-- 3. 국가별 통계 갱신 함수
-- ================================================================
CREATE OR REPLACE FUNCTION refresh_country_statistics(p_country_code VARCHAR(2) DEFAULT NULL)
RETURNS VOID AS $$
DECLARE
    v_country VARCHAR(2);
BEGIN
    -- 특정 국가 또는 전체 국가
    FOR v_country IN 
        SELECT DISTINCT country_code 
        FROM certificates 
        WHERE (p_country_code IS NULL OR country_code = p_country_code)
          AND is_latest = TRUE
    LOOP
        INSERT INTO country_statistics (
            country_code,
            stats_date,
            total_csca,
            valid_csca,
            expired_csca,
            revoked_csca,
            total_dsc,
            valid_dsc,
            expired_dsc,
            revoked_dsc,
            total_revocations,
            last_crl_update,
            total_deviations,
            critical_deviations
        )
        SELECT
            v_country,
            CURRENT_DATE,
            -- CSCA 통계
            COUNT(*) FILTER (WHERE cert_type = 'CSCA'),
            COUNT(*) FILTER (WHERE cert_type = 'CSCA' AND status = 'VALID'),
            COUNT(*) FILTER (WHERE cert_type = 'CSCA' AND status = 'EXPIRED'),
            COUNT(*) FILTER (WHERE cert_type = 'CSCA' AND status = 'REVOKED'),
            -- DSC 통계
            COUNT(*) FILTER (WHERE cert_type = 'DSC'),
            COUNT(*) FILTER (WHERE cert_type = 'DSC' AND status = 'VALID'),
            COUNT(*) FILTER (WHERE cert_type = 'DSC' AND status = 'EXPIRED'),
            COUNT(*) FILTER (WHERE cert_type = 'DSC' AND status = 'REVOKED'),
            -- CRL 통계
            (SELECT COUNT(*) FROM crl_entries WHERE country_code = v_country),
            (SELECT MAX(this_update) FROM crl_lists WHERE country_code = v_country),
            -- Deviation 통계
            (SELECT COUNT(*) FROM deviations WHERE country_code = v_country AND status = 'ACTIVE'),
            (SELECT COUNT(*) FROM deviations WHERE country_code = v_country AND status = 'ACTIVE' AND severity = 'CRITICAL')
        FROM certificates
        WHERE country_code = v_country
          AND is_latest = TRUE
        ON CONFLICT (country_code, stats_date)
        DO UPDATE SET
            total_csca = EXCLUDED.total_csca,
            valid_csca = EXCLUDED.valid_csca,
            expired_csca = EXCLUDED.expired_csca,
            revoked_csca = EXCLUDED.revoked_csca,
            total_dsc = EXCLUDED.total_dsc,
            valid_dsc = EXCLUDED.valid_dsc,
            expired_dsc = EXCLUDED.expired_dsc,
            revoked_dsc = EXCLUDED.revoked_dsc,
            total_revocations = EXCLUDED.total_revocations,
            last_crl_update = EXCLUDED.last_crl_update,
            total_deviations = EXCLUDED.total_deviations,
            critical_deviations = EXCLUDED.critical_deviations,
            updated_at = CURRENT_TIMESTAMP;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION refresh_country_statistics(VARCHAR) IS '국가별 통계 갱신 (NULL이면 전체 국가)';

-- ================================================================
-- 4. CRL 적용 함수
-- ================================================================
CREATE OR REPLACE FUNCTION apply_crl_revocations(p_crl_list_id BIGINT)
RETURNS INTEGER AS $$
DECLARE
    revoked_count INTEGER := 0;
    v_entry RECORD;
BEGIN
    -- CRL 항목을 순회하며 인증서 폐기 처리
    FOR v_entry IN
        SELECT ce.country_code, ce.serial_number, ce.revocation_date, ce.revocation_reason, ce.id as entry_id
        FROM crl_entries ce
        WHERE ce.crl_list_id = p_crl_list_id
          AND ce.affected_cert_id IS NULL
    LOOP
        -- 해당 serial_number를 가진 인증서 찾기
        UPDATE certificates
        SET status = 'REVOKED',
            revoked_at = v_entry.revocation_date,
            revocation_reason = v_entry.revocation_reason,
            revocation_source = (SELECT crl_id FROM crl_lists WHERE id = p_crl_list_id),
            updated_at = CURRENT_TIMESTAMP
        WHERE country_code = v_entry.country_code
          AND serial_number = v_entry.serial_number
          AND is_latest = TRUE
          AND status != 'REVOKED';
        
        -- affected_cert_id 업데이트
        UPDATE crl_entries
        SET affected_cert_id = (
            SELECT cert_id 
            FROM certificates 
            WHERE country_code = v_entry.country_code 
              AND serial_number = v_entry.serial_number
              AND is_latest = TRUE
            LIMIT 1
        )
        WHERE id = v_entry.entry_id;
        
        IF FOUND THEN
            revoked_count := revoked_count + 1;
        END IF;
    END LOOP;
    
    RETURN revoked_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION apply_crl_revocations(BIGINT) IS 'CRL 폐기 목록을 인증서에 적용';

-- ================================================================
-- 5. 파일 처리 상태 업데이트 함수
-- ================================================================
CREATE OR REPLACE FUNCTION update_file_processing_status(
    p_file_id VARCHAR(36),
    p_status_type VARCHAR(20), -- 'upload', 'parse', 'verify', 'ldap'
    p_status VARCHAR(20),
    p_error_message TEXT DEFAULT NULL
)
RETURNS VOID AS $$
BEGIN
    CASE p_status_type
        WHEN 'upload' THEN
            UPDATE pkd_files
            SET upload_status = p_status,
                error_message = COALESCE(p_error_message, error_message),
                updated_at = CURRENT_TIMESTAMP
            WHERE file_id = p_file_id;
            
        WHEN 'parse' THEN
            UPDATE pkd_files
            SET parse_status = p_status,
                parse_completed_at = CASE WHEN p_status IN ('PARSED', 'FAILED') THEN CURRENT_TIMESTAMP ELSE parse_completed_at END,
                error_message = COALESCE(p_error_message, error_message),
                updated_at = CURRENT_TIMESTAMP
            WHERE file_id = p_file_id;
            
        WHEN 'verify' THEN
            UPDATE pkd_files
            SET verify_status = p_status,
                verify_completed_at = CASE WHEN p_status IN ('VERIFIED', 'FAILED') THEN CURRENT_TIMESTAMP ELSE verify_completed_at END,
                error_message = COALESCE(p_error_message, error_message),
                updated_at = CURRENT_TIMESTAMP
            WHERE file_id = p_file_id;
            
        WHEN 'ldap' THEN
            UPDATE pkd_files
            SET ldap_status = p_status,
                ldap_completed_at = CASE WHEN p_status IN ('COMPLETED', 'FAILED') THEN CURRENT_TIMESTAMP ELSE ldap_completed_at END,
                error_message = COALESCE(p_error_message, error_message),
                updated_at = CURRENT_TIMESTAMP
            WHERE file_id = p_file_id;
    END CASE;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION update_file_processing_status(VARCHAR, VARCHAR, VARCHAR, TEXT) IS '파일 처리 상태 업데이트';

-- ================================================================
-- 6. 중복 인증서 확인 함수
-- ================================================================
CREATE OR REPLACE FUNCTION check_certificate_duplicate(
    p_fingerprint_sha256 VARCHAR(64)
)
RETURNS BOOLEAN AS $$
DECLARE
    v_exists BOOLEAN;
BEGIN
    SELECT EXISTS(
        SELECT 1 
        FROM certificates 
        WHERE fingerprint_sha256 = p_fingerprint_sha256
          AND is_latest = TRUE
    ) INTO v_exists;
    
    RETURN v_exists;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION check_certificate_duplicate(VARCHAR) IS 'SHA256 fingerprint로 중복 인증서 확인';

-- ================================================================
-- 7. 최신 버전 파일 조회 함수
-- ================================================================
CREATE OR REPLACE FUNCTION get_latest_version(
    p_collection_number VARCHAR(3)
)
RETURNS VARCHAR(10) AS $$
DECLARE
    v_latest_version VARCHAR(10);
BEGIN
    SELECT MAX(version_number)
    INTO v_latest_version
    FROM pkd_files
    WHERE collection_number = p_collection_number
      AND upload_status = 'APPLIED';
    
    RETURN COALESCE(v_latest_version, '000000');
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_latest_version(VARCHAR) IS 'Collection별 최신 버전 번호 조회';

-- ================================================================
-- 8. 처리 대기 중인 파일 조회 함수
-- ================================================================
CREATE OR REPLACE FUNCTION get_pending_files()
RETURNS TABLE (
    file_id VARCHAR(36),
    file_type VARCHAR(20),
    file_format VARCHAR(30),
    uploaded_at TIMESTAMP
) AS $
BEGIN
    RETURN QUERY
    SELECT 
        pf.file_id,
        pf.file_type,
        pf.file_format,
        pf.uploaded_at
    FROM pkd_files pf
    WHERE pf.upload_status = 'UPLOADED'
      AND (pf.parse_status IS NULL OR pf.parse_status = 'FAILED')
    ORDER BY pf.uploaded_at ASC;
END;
$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_pending_files() IS '처리 대기 중인 파일 목록 조회';

-- ================================================================
-- 9. 통계 요약 함수
-- ================================================================
CREATE OR REPLACE FUNCTION get_global_statistics()
RETURNS TABLE (
    total_countries INTEGER,
    total_csca INTEGER,
    total_dsc INTEGER,
    valid_csca INTEGER,
    valid_dsc INTEGER,
    expired_csca INTEGER,
    expired_dsc INTEGER,
    revoked_csca INTEGER,
    revoked_dsc INTEGER,
    total_revocations INTEGER,
    active_deviations INTEGER
) AS $
BEGIN
    RETURN QUERY
    SELECT
        COUNT(DISTINCT c.country_code)::INTEGER as total_countries,
        COUNT(*) FILTER (WHERE c.cert_type = 'CSCA')::INTEGER as total_csca,
        COUNT(*) FILTER (WHERE c.cert_type = 'DSC')::INTEGER as total_dsc,
        COUNT(*) FILTER (WHERE c.cert_type = 'CSCA' AND c.status = 'VALID')::INTEGER as valid_csca,
        COUNT(*) FILTER (WHERE c.cert_type = 'DSC' AND c.status = 'VALID')::INTEGER as valid_dsc,
        COUNT(*) FILTER (WHERE c.cert_type = 'CSCA' AND c.status = 'EXPIRED')::INTEGER as expired_csca,
        COUNT(*) FILTER (WHERE c.cert_type = 'DSC' AND c.status = 'EXPIRED')::INTEGER as expired_dsc,
        COUNT(*) FILTER (WHERE c.cert_type = 'CSCA' AND c.status = 'REVOKED')::INTEGER as revoked_csca,
        COUNT(*) FILTER (WHERE c.cert_type = 'DSC' AND c.status = 'REVOKED')::INTEGER as revoked_dsc,
        (SELECT COUNT(*)::INTEGER FROM crl_entries) as total_revocations,
        (SELECT COUNT(*)::INTEGER FROM deviations WHERE status = 'ACTIVE') as active_deviations
    FROM certificates c
    WHERE c.is_latest = TRUE;
END;
$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_global_statistics() IS '전체 시스템 통계 요약';

-- ================================================================
-- End of Migration
-- ================================================================