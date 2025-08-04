package com.smartcoreinc.localpkd.icao.service;

import java.security.cert.X509Certificate;

import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CscaCertificateValidator {

    public boolean isCertificateValid(X509Certificate x509Cert) {
        try {
            x509Cert.checkValidity(); // 현재 시간 기준 notBefore, notAfter 검증
            x509Cert.verify(x509Cert.getPublicKey()); // 자기 자신의 서명 확인 (self-signed 검증)

            // 1. Basic Constraints: must be CA
            if (x509Cert.getBasicConstraints() < 0) {
                log.error("Basic Constrains value: {}", x509Cert.getBasicConstraints());
                log.error("CA 인증서 아님: {}", x509Cert.getSubjectX500Principal().toString());
                return false;
            }

            // 2. Key Usage: keyCertSign must be true
            boolean[] keyUsage = x509Cert.getKeyUsage();
            if (keyUsage != null && !keyUsage[5]) { // keyCertSign = index 5
                log.error("keyCertSign 없음: {}", x509Cert.getSubjectX500Principal());
                return false;
            }

            // 3. (Optional) Subject 국가 코드 존재 확인
            X500Name x500name = new JcaX509CertificateHolder(x509Cert).getSubject();
            RDN[] countryRDNs = x500name.getRDNs(BCStyle.C);
            if (countryRDNs == null || countryRDNs.length == 0) {
                log.error("Subject DN에 국가코드(C) 없음: {}", x509Cert.getSubjectX500Principal());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("CSCA 인증서 유효성 실패: {}", e.getMessage());
            return false;
        }
    }
}
