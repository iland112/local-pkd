-- V4: Update FileFormat Types for Collection 003 (Non-Conformant DSC)
-- 기존 Collection 003 파일들의 file_format을 EMRTD_COMPLETE_LDIF에서 DSC_NC_COMPLETE_LDIF로 업데이트
-- 또한 Collection 001 파일들을 EMRTD에서 DSC로 명확하게 업데이트

-- Update Collection 003 Complete files (Non-Conformant DSC)
UPDATE uploaded_file
SET file_format = 'DSC_NC_COMPLETE_LDIF'
WHERE file_name LIKE '%003-complete%'
  AND file_format = 'EMRTD_COMPLETE_LDIF';

-- Update Collection 003 Delta files (Non-Conformant DSC)
UPDATE uploaded_file
SET file_format = 'DSC_NC_DELTA_LDIF'
WHERE file_name LIKE '%003-delta%'
  AND file_format = 'EMRTD_DELTA_LDIF';

-- Update Collection 001 Complete files (DSC - Document Signer Certificate)
-- EMRTD_COMPLETE_LDIF -> DSC_COMPLETE_LDIF 로 이름 변경 (collection 001만)
UPDATE uploaded_file
SET file_format = 'DSC_COMPLETE_LDIF'
WHERE file_name LIKE '%001-complete%'
  AND file_format = 'EMRTD_COMPLETE_LDIF';

-- Update Collection 001 Delta files (DSC - Document Signer Certificate)
UPDATE uploaded_file
SET file_format = 'DSC_DELTA_LDIF'
WHERE file_name LIKE '%001-delta%'
  AND file_format = 'EMRTD_DELTA_LDIF';
