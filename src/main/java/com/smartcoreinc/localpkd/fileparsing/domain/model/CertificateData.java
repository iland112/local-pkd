package com.smartcoreinc.localpkd.fileparsing.domain.model;

import com.smartcoreinc.localpkd.shared.domain.ValueObject;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CertificateData implements ValueObject {

    @Column(name = "cert_type", length = 20, nullable = false)
    private String certificateType;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "subject_dn", length = 500, nullable = false)
    private String subjectDN;

    @Column(name = "issuer_dn", length = 500, nullable = false)
    private String issuerDN;

    @Column(name = "serial_number", length = 100, nullable = false)
    private String serialNumber;

    @Column(name = "not_before", nullable = false)
    private LocalDateTime notBefore;

    @Column(name = "not_after", nullable = false)
    private LocalDateTime notAfter;

    @JdbcTypeCode(java.sql.Types.BINARY)  // Hibernate 6: bytea 매핑을 위해 필수
    @Column(name = "certificate_binary", nullable = false, columnDefinition = "BYTEA")
    private byte[] certificateBinary;

    @Column(name = "fingerprint_sha256", length = 64)
    private String fingerprintSha256;

    @Column(name = "is_valid", nullable = false)
    private boolean valid;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "all_attributes", columnDefinition = "jsonb")
    private Map<String, List<String>> allAttributes;

    public static CertificateData of(
        String certificateType,
        String countryCode,
        String subjectDN,
        String issuerDN,
        String serialNumber,
        LocalDateTime notBefore,
        LocalDateTime notAfter,
        byte[] certificateBinary,
        String fingerprintSha256,
        boolean valid,
        Map<String, List<String>> allAttributes
    ) {
        CertificateData data = new CertificateData();
        data.certificateType = certificateType;
        data.countryCode = countryCode;
        data.subjectDN = subjectDN;
        data.issuerDN = issuerDN;
        data.serialNumber = serialNumber;
        data.notBefore = notBefore;
        data.notAfter = notAfter;
        data.certificateBinary = certificateBinary;
        data.fingerprintSha256 = fingerprintSha256;
        data.valid = valid;
        data.allAttributes = allAttributes;
        data.validate();
        return data;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(notAfter);
    }

    public boolean isNotYetValid() {
        return LocalDateTime.now().isBefore(notBefore);
    }

    public boolean isCurrentlyValid() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(notBefore) && !now.isAfter(notAfter) && valid;
    }

    public boolean isCountry(String countryCode) {
        return this.countryCode != null && this.countryCode.equalsIgnoreCase(countryCode);
    }

    public boolean isSelfSigned() {
        return subjectDN.equals(issuerDN);
    }

    public boolean isCsca() {
        return "CSCA".equalsIgnoreCase(certificateType);
    }

    public boolean isDsc() {
        return "DSC".equalsIgnoreCase(certificateType) || "DSC_NC".equalsIgnoreCase(certificateType);
    }

    private void validate() {
        if (certificateType == null || certificateType.isBlank()) {
            throw new IllegalArgumentException("certificateType must not be blank");
        }
        if (subjectDN == null || subjectDN.isBlank()) {
            throw new IllegalArgumentException("subjectDN must not be blank");
        }
        if (issuerDN == null || issuerDN.isBlank()) {
            throw new IllegalArgumentException("issuerDN must not be blank");
        }
        if (serialNumber == null || serialNumber.isBlank()) {
            throw new IllegalArgumentException("serialNumber must not be blank");
        }
        if (notBefore == null) {
            throw new IllegalArgumentException("notBefore must not be null");
        }
        if (notAfter == null) {
            throw new IllegalArgumentException("notAfter must not be null");
        }
        if (notAfter.isBefore(notBefore)) {
            throw new IllegalArgumentException("notAfter must be after notBefore");
        }
        if (certificateBinary == null || certificateBinary.length == 0) {
            throw new IllegalArgumentException("certificateBinary must not be empty");
        }
        if (countryCode != null && countryCode.length() != 2) {
            throw new IllegalArgumentException("countryCode must be 2 characters (ISO 3166-1 alpha-2)");
        }
        if (!certificateType.matches("^(CSCA|DSC|DSC_NC)$")) {
            throw new IllegalArgumentException("certificateType must be one of: CSCA, DSC, DSC_NC");
        }
    }

    @Override
    public String toString() {
        return String.format(
            "CertificateData[type=%s, country=%s, subject=%s, serial=%s, valid=%b]",
            certificateType,
            countryCode,
            subjectDN.length() > 50 ? subjectDN.substring(0, 47) + "..." : subjectDN,
            serialNumber,
            valid
        );
    }
}
