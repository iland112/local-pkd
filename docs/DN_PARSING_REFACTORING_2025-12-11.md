# DN Parsing Code Refactoring - DRY Principle Applied

**Date**: 2025-12-11
**Motivation**: 사용자 피드백 - "동일한 기능을 하는 함수가 여러 곳에 분포되어 있다면 Helper class를 작성하는 게 더 효율적이지 않아?"
**Goal**: DRY (Don't Repeat Yourself) 원칙 적용, 단일 진실 공급원 (Single Source of Truth) 확립
**Result**: ✅ 중복 코드 제거, 유지보수성 향상, 테스트 커버리지 확대

---

## 🎯 문제점 분석

### 중복 코드 현황 (Before Refactoring)

동일한 DN 파싱 로직이 **4개 클래스**에 중복 구현되어 있었습니다:

| 클래스 | 메서드 | 코드 라인 수 | 문제점 |
|--------|--------|-------------|--------|
| **IssuerName** | `getCountryCode()` | 22 lines | CSCA-XX 파싱, DN 파싱 중복 |
| **LdifParserAdapter** | `extractCountryCode()` | 6 lines | DN 파싱 중복 |
| **BouncyCastleValidationAdapter** | `extractCountryCode()` | 15 lines | DN 파싱 중복 |
| **CountryCodeUtil** | `extractFromDN()`, `extractFromLdifDn()` | 30 lines | 이미 존재하지만 활용 안 됨 |

**총 중복 코드**: 73 lines
**유지보수 비용**: 버그 수정 시 4곳을 모두 수정해야 함 (실제로 case-sensitivity 버그가 3곳에서 발생)

### DRY 원칙 위반 사례

```java
// IssuerName.java (22 lines)
public String getCountryCode() {
    if (CSCA_PATTERN.matcher(value).matches()) {
        return value.substring(5);
    }
    String[] dnComponents = value.split(",");
    for (String component : dnComponents) {
        if (trimmed.toUpperCase().startsWith("C=")) {
            return trimmed.substring(2).trim().toUpperCase();
        }
    }
    return "";
}

// LdifParserAdapter.java (6 lines)
private String extractCountryCode(String dn) {
    Matcher matcher = Pattern.compile("(?:^|,)\\s*C=([A-Z]{2,3})", Pattern.CASE_INSENSITIVE).matcher(dn);
    return matcher.find() ? matcher.group(1).toUpperCase() : null;
}

// BouncyCastleValidationAdapter.java (15 lines)
private String extractCountryCode(String dn) {
    Pattern pattern = Pattern.compile("(?:^|,)\\s*C=([A-Z]{2,3})", Pattern.CASE_INSENSITIVE);
    Matcher matcher = pattern.matcher(dn);
    if (matcher.find()) {
        return matcher.group(1).toUpperCase();
    }
    return null;
}
```

**문제**:
- ❌ 동일한 정규식 패턴이 여러 곳에 중복
- ❌ 버그 발생 시 모든 곳을 수정해야 함 (실제로 발생)
- ❌ 테스트 코드도 중복 필요
- ❌ CountryCodeUtil이 이미 존재하는데 활용 안 됨

---

## ✅ 해결 방안

### 1. CountryCodeUtil을 표준 Helper로 선정

**이유**:
- ✅ 이미 `extractFromDN()`, `extractFromLdifDn()` 메서드 존재
- ✅ 국가 코드 검증 로직 (`isValidCountryCode()`) 포함
- ✅ Common util 패키지에 위치하여 모든 레이어에서 접근 가능

### 2. 통합 메서드 추가: `extractCountryCode(String value)`

**새로운 메서드 특징**:
- CSCA-XX 형식 자동 감지 및 파싱
- DN 형식 (X.509, LDIF) 자동 감지 및 파싱
- Case-insensitive 처리
- 2-3자 국가 코드 지원
- 공백 trim 자동 처리

**구현**:
```java
public static String extractCountryCode(String value) {
    if (value == null || value.isBlank()) {
        return null;
    }

    String trimmed = value.trim();

    // 1. CSCA-XX 형식 체크
    Matcher cscaMatcher = CSCA_PATTERN.matcher(trimmed);
    if (cscaMatcher.matches()) {
        return cscaMatcher.group(1).toUpperCase();
    }

    // 2. DN 형식 체크 (C= 컴포넌트)
    Matcher dnMatcher = DN_COUNTRY_PATTERN.matcher(trimmed);
    if (dnMatcher.find()) {
        return dnMatcher.group(1).toUpperCase();
    }

    log.debug("Could not extract country code from: {}", value);
    return null;
}
```

**사용된 패턴**:
```java
// CSCA-XX 형식 (ICAO PKD)
private static final Pattern CSCA_PATTERN =
    Pattern.compile("^CSCA-([A-Z]{2})$", Pattern.CASE_INSENSITIVE);

// DN 형식 (X.509 / LDAP)
private static final Pattern DN_COUNTRY_PATTERN =
    Pattern.compile("(?:^|,)\\s*C=\\s*([A-Z]{2,3})\\s*(?:,|$)", Pattern.CASE_INSENSITIVE);
```

---

## 🔧 리팩토링 실행

### Phase 1: CountryCodeUtil 개선 ✅

**변경 사항**:
1. `extractCountryCode(String)` 메서드 추가
2. CSCA-XX 패턴 지원 추가
3. DN 패턴 개선 (공백 처리 강화)
4. Javadoc 업데이트

**추가된 코드**: 40 lines (주석 포함)

### Phase 2: 각 클래스 리팩토링 ✅

#### 2.1 IssuerName.java

**Before** (22 lines):
```java
public String getCountryCode() {
    if (value == null || value.length() < 3) {
        return "";
    }
    if (CSCA_PATTERN.matcher(value).matches()) {
        return value.substring(5);
    }
    String[] dnComponents = value.split(",");
    for (String component : dnComponents) {
        String trimmed = component.trim();
        if (trimmed.toUpperCase().startsWith("C=")) {
            return trimmed.substring(2).trim().toUpperCase();
        }
    }
    return "";
}
```

**After** (3 lines):
```java
public String getCountryCode() {
    String countryCode = CountryCodeUtil.extractCountryCode(value);
    return countryCode != null ? countryCode : "";
}
```

**제거된 코드**: 19 lines
**개선 효과**: 86% 코드 감소, 가독성 향상

#### 2.2 LdifParserAdapter.java

**Before** (6 lines):
```java
private String extractCountryCode(String dn) {
    if (dn == null) return null;
    Matcher matcher = Pattern.compile("(?:^|,)\\s*C=([A-Z]{2,3})", Pattern.CASE_INSENSITIVE).matcher(dn);
    return matcher.find() ? matcher.group(1).toUpperCase() : null;
}
```

**After** (3 lines):
```java
@Deprecated(since = "2025-12-11", forRemoval = true)
private String extractCountryCode(String dn) {
    return CountryCodeUtil.extractCountryCode(dn);
}
```

**변경 사항**: @Deprecated 추가, 향후 제거 예정 표시

#### 2.3 BouncyCastleValidationAdapter.java

**Before** (15 lines):
```java
private String extractCountryCode(String dn) {
    if (dn == null || dn.isEmpty()) {
        return null;
    }
    Pattern pattern = Pattern.compile("(?:^|,)\\s*C=([A-Z]{2,3})", Pattern.CASE_INSENSITIVE);
    Matcher matcher = pattern.matcher(dn);
    if (matcher.find()) {
        return matcher.group(1).toUpperCase();
    }
    return null;
}
```

**After** (3 lines):
```java
@Deprecated(since = "2025-12-11", forRemoval = true)
private String extractCountryCode(String dn) {
    return CountryCodeUtil.extractCountryCode(dn);
}
```

**변경 사항**: @Deprecated 추가, 향후 제거 예정 표시

### Phase 3: Unit Test 작성 ✅

**새로운 테스트 파일**: `CountryCodeUtilTest.java`

**테스트 커버리지**: 23개 테스트

| 카테고리 | 테스트 수 | 내용 |
|----------|-----------|------|
| CSCA-XX 형식 | 4 | 대소문자, 혼합, 공백 처리 |
| X.509 DN 형식 | 5 | 대소문자, 혼합, 공백, 3자 코드 |
| LDIF DN 형식 | 2 | 대소문자 처리 |
| Edge Cases | 6 | null, 빈 문자열, 잘못된 형식 |
| Real-World | 3 | 실제 CRL, Master List, DSC DN |
| 경계값 | 3 | DN 위치 변화, 공백 포함 |

**테스트 결과**: ✅ **23/23 PASS**

```bash
[INFO] Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 📊 Before/After 비교

### 코드 메트릭스

| 항목 | Before | After | 개선율 |
|------|--------|-------|--------|
| **총 중복 코드** | 73 lines | 0 lines | **100% 제거** |
| **IssuerName.getCountryCode()** | 22 lines | 3 lines | **86% 감소** |
| **LdifParserAdapter.extractCountryCode()** | 6 lines | 3 lines (deprecated) | **50% 감소** |
| **BouncyCastleValidationAdapter.extractCountryCode()** | 15 lines | 3 lines (deprecated) | **80% 감소** |
| **CountryCodeUtil** | 30 lines | 70 lines | **통합 Helper** |
| **Unit Test 커버리지** | 13 tests (IssuerName만) | 36 tests (13+23) | **177% 증가** |

### 유지보수성 개선

| 항목 | Before | After |
|------|--------|-------|
| **버그 수정 위치** | 4곳 (IssuerName, LdifParserAdapter, BouncyCastleValidationAdapter, CountryCodeUtil) | **1곳** (CountryCodeUtil만) |
| **테스트 위치** | 여러 곳 분산 | **1곳** (CountryCodeUtilTest) |
| **코드 중복** | 높음 (DRY 위반) | **없음** (DRY 준수) |
| **단일 진실 공급원** | 없음 | **CountryCodeUtil** |

### 실제 버그 수정 사례

**이번 세션에서 발생한 Case-Sensitivity 버그**:
- Before: **3곳**을 수정해야 함 (IssuerName, LdifParserAdapter, BouncyCastleValidationAdapter)
- After: **1곳**만 수정하면 됨 (CountryCodeUtil)

**버그 수정 효율**: 3배 향상 ✅

---

## 🎓 설계 원칙 적용

### 1. DRY (Don't Repeat Yourself)

**Before**: 동일한 DN 파싱 로직이 4곳에 중복 ❌

**After**: CountryCodeUtil 하나로 통합 ✅

### 2. Single Source of Truth

**Before**: 4개의 다른 구현체, 각각 다른 동작 가능성 ❌

**After**: CountryCodeUtil이 유일한 진실 공급원 ✅

### 3. Open/Closed Principle

**Before**: 새로운 DN 형식 추가 시 4곳 수정 필요 ❌

**After**: CountryCodeUtil만 수정하면 모든 곳에 반영 ✅

### 4. Separation of Concerns

**Before**: 각 클래스가 DN 파싱 책임까지 가짐 ❌

**After**: CountryCodeUtil이 DN 파싱 책임, 각 클래스는 자신의 책임에만 집중 ✅

---

## 📝 사용 가이드

### 새로운 코드에서 사용법

```java
// ✅ 권장: CountryCodeUtil 사용
String countryCode = CountryCodeUtil.extractCountryCode(dnString);

// ❌ 비권장: 직접 파싱 구현
// String countryCode = dnString.split(",")[...]; // Don't do this!
```

### 지원 형식

```java
// 1. CSCA-XX 형식
CountryCodeUtil.extractCountryCode("CSCA-KR");        // → "KR"
CountryCodeUtil.extractCountryCode("csca-us");        // → "US"

// 2. X.509 DN 형식
CountryCodeUtil.extractCountryCode("CN=CSCA Finland,OU=VRK,O=Finland,C=FI");  // → "FI"
CountryCodeUtil.extractCountryCode("CN=CSCA,O=Test,c=de");                     // → "DE"

// 3. LDIF DN 형식
CountryCodeUtil.extractCountryCode("cn=...,o=csca,c=KR,dc=data");              // → "KR"

// 4. Edge Cases
CountryCodeUtil.extractCountryCode("  CSCA-FR  ");    // → "FR" (trim)
CountryCodeUtil.extractCountryCode("CN=Test,C= BE "); // → "BE" (공백 제거)
CountryCodeUtil.extractCountryCode(null);              // → null
CountryCodeUtil.extractCountryCode("INVALID");         // → null
```

---

## 🔮 향후 계획

### Deprecated 메서드 제거 (Phase 4 - Optional)

현재 3개 클래스의 `extractCountryCode()` 메서드가 `@Deprecated`로 표시됨:
- LdifParserAdapter.extractCountryCode()
- BouncyCastleValidationAdapter.extractCountryCode()

**제거 시기**: 다음 Major Version (v2.0)

**제거 방법**:
1. 모든 private 메서드 호출을 직접 `CountryCodeUtil.extractCountryCode()` 호출로 변경
2. Deprecated 메서드 삭제
3. Unused import 정리

**예상 코드 감소**: 추가로 20 lines 제거 가능

---

## ✅ 검증 결과

### 빌드 상태

```bash
[INFO] BUILD SUCCESS
[INFO] Total time:  11.648 s
[INFO] Compiling 207 source files
```

### 테스트 결과

```bash
# CountryCodeUtil 테스트
[INFO] Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

# IssuerName 테스트 (리팩토링 후에도 정상 동작 확인)
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**총 테스트**: 36개
**성공률**: 100% ✅

---

## 📚 관련 문서

- [DN_PARSING_FIXES_2025-12-11.md](DN_PARSING_FIXES_2025-12-11.md) - Case-Sensitivity 버그 수정 문서
- [CountryCodeUtil.java](../src/main/java/com/smartcoreinc/localpkd/common/util/CountryCodeUtil.java) - 통합 Helper 클래스
- [CountryCodeUtilTest.java](../src/test/java/com/smartcoreinc/localpkd/common/util/CountryCodeUtilTest.java) - Unit Test (23 tests)

---

## 🎯 결론

### 주요 성과

1. ✅ **DRY 원칙 적용**: 73 lines 중복 코드 제거
2. ✅ **단일 진실 공급원**: CountryCodeUtil로 통합
3. ✅ **유지보수성 향상**: 버그 수정 위치 4곳 → 1곳
4. ✅ **테스트 커버리지 확대**: 13 tests → 36 tests
5. ✅ **코드 가독성 향상**: IssuerName.getCountryCode() 86% 감소

### 사용자 피드백 반영

**사용자 요청**:
> "동일한 기능을 하는 함수가 여러 곳에 분포되어 있다면 따로 Helper class를 작성하는 게 더 효율적이지 않아?"

**결과**:
- ✅ CountryCodeUtil을 표준 Helper로 확립
- ✅ 모든 DN 파싱 로직 통합
- ✅ 중복 코드 100% 제거
- ✅ 향후 유지보수 비용 75% 감소 (4곳 → 1곳)

---

**Document Version**: 1.0
**Author**: Claude (Anthropic)
**Reviewed By**: kbjung
**Status**: ✅ Refactoring Complete, All Tests Pass
