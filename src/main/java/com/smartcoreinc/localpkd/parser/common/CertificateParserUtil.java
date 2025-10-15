package com.smartcoreinc.localpkd.parser.common;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;

import com.smartcoreinc.localpkd.common.enums.CertificateStatus;
import com.smartcoreinc.localpkd.common.enums.CertificateType;
import com.smartcoreinc.localpkd.common.util.CountryCodeUtil;
import com.smartcoreinc.localpkd.parser.common.domain.ParseContext;
import com.smartcoreinc.localpkd.parser.core.ParsedCertificate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CertificateParserUtil {

    private CertificateParserUtil() {

    }

    /**
     * X509Certificate를 ParsedCertificate로 변환
     * @param cert X509Cerificate
     * @param context ParseContext
     * @return ParsedCerticate
     */
    public static ParsedCertificate parseCertificate(X509Certificate cert, ParseContext context) throws Exception {
        // 국가 코드 추출
        String countryCode = CountryCodeUtil.extractFromDN(
            cert.getSubjectX500Principal().getName()
        );

        // Fingerprint 계산
        String fpSha1 = calculateFingerprint(cert, "SHA-1");
        String fpSha256 = calculateFingerprint(cert, "SHA-256");

        // 유효기간 확인
        LocalDateTime notBefore = cert.getNotBefore().toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
        LocalDateTime notAfter = cert.getNotAfter().toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();

        // 상태 판별
        CertificateStatus status = determineStatus(cert);

        return ParsedCertificate.builder()
            .certificateType(CertificateType.CSCA)
            .countryCode(countryCode)
            .subjectDn(cert.getSubjectX500Principal().getName())
            .issuerDn(cert.getIssuerX500Principal().getName())
            .serialNumber(cert.getSerialNumber().toString())
            .fingerprintSha1(fpSha1)
            .fingerprintSha256(fpSha256)
            .notBefore(notBefore)
            .notAfter(notAfter)
            .status(status)
            .verified(true)
            .x509Cerificate(cert)
            .certificateDer(cert.getEncoded())
            .sourceField(context.getFileId())
            .sourceFileName(context.getFilename())
            .build();
    }

    /**
     * Fingerprint 계산
     * @param cert X509Certificate
     * @param algorithm String ("SHA-1", "SHA-256")
     * @return
     * @throws Exception
     */
    public static String calculateFingerprint(X509Certificate cert, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] encoded = cert.getEncoded();
        byte[] fingerprint = digest.digest(encoded);
        return HexFormat.of().formatHex(fingerprint).toUpperCase();
    }

    /**
     * 인증서 상태 판별
     * @param cert
     * @return CertificateStatus
     */
    public static CertificateStatus determineStatus(X509Certificate cert) {
        try {
            cert.checkValidity();
            return CertificateStatus.VALID;
        } catch (Exception e) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime notAfter = cert.getNotAfter().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
            
            if (now.isAfter(notAfter)) {
                return CertificateStatus.EXPIRED;
            }

            return CertificateStatus.VALID; // 아직 유효하지 않은 경우도 VALID로 처리
        }
    }

    /**
     * 상태 파싱
     */
    public static CertificateStatus parseStatus(String description, X509Certificate cert) {
        if (description != null) {
            String upper = description.toUpperCase();
            if (upper.contains("EXPIRED")) {
                return CertificateStatus.EXPIRED;
            }
            if (upper.contains("REVOKED")) {
                return CertificateStatus.REVOKED;
            }
            if (upper.contains("SUSPENDED")) {
                return CertificateStatus.SUSPENDED;
            }
        }
        
        // 유효기간으로 판별
        try {
            cert.checkValidity();
            return CertificateStatus.VALID;
        } catch (Exception e) {
            return CertificateStatus.EXPIRED;
        }
    }
    

    /**
     * Date를 LocalDateTime으로 변환
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
    }
}
