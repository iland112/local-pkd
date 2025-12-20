package com.smartcoreinc.localpkd.passiveauthentication.infrastructure.adapter;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DG2 (Data Group 2) Face Image Parser.
 * <p>
 * Parses biometric face image data from ePassport DG2 according to ICAO 9303 and ISO/IEC 19794-5.
 * </p>
 *
 * <h3>DG2 ASN.1 Structure:</h3>
 * <pre>
 * Tag 0x75 (Application 21) - DG2 wrapper
 *   └─ Tag 0x7F60 - Biometric Info Template
 *       └─ SEQUENCE {
 *           faceInfos FaceInfos
 *       }
 *
 * FaceInfos ::= SEQUENCE OF FaceInfo
 *
 * FaceInfo ::= SEQUENCE {
 *     faceImage FaceImageBlock
 * }
 *
 * FaceImageBlock ::= SEQUENCE {
 *     imageFormat      ENUMERATED,
 *     imageData        OCTET STRING
 * }
 * </pre>
 *
 * @see <a href="https://www.icao.int/publications/Documents/9303_p10_cons_en.pdf">ICAO 9303 Part 10</a>
 * @see <a href="https://www.iso.org/standard/50867.html">ISO/IEC 19794-5</a>
 */
@Slf4j
@Component
public class Dg2FaceImageParser {

    /**
     * Parses DG2 binary data and extracts face image information.
     *
     * @param dg2Bytes DG2 binary data (ASN.1 encoded)
     * @return Map containing face image metadata and image data
     * @throws Exception if parsing fails
     */
    public Map<String, Object> parse(byte[] dg2Bytes) throws Exception {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> faceImages = new ArrayList<>();

        // Parse ASN.1 structure (unwrap any ICAO tag wrappers)
        ASN1Primitive primitive = ASN1Primitive.fromByteArray(dg2Bytes);

        // Unwrap all TaggedObject layers (Tag 0x75, Tag 0x7F60, etc.)
        while (primitive instanceof ASN1TaggedObject) {
            primitive = ((ASN1TaggedObject) primitive).getBaseObject().toASN1Primitive();
        }

        // Extract DG2 SEQUENCE (ICAO 9303 standard structure)
        ASN1Sequence dg2 = ASN1Sequence.getInstance(primitive);

        // First element is FaceInfos (version INTEGER is optional and may not exist)
        // Standard: ASN1Sequence faceInfos = ASN1Sequence.getInstance(dg2.getObjectAt(0));
        int faceInfosIndex = 0;
        if (dg2.size() > 1 && dg2.getObjectAt(0) instanceof ASN1Integer) {
            // Skip optional version number if present
            faceInfosIndex = 1;
        }

        // Get FaceInfos object (may also be wrapped in TaggedObject)
        ASN1Encodable faceInfosObj = dg2.getObjectAt(faceInfosIndex);

        // Unwrap if it's a TaggedObject
        while (faceInfosObj instanceof ASN1TaggedObject) {
            faceInfosObj = ((ASN1TaggedObject) faceInfosObj).getBaseObject();
        }

        ASN1Sequence faceInfos = ASN1Sequence.getInstance(faceInfosObj);

        // Iterate through each FaceInfo
        for (ASN1Encodable fi : faceInfos) {
            // Unwrap if FaceInfo is also wrapped in TaggedObject
            ASN1Encodable faceInfoObj = fi;
            while (faceInfoObj instanceof ASN1TaggedObject) {
                faceInfoObj = ((ASN1TaggedObject) faceInfoObj).getBaseObject();
            }

            Map<String, Object> faceImageData;

            // FaceInfo can be either:
            // 1. ASN1Sequence containing FaceImageBlock
            // 2. Direct OCTET STRING (simplified format)
            if (faceInfoObj instanceof ASN1OctetString) {
                // Direct image data (ultra-simplified format)
                faceImageData = parseDirectImageData((ASN1OctetString) faceInfoObj);
            } else if (faceInfoObj instanceof ASN1Sequence) {
                // Standard FaceInfo SEQUENCE
                ASN1Sequence faceInfo = (ASN1Sequence) faceInfoObj;
                faceImageData = parseFaceInfo(faceInfo);
            } else {
                throw new IllegalArgumentException("Unexpected FaceInfo type: " + faceInfoObj.getClass().getName());
            }

            // Only add if image size is reasonable (> 100 bytes)
            // This filters out metadata-only entries
            Integer imageSize = (Integer) faceImageData.get("imageSize");
            if (imageSize != null && imageSize > 100) {
                faceImages.add(faceImageData);
                log.debug("Added face image: format={}, size={} bytes",
                         faceImageData.get("imageFormat"), imageSize);
            } else {
                log.warn("Skipped small/invalid face image: size={} bytes", imageSize);
            }
        }

        result.put("faceCount", faceImages.size());
        result.put("faceImages", faceImages);

        return result;
    }

    /**
     * Parses a single FaceInfo sequence.
     *
     * @param faceInfo ASN.1 FaceInfo sequence
     * @return Map containing face image metadata
     * @throws Exception if parsing fails
     */
    private Map<String, Object> parseFaceInfo(ASN1Sequence faceInfo) throws Exception {
        Map<String, Object> result = new HashMap<>();

        // FaceInfo contains FaceImageBlock (may also be wrapped in TaggedObject)
        ASN1Encodable faceImageBlockObj = faceInfo.getObjectAt(0);

        // Unwrap if it's a TaggedObject
        while (faceImageBlockObj instanceof ASN1TaggedObject) {
            faceImageBlockObj = ((ASN1TaggedObject) faceImageBlockObj).getBaseObject();
        }

        // Parse image format and data
        // Note: Actual structure may vary by implementation
        // ICAO 9303 allows two formats:
        // 1. FaceImageBlock as SEQUENCE containing image data
        // 2. Direct OCTET STRING (image data)

        byte[] imageBytes;

        if (faceImageBlockObj instanceof ASN1OctetString) {
            // Direct image data (simplified format)
            imageBytes = ((ASN1OctetString) faceImageBlockObj).getOctets();
        } else if (faceImageBlockObj instanceof ASN1Sequence) {
            // FaceImageBlock as SEQUENCE
            ASN1Sequence faceImageBlock = (ASN1Sequence) faceImageBlockObj;

            // Extract image data (usually the last OCTET STRING in the sequence)
            ASN1OctetString imageData = null;
            for (int i = 0; i < faceImageBlock.size(); i++) {
                ASN1Encodable obj = faceImageBlock.getObjectAt(i);
                if (obj instanceof ASN1OctetString) {
                    imageData = (ASN1OctetString) obj;
                }
            }

            if (imageData == null) {
                throw new IllegalArgumentException("No image data found in FaceImageBlock SEQUENCE");
            }

            imageBytes = imageData.getOctets();
        } else {
            throw new IllegalArgumentException("Unexpected FaceImageBlock type: " + faceImageBlockObj.getClass().getName());
        }

        // Extract actual image data (remove ISO/IEC 19794-5 header if present)
        byte[] actualImageBytes = extractActualImageData(imageBytes);

        // Detect image format from actual image data
        String imageFormat = detectImageFormat(actualImageBytes);

        result.put("imageFormat", imageFormat);
        result.put("imageSize", actualImageBytes.length);
        result.put("imageData", actualImageBytes); // Binary image data
        result.put("imageDataBase64", java.util.Base64.getEncoder().encodeToString(actualImageBytes));

        // Extract image metadata if available
        if (imageFormat.equals("JPEG") || imageFormat.equals("JPEG2000")) {
            Map<String, Object> metadata = extractImageMetadata(imageBytes, imageFormat);
            result.putAll(metadata);
        }

        return result;
    }

    /**
     * Parses direct image data (ultra-simplified format).
     * <p>
     * Some ICAO 9303 implementations use a simplified format where FaceInfo
     * is directly an OCTET STRING containing the image data, without the
     * intermediate FaceImageBlock SEQUENCE wrapper.
     * </p>
     *
     * @param imageOctet ASN.1 OCTET STRING containing image binary data
     * @return Map containing face image metadata
     */
    private Map<String, Object> parseDirectImageData(ASN1OctetString imageOctet) {
        Map<String, Object> result = new HashMap<>();

        byte[] imageBytes = imageOctet.getOctets();

        // Extract actual image data (remove ISO/IEC 19794-5 header if present)
        byte[] actualImageBytes = extractActualImageData(imageBytes);

        // Detect image format from actual image data
        String imageFormat = detectImageFormat(actualImageBytes);

        result.put("imageFormat", imageFormat);
        result.put("imageSize", actualImageBytes.length);
        result.put("imageData", actualImageBytes); // Binary image data
        result.put("imageDataBase64", java.util.Base64.getEncoder().encodeToString(actualImageBytes));

        // Extract image metadata if available
        if (imageFormat.equals("JPEG") || imageFormat.equals("JPEG2000")) {
            Map<String, Object> metadata = extractImageMetadata(imageBytes, imageFormat);
            result.putAll(metadata);
        }

        return result;
    }

    /**
     * Detects image format from magic bytes.
     *
     * @param imageBytes Image binary data
     * @return Image format (JPEG, JPEG2000, PNG, etc.)
     */
    private String detectImageFormat(byte[] imageBytes) {
        if (imageBytes.length < 4) {
            log.warn("Image data too small for format detection: {} bytes", imageBytes.length);
            return "UNKNOWN";
        }

        // Log first 16 bytes as hex for debugging
        StringBuilder hexDump = new StringBuilder();
        int dumpLength = Math.min(16, imageBytes.length);
        for (int i = 0; i < dumpLength; i++) {
            hexDump.append(String.format("%02X ", imageBytes[i] & 0xFF));
        }
        log.debug("Image magic bytes (first {} bytes): {}", dumpLength, hexDump.toString().trim());

        // Check for ISO/IEC 19794-5 Facial Image Data format (FAC header)
        // Magic bytes: "FAC" (0x46 0x41 0x43) at start
        if (imageBytes.length >= 20 &&
            imageBytes[0] == 0x46 && imageBytes[1] == 0x41 && imageBytes[2] == 0x43) {
            log.info("Detected ISO/IEC 19794-5 Facial Image Data header, extracting embedded image...");
            // The actual image data starts after the 20-byte header
            // Try to detect the embedded image format
            return detectEmbeddedImageFormat(imageBytes);
        }

        // JPEG magic bytes: FF D8 FF
        if (imageBytes[0] == (byte) 0xFF &&
            imageBytes[1] == (byte) 0xD8 &&
            imageBytes[2] == (byte) 0xFF) {
            log.info("Detected image format: JPEG (size: {} bytes)", imageBytes.length);
            return "JPEG";
        }

        // JPEG2000 magic bytes: 00 00 00 0C 6A 50 20 20
        if (imageBytes.length >= 12 &&
            imageBytes[0] == 0x00 && imageBytes[1] == 0x00 &&
            imageBytes[2] == 0x00 && imageBytes[3] == 0x0C &&
            imageBytes[4] == 0x6A && imageBytes[5] == 0x50) {
            log.info("Detected image format: JPEG2000 (size: {} bytes)", imageBytes.length);
            return "JPEG2000";
        }

        // PNG magic bytes: 89 50 4E 47
        if (imageBytes[0] == (byte) 0x89 &&
            imageBytes[1] == 0x50 &&
            imageBytes[2] == 0x4E &&
            imageBytes[3] == 0x47) {
            log.info("Detected image format: PNG (size: {} bytes)", imageBytes.length);
            return "PNG";
        }

        log.warn("Unknown image format detected (size: {} bytes)", imageBytes.length);
        return "UNKNOWN";
    }

    /**
     * Extracts actual image data by removing ISO/IEC 19794-5 header if present.
     * <p>
     * ISO/IEC 19794-5 structure:
     * - Bytes 0-19: Fixed 20-byte header
     * - Byte 13: Number of feature points
     * - Bytes 20+: Variable metadata + feature points + actual image
     *
     * Strategy: Search for JPEG (FF D8 FF) or JPEG2000 (00 00 00 0C 6A 50) magic bytes
     * </p>
     *
     * @param imageBytes Image binary data (possibly with ISO/IEC 19794-5 header)
     * @return Actual image data without header
     */
    private byte[] extractActualImageData(byte[] imageBytes) {
        // Check for ISO/IEC 19794-5 "FAC" header
        if (imageBytes.length >= 20 &&
            imageBytes[0] == 0x46 && imageBytes[1] == 0x41 && imageBytes[2] == 0x43) {

            // Search for JPEG magic bytes (FF D8 FF)
            for (int i = 20; i < imageBytes.length - 2; i++) {
                if (imageBytes[i] == (byte) 0xFF &&
                    imageBytes[i + 1] == (byte) 0xD8 &&
                    imageBytes[i + 2] == (byte) 0xFF) {

                    byte[] actualImage = new byte[imageBytes.length - i];
                    System.arraycopy(imageBytes, i, actualImage, 0, actualImage.length);
                    log.info("Found JPEG at offset {} in ISO/IEC 19794-5 data, actual image size: {} bytes",
                             i, actualImage.length);
                    return actualImage;
                }
            }

            // Search for JPEG2000 magic bytes (00 00 00 0C 6A 50)
            for (int i = 20; i < imageBytes.length - 5; i++) {
                if (imageBytes[i] == 0x00 && imageBytes[i + 1] == 0x00 &&
                    imageBytes[i + 2] == 0x00 && imageBytes[i + 3] == 0x0C &&
                    imageBytes[i + 4] == 0x6A && imageBytes[i + 5] == 0x50) {

                    byte[] actualImage = new byte[imageBytes.length - i];
                    System.arraycopy(imageBytes, i, actualImage, 0, actualImage.length);
                    log.info("Found JPEG2000 at offset {} in ISO/IEC 19794-5 data, actual image size: {} bytes",
                             i, actualImage.length);
                    return actualImage;
                }
            }

            log.warn("Could not find JPEG or JPEG2000 magic bytes in ISO/IEC 19794-5 data ({} bytes)", imageBytes.length);
            return imageBytes;
        }
        // No header, return as-is
        return imageBytes;
    }

    /**
     * Detects image format embedded in ISO/IEC 19794-5 Facial Image Data.
     * <p>
     * Searches for JPEG or JPEG2000 magic bytes within the data.
     * </p>
     *
     * @param facData ISO/IEC 19794-5 Facial Image Data bytes
     * @return Detected embedded image format
     */
    private String detectEmbeddedImageFormat(byte[] facData) {
        if (facData.length < 24) {
            log.warn("ISO/IEC 19794-5 data too short: {} bytes", facData.length);
            return "UNKNOWN";
        }

        // Search for JPEG magic bytes (FF D8 FF)
        for (int i = 20; i < facData.length - 2; i++) {
            if (facData[i] == (byte) 0xFF &&
                facData[i + 1] == (byte) 0xD8 &&
                facData[i + 2] == (byte) 0xFF) {
                log.info("Detected embedded JPEG at offset {} in ISO/IEC 19794-5 (size: {} bytes)",
                         i, facData.length - i);
                return "JPEG";
            }
        }

        // Search for JPEG2000 magic bytes (00 00 00 0C 6A 50)
        for (int i = 20; i < facData.length - 5; i++) {
            if (facData[i] == 0x00 && facData[i + 1] == 0x00 &&
                facData[i + 2] == 0x00 && facData[i + 3] == 0x0C &&
                facData[i + 4] == 0x6A && facData[i + 5] == 0x50) {
                log.info("Detected embedded JPEG2000 at offset {} in ISO/IEC 19794-5 (size: {} bytes)",
                         i, facData.length - i);
                return "JPEG2000";
            }
        }

        log.warn("Could not detect embedded image format in ISO/IEC 19794-5 data ({} bytes)", facData.length);
        return "UNKNOWN";
    }

    /**
     * Extracts basic metadata from image binary.
     *
     * @param imageBytes Image binary data
     * @param format Image format
     * @return Map containing width, height, color depth, etc.
     */
    private Map<String, Object> extractImageMetadata(byte[] imageBytes, String format) {
        Map<String, Object> metadata = new HashMap<>();

        if ("JPEG".equals(format)) {
            // Basic JPEG metadata extraction
            // For production, use ImageIO or Apache Commons Imaging
            metadata.put("width", "N/A");
            metadata.put("height", "N/A");
            metadata.put("colorDepth", "24-bit (typical)");
        } else if ("JPEG2000".equals(format)) {
            metadata.put("width", "N/A");
            metadata.put("height", "N/A");
            metadata.put("colorDepth", "N/A");
        }

        return metadata;
    }

    /**
     * Converts image bytes to data URL for HTML display.
     *
     * @param imageBytes Image binary data
     * @param format Image format
     * @return Data URL string (e.g., "data:image/jpeg;base64,...")
     */
    public String toDataUrl(byte[] imageBytes, String format) {
        String mimeType = switch (format) {
            case "JPEG" -> "image/jpeg";
            case "JPEG2000" -> "image/jp2";
            case "PNG" -> "image/png";
            default -> "application/octet-stream";
        };

        String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
        return "data:" + mimeType + ";base64," + base64;
    }
}
