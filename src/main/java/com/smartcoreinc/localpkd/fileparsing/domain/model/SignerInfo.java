package com.smartcoreinc.localpkd.fileparsing.domain.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * SignerInfo - Information about the entity that signed the Master List
 *
 * <p>Stores metadata about the Master List signer in JSON format, including:
 * <ul>
 *   <li>signer DN (Distinguished Name)</li>
 *   <li>signature algorithm</li>
 *   <li>signing time</li>
 *   <li>certificate serial number</li>
 * </ul>
 * </p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-27
 */
@Slf4j
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SignerInfo {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Column(name = "signer_info", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> value;

    private SignerInfo(Map<String, Object> value) {
        validate(value);
        this.value = new HashMap<>(value);  // Defensive copy
    }

    public static SignerInfo of(Map<String, Object> value) {
        return new SignerInfo(value);
    }

    public static SignerInfo empty() {
        return new SignerInfo(new HashMap<>());
    }

    /**
     * Create SignerInfo from individual components
     */
    public static SignerInfo create(String signerDn, String signatureAlgorithm,
                                     String signingTime, String serialNumber) {
        Map<String, Object> info = new HashMap<>();
        info.put("signerDn", signerDn);
        info.put("signatureAlgorithm", signatureAlgorithm);
        info.put("signingTime", signingTime);
        info.put("serialNumber", serialNumber);
        return new SignerInfo(info);
    }

    private void validate(Map<String, Object> value) {
        if (value == null) {
            throw new IllegalArgumentException("SignerInfo map cannot be null");
        }
        // Allow empty map for cases where signer info is not yet extracted
    }

    /**
     * Get a defensive copy of the signer info map
     */
    public Map<String, Object> getValue() {
        return value != null ? new HashMap<>(value) : new HashMap<>();
    }

    /**
     * Get signer DN if available
     */
    public String getSignerDn() {
        return value != null ? (String) value.get("signerDn") : null;
    }

    /**
     * Get signature algorithm if available
     */
    public String getSignatureAlgorithm() {
        return value != null ? (String) value.get("signatureAlgorithm") : null;
    }

    /**
     * Get signing time if available
     */
    public String getSigningTime() {
        return value != null ? (String) value.get("signingTime") : null;
    }

    /**
     * Get serial number if available
     */
    public String getSerialNumber() {
        return value != null ? (String) value.get("serialNumber") : null;
    }

    /**
     * Convert to JSON string for debugging
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to convert SignerInfo to JSON", e);
            return value != null ? value.toString() : "{}";
        }
    }

    @Override
    public String toString() {
        return "SignerInfo" + toJson();
    }
}
