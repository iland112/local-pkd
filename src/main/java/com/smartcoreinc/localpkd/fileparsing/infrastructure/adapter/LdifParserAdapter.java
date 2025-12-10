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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
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

        log.info("Starting LDIF parsing with batch duplicate check optimization");

        // ✅ Step 1: 모든 엔트리를 먼저 읽고 fingerprint 수집
        List<Entry> allEntries = new ArrayList<>();
        Set<String> allFingerprints = new HashSet<>();

        try (LDIFReader ldifReader = new LDIFReader(new ByteArrayInputStream(fileBytes))) {
            Entry entry;
            while ((entry = ldifReader.readEntry()) != null) {
                allEntries.add(entry);

                // 인증서 엔트리면 fingerprint 계산
                if (entry.hasAttribute(ATTR_USER_CERTIFICATE)) {
                    byte[] certBytes = entry.getAttribute(ATTR_USER_CERTIFICATE).getValueByteArray();
                    try {
                        String fingerprint = null;

                        // Try standard X509Certificate parsing first
                        try {
                            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(
                                new ByteArrayInputStream(certBytes)
                            );
                            fingerprint = calculateFingerprint(cert);
                        } catch (Exception e) {
                            // Check if this is an EC Parameter error
                            if (e.getMessage() != null && e.getMessage().contains("ECParameters")) {
                                log.debug("Certificate uses explicit EC parameters, using fallback fingerprint calculation: entry={}",
                                    entry.getDN());
                                // Fallback: Calculate fingerprint directly from bytes
                                fingerprint = calculateFingerprintFromBytes(certBytes);
                            } else {
                                // Other error, rethrow
                                throw e;
                            }
                        }

                        if (fingerprint != null) {
                            allFingerprints.add(fingerprint);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to calculate fingerprint for entry: {}", entry.getDN(), e);
                    }
                }
            }
        } catch (Exception e) {
            throw new ParsingException("LDIF reading error: " + e.getMessage(), e);
        }

        log.info("LDIF entries loaded: {} total, {} certificates with fingerprints",
            allEntries.size(), allFingerprints.size());

        // ✅ Step 2: 일괄 중복 체크 (단일 쿼리)
        Set<String> existingFingerprints = certificateExistenceService.findExistingFingerprints(allFingerprints);
        log.info("Batch duplicate check completed: {} existing out of {} total fingerprints",
            existingFingerprints.size(), allFingerprints.size());

        // ✅ Step 3: 엔트리 파싱 (중복 체크는 메모리 Set으로 수행)
        int entryNumber = 0;
        int estimatedTotalEntries = allEntries.size();

        for (Entry entry : allEntries) {
            entryNumber++;
            updateProgress(parsedFile, entryNumber, estimatedTotalEntries);
            parseEntryWithCache(entry, entryNumber, parsedFile, existingFingerprints);
        }

        log.info("LDIF parsing completed: {} entries processed, {} new certificates added",
            allEntries.size(), parsedFile.getCertificates().size());
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

    /**
     * ✅ 캐시 기반 엔트리 파싱 (배치 중복 체크 최적화)
     */
    private void parseEntryWithCache(Entry entry, int entryNumber, ParsedFile parsedFile, Set<String> existingFingerprints) {
        // Debug: Log all entry DNs that contain "crl" to diagnose CRL parsing issue
        if (entry.getDN() != null && entry.getDN().toLowerCase().contains("crl")) {
            log.debug("Found CRL-related entry: dn={}, attributes={}", entry.getDN(),
                java.util.Arrays.toString(entry.getAttributes().toArray()));
            log.debug("Has ATTR_CRL ({}): {}", ATTR_CRL, entry.hasAttribute(ATTR_CRL));
        }

        if (entry.hasAttribute(ATTR_USER_CERTIFICATE)) {
            parseCertificateFromEntryWithCache(entry, parsedFile, existingFingerprints);
        } else if (entry.hasAttribute(ATTR_CRL)) {
            log.debug("CRL entry found: dn={}", entry.getDN());
            parseCrlFromBytes(entry.getAttribute(ATTR_CRL).getValueByteArray(), entry.getDN(), parsedFile);
        } else if (entry.hasAttribute(ATTR_MASTER_LIST_CONTENT)) {
            parseMasterListContent(entry.getAttribute(ATTR_MASTER_LIST_CONTENT).getValueByteArray(), entry.getDN(), parsedFile);
        } else {
            // Log entries that don't match any known type
            if (entry.getDN() != null && !entry.getDN().contains("dc=data") && !entry.getDN().contains("o=crl") && !entry.getDN().contains("c=")) {
                log.trace("Skipping entry (no recognized attributes): dn={}", entry.getDN());
            }
        }
    }

    /**
     * @deprecated Use {@link #parseEntryWithCache(Entry, int, ParsedFile, Set)} for better performance
     */
    @Deprecated
    private void parseEntry(Entry entry, int entryNumber, ParsedFile parsedFile) {
        if (entry.hasAttribute(ATTR_USER_CERTIFICATE)) {
            parseCertificateFromEntry(entry, parsedFile);
        } else if (entry.hasAttribute(ATTR_CRL)) {
            parseCrlFromBytes(entry.getAttribute(ATTR_CRL).getValueByteArray(), entry.getDN(), parsedFile);
        } else if (entry.hasAttribute(ATTR_MASTER_LIST_CONTENT)) {
            parseMasterListContent(entry.getAttribute(ATTR_MASTER_LIST_CONTENT).getValueByteArray(), entry.getDN(), parsedFile);
        }
    }

    /**
     * ✅ 캐시 기반 인증서 파싱 (배치 중복 체크 최적화)
     *
     * <p>메모리 Set으로 중복 체크하여 DB 조회 없음 (N+1 문제 해결)</p>
     */
    private void parseCertificateFromEntryWithCache(Entry entry, ParsedFile parsedFile, Set<String> existingFingerprints) {
        byte[] certBytes = entry.getAttribute(ATTR_USER_CERTIFICATE).getValueByteArray();
        String dn = entry.getDN();
        try {
            X509Certificate cert = null;
            X509CertificateHolder holder = null;
            boolean usesFallbackParsing = false;

            // Try standard X509Certificate parsing first
            try {
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                cert = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(certBytes));
            } catch (Exception e) {
                // Check if this is an EC Parameter error
                if (e.getMessage() != null && e.getMessage().contains("ECParameters")) {
                    log.warn("Certificate uses explicit EC parameters, using fallback parsing: dn={}", dn);
                    usesFallbackParsing = true;
                    // Parse using X509CertificateHolder instead
                    holder = new X509CertificateHolder(certBytes);
                } else {
                    // Other error, rethrow
                    throw e;
                }
            }

            String subjectDn = usesFallbackParsing ? holder.getSubject().toString() : cert.getSubjectX500Principal().getName();
            String issuerDn = usesFallbackParsing ? holder.getIssuer().toString() : cert.getIssuerX500Principal().getName();
            String countryCode = extractCountryCode(subjectDn);

            boolean selfSigned = subjectDn.equals(issuerDn);
            boolean isNonConformantPath = dn != null && dn.toLowerCase().contains("dc=nc-data");

            String certType;
            if (selfSigned) {
                certType = "CSCA";
            } else if (isNonConformantPath) {
                certType = "DSC_NC";
                log.debug("Detected DSC_NC certificate from nc-data path: dn={}", dn);
            } else {
                certType = "DSC";
            }

            String fingerprint = usesFallbackParsing ? calculateFingerprintFromBytes(certBytes) : calculateFingerprint(cert);

            Map<String, List<String>> allAttributes = new HashMap<>();
            for (Attribute attr : entry.getAttributes()) {
                String name = attr.getName();
                List<String> values = new ArrayList<>();
                if (attr.hasValue()) {
                    if (name.endsWith(";binary")) {
                         for (byte[] val : attr.getValueByteArrays()) {
                            values.add(Base64.getEncoder().encodeToString(val));
                        }
                    } else {
                        values.addAll(Arrays.asList(attr.getValues()));
                    }
                }
                allAttributes.put(name, values);
            }

            // ✅ 메모리 Set으로 중복 체크 (DB 조회 없음)
            if (!existingFingerprints.contains(fingerprint)) {
                CertificateData certData;
                if (usesFallbackParsing) {
                    // Fallback: Extract data from X509CertificateHolder
                    certData = CertificateData.of(
                        certType,
                        countryCode,
                        subjectDn,
                        issuerDn,
                        holder.getSerialNumber().toString(16).toUpperCase(),
                        convertToLocalDateTime(holder.getNotBefore()),
                        convertToLocalDateTime(holder.getNotAfter()),
                        holder.getEncoded(),
                        fingerprint,
                        true,
                        allAttributes
                    );
                    log.info("Successfully parsed DSC/CSCA with explicit EC parameters using fallback: fingerprint={}, type={}",
                        fingerprint.substring(0, 16) + "...", certType);
                } else {
                    // Standard parsing
                    certData = CertificateData.of(
                        certType,
                        countryCode,
                        subjectDn,
                        issuerDn,
                        cert.getSerialNumber().toString(16).toUpperCase(),
                        convertToLocalDateTime(cert.getNotBefore()),
                        convertToLocalDateTime(cert.getNotAfter()),
                        cert.getEncoded(),
                        fingerprint,
                        true,
                        allAttributes
                    );
                }
                parsedFile.addCertificate(certData);
            } else {
                parsedFile.addError(ParsingError.of("DUPLICATE_CERTIFICATE", fingerprint, "Certificate with this fingerprint already exists globally."));
                log.debug("Duplicate certificate skipped: fingerprint_sha256={}", fingerprint);
            }
        } catch (Exception e) {
            parsedFile.addError(ParsingError.of("CERT_PARSE_ERROR", dn, e.getMessage()));
            log.error("Failed to parse certificate entry: dn={}, error={}", dn, e.getMessage(), e);
        }
    }

    /**
     * LDIF 엔트리에서 X.509 인증서를 파싱하여 ParsedFile에 추가합니다.
     *
     * <p>
     *  - CSCA: self-signed (subject == issuer)<br>
     *  - DSC:  subject != issuer, 일반 데이터 경로 (dc=data)<br>
     *  - DSC_NC: subject != issuer 이고 DN 경로에 {@code dc=nc-data} 가 포함된 경우
     * </p>
     *
     * <p>
     * NC-DATA(DSC_NC)는 이후 검증 단계에서 별도의 유효성 검사를 수행하지 않고
     * 통계/분석 및 LDAP 저장 용도로만 사용됩니다.
     * </p>
     *
     * @deprecated Use {@link #parseCertificateFromEntryWithCache(Entry, ParsedFile, Set)} for better performance
     */
    @Deprecated
    private void parseCertificateFromEntry(Entry entry, ParsedFile parsedFile) {
        byte[] certBytes = entry.getAttribute(ATTR_USER_CERTIFICATE).getValueByteArray();
        String dn = entry.getDN();
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(certBytes));

            String subjectDn = cert.getSubjectX500Principal().getName();
            String issuerDn = cert.getIssuerX500Principal().getName();
            String countryCode = extractCountryCode(subjectDn);

            boolean selfSigned = subjectDn.equals(issuerDn);
            boolean isNonConformantPath = dn != null && dn.toLowerCase().contains("dc=nc-data");

            String certType;
            if (selfSigned) {
                certType = "CSCA";
            } else if (isNonConformantPath) {
                certType = "DSC_NC";
                log.debug("Detected DSC_NC certificate from nc-data path: dn={}", dn);
            } else {
                certType = "DSC";
            }

            String fingerprint = calculateFingerprint(cert);

            Map<String, List<String>> allAttributes = new HashMap<>();
            for (Attribute attr : entry.getAttributes()) {
                String name = attr.getName();
                List<String> values = new ArrayList<>();
                if (attr.hasValue()) {
                    if (name.endsWith(";binary")) {
                         for (byte[] val : attr.getValueByteArrays()) {
                            values.add(Base64.getEncoder().encodeToString(val));
                        }
                    } else {
                        values.addAll(Arrays.asList(attr.getValues()));
                    }
                }
                allAttributes.put(name, values);
            }

            CertificateData certData = CertificateData.of(
                certType,
                countryCode,
                subjectDn,
                issuerDn,
                cert.getSerialNumber().toString(16),
                convertToLocalDateTime(cert.getNotBefore()),
                convertToLocalDateTime(cert.getNotAfter()),
                cert.getEncoded(),
                fingerprint,
                true,
                allAttributes
            );

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
            log.debug("CRL parsed successfully: country={}, issuer={}, revokedCount={}",
                countryCode, crl.getIssuerX500Principal().getName(),
                crl.getRevokedCertificates() != null ? crl.getRevokedCertificates().size() : 0);
        } catch (Exception e) {
            parsedFile.addError(ParsingError.of("CRL_PARSE_ERROR", dn, e.getMessage()));
            log.warn("CRL parse error: dn={}, error={}", dn, e.getMessage());
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
                    X509Certificate x509Cert = null;
                    boolean usesFallbackParsing = false;

                    // Try to parse X509 certificate using standard CertificateFactory
                    try {
                        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                        x509Cert = (X509Certificate) certFactory.generateCertificate(
                            new ByteArrayInputStream(holder.getEncoded())
                        );
                    } catch (Exception e) {
                        // Check if this is an EC Parameter error
                        if (e.getMessage() != null && e.getMessage().contains("ECParameters")) {
                            log.warn("Certificate uses explicit EC parameters, using fallback parsing: subject={}",
                                holder.getSubject().toString());
                            usesFallbackParsing = true;
                            // Continue with fallback - we'll extract data from holder directly
                        } else {
                            // Other parsing error, rethrow
                            throw e;
                        }
                    }

                    // CRITICAL: Filter out Master List Signer certificates
                    // Master List Signer certificates have basicConstraints = -1 (not a CA)
                    // Only CA certificates (basicConstraints >= 0) are CSCA
                    int basicConstraints;
                    if (usesFallbackParsing) {
                        // Extract basicConstraints from X509CertificateHolder
                        basicConstraints = extractBasicConstraintsFromHolder(holder);
                    } else {
                        basicConstraints = x509Cert.getBasicConstraints();
                    }

                    if (basicConstraints == -1) {
                        String subjectDn = usesFallbackParsing ?
                            holder.getSubject().toString() :
                            x509Cert.getSubjectX500Principal().getName();
                        log.debug("Skipping Master List Signer certificate (not a CA): subject={}", subjectDn);
                        continue; // Skip Master List Signer certificates
                    }

                    // Calculate fingerprint first for duplicate check
                    String fingerprint = usesFallbackParsing ?
                        calculateFingerprintFromBytes(holder.getEncoded()) :
                        calculateFingerprint(x509Cert);

                    // Check for duplicate fingerprint for Certificate entity saving
                    boolean isDuplicate = certificateExistenceService.existsByFingerprintSha256(fingerprint);

                    if (!isDuplicate) {
                        // Create Certificate entity from Master List CSCA (only if not duplicate in DB)
                        com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate cert;
                        if (usesFallbackParsing) {
                            cert = createCertificateFromMasterListCscaFallback(
                                parsedFile.getUploadId().getId(),
                                savedMasterList.getId().getId(),
                                holder,
                                fingerprint
                            );
                        } else {
                            cert = createCertificateFromMasterListCsca(
                                parsedFile.getUploadId().getId(),
                                savedMasterList.getId().getId(),
                                x509Cert
                            );
                        }
                        cscaCerts.add(cert);
                    } else {
                        log.debug("CSCA already exists in database, skipping Certificate entity save: fingerprint={}", fingerprint);
                    }

                    // IMPORTANT: Always add CSCA to ParsedFile for validation, even if duplicate
                    // This allows validation to proceed with existing certificates
                    CertificateData certData;
                    if (usesFallbackParsing) {
                        certData = createCertificateDataFromHolder(holder, countryCode, fingerprint);
                    } else {
                        certData = CertificateData.of(
                            "CSCA",  // Certificate type
                            countryCode,  // Country code from Master List
                            x509Cert.getSubjectX500Principal().getName(),
                            x509Cert.getIssuerX500Principal().getName(),
                            x509Cert.getSerialNumber().toString(16).toUpperCase(),
                            convertToLocalDateTime(x509Cert.getNotBefore()),
                            convertToLocalDateTime(x509Cert.getNotAfter()),
                            x509Cert.getEncoded(),
                            fingerprint,
                            true,  // fromLdif
                            null // All attributes not available here
                        );
                    }

                    // Add to ParsedFile regardless of duplication (needed for validation)
                    parsedFile.addCertificate(certData);
                    log.debug("Added CSCA from Master List to ParsedFile: fingerprint={}, duplicate={}, fallbackParsing={}",
                        fingerprint, isDuplicate, usesFallbackParsing);

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
                // ❌ REMOVED: Parsing phase should NOT save Certificate entities (DDD architecture violation)
                // Certificate entities should only be created and saved by ValidateCertificatesUseCase
                // java.util.List<com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate> savedCerts =
                //     certificateRepository.saveAll(cscaCerts);
                // log.info("Saved {} CSCA certificates from LDIF Master List", savedCerts.size());
                log.info("Parsed {} CSCA certificates from LDIF Master List (will be saved during validation phase)", cscaCerts.size());
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

    // ===========================
    // Fallback Parsing Helpers for EC Parameter Issue
    // ===========================

    /**
     * Extract basicConstraints extension from X509CertificateHolder
     * Used when X509Certificate conversion fails due to EC parameter issues
     */
    private int extractBasicConstraintsFromHolder(X509CertificateHolder holder) {
        try {
            org.bouncycastle.asn1.x509.Extension ext = holder.getExtension(Extension.basicConstraints);
            if (ext == null) {
                return -1; // Not a CA
            }
            org.bouncycastle.asn1.x509.BasicConstraints bc = org.bouncycastle.asn1.x509.BasicConstraints.getInstance(ext.getParsedValue());
            if (!bc.isCA()) {
                return -1;
            }
            if (bc.getPathLenConstraint() == null) {
                return Integer.MAX_VALUE;
            }
            return bc.getPathLenConstraint().intValue();
        } catch (Exception e) {
            log.warn("Failed to extract basicConstraints from holder, assuming -1 (not a CA): {}", e.getMessage());
            return -1;
        }
    }

    /**
     * Calculate SHA-256 fingerprint from raw certificate bytes
     */
    private String calculateFingerprintFromBytes(byte[] certBytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(certBytes);
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Create CertificateData from X509CertificateHolder (fallback parsing)
     * Used when X509Certificate conversion fails due to EC parameter issues
     */
    private CertificateData createCertificateDataFromHolder(
        X509CertificateHolder holder,
        String countryCode,
        String fingerprint
    ) throws Exception {
        String subjectDn = holder.getSubject().toString();
        String issuerDn = holder.getIssuer().toString();
        String serialNumber = holder.getSerialNumber().toString(16).toUpperCase();

        // Extract validity dates
        java.util.Date notBefore = holder.getNotBefore();
        java.util.Date notAfter = holder.getNotAfter();
        LocalDateTime notBeforeLdt = convertToLocalDateTime(notBefore);
        LocalDateTime notAfterLdt = convertToLocalDateTime(notAfter);

        return CertificateData.of(
            "CSCA",  // Certificate type
            countryCode,
            subjectDn,
            issuerDn,
            serialNumber,
            notBeforeLdt,
            notAfterLdt,
            holder.getEncoded(),
            fingerprint,
            true,  // fromLdif
            null // All attributes not available here
        );
    }

    /**
     * Create Certificate entity from X509CertificateHolder (fallback parsing)
     * Used when X509Certificate conversion fails due to EC parameter issues
     */
    private com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate createCertificateFromMasterListCscaFallback(
        java.util.UUID uploadId,
        java.util.UUID masterListId,
        X509CertificateHolder holder,
        String fingerprint
    ) throws Exception {
        String subjectDn = holder.getSubject().toString();
        String issuerDn = holder.getIssuer().toString();
        String serialNumber = holder.getSerialNumber().toString(16).toUpperCase();

        // Extract country code from DN
        String countryCode = extractCountryCode(subjectDn);

        // Create X509Data (note: publicKey will be null in fallback mode)
        com.smartcoreinc.localpkd.certificatevalidation.domain.model.X509Data x509Data =
            com.smartcoreinc.localpkd.certificatevalidation.domain.model.X509Data.of(
                holder.getEncoded(),
                null,  // PublicKey not available in fallback mode
                serialNumber,
                fingerprint
            );

        // Create SubjectInfo (fallback mode - extract from X509CertificateHolder)
        com.smartcoreinc.localpkd.certificatevalidation.domain.model.SubjectInfo subjectInfo =
            com.smartcoreinc.localpkd.certificatevalidation.domain.model.SubjectInfo.of(
                subjectDn,
                extractDnComponent(subjectDn, "C"),
                extractDnComponent(subjectDn, "O"),
                extractDnComponent(subjectDn, "OU"),
                extractDnComponent(subjectDn, "CN")
            );

        // Create IssuerInfo (fallback mode)
        // Note: isCA cannot be reliably determined in fallback mode without basicConstraints
        // We assume true since this is a CSCA certificate
        boolean isCA = true;  // Assume CA for CSCA certificates
        com.smartcoreinc.localpkd.certificatevalidation.domain.model.IssuerInfo issuerInfo =
            com.smartcoreinc.localpkd.certificatevalidation.domain.model.IssuerInfo.of(
                issuerDn,
                extractDnComponent(issuerDn, "C"),
                extractDnComponent(issuerDn, "O"),
                extractDnComponent(issuerDn, "OU"),
                extractDnComponent(issuerDn, "CN"),
                isCA
            );

        // Create ValidityPeriod
        com.smartcoreinc.localpkd.certificatevalidation.domain.model.ValidityPeriod validity =
            com.smartcoreinc.localpkd.certificatevalidation.domain.model.ValidityPeriod.of(
                convertToLocalDateTime(holder.getNotBefore()),
                convertToLocalDateTime(holder.getNotAfter())
            );

        // Extract signature algorithm
        String signatureAlgorithm = holder.getSignatureAlgorithm().getAlgorithm().getId();

        return com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate.createFromMasterList(
            uploadId,
            masterListId,
            x509Data,
            subjectInfo,
            issuerInfo,
            validity,
            signatureAlgorithm
        );
    }
}
