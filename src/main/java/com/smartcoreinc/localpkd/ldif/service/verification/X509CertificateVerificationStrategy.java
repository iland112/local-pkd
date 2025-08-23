package com.smartcoreinc.localpkd.ldif.service.verification;

import java.security.cert.X509Certificate;

import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.ldif.service.CertificateValidationResult;

import lombok.extern.slf4j.Slf4j;

// X.509 인증서 검증 전략
@Slf4j
@Component
public class X509CertificateVerificationStrategy
        implements CertificateVerificationStrategy<byte[], CertificateValidationResult> {

    private final CertificateParsingService certificateParsingService;

    public X509CertificateVerificationStrategy(CertificateParsingService certificateParsingService) {
        this.certificateParsingService = certificateParsingService;
    }
    
    @Override
    public CertificateValidationResult verify(byte[] certBytes, VerificationContext context) {
        if (certBytes == null || certBytes.length < 10) {
            return CertificateValidationResult.invalid("Certificate data is null or too short");
        }

        if (certBytes[0] != 0x30) {
            return CertificateValidationResult.invalid("Invalid DER encoding - does not start with 0x30");
        }

        try {
            X509Certificate certificate = certificateParsingService.parseX509Certificate(certBytes);
            return validateCertificateValidity(certificate, context);
        } catch (Exception e) {
            return CertificateValidationResult.invalid("Certificate parsing failed: " + e.getMessage());
        }
    }
    
    @Override
    public boolean supports(Class<?> dataType) {
        return byte[].class.equals(dataType);
    }
        
    private CertificateValidationResult validateCertificateValidity(X509Certificate certificate, VerificationContext context) {
        try {
            String subject = certificate.getSubjectX500Principal().getName();
            String issuer = certificate.getIssuerX500Principal().getName();
            String serialNumber = certificate.getSerialNumber().toString();

            // 유효 기간 검증
            certificate.checkValidity();
            
            // 컨텍스트에 인증서 저장 (체인 검증 등에서 사용)
            context.setAttribute("parsedCertificate", certificate);

            String details = String.format("Subject: %s, Issuer: %s, Serial: %s", subject, issuer, serialNumber);
            return CertificateValidationResult.valid("Valid certificate", details);
        } catch (Exception validityException) {
            String subject = certificate.getSubjectX500Principal().getName();
            String issuer = certificate.getIssuerX500Principal().getName();
            String serialNumber = certificate.getSerialNumber().toString();
            
            String details = String.format("Subject: %s, Issuer: %s, Serial: %s, Validity Error: %s",
                    subject, issuer, serialNumber, validityException.getMessage());
            return CertificateValidationResult.invalid("Certificate validity check failed", details);
        }
    }
}
