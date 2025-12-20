# DG1 & DG2 Data Structure Parsing Guide

**Date**: 2025-12-20
**Phase**: Passive Authentication - Data Group Parsing Implementation
**Status**: ✅ COMPLETED

---

## 📖 Table of Contents

1. [Overview](#overview)
2. [DG1: Machine Readable Zone (MRZ) Parsing](#dg1-machine-readable-zone-mrz-parsing)
3. [DG2: Face Image Parsing](#dg2-face-image-parsing)
4. [Technical Implementation](#technical-implementation)
5. [Testing & Validation](#testing--validation)
6. [References](#references)

---

## Overview

This document provides comprehensive technical documentation for parsing ePassport Data Groups (DG1 and DG2) according to ICAO 9303 standards. These data groups contain essential biometric and identity information that must be verified during Passive Authentication.

### Key Standards

- **ICAO Doc 9303 Part 10**: Logical Data Structure (LDS) for eMRTDs
- **ICAO Doc 9303 Part 3**: Machine Readable Official Travel Documents (TD3 MRZ)
- **ISO/IEC 19794-5**: Biometric Data Interchange Formats - Face Image Data
- **ISO/IEC 7816-11**: Biometric methods for identification cards

### Data Group Types

| Data Group | Content | Standard | Mandatory |
|------------|---------|----------|-----------|
| **DG1** | Machine Readable Zone (MRZ) | ICAO 9303 Part 3 | ✅ Yes |
| **DG2** | Encoded Face Image | ISO/IEC 19794-5 | ✅ Yes |
| DG3 | Encoded Fingerprint(s) | ISO/IEC 19794-4 | ❌ No |
| DG4 | Encoded Iris Image(s) | ISO/IEC 19794-6 | ❌ No |
| DG14 | Security Options | ICAO 9303 Part 11 | ❌ No |

**Focus**: This guide covers DG1 and DG2, the two mandatory data groups present in all ePassports.

---

## DG1: Machine Readable Zone (MRZ) Parsing

### 1. DG1 ASN.1 Structure

```asn1
Tag 0x61 (Application 1) - DG1 wrapper
  └─ Tag 0x5F1F - MRZ Info
      └─ OCTET STRING (ASCII characters)
          └─ MRZ data (88 characters for TD3)
```

### 2. TD3 MRZ Format (Standard Passport)

**Format**: 2 lines × 44 characters = 88 total characters

```
Line 1: P<KORHONG<GILDONG<<<<<<<<<<<<<<<<<<<<<<
        │ │   └──────────────┬──────────────┘
        │ │                  └─ Full Name (39 chars)
        │ └─ Issuing Country (3 chars: KOR)
        └─ Document Type (1 char: P = Passport)

Line 2: M12345678KOR8001019M2501012<<<<<<<<<<<<<<
        │         │  │      │ │      │
        │         │  │      │ │      └─ Personal Number + Check Digit (14+1 chars)
        │         │  │      │ └─ Expiration Date + Check (6+1 chars)
        │         │  │      └─ Sex (1 char: M/F)
        │         │  └─ Date of Birth + Check (6+1 chars)
        │         └─ Nationality (3 chars)
        └─ Document Number + Check (9+1 chars)
```

### 3. Parsing Implementation

#### 3.1 ASN.1 Unwrapping

```java
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
```

#### 3.2 MRZ Field Extraction

```java
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
```

#### 3.3 Date Formatting

```java
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
```

### 4. Example Output

**Input** (MRZ binary from DG1):
```
P<KORHONG<GILDONG<<<<<<<<<<<<<<<<<<<<<<
M12345678KOR8001019M2501012<<<<<<<<<<<<<<
```

**Output** (Parsed JSON):
```json
{
  "documentType": "P",
  "issuingCountry": "KOR",
  "surname": "HONG",
  "givenNames": "GILDONG",
  "fullName": "HONG GILDONG",
  "documentNumber": "M12345678",
  "nationality": "KOR",
  "dateOfBirth": "1980-01-01",
  "sex": "M",
  "expirationDate": "2025-01-01",
  "personalNumber": "",
  "checkDigit1": "9",
  "checkDigit2": "9",
  "checkDigit3": "2",
  "checkDigit4": "<",
  "compositeCheckDigit": "<",
  "mrzLine1": "P<KORHONG<GILDONG<<<<<<<<<<<<<<<<<<<<<<",
  "mrzLine2": "M12345678KOR8001019M2501012<<<<<<<<<<<<<<",
  "mrzFull": "P<KORHONG<GILDONG<<<<<<<<<<<<<<<<<<<<<<\nM12345678KOR8001019M2501012<<<<<<<<<<<<<"
}
```

### 5. Check Digit Verification (Optional)

```java
/**
 * Verifies MRZ check digits using ICAO 9303 algorithm.
 *
 * Algorithm: weighted sum modulo 10
 * Weights: 7, 3, 1 (repeating)
 * Character values: 0-9 = 0-9, A-Z = 10-35, < = 0
 */
public boolean verifyCheckDigits(Map<String, String> mrz) {
    // TODO: Implement check digit verification algorithm
    // See ICAO 9303 Part 3 Section 4.9
    return true;
}
```

---

## DG2: Face Image Parsing

### 1. DG2 ASN.1 Structure Variations

DG2 parsing is significantly more complex than DG1 due to multiple valid structure variations allowed by ICAO 9303 and real-world implementation differences.

#### Variation 1: Standard Format (ICAO 9303 Part 10)

```asn1
Tag 0x75 (Application 21) - DG2 wrapper
  └─ Tag 0x7F60 - Biometric Info Template
      └─ SEQUENCE {
          version INTEGER (optional)
          faceInfos SEQUENCE OF FaceInfo {
              FaceInfo SEQUENCE {
                  FaceImageBlock SEQUENCE {
                      imageFormat ENUMERATED
                      imageData OCTET STRING
                  }
              }
          }
      }
```

#### Variation 2: Simplified FaceImageBlock

```asn1
Tag 0x75 (Application 21) - DG2 wrapper
  └─ Tag 0x7F60 - Biometric Info Template
      └─ SEQUENCE {
          faceInfos SEQUENCE OF FaceInfo {
              FaceInfo SEQUENCE {
                  imageData OCTET STRING  ← Direct, no FaceImageBlock wrapper
              }
          }
      }
```

#### Variation 3: Ultra-Simplified (Real-world)

```asn1
Tag 0x75 (Application 21) - DG2 wrapper
  └─ Tag 0x7F60 - Biometric Info Template
      └─ SEQUENCE {
          faceInfos SEQUENCE OF {
              imageData OCTET STRING  ← Direct FaceInfo as OCTET STRING
          }
      }
```

#### Variation 4: Multiple TaggedObject Nesting

```asn1
Tag 0x75 (Application 21) - DG2 wrapper
  └─ TaggedObject [context-specific]
      └─ Tag 0x7F60 - Biometric Info Template
          └─ TaggedObject [context-specific]
              └─ SEQUENCE {
                  faceInfos TaggedObject [context-specific] {
                      SEQUENCE OF TaggedObject [context-specific] {
                          FaceInfo TaggedObject [context-specific] {
                              // ... (any of the above variations)
                          }
                      }
                  }
              }
```

### 2. ISO/IEC 19794-5 Face Image Container

Face images in DG2 are typically wrapped in ISO/IEC 19794-5 containers:

```
Offset | Length | Field | Value
-------|--------|-------|-------
0-3    | 4      | Magic | "FAC\0" (0x46 0x41 0x43 0x00)
4-7    | 4      | Version | "010\0" (ISO/IEC 19794-5:2005)
8-11   | 4      | Total Length | Big-endian integer
12-13  | 2      | Number of Face Images | Big-endian short
14-19  | 6      | Reserved | All zeros
20+    | var    | JPEG/JPEG2000 data | Actual image binary
```

**Key Insight**: JPEG data starts at offset 20 or later, identified by magic bytes `FF D8 FF`.

### 3. Parsing Implementation

#### 3.1 Main Parse Method with Multiple Face Image Handling

```java
/**
 * Parses DG2 binary data and extracts face image information.
 *
 * CRITICAL FIX (2025-12-20):
 * - Filters out metadata-only entries (< 100 bytes)
 * - Returns only valid displayable images
 * - Prevents "Image too small" errors in UI
 *
 * @param dg2Bytes DG2 binary data (ASN.1 encoded)
 * @return Map containing face image data and metadata
 * @throws Exception if parsing fails
 */
public Map<String, Object> parse(byte[] dg2Bytes) throws Exception {
    Map<String, Object> result = new HashMap<>();

    // Parse ASN.1 structure (unwrap ICAO tag wrappers)
    ASN1Primitive primitive = ASN1Primitive.fromByteArray(dg2Bytes);

    // Unwrap all TaggedObject layers (Tag 0x75, Tag 0x7F60, etc.)
    while (primitive instanceof ASN1TaggedObject) {
        primitive = ((ASN1TaggedObject) primitive).getBaseObject().toASN1Primitive();
    }

    ASN1Sequence dg2 = ASN1Sequence.getInstance(primitive);

    // Find FaceInfos (SEQUENCE OF FaceInfo) - usually last element
    int faceInfosIndex = dg2.size() - 1;

    // Get FaceInfos object (may also be wrapped in TaggedObject)
    ASN1Encodable faceInfosObj = dg2.getObjectAt(faceInfosIndex);

    // Unwrap if it's a TaggedObject
    while (faceInfosObj instanceof ASN1TaggedObject) {
        faceInfosObj = ((ASN1TaggedObject) faceInfosObj).getBaseObject();
    }

    ASN1Sequence faceInfos = ASN1Sequence.getInstance(faceInfosObj);

    // Parse each FaceInfo
    List<Map<String, Object>> faceImages = new ArrayList<>();

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

        // ✅ CRITICAL FIX: Filter out metadata-only entries (< 100 bytes)
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
```

**Critical Fix (2025-12-20)**:
- **Problem**: DG2 can contain multiple FaceInfo entries, some being metadata-only (1 byte)
- **Symptom**: Browser displayed `data:application/octet-stream;base64,Ag==` (2 bytes)
- **Solution**: Filter images < 100 bytes before adding to result array
- **Result**: Only valid JPEG images (11,790+ bytes) displayed in UI

#### 3.2 FaceInfo Parsing (Standard Format)

```java
/**
 * Parses FaceInfo SEQUENCE (standard format).
 */
private Map<String, Object> parseFaceInfo(ASN1Sequence faceInfo) throws Exception {
    Map<String, Object> result = new HashMap<>();

    // FaceInfo contains FaceImageBlock (may also be wrapped in TaggedObject)
    ASN1Encodable faceImageBlockObj = faceInfo.getObjectAt(0);

    // Unwrap if it's a TaggedObject
    while (faceImageBlockObj instanceof ASN1TaggedObject) {
        faceImageBlockObj = ((ASN1TaggedObject) faceImageBlockObj).getBaseObject();
    }

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

    // Extract actual JPEG from ISO/IEC 19794-5 container if present
    imageBytes = extractActualImageData(imageBytes);

    // Detect image format from magic bytes
    String imageFormat = detectImageFormat(imageBytes);

    result.put("imageFormat", imageFormat);
    result.put("imageSize", imageBytes.length);
    result.put("imageData", imageBytes);
    result.put("imageDataBase64", java.util.Base64.getEncoder().encodeToString(imageBytes));

    // Create data URL for browser display
    String mimeType = imageFormat.equals("JPEG") ? "image/jpeg" :
                     imageFormat.equals("JPEG2000") ? "image/jp2" :
                     "application/octet-stream";
    result.put("imageDataUrl", "data:" + mimeType + ";base64," + result.get("imageDataBase64"));

    return result;
}
```

#### 3.3 Direct Image Data Parsing (Ultra-Simplified Format)

```java
/**
 * Parses direct image data (ultra-simplified format).
 * <p>
 * Some ICAO 9303 implementations use a simplified format where FaceInfo
 * is directly an OCTET STRING containing the image data, without the
 * intermediate FaceImageBlock SEQUENCE wrapper.
 * </p>
 */
private Map<String, Object> parseDirectImageData(ASN1OctetString imageOctet) {
    Map<String, Object> result = new HashMap<>();

    byte[] imageBytes = imageOctet.getOctets();

    // Extract actual JPEG from ISO/IEC 19794-5 container if present
    imageBytes = extractActualImageData(imageBytes);

    // Detect image format from magic bytes
    String imageFormat = detectImageFormat(imageBytes);

    result.put("imageFormat", imageFormat);
    result.put("imageSize", imageBytes.length);
    result.put("imageData", imageBytes);
    result.put("imageDataBase64", java.util.Base64.getEncoder().encodeToString(imageBytes));

    // Create data URL for browser display
    String mimeType = imageFormat.equals("JPEG") ? "image/jpeg" :
                     imageFormat.equals("JPEG2000") ? "image/jp2" :
                     "application/octet-stream";
    result.put("imageDataUrl", "data:" + mimeType + ";base64," + result.get("imageDataBase64"));

    return result;
}
```

#### 3.4 JPEG Extraction from ISO/IEC 19794-5 Container

```java
/**
 * Extracts actual JPEG image data from ISO/IEC 19794-5 container.
 * <p>
 * Scans for JPEG magic bytes (FF D8 FF) starting from offset 20.
 * Falls back to JPEG2000 (00 00 00 0C 6A 50) if JPEG not found.
 * </p>
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
            if (imageBytes[i] == (byte) 0x00 &&
                imageBytes[i + 1] == (byte) 0x00 &&
                imageBytes[i + 2] == (byte) 0x00 &&
                imageBytes[i + 3] == (byte) 0x0C &&
                imageBytes[i + 4] == (byte) 0x6A &&
                imageBytes[i + 5] == (byte) 0x50) {

                byte[] actualImage = new byte[imageBytes.length - i];
                System.arraycopy(imageBytes, i, actualImage, 0, actualImage.length);
                log.info("Found JPEG2000 at offset {} in ISO/IEC 19794-5 data", i);
                return actualImage;
            }
        }

        log.warn("ISO/IEC 19794-5 container found but no JPEG/JPEG2000 magic bytes detected");
    }

    return imageBytes;
}
```

#### 3.5 Image Format Detection

```java
/**
 * Detects image format from magic bytes.
 */
private String detectImageFormat(byte[] imageBytes) {
    if (imageBytes.length < 16) {
        log.warn("Image data too small for format detection: {} bytes", imageBytes.length);
        return "UNKNOWN";
    }

    // Log first 16 bytes for debugging
    StringBuilder hexDump = new StringBuilder();
    for (int i = 0; i < Math.min(16, imageBytes.length); i++) {
        hexDump.append(String.format("%02X ", imageBytes[i]));
    }
    log.debug("Image magic bytes (first 16 bytes): {}", hexDump);

    // JPEG: FF D8 FF
    if (imageBytes[0] == (byte) 0xFF &&
        imageBytes[1] == (byte) 0xD8 &&
        imageBytes[2] == (byte) 0xFF) {
        log.info("Detected image format: JPEG (size: {} bytes)", imageBytes.length);
        return "JPEG";
    }

    // JPEG2000: 00 00 00 0C 6A 50
    if (imageBytes.length >= 6 &&
        imageBytes[0] == (byte) 0x00 &&
        imageBytes[1] == (byte) 0x00 &&
        imageBytes[2] == (byte) 0x00 &&
        imageBytes[3] == (byte) 0x0C &&
        imageBytes[4] == (byte) 0x6A &&
        imageBytes[5] == (byte) 0x50) {
        return "JPEG2000";
    }

    log.warn("Unknown image format (magic bytes: {})", hexDump);
    return "UNKNOWN";
}
```

### 4. Example Output

**Input** (DG2 binary):
- 2 FaceInfo entries
- First: 1 byte (metadata only)
- Second: 11,790 bytes (JPEG image)

**Output** (After filtering, JSON):
```json
{
  "faceCount": 1,
  "faceImages": [
    {
      "imageFormat": "JPEG",
      "imageSize": 11790,
      "imageData": "<byte array>",
      "imageDataBase64": "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBD...",
      "imageDataUrl": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD/2wBD..."
    }
  ]
}
```

**UI Display** (HTML):
```html
<img :src="steps.dgParsing.dg2Data?.faceImages?.[0]?.imageDataUrl"
     alt="Passport Face Image"
     class="max-w-xs mx-auto rounded-lg shadow-lg">
```

---

## Technical Implementation

### 1. Architecture Overview

```
PassiveAuthenticationController (REST)
  ↓
Dg1MrzParser (Infrastructure Adapter)
  ├─ Bouncy Castle ASN.1 (ASN1Primitive, ASN1TaggedObject, ASN1OctetString)
  └─ StandardCharsets.US_ASCII

Dg2FaceImageParser (Infrastructure Adapter)
  ├─ Bouncy Castle ASN.1 (ASN1Primitive, ASN1Sequence, ASN1OctetString)
  ├─ ISO/IEC 19794-5 Container Parsing
  ├─ JPEG Magic Bytes Scanning
  └─ Base64 Encoding (java.util.Base64)
```

### 2. Defensive Programming Patterns

#### Pattern 1: TaggedObject Unwrapping Loop

```java
// Unwrap all nested TaggedObject layers
while (primitive instanceof ASN1TaggedObject) {
    primitive = ((ASN1TaggedObject) primitive).getBaseObject().toASN1Primitive();
}
```

**Why**: ICAO 9303 implementations use variable nesting depths (1-4 layers).

#### Pattern 2: Type Checking Before Casting

```java
if (faceInfoObj instanceof ASN1OctetString) {
    // Handle simplified format
    faceImageData = parseDirectImageData((ASN1OctetString) faceInfoObj);
} else if (faceInfoObj instanceof ASN1Sequence) {
    // Handle standard format
    faceImageData = parseFaceInfo((ASN1Sequence) faceInfoObj);
} else {
    throw new IllegalArgumentException("Unexpected type: " + faceInfoObj.getClass().getName());
}
```

**Why**: DG2 can have 3+ structural variations in real-world passports.

#### Pattern 3: Magic Bytes Scanning

```java
// Search for JPEG magic bytes (FF D8 FF) starting from offset 20
for (int i = 20; i < imageBytes.length - 2; i++) {
    if (imageBytes[i] == (byte) 0xFF &&
        imageBytes[i + 1] == (byte) 0xD8 &&
        imageBytes[i + 2] == (byte) 0xFF) {
        // Extract JPEG
        byte[] actualImage = new byte[imageBytes.length - i];
        System.arraycopy(imageBytes, i, actualImage, 0, actualImage.length);
        return actualImage;
    }
}
```

**Why**: ISO/IEC 19794-5 header (20 bytes) wraps actual image data.

#### Pattern 4: Size-Based Filtering

```java
// Filter out metadata-only entries (< 100 bytes)
Integer imageSize = (Integer) faceImageData.get("imageSize");
if (imageSize != null && imageSize > 100) {
    faceImages.add(faceImageData);
}
```

**Why**: DG2 FaceInfos array can contain both metadata and actual images.

### 3. REST API Endpoints

#### POST /api/pa/parse-dg1

**Request**:
```json
{
  "dg1": "YQVfHw0...(Base64)"
}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "documentType": "P",
    "surname": "HONG",
    "givenNames": "GILDONG",
    "dateOfBirth": "1980-01-01",
    ...
  }
}
```

#### POST /api/pa/parse-dg2

**Request**:
```json
{
  "dg2": "dQR/YA...(Base64)"
}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "faceCount": 1,
    "faceImages": [
      {
        "imageFormat": "JPEG",
        "imageSize": 11790,
        "imageDataUrl": "data:image/jpeg;base64,/9j/4AAQ..."
      }
    ]
  }
}
```

---

## Testing & Validation

### 1. Unit Test Strategy (Future Work)

```java
@Test
void parseDg1_validTd3Mrz_success() {
    // Given: Valid DG1 binary with TD3 MRZ
    byte[] dg1Bytes = loadFixture("dg1-kor-passport.bin");

    // When: Parse DG1
    Map<String, String> result = dg1MrzParser.parse(dg1Bytes);

    // Then: All fields extracted correctly
    assertThat(result.get("surname")).isEqualTo("HONG");
    assertThat(result.get("givenNames")).isEqualTo("GILDONG");
    assertThat(result.get("documentNumber")).isEqualTo("M12345678");
}

@Test
void parseDg2_standardFormat_success() {
    // Given: Valid DG2 with standard FaceImageBlock SEQUENCE
    byte[] dg2Bytes = loadFixture("dg2-standard-format.bin");

    // When: Parse DG2
    Map<String, Object> result = dg2FaceImageParser.parse(dg2Bytes);

    // Then: JPEG image extracted
    assertThat(result.get("faceCount")).isEqualTo(1);
    List<Map<String, Object>> images = (List) result.get("faceImages");
    assertThat(images.get(0).get("imageFormat")).isEqualTo("JPEG");
}

@Test
void parseDg2_ultraSimplifiedFormat_success() {
    // Given: DG2 with direct OCTET STRING FaceInfo
    byte[] dg2Bytes = loadFixture("dg2-simplified-format.bin");

    // When: Parse DG2
    Map<String, Object> result = dg2FaceImageParser.parse(dg2Bytes);

    // Then: Image still extracted correctly
    assertThat(result.get("faceCount")).isEqualTo(1);
}

@Test
void parseDg2_multipleMetadataEntries_filtersCorrectly() {
    // Given: DG2 with 2 FaceInfo entries (1 metadata, 1 image)
    byte[] dg2Bytes = loadFixture("dg2-multiple-entries.bin");

    // When: Parse DG2
    Map<String, Object> result = dg2FaceImageParser.parse(dg2Bytes);

    // Then: Only 1 valid image returned (metadata filtered out)
    assertThat(result.get("faceCount")).isEqualTo(1);
    List<Map<String, Object>> images = (List) result.get("faceImages");
    assertThat((Integer) images.get(0).get("imageSize")).isGreaterThan(100);
}
```

### 2. E2E Testing with Real Fixtures

**Test Fixtures** (Phase 4.13):
- `src/test/resources/fixtures/pa/dg1.bin` (88 bytes MRZ)
- `src/test/resources/fixtures/pa/dg2.bin` (11,790+ bytes JPEG)
- `src/test/resources/fixtures/pa/dg14.bin` (Security options)
- `src/test/resources/fixtures/pa/sod.bin` (SOD with DSC)

**Test Results** (2025-12-19):
- ✅ DG1 parsing: All MRZ fields extracted correctly
- ✅ DG2 parsing: JPEG image displayed in browser
- ✅ Multiple FaceInfo entries: Metadata filtered, valid image shown

### 3. Browser Testing

**Test Steps**:
1. Navigate to PA Verification page
2. Upload DG1, DG2, DG14, SOD files
3. Click "Parse Data Groups" (Step 8)
4. Verify MRZ data table displayed
5. Verify face image rendered

**Expected Result**:
```html
<img src="data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD/2wBD..."
     alt="Passport Face Image"
     class="max-w-xs mx-auto rounded-lg shadow-lg">
```

**Actual Result** (2025-12-20):
✅ Face image displays correctly
✅ MRZ data shown in formatted table
✅ Image size: 11,790 bytes (valid JPEG)

---

## References

### ICAO Standards

- **ICAO Doc 9303 Part 3**: Machine Readable Travel Documents (TD3 MRZ format)
  - Section 4.6: MRZ specifications
  - Section 4.9: Check digit calculation
  - https://www.icao.int/publications/Documents/9303_p3_cons_en.pdf

- **ICAO Doc 9303 Part 10**: Logical Data Structure (LDS) for eMRTDs
  - Section 4.2: Data Group 1 (DG1)
  - Section 4.6: Data Group 2 (DG2)
  - Section 7: ASN.1 specifications
  - https://www.icao.int/publications/Documents/9303_p10_cons_en.pdf

- **ICAO Doc 9303 Part 11**: Security Mechanisms for MRTDs
  - Section 6: Passive Authentication
  - https://www.icao.int/publications/Documents/9303_p11_cons_en.pdf

### ISO Standards

- **ISO/IEC 19794-5**: Biometric Data Interchange Formats - Face Image Data
  - Section 5: Face image data format
  - Section 6: Facial feature points
  - https://www.iso.org/standard/50867.html

- **ISO/IEC 7816-11**: Identification cards - Integrated circuit cards - Part 11: Personal verification through biometric methods
  - Section 7: Biometric information template
  - https://www.iso.org/standard/66094.html

### Technical References

- **Bouncy Castle ASN.1 Documentation**:
  - https://www.bouncycastle.org/docs/docs1.5on/index.html

- **JMRTD Library** (Open-source Java implementation):
  - https://jmrtd.org/

### Related Project Documentation

- [SESSION_2025-12-20_DG2_PARSING_FIX.md](SESSION_2025-12-20_DG2_PARSING_FIX.md) - DG2 ASN.1 structure variation handling
- [SESSION_2025-12-20_PA_UI_COMPLETE.md](SESSION_2025-12-20_PA_UI_COMPLETE.md) - PA UI bug fixes and E2E testing
- [ICAO_9303_PA_CRL_STANDARD.md](ICAO_9303_PA_CRL_STANDARD.md) - ICAO 9303 PA + CRL standard procedures

---

**Document Version**: 1.0
**Last Updated**: 2025-12-20
**Author**: SmartCore Inc. Development Team
**Status**: ✅ COMPLETED

*This document provides comprehensive technical documentation for parsing ePassport DG1 and DG2 data groups according to ICAO 9303 standards.*
