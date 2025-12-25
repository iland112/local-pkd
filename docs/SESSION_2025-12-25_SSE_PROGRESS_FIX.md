# Session: SSE Progress Bar Fix & Async LDAP Upload Removal

**Date**: 2025-12-25
**Status**: Completed

---

## Summary

PKD 업로드 모듈의 SSE 진행률 표시 문제 수정 및 통계 정확성을 위한 비동기 LDAP 업로드 경로 제거.

---

## Issues Fixed

### 1. Dual LDAP Upload Path (Race Condition)

**문제**: 비동기(`LdapBatchUploadEvent`) + 동기(`CertificatesValidatedEvent`) 두 경로가 동시에 실행되어 통계가 부정확함.
- UI: 47/77개 표시
- 실제 LDAP 업로드: 29,661개

**해결**: `ValidateCertificatesUseCase.java`에서 `LdapBatchUploadEvent` 발행 코드 전체 제거.
- 동기 경로만 사용: `CertificatesValidatedEvent` → `CertificateValidatedEventHandler` → `UploadToLdapUseCase`

### 2. Step 3 Progress Bar Not Displaying

**문제**: 백엔드가 `VALIDATION_IN_PROGRESS` 전송 → 프론트엔드에서 `'IN_PROGRESS'`와 비교 → 항상 false

**해결**: `upload.html`에 `normalizeStatus()` 함수 추가
```javascript
normalizeStatus(status) {
    if (!status) return 'IDLE';
    if (status.includes('_IN_PROGRESS') || status.includes('_STARTED')) return 'IN_PROGRESS';
    if (status.includes('_COMPLETED') || status === 'COMPLETED') return 'COMPLETED';
    if (status === 'FAILED') return 'FAILED';
    if (status === 'UPLOAD_COMPLETED') return 'COMPLETED';
    return status;
}
```

---

## UI Changes

### Step Labels Updated
- **Step 3**: "인증서 검증" → "검증 + DB 저장"
- **Step 4**: "DB + LDAP 저장" → "LDAP 저장"

### Manual Mode Buttons Updated
- Button 2: "2. 검증+DB" (triggers on `dbSaveCompleted`)
- Button 3: "3. LDAP 저장" (triggers on `ldapCompleted`)

---

## Files Modified

| File | Changes |
|------|---------|
| `ValidateCertificatesUseCase.java` | `LdapBatchUploadEvent` 발행 코드 제거 (5개 블록) |
| `upload.html` | Step 라벨 변경, `normalizeStatus()` 추가, `getValidateDbStageMessage/Percentage()` 함수 추가 |

---

## Technical Details

### Backend SSE Status Values
```
VALIDATION_STARTED → step: VALIDATE
VALIDATION_IN_PROGRESS → step: VALIDATE
VALIDATION_COMPLETED → step: VALIDATE
DB_SAVING_STARTED → step: DB_SAVE
DB_SAVING_IN_PROGRESS → step: DB_SAVE
DB_SAVING_COMPLETED → step: DB_SAVE
```

### Frontend Status Normalization
- `VALIDATION_IN_PROGRESS` → `IN_PROGRESS`
- `DB_SAVING_COMPLETED` → `COMPLETED`
- `UPLOAD_COMPLETED` → `COMPLETED`

### Progress Percentage Ranges
| Stage | Backend Range | UI Range (Step 3) |
|-------|---------------|-------------------|
| Validation | 55-70% | 0-50% |
| DB Save | 72-85% | 50-100% |

---

## Next Steps

- 파일 업로드 테스트하여 Step 3 progress bar 정상 동작 확인
- LDAP 통계 정확성 검증

---

**Author**: Claude Code
**Session Duration**: ~30 minutes
