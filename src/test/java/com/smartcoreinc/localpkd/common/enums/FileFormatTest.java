package com.smartcoreinc.localpkd.common.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * FileFormat 테스트
 * 실제 ICAO PKD 파일명으로 FileFormat 감지 테스트
 */
class FileFormatTest {

    @Test
    void testMasterListFilename() {
        // Given
        String filename = "ICAO_ml_July2025.ml";

        // When
        FileFormat format = FileFormat.detectFromFilename(filename);

        // Then
        assertEquals(FileFormat.ML_SIGNED_CMS, format);
        assertEquals("July2025", FileFormat.extractVersion(filename));
        assertEquals("002", FileFormat.extractCollectionNumber(filename));  // ML은 Collection #002 (CSCA)와 동일
    }

    @Test
    void testCscaCompleteLdif() {
        // Given
        String filename = "icaopkd-001-complete-009409.ldif";

        // When
        FileFormat format = FileFormat.detectFromFilename(filename);

        // Then
        assertEquals(FileFormat.CSCA_COMPLETE_LDIF, format);
        assertEquals("009409", FileFormat.extractVersion(filename));
        assertEquals("001", FileFormat.extractCollectionNumber(filename));
        assertNull(FileFormat.extractDeltaType(filename));
        assertFalse(format.isDelta());
    }

    @Test
    void testCscaDeltaLdif() {
        // Given
        String filename = "icaopkd-001-delta-009400.ldif";

        // When
        FileFormat format = FileFormat.detectFromFilename(filename);

        // Then
        assertEquals(FileFormat.CSCA_DELTA_LDIF, format);
        assertEquals("009400", FileFormat.extractVersion(filename));
        assertEquals("001", FileFormat.extractCollectionNumber(filename));
        assertEquals("delta", FileFormat.extractDeltaType(filename));
        assertTrue(format.isDelta());
    }

    @Test
    void testEmrtdCompleteLdif() {
        // Given
        String filename = "icaopkd-002-complete-000325.ldif";

        // When
        FileFormat format = FileFormat.detectFromFilename(filename);

        // Then
        assertEquals(FileFormat.EMRTD_COMPLETE_LDIF, format);
        assertEquals("000325", FileFormat.extractVersion(filename));
        assertEquals("002", FileFormat.extractCollectionNumber(filename));
        assertNull(FileFormat.extractDeltaType(filename));
        assertFalse(format.isDelta());
    }

    @Test
    void testEmrtdDeltaLdif() {
        // Given
        String filename = "icaopkd-002-delta-000318.ldif";

        // When
        FileFormat format = FileFormat.detectFromFilename(filename);

        // Then
        assertEquals(FileFormat.EMRTD_DELTA_LDIF, format);
        assertEquals("000318", FileFormat.extractVersion(filename));
        assertEquals("002", FileFormat.extractCollectionNumber(filename));
        assertEquals("delta", FileFormat.extractDeltaType(filename));
        assertTrue(format.isDelta());
    }

    @Test
    void testNonConformantCompleteLdif() {
        // Given
        String filename = "icaopkd-003-complete-000090.ldif";

        // When
        FileFormat format = FileFormat.detectFromFilename(filename);

        // Then
        assertEquals(FileFormat.NON_CONFORMANT_COMPLETE_LDIF, format);
        assertEquals("000090", FileFormat.extractVersion(filename));
        assertEquals("003", FileFormat.extractCollectionNumber(filename));
        assertNull(FileFormat.extractDeltaType(filename));
        assertTrue(format.isDeprecated());
    }

    @Test
    void testNonConformantDeltaLdif() {
        // Given
        String filename = "icaopkd-003-delta-000081.ldif";

        // When
        FileFormat format = FileFormat.detectFromFilename(filename);

        // Then
        assertEquals(FileFormat.NON_CONFORMANT_DELTA_LDIF, format);
        assertEquals("000081", FileFormat.extractVersion(filename));
        assertEquals("003", FileFormat.extractCollectionNumber(filename));
        assertEquals("delta", FileFormat.extractDeltaType(filename));
        assertTrue(format.isDelta());
        assertTrue(format.isDeprecated());
    }

    @Test
    void testInvalidFilename() {
        // Given
        String filename = "invalid-file.txt";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            FileFormat.detectFromFilename(filename);
        });
    }

    @Test
    void testFileTypeMapping() {
        assertEquals(FileType.CSCA_MASTER_LIST, FileFormat.ML_SIGNED_CMS.getFileType());
        assertEquals(FileType.CSCA_MASTER_LIST, FileFormat.CSCA_COMPLETE_LDIF.getFileType());
        assertEquals(FileType.CSCA_MASTER_LIST, FileFormat.CSCA_DELTA_LDIF.getFileType());

        assertEquals(FileType.EMRTD_PKI_OBJECTS, FileFormat.EMRTD_COMPLETE_LDIF.getFileType());
        assertEquals(FileType.EMRTD_PKI_OBJECTS, FileFormat.EMRTD_DELTA_LDIF.getFileType());

        assertEquals(FileType.NON_CONFORMANT, FileFormat.NON_CONFORMANT_COMPLETE_LDIF.getFileType());
        assertEquals(FileType.NON_CONFORMANT, FileFormat.NON_CONFORMANT_DELTA_LDIF.getFileType());
    }
}
