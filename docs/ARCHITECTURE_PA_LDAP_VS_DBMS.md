# PA Module Architecture Decision: LDAP vs DBMS

**Date**: 2025-12-19
**Phase**: 4.11.4
**Status**: Implemented

---

## Decision

PA (Passive Authentication) Module uses **LDAP only** for CSCA lookup, not DBMS.

---

## Rationale

### ICAO 9303 Standard Compliance

- ICAO 9303 Part 11 requires CSCA lookup from PKD (Public Key Directory)
- PKD is implemented as OpenLDAP server (LDAP protocol)
- Real-time verification needs live certificate data from authoritative source
- LDAP is the de facto standard for certificate directory services

### Bounded Context Separation (DDD)

**PKD Upload Module** (DBMS-based):
- Purpose: File upload history and statistics
- Database: PostgreSQL `certificate` table
- Use case: Audit trails, upload tracking, analytics
- Data: Parsed certificates with validation metadata

**PA Module** (LDAP-based):
- Purpose: Real-time passport verification
- Directory: OpenLDAP server
- Use case: Certificate chain validation during PA
- Data: Live X.509 certificates from issuing countries

**Benefit**: Clear separation of concerns, no data duplication logic needed

### Data Flow

```
PKD Upload Flow:
  File Upload → Parse → Validate → LDAP Store → DBMS Statistics
                                      ↓
                                   (CSCA stored in LDAP)

PA Verification Flow:
  Client Request → Extract DSC from SOD
                → LDAP Lookup (CSCA) ← Uses LDAP only
                → Verify Trust Chain
                → Verify SOD Signature
                → Verify Data Group Hashes
                → Return Result
```

**Key Point**: PA verification uses LDAP as the single source of truth for CSCAs.

---

## Implementation

### Port Interface

```java
// passiveauthentication/domain/port/LdapCscaRepository.java
public interface LdapCscaRepository {
    Optional<X509Certificate> findBySubjectDn(String subjectDn);
}
```

### Adapter Implementation

```java
// passiveauthentication/infrastructure/adapter/UnboundIdLdapCscaAdapter.java
@Component
public class UnboundIdLdapCscaAdapter implements LdapCscaRepository {
    // LDAP connection using UnboundID SDK
    // RFC 4514 DN escaping
    // userCertificate;binary attribute retrieval
}
```

### UseCase Integration

```java
// passiveauthentication/application/usecase/PerformPassiveAuthenticationUseCase.java
@Service
public class PerformPassiveAuthenticationUseCase {
    private final LdapCscaRepository ldapCscaRepository; // LDAP only
    // CertificateRepository removed (was DBMS)

    private X509Certificate retrieveCscaFromLdap(String issuerDn) {
        // Try exact match
        // Try RFC 2253 normalized
        // Try canonical format
        // Throw CSCA_NOT_FOUND if not in LDAP
    }
}
```

---

## Alternatives Considered

### ❌ Alternative 1: DBMS `certificate` table

**Rejected Reasons**:
- Violates DDD bounded context separation
- Requires data synchronization between LDAP and DBMS
- DBMS is for upload history, not real-time verification
- Adds unnecessary complexity

### ❌ Alternative 2: Dual lookup (LDAP + DBMS fallback)

**Rejected Reasons**:
- Unnecessary complexity
- Which source is authoritative? (conflicts possible)
- Slower performance (two lookups)
- LDAP should be the only source for PA

### ✅ Alternative 3: LDAP only (Selected)

**Advantages**:
- Follows ICAO 9303 Part 11 standard
- Clear bounded context separation
- Single source of truth for certificates
- Simpler codebase (no sync logic)
- Better performance (direct LDAP lookup)

---

## LDAP Search Details

### Search Criteria

```
Base DN: dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
Filter: (&(objectClass=pkdDownload)(o=csca)(cn={escaped-dn}))
Attribute: userCertificate;binary
Scope: SUB
```

### DN Escaping (RFC 4514)

```
Original DN: CN=CSCA003,OU=MOFA,O=Government,C=KR
Escaped DN:  CN\3DCSCA003\2COU\3DMOFA\2CO\3DGovernment\2CC\3DKR

Escape Rules:
- , (comma)  → \2C
- = (equals) → \3D
- + (plus)   → \2B
- " (quote)  → \22
- \ (backslash) → \5C
```

### DN Format Handling

PA tries multiple DN formats because:
1. **DSC Issuer DN** (from SOD): RFC 2253 format (CN=...,OU=...,O=...,C=...)
2. **CSCA Subject DN** (in LDAP): May be in different format/order
3. **X.500 Standard**: Both represent the same DN semantically

**Lookup Strategy**:
1. Try exact match first
2. Try RFC 2253 normalized
3. Try canonical format (case-insensitive, sorted)
4. Throw `CSCA_NOT_FOUND` exception

---

## Configuration

### Production (application.properties)

```properties
app.ldap.urls=ldap://192.168.100.10:389
app.ldap.base=dc=ldap,dc=smartcoreinc,dc=com
app.ldap.username=cn=admin,dc=ldap,dc=smartcoreinc,dc=com
app.ldap.password=core
```

### Test (application-test.properties)

```properties
# Same LDAP server for integration tests
app.ldap.urls=ldap://192.168.100.10:389
app.ldap.base=dc=ldap,dc=smartcoreinc,dc=com
app.ldap.username=cn=admin,dc=ldap,dc=smartcoreinc,dc=com
app.ldap.password=core
```

---

## Benefits

### 1. Standards Compliance
- ICAO 9303 Part 11 standard implementation
- PKD (LDAP) as authoritative certificate source

### 2. Architecture Clarity
- Clear bounded context separation
- No data sync issues between LDAP and DBMS

### 3. Performance
- Direct LDAP lookup (no DBMS query overhead)
- Connection pooling for efficiency

### 4. Maintainability
- Single source of truth for certificates
- Simpler codebase (no dual-lookup logic)

### 5. Operational
- Real-time certificate updates in LDAP
- No stale certificate data in DBMS

---

## Related Documents

- [CLAUDE.md - ICAO 9303 PA Workflow](../CLAUDE.md#icao-9303-passive-authentication-workflow)
- [Phase 4.11.3 Session Report](./SESSION_2025-12-19_PA_PHASE_4_11_3_SOD_DSC_EXTRACTION.md)
- [Phase 4.11.4 TODO](./TODO_PHASE_4_11_4_LDAP_CSCA_LOOKUP.md)
- [PKD Upload Module Architecture](./MASTER_LIST_LDAP_STORAGE_ANALYSIS.md)

---

## Implementation Files

| Component | File | LOC |
|-----------|------|-----|
| Port Interface | `LdapCscaRepository.java` | 30 |
| Adapter | `UnboundIdLdapCscaAdapter.java` | 230 |
| UseCase | `PerformPassiveAuthenticationUseCase.java` | 60 (refactored) |
| Config | `application-test.properties` | 7 |

**Total**: ~327 LOC

---

## Summary

This architectural decision aligns PA Module with ICAO 9303 standards, establishes clear bounded contexts, and simplifies the codebase by using LDAP as the single authoritative source for CSCA certificates during passive authentication.
