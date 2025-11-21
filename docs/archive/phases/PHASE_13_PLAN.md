# Phase 13: Certificate Validation Context 완성 (Trust Chain Verification)

**계획 수립일**: 2025-10-24
**예상 기간**: 3주 (Week 1-3)
**목표**: Certificate Validation Context의 핵심 비즈니스 로직 완성

---

## 🎯 Phase 13 목표

**"Trust Chain Verification"** - ICAO PKD의 핵심 기능 구현

Phase 11-12에서 구축한 Certificate 및 CRL Aggregate를 기반으로, 인증서 신뢰 체인 검증 로직을 완성합니다.

### 핵심 비즈니스 요구사항

1. **Trust Chain 검증**: CSCA → DSC → DS 3단계 체인 검증
2. **Self-Signed CA 검증**: CSCA 자체 서명 검증
3. **인증서 폐기 확인**: CRL 기반 revocation check
4. **유효성 검증**: 유효기간, 서명, 목적(Key Usage) 검증
5. **Certificate Path Building**: 신뢰 경로 자동 구축

---

## 📋 Phase 13 전체 작업 계획

### Week 1: Trust Chain Verification (Domain Services) ✅ 계획 완료

**기간**: 2025-10-24 ~ 2025-10-31 (7일)

#### Task 1: Domain Services 설계 (1일)

**산출물**:
- Trust Chain 검증 흐름도
- Domain Service 인터페이스 설계
- Validation Result Value Object 설계

**상세 작업**:
- [ ] Trust Chain Verification 시나리오 문서화
  - CSCA (Country Signing CA) 역할
  - DSC (Document Signer Certificate) 역할
  - DS (Document Signer) 역할
  - Self-Signed CA 특수 케이스
- [ ] Validation Result Value Object 설계
  - `ValidationResult` - 검증 결과 (성공/실패, 오류 메시지)
  - `TrustPath` - 신뢰 경로 (CSCA → DSC → DS)
  - `ValidationError` - 검증 실패 사유

#### Task 2: TrustChainValidator Domain Service 구현 (2일)

**파일**: `certificatevalidation/domain/service/TrustChainValidator.java`

**비즈니스 규칙**:
1. **CSCA 검증**:
   - Self-Signed 확인 (issuer == subject)
   - CA 플래그 확인 (Basic Constraints: CA=true)
   - Key Usage: Certificate Sign, CRL Sign
   - 유효기간 확인

2. **DSC 검증**:
   - CSCA가 발급자인지 확인 (issuer DN 매칭)
   - CSCA 서명 검증 (공개키로 서명 확인)
   - CA 플래그 확인 (중간 CA일 수 있음)
   - 유효기간 확인

3. **DS 검증**:
   - DSC가 발급자인지 확인
   - DSC 서명 검증
   - Key Usage: Digital Signature
   - 유효기간 확인

**메서드**:
```java
public interface TrustChainValidator {
    /**
     * 전체 신뢰 체인 검증 (CSCA → DSC → DS)
     */
    ValidationResult validateChain(
        Certificate csca,
        Certificate dsc,
        Certificate ds
    );

    /**
     * CSCA Self-Signed 검증
     */
    ValidationResult validateSelfSignedCA(Certificate csca);

    /**
     * 부모-자식 인증서 관계 검증 (서명 검증)
     */
    ValidationResult validateIssuerSignature(
        Certificate issuer,
        Certificate subject
    );

    /**
     * 신뢰 경로 자동 구축
     */
    Optional<TrustPath> buildTrustPath(
        Certificate targetCertificate,
        List<Certificate> trustedCAs
    );
}
```

**의존성**:
- BouncyCastle `CertificateFactory`
- BouncyCastle `Signature` (서명 검증)
- Certificate Aggregate
- ValidationResult Value Object

#### Task 3: CertificatePathBuilder Domain Service 구현 (2일)

**파일**: `certificatevalidation/domain/service/CertificatePathBuilder.java`

**알고리즘**:
1. Target Certificate부터 시작
2. Issuer DN으로 부모 Certificate 검색
3. 재귀적으로 CSCA (Self-Signed)까지 탐색
4. 역순으로 Trust Path 구성 (CSCA → ... → Target)

**메서드**:
```java
public interface CertificatePathBuilder {
    /**
     * Trust Path 자동 구축
     *
     * @param targetCert 대상 인증서
     * @param availableCerts 사용 가능한 모든 인증서 (CSCA, DSC 포함)
     * @return TrustPath (CSCA → ... → Target) 또는 Empty (경로 없음)
     */
    Optional<TrustPath> buildPath(
        Certificate targetCert,
        List<Certificate> availableCerts
    );

    /**
     * 여러 Trust Path 중 최적 경로 선택
     * (가장 짧은 경로, 가장 최신 인증서 사용)
     */
    Optional<TrustPath> findBestPath(
        Certificate targetCert,
        List<Certificate> availableCerts
    );
}
```

**특수 케이스 처리**:
- 순환 참조 방지 (이미 방문한 Certificate 추적)
- 최대 깊이 제한 (예: 10단계)
- 만료된 인증서 제외 (선택적)

#### Task 4: Value Objects 구현 (1일)

**파일**:
1. `ValidationResult.java` (Domain Layer)
   - `isValid: boolean`
   - `validationErrors: List<ValidationError>`
   - `trustPath: TrustPath` (성공 시)
   - `validatedAt: LocalDateTime`

2. `TrustPath.java` (Domain Layer)
   - `certificates: List<Certificate>` (순서: CSCA → ... → Target)
   - `pathLength: int`
   - `rootCA: Certificate` (CSCA)
   - `targetCertificate: Certificate`
   - 메서드: `getIntermediateCAs()`, `isValid()`, `contains(Certificate)`

3. `ValidationError.java` (Domain Layer)
   - `errorCode: String` (예: "EXPIRED", "INVALID_SIGNATURE")
   - `errorMessage: String`
   - `certificateId: CertificateId`
   - `severity: ErrorSeverity` (ERROR, WARNING)

#### Task 5: Unit Tests (1일)

**테스트 파일**:
- `TrustChainValidatorTest.java` (20+ tests)
  - CSCA self-signed 검증
  - 정상 체인 검증 (CSCA → DSC → DS)
  - 서명 불일치 케이스
  - 만료된 인증서 케이스
  - CA 플래그 없는 케이스
  - Key Usage 불일치 케이스

- `CertificatePathBuilderTest.java` (15+ tests)
  - 정상 경로 구축
  - 다중 경로 중 최적 경로 선택
  - 순환 참조 방지
  - 경로 없음 케이스
  - 깊이 제한 케이스

- `ValidationResultTest.java` (10+ tests)
- `TrustPathTest.java` (10+ tests)

**예상 테스트 개수**: 55개

---

### Week 2: Use Cases & Repository 개선 ✅ 계획 완료

**기간**: 2025-11-01 ~ 2025-11-07 (7일)

#### Task 6: Certificate Repository 개선 (2일)

**현재 상태 확인**:
- Phase 11에서 `CertificateRepository` 인터페이스 정의됨
- 기본 CRUD 메서드 있음

**추가 구현 필요**:

1. **findBySubjectDn()** (Phase 12 Week 3에서 계획됨)
   ```java
   Optional<Certificate> findBySubjectDn(String subjectDn);
   ```

2. **findByFingerprint()**
   ```java
   Optional<Certificate> findByFingerprint(String fingerprintSha256);
   ```

3. **findByIssuerDn()** (Trust Path Building용)
   ```java
   List<Certificate> findByIssuerDn(String issuerDn);
   ```

4. **findAllCAs()** (CSCA, Intermediate CA 조회)
   ```java
   List<Certificate> findAllCAs();
   ```

5. **findByType()**
   ```java
   List<Certificate> findByType(CertificateType type);
   ```

**파일**:
- `CertificateRepository.java` (Domain Layer) - 메서드 추가
- `JpaCertificateRepository.java` (Infrastructure) - 구현 추가
- `SpringDataCertificateRepository.java` (Infrastructure) - JPQL 쿼리 추가

**Index 최적화**:
```sql
-- Migration: V11__Add_Certificate_Indexes.sql
CREATE INDEX idx_cert_subject_dn ON certificate(subject_dn);
CREATE INDEX idx_cert_issuer_dn ON certificate(issuer_dn);
CREATE INDEX idx_cert_fingerprint ON certificate(fingerprint_sha256);
CREATE INDEX idx_cert_type ON certificate(certificate_type);
```

#### Task 7: Use Cases 구현 (3일)

**1. ValidateCertificateUseCase** (1일)

**파일**: `certificatevalidation/application/usecase/ValidateCertificateUseCase.java`

**책임**: 단일 인증서 유효성 검증 (Trust Chain 없이)

**검증 항목**:
- 유효기간 검증 (`ValidityPeriod.isCurrentlyValid()`)
- 서명 검증 (Self-Signed CA인 경우)
- Certificate Status 확인
- CRL 폐기 확인 (선택적)

**입출력**:
```java
@Service
@RequiredArgsConstructor
public class ValidateCertificateUseCase {
    private final CertificateRepository certificateRepository;
    private final CertificateRevocationListRepository crlRepository;
    private final BouncyCastleValidationAdapter validationAdapter;

    @Transactional(readOnly = true)
    public ValidationResult execute(ValidateCertificateCommand command) {
        // 1. Certificate 조회
        // 2. 유효기간 검증
        // 3. CRL 폐기 확인 (선택적)
        // 4. ValidationResult 반환
    }
}
```

**2. VerifyTrustChainUseCase** (1일)

**파일**: `certificatevalidation/application/usecase/VerifyTrustChainUseCase.java`

**책임**: 전체 Trust Chain 검증 (CSCA → DSC → DS)

**흐름**:
1. Target Certificate 조회
2. Trust Path 자동 구축 (`CertificatePathBuilder`)
3. 각 단계 서명 검증 (`TrustChainValidator`)
4. CRL 폐기 확인 (모든 인증서)
5. ValidationResult + TrustPath 반환

**입출력**:
```java
@Service
@RequiredArgsConstructor
public class VerifyTrustChainUseCase {
    private final CertificateRepository certificateRepository;
    private final CertificateRevocationListRepository crlRepository;
    private final TrustChainValidator trustChainValidator;
    private final CertificatePathBuilder pathBuilder;

    @Transactional(readOnly = true)
    public TrustChainVerificationResult execute(VerifyTrustChainCommand command) {
        // 1. Target Certificate 조회
        // 2. 모든 CA Certificate 조회
        // 3. Trust Path 구축
        // 4. Trust Chain 검증
        // 5. CRL 폐기 확인 (전체 경로)
        // 6. Result 반환
    }
}
```

**3. CheckCertificateRevocationUseCase** (1일)

**파일**: `certificatevalidation/application/usecase/CheckCertificateRevocationUseCase.java`

**책임**: CRL 기반 인증서 폐기 확인

**흐름**:
1. Certificate 조회
2. Issuer 기반 CRL 조회 (`CertificateRevocationListRepository`)
3. CRL 유효성 확인 (`ValidityPeriod.isCurrentlyValid()`)
4. 폐기 여부 확인 (`BouncyCastleValidationAdapter.checkRevocation()`)
5. RevocationCheckResult 반환

**입출력**:
```java
@Service
@RequiredArgsConstructor
public class CheckCertificateRevocationUseCase {
    private final CertificateRepository certificateRepository;
    private final CertificateRevocationListRepository crlRepository;
    private final BouncyCastleValidationAdapter validationAdapter;

    @Transactional(readOnly = true)
    public RevocationCheckResult execute(CheckRevocationCommand command) {
        // 1. Certificate 조회
        // 2. Issuer 기반 CRL 조회
        // 3. CRL 유효성 확인
        // 4. checkRevocation() 호출
        // 5. Result 반환
    }
}
```

#### Task 8: DTOs 구현 (1일)

**Commands**:
1. `ValidateCertificateCommand`
   - `certificateId: UUID`
   - `checkRevocation: boolean` (기본: true)

2. `VerifyTrustChainCommand`
   - `targetCertificateId: UUID`
   - `checkRevocation: boolean` (기본: true)

3. `CheckRevocationCommand`
   - `certificateId: UUID`

**Responses**:
1. `ValidateCertificateResponse`
   - `isValid: boolean`
   - `validationErrors: List<ValidationError>`
   - `certificateStatus: String`
   - `expirationDate: LocalDateTime`

2. `TrustChainVerificationResult`
   - `isValid: boolean`
   - `trustPath: TrustPath`
   - `validationResult: ValidationResult`
   - `verifiedAt: LocalDateTime`

3. `RevocationCheckResult`
   - `isRevoked: boolean`
   - `revokedAt: LocalDateTime` (폐기된 경우)
   - `crlIssuer: String`
   - `crlThisUpdate: LocalDateTime`

#### Task 9: Unit Tests (1일)

**테스트 파일**:
- `ValidateCertificateUseCaseTest.java` (15+ tests)
- `VerifyTrustChainUseCaseTest.java` (20+ tests)
- `CheckCertificateRevocationUseCaseTest.java` (15+ tests)

**예상 테스트 개수**: 50개

---

### Week 3: Event Handlers & Integration Tests ✅ 계획 완료

**기간**: 2025-11-08 ~ 2025-11-14 (7일)

#### Task 10: Domain Events 정의 (1일)

**파일**:
1. `CertificateValidatedEvent.java`
   - `certificateId: UUID`
   - `isValid: boolean`
   - `validationErrors: List<ValidationError>`
   - `validatedAt: LocalDateTime`

2. `TrustChainVerifiedEvent.java`
   - `targetCertificateId: UUID`
   - `trustPath: TrustPath`
   - `isValid: boolean`
   - `verifiedAt: LocalDateTime`

3. `CertificateRevokedEvent.java` (폐기 발견 시)
   - `certificateId: UUID`
   - `revokedAt: LocalDateTime`
   - `crlId: UUID`

#### Task 11: Event Handlers 구현 (2일)

**파일**: `certificatevalidation/application/event/CertificateValidationEventHandler.java`

**기능**:
1. **CertificateValidatedEvent 처리**:
   - 동기: 로깅, 통계 업데이트
   - 비동기: Certificate Status 업데이트, LDAP 업로드 트리거 (Phase 14 준비)

2. **TrustChainVerifiedEvent 처리**:
   - 동기: Trust Path 로깅
   - 비동기: 검증 결과 저장, 알림 발송

3. **CertificateRevokedEvent 처리**:
   - 동기: Certificate Status → REVOKED 업데이트
   - 비동기: 알림 발송, LDAP 업데이트 (Phase 14)

**패턴**:
```java
@Slf4j
@Component
@RequiredArgsConstructor
public class CertificateValidationEventHandler {

    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleCertificateValidated(CertificateValidatedEvent event) {
        // 동기 처리: 로깅, 통계
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCertificateValidatedAsync(CertificateValidatedEvent event) {
        // 비동기 처리: Status 업데이트
    }
}
```

#### Task 12: Integration Tests (3일)

**1. TrustChainVerificationIntegrationTest** (1일)

**테스트 시나리오**:
- 실제 ICAO PKD 인증서 사용 (테스트 데이터)
- CSCA → DSC → DS 전체 체인 검증
- 자체 생성한 테스트 인증서 사용 (BouncyCastle)

**테스트 케이스**:
- [ ] 정상 Trust Chain 검증 성공
- [ ] 서명 불일치 실패
- [ ] 만료된 인증서 실패
- [ ] 중간 CA 누락 실패
- [ ] CRL 폐기 인증서 실패

**2. CertificateRevocationIntegrationTest** (1일)

**테스트 시나리오**:
- 실제 CRL 데이터 사용 (Phase 12에서 추출한 CRL)
- Certificate → CRL 매칭 검증

**테스트 케이스**:
- [ ] 정상 인증서 (폐기되지 않음)
- [ ] 폐기된 인증서 (CRL에 포함)
- [ ] CRL 없음 (Issuer 매칭 실패)
- [ ] CRL 만료됨 (nextUpdate 지남)

**3. CertificatePathBuildingIntegrationTest** (1일)

**테스트 시나리오**:
- 복잡한 Certificate 트리 구조
- 다중 경로 존재 케이스

**테스트 케이스**:
- [ ] 단일 경로 구축
- [ ] 다중 경로 중 최적 경로 선택
- [ ] 순환 참조 케이스
- [ ] 경로 없음 케이스
- [ ] 최대 깊이 초과 케이스

**예상 테스트 개수**: 30개 (Integration Tests)

#### Task 13: Performance Tests (1일)

**성능 목표**:
- 단일 Certificate 검증: < 100ms
- Trust Chain 검증 (3단계): < 500ms
- CRL 폐기 확인: < 50ms
- Trust Path Building (10개 CA): < 200ms

**테스트**:
- 대량 인증서 검증 (1000개)
- 동시 검증 요청 (50 threads)
- 메모리 사용량 측정

---

## 📊 Phase 13 전체 통계 (예상)

| 항목 | 수량 |
|------|------|
| **구현 파일** | 25개 |
| **Domain Services** | 2개 (TrustChainValidator, CertificatePathBuilder) |
| **Value Objects** | 3개 (ValidationResult, TrustPath, ValidationError) |
| **Use Cases** | 3개 |
| **DTOs** | 6개 (Commands + Responses) |
| **Event Handlers** | 1개 (3개 이벤트 처리) |
| **Domain Events** | 3개 |
| **Repository 개선** | 5개 메서드 추가 |
| **Database Migration** | 1개 (Indexes) |
| **Unit Tests** | 105개 |
| **Integration Tests** | 30개 |
| **Total Tests** | 135개 |
| **예상 LOC** | ~5,000 lines |

---

## 🎯 성공 기준 (Definition of Done)

Phase 13이 완료되려면:

- [x] **Week 1 완료**: TrustChainValidator, CertificatePathBuilder 구현 + 55개 Unit Tests 통과
- [ ] **Week 2 완료**: 3개 Use Cases 구현 + Repository 개선 + 50개 Unit Tests 통과
- [ ] **Week 3 완료**: Event Handlers + 30개 Integration Tests 통과
- [ ] **전체 테스트 통과**: 135개 테스트 100% 통과
- [ ] **빌드 성공**: `./mvnw clean test` BUILD SUCCESS
- [ ] **문서 완성**: Phase 13 완료 리포트 작성
- [ ] **CLAUDE.md 업데이트**: Phase 13 섹션 추가

---

## 🔗 Phase 13과 다른 Phase의 관계

### Phase 11-12에서 가져오는 것

**Phase 11** (Certificate Aggregate):
- Certificate Aggregate Root
- Value Objects: ValidityPeriod, SubjectInfo, IssuerInfo, X509Data
- CertificateRepository Interface
- CertificateType, CertificateStatus Enums

**Phase 12** (CRL):
- CertificateRevocationList Aggregate
- CertificateRevocationListRepository
- BouncyCastleValidationAdapter (checkRevocation 메서드)

### Phase 14로 전달하는 것

**Phase 14** (LDAP Integration):
- 검증된 Certificate만 LDAP 업로드
- TrustChainVerifiedEvent → LDAP Upload 트리거
- ValidationResult를 LDAP 메타데이터로 저장

---

## 🚧 리스크 & 대응 방안

### 리스크 1: BouncyCastle 복잡도

**문제**: BouncyCastle API가 복잡하여 서명 검증 구현이 어려울 수 있음

**대응**:
- BouncyCastle 공식 문서 참조
- 기존 Phase 12의 `checkRevocation()` 구현 참고
- 필요 시 Simple한 케이스부터 구현 (Self-Signed CA)

### 리스크 2: Trust Path Building 성능

**문제**: 재귀 알고리즘이 Certificate 개수가 많으면 느려질 수 있음

**대응**:
- 최대 깊이 제한 (10단계)
- Memoization (이미 탐색한 경로 캐싱)
- BFS 알고리즘 사용 (DFS보다 빠를 수 있음)

### 리스크 3: Test 데이터 부족

**문제**: 실제 ICAO PKD 인증서가 없을 수 있음

**대응**:
- BouncyCastle로 테스트용 인증서 직접 생성
- Self-Signed CSCA, DSC, DS 생성 스크립트 작성
- 기존 LDIF 파일에서 추출한 인증서 사용

---

## 📝 다음 단계 (Phase 14 예고)

Phase 13 완료 후:

**Phase 14: LDAP Integration Context**
- 검증된 인증서를 OpenLDAP에 업로드
- CRL을 LDAP에 저장
- 배치 동기화 (LDAP ↔ Local DB)
- LDAP 검색 기능

**예상 기간**: 2-3주

---

## 📚 참고 자료

### BouncyCastle 문서
- [BouncyCastle Provider](https://www.bouncycastle.org/java.html)
- [X.509 Certificate Verification](https://www.bouncycastle.org/specifications.html)

### ICAO PKD 문서
- [ICAO PKD Specifications](https://www.icao.int/Security/FAL/PKD/Pages/default.aspx)
- [Trust Chain Verification Guidelines](https://www.icao.int/publications/Documents/9303_p12_cons_en.pdf)

### DDD 참고
- 기존 Phase 11-12 구현 패턴
- CLAUDE.md의 코딩 규칙

---

**문서 버전**: 1.0
**작성자**: kbjung
**최종 업데이트**: 2025-10-24
**상태**: ✅ 계획 수립 완료, 승인 대기
