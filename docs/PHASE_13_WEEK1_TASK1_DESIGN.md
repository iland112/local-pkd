# Phase 13 Week 1 Task 1: Domain Services 설계

**작성 날짜**: 2025-10-24
**작업 범위**: Trust Chain Verification 설계 및 Domain Services 인터페이스 정의
**상태**: ✅ **설계 완료**

---

## 📋 Task 1 개요

### 목표

Certificate Validation Context의 핵심 도메인 서비스인 Trust Chain Validator와 Certificate Path Builder를 설계합니다.

### 산출물

1. **Trust Chain 검증 흐름도** - ICAO PKD 인증서 계층 구조 기반 검증 시나리오
2. **Domain Service 인터페이스** - TrustChainValidator, CertificatePathBuilder
3. **Value Objects 설계** - ValidationResult, TrustPath, ValidationError

---

## 🔐 Trust Chain Verification 시나리오

### ICAO PKD 인증서 계층 구조

```
┌─────────────────────────────────────────────────────────────┐
│                  CSCA (Country Signing CA)                   │
│                                                              │
│  - Self-Signed Certificate                                  │
│  - Root of Trust (국가별)                                    │
│  - Subject = Issuer (예: C=KR, CN=CSCA-KR)                  │
│  - KeyUsage: keyCertSign, cRLSign                           │
│  - BasicConstraints: CA=true                                │
└────────────────────┬────────────────────────────────────────┘
                     │ Signs (발급)
                     ▼
┌─────────────────────────────────────────────────────────────┐
│              DSC (Document Signer Certificate)               │
│                                                              │
│  - Issued by CSCA                                           │
│  - Intermediate Certificate                                 │
│  - Subject: C=KR, CN=DSC-KR-xxx                             │
│  - Issuer: C=KR, CN=CSCA-KR                                 │
│  - KeyUsage: digitalSignature                               │
│  - BasicConstraints: CA=false (일반적으로)                  │
└────────────────────┬────────────────────────────────────────┘
                     │ Signs (서명)
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                DS (Document Signature)                       │
│                                                              │
│  - Actual signature on eMRTD/Passport                       │
│  - Signed by DSC                                            │
│  - End-Entity (Leaf Certificate)                            │
└─────────────────────────────────────────────────────────────┘
```

### Trust Chain 검증 흐름도

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Certificate Path 구축                                     │
│    - 대상 인증서 (DS 또는 DSC)                               │
│    - Issuer DN으로 부모 인증서 검색                          │
│    - CSCA까지 재귀적으로 경로 구축                           │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. CSCA 검증 (Root of Trust)                                │
│    - Self-Signed 확인 (Subject == Issuer)                   │
│    - KeyUsage: keyCertSign, cRLSign                         │
│    - BasicConstraints: CA=true                              │
│    - Signature Self-Verification                            │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. DSC 검증 (Intermediate)                                  │
│    - Issuer DN == CSCA Subject DN                           │
│    - CSCA Public Key로 DSC Signature 검증                   │
│    - Validity Period 확인 (현재 시간 기준)                   │
│    - KeyUsage 확인 (digitalSignature)                       │
│    - CRL 확인 (폐기 여부)                                    │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. DS 검증 (End-Entity) - 선택적                            │
│    - Issuer DN == DSC Subject DN                            │
│    - DSC Public Key로 DS Signature 검증                     │
│    - Validity Period 확인                                   │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. Trust Chain 검증 결과 반환                                │
│    - ValidationResult (성공/실패)                            │
│    - TrustPath (CSCA → DSC → DS)                            │
│    - ValidationError List (실패 사유)                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎯 비즈니스 규칙

### 1. CSCA (Country Signing CA) 검증 규칙

| 규칙 ID | 규칙 내용 | 검증 로직 |
|---------|-----------|-----------|
| CSCA-001 | Self-Signed 확인 | `certificate.getSubjectDN().equals(certificate.getIssuerDN())` |
| CSCA-002 | CA 플래그 확인 | `certificate.getBasicConstraints() >= 0` (CA=true) |
| CSCA-003 | KeyUsage 확인 | `keyCertSign` AND `cRLSign` 포함 |
| CSCA-004 | Signature 자기 검증 | `certificate.verify(certificate.getPublicKey())` |
| CSCA-005 | 유효기간 확인 | `notBefore <= now <= notAfter` |

### 2. DSC (Document Signer Certificate) 검증 규칙

| 규칙 ID | 규칙 내용 | 검증 로직 |
|---------|-----------|-----------|
| DSC-001 | Issuer 확인 | `dsc.getIssuerDN().equals(csca.getSubjectDN())` |
| DSC-002 | Signature 검증 | `dsc.verify(csca.getPublicKey())` |
| DSC-003 | 유효기간 확인 | `notBefore <= now <= notAfter` |
| DSC-004 | KeyUsage 확인 | `digitalSignature` 포함 |
| DSC-005 | CRL 확인 | CRL에서 Serial Number 검색 → NOT_FOUND |
| DSC-006 | CA 플래그 확인 (선택) | `basicConstraints == -1` (일반적으로 CA=false) |

### 3. Trust Path 구축 규칙

| 규칙 ID | 규칙 내용 | 알고리즘 |
|---------|-----------|----------|
| PATH-001 | 최대 깊이 제한 | `maxDepth = 5` (무한 루프 방지) |
| PATH-002 | Issuer DN 기반 검색 | `certificateRepository.findBySubjectDn(issuerDn)` |
| PATH-003 | Self-Signed 감지 | `subject == issuer` → Root 도달 |
| PATH-004 | 경로 순서 | List[0]=CSCA, List[1]=DSC, List[2]=DS (Root → Leaf) |
| PATH-005 | 순환 참조 방지 | Set으로 방문 인증서 추적 |

### 4. 특수 케이스 처리

#### Case 1: Self-Signed CSCA (정상 케이스)
```
CSCA (Self-Signed, C=KR, CN=CSCA-KR)
  │
  └─ Subject == Issuer
  └─ Signature verified with own public key
  └─ ✅ Valid Root of Trust
```

#### Case 2: Multiple CSCA (국가별 여러 CSCA)
```
CSCA-KR-OLD (Expired)
CSCA-KR-NEW (Valid)
  │
  └─ 동일 국가 코드, 다른 버전
  └─ Valid 기간으로 자동 선택
  └─ ✅ Use CSCA-KR-NEW
```

#### Case 3: Cross-Certified CSCA (교차 인증)
```
CSCA-KR (Self-Signed)
  │
  └─ Issues DSC-KR
     │
     └─ Also signed by CSCA-US (Cross-Certification)
        │
        └─ 여러 Trust Path 존재
        └─ ✅ 모든 경로 검증 필요
```

#### Case 4: Missing Intermediate Certificate
```
CSCA-KR (존재)
  │
  └─ DSC-KR-123 (누락)
     │
     └─ DS (검증 대상)
        │
        └─ ❌ Path 구축 실패 → ValidationError
```

---

## 🏗️ Value Objects 설계

### 1. ValidationResult

**목적**: 인증서 검증 결과를 표현하는 Value Object

**클래스 정의**:

```java
package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ValidationResult - 인증서 검증 결과 Value Object
 *
 * <p>인증서 Trust Chain 검증 결과를 표현합니다.
 * 검증 성공/실패 여부, 실패 사유 목록을 포함합니다.</p>
 *
 * <h3>비즈니스 규칙</h3>
 * <ul>
 *   <li>검증 성공 시: status=VALID, errors=empty</li>
 *   <li>검증 실패 시: status=INVALID, errors 1개 이상</li>
 *   <li>경고 포함 성공: status=VALID_WITH_WARNINGS, warnings 1개 이상</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * ValidationResult result = ValidationResult.valid();
 * if (!result.isValid()) {
 *     result.getErrors().forEach(error -> log.error("{}", error.getMessage()));
 * }
 * }</pre>
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ValidationResult implements ValueObject {

    /**
     * 검증 상태
     */
    public enum Status {
        VALID("검증 성공"),
        VALID_WITH_WARNINGS("경고 포함 검증 성공"),
        INVALID("검증 실패"),
        NOT_VERIFIED("검증되지 않음");

        private final String displayName;

        Status(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    @Enumerated(EnumType.STRING)
    private Status status;

    // Note: JPA does not support @ElementCollection in @Embeddable directly
    // These will be managed in the Aggregate Root with @ElementCollection
    private transient List<ValidationError> errors;
    private transient List<ValidationError> warnings;

    /**
     * Private constructor with validation
     */
    private ValidationResult(Status status, List<ValidationError> errors, List<ValidationError> warnings) {
        if (status == null) {
            throw new IllegalArgumentException("Status must not be null");
        }

        this.status = status;
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
        this.warnings = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
    }

    // ==================== Static Factory Methods ====================

    /**
     * 검증 성공 결과 생성
     */
    public static ValidationResult valid() {
        return new ValidationResult(Status.VALID, Collections.emptyList(), Collections.emptyList());
    }

    /**
     * 경고 포함 검증 성공 결과 생성
     */
    public static ValidationResult validWithWarnings(List<ValidationError> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            throw new IllegalArgumentException("Warnings must not be empty for VALID_WITH_WARNINGS status");
        }
        return new ValidationResult(Status.VALID_WITH_WARNINGS, Collections.emptyList(), warnings);
    }

    /**
     * 검증 실패 결과 생성
     */
    public static ValidationResult invalid(List<ValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("Errors must not be empty for INVALID status");
        }
        return new ValidationResult(Status.INVALID, errors, Collections.emptyList());
    }

    /**
     * 단일 오류로 검증 실패 결과 생성
     */
    public static ValidationResult invalid(ValidationError error) {
        return invalid(List.of(error));
    }

    /**
     * 검증되지 않음 결과 생성
     */
    public static ValidationResult notVerified() {
        return new ValidationResult(Status.NOT_VERIFIED, Collections.emptyList(), Collections.emptyList());
    }

    // ==================== Business Methods ====================

    /**
     * 검증 성공 여부 확인
     */
    public boolean isValid() {
        return status == Status.VALID || status == Status.VALID_WITH_WARNINGS;
    }

    /**
     * 검증 실패 여부 확인
     */
    public boolean isInvalid() {
        return status == Status.INVALID;
    }

    /**
     * 경고가 있는지 확인
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * 오류가 있는지 확인
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * 오류 목록 반환 (Immutable)
     */
    public List<ValidationError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * 경고 목록 반환 (Immutable)
     */
    public List<ValidationError> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * 첫 번째 오류 메시지 반환 (없으면 null)
     */
    public String getFirstErrorMessage() {
        return errors.isEmpty() ? null : errors.get(0).getMessage();
    }

    @Override
    public String toString() {
        return String.format("ValidationResult[status=%s, errors=%d, warnings=%d]",
                status, errors.size(), warnings.size());
    }
}
```

---

### 2. TrustPath

**목적**: CSCA → DSC → DS 신뢰 경로를 표현하는 Value Object

**클래스 정의**:

```java
package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * TrustPath - 인증서 신뢰 경로 Value Object
 *
 * <p>CSCA (Root) → DSC (Intermediate) → DS (Leaf) 경로를 표현합니다.
 * 인증서 체인의 순서와 계층 구조를 유지합니다.</p>
 *
 * <h3>비즈니스 규칙</h3>
 * <ul>
 *   <li>경로는 최소 1개 이상의 인증서 포함 (CSCA)</li>
 *   <li>경로 순서: [0]=CSCA (Root), [1]=DSC, [2]=DS (Leaf)</li>
 *   <li>최대 깊이: 5 (무한 루프 방지)</li>
 *   <li>첫 번째 인증서는 Self-Signed (CSCA)</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * TrustPath path = TrustPath.of(List.of(cscaId, dscId, dsId));
 * UUID rootId = path.getRoot();  // CSCA ID
 * UUID leafId = path.getLeaf();  // DS ID
 * int depth = path.getDepth();   // 3
 * }</pre>
 */
@Getter
@EqualsAndHashCode
public class TrustPath implements ValueObject {

    public static final int MAX_DEPTH = 5;

    private final List<UUID> certificateIds;  // Order: [0]=CSCA, [1]=DSC, [2]=DS

    /**
     * Private constructor with validation
     */
    private TrustPath(List<UUID> certificateIds) {
        validate(certificateIds);
        this.certificateIds = new ArrayList<>(certificateIds);
    }

    private void validate(List<UUID> certificateIds) {
        if (certificateIds == null || certificateIds.isEmpty()) {
            throw new IllegalArgumentException("Certificate IDs must not be empty");
        }

        if (certificateIds.size() > MAX_DEPTH) {
            throw new IllegalArgumentException(
                    String.format("Trust path depth exceeds maximum allowed (%d > %d)",
                            certificateIds.size(), MAX_DEPTH)
            );
        }

        // Check for duplicates (circular reference)
        long distinctCount = certificateIds.stream().distinct().count();
        if (distinctCount != certificateIds.size()) {
            throw new IllegalArgumentException("Trust path contains circular reference (duplicate certificate IDs)");
        }
    }

    // ==================== Static Factory Methods ====================

    /**
     * 인증서 ID 목록으로 TrustPath 생성
     */
    public static TrustPath of(List<UUID> certificateIds) {
        return new TrustPath(certificateIds);
    }

    /**
     * 단일 인증서 (CSCA only) TrustPath 생성
     */
    public static TrustPath ofSingle(UUID cscaId) {
        return new TrustPath(List.of(cscaId));
    }

    /**
     * 2개 인증서 (CSCA → DSC) TrustPath 생성
     */
    public static TrustPath ofTwo(UUID cscaId, UUID dscId) {
        return new TrustPath(List.of(cscaId, dscId));
    }

    /**
     * 3개 인증서 (CSCA → DSC → DS) TrustPath 생성
     */
    public static TrustPath ofThree(UUID cscaId, UUID dscId, UUID dsId) {
        return new TrustPath(List.of(cscaId, dscId, dsId));
    }

    // ==================== Business Methods ====================

    /**
     * Root 인증서 ID 반환 (CSCA)
     */
    public UUID getRoot() {
        return certificateIds.get(0);
    }

    /**
     * Leaf 인증서 ID 반환 (마지막 인증서)
     */
    public UUID getLeaf() {
        return certificateIds.get(certificateIds.size() - 1);
    }

    /**
     * 경로 깊이 반환 (인증서 개수)
     */
    public int getDepth() {
        return certificateIds.size();
    }

    /**
     * Immutable 인증서 ID 목록 반환
     */
    public List<UUID> getCertificateIds() {
        return Collections.unmodifiableList(certificateIds);
    }

    /**
     * 특정 인덱스의 인증서 ID 반환
     */
    public UUID getCertificateIdAt(int index) {
        if (index < 0 || index >= certificateIds.size()) {
            throw new IllegalArgumentException(
                    String.format("Index out of bounds: %d (size: %d)", index, certificateIds.size())
            );
        }
        return certificateIds.get(index);
    }

    /**
     * 특정 인증서가 경로에 포함되어 있는지 확인
     */
    public boolean contains(UUID certificateId) {
        return certificateIds.contains(certificateId);
    }

    /**
     * 경로가 단일 인증서인지 확인 (CSCA only, Self-Signed)
     */
    public boolean isSingleCertificate() {
        return certificateIds.size() == 1;
    }

    /**
     * 경로 문자열 표현 (ID 앞 8자만)
     */
    public String toShortString() {
        return certificateIds.stream()
                .map(uuid -> uuid.toString().substring(0, 8))
                .collect(Collectors.joining(" → "));
    }

    @Override
    public String toString() {
        return String.format("TrustPath[depth=%d, path=%s]",
                certificateIds.size(),
                certificateIds.stream()
                        .map(UUID::toString)
                        .collect(Collectors.joining(" → "))
        );
    }
}
```

---

### 3. ValidationError

**목적**: 검증 실패 사유를 표현하는 Value Object

**클래스 정의**:

```java
package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ValidationError - 인증서 검증 오류 Value Object
 *
 * <p>인증서 검증 실패 시 발생한 오류의 상세 정보를 표현합니다.
 * 오류 코드, 메시지, 발생 시각, 관련 인증서 ID 등을 포함합니다.</p>
 *
 * <h3>오류 타입</h3>
 * <ul>
 *   <li>SIGNATURE_VERIFICATION_FAILED: 서명 검증 실패</li>
 *   <li>CERTIFICATE_EXPIRED: 인증서 만료</li>
 *   <li>CERTIFICATE_NOT_YET_VALID: 인증서 아직 유효하지 않음</li>
 *   <li>INVALID_KEY_USAGE: KeyUsage 검증 실패</li>
 *   <li>INVALID_BASIC_CONSTRAINTS: BasicConstraints 검증 실패</li>
 *   <li>CERTIFICATE_REVOKED: 인증서 폐기됨 (CRL)</li>
 *   <li>PATH_CONSTRUCTION_FAILED: Trust Path 구축 실패</li>
 *   <li>ISSUER_NOT_FOUND: Issuer 인증서를 찾을 수 없음</li>
 *   <li>CIRCULAR_REFERENCE: 순환 참조 감지</li>
 *   <li>MAX_DEPTH_EXCEEDED: 최대 깊이 초과</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * ValidationError error = ValidationError.signatureVerificationFailed(
 *     certificateId,
 *     "Signature verification failed using CSCA public key"
 * );
 * log.error("Validation failed: {} - {}", error.getErrorType(), error.getMessage());
 * }</pre>
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ValidationError implements ValueObject {

    /**
     * 검증 오류 타입
     */
    public enum ErrorType {
        // Signature & Cryptography
        SIGNATURE_VERIFICATION_FAILED("서명 검증 실패"),
        INVALID_PUBLIC_KEY("잘못된 공개키"),

        // Validity Period
        CERTIFICATE_EXPIRED("인증서 만료"),
        CERTIFICATE_NOT_YET_VALID("인증서 아직 유효하지 않음"),

        // Key Usage & Constraints
        INVALID_KEY_USAGE("KeyUsage 검증 실패"),
        INVALID_BASIC_CONSTRAINTS("BasicConstraints 검증 실패"),
        INVALID_EXTENDED_KEY_USAGE("ExtendedKeyUsage 검증 실패"),

        // Revocation
        CERTIFICATE_REVOKED("인증서 폐기됨"),
        CRL_CHECK_FAILED("CRL 확인 실패"),

        // Path Construction
        PATH_CONSTRUCTION_FAILED("신뢰 경로 구축 실패"),
        ISSUER_NOT_FOUND("Issuer 인증서를 찾을 수 없음"),
        CIRCULAR_REFERENCE("순환 참조 감지"),
        MAX_DEPTH_EXCEEDED("최대 깊이 초과"),

        // Others
        UNKNOWN_ERROR("알 수 없는 오류");

        private final String displayName;

        ErrorType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    @Enumerated(EnumType.STRING)
    private ErrorType errorType;

    private String message;

    private LocalDateTime occurredAt;

    private String certificateId;  // UUID as String (nullable)

    /**
     * Private constructor with validation
     */
    private ValidationError(ErrorType errorType, String message, UUID certificateId) {
        if (errorType == null) {
            throw new IllegalArgumentException("ErrorType must not be null");
        }
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message must not be blank");
        }

        this.errorType = errorType;
        this.message = message;
        this.occurredAt = LocalDateTime.now();
        this.certificateId = certificateId != null ? certificateId.toString() : null;
    }

    // ==================== Static Factory Methods ====================

    /**
     * 서명 검증 실패 오류 생성
     */
    public static ValidationError signatureVerificationFailed(UUID certificateId, String details) {
        String message = String.format("Signature verification failed: %s", details);
        return new ValidationError(ErrorType.SIGNATURE_VERIFICATION_FAILED, message, certificateId);
    }

    /**
     * 인증서 만료 오류 생성
     */
    public static ValidationError certificateExpired(UUID certificateId, LocalDateTime notAfter) {
        String message = String.format("Certificate expired on %s", notAfter);
        return new ValidationError(ErrorType.CERTIFICATE_EXPIRED, message, certificateId);
    }

    /**
     * 인증서 아직 유효하지 않음 오류 생성
     */
    public static ValidationError certificateNotYetValid(UUID certificateId, LocalDateTime notBefore) {
        String message = String.format("Certificate not yet valid until %s", notBefore);
        return new ValidationError(ErrorType.CERTIFICATE_NOT_YET_VALID, message, certificateId);
    }

    /**
     * KeyUsage 검증 실패 오류 생성
     */
    public static ValidationError invalidKeyUsage(UUID certificateId, String expected, String actual) {
        String message = String.format("Invalid KeyUsage: expected [%s], but got [%s]", expected, actual);
        return new ValidationError(ErrorType.INVALID_KEY_USAGE, message, certificateId);
    }

    /**
     * BasicConstraints 검증 실패 오류 생성
     */
    public static ValidationError invalidBasicConstraints(UUID certificateId, String details) {
        String message = String.format("Invalid BasicConstraints: %s", details);
        return new ValidationError(ErrorType.INVALID_BASIC_CONSTRAINTS, message, certificateId);
    }

    /**
     * 인증서 폐기 오류 생성
     */
    public static ValidationError certificateRevoked(UUID certificateId, LocalDateTime revocationDate) {
        String message = String.format("Certificate revoked on %s", revocationDate);
        return new ValidationError(ErrorType.CERTIFICATE_REVOKED, message, certificateId);
    }

    /**
     * Issuer를 찾을 수 없음 오류 생성
     */
    public static ValidationError issuerNotFound(UUID certificateId, String issuerDn) {
        String message = String.format("Issuer certificate not found: %s", issuerDn);
        return new ValidationError(ErrorType.ISSUER_NOT_FOUND, message, certificateId);
    }

    /**
     * 순환 참조 오류 생성
     */
    public static ValidationError circularReference(UUID certificateId) {
        String message = "Circular reference detected in trust path";
        return new ValidationError(ErrorType.CIRCULAR_REFERENCE, message, certificateId);
    }

    /**
     * 최대 깊이 초과 오류 생성
     */
    public static ValidationError maxDepthExceeded(int currentDepth, int maxDepth) {
        String message = String.format("Trust path depth exceeded: %d > %d", currentDepth, maxDepth);
        return new ValidationError(ErrorType.MAX_DEPTH_EXCEEDED, message, null);
    }

    /**
     * 일반 오류 생성
     */
    public static ValidationError of(ErrorType errorType, String message, UUID certificateId) {
        return new ValidationError(errorType, message, certificateId);
    }

    // ==================== Business Methods ====================

    /**
     * 인증서 ID 반환 (UUID)
     */
    public UUID getCertificateIdAsUuid() {
        return certificateId != null ? UUID.fromString(certificateId) : null;
    }

    /**
     * 오류가 특정 인증서와 관련되어 있는지 확인
     */
    public boolean isCertificateRelated() {
        return certificateId != null;
    }

    @Override
    public String toString() {
        return String.format("ValidationError[type=%s, message=%s, certificateId=%s, occurredAt=%s]",
                errorType, message,
                certificateId != null ? certificateId.substring(0, 8) + "..." : "N/A",
                occurredAt);
    }
}
```

---

## 🔧 Domain Service 인터페이스 설계

### 1. TrustChainValidator (Domain Service)

**목적**: Trust Chain 검증 로직을 캡슐화하는 Domain Service

**인터페이스 정의**:

```java
package com.smartcoreinc.localpkd.certificatevalidation.domain.service;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.TrustPath;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.ValidationResult;

/**
 * TrustChainValidator - Trust Chain 검증 Domain Service
 *
 * <p>ICAO PKD 인증서 계층 구조에서 Trust Chain을 검증합니다.
 * CSCA (Root of Trust) → DSC (Intermediate) → DS (Leaf) 경로를 검증합니다.</p>
 *
 * <h3>검증 단계</h3>
 * <ol>
 *   <li>CSCA 검증: Self-Signed, CA 플래그, KeyUsage, Signature</li>
 *   <li>DSC 검증: Issuer 확인, Signature 검증, Validity, KeyUsage, CRL</li>
 *   <li>DS 검증 (선택): Issuer 확인, Signature 검증, Validity</li>
 * </ol>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * TrustChainValidator validator = new TrustChainValidatorImpl(certificateRepository, crlRepository);
 * TrustPath path = TrustPath.ofThree(cscaId, dscId, dsId);
 * ValidationResult result = validator.validate(path);
 *
 * if (result.isValid()) {
 *     log.info("Trust chain validated successfully");
 * } else {
 *     result.getErrors().forEach(error -> log.error("{}", error.getMessage()));
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
public interface TrustChainValidator {

    /**
     * Trust Path 전체 검증
     *
     * <p>주어진 Trust Path의 모든 인증서를 순차적으로 검증합니다.
     * Root (CSCA) → Intermediate (DSC) → Leaf (DS) 순서로 검증합니다.</p>
     *
     * @param path Trust Path (CSCA → DSC → DS)
     * @return ValidationResult (성공/실패, 오류 목록)
     */
    ValidationResult validate(TrustPath path);

    /**
     * 단일 인증서 검증 (Trust Path 없이)
     *
     * <p>단일 인증서의 기본 속성만 검증합니다 (Validity, KeyUsage, BasicConstraints).
     * Trust Chain 검증은 수행하지 않습니다.</p>
     *
     * @param certificate 검증 대상 인증서
     * @return ValidationResult (성공/실패, 오류 목록)
     */
    ValidationResult validateSingle(Certificate certificate);

    /**
     * CSCA (Root of Trust) 검증
     *
     * <p>CSCA 인증서가 Root of Trust로서 유효한지 검증합니다.</p>
     *
     * <h4>검증 항목</h4>
     * <ul>
     *   <li>Self-Signed 확인 (Subject == Issuer)</li>
     *   <li>CA 플래그 확인 (BasicConstraints: CA=true)</li>
     *   <li>KeyUsage 확인 (keyCertSign, cRLSign)</li>
     *   <li>Signature 자기 검증</li>
     *   <li>유효기간 확인</li>
     * </ul>
     *
     * @param csca CSCA 인증서
     * @return ValidationResult (성공/실패, 오류 목록)
     */
    ValidationResult validateCsca(Certificate csca);

    /**
     * DSC (Document Signer Certificate) 검증
     *
     * <p>DSC 인증서가 주어진 CSCA에 의해 발급되었으며 유효한지 검증합니다.</p>
     *
     * <h4>검증 항목</h4>
     * <ul>
     *   <li>Issuer 확인 (Issuer DN == CSCA Subject DN)</li>
     *   <li>Signature 검증 (CSCA Public Key 사용)</li>
     *   <li>유효기간 확인</li>
     *   <li>KeyUsage 확인 (digitalSignature)</li>
     *   <li>CRL 확인 (폐기 여부)</li>
     * </ul>
     *
     * @param dsc DSC 인증서
     * @param csca Issuer CSCA 인증서
     * @return ValidationResult (성공/실패, 오류 목록)
     */
    ValidationResult validateDsc(Certificate dsc, Certificate csca);

    /**
     * 2개 인증서 간 Issuer-Subject 관계 검증
     *
     * <p>Child 인증서가 Parent 인증서에 의해 발급되었는지 검증합니다.</p>
     *
     * <h4>검증 항목</h4>
     * <ul>
     *   <li>Issuer DN 일치 확인</li>
     *   <li>Signature 검증 (Parent Public Key 사용)</li>
     * </ul>
     *
     * @param child Child 인증서 (DSC or DS)
     * @param parent Parent 인증서 (CSCA or DSC)
     * @return ValidationResult (성공/실패, 오류 목록)
     */
    ValidationResult validateIssuerRelationship(Certificate child, Certificate parent);
}
```

---

### 2. CertificatePathBuilder (Domain Service)

**목적**: 인증서로부터 CSCA까지의 Trust Path를 자동으로 구축하는 Domain Service

**인터페이스 정의**:

```java
package com.smartcoreinc.localpkd.certificatevalidation.domain.service;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateId;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.TrustPath;

import java.util.Optional;

/**
 * CertificatePathBuilder - 인증서 신뢰 경로 자동 구축 Domain Service
 *
 * <p>주어진 인증서로부터 CSCA (Root of Trust)까지의 경로를 자동으로 구축합니다.
 * Issuer DN을 따라 재귀적으로 부모 인증서를 검색하여 Trust Path를 생성합니다.</p>
 *
 * <h3>알고리즘</h3>
 * <pre>
 * 1. 시작 인증서 (Leaf)
 * 2. Issuer DN 추출
 * 3. Repository에서 Issuer DN으로 부모 인증서 검색
 * 4. 부모 인증서 발견 → 경로에 추가 → 2단계 반복
 * 5. Self-Signed 인증서 도달 (CSCA) → 경로 구축 완료
 * 6. 최대 깊이 도달 또는 Issuer 없음 → 실패
 * </pre>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * CertificatePathBuilder builder = new CertificatePathBuilderImpl(certificateRepository);
 * Optional<TrustPath> pathOpt = builder.buildPath(dscCertificateId);
 *
 * if (pathOpt.isPresent()) {
 *     TrustPath path = pathOpt.get();
 *     log.info("Trust path built: depth={}, path={}", path.getDepth(), path.toShortString());
 * } else {
 *     log.error("Failed to build trust path: CSCA not found");
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-24
 */
public interface CertificatePathBuilder {

    /**
     * 인증서 ID로부터 Trust Path 구축
     *
     * <p>주어진 인증서 ID로부터 CSCA까지의 경로를 자동으로 구축합니다.
     * Issuer DN을 따라 재귀적으로 검색합니다.</p>
     *
     * @param certificateId 시작 인증서 ID (DSC or DS)
     * @return Optional<TrustPath> (경로 구축 성공 시 TrustPath, 실패 시 empty)
     */
    Optional<TrustPath> buildPath(CertificateId certificateId);

    /**
     * 인증서 객체로부터 Trust Path 구축
     *
     * <p>주어진 인증서 객체로부터 CSCA까지의 경로를 자동으로 구축합니다.</p>
     *
     * @param certificate 시작 인증서 (DSC or DS)
     * @return Optional<TrustPath> (경로 구축 성공 시 TrustPath, 실패 시 empty)
     */
    Optional<TrustPath> buildPath(Certificate certificate);

    /**
     * 인증서가 Self-Signed (CSCA)인지 확인
     *
     * <p>인증서의 Subject DN과 Issuer DN이 동일한지 확인합니다.</p>
     *
     * @param certificate 확인 대상 인증서
     * @return true: Self-Signed (CSCA), false: Issued by another CA
     */
    boolean isSelfSigned(Certificate certificate);

    /**
     * 특정 Issuer DN을 가진 부모 인증서 검색
     *
     * <p>Repository에서 주어진 Subject DN과 일치하는 인증서를 검색합니다.
     * 여러 개 발견 시 유효기간이 가장 긴 인증서를 반환합니다.</p>
     *
     * @param issuerDn Issuer Distinguished Name
     * @return Optional<Certificate> (발견 시 Certificate, 없으면 empty)
     */
    Optional<Certificate> findIssuerCertificate(String issuerDn);
}
```

---

## 📊 설계 검증 체크리스트

### Value Objects

- [x] **ValidationResult** - Status, Errors, Warnings 포함, Static Factory Methods 제공
- [x] **TrustPath** - Certificate ID 목록, Immutable, 순환 참조 방지
- [x] **ValidationError** - ErrorType, Message, OccurredAt, CertificateId 포함

### Domain Services

- [x] **TrustChainValidator** - validate(), validateCsca(), validateDsc(), validateIssuerRelationship() 메서드
- [x] **CertificatePathBuilder** - buildPath(), isSelfSigned(), findIssuerCertificate() 메서드

### 비즈니스 규칙

- [x] CSCA 검증 규칙 (5개: Self-Signed, CA Flag, KeyUsage, Signature, Validity)
- [x] DSC 검증 규칙 (6개: Issuer, Signature, Validity, KeyUsage, CRL, CA Flag)
- [x] Trust Path 구축 규칙 (5개: Max Depth, Issuer DN, Self-Signed, Order, Circular Reference)

### 특수 케이스

- [x] Self-Signed CSCA 처리
- [x] Multiple CSCA (동일 국가, 다른 버전) 처리
- [x] Cross-Certified CSCA 처리
- [x] Missing Intermediate Certificate 처리

### JavaDoc

- [x] 모든 클래스 JavaDoc 완비 (목적, 비즈니스 규칙, 사용 예시)
- [x] 모든 public 메서드 JavaDoc 완비
- [x] 파라미터 설명, 반환값 설명, 예외 설명

---

## 🎯 다음 단계 (Task 2)

### Task 2: TrustChainValidator Domain Service 구현

**파일 위치**: `src/main/java/com/smartcoreinc/localpkd/certificatevalidation/domain/service/TrustChainValidatorImpl.java`

**구현 항목**:
1. `validate(TrustPath path)` - Trust Path 전체 검증
2. `validateSingle(Certificate)` - 단일 인증서 검증
3. `validateCsca(Certificate)` - CSCA 검증
4. `validateDsc(Certificate, Certificate)` - DSC 검증
5. `validateIssuerRelationship(Certificate, Certificate)` - Issuer-Subject 관계 검증

**의존성**:
- `CertificateRepository` - 인증서 조회
- `CertificateRevocationListRepository` - CRL 조회

**예상 LOC**: ~500 lines

---

## ✅ Acceptance Criteria

- [x] Trust Chain Verification 시나리오 문서화
- [x] CSCA/DSC/DS 역할 정의
- [x] Self-Signed CA 특수 케이스 정의
- [x] ValidationResult Value Object 설계 완료
- [x] TrustPath Value Object 설계 완료
- [x] ValidationError Value Object 설계 완료
- [x] TrustChainValidator 인터페이스 정의 완료
- [x] CertificatePathBuilder 인터페이스 정의 완료
- [x] 모든 클래스 JavaDoc 완비
- [x] 비즈니스 규칙 테이블 작성

---

## 📝 최종 상태

**Phase 13 Week 1 Task 1 완료** ✅

- **Value Objects 설계**: 3개 (ValidationResult, TrustPath, ValidationError)
- **Domain Service 인터페이스**: 2개 (TrustChainValidator, CertificatePathBuilder)
- **비즈니스 규칙**: 16개 (CSCA 5개, DSC 6개, Path 5개)
- **특수 케이스**: 4개 시나리오
- **Total Lines**: ~800 lines (설계 문서)

**다음 작업**: Phase 13 Week 1 Task 2 - TrustChainValidator Domain Service 구현

---

**작성자**: kbjung
**문서 버전**: 1.0
**마지막 업데이트**: 2025-10-24
