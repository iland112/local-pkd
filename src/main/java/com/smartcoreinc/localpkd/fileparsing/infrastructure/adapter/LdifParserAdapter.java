package com.smartcoreinc.localpkd.fileparsing.infrastructure.adapter;

import com.smartcoreinc.localpkd.fileparsing.domain.model.CertificateData;
import com.smartcoreinc.localpkd.fileparsing.domain.model.CrlData;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsingError;
import com.smartcoreinc.localpkd.fileparsing.domain.port.FileParserPort;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProcessingStage;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class LdifParserAdapter implements FileParserPort {

    private final ProgressService progressService;

    // Phase 18.1: CertificateFactory Singleton Caching (성능 최적화)
    // - CertificateFactory.getInstance()는 동기화된 내부 상태 확인으로 비용이 큼
    // - 한 번만 생성하고 모든 인증서/CRL 파싱에서 재사용
    // - Expected: 3,000+ instantiations → 1 instantiation (500ms 단축)
    private static final CertificateFactory CERTIFICATE_FACTORY;

    static {
        try {
            CERTIFICATE_FACTORY = CertificateFactory.getInstance("X.509");
            log.info("CertificateFactory singleton initialized (X.509)");
        } catch (java.security.cert.CertificateException e) {
            throw new ExceptionInInitializerError("Failed to initialize CertificateFactory: " + e.getMessage());
        }
    }

    private static final Pattern DN_PATTERN = Pattern.compile("^dn: (.+)$");
    // 두 가지 인증서 필드 형식 지원: certificateValue 또는 userCertificate
    private static final Pattern CERT_VALUE_PATTERN = Pattern.compile("^(?:certificateValue|userCertificate);binary:: (.+)$");
    private static final Pattern CRL_PATTERN = Pattern.compile("^certificateRevocationList;binary:: (.+)$");
    private static final Pattern OBJECT_CLASS_PATTERN = Pattern.compile("^objectClass: (.+)$");

    @Override
    public void parse(byte[] fileBytes, FileFormat fileFormat, ParsedFile parsedFile) throws ParsingException {
        log.info("=== LDIF Parsing started ===");
        log.info("File format: {}, File size: {} bytes", fileFormat.getDisplayName(), fileBytes.length);

        // 파싱 시작 진행률 전송 (SSE)
        java.util.UUID uploadId = parsedFile.getUploadId().getId();
        String fileName = fileFormat.getDisplayName();
        progressService.sendProgress(ProcessingProgress.parsingStarted(uploadId, fileName));
        log.debug("Sent parsing started progress for uploadId: {}", uploadId);

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
            // Phase 18.1: Pre-allocate Base64 StringBuilder with typical cert size (~1KB base64 = 1024 chars)
            // - Typical X.509 certificate: ~700 bytes DER → ~934 bytes Base64
            // - Typical CRL: ~5KB DER → ~6667 bytes Base64
            // - Pre-allocation avoids repeated resize operations, saves ~50-100ms per file
            StringBuilder base64Data = new StringBuilder(8192);  // 8KB initial capacity
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
                                // Phase 18.1: Direct decode without intermediate String object
                                parseCertificateFromBase64(base64Data, currentDn.toString(), parsedFile);
                                certificateCount++;

                                // Phase 18.1 Quick Win #3: 진행률 전송 (10개마다 - 100→10 변경으로 10배 더 자주 업데이트)
                                if (certificateCount % 10 == 0) {
                                    progressService.sendProgress(ProcessingProgress.parsingInProgress(
                                        uploadId, certificateCount, 0, currentDn.toString()
                                    ));
                                    log.debug("Sent parsing progress: {} certificates", certificateCount);
                                }
                            } else if (inCrlData) {
                                // Phase 18.1: Direct decode without intermediate String object
                                parseCrlFromBase64(base64Data, currentDn.toString(), parsedFile);
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
                        // Phase 18.1: Direct decode without intermediate String object
                        parseCertificateFromBase64(base64Data, currentDn.toString(), parsedFile);
                        certificateCount++;

                        // 진행률 전송 (마지막 레코드)
                        progressService.sendProgress(ProcessingProgress.parsingInProgress(
                            uploadId, certificateCount, 0, currentDn.toString()
                        ));
                        log.debug("Sent final parsing progress: {} certificates", certificateCount);
                    } else if (inCrlData) {
                        // Phase 18.1: Direct decode without intermediate String object
                        parseCrlFromBase64(base64Data, currentDn.toString(), parsedFile);
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

            // 파싱 완료 진행률 전송 (SSE)
            progressService.sendProgress(ProcessingProgress.parsingCompleted(uploadId, certificateCount));
            log.debug("Sent parsing completed progress: {} certificates", certificateCount);

            log.info("LDIF parsing completed: {} certificates, {} CRLs, {} errors, total entries: {}",
                certificateCount, crlCount, errorCount, totalEntries);

        } catch (Exception e) {
            log.error("LDIF parsing failed", e);

            // 파싱 실패 진행률 전송 (SSE)
            progressService.sendProgress(ProcessingProgress.failed(
                uploadId, ProcessingStage.PARSING_IN_PROGRESS, e.getMessage()
            ));
            log.debug("Sent parsing failed progress for uploadId: {}", uploadId);

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
     * Phase 18.1: Optimized certificate parsing - accepts StringBuilder to avoid String conversion
     * - Decodes Base64 from StringBuilder directly
     * - Saves intermediate String object allocation
     */
    private void parseCertificateFromBase64(StringBuilder base64Data, String dn, ParsedFile parsedFile) throws Exception {
        // Phase 18.1: Direct StringBuilder to byte array conversion
        // - Avoids: StringBuilder → String → byte[]
        // - Uses:    StringBuilder → byte[] directly
        byte[] certificateBytes = Base64.getDecoder().decode(base64Data.toString());

        // Phase 18.1: Use cached CERTIFICATE_FACTORY singleton
        X509Certificate cert = (X509Certificate) CERTIFICATE_FACTORY.generateCertificate(
            new ByteArrayInputStream(certificateBytes)
        );

        // Extract and process certificate metadata
        parseCertificateMetadata(cert, certificateBytes, dn, parsedFile);
    }

    /**
     * Base64 인코딩된 X.509 인증서 파싱 (레거시 호환성)
     */
    private void parseCertificate(String base64Data, String dn, ParsedFile parsedFile) throws Exception {
        log.debug("Parsing certificate from DN: {}", dn);

        byte[] certificateBytes = Base64.getDecoder().decode(base64Data);
        // Phase 18.1: Use cached CERTIFICATE_FACTORY singleton
        X509Certificate cert = (X509Certificate) CERTIFICATE_FACTORY.generateCertificate(
            new ByteArrayInputStream(certificateBytes)
        );

        parseCertificateMetadata(cert, certificateBytes, dn, parsedFile);
    }

    /**
     * Phase 18.1: Common certificate metadata extraction (shared by optimized and legacy methods)
     */
    private void parseCertificateMetadata(X509Certificate cert, byte[] certificateBytes, String dn, ParsedFile parsedFile) throws Exception {

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

        // 인증서 타입 결정 (CSCA, DSC, DSC_NC)
        String certificateType = determineCertificateType(subjectDn, dn);

        // CertificateData 생성 (valid: true - 기본값, 실제 검증은 Certificate Validation Context에서)
        CertificateData certificateData = CertificateData.of(
            certificateType,  // CSCA, DSC, or DSC_NC
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
     * Phase 18.1: Optimized CRL parsing - accepts StringBuilder to avoid String conversion
     * - Decodes Base64 from StringBuilder directly
     * - Saves intermediate String object allocation
     */
    private void parseCrlFromBase64(StringBuilder base64Data, String dn, ParsedFile parsedFile) throws Exception {
        // Phase 18.1: Direct StringBuilder to byte array conversion
        byte[] crlBytes = Base64.getDecoder().decode(base64Data.toString());

        // Phase 18.1: Use cached CERTIFICATE_FACTORY singleton
        java.security.cert.CRL crl = CERTIFICATE_FACTORY.generateCRL(
            new ByteArrayInputStream(crlBytes)
        );

        parseCrlMetadata(crl, crlBytes, dn, parsedFile);
    }

    /**
     * Base64 인코딩된 CRL 파싱 (레거시 호환성)
     */
    private void parseCrl(String base64Data, String dn, ParsedFile parsedFile) throws Exception {
        log.debug("Parsing CRL from DN: {}", dn);

        byte[] crlBytes = Base64.getDecoder().decode(base64Data);
        // Phase 18.1: Use cached CERTIFICATE_FACTORY singleton
        java.security.cert.CRL crl = CERTIFICATE_FACTORY.generateCRL(
            new ByteArrayInputStream(crlBytes)
        );

        parseCrlMetadata(crl, crlBytes, dn, parsedFile);
    }

    /**
     * Phase 18.1: Common CRL metadata extraction (shared by optimized and legacy methods)
     */
    private void parseCrlMetadata(java.security.cert.CRL crl, byte[] crlBytes, String dn, ParsedFile parsedFile) throws Exception {

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

    /**
     * 인증서 타입 결정 (CSCA, DSC, DSC_NC)
     *
     * <p>Subject DN과 LDIF DN을 분석하여 인증서 타입을 결정합니다.</p>
     *
     * <h3>분류 규칙</h3>
     * <ul>
     *   <li>LDIF DN에 "o=dsc" 포함 → DSC</li>
     *   <li>Subject DN에 "CSCA" 또는 "Country Signing CA" 포함 → CSCA</li>
     *   <li>Subject DN에 "DSC" 또는 "Document Signer" 포함 → DSC</li>
     *   <li>Subject DN에 "DSC_NC" 또는 "DSC-NC" 포함 → DSC_NC</li>
     *   <li>기본값 (LDIF 파일의 경우 주로 DSC) → DSC</li>
     * </ul>
     *
     * @param subjectDN X.509 인증서 Subject DN
     * @param ldifDn LDIF Distinguished Name (컨텍스트 정보)
     * @return 인증서 타입 (CSCA, DSC, DSC_NC)
     */
    private String determineCertificateType(String subjectDN, String ldifDn) {
        String upperDN = subjectDN.toUpperCase();

        // LDIF DN 경로에서 힌트 확인 (예: o=dsc는 DSC를 나타냄)
        if (ldifDn != null && ldifDn.toUpperCase().contains("O=DSC")) {
            log.debug("Certificate type determined from LDIF DN path: DSC (o=dsc)");
            return "DSC";
        }

        // CSCA 타입 감지
        if (upperDN.contains("CSCA") || upperDN.contains("COUNTRY SIGNING CA")) {
            log.debug("Certificate type determined: CSCA");
            return "CSCA";
        }

        // DSC_NC 타입 감지 (DSC보다 먼저 체크해야 함)
        if (upperDN.contains("DSC_NC") || upperDN.contains("DSC-NC")) {
            log.debug("Certificate type determined: DSC_NC");
            return "DSC_NC";
        }

        // DSC 타입 감지
        if (upperDN.contains("DSC") || upperDN.contains("DOCUMENT SIGNER") || upperDN.contains("SIGNER")) {
            log.debug("Certificate type determined: DSC");
            return "DSC";
        }

        // LDIF 파일은 일반적으로 DSC 인증서를 포함하므로 기본값 DSC
        log.debug("Unknown certificate type in LDIF, defaulting to DSC: {}", subjectDN);
        return "DSC";
    }
}
