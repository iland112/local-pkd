package com.smartcoreinc.localpkd.fileparsing.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;
import java.time.LocalDateTime;

/**
 * CrlData - 추출된 CRL 데이터 Value Object
 *
 * <p><b>CRL (Certificate Revocation List)</b>: 폐기된 인증서 목록</p>
 *
 * <p><b>DDD Value Object 패턴</b>:</p>
 * <ul>
 *   <li>Immutability: 생성 후 변경 불가</li>
 *   <li>Self-validation: 생성 시 필수 필드 검증</li>
 *   <li>Value equality: 모든 필드 값으로 동등성 판단</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * CrlData crlData = CrlData.of(
 *     "KR",
 *     "CN=Korea CSCA, C=KR",
 *     "1",
 *     LocalDateTime.of(2025, 10, 23, 0, 0),
 *     LocalDateTime.of(2025, 11, 23, 0, 0),
 *     crlBytes,
 *     15,
 *     true
 * );
 *
 * // CRL 만료 여부 확인
 * boolean isExpired = crlData.isExpired();
 *
 * // 폐기된 인증서 개수
 * int revokedCount = crlData.getRevokedCertificatesCount();
 * </pre>
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrlData implements ValueObject {

    /**
     * 발급 국가 코드 (ISO 3166-1 alpha-2)
     */
    @Column(name = "crl_country_code", length = 2)
    private String countryCode;

    /**
     * Issuer DN (CRL 발급자)
     */
    @Column(name = "crl_issuer_dn", length = 500, nullable = false)
    private String issuerDN;

    /**
     * CRL Number (CRL 버전 번호)
     */
    @Column(name = "crl_number", length = 50)
    private String crlNumber;

    /**
     * This Update (CRL 발행 일시)
     */
    @Column(name = "crl_this_update", nullable = false)
    private LocalDateTime thisUpdate;

    /**
     * Next Update (다음 CRL 발행 예정 일시)
     */
    @Column(name = "crl_next_update")
    private LocalDateTime nextUpdate;

    /**
     * CRL 바이너리 (DER 인코딩)
     *
     * NOTE: @Lob 제거 - Hibernate/PostgreSQL bytea 매핑 버그 회피
     * columnDefinition="BYTEA"만으로도 충분함
     */
    @Column(name = "crl_binary", nullable = false, columnDefinition = "BYTEA")
    private byte[] crlBinary;

    /**
     * 폐기된 인증서 개수
     */
    @Column(name = "revoked_certs_count")
    private int revokedCertificatesCount;

    /**
     * 유효 여부
     */
    @Column(name = "crl_is_valid", nullable = false)
    private boolean valid;

    // ========== Static Factory Method ==========

    /**
     * CrlData 생성
     *
     * @param countryCode 발급 국가 코드
     * @param issuerDN Issuer DN
     * @param crlNumber CRL Number
     * @param thisUpdate This Update
     * @param nextUpdate Next Update
     * @param crlBinary CRL 바이너리
     * @param revokedCertificatesCount 폐기된 인증서 개수
     * @param valid 유효 여부
     * @return CrlData
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static CrlData of(
        String countryCode,
        String issuerDN,
        String crlNumber,
        LocalDateTime thisUpdate,
        LocalDateTime nextUpdate,
        byte[] crlBinary,
        int revokedCertificatesCount,
        boolean valid
    ) {
        CrlData data = new CrlData();
        data.countryCode = countryCode;
        data.issuerDN = issuerDN;
        data.crlNumber = crlNumber;
        data.thisUpdate = thisUpdate;
        data.nextUpdate = nextUpdate;
        data.crlBinary = crlBinary;
        data.revokedCertificatesCount = revokedCertificatesCount;
        data.valid = valid;

        // Self-validation
        data.validate();

        return data;
    }

    // ========== Business Logic Methods ==========

    /**
     * CRL 만료 여부 확인
     *
     * @return 만료 여부
     */
    public boolean isExpired() {
        if (nextUpdate == null) {
            return false; // nextUpdate가 없으면 만료 판단 불가
        }
        return LocalDateTime.now().isAfter(nextUpdate);
    }

    /**
     * 현재 시점에 유효한 CRL 여부
     *
     * @return 유효 여부
     */
    public boolean isCurrentlyValid() {
        LocalDateTime now = LocalDateTime.now();
        boolean notExpired = nextUpdate == null || !now.isAfter(nextUpdate);
        boolean issued = !now.isBefore(thisUpdate);
        return issued && notExpired && valid;
    }

    /**
     * 특정 국가 CRL 여부
     *
     * @param countryCode 국가 코드
     * @return 해당 국가 CRL 여부
     */
    public boolean isCountry(String countryCode) {
        return this.countryCode != null && this.countryCode.equalsIgnoreCase(countryCode);
    }

    /**
     * 폐기된 인증서가 있는지 여부
     *
     * @return 폐기된 인증서 존재 여부
     */
    public boolean hasRevokedCertificates() {
        return revokedCertificatesCount > 0;
    }

    // ========== Validation ==========

    private void validate() {
        if (issuerDN == null || issuerDN.isBlank()) {
            throw new IllegalArgumentException("issuerDN must not be blank");
        }

        if (thisUpdate == null) {
            throw new IllegalArgumentException("thisUpdate must not be null");
        }

        if (nextUpdate != null && nextUpdate.isBefore(thisUpdate)) {
            throw new IllegalArgumentException("nextUpdate must be after thisUpdate");
        }

        if (crlBinary == null || crlBinary.length == 0) {
            throw new IllegalArgumentException("crlBinary must not be empty");
        }

        if (revokedCertificatesCount < 0) {
            throw new IllegalArgumentException("revokedCertificatesCount must not be negative");
        }

        // Country code validation (optional)
        if (countryCode != null && countryCode.length() != 2) {
            throw new IllegalArgumentException("countryCode must be 2 characters (ISO 3166-1 alpha-2)");
        }
    }

    @Override
    public String toString() {
        return String.format(
            "CrlData[country=%s, issuer=%s, number=%s, revokedCount=%d, valid=%b]",
            countryCode,
            issuerDN.length() > 50 ? issuerDN.substring(0, 47) + "..." : issuerDN,
            crlNumber,
            revokedCertificatesCount,
            valid
        );
    }
}
