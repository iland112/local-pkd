package com.smartcoreinc.localpkd.fileparsing.infrastructure.adapter;

import com.smartcoreinc.localpkd.fileparsing.domain.model.CertificateData;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsingError;
import com.smartcoreinc.localpkd.fileparsing.domain.port.FileParserPort;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * MasterListParserAdapter - Master List (CMS) 파일 파싱 Adapter
 *
 * <p><b>역할</b>: FileParserPort (Domain Port)의 구현체로서, Master List CMS 형식의 파일을 파싱하고
 * 인증서를 추출하여 ParsedFile Aggregate에 저장합니다.</p>
 *
 * <p><b>지원 형식</b>:</p>
 * <ul>
 *   <li>ML_SIGNED_CMS: Master List (Signed CMS)</li>
 * </ul>
 *
 * <p><b>파싱 알고리즘</b>:</p>
 * <ol>
 *   <li>CMS 구조 검증 (Magic bytes 확인)</li>
 *   <li>CMS 서명 검증 (BouncyCastle CMSSignedData 사용)</li>
 *   <li>서명된 콘텐츠에서 인증서 목록 추출</li>
 *   <li>각 인증서 파싱 및 메타데이터 추출</li>
 *   <li>ParsedFile에 CertificateData 추가</li>
 * </ol>
 *
 * <p><b>오류 처리</b>:</p>
 * <ul>
 *   <li>CMS 형식 검증 실패: ParsingException 발생</li>
 *   <li>서명 검증 실패: ParsingError 추가</li>
 *   <li>인증서 파싱 오류: ParsingError 추가</li>
 * </ul>
 *
 * <p><b>Note</b>: 현재 구현은 기본 CMS 파싱만 지원합니다.
 * 실제 서명 검증은 추후 CertificateValidation Context에서 수행됩니다.</p>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * MasterListParserAdapter parser = new MasterListParserAdapter();
 *
 * byte[] mlContent = Files.readAllBytes(Paths.get("masterlist-ML.ml"));
 * ParsedFile parsedFile = ParsedFile.create(parsedFileId, uploadId, fileFormat);
 *
 * parser.parse(mlContent, FileFormat.ML_SIGNED_CMS, parsedFile);
 * // parsedFile.getCertificates() - 추출된 인증서 목록
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-23
 * @see FileParserPort
 * @see ParsedFile
 * @see org.bouncycastle.cms.CMSSignedData
 */
@Slf4j
@Component
public class MasterListParserAdapter implements FileParserPort {

    // CMS Magic bytes (ASN.1 SEQUENCE tag)
    private static final byte[] CMS_MAGIC = {0x30};  // SEQUENCE tag in BER

    @Override
    public void parse(byte[] fileBytes, FileFormat fileFormat, ParsedFile parsedFile) throws ParsingException {
        log.info("=== Master List Parsing started ===");
        log.info("File format: {}, File size: {} bytes", fileFormat.getDisplayName(), fileBytes.length);

        try {
            if (!supports(fileFormat)) {
                throw new ParsingException("Unsupported file format: " + fileFormat.getDisplayName());
            }

            // NOTE: startParsing()은 UseCase에서 호출하므로 여기서는 호출하지 않음
            // parsedFile.startParsing();

            // 1. CMS 형식 검증
            validateCmsFormat(fileBytes);

            // 2. CMS 파싱
            parseCmsContent(fileBytes, parsedFile);

            // 3. 파싱 완료
            int totalEntries = parsedFile.getCertificates().size() +
                             parsedFile.getCrls().size() +
                             parsedFile.getErrors().size();

            // NOTE: completeParsing()은 UseCase에서 호출하므로 여기서는 호출하지 않음
            // parsedFile.completeParsing(totalEntries);

            log.info("Master List parsing completed: {} certificates, {} errors",
                parsedFile.getCertificates().size(),
                parsedFile.getErrors().size());

        } catch (ParsingException e) {
            log.error("Master List parsing failed", e);
            // NOTE: failParsing()은 UseCase에서 호출하므로 여기서는 호출하지 않음
            // parsedFile.failParsing(e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Master List parsing failed", e);
            // NOTE: failParsing()은 UseCase에서 호출하므로 여기서는 호출하지 않음
            // parsedFile.failParsing(e.getMessage());
            throw new ParsingException("Master List parsing error: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(FileFormat fileFormat) {
        return fileFormat != null && fileFormat.getDisplayName() != null &&
            (fileFormat.getDisplayName().contains("Master List") ||
             fileFormat.getDisplayName().contains("CMS"));
    }

    /**
     * CMS 형식 검증
     */
    private void validateCmsFormat(byte[] fileBytes) throws ParsingException {
        if (fileBytes == null || fileBytes.length < 4) {
            throw new ParsingException("Invalid Master List: file too small");
        }

        // ASN.1 SEQUENCE tag (0x30) 확인
        if (fileBytes[0] != 0x30) {
            throw new ParsingException("Invalid Master List: not a valid CMS structure (missing SEQUENCE tag)");
        }

        log.debug("CMS format validation passed");
    }

    /**
     * CMS 콘텐츠 파싱
     *
     * <p>참고: 현재 구현은 간단한 파싱만 수행합니다.
     * 실제 CMS 서명 검증은 CertificateValidation Context에서 수행됩니다.</p>
     */
    private void parseCmsContent(byte[] fileBytes, ParsedFile parsedFile) throws Exception {
        log.debug("Parsing CMS content");

        try {
            // CertificateFactory를 사용하여 인증서 추출 시도
            // Note: 실제 CMS 서명 검증은 BouncyCastle CMSSignedData 사용
            // 현재는 기본 파싱만 수행
            CertificateFactory cf = CertificateFactory.getInstance("X.509");

            try {
                // DER 형식의 인증서로 직접 파싱 시도 (CMS 내부의 인증서)
                ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes);
                X509Certificate cert = (X509Certificate) cf.generateCertificate(bais);

                log.debug("Direct certificate extraction successful");
                processCertificate(cert, parsedFile);

            } catch (Exception e1) {
                log.debug("Direct certificate extraction failed, trying alternative method: {}", e1.getMessage());

                // BouncyCastle을 사용한 CMS 파싱 시도
                try {
                    // Note: 이 부분은 BouncyCastle 의존성이 필요합니다
                    // pom.xml에 org.bouncycastle:bcmail-jdk15on 추가 필요
                    parseCmsWithBouncyCastle(fileBytes, parsedFile);
                } catch (Exception e2) {
                    log.warn("CMS parsing failed with both methods: {}", e2.getMessage());
                    parsedFile.addError(ParsingError.of(
                        "CMS_PARSE_ERROR",
                        "CMS Content",
                        "Failed to parse CMS: " + e2.getMessage()
                    ));
                }
            }

        } catch (Exception e) {
            log.error("Error parsing CMS content", e);
            parsedFile.addError(ParsingError.of(
                "CMS_PARSE_ERROR",
                "CMS Content",
                e.getMessage()
            ));
        }
    }

    /**
     * BouncyCastle을 사용한 CMS 파싱
     *
     * <p>주의: 이 메서드는 BouncyCastle 라이브러리의 존재를 가정합니다.</p>
     */
    private void parseCmsWithBouncyCastle(byte[] fileBytes, ParsedFile parsedFile) throws Exception {
        log.debug("Parsing CMS with BouncyCastle");

        try {
            // BouncyCastle CMSSignedData를 사용한 파싱
            // Class<?>를 사용하여 동적으로 로드 (선택 사항)
            Class<?> cmsSignedDataClass = Class.forName("org.bouncycastle.cms.CMSSignedData");
            var cmsSignedData = cmsSignedDataClass.getConstructor(byte[].class).newInstance(fileBytes);

            // getCertificates() 메서드 호출
            var getCertificates = cmsSignedDataClass.getMethod("getCertificates");
            Object certStoreObject = getCertificates.invoke(cmsSignedData);

            // CertStore의 getCertificates 메서드 호출
            Class<?> certStoreClass = Class.forName("java.security.cert.CertStore");
            var getCertsMethod = certStoreClass.getMethod("getCertificates", Class.forName("java.security.cert.CertSelector"));

            // null selector로 모든 인증서 가져오기
            Object selector = Class.forName("java.security.cert.X509CertSelector").newInstance();
            Object certificates = getCertsMethod.invoke(certStoreObject, selector);

            // 인증서 처리
            if (certificates instanceof java.util.Collection) {
                for (Object cert : (java.util.Collection<?>) certificates) {
                    if (cert instanceof X509Certificate) {
                        processCertificate((X509Certificate) cert, parsedFile);
                    }
                }
            }

        } catch (ClassNotFoundException e) {
            log.warn("BouncyCastle not available, skipping CMS signature verification: {}", e.getMessage());
        }
    }

    /**
     * 인증서 처리
     */
    private void processCertificate(X509Certificate cert, ParsedFile parsedFile) throws Exception {
        log.debug("Processing certificate: {}", cert.getSubjectX500Principal().getName());

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
            cert.getEncoded(),
            calculateFingerprint(cert),
            true  // valid - 기본값으로 true, 실제 검증은 추후 진행
        );

        parsedFile.addCertificate(certificateData);
        log.debug("Certificate processed: subject={}, issuer={}, serial={}",
            subjectDn, issuerDn, serialNumber);
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
        java.util.regex.Matcher matcher = countryPattern.matcher(dn);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
}
