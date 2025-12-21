# GraalVM Native Image Build Guide

**Version**: 1.0
**Last Updated**: 2025-12-22
**Status**: Production Ready

---

## Overview

Local PKD 애플리케이션의 GraalVM Native Image 빌드 및 실행 가이드입니다.

### Native Image 장점
- **빠른 시작 시간**: ~0.1초 (JVM: ~5초)
- **낮은 메모리 사용량**: ~100MB (JVM: ~500MB)
- **단일 실행 파일**: 컨테이너 없이 배포 가능

---

## Prerequisites

### 1. GraalVM 21 설치

```bash
# SDKMAN 사용 권장
sdk install java 21.0.2-graal
sdk use java 21.0.2-graal

# native-image 설치
gu install native-image

# 확인
java -version
native-image --version
```

### 2. 컨테이너 실행

```bash
# PostgreSQL + OpenLDAP 시작
./podman-start.sh
```

---

## Build

### Quick Build (권장)

```bash
# 테스트 스킵, 빠른 빌드
./scripts/native-build.sh --skip-tests

# 클린 빌드
./scripts/native-build.sh --clean --skip-tests
```

### Manual Build

```bash
./mvnw -Pnative native:compile -DskipTests
```

### Build Output

```
target/local-pkd    # Native 실행 파일 (~150MB)
```

---

## Run

### Quick Run (권장)

```bash
./scripts/native-run.sh

# 포트 변경
./scripts/native-run.sh --port=9090
```

### Manual Run

```bash
./target/local-pkd --spring.profiles.active=native
```

### Access

```
http://localhost:8081
```

---

## Configuration Files

### 1. reflect-config.json

Native Image에서 리플렉션이 필요한 클래스 등록.

**위치**: `src/main/resources/META-INF/native-image/reflect-config.json`

**필수 등록 클래스**:

| Category | Classes |
|----------|---------|
| Spring Data | `PageImpl`, `Sort`, `Pageable` |
| Hibernate | Entity classes, Proxy classes |
| BouncyCastle X.509 | `CertificateFactory`, `X509CertificateObject`, `X509CRLObject` |
| BouncyCastle RSA-PSS | `PSSSignatureSpi$SHA*`, `DigestSignatureSpi$SHA*`, `AlgorithmParametersSpi$PSS` |
| Jackson | DTOs, Response classes |

### 2. resource-config.json

Native Image에 포함할 리소스 파일.

**위치**: `src/main/resources/META-INF/native-image/resource-config.json`

```json
{
  "resources": {
    "includes": [
      {"pattern": "templates/.*"},
      {"pattern": "static/.*"},
      {"pattern": "application.*\\.properties"}
    ]
  }
}
```

### 3. application-native.properties

Native Image 전용 설정.

**위치**: `src/main/resources/application-native.properties`

```properties
# Native Image optimizations
spring.jpa.open-in-view=false
spring.main.lazy-initialization=true
```

---

## Troubleshooting

### 1. ClassNotFoundException: BouncyCastle classes

**증상**:
```
java.lang.ClassNotFoundException: org.bouncycastle.jcajce.provider.asymmetric.rsa.PSSSignatureSpi$SHA256withRSA
```

**해결**: `reflect-config.json`에 클래스 추가

```json
{
  "name": "org.bouncycastle.jcajce.provider.asymmetric.rsa.PSSSignatureSpi$SHA256withRSA",
  "allDeclaredMethods": true,
  "allDeclaredConstructors": true
}
```

**자주 누락되는 BouncyCastle 클래스**:
- `x509.CertificateFactory`
- `x509.X509CertificateObject`
- `x509.X509CRLObject`
- `rsa.PSSSignatureSpi` (및 SHA 변형들)
- `rsa.AlgorithmParametersSpi$PSS`
- `rsa.DigestSignatureSpi` (및 SHA 변형들)

### 2. MissingReflectionRegistrationError: PageImpl

**증상**:
```
MissingReflectionRegistrationError: PageImpl.getTotalPages() is not registered
```

**해결**: `reflect-config.json`에서 PageImpl 설정 변경

```json
{
  "name": "org.springframework.data.domain.PageImpl",
  "allDeclaredFields": true,
  "queryAllPublicMethods": true,
  "allPublicMethods": true
}
```

### 3. Thymeleaf Template Not Found

**증상**:
```
TemplateInputException: Error resolving template
```

**해결**: `resource-config.json`에 템플릿 패턴 추가

```json
{
  "resources": {
    "includes": [
      {"pattern": "templates/.*\\.html"}
    ]
  }
}
```

### 4. Alpine.js "X is not defined" Error

**증상**: 브라우저 콘솔에 `steps is not defined` 등 오류

**원인**: `<script>` 태그가 `</th:block>` 외부에 위치

**해결**: 스크립트를 content block 내부로 이동

```html
<!-- 잘못된 예 -->
</th:block>
<script>function myFunc() {...}</script>
</body>

<!-- 올바른 예 -->
<script>function myFunc() {...}</script>
</th:block>
</body>
```

### 5. Chart.js 500 Error

**증상**:
```
GET /webjars/chartjs/4.4.3/dist/chart.umd.min.js 500
```

**원인**: Webjar에 minified 버전 없음

**해결**: `chart.umd.min.js` → `chart.umd.js`

---

## Key Changes for Native Image Compatibility

### 1. Removed Thymeleaf Layout Dialect

Layout Dialect는 Groovy 의존성으로 인해 Native Image 비호환.

**변경 전**:
```html
<html layout:decorate="~{layout/main}">
  <div layout:fragment="content">
```

**변경 후** (Pure Fragment Pattern):
```html
<html th:replace="~{layout/base :: layout(~{::content})}">
  <th:block th:fragment="content">
```

### 2. Removed Unused Controllers

- `LdifUploadWebController.java` (삭제)
- `MasterListUploadWebController.java` (삭제)

기능이 `FileUploadWebController`로 통합됨.

### 3. Removed Unused Templates

- `templates/ldif/upload-ldif.html` (삭제)
- `templates/masterlist/upload-ml.html` (삭제)
- `templates/masterlist/result.html` (삭제)
- `templates/layout/main.html` (삭제)
- `templates/layout/head-fragments.html` (삭제)

### 4. Added Exception Handler for Chrome DevTools

Chrome DevTools 자동 요청 (`/.well-known/appspecific/com.chrome.devtools.json`)을
ERROR 대신 DEBUG 레벨로 조용히 처리.

---

## File Structure

```
src/main/resources/
├── META-INF/
│   └── native-image/
│       ├── reflect-config.json      # 리플렉션 설정
│       ├── resource-config.json     # 리소스 포함 설정
│       ├── serialization-config.json
│       └── proxy-config.json
├── application-native.properties    # Native 전용 설정
└── templates/
    └── layout/
        └── base.html                # 새 레이아웃 (Fragment 방식)

scripts/
├── native-build.sh                  # 빌드 스크립트
└── native-run.sh                    # 실행 스크립트
```

---

## Performance Comparison

| Metric | JVM Mode | Native Image |
|--------|----------|--------------|
| Startup Time | ~5 sec | ~0.1 sec |
| Memory (RSS) | ~500 MB | ~100 MB |
| Build Time | ~30 sec | ~5 min |
| Binary Size | - | ~150 MB |

---

## References

- [GraalVM Native Image](https://www.graalvm.org/reference-manual/native-image/)
- [Spring Boot Native Support](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html)
- [BouncyCastle + GraalVM](https://github.com/bcgit/bc-java/issues/1135)
