package com.smartcoreinc.localpkd.ldif.service;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.ldif.service.verification.CertificateParsingService;

import lombok.extern.slf4j.Slf4j;

/**
 * Master List Content에서 인증서 추출 전담 클래스
 */
@Slf4j
@Component
public class CertificateExtractor {
    private final CertificateParsingService certificateParsingService;

    public CertificateExtractor(CertificateParsingService certificateParsingService) {
        this.certificateParsingService = certificateParsingService;
    }

    /**
     * Master List content에서 CSCA 인증서들 추출 (메인 진입점)
     */
    public List<X509Certificate> extractCertificatesFromContent(byte[] content) {
        if (content == null || content.length < 10) {
            log.debug("Content is null or too short: {} bytes", content != null ? content.length : 0);
            return new ArrayList<>();
        }

        log.debug("Content starts with: {}", 
            Arrays.toString(Arrays.copyOf(content, Math.min(20, content.length))));

        List<ExtractionStrategy> strategies = Arrays.asList(
            new Asn1SequenceStrategy(),
            new SetOfCertificatesStrategy(), 
            new ConsecutiveDerStrategy(),
            new Pkcs7CertificatesStrategy(),
            new SingleCertificateStrategy()
        );

        for (ExtractionStrategy strategy : strategies) {
            try {
                List<X509Certificate> certificates = strategy.extract(content);
                if (!certificates.isEmpty()) {
                    log.info("Successfully extracted {} certificates using {}", 
                        certificates.size(), strategy.getClass().getSimpleName());
                    return certificates;
                }
            } catch (Exception e) {
                log.debug("Strategy {} failed: {}", strategy.getClass().getSimpleName(), e.getMessage());
            }
        }

        log.warn("Failed to extract any certificates from content of {} bytes", content.length);
        return new ArrayList<>();
    }

    /**
     * 인증서 추출 전략 인터페이스
     */
    private interface ExtractionStrategy {
        List<X509Certificate> extract(byte[] content) throws Exception;
    }

    /**
     * ASN.1 SEQUENCE 구조로 파싱
     */
    private class Asn1SequenceStrategy implements ExtractionStrategy {
        @Override
        public List<X509Certificate> extract(byte[] content) throws Exception {
            List<X509Certificate> certificates = new ArrayList<>();
            
            try (ASN1InputStream asn1InputStream = new ASN1InputStream(content)) {
                ASN1Sequence sequence = (ASN1Sequence) asn1InputStream.readObject();
                log.debug("ASN.1 SEQUENCE found with {} elements", sequence.size());
                
                for (int i = 0; i < sequence.size(); i++) {
                    try {
                        byte[] elementBytes = sequence.getObjectAt(i).toASN1Primitive().getEncoded();
                        log.debug("Element {} size: {} bytes, starts with: {}", 
                            i, elementBytes.length, 
                            Arrays.toString(Arrays.copyOf(elementBytes, Math.min(4, elementBytes.length))));
                        
                        if (isDerCertificate(elementBytes)) {
                            X509Certificate cert = certificateParsingService.parseX509Certificate(elementBytes);
                            if (cert != null) {
                                certificates.add(cert);
                                log.debug("Extracted certificate {}: Subject={}", 
                                    i, cert.getSubjectX500Principal().getName());
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Failed to parse SEQUENCE element {} as certificate: {}", i, e.getMessage());
                    }
                }
            }
            
            return certificates;
        }
    }

    /**
     * SET OF Certificate 구조로 파싱
     */
    private class SetOfCertificatesStrategy implements ExtractionStrategy {
        @Override
        public List<X509Certificate> extract(byte[] content) throws Exception {
            List<X509Certificate> certificates = new ArrayList<>();
            
            try (ASN1InputStream asn1InputStream = new ASN1InputStream(content)) {
                Object asn1Object = asn1InputStream.readObject();
                
                if (asn1Object instanceof ASN1Sequence) {
                    ASN1Sequence sequence = (ASN1Sequence) asn1Object;
                    log.debug("Found SET/SEQUENCE with {} elements for certificate parsing", sequence.size());
                    
                    for (int i = 0; i < sequence.size(); i++) {
                        try {
                            byte[] certBytes = sequence.getObjectAt(i).toASN1Primitive().getEncoded();
                            if (isDerCertificate(certBytes)) {
                                X509Certificate cert = certificateParsingService.parseX509Certificate(certBytes);
                                if (cert != null) {
                                    certificates.add(cert);
                                    log.debug("Extracted certificate from SET element {}", i);
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Failed to parse SET element {} as certificate: {}", i, e.getMessage());
                        }
                    }
                }
            }
            
            return certificates;
        }
    }

    /**
     * 연속된 DER 인코딩된 인증서들로 파싱
     */
    private class ConsecutiveDerStrategy implements ExtractionStrategy {
        @Override
        public List<X509Certificate> extract(byte[] content) throws Exception {
            List<X509Certificate> certificates = new ArrayList<>();
            int offset = 0;
            int certCount = 0;
            
            while (offset < content.length - 10) {
                // DER 인증서 시작점 찾기 (0x30으로 시작)
                if (content[offset] != 0x30) {
                    offset++;
                    continue;
                }
                
                // DER 길이 계산
                int length = DerLengthCalculator.calculateLength(content, offset);
                if (length <= 0 || offset + length > content.length) {
                    offset++;
                    continue;
                }
                
                try {
                    byte[] certBytes = Arrays.copyOfRange(content, offset, offset + length);
                    X509Certificate cert = certificateParsingService.parseX509Certificate(certBytes);
                    if (cert != null) {
                        certificates.add(cert);
                        certCount++;
                        log.debug("Extracted consecutive certificate {}: {} bytes", 
                            certCount, certBytes.length);
                    }
                    offset += length;
                } catch (Exception e) {
                    log.debug("Failed to parse consecutive certificate at offset {}: {}", offset, e.getMessage());
                    offset++;
                }
            }
            
            return certificates;
        }
    }

    /**
     * PKCS#7 SignedData의 certificates 필드에서 추출
     */
    private class Pkcs7CertificatesStrategy implements ExtractionStrategy {
        @Override
        public List<X509Certificate> extract(byte[] content) throws Exception {
            List<X509Certificate> certificates = new ArrayList<>();
            
            CMSSignedData cmsSignedData = new CMSSignedData(content);
            
            // certificates 컬렉션에서 모든 인증서 추출
            for (Object certHolder : cmsSignedData.getCertificates().getMatches(null)) {
                if (certHolder instanceof X509CertificateHolder) {
                    try {
                        X509Certificate cert = new JcaX509CertificateConverter()
                            .setProvider("BC")
                            .getCertificate((X509CertificateHolder) certHolder);
                        certificates.add(cert);
                        log.debug("Extracted certificate from PKCS#7: Subject={}", 
                            cert.getSubjectX500Principal().getName());
                    } catch (Exception e) {
                        log.debug("Failed to convert certificate holder: {}", e.getMessage());
                    }
                }
            }
            
            return certificates;
        }
    }

    /**
     * 단일 인증서로 파싱
     */
    private class SingleCertificateStrategy implements ExtractionStrategy {
        @Override
        public List<X509Certificate> extract(byte[] content) throws Exception {
            List<X509Certificate> certificates = new ArrayList<>();
            
            if (isDerCertificate(content)) {
                X509Certificate cert = certificateParsingService.parseX509Certificate(content);
                if (cert != null) {
                    certificates.add(cert);
                    log.debug("Extracted single certificate: {} bytes", content.length);
                }
            }
            
            return certificates;
        }
    }

    /**
     * DER 형식 인증서인지 확인
     */
    private boolean isDerCertificate(byte[] data) {
        return data != null && data.length > 10 && data[0] == 0x30;
    }

    /**
     * DER 길이 계산 유틸리티 클래스
     */
    private static class DerLengthCalculator {
        public static int calculateLength(byte[] data, int offset) {
            try {
                if (offset >= data.length || data[offset] != 0x30) {
                    return -1;
                }
                
                int lengthOffset = offset + 1;
                if (lengthOffset >= data.length) {
                    return -1;
                }
                
                int firstLengthByte = data[lengthOffset] & 0xFF;
                
                if ((firstLengthByte & 0x80) == 0) {
                    // 짧은 형식: 길이가 1바이트
                    return 2 + firstLengthByte; // tag(1) + length(1) + content
                } else {
                    // 긴 형식: 길이 바이트 수 계산
                    int lengthByteCount = firstLengthByte & 0x7F;
                    if (lengthByteCount == 0 || lengthByteCount > 4 || 
                        lengthOffset + lengthByteCount >= data.length) {
                        return -1;
                    }
                    
                    int contentLength = 0;
                    for (int i = 0; i < lengthByteCount; i++) {
                        contentLength = (contentLength << 8) | (data[lengthOffset + 1 + i] & 0xFF);
                    }
                    
                    return 2 + lengthByteCount + contentLength; // tag + length_of_length + length_bytes + content
                }
            } catch (Exception e) {
                return -1;
            }
        }
    }
}
