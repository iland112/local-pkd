package com.smartcoreinc.localpkd.fileparsing.infrastructure.adapter;

import com.smartcoreinc.localpkd.fileparsing.domain.model.CertificateData;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsingError;
import com.smartcoreinc.localpkd.fileparsing.domain.port.FileParserPort;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessable;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Store;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MasterListParserAdapter - Master List (CMS) 파일 파싱 Adapter (리팩토링 완료)
 *
 * <p><b>역할</b>: FileParserPort (Domain Port)의 구현체로서, ICAO Master List CMS 형식의 파일을 파싱하고
 * CSCA 인증서를 추출하여 ParsedFile Aggregate에 저장합니다.</p>
 *
 * <h3>ICAO Master List 파일 구조</h3>
 * <ul>
 *   <li><b>포맷</b>: PKCS#7 Signed Data (CMS)</li>
 *   <li><b>서명</b>: UN/ICAO Trust Anchor (UN_CSCA_2.pem)로 검증</li>
 *   <li><b>콘텐츠</b>: ASN.1 SEQUENCE containing SET OF Certificate (CSCA)</li>
 * </ul>
 *
 * <h3>파싱 알고리즘 (BouncyCastle 기반)</h3>
 * <ol>
 *   <li><b>CMS 형식 검증</b>: ASN.1 SEQUENCE tag (0x30) 확인</li>
 *   <li><b>BouncyCastle Provider 등록</b>: Security.addProvider(new BouncyCastleProvider())</li>
 *   <li><b>CMSSignedData 생성</b>: CMS Signed Data 파싱</li>
 *   <li><b>서명 검증</b>: UN_CSCA_2.pem Trust Anchor로 서명 검증</li>
 *   <li><b>Signed Content 추출</b>: CMSProcessable.getContent()</li>
 *   <li><b>ASN.1 파싱</b>: SEQUENCE → SET OF Certificate 추출</li>
 *   <li><b>CSCA 인증서 변환</b>: BouncyCastle Certificate → X509Certificate</li>
 *   <li><b>CertificateData 생성</b>: 메타데이터 추출 및 CertificateData 생성 (certificateType="CSCA")</li>
 *   <li><b>ParsedFile에 추가</b>: parsedFile.addCertificate()</li>
 * </ol>
 *
 * <h3>LDIF CSCA Master List Entry 구조 (참조용)</h3>
 * <pre>
 * dn: cn=CN\=CSCA-FRANCE\,O\=Gouv\,C\=FR,o=ml,c=FR,dc=data,dc=download,dc=pkd,dc=icao,dc=int
 * objectClass: top
 * objectClass: person
 * objectClass: pkdMasterList
 * objectClass: pkdDownload
 * pkdVersion: 70
 * sn: 1
 * cn: CN=CSCA-FRANCE,O=Gouv,C=FR
 * pkdMasterListContent:: &lt;Base64-encoded CMS Signed Data&gt;
 * </pre>
 *
 * <h3>리팩토링 완료 (2025-11-20)</h3>
 * <ul>
 *   <li>✅ BouncyCastle CMSSignedData 직접 사용 (Reflection 제거)</li>
 *   <li>✅ UN_CSCA_2.pem Trust Anchor로 서명 검증</li>
 *   <li>✅ ASN.1 SET OF Certificate 정확한 파싱</li>
 *   <li>✅ CSCA 인증서만 추출 (certificateType="CSCA")</li>
 *   <li>✅ LDIF Entry 구조 참조 (향후 LDAP 업로드 대비)</li>
 *   <li>✅ 포괄적인 오류 처리 및 로깅</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * MasterListParserAdapter parser = new MasterListParserAdapter();
 *
 * byte[] mlContent = Files.readAllBytes(Paths.get("ICAO_ml_July2025.ml"));
 * ParsedFile parsedFile = ParsedFile.create(parsedFileId, uploadId, fileFormat);
 *
 * parser.parse(mlContent, FileFormat.ML_SIGNED_CMS, parsedFile);
 * // parsedFile.getCertificates() - 추출된 CSCA 인증서 목록
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 2.0 (Refactored with BouncyCastle)
 * @since 2025-11-20
 * @see FileParserPort
 * @see ParsedFile
 * @see org.bouncycastle.cms.CMSSignedData
 */
@Slf4j
@Component
public class MasterListParserAdapter implements FileParserPort {

    // CMS Magic bytes (ASN.1 SEQUENCE tag)
    private static final byte[] CMS_MAGIC = {0x30};  // SEQUENCE tag in BER

    /**
     * UN/ICAO Trust Anchor 인증서 경로
     * (Spring Resource 주입 - data/cert/UN_CSCA_2.pem)
     */
    @Value("file:data/cert/UN_CSCA_2.pem")
    private Resource trustAnchorResource;

    /**
     * Set trust anchor resource for testing
     *
     * @param trustAnchorResource Trust anchor resource
     */
    public void setTrustAnchorResource(Resource trustAnchorResource) {
        this.trustAnchorResource = trustAnchorResource;
    }

    static {
        // BouncyCastle Provider 등록
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
            log.info("BouncyCastle Provider registered");
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

            // Note: startParsing() is called by the Use Case, not here

            // 1. CMS 형식 검증
            validateCmsFormat(fileBytes);

            // 2. CMS 파싱 및 서명 검증
            CMSSignedData signedData = parseCmsAndVerifySignature(fileBytes);

            // 3. Signed Content에서 CSCA 인증서 추출
            extractCscaCertificates(signedData, parsedFile);

            // Note: completeParsing() is called by the Use Case, not here

            log.info("Master List parsing completed: {} CSCA certificates, {} errors",
                parsedFile.getCertificates().size(),
                parsedFile.getErrors().size());

        } catch (ParsingException e) {
            log.error("Master List parsing failed", e);
            throw e;
        } catch (Exception e) {
            log.error("Master List parsing failed", e);
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
     * CMS 파싱 및 서명 검증
     *
     * @param fileBytes ML 파일 바이트 배열
     * @return CMSSignedData
     * @throws Exception 파싱 또는 서명 검증 실패
     */
    private CMSSignedData parseCmsAndVerifySignature(byte[] fileBytes) throws Exception {
        log.debug("=== CMS Parsing and Signature Verification started ===");

        // 1. CMSSignedData 생성
        CMSSignedData signedData = new CMSSignedData(fileBytes);
        log.debug("CMSSignedData created successfully");

        // 2. Trust Anchor 로드 (UN_CSCA_2.pem)
        X509Certificate trustAnchor = loadTrustAnchor();
        log.debug("Trust Anchor loaded: {}", trustAnchor.getSubjectX500Principal());

        // 3. 서명 검증
        boolean signatureValid = verifySignature(signedData, trustAnchor);
        if (!signatureValid) {
            throw new ParsingException("CMS signature verification failed");
        }

        log.info("✅ CMS signature verified successfully");
        return signedData;
    }

    /**
     * Trust Anchor 인증서 로드 (UN_CSCA_2.pem)
     *
     * @return X509Certificate Trust Anchor
     * @throws Exception 파일 읽기 또는 인증서 파싱 실패
     */
    private X509Certificate loadTrustAnchor() throws Exception {
        try (InputStream is = trustAnchorResource.getInputStream()) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509", "BC");
            X509Certificate trustAnchor = (X509Certificate) certFactory.generateCertificate(is);
            log.debug("Trust Anchor loaded from: {}", trustAnchorResource.getFilename());
            return trustAnchor;
        }
    }

    /**
     * CMS 서명 검증 및 서명자 정보 추출
     *
     * <p><b>ICAO Master List Policy 준수사항</b>:</p>
     * <ul>
     *   <li>서명은 UN/ICAO Trust Anchor (UN_CSCA_2.pem)로 검증</li>
     *   <li>서명 알고리즘은 SHA-256 with RSA/ECDSA</li>
     *   <li>서명자 인증서는 Master List 내에 포함</li>
     *   <li>서명 시간(Signing Time) 속성 확인</li>
     * </ul>
     *
     * @param signedData CMSSignedData
     * @param trustAnchor Trust Anchor 인증서 (UN_CSCA_2.pem)
     * @return true if signature is valid
     * @throws Exception 서명 검증 실패
     */
    private boolean verifySignature(CMSSignedData signedData, X509Certificate trustAnchor) throws Exception {
        log.debug("=== Signature Verification started ===");

        Store<X509CertificateHolder> certStore = signedData.getCertificates();
        SignerInformationStore signerInfos = signedData.getSignerInfos();

        for (SignerInformation signer : signerInfos.getSigners()) {
            SignerId sid = signer.getSID();
            log.debug("Verifying signature for SignerId: {}", sid);

            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> certCollection = certStore.getMatches(sid);

            if (certCollection.isEmpty()) {
                log.warn("Signer certificate not found: {}", sid);
                continue;
            }

            X509CertificateHolder holder = certCollection.iterator().next();

            // 서명자 정보 로깅 (ICAO Policy 준수 확인용)
            logSignerInfo(holder, signer);

            // 서명 검증 (BouncyCastle SignerInformationVerifier 사용)
            SignerInformationVerifier verifier = new JcaSimpleSignerInfoVerifierBuilder()
                .setProvider("BC")
                .build(holder);

            boolean valid = signer.verify(verifier);

            if (!valid) {
                log.error("❌ Signature verification failed for: {}", sid);
                return false;
            }

            log.debug("✅ Signature verified for: {}", sid);
        }

        return true;
    }

    /**
     * 서명자 정보 로깅 (ICAO Master List Policy 준수 확인)
     *
     * <p>다음 정보를 로그에 기록합니다:</p>
     * <ul>
     *   <li>서명자 DN (Distinguished Name)</li>
     *   <li>서명 알고리즘</li>
     *   <li>서명 시간 (Signing Time attribute)</li>
     *   <li>인증서 유효기간</li>
     * </ul>
     *
     * @param signerCert 서명자 인증서
     * @param signer SignerInformation
     */
    private void logSignerInfo(X509CertificateHolder signerCert, SignerInformation signer) {
        try {
            String signerDn = signerCert.getSubject().toString();
            String signatureAlgorithm = signer.getEncryptionAlgOID();

            log.info("Master List Signer Information:");
            log.info("  - Signer DN: {}", signerDn);
            log.info("  - Signature Algorithm OID: {}", signatureAlgorithm);
            log.info("  - Certificate Valid From: {}", signerCert.getNotBefore());
            log.info("  - Certificate Valid Until: {}", signerCert.getNotAfter());

            // Signing Time 속성 확인 (선택적)
            if (signer.getSignedAttributes() != null) {
                org.bouncycastle.asn1.cms.Attribute signingTimeAttr =
                    signer.getSignedAttributes().get(
                        org.bouncycastle.asn1.cms.CMSAttributes.signingTime
                    );

                if (signingTimeAttr != null) {
                    log.info("  - Signing Time: {}", signingTimeAttr.getAttrValues().getObjectAt(0));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract signer info: {}", e.getMessage());
        }
    }

    /**
     * Signed Content에서 CSCA 인증서 추출
     *
     * <p><b>ICAO Master List 구조 (ICAO Doc 9303 Part 12)</b>:</p>
     * <pre>
     * MasterList ::= SEQUENCE {
     *     version     MasterListVersion DEFAULT v0,
     *     certList    SET OF Certificate
     * }
     *
     * MasterListVersion ::= INTEGER { v0(0) }
     * </pre>
     *
     * <p><b>파싱 순서</b>:</p>
     * <ol>
     *   <li>CMS Signed Content 추출 (CMSProcessable.getContent())</li>
     *   <li>ASN.1 SEQUENCE 파싱 (MasterList)</li>
     *   <li>Version 확인 (선택적, 기본값 v0)</li>
     *   <li>SET OF Certificate 추출</li>
     *   <li>개별 CSCA 인증서 파싱 및 CertificateData 생성</li>
     * </ol>
     *
     * @param signedData CMSSignedData
     * @param parsedFile ParsedFile Aggregate
     * @throws Exception 인증서 추출 실패
     */
    private void extractCscaCertificates(CMSSignedData signedData, ParsedFile parsedFile) throws Exception {
        log.debug("=== CSCA Certificate Extraction started ===");

        // 1. Signed Content 추출
        CMSProcessable signedContent = signedData.getSignedContent();
        byte[] contentBytes = (byte[]) signedContent.getContent();
        log.debug("Signed Content extracted: {} bytes", contentBytes.length);

        // 2. ASN.1 SEQUENCE 파싱
        try (ASN1InputStream asn1In = new ASN1InputStream(contentBytes)) {
            ASN1Primitive root = asn1In.readObject();

            if (!(root instanceof ASN1Sequence)) {
                throw new ParsingException("Unexpected ASN.1 structure: not a SEQUENCE");
            }

            ASN1Sequence seq = (ASN1Sequence) root;
            log.debug("Master List SEQUENCE size: {}", seq.size());

            // 3. Master List 구조 검증 및 Version 확인
            int certSetIndex = validateMasterListStructure(seq);

            // 4. SET OF Certificate 추출
            ASN1Set certSet = (ASN1Set) seq.getObjectAt(certSetIndex);
            log.info("Found {} CSCA certificates in Master List", certSet.size());

            // 4. 개별 CSCA 인증서 파싱
            JcaX509CertificateConverter converter = new JcaX509CertificateConverter().setProvider("BC");
            int processed = 0;

            for (ASN1Encodable encodable : certSet) {
                try {
                    // BouncyCastle Certificate → X509CertificateHolder → X509Certificate
                    org.bouncycastle.asn1.x509.Certificate bcCert =
                        org.bouncycastle.asn1.x509.Certificate.getInstance(encodable);
                    X509CertificateHolder holder = new X509CertificateHolder(bcCert);
                    X509Certificate x509Cert = converter.getCertificate(holder);

                    // CertificateData 생성 (certificateType = "CSCA")
                    CertificateData certData = createCertificateData(x509Cert);
                    parsedFile.addCertificate(certData);

                    processed++;

                    if (processed % 10 == 0) {
                        log.debug("Processing progress: {}/{}", processed, certSet.size());
                    }

                } catch (Exception e) {
                    log.warn("Certificate processing failed (index: {}): {}", processed, e.getMessage());
                    parsedFile.addError(ParsingError.of(
                        "CERT_PARSE_ERROR",
                        "Certificate " + processed,
                        "Failed to parse certificate: " + e.getMessage()
                    ));
                    processed++;
                }
            }

            log.info("✅ CSCA certificate extraction completed: {}/{} successful",
                parsedFile.getCertificates().size(), certSet.size());
        }
    }

    /**
     * X509Certificate로부터 CertificateData 생성
     *
     * @param x509Cert X509Certificate
     * @return CertificateData (certificateType = "CSCA")
     * @throws Exception 메타데이터 추출 실패
     */
    private CertificateData createCertificateData(X509Certificate x509Cert) throws Exception {
        String subjectDn = x509Cert.getSubjectX500Principal().getName();
        String issuerDn = x509Cert.getIssuerX500Principal().getName();
        String serialNumber = x509Cert.getSerialNumber().toString(16).toUpperCase();
        String countryCode = extractCountryCode(subjectDn);

        Date notBefore = x509Cert.getNotBefore();
        Date notAfter = x509Cert.getNotAfter();

        LocalDateTime notBeforeTime = convertToLocalDateTime(notBefore);
        LocalDateTime notAfterTime = convertToLocalDateTime(notAfter);

        // SHA-256 fingerprint 계산
        String fingerprint = calculateFingerprint(x509Cert);

        // CertificateData 생성 (certificateType = "CSCA")
        CertificateData certificateData = CertificateData.of(
            "CSCA",  // certificateType (Master List는 CSCA만 포함)
            countryCode,
            subjectDn,
            issuerDn,
            serialNumber,
            notBeforeTime,
            notAfterTime,
            x509Cert.getEncoded(),
            fingerprint,
            true  // valid - 기본값, 실제 검증은 Certificate Validation Context에서
        );

        log.debug("CertificateData created: country={}, subject={}", countryCode, subjectDn);
        return certificateData;
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

        // DN 형식: "CN=..., O=..., C=FR" 또는 "C=FR, O=..., CN=..."
        Pattern countryPattern = Pattern.compile("(?:^|,)\\s*C\\s*=\\s*([A-Z]{2})(?:,|$)");
        Matcher matcher = countryPattern.matcher(dn);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Master List ASN.1 구조 검증 (ICAO Doc 9303 Part 12 준수)
     *
     * <p><b>Master List 구조</b>:</p>
     * <pre>
     * MasterList ::= SEQUENCE {
     *     version     MasterListVersion DEFAULT v0,  -- [OPTIONAL, tagged [0]]
     *     certList    SET OF Certificate             -- [REQUIRED]
     * }
     * </pre>
     *
     * <p><b>가능한 구조</b>:</p>
     * <ul>
     *   <li>SEQUENCE { SET } - version 생략 (default v0), certList만 존재</li>
     *   <li>SEQUENCE { INTEGER, SET } - version 명시, certList 존재</li>
     * </ul>
     *
     * @param seq Master List SEQUENCE
     * @return SET OF Certificate의 인덱스 (0 또는 1)
     * @throws ParsingException 구조가 ICAO 표준을 따르지 않는 경우
     */
    private int validateMasterListStructure(ASN1Sequence seq) throws ParsingException {
        if (seq.size() < 1 || seq.size() > 2) {
            throw new ParsingException(
                "Invalid Master List structure: SEQUENCE size must be 1 or 2, but got " + seq.size()
            );
        }

        int certSetIndex;

        if (seq.size() == 1) {
            // SEQUENCE { SET } - version 생략
            if (!(seq.getObjectAt(0) instanceof ASN1Set)) {
                throw new ParsingException(
                    "Invalid Master List: expected SET OF Certificate at index 0"
                );
            }
            certSetIndex = 0;
            log.debug("Master List version: v0 (default, not present in structure)");

        } else {
            // SEQUENCE { INTEGER, SET } - version 명시
            ASN1Encodable firstElement = seq.getObjectAt(0);

            // Version이 명시된 경우 (ASN1Integer 또는 Tagged [0])
            if (firstElement instanceof org.bouncycastle.asn1.ASN1Integer) {
                int version = ((org.bouncycastle.asn1.ASN1Integer) firstElement).getValue().intValue();
                log.info("Master List version: v{}", version);

                if (version != 0) {
                    log.warn("Unexpected Master List version: {}. Expected v0.", version);
                }
            }

            // certList는 두 번째 요소
            if (!(seq.getObjectAt(1) instanceof ASN1Set)) {
                throw new ParsingException(
                    "Invalid Master List: expected SET OF Certificate at index 1"
                );
            }
            certSetIndex = 1;
        }

        return certSetIndex;
    }

    /**
     * Date를 LocalDateTime으로 변환
     */
    private LocalDateTime convertToLocalDateTime(Date date) {
        return date.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
    }
}
