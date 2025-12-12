package com.smartcoreinc.localpkd.passiveauthentication.application.command;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCode;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroupNumber;

import java.util.Map;

/**
 * Command for performing Passive Authentication (PA) verification on ePassport data.
 * <p>
 * This command encapsulates all necessary data for verifying the integrity of
 * electronic passport chips according to ICAO 9303 Part 11 standard.
 * </p>
 *
 * <h3>Passive Authentication Process:</h3>
 * <ol>
 *   <li>Certificate Chain Validation: Verify DSC is signed by CSCA</li>
 *   <li>SOD Signature Verification: Verify SOD is signed by DSC</li>
 *   <li>Data Group Hash Verification: Verify each DG hash matches SOD</li>
 * </ol>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * PerformPassiveAuthenticationCommand command = new PerformPassiveAuthenticationCommand(
 *     CountryCode.of("KR"),
 *     "M12345678",
 *     sodBytes,
 *     "CN=DS-KOREA,O=Government,C=KR",
 *     "A1B2C3D4",
 *     Map.of(
 *         DataGroupNumber.DG1, dg1Bytes,
 *         DataGroupNumber.DG2, dg2Bytes
 *     ),
 *     "192.168.1.100",
 *     "Mozilla/5.0...",
 *     "api-user-123"
 * );
 * }</pre>
 *
 * @param issuingCountry ISO 3166-1 alpha-3 country code (e.g., "KOR", "USA", "FRA")
 * @param documentNumber Passport document number (e.g., "M12345678")
 * @param sodBytes Security Object Document (PKCS#7 SignedData) binary
 * @param dscSubjectDn Document Signer Certificate Subject DN
 * @param dscSerialNumber DSC serial number (hex string)
 * @param dataGroups Map of Data Group numbers to binary content (DG1-DG16)
 * @param requestIpAddress Client IP address (IPv4 or IPv6)
 * @param requestUserAgent HTTP User-Agent header
 * @param requestedBy User ID, API key, or system name
 *
 * @see com.smartcoreinc.localpkd.passiveauthentication.application.usecase.PerformPassiveAuthenticationUseCase
 * @see com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassportData
 */
public record PerformPassiveAuthenticationCommand(
    CountryCode issuingCountry,
    String documentNumber,
    byte[] sodBytes,
    String dscSubjectDn,
    String dscSerialNumber,
    Map<DataGroupNumber, byte[]> dataGroups,
    String requestIpAddress,
    String requestUserAgent,
    String requestedBy
) {
    /**
     * Validates command inputs.
     *
     * @throws IllegalArgumentException if any required field is null or invalid
     */
    public PerformPassiveAuthenticationCommand {
        if (issuingCountry == null) {
            throw new IllegalArgumentException("Issuing country cannot be null");
        }
        if (documentNumber == null || documentNumber.isBlank()) {
            throw new IllegalArgumentException("Document number cannot be null or empty");
        }
        if (sodBytes == null || sodBytes.length == 0) {
            throw new IllegalArgumentException("SOD bytes cannot be null or empty");
        }
        if (dscSubjectDn == null || dscSubjectDn.isBlank()) {
            throw new IllegalArgumentException("DSC Subject DN cannot be null or empty");
        }
        if (dscSerialNumber == null || dscSerialNumber.isBlank()) {
            throw new IllegalArgumentException("DSC Serial Number cannot be null or empty");
        }
        if (dataGroups == null || dataGroups.isEmpty()) {
            throw new IllegalArgumentException("Data groups cannot be null or empty");
        }
        if (requestIpAddress == null || requestIpAddress.isBlank()) {
            throw new IllegalArgumentException("Request IP address cannot be null or empty");
        }

        // Make defensive copies to ensure immutability
        dataGroups = Map.copyOf(dataGroups);
    }
}
