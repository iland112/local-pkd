-- Test data for duplicate check API testing
-- Insert sample file upload history records

INSERT INTO file_upload_history (
    filename,
    collection_number,
    version,
    file_format,
    file_size_bytes,
    file_size_display,
    uploaded_at,
    status,
    file_hash,
    created_at
) VALUES
(
    'icaopkd-001-complete-009410.ldif',
    '001',
    '009410',
    'CSCA_COMPLETE_LDIF',
    78643200,
    '75.0 MB',
    NOW(),
    'SUCCESS',
    'abc123def456789012345678901234567890123456789012345678901234abcd',
    NOW()
),
(
    'icaopkd-002-complete-July2025.ml',
    '002',
    'July2025',
    'EMRTD_COMPLETE_LDIF',
    45678900,
    '43.5 MB',
    NOW() - INTERVAL '1 day',
    'SUCCESS',
    'def456abc789012345678901234567890123456789012345678901234567890ef',
    NOW() - INTERVAL '1 day'
);

-- Verify insertion
SELECT id, filename, file_hash, status, version
FROM file_upload_history
ORDER BY uploaded_at DESC
LIMIT 5;