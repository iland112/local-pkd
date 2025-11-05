package com.smartcoreinc.localpkd.fileparsing.infrastructure.adapter;

import com.smartcoreinc.localpkd.fileparsing.domain.model.CertificateData;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsingError;
import com.smartcoreinc.localpkd.fileparsing.domain.port.FileParserPort;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Store;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
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
 *   <li>ML_SIGNED_CMS: Master List (Signed CMS) - ICAO PKD Master List</li>
 * </ul>
 *
 * <p><b>파싱 알고리즘</b>:</p>
 * <ol>
 *   <li>CMS 구조 검증 (Magic bytes 확인)</li>
 *   <li>BouncyCastle을 사용한 CMS 서명 검증</li>
 *   <li>신뢰 인증서(UN_CSCA_2.pem)를 사용하여 서명자 검증</li>
 *   <li>서명된 콘텐츠에서 인증서 목록 추출</li>
 *   <li>각 인증서 파싱 및 메타데이터 추출</li>
 *   <li>ParsedFile에 CertificateData 추가</li>
 * </ol>
 *
 * <p><b>CMS 서명 검증</b>:</p>
 * <ul>
 *   <li>신뢰 인증서: UN_CSCA_2.pem (application.properties에서 설정)</li>
 *   <li>검증 실패 시: ParsingError 추가하지만 파싱은 계속 진행</li>
 * </ul>
 *
 * <p><b>오류 처리</b>:</p>
 * <ul>
 *   <li>CMS 형식 검증 실패: ParsingException 발생</li>
 *   <li>서명 검증 실패: ParsingError 추가</li>
 *   <li>인증서 파싱 오류: ParsingError 추가</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 2.0
 * @since 2025-11-05
 * @see FileParserPort
 * @see ParsedFile
 * @see org.bouncycastle.cms.CMSSignedData
 */
@Slf4j
@Component
public class MasterListParserAdapter implements FileParserPort {

    // CMS Magic bytes (ASN.1 SEQUENCE tag)
    private static final byte[] CMS_MAGIC = {0x30};  // SEQUENCE tag in BER

    @Value("${app.masterlist.trust-cert-path}")
    private Resource trustCertResource;

    private X509Certificate trustCertificate;

    /**
     * BouncyCastle Provider 등록 및 신뢰 인증서 로드
     */
    @PostConstruct
    public void init() {
        // BouncyCastle Provider 등록
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
            log.info("BouncyCastle Provider registered");
        }

        // 신뢰 인증서 로드
        loadTrustCertificate();
    }

    /**
     * UN_CSCA_2.pem 신뢰 인증서 로드
     */
    private void loadTrustCertificate() {
        try {
            log.info("Loading trust certificate from: {}", trustCertResource.getDescription());

            try (InputStream is = trustCertResource.getInputStream()) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                trustCertificate = (X509Certificate) cf.generateCertificate(is);

                log.info("Trust certificate loaded successfully");
                log.info("  - Subject: {}", trustCertificate.getSubjectX500Principal().getName());
                log.info("  - Issuer: {}", trustCertificate.getIssuerX500Principal().getName());
                log.info("  - Valid from: {} to {}",
                    trustCertificate.getNotBefore(),
                    trustCertificate.getNotAfter());
            }
        } catch (Exception e) {
            log.error("Failed to load trust certificate", e);
            throw new RuntimeException("Failed to load trust certificate: " + e.getMessage(), e);
        }
    }

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

            // 2. CMS 파싱 및 인증서 추출
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
        return fileFormat.isMasterList();
    }

    /**
     * CMS 형식 검증
     *
     * @param fileBytes 파일 바이트 배열
     * @throws ParsingException CMS 형식이 아닌 경우
     */
    private void validateCmsFormat(byte[] fileBytes) throws ParsingException {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new ParsingException("File content is empty");
        }

        // Check for CMS magic bytes (ASN.1 SEQUENCE)
        if (fileBytes[0] != CMS_MAGIC[0]) {
            throw new ParsingException("Invalid CMS format: missing SEQUENCE tag");
        }

        log.debug("CMS format validation passed");
    }

    /**
     * CMS 콘텐츠 파싱 및 인증서 추출
     *
     * <p>BouncyCastle CMSSignedData를 사용하여 CMS 서명을 검증하고,
     * 서명된 콘텐츠에서 인증서를 추출합니다.</p>
     *
     * <p><b>처리 과정</b>:</p>
     * <ol>
     *   <li>CMSSignedData 객체 생성</li>
     *   <li>서명자(Signer) 정보 추출</li>
     *   <li>신뢰 인증서를 사용하여 서명 검증</li>
     *   <li>인증서 Store에서 모든 인증서 추출</li>
     *   <li>각 인증서를 CertificateData로 변환</li>
     * </ol>
     *
     * @param fileBytes 파일 바이트 배열
     * @param parsedFile ParsedFile Aggregate
     */
    private void parseCmsContent(byte[] fileBytes, ParsedFile parsedFile) {
        log.debug("Parsing CMS content with BouncyCastle");

        try {
            // 1. CMSSignedData 객체 생성
            CMSSignedData cmsSignedData = new CMSSignedData(fileBytes);
            log.debug("CMSSignedData created successfully");

            // 2. 서명 검증
            boolean signatureValid = verifyCmsSignature(cmsSignedData, parsedFile);
            if (signatureValid) {
                log.info("CMS signature verification: PASSED");
            } else {
                log.warn("CMS signature verification: FAILED (continuing with certificate extraction)");
            }

            // 3. 인증서 추출
            Store<X509CertificateHolder> certStore = cmsSignedData.getCertificates();
            Collection<X509CertificateHolder> certHolders = certStore.getMatches(null);

            log.info("Found {} certificates in CMS", certHolders.size());

            // 4. 각 인증서 처리
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            int processedCount = 0;
            int errorCount = 0;

            for (X509CertificateHolder certHolder : certHolders) {
                try {
                    // X509CertificateHolder → X509Certificate 변환
                    byte[] certBytes = certHolder.getEncoded();
                    ByteArrayInputStream bais = new ByteArrayInputStream(certBytes);
                    X509Certificate cert = (X509Certificate) cf.generateCertificate(bais);

                    // 인증서 처리
                    processCertificate(cert, parsedFile);
                    processedCount++;

                    log.debug("Certificate processed [{}/{}]: subject={}",
                        processedCount, certHolders.size(),
                        cert.getSubjectX500Principal().getName());

                } catch (Exception e) {
                    errorCount++;
                    log.error("Failed to process certificate", e);
                    parsedFile.addError(ParsingError.of(
                        "CERT_PROCESSING_ERROR",
                        "Failed to process certificate: " + e.getMessage()
                    ));
                }
            }

            log.info("Certificate extraction completed: {} processed, {} errors",
                processedCount, errorCount);

        } catch (Exception e) {
            log.error("CMS parsing failed", e);
            parsedFile.addError(ParsingError.of(
                "CMS_PARSING_ERROR",
                "Failed to parse CMS content: " + e.getMessage()
            ));
        }
    }

    /**
     * CMS 서명 검증
     *
     * <p>신뢰 인증서(UN_CSCA_2.pem)를 사용하여 CMS 서명을 검증합니다.</p>
     *
     * @param cmsSignedData CMSSignedData 객체
     * @param parsedFile ParsedFile Aggregate (오류 기록용)
     * @return 서명 검증 성공 여부
     */
    private boolean verifyCmsSignature(CMSSignedData cmsSignedData, ParsedFile parsedFile) {
        try {
            SignerInformationStore signers = cmsSignedData.getSignerInfos();
            Collection<SignerInformation> signerCollection = signers.getSigners();

            if (signerCollection.isEmpty()) {
                log.warn("No signers found in CMS");
                parsedFile.addError(ParsingError.of(
                    "NO_SIGNERS",
                    "No signers found in CMS signature"
                ));
                return false;
            }

            log.debug("Found {} signer(s) in CMS", signerCollection.size());

            // 첫 번째 서명자 검증
            SignerInformation signer = signerCollection.iterator().next();

            // 신뢰 인증서를 사용하여 서명 검증
            JcaSimpleSignerInfoVerifierBuilder verifierBuilder =
                new JcaSimpleSignerInfoVerifierBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME);

            boolean verified = signer.verify(verifierBuilder.build(trustCertificate));

            if (verified) {
                log.info("CMS signature verified successfully with trust certificate");
            } else {
                log.warn("CMS signature verification failed");
                parsedFile.addError(ParsingError.of(
                    "SIGNATURE_VERIFICATION_FAILED",
                    "CMS signature verification failed with trust certificate"
                ));
            }

            return verified;

        } catch (Exception e) {
            log.error("CMS signature verification failed", e);
            parsedFile.addError(ParsingError.of(
                "SIGNATURE_VERIFICATION_ERROR",
                "CMS signature verification error: " + e.getMessage()
            ));
            return false;
        }
    }

    /**
     * 개별 인증서 처리 및 CertificateData 생성
     *
     * @param cert X509Certificate
     * @param parsedFile ParsedFile Aggregate
     */
    private void processCertificate(X509Certificate cert, ParsedFile parsedFile) throws Exception {
        log.debug("Processing certificate: {}", cert.getSubjectX500Principal().getName());

        // 1. 발급자 정보 추출
        String issuer = cert.getIssuerX500Principal().getName();
        String subject = cert.getSubjectX500Principal().getName();

        // 2. 국가 코드 추출 (C=KR 형식)
        String countryCode = extractCountryCode(subject);

        // 3. 일련번호 추출
        String serialNumber = cert.getSerialNumber().toString(16).toUpperCase();

        // 4. 유효기간 추출
        Date notBefore = cert.getNotBefore();
        Date notAfter = cert.getNotAfter();
        LocalDateTime validFrom = LocalDateTime.ofInstant(notBefore.toInstant(), ZoneId.systemDefault());
        LocalDateTime validUntil = LocalDateTime.ofInstant(notAfter.toInstant(), ZoneId.systemDefault());

        // 5. 지문 계산
        String fingerprint = calculateFingerprint(cert);

        // 6. 인증서 타입 결정 (CSCA, DSC 등)
        String certificateType = determineCertificateType(subject);

        // 7. CertificateData 생성 (valid: true - 기본값, 실제 검증은 Certificate Validation Context에서)
        CertificateData certificateData = CertificateData.of(
            certificateType,
            countryCode,
            subject,
            issuer,
            serialNumber,
            validFrom,
            validUntil,
            cert.getEncoded(),
            fingerprint,
            true  // 기본값: 서명 검증 성공 시 true
        );

        parsedFile.addCertificate(certificateData);
        log.debug("Certificate processed: subject={}, issuer={}, serial={}",
            subject, issuer, serialNumber);
    }

    /**
     * Subject DN을 분석하여 인증서 타입 결정
     *
     * <p>Master List CMS 파일의 경우, 대부분의 인증서가 CSCA 타입입니다.
     * CertificateData는 CSCA, DSC, DSC_NC 타입만 허용하므로, 이에 맞춰 매핑합니다.</p>
     *
     * @param subjectDN Subject Distinguished Name
     * @return 인증서 타입 (CSCA, DSC, DSC_NC)
     */
    private String determineCertificateType(String subjectDN) {
        String upperDN = subjectDN.toUpperCase();

        // CSCA 타입 감지
        if (upperDN.contains("CSCA") || upperDN.contains("COUNTRY SIGNING CA")) {
            return "CSCA";
        }

        // DSC 타입 감지
        if (upperDN.contains("DSC") || upperDN.contains("DOCUMENT SIGNER")) {
            return "DSC";
        }

        // DSC_NC 타입 감지 (DSC with Name Change)
        if (upperDN.contains("DSC_NC") || upperDN.contains("DSC-NC")) {
            return "DSC_NC";
        }

        // DS 또는 SIGNER로 표시된 경우 DSC로 분류
        if (upperDN.contains("DS") || upperDN.contains("SIGNER")) {
            log.debug("Classifying certificate with 'DS' or 'SIGNER' as DSC: {}", subjectDN);
            return "DSC";
        }

        // Master List CMS의 경우, 대부분 CSCA이므로 기본값 CSCA 반환
        log.debug("Unknown certificate type, defaulting to CSCA: {}", subjectDN);
        return "CSCA";
    }

    /**
     * Subject DN에서 국가 코드 추출
     */
    private String extractCountryCode(String subjectDN) {
        Pattern pattern = Pattern.compile("C=([A-Z]{2})");
        java.util.regex.Matcher matcher = pattern.matcher(subjectDN);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "UNKNOWN";
    }

    /**
     * 인증서 지문(Fingerprint) 계산 (SHA-256)
     */
    private String calculateFingerprint(X509Certificate cert) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] der = cert.getEncoded();
        byte[] digest = md.digest(der);

        StringBuilder hexString = new StringBuilder();
        for (byte b : digest) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString().toUpperCase();
    }
}
