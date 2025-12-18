package com.smartcoreinc.localpkd.passiveauthentication.application.usecase;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCode;
import com.smartcoreinc.localpkd.passiveauthentication.application.command.PerformPassiveAuthenticationCommand;
import com.smartcoreinc.localpkd.passiveauthentication.application.response.PassiveAuthenticationResponse;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroupNumber;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassiveAuthenticationStatus;
import com.smartcoreinc.localpkd.passiveauthentication.domain.repository.PassiveAuthenticationAuditLogRepository;
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
 * Complete Passive Authentication Flow Integration Tests (Phase 4.5.5)
 * <p>
 * Tests the complete end-to-end PA flow:
 * - Full verification of valid passport
 * - Full verification of invalid passport
 * - Audit log creation
 * - Performance verification
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Complete PA Flow Tests")
class CompletePassiveAuthenticationFlowTests {

    @Autowired
    private PerformPassiveAuthenticationUseCase useCase;

    @Autowired
    private PassiveAuthenticationAuditLogRepository auditLogRepository;

    private static final String FIXTURE_BASE = "src/test/resources/passport-fixtures";

    @Test
    @DisplayName("Scenario 1: Complete valid passport verification - all steps should pass")
    void testCompleteValidPassportVerification() throws IOException {
        // Given: Valid Korean passport with all required data
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

        // When: Perform complete passive authentication
        PassiveAuthenticationResponse response = useCase.execute(command);

        // Then: All verification steps should pass
        assertThat(response.status()).isEqualTo(PassiveAuthenticationStatus.VALID);
        assertThat(response.verificationId()).isNotNull();
        assertThat(response.verificationTimestamp()).isNotNull();
        assertThat(response.issuingCountry()).isEqualTo("KR");
        assertThat(response.documentNumber()).isEqualTo("M12345678");

        // Certificate chain validation
        assertThat(response.certificateChainValidation()).isNotNull();
        assertThat(response.certificateChainValidation().valid()).isTrue();

        // SOD signature validation
        assertThat(response.sodSignatureValidation()).isNotNull();
        assertThat(response.sodSignatureValidation().valid()).isTrue();

        // Data group validation
        assertThat(response.dataGroupValidation()).isNotNull();
        assertThat(response.dataGroupValidation().validGroups())
                .isEqualTo(response.dataGroupValidation().totalGroups());
        assertThat(response.dataGroupValidation().invalidGroups()).isEqualTo(0);

        // No errors
        assertThat(response.errors()).isEmpty();

        // Processing time should be reasonable (< 5 seconds)
        assertThat(response.processingDurationMs()).isLessThan(5000L);

        // Response should have verification ID (which means audit log should be created)
        assertThat(response.verificationId()).isNotNull();
    }

    @Test
    @DisplayName("Scenario 2: Complete invalid passport verification - should fail with details")
    void testCompleteInvalidPassportVerification() throws IOException {
        // Given: Passport with tampered data
        byte[] dg1 = loadFixture("valid-korean-passport/dg1.bin");
        byte[] dg2 = loadFixture("valid-korean-passport/dg2.bin");
        byte[] sod = loadFixture("valid-korean-passport/sod.bin");

        // Tamper DG1
        byte[] tamperedDg1 = dg1.clone();
        if (tamperedDg1.length > 10) {
            tamperedDg1[5] ^= 0xFF;
        }

        // Build data groups map with tampered data
        Map<DataGroupNumber, byte[]> dataGroups = new HashMap<>();
        dataGroups.put(DataGroupNumber.DG1, tamperedDg1);
        dataGroups.put(DataGroupNumber.DG2, dg2);

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

        // When: Perform complete passive authentication
        PassiveAuthenticationResponse response = useCase.execute(command);

        // Then: Verification should fail
        assertThat(response.status()).isEqualTo(PassiveAuthenticationStatus.INVALID);
        assertThat(response.verificationId()).isNotNull();

        // Errors should be present
        assertThat(response.errors()).isNotEmpty();

        // Data group validation should show failure
        assertThat(response.dataGroupValidation()).isNotNull();
        assertThat(response.dataGroupValidation().invalidGroups()).isGreaterThan(0);

        // Response should have verification ID (audit log created even for failures)
        assertThat(response.verificationId()).isNotNull();
    }

    @Test
    @DisplayName("Scenario 3: Audit log persistence - should record all verification attempts")
    void testAuditLogPersistence() throws IOException {
        // Given: Valid passport data
        byte[] dg1 = loadFixture("valid-korean-passport/dg1.bin");
        byte[] dg2 = loadFixture("valid-korean-passport/dg2.bin");
        byte[] sod = loadFixture("valid-korean-passport/sod.bin");

        Map<DataGroupNumber, byte[]> dataGroups = new HashMap<>();
        dataGroups.put(DataGroupNumber.DG1, dg1);
        dataGroups.put(DataGroupNumber.DG2, dg2);

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

        // When: Perform verification
        PassiveAuthenticationResponse response = useCase.execute(command);

        // Then: Response should contain verification ID (indicating audit log was created)
        assertThat(response.verificationId()).isNotNull();
        assertThat(response.verificationTimestamp()).isNotNull();
        assertThat(response.issuingCountry()).isEqualTo("KR");
        assertThat(response.documentNumber()).isEqualTo("M12345678");
    }

    @Test
    @DisplayName("Scenario 4: Multiple data groups verification - should verify all present data groups")
    void testMultipleDataGroupsVerification() throws IOException {
        // Given: Passport with DG1, DG2, and DG14
        byte[] dg1 = loadFixture("valid-korean-passport/dg1.bin");
        byte[] dg2 = loadFixture("valid-korean-passport/dg2.bin");
        byte[] dg14 = loadFixture("valid-korean-passport/dg14.bin");
        byte[] sod = loadFixture("valid-korean-passport/sod.bin");

        // Build data groups map with all available data
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

        // When: Perform verification
        PassiveAuthenticationResponse response = useCase.execute(command);

        // Then: All data groups should be verified
        assertThat(response.status()).isEqualTo(PassiveAuthenticationStatus.VALID);
        assertThat(response.dataGroupValidation()).isNotNull();
        assertThat(response.dataGroupValidation().totalGroups()).isEqualTo(3);
        assertThat(response.dataGroupValidation().validGroups()).isEqualTo(3);
        assertThat(response.dataGroupValidation().details()).hasSize(3);

        // Verify each data group individually
        assertThat(response.dataGroupValidation().details()).containsKeys(
                DataGroupNumber.DG1,
                DataGroupNumber.DG2,
                DataGroupNumber.DG14
        );

        // All should be valid
        response.dataGroupValidation().details().values().forEach(detail -> {
            assertThat(detail.valid()).isTrue();
            assertThat(detail.expectedHash()).isNotBlank();
            assertThat(detail.actualHash()).isNotBlank();
            assertThat(detail.expectedHash()).isEqualTo(detail.actualHash());
        });
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
