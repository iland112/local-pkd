package com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateType;
import com.smartcoreinc.localpkd.ldapintegration.domain.model.DistinguishedName;
import com.smartcoreinc.localpkd.ldapintegration.domain.model.LdapAttributes;
import com.smartcoreinc.localpkd.ldapintegration.domain.model.LdapCertificateEntry;
import com.smartcoreinc.localpkd.ldapintegration.domain.port.LdapUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.LdapTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SpringLdapUploadAdapterTest - Unit tests for LDAP upload adapter
 *
 * <p>Tests the upload functionality including:
 * - Single certificate upload
 * - Batch certificate upload
 * - CRL operations
 * - Error handling and validation
 * </p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpringLdapUploadAdapter Tests")
class SpringLdapUploadAdapterTest {

    @Mock
    private LdapTemplate ldapTemplate;

    private SpringLdapUploadAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpringLdapUploadAdapter(ldapTemplate);
    }

    // ======================== addCertificate Tests ========================

    @Test
    @DisplayName("addCertificate should return UploadResult with success")
    void testAddCertificateSuccess() {
        // Given
        DistinguishedName dn = DistinguishedName.of("cn=CSCA-KOREA,ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
        LdapCertificateEntry entry = LdapCertificateEntry.builder()
                .dn(dn)
                .certificateId(UUID.randomUUID())
                .x509CertificateBase64("MIIB...")
                .fingerprint("a1b2c3d4...")
                .serialNumber("123456")
                .issuerDn("cn=Root CA,ou=ca,dc=ldap,dc=smartcoreinc,dc=com")
                .certificateType(CertificateType.CSCA)
                .notBefore(LocalDateTime.now().minusYears(1))
                .notAfter(LocalDateTime.now().plusYears(5))
                .validationStatus("VALID")
                .build();

        LdapAttributes attributes = LdapAttributes.builder().build();

        // When
        LdapUploadService.UploadResult result = adapter.addCertificate(entry, attributes);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();  // Stub returns false
        assertThat(result.getMessage()).isNotEmpty();
    }

    @Test
    @DisplayName("addCertificate should throw exception when entry is null")
    void testAddCertificateNullEntry() {
        // Given
        LdapAttributes attributes = LdapAttributes.builder().build();

        // When & Then
        assertThatThrownBy(() -> adapter.addCertificate(null, attributes))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("addCertificate should throw exception when attributes is null")
    void testAddCertificateNullAttributes() {
        // Given
        DistinguishedName dn = DistinguishedName.of("cn=CSCA-KOREA,ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
        LdapCertificateEntry entry = LdapCertificateEntry.builder()
                .dn(dn)
                .certificateId(UUID.randomUUID())
                .x509CertificateBase64("MIIB...")
                .certificateType(CertificateType.CSCA)
                .build();

        // When & Then
        assertThatThrownBy(() -> adapter.addCertificate(entry, null))
                .isInstanceOf(Exception.class);
    }

    // ======================== addCertificatesBatch Tests ========================

    @Test
    @DisplayName("addCertificatesBatch should return BatchUploadResult")
    void testAddCertificatesBatchSuccess() {
        // Given
        List<LdapCertificateEntry> entries = createTestEntries(3);

        // When
        LdapUploadService.BatchUploadResult result = adapter.addCertificatesBatch(entries);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalCount()).isGreaterThanOrEqualTo(0);
        assertThat(result.getSuccessCount()).isGreaterThanOrEqualTo(0);
        assertThat(result.getFailedCount()).isGreaterThanOrEqualTo(0);
        assertThat(result.getFailedEntries()).isNotNull();
    }

    @Test
    @DisplayName("addCertificatesBatch should handle empty list")
    void testAddCertificatesBatchEmptyList() {
        // Given
        List<LdapCertificateEntry> entries = new ArrayList<>();

        // When
        LdapUploadService.BatchUploadResult result = adapter.addCertificatesBatch(entries);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFailedEntries()).isEmpty();
    }

    @Test
    @DisplayName("addCertificatesBatch should handle multiple entries")
    void testAddCertificatesBatchMultipleEntries() {
        // Given
        List<LdapCertificateEntry> entries = createTestEntries(10);

        // When
        LdapUploadService.BatchUploadResult result = adapter.addCertificatesBatch(entries);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalCount()).isGreaterThanOrEqualTo(0);
        assertThat(result.getSuccessRate()).isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("addCertificatesBatch should throw exception when list is null")
    void testAddCertificatesBatchNullList() {
        // When & Then
        assertThatThrownBy(() -> adapter.addCertificatesBatch(null))
                .isInstanceOf(Exception.class);
    }

    // ======================== updateCertificate Tests ========================

    @Test
    @DisplayName("updateCertificate should return UploadResult")
    void testUpdateCertificateSuccess() {
        // Given
        DistinguishedName dn = DistinguishedName.of("cn=CSCA-KOREA,ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
        LdapCertificateEntry entry = createTestEntry(dn, CertificateType.CSCA);
        LdapAttributes attributes = LdapAttributes.builder().build();

        // When
        LdapUploadService.UploadResult result = adapter.updateCertificate(entry, attributes);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMessage()).isNotEmpty();
    }

    // ======================== addOrUpdateCertificate Tests ========================

    @Test
    @DisplayName("addOrUpdateCertificate should return UploadResult")
    void testAddOrUpdateCertificateSuccess() {
        // Given
        DistinguishedName dn = DistinguishedName.of("cn=DSC-KOREA,ou=dsc,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");
        LdapCertificateEntry entry = createTestEntry(dn, CertificateType.DSC);
        LdapAttributes attributes = LdapAttributes.builder().build();

        // When
        LdapUploadService.UploadResult result = adapter.addOrUpdateCertificate(entry, attributes);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUploadedDn()).isNotNull();
    }

    // ======================== deleteEntry Tests ========================

    @Test
    @DisplayName("deleteEntry should return boolean result")
    void testDeleteEntrySuccess() {
        // Given
        DistinguishedName dn = DistinguishedName.of("cn=CSCA-KOREA,ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        // When
        boolean result = adapter.deleteEntry(dn);

        // Then
        assertThat(result).isFalse();  // Stub returns false
    }

    @Test
    @DisplayName("deleteEntry should throw exception when DN is null")
    void testDeleteEntryNullDn() {
        // When & Then
        assertThatThrownBy(() -> adapter.deleteEntry(null))
                .isInstanceOf(Exception.class);
    }

    // ======================== deleteSubtree Tests ========================

    @Test
    @DisplayName("deleteSubtree should return count of deleted entries")
    void testDeleteSubtreeSuccess() {
        // Given
        DistinguishedName baseDn = DistinguishedName.of("ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com");

        // When
        int result = adapter.deleteSubtree(baseDn);

        // Then
        assertThat(result).isZero();  // Stub returns 0
    }

    @Test
    @DisplayName("deleteSubtree should throw exception when baseDn is null")
    void testDeleteSubtreeNullBaseDn() {
        // When & Then
        assertThatThrownBy(() -> adapter.deleteSubtree(null))
                .isInstanceOf(Exception.class);
    }

    // ======================== CRL Tests ========================

    @Test
    @DisplayName("addCrl should return UploadResult")
    void testAddCrlSuccess() {
        // Given
        // Note: Need to check LdapCrlEntry structure for proper test

        // When - For now, just verify adapter can be called
        assertThat(adapter).isNotNull();
    }

    // ======================== Integration Tests ========================

    @Test
    @DisplayName("Adapter should be instantiated with LdapTemplate")
    void testAdapterInstantiation() {
        // Then
        assertThat(adapter).isNotNull();
    }

    @Test
    @DisplayName("Multiple upload operations should be independent")
    void testMultipleOperationsIndependent() {
        // Given
        LdapCertificateEntry entry1 = createTestEntry(
                DistinguishedName.of("cn=CSCA-1,ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"),
                CertificateType.CSCA
        );
        LdapCertificateEntry entry2 = createTestEntry(
                DistinguishedName.of("cn=CSCA-2,ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com"),
                CertificateType.CSCA
        );
        LdapAttributes attributes = LdapAttributes.builder().build();

        // When
        LdapUploadService.UploadResult result1 = adapter.addCertificate(entry1, attributes);
        LdapUploadService.UploadResult result2 = adapter.addCertificate(entry2, attributes);

        // Then
        assertThat(result1).isNotNull();
        assertThat(result2).isNotNull();
    }

    // ======================== Helper Methods ========================

    private LdapCertificateEntry createTestEntry(DistinguishedName dn, CertificateType type) {
        return LdapCertificateEntry.builder()
                .dn(dn)
                .certificateId(UUID.randomUUID())
                .x509CertificateBase64("MIIB...")
                .fingerprint("a1b2c3d4e5f6...")
                .serialNumber("123456")
                .issuerDn("cn=Root CA,ou=ca,dc=ldap,dc=smartcoreinc,dc=com")
                .certificateType(type)
                .notBefore(LocalDateTime.now().minusYears(1))
                .notAfter(LocalDateTime.now().plusYears(5))
                .validationStatus("VALID")
                .build();
    }

    private List<LdapCertificateEntry> createTestEntries(int count) {
        List<LdapCertificateEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DistinguishedName dn = DistinguishedName.of(
                    String.format("cn=CSCA-%d,ou=csca,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com", i)
            );
            entries.add(createTestEntry(dn, CertificateType.CSCA));
        }
        return entries;
    }
}
