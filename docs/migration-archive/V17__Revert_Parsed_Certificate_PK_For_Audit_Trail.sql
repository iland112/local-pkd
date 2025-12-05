-- V17: Revert parsed_certificate primary key to composite (parsed_file_id, fingerprint_sha256)
-- Reason: ICAO PKD files are periodically updated and contain duplicate certificates
--         We need to track the same certificate appearing in multiple uploads (audit trail)
--         While the Certificate entity (certificate table) maintains global uniqueness,
--         ParsedFile (parsed_certificate table) should allow tracking duplicate occurrences
--
-- User Requirement: "ICAO PKD 파일을 다운 받을 때 주기적으로 변경된 내역을 업데이트 해야 하므로
--                    증복 인증서가 발생할 수 있음을 감안하자. 중복된 인증서는 저장하지 않더라도
--                    중복 이력은 기록해둬야. 나중에 audit 에 도움이 되지 않을까?"
--
-- Design Decision:
--   - Certificate table (certificatevalidation context): Global uniqueness by fingerprint_sha256
--   - ParsedCertificate table (fileparsing context): Per-upload tracking with (parsed_file_id, fingerprint_sha256)
--
-- Date: 2025-12-05

-- 1. Drop existing primary key constraint (fingerprint_sha256)
ALTER TABLE parsed_certificate
    DROP CONSTRAINT parsed_certificate_pkey;

-- 2. Add new composite primary key on (parsed_file_id, fingerprint_sha256)
--    This allows the same certificate to appear in multiple ParsedFiles for audit purposes
ALTER TABLE parsed_certificate
    ADD CONSTRAINT parsed_certificate_pkey PRIMARY KEY (parsed_file_id, fingerprint_sha256);

-- 3. Create index on fingerprint_sha256 for global lookups
--    (This was previously the primary key index, now it's a regular index)
CREATE INDEX IF NOT EXISTS idx_parsed_certificate_fingerprint ON parsed_certificate(fingerprint_sha256);

-- 4. Drop the old index on (parsed_file_id, serial_number) if it exists
--    (This was created in V7 but is no longer needed)
DROP INDEX IF EXISTS idx_parsed_certificate_file_serial;

-- Note: This change enables audit trail tracking while maintaining data integrity:
--       - Same certificate can appear in multiple uploads (tracked in parsed_certificate)
--       - Certificate entities are still deduplicated globally (certificate table)
--       - Each ParsedFile can have at most one copy of each certificate (by fingerprint)
