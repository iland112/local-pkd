# Phase 17: Real Use Case Implementation - 상세 계획

**시작 날짜**: 2025-10-29
**예상 기간**: 2-3주
**목표**: 시뮬레이션 코드를 실제 구현으로 대체

---

## 📋 목표

Phase 16에서 구축한 Event-Driven Orchestration Architecture의 **시뮬레이션 코드를 실제 구현으로 대체**합니다.

### 현재 상태 (Phase 16 완료)

✅ **완료된 항목**:
- Event-Driven Use Cases 구조 완성
- Event Handlers 구현 (동기/비동기)
- REST API Controllers 완성
- UI Integration 완성 (AJAX + SSE)
- Integration Tests (Event Handler 검증)

⚠️ **시뮬레이션 상태**:
- `ValidateCertificatesUseCase`: 시뮬레이션 검증 로직
- `UploadToLdapUseCase`: 시뮬레이션 LDAP 업로드
- Repository 조회 메서드: 일부 미구현

### Phase 17 목표

🎯 **실제 구현으로 전환**:
1. Repository 메서드 완성 (Certificate, CRL)
2. Trust Chain Validator 통합 (Bouncy Castle)
3. LDAP Upload Service 통합 (Spring LDAP)
4. Use Case 실제 로직 구현
5. E2E Tests 재구현 및 검증

---

## 🗓️ Week-by-Week Plan

### Week 1: Repository & Domain Services (예상 소요: 5일)

**Day 1-2: Certificate & CRL Repository 완성**
- Task 1.1: `CertificateRepository.findByUploadId()` 구현
- Task 1.2: `CertificateRepository.findByStatus()` 구현
- Task 1.3: `CrlRepository.findByUploadId()` 구현
- Task 1.4: `CrlRepository.findByStatus()` 구현
- Task 1.5: Repository Unit Tests 작성

**Day 3-4: Trust Chain Validator 구현**
- Task 1.6: Bouncy Castle 의존성 추가
- Task 1.7: `TrustChainValidator` Domain Service 구현
- Task 1.8: Certificate Path Building 로직
- Task 1.9: CRL 검사 로직
- Task 1.10: Trust Chain Validator Unit Tests

**Day 5: LDAP Upload Service 기반 구현**
- Task 1.11: `LdapUploadService` Interface 정의
- Task 1.12: `LdapConnectionManager` 구현
- Task 1.13: LDAP Entry 변환 로직
- Task 1.14: LDAP Service Unit Tests

### Week 2: Use Case 실제 구현 (예상 소요: 5일)

**Day 1-2: ValidateCertificatesUseCase 실제 구현**
- Task 2.1: 시뮬레이션 코드 제거
- Task 2.2: Repository 조회 로직 통합
- Task 2.3: TrustChainValidator 호출
- Task 2.4: CRL 검사 통합
- Task 2.5: Progress 업데이트 로직 정교화
- Task 2.6: Use Case Unit Tests 업데이트

**Day 3-4: UploadToLdapUseCase 실제 구현**
- Task 2.7: 시뮬레이션 코드 제거
- Task 2.8: Repository 조회 로직 통합
- Task 2.9: LdapUploadService 호출
- Task 2.10: 배치 처리 로직 구현
- Task 2.11: 에러 처리 및 재시도 로직
- Task 2.12: Use Case Unit Tests 업데이트

**Day 5: Integration Tests 업데이트**
- Task 2.13: CertificatesValidatedEventHandlerTest 업데이트
- Task 2.14: LdapUploadEventHandler Tests 재구현
- Task 2.15: 모든 Integration Tests 실행 및 검증

### Week 3: E2E Tests & Performance Optimization (예상 소요: 5일)

**Day 1-2: E2E Tests 재구현**
- Task 3.1: Embedded LDAP 설정
- Task 3.2: 전체 워크플로우 E2E Test
- Task 3.3: 대량 데이터 처리 E2E Test
- Task 3.4: 에러 시나리오 E2E Test

**Day 3-4: Performance Optimization**
- Task 3.5: 배치 처리 크기 최적화
- Task 3.6: LDAP 연결 풀 튜닝
- Task 3.7: JPA 배치 Insert 적용
- Task 3.8: Performance Tests 작성

**Day 5: Documentation & Review**
- Task 3.9: Phase 17 완료 문서 작성
- Task 3.10: API 문서 업데이트
- Task 3.11: 코드 리뷰 및 리팩토링
- Task 3.12: Phase 17 최종 검증

---

## 📂 구현할 컴포넌트 (예상)

### 1. Repository Layer (4개 메서드)

| Repository | 메서드 | 설명 | 우선순위 |
|------------|--------|------|---------|
| `CertificateRepository` | `findByUploadId(UUID)` | 업로드 ID로 인증서 목록 조회 | ⭐⭐⭐ |
| `CertificateRepository` | `findByStatus(ValidationStatus)` | 상태별 인증서 목록 조회 | ⭐⭐ |
| `CrlRepository` | `findByUploadId(UUID)` | 업로드 ID로 CRL 목록 조회 | ⭐⭐⭐ |
| `CrlRepository` | `findByStatus(ValidationStatus)` | 상태별 CRL 목록 조회 | ⭐⭐ |

### 2. Domain Services (2개)

| Service | 책임 | 주요 메서드 | 기술 스택 |
|---------|------|------------|----------|
| `TrustChainValidator` | Trust Chain 검증 | `validateCertificate()`, `buildCertificatePath()` | Bouncy Castle |
| `CrlChecker` | CRL 폐기 검사 | `checkRevocation()` | Bouncy Castle |

### 3. Infrastructure Services (2개)

| Service | 책임 | 주요 메서드 | 기술 스택 |
|---------|------|------------|----------|
| `LdapUploadService` | LDAP 업로드 | `uploadCertificatesBatch()`, `uploadCrlsBatch()` | Spring LDAP |
| `LdapConnectionManager` | LDAP 연결 관리 | `getConnection()`, `releaseConnection()` | Spring LDAP |

### 4. Tests (예상 15개)

| Test Type | Count | 파일명 예시 |
|-----------|-------|------------|
| Repository Unit Tests | 4 | `CertificateRepositoryTest`, `CrlRepositoryTest` |
| Domain Service Unit Tests | 4 | `TrustChainValidatorTest`, `CrlCheckerTest` |
| Use Case Unit Tests | 2 | `ValidateCertificatesUseCaseTest`, `UploadToLdapUseCaseTest` |
| Integration Tests | 2 | `CertificatesValidatedEventHandlerTest` (updated), `LdapUploadEventHandlerTest` |
| E2E Tests | 3 | `FileParsingToLdapUploadE2ETest`, `LargeDatasetE2ETest`, `ErrorScenarioE2ETest` |

---

## 🔧 기술 스택 추가

### 필요한 의존성

```xml
<!-- Bouncy Castle (Certificate & CRL handling) -->
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.78</version>
</dependency>
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcpkix-jdk18on</artifactId>
    <version>1.78</version>
</dependency>

<!-- Spring LDAP (이미 존재하는지 확인) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-ldap</artifactId>
</dependency>

<!-- Embedded LDAP for testing -->
<dependency>
    <groupId>com.unboundid</groupId>
    <artifactId>unboundid-ldapsdk</artifactId>
    <scope>test</scope>
</dependency>
```

---

## 📊 예상 작업량

| Category | Files | LOC | Days |
|----------|-------|-----|------|
| Repository Implementations | 2 | ~400 | 2 |
| Domain Services | 2 | ~800 | 2 |
| Infrastructure Services | 2 | ~600 | 1 |
| Use Case Updates | 2 | ~400 | 2 |
| Unit Tests | 8 | ~1,200 | 2 |
| Integration Tests | 2 | ~400 | 1 |
| E2E Tests | 3 | ~600 | 2 |
| Documentation | 2 | ~800 | 1 |
| **Total** | **23** | **~5,200** | **13-15** |

---

## 🎯 Week 1 상세 계획

### Day 1: Certificate Repository 완성

#### Task 1.1: `findByUploadId()` 구현

**목표**: 업로드 ID로 모든 인증서 조회

**구현 위치**: `CertificateRepository` (Domain Interface) + `JpaCertificateRepository` (Infrastructure)

**기존 코드**:
```java
public interface CertificateRepository {
    Certificate save(Certificate certificate);
    Optional<Certificate> findById(CertificateId id);
    // ❌ findByUploadId() 없음
}
```

**목표 코드**:
```java
public interface CertificateRepository {
    Certificate save(Certificate certificate);
    Optional<Certificate> findById(CertificateId id);

    // ✅ 신규 추가
    List<Certificate> findByUploadId(UUID uploadId);
    List<Certificate> findByStatus(ValidationStatus status);
}

// Infrastructure implementation
@Repository
public class JpaCertificateRepository implements CertificateRepository {

    @Override
    public List<Certificate> findByUploadId(UUID uploadId) {
        return springDataRepository.findByUploadId(uploadId);
    }
}

// Spring Data JPA Interface
public interface SpringDataCertificateRepository
        extends JpaRepository<Certificate, CertificateId> {

    @Query("SELECT c FROM Certificate c WHERE c.uploadId = :uploadId")
    List<Certificate> findByUploadId(@Param("uploadId") UUID uploadId);
}
```

**Unit Test**:
```java
@Test
void testFindByUploadId_ReturnsAllCertificates() {
    // Given
    UUID uploadId = UUID.randomUUID();
    Certificate cert1 = CertificateTestFixture.createValid();
    Certificate cert2 = CertificateTestFixture.createValid();

    repository.save(cert1);
    repository.save(cert2);

    // When
    List<Certificate> results = repository.findByUploadId(uploadId);

    // Then
    assertThat(results).hasSize(2);
}
```

---

#### Task 1.2: `findByStatus()` 구현

**목표**: 상태별 인증서 필터링

**구현**:
```java
@Query("SELECT c FROM Certificate c WHERE c.validationStatus = :status")
List<Certificate> findByStatus(@Param("status") ValidationStatus status);
```

---

### Day 2: CRL Repository 완성

#### Task 1.3-1.4: CRL Repository 메서드 추가

**Certificate Repository와 동일한 패턴 적용**

---

### Day 3-4: Trust Chain Validator

#### Task 1.7: TrustChainValidator 구현

**목표**: X.509 Trust Chain 검증

**구현 예시**:
```java
@Service
@RequiredArgsConstructor
public class TrustChainValidator {

    private final CertificateRepository certificateRepository;

    public ValidationResult validateCertificate(Certificate certificate) {
        try {
            // 1. Build certificate path
            List<X509Certificate> certPath = buildCertificatePath(certificate);

            // 2. Validate trust chain
            PKIXParameters params = new PKIXParameters(getTrustAnchors());
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            validator.validate(certPath, params);

            return ValidationResult.success();

        } catch (CertPathValidatorException e) {
            return ValidationResult.failure(e.getMessage());
        }
    }

    private List<X509Certificate> buildCertificatePath(Certificate certificate) {
        // Bouncy Castle를 사용한 Certificate Path 구축
        // ...
    }
}
```

---

## 🚨 주의사항 및 리스크

### 1. Bouncy Castle 버전 호환성

**리스크**: JDK 21과 Bouncy Castle 버전 충돌
**대응**: 최신 버전 (1.78) 사용, 테스트 철저히 수행

### 2. LDAP 연결 안정성

**리스크**: OpenLDAP 서버 다운타임, 연결 타임아웃
**대응**:
- Connection Pool 설정
- 재시도 로직 구현
- 에러 처리 강화

### 3. Performance 이슈

**리스크**: 대량 데이터 처리 시 성능 저하
**대응**:
- 배치 크기 조정 (기본 100개)
- JPA Batch Insert 적용
- 비동기 처리 최적화

### 4. E2E Test 환경

**리스크**: 실제 OpenLDAP 서버 필요
**대응**:
- Embedded LDAP (UnboundID) 사용
- Docker Compose로 테스트 환경 구성

---

## 📈 성공 기준

### Phase 17 완료 조건

✅ **기능 완성**:
- [ ] Certificate Repository 모든 메서드 구현 및 테스트 통과
- [ ] CRL Repository 모든 메서드 구현 및 테스트 통과
- [ ] TrustChainValidator 구현 및 테스트 통과
- [ ] LdapUploadService 구현 및 테스트 통과
- [ ] ValidateCertificatesUseCase 실제 구현 (시뮬레이션 제거)
- [ ] UploadToLdapUseCase 실제 구현 (시뮬레이션 제거)

✅ **테스트 통과**:
- [ ] Unit Tests: 100% 통과 (예상 8개)
- [ ] Integration Tests: 100% 통과 (예상 2개)
- [ ] E2E Tests: 100% 통과 (예상 3개)

✅ **Performance**:
- [ ] 1,000개 인증서 검증: < 30초
- [ ] 1,000개 인증서 LDAP 업로드: < 60초
- [ ] End-to-End 처리: < 120초

✅ **문서화**:
- [ ] Phase 17 완료 문서 작성
- [ ] API 문서 업데이트
- [ ] 아키텍처 다이어그램 업데이트

---

## 🎯 첫 작업 시작

**Task 1.1**: Certificate Repository `findByUploadId()` 구현부터 시작합니다.

**작업 순서**:
1. Domain Interface에 메서드 추가
2. Spring Data JPA Interface에 Query 정의
3. Infrastructure Implementation 작성
4. Unit Test 작성 및 실행
5. Build 검증

---

**Document Version**: 1.0
**Created**: 2025-10-29
**Status**: Ready to Start
