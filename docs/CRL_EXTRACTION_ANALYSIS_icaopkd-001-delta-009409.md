# LDIF CRL Extraction Report

## File Information
- **File**: icaopkd-001-delta-009409.ldif
- **Version**: 1
- **Total CRL Entries**: 1

## CRL Entry Details

### Entry 1: Costa Rica CSCA CRL

#### Distinguished Name (DN)
**Raw (Base64 encoded)**:
```
Y249Q05cPUNvc3RhIFJpY2EgQ1NDQVwsT1VcPURHVElcLE9cPUp1bnRhIEFkbS4gZGUgbGEgRGlyZWNjacOzbiBHZW5lcmFsIGRlIE1pZ3JhY2nDs24geSBFeHRyYW5qZXLDrWFcLENcPUNSLG89Y3JsLGM9Q1IsZGM9ZGF0YSxkYz1kb3dubG9hZCxkYz1wa2QsZGM9aWNhbyxkYz1pbnQ=
```

**Decoded**:
```
cn=CN\=Costa Rica CSCA\,OU\=DGTI\,O\=Junta Adm. de la Dirección General de Migración y Extranjería\,C\=CR,o=crl,c=CR,dc=data,dc=download,dc=pkd,dc=icao,dc=int
```

#### Common Name (CN)
**Raw (Base64 encoded)**:
```
Q049Q29zdGEgUmljYSBDU0NBLE9VPURHVEksTz1KdW50YSBBZG0uIGRlIGxhIERpcmVjY2nDs24gR2VuZXJhbCBkZSBNaWdyYWNpw7NuIHkgRXh0cmFuamVyw61hLEM9Q1I=
```

**Decoded**:
```
CN=Costa Rica CSCA,OU=DGTI,O=Junta Adm. de la Dirección General de Migración y Extranjería,C=CR
```

#### Extracted Components
- **Country Code**: CR (Costa Rica)
- **Organization**: Junta Adm. de la Dirección General de Migración y Extranjería
- **Organizational Unit**: DGTI
- **Common Name**: Costa Rica CSCA
- **PKD Version**: 9409
- **PKD Conformance Policy**: B/Tec26+ Set 2

#### CRL Information (from OpenSSL parsing)
- **CRL Version**: 2 (0x1)
- **Signature Algorithm**: ecdsa-with-SHA384
- **Issuer**: C=CR, O=Junta Adm. de la Dirección General de Migración y Extranjería, OU=DGTI, CN=Costa Rica CSCA
- **Last Update**: Jul 3 23:49:02 2025 GMT
- **Next Update**: Oct 1 23:49:02 2025 GMT
- **CRL Number**: 18
- **Revoked Certificates**: None (empty CRL)
- **Authority Key Identifier**: A4:91:89:7D:C8:3D:38:2E:70:EE:A5:76:DB:B3:54:23:18:09:02:CE

#### CRL Binary Data (Base64)
```
MIIBWjCB4gIBATAKBggqhkjOPQQDAzCBgTELMAkGA1UEBhMCQ1IxSTBHBgNVBAoMQEp1bnRhIEFkbS4gZGUgbGEgRGlyZWNjacOzbiBHZW5lcmFsIGRlIE1pZ3JhY2nDs24geSBFeHRyYW5qZXLDrWExDTALBgNVBAsMBERHVEkxGDAWBgNVBAMMD0Nvc3RhIFJpY2EgQ1NDQRcNMjUwNzAzMjM0OTAyWhcNMjUxMDAxMjM0OTAyWqAvMC0wHwYDVR0jBBgwFoAUpJGJfcg9OC5w7qV227NUIxgJAs4wCgYDVR0UBAMCARIwCgYIKoZIzj0EAwMDZwAwZAIwKoV8Bjckd9hKUaRBsjdaaMzniyokhkTqOCx14v/yDBOOKV5RzTy+5zEppaCcmmNZAjBwsUpY1CuEuRYuVOZk9lwH3wBtLJwqXuG+btDgc04B1/URzo5xaUQYZhm/lyGfosc=
```

#### Object Classes
- top
- cRLDistributionPoint
- pkdDownload

---

## SQL INSERT Statement Format

```sql
INSERT INTO certificate_revocation_list (
    id,
    upload_id,
    issuer_name,
    country_code,
    x509_crl_data,
    this_update,
    next_update,
    crl_number,
    signature_algorithm,
    authority_key_identifier,
    revoked_count,
    extracted_at
) VALUES (
    'UUID_GENERATED',                    -- id
    'UPLOAD_UUID',                       -- upload_id (from file upload)
    'CN=Costa Rica CSCA,OU=DGTI,O=Junta Adm. de la Dirección General de Migración y Extranjería,C=CR',  -- issuer_name
    'CR',                                -- country_code
    decode('MIIBWjCB4gIBATAKBggqhkjOPQQDAzCBgTELMAkGA1UEBhMCQ1IxSTBHBgNVBAoMQEp1bnRhIEFkbS4gZGUgbGEgRGlyZWNjacOzbiBHZW5lcmFsIGRlIE1pZ3JhY2nDs24geSBFeHRyYW5qZXLDrWExDTALBgNVBAsMBERHVEkxGDAWBgNVBAMMD0Nvc3RhIFJpY2EgQ1NDQRcNMjUwNzAzMjM0OTAyWhcNMjUxMDAxMjM0OTAyWqAvMC0wHwYDVR0jBBgwFoAUpJGJfcg9OC5w7qV227NUIxgJAs4wCgYDVR0UBAMCARIwCgYIKoZIzj0EAwMDZwAwZAIwKoV8Bjckd9hKUaRBsjdaaMzniyokhkTqOCx14v/yDBOOKV5RzTy+5zEppaCcmmNZAjBwsUpY1CuEuRYuVOZk9lwH3wBtLJwqXuG+btDgc04B1/URzo5xaUQYZhm/lyGfosc=', 'base64'),  -- x509_crl_data
    '2025-07-03 23:49:02'::timestamp,    -- this_update
    '2025-10-01 23:49:02'::timestamp,    -- next_update
    18,                                  -- crl_number
    'ecdsa-with-SHA384',                 -- signature_algorithm
    'A4:91:89:7D:C8:3D:38:2E:70:EE:A5:76:DB:B3:54:23:18:09:02:CE',  -- authority_key_identifier
    0,                                   -- revoked_count (no revoked certificates)
    NOW()                                -- extracted_at
);
```

---

## Data Structure for Application Layer

### Java Record/DTO Format

```java
public record ExtractedCrlData(
    String distinguishedName,
    String issuerName,
    String countryCode,
    String organization,
    String organizationalUnit,
    String commonName,
    byte[] crlBinaryData,
    String crlBase64Data,
    LocalDateTime thisUpdate,
    LocalDateTime nextUpdate,
    Integer crlNumber,
    String signatureAlgorithm,
    String authorityKeyIdentifier,
    int revokedCount,
    Integer pkdVersion,
    String pkdConformancePolicy,
    List<String> objectClasses
)
```

### Example Instance

```java
new ExtractedCrlData(
    "cn=CN\\=Costa Rica CSCA\\,OU\\=DGTI\\,O\\=Junta Adm. de la Dirección General de Migración y Extranjería\\,C\\=CR,o=crl,c=CR,dc=data,dc=download,dc=pkd,dc=icao,dc=int",
    "CN=Costa Rica CSCA,OU=DGTI,O=Junta Adm. de la Dirección General de Migración y Extranjería,C=CR",
    "CR",
    "Junta Adm. de la Dirección General de Migración y Extranjería",
    "DGTI",
    "Costa Rica CSCA",
    Base64.getDecoder().decode("MIIBWjCB4gIBATAKBggqhkjOPQQDAzCBgTELMAkGA..."),
    "MIIBWjCB4gIBATAKBggqhkjOPQQDAzCBgTELMAkGA...",
    LocalDateTime.of(2025, 7, 3, 23, 49, 2),
    LocalDateTime.of(2025, 10, 1, 23, 49, 2),
    18,
    "ecdsa-with-SHA384",
    "A4:91:89:7D:C8:3D:38:2E:70:EE:A5:76:DB:B3:54:23:18:09:02:CE",
    0,
    9409,
    "B/Tec26+ Set 2",
    List.of("top", "cRLDistributionPoint", "pkdDownload")
)
```

---

## LDIF Parsing Rules Applied

### Base64 Decoding
- Fields ending with `::` contain Base64-encoded data
- DN and CN fields are Base64-encoded to preserve special characters
- Binary CRL data is Base64-encoded

### Line Continuation
- Lines starting with a single space are continuations of the previous line
- Must concatenate continuation lines with the previous line (removing leading space)

### Entry Structure
- Each entry starts with `dn:` or `dn::`
- Entries are separated by blank lines
- Multi-valued attributes can appear multiple times

### Country Code Extraction
- From DN: `c=CR`
- From Issuer DN in CRL: `C=CR`
- Both represent the same country code

---

## Summary Statistics

- **Total LDIF Entries**: 4
  - 1 root entry (dc=data)
  - 1 country entry (c=CR)
  - 1 organization entry (o=crl)
  - 1 CRL entry
- **Total CRL Entries**: 1
- **Countries**: 1 (Costa Rica)
- **Total Revoked Certificates**: 0
- **CRL Version**: 2
- **Signature Algorithm**: ECDSA with SHA-384

