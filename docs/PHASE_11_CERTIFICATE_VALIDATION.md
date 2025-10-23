# Phase 11: Certificate Validation Context Design

**시작일**: 2025-10-23
**단계**: DDD Bounded Context - Domain Layer 설계
**목표**: X.509 인증서 검증 및 Trust Chain 검증 구현

---

## 1. Context 개요

### 역할
Certificate Validation Context는 파일 파싱 후 추출된 X.509 인증서의 유효성을 검증합니다.

### 책임
- X.509 인증서 유효성 검증 (서명, 용도, 확장자)
- Trust Chain 검증 (Self-signed, Root CA, Intermediate CA)
- 유효기간 검증 (notBefore, notAfter)
- CRL (Certificate Revocation List) 확인
- OCSP (Online Certificate Status Protocol) 조회 (선택사항)

### 경계
```
File Parsing Context
         ↓
    ParsedFile (인증서 추출)
         ↓
Certificate Validation Context ← Main Responsibility
         ↓
Certificate 유효성 검증
         ↓
LDAP Integration Context
```

---

## 2. Bounded Context Architecture

### 2.1 Aggregate Root: Certificate

```java
public class Certificate extends AbstractAggregateRoot<CertificateId> {
    // Entity ID
    private CertificateId id;

    // Value Objects
    private X509Data x509Data;              // 인증서 DER 데이터 + PublicKey
    private SubjectInfo subject;            // Subject DN + 파싱된 정보
    private IssuerInfo issuer;              // Issuer DN + 파싱된 정보
    private ValidityPeriod validity;        // notBefore, notAfter
    private CertificateType certType;       // CSCA, DSC, DSC_NC 등
    private CountryCode countryCode;        // ISO 3166-1 alpha-2
    private CertificateStatus status;       // VALID, EXPIRED, REVOKED, INVALID

    // Validation Result
    private ValidationResult validationResult;  // 검증 결과 상세 정보
    private List<ValidationError> validationErrors;  // ElementCollection
}
```

### 2.2 Value Objects

#### X509Data
- **DER 인증서 바이너리** (byte[])
- **공개키** (PublicKey)
- **Serial Number** (String, 16진수)
- **Fingerprint SHA-256** (String)

#### SubjectInfo
- **Distinguished Name** (String)
- **Country** (CountryCode)
- **Organization** (String)
- **Organizational Unit** (String)
- **Common Name** (String)

#### IssuerInfo
- **Distinguished Name** (String)
- **Country** (CountryCode)
- **Organization** (String)
- **Organizational Unit** (String)
- **Common Name** (String)
- **IsCA** (boolean)  // Self-signed 여부

#### ValidityPeriod
- **notBefore** (LocalDateTime)
- **notAfter** (LocalDateTime)
- **Methods**: isExpired(), isNotYetValid(), isCurrentlyValid(), daysUntilExpiration()

#### CertificateType
- **CSCA** (Country Signing CA)
- **DSC** (Document Signer Certificate)
- **DSC_NC** (Document Signer Certificate with no ePassport Linking)
- **DS** (Document Signer)
- **UNKNOWN** (기타)

#### CertificateStatus
- **VALID** (유효)
- **EXPIRED** (만료)
- **NOT_YET_VALID** (유효기간 이전)
- **REVOKED** (폐기됨)
- **INVALID** (유효하지 않음)

#### ValidationResult
- **overallStatus** (VALID, INVALID)
- **signatureValid** (boolean)
- **chainValid** (boolean)
- **notRevoked** (boolean)
- **validityValid** (boolean)
- **certificateConstraintsValid** (boolean)
- **validatedAt** (LocalDateTime)
- **validationDuration** (long, milliseconds)

#### ValidationError
- **errorCode** (String: SIGNATURE_INVALID, CHAIN_UNTRUSTED, EXPIRED, REVOKED, etc.)
- **errorMessage** (String)
- **severity** (ERROR, WARNING)
- **occuredAt** (LocalDateTime)

---

## 3. Domain Services

### 3.1 CertificateSignatureValidator
**책임**: 인증서 서명 검증

```java
public interface CertificateSignatureValidator {
    ValidationResult validateSignature(Certificate certificate, Certificate issuerCertificate);
    // issuerCertificate의 공개키로 certificate의 서명 검증
}
```

### 3.2 TrustChainValidator
**책임**: Trust Chain 검증 (Root CA부터 Certificate까지 연쇄 검증)

```java
public interface TrustChainValidator {
    ValidationResult validateTrustChain(
        Certificate certificate,
        List<Certificate> intermediateCAs,
        List<Certificate> trustedRootCAs
    );
    // 신뢰할 수 있는 Root CA에서 시작하는 체인 검증
}
```

### 3.3 CertificateRevocationChecker
**책임**: CRL 및 OCSP를 통한 폐기 여부 확인

```java
public interface CertificateRevocationChecker {
    RevocationStatus checkRevocationByCRL(Certificate certificate, CRL crl);
    RevocationStatus checkRevocationByOCSP(Certificate certificate, String ocspUrl);
    // CRL: Certificate Revocation List
    // OCSP: Online Certificate Status Protocol
}
```

### 3.4 CertificateConstraintsValidator
**책임**: 인증서 제약조건 검증 (BasicConstraints, KeyUsage, ExtendedKeyUsage)

```java
public interface CertificateConstraintsValidator {
    ValidationResult validateConstraints(Certificate certificate);
    // BasicConstraints (CA 여부)
    // KeyUsage (digitalSignature, keyCertSign, cRLSign 등)
    // ExtendedKeyUsage (serverAuth, clientAuth 등)
}
```

---

## 4. Domain Events

### CertificateValidatedEvent
```java
public class CertificateValidatedEvent extends DomainEvent {
    private CertificateId certificateId;
    private ValidationResult validationResult;  // VALID, INVALID
    private long validationDurationMillis;
}
```

### CertificateValidationFailedEvent
```java
public class CertificateValidationFailedEvent extends DomainEvent {
    private CertificateId certificateId;
    private String failureReason;
    private List<ValidationError> validationErrors;
}
```

### TrustChainVerifiedEvent
```java
public class TrustChainVerifiedEvent extends DomainEvent {
    private CertificateId certificateId;
    private List<CertificateId> chainPath;  // Root → Intermediate → Certificate
    private boolean chainValid;
}
```

### CertificateRevokedEvent
```java
public class CertificateRevokedEvent extends DomainEvent {
    private CertificateId certificateId;
    private LocalDateTime revocationDate;
    private String revocationReason;
}
```

---

## 5. Repository Interface

### CertificateRepository
```java
public interface CertificateRepository {
    Certificate save(Certificate certificate);
    Optional<Certificate> findById(CertificateId id);
    Optional<Certificate> findBySerialNumber(String serialNumber);
    Optional<Certificate> findByFingerprint(String fingerprintSha256);
    List<Certificate> findByIssuerDN(String issuerDN);
    List<Certificate> findByCountryCode(String countryCode);
    List<Certificate> findByStatus(CertificateStatus status);
    void deleteById(CertificateId id);
    boolean existsById(CertificateId id);
}
```

---

## 6. Port Interface (Hexagonal)

### CertificateValidationPort
```java
public interface CertificateValidationPort {
    ValidationResult validateSignature(
        byte[] certificateDER,
        byte[] issuerCertificateDER
    ) throws ValidationException;

    ValidationResult validateTrustChain(
        byte[] certificateDER,
        List<byte[]> intermediateCertificatesDER,
        List<byte[]> trustedRootCertificatesDER
    ) throws ValidationException;

    RevocationStatus checkRevocationByCRL(
        byte[] certificateDER,
        byte[] crlDER
    ) throws ValidationException;
}
```

### Adapter Implementation
- **X509CertificateValidationAdapter**: BouncyCastle 기반 구현
- **OCSPValidationAdapter**: OCSP 조회 (선택사항, Phase 12)

---

## 7. Database Schema (Flyway V8)

### `certificate` (Aggregate Root)
```sql
CREATE TABLE certificate (
    id UUID PRIMARY KEY,
    parsed_file_id UUID NOT NULL,

    -- X509Data
    certificate_der BYTEA NOT NULL,
    serial_number VARCHAR(100) NOT NULL UNIQUE,
    fingerprint_sha256 VARCHAR(64) NOT NULL UNIQUE,

    -- SubjectInfo
    subject_dn VARCHAR(500) NOT NULL,
    subject_country_code VARCHAR(2),
    subject_organization VARCHAR(255),
    subject_organizational_unit VARCHAR(255),
    subject_common_name VARCHAR(255),

    -- IssuerInfo
    issuer_dn VARCHAR(500) NOT NULL,
    issuer_country_code VARCHAR(2),
    issuer_organization VARCHAR(255),
    issuer_organizational_unit VARCHAR(255),
    issuer_common_name VARCHAR(255),
    issuer_is_ca BOOLEAN,

    -- ValidityPeriod
    not_before TIMESTAMP NOT NULL,
    not_after TIMESTAMP NOT NULL,

    -- CertificateType & CountryCode
    cert_type VARCHAR(20) NOT NULL,
    country_code VARCHAR(2),

    -- CertificateStatus
    status VARCHAR(20) NOT NULL,

    -- ValidationResult
    validation_status VARCHAR(20),  -- VALID, INVALID
    signature_valid BOOLEAN,
    chain_valid BOOLEAN,
    not_revoked BOOLEAN,
    validity_valid BOOLEAN,
    constraints_valid BOOLEAN,
    validated_at TIMESTAMP,
    validation_duration_millis BIGINT,

    -- Constraints
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (parsed_file_id) REFERENCES parsed_file(id) ON DELETE CASCADE,
    CHECK (status IN ('VALID', 'EXPIRED', 'NOT_YET_VALID', 'REVOKED', 'INVALID'))
);

CREATE INDEX idx_certificate_serial_number ON certificate(serial_number);
CREATE INDEX idx_certificate_fingerprint ON certificate(fingerprint_sha256);
CREATE INDEX idx_certificate_issuer_dn ON certificate(issuer_dn);
CREATE INDEX idx_certificate_country_code ON certificate(country_code);
CREATE INDEX idx_certificate_status ON certificate(status);
CREATE INDEX idx_certificate_parsed_file_id ON certificate(parsed_file_id);
```

### `validation_error` (ElementCollection)
```sql
CREATE TABLE validation_error (
    certificate_id UUID NOT NULL,
    error_code VARCHAR(50) NOT NULL,
    error_message VARCHAR(1000) NOT NULL,
    severity VARCHAR(20) NOT NULL,  -- ERROR, WARNING
    occurred_at TIMESTAMP NOT NULL,

    PRIMARY KEY (certificate_id, error_code, occurred_at),
    FOREIGN KEY (certificate_id) REFERENCES certificate(id) ON DELETE CASCADE,
    CHECK (severity IN ('ERROR', 'WARNING'))
);

CREATE INDEX idx_validation_error_code ON validation_error(error_code);
CREATE INDEX idx_validation_error_severity ON validation_error(severity);
```

### `trust_chain` (관계 추적, 선택사항)
```sql
CREATE TABLE trust_chain (
    id UUID PRIMARY KEY,
    leaf_certificate_id UUID NOT NULL,
    root_certificate_id UUID NOT NULL,
    chain_path VARCHAR(2000) NOT NULL,  -- JSON: [id1, id2, id3]
    chain_valid BOOLEAN NOT NULL,
    verified_at TIMESTAMP NOT NULL,

    FOREIGN KEY (leaf_certificate_id) REFERENCES certificate(id) ON DELETE CASCADE,
    FOREIGN KEY (root_certificate_id) REFERENCES certificate(id) ON DELETE CASCADE
);

CREATE INDEX idx_trust_chain_leaf ON trust_chain(leaf_certificate_id);
CREATE INDEX idx_trust_chain_root ON trust_chain(root_certificate_id);
CREATE INDEX idx_trust_chain_valid ON trust_chain(chain_valid);
```

---

## 8. Use Case Examples

### ValidateCertificateUseCase
```
입력: ValidateCertificateCommand {
  certificateId: CertificateId,
  issuerCertificateId: CertificateId (선택사항),
  trustedRootCertificateIds: List<CertificateId> (선택사항),
  validateChain: boolean (기본값: true),
  checkRevocation: boolean (기본값: false)
}

프로세스:
1. Certificate 로드
2. 서명 검증 (issuerCertificateId가 있으면)
3. Trust Chain 검증 (validateChain=true면)
4. 폐기 여부 확인 (checkRevocation=true면)
5. 제약조건 검증
6. ValidationResult 생성
7. Certificate 상태 업데이트
8. CertificateValidatedEvent 또는 CertificateValidationFailedEvent 발행

출력: ValidateCertificateResponse {
  certificateId: CertificateId,
  validationResult: ValidationResult,
  overallStatus: VALID/INVALID,
  validationErrors: List<ValidationError>,
  validationDurationMillis: long
}
```

### VerifyTrustChainUseCase
```
입력: VerifyTrustChainCommand {
  leafCertificateId: CertificateId,
  intermediateCertificateIds: List<CertificateId>,
  rootCertificateIds: List<CertificateId>
}

프로세스:
1. 모든 인증서 로드
2. Leaf부터 Root까지 체인 구성
3. 각 인증서 쌍에 대해 서명 검증
4. Root 인증서가 Self-signed인지 확인
5. Trust Chain 관계 저장
6. TrustChainVerifiedEvent 발행

출력: VerifyTrustChainResponse {
  chainValid: boolean,
  chainPath: List<CertificateId>,
  validationDetails: Map<CertificateId, ValidationResult>
}
```

---

## 9. 구현 순서

### Sprint 1: Domain Layer (3-4시간)
1. Entity IDs: CertificateId
2. Value Objects: X509Data, SubjectInfo, IssuerInfo, ValidityPeriod, CertificateType, CertificateStatus, ValidationResult, ValidationError
3. Aggregate Root: Certificate
4. Domain Events: 4개
5. Repository Interface: CertificateRepository
6. Domain Services: Interfaces only (구현은 Infrastructure)

### Sprint 2: Flyway Migration (1시간)
1. V8__Create_Certificate_Table.sql
2. V9__Create_Validation_Error_Table.sql
3. V10__Create_Trust_Chain_Table.sql (선택사항)

### Sprint 3: Application Layer (2-3시간)
1. Commands: ValidateCertificateCommand, VerifyTrustChainCommand
2. Responses: ValidateCertificateResponse, VerifyTrustChainResponse
3. Use Cases: ValidateCertificateUseCase, VerifyTrustChainUseCase

### Sprint 4: Infrastructure Layer (2-3시간)
1. Repository Implementation: JpaCertificateRepository
2. Validation Adapters: X509CertificateValidationAdapter
3. Domain Service Implementations: CertificateSignatureValidator, TrustChainValidator

### Sprint 5: Testing & Integration (1-2시간)
1. Unit Tests for Value Objects
2. Integration Tests for Use Cases
3. Event Handler (SSE 통합)

---

## 10. 기술 스택

### 인증서 검증 라이브러리
- **BouncyCastle**: X.509 인증서 처리 및 서명 검증
- **Java Security API**: 기본 보안 기능
- **JCA/JCE**: 암호화 작업

### 의존성 추가
```xml
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk15on</artifactId>
    <version>1.70</version>
</dependency>

<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcpkix-jdk15on</artifactId>
    <version>1.70</version>
</dependency>
```

---

## 11. 향후 확장

### Phase 12: OCSP 지원
- OCSP Responder 조회
- OCSP Response 검증
- 캐싱 전략

### Phase 13: CRL 캐싱
- CRL 다운로드 및 캐싱
- 주기적 갱신
- 저장소 관리

### Phase 14: 통계 및 모니터링
- 검증 통계 수집
- 무효 인증서 추적
- 경고 시스템

---

**Design Version**: 1.0
**Last Updated**: 2025-10-23 14:55
**Status**: Design Complete ✅
**Next**: Domain Layer Implementation
