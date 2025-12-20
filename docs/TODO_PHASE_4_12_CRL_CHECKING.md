# Phase 4.12: CRL Checking Implementation - Task Breakdown

**Phase**: 4.12
**Title**: CRL (Certificate Revocation List) Checking Implementation
**Status**: Planning
**Started**: 2025-12-19
**Target Completion**: TBD

---

## 📋 Overview

Implement Certificate Revocation List (CRL) checking in the Passive Authentication workflow according to **ICAO Doc 9303 Part 11 & Part 12** standards.

**Goal**: Verify that DSC (Document Signer Certificate) has not been revoked before using it to verify SOD signature.

**Standards Compliance**:
- ICAO Doc 9303 Part 11 (Security Mechanisms for MRTDs)
- ICAO Doc 9303 Part 12 (PKI for MRTDs)
- RFC 5280 (X.509 Certificate and CRL Profile)
- RFC 4515 (LDAP Search Filter String Representation)

**Reference Document**: [ICAO_9303_PA_CRL_STANDARD.md](ICAO_9303_PA_CRL_STANDARD.md)

---

## 🎯 Success Criteria

- [ ] CRL can be retrieved from LDAP directory by CSCA DN
- [ ] CRL signature is verified using CSCA public key
- [ ] CRL freshness is validated (thisUpdate, nextUpdate)
- [ ] DSC revocation status is checked against CRL
- [ ] CRL results are cached (in-memory + database)
- [ ] PassiveAuthenticationService integrates CRL check at Step 7
- [ ] All tests pass (unit + integration)
- [ ] API response includes CRL check result

---

## 📦 Task 1: CRL LDAP Adapter Implementation

**Priority**: High
**Estimated Effort**: 3-4 hours
**Dependencies**: None

### 1.1 Create CRL LDAP Port (Interface)

**File**: `src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/port/CrlLdapPort.java`

**Interface Design**:
```java
package com.smartcoreinc.localpkd.passiveauthentication.domain.port;

import java.security.cert.X509CRL;
import java.util.Optional;

/**
 * Port for retrieving Certificate Revocation Lists (CRLs) from LDAP directory.
 *
 * ICAO Doc 9303 Part 12 - PKI for MRTDs
 * RFC 5280 - X.509 Certificate and CRL Profile
 */
public interface CrlLdapPort {

    /**
     * Retrieves CRL for the given CSCA from LDAP directory.
     *
     * LDAP DN Structure:
     * cn={CSCA-DN},o=crl,c={COUNTRY},dc=data,dc=download,dc=pkd,{baseDN}
     *
     * @param cscaSubjectDn CSCA Subject DN (RFC 4514 format)
     * @param countryCode ISO 3166-1 alpha-2 country code
     * @return X509CRL if found, empty otherwise
     * @throws InfrastructureException if LDAP connection fails
     */
    Optional<X509CRL> findCrlByCsca(String cscaSubjectDn, String countryCode);
}
```

**Acceptance Criteria**:
- [ ] Interface created with Javadoc
- [ ] Method signature matches ICAO 9303 requirements
- [ ] Returns Optional<X509CRL> (not null)
- [ ] Throws InfrastructureException on LDAP errors

---

### 1.2 Implement UnboundID CRL LDAP Adapter

**File**: `src/main/java/com/smartcoreinc/localpkd/passiveauthentication/infrastructure/adapter/UnboundIdCrlLdapAdapter.java`

**Implementation Tasks**:

1. **RFC 4515 LDAP Filter Escaping** (reuse from UnboundIdLdapCscaAdapter)
   ```java
   private String buildCrlFilter(String cscaSubjectDn) {
       String escapedDn = LdapFilterEscaper.escape(cscaSubjectDn);
       return String.format(
           "(&(objectClass=cRLDistributionPoint)(cn=%s))",
           escapedDn
       );
   }
   ```

2. **LDAP Base DN Construction**
   ```java
   private String buildCrlBaseDn(String countryCode) {
       return String.format(
           "o=crl,c=%s,dc=data,dc=download,dc=pkd,%s",
           countryCode,
           ldapBaseDn
       );
   }
   ```

3. **LDAP Search Execution**
   ```java
   SearchResult result = ldapConnection.search(
       baseDn,
       SearchScope.SUB,
       filter,
       "certificateRevocationList;binary"
   );
   ```

4. **CRL Binary Parsing**
   ```java
   byte[] crlBytes = result.getAttributeValueBytes("certificateRevocationList;binary");
   CertificateFactory cf = CertificateFactory.getInstance("X.509");
   X509CRL crl = (X509CRL) cf.generateCRL(new ByteArrayInputStream(crlBytes));
   ```

5. **Error Handling**
   - LDAPException → InfrastructureException
   - No results → Optional.empty()
   - CRL parsing error → InfrastructureException

**Acceptance Criteria**:
- [ ] Adapter implements CrlLdapPort interface
- [ ] RFC 4515 filter escaping applied
- [ ] LDAP search uses correct DN structure
- [ ] CRL binary attribute parsed correctly
- [ ] Returns Optional.empty() when CRL not found
- [ ] Throws InfrastructureException on LDAP errors
- [ ] Logs appropriate messages (info, error)

---

### 1.3 Configuration

**File**: `src/main/resources/application.properties`

```properties
# CRL LDAP Configuration (reuse existing LDAP settings)
ldap.pkd.crl.base-dn=o=crl,c=%s,dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
```

**Acceptance Criteria**:
- [ ] Configuration properties added
- [ ] Reuses existing LDAP connection settings

---

## 📦 Task 2: CRL Verification Service Implementation

**Priority**: High
**Estimated Effort**: 4-5 hours
**Dependencies**: Task 1

### 2.1 Create CrlCheckResult Value Object

**File**: `src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/model/CrlCheckResult.java`

**Design**:
```java
package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDateTime;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrlCheckResult {

    @Enumerated(EnumType.STRING)
    private CrlCheckStatus status;

    private LocalDateTime revocationDate;
    private String revocationReason;
    private String errorMessage;

    public enum CrlCheckStatus {
        VALID,              // Certificate not revoked
        REVOKED,            // Certificate is revoked
        CRL_UNAVAILABLE,    // CRL not found in LDAP
        CRL_EXPIRED,        // CRL has expired
        CRL_INVALID         // CRL signature invalid
    }

    // Static factory methods
    public static CrlCheckResult valid() {
        CrlCheckResult result = new CrlCheckResult();
        result.status = CrlCheckStatus.VALID;
        return result;
    }

    public static CrlCheckResult revoked(LocalDateTime revocationDate, String reason) {
        CrlCheckResult result = new CrlCheckResult();
        result.status = CrlCheckStatus.REVOKED;
        result.revocationDate = revocationDate;
        result.revocationReason = reason;
        return result;
    }

    public static CrlCheckResult unavailable(String message) {
        CrlCheckResult result = new CrlCheckResult();
        result.status = CrlCheckStatus.CRL_UNAVAILABLE;
        result.errorMessage = message;
        return result;
    }

    public static CrlCheckResult expired(String message) {
        CrlCheckResult result = new CrlCheckResult();
        result.status = CrlCheckStatus.CRL_EXPIRED;
        result.errorMessage = message;
        return result;
    }

    public static CrlCheckResult invalid(String message) {
        CrlCheckResult result = new CrlCheckResult();
        result.status = CrlCheckStatus.CRL_INVALID;
        result.errorMessage = message;
        return result;
    }

    public boolean isValid() {
        return status == CrlCheckStatus.VALID;
    }

    public boolean isRevoked() {
        return status == CrlCheckStatus.REVOKED;
    }
}
```

**Acceptance Criteria**:
- [ ] Value Object follows DDD patterns
- [ ] @Embeddable for JPA persistence
- [ ] Protected no-args constructor
- [ ] Static factory methods for each status
- [ ] Enum for status types
- [ ] Helper methods (isValid, isRevoked)

---

### 2.2 Create CRL Verification Service

**File**: `src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/service/CrlVerificationService.java`

**Implementation**:

```java
package com.smartcoreinc.localpkd.passiveauthentication.domain.service;

import com.smartcoreinc.localpkd.passiveauthentication.domain.model.CrlCheckResult;
import com.smartcoreinc.localpkd.shared.exception.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Domain Service for CRL verification according to RFC 5280.
 */
@Service
@Slf4j
public class CrlVerificationService {

    /**
     * Verifies certificate against CRL.
     *
     * Steps (RFC 5280):
     * 1. Verify CRL signature using issuer certificate
     * 2. Validate CRL freshness (thisUpdate, nextUpdate)
     * 3. Check if certificate serial number is in revoked list
     *
     * @param certificate Certificate to check (DSC)
     * @param crl CRL to check against
     * @param issuerCert CRL issuer certificate (CSCA)
     * @return CrlCheckResult
     */
    public CrlCheckResult verifyCertificate(
            X509Certificate certificate,
            X509CRL crl,
            X509Certificate issuerCert) {

        try {
            // Step 1: Verify CRL signature
            crl.verify(issuerCert.getPublicKey());
            log.debug("CRL signature verified successfully");

            // Step 2: Verify CRL issuer matches CSCA subject
            String crlIssuer = crl.getIssuerX500Principal().getName();
            String cscaSubject = issuerCert.getSubjectX500Principal().getName();

            if (!crlIssuer.equals(cscaSubject)) {
                return CrlCheckResult.invalid(
                    String.format("CRL issuer (%s) does not match CSCA subject (%s)",
                        crlIssuer, cscaSubject)
                );
            }

            // Step 3: Check CRL freshness
            CrlCheckResult freshnessResult = validateCrlFreshness(crl);
            if (!freshnessResult.isValid()) {
                return freshnessResult;
            }

            // Step 4: Check certificate revocation
            X509CRLEntry revokedEntry = crl.getRevokedCertificate(certificate.getSerialNumber());

            if (revokedEntry != null) {
                LocalDateTime revocationDate = revokedEntry.getRevocationDate()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

                String reason = getCrlReason(revokedEntry);

                log.warn("Certificate {} has been revoked on {}. Reason: {}",
                    certificate.getSerialNumber(), revocationDate, reason);

                return CrlCheckResult.revoked(revocationDate, reason);
            }

            log.info("Certificate {} is not revoked", certificate.getSerialNumber());
            return CrlCheckResult.valid();

        } catch (Exception e) {
            log.error("CRL verification failed: {}", e.getMessage());
            return CrlCheckResult.invalid("CRL verification failed: " + e.getMessage());
        }
    }

    private CrlCheckResult validateCrlFreshness(X509CRL crl) {
        Date now = new Date();
        Date thisUpdate = crl.getThisUpdate();
        Date nextUpdate = crl.getNextUpdate();

        if (now.before(thisUpdate)) {
            return CrlCheckResult.invalid(
                String.format("CRL not yet valid. thisUpdate=%s, now=%s", thisUpdate, now)
            );
        }

        if (nextUpdate != null && now.after(nextUpdate)) {
            return CrlCheckResult.expired(
                String.format("CRL has expired. nextUpdate=%s, now=%s", nextUpdate, now)
            );
        }

        return CrlCheckResult.valid();
    }

    private String getCrlReason(X509CRLEntry entry) {
        if (entry.hasExtensions()) {
            try {
                byte[] reasonCode = entry.getExtensionValue("2.5.29.21"); // CRL Reason Code OID
                if (reasonCode != null) {
                    return parseCrlReasonCode(reasonCode);
                }
            } catch (Exception e) {
                log.debug("Failed to parse CRL reason code: {}", e.getMessage());
            }
        }
        return "UNSPECIFIED";
    }

    private String parseCrlReasonCode(byte[] reasonCode) {
        // RFC 5280 CRL Reason Codes
        String[] reasons = {
            "UNSPECIFIED",
            "KEY_COMPROMISE",
            "CA_COMPROMISE",
            "AFFILIATION_CHANGED",
            "SUPERSEDED",
            "CESSATION_OF_OPERATION",
            "CERTIFICATE_HOLD",
            "UNUSED",
            "REMOVE_FROM_CRL",
            "PRIVILEGE_WITHDRAWN",
            "AA_COMPROMISE"
        };

        // Parse ASN.1 ENUMERATED value
        // This is simplified - production code should use proper ASN.1 parser
        int code = reasonCode[reasonCode.length - 1] & 0xFF;

        if (code >= 0 && code < reasons.length) {
            return reasons[code];
        }

        return "UNSPECIFIED";
    }
}
```

**Acceptance Criteria**:
- [ ] Domain Service with proper annotations
- [ ] Verifies CRL signature using CSCA public key
- [ ] Validates CRL issuer matches CSCA subject
- [ ] Checks CRL freshness (thisUpdate, nextUpdate)
- [ ] Checks certificate serial number against CRL
- [ ] Extracts revocation reason from CRL extensions
- [ ] Returns appropriate CrlCheckResult for each scenario
- [ ] Comprehensive logging (debug, info, warn, error)
- [ ] Exception handling with meaningful messages

---

## 📦 Task 3: CRL Caching Strategy Implementation

**Priority**: Medium
**Estimated Effort**: 3-4 hours
**Dependencies**: Task 1, Task 2

### 3.1 Create CRL Cache Service

**File**: `src/main/java/com/smartcoreinc/localpkd/passiveauthentication/infrastructure/cache/CrlCacheService.java`

**Design**:

```java
package com.smartcoreinc.localpkd.passiveauthentication.infrastructure.cache;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import com.smartcoreinc.localpkd.passiveauthentication.domain.port.CrlLdapPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for caching CRLs with two-tier strategy:
 * 1. In-memory cache (ConcurrentHashMap) - fast access
 * 2. Database cache - persistence across restarts
 *
 * Cache validity: until CRL nextUpdate time
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CrlCacheService {

    private final CrlLdapPort crlLdapPort;
    private final CertificateRevocationListRepository crlRepository;

    // In-memory cache: key = "{cscaSubjectDn}:{countryCode}"
    private final Map<String, CachedCrl> crlCache = new ConcurrentHashMap<>();

    /**
     * Retrieves CRL with caching strategy.
     *
     * Lookup order:
     * 1. In-memory cache (if not expired)
     * 2. Database cache (if not expired)
     * 3. LDAP directory (fresh fetch)
     *
     * @param cscaSubjectDn CSCA Subject DN
     * @param countryCode Country code
     * @return X509CRL or empty if not found
     */
    public Optional<X509CRL> getCrl(String cscaSubjectDn, String countryCode) {
        String cacheKey = buildCacheKey(cscaSubjectDn, countryCode);

        // 1. Check in-memory cache
        CachedCrl cached = crlCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("CRL cache hit (in-memory): {}", cacheKey);
            return Optional.of(cached.getCrl());
        }

        // 2. Check database cache
        Optional<CertificateRevocationList> dbCrl = findLatestCrlInDb(cscaSubjectDn, countryCode);
        if (dbCrl.isPresent() && !isCrlExpired(dbCrl.get())) {
            log.debug("CRL cache hit (database): {}", cacheKey);
            X509CRL crl = parseCrl(dbCrl.get().getEncoded());
            updateInMemoryCache(cacheKey, crl);
            return Optional.of(crl);
        }

        // 3. Fetch from LDAP
        log.debug("CRL cache miss, fetching from LDAP: {}", cacheKey);
        Optional<X509CRL> freshCrl = crlLdapPort.findCrlByCsca(cscaSubjectDn, countryCode);

        if (freshCrl.isPresent()) {
            // Update both caches
            updateCaches(cacheKey, freshCrl.get(), cscaSubjectDn, countryCode);
        }

        return freshCrl;
    }

    private String buildCacheKey(String cscaSubjectDn, String countryCode) {
        return String.format("%s:%s", cscaSubjectDn, countryCode);
    }

    private Optional<CertificateRevocationList> findLatestCrlInDb(
            String cscaSubjectDn, String countryCode) {
        // Find latest CRL by thisUpdate date
        return crlRepository.findTopByIssuerNameAndCountryCodeOrderByThisUpdateDesc(
            cscaSubjectDn, countryCode
        );
    }

    private boolean isCrlExpired(CertificateRevocationList crl) {
        if (crl.getNextUpdate() == null) {
            return false; // No expiration
        }
        return new Date().after(crl.getNextUpdate());
    }

    private X509CRL parseCrl(byte[] crlBytes) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509CRL) cf.generateCRL(new ByteArrayInputStream(crlBytes));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CRL from database", e);
        }
    }

    private void updateInMemoryCache(String cacheKey, X509CRL crl) {
        Date nextUpdate = crl.getNextUpdate();
        crlCache.put(cacheKey, new CachedCrl(crl, nextUpdate));
        log.debug("Updated in-memory cache: {}", cacheKey);
    }

    private void updateCaches(String cacheKey, X509CRL crl, String cscaSubjectDn, String countryCode) {
        // Update in-memory cache
        updateInMemoryCache(cacheKey, crl);

        // Update database cache
        try {
            CertificateRevocationList dbCrl = new CertificateRevocationList(
                cscaSubjectDn,
                countryCode,
                crl.getThisUpdate(),
                crl.getNextUpdate(),
                crl.getEncoded()
            );
            crlRepository.save(dbCrl);
            log.debug("Updated database cache: {}", cacheKey);
        } catch (Exception e) {
            log.error("Failed to save CRL to database: {}", e.getMessage());
            // Continue - in-memory cache is still valid
        }
    }

    /**
     * Evicts expired entries from in-memory cache.
     * Should be called periodically (e.g., via @Scheduled).
     */
    public void evictExpiredEntries() {
        int evicted = 0;
        for (Map.Entry<String, CachedCrl> entry : crlCache.entrySet()) {
            if (entry.getValue().isExpired()) {
                crlCache.remove(entry.getKey());
                evicted++;
            }
        }
        if (evicted > 0) {
            log.info("Evicted {} expired CRL cache entries", evicted);
        }
    }

    /**
     * Cached CRL wrapper with expiration.
     */
    private static class CachedCrl {
        private final X509CRL crl;
        private final Date expiresAt;

        public CachedCrl(X509CRL crl, Date expiresAt) {
            this.crl = crl;
            this.expiresAt = expiresAt;
        }

        public X509CRL getCrl() {
            return crl;
        }

        public boolean isExpired() {
            if (expiresAt == null) {
                return false; // No expiration
            }
            return new Date().after(expiresAt);
        }
    }
}
```

**Acceptance Criteria**:
- [ ] Two-tier caching (in-memory + database)
- [ ] Cache key format: "{cscaSubjectDn}:{countryCode}"
- [ ] In-memory cache uses ConcurrentHashMap
- [ ] Database cache uses existing CertificateRevocationListRepository
- [ ] Cache validity based on CRL nextUpdate time
- [ ] Expired entry eviction (manual trigger)
- [ ] Comprehensive logging for cache hits/misses
- [ ] Thread-safe implementation

---

### 3.2 Add Repository Method

**File**: `src/main/java/com/smartcoreinc/localpkd/certificatevalidation/domain/repository/CertificateRevocationListRepository.java`

**Add Method**:
```java
Optional<CertificateRevocationList> findTopByIssuerNameAndCountryCodeOrderByThisUpdateDesc(
    String issuerName,
    String countryCode
);
```

**Acceptance Criteria**:
- [ ] Method added to repository interface
- [ ] Returns latest CRL by thisUpdate date
- [ ] Query derived from method name (Spring Data JPA)

---

## 📦 Task 4: Integration into PassiveAuthenticationService

**Priority**: High
**Estimated Effort**: 2-3 hours
**Dependencies**: Task 1, Task 2, Task 3

### 4.1 Modify PassiveAuthenticationService

**File**: `src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/service/PassiveAuthenticationService.java`

**Changes**:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PassiveAuthenticationService {

    private final SodParserPort sodParserPort;
    private final LdapCscaPort ldapCscaPort;
    private final CrlCacheService crlCacheService;  // NEW
    private final CrlVerificationService crlVerificationService;  // NEW

    public PassportVerificationDetails verifyPassport(
            byte[] sodBytes,
            Map<Integer, byte[]> dataGroups,
            String countryCode) {

        // ... (existing trust chain verification)

        // ✅ NEW: Step 7 - CRL Check
        CrlCheckResult crlResult = performCrlCheck(dscCert, cscaCert, countryCode);

        if (crlResult.isRevoked()) {
            throw new DomainException(
                "CERTIFICATE_REVOKED",
                String.format("DSC has been revoked on %s. Reason: %s",
                    crlResult.getRevocationDate(), crlResult.getRevocationReason())
            );
        }

        // Log warning if CRL unavailable but continue
        if (crlResult.getStatus() == CrlCheckStatus.CRL_UNAVAILABLE) {
            log.warn("CRL not available for CSCA: {}. Continuing verification without CRL check.",
                cscaCert.getSubjectX500Principal().getName());
        }

        // ... (continue with SOD verification)

        return PassportVerificationDetails.builder()
            .trustChainValid(true)
            .crlCheckResult(crlResult)  // NEW
            .sodSignatureValid(true)
            // ... (other fields)
            .build();
    }

    private CrlCheckResult performCrlCheck(
            X509Certificate dscCert,
            X509Certificate cscaCert,
            String countryCode) {

        String cscaSubjectDn = cscaCert.getSubjectX500Principal().getName();

        // Retrieve CRL (with caching)
        Optional<X509CRL> crlOpt = crlCacheService.getCrl(cscaSubjectDn, countryCode);

        if (crlOpt.isEmpty()) {
            log.warn("CRL not found for CSCA: {}", cscaSubjectDn);
            return CrlCheckResult.unavailable(
                "CRL not found for CSCA: " + cscaSubjectDn
            );
        }

        // Verify certificate against CRL
        return crlVerificationService.verifyCertificate(dscCert, crlOpt.get(), cscaCert);
    }
}
```

**Acceptance Criteria**:
- [ ] CrlCacheService injected
- [ ] CrlVerificationService injected
- [ ] CRL check performed after trust chain verification (Step 7)
- [ ] Throws exception if DSC is revoked
- [ ] Logs warning if CRL unavailable but continues
- [ ] CrlCheckResult included in response
- [ ] Does not break existing tests

---

### 4.2 Update PassportVerificationDetails

**File**: `src/main/java/com/smartcoreinc/localpkd/passiveauthentication/domain/model/PassportVerificationDetails.java`

**Add Field**:
```java
@Embedded
@AttributeOverrides({
    @AttributeOverride(name = "status", column = @Column(name = "crl_check_status")),
    @AttributeOverride(name = "revocationDate", column = @Column(name = "crl_revocation_date")),
    @AttributeOverride(name = "revocationReason", column = @Column(name = "crl_revocation_reason")),
    @AttributeOverride(name = "errorMessage", column = @Column(name = "crl_error_message"))
})
private CrlCheckResult crlCheckResult;
```

**Acceptance Criteria**:
- [ ] CrlCheckResult field added
- [ ] @Embedded annotation
- [ ] @AttributeOverrides for column names
- [ ] Builder pattern updated

---

### 4.3 Update PassiveAuthenticationResponse

**File**: `src/main/java/com/smartcoreinc/localpkd/passiveauthentication/application/response/PassiveAuthenticationResponse.java`

**Add Field**:
```java
private CrlCheckStatus crlCheckStatus;
private LocalDateTime crlRevocationDate;
private String crlRevocationReason;
```

**Acceptance Criteria**:
- [ ] CRL check fields added to response
- [ ] Mapper updated (PassiveAuthenticationMapper)
- [ ] OpenAPI/Swagger docs updated

---

## 📦 Task 5: Testing

**Priority**: High
**Estimated Effort**: 4-5 hours
**Dependencies**: All previous tasks

### 5.1 Unit Tests - CrlVerificationService

**File**: `src/test/java/com/smartcoreinc/localpkd/passiveauthentication/domain/service/CrlVerificationServiceTest.java`

**Test Scenarios**:

1. ✅ **Valid Certificate (Not Revoked)**
   - Given: Valid DSC, fresh CRL, DSC not in revoked list
   - When: verifyCertificate()
   - Then: CrlCheckResult.VALID

2. ✅ **Revoked Certificate**
   - Given: Valid DSC, fresh CRL, DSC in revoked list
   - When: verifyCertificate()
   - Then: CrlCheckResult.REVOKED with date and reason

3. ✅ **Expired CRL**
   - Given: Valid DSC, expired CRL (nextUpdate < now)
   - When: verifyCertificate()
   - Then: CrlCheckResult.CRL_EXPIRED

4. ✅ **Invalid CRL Signature**
   - Given: Valid DSC, CRL with invalid signature
   - When: verifyCertificate()
   - Then: CrlCheckResult.CRL_INVALID

5. ✅ **CRL Issuer Mismatch**
   - Given: Valid DSC, CRL issued by different CSCA
   - When: verifyCertificate()
   - Then: CrlCheckResult.CRL_INVALID

**Acceptance Criteria**:
- [ ] All 5 test scenarios pass
- [ ] Test uses mock CRL and certificates
- [ ] Assertions on CrlCheckResult fields
- [ ] Test coverage > 90%

---

### 5.2 Unit Tests - CrlCacheService

**File**: `src/test/java/com/smartcoreinc/localpkd/passiveauthentication/infrastructure/cache/CrlCacheServiceTest.java`

**Test Scenarios**:

1. ✅ **Cache Miss - Fetch from LDAP**
   - Given: Empty cache
   - When: getCrl()
   - Then: Fetches from LDAP, updates both caches

2. ✅ **Cache Hit - In-Memory**
   - Given: Valid CRL in memory cache
   - When: getCrl()
   - Then: Returns from memory, no LDAP call

3. ✅ **Cache Hit - Database**
   - Given: Valid CRL in database cache, memory cache empty
   - When: getCrl()
   - Then: Returns from database, updates memory cache

4. ✅ **Expired Cache Entry**
   - Given: Expired CRL in cache
   - When: getCrl()
   - Then: Fetches fresh CRL from LDAP

5. ✅ **CRL Not Found**
   - Given: CRL not in LDAP
   - When: getCrl()
   - Then: Returns Optional.empty()

**Acceptance Criteria**:
- [ ] All 5 test scenarios pass
- [ ] Mocks CrlLdapPort and CertificateRevocationListRepository
- [ ] Verifies cache update logic
- [ ] Test coverage > 85%

---

### 5.3 Integration Tests - UnboundIdCrlLdapAdapter

**File**: `src/test/java/com/smartcoreinc/localpkd/passiveauthentication/infrastructure/adapter/UnboundIdCrlLdapAdapterTest.java`

**Test Scenarios**:

1. ✅ **Find CRL by CSCA DN - Success**
   - Given: CRL exists in LDAP
   - When: findCrlByCsca()
   - Then: Returns X509CRL

2. ✅ **Find CRL by CSCA DN - Not Found**
   - Given: CRL does not exist in LDAP
   - When: findCrlByCsca()
   - Then: Returns Optional.empty()

3. ✅ **RFC 4515 Filter Escaping**
   - Given: CSCA DN with special characters
   - When: findCrlByCsca()
   - Then: LDAP filter properly escaped

**Acceptance Criteria**:
- [ ] All 3 test scenarios pass
- [ ] Uses @SpringBootTest with LDAP connection
- [ ] Test data seeded in LDAP (test CRL)
- [ ] Assertions on X509CRL properties

---

### 5.4 Integration Tests - Complete PA + CRL Flow

**File**: `src/test/java/com/smartcoreinc/localpkd/passiveauthentication/application/usecase/PerformPassiveAuthenticationUseCaseWithCrlTest.java`

**Test Scenarios**:

1. ✅ **PA with Valid DSC (Not Revoked)**
   - Given: Valid SOD, valid DSC, CRL available, DSC not revoked
   - When: execute()
   - Then: PA succeeds, crlCheckStatus = VALID

2. ✅ **PA with Revoked DSC**
   - Given: Valid SOD, revoked DSC, CRL available
   - When: execute()
   - Then: PA fails with CertificateRevokedException

3. ✅ **PA with CRL Unavailable**
   - Given: Valid SOD, valid DSC, CRL not available
   - When: execute()
   - Then: PA succeeds, crlCheckStatus = CRL_UNAVAILABLE

**Acceptance Criteria**:
- [ ] All 3 test scenarios pass
- [ ] Uses @SpringBootTest
- [ ] Test data seeded (SOD, certificates, CRL)
- [ ] Assertions on PassiveAuthenticationResponse.crlCheckStatus

---

## 📦 Task 6: Documentation and Finalization

**Priority**: Medium
**Estimated Effort**: 2 hours
**Dependencies**: All previous tasks

### 6.1 Update OpenAPI/Swagger Documentation

**File**: `src/main/java/com/smartcoreinc/localpkd/passiveauthentication/infrastructure/web/PassiveAuthenticationController.java`

**Add Schema Examples**:
```java
@Schema(
    description = "CRL check status",
    example = "VALID",
    allowableValues = {"VALID", "REVOKED", "CRL_UNAVAILABLE", "CRL_EXPIRED", "CRL_INVALID"}
)
private String crlCheckStatus;

@Schema(
    description = "Revocation date if DSC is revoked",
    example = "2025-01-15T10:30:00"
)
private LocalDateTime crlRevocationDate;
```

**Acceptance Criteria**:
- [ ] CRL fields documented in OpenAPI schema
- [ ] Example values provided
- [ ] Swagger UI displays CRL fields

---

### 6.2 Update CLAUDE.md

**File**: `CLAUDE.md`

**Update Section**: "Next Phase: Passive Authentication Phase 4.12"

**Changes**:
- [ ] Mark all Phase 4.12 items as completed
- [ ] Add link to session report
- [ ] Update "Current Phase" to Phase 5

---

### 6.3 Create Session Report

**File**: `docs/SESSION_2025-12-19_PA_PHASE_4_12_CRL_CHECKING.md`

**Sections**:
1. Overview
2. Implementation Summary
3. Architecture Changes
4. Code Changes (file list with LOC)
5. Test Results (pass/fail counts)
6. Standards Compliance
7. Next Steps

**Acceptance Criteria**:
- [ ] Session report created
- [ ] All sections completed
- [ ] Referenced in CLAUDE.md

---

## 📊 Estimated Timeline

| Task | Estimated Hours | Status |
|------|----------------|--------|
| Task 1: CRL LDAP Adapter | 3-4 hours | Pending |
| Task 2: CRL Verification Service | 4-5 hours | Pending |
| Task 3: CRL Caching Strategy | 3-4 hours | Pending |
| Task 4: Integration into PA Service | 2-3 hours | Pending |
| Task 5: Testing | 4-5 hours | Pending |
| Task 6: Documentation | 2 hours | Pending |
| **Total** | **18-23 hours** | **0% Complete** |

---

## 🔍 Quality Checklist

Before marking Phase 4.12 as complete:

- [ ] All tests pass (unit + integration)
- [ ] Test coverage > 85%
- [ ] ICAO Doc 9303 Part 11 & 12 compliance verified
- [ ] RFC 5280 CRL profile compliance verified
- [ ] RFC 4515 LDAP filter escaping implemented
- [ ] Code review completed
- [ ] OpenAPI/Swagger docs updated
- [ ] CLAUDE.md updated
- [ ] Session report created
- [ ] No compiler warnings
- [ ] Logging is appropriate (info, debug, error)
- [ ] Exception handling is comprehensive
- [ ] Performance is acceptable (CRL caching works)

---

## 📚 References

- [ICAO_9303_PA_CRL_STANDARD.md](ICAO_9303_PA_CRL_STANDARD.md) - **Must Read**
- [RFC 5280 - X.509 PKI Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280)
- [RFC 4515 - LDAP Search Filter String Representation](https://www.rfc-editor.org/rfc/rfc4515)
- [ICAO Doc 9303 Part 11](https://www.icao.int/publications/doc-series/doc-9303)
- [ICAO Doc 9303 Part 12](https://www.icao.int/publications/doc-series/doc-9303)

---

## 📝 Notes

- CRL check is performed **after trust chain verification** (Step 7)
- CRL check failure (REVOKED) **blocks PA verification**
- CRL unavailable (CRL_UNAVAILABLE) **allows PA to continue** with warning
- CRL caching is **critical for performance** (reduces LDAP calls)
- All new code must follow existing **DDD patterns** (Port/Adapter, Domain Service, Value Object)

---

**Document Version**: 1.0
**Last Updated**: 2025-12-19
**Status**: Planning Complete - Ready for Implementation
