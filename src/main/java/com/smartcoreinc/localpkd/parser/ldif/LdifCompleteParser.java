package com.smartcoreinc.localpkd.parser.ldif;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.common.enums.CertificateType;
import com.smartcoreinc.localpkd.common.enums.EntryType;
import com.smartcoreinc.localpkd.common.enums.FileFormat;
import com.smartcoreinc.localpkd.common.enums.FileType;
import com.smartcoreinc.localpkd.common.util.CountryCodeUtil;
import com.smartcoreinc.localpkd.parser.common.CertificateParserUtil;
import com.smartcoreinc.localpkd.parser.common.FileParser;
import com.smartcoreinc.localpkd.parser.common.domain.ParseContext;
import com.smartcoreinc.localpkd.parser.common.domain.ParseResult;
import com.smartcoreinc.localpkd.parser.common.exception.ParsingException;
import com.smartcoreinc.localpkd.parser.core.ParsedCertificate;
import com.smartcoreinc.localpkd.parser.core.ParsedCrl;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldif.LDIFReader;

import lombok.extern.slf4j.Slf4j;

/**
 * LDIF Complete 파서
 * 
 * Complete LDIF 파일 파싱
 * - CSCA Complete: icaopkd-001-complete-{version}.ldif
 * - eMRTD Complete: icaopkd-002-complete-{version}.ldif
 * - Non-Conformant: icaopkd-003-complete-{version}.ldif
 */
@Slf4j
@Component
public class LdifCompleteParser implements FileParser {

    @Override
    public boolean supports(FileType fileType, FileFormat fileFormat) {
        return fileFormat.isLdif() && fileFormat.isComplete();
    }

    @Override
    public int getPriority() {
        return 60;
    }

    @Override
    public ParseResult parse(byte[] fileData, ParseContext context) throws ParsingException {
        log.info("=== LDIF Complete 파싱 시작: {} ===", context.getFilename());

        LocalDateTime startTime = LocalDateTime.now();
        List<ParsedCertificate> parsedCertificates = new ArrayList<>();
        List<ParsedCrl> parsedCrls = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (InputStream is = new ByteArrayInputStream(fileData);
            LDIFReader ldifReader = new LDIFReader(is)) {
            int processed = 0;
            int certificates = 0;
            int crls = 0;

            Entry entry;
            while ((entry = ldifReader.readEntry()) != null) {
                try {
                    // Entry 타입 판별
                    EntryType entryType = EntryType.fromObjectClasses(entry.getObjectClassValues());

                    switch (entryType) {
                        case CERTIFICATE:
                            ParsedCertificate cert = parseCertificateEntry(entry, context);
                            if (cert != null) {
                                parsedCertificates.add(cert);
                                certificates++;
                            }
                            break;

                        case CRL:
                            ParsedCrl crl = parseCrlEntry(entry, context);
                            if (crl != null) {
                                parsedCrls.add(crl);
                                crls++;
                            }
                            break;

                        case UNKNOWN:
                            log.debug("알 수 없는 Entry 타입: {}", entry.getDN());
                            break;
                    }

                    processed++;

                    if (processed % 50 == 0) {
                        log.debug("처리 진행: {} (인증서: {}, CRL: {})", processed, certificates, crls);
                    }

                } catch (Exception e) {
                    log.warn("Entry 처리 실패 (DN: {}): {}", entry.getDN(), e.getMessage());
                    errors.add(String.format("Entry %s 처리 실패: %s", entry.getDN(), e.getMessage()));
                    processed++;
                }
            }

            // 통계 계산
            int valid = (int) parsedCertificates.stream().filter(ParsedCertificate::isValid).count();
            int invalid = parsedCertificates.size() - valid;

            log.info("✅ LDIF Complete 파싱 완료: 총 {}, 인증서 {}, CRL {}, 유효 {}, 무효 {}",
                processed, certificates, crls, valid, invalid);

            // ParseResult 생성
            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(startTime, endTime).toMillis();

            return ParseResult.builder()
                .fileId(context.getFileId())
                .filename(context.getFilename())
                .fileType(context.getFileType())
                .fileFormat(context.getFileFormat())
                .version(context.getVersion())
                .success(true)
                .completed(true)
                .totalCertificates(certificates)
                .validCount(valid)
                .invalidCount(invalid)
                .processedCount(processed)
                .startTime(startTime)
                .endTime(endTime)
                .durationMillis(duration)
                .errorMessages(errors)
                .metadata("crlCount", crls)
                .build();

        } catch (Exception e) {
            log.error("LDIF Complete 파싱 실패", e);

            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(startTime, endTime).toMillis();

            throw new ParsingException(
                context.getFileId(),
                context.getFilename(),
                "LDIF Complete 파싱 실패: " + e.getMessage(),
                e
            );
        }
    }


    /**
     * 인증서 바이너리 데이터 추출
     * @param entry Entry
     * @return byte[]
     */
    private byte[] getCertificateBytes(Entry entry) {
        // cACerificate;binary 또는 userCertificate;binary
        Attribute attr = entry.getAttribute("cACertificate;binary");
        if (attr == null) {
            attr = entry.getAttribute("userCertificate;binary");
        }
        if (attr == null) {
            attr = entry.getAttribute("cACertificate");
        }
        if (attr == null) {
            attr = entry.getAttribute("userCertificate");
        }

        return attr != null ? attr.getValueByteArray() : null;
    }

    /**
     * CertificateType 판별
     *
     * ICAO PKD LDIF 구조:
     * - dc=data,dc=download,dc=pkd,dc=icao,dc=int (eMRTD PKI Objects)
     * - dc=nc-data,dc=download,dc=pkd,dc=icao,dc=int (Non-Conformant)
     * - o=ml: CSCA Master List
     * - o=dsc: Document Signer Certificate
     * - o=crl: Certificate Revocation List
     */
    private CertificateType determineCertificateType(Entry entry, X509Certificate cert) {
        // objectClass로 판별 시도
        CertificateType type = CertificateType.fromLdifObjectClasses(
            entry.getObjectClassValues()
        );

        if (type != null) {
            return type;
        }

        // DN에서 판별 시도
        String dn = entry.getDN();

        // Non-Conformant (Collection #3)
        // Collection #3는 비표준 DSC만 포함 (CSCA는 없음)
        if (dn.contains("dc=nc-data")) {
            return CertificateType.DSC_NC;
        }

        // CSCA Master List (Collection #1)
        if (dn.contains("o=ml") || dn.contains("o=ML")) {
            return CertificateType.CSCA;
        }

        // Document Signer Certificate (Collection #2)
        if (dn.contains("o=dsc") || dn.contains("o=DSC")) {
            return CertificateType.DSC;
        }

        // 기본값: 인증서 타입에서 판별
        // BasicConstraints 확인: CA=true이면 CSCA
        if (cert.getBasicConstraints() >= 0) {
            return CertificateType.CSCA;
        }

        return CertificateType.DSC;
    }

     /**
     * 인증서 Entry 파싱
     * @param entry Entry
     * @param context ParseContext
     * @return ParsedCertificate
     */
    private ParsedCertificate parseCertificateEntry(Entry entry, ParseContext context) throws Exception {
        // 인증서 바이너리 데이터 추출
        byte[] certBytes = getCertificateBytes(entry);
        if (certBytes == null) {
            log.warn("인증서 바이너리 데이터를 찾을 수 없음: {}", entry.getDN());
            return null;
        }

        // X509Cerificate 생성
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
            new ByteArrayInputStream(certBytes)
        );

        ParsedCertificate parsedCertificate = CertificateParserUtil.parseCertificate(cert, context);
        return parsedCertificate;
    }

    /**
     * CRL Entry 파싱
     * 
     * @param entry
     * @param context
     * @return
     * @throws Exception
     */
    private ParsedCrl parseCrlEntry(Entry entry, ParseContext context) throws Exception {
        // CRL 바이너리 데이터 추출
        Attribute crlAttr = entry.getAttribute("certificateRevocationList;binary");
        if (crlAttr == null) {
            crlAttr = entry.getAttribute("certificateRevocationList");
        }

        if (crlAttr == null) {
            log.warn("CRL 바이너리 데이터를 찾을 수 없음: {}", entry.getDN());
            return null;
        }

        byte[] crlBytes = crlAttr.getValueByteArray();

        // X509CRL 생성
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509CRL crl = (X509CRL) cf.generateCRL(new ByteArrayInputStream(crlBytes));

        // 국가 코드
        String countryCode = entry.getAttributeValue("c");

        // This/Next update
        LocalDateTime thisUpdate = CertificateParserUtil.toLocalDateTime(crl.getThisUpdate());
        LocalDateTime nextUpdate = CertificateParserUtil.toLocalDateTime(crl.getNextUpdate());

        ParsedCrl parsedCrl = ParsedCrl.builder()
            .countryCode(countryCode)
            .issuerDn(crl.getIssuerX500Principal().getName())
            .thisUpdate(thisUpdate)
            .nextUpdate(nextUpdate)
            .x509Crl(crl)
            .crlDer(crlBytes)
            .ldifDn(entry.getDN())
            .sourceFileId(context.getFileId())
            .build();

        // 페기 항목 추출
        if (crl.getRevokedCertificates() != null) {
            for (var revokedCert : crl.getRevokedCertificates()) {
                ParsedCrl.RevokedCertificateEntry rce = ParsedCrl.RevokedCertificateEntry.builder()
                    .serialNumber(revokedCert.getSerialNumber().toString())
                    .revocationDate(CertificateParserUtil.toLocalDateTime(revokedCert.getRevocationDate()))
                    .build();

                parsedCrl.addRevokedCertificate(rce);
            }
        }

        return parsedCrl;
    }
}
