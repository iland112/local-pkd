package com.smartcoreinc.localpkd.certificatevalidation.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.Objects;

/**
 * X509Data - X.509 인증서 데이터 Value Object
 *
 * <p><b>DDD Value Object Pattern</b>:</p>
 * <ul>
 *   <li>Immutability: 생성 후 변경 불가</li>
 *   <li>Self-validation: 생성 시 필수 필드 검증</li>
 *   <li>Value equality: 모든 필드 값으로 동등성 판단</li>
 * </ul>
 *
 * <p><b>책임</b>:</p>
 * <ul>
 *   <li>X.509 인증서의 이진 데이터 (DER 인코딩)</li>
 *   <li>공개 키 (PublicKey 객체)</li>
 *   <li>일련 번호 (Serial Number)</li>
 *   <li>SHA-256 지문 (Fingerprint)</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * X509Data x509Data = X509Data.of(
 *     certificateBytes,          // DER-encoded certificate
 *     publicKey,                 // Extracted public key
 *     "1234567890ABCDEF",        // Serial number (hex)
 *     "A1B2C3D4E5F6..."          // SHA-256 fingerprint (64 chars)
 * );
 *
 * // 도메인 로직
 * byte[] derEncoding = x509Data.getCertificateBinary();
 * String fingerprint = x509Data.getFingerprintSha256();
 * }</pre>
 *
 * @see SubjectInfo
 * @see IssuerInfo
 * @see ValidityPeriod
 * @see ValueObject
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-23
 */
@Embeddable
public class X509Data implements ValueObject {

    /**
     * X.509 인증서 이진 데이터 (DER 인코딩)
     *
     * <p>X.509 인증서의 완전한 DER-encoded 바이너리 데이터입니다.</p>
     * <p>최대 크기: 약 16MB (대부분의 인증서는 1-10KB)</p>
     *
     * <p><b>JPA 매핑 주의사항</b>: PostgreSQL에서 @Lob은 OID 타입으로 매핑되므로 제거하고,
     * columnDefinition="BYTEA"만 사용해야 합니다.</p>
     */
    @Column(name = "certificate_binary", nullable = false, columnDefinition = "BYTEA")
    private byte[] certificateBinary;

    /**
     * 공개 키
     *
     * <p>X.509 인증서에서 추출한 PublicKey 객체입니다.
     * JPA 저장/로드 시 직렬화되지 않으므로, 인증서로부터 재추출해야 합니다.</p>
     *
     * <p><b>주의</b>: JPA @Transient로 표시되며, 데이터베이스에 저장되지 않습니다.</p>
     */
    @jakarta.persistence.Transient
    private transient PublicKey publicKey;

    /**
     * 일련 번호 (Serial Number)
     *
     * <p>16진수 문자열 형식 (예: "1234567890ABCDEF")
     * X.509 인증서의 고유 식별자입니다.</p>
     */
    @Column(name = "certificate_serial_number", length = 100)
    private String serialNumber;

    /**
     * SHA-256 지문 (Fingerprint)
     *
     * <p>64자 16진수 문자열 (예: "A1B2C3D4...EF01")</p>
     * <p>인증서의 내용 해시로, 중복 검증에 사용됩니다.</p>
     */
    @Column(name = "certificate_fingerprint_sha256", length = 64)
    private String fingerprintSha256;

    /**
     * JPA용 기본 생성자 (protected)
     */
    protected X509Data() {
    }

    /**
     * X509Data 생성 (Static Factory Method)
     *
     * @param certificateBinary X.509 인증서 바이너리 (DER-encoded)
     * @param publicKey 공개 키 객체
     * @param serialNumber 일련 번호 (16진수 문자열)
     * @param fingerprintSha256 SHA-256 지문 (64자 16진수)
     * @return X509Data
     * @throws IllegalArgumentException 필수 필드가 null이거나 형식이 올바르지 않은 경우
     */
    public static X509Data of(
            byte[] certificateBinary,
            PublicKey publicKey,
            String serialNumber,
            String fingerprintSha256
    ) {
        if (certificateBinary == null || certificateBinary.length == 0) {
            throw new IllegalArgumentException("Certificate binary cannot be null or empty");
        }
        if (publicKey == null) {
            throw new IllegalArgumentException("Public key cannot be null");
        }
        if (serialNumber == null || serialNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Serial number cannot be null or blank");
        }
        if (fingerprintSha256 == null || fingerprintSha256.trim().isEmpty()) {
            throw new IllegalArgumentException("Fingerprint cannot be null or blank");
        }

        // SHA-256 지문 형식 검증 (64자 16진수)
        if (!fingerprintSha256.matches("^[a-fA-F0-9]{64}$")) {
            throw new IllegalArgumentException(
                String.format("Fingerprint must be 64-character hex string, but got: %s", fingerprintSha256)
            );
        }

        X509Data data = new X509Data();
        data.certificateBinary = certificateBinary.clone();
        data.publicKey = publicKey;
        data.serialNumber = serialNumber;
        data.fingerprintSha256 = fingerprintSha256.toLowerCase();

        return data;
    }

    // ========== Getters ==========

    public byte[] getCertificateBinary() {
        return certificateBinary != null ? certificateBinary.clone() : null;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getFingerprintSha256() {
        return fingerprintSha256;
    }

    // ========== Business Logic Methods ==========

    /**
     * 지문 일치 여부
     *
     * @param otherFingerprint 다른 지문
     * @return 지문이 일치하면 true
     */
    public boolean hasSameFingerprint(String otherFingerprint) {
        if (otherFingerprint == null) {
            return false;
        }
        return this.fingerprintSha256.equalsIgnoreCase(otherFingerprint);
    }

    /**
     * 일련 번호 일치 여부
     *
     * @param otherSerialNumber 다른 일련 번호
     * @return 일련 번호가 일치하면 true
     */
    public boolean hasSameSerialNumber(String otherSerialNumber) {
        if (otherSerialNumber == null) {
            return false;
        }
        return this.serialNumber.equalsIgnoreCase(otherSerialNumber);
    }

    /**
     * 인증서 크기 (바이트)
     *
     * @return 바이트 단위 크기
     */
    public int getCertificateSize() {
        return certificateBinary != null ? certificateBinary.length : 0;
    }

    /**
     * 인증서 크기 (사람 친화적 표현)
     *
     * @return 예: "2.5 KB", "1.2 MB"
     */
    public String getCertificateSizeDisplay() {
        int size = getCertificateSize();
        if (size <= 0) return "0 B";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    /**
     * 공개 키 알고리즘
     *
     * @return 알고리즘 명 (예: "RSA", "EC", "DSA")
     */
    public String getPublicKeyAlgorithm() {
        return publicKey != null ? publicKey.getAlgorithm() : "UNKNOWN";
    }

    /**
     * 완전한 데이터 여부
     *
     * @return 모든 필드가 채워져 있으면 true
     */
    public boolean isComplete() {
        return certificateBinary != null && certificateBinary.length > 0 &&
               publicKey != null &&
               serialNumber != null && !serialNumber.trim().isEmpty() &&
               fingerprintSha256 != null && !fingerprintSha256.trim().isEmpty();
    }

    // ========== equals & hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        X509Data that = (X509Data) o;
        return Arrays.equals(certificateBinary, that.certificateBinary) &&
               Objects.equals(serialNumber, that.serialNumber) &&
               Objects.equals(fingerprintSha256, that.fingerprintSha256);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            Arrays.hashCode(certificateBinary),
            serialNumber,
            fingerprintSha256
        );
    }

    @Override
    public String toString() {
        return String.format(
            "X509Data[fingerprint=%s, serialNumber=%s, size=%s, keyAlgorithm=%s]",
            fingerprintSha256, serialNumber, getCertificateSizeDisplay(), getPublicKeyAlgorithm()
        );
    }

    // ========== Test-Only Factory Methods ==========

    /**
     * 테스트용 X509Data 생성 (Static Factory Method)
     *
     * <p><b>⚠️ 이 메서드는 테스트 코드에서만 사용하세요!</b></p>
     *
     * @param certificateBinary 인증서 바이너리 (간단한 mock 데이터)
     * @param serialNumber 일련 번호
     * @param isCA CA 인증서 여부 (현재는 사용하지 않음)
     * @return X509Data (테스트용)
     */
    public static X509Data createForTest(byte[] certificateBinary, String serialNumber, boolean isCA) {
        X509Data data = new X509Data();
        data.certificateBinary = certificateBinary;
        data.serialNumber = serialNumber;
        // 간단한 지문 생성 (실제로는 SHA-256 해시)
        data.fingerprintSha256 = String.format("%064x", Arrays.hashCode(certificateBinary));
        data.publicKey = null;  // 테스트에서는 null
        return data;
    }
}
