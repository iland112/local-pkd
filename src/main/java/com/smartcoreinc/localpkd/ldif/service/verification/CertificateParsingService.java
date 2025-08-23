package com.smartcoreinc.localpkd.ldif.service.verification;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CertificateParsingService {

    private static final CertificateFactory CERTIFICATE_FACTORY;

    static {
        try {
            CERTIFICATE_FACTORY = CertificateFactory.getInstance("X.509", "BC");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize CertificateFactory", e);
        }
    }

    /**
     * X.509 인증서 파싱
     */
    public X509Certificate parseX509Certificate(byte[] certBytes) throws CertificateException {
        if (certBytes == null || certBytes.length < 10) {
            throw new CertificateException("Certificate data is null or too short");
        }

        if (certBytes[0] != 0x30) {
            throw new CertificateException("Invalide DER encoding - does not start with 0x30");
        }

        // BouncyCastle 우선 시도
        try {
            X509CertificateHolder holder = new X509CertificateHolder(certBytes);
            return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
        } catch (Exception e) {
            // 기본 CertificateFactory로 fallback
        }

        try (InputStream bis = new ByteArrayInputStream(certBytes)) {
            return (X509Certificate) CERTIFICATE_FACTORY.generateCertificate(bis);
        } catch (CertificateException e) {
            throw e;
        } catch (Exception e) {
            throw new CertificateException("Failed to parse X.509 certificate: " + e.getMessage(), e);
        }
    }
}
