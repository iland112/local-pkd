package com.smartcoreinc.localpkd.fileparsing.infrastructure.adapter;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCode;
import com.smartcoreinc.localpkd.fileparsing.application.service.CertificateExistenceService;
import com.smartcoreinc.localpkd.fileparsing.domain.model.*;
import com.smartcoreinc.localpkd.fileparsing.domain.port.FileParserPort;
import com.smartcoreinc.localpkd.fileparsing.domain.port.MasterListParser;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessable;
import org.bouncycastle.cms.CMSSignedData;
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
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class MasterListParserAdapter implements FileParserPort, MasterListParser {

    private final ProgressService progressService;
    private final CertificateExistenceService certificateExistenceService; // Inject CertificateExistenceService

    @Value("file:data/cert/UN_CSCA_2.pem")
    private Resource trustAnchorResource;

    public void setTrustAnchorResource(Resource trustAnchorResource) {
        this.trustAnchorResource = trustAnchorResource;
    }

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
            log.info("BouncyCastle Provider registered");
        }
    }

    @Override
    public void parse(byte[] fileBytes, FileFormat fileFormat, ParsedFile parsedFile) throws FileParserPort.ParsingException {
        log.info("=== Master List Parsing started for upload {} ===", parsedFile.getUploadId().getId());

        try {
            if (!supports(fileFormat)) {
                throw new FileParserPort.ParsingException("Unsupported file format: " + fileFormat.getDisplayName());
            }
            
            progressService.sendProgress(ProcessingProgress.parsingStarted(parsedFile.getUploadId().getId(), "Master List"));

            validateCmsFormat(fileBytes);
            progressService.sendProgress(ProcessingProgress.parsingInProgress(parsedFile.getUploadId().getId(), 1, 4, "CMS 형식 검증 완료", 10, 50));

            CMSSignedData signedData = parseCmsAndVerifySignature(fileBytes);
            progressService.sendProgress(ProcessingProgress.parsingInProgress(parsedFile.getUploadId().getId(), 2, 4, "CMS 서명 검증 완료", 10, 50));

            extractCscaCertificates(signedData, parsedFile);
            progressService.sendProgress(ProcessingProgress.parsingInProgress(parsedFile.getUploadId().getId(), 3, 4, "CSCA 인증서 추출 완료", 10, 50));

            log.info("Master List parsing completed: {} CSCA certificates, {} errors",
                parsedFile.getCertificates().size(), parsedFile.getErrors().size());

        } catch (FileParserPort.ParsingException e) {
            log.error("Master List parsing failed", e);
            throw e;
        } catch (Exception e) {
            log.error("Master List parsing failed", e);
            throw new FileParserPort.ParsingException("Master List parsing error: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(FileFormat fileFormat) {
        return fileFormat != null && fileFormat.isMasterList();
    }

    private void validateCmsFormat(byte[] fileBytes) throws FileParserPort.ParsingException {
        if (fileBytes == null || fileBytes.length < 4) {
            throw new FileParserPort.ParsingException("Invalid Master List: file too small");
        }
        if (fileBytes[0] != 0x30) {
            throw new FileParserPort.ParsingException("Invalid Master List: not a valid CMS structure (missing SEQUENCE tag)");
        }
        log.debug("CMS format validation passed");
    }

    private CMSSignedData parseCmsAndVerifySignature(byte[] fileBytes) throws Exception {
        log.debug("=== CMS Parsing and Signature Verification started ===");
        CMSSignedData signedData = new CMSSignedData(fileBytes);
        X509Certificate trustAnchor = loadTrustAnchor();
        boolean signatureValid = verifySignature(signedData, trustAnchor);
        if (!signatureValid) {
            throw new FileParserPort.ParsingException("CMS signature verification failed");
        }
        log.info("✅ CMS signature verified successfully");
        return signedData;
    }

    private X509Certificate loadTrustAnchor() throws Exception {
        try (InputStream is = trustAnchorResource.getInputStream()) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509", "BC");
            return (X509Certificate) certFactory.generateCertificate(is);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean verifySignature(CMSSignedData signedData, X509Certificate trustAnchor) throws Exception {
        Store<X509CertificateHolder> certStore = signedData.getCertificates();
        SignerInformationStore signerInfos = signedData.getSignerInfos();

        for (SignerInformation signer : signerInfos.getSigners()) {
            Collection<X509CertificateHolder> certCollection = certStore.getMatches(signer.getSID());
            if (certCollection.isEmpty()) continue;

            X509CertificateHolder holder = certCollection.iterator().next();
            SignerInformationVerifier verifier = new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(holder);
            if (!signer.verify(verifier)) {
                return false;
            }
        }
        return true;
    }
    
    @SuppressWarnings("unchecked")
    private void extractCscaCertificates(CMSSignedData signedData, ParsedFile parsedFile) throws Exception {
        CMSProcessable signedContent = signedData.getSignedContent();
        byte[] contentBytes = (byte[]) signedContent.getContent();

        try (ASN1InputStream asn1In = new ASN1InputStream(contentBytes)) {
            ASN1Primitive root = asn1In.readObject();
            if (!(root instanceof ASN1Sequence)) {
                throw new FileParserPort.ParsingException("Unexpected ASN.1 structure: not a SEQUENCE");
            }

            ASN1Sequence seq = (ASN1Sequence) root;
            int certSetIndex = validateMasterListStructure(seq);
            ASN1Set certSet = (ASN1Set) seq.getObjectAt(certSetIndex);
            
            JcaX509CertificateConverter converter = new JcaX509CertificateConverter().setProvider("BC");
            for (ASN1Encodable encodable : certSet) {
                try {
                    org.bouncycastle.asn1.x509.Certificate bcCert = org.bouncycastle.asn1.x509.Certificate.getInstance(encodable);
                    X509CertificateHolder holder = new X509CertificateHolder(bcCert);
                    X509Certificate x509Cert = converter.getCertificate(holder);
                    
                    String fingerprint = calculateFingerprint(x509Cert); // Calculate fingerprint
                    // Check for duplicate fingerprint before adding
                    if (!certificateExistenceService.existsByFingerprintSha256(fingerprint)) {
                        parsedFile.addCertificate(createCertificateData(x509Cert, fingerprint));
                    } else {
                        parsedFile.addError(ParsingError.of("DUPLICATE_CERTIFICATE", fingerprint, "Certificate with this fingerprint already exists globally."));
                        log.warn("Duplicate certificate skipped: fingerprint_sha256={}", fingerprint);
                    }
                } catch (Exception e) {
                    parsedFile.addError(ParsingError.of("CERT_PARSE_ERROR", "Certificate", e.getMessage()));
                }
            }
        }
    }

    private int validateMasterListStructure(ASN1Sequence seq) throws FileParserPort.ParsingException {
        if (seq.size() < 1 || seq.size() > 2) {
            throw new FileParserPort.ParsingException("Invalid Master List structure: SEQUENCE size must be 1 or 2, but got " + seq.size());
        }
        if (seq.size() == 1) {
            if (!(seq.getObjectAt(0) instanceof ASN1Set)) throw new FileParserPort.ParsingException("Invalid Master List: expected SET OF Certificate at index 0");
            return 0;
        } else {
            if (!(seq.getObjectAt(1) instanceof ASN1Set)) throw new FileParserPort.ParsingException("Invalid Master List: expected SET OF Certificate at index 1");
            return 1;
        }
    }

    private CertificateData createCertificateData(X509Certificate x509Cert, String fingerprint) throws Exception {
        String subjectDn = x509Cert.getSubjectX500Principal().getName();
        String countryCode = extractCountryCode(subjectDn);
        return CertificateData.of(
            "CSCA",
            countryCode,
            subjectDn,
            x509Cert.getIssuerX500Principal().getName(),
            x509Cert.getSerialNumber().toString(16).toUpperCase(),
            convertToLocalDateTime(x509Cert.getNotBefore()),
            convertToLocalDateTime(x509Cert.getNotAfter()),
            x509Cert.getEncoded(),
            fingerprint, // Use the provided fingerprint
            true,
            null
        );
    }
    
    private String extractCountryCode(String dn) {
        if (dn == null) return null;
        Pattern countryPattern = Pattern.compile("(?:^|,)\\s*C\\s*=\\s*([A-Z]{2})(?:,|$)");
        Matcher matcher = countryPattern.matcher(dn);
        return matcher.find() ? matcher.group(1) : null;
    }

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
    
    private LocalDateTime convertToLocalDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    // ===========================
    // MasterListParser Implementation
    // ===========================

    /**
     * Master List 파싱 (MasterListParser 인터페이스 구현)
     *
     * <p>Phase 3에서 추가된 새로운 파싱 메서드입니다.</p>
     * <p>ParseMasterListFileUseCase에서 MasterList aggregate와 Certificate 엔티티를 생성하기 위해 사용됩니다.</p>
     *
     * @param masterListBytes Master List CMS 바이너리
     * @return MasterListParseResult (MasterList 생성에 필요한 모든 데이터)
     * @throws MasterListParser.ParsingException 파싱 실패 시
     */
    @Override
    public MasterListParseResult parse(byte[] masterListBytes) throws MasterListParser.ParsingException {
        try {
            log.info("=== MasterListParser.parse() started ===");

            // 1. Validate CMS format
            validateCmsFormat(masterListBytes);

            // 2. Parse CMS and verify signature
            CMSSignedData signedData = parseCmsAndVerifySignature(masterListBytes);

            // 3. Extract signer information
            SignerInfo signerInfo = extractSignerInfo(signedData);

            // 4. Extract CSCA certificates
            java.util.List<MasterListParseResult.ParsedCsca> parsedCscas = extractCscaCertificatesForMasterList(signedData);

            // 5. Determine country code (from first CSCA or default to unknown)
            CountryCode countryCode = parsedCscas.isEmpty() ? null :
                    (parsedCscas.get(0).getCountryCode() != null ? parsedCscas.get(0).getCountryCode() : null);

            // 6. Create MasterListParseResult
            MasterListParseResult result = MasterListParseResult.of(
                    countryCode,
                    MasterListVersion.unknown(), // TODO: Extract version if available in CMS
                    CmsBinaryData.of(masterListBytes),
                    signerInfo,
                    parsedCscas
            );

            log.info("✅ MasterListParser.parse() completed successfully: {} CSCAs extracted", result.getCscaCount());
            return result;

        } catch (Exception e) {
            log.error("❌ MasterListParser.parse() failed: {}", e.getMessage(), e);
            throw new MasterListParser.ParsingException("Failed to parse Master List: " + e.getMessage(), e);
        }
    }

    /**
     * 서명자 정보 추출
     *
     * @param signedData CMS SignedData
     * @return SignerInfo (DN, 알고리즘 등)
     */
    @SuppressWarnings("unchecked")
    private SignerInfo extractSignerInfo(CMSSignedData signedData) {
        try {
            Store<X509CertificateHolder> certStore = signedData.getCertificates();
            SignerInformationStore signerInfos = signedData.getSignerInfos();

            for (SignerInformation signer : signerInfos.getSigners()) {
                Collection<X509CertificateHolder> certCollection = certStore.getMatches(signer.getSID());
                if (certCollection.isEmpty()) continue;

                X509CertificateHolder holder = certCollection.iterator().next();
                String signerDn = holder.getSubject().toString();
                String algorithm = signer.getDigestAlgOID();

                java.util.Map<String, Object> signerData = new java.util.HashMap<>();
                signerData.put("signerDN", signerDn);
                signerData.put("signatureAlgorithm", algorithm);

                return SignerInfo.of(signerData);
            }

            return SignerInfo.empty();
        } catch (Exception e) {
            log.warn("Failed to extract signer info: {}", e.getMessage());
            return SignerInfo.empty();
        }
    }

    /**
     * CSCA 인증서 추출 (MasterListParseResult용)
     *
     * <p>기존 extractCscaCertificates()는 ParsedFile을 직접 수정하므로,
     * 새로운 메서드를 추가하여 List<ParsedCsca>를 반환합니다.</p>
     *
     * @param signedData CMS SignedData
     * @return List of ParsedCsca
     * @throws Exception 파싱 실패 시
     */
    private java.util.List<MasterListParseResult.ParsedCsca> extractCscaCertificatesForMasterList(CMSSignedData signedData) throws Exception {
        java.util.List<MasterListParseResult.ParsedCsca> parsedCscas = new java.util.ArrayList<>();

        CMSProcessable signedContent = signedData.getSignedContent();
        byte[] contentBytes = (byte[]) signedContent.getContent();

        try (ASN1InputStream asn1In = new ASN1InputStream(contentBytes)) {
            ASN1Primitive root = asn1In.readObject();
            if (!(root instanceof ASN1Sequence)) {
                throw new FileParserPort.ParsingException("Unexpected ASN.1 structure: not a SEQUENCE");
            }

            ASN1Sequence seq = (ASN1Sequence) root;
            int certSetIndex = validateMasterListStructure(seq);
            ASN1Set certSet = (ASN1Set) seq.getObjectAt(certSetIndex);

            JcaX509CertificateConverter converter = new JcaX509CertificateConverter().setProvider("BC");
            for (ASN1Encodable encodable : certSet) {
                try {
                    org.bouncycastle.asn1.x509.Certificate bcCert = org.bouncycastle.asn1.x509.Certificate.getInstance(encodable);
                    X509CertificateHolder holder = new X509CertificateHolder(bcCert);
                    X509Certificate x509Cert = converter.getCertificate(holder);

                    String fingerprint = calculateFingerprint(x509Cert);
                    String subjectDn = x509Cert.getSubjectX500Principal().getName();
                    String countryCodeStr = extractCountryCode(subjectDn);
                    CountryCode countryCode = countryCodeStr != null ? CountryCode.of(countryCodeStr) : null;

                    parsedCscas.add(MasterListParseResult.ParsedCsca.of(
                            x509Cert,
                            fingerprint,
                            countryCode
                    ));

                } catch (Exception e) {
                    log.warn("Failed to extract CSCA certificate: {}", e.getMessage());
                    // Continue with other certificates
                }
            }
        }

        return parsedCscas;
    }
}
