# LDAP Integration Module - API Reference Documentation

**Version**: 1.0.0
**Last Updated**: 2025-10-25
**Author**: SmartCore Inc.
**Status**: COMPLETE

---

## Table of Contents

1. [Module Overview](#module-overview)
2. [Architecture](#architecture)
3. [Core Components](#core-components)
4. [Domain Models](#domain-models)
5. [Service Ports (Interfaces)](#service-ports-interfaces)
6. [Adapter Implementations](#adapter-implementations)
7. [Configuration](#configuration)
8. [Usage Patterns](#usage-patterns)
9. [Error Handling](#error-handling)
10. [Testing](#testing)

---

## Module Overview

### Purpose

The LDAP Integration Module provides a Hexagonal Architecture-based abstraction for interacting with OpenLDAP directory services. It handles:

- **Certificate & CRL Storage**: Upload and manage CSCA, DSC, and CRL entries
- **Directory Queries**: Search and retrieve LDAP entries with advanced filtering
- **Synchronization**: Batch processing and regular sync operations
- **Connection Management**: Thread-safe connection pooling with health checks

### Package Structure

```
com.smartcoreinc.localpkd.ldapintegration
├── domain/                    # Domain Layer (Business Logic)
│   ├── model/                # Domain Models
│   │   ├── DistinguishedName.java
│   │   ├── LdapAttributes.java
│   │   ├── LdapCertificateEntry.java
│   │   ├── LdapCrlEntry.java
│   │   ├── LdapSearchFilter.java
│   │   └── LdapEntryMapper.java
│   └── port/                 # Port Interfaces
│       ├── LdapUploadService.java
│       ├── LdapQueryService.java
│       └── LdapSyncService.java
├── infrastructure/           # Infrastructure Layer
│   ├── config/              # Configuration
│   │   ├── LdapProperties.java
│   │   └── LdapConfiguration.java
│   └── adapter/             # Adapter Implementations
│       ├── SpringLdapUploadAdapter.java
│       ├── SpringLdapQueryAdapter.java
│       └── SpringLdapSyncAdapter.java
└── integration/             # Integration Tests
    └── LdapIntegrationTestFixture.java
```

### Key Features

- ✅ **Type-Safe**: Value Objects for LDAP DN, attributes, filters
- ✅ **Hexagonal Architecture**: Clean separation via Ports and Adapters
- ✅ **Connection Pooling**: HikariCP integration with configurable pool sizes
- ✅ **Health Checks**: LDAP connection health monitoring
- ✅ **Thread-Safe**: Concurrent access to shared LDAP resources
- ✅ **Comprehensive Logging**: SLF4J integration for debugging
- ✅ **Testable**: Embedded LDAP test server support

---

## Architecture

### Hexagonal Architecture (Ports & Adapters)

```
┌─────────────────────────────────────────────────────┐
│                  Spring Boot Application             │
│        (File Upload, Parsing, Validation)            │
└────────────────┬────────────────────────────────────┘
                 │
         ┌───────▼─────────┐
         │ Domain Ports    │ (Domain Layer)
         │                 │
         │ - LdapUploadService
         │ - LdapQueryService
         │ - LdapSyncService
         │ - Domain Models
         └───────┬─────────┘
                 │
         ┌───────▼─────────┐
         │ Adapter         │ (Infrastructure Layer)
         │ Implementations │
         │                 │
         │ SpringLdapUploadAdapter
         │ SpringLdapQueryAdapter
         │ SpringLdapSyncAdapter
         │ LdapConfiguration
         └───────┬─────────┘
                 │
         ┌───────▼──────────────┐
         │  OpenLDAP Server      │
         │  (ldap://localhost:389)
         │                       │
         │  Directory Structure: │
         │  ├── cn=admin         │
         │  ├── ou=certificates  │
         │  │   ├── ou=csca      │
         │  │   ├── ou=dsc       │
         │  │   └── ou=ds        │
         │  ├── ou=crl           │
         │  └── ou=users         │
         └───────────────────────┘
```

### Dependency Flow

```
Domain Layer:
  - LdapUploadService (interface)
  - LdapQueryService (interface)
  - LdapSyncService (interface)
  - Domain Models (DistinguishedName, LdapAttributes, etc.)
  ↑
  │ (implements)
  │
Infrastructure Layer:
  - SpringLdapUploadAdapter
  - SpringLdapQueryAdapter
  - SpringLdapSyncAdapter
  - LdapConfiguration
  ↑
  │ (uses)
  │
External:
  - OpenLDAP Server
  - Spring LDAP (LdapTemplate)
  - HikariCP (Connection Pool)
```

---

## Core Components

### 1. Domain Models (Value Objects & Aggregates)

#### DistinguishedName

**Purpose**: Type-safe representation of LDAP Distinguished Names (DN)

**Package**: `com.smartcoreinc.localpkd.ldapintegration.domain.model`

```java
public class DistinguishedName {
    // Value
    private String value;  // e.g., "cn=admin,dc=ldap,dc=smartcoreinc,dc=com"

    // Factory Methods
    public static DistinguishedName of(String value)
    public static DistinguishedName of(String rdn, DistinguishedName parentDn)
    public static DistinguishedName fromCertificateDn(X500Principal principal)

    // Methods
    public String getValue()
    public String getRdn()                     // Get RDN component (e.g., "cn=admin")
    public DistinguishedName getParent()      // Get parent DN
    public boolean startsWith(String prefix)
    public boolean equals(Object obj)
    public int hashCode()
    public String toString()
}
```

**Usage Example**:
```java
// Create DN
DistinguishedName adminDn = DistinguishedName.of("cn=admin,dc=ldap,dc=smartcoreinc,dc=com");

// Create child DN
DistinguishedName certDn = DistinguishedName.of(
    "cn=cert-001",
    DistinguishedName.of("ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com")
);

// Get RDN
String rdn = adminDn.getRdn();  // "cn=admin"

// Get parent
DistinguishedName parent = certDn.getParent();  // "ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"
```

**Validation Rules**:
- Not null or empty
- Valid DN format (cn=value,ou=value,dc=value,...)
- Each component has valid attribute type (cn, ou, dc, etc.)

---

#### LdapAttributes

**Purpose**: Type-safe representation of LDAP entry attributes

**Package**: `com.smartcoreinc.localpkd.ldapintegration.domain.model`

```java
public class LdapAttributes {
    // Map of attribute name to list of values
    private Map<String, List<String>> attributes;

    // Factory Methods
    public static LdapAttributes empty()
    public static LdapAttributes of(Map<String, List<String>> attributes)
    public static LdapAttributesBuilder builder()

    // Methods
    public String getAttribute(String name)                    // Get single value
    public List<String> getAttributeValues(String name)       // Get all values
    public void setAttribute(String name, String value)
    public void setAttribute(String name, String... values)
    public void setAttribute(String name, List<String> values)
    public boolean hasAttribute(String name)
    public Set<String> getAttributeNames()
    public Map<String, List<String>> toMap()
    public List<ModificationItem> toModificationItems()      // For LDAP modifications
    public boolean equals(Object obj)
    public int hashCode()
    public String toString()
}
```

**Builder Pattern**:
```java
LdapAttributes attributes = LdapAttributes.builder()
    .add("objectClass", "pkiCertificate", "inetOrgPerson")
    .add("cn", "test-cert-001")
    .add("sn", "Test")
    .add("mail", "test@example.com")
    .add("certificate;binary", encodedCertBytes)
    .build();
```

**Usage Example**:
```java
// Get single attribute
String commonName = attributes.getAttribute("cn");

// Get multiple values
List<String> objectClasses = attributes.getAttributeValues("objectClass");

// Check attribute existence
if (attributes.hasAttribute("certificate;binary")) {
    // Handle certificate
}

// Update attributes
attributes.setAttribute("modifyTimestamp", LocalDateTime.now().toString());

// Get all attribute names
Set<String> names = attributes.getAttributeNames();

// Convert to LDAP modification items
List<ModificationItem> mods = attributes.toModificationItems();
```

**Standard Attributes for Certificates**:
- `objectClass`: "pkiCertificate", "inetOrgPerson", "top"
- `cn`: Common Name
- `sn`: Surname
- `mail`: Email address
- `certificate;binary`: Base64-encoded X.509 certificate
- `certificateSerialNumber`: Serial number from cert
- `certificateIssuer`: Issuer DN from cert
- `certificateNotBefore`: Validity start
- `certificateNotAfter`: Validity end
- `certificateSubjectPublicKeyAlgorithm`: Algorithm (RSA, ECDSA, etc.)

---

#### LdapCertificateEntry

**Purpose**: LDAP representation of an X.509 certificate

**Package**: `com.smartcoreinc.localpkd.ldapintegration.domain.model`

```java
public class LdapCertificateEntry {
    private DistinguishedName dn;
    private String subjectDn;           // From certificate
    private String issuerDn;            // From certificate
    private String serialNumber;
    private byte[] encodedCertificate;  // DER-encoded X.509
    private LocalDateTime notBefore;
    private LocalDateTime notAfter;
    private String certificateType;     // "CSCA", "DSC", "DS"

    // Factory Methods
    public static LdapCertificateEntry from(X509Certificate cert, DistinguishedName dn)
    public static LdapCertificateEntry from(X509Certificate cert,
                                          DistinguishedName baseDn,
                                          String certificateType)

    // Methods
    public DistinguishedName getDn()
    public String getSubjectDn()
    public String getIssuerDn()
    public String getSerialNumber()
    public byte[] getEncodedCertificate()
    public LocalDateTime getNotBefore()
    public LocalDateTime getNotAfter()
    public String getCertificateType()
    public boolean isExpired()
    public boolean isValid()              // Check expiration
    public long getDaysUntilExpiration()
    public boolean equals(Object obj)
    public int hashCode()
    public String toString()
}
```

**Usage Example**:
```java
// Create from X.509 certificate
X509Certificate x509Cert = loadCertificate("cert.der");
DistinguishedName dn = DistinguishedName.of(
    "cn=cert-001,ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"
);

LdapCertificateEntry entry = LdapCertificateEntry.from(x509Cert, dn, "CSCA");

// Check validity
if (entry.isValid()) {
    System.out.println("Certificate is valid");
    System.out.println("Days until expiration: " + entry.getDaysUntilExpiration());
} else {
    System.out.println("Certificate is expired");
}

// Get certificate details
System.out.println("Issuer: " + entry.getIssuerDn());
System.out.println("Subject: " + entry.getSubjectDn());
System.out.println("Serial: " + entry.getSerialNumber());
```

---

#### LdapCrlEntry

**Purpose**: LDAP representation of a Certificate Revocation List (CRL)

**Package**: `com.smartcoreinc.localpkd.ldapintegration.domain.model`

```java
public class LdapCrlEntry {
    private DistinguishedName dn;
    private String issuerDn;            // From CRL
    private byte[] encodedCrl;          // DER-encoded CRL
    private LocalDateTime thisUpdate;
    private LocalDateTime nextUpdate;
    private Set<BigInteger> revokedSerialNumbers;  // Revoked certificate serials

    // Factory Methods
    public static LdapCrlEntry from(X509CRL crl, DistinguishedName dn)

    // Methods
    public DistinguishedName getDn()
    public String getIssuerDn()
    public byte[] getEncodedCrl()
    public LocalDateTime getThisUpdate()
    public LocalDateTime getNextUpdate()
    public Set<BigInteger> getRevokedSerialNumbers()
    public boolean isRevokedCertificate(BigInteger serialNumber)
    public boolean isCurrentlyValid()   // Check update dates
    public long getDaysUntilNextUpdate()
    public boolean equals(Object obj)
    public int hashCode()
    public String toString()
}
```

**Usage Example**:
```java
// Create from X.509 CRL
X509CRL crl = loadCrl("crl.der");
DistinguishedName dn = DistinguishedName.of(
    "cn=crl-csca-001,ou=crl,dc=ldap,dc=smartcoreinc,dc=com"
);

LdapCrlEntry entry = LdapCrlEntry.from(crl, dn);

// Check certificate revocation
BigInteger serialToCheck = new BigInteger("12345678");
if (entry.isRevokedCertificate(serialToCheck)) {
    System.out.println("Certificate is revoked!");
} else {
    System.out.println("Certificate is not revoked");
}

// Check CRL validity
if (entry.isCurrentlyValid()) {
    System.out.println("CRL is current, next update in: " +
                      entry.getDaysUntilNextUpdate() + " days");
}
```

---

#### LdapSearchFilter

**Purpose**: Type-safe LDAP search filter builder

**Package**: `com.smartcoreinc.localpkd.ldapintegration.domain.model`

```java
public class LdapSearchFilter {
    private String filter;  // LDAP filter string

    // Factory Methods (Static Builders)
    public static LdapSearchFilter create(String filterPattern)
    public static LdapSearchFilter equals(String attributeName, String value)
    public static LdapSearchFilter contains(String attributeName, String value)
    public static LdapSearchFilter startsWith(String attributeName, String value)
    public static LdapSearchFilter endsWith(String attributeName, String value)
    public static LdapSearchFilter and(LdapSearchFilter... filters)
    public static LdapSearchFilter or(LdapSearchFilter... filters)
    public static LdapSearchFilter not(LdapSearchFilter filter)
    public static LdapSearchFilter anyAttribute()  // (objectClass=*)
    public static LdapSearchFilter ofObjectClass(String... objectClasses)

    // Methods
    public String getFilterString()
    public boolean isEmpty()
    public LdapSearchFilter and(LdapSearchFilter other)
    public LdapSearchFilter or(LdapSearchFilter other)
    public LdapSearchFilter not()
    public boolean equals(Object obj)
    public int hashCode()
    public String toString()
}
```

**Usage Examples**:
```java
// Simple equals filter
LdapSearchFilter filter1 = LdapSearchFilter.equals("cn", "admin");
// Result: (cn=admin)

// Contains filter
LdapSearchFilter filter2 = LdapSearchFilter.contains("mail", "example.com");
// Result: (mail=*example.com*)

// Composite filters
LdapSearchFilter filter3 = LdapSearchFilter.and(
    LdapSearchFilter.equals("objectClass", "pkiCertificate"),
    LdapSearchFilter.equals("certificateType", "CSCA")
);
// Result: (&(objectClass=pkiCertificate)(certificateType=CSCA))

// Complex filter
LdapSearchFilter filter4 = LdapSearchFilter.or(
    LdapSearchFilter.equals("certificateType", "CSCA"),
    LdapSearchFilter.equals("certificateType", "DSC")
);
// Result: (|(certificateType=CSCA)(certificateType=DSC))

// Not filter
LdapSearchFilter filter5 = LdapSearchFilter.not(
    LdapSearchFilter.equals("certificateStatus", "REVOKED")
);
// Result: (!(certificateStatus=REVOKED))

// Wildcard filter
LdapSearchFilter filter6 = LdapSearchFilter.startsWith("cn", "cert-");
// Result: (cn=cert-*)

// Certification type check
LdapSearchFilter filter7 = LdapSearchFilter.ofObjectClass("pkiCertificate", "inetOrgPerson");
// Result: (|(objectClass=pkiCertificate)(objectClass=inetOrgPerson))
```

**Supported LDAP Filter Operators**:
- `=` (equals)
- `~=` (approximately equals)
- `<=` (less than or equal)
- `>=` (greater than or equal)
- `&` (AND)
- `|` (OR)
- `!` (NOT)
- `*` (wildcard)
- `:` (extensible match)

---

#### LdapEntryMapper

**Purpose**: Bidirectional mapping between domain models and LDAP entries

**Package**: `com.smartcoreinc.localpkd.ldapintegration.domain.model`

```java
public class LdapEntryMapper {
    // Factory Method
    public static LdapEntryMapper create()

    // Certificate Mapping
    public LdapAttributes certificateToDomainAttributes(
        LdapCertificateEntry entry)
    public LdapCertificateEntry fromDomainAttributes(
        DistinguishedName dn,
        LdapAttributes attributes,
        String certificateType)

    // CRL Mapping
    public LdapAttributes crlToDomainAttributes(
        LdapCrlEntry entry)
    public LdapCrlEntry fromCrlAttributes(
        DistinguishedName dn,
        LdapAttributes attributes)

    // Generic Mapping
    public LdapAttributes mapToAttributes(Map<String, Object> data)
    public Map<String, Object> mapFromAttributes(LdapAttributes attributes)

    // Encoding/Decoding
    public byte[] encodeAttributeValue(String attributeName, String value)
    public String decodeAttributeValue(String attributeName, byte[] value)
    public String base64Encode(byte[] data)
    public byte[] base64Decode(String data)
}
```

**Usage Example**:
```java
LdapEntryMapper mapper = LdapEntryMapper.create();

// Certificate to attributes
LdapAttributes attrs = mapper.certificateToDomainAttributes(certEntry);

// Attributes to certificate
LdapCertificateEntry cert = mapper.fromDomainAttributes(
    dn,
    attrs,
    "CSCA"
);

// Generic mapping
LdapAttributes genericAttrs = mapper.mapToAttributes(
    Map.of(
        "cn", "test",
        "mail", "test@example.com"
    )
);
```

---

### 2. Service Ports (Domain Interfaces)

#### LdapUploadService

**Purpose**: Upload and manage certificates/CRLs in LDAP

**Package**: `com.smartcoreinc.localpkd.ldapintegration.domain.port`

**Implemented by**: `SpringLdapUploadAdapter`

```java
public interface LdapUploadService {

    // Certificate Operations
    UploadResult addCertificate(
        LdapCertificateEntry entry,
        LdapAttributes attributes
    ) throws LdapUploadException;

    UploadResult updateCertificate(
        LdapCertificateEntry entry,
        LdapAttributes attributes
    ) throws LdapUploadException;

    UploadResult addOrUpdateCertificate(
        LdapCertificateEntry entry,
        LdapAttributes attributes
    ) throws LdapUploadException;

    UploadResult deleteCertificate(
        DistinguishedName dn
    ) throws LdapUploadException;

    // CRL Operations
    UploadResult addCrl(
        LdapCrlEntry entry,
        LdapAttributes attributes
    ) throws LdapUploadException;

    UploadResult updateCrl(
        LdapCrlEntry entry,
        LdapAttributes attributes
    ) throws LdapUploadException;

    UploadResult deleteCrl(
        DistinguishedName dn
    ) throws LdapUploadException;

    // Batch Operations
    BatchUploadResult batchAddCertificates(
        List<LdapCertificateEntry> entries,
        List<LdapAttributes> attributesList
    ) throws LdapUploadException;

    BatchUploadResult batchUpdateCertificates(
        List<LdapCertificateEntry> entries,
        List<LdapAttributes> attributesList
    ) throws LdapUploadException;

    BatchUploadResult batchDeleteCertificates(
        List<DistinguishedName> dns
    ) throws LdapUploadException;

    // Utility Methods
    boolean existsCertificate(DistinguishedName dn) throws LdapUploadException;
    boolean existsCrl(DistinguishedName dn) throws LdapUploadException;
    void createOrganizationalUnit(DistinguishedName ou) throws LdapUploadException;
}
```

**Result Objects**:

```java
public interface UploadResult {
    boolean isSuccess();
    DistinguishedName getDistinguishedName();
    String getMessage();
    Exception getError();
    long getDurationMillis();
    int getEntriesProcessed();
}

public interface BatchUploadResult {
    int getTotalCount();
    int getSuccessCount();
    int getFailureCount();
    List<UploadResult> getSuccessfulResults();
    List<UploadResult> getFailedResults();
    long getTotalDurationMillis();
    double getSuccessRate();
}
```

**Exceptions**:
- `LdapUploadException`: Base exception for upload failures
- `LdapConnectionException`: LDAP connection issues
- `LdapEntryExistsException`: DN already exists
- `LdapEntryNotFoundException`: DN not found
- `LdapInvalidAttributeException`: Invalid attribute format

**Usage Example**:
```java
@Service
public class CertificateUploadService {
    private final LdapUploadService ldapUploadService;

    public void uploadCertificate(X509Certificate cert, String certType) {
        // Create domain models
        DistinguishedName dn = DistinguishedName.of(
            "cn=" + cert.getSubjectX500Principal().getName() +
            ",ou=" + certType.toLowerCase() +
            ",ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"
        );

        LdapCertificateEntry entry = LdapCertificateEntry.from(cert, dn, certType);
        LdapAttributes attrs = createAttributesFromCert(cert);

        // Upload to LDAP
        try {
            UploadResult result = ldapUploadService.addOrUpdateCertificate(entry, attrs);

            if (result.isSuccess()) {
                log.info("Certificate uploaded: {} in {} ms",
                        dn.getValue(), result.getDurationMillis());
            } else {
                log.error("Upload failed: {}", result.getMessage());
            }
        } catch (LdapUploadException e) {
            log.error("LDAP error during upload", e);
            throw new RuntimeException("Certificate upload failed", e);
        }
    }

    // Batch upload
    public void batchUploadCertificates(List<X509Certificate> certs, String certType) {
        List<LdapCertificateEntry> entries = new ArrayList<>();
        List<LdapAttributes> attributesList = new ArrayList<>();

        for (X509Certificate cert : certs) {
            DistinguishedName dn = createDnFromCertificate(cert, certType);
            entries.add(LdapCertificateEntry.from(cert, dn, certType));
            attributesList.add(createAttributesFromCert(cert));
        }

        try {
            BatchUploadResult result = ldapUploadService.batchAddCertificates(
                entries, attributesList
            );

            log.info("Batch upload completed: {}/{} succeeded ({:.1f}%)",
                    result.getSuccessCount(),
                    result.getTotalCount(),
                    result.getSuccessRate() * 100);

            if (!result.getFailedResults().isEmpty()) {
                log.warn("Failed uploads: {}", result.getFailureCount());
                result.getFailedResults().forEach(r ->
                    log.warn("  - {}: {}", r.getDistinguishedName(), r.getMessage())
                );
            }
        } catch (LdapUploadException e) {
            log.error("Batch upload failed", e);
        }
    }
}
```

---

#### LdapQueryService

**Purpose**: Query and retrieve certificates/CRLs from LDAP

**Package**: `com.smartcoreinc.localpkd.ldapintegration.domain.port`

**Implemented by**: `SpringLdapQueryAdapter`

```java
public interface LdapQueryService {

    // Single Entry Queries
    Optional<LdapCertificateEntry> findCertificateByDn(DistinguishedName dn)
        throws LdapQueryException;

    Optional<LdapCrlEntry> findCrlByDn(DistinguishedName dn)
        throws LdapQueryException;

    // List Queries
    List<LdapCertificateEntry> findAllCertificates(DistinguishedName baseDn)
        throws LdapQueryException;

    List<LdapCrlEntry> findAllCrls(DistinguishedName baseDn)
        throws LdapQueryException;

    // Filtered Queries
    List<LdapCertificateEntry> findCertificatesByFilter(
        DistinguishedName baseDn,
        LdapSearchFilter filter
    ) throws LdapQueryException;

    List<LdapCrlEntry> findCrlsByFilter(
        DistinguishedName baseDn,
        LdapSearchFilter filter
    ) throws LdapQueryException;

    // Type-Specific Queries
    List<LdapCertificateEntry> findCertificatesByType(
        DistinguishedName baseDn,
        String certificateType  // "CSCA", "DSC", "DS"
    ) throws LdapQueryException;

    List<LdapCertificateEntry> findExpiredCertificates(
        DistinguishedName baseDn
    ) throws LdapQueryException;

    List<LdapCertificateEntry> findExpiringCertificates(
        DistinguishedName baseDn,
        int daysUntilExpiration
    ) throws LdapQueryException;

    // Pagination Support
    Page<LdapCertificateEntry> findCertificatesPaged(
        DistinguishedName baseDn,
        LdapSearchFilter filter,
        Pageable pageable
    ) throws LdapQueryException;

    // Count Queries
    long countCertificates(DistinguishedName baseDn) throws LdapQueryException;
    long countCrls(DistinguishedName baseDn) throws LdapQueryException;
    long countByFilter(DistinguishedName baseDn, LdapSearchFilter filter)
        throws LdapQueryException;

    // Attribute Queries
    List<String> getDistinctAttributeValues(
        DistinguishedName baseDn,
        String attributeName
    ) throws LdapQueryException;
}
```

**Result Objects**:

```java
public interface LdapQueryResult<T> {
    boolean isEmpty();
    T getFirstResult();
    List<T> getResults();
    int getResultCount();
    long getQueryDurationMillis();
}

public interface Page<T> {
    List<T> getContent();
    int getCurrentPage();
    int getTotalPages();
    long getTotalElements();
    int getPageSize();
    boolean hasNext();
    boolean hasPrevious();
}
```

**Exceptions**:
- `LdapQueryException`: Base exception for query failures
- `LdapConnectionException`: Connection issues
- `LdapFilterException`: Invalid filter format

**Usage Example**:
```java
@Service
public class CertificateSearchService {
    private final LdapQueryService ldapQueryService;
    private final LdapProperties ldapProperties;

    // Find certificate by DN
    public Optional<LdapCertificateEntry> getCertificate(String dn) {
        try {
            return ldapQueryService.findCertificateByDn(
                DistinguishedName.of(dn)
            );
        } catch (LdapQueryException e) {
            log.error("Error fetching certificate", e);
            return Optional.empty();
        }
    }

    // Find all CSCA certificates
    public List<LdapCertificateEntry> getAllCscaCertificates() {
        try {
            DistinguishedName baseDn = DistinguishedName.of(
                ldapProperties.getCscaBaseDn(ldapProperties.getBase())
            );
            return ldapQueryService.findAllCertificates(baseDn);
        } catch (LdapQueryException e) {
            log.error("Error fetching CSCA certificates", e);
            return Collections.emptyList();
        }
    }

    // Find certificates by filter
    public List<LdapCertificateEntry> searchCertificates(String searchTerm) {
        try {
            DistinguishedName baseDn = DistinguishedName.of(
                ldapProperties.getCertificateBaseDn(ldapProperties.getBase())
            );

            LdapSearchFilter filter = LdapSearchFilter.contains("cn", searchTerm);

            return ldapQueryService.findCertificatesByFilter(baseDn, filter);
        } catch (LdapQueryException e) {
            log.error("Search failed", e);
            return Collections.emptyList();
        }
    }

    // Find expiring certificates
    public List<LdapCertificateEntry> getExpiringCertificates(int daysThreshold) {
        try {
            DistinguishedName baseDn = DistinguishedName.of(
                ldapProperties.getCertificateBaseDn(ldapProperties.getBase())
            );
            return ldapQueryService.findExpiringCertificates(baseDn, daysThreshold);
        } catch (LdapQueryException e) {
            log.error("Error fetching expiring certificates", e);
            return Collections.emptyList();
        }
    }

    // Paginated search
    public Page<LdapCertificateEntry> searchCertificatesPaged(
            String searchTerm,
            int page,
            int pageSize) {
        try {
            DistinguishedName baseDn = DistinguishedName.of(
                ldapProperties.getCertificateBaseDn(ldapProperties.getBase())
            );

            LdapSearchFilter filter = LdapSearchFilter.contains("cn", searchTerm);
            Pageable pageable = PageRequest.of(page, pageSize);

            return ldapQueryService.findCertificatesPaged(baseDn, filter, pageable);
        } catch (LdapQueryException e) {
            log.error("Paginated search failed", e);
            return Page.empty();
        }
    }

    // Get certificate count
    public long getCertificateCount() {
        try {
            DistinguishedName baseDn = DistinguishedName.of(
                ldapProperties.getCertificateBaseDn(ldapProperties.getBase())
            );
            return ldapQueryService.countCertificates(baseDn);
        } catch (LdapQueryException e) {
            log.error("Error counting certificates", e);
            return 0;
        }
    }
}
```

---

#### LdapSyncService

**Purpose**: Synchronize LDAP directory with batch operations

**Package**: `com.smartcoreinc.localpkd.ldapintegration.domain.port`

**Implemented by**: `SpringLdapSyncAdapter`

```java
public interface LdapSyncService {

    // Synchronization Operations
    SyncResult syncCertificates(
        List<LdapCertificateEntry> entries,
        List<LdapAttributes> attributesList,
        SyncStrategy strategy  // ADD_ONLY, UPDATE_ONLY, ADD_OR_UPDATE
    ) throws LdapSyncException;

    SyncResult syncCrls(
        List<LdapCrlEntry> entries,
        List<LdapAttributes> attributesList,
        SyncStrategy strategy
    ) throws LdapSyncException;

    // Incremental Sync
    IncrementalSyncResult incrementalSync(
        List<LdapCertificateEntry> newEntries,
        List<DistinguishedName> entresToDelete
    ) throws LdapSyncException;

    // Full Directory Sync
    FullSyncResult fullSync(
        DistinguishedName baseDn,
        List<LdapCertificateEntry> expectedEntries,
        SyncAction onMissing,  // DELETE, LEAVE, LOG
        SyncAction onExtra     // DELETE, LEAVE, LOG
    ) throws LdapSyncException;

    // Scheduled Sync
    void startScheduledSync(SyncSchedule schedule) throws LdapSyncException;
    void stopScheduledSync() throws LdapSyncException;
    SyncScheduleStatus getScheduledSyncStatus();

    // Transaction Support
    SyncTransaction beginTransaction() throws LdapSyncException;
    void commitTransaction(SyncTransaction transaction) throws LdapSyncException;
    void rollbackTransaction(SyncTransaction transaction) throws LdapSyncException;

    // Monitoring
    SyncStatistics getLastSyncStatistics();
    SyncStatistics getSyncStatisticsSince(LocalDateTime since);
    List<SyncLog> getSyncLogs(int limit);
}
```

**Enums & Constants**:

```java
public enum SyncStrategy {
    ADD_ONLY,           // Only add new entries, ignore duplicates
    UPDATE_ONLY,        // Only update existing entries
    ADD_OR_UPDATE,      // Add new, update existing (upsert)
    DELETE_MISSING      // Delete entries not in sync list
}

public enum SyncAction {
    DELETE,  // Delete missing or extra entries
    LEAVE,   // Leave as-is
    LOG      // Log a warning
}
```

**Result Objects**:

```java
public interface SyncResult {
    boolean isSuccess();
    int getProcessedCount();
    int getAddedCount();
    int getUpdatedCount();
    int getDeletedCount();
    int getFailureCount();
    long getDurationMillis();
    List<SyncError> getErrors();
}

public interface IncrementalSyncResult {
    int getAddedCount();
    int getUpdatedCount();
    int getDeletedCount();
    List<SyncError> getErrors();
}

public interface FullSyncResult {
    int getTotalEntries();
    int getInSyncCount();
    int getMissingCount();
    int getExtraCount();
    List<DistinguishedName> getMissingDns();
    List<DistinguishedName> getExtraDns();
}

public interface SyncStatistics {
    LocalDateTime getStartTime();
    LocalDateTime getEndTime();
    long getDurationMillis();
    int getTotalProcessed();
    double getSuccessRate();
    List<SyncError> getErrors();
}

public interface SyncLog {
    LocalDateTime getTimestamp();
    String getOperation();
    String getStatus();
    String getMessage();
}
```

**Usage Example**:
```java
@Service
public class CertificateSyncService {
    private final LdapSyncService ldapSyncService;
    private final LdapUploadService ldapUploadService;

    // Sync new certificates batch
    public void syncNewCertificates(List<X509Certificate> certs, String certType) {
        try {
            List<LdapCertificateEntry> entries = certs.stream()
                .map(cert -> createEntryFromCertificate(cert, certType))
                .collect(Collectors.toList());

            List<LdapAttributes> attributesList = certs.stream()
                .map(this::createAttributesFromCertificate)
                .collect(Collectors.toList());

            SyncResult result = ldapSyncService.syncCertificates(
                entries,
                attributesList,
                SyncStrategy.ADD_OR_UPDATE
            );

            log.info("Sync completed: {} processed, {} added, {} updated, {} failed",
                    result.getProcessedCount(),
                    result.getAddedCount(),
                    result.getUpdatedCount(),
                    result.getFailureCount());

            if (!result.getErrors().isEmpty()) {
                log.warn("Sync errors encountered:");
                result.getErrors().forEach(err ->
                    log.warn("  - {}: {}", err.getEntryDn(), err.getMessage())
                );
            }
        } catch (LdapSyncException e) {
            log.error("Sync failed", e);
            throw new RuntimeException("Certificate sync failed", e);
        }
    }

    // Incremental sync (new and deleted)
    public void incrementalSync(List<X509Certificate> newCerts,
                               List<String> deletedDns) {
        try {
            List<LdapCertificateEntry> entries = newCerts.stream()
                .map(cert -> createEntryFromCertificate(cert, "CSCA"))
                .collect(Collectors.toList());

            List<DistinguishedName> dnToDelete = deletedDns.stream()
                .map(DistinguishedName::of)
                .collect(Collectors.toList());

            IncrementalSyncResult result = ldapSyncService.incrementalSync(
                entries,
                dnToDelete
            );

            log.info("Incremental sync: {} added, {} updated, {} deleted",
                    result.getAddedCount(),
                    result.getUpdatedCount(),
                    result.getDeletedCount());
        } catch (LdapSyncException e) {
            log.error("Incremental sync failed", e);
        }
    }

    // Full sync with verification
    public void verifyAndSyncDirectory(List<X509Certificate> expectedCerts) {
        try {
            List<LdapCertificateEntry> expectedEntries = expectedCerts.stream()
                .map(cert -> createEntryFromCertificate(cert, "CSCA"))
                .collect(Collectors.toList());

            DistinguishedName baseDn = DistinguishedName.of(
                "ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"
            );

            FullSyncResult result = ldapSyncService.fullSync(
                baseDn,
                expectedEntries,
                SyncAction.DELETE,  // Delete missing
                SyncAction.LOG      // Log extra
            );

            log.info("Directory verification: {} total, {} in sync",
                    result.getTotalEntries(),
                    result.getInSyncCount());

            if (!result.getMissingDns().isEmpty()) {
                log.warn("Missing entries in LDAP: {}", result.getMissingDns());
            }

            if (!result.getExtraDns().isEmpty()) {
                log.warn("Extra entries in LDAP: {}", result.getExtraDns());
            }
        } catch (LdapSyncException e) {
            log.error("Directory verification failed", e);
        }
    }

    // Scheduled sync
    public void setupScheduledSync() {
        try {
            SyncSchedule schedule = SyncSchedule.builder()
                .enabled(true)
                .cronExpression("0 0 2 * * *")  // Daily at 2 AM
                .timeoutSeconds(300)
                .batchSize(100)
                .build();

            ldapSyncService.startScheduledSync(schedule);
            log.info("Scheduled sync started");

            // Monitor sync status
            SyncScheduleStatus status = ldapSyncService.getScheduledSyncStatus();
            log.info("Next sync: {}", status.getNextSyncTime());

        } catch (LdapSyncException e) {
            log.error("Failed to start scheduled sync", e);
        }
    }

    // Monitor sync health
    public void printSyncStatistics() {
        SyncStatistics stats = ldapSyncService.getLastSyncStatistics();

        log.info("Last sync: {} (duration: {} ms)",
                stats.getEndTime(),
                stats.getDurationMillis());
        log.info("Processed: {} entries, success rate: {:.1f}%",
                stats.getTotalProcessed(),
                stats.getSuccessRate() * 100);

        if (!stats.getErrors().isEmpty()) {
            log.warn("Errors: {}", stats.getErrors().size());
            stats.getErrors().forEach(err ->
                log.warn("  - {}", err.getMessage())
            );
        }
    }
}
```

---

## Configuration

### LdapProperties

**Purpose**: LDAP configuration properties (maps to `application.properties`)

**Package**: `com.smartcoreinc.localpkd.ldapintegration.infrastructure.config`

```yaml
# Basic Connection
app.ldap.urls: ldap://localhost:389
app.ldap.base: dc=ldap,dc=smartcoreinc,dc=com
app.ldap.username: cn=admin,dc=ldap,dc=smartcoreinc,dc=com
app.ldap.password: ${LDAP_PASSWORD}

# Timeouts
app.ldap.connect-timeout: 30000    # 30 seconds
app.ldap.read-timeout: 60000       # 60 seconds
app.ldap.pool-timeout: 5000        # 5 seconds

# Directory Organization
app.ldap.certificate-base: ou=certificates
app.ldap.csca-ou: ou=csca
app.ldap.dsc-ou: ou=dsc
app.ldap.ds-ou: ou=ds
app.ldap.crl-base: ou=crl

# Connection Pool
app.ldap.pool.max-active: 8
app.ldap.pool.max-idle: 4
app.ldap.pool.max-total: 12
app.ldap.pool.min-idle: 2
app.ldap.pool.block-when-exhausted: true
app.ldap.pool.test-on-borrow: true
app.ldap.pool.test-on-return: true
app.ldap.pool.test-while-idle: true
app.ldap.pool.eviction-interval-millis: 600000
app.ldap.pool.min-evictable-idle-time-millis: 300000

# Synchronization
app.ldap.sync.enabled: true
app.ldap.sync.batch-size: 100
app.ldap.sync.max-retries: 3
app.ldap.sync.initial-delay-ms: 1000
app.ldap.sync.scheduled-cron: "0 0 0 * * *"  # Daily at midnight
app.ldap.sync.sync-timeout-seconds: 300      # 5 minutes

# Batch Processing
app.ldap.batch.thread-pool-size: 4
app.ldap.batch.queue-capacity: 1000
app.ldap.batch.keep-alive-seconds: 60
app.ldap.batch.thread-name-prefix: ldap-batch-
app.ldap.batch.record-failures: true
app.ldap.batch.max-batch-size: 1000
```

**Java Configuration**:

```java
@Configuration
@EnableConfigurationProperties(LdapProperties.class)
public class LdapConfiguration {

    @Bean
    public LdapContextSource ldapContextSource(LdapProperties props) {
        // Creates and configures Spring LdapContextSource
    }

    @Bean
    public PoolingContextSource poolingContextSource(
            LdapContextSource contextSource,
            LdapProperties props) {
        // Wraps context source with connection pooling
    }

    @Bean
    public LdapTemplate ldapTemplate(PoolingContextSource contextSource) {
        // Creates Spring LdapTemplate for LDAP operations
    }

    @Bean
    public LdapHealthCheck ldapHealthCheck(
            PoolingContextSource contextSource,
            LdapTemplate template) {
        // Creates health check utility
    }
}
```

### LdapHealthCheck

**Purpose**: Monitor LDAP connection health

```java
public static class LdapHealthCheck {
    public boolean isConnected()  // Check if LDAP is reachable
    public String getPoolStatistics()  // Get active/idle connection counts
}
```

**Usage**:

```java
@RestController
@RequestMapping("/health")
public class HealthController {
    private final LdapHealthCheck ldapHealthCheck;

    @GetMapping("/ldap")
    public ResponseEntity<?> checkLdapHealth() {
        return ResponseEntity.ok(Map.of(
            "connected", ldapHealthCheck.isConnected(),
            "poolStats", ldapHealthCheck.getPoolStatistics()
        ));
    }
}
```

---

## Adapter Implementations

### SpringLdapUploadAdapter

**Location**: `com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter`

**Implements**: `LdapUploadService`

**Key Methods**:

```java
@Component
public class SpringLdapUploadAdapter implements LdapUploadService {

    // Delegates to LdapTemplate for actual LDAP operations
    // Implements retry logic, error handling, and monitoring

    @Override
    public UploadResult addCertificate(
            LdapCertificateEntry entry,
            LdapAttributes attributes) {
        // 1. Validate inputs
        // 2. Create LDAP Entry
        // 3. Bind to directory
        // 4. Return UploadResult
    }

    @Override
    public BatchUploadResult batchAddCertificates(
            List<LdapCertificateEntry> entries,
            List<LdapAttributes> attributesList) {
        // 1. Process in parallel using ThreadPoolExecutor
        // 2. Track success/failure per entry
        // 3. Return BatchUploadResult with statistics
    }
}
```

---

### SpringLdapQueryAdapter

**Location**: `com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter`

**Implements**: `LdapQueryService`

**Key Methods**:

```java
@Component
public class SpringLdapQueryAdapter implements LdapQueryService {

    @Override
    public List<LdapCertificateEntry> findCertificatesByFilter(
            DistinguishedName baseDn,
            LdapSearchFilter filter) {
        // 1. Execute LDAP search with filter
        // 2. Map results to domain models
        // 3. Return list of certificates
    }

    @Override
    public List<LdapCertificateEntry> findExpiringCertificates(
            DistinguishedName baseDn,
            int daysUntilExpiration) {
        // 1. Calculate threshold date
        // 2. Create filter for expiration date
        // 3. Search and filter results
    }
}
```

---

### SpringLdapSyncAdapter

**Location**: `com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter`

**Implements**: `LdapSyncService`

**Key Methods**:

```java
@Component
public class SpringLdapSyncAdapter implements LdapSyncService {

    @Override
    public SyncResult syncCertificates(
            List<LdapCertificateEntry> entries,
            List<LdapAttributes> attributesList,
            SyncStrategy strategy) {
        // 1. Process each entry
        // 2. Add/Update/Delete based on strategy
        // 3. Track success/failure
        // 4. Return SyncResult
    }

    @Override
    public void startScheduledSync(SyncSchedule schedule) {
        // 1. Create scheduled task using Spring Scheduler
        // 2. Use cron expression from schedule
        // 3. Implement retry on failure
    }
}
```

---

## Error Handling

### Exception Hierarchy

```
RuntimeException
├── LdapException (root LDAP exception)
│   ├── LdapConnectionException       # Connection failures
│   ├── LdapUploadException          # Upload/write failures
│   │   ├── LdapEntryExistsException # DN already exists
│   │   ├── LdapEntryNotFoundException # DN not found
│   │   └── LdapInvalidAttributeException # Invalid attributes
│   ├── LdapQueryException           # Query/search failures
│   │   ├── LdapFilterException      # Invalid filter
│   │   └── LdapEmptyResultException # No results found
│   └── LdapSyncException            # Sync failures
│       ├── LdapSyncTimeoutException # Sync exceeded timeout
│       └── LdapPartialSyncException # Some entries failed
```

### Error Codes

```java
public enum LdapErrorCode {
    LDAP_001("Connection failed"),
    LDAP_002("Entry already exists"),
    LDAP_003("Entry not found"),
    LDAP_004("Invalid attribute"),
    LDAP_005("Invalid filter"),
    LDAP_006("Query timeout"),
    LDAP_007("Sync failed"),
    LDAP_008("Pool exhausted"),
    LDAP_009("Invalid configuration")
}
```

### Exception Handling Best Practices

```java
try {
    UploadResult result = ldapUploadService.addCertificate(entry, attrs);
} catch (LdapEntryExistsException e) {
    // Handle duplicate entry
    log.warn("Certificate already exists: {}", e.getMessage());
    // Try update instead
    result = ldapUploadService.updateCertificate(entry, attrs);
} catch (LdapConnectionException e) {
    // Handle connection failure
    log.error("LDAP connection lost", e);
    // Trigger failover or retry logic
} catch (LdapUploadException e) {
    // Generic upload failure
    log.error("Upload failed", e);
    throw new ServiceException("Failed to upload certificate", e);
}
```

---

## Testing

### Unit Testing

**Test Framework**: JUnit 5 + Mockito + AssertJ

```java
@ExtendWith(MockitoExtension.class)
class LdapUploadServiceTest {

    @Mock
    private LdapTemplate ldapTemplate;

    @InjectMocks
    private SpringLdapUploadAdapter uploadAdapter;

    @Test
    @DisplayName("addCertificate should successfully add certificate to LDAP")
    void testAddCertificate() {
        // Arrange
        LdapCertificateEntry entry = createTestCertificateEntry();
        LdapAttributes attributes = createTestAttributes();

        // Act
        UploadResult result = uploadAdapter.addCertificate(entry, attributes);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(ldapTemplate).bind(any());
    }
}
```

### Integration Testing

**Test Infrastructure**: Embedded LDAP Server (UnboundID)

```java
@SpringBootTest
class LdapIntegrationTest {

    static final LdapIntegrationTestFixture ldapFixture =
        new LdapIntegrationTestFixture();

    @BeforeAll
    static void startLdapServer() {
        ldapFixture.start();
    }

    @AfterAll
    static void stopLdapServer() {
        ldapFixture.stop();
    }

    @Test
    void testEndToEndUploadAndQuery() {
        // Use ldapFixture for integration testing
        LdapCertificateEntry entry = createTestCertificate();
        ldapFixture.addTestCertificate(entry);

        Optional<LdapCertificateEntry> found =
            ldapQueryService.findCertificateByDn(entry.getDn());

        assertThat(found).isPresent();
    }
}
```

### Test Fixture

**Location**: `src/test/java/.../LdapIntegrationTestFixture.java`

```java
public class LdapIntegrationTestFixture {

    public void start()                          // Start embedded LDAP
    public void stop()                           // Stop LDAP
    public LdapContextSource createContextSource()  // Get Spring context source
    public void addTestCertificate(...)          // Add test data
    public List<Entry> getAllEntries(...)        // Search entries
    public List<Entry> searchEntries(...) // Search with filter
    public boolean isRunning()                   // Check server status
}
```

---

## Quick Start Guide

### 1. Configuration

```yaml
# application.properties
app.ldap.urls=ldap://localhost:389
app.ldap.base=dc=ldap,dc=smartcoreinc,dc=com
app.ldap.username=cn=admin,dc=ldap,dc=smartcoreinc,dc=com
app.ldap.password=${LDAP_PASSWORD}

# ... other properties ...
```

### 2. Inject Services

```java
@Service
public class MyCertificateService {

    private final LdapUploadService uploadService;
    private final LdapQueryService queryService;

    @Autowired
    public MyCertificateService(
            LdapUploadService uploadService,
            LdapQueryService queryService) {
        this.uploadService = uploadService;
        this.queryService = queryService;
    }
}
```

### 3. Upload Certificate

```java
public void uploadCertificate(X509Certificate cert) throws Exception {
    // Create domain models
    DistinguishedName dn = DistinguishedName.of(
        "cn=my-cert,ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"
    );

    LdapCertificateEntry entry = LdapCertificateEntry.from(cert, dn, "CSCA");

    LdapAttributes attributes = LdapAttributes.builder()
        .add("objectClass", "pkiCertificate", "inetOrgPerson")
        .add("cn", "my-cert")
        .add("certificate;binary", cert.getEncoded())
        .build();

    // Upload
    UploadResult result = uploadService.addOrUpdateCertificate(entry, attributes);

    if (result.isSuccess()) {
        System.out.println("Certificate uploaded successfully");
    }
}
```

### 4. Query Certificates

```java
public void searchCertificates() throws Exception {
    DistinguishedName baseDn = DistinguishedName.of(
        "ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"
    );

    // Find all certificates
    List<LdapCertificateEntry> all = queryService.findAllCertificates(baseDn);

    // Find by filter
    LdapSearchFilter filter = LdapSearchFilter.contains("cn", "test");
    List<LdapCertificateEntry> filtered =
        queryService.findCertificatesByFilter(baseDn, filter);

    // Find expiring
    List<LdapCertificateEntry> expiring =
        queryService.findExpiringCertificates(baseDn, 30);  // Within 30 days
}
```

---

## Summary

This API Reference Documentation provides comprehensive coverage of the LDAP Integration Module including:

- **Domain Models**: Type-safe representations of LDAP concepts
- **Service Ports**: Clean abstraction interfaces
- **Adapter Implementations**: Spring LDAP-based concrete implementations
- **Configuration**: Property-based and Java-based configuration
- **Usage Examples**: Real-world use case patterns
- **Error Handling**: Exception hierarchy and handling best practices
- **Testing**: Unit and integration testing strategies

For additional information, refer to:
- **CLAUDE.md**: Project architecture and phases
- **Phase 11 Progress**: LDAP integration implementation details
- **Source Code**: JavaDoc annotations in actual implementation files

