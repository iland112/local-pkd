package com.smartcoreinc.localpkd.validator;

import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;

import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.enums.X509ValidationResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class X509CertificateValidator {

    public X509ValidationResult validateCertificate(X509Certificate x509Cert) {
        try {
            // 1. 유효 기간 검증
            try {
                x509Cert.checkValidity();
            } catch (CertificateExpiredException e) {
                log.error("CSCA 인증서 유효성 실패: {}", e.getMessage());
                return X509ValidationResult.FAILURE_EXPIRED;
            }

            // 2. 자기 서명 검증
            x509Cert.verify(x509Cert.getPublicKey());

            // 3. Basic Constraints: must be CA
            if (x509Cert.getBasicConstraints() < 0) {
                log.error("Basic Constraints value: {}", x509Cert.getBasicConstraints());
                log.error("CA 인증서 아님: {}", x509Cert.getSubjectX500Principal().toString());
                return X509ValidationResult.FAILURE_BASIC_CONSTRAINTS;
            }

            // 4. Key Usage: keyCertSign must be true
            boolean[] keyUsage = x509Cert.getKeyUsage();
            if (keyUsage != null && !keyUsage[5]) { // keyCertSign = index 5
                log.error("keyCertSign 없음: {}", x509Cert.getSubjectX500Principal());
                return X509ValidationResult.FAILURE_KEY_USAGE;
            }

            // 5. (Optional) Subject 국가 코드 존재 확인
            X500Name x500name = new JcaX509CertificateHolder(x509Cert).getSubject();
            RDN[] countryRDNs = x500name.getRDNs(BCStyle.C);
            if (countryRDNs == null || countryRDNs.length == 0) {
                log.error("Subject DN에 국가코드(C) 없음: {}", x509Cert.getSubjectX500Principal());
                return X509ValidationResult.FAILURE_SUBJECT_COUNTRY_CODE;
            }

            return X509ValidationResult.SUCCESS;
        } catch (Exception e) {
            log.error("CSCA 인증서 유효성 실패: {}", e.getMessage());
            return X509ValidationResult.FAILURE_UNKNOWN;
        }
    }
}
