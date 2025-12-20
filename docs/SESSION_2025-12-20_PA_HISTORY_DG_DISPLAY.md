# Session 2025-12-20: PA History Page DG1/DG2 Display Feature

**Date**: 2025-12-20
**Phase**: PA Phase 4.14 Enhancement
**Status**: Completed

---

## Summary

이번 세션에서는 PA 검증 이력 페이지(history.html)에서 VALID 레코드의 상세 보기 다이얼로그에 DG1(MRZ)과 DG2(얼굴 이미지)를 표시하는 기능을 구현했습니다.

---

## Completed Tasks

### 1. 검증 시각 필드 매핑 수정

**문제**: history.html에서 `verifiedAt` 필드를 사용했으나 API는 `verificationTimestamp`를 반환

**수정 위치** (3곳):
- Line 231: 테이블 행의 검증 시각
- Line 355: 상세 모달의 검증 시각
- Line 450: 정렬 파라미터 (`completedAt,desc`)

### 2. 새 API 엔드포인트 추가

**Endpoint**: `GET /api/pa/{verificationId}/datagroups`

**파일**: `PassiveAuthenticationController.java`

```java
@GetMapping("/{verificationId}/datagroups")
public ResponseEntity<Map<String, Object>> getDataGroups(
    @PathVariable UUID verificationId
) {
    // PassportData에서 DG1, DG2 조회
    // Dg1MrzParser, Dg2FaceImageParser로 파싱
    // 결과 반환 (hasDg1, hasDg2, dg1, dg2)
}
```

**응답 예시**:
```json
{
  "verificationId": "550e8400-e29b-41d4-a716-446655440000",
  "hasDg1": true,
  "hasDg2": true,
  "dg1": {
    "surname": "HONG",
    "givenNames": "GILDONG",
    "documentNumber": "M12345678",
    "nationality": "KOR",
    "dateOfBirth": "1980-01-01",
    "sex": "M",
    "expirationDate": "2025-01-01"
  },
  "dg2": {
    "faceCount": 1,
    "faceImages": [{
      "imageFormat": "JPEG",
      "imageSize": 11790,
      "imageDataUrl": "data:image/jpeg;base64,/9j/4AAQ..."
    }]
  }
}
```

### 3. UseCase 메서드 추가

**파일**: `GetPassiveAuthenticationHistoryUseCase.java`

```java
public PassportData getPassportDataById(UUID verificationId) {
    return passportDataRepository.findById(PassportDataId.of(verificationId.toString()))
        .orElse(null);
}
```

### 4. History 상세 다이얼로그 UI 구현

**파일**: `history.html`

**추가된 Alpine.js 상태 변수**:
- `dgData`: DG 데이터 저장
- `dgLoading`: 로딩 상태
- `dgError`: 에러 메시지

**추가된 함수**:
- `loadDgData(verificationId)`: API 호출하여 DG 데이터 로드
- `viewDetails(record)` 수정: VALID 레코드 시 자동으로 DG 로드

**UI 레이아웃**:
- DG2 (얼굴 이미지): 왼쪽, 컴팩트 (w-28)
- DG1 (MRZ): 오른쪽, 확장
- 가로 배치 (flex-row)
- 로딩 인디케이터 및 에러 처리 포함

---

## Code Changes

### Modified Files

1. **PassiveAuthenticationController.java**
   - Added: `getDataGroups()` endpoint (~70 lines)

2. **GetPassiveAuthenticationHistoryUseCase.java**
   - Added: `getPassportDataById()` method (~10 lines)

3. **history.html**
   - Added: DG display section in modal (~80 lines HTML)
   - Added: JavaScript state and functions (~35 lines)
   - Fixed: verificationTimestamp field mapping (3 locations)

### Code Statistics

- Backend: ~80 lines added
- Frontend: ~115 lines added
- Total: ~195 lines of code

---

## Testing Notes

1. 애플리케이션 시작: `./mvnw spring-boot:run`
2. PA 검증 페이지에서 검증 수행: http://localhost:8081/pa/verify
3. 이력 페이지에서 VALID 레코드 상세 보기: http://localhost:8081/pa/history
4. 상세 다이얼로그에서 "여권 데이터" 섹션 확인

---

## Related Files

- [PassiveAuthenticationController.java](../src/main/java/com/smartcoreinc/localpkd/passiveauthentication/infrastructure/web/PassiveAuthenticationController.java)
- [GetPassiveAuthenticationHistoryUseCase.java](../src/main/java/com/smartcoreinc/localpkd/passiveauthentication/application/usecase/GetPassiveAuthenticationHistoryUseCase.java)
- [history.html](../src/main/resources/templates/pa/history.html)

---

## Next Steps

- Phase 5: PA UI Advanced Features
  - 실시간 검증 진행 상황 (SSE)
  - 배치 검증 지원
  - 검증 리포트 내보내기

---

**Author**: Claude AI Assistant
**Build Status**: SUCCESS (249 source files compiled)
