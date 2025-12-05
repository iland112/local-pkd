ALTER TABLE parsed_certificate
ADD COLUMN all_attributes JSONB;

COMMENT ON COLUMN parsed_certificate.all_attributes IS 'LDIF 파일에서 파싱된 모든 속성을 저장하는 JSONB 컬럼';
