package com.smartcoreinc.localpkd.fileparsing.infrastructure.adapter;

import com.smartcoreinc.localpkd.fileparsing.application.service.CertificateExistenceService;
import com.smartcoreinc.localpkd.fileparsing.domain.model.CertificateData;
import com.smartcoreinc.localpkd.fileparsing.domain.model.CrlData;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsingError;
import com.smartcoreinc.localpkd.fileparsing.domain.port.FileParserPort;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;
import com.smartcoreinc.localpkd.shared.progress.ProcessingProgress;
import com.smartcoreinc.localpkd.shared.progress.ProgressService;

import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldif.LDIFReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.util.Store;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.cert.X509CRL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class LdifParserAdapter implements FileParserPort {

    private final ProgressService progressService;
    private final CertificateExistenceService certificateExistenceService;
    private final com.smartcoreinc.localpkd.fileparsing.domain.repository.MasterListRepository masterListRepository;  // NEW: For LDIF Master List storage
    private final com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository certificateRepository;  // NEW: For Master List CSCAs

    private static final String ATTR_USER_CERTIFICATE = "userCertificate;binary";
    private static final String ATTR_CRL = "certificateRevocationList;binary";
    private static final String ATTR_MASTER_LIST_CONTENT = "pkdMasterListContent";

    @Override
    public boolean supports(FileFormat fileFormat) {
        return fileFormat != null && fileFormat.isLdif();
    }

    @Override
    public void parse(byte[] fileBytes, FileFormat fileFormat, ParsedFile parsedFile) throws ParsingException {
        if (!supports(fileFormat)) throw new ParsingException("Unsupported file format: " + fileFormat.getDisplayName());

        int entryNumber = 0;
        int estimatedTotalEntries = Math.max(fileBytes.length / 200, 100);

        try (LDIFReader ldifReader = new LDIFReader(new ByteArrayInputStream(fileBytes))) {
            Entry entry;
            while ((entry = ldifReader.readEntry()) != null) {
                entryNumber++;
                updateProgress(parsedFile, entryNumber, estimatedTotalEntries);
                parseEntry(entry, entryNumber, parsedFile);
            }
        } catch (Exception e) {
            throw new ParsingException("LDIF parsing error: " + e.getMessage(), e);
        }
    }
    
    private void updateProgress(ParsedFile parsedFile, int entryNumber, int estimatedTotalEntries) {
        if (entryNumber % 100 == 0 || entryNumber == 1) {
            int percentage = 10 + (int) (((double) entryNumber / estimatedTotalEntries) * 40);
            percentage = Math.min(percentage, 50);
            progressService.sendProgress(ProcessingProgress.parsingInProgress(
                parsedFile.getUploadId().getId(), entryNumber, estimatedTotalEntries,
                "LDIF 엔트리 파싱 중: " + entryNumber + "/" + estimatedTotalEntries, 10, 50));
        }
    }

    private void parseEntry(Entry entry, int entryNumber, ParsedFile parsedFile) {
        Attribute certAttr = entry.getAttribute(ATTR_USER_CERTIFICATE);
        if (certAttr != null) parseCertificateFromBytes(certAttr.getValueByteArray(), entry.getDN(), parsedFile);

        Attribute crlAttr = entry.getAttribute(ATTR_CRL);
        if (crlAttr != null) parseCrlFromBytes(crlAttr.getValueByteArray(), entry.getDN(), parsedFile);

        Attribute mlAttr = entry.getAttribute(ATTR_MASTER_LIST_CONTENT);
        if (mlAttr != null) parseMasterListContent(mlAttr.getValueByteArray(), entry.getDN(), parsedFile);
    }

    private void parseCertificateFromBytes(byte[] certBytes, String dn, ParsedFile parsedFile) {
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(certBytes));
            
            String countryCode = extractCountryCode(cert.getSubjectX500Principal().getName());
            String certType = cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal()) ? "CSCA" : "DSC";
            String fingerprint = calculateFingerprint(cert); // Calculate fingerprint once

            CertificateData certData = CertificateData.of(
                certType,
                countryCode,
                cert.getSubjectX500Principal().getName(),
                cert.getIssuerX500Principal().getName(),
                cert.getSerialNumber().toString(16),
                convertToLocalDateTime(cert.getNotBefore()),
                convertToLocalDateTime(cert.getNotAfter()),
                cert.getEncoded(),
                fingerprint, // Use the calculated fingerprint
                true
            );
            
            // Check for duplicate fingerprint before adding
            if (!certificateExistenceService.existsByFingerprintSha256(fingerprint)) {
                parsedFile.addCertificate(certData);
            } else {
                parsedFile.addError(ParsingError.of("DUPLICATE_CERTIFICATE", fingerprint, "Certificate with this fingerprint already exists globally."));
                log.warn("Duplicate certificate skipped: fingerprint_sha256={}", fingerprint);
            }
        } catch (Exception e) {
            parsedFile.addError(ParsingError.of("CERT_PARSE_ERROR", dn, e.getMessage()));
        }
    }

    private void parseCrlFromBytes(byte[] crlBytes, String dn, ParsedFile parsedFile) {
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            X509CRL crl = (X509CRL) certFactory.generateCRL(new ByteArrayInputStream(crlBytes));

            String countryCode = extractCountryCode(crl.getIssuerX500Principal().getName());

            CrlData crlData = CrlData.of(
                countryCode,
                crl.getIssuerX500Principal().getName(),
                extractCrlNumber(crl),
                convertToLocalDateTime(crl.getThisUpdate()),
                convertToLocalDateTime(crl.getNextUpdate()),
                crl.getEncoded(),
                crl.getRevokedCertificates() != null ? crl.getRevokedCertificates().size() : 0,
                true
            );
            parsedFile.addCrl(crlData);
        } catch (Exception e) {
            parsedFile.addError(ParsingError.of("CRL_PARSE_ERROR", dn, e.getMessage()));
        }
    }

    private void parseMasterListContent(byte[] masterListBytes, String dn, ParsedFile parsedFile) {
        try {
            // Parse CMS-signed Master List binary
            CMSSignedData signedData = new CMSSignedData(new CMSProcessableByteArray(masterListBytes), new ByteArrayInputStream(masterListBytes));
            Store<X509CertificateHolder> certStore = signedData.getCertificates();
            Collection<X509CertificateHolder> certs = certStore.getMatches(null);

            // Extract country code from DN (e.g., "c=FR" from "cn=...,o=ml,c=FR,dc=data,...")
            String countryCode = extractCountryCodeFromMasterListDn(dn);
            if (countryCode == null) {
                log.warn("Could not extract country code from Master List DN: {}", dn);
                parsedFile.addError(ParsingError.of("MASTER_LIST_COUNTRY_ERROR", dn, "Could not extract country code from DN"));
                return;
            }

            // Create and save MasterList aggregate
            com.smartcoreinc.localpkd.fileparsing.domain.model.MasterListId masterListId =
                com.smartcoreinc.localpkd.fileparsing.domain.model.MasterListId.newId();
            com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCode countryCodeVO =
                com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCode.of(countryCode);
            com.smartcoreinc.localpkd.fileparsing.domain.model.CmsBinaryData cmsBinary =
                com.smartcoreinc.localpkd.fileparsing.domain.model.CmsBinaryData.of(masterListBytes);

            com.smartcoreinc.localpkd.fileparsing.domain.model.MasterList masterList =
                com.smartcoreinc.localpkd.fileparsing.domain.model.MasterList.create(
                    masterListId,
                    parsedFile.getUploadId(),
                    countryCodeVO,
                    null,  // version: null for LDIF Master List
                    cmsBinary,
                    null,  // signerInfo: null for LDIF Master List
                    certs.size()
                );

            com.smartcoreinc.localpkd.fileparsing.domain.model.MasterList savedMasterList =
                masterListRepository.save(masterList);

            log.info("Master List saved from LDIF: masterListId={}, country={}, cscaCount={}",
                savedMasterList.getId().getId(), countryCode, certs.size());

            // Extract and save CSCA certificates with masterListId reference
            java.util.List<com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate> cscaCerts =
                new java.util.ArrayList<>();

            for (X509CertificateHolder holder : certs) {
                try {
                    // Parse X509 certificate
                    CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                    X509Certificate x509Cert = (X509Certificate) certFactory.generateCertificate(
                        new ByteArrayInputStream(holder.getEncoded())
                    );

                    // Calculate fingerprint first for duplicate check
                    String fingerprint = calculateFingerprint(x509Cert);

                    // Check for duplicate fingerprint BEFORE creating Certificate entity
                    if (certificateExistenceService.existsByFingerprintSha256(fingerprint)) {
                        parsedFile.addError(ParsingError.of("DUPLICATE_CERTIFICATE", fingerprint,
                            "CSCA with this fingerprint already exists globally."));
                        log.warn("Duplicate CSCA skipped from Master List: fingerprint={}", fingerprint);
                        continue; // Skip this certificate entirely
                    }

                    // Create Certificate entity from Master List CSCA (only if not duplicate)
                    com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate cert =
                        createCertificateFromMasterListCsca(
                            parsedFile.getUploadId().getId(),
                            savedMasterList.getId().getId(),
                            x509Cert
                        );

                    cscaCerts.add(cert);

                    // IMPORTANT: Also add CSCA to ParsedFile for validation
                    // Convert X509Certificate to CertificateData and add to ParsedFile
                    CertificateData certData = CertificateData.of(
                        "CSCA",  // Certificate type
                        countryCode,  // Country code from Master List
                        x509Cert.getSubjectX500Principal().getName(),
                        x509Cert.getIssuerX500Principal().getName(),
                        x509Cert.getSerialNumber().toString(16).toUpperCase(),
                        convertToLocalDateTime(x509Cert.getNotBefore()),
                        convertToLocalDateTime(x509Cert.getNotAfter()),
                        x509Cert.getEncoded(),
                        fingerprint,
                        true  // fromLdif
                    );

                    // Add to ParsedFile (duplicate already checked above)
                    parsedFile.addCertificate(certData);
                    log.debug("Added CSCA from Master List to ParsedFile: fingerprint={}", fingerprint);

                } catch (Exception e) {
                    log.warn("Failed to parse CSCA from Master List: {}", e.getMessage());
                    // Continue with other certificates
                }
            }

            // Save all CSCA certificates from this Master List
            if (cscaCerts.isEmpty()) {
                log.warn("No CSCA certificates successfully parsed from Master List: masterListId={}, country={}",
                    savedMasterList.getId().getId(), countryCode);
            } else {
                java.util.List<com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate> savedCerts =
                    certificateRepository.saveAll(cscaCerts);

                log.info("Saved {} CSCA certificates from LDIF Master List", savedCerts.size());
            }

        } catch (Exception e) {
            log.error("Failed to parse Master List content: {}", e.getMessage(), e);
            parsedFile.addError(ParsingError.of("MASTER_LIST_PARSE_ERROR", dn, e.getMessage()));
        }
    }

    /**
     * Extract country code from Master List DN
     * Example: "cn=CN\=CSCA-FRANCE\,O\=Gouv\,C\=FR,o=ml,c=FR,dc=data,dc=download,dc=pkd,dc=icao,dc=int"
     * Extracts: "FR"
     */
    private String extractCountryCodeFromMasterListDn(String dn) {
        if (dn == null) return null;
        // Look for "c=XX" pattern (country code in LDAP DN)
        Matcher matcher = Pattern.compile(",\\s*c=([A-Z]{2,3})", Pattern.CASE_INSENSITIVE).matcher(dn);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return null;
    }

    /**
     * Create Certificate entity from Master List CSCA
     */
    private com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate createCertificateFromMasterListCsca(
            java.util.UUID uploadId,
            java.util.UUID masterListId,
            X509Certificate x509Cert
    ) throws Exception {
        // Extract certificate data
        com.smartcoreinc.localpkd.certificatevalidation.domain.model.X509Data x509Data =
            com.smartcoreinc.localpkd.certificatevalidation.domain.model.X509Data.of(
                x509Cert.getEncoded(),
                x509Cert.getPublicKey(),
                x509Cert.getSerialNumber().toString(16).toUpperCase(),
                calculateFingerprint(x509Cert)
            );

        String subjectDn = x509Cert.getSubjectX500Principal().getName();
        com.smartcoreinc.localpkd.certificatevalidation.domain.model.SubjectInfo subjectInfo =
            com.smartcoreinc.localpkd.certificatevalidation.domain.model.SubjectInfo.of(
                subjectDn,
                extractDnComponent(subjectDn, "C"),
                extractDnComponent(subjectDn, "O"),
                extractDnComponent(subjectDn, "OU"),
                extractDnComponent(subjectDn, "CN")
            );

        String issuerDn = x509Cert.getIssuerX500Principal().getName();
        boolean isCA = x509Cert.getBasicConstraints() != -1;
        com.smartcoreinc.localpkd.certificatevalidation.domain.model.IssuerInfo issuerInfo =
            com.smartcoreinc.localpkd.certificatevalidation.domain.model.IssuerInfo.of(
                issuerDn,
                extractDnComponent(issuerDn, "C"),
                extractDnComponent(issuerDn, "O"),
                extractDnComponent(issuerDn, "OU"),
                extractDnComponent(issuerDn, "CN"),
                isCA
            );

        com.smartcoreinc.localpkd.certificatevalidation.domain.model.ValidityPeriod validity =
            com.smartcoreinc.localpkd.certificatevalidation.domain.model.ValidityPeriod.of(
                x509Cert.getNotBefore().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(),
                x509Cert.getNotAfter().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            );

        return com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate.createFromMasterList(
            uploadId,
            masterListId,  // Link to MasterList entity
            x509Data,
            subjectInfo,
            issuerInfo,
            validity,
            x509Cert.getSigAlgName()
        );
    }

    private String extractDnComponent(String dn, String component) {
        if (dn == null || dn.isEmpty()) return null;
        Pattern pattern = Pattern.compile(
            "(?:^|,)\\s*" + Pattern.quote(component) + "\\s*=\\s*([^,]+)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(dn);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String extractCountryCode(String dn) {
        if (dn == null) return null;
        Matcher matcher = Pattern.compile("(?:^|,)\\s*C=([A-Z]{2})").matcher(dn);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String calculateFingerprint(X509Certificate cert) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(cert.getEncoded());
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
    
    private LocalDateTime convertToLocalDateTime(Date date) {
        return (date == null) ? null : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private String extractCrlNumber(X509CRL crl) {
        byte[] extValue = crl.getExtensionValue(Extension.cRLNumber.getId());
        if (extValue == null) return null;
        try {
            ASN1OctetString octetString = ASN1OctetString.getInstance(extValue);
            ASN1Integer crlNumber = ASN1Integer.getInstance(octetString.getOctets());
            return crlNumber.getValue().toString();
        } catch (Exception e) {
            log.warn("Could not extract CRL number", e);
            return null;
        }
    }
}
