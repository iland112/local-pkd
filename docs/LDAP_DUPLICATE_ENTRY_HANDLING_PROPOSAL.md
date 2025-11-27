# LDAP Duplicate Entry Handling Proposal

**작성일**: 2025-11-27
**작성자**: Claude (AI Assistant)
**상태**: Proposal

---

## 현재 상황

### 현재 구현 (Skip 방식)

**파일**: `UnboundIdLdapAdapter.java`
**메서드**: `addLdifEntry(String ldifEntryText)`
**코드**: line 216-220

```java
// 중복 체크
if (isDuplicateEntry(convertedDn)) {
    log.warn("Duplicate entry detected, skipping: {}", convertedDn);
    return false;  // 중복이면 skip
}
```

**동작**:
- LDAP에 동일한 DN이 이미 존재하면 추가하지 않고 건너뜀
- 기존 entry는 그대로 유지
- `false` 반환 (실패로 간주)

**장점**:
- ✅ 멱등성 보장 (여러 번 실행해도 결과 동일)
- ✅ 안전함 (기존 데이터 손실 없음)
- ✅ 빠른 처리 (추가 작업 불필요)

**단점**:
- ❌ 최신 데이터 반영 불가 (업데이트 안 됨)
- ❌ 재업로드 시 변경사항 반영 불가

---

## 제안 방안

### 방안 1: Skip (현재 구현, 권장)

**설명**: 중복이면 건너뛰고 기존 entry 유지

**사용 사례**:
- 초기 데이터 로드 후 변경 없음
- 동일한 Master List를 여러 번 업로드
- 네트워크 오류 후 재시도

**장점**:
- 멱등성 보장
- 안전함 (데이터 손실 없음)
- 빠름

**단점**:
- 업데이트 불가

**구현**: 이미 완료

---

### 방안 2: Update (Replace)

**설명**: 중복이면 기존 entry를 **삭제**하고 새로운 entry 추가

**사용 사례**:
- Master List 버전 업데이트 (예: July 2025 → August 2025)
- 인증서 갱신 (Serial Number 동일하지만 내용 변경)
- 최신 CRL 반영

**구현 방법**:

```java
if (isDuplicateEntry(convertedDn)) {
    log.info("Duplicate entry detected, replacing: {}", convertedDn);

    // 1. 기존 entry 삭제
    DeleteRequest deleteRequest = new DeleteRequest(convertedDn);
    LDAPResult deleteResult = connection.delete(deleteRequest);

    if (deleteResult.getResultCode() != ResultCode.SUCCESS) {
        log.error("Failed to delete existing entry: {}", convertedDn);
        return false;
    }

    log.debug("Existing entry deleted: {}", convertedDn);
    // 2. 새로운 entry 추가 (아래 로직 계속 실행)
}
```

**장점**:
- ✅ 최신 데이터 반영
- ✅ 간단한 로직 (delete + add)
- ✅ DN 변경 가능

**단점**:
- ⚠️ 일시적 데이터 부재 (delete와 add 사이)
- ⚠️ 트랜잭션 보장 필요 (delete 성공 후 add 실패 시 복구)
- ❌ 멱등성 보장 약함

**위험성**:
- Delete 성공 후 Add 실패 시 데이터 손실
- LDAP에 트랜잭션 개념이 없어 rollback 불가

---

### 방안 3: Modify (Partial Update)

**설명**: 중복이면 기존 entry의 **attribute만 업데이트** (DN 유지)

**사용 사례**:
- 인증서 검증 상태 업데이트 (예: PENDING → VALID)
- CRL 버전 업데이트 (binary는 동일하지만 메타데이터 변경)
- pkdVersion 업데이트

**구현 방법**:

```java
if (isDuplicateEntry(convertedDn)) {
    log.info("Duplicate entry detected, updating attributes: {}", convertedDn);

    // 1. 기존 entry 조회
    Entry existingEntry = connection.getEntry(convertedDn);
    if (existingEntry == null) {
        log.error("Existing entry not found: {}", convertedDn);
        return false;
    }

    // 2. 변경할 attribute 목록 생성
    List<Modification> modifications = new ArrayList<>();
    for (Attribute newAttr : entry.getAttributes()) {
        Attribute existingAttr = existingEntry.getAttribute(newAttr.getName());

        // 기존 attribute와 다르면 REPLACE
        if (existingAttr == null || !existingAttr.equals(newAttr)) {
            modifications.add(new Modification(
                ModificationType.REPLACE,
                newAttr.getName(),
                newAttr.getValues()
            ));
        }
    }

    if (modifications.isEmpty()) {
        log.debug("No changes detected, skipping update: {}", convertedDn);
        return true;  // 동일하므로 성공으로 간주
    }

    // 3. ModifyRequest 실행
    ModifyRequest modifyRequest = new ModifyRequest(convertedDn, modifications);
    LDAPResult modifyResult = connection.modify(modifyRequest);

    if (modifyResult.getResultCode() == ResultCode.SUCCESS) {
        log.info("Entry attributes updated: {}", convertedDn);
        return true;
    } else {
        log.error("Failed to update entry: {}", convertedDn);
        return false;
    }
}
```

**장점**:
- ✅ 원자적 업데이트 (LDAP ModifyRequest는 atomic)
- ✅ DN 유지 (참조 무결성 보장)
- ✅ 부분 업데이트 가능 (변경된 attribute만)
- ✅ 안전함 (데이터 손실 없음)

**단점**:
- ❌ 복잡한 로직 (attribute 비교 필요)
- ❌ DN 변경 불가 (DN이 달라지면 사용 불가)
- ⚠️ objectClass 변경 제약 (structural objectClass는 변경 불가)

**제약사항**:
- Binary attribute (userCertificate;binary, certificateRevocationList;binary) 크기가 크면 성능 저하
- objectClass 변경 불가 (예: person → inetOrgPerson 불가)

---

## 권장 방안

### **방안 1: Skip (현재 구현) + 설정 옵션 추가**

**이유**:
1. **안전성 최우선**: 인증서는 중요 데이터이므로 데이터 손실 위험 최소화
2. **멱등성 보장**: CI/CD 파이프라인에서 재실행 가능
3. **ICAO PKD 표준**: Master List는 버전별로 DN이 다르므로 충돌 가능성 낮음

**개선 제안**:

#### 1. 중복 처리 정책을 설정으로 관리

`application.properties`:
```properties
# LDAP duplicate entry handling strategy
# - SKIP: Skip duplicate entries (default, safest)
# - REPLACE: Delete and add (risky, data loss possible)
# - UPDATE: Modify attributes only (complex, limited use cases)
ldap.duplicate-strategy=SKIP
```

#### 2. Enum으로 전략 정의

```java
public enum DuplicateHandlingStrategy {
    SKIP,      // 현재 구현
    REPLACE,   // Delete + Add
    UPDATE     // ModifyRequest
}
```

#### 3. UnboundIdLdapAdapter에 전략 적용

```java
@Value("${ldap.duplicate-strategy:SKIP}")
private DuplicateHandlingStrategy duplicateStrategy;

public boolean addLdifEntry(String ldifEntryText) throws LDAPException {
    // ... (파싱 로직)

    if (isDuplicateEntry(convertedDn)) {
        switch (duplicateStrategy) {
            case SKIP:
                log.warn("Duplicate entry detected, skipping: {}", convertedDn);
                return false;

            case REPLACE:
                return replaceEntry(convertedDn, convertedEntry);

            case UPDATE:
                return updateEntryAttributes(convertedDn, convertedEntry);

            default:
                throw new IllegalStateException("Unknown strategy: " + duplicateStrategy);
        }
    }

    // ... (추가 로직)
}
```

---

## 구현 우선순위

### Phase 1: 현재 상태 유지 (완료)
- ✅ Skip 방식 유지
- ✅ 로그에 명확한 메시지 출력

### Phase 2: 설정 옵션 추가 (권장)
- [ ] `DuplicateHandlingStrategy` enum 생성
- [ ] `application.properties`에 설정 추가
- [ ] UnboundIdLdapAdapter에 switch 로직 추가
- [ ] 단위 테스트 작성

### Phase 3: Replace 전략 구현 (선택)
- [ ] `replaceEntry()` 메서드 구현
- [ ] 트랜잭션 실패 처리 (rollback 로직)
- [ ] 통합 테스트 작성

### Phase 4: Update 전략 구현 (선택)
- [ ] `updateEntryAttributes()` 메서드 구현
- [ ] Attribute 비교 로직
- [ ] objectClass 변경 제약 처리
- [ ] 통합 테스트 작성

---

## 테스트 시나리오

### Scenario 1: 동일 Master List 재업로드
- **Input**: ICAO_ml_July2025.ml (두 번 업로드)
- **Expected (SKIP)**: 두 번째 업로드 시 "Duplicate entry detected, skipping" 로그
- **Expected (REPLACE)**: 두 번째 업로드 시 기존 entry 삭제 후 새로 추가

### Scenario 2: 인증서 갱신
- **Input**: 동일 Serial Number, 다른 유효기간
- **Expected (SKIP)**: 기존 인증서 유지
- **Expected (UPDATE)**: 유효기간 attribute만 업데이트

### Scenario 3: CRL 업데이트
- **Input**: 동일 Issuer, 다른 thisUpdate 날짜
- **Expected (SKIP)**: 기존 CRL 유지
- **Expected (REPLACE)**: 최신 CRL로 교체

---

## 결론

**현재 구현 (Skip)을 유지**하되, **설정 옵션을 추가**하여 필요 시 Replace 또는 Update 전략을 선택할 수 있도록 확장성을 확보하는 것을 권장합니다.

**즉시 조치 필요 사항**: 없음 (현재 구현으로 충분)

**향후 개선 사항**: Phase 2 (설정 옵션 추가)를 Phase 19 또는 Phase 20에서 구현 고려

---

**문서 버전**: 1.0
**검토 상태**: Proposal (사용자 승인 대기)
