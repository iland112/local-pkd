package com.smartcoreinc.localpkd.fileparsing.infrastructure.adapter;

import com.smartcoreinc.localpkd.fileparsing.domain.model.CertificateData;
import com.smartcoreinc.localpkd.fileparsing.domain.model.CrlData;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsingError;
import com.smartcoreinc.localpkd.fileparsing.domain.port.FileParserPort;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter.UnboundIdLdapAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldif.LDIFReader;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

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

    private final UnboundIdLdapAdapter ldapAdapter;

    // LDIF attribute names for certificates, CRLs, and Master Lists
    private static final String ATTR_USER_CERTIFICATE = "userCertificate;binary";
    private static final String ATTR_CRL = "certificateRevocationList;binary";
    private static final String ATTR_MASTER_LIST_CONTENT = "pkdMasterListContent";

    @Override
    public void parse(byte[] fileBytes, FileFormat fileFormat, ParsedFile parsedFile) throws ParsingException {
        log.info("=== LDIF Parsing started (UnboundId LDIFReader) ===");
        log.info("File format: {}, File size: {} bytes", fileFormat.getDisplayName(), fileBytes.length);

        try {
            if (!supports(fileFormat)) {
                throw new ParsingException("Unsupported file format: " + fileFormat.getDisplayName());
            }

            // LDAP 연결 확인
            if (!ldapAdapter.isConnected()) {
                log.info("LDAP not connected, connecting...");
                ldapAdapter.connect();
            }

            int certificateCount = 0;
            int crlCount = 0;
            int errorCount = 0;
            int ldapUploadCount = 0;
            int ldapSkipCount = 0;
            int entryNumber = 0;

            // UnboundId LDIFReader 사용 - RFC 2849 표준 준수, 라인 연속 자동 처리
            try (LDIFReader ldifReader = new LDIFReader(new ByteArrayInputStream(fileBytes))) {

                Entry entry;
                while ((entry = ldifReader.readEntry()) != null) {
                    entryNumber++;

                    try {
                        String dn = entry.getDN();

                        if (entryNumber % 1000 == 0) {
                            log.info("Processing entry #{}: {}", entryNumber, dn);
                        }

                        boolean hasCertificate = false;
                        boolean hasCrl = false;

                        // 인증서 속성 확인 (userCertificate;binary)
                        Attribute certAttr = entry.getAttribute(ATTR_USER_CERTIFICATE);
                        if (certAttr != null && certAttr.getValueByteArray() != null) {
                            try {
                                parseCertificateFromBytes(certAttr.getValueByteArray(), dn, parsedFile);
                                certificateCount++;
                                hasCertificate = true;
                            } catch (Exception e) {
                                log.warn("Error parsing certificate at entry #{}: {}", entryNumber, e.getMessage());
                                parsedFile.addError(ParsingError.of(
                                    "CERTIFICATE_PARSE_ERROR",
                                    "Entry #" + entryNumber + " (" + dn + ")",
                                    e.getMessage()
                                ));
                                errorCount++;
                            }
                        }

                        // CRL 속성 확인 (certificateRevocationList;binary)
                        Attribute crlAttr = entry.getAttribute(ATTR_CRL);
                        if (crlAttr != null && crlAttr.getValueByteArray() != null) {
                            try {
                                parseCrlFromBytes(crlAttr.getValueByteArray(), dn, parsedFile);
                                crlCount++;
                                hasCrl = true;
                            } catch (Exception e) {
                                log.warn("Error parsing CRL at entry #{}: {}", entryNumber, e.getMessage());
                                parsedFile.addError(ParsingError.of(
                                    "CRL_PARSE_ERROR",
                                    "Entry #" + entryNumber + " (" + dn + ")",
                                    e.getMessage()
                                ));
                                errorCount++;
                            }
                        }

                        // Master List Content 속성 확인 (pkdMasterListContent)
                        Attribute mlAttr = entry.getAttribute(ATTR_MASTER_LIST_CONTENT);
                        if (mlAttr != null && mlAttr.getValueByteArray() != null) {
                            try {
                                int extractedCerts = parseMasterListContent(mlAttr.getValueByteArray(), dn, parsedFile);
                                certificateCount += extractedCerts;
                                if (extractedCerts > 0) {
                                    hasCertificate = true;
                                    log.info("Extracted {} certificates from Master List at entry #{}", extractedCerts, entryNumber);
                                }
                            } catch (Exception e) {
                                log.warn("Error parsing Master List at entry #{}: {}", entryNumber, e.getMessage());
                                parsedFile.addError(ParsingError.of(
                                    "MASTER_LIST_PARSE_ERROR",
                                    "Entry #" + entryNumber + " (" + dn + ")",
                                    e.getMessage()
                                ));
                                errorCount++;
                            }
                        }

                        // OpenLDAP 직접 등록 (Entry 객체를 LDIF 문자열로 변환)
                        if (hasCertificate || hasCrl) {
                            try {
                                String ldifText = entry.toLDIFString();
                                boolean uploaded = ldapAdapter.addLdifEntry(ldifText);
                                if (uploaded) {
                                    ldapUploadCount++;
                                    if (ldapUploadCount % 100 == 0) {
                                        log.info("LDAP upload progress: {} entries uploaded", ldapUploadCount);
                                    }
                                } else {
                                    ldapSkipCount++;
                                }
                            } catch (Exception ldapEx) {
                                log.warn("LDAP upload failed for entry #{}: {}", entryNumber, ldapEx.getMessage());
                                parsedFile.addError(ParsingError.of(
                                    "LDAP_UPLOAD_ERROR",
                                    "Entry #" + entryNumber + " (" + dn + ")",
                                    ldapEx.getMessage()
                                ));
                            }
                        }

                    } catch (Exception e) {
                        log.warn("Error processing entry #{}: {}", entryNumber, e.getMessage());
                        parsedFile.addError(ParsingError.of(
                            "ENTRY_PROCESSING_ERROR",
                            "Entry #" + entryNumber,
                            e.getMessage()
                        ));
                        errorCount++;
                    }
                }
            }

            int totalEntries = certificateCount + crlCount + errorCount;

            log.info("=== LDIF parsing completed ===");
            log.info("Total entries processed: {}", entryNumber);
            log.info("PostgreSQL: {} certificates, {} CRLs, {} errors (total: {})",
                certificateCount, crlCount, errorCount, totalEntries);
            log.info("OpenLDAP: {} uploaded, {} skipped (duplicates)",
                ldapUploadCount, ldapSkipCount);

        } catch (Exception e) {
            log.error("LDIF parsing failed", e);
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
     * Byte array 기반 X.509 인증서 파싱 (LDIFReader용)
     */
    private void parseCertificateFromBytes(byte[] certificateBytes, String dn, ParsedFile parsedFile) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
            new ByteArrayInputStream(certificateBytes)
        );

        String subjectDn = cert.getSubjectX500Principal().getName();
        String issuerDn = cert.getIssuerX500Principal().getName();
        String serialNumber = cert.getSerialNumber().toString(16).toUpperCase();
        String countryCode = extractCountryCode(subjectDn);

        // DN에서 organization(o=) 필드를 추출하여 인증서 타입 결정
        String certType = extractCertificateType(dn);

        LocalDateTime notBeforeTime = cert.getNotBefore().toInstant()
            .atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime notAfterTime = cert.getNotAfter().toInstant()
            .atZone(ZoneId.systemDefault()).toLocalDateTime();

        CertificateData certificateData = CertificateData.of(
            certType, countryCode, subjectDn, issuerDn, serialNumber,
            notBeforeTime, notAfterTime, certificateBytes,
            calculateFingerprint(cert), true
        );

        parsedFile.addCertificate(certificateData);
    }

    /**
     * Byte array 기반 CRL 파싱 (LDIFReader용)
     */
    private void parseCrlFromBytes(byte[] crlBytes, String dn, ParsedFile parsedFile) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        java.security.cert.X509CRL x509Crl = (java.security.cert.X509CRL) cf.generateCRL(
            new ByteArrayInputStream(crlBytes)
        );

        String issuerDn = x509Crl.getIssuerX500Principal().getName();
        String countryCode = extractCountryCode(issuerDn);

        LocalDateTime thisUpdateTime = x509Crl.getThisUpdate().toInstant()
            .atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime nextUpdateTime = x509Crl.getNextUpdate() != null ?
            x509Crl.getNextUpdate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null;

        int revokedCount = x509Crl.getRevokedCertificates() != null ?
            x509Crl.getRevokedCertificates().size() : 0;

        String crlNumber = thisUpdateTime.toString().replaceAll("[^0-9]", "");

        CrlData crlData = CrlData.of(
            countryCode, issuerDn, crlNumber, thisUpdateTime, nextUpdateTime,
            crlBytes, revokedCount, true
        );

        parsedFile.addCrl(crlData);
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
     * Master List Content (CMS 형식) 파싱 및 포함된 CSCA 인증서 추출
     *
     * @param masterListBytes Master List Content 바이트 배열 (CMS 형식)
     * @param dn LDAP Distinguished Name
     * @param parsedFile ParsedFile Aggregate
     * @return 추출된 인증서 개수
     * @throws Exception 파싱 오류
     */
    private int parseMasterListContent(byte[] masterListBytes, String dn, ParsedFile parsedFile) throws Exception {
        int extractedCount = 0;

        try {
            // CMS SignedData 파싱
            org.bouncycastle.cms.CMSSignedData cmsSignedData =
                new org.bouncycastle.cms.CMSSignedData(masterListBytes);

            // SignedData에 포함된 인증서들 추출
            org.bouncycastle.util.Store certStore = cmsSignedData.getCertificates();
            org.bouncycastle.util.StoreException storeException = null;

            @SuppressWarnings("unchecked")
            java.util.Collection<org.bouncycastle.cert.X509CertificateHolder> certCollection =
                (java.util.Collection<org.bouncycastle.cert.X509CertificateHolder>) certStore.getMatches(null);

            log.debug("Master List contains {} certificates", certCollection.size());

            // 각 인증서를 파싱
            for (org.bouncycastle.cert.X509CertificateHolder certHolder : certCollection) {
                try {
                    // X509CertificateHolder를 byte[]로 변환
                    byte[] certBytes = certHolder.getEncoded();

                    // 기존 parseCertificateFromBytes 메서드 재사용
                    // Master List의 경우 DN에서 "o=ml"을 "o=csca"로 변경하여 CSCA 타입으로 인식
                    String modifiedDn = dn.replace("o=ml", "o=csca");
                    parseCertificateFromBytes(certBytes, modifiedDn, parsedFile);
                    extractedCount++;
                } catch (Exception e) {
                    log.warn("Error parsing certificate from Master List: {}", e.getMessage());
                    parsedFile.addError(ParsingError.of(
                        "MASTER_LIST_CERT_PARSE_ERROR",
                        "Master List DN: " + dn,
                        e.getMessage()
                    ));
                }
            }

            log.info("Extracted {} CSCA certificates from Master List", extractedCount);

        } catch (org.bouncycastle.cms.CMSException e) {
            log.error("Error parsing CMS Master List Content: {}", e.getMessage());
            throw new Exception("CMS parsing error: " + e.getMessage(), e);
        }

        return extractedCount;
    }

    /**
     * DN에서 organization(o=) 필드를 추출하여 인증서 타입 결정
     *
     * <p>ICAO PKD에서 Non-Conformant 인증서는 두 가지 방식으로 식별됩니다:</p>
     * <ul>
     *   <li>1. DN에 {@code dc=nc-data} 경로가 포함됨 (ICAO PKD 표준 방식)</li>
     *   <li>2. {@code o=dsc_nc} 값을 가짐 (하위 호환성)</li>
     * </ul>
     *
     * @param dn LDAP Distinguished Name
     * @return 인증서 타입 (CSCA, DSC, DSC_NC 중 하나)
     */
    private String extractCertificateType(String dn) {
        if (dn == null || dn.isEmpty()) {
            return "DSC";  // 기본값
        }

        // 1. DN에 "dc=nc-data"가 포함되어 있으면 Non-Conformant 데이터
        boolean isNonConformant = dn.toLowerCase().contains("dc=nc-data");

        // DN에서 o= 필드 추출 (대소문자 무시)
        Pattern orgPattern = Pattern.compile("(?:^|,)\\s*[Oo]\\s*=\\s*([^,]+)");
        Matcher matcher = orgPattern.matcher(dn);

        if (matcher.find()) {
            String orgValue = matcher.group(1).trim().toLowerCase();

            // organization 값에 따라 인증서 타입 결정
            if (orgValue.equals("csca")) {
                return "CSCA";
            } else if (orgValue.equals("dsc") || orgValue.equals("ds")) {
                // dc=nc-data 경로에 있는 DSC는 DSC_NC로 분류
                if (isNonConformant) {
                    log.debug("DSC in nc-data path, treating as DSC_NC: {}", dn);
                    return "DSC_NC";
                }
                return "DSC";
            } else if (orgValue.equals("dsc_nc")) {
                return "DSC_NC";
            } else if (orgValue.equals("crl")) {
                // CRL은 인증서가 아니므로 예외 발생
                throw new IllegalArgumentException("CRL entry cannot be parsed as certificate");
            }
            // bcsc, d 등 기타 값들은 DSC로 간주 (nc-data 경로면 DSC_NC)
            log.debug("Unknown organization type '{}' in DN, treating as {}",
                orgValue, isNonConformant ? "DSC_NC" : "DSC");
            return isNonConformant ? "DSC_NC" : "DSC";
        }

        // o= 필드가 없으면 기본값 DSC (nc-data 경로면 DSC_NC)
        return isNonConformant ? "DSC_NC" : "DSC";
    }
}
