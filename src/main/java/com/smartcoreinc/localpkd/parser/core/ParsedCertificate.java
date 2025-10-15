package com.smartcoreinc.localpkd.parser.core;

import java.security.cert.X509Certificate;
import java.time.LocalDateTime;

import com.smartcoreinc.localpkd.common.enums.CertificateStatus;
import com.smartcoreinc.localpkd.common.enums.CertificateType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParsedCertificate {
    private CertificateType certificateType;
    private String countryCode;
    private String subjectDn;
    private String issuerDn;
    private String serialNumber;
    private String fingerprintSha1;
    private String fingerprintSha256;
    private LocalDateTime notBefore;
    private LocalDateTime notAfter;
    private CertificateStatus status;
    private boolean verified;
    private X509Certificate x509Cerificate;
    private byte[] certificateDer;
    private String sourceField;
    private String sourceFileName;

    public boolean isValid() {
        return status == CertificateStatus.VALID;
    }
}
