package com.smartcoreinc.localpkd.passiveauthentication.infrastructure.adapter;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * DG1 (Data Group 1) MRZ Parser.
 * <p>
 * Parses Machine Readable Zone (MRZ) data from ePassport DG1 according to ICAO 9303.
 * </p>
 *
 * <h3>DG1 Structure:</h3>
 * <pre>
 * Tag 0x61 (Application 1) - DG1 wrapper
 *   └─ Tag 0x5F1F - MRZ Info
 *       └─ OCTET STRING (ASCII characters)
 * </pre>
 *
 * <h3>TD3 MRZ Format (2 lines × 44 characters):</h3>
 * <pre>
 * Line 1: P<KORHONG<GILDONG<<<<<<<<<<<<<<<<<<<<<<
 * Line 2: M12345678KOR8001019M2501012<<<<<<<<<<<<<<
 * </pre>
 *
 * @see <a href="https://www.icao.int/publications/Documents/9303_p3_cons_en.pdf">ICAO 9303 Part 3</a>
 */
@Component
public class Dg1MrzParser {

    /**
     * Parses DG1 binary data and extracts MRZ information.
     *
     * @param dg1Bytes DG1 binary data (ASN.1 encoded)
     * @return Map containing parsed MRZ fields
     * @throws Exception if parsing fails
     */
    public Map<String, String> parse(byte[] dg1Bytes) throws Exception {
        // Parse ASN.1 structure (unwrap any ICAO tag wrappers)
        ASN1Primitive primitive = ASN1Primitive.fromByteArray(dg1Bytes);

        // Unwrap all TaggedObject layers (Tag 0x61, Tag 0x5F1F, etc.)
        while (primitive instanceof ASN1TaggedObject) {
            primitive = ((ASN1TaggedObject) primitive).getBaseObject().toASN1Primitive();
        }

        // Extract MRZ OCTET STRING (ICAO 9303 standard structure)
        ASN1OctetString mrzOctet = ASN1OctetString.getInstance(primitive);

        // Extract MRZ string (ASCII)
        String mrz = new String(mrzOctet.getOctets(), StandardCharsets.US_ASCII);

        // Parse MRZ (TD3 format: 2 lines × 44 characters = 88 total)
        return parseTd3Mrz(mrz);
    }

    /**
     * Parses TD3 MRZ format (standard passport).
     *
     * @param mrz MRZ string (88 characters)
     * @return Map containing parsed fields
     */
    private Map<String, String> parseTd3Mrz(String mrz) {
        Map<String, String> result = new HashMap<>();

        // Handle both with/without newlines
        String cleanMrz = mrz.replace("\n", "").replace("\r", "");

        if (cleanMrz.length() < 88) {
            throw new IllegalArgumentException("Invalid MRZ length: " + cleanMrz.length());
        }

        // Line 1 (positions 0-43)
        String line1 = cleanMrz.substring(0, 44);
        result.put("documentType", line1.substring(0, 1)); // P
        result.put("issuingCountry", line1.substring(2, 5)); // KOR
        result.put("fullName", line1.substring(5, 44).replace("<", " ").trim()); // HONG GILDONG

        // Split name into surname and given name
        String[] nameParts = result.get("fullName").split("  ");
        result.put("surname", nameParts.length > 0 ? nameParts[0].trim() : "");
        result.put("givenNames", nameParts.length > 1 ? nameParts[1].trim() : "");

        // Line 2 (positions 44-87)
        String line2 = cleanMrz.substring(44, 88);
        result.put("documentNumber", line2.substring(0, 9).replace("<", "").trim()); // M12345678
        result.put("checkDigit1", line2.substring(9, 10)); // Document number check digit
        result.put("nationality", line2.substring(10, 13)); // KOR
        result.put("dateOfBirth", formatDate(line2.substring(13, 19))); // 800101 → 1980-01-01
        result.put("checkDigit2", line2.substring(19, 20)); // DOB check digit
        result.put("sex", line2.substring(20, 21)); // M / F
        result.put("expirationDate", formatDate(line2.substring(21, 27))); // 250101 → 2025-01-01
        result.put("checkDigit3", line2.substring(27, 28)); // Expiration check digit
        result.put("personalNumber", line2.substring(28, 42).replace("<", "").trim()); // Optional
        result.put("checkDigit4", line2.substring(42, 43)); // Personal number check digit
        result.put("compositeCheckDigit", line2.substring(43, 44)); // Final check digit

        // Store raw MRZ for verification
        result.put("mrzLine1", line1);
        result.put("mrzLine2", line2);
        result.put("mrzFull", line1 + "\n" + line2);

        return result;
    }

    /**
     * Formats MRZ date (YYMMDD) to ISO format (YYYY-MM-DD).
     *
     * @param mrzDate MRZ date string (6 digits)
     * @return ISO formatted date
     */
    private String formatDate(String mrzDate) {
        if (mrzDate.length() != 6) {
            return mrzDate;
        }

        String yy = mrzDate.substring(0, 2);
        String mm = mrzDate.substring(2, 4);
        String dd = mrzDate.substring(4, 6);

        // Assume 19xx for year >= 50, otherwise 20xx
        int year = Integer.parseInt(yy);
        String yyyy = (year >= 50 ? "19" : "20") + yy;

        return yyyy + "-" + mm + "-" + dd;
    }

    /**
     * Parses raw MRZ text (from mrz.txt file) directly.
     * <p>
     * This method accepts plain text MRZ without ASN.1 encoding.
     * Useful when MRZ is provided as a separate text file.
     * </p>
     *
     * @param mrzText Raw MRZ string (TD3 format: 88 characters or 2 lines × 44 characters)
     * @return Map containing parsed MRZ fields
     * @throws IllegalArgumentException if MRZ format is invalid
     */
    public Map<String, String> parseRawMrzText(String mrzText) {
        if (mrzText == null || mrzText.isBlank()) {
            throw new IllegalArgumentException("MRZ text cannot be null or empty");
        }

        // Trim and normalize line endings
        String normalizedMrz = mrzText.trim()
            .replace("\r\n", "\n")
            .replace("\r", "\n");

        // Parse TD3 MRZ format
        return parseTd3Mrz(normalizedMrz);
    }

    /**
     * Verifies MRZ check digits.
     *
     * @param mrz Parsed MRZ map
     * @return true if all check digits are valid
     */
    public boolean verifyCheckDigits(Map<String, String> mrz) {
        // TODO: Implement check digit verification algorithm
        // See ICAO 9303 Part 3 Section 4.9
        return true;
    }
}
