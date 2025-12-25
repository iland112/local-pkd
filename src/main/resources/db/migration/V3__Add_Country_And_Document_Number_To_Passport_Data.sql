-- V3: Add issuing_country and document_number to passport_data table
-- These fields store passport information for display in PA History page

ALTER TABLE passport_data
    ADD COLUMN IF NOT EXISTS issuing_country VARCHAR(3),
    ADD COLUMN IF NOT EXISTS document_number VARCHAR(20);

-- Add comments for documentation
COMMENT ON COLUMN passport_data.issuing_country IS 'ISO 3166-1 alpha-2 or alpha-3 country code of passport issuer';
COMMENT ON COLUMN passport_data.document_number IS 'Passport document number';

-- Create index for filtering by country
CREATE INDEX IF NOT EXISTS idx_passport_issuing_country ON passport_data(issuing_country);
