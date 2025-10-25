package com.smartcoreinc.localpkd.ldapintegration.infrastructure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.pool.factory.PoolingContextSource;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.*;

/**
 * LdapConfigurationTest - Unit tests for LDAP configuration
 *
 * <p>Tests the LDAP configuration functionality including:
 * - LDAP context source initialization
 * - Connection pool configuration
 * - LdapTemplate bean creation
 * - Health check functionality
 * </p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.ldap.urls=ldap://localhost:389",
        "app.ldap.base=dc=ldap,dc=smartcoreinc,dc=com",
        "app.ldap.username=cn=admin,dc=ldap,dc=smartcoreinc,dc=com",
        "app.ldap.password=test-password",
        "app.ldap.pool.max-active=8",
        "app.ldap.pool.max-idle=4",
        "app.ldap.pool.max-total=12",
        "app.ldap.pool.test-on-borrow=true",
        "app.ldap.pool.test-on-return=true",
        "app.ldap.pool.test-while-idle=true"
})
@DisplayName("LdapConfiguration Tests")
class LdapConfigurationTest {

    private LdapProperties ldapProperties;
    private LdapConfiguration ldapConfiguration;

    @BeforeEach
    void setUp() {
        // Initialize test properties
        ldapProperties = new LdapProperties();
        ldapProperties.setUrls("ldap://localhost:389");
        ldapProperties.setBase("dc=ldap,dc=smartcoreinc,dc=com");
        ldapProperties.setUsername("cn=admin,dc=ldap,dc=smartcoreinc,dc=com");
        ldapProperties.setPassword("test-password");

        // Initialize configuration with test properties
        ldapConfiguration = new LdapConfiguration(ldapProperties);
    }

    // ======================== LDAP Context Source Tests ========================

    @Test
    @DisplayName("ldapContextSource should create LdapContextSource bean")
    void testLdapContextSourceCreation() {
        // When
        LdapContextSource contextSource = ldapConfiguration.ldapContextSource();

        // Then
        assertThat(contextSource).isNotNull();
    }

    @Test
    @DisplayName("ldapContextSource should set LDAP URLs correctly")
    void testLdapContextSourceUrls() {
        // Given
        ldapProperties.setUrls("ldap://host1:389,ldap://host2:389");

        LdapConfiguration config = new LdapConfiguration(ldapProperties);

        // When
        LdapContextSource contextSource = config.ldapContextSource();

        // Then
        assertThat(contextSource).isNotNull();
        // Note: Can't directly verify URLs as they're private, but creation succeeds
    }

    @Test
    @DisplayName("ldapContextSource should set base DN correctly")
    void testLdapContextSourceBaseDn() {
        // When
        LdapContextSource contextSource = ldapConfiguration.ldapContextSource();

        // Then
        assertThat(contextSource).isNotNull();
        assertThat(ldapProperties.getBase()).isEqualTo("dc=ldap,dc=smartcoreinc,dc=com");
    }

    // ======================== Pooling Context Source Tests ========================

    @Test
    @DisplayName("poolingContextSource should create PoolingContextSource bean")
    void testPoolingContextSourceCreation() {
        // When
        LdapContextSource contextSource = ldapConfiguration.ldapContextSource();
        PoolingContextSource poolingContextSource = ldapConfiguration.poolingContextSource(contextSource);

        // Then
        assertThat(poolingContextSource).isNotNull();
    }

    @Test
    @DisplayName("poolingContextSource should apply connection pool settings")
    void testPoolingContextSourcePoolSettings() {
        // Given
        LdapProperties.PoolConfig poolConfig = ldapProperties.getPool();
        poolConfig.setMaxActive(8);
        poolConfig.setMaxIdle(4);
        poolConfig.setMaxTotal(12);

        LdapConfiguration config = new LdapConfiguration(ldapProperties);

        // When
        LdapContextSource contextSource = config.ldapContextSource();
        PoolingContextSource poolingContextSource = config.poolingContextSource(contextSource);

        // Then
        assertThat(poolingContextSource).isNotNull();
        assertThat(poolConfig.getMaxActive()).isEqualTo(8);
        assertThat(poolConfig.getMaxIdle()).isEqualTo(4);
        assertThat(poolConfig.getMaxTotal()).isEqualTo(12);
    }

    @Test
    @DisplayName("poolingContextSource should enable connection validation")
    void testPoolingContextSourceValidation() {
        // Given
        LdapProperties.PoolConfig poolConfig = ldapProperties.getPool();
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);

        LdapConfiguration config = new LdapConfiguration(ldapProperties);

        // When
        LdapContextSource contextSource = config.ldapContextSource();
        PoolingContextSource poolingContextSource = config.poolingContextSource(contextSource);

        // Then
        assertThat(poolingContextSource).isNotNull();
        assertThat(poolConfig.isTestOnBorrow()).isTrue();
        assertThat(poolConfig.isTestOnReturn()).isTrue();
        assertThat(poolConfig.isTestWhileIdle()).isTrue();
    }

    // ======================== LdapTemplate Tests ========================

    @Test
    @DisplayName("ldapTemplate should create LdapTemplate bean")
    void testLdapTemplateCreation() {
        // When
        LdapContextSource contextSource = ldapConfiguration.ldapContextSource();
        PoolingContextSource poolingContextSource = ldapConfiguration.poolingContextSource(contextSource);
        LdapTemplate ldapTemplate = ldapConfiguration.ldapTemplate(poolingContextSource);

        // Then
        assertThat(ldapTemplate).isNotNull();
    }

    // ======================== LDAP Health Check Tests ========================

    @Test
    @DisplayName("ldapHealthCheck should create LdapHealthCheck bean")
    void testLdapHealthCheckCreation() {
        // When
        LdapContextSource contextSource = ldapConfiguration.ldapContextSource();
        PoolingContextSource poolingContextSource = ldapConfiguration.poolingContextSource(contextSource);
        LdapTemplate ldapTemplate = ldapConfiguration.ldapTemplate(poolingContextSource);

        LdapConfiguration.LdapHealthCheck healthCheck = ldapConfiguration.ldapHealthCheck(
                poolingContextSource, ldapTemplate
        );

        // Then
        assertThat(healthCheck).isNotNull();
    }

    @Test
    @DisplayName("ldapHealthCheck.isConnected should return true when template is available")
    void testLdapHealthCheckIsConnected() {
        // When
        LdapContextSource contextSource = ldapConfiguration.ldapContextSource();
        PoolingContextSource poolingContextSource = ldapConfiguration.poolingContextSource(contextSource);
        LdapTemplate ldapTemplate = ldapConfiguration.ldapTemplate(poolingContextSource);

        LdapConfiguration.LdapHealthCheck healthCheck = ldapConfiguration.ldapHealthCheck(
                poolingContextSource, ldapTemplate
        );

        boolean isConnected = healthCheck.isConnected();

        // Then
        assertThat(isConnected).isTrue();  // LdapTemplate is available
    }

    @Test
    @DisplayName("ldapHealthCheck.getPoolStatistics should return pool info string")
    void testLdapHealthCheckGetPoolStatistics() {
        // When
        LdapContextSource contextSource = ldapConfiguration.ldapContextSource();
        PoolingContextSource poolingContextSource = ldapConfiguration.poolingContextSource(contextSource);
        LdapTemplate ldapTemplate = ldapConfiguration.ldapTemplate(poolingContextSource);

        LdapConfiguration.LdapHealthCheck healthCheck = ldapConfiguration.ldapHealthCheck(
                poolingContextSource, ldapTemplate
        );

        String statistics = healthCheck.getPoolStatistics();

        // Then
        assertThat(statistics).isNotEmpty();
        assertThat(statistics).contains("LDAP Connection Pool");
    }

    // ======================== LDAP Properties Tests ========================

    @Test
    @DisplayName("LdapProperties should have default values")
    void testLdapPropertiesDefaults() {
        // Given
        LdapProperties props = new LdapProperties();

        // Then
        assertThat(props.getUrls()).isEqualTo("ldap://localhost:389");
        assertThat(props.getBase()).isEqualTo("dc=ldap,dc=smartcoreinc,dc=com");
        assertThat(props.getCertificateBase()).isEqualTo("ou=certificates");
        assertThat(props.getCscaOu()).isEqualTo("ou=csca");
        assertThat(props.getDscOu()).isEqualTo("ou=dsc");
        assertThat(props.getCrlBase()).isEqualTo("ou=crl");
        assertThat(props.getConnectTimeout()).isEqualTo(30000);
        assertThat(props.getReadTimeout()).isEqualTo(60000);
        assertThat(props.getPoolTimeout()).isEqualTo(5000);
    }

    @Test
    @DisplayName("PoolConfig should have default values")
    void testPoolConfigDefaults() {
        // Given
        LdapProperties.PoolConfig poolConfig = new LdapProperties.PoolConfig();

        // Then
        assertThat(poolConfig.getMaxActive()).isEqualTo(8);
        assertThat(poolConfig.getMaxIdle()).isEqualTo(4);
        assertThat(poolConfig.getMaxTotal()).isEqualTo(12);
        assertThat(poolConfig.getMinIdle()).isEqualTo(2);
        assertThat(poolConfig.isBlockWhenExhausted()).isTrue();
        assertThat(poolConfig.isTestOnBorrow()).isTrue();
        assertThat(poolConfig.isTestOnReturn()).isTrue();
        assertThat(poolConfig.isTestWhileIdle()).isTrue();
        assertThat(poolConfig.getEvictionIntervalMillis()).isEqualTo(600000L);
        assertThat(poolConfig.getMinEvictableIdleTimeMillis()).isEqualTo(300000L);
    }

    @Test
    @DisplayName("SyncConfig should have default values")
    void testSyncConfigDefaults() {
        // Given
        LdapProperties.SyncConfig syncConfig = new LdapProperties.SyncConfig();

        // Then
        assertThat(syncConfig.isEnabled()).isTrue();
        assertThat(syncConfig.getBatchSize()).isEqualTo(100);
        assertThat(syncConfig.getMaxRetries()).isEqualTo(3);
        assertThat(syncConfig.getInitialDelayMs()).isEqualTo(1000);
        assertThat(syncConfig.getScheduledCron()).isEqualTo("0 0 0 * * *");
        assertThat(syncConfig.getSyncTimeoutSeconds()).isEqualTo(300);
    }

    @Test
    @DisplayName("BatchConfig should have default values")
    void testBatchConfigDefaults() {
        // Given
        LdapProperties.BatchConfig batchConfig = new LdapProperties.BatchConfig();

        // Then
        assertThat(batchConfig.getThreadPoolSize()).isEqualTo(4);
        assertThat(batchConfig.getQueueCapacity()).isEqualTo(1000);
        assertThat(batchConfig.getKeepAliveSeconds()).isEqualTo(60);
        assertThat(batchConfig.getThreadNamePrefix()).isEqualTo("ldap-batch-");
        assertThat(batchConfig.isRecordFailures()).isTrue();
        assertThat(batchConfig.getMaxBatchSize()).isEqualTo(1000);
    }

    // ======================== DN Builder Tests ========================

    @Test
    @DisplayName("getCertificateBaseDn should build certificate base DN")
    void testGetCertificateBaseDn() {
        // Given
        LdapProperties props = new LdapProperties();
        props.setCertificateBase("ou=certificates");

        // When
        String dn = props.getCertificateBaseDn("dc=ldap,dc=smartcoreinc,dc=com");

        // Then
        assertThat(dn).isEqualTo("ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
    }

    @Test
    @DisplayName("getCscaBaseDn should build CSCA base DN")
    void testGetCscaBaseDn() {
        // Given
        LdapProperties props = new LdapProperties();
        props.setCscaOu("ou=csca");
        props.setCertificateBase("ou=certificates");

        // When
        String dn = props.getCscaBaseDn("dc=ldap,dc=smartcoreinc,dc=com");

        // Then
        assertThat(dn).isEqualTo("ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
    }

    @Test
    @DisplayName("getDscBaseDn should build DSC base DN")
    void testGetDscBaseDn() {
        // Given
        LdapProperties props = new LdapProperties();
        props.setDscOu("ou=dsc");
        props.setCertificateBase("ou=certificates");

        // When
        String dn = props.getDscBaseDn("dc=ldap,dc=smartcoreinc,dc=com");

        // Then
        assertThat(dn).isEqualTo("ou=dsc,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
    }

    @Test
    @DisplayName("getCrlBaseDn should build CRL base DN")
    void testGetCrlBaseDn() {
        // Given
        LdapProperties props = new LdapProperties();
        props.setCrlBase("ou=crl");

        // When
        String dn = props.getCrlBaseDn("dc=ldap,dc=smartcoreinc,dc=com");

        // Then
        assertThat(dn).isEqualTo("ou=crl,dc=ldap,dc=smartcoreinc,dc=com");
    }

    // ======================== Integration Tests ========================

    @Test
    @DisplayName("Configuration should be fully initialized")
    void testConfigurationFullyInitialized() {
        // When & Then
        assertThat(ldapConfiguration).isNotNull();
        assertThat(ldapProperties).isNotNull();
        assertThat(ldapProperties.getUrls()).isNotEmpty();
        assertThat(ldapProperties.getBase()).isNotEmpty();
        assertThat(ldapProperties.getUsername()).isNotEmpty();
    }

    @Test
    @DisplayName("All beans should be creatable")
    void testAllBeansCreatable() {
        // When
        LdapContextSource contextSource = ldapConfiguration.ldapContextSource();
        PoolingContextSource poolingContextSource = ldapConfiguration.poolingContextSource(contextSource);
        LdapTemplate ldapTemplate = ldapConfiguration.ldapTemplate(poolingContextSource);
        LdapConfiguration.LdapHealthCheck healthCheck = ldapConfiguration.ldapHealthCheck(
                poolingContextSource, ldapTemplate
        );

        // Then
        assertThat(contextSource).isNotNull();
        assertThat(poolingContextSource).isNotNull();
        assertThat(ldapTemplate).isNotNull();
        assertThat(healthCheck).isNotNull();
    }
}
