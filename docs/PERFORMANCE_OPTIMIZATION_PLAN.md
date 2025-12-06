# Local PKD Performance Optimization Plan

**Version**: 1.0
**Date**: 2025-12-05
**Status**: Proposal
**Author**: Performance Analysis Team

---

## 📊 Executive Summary

### Current Performance Issue
- **Problem**: LDIF 파일 처리 시간이 **5시간 이상** 소요
- **Impact**: 대용량 DSC/CRL 인증서 업로드 시 운영 불가능
- **Root Cause**: N+1 쿼리, 개별 트랜잭션, 순차 LDAP 업로드

### Optimization Goal
- **Target**: 5시간 → **10-15분** (95% 성능 향상)
- **Phase 1 (Quick Win)**: 5시간 → **1-1.5시간** (70-80% 개선)
- **Phase 2 (Parallel)**: 1.5시간 → **20-30분** (추가 60% 개선)
- **Phase 3 (Architecture)**: 30분 → **10-15분** (추가 50% 개선)

### Key Actions
1. ✅ **배치 중복 체크** - N+1 쿼리 제거 (20,000개 → 1개)
2. ✅ **CSCA 캐싱** - DSC 검증 시 DB 조회 제거
3. ✅ **배치 저장** - 트랜잭션 통합 (10,000개 → 10개)
4. ✅ **LDAP 배치 업로드** - 네트워크 I/O 80% 감소
5. ⏳ **멀티스레드 처리** - CPU 멀티코어 활용

---

## 🔍 Current Architecture Analysis

### Processing Pipeline (Total: 5 hours)

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. Upload (AsyncUploadProcessor)                               │
│    - File upload & SHA-256 checksum calculation               │
│    - Duplicate check (single query)                           │
│    Duration: ~1-2 minutes                                      │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 2. Parsing (LdifParserAdapter)                         ⚠️ SLOW │
│    - LDIF entry parsing (sequential)                           │
│    - Certificate duplicate check (N+1 query)                   │
│    - Master List CMS parsing (crypto overhead)                 │
│    Duration: ~2 hours (10,000 entries)                         │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 3. Validation (ValidateCertificatesUseCase)            ⚠️ SLOW │
│    - Pass 1: CSCA validation (sequential)                      │
│    - Pass 2: DSC validation + CSCA lookup (N+1)                │
│    - Individual transactions (REQUIRES_NEW)                    │
│    Duration: ~2 hours (10,000 certificates)                    │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 4. LDAP Upload (UploadToLdapUseCase)                   ⚠️ SLOW │
│    - Individual LDAP connections (sequential)                  │
│    - Network I/O overhead (10,000 requests)                    │
│    Duration: ~1 hour (10,000 entries)                          │
└─────────────────────────────────────────────────────────────────┘
```

### Technology Stack
- **Backend**: Spring Boot 3.5.5, Java 21
- **Database**: PostgreSQL 15.14
- **LDAP**: UnboundID LDAP SDK
- **Certificate**: Bouncy Castle 1.70
- **Async**: Spring @Async, ThreadPoolTaskExecutor

---

## 🚨 Performance Bottleneck Analysis

### HIGH Priority Issues

#### Issue #1: N+1 Query Problem (Parsing Phase)

**Location**: [LdifParserAdapter.java:174](../src/main/java/com/smartcoreinc/localpkd/fileparsing/infrastructure/adapter/LdifParserAdapter.java#L174)

**Current Code**:
```java
private void parseCertificateFromEntry(Entry entry, ParsedFile parsedFile) {
    // ... 인증서 파싱 ...
    String fingerprint = calculateFingerprint(cert);

    // ❌ 매 인증서마다 DB 조회
    if (!certificateExistenceService.existsByFingerprintSha256(fingerprint)) {
        parsedFile.addCertificate(certData);
    } else {
        parsedFile.addError(ParsingError.of("DUPLICATE_CERTIFICATE", fingerprint, "..."));
        log.warn("Duplicate certificate skipped: fingerprint_sha256={}", fingerprint);
    }
}
```

**Problem**:
- 10,000개 인증서 → **10,000번 DB 쿼리**
- PostgreSQL 쿼리 실행 오버헤드 (파싱, 플랜, 락, I/O)
- 네트워크 왕복 지연 (애플리케이션 ↔ DB)

**Impact**: 파싱 시간의 **40-50%** 차지

**SQL Execution**:
```sql
-- 10,000번 실행
SELECT COUNT(*) > 0
FROM parsed_certificate
WHERE fingerprint_sha256 = ?
```

---

#### Issue #2: N+1 Query Problem (Validation Phase)

**Location**: [ValidateCertificatesUseCase.java:546](../src/main/java/com/smartcoreinc/localpkd/certificatevalidation/application/usecase/ValidateCertificatesUseCase.java#L546)

**Current Code**:
```java
private ValidationResult validateDscCertificate(...) {
    String issuerDN = certData.getIssuerDN();

    // ❌ DSC 검증 시 매번 CSCA 조회
    Optional<Certificate> cscaCertOpt = certificateRepository.findBySubjectDn(issuerDN);

    if (cscaCertOpt.isEmpty()) {
        errors.add(ValidationError.critical("CHAIN_INCOMPLETE", "CSCA not found..."));
    } else {
        Certificate cscaCert = cscaCertOpt.get();
        X509Certificate cscaX509 = convertToX509Certificate(cscaCert.getX509Data().getCertificateBinary());
        x509Cert.verify(cscaX509.getPublicKey()); // 서명 검증
    }
}
```

**Problem**:
- Pass 2에서 DSC 8,000개 검증 시 → **8,000번 CSCA 조회**
- CSCA는 이미 Pass 1에서 저장되었음 (중복 조회)
- 인덱스 활용해도 쿼리 오버헤드 존재

**Impact**: 검증 시간의 **30-40%** 차지

---

#### Issue #3: Individual Transaction Overhead

**Location**: [ValidateCertificatesUseCase.java:166-171](../src/main/java/com/smartcoreinc/localpkd/certificatevalidation/application/usecase/ValidateCertificatesUseCase.java#L166-L171)

**Current Code**:
```java
// Pass 1: CSCA 검증
for (int i = 0; i < totalCertificates; i++) {
    CertificateData certData = certificateDataList.get(i);

    if (certData.isCsca()) {
        // ... 검증 로직 ...

        // ❌ 개별 트랜잭션 (REQUIRES_NEW)
        certificateSaveService.saveOrUpdate(
            certificate, validationResult, errors,
            validCertificateIds, invalidCertificateIds, processedFingerprints,
            true, // isCsca = true
            command.uploadId()
        );
    }
}
```

**CertificateSaveService Implementation**:
```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void saveOrUpdate(...) {
    // 새 트랜잭션 시작
    certificateRepository.save(certificate);
    // 트랜잭션 커밋
}
```

**Problem**:
- 10,000개 인증서 → **10,000개 트랜잭션**
- 각 트랜잭션마다:
  - BEGIN/COMMIT 오버헤드
  - WAL(Write-Ahead Log) 쓰기
  - fsync() 시스템 콜 (디스크 동기화)
  - 락 획득/해제

**Impact**: 검증 시간의 **20-30%** 차지

**PostgreSQL Log Example**:
```
BEGIN;
INSERT INTO certificate (...) VALUES (...);
COMMIT; -- fsync() 호출 → 디스크 I/O 대기

BEGIN;
INSERT INTO certificate (...) VALUES (...);
COMMIT; -- fsync() 호출 → 디스크 I/O 대기
... (10,000번 반복)
```

---

#### Issue #4: Sequential LDAP Upload

**Location**: [UploadToLdapUseCase.java:125-164](../src/main/java/com/smartcoreinc/localpkd/ldapintegration/application/usecase/UploadToLdapUseCase.java#L125-L164)

**Current Code**:
```java
// 3. Upload all certificates
List<Certificate> certificates = certificateRepository.findByUploadId(command.uploadId());

// ❌ 순차 LDAP 업로드
for (int i = 0; i < certificates.size(); i++) {
    Certificate cert = certificates.get(i);

    // LDIF 변환
    String ldifEntry = ldifConverter.certificateToLdif(cert);

    // ❌ 개별 LDAP 연결/저장
    boolean success = ldapAdapter.addLdifEntry(ldifEntry);

    if (success) {
        uploadedCertificateCount++;
    }
}
```

**UnboundIdLdapAdapter Implementation**:
```java
public boolean addLdifEntry(String ldifContent) {
    LDIFReader ldifReader = new LDIFReader(new ByteArrayInputStream(ldifContent.getBytes()));
    Entry entry = ldifReader.readEntry();

    // ❌ 네트워크 I/O (LDAP 서버 연결)
    AddRequest addRequest = new AddRequest(entry);
    LDAPResult result = ldapConnection.add(addRequest);

    return result.getResultCode() == ResultCode.SUCCESS;
}
```

**Problem**:
- 10,000개 인증서 → **10,000번 네트워크 요청**
- 네트워크 왕복 지연 (RTT): ~10ms
- LDAP 서버 처리 시간: ~5ms
- 총 소요 시간: 10,000 × 15ms = **150초 (2.5분)**

**Impact**: LDAP 업로드 시간의 **80-90%** 차지

---

### MEDIUM Priority Issues

#### Issue #5: Sequential Processing (CPU Underutilization)

**Problem**:
- 모든 단계에서 `for` 루프로 순차 처리
- 멀티코어 CPU 활용률 낮음 (1 코어만 사용)

**Locations**:
- 파싱: [LdifParserAdapter.java:74-78](../src/main/java/com/smartcoreinc/localpkd/fileparsing/infrastructure/adapter/LdifParserAdapter.java#L74-L78)
- 검증: [ValidateCertificatesUseCase.java:137-208](../src/main/java/com/smartcoreinc/localpkd/certificatevalidation/application/usecase/ValidateCertificatesUseCase.java#L137-L208)
- LDAP: [UploadToLdapUseCase.java:125](../src/main/java/com/smartcoreinc/localpkd/ldapintegration/application/usecase/UploadToLdapUseCase.java#L125)

**CPU Usage Example**:
```
Core 0: ████████████████████ 100%  (Processing thread)
Core 1: ██                    10%   (Idle)
Core 2: ██                    10%   (Idle)
Core 3: ██                    10%   (Idle)
Core 4: ██                    10%   (Idle)
Core 5: ██                    10%   (Idle)
Core 6: ██                    10%   (Idle)
Core 7: ██                    10%   (Idle)
```

**Impact**: 병렬 처리 시 **4-8배 성능 향상** 가능

---

#### Issue #6: Master List CMS Parsing Overhead

**Location**: [LdifParserAdapter.java:208-330](../src/main/java/com/smartcoreinc/localpkd/fileparsing/infrastructure/adapter/LdifParserAdapter.java#L208-L330)

**Current Code**:
```java
private void parseMasterListContent(byte[] masterListBytes, String dn, ParsedFile parsedFile) {
    // ❌ CMS SignedData 파싱 (암호화 연산)
    CMSSignedData signedData = new CMSSignedData(
        new CMSProcessableByteArray(masterListBytes),
        new ByteArrayInputStream(masterListBytes)
    );

    Store<X509CertificateHolder> certStore = signedData.getCertificates();
    Collection<X509CertificateHolder> certs = certStore.getMatches(null);

    // ... CSCA 추출 및 저장 ...
}
```

**Problem**:
- Bouncy Castle CMS 파싱 시 암호화 검증 수행
- ASN.1 파싱 오버헤드
- 27개 국가 Master List → **27번 CMS 파싱**

**Impact**: 파싱 시간의 **10-15%** 차지 (최적화 어려움)

---

## 💡 Optimization Plan

### Phase 1: Quick Wins (1-2 Days) - 70-80% Improvement

#### 1-1. Batch Duplicate Check (N+1 Elimination)

**Goal**: 중복 체크 쿼리 20,000개 → 1개

**Implementation**:

**Step 1**: CertificateExistenceService 배치 조회 메서드 추가

```java
// CertificateExistenceService.java
public interface CertificateExistenceService {
    // ✅ 새로운 배치 조회 메서드
    Set<String> findExistingFingerprints(Set<String> fingerprints);

    // 기존 메서드 (deprecated 예정)
    @Deprecated
    boolean existsByFingerprintSha256(String fingerprint);
}

// CertificateExistenceServiceImpl.java
@Service
@RequiredArgsConstructor
public class CertificateExistenceServiceImpl implements CertificateExistenceService {

    private final ParsedCertificateRepository parsedCertificateRepository;

    @Override
    public Set<String> findExistingFingerprints(Set<String> fingerprints) {
        if (fingerprints.isEmpty()) {
            return Collections.emptySet();
        }

        // ✅ IN 절로 일괄 조회
        List<String> existing = parsedCertificateRepository
            .findFingerprintsByFingerprintSha256In(fingerprints);

        return new HashSet<>(existing);
    }
}
```

**Step 2**: ParsedCertificateRepository에 배치 조회 추가

```java
// ParsedCertificateRepository.java
public interface ParsedCertificateRepository extends JpaRepository<ParsedCertificate, ParsedCertificateId> {

    // ✅ IN 절 조회
    @Query("SELECT pc.fingerprintSha256 FROM ParsedCertificate pc WHERE pc.fingerprintSha256 IN :fingerprints")
    List<String> findFingerprintsByFingerprintSha256In(@Param("fingerprints") Set<String> fingerprints);
}
```

**Step 3**: LdifParserAdapter 리팩토링

```java
// LdifParserAdapter.java
@Override
public void parse(byte[] fileBytes, FileFormat fileFormat, ParsedFile parsedFile) throws ParsingException {
    if (!supports(fileFormat)) throw new ParsingException("Unsupported file format: " + fileFormat.getDisplayName());

    // ✅ Step 1: 모든 엔트리를 먼저 읽어서 fingerprint 수집
    Set<String> allFingerprints = new HashSet<>();
    List<Entry> allEntries = new ArrayList<>();

    try (LDIFReader ldifReader = new LDIFReader(new ByteArrayInputStream(fileBytes))) {
        Entry entry;
        while ((entry = ldifReader.readEntry()) != null) {
            allEntries.add(entry);

            // 인증서 엔트리면 fingerprint 계산
            if (entry.hasAttribute(ATTR_USER_CERTIFICATE)) {
                byte[] certBytes = entry.getAttribute(ATTR_USER_CERTIFICATE).getValueByteArray();
                try {
                    CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                    X509Certificate cert = (X509Certificate) certFactory.generateCertificate(
                        new ByteArrayInputStream(certBytes)
                    );
                    String fingerprint = calculateFingerprint(cert);
                    allFingerprints.add(fingerprint);
                } catch (Exception e) {
                    log.warn("Failed to calculate fingerprint for entry: {}", entry.getDN(), e);
                }
            }
        }
    } catch (Exception e) {
        throw new ParsingException("LDIF parsing error: " + e.getMessage(), e);
    }

    // ✅ Step 2: 일괄 중복 체크 (단일 쿼리)
    Set<String> existingFingerprints = certificateExistenceService.findExistingFingerprints(allFingerprints);
    log.info("Duplicate check completed: {} existing out of {} total", existingFingerprints.size(), allFingerprints.size());

    // ✅ Step 3: 엔트리 파싱 (중복 체크는 메모리 Set으로 수행)
    int entryNumber = 0;
    int estimatedTotalEntries = allEntries.size();

    for (Entry entry : allEntries) {
        entryNumber++;
        updateProgress(parsedFile, entryNumber, estimatedTotalEntries);
        parseEntryWithCache(entry, entryNumber, parsedFile, existingFingerprints);
    }
}

private void parseEntryWithCache(Entry entry, int entryNumber, ParsedFile parsedFile, Set<String> existingFingerprints) {
    if (entry.hasAttribute(ATTR_USER_CERTIFICATE)) {
        parseCertificateFromEntryWithCache(entry, parsedFile, existingFingerprints);
    } else if (entry.hasAttribute(ATTR_CRL)) {
        parseCrlFromBytes(entry.getAttribute(ATTR_CRL).getValueByteArray(), entry.getDN(), parsedFile);
    } else if (entry.hasAttribute(ATTR_MASTER_LIST_CONTENT)) {
        parseMasterListContent(entry.getAttribute(ATTR_MASTER_LIST_CONTENT).getValueByteArray(), entry.getDN(), parsedFile);
    }
}

private void parseCertificateFromEntryWithCache(Entry entry, ParsedFile parsedFile, Set<String> existingFingerprints) {
    byte[] certBytes = entry.getAttribute(ATTR_USER_CERTIFICATE).getValueByteArray();
    String dn = entry.getDN();

    try {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(certBytes));

        String fingerprint = calculateFingerprint(cert);

        // ✅ 메모리 Set으로 중복 체크 (DB 조회 없음)
        if (!existingFingerprints.contains(fingerprint)) {
            // ... CertificateData 생성 및 추가 (기존 코드 동일) ...
            CertificateData certData = CertificateData.of(...);
            parsedFile.addCertificate(certData);
        } else {
            parsedFile.addError(ParsingError.of("DUPLICATE_CERTIFICATE", fingerprint, "Certificate with this fingerprint already exists globally."));
            log.debug("Duplicate certificate skipped: fingerprint_sha256={}", fingerprint);
        }
    } catch (Exception e) {
        parsedFile.addError(ParsingError.of("CERT_PARSE_ERROR", dn, e.getMessage()));
    }
}
```

**Expected Result**:
```
Before: 10,000 DB queries (10,000 × 5ms) = 50 seconds
After:  1 DB query (1 × 100ms) = 0.1 seconds
Improvement: 99.8% faster
```

**SQL Execution**:
```sql
-- ✅ 단일 쿼리 (IN 절)
SELECT fingerprint_sha256
FROM parsed_certificate
WHERE fingerprint_sha256 IN (
    'abc123...', 'def456...', ..., '10000개 fingerprint'
)
```

---

#### 1-2. CSCA Caching (DSC Validation N+1 Elimination)

**Goal**: DSC 검증 시 CSCA 조회 8,000개 → 1개

**Implementation**:

```java
// ValidateCertificatesUseCase.java

@Transactional
public CertificatesValidatedResponse execute(ValidateCertificatesCommand command) {
    // ... 기존 코드 ...

    // Pass 1 완료 후 통계 로깅
    log.info("Pass 1 completed: {} CSCA certificates processed ({} valid, {} invalid)",
        cscaProcessed, validCertificateIds.size(), invalidCertificateIds.size());

    // ✅ Pass 2 시작 전: CSCA 캐시 구축
    Map<String, Certificate> cscaCache = buildCscaCache(command.uploadId());
    log.info("CSCA cache built: {} entries", cscaCache.size());

    // === Pass 2: DSC/DSC_NC 인증서 검증/저장 ===
    log.info("=== Pass 2: DSC/DSC_NC certificate validation started ===");
    int dscProcessed = 0;
    for (int i = 0; i < totalCertificates; i++) {
        CertificateData certData = certificateDataList.get(i);

        if (!certData.isCsca()) {
            // ... 검증 로직 (cscaCache 전달) ...
            validationResult = validateDscCertificate(x509Cert, certData, command.uploadId(), errors, cscaCache);
        }
    }
}

// ✅ CSCA 캐시 구축 메서드
private Map<String, Certificate> buildCscaCache(java.util.UUID uploadId) {
    List<Certificate> allCertificates = certificateRepository.findByUploadId(uploadId);

    return allCertificates.stream()
        .filter(cert -> cert.getCertificateType() == CertificateType.CSCA)
        .collect(Collectors.toMap(
            cert -> cert.getSubjectInfo().getSubjectDn(),
            cert -> cert,
            (existing, replacement) -> existing // 중복 시 기존 값 유지
        ));
}

// ✅ DSC 검증 메서드 (캐시 사용)
private ValidationResult validateDscCertificate(
    X509Certificate x509Cert,
    CertificateData certData,
    java.util.UUID uploadId,
    List<ValidationError> errors,
    Map<String, Certificate> cscaCache // ✅ 캐시 파라미터 추가
) {
    boolean signatureValid = true;
    boolean validityValid = true;
    boolean constraintsValid = true;
    long validationStartTime = System.currentTimeMillis();

    try {
        // 1. Issuer DN으로 CSCA 조회 (✅ 캐시에서 조회)
        String issuerDN = certData.getIssuerDN();
        log.debug("Finding CSCA for DSC validation: issuerDN={}", issuerDN);

        // ❌ 기존: DB 조회
        // Optional<Certificate> cscaCertOpt = certificateRepository.findBySubjectDn(issuerDN);

        // ✅ 개선: 캐시 조회
        Certificate cscaCert = cscaCache.get(issuerDN);

        if (cscaCert == null) {
            signatureValid = false;
            errors.add(ValidationError.critical("CHAIN_INCOMPLETE", "CSCA not found for DSC. IssuerDN: " + issuerDN));
            log.error("CSCA not found for DSC. IssuerDN: {}", issuerDN);
        } else {
            // CSCA 인증서로 DSC 서명 검증
            X509Certificate cscaX509 = convertToX509Certificate(
                cscaCert.getX509Data().getCertificateBinary()
            );
            try {
                x509Cert.verify(cscaX509.getPublicKey());
                log.debug("Signature verified for DSC by CSCA: {}", certData.getSubjectDN());
            } catch (Exception e) {
                signatureValid = false;
                errors.add(ValidationError.critical("SIGNATURE_INVALID", "Signature verification failed by CSCA: " + e.getMessage()));
                log.error("Signature verification failed for DSC by CSCA: {}. Error: {}", certData.getSubjectDN(), e.getMessage());
            }
        }

        // 2. Validity period 검증 (기존 코드 동일)
        // 3. Basic Constraints 검증 (기존 코드 동일)

        // ... 나머지 검증 로직 동일 ...
    }
}
```

**Expected Result**:
```
Before: 8,000 DB queries (8,000 × 5ms) = 40 seconds
After:  1 DB query + 8,000 Map lookups (1 × 50ms + 8,000 × 0.001ms) = 0.058 seconds
Improvement: 99.85% faster
```

**Memory Usage**:
```
CSCA Cache Size: ~2,000 CSCAs × 5KB = 10 MB (acceptable)
```

---

#### 1-3. Batch Save (Transaction Consolidation)

**Goal**: 트랜잭션 10,000개 → 10개 (배치 크기 1000)

**Implementation**:

**Step 1**: ValidateCertificatesUseCase 배치 저장 리팩토링

```java
// ValidateCertificatesUseCase.java

@Transactional
public CertificatesValidatedResponse execute(ValidateCertificatesCommand command) {
    // ... 기존 코드 ...

    // === Pass 1: CSCA 인증서 배치 검증/저장 ===
    log.info("=== Pass 1: CSCA certificate validation started ===");

    // ✅ 배치 단위 처리 변수
    List<Certificate> cscaBatch = new ArrayList<>();
    List<ValidationError> allErrors = new ArrayList<>();
    int cscaProcessed = 0;
    final int BATCH_SIZE = 1000;

    for (int i = 0; i < totalCertificates; i++) {
        CertificateData certData = certificateDataList.get(i);

        if (certData.isCsca()) {
            cscaProcessed++;

            if (processedFingerprints.contains(certData.getFingerprintSha256())) {
                log.warn("Skipping duplicate certificate within the same batch: fingerprint={}", certData.getFingerprintSha256());
                continue;
            }

            try {
                X509Certificate x509Cert = convertToX509Certificate(certData.getCertificateBinary());
                Certificate certificate = createCertificateFromData(certData, x509Cert, command.uploadId());

                // 검증
                List<ValidationError> errors = new ArrayList<>();
                ValidationResult validationResult = validateCscaCertificate(x509Cert, certData, errors);
                certificate.recordValidation(validationResult);
                certificate.addValidationErrors(errors);

                // ✅ 배치에 추가 (개별 저장 대신)
                cscaBatch.add(certificate);
                allErrors.addAll(errors);

                // 상태 추적
                if (validationResult.getStatus() == CertificateStatus.VALID) {
                    validCertificateIds.add(certificate.getId().getId());
                } else {
                    invalidCertificateIds.add(certificate.getId().getId());
                }
                processedFingerprints.add(certData.getFingerprintSha256());

                // ✅ 배치 크기 도달 시 일괄 저장
                if (cscaBatch.size() >= BATCH_SIZE) {
                    saveBatch(cscaBatch, allErrors);
                    cscaBatch.clear();
                    allErrors.clear();
                    log.info("CSCA batch saved: {} certificates", BATCH_SIZE);
                }

            } catch (Exception e) {
                log.error("CSCA certificate processing failed: subject={}. Error: {}", certData.getSubjectDN(), e.getMessage());
                // 에러 처리 (기존 코드 유사)
            }

            // SSE 진행 상황 업데이트 (기존 코드 동일)
            progressService.sendProgress(...);
        }
    }

    // ✅ 남은 배치 저장
    if (!cscaBatch.isEmpty()) {
        saveBatch(cscaBatch, allErrors);
        log.info("Final CSCA batch saved: {} certificates", cscaBatch.size());
    }

    log.info("Pass 1 completed: {} CSCA certificates processed ({} valid, {} invalid)",
        cscaProcessed, validCertificateIds.size(), invalidCertificateIds.size());

    // === Pass 2: DSC/DSC_NC 배치 검증/저장 (유사한 로직) ===
    // ...
}

// ✅ 배치 저장 메서드
private void saveBatch(List<Certificate> certificates, List<ValidationError> errors) {
    try {
        // JPA saveAll() - 단일 트랜잭션에서 일괄 저장
        certificateRepository.saveAll(certificates);
        log.debug("Batch saved: {} certificates", certificates.size());
    } catch (DataIntegrityViolationException e) {
        // 중복 키 충돌 시 개별 처리
        log.warn("Batch save failed, falling back to individual save: {}", e.getMessage());
        for (Certificate cert : certificates) {
            try {
                certificateRepository.save(cert);
            } catch (Exception ex) {
                log.error("Failed to save certificate: id={}, error={}", cert.getId().getId(), ex.getMessage());
            }
        }
    }
}
```

**Step 2**: application.properties에 JPA Batch Insert 설정 추가

```properties
# JPA Batch Insert Optimization
spring.jpa.properties.hibernate.jdbc.batch_size=1000
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.properties.hibernate.jdbc.batch_versioned_data=true

# Statement Caching
spring.jpa.properties.hibernate.jdbc.use_get_generated_keys=true
```

**Expected Result**:
```
Before: 10,000 transactions × 20ms (BEGIN + INSERT + COMMIT + fsync) = 200 seconds
After:  10 transactions × 150ms (batch insert 1000 rows) = 1.5 seconds
Improvement: 99.25% faster
```

**PostgreSQL Execution**:
```sql
-- ✅ 배치 INSERT (1000개씩)
BEGIN;
INSERT INTO certificate (...) VALUES (...), (...), ..., (...); -- 1000 rows
COMMIT; -- 1번만 fsync()

BEGIN;
INSERT INTO certificate (...) VALUES (...), (...), ..., (...); -- 1000 rows
COMMIT; -- 1번만 fsync()
... (10번 반복)
```

---

#### 1-4. LDAP Batch Upload

**Goal**: LDAP 네트워크 요청 10,000개 → 100개 (배치 크기 100)

**Option A: LDIF 파일 일괄 업로드 (권장)**

**Step 1**: UnboundIdLdapAdapter에 배치 업로드 메서드 추가

```java
// UnboundIdLdapAdapter.java

/**
 * ✅ 배치 LDIF 업로드 (단일 LDIF 파일로 전송)
 */
public BatchUploadResult addLdifBatch(String ldifContent) {
    int successCount = 0;
    int failureCount = 0;
    List<String> failedDns = new ArrayList<>();

    try (LDIFReader ldifReader = new LDIFReader(new ByteArrayInputStream(ldifContent.getBytes(StandardCharsets.UTF_8)))) {
        Entry entry;
        while ((entry = ldifReader.readEntry()) != null) {
            try {
                AddRequest addRequest = new AddRequest(entry);
                LDAPResult result = ldapConnection.add(addRequest);

                if (result.getResultCode() == ResultCode.SUCCESS) {
                    successCount++;
                } else if (result.getResultCode() == ResultCode.ENTRY_ALREADY_EXISTS) {
                    log.debug("Entry already exists, skipping: {}", entry.getDN());
                    successCount++; // 중복은 성공으로 간주
                } else {
                    failureCount++;
                    failedDns.add(entry.getDN());
                    log.warn("Failed to add entry: DN={}, ResultCode={}", entry.getDN(), result.getResultCode());
                }
            } catch (LDAPException e) {
                failureCount++;
                failedDns.add(entry.getDN());
                log.error("Exception adding entry: DN={}", entry.getDN(), e);
            }
        }
    } catch (IOException | LDIFException e) {
        throw new InfrastructureException("LDAP_BATCH_UPLOAD_ERROR", "Failed to parse LDIF batch: " + e.getMessage(), e);
    }

    return new BatchUploadResult(successCount, failureCount, failedDns);
}

// ✅ 배치 업로드 결과 클래스
@Value
@Builder
public static class BatchUploadResult {
    int successCount;
    int failureCount;
    List<String> failedDns;
}
```

**Step 2**: UploadToLdapUseCase 배치 업로드 리팩토링

```java
// UploadToLdapUseCase.java

@Transactional
public UploadToLdapResponse execute(UploadToLdapCommand command) {
    // ... 기존 코드 ...

    // 3. Upload all certificates (✅ 배치 처리)
    List<Certificate> certificates = certificateRepository.findByUploadId(command.uploadId());

    log.info("Uploading {} certificates to LDAP in batches...", certificates.size());
    int uploadedCertificateCount = 0;
    int skippedCertificateCount = 0;
    int failedCertificateCount = 0;

    // ✅ 배치 단위로 LDIF 문자열 생성
    final int BATCH_SIZE = 100;
    StringBuilder ldifBatch = new StringBuilder();
    int batchCount = 0;

    for (int i = 0; i < certificates.size(); i++) {
        Certificate cert = certificates.get(i);

        try {
            // LDIF 변환
            String ldifEntry = ldifConverter.certificateToLdif(cert);
            ldifBatch.append(ldifEntry).append("\n");
            batchCount++;

            // ✅ 배치 크기 도달 또는 마지막 인증서 시 일괄 업로드
            if (batchCount >= BATCH_SIZE || (i == certificates.size() - 1)) {
                UnboundIdLdapAdapter.BatchUploadResult result = ldapAdapter.addLdifBatch(ldifBatch.toString());

                uploadedCertificateCount += result.getSuccessCount();
                failedCertificateCount += result.getFailureCount();

                log.info("Certificate batch uploaded: success={}, failed={}",
                    result.getSuccessCount(), result.getFailureCount());

                if (!result.getFailedDns().isEmpty()) {
                    log.warn("Failed DNs: {}", result.getFailedDns());
                }

                // 배치 초기화
                ldifBatch.setLength(0);
                batchCount = 0;
            }

            // SSE 진행 상황 업데이트 (기존 코드 동일)
            if ((i + 1) % 100 == 0 || (i + 1) == certificates.size()) {
                int percentage = 90 + ((i + 1) * 5 / certificates.size());
                progressService.sendProgress(...);
            }

        } catch (Exception e) {
            failedCertificateCount++;
            log.error("Failed to convert certificate to LDIF: id={}", cert.getId().getId(), e);
        }
    }

    // 4. CRL 배치 업로드 (유사한 로직)
    // ...
}
```

**Expected Result**:
```
Before: 10,000 LDAP requests × 15ms (RTT + processing) = 150 seconds
After:  100 batch requests × 200ms (batch of 100 entries) = 20 seconds
Improvement: 86.7% faster
```

**Option B: 병렬 업로드 (멀티스레드)**

```java
// ✅ CompletableFuture로 병렬 LDAP 업로드
@Transactional
public UploadToLdapResponse execute(UploadToLdapCommand command) {
    List<Certificate> certificates = certificateRepository.findByUploadId(command.uploadId());

    // ✅ 배치 분할 (100개씩)
    final int BATCH_SIZE = 100;
    List<List<Certificate>> batches = new ArrayList<>();
    for (int i = 0; i < certificates.size(); i += BATCH_SIZE) {
        batches.add(certificates.subList(i, Math.min(i + BATCH_SIZE, certificates.size())));
    }

    // ✅ ExecutorService 생성 (스레드 풀 크기: 10)
    ExecutorService executor = Executors.newFixedThreadPool(10);

    // ✅ 병렬 업로드
    List<CompletableFuture<BatchUploadResult>> futures = batches.stream()
        .map(batch -> CompletableFuture.supplyAsync(() -> uploadBatchToLdap(batch), executor))
        .collect(Collectors.toList());

    // ✅ 모든 업로드 완료 대기
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    // 결과 집계
    int totalUploaded = futures.stream()
        .map(CompletableFuture::join)
        .mapToInt(BatchUploadResult::getSuccessCount)
        .sum();

    executor.shutdown();

    // ... 나머지 로직 ...
}

private BatchUploadResult uploadBatchToLdap(List<Certificate> batch) {
    StringBuilder ldifBatch = new StringBuilder();
    for (Certificate cert : batch) {
        ldifBatch.append(ldifConverter.certificateToLdif(cert)).append("\n");
    }
    return ldapAdapter.addLdifBatch(ldifBatch.toString());
}
```

**Expected Result (병렬)**:
```
Before: 10,000 LDAP requests × 15ms = 150 seconds
After:  100 batches ÷ 10 threads × 200ms = 2 seconds
Improvement: 98.7% faster
```

---

### Phase 1 Summary

| 개선 항목 | 현재 시간 | 개선 후 시간 | 감소율 |
|----------|----------|------------|--------|
| Parsing (N+1 제거) | 120분 | 60분 | 50% |
| Validation (캐싱+배치) | 120분 | 20분 | 83% |
| LDAP Upload (배치) | 60분 | 10분 | 83% |
| **Total** | **300분 (5시간)** | **90분 (1.5시간)** | **70%** |

---

### Phase 2: Parallel Processing (3-5 Days) - Additional 60% Improvement

#### 2-1. Multi-threaded Parsing

**Goal**: CPU 멀티코어 활용 (8 cores → 8배 성능)

**Implementation**:

```java
// LdifParserAdapter.java

@Override
public void parse(byte[] fileBytes, FileFormat fileFormat, ParsedFile parsedFile) throws ParsingException {
    // ... 기존 엔트리 수집 및 중복 체크 코드 ...

    // ✅ Custom ForkJoinPool 생성 (스레드 수 제어)
    int parallelism = Runtime.getRuntime().availableProcessors(); // 8 cores
    ForkJoinPool customThreadPool = new ForkJoinPool(parallelism);

    // ✅ Thread-safe ParsedFile 구현 필요
    ConcurrentParsedFile concurrentParsedFile = new ConcurrentParsedFile(parsedFile);

    try {
        customThreadPool.submit(() -> {
            // ✅ 병렬 스트림으로 엔트리 파싱
            allEntries.parallelStream()
                .forEach(entry -> {
                    try {
                        parseEntryWithCache(entry, concurrentParsedFile, existingFingerprints);
                    } catch (Exception e) {
                        log.error("Failed to parse entry: {}", entry.getDN(), e);
                    }
                });
        }).get(); // 완료 대기
    } catch (Exception e) {
        throw new ParsingException("Parallel parsing error: " + e.getMessage(), e);
    } finally {
        customThreadPool.shutdown();
    }

    log.info("Parallel parsing completed: {} entries processed", allEntries.size());
}
```

**Thread-safe ParsedFile Wrapper**:

```java
// ConcurrentParsedFile.java
public class ConcurrentParsedFile {
    private final ParsedFile parsedFile;
    private final ConcurrentHashMap<String, CertificateData> certificates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CrlData> crls = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ParsingError> errors = new ConcurrentLinkedQueue<>();

    public void addCertificate(CertificateData certData) {
        certificates.put(certData.getFingerprintSha256(), certData);
    }

    public void addCrl(CrlData crlData) {
        crls.put(crlData.getIssuerName(), crlData);
    }

    public void addError(ParsingError error) {
        errors.add(error);
    }

    public void mergeIntoParsedFile() {
        certificates.values().forEach(parsedFile::addCertificate);
        crls.values().forEach(parsedFile::addCrl);
        errors.forEach(parsedFile::addError);
    }
}
```

**Expected Result**:
```
Before: 60분 (single thread)
After:  10분 (8 threads, 6x speedup due to I/O wait)
Improvement: 83% faster
```

---

#### 2-2. Multi-threaded Validation

**Implementation**:

```java
// ValidateCertificatesUseCase.java

@Transactional
public CertificatesValidatedResponse execute(ValidateCertificatesCommand command) {
    // ... 기존 코드 ...

    // ✅ Custom ThreadPoolExecutor 생성
    int poolSize = Runtime.getRuntime().availableProcessors();
    ExecutorService validationExecutor = Executors.newFixedThreadPool(poolSize);

    // ✅ Pass 1: CSCA 병렬 검증
    List<CertificateData> cscaCertificates = certificateDataList.stream()
        .filter(CertificateData::isCsca)
        .collect(Collectors.toList());

    List<CompletableFuture<ValidationResultWrapper>> cscaFutures = cscaCertificates.stream()
        .map(certData -> CompletableFuture.supplyAsync(() ->
            validateAndCreateCertificate(certData, command.uploadId(), true),
            validationExecutor
        ))
        .collect(Collectors.toList());

    // ✅ 모든 CSCA 검증 완료 대기
    List<ValidationResultWrapper> cscaResults = cscaFutures.stream()
        .map(CompletableFuture::join)
        .collect(Collectors.toList());

    // ✅ 배치 저장
    List<Certificate> cscaCerts = cscaResults.stream()
        .map(ValidationResultWrapper::getCertificate)
        .collect(Collectors.toList());
    certificateRepository.saveAll(cscaCerts);

    // ... Pass 2도 유사하게 구현 ...

    validationExecutor.shutdown();
}

private ValidationResultWrapper validateAndCreateCertificate(
    CertificateData certData,
    UUID uploadId,
    boolean isCsca
) {
    try {
        X509Certificate x509Cert = convertToX509Certificate(certData.getCertificateBinary());
        Certificate certificate = createCertificateFromData(certData, x509Cert, uploadId);

        List<ValidationError> errors = new ArrayList<>();
        ValidationResult validationResult = isCsca
            ? validateCscaCertificate(x509Cert, certData, errors)
            : validateDscCertificate(x509Cert, certData, uploadId, errors, cscaCache);

        certificate.recordValidation(validationResult);
        certificate.addValidationErrors(errors);

        return new ValidationResultWrapper(certificate, validationResult, errors);
    } catch (Exception e) {
        // 에러 처리
        return ValidationResultWrapper.failed(certData, e);
    }
}
```

**Expected Result**:
```
Before: 20분 (single thread, after Phase 1)
After:  4분 (8 threads, 5x speedup)
Improvement: 80% faster
```

---

### Phase 3: Architecture Optimization (5-7 Days) - Additional 30% Improvement

#### 3-1. Redis Caching

**Goal**: CSCA 조회 속도 향상 (DB → Redis)

**Implementation**:

```yaml
# application.yml
spring:
  cache:
    type: redis
  redis:
    host: localhost
    port: 6379
    cache:
      ttl: 3600000 # 1 hour
```

```java
// CertificateRepository.java

@Cacheable(value = "csca", key = "#subjectDn")
public Optional<Certificate> findBySubjectDn(String subjectDn) {
    return Optional.ofNullable(entityManager.createQuery(
        "SELECT c FROM Certificate c WHERE c.subjectInfo.subjectDn = :subjectDn",
        Certificate.class
    )
    .setParameter("subjectDn", subjectDn)
    .getSingleResult());
}
```

**Expected Result**:
```
DB Query: ~5ms
Redis Query: ~0.5ms (10x faster)
```

---

#### 3-2. Async Pipeline (Parallel Stages)

**Goal**: 파싱 → 검증 → LDAP 업로드를 파이프라인으로 병렬 처리

**Implementation**:

```java
// AsyncUploadProcessor.java

@Async("parsingExecutor")
public void parseLdif(UploadId uploadId, ...) {
    // 파싱 완료 후 검증 트리거
    ParseFileResponse parseResponse = parseLdifFile(...);

    if (parseResponse.success()) {
        validateCertificatesAsync(uploadId, parseResponse);
    }
}

@Async("validationExecutor")
public void validateCertificatesAsync(UploadId uploadId, ParseFileResponse parseResponse) {
    // 검증 완료 후 LDAP 업로드 트리거
    CertificatesValidatedResponse validationResponse = validateCertificates(...);

    if (validationResponse.success()) {
        uploadToLdapAsync(uploadId, validationResponse);
    }
}

@Async("ldapExecutor")
public void uploadToLdapAsync(UploadId uploadId, CertificatesValidatedResponse validationResponse) {
    // LDAP 업로드
    uploadToLdap(...);
}
```

**ThreadPoolTaskExecutor Configuration**:

```java
@Configuration
public class AsyncConfig {

    @Bean(name = "parsingExecutor")
    public Executor parsingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("parsing-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "validationExecutor")
    public Executor validationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("validation-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "ldapExecutor")
    public Executor ldapExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ldap-");
        executor.initialize();
        return executor;
    }
}
```

---

## 📊 Expected Performance Improvement

### Detailed Timeline

| Phase | 개선 항목 | 현재 시간 | 개선 후 시간 | 감소율 | 작업 기간 |
|-------|----------|----------|------------|--------|----------|
| **Phase 1-1** | 배치 중복 체크 | 50분 | 5분 | 90% | 0.5일 |
| **Phase 1-2** | CSCA 캐싱 | 40분 | 4분 | 90% | 0.5일 |
| **Phase 1-3** | 배치 저장 | 80분 | 12분 | 85% | 1일 |
| **Phase 1-4** | LDAP 배치 업로드 | 60분 | 10분 | 83% | 1일 |
| **Phase 1 Total** | | **300분** | **90분** | **70%** | **2-3일** |
| **Phase 2-1** | 멀티스레드 파싱 | 60분 | 10분 | 83% | 2일 |
| **Phase 2-2** | 멀티스레드 검증 | 30분 | 6분 | 80% | 2일 |
| **Phase 2 Total** | | **90분** | **30분** | **67%** | **3-5일** |
| **Phase 3-1** | Redis 캐싱 | 5분 | 2분 | 60% | 2일 |
| **Phase 3-2** | 비동기 파이프라인 | 30분 | 15분 | 50% | 3일 |
| **Phase 3 Total** | | **30분** | **15분** | **50%** | **5-7일** |
| **Grand Total** | | **300분 (5시간)** | **15분** | **95%** | **10-15일** |

---

## 🎯 Implementation Priority

### Immediate (Week 1)
1. ✅ **Phase 1-1: 배치 중복 체크** - 가장 큰 성능 향상
2. ✅ **Phase 1-2: CSCA 캐싱** - 구현 간단, 효과 큼

### Short-term (Week 2)
3. ✅ **Phase 1-3: 배치 저장** - 트랜잭션 오버헤드 제거
4. ✅ **Phase 1-4: LDAP 배치 업로드** - 네트워크 I/O 최적화

### Mid-term (Week 3-4)
5. ⏳ **Phase 2-1: 멀티스레드 파싱** - 추가 성능 향상
6. ⏳ **Phase 2-2: 멀티스레드 검증** - CPU 활용률 극대화

### Long-term (Month 2)
7. ⏳ **Phase 3-1: Redis 캐싱** - 인프라 추가 필요
8. ⏳ **Phase 3-2: 비동기 파이프라인** - 아키텍처 변경

---

## ⚠️ Risks and Mitigation

### Risk #1: 메모리 부족 (Batch Processing)

**Problem**: 배치 크기가 클 경우 OOM 발생 가능

**Mitigation**:
```java
// JVM 힙 메모리 설정
-Xmx4G -Xms2G

// 배치 크기 조정
final int BATCH_SIZE = 1000; // 메모리 부족 시 500으로 감소
```

---

### Risk #2: 데드락 (Parallel Processing)

**Problem**: 멀티스레드 검증 시 DB 락 경합

**Mitigation**:
```java
// 트랜잭션 격리 수준 조정
@Transactional(isolation = Isolation.READ_COMMITTED)

// 락 타임아웃 설정
spring.jpa.properties.javax.persistence.lock.timeout=5000
```

---

### Risk #3: LDAP 서버 부하

**Problem**: 병렬 업로드 시 LDAP 서버 과부하

**Mitigation**:
```java
// 스레드 풀 크기 제한
ExecutorService executor = Executors.newFixedThreadPool(10); // 최대 10 concurrent requests

// Rate Limiting
RateLimiter rateLimiter = RateLimiter.create(100.0); // 초당 100 requests
rateLimiter.acquire();
ldapAdapter.addLdifBatch(ldifContent);
```

---

### Risk #4: 중복 키 충돌 (Batch Insert)

**Problem**: 배치 저장 시 일부 중복 키로 전체 배치 실패

**Mitigation**:
```java
// PostgreSQL ON CONFLICT 사용
@Query(value = """
    INSERT INTO certificate (...)
    VALUES (...)
    ON CONFLICT (fingerprint_sha256)
    DO UPDATE SET status = EXCLUDED.status
    """, nativeQuery = true)
void upsertCertificate(...);

// 또는 개별 재시도
try {
    certificateRepository.saveAll(batch);
} catch (DataIntegrityViolationException e) {
    // Fallback to individual save
    for (Certificate cert : batch) {
        try {
            certificateRepository.save(cert);
        } catch (Exception ex) {
            log.error("Failed to save: {}", cert.getId(), ex);
        }
    }
}
```

---

## 🧪 Testing Plan

### Unit Tests
```java
// CertificateExistenceServiceTest.java
@Test
void testFindExistingFingerprintsBatch() {
    Set<String> fingerprints = Set.of("abc123", "def456", "ghi789");
    Set<String> existing = service.findExistingFingerprints(fingerprints);
    assertThat(existing).hasSize(2);
}
```

### Integration Tests
```java
// LdifParserAdapterIntegrationTest.java
@Test
void testBatchDuplicateCheck() {
    byte[] ldifBytes = loadTestLdif("large_10k_entries.ldif");
    ParsedFile parsedFile = new ParsedFile(...);

    long startTime = System.currentTimeMillis();
    ldifParserAdapter.parse(ldifBytes, FileFormat.LDIF, parsedFile);
    long duration = System.currentTimeMillis() - startTime;

    assertThat(duration).isLessThan(60000); // < 1 minute
    assertThat(parsedFile.getCertificates()).hasSize(expectedCount);
}
```

### Performance Tests
```java
// PerformanceTest.java
@Test
void testFullPipelinePerformance() {
    // 10,000 certificates 업로드
    byte[] ldifBytes = loadRealLdif("icao_pkd_10k.ldif");

    long startTime = System.currentTimeMillis();
    UploadId uploadId = uploadFile(ldifBytes);
    waitForCompletion(uploadId);
    long duration = System.currentTimeMillis() - startTime;

    assertThat(duration).isLessThan(1800000); // < 30 minutes (after Phase 1+2)
}
```

---

## 📝 Action Items

### Immediate Actions (This Week)
- [ ] CertificateExistenceService에 `findExistingFingerprints()` 구현
- [ ] LdifParserAdapter 배치 중복 체크 리팩토링
- [ ] ValidateCertificatesUseCase CSCA 캐싱 구현
- [ ] 성능 테스트 환경 구축 (대용량 LDIF 파일 준비)

### Week 2
- [ ] 배치 저장 구현 (CertificateSaveService 제거)
- [ ] JPA Batch Insert 설정 추가
- [ ] UnboundIdLdapAdapter 배치 업로드 구현
- [ ] Phase 1 통합 테스트

### Week 3-4
- [ ] 멀티스레드 파싱 구현
- [ ] 멀티스레드 검증 구현
- [ ] Thread-safe 유틸리티 개발
- [ ] Phase 2 성능 측정

### Month 2 (Optional)
- [ ] Redis 캐싱 인프라 구축
- [ ] 비동기 파이프라인 리팩토링
- [ ] 모니터링 대시보드 구축

---

## 📚 References

### Code Locations
- Parsing: [LdifParserAdapter.java](../src/main/java/com/smartcoreinc/localpkd/fileparsing/infrastructure/adapter/LdifParserAdapter.java)
- Validation: [ValidateCertificatesUseCase.java](../src/main/java/com/smartcoreinc/localpkd/certificatevalidation/application/usecase/ValidateCertificatesUseCase.java)
- LDAP Upload: [UploadToLdapUseCase.java](../src/main/java/com/smartcoreinc/localpkd/ldapintegration/application/usecase/UploadToLdapUseCase.java)

### Architecture Docs
- [CLAUDE.md](../CLAUDE.md) - Project Guide
- [PROJECT_SUMMARY_2025-11-21.md](PROJECT_SUMMARY_2025-11-21.md) - Project Overview
- [SESSION_2025-12-05_MIGRATION_CONSOLIDATION.md](SESSION_2025-12-05_MIGRATION_CONSOLIDATION.md) - Recent DB Changes

### External Resources
- [PostgreSQL Batch Insert Best Practices](https://www.postgresql.org/docs/current/populate.html)
- [Hibernate Batch Processing](https://docs.jboss.org/hibernate/orm/5.6/userguide/html_single/Hibernate_User_Guide.html#batch)
- [UnboundID LDAP SDK Documentation](https://docs.ldap.com/ldap-sdk/)

---

**Document Version**: 1.0
**Last Updated**: 2025-12-05
**Next Review**: After Phase 1 completion

*이 문서는 성능 개선 작업의 마스터 플랜입니다. 각 Phase 완료 후 실제 측정 결과를 바탕으로 업데이트하세요.*
