package com.smartcoreinc.localpkd.passiveauthentication.application.usecase;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCode;
import com.smartcoreinc.localpkd.passiveauthentication.application.command.PerformPassiveAuthenticationCommand;
import com.smartcoreinc.localpkd.passiveauthentication.application.response.PassiveAuthenticationResponse;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroupNumber;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassiveAuthenticationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Data Group Hash Verification Integration Tests (Phase 4.5.3)
 * <p>
 * Tests the data group hash verification against SOD:
 * - Valid data group hashes matching SOD
 * - Data group with tampered content
 * - Missing required data group
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Data Group Hash Verification Tests")
class DataGroupHashVerificationTests {

    @Autowired
    private PerformPassiveAuthenticationUseCase useCase;

    private static final String FIXTURE_BASE = "src/test/resources/passport-fixtures";

    @Test
    @DisplayName("Scenario 1: Valid data group hashes - should pass")
    void testValidDataGroupHashes() throws IOException {
        // Given: Valid Korean passport with all data groups matching SOD
        byte[] dg1 = loadFixture("valid-korean-passport/dg1.bin");
        byte[] dg2 = loadFixture("valid-korean-passport/dg2.bin");
        byte[] dg14 = loadFixture("valid-korean-passport/dg14.bin");
        byte[] sod = loadFixture("valid-korean-passport/sod.bin");

        // Build data groups map
        Map<DataGroupNumber, byte[]> dataGroups = new HashMap<>();
        dataGroups.put(DataGroupNumber.DG1, dg1);
        dataGroups.put(DataGroupNumber.DG2, dg2);
        dataGroups.put(DataGroupNumber.DG14, dg14);

        PerformPassiveAuthenticationCommand command = new PerformPassiveAuthenticationCommand(
                CountryCode.of("KR"),
                "M12345678",
                sod,
                "CN=DSC-KOREA,O=Government,C=KR",
                "A1B2C3D4",
                dataGroups,
                "127.0.0.1",
                "Test-Agent/1.0",
                "integration-test"
        );

        // When: Perform passive authentication
        PassiveAuthenticationResponse response = useCase.execute(command);

        // Then: Data group hash verification should succeed
        assertThat(response.status()).isEqualTo(PassiveAuthenticationStatus.VALID);
        assertThat(response.dataGroupValidation()).isNotNull();
        assertThat(response.dataGroupValidation().totalGroups()).isEqualTo(3);
        assertThat(response.dataGroupValidation().validGroups()).isEqualTo(3);
        assertThat(response.dataGroupValidation().invalidGroups()).isEqualTo(0);
        assertThat(response.dataGroupValidation().details()).hasSize(3);
    }

    @Test
    @DisplayName("Scenario 2: Data group with tampered content - should fail")
    void testTamperedDataGroup() throws IOException {
        // Given: Valid passport but DG1 is tampered
        byte[] dg1 = loadFixture("valid-korean-passport/dg1.bin");
        byte[] dg2 = loadFixture("valid-korean-passport/dg2.bin");
        byte[] dg14 = loadFixture("valid-korean-passport/dg14.bin");
        byte[] sod = loadFixture("valid-korean-passport/sod.bin");

        // Tamper DG1 content (flip some bytes)
        byte[] tamperedDg1 = dg1.clone();
        if (tamperedDg1.length > 10) {
            tamperedDg1[5] ^= 0xFF;
            tamperedDg1[10] ^= 0xFF;
        }

        // Build data groups map
        Map<DataGroupNumber, byte[]> dataGroups = new HashMap<>();
        dataGroups.put(DataGroupNumber.DG1, tamperedDg1);  // Use tampered version
        dataGroups.put(DataGroupNumber.DG2, dg2);
        dataGroups.put(DataGroupNumber.DG14, dg14);

        PerformPassiveAuthenticationCommand command = new PerformPassiveAuthenticationCommand(
                CountryCode.of("KR"),
                "M12345678",
                sod,
                "CN=DSC-KOREA,O=Government,C=KR",
                "A1B2C3D4",
                dataGroups,
                "127.0.0.1",
                "Test-Agent/1.0",
                "integration-test"
        );

        // When: Perform passive authentication
        PassiveAuthenticationResponse response = useCase.execute(command);

        // Then: Should fail due to hash mismatch
        assertThat(response.status()).isEqualTo(PassiveAuthenticationStatus.INVALID);
        assertThat(response.dataGroupValidation()).isNotNull();
        assertThat(response.dataGroupValidation().invalidGroups()).isGreaterThan(0);
        assertThat(response.dataGroupValidation().validGroups()).isLessThan(response.dataGroupValidation().totalGroups());

        // Check that error mentions hash or data group
        assertThat(response.errors()).isNotEmpty();
        assertThat(response.errors().get(0).getCode())
                .satisfiesAnyOf(
                        code -> assertThat(code).contains("HASH"),
                        code -> assertThat(code).contains("DATA_GROUP"),
                        code -> assertThat(code).contains("MISMATCH")
                );
    }

    @Test
    @DisplayName("Scenario 3: Missing required data group - should fail")
    void testMissingRequiredDataGroup() throws IOException {
        // Given: Passport with missing DG1 (which is mandatory)
        byte[] dg2 = loadFixture("valid-korean-passport/dg2.bin");
        byte[] sod = loadFixture("valid-korean-passport/sod.bin");

        // Build data groups map WITHOUT DG1 (mandatory)
        Map<DataGroupNumber, byte[]> dataGroups = new HashMap<>();
        dataGroups.put(DataGroupNumber.DG2, dg2);
        // DG1 is intentionally missing

        PerformPassiveAuthenticationCommand command = new PerformPassiveAuthenticationCommand(
                CountryCode.of("KR"),
                "M12345678",
                sod,
                "CN=DSC-KOREA,O=Government,C=KR",
                "A1B2C3D4",
                dataGroups,
                "127.0.0.1",
                "Test-Agent/1.0",
                "integration-test"
        );

        // When: Perform passive authentication
        PassiveAuthenticationResponse response = useCase.execute(command);

        // Then: Should fail due to missing data group
        assertThat(response.status()).isEqualTo(PassiveAuthenticationStatus.INVALID);

        // Error should mention missing data group
        assertThat(response.errors()).isNotEmpty();
        assertThat(response.errors().get(0).getCode())
                .satisfiesAnyOf(
                        code -> assertThat(code).contains("MISSING"),
                        code -> assertThat(code).contains("DATA_GROUP"),
                        code -> assertThat(code).contains("REQUIRED")
                );
    }

    /**
     * Helper method to load test fixture files
     */
    private byte[] loadFixture(String relativePath) throws IOException {
        Path fixturePath = Path.of(FIXTURE_BASE, relativePath);
        if (!Files.exists(fixturePath)) {
            throw new IllegalStateException("Fixture not found: " + fixturePath);
        }
        return Files.readAllBytes(fixturePath);
    }
}
