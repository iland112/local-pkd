package com.smartcoreinc.localpkd.passiveauthentication.infrastructure.web;

import com.smartcoreinc.localpkd.passiveauthentication.domain.model.DataGroupNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Request DTO for Passive Authentication verification.
 *
 * <p>Contains SOD (Security Object Document) and Data Groups from ePassport chip
 * for verification according to ICAO 9303 standard.
 *
 * <p><b>Validation Rules:</b>
 * <ul>
 *   <li>issuingCountry: ISO 3166-1 alpha-3 code (e.g., KOR, USA, GBR)</li>
 *   <li>documentNumber: 1-20 alphanumeric characters</li>
 *   <li>sod: Base64-encoded PKCS#7 SignedData</li>
 *   <li>dataGroups: At least one Data Group (DG1-DG16) required</li>
 * </ul>
 *
 * <p><b>Example Request:</b>
 * <pre>{@code
 * {
 *   "issuingCountry": "KOR",
 *   "documentNumber": "M12345678",
 *   "sod": "MIIGBwYJKoZIhvcNAQcCoII...",
 *   "dataGroups": {
 *     "DG1": "UEQxMjM0NTY3ODk...",
 *     "DG2": "iVBORw0KGgoAAAANS...",
 *     "DG15": "MIIDXzCCAkegAwIB..."
 *   }
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-12-12
 */
@Schema(description = "Passive Authentication 검증 요청")
public record PassiveAuthenticationRequest(

    @Schema(description = "여권 발급 국가 코드 (ISO 3166-1 alpha-3)", example = "KOR", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "^[A-Z]{3}$", message = "발급 국가 코드는 ISO 3166-1 alpha-3 형식이어야 합니다 (예: KOR, USA, GBR)")
    String issuingCountry,

    @Schema(description = "여권 번호", example = "M12345678", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(min = 1, max = 20, message = "여권 번호는 1-20자여야 합니다")
    String documentNumber,

    @Schema(description = "SOD (Security Object Document) Base64 인코딩",
            example = "MIIGBwYJKoZIhvcNAQcCoII...",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "SOD는 필수입니다")
    String sod,

    @Schema(description = "Data Groups (DG1-DG16) Base64 인코딩 Map",
            example = "{\"DG1\": \"UEQxMjM0NTY3ODk...\", \"DG2\": \"iVBORw0KGgoAAAANS...\"}",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Data Groups는 필수입니다")
    @NotEmpty(message = "최소 하나의 Data Group이 필요합니다")
    Map<String, String> dataGroups,

    @Schema(description = "요청자 식별자 (선택사항, 감사 추적용)", example = "api-client-123", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    String requestedBy

) {

    /**
     * Validates Data Group keys are valid ICAO 9303 Data Group numbers.
     *
     * @throws IllegalArgumentException if any key is not a valid Data Group number
     */
    public void validateDataGroupKeys() {
        for (String key : dataGroups.keySet()) {
            try {
                DataGroupNumber.fromString(key);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    "Invalid Data Group key: " + key + ". Must be one of DG1-DG16"
                );
            }
        }
    }

    /**
     * Checks if a specific Data Group is present in the request.
     *
     * @param dgNumber Data Group number to check
     * @return true if the Data Group is present, false otherwise
     */
    public boolean hasDataGroup(DataGroupNumber dgNumber) {
        return dataGroups.containsKey(dgNumber.name());
    }

    /**
     * Gets the count of Data Groups in the request.
     *
     * @return number of Data Groups
     */
    public int getDataGroupCount() {
        return dataGroups.size();
    }
}
