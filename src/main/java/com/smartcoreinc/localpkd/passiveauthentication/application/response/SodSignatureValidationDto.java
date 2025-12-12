package com.smartcoreinc.localpkd.passiveauthentication.application.response;

/**
 * DTO representing SOD (Security Object Document) Signature Validation result.
 * <p>
 * This DTO encapsulates the result of verifying the digital signature on the
 * SOD using the Document Signer Certificate's public key.
 * </p>
 *
 * <h3>Validation Process:</h3>
 * <ol>
 *   <li>Parse SOD (PKCS#7 SignedData)</li>
 *   <li>Extract signature algorithm</li>
 *   <li>Extract hash algorithm</li>
 *   <li>Verify signature using DSC public key</li>
 * </ol>
 *
 * <h3>Supported Algorithms:</h3>
 * <ul>
 *   <li>Signature: SHA256withRSA, SHA384withRSA, SHA512withRSA, SHA256withECDSA</li>
 *   <li>Hash: SHA-256, SHA-384, SHA-512</li>
 * </ul>
 *
 * @param valid True if SOD signature is valid
 * @param signatureAlgorithm Signature algorithm OID or name (e.g., "SHA256withRSA")
 * @param hashAlgorithm Hash algorithm OID or name (e.g., "SHA-256")
 * @param validationErrors Validation error messages (if any)
 */
public record SodSignatureValidationDto(
    boolean valid,
    String signatureAlgorithm,
    String hashAlgorithm,
    String validationErrors
) {
}
