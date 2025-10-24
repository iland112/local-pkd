# Phase 14: LDAP Integration Context 완성

**계획 수립일**: 2025-10-25
**예상 기간**: 2-3주 (Week 1-3)
**목표**: 검증된 인증서를 OpenLDAP에 업로드하고 동기화하는 기능 완성
**선행 조건**: Phase 13 Week 1 완료 (Domain Services, Controllers, Global Exception Handler)

---

## 🎯 Phase 14 목표

**"LDAP Directory Integration"** - 로컬 검증 결과를 OpenLDAP에 저장 및 동기화

Phase 13에서 구현한 **검증된 인증서 데이터**를 OpenLDAP 디렉토리에 업로드하고, 양방향 동기화를 구현합니다.

### 핵심 비즈니스 요구사항

1. **Certificate를 LDAP에 업로드**: 검증된 인증서만 선택적 업로드
2. **CRL을 LDAP에 저장**: 폐기 리스트 동기화
3. **양방향 동기화**: Local DB ↔ LDAP 자동 동기화
4. **LDAP 검색**: 인증서를 LDAP에서 검색 가능하도록 구성
5. **배치 처리**: 대량의 인증서/CRL 처리 (1000+ 항목)

---

## 📋 Phase 14 전체 작업 계획

### Week 1: LDAP Domain Services & Infrastructure (7일)

**기간**: 2025-10-25 ~ 2025-10-31 (7일)

#### Task 1: LDAP 설정 & Domain Model 설계 (1일)

**산출물**:
- LDAP 연결 설정 (spring-ldap configuration)
- DN (Distinguished Name) 구조 설계
- LDAP Entry 스키마 설계

**상세 작업**:

1. **LDAP DN 구조 설계**:
   ```
   ou=certificates,dc=ldap,dc=smartcoreinc,dc=com
   ├── ou=csca
   │   └── cn=CSCA-COUNTRY-CODE
   │       ├── cn=Subject DN
   │       ├── certificateFingerprint=...
   │       └── x509certificate=...
   ├── ou=dsc
   │   └── cn=DSC-ISSUER
   └── ou=crl
       └── cn=CRL-ISSUER
   ```

2. **LDAP Entry Attributes**:
   - `cn`: Common Name (Subject DN)
   - `objectClass`: inetOrgPerson, certificate
   - `x509certificate`: Base64 인증서
   - `certificateFingerprint`: SHA-256 fingerprint
   - `serialNumber`: 인증서 시리얼 번호
   - `issuerDN`: 발급자 DN
   - `notBefore`: 유효기간 시작
   - `notAfter`: 유효기간 종료
   - `certificateType`: CSCA, DSC, DS
   - `validationStatus`: VALID, INVALID, REVOKED
   - `crlDistributionPoints`: CRL 위치
   - `lastSyncAt`: 마지막 동기화 시간

3. **LDAP Configuration 설정**:
   ```properties
   spring.ldap.urls=ldap://192.168.100.10:389
   spring.ldap.base=dc=ldap,dc=smartcoreinc,dc=com
   spring.ldap.username=cn=admin,dc=ldap,dc=smartcoreinc,dc=com
   spring.ldap.password=${LDAP_PASSWORD}
   spring.ldap.pool.max-active=8
   spring.ldap.pool.max-idle=4
   app.ldap.certificate-base=ou=certificates
   app.ldap.crl-base=ou=crl
   ```

#### Task 2: Domain Models (LDAP Entry, DN) (1일)

**파일**:
1. `ldapintegration/domain/model/DistinguishedName.java` (Value Object)
2. `ldapintegration/domain/model/LdapCertificateEntry.java` (Domain Model)
3. `ldapintegration/domain/model/LdapCrlEntry.java` (Domain Model)
4. `ldapintegration/domain/model/LdapAttributes.java` (Value Object)
5. `ldapintegration/domain/model/LdapSyncStatus.java` (Enum)

**DistinguishedName (Value Object)**:
```java
@Embeddable
public class DistinguishedName {
    private String value;  // cn=....,ou=....,dc=...

    // 메서드
    public static DistinguishedName of(String value);
    public String getCommonName();  // cn 부분 추출
    public boolean isUnderBase(DistinguishedName base);
    public String toRfc2253Format();  // RFC 2253 형식
}
```

**LdapCertificateEntry (Domain Model)**:
```java
public class LdapCertificateEntry {
    private DistinguishedName dn;
    private UUID certificateId;
    private String x509CertificateBase64;
    private String fingerprint;
    private String serialNumber;
    private String certificateType;  // CSCA, DSC, DS
    private String validationStatus;  // VALID, INVALID, REVOKED
    private LocalDateTime notBefore;
    private LocalDateTime notAfter;
    private LocalDateTime lastSyncAt;

    // 메서드
    public static LdapCertificateEntry createFromCertificate(Certificate cert, CertificateType type);
    public boolean isExpired();
    public void markAsSynced();
}
```

#### Task 3: LDAP Domain Services (2일)

**파일**:
1. `ldapintegration/domain/service/LdapUploadService.java` (Domain Service)
2. `ldapintegration/domain/service/LdapQueryService.java` (Domain Service)
3. `ldapintegration/domain/service/LdapSyncService.java` (Domain Service)

**LdapUploadService**:
```java
public interface LdapUploadService {
    /**
     * 인증서를 LDAP에 업로드
     */
    LdapUploadResult uploadCertificate(
        Certificate certificate,
        CertificateType certificateType
    );

    /**
     * CRL을 LDAP에 업로드
     */
    LdapUploadResult uploadCrl(
        CertificateRevocationList crl
    );

    /**
     * 배치 업로드 (다량의 인증서/CRL)
     */
    BatchUploadResult uploadBatch(
        List<Certificate> certificates,
        List<CertificateRevocationList> crls
    );

    /**
     * LDAP에서 삭제
     */
    void removeEntry(DistinguishedName dn);
}
```

**LdapQueryService**:
```java
public interface LdapQueryService {
    /**
     * 인증서 검색 (Subject DN 기반)
     */
    Optional<LdapCertificateEntry> findCertificateBySubjectDn(String subjectDn);

    /**
     * 인증서 검색 (Fingerprint 기반)
     */
    Optional<LdapCertificateEntry> findCertificateByFingerprint(String fingerprint);

    /**
     * 타입별 인증서 검색 (CSCA, DSC, DS)
     */
    List<LdapCertificateEntry> findCertificatesByType(CertificateType type);

    /**
     * LDAP 사용자 검색 (일반 검색)
     */
    List<LdapEntry> search(String filter);
}
```

**LdapSyncService**:
```java
public interface LdapSyncService {
    /**
     * 로컬 DB → LDAP 동기화
     */
    SyncResult syncToLdap(List<Certificate> certificates);

    /**
     * LDAP → 로컬 DB 동기화 (역동기화)
     */
    SyncResult syncFromLdap();

    /**
     * 증분 동기화 (마지막 동기화 이후만)
     */
    SyncResult incrementalSync();

    /**
     * 동기화 충돌 해결
     */
    void resolveConflict(Certificate localCert, LdapCertificateEntry ldapEntry);
}
```

#### Task 4: LDAP Spring Adapter (1일)

**파일**: `ldapintegration/infrastructure/adapter/SpringLdapAdapter.java`

**책임**: Spring LDAP API를 Domain Services에 맞게 어댑트

**구현 항목**:
```java
@Component
public class SpringLdapAdapter implements LdapUploadService, LdapQueryService {
    private final LdapTemplate ldapTemplate;

    // DN 생성
    private DistinguishedName buildCertificateDn(Certificate cert);

    // Entry 생성
    private DirContextAdapter createDirContextAdapter(LdapCertificateEntry entry);

    // 업로드 메서드
    public void add(DistinguishedName dn, DirContextAdapter adapter);
    public void modifyAttributes(DistinguishedName dn, Attributes attrs);

    // 쿼리 메서드
    public Object findByDn(DistinguishedName dn, Class<?> targetClass);
    public List<Object> search(String filter, Class<?> targetClass);
}
```

#### Task 5: LDAP Connection Pool & Error Handling (1일)

**파일**:
1. `ldapintegration/infrastructure/config/LdapConfig.java`
2. `ldapintegration/infrastructure/exception/LdapException.java`
3. `ldapintegration/infrastructure/exception/LdapConnectionException.java`

**LdapConfig**:
```java
@Configuration
public class LdapConfig {

    @Bean
    public LdapTemplate ldapTemplate(ContextSource contextSource) {
        return new LdapTemplate(contextSource);
    }

    @Bean
    public ContextSource contextSource(
        @Value("${spring.ldap.urls}") String urls,
        @Value("${spring.ldap.base}") String baseDn,
        @Value("${spring.ldap.username}") String username,
        @Value("${spring.ldap.password}") String password) {

        return new LdapContextSource();
            // Connection Pool 설정
            // Timeout 설정 (예: 30초)
            // Retry 로직 설정
    }
}
```

**예외 처리**:
```java
public class LdapException extends DomainException {
    // LDAP 작업 중 발생하는 모든 예외
    // - Connection Error
    // - Entry Not Found
    // - Constraint Violation
    // - Insufficient Permissions
}
```

#### Task 6: Unit Tests (1일)

**테스트 파일**:
- `LdapUploadServiceTest.java` (15+ tests)
  - 인증서 업로드
  - CRL 업로드
  - 배치 업로드
  - DN 생성 검증
  - Attributes 매핑

- `LdapQueryServiceTest.java` (12+ tests)
  - 인증서 검색 (Subject DN)
  - 인증서 검색 (Fingerprint)
  - 타입별 검색

- `SpringLdapAdapterTest.java` (10+ tests)
  - Spring LDAP 어댑터 동작

**예상 테스트**: 37개

---

### Week 2: Use Cases & Integration (7일)

**기간**: 2025-11-01 ~ 2025-11-07 (7일)

#### Task 7: Use Cases 구현 (2일)

**1. UploadCertificateToLdapUseCase**:
```java
@Service
@RequiredArgsConstructor
public class UploadCertificateToLdapUseCase {
    private final CertificateRepository certificateRepository;
    private final LdapUploadService ldapUploadService;
    private final LdapSyncStatusRepository syncStatusRepository;

    @Transactional
    public LdapUploadResult execute(UploadCertificateCommand command) {
        // 1. Certificate 조회 (DB)
        // 2. LDAP 업로드
        // 3. SyncStatus 기록
        // 4. Event 발행 (CertificateUploadedToLdapEvent)
        // 5. Result 반환
    }
}
```

**2. SyncCertificatesToLdapUseCase**:
```java
@Service
@RequiredArgsConstructor
public class SyncCertificatesToLdapUseCase {
    private final CertificateRepository certificateRepository;
    private final LdapSyncService ldapSyncService;
    private final SyncStatusRepository syncStatusRepository;

    @Transactional
    public BatchSyncResult execute(SyncCertificatesCommand command) {
        // 1. 검증된 Certificate 조회 (status = VALID)
        // 2. 배치 LDAP 업로드
        // 3. SyncStatus 업데이트
        // 4. Event 발행
        // 5. Result 반환 (성공/실패 통계)
    }
}
```

**3. QueryCertificateFromLdapUseCase**:
```java
@Service
@RequiredArgsConstructor
public class QueryCertificateFromLdapUseCase {
    private final LdapQueryService ldapQueryService;

    @Transactional(readOnly = true)
    public LdapSearchResult execute(LdapSearchCommand command) {
        // 1. LDAP에서 검색
        // 2. 결과 변환
        // 3. SearchResult 반환
    }
}
```

#### Task 8: DTOs & Commands (1일)

**Commands**:
1. `UploadCertificateCommand` - 단일 인증서 업로드
2. `SyncCertificatesCommand` - 배치 동기화
3. `LdapSearchCommand` - LDAP 검색

**Responses**:
1. `LdapUploadResult` - 업로드 결과
2. `BatchSyncResult` - 배치 동기화 결과
3. `LdapSearchResult` - 검색 결과

#### Task 9: REST Controllers (1일)

**LdapIntegrationController.java**:
```
POST /api/ldap/upload-certificate          - 인증서 업로드
POST /api/ldap/sync-certificates          - 배치 동기화
POST /api/ldap/sync-crls                  - CRL 동기화
GET  /api/ldap/search-certificate         - 인증서 검색
GET  /api/ldap/sync-status                - 동기화 상태 조회
POST /api/ldap/test-connection            - LDAP 연결 테스트
```

#### Task 10: Integration Tests (2일)

**테스트 파일**:
- `LdapUploadIntegrationTest.java` (15+ tests)
  - Embedded LDAP 서버 사용 (Unboundid)
  - 실제 업로드 테스트
  - Entry 생성/수정 검증

- `LdapSyncIntegrationTest.java` (12+ tests)
  - DB → LDAP 동기화 테스트
  - 충돌 해결 테스트
  - 증분 동기화 테스트

- `LdapIntegrationControllerTest.java` (15+ tests)
  - REST API 엔드포인트 테스트

**예상 테스트**: 42개

---

### Week 3: Batch Processing & Advanced Features (7일)

**기간**: 2025-11-08 ~ 2025-11-14 (7일)

#### Task 11: Batch Processing Service (2일)

**LdapBatchProcessor.java**:
```java
@Service
@RequiredArgsConstructor
public class LdapBatchProcessor {
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 3;

    /**
     * 대량 인증서 배치 업로드 (1000+ 항목)
     */
    public BatchProcessingResult processCertificateBatch(
        List<Certificate> certificates,
        BatchProcessingConfig config
    );

    /**
     * 실패 항목 재시도
     */
    public RetryResult retryFailedEntries(
        List<BatchFailureRecord> failures,
        int retryCount
    );

    /**
     * 진행 상황 추적
     */
    public BatchProgress getProgress(String batchId);
}
```

**특징**:
- 스레드 풀 기반 병렬 처리 (설정 가능)
- 실패 항목 자동 재시도
- 진행 상황 모니터링 (이벤트 기반)
- 트랜잭션 관리 (배치별)

#### Task 12: Scheduled Sync Job (1일)

**LdapSyncScheduler.java**:
```java
@Component
@RequiredArgsConstructor
public class LdapSyncScheduler {

    /**
     * 매일 자정 LDAP 동기화
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void dailySync();

    /**
     * 1시간마다 증분 동기화
     */
    @Scheduled(fixedRate = 3600000)
    public void incrementalSync();

    /**
     * 시작 시 초기 동기화
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initialSync();
}
```

#### Task 13: Conflict Resolution (1일)

**LdapConflictResolver.java**:
```java
@Service
@RequiredArgsConstructor
public class LdapConflictResolver {

    /**
     * 로컬과 LDAP의 데이터 불일치 해결
     * - 최신 버전 기준 선택
     * - 수동 검토 큐에 추가
     * - 병합 전략 실행
     */
    public ConflictResolutionResult resolveConflict(
        Certificate localData,
        LdapCertificateEntry ldapData
    );
}
```

#### Task 14: LDAP Health Check & Monitoring (1일)

**LdapHealthIndicator.java**:
```java
@Component
public class LdapHealthIndicator extends AbstractHealthIndicator {
    /**
     * LDAP 연결 상태 확인
     * - Connection Pool 상태
     * - Directory 접근성
     * - 네트워크 지연
     */
    protected void doHealthCheck(Health.Builder builder);
}
```

**LdapMetrics.java**:
```java
@Component
public class LdapMetrics {
    // Micrometer 기반 메트릭
    - ldap.upload.count
    - ldap.upload.duration
    - ldap.sync.duration
    - ldap.query.count
    - ldap.connection.pool.active
}
```

#### Task 15: Error Handling & Retry Logic (1일)

**LdapRetryManager.java**:
```java
@Component
public class LdapRetryManager {

    /**
     * Exponential Backoff 기반 재시도
     * - 1차: 즉시 (0초)
     * - 2차: 1초 후
     * - 3차: 4초 후
     * - 4차: 16초 후
     */
    public <T> T executeWithRetry(
        Supplier<T> operation,
        int maxRetries,
        long initialDelayMs
    );
}
```

#### Task 16: Integration Tests (2일)

**테스트 파일**:
- `LdapBatchProcessingTest.java` (10+ tests)
- `LdapScheduledSyncTest.java` (8+ tests)
- `LdapConflictResolutionTest.java` (10+ tests)
- `LdapHealthCheckTest.java` (6+ tests)

**예상 테스트**: 34개

---

## 📊 Phase 14 전체 통계 (예상)

| 항목 | 수량 |
|------|------|
| **구현 파일** | 25개 |
| **Domain Models** | 4개 (DistinguishedName, LdapCertificateEntry, LdapCrlEntry, LdapAttributes) |
| **Domain Services** | 3개 (LdapUploadService, LdapQueryService, LdapSyncService) |
| **Use Cases** | 3개 (Upload, Sync, Query) |
| **Controllers** | 1개 (LdapIntegrationController) |
| **DTOs** | 6개 (Commands + Responses) |
| **Infrastructure** | 5개 (Adapter, Config, Exception, Health, Metrics) |
| **Scheduled Jobs** | 1개 (LdapSyncScheduler) |
| **Batch Processing** | 2개 (BatchProcessor, ConflictResolver) |
| **Database Migration** | 2개 (V12, V13 - LDAP Sync Status) |
| **Unit Tests** | 37개 |
| **Integration Tests** | 42개 |
| **Batch & Advanced Tests** | 34개 |
| **Total Tests** | 113개 |
| **예상 LOC** | ~6,000 lines |

---

## 🎯 성공 기준 (Definition of Done)

Phase 14가 완료되려면:

- [ ] **Week 1 완료**: LDAP 설정 + Domain Services + 37개 Unit Tests 통과
- [ ] **Week 2 완료**: Use Cases + REST Controllers + 42개 Integration Tests 통과
- [ ] **Week 3 완료**: Batch Processing + Scheduling + 34개 Advanced Tests 통과
- [ ] **전체 테스트 통과**: 113개 테스트 100% 통과
- [ ] **빌드 성공**: `./mvnw clean test` BUILD SUCCESS
- [ ] **LDAP 통합 검증**: 실제 OpenLDAP와의 통합 테스트 완료
- [ ] **문서 완성**: Phase 14 완료 리포트 작성
- [ ] **CLAUDE.md 업데이트**: Phase 14 섹션 추가

---

## 🔗 Phase 14와 다른 Phase의 관계

### Phase 13에서 가져오는 것

**검증된 데이터**:
- Certificate Aggregate (검증 상태 포함)
- CertificateRevocationList
- ValidationResult (검증 결과)
- TrustChainVerificationResult

**이벤트**:
- CertificateValidatedEvent → LDAP 업로드 트리거
- TrustChainVerifiedEvent → 신뢰 경로 저장

### Phase 15로 전달하는 것 (미래)

**LDAP 기반 기능들**:
- LDAP 인증서 검색 API
- LDAP 기반 온라인 인증서 상태 프로토콜 (OCSP)
- 다중 LDAP 디렉토리 동기화
- LDAP 기반 감사 로그

---

## 🚧 리스크 & 대응 방안

### 리스크 1: OpenLDAP 가용성

**문제**: 개발 중 LDAP 서버가 없을 수 있음

**대응**:
- Unboundid LDAP 임베디드 서버로 테스트
- Docker 컨테이너로 LDAP 서버 제공
- Mock LDAP Adapter 제공 (선택적)

### 리스크 2: DN 구조 변경

**문제**: 요구사항 변경으로 DN 구조가 바뀔 수 있음

**대응**:
- DN 구조는 설정 기반으로 유연하게
- Migration 스크립트 준비
- 기존 Entry 매핑 전략

### 리스크 3: 대량 데이터 성능

**문제**: 1000+ 인증서 동기화 시 성능 저하

**대응**:
- 배치 크기 최적화 (100개씩)
- 스레드 풀 기반 병렬 처리
- 비동기 처리 (완전 블로킹 아님)
- 성능 테스트 및 최적화

---

## 📝 다음 단계 (Phase 15 예고)

Phase 14 완료 후:

**Phase 15: LDAP 기반 고급 기능**
- 인증서 OCSP (온라인 상태 확인)
- LDAP 검색 최적화
- 다중 LDAP 디렉토리 동기화
- LDAP 감사 로그

**예상 기간**: 2-3주

---

## 📚 기술 스택 추가 (Phase 14)

### Spring LDAP
- `spring-boot-starter-data-ldap`: 2.x
- `spring-ldap-core`: 3.x
- `spring-ldap-ldif-core`: 3.x

### Embedded LDAP (테스트)
- `unboundid-ldapsdk`: 6.0+

### Monitoring
- `micrometer-core`: 기본 포함
- `spring-boot-starter-actuator`: 헬스 체크

---

**문서 버전**: 1.0
**작성자**: Claude (Anthropic)
**최종 업데이트**: 2025-10-25
**상태**: ✅ 계획 수립 완료

---

*이 계획은 Phase 13의 검증된 인증서 데이터를 OpenLDAP에 통합하는 방안을 상세히 기술합니다.*
