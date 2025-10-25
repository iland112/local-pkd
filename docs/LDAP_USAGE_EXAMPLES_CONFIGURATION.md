# LDAP Integration - Usage Examples & Configuration Guide

**Version**: 1.0.0
**Last Updated**: 2025-10-25
**Author**: SmartCore Inc.
**Status**: COMPLETE

---

## Table of Contents

1. [Configuration Setup](#configuration-setup)
2. [Common Usage Patterns](#common-usage-patterns)
3. [Certificate Management Examples](#certificate-management-examples)
4. [CRL Management Examples](#crl-management-examples)
5. [Query & Search Examples](#query--search-examples)
6. [Synchronization Examples](#synchronization-examples)
7. [Error Handling & Recovery](#error-handling--recovery)
8. [Performance & Tuning](#performance--tuning)
9. [Troubleshooting](#troubleshooting)
10. [Production Deployment](#production-deployment)

---

## Configuration Setup

### Basic Configuration

#### application.properties (Minimum)

```properties
# Basic LDAP Connection
app.ldap.urls=ldap://ldap.example.com:389
app.ldap.base=dc=example,dc=com
app.ldap.username=cn=admin,dc=example,dc=com
app.ldap.password=${LDAP_PASSWORD}  # From environment variable
```

#### application-dev.properties (Development)

```properties
# Development environment - local LDAP
app.ldap.urls=ldap://localhost:389
app.ldap.base=dc=ldap,dc=smartcoreinc,dc=com
app.ldap.username=cn=admin,dc=ldap,dc=smartcoreinc,dc=com
app.ldap.password=admin-password

# Shorter timeouts for debugging
app.ldap.connect-timeout=10000
app.ldap.read-timeout=20000
app.ldap.pool-timeout=2000

# Connection pool (small for dev)
app.ldap.pool.max-active=2
app.ldap.pool.max-idle=1
app.ldap.pool.max-total=3
```

#### application-prod.properties (Production)

```properties
# Production environment - HA LDAP cluster
app.ldap.urls=ldap://ldap1.prod.com:389,ldap://ldap2.prod.com:389,ldap://ldap3.prod.com:389
app.ldap.base=dc=prod,dc=example,dc=com
app.ldap.username=${LDAP_ADMIN_DN}
app.ldap.password=${LDAP_ADMIN_PASSWORD}

# Longer timeouts for stability
app.ldap.connect-timeout=60000
app.ldap.read-timeout=120000
app.ldap.pool-timeout=10000

# Larger connection pool
app.ldap.pool.max-active=20
app.ldap.pool.max-idle=10
app.ldap.pool.max-total=30
app.ldap.pool.min-idle=5

# Health checks
app.ldap.pool.test-on-borrow=true
app.ldap.pool.test-on-return=true
app.ldap.pool.test-while-idle=true
app.ldap.pool.eviction-interval-millis=300000  # 5 minutes

# Sync configuration
app.ldap.sync.enabled=true
app.ldap.sync.batch-size=500
app.ldap.sync.max-retries=5
app.ldap.sync.scheduled-cron=0 0 2 * * *  # Daily 2 AM

# Batch processing
app.ldap.batch.thread-pool-size=8
app.ldap.batch.queue-capacity=5000
```

#### application-secure.properties (TLS/SSL)

```properties
# LDAPS (Secure LDAP) Configuration
app.ldap.urls=ldaps://ldap.example.com:636
app.ldap.base=dc=example,dc=com
app.ldap.username=cn=admin,dc=example,dc=com
app.ldap.password=${LDAP_PASSWORD}

# SSL/TLS Settings
spring.ldap.embedded.ldif=ldap://...
spring.ldap.embedded.base-dn=dc=example,dc=com
spring.ldap.pool.validation.enabled=true

# Certificate validation (if using self-signed)
javax.net.debug=ssl
# Or disable validation (not recommended for production)
# com.sun.jndi.ldap.LdapCtxFactory.trustStorePassword=${TRUST_STORE_PASSWORD}
```

### Java Configuration

#### @Configuration Class

```java
@Configuration
@EnableConfigurationProperties(LdapProperties.class)
@EnableScheduling
@Slf4j
public class LdapConfiguration {

    private final LdapProperties ldapProperties;

    @Bean
    public LdapContextSource ldapContextSource() {
        log.info("Initializing LDAP Context Source");
        log.info("URLs: {}", ldapProperties.getUrls());
        log.info("Base DN: {}", ldapProperties.getBase());

        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrls(ldapProperties.getUrls().split(","));
        contextSource.setBase(ldapProperties.getBase());
        contextSource.setUserDn(ldapProperties.getUsername());
        contextSource.setPassword(ldapProperties.getPassword());

        // Set timeouts
        contextSource.setConnectTimeout(ldapProperties.getConnectTimeout());
        contextSource.setReadTimeout(ldapProperties.getReadTimeout());

        try {
            contextSource.afterPropertiesSet();
        } catch (Exception e) {
            log.error("Failed to initialize LDAP Context Source", e);
            throw new RuntimeException("LDAP configuration failed", e);
        }

        log.info("LDAP Context Source initialized successfully");
        return contextSource;
    }

    @Bean
    public PoolingContextSource poolingContextSource(
            LdapContextSource contextSource) {
        log.info("Initializing Connection Pool");

        PoolingContextSource pooling = new PoolingContextSource();
        pooling.setContextSource(contextSource);

        LdapProperties.PoolConfig pool = ldapProperties.getPool();
        pooling.setMaxActive(pool.getMaxActive());
        pooling.setMaxIdle(pool.getMaxIdle());
        pooling.setMaxTotal(pool.getMaxTotal());
        pooling.setMaxWait(ldapProperties.getPoolTimeout());
        pooling.setTestOnBorrow(pool.isTestOnBorrow());
        pooling.setTestOnReturn(pool.isTestOnReturn());
        pooling.setTestWhileIdle(pool.isTestWhileIdle());
        pooling.setMinEvictableIdleTimeMillis(
            pool.getMinEvictableIdleTimeMillis());
        pooling.setDirContextValidator(new DefaultDirContextValidator());

        log.info("Connection Pool: max-active={}, max-idle={}, max-total={}",
                pool.getMaxActive(), pool.getMaxIdle(), pool.getMaxTotal());

        return pooling;
    }

    @Bean
    public LdapTemplate ldapTemplate(PoolingContextSource pooling) {
        LdapTemplate template = new LdapTemplate(pooling);
        template.setIgnorePartialResultException(true);
        return template;
    }

    @Bean
    public LdapHealthCheck ldapHealthCheck(
            PoolingContextSource pooling,
            LdapTemplate template) {
        return new LdapHealthCheck(pooling, template);
    }

    // Service Beans (Adapters)
    @Bean
    public LdapUploadService ldapUploadService(LdapTemplate template) {
        return new SpringLdapUploadAdapter(template);
    }

    @Bean
    public LdapQueryService ldapQueryService(LdapTemplate template) {
        return new SpringLdapQueryAdapter(template);
    }

    @Bean
    public LdapSyncService ldapSyncService(
            LdapTemplate template,
            LdapUploadService uploadService) {
        return new SpringLdapSyncAdapter(template, uploadService);
    }
}
```

### Environment Variable Setup

#### .env File (Local Development)

```bash
# LDAP Configuration
LDAP_IP=localhost
LDAP_PORT=389
LDAP_USERNAME=cn=admin,dc=ldap,dc=smartcoreinc,dc=com
LDAP_PASSWORD=admin-password

# Application
APP_ENVIRONMENT=dev
APP_DEBUG_MODE=true
```

#### Docker Compose with LDAP

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: local_pkd
      POSTGRES_PASSWORD: postgres-password
    ports:
      - "5432:5432"

  openldap:
    image: osixia/openldap:latest
    environment:
      LDAP_ORGANISATION: "SmartCore Inc."
      LDAP_DOMAIN: ldap.smartcoreinc.com
      LDAP_ADMIN_PASSWORD: admin-password
    ports:
      - "389:389"
      - "636:636"
    volumes:
      - ldap-data:/var/lib/ldap
      - ldap-config:/etc/ldap/slapd.d

  phpldapadmin:
    image: osixia/phpldapadmin:latest
    environment:
      PHPLDAPADMIN_LDAP_HOSTS: openldap
    ports:
      - "6443:443"
    depends_on:
      - openldap

volumes:
  ldap-data:
  ldap-config:
```

#### Initialize LDAP with LDIF

```bash
# Create ldif/bootstrap.ldif
dn: dc=ldap,dc=smartcoreinc,dc=com
objectClass: top
objectClass: dcObject
objectClass: organization
o: SmartCore Inc.
dc: ldap

dn: cn=admin,dc=ldap,dc=smartcoreinc,dc=com
objectClass: simpleSecurityObject
objectClass: organizationalRole
cn: admin
userPassword: admin-password

dn: ou=certificates,dc=ldap,dc=smartcoreinc,dc=com
objectClass: organizationalUnit
ou: certificates

dn: ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com
objectClass: organizationalUnit
ou: csca

dn: ou=dsc,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com
objectClass: organizationalUnit
ou: dsc

dn: ou=ds,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com
objectClass: organizationalUnit
ou: ds

dn: ou=crl,dc=ldap,dc=smartcoreinc,dc=com
objectClass: organizationalUnit
ou: crl

dn: ou=users,dc=ldap,dc=smartcoreinc,dc=com
objectClass: organizationalUnit
ou: users

# Load into LDAP server
ldapadd -x -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
        -w admin-password -H ldap://localhost:389 < bootstrap.ldif
```

---

## Common Usage Patterns

### Dependency Injection Pattern

```java
@Service
public class CertificateManagementService {

    private final LdapUploadService uploadService;
    private final LdapQueryService queryService;
    private final LdapSyncService syncService;
    private final LdapProperties ldapProperties;

    @Autowired
    public CertificateManagementService(
            LdapUploadService uploadService,
            LdapQueryService queryService,
            LdapSyncService syncService,
            LdapProperties ldapProperties) {
        this.uploadService = uploadService;
        this.queryService = queryService;
        this.syncService = syncService;
        this.ldapProperties = ldapProperties;
    }

    // ... service methods ...
}
```

### REST Controller Pattern

```java
@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
@Slf4j
public class CertificateController {

    private final CertificateManagementService managementService;
    private final LdapProperties ldapProperties;

    @GetMapping
    public ResponseEntity<?> listCertificates(
            @RequestParam(defaultValue = "CSCA") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        log.debug("List certificates: type={}, page={}, size={}", type, page, size);

        try {
            List<CertificateDto> certificates = managementService
                .findCertificatesByType(type, page, size);

            return ResponseEntity.ok(Map.of(
                "count", certificates.size(),
                "data", certificates
            ));
        } catch (LdapQueryException e) {
            log.error("Query failed", e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to query LDAP",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping
    public ResponseEntity<?> uploadCertificate(
            @RequestParam String dn,
            @RequestBody byte[] certificateData) {
        log.info("Upload certificate: {}", dn);

        try {
            managementService.uploadCertificate(dn, certificateData);
            return ResponseEntity.status(201).build();
        } catch (Exception e) {
            log.error("Upload failed", e);
            return ResponseEntity.status(400).body(Map.of(
                "error", "Upload failed",
                "message", e.getMessage()
            ));
        }
    }
}
```

---

## Certificate Management Examples

### Example 1: Upload Single Certificate

```java
public class SingleCertificateUploadExample {

    public static void main(String[] args) throws Exception {
        // Load Spring context
        ApplicationContext context = new SpringApplication(
            Application.class).run(args);

        LdapUploadService uploadService =
            context.getBean(LdapUploadService.class);
        LdapProperties props = context.getBean(LdapProperties.class);

        // Load certificate from file
        X509Certificate cert = loadCertificateFromFile("path/to/cert.pem");

        // Create DN
        String baseDn = props.getCscaBaseDn(props.getBase());
        String certName = cert.getSubjectX500Principal()
                              .getName()
                              .split(",")[0];  // Extract CN
        DistinguishedName dn = DistinguishedName.of(
            certName + "," + baseDn
        );

        // Create entry and attributes
        LdapCertificateEntry entry = LdapCertificateEntry.from(
            cert, dn, "CSCA"
        );

        LdapAttributes attributes = LdapAttributes.builder()
            .add("objectClass", "pkiCertificate", "inetOrgPerson")
            .add("cn", extractCommonName(cert))
            .add("mail", "certificates@example.com")
            .add("certificate;binary", cert.getEncoded())
            .build();

        // Upload
        try {
            UploadResult result = uploadService.addOrUpdateCertificate(
                entry, attributes
            );

            if (result.isSuccess()) {
                System.out.println("✓ Certificate uploaded successfully");
                System.out.println("  DN: " + dn.getValue());
                System.out.println("  Time: " + result.getDurationMillis() + "ms");
            } else {
                System.err.println("✗ Upload failed: " + result.getMessage());
            }
        } catch (LdapUploadException e) {
            System.err.println("✗ LDAP error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static X509Certificate loadCertificateFromFile(String path)
            throws Exception {
        byte[] certificateData = Files.readAllBytes(Paths.get(path));
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(
            new ByteArrayInputStream(certificateData)
        );
    }

    private static String extractCommonName(X509Certificate cert) {
        String dn = cert.getSubjectX500Principal().getName();
        String[] parts = dn.split(",");
        for (String part : parts) {
            if (part.trim().startsWith("CN=")) {
                return part.trim().substring(3);
            }
        }
        return "unknown";
    }
}
```

### Example 2: Batch Upload Certificates

```java
public class BatchCertificateUploadExample {

    public static void main(String[] args) throws Exception {
        ApplicationContext context = new SpringApplication(
            Application.class).run(args);

        LdapUploadService uploadService =
            context.getBean(LdapUploadService.class);

        // Load certificates from directory
        List<X509Certificate> certificates = loadCertificatesFromDirectory(
            "path/to/certs"
        );

        System.out.println("Loading " + certificates.size() + " certificates...");

        List<LdapCertificateEntry> entries = new ArrayList<>();
        List<LdapAttributes> attributesList = new ArrayList<>();

        for (X509Certificate cert : certificates) {
            DistinguishedName dn = createDnFromCertificate(cert);
            LdapCertificateEntry entry = LdapCertificateEntry.from(
                cert, dn, "CSCA"
            );
            LdapAttributes attrs = createAttributesFromCertificate(cert);

            entries.add(entry);
            attributesList.add(attrs);
        }

        // Batch upload with progress tracking
        try {
            System.out.print("Uploading certificates");
            long startTime = System.currentTimeMillis();

            BatchUploadResult result = uploadService.batchAddCertificates(
                entries, attributesList
            );

            long duration = System.currentTimeMillis() - startTime;

            System.out.println("\n✓ Batch upload completed in " + duration + "ms");
            System.out.println("  Total: " + result.getTotalCount());
            System.out.println("  Success: " + result.getSuccessCount());
            System.out.println("  Failed: " + result.getFailureCount());
            System.out.println("  Success Rate: " + String.format("%.1f%%",
                result.getSuccessRate() * 100));

            // Print failures
            if (!result.getFailedResults().isEmpty()) {
                System.out.println("\nFailures:");
                for (UploadResult failedResult : result.getFailedResults()) {
                    System.out.println("  ✗ " + failedResult.getDistinguishedName()
                                     + ": " + failedResult.getMessage());
                }
            }

        } catch (LdapUploadException e) {
            System.err.println("✗ Batch upload failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<X509Certificate> loadCertificatesFromDirectory(
            String dirPath) throws Exception {
        List<X509Certificate> certificates = new ArrayList<>();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        Files.walk(Paths.get(dirPath))
            .filter(path -> path.toString().endsWith(".pem") ||
                           path.toString().endsWith(".der"))
            .forEach(path -> {
                try {
                    byte[] data = Files.readAllBytes(path);
                    X509Certificate cert = (X509Certificate)
                        cf.generateCertificate(new ByteArrayInputStream(data));
                    certificates.add(cert);
                } catch (Exception e) {
                    System.err.println("Failed to load: " + path);
                }
            });

        return certificates;
    }

    private static DistinguishedName createDnFromCertificate(
            X509Certificate cert) {
        String baseDn = "ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com";
        String cn = extractCommonName(cert);
        return DistinguishedName.of("cn=" + cn + "," + baseDn);
    }

    private static LdapAttributes createAttributesFromCertificate(
            X509Certificate cert) throws Exception {
        return LdapAttributes.builder()
            .add("objectClass", "pkiCertificate")
            .add("cn", extractCommonName(cert))
            .add("certificate;binary", cert.getEncoded())
            .build();
    }

    private static String extractCommonName(X509Certificate cert) {
        // ... implementation ...
        return "";
    }
}
```

### Example 3: Update Certificate Attributes

```java
public class UpdateCertificateExample {

    public static void updateCertificateAttributes(
            LdapUploadService uploadService,
            String dn) throws LdapUploadException {

        // Find existing certificate
        DistinguishedName distinguishedName = DistinguishedName.of(dn);

        // Create updated attributes
        LdapAttributes updatedAttrs = LdapAttributes.builder()
            .add("mail", "updated-email@example.com")
            .add("description", "Updated on " + LocalDateTime.now())
            .add("modifyTimestamp", LocalDateTime.now().toString())
            .build();

        // Create entry with updated attributes
        LdapCertificateEntry entry = new LdapCertificateEntry();
        // ... set entry details ...

        // Update in LDAP
        UploadResult result = uploadService.updateCertificate(entry, updatedAttrs);

        if (result.isSuccess()) {
            System.out.println("✓ Certificate updated: " + dn);
        } else {
            System.err.println("✗ Update failed: " + result.getMessage());
        }
    }
}
```

---

## Query & Search Examples

### Example 1: Find All Certificates

```java
public class FindAllCertificatesExample {

    public static void main(String[] args) throws LdapQueryException {
        ApplicationContext context = new SpringApplication(
            Application.class).run(args);

        LdapQueryService queryService =
            context.getBean(LdapQueryService.class);
        LdapProperties props = context.getBean(LdapProperties.class);

        // Get base DN
        DistinguishedName baseDn = DistinguishedName.of(
            props.getCertificateBaseDn(props.getBase())
        );

        // Query all certificates
        List<LdapCertificateEntry> certificates =
            queryService.findAllCertificates(baseDn);

        System.out.println("Found " + certificates.size() + " certificates:");

        for (LdapCertificateEntry cert : certificates) {
            System.out.println("  " + cert.getDn().getValue());
            System.out.println("    Issuer: " + cert.getIssuerDn());
            System.out.println("    Valid Until: " + cert.getNotAfter());
            System.out.println();
        }
    }
}
```

### Example 2: Search with Filter

```java
public class SearchCertificatesExample {

    public static void main(String[] args) throws LdapQueryException {
        ApplicationContext context = new SpringApplication(
            Application.class).run(args);

        LdapQueryService queryService =
            context.getBean(LdapQueryService.class);
        LdapProperties props = context.getBean(LdapProperties.class);

        DistinguishedName baseDn = DistinguishedName.of(
            props.getCscaBaseDn(props.getBase())
        );

        // Example 1: Simple equals filter
        LdapSearchFilter filter1 = LdapSearchFilter.equals("cn", "test-cert");
        List<LdapCertificateEntry> results1 =
            queryService.findCertificatesByFilter(baseDn, filter1);
        System.out.println("Found with equals filter: " + results1.size());

        // Example 2: Contains filter
        LdapSearchFilter filter2 = LdapSearchFilter.contains("cn", "cert");
        List<LdapCertificateEntry> results2 =
            queryService.findCertificatesByFilter(baseDn, filter2);
        System.out.println("Found with contains filter: " + results2.size());

        // Example 3: AND filter (multiple conditions)
        LdapSearchFilter filter3 = LdapSearchFilter.and(
            LdapSearchFilter.equals("objectClass", "pkiCertificate"),
            LdapSearchFilter.startsWith("cn", "csca-")
        );
        List<LdapCertificateEntry> results3 =
            queryService.findCertificatesByFilter(baseDn, filter3);
        System.out.println("Found with AND filter: " + results3.size());

        // Example 4: OR filter (multiple conditions)
        LdapSearchFilter filter4 = LdapSearchFilter.or(
            LdapSearchFilter.equals("cn", "cert-001"),
            LdapSearchFilter.equals("cn", "cert-002"),
            LdapSearchFilter.equals("cn", "cert-003")
        );
        List<LdapCertificateEntry> results4 =
            queryService.findCertificatesByFilter(baseDn, filter4);
        System.out.println("Found with OR filter: " + results4.size());

        // Example 5: NOT filter (negation)
        LdapSearchFilter filter5 = LdapSearchFilter.not(
            LdapSearchFilter.equals("certificateStatus", "REVOKED")
        );
        List<LdapCertificateEntry> results5 =
            queryService.findCertificatesByFilter(baseDn, filter5);
        System.out.println("Found with NOT filter (not revoked): " + results5.size());
    }
}
```

### Example 3: Find Expiring Certificates

```java
public class ExpiringCertificatesExample {

    public static void main(String[] args) throws LdapQueryException {
        ApplicationContext context = new SpringApplication(
            Application.class).run(args);

        LdapQueryService queryService =
            context.getBean(LdapQueryService.class);
        LdapProperties props = context.getBean(LdapProperties.class);

        DistinguishedName baseDn = DistinguishedName.of(
            props.getCertificateBaseDn(props.getBase())
        );

        // Find certificates expiring within 30 days
        List<LdapCertificateEntry> expiringCerts =
            queryService.findExpiringCertificates(baseDn, 30);

        System.out.println("Certificates expiring within 30 days: " +
                         expiringCerts.size());

        for (LdapCertificateEntry cert : expiringCerts) {
            long daysRemaining = cert.getDaysUntilExpiration();
            System.out.println("  ⚠ " + cert.getDn().getValue());
            System.out.println("    Expires in: " + daysRemaining + " days");
            System.out.println("    Expiration: " + cert.getNotAfter());
            System.out.println();
        }
    }
}
```

---

## Synchronization Examples

### Example 1: Basic Sync

```java
public class BasicSyncExample {

    public static void main(String[] args) throws LdapSyncException {
        ApplicationContext context = new SpringApplication(
            Application.class).run(args);

        LdapSyncService syncService =
            context.getBean(LdapSyncService.class);

        // Load certificates from external source
        List<X509Certificate> certificates = loadCertificates();
        List<LdapCertificateEntry> entries = convertToLdapEntries(certificates);
        List<LdapAttributes> attributes = createAttributes(certificates);

        // Sync to LDAP (add or update existing)
        SyncResult result = syncService.syncCertificates(
            entries,
            attributes,
            SyncStrategy.ADD_OR_UPDATE
        );

        System.out.println("✓ Sync completed");
        System.out.println("  Processed: " + result.getProcessedCount());
        System.out.println("  Added: " + result.getAddedCount());
        System.out.println("  Updated: " + result.getUpdatedCount());
        System.out.println("  Failed: " + result.getFailureCount());
        System.out.println("  Duration: " + result.getDurationMillis() + "ms");

        if (!result.getErrors().isEmpty()) {
            System.out.println("\nErrors:");
            for (SyncError error : result.getErrors()) {
                System.out.println("  ✗ " + error.getMessage());
            }
        }
    }

    private static List<X509Certificate> loadCertificates() {
        // ... implementation ...
        return new ArrayList<>();
    }

    private static List<LdapCertificateEntry> convertToLdapEntries(
            List<X509Certificate> certs) {
        // ... implementation ...
        return new ArrayList<>();
    }

    private static List<LdapAttributes> createAttributes(
            List<X509Certificate> certs) {
        // ... implementation ...
        return new ArrayList<>();
    }
}
```

### Example 2: Scheduled Sync

```java
@Configuration
@EnableScheduling
public class SyncSchedulingConfig {

    private final LdapSyncService syncService;

    @Scheduled(cron = "0 0 2 * * *")  // Daily 2 AM
    public void scheduledSync() throws LdapSyncException {
        long startTime = System.currentTimeMillis();
        SyncLog.info("Starting scheduled LDAP sync...");

        try {
            List<X509Certificate> certificates = loadCertificatesFromSource();
            List<LdapCertificateEntry> entries = convertToLdapEntries(certificates);
            List<LdapAttributes> attributes = createAttributes(certificates);

            SyncResult result = syncService.syncCertificates(
                entries,
                attributes,
                SyncStrategy.ADD_OR_UPDATE
            );

            long duration = System.currentTimeMillis() - startTime;

            if (result.isSuccess()) {
                SyncLog.info("Sync successful: {} processed in {}ms",
                           result.getProcessedCount(), duration);
            } else {
                SyncLog.warn("Sync completed with errors: {} failed",
                           result.getFailureCount());
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            SyncLog.error("Sync failed after {}ms: {}", duration, e.getMessage());
            // Send alert/notification
            notifyAdmins("LDAP sync failed", e);
        }
    }

    private void notifyAdmins(String subject, Exception e) {
        // ... send email/SMS/Slack notification ...
    }
}
```

---

## Error Handling & Recovery

### Exception Handling Best Practices

```java
@Service
@Slf4j
public class RobustLdapOperationsService {

    private final LdapUploadService uploadService;
    private final LdapQueryService queryService;

    public void uploadWithRetry(LdapCertificateEntry entry,
                               LdapAttributes attributes,
                               int maxRetries) {
        int attempt = 0;
        long backoffMs = 1000;  // Start with 1 second

        while (attempt < maxRetries) {
            try {
                UploadResult result = uploadService.addCertificate(
                    entry, attributes
                );

                if (result.isSuccess()) {
                    log.info("Upload successful on attempt {}", attempt + 1);
                    return;
                } else {
                    throw new LdapUploadException(
                        result.getMessage(), result.getError()
                    );
                }

            } catch (LdapEntryExistsException e) {
                // Entry already exists, try update instead
                log.warn("Entry exists, attempting update: {}", e.getMessage());

                try {
                    UploadResult result = uploadService.updateCertificate(
                        entry, attributes
                    );

                    if (result.isSuccess()) {
                        log.info("Update successful");
                        return;
                    }
                } catch (LdapUploadException updateError) {
                    log.error("Update also failed", updateError);
                    throw updateError;
                }

            } catch (LdapConnectionException e) {
                // Connection error, retry with backoff
                attempt++;
                if (attempt < maxRetries) {
                    log.warn("Connection error (attempt {}/{}), retrying in {}ms: {}",
                           attempt, maxRetries, backoffMs, e.getMessage());

                    try {
                        Thread.sleep(backoffMs);
                        backoffMs *= 2;  // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Upload interrupted", ie);
                    }
                } else {
                    log.error("Max retries exceeded");
                    throw e;
                }

            } catch (LdapUploadException e) {
                // Other errors
                log.error("Upload failed: {}", e.getMessage(), e);
                throw e;
            }
        }
    }

    public Optional<LdapCertificateEntry> findWithFallback(
            DistinguishedName dn,
            String fallbackBaseDn) {
        try {
            // Try primary location
            return queryService.findCertificateByDn(dn);

        } catch (LdapQueryException e) {
            log.warn("Query failed at primary location, trying fallback: {}",
                   e.getMessage());

            try {
                // Try fallback location
                DistinguishedName fallbackDn = DistinguishedName.of(
                    dn.getRdn() + "," + fallbackBaseDn
                );
                return queryService.findCertificateByDn(fallbackDn);

            } catch (LdapQueryException fallbackError) {
                log.error("Both locations failed: {}", fallbackError.getMessage());
                return Optional.empty();
            }
        }
    }

    public void executeWithCircuitBreaker() {
        CircuitBreaker circuitBreaker = CircuitBreaker.builder()
            .failureThreshold(5)
            .successThreshold(2)
            .waitDuration(Duration.ofMinutes(1))
            .build();

        circuitBreaker.onError(event ->
            log.error("Circuit breaker opened: {}", event.getThrowable())
        );

        circuitBreaker.onSuccess(event ->
            log.info("Circuit breaker closed")
        );

        // Wrap LDAP operations with circuit breaker
        try {
            circuitBreaker.executeSupplier(() ->
                queryService.findAllCertificates(baseDn)
            );
        } catch (Exception e) {
            log.error("Circuit breaker triggered", e);
            // Fallback to cache or alternative source
        }
    }
}
```

---

## Performance & Tuning

### Connection Pool Tuning

```properties
# For HIGH throughput applications
app.ldap.pool.max-active=32      # More concurrent operations
app.ldap.pool.max-idle=16
app.ldap.pool.max-total=48
app.ldap.pool.min-idle=8
app.ldap.pool.eviction-interval-millis=180000  # 3 minutes
app.ldap.pool.test-while-idle=true

# For LOW latency applications
app.ldap.connect-timeout=10000
app.ldap.read-timeout=30000
app.ldap.pool-timeout=2000
app.ldap.pool.min-evictable-idle-time-millis=60000  # 1 minute

# For LARGE batch operations
app.ldap.batch.thread-pool-size=16
app.ldap.batch.queue-capacity=10000
app.ldap.batch.max-batch-size=2000
```

### Query Optimization

```java
// Inefficient: Get all, then filter
List<LdapCertificateEntry> all = queryService.findAllCertificates(baseDn);
List<LdapCertificateEntry> filtered = all.stream()
    .filter(cert -> cert.getCertificateType().equals("CSCA"))
    .collect(Collectors.toList());

// Optimized: Filter at server
LdapSearchFilter filter = LdapSearchFilter.equals(
    "certificateType", "CSCA"
);
List<LdapCertificateEntry> filtered =
    queryService.findCertificatesByFilter(baseDn, filter);

// Even better: Use pagination for large result sets
Page<LdapCertificateEntry> page = queryService.findCertificatesPaged(
    baseDn,
    filter,
    PageRequest.of(0, 100)
);
```

---

## Troubleshooting

### Connection Issues

```java
@Component
public class LdapDiagnostics {

    @Autowired
    private LdapHealthCheck healthCheck;

    @Scheduled(fixedDelay = 300000)  // Every 5 minutes
    public void diagnosticCheck() {
        if (healthCheck.isConnected()) {
            log.info("✓ LDAP connection healthy");
            log.info(healthCheck.getPoolStatistics());
        } else {
            log.error("✗ LDAP connection failed");
            // Trigger alerts
        }
    }

    public void printDetailedStatus() {
        System.out.println("LDAP Connection Status:");
        System.out.println("  Connected: " + healthCheck.isConnected());
        System.out.println("  " + healthCheck.getPoolStatistics());
    }
}
```

---

## Production Deployment

### Pre-Production Checklist

- [ ] Configure with production LDAP URLs (HA cluster)
- [ ] Set appropriate timeouts and pool sizes
- [ ] Enable SSL/TLS
- [ ] Configure monitoring and alerting
- [ ] Set up scheduled syncs
- [ ] Configure error notifications
- [ ] Load test with expected volume
- [ ] Plan rollback strategy

### Monitoring Metrics

```yaml
metrics:
  ldap:
    - connection_pool_active_count
    - connection_pool_idle_count
    - query_duration_ms
    - upload_success_rate
    - sync_completion_time_ms
```

### Production Configuration Example

```properties
app.ldap.urls=ldaps://ldap1.prod:636,ldaps://ldap2.prod:636,ldaps://ldap3.prod:636
app.ldap.base=dc=prod,dc=example,dc=com
app.ldap.username=${LDAP_ADMIN_DN}
app.ldap.password=${LDAP_ADMIN_PASSWORD}
app.ldap.connect-timeout=60000
app.ldap.read-timeout=120000
app.ldap.pool.max-active=50
app.ldap.pool.max-idle=25
app.ldap.pool.max-total=75
app.ldap.pool.min-idle=10
app.ldap.pool.test-on-borrow=true
app.ldap.pool.test-while-idle=true
app.ldap.sync.enabled=true
app.ldap.sync.scheduled-cron=0 0 2 * * *
app.ldap.batch.thread-pool-size=16
```

---

## Summary

This guide provides comprehensive examples and configurations for:

- **Setup & Configuration**: From local development to production
- **Common Patterns**: DI, REST, services
- **Operations**: Upload, query, sync
- **Error Handling**: Retry, fallback, circuit breaker
- **Performance**: Tuning and optimization
- **Troubleshooting**: Diagnostics and monitoring
- **Deployment**: Production best practices

For additional details, refer to the [API Reference Documentation](API_REFERENCE_LDAP_MODULE.md).

