package com.smartcoreinc.localpkd.common.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * EntryType 테스트
 * 실제 ICAO PKD LDIF 파일의 objectClass로 EntryType 감지 테스트
 */
class EntryTypeTest {

    @Test
    void testCertificateEntryWithPkdDownload() {
        // Given - 실제 LDIF 파일에서 사용되는 objectClass
        String[] objectClasses = {"top", "inetOrgPerson", "pkdDownload"};

        // When
        EntryType type = EntryType.fromObjectClasses(objectClasses);

        // Then
        assertEquals(EntryType.CERTIFICATE, type);
        assertTrue(type.isCertificate());
    }

    @Test
    void testCertificateEntryWithPkdMasterList() {
        // Given - Master List entry
        String[] objectClasses = {"top", "person", "pkdMasterList", "pkdDownload"};

        // When
        EntryType type = EntryType.fromObjectClasses(objectClasses);

        // Then
        assertEquals(EntryType.CERTIFICATE, type);
    }

    @Test
    void testCertificateEntryWithPerson() {
        // Given - 인증서를 담는 person 객체
        String[] objectClasses = {"top", "person", "organizationalPerson", "inetOrgPerson"};

        // When
        EntryType type = EntryType.fromObjectClasses(objectClasses);

        // Then
        assertEquals(EntryType.CERTIFICATE, type);
    }

    @Test
    void testCrlEntry() {
        // Given - CRL entry
        String[] objectClasses = {"top", "cRLDistributionPoint", "pkdDownload"};

        // When
        EntryType type = EntryType.fromObjectClasses(objectClasses);

        // Then
        assertEquals(EntryType.CRL, type);
        assertTrue(type.isCrl());
    }

    @Test
    void testCrlEntryLowerCase() {
        // Given - CRL entry (소문자)
        String[] objectClasses = {"top", "crldistributionpoint"};

        // When
        EntryType type = EntryType.fromObjectClasses(objectClasses);

        // Then
        assertEquals(EntryType.CRL, type);
    }

    @Test
    void testUnknownEntry() {
        // Given - 도메인/조직 entry
        String[] objectClasses = {"top", "domain"};

        // When
        EntryType type = EntryType.fromObjectClasses(objectClasses);

        // Then
        assertEquals(EntryType.UNKNOWN, type);
        assertTrue(type.isUnknown());
    }

    @Test
    void testOrganizationEntry() {
        // Given - 조직 entry
        String[] objectClasses = {"top", "organization"};

        // When
        EntryType type = EntryType.fromObjectClasses(objectClasses);

        // Then
        assertEquals(EntryType.UNKNOWN, type);
    }

    @Test
    void testCountryEntry() {
        // Given - 국가 entry
        String[] objectClasses = {"top", "country"};

        // When
        EntryType type = EntryType.fromObjectClasses(objectClasses);

        // Then
        assertEquals(EntryType.UNKNOWN, type);
    }

    @Test
    void testNullObjectClasses() {
        // Given
        String[] objectClasses = null;

        // When
        EntryType type = EntryType.fromObjectClasses(objectClasses);

        // Then
        assertEquals(EntryType.UNKNOWN, type);
    }

    @Test
    void testEmptyObjectClasses() {
        // Given
        String[] objectClasses = new String[0];

        // When
        EntryType type = EntryType.fromObjectClasses(objectClasses);

        // Then
        assertEquals(EntryType.UNKNOWN, type);
    }

    @Test
    void testCaseInsensitivity() {
        // Given - 대소문자 혼합
        String[] objectClasses = {"TOP", "PkdDownload", "INETORGPERSON"};

        // When
        EntryType type = EntryType.fromObjectClasses(objectClasses);

        // Then
        assertEquals(EntryType.CERTIFICATE, type);
    }
}
