package com.smartcoreinc.localpkd.fileparsing.infrastructure.adapter;

import com.smartcoreinc.localpkd.fileparsing.domain.model.CertificateData;
import com.smartcoreinc.localpkd.fileparsing.domain.model.CrlData;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsingError;
import com.smartcoreinc.localpkd.fileparsing.domain.port.FileParserPort;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LdifParserAdapter - LDIF 파일 파싱 Adapter
 *
 * <p><b>역할</b>: FileParserPort (Domain Port)의 구현체로서, LDIF 형식의 파일을 파싱하고
 * 인증서/CRL을 추출하여 ParsedFile Aggregate에 저장합니다.</p>
 *
 * <p><b>지원 형식</b>:</p>
 * <ul>
 *   <li>CSCA_COMPLETE_LDIF: CSCA Complete LDIF</li>
 *   <li>CSCA_DELTA_LDIF: CSCA Delta LDIF</li>
 *   <li>EMRTD_COMPLETE_LDIF: eMRTD Complete LDIF</li>
 *   <li>EMRTD_DELTA_LDIF: eMRTD Delta LDIF</li>
 * </ul>
 *
 * <p><b>파싱 알고리즘</b>:</p>
 * <ol>
 *   <li>LDIF 파일을 라인 단위로 읽음</li>
 *   <li>"dn:" 라인을 찾아 Distinguished Name 추출</li>
 *   <li>"certificateValue;binary:" 또는 "cRLDistributionPoint" 찾음</li>
 *   <li>Base64 인코딩된 바이너리 데이터 수집</li>
 *   <li>X.509 인증서 또는 CRL 파싱</li>
 *   <li>메타데이터(DN, SerialNumber, Validity Period 등) 추출</li>
 *   <li>ParsedFile에 CertificateData 또는 CrlData 추가</li>
 * </ol>
 *
 * <p><b>오류 처리</b>:</p>
 * <ul>
 *   <li>Base64 디코딩 오류: ParsingError 추가</li>
 *   <li>인증서 파싱 오류: ParsingError 추가</li>
 *   <li>형식 오류: ParsingException 발생</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * LdifParserAdapter parser = new LdifParserAdapter();
 *
 * byte[] ldifContent = Files.readAllBytes(Paths.get("icaopkd-001-complete.ldif"));
 * ParsedFile parsedFile = ParsedFile.create(parsedFileId, uploadId, fileFormat);
 *
 * parser.parse(ldifContent, FileFormat.CSCA_COMPLETE_LDIF, parsedFile);
 * // parsedFile.getCertificates() - 추출된 인증서 목록
 * // parsedFile.getCrls() - 추출된 CRL 목록
 * // parsedFile.getErrors() - 파싱 오류 목록
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-23
 * @see FileParserPort
 * @see ParsedFile
 */
@Slf4j
@Component
public class LdifParserAdapter implements FileParserPort {

    private static final Pattern DN_PATTERN = Pattern.compile("^dn: (.+)$");
    private static final Pattern CERT_VALUE_PATTERN = Pattern.compile("^certificateValue;binary:: (.+)$");
    private static final Pattern CRL_PATTERN = Pattern.compile("^certificateRevocationList;binary:: (.+)$");
    private static final Pattern OBJECT_CLASS_PATTERN = Pattern.compile("^objectClass: (.+)$");

    @Override
    public void parse(byte[] fileBytes, FileFormat fileFormat, ParsedFile parsedFile) throws ParsingException {
        log.info("=== LDIF Parsing started ===");
        log.info("File format: {}, File size: {} bytes", fileFormat.getDisplayName(), fileBytes.length);

        try {
            if (!supports(fileFormat)) {
                throw new ParsingException("Unsupported file format: " + fileFormat.getDisplayName());
            }

            // NOTE: startParsing()은 UseCase에서 호출하므로 여기서는 호출하지 않음
            // parsedFile.startParsing();

            // LDIF 파일을 라인 단위로 읽기
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(fileBytes))
            );

            String line;
            StringBuilder currentDn = new StringBuilder();
            StringBuilder base64Data = new StringBuilder();
            boolean inCertificateValue = false;
            boolean inCrlData = false;
            int lineNumber = 0;
            int certificateCount = 0;
            int crlCount = 0;
            int errorCount = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // 빈 줄 처리 (레코드 끝)
                if (line.trim().isEmpty()) {
                    if (base64Data.length() > 0 && currentDn.length() > 0) {
                        try {
                            if (inCertificateValue) {
                                parseCertificate(base64Data.toString(), currentDn.toString(), parsedFile);
                                certificateCount++;
                            } else if (inCrlData) {
                                parseCrl(base64Data.toString(), currentDn.toString(), parsedFile);
                                crlCount++;
                            }
                        } catch (Exception e) {
                            log.warn("Error parsing certificate/CRL at line {}: {}", lineNumber, e.getMessage());
                            parsedFile.addError(ParsingError.of(
                                "CERTIFICATE_PARSE_ERROR",
                                "Line " + lineNumber,
                                e.getMessage()
                            ));
                            errorCount++;
                        }
                    }

                    // 상태 초기화
                    currentDn.setLength(0);
                    base64Data.setLength(0);
                    inCertificateValue = false;
                    inCrlData = false;
                    continue;
                }

                // DN 추출
                Matcher dnMatcher = DN_PATTERN.matcher(line);
                if (dnMatcher.matches()) {
                    currentDn.append(dnMatcher.group(1));
                    log.debug("DN found: {}", currentDn);
                    continue;
                }

                // 인증서 바이너리 데이터
                Matcher certMatcher = CERT_VALUE_PATTERN.matcher(line);
                if (certMatcher.matches()) {
                    inCertificateValue = true;
                    base64Data.append(certMatcher.group(1));
                    log.debug("Certificate data found at line {}", lineNumber);
                    continue;
                }

                // CRL 바이너리 데이터
                Matcher crlMatcher = CRL_PATTERN.matcher(line);
                if (crlMatcher.matches()) {
                    inCrlData = true;
                    base64Data.append(crlMatcher.group(1));
                    log.debug("CRL data found at line {}", lineNumber);
                    continue;
                }

                // Base64 연속 라인 (라인 폴딩)
                if ((inCertificateValue || inCrlData) && line.startsWith(" ")) {
                    base64Data.append(line.trim());
                    continue;
                }
            }

            // 마지막 레코드 처리
            if (base64Data.length() > 0 && currentDn.length() > 0) {
                try {
                    if (inCertificateValue) {
                        parseCertificate(base64Data.toString(), currentDn.toString(), parsedFile);
                        certificateCount++;
                    } else if (inCrlData) {
                        parseCrl(base64Data.toString(), currentDn.toString(), parsedFile);
                        crlCount++;
                    }
                } catch (Exception e) {
                    log.warn("Error parsing last certificate/CRL: {}", e.getMessage());
                    parsedFile.addError(ParsingError.of(
                        "CERTIFICATE_PARSE_ERROR",
                        "EOF",
                        e.getMessage()
                    ));
                    errorCount++;
                }
            }

            reader.close();

            // NOTE: completeParsing()은 UseCase에서 호출하므로 여기서는 호출하지 않음
            // 파싱 통계만 로깅
            int totalEntries = certificateCount + crlCount + errorCount;
            // parsedFile.completeParsing(totalEntries);

            log.info("LDIF parsing completed: {} certificates, {} CRLs, {} errors, total entries: {}",
                certificateCount, crlCount, errorCount, totalEntries);

        } catch (Exception e) {
            log.error("LDIF parsing failed", e);
            // NOTE: failParsing()은 UseCase에서 호출하므로 여기서는 호출하지 않음
            // parsedFile.failParsing(e.getMessage());
            throw new ParsingException("LDIF parsing error: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(FileFormat fileFormat) {
        return fileFormat != null && fileFormat.getDisplayName() != null &&
            (fileFormat.getDisplayName().contains("LDIF") ||
             fileFormat.getDisplayName().contains("ldif"));
    }

    /**
     * Base64 인코딩된 X.509 인증서 파싱
     */
    private void parseCertificate(String base64Data, String dn, ParsedFile parsedFile) throws Exception {
        log.debug("Parsing certificate from DN: {}", dn);

        byte[] certificateBytes = Base64.getDecoder().decode(base64Data);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
            new ByteArrayInputStream(certificateBytes)
        );

        // 인증서 메타데이터 추출
        String subjectDn = cert.getSubjectX500Principal().getName();
        String issuerDn = cert.getIssuerX500Principal().getName();
        String serialNumber = cert.getSerialNumber().toString(16).toUpperCase();
        String countryCode = extractCountryCode(subjectDn);

        Date notBefore = cert.getNotBefore();
        Date notAfter = cert.getNotAfter();

        LocalDateTime notBeforeTime = notBefore.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
        LocalDateTime notAfterTime = notAfter.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();

        // CertificateData 생성 (valid: true - 기본값, 실제 검증은 Certificate Validation Context에서)
        CertificateData certificateData = CertificateData.of(
            "X.509",  // certType
            countryCode,
            subjectDn,
            issuerDn,
            serialNumber,
            notBeforeTime,
            notAfterTime,
            certificateBytes,
            calculateFingerprint(cert),
            true  // valid - 기본값으로 true, 실제 검증은 추후 진행
        );

        parsedFile.addCertificate(certificateData);
        log.debug("Certificate parsed: subject={}, issuer={}, serial={}",
            subjectDn, issuerDn, serialNumber);
    }

    /**
     * Base64 인코딩된 CRL 파싱
     */
    private void parseCrl(String base64Data, String dn, ParsedFile parsedFile) throws Exception {
        log.debug("Parsing CRL from DN: {}", dn);

        byte[] crlBytes = Base64.getDecoder().decode(base64Data);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        java.security.cert.CRL crl = cf.generateCRL(
            new ByteArrayInputStream(crlBytes)
        );

        if (!(crl instanceof java.security.cert.X509CRL)) {
            throw new Exception("Not an X.509 CRL");
        }

        java.security.cert.X509CRL x509Crl = (java.security.cert.X509CRL) crl;

        // CRL 메타데이터 추출
        String issuerDn = x509Crl.getIssuerX500Principal().getName();
        String countryCode = extractCountryCode(issuerDn);

        Date thisUpdate = x509Crl.getThisUpdate();
        Date nextUpdate = x509Crl.getNextUpdate();

        LocalDateTime thisUpdateTime = thisUpdate.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
        LocalDateTime nextUpdateTime = nextUpdate != null ? nextUpdate.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime() : null;

        int revokedCount = x509Crl.getRevokedCertificates() != null ?
            x509Crl.getRevokedCertificates().size() : 0;

        // CRL Number 추출 (보통 CRL 갱신 번호로 사용, 없으면 thisUpdate 기반으로 생성)
        String crlNumber = null;
        try {
            // CRL Number extension (OID: 2.5.29.28) 추출 시도
            // Note: Java X509CRL은 extension에 직접 접근 불가능하므로,
            // thisUpdate 시간을 기반으로 crlNumber 생성
            crlNumber = thisUpdateTime.toString().replaceAll("[^0-9]", "");
        } catch (Exception e) {
            log.debug("CRL number generation error: {}", e.getMessage());
        }

        // CrlData 생성 (valid: true - 기본값, 실제 검증은 Certificate Validation Context에서)
        CrlData crlData = CrlData.of(
            countryCode,
            issuerDn,
            crlNumber != null ? crlNumber : "unknown",  // crlNumber (필수)
            thisUpdateTime,
            nextUpdateTime,
            crlBytes,
            revokedCount,
            true  // valid - 기본값으로 true, 실제 검증은 추후 진행
        );

        parsedFile.addCrl(crlData);
        log.debug("CRL parsed: issuer={}, thisUpdate={}, revokedCount={}",
            issuerDn, thisUpdateTime, revokedCount);
    }

    /**
     * X.509 인증서의 SHA-256 fingerprint 계산
     */
    private String calculateFingerprint(X509Certificate cert) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        md.update(cert.getEncoded());
        byte[] digest = md.digest();

        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString().toLowerCase();
    }

    /**
     * DN에서 국가코드(C) 추출
     */
    private String extractCountryCode(String dn) {
        if (dn == null || dn.isEmpty()) {
            return null;
        }

        Pattern countryPattern = Pattern.compile("(?:^|,)\\s*C\\s*=\\s*([A-Z]{2})(?:,|$)");
        Matcher matcher = countryPattern.matcher(dn);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
}
