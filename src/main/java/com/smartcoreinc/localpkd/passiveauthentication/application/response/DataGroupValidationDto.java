package com.smartcoreinc.localpkd.passiveauthentication.application.response;

import com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroupNumber;

import java.util.Map;

/**
 * DTO representing Data Group Hash Validation result.
 * <p>
 * This DTO encapsulates the result of verifying that each Data Group's
 * calculated hash matches the hash stored in the SOD.
 * </p>
 *
 * <h3>Validation Process:</h3>
 * <ol>
 *   <li>Extract expected hashes from SOD</li>
 *   <li>Calculate actual hash for each Data Group</li>
 *   <li>Compare expected vs actual hashes</li>
 *   <li>Report mismatches</li>
 * </ol>
 *
 * <h3>Data Group Types:</h3>
 * <ul>
 *   <li>DG1: MRZ (Machine Readable Zone)</li>
 *   <li>DG2: Encoded Face</li>
 *   <li>DG3: Encoded Fingerprints</li>
 *   <li>DG4: Encoded Iris</li>
 *   <li>DG5-DG16: Additional biometric/biographical data</li>
 * </ul>
 *
 * @param totalGroups Total number of data groups verified
 * @param validGroups Number of data groups with matching hashes
 * @param invalidGroups Number of data groups with mismatched hashes
 * @param details Map of DataGroupNumber to DataGroupDetailDto
 *
 * @see DataGroupDetailDto
 */
public record DataGroupValidationDto(
    int totalGroups,
    int validGroups,
    int invalidGroups,
    Map<DataGroupNumber, DataGroupDetailDto> details
) {
    /**
     * Nested DTO for individual Data Group validation details.
     *
     * @param valid True if hash matches
     * @param expectedHash Expected hash from SOD (hex string)
     * @param actualHash Calculated hash (hex string)
     */
    public record DataGroupDetailDto(
        boolean valid,
        String expectedHash,
        String actualHash
    ) {
    }
}
