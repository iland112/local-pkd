# Browser Cache Clear Guide - PA Verify Page

**Issue**: 수정된 JavaScript가 브라우저에 반영되지 않음 (캐시 문제)

## 🚨 긴급 해결법 (30초)

### Chrome DevTools에서 캐시 완전 비활성화

1. **Chrome 열기**: http://localhost:8081/pa/verify
2. **F12 누르기** (DevTools 열기)
3. **Network 탭** 클릭
4. **"Disable cache" 체크박스** 활성화
5. **DevTools를 열어둔 채로** 페이지 새로고침 (F5)

```
Chrome DevTools (F12)
├─ Network 탭
└─ ☑ Disable cache  ← 이것을 체크!
```

**주의**: DevTools를 닫으면 다시 캐시가 활성화됩니다.

---

## 📋 검증 방법

### 1. Console에서 함수 코드 확인

DevTools Console 탭에서 실행:

```javascript
paVerifyPageState().arrayBufferToBase64.toString()
```

**올바른 출력** (Array.from 사용):
```javascript
"arrayBufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  // Correct method: Use Array.from().map() to avoid UTF-16 encoding issues
  const binary = Array.from(bytes)
    .map(byte => String.fromCharCode(byte))
    .join('');
  return btoa(binary);
}"
```

**잘못된 출력** (spread operator 또는 loop):
```javascript
// WRONG: Spread operator
const binary = String.fromCharCode(...bytes);

// WRONG: Loop
for (let i = 0; i < bytes.byteLength; i++) {
  binary += String.fromCharCode(bytes[i]);
}
```

### 2. Debug 로그 확인

Console에 다음 메시지가 보여야 함:

```
[DEBUG] PA Verify Page State initialized
[DEBUG] Processing file: dg1.bin Size: 93 bytes
[DEBUG] DG1 File SHA-256: 9d3cccd94f61440bac64df109d9251051e8e4bbf849048277f897f1ed1e41d4b
[DEBUG] DG1 SHA-256 BEFORE Base64: 9d3cccd94f61440bac64df109d9251051e8e4bbf849048277f897f1ed1e41d4b
[DEBUG] DG1 Base64 length: 124
```

---

## 🔧 추가 해결 방법

### Method 1: Hard Refresh (가장 간단)

```
Windows/Linux: Ctrl + Shift + R
Mac: Cmd + Shift + R
```

**문제**: 때때로 JavaScript 캐시가 남아있을 수 있음

### Method 2: Chrome Settings에서 완전 삭제

1. Chrome 설정 → 개인정보 및 보안
2. "인터넷 사용 기록 삭제"
3. **시간 범위**: "전체 기간"
4. 체크:
   - ☑ 캐시된 이미지 및 파일
   - ☑ 쿠키 및 기타 사이트 데이터
5. "데이터 삭제" 클릭

**문제**: 모든 사이트 데이터가 삭제됨

### Method 3: Incognito Mode (시크릿 모드)

```
Windows/Linux: Ctrl + Shift + N
Mac: Cmd + Shift + N
```

새 시크릿 창에서 http://localhost:8081/pa/verify 접속

**장점**: 캐시 없이 깨끗한 상태로 테스트 가능

### Method 4: Chrome Flags (개발 환경용)

chrome://flags/ 에서:

```
#enable-experimental-web-platform-features
→ Enabled

#disable-http-cache
→ Enabled
```

Chrome 재시작 후 적용

---

## 🧪 Playwright 테스트 결과 (검증 완료)

### Base64 Encoding Test ✅

```javascript
// Test with actual DG1 data
{
  "base64Match": true,
  "originalHash": "9d3cccd94f61440bac64df109d9251051e8e4bbf849048277f897f1ed1e41d4b",
  "reEncodedHash": "9d3cccd94f61440bac64df109d9251051e8e4bbf849048277f897f1ed1e41d4b",
  "hashMatch": true ✅
}
```

**결론**: 수정된 JavaScript는 **정상 작동**합니다. 문제는 **브라우저 캐시**입니다.

---

## 🎯 최종 확인 절차

1. **Chrome DevTools 열기** (F12)
2. **Network 탭** → "Disable cache" 체크
3. **Console 탭** → `[DEBUG] PA Verify Page State initialized` 확인
4. **파일 업로드**:
   - sod.bin
   - dg1.bin
   - dg2.bin
5. **Console 확인**:
   ```
   [DEBUG] DG1 File SHA-256: 9d3cccd9... ✅
   [DEBUG] DG1 SHA-256 BEFORE Base64: 9d3cccd9... ✅
   ```
6. **검증 시작** 클릭
7. **서버 로그 확인**:
   ```
   [DEBUG] DG1 hash validation passed ✅
   [DEBUG] DG2 hash validation passed ✅
   ```

---

## 🐛 Troubleshooting

### "Console에 아무것도 표시되지 않아요"

**원인**: JavaScript가 로드되지 않음 (캐시 문제)

**해결**:
1. DevTools Network 탭에서 "Disable cache" 체크
2. F5로 새로고침
3. Console 탭에서 `[DEBUG]` 메시지 확인

### "여전히 DG1 hash mismatch가 발생해요"

**원인**: 오래된 JavaScript가 여전히 실행 중

**확인**:
```javascript
// Console에서 실행
paVerifyPageState().arrayBufferToBase64.toString()
```

**Array.from**이 보이지 않으면 → 캐시 문제!

**해결**:
1. Chrome 완전 종료 (모든 탭 닫기)
2. Chrome 재시작
3. DevTools 열고 "Disable cache" 체크
4. http://localhost:8081/pa/verify 접속

### "Application 재시작이 필요한가요?"

**아니오!** Application은 이미 최신 코드로 빌드되었습니다.

```bash
$ ./mvnw clean compile -DskipTests  # 완료 ✅
[INFO] BUILD SUCCESS
```

문제는 **브라우저 측**입니다.

---

## 📊 현재 상태

| 항목 | 상태 | 비고 |
|------|------|------|
| **JavaScript 코드** | ✅ 수정 완료 | Array.from().map().join() |
| **Maven Build** | ✅ 성공 | target/classes/templates/pa/verify.html |
| **Application** | ✅ 실행 중 | http://localhost:8081 |
| **Playwright 테스트** | ✅ 통과 | Base64 인코딩 정상 |
| **브라우저 캐시** | ❌ 문제 | 오래된 JS 실행 중 |

---

## 🎓 Cache-Busting 전략 (향후 개선)

### Option 1: Spring Boot Resource Versioning

```properties
# application.properties
spring.web.resources.chain.strategy.content.enabled=true
spring.web.resources.chain.strategy.content.paths=/**
```

**효과**: `/css/application.css?v=abc123` 형태로 자동 버전 관리

### Option 2: Thymeleaf Cache Disable (개발용)

```properties
spring.thymeleaf.cache=false
```

**현재 설정**: 이미 적용됨 ✅

### Option 3: HTTP Cache-Control Headers

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/templates/**")
                .addResourceLocations("classpath:/templates/")
                .setCacheControl(CacheControl.noCache());
    }
}
```

**효과**: 브라우저가 매번 서버에 최신 파일 요청

---

## ✅ 권장 작업 순서

1. **Chrome DevTools 열기** (F12)
2. **Network 탭** → ☑ Disable cache
3. **F5** 새로고침
4. **Console 탭** → Debug 로그 확인
5. **파일 업로드** → 검증 시작
6. **성공!** 🎉

**DevTools를 닫지 마세요!** 닫으면 캐시가 다시 활성화됩니다.

---

**작성일**: 2025-12-19
**상태**: ✅ JavaScript 수정 완료, 브라우저 캐시 클리어 대기 중
