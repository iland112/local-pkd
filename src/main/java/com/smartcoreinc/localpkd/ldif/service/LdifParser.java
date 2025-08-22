package com.smartcoreinc.localpkd.ldif.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisResult;
import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisSummary;
import com.smartcoreinc.localpkd.ldif.dto.LdifEntryDto;
import com.smartcoreinc.localpkd.sse.Progress;
import com.smartcoreinc.localpkd.sse.ProgressEvent;
import com.smartcoreinc.localpkd.sse.ProgressPublisher;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldif.LDIFException;
import com.unboundid.ldif.LDIFReader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LdifParser {

    private final ProgressPublisher progressPublisher;
    private final CertificateVerifier certificateVerifier;
    private final BinaryAttributeProcessor binaryAttributeProcessor;

    private static final List<String> BINARY_ATTRIBUTES = Arrays.asList(
            "userCertificate", "caCertificate", "crossCertificatePair",
            "certificateRevocationList", "authorityRevocationList", "pkdMasterListContent");

    public LdifParser(ProgressPublisher progressPublisher, CertificateVerifier certificateVerifier) {
        this.progressPublisher = progressPublisher;
        this.certificateVerifier = certificateVerifier;
        this.binaryAttributeProcessor = new BinaryAttributeProcessor(certificateVerifier);
    }

    /**
     * LDIF 파일 파싱 (메인 진입점)
     */
    public LdifAnalysisResult parseLdifFile(MultipartFile file) throws IOException {
        log.info("Starting line-tracking LDIF parsing for file: {}", file.getOriginalFilename());

        ParsingContext context = new ParsingContext();
        int totalLines = countLines(file);

        try (InputStream inputStream = file.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            
            processLdifFile(bufferedReader, context, totalLines);
        } catch (IOException e) {
            log.error("IO Error reading LDIF file: {}", e.getMessage());
            context.addError("File reading error: " + e.getMessage());
        }

        return buildAnalysisResult(context);
    }

    /**
     * LDIF 파일 처리 (라인별 파싱)
     */
    private void processLdifFile(BufferedReader bufferedReader, ParsingContext context, int totalLines) throws IOException {
        StringBuilder currentRecord = new StringBuilder();
        int processedLines = 0;
        int lineNumber = 0;
        int recordNumber = 0;
        String line;
        boolean inRecord = false;

        while ((line = bufferedReader.readLine()) != null) {
            processedLines++;
            publishProgress(processedLines, totalLines, line);
            lineNumber++;

            if (line.trim().isEmpty()) {
                if (inRecord && currentRecord.length() > 0) {
                    processRecord(currentRecord.toString(), ++recordNumber, lineNumber, context);
                    resetRecord(currentRecord);
                    inRecord = false;
                }
                continue;
            }

            if (line.startsWith("#")) {
                continue;
            }

            if (line.startsWith("dn:") || line.startsWith("dn::")) {
                if (inRecord && currentRecord.length() > 0) {
                    processRecord(currentRecord.toString(), ++recordNumber, lineNumber - 1, context);
                }
                resetRecord(currentRecord);
                currentRecord.append(line).append("\n");
                inRecord = true;
            } else if (inRecord) {
                currentRecord.append(line).append("\n");
            }
        }

        // 마지막 레코드 처리
        if (inRecord && currentRecord.length() > 0) {
            processRecord(currentRecord.toString(), ++recordNumber, lineNumber, context);
        }
    }

    /**
     * 개별 레코드 처리
     */
    private void processRecord(String recordText, int recordNumber, int lineNumber, ParsingContext context) {
        try {
            log.debug("Processing record {}", recordNumber);
            ParseResult parseResult = parseRecord(recordText, recordNumber, lineNumber);
            
            if (parseResult.entry != null) {
                context.addEntry(parseResult.entry);
                context.updateStatistics(parseResult);
            }
        } catch (Exception e) {
            String error = String.format("Record %d (around line %d): %s", recordNumber, lineNumber, e.getMessage());
            context.addError(error);
            log.error("Error parsing record {}: {}", recordNumber, e.getMessage(), e);
        }
    }

    /**
     * 레코드 파싱
     */
    private ParseResult parseRecord(String recordText, int recordNumber, int lineNumber) throws LDIFException, IOException {
        // 커스텀 파싱으로 바이너리 속성 처리
        BinaryAttributeProcessor.ProcessResult binaryResult = 
            binaryAttributeProcessor.processBinaryAttributes(recordText, recordNumber);

        // UnboundID LDIF 파서로 엔트리 파싱
        try (StringReader stringReader = new StringReader(recordText);
                LDIFReader ldifReader = new LDIFReader(new BufferedReader(stringReader))) {
            
            Entry entry = ldifReader.readEntry();
            log.debug("Parsed entry DN: {}", entry.getDN());
            
            if (entry != null) {
                LdifEntryDto entryDto = convertToEntryDto(entry, "ADD", binaryResult.getBinaryAttributes());
                return new ParseResult(entryDto, binaryResult);
            } else {
                return new ParseResult(null, binaryResult);
            }
        } catch (LDIFException e) {
            throw new LDIFException(
                    "LDIF parsing error in record " + recordNumber + ": " + e.getMessage(),
                    lineNumber, true, e);
        }
    }

    /**
     * Entry를 DTO로 변환
     */
    private LdifEntryDto convertToEntryDto(Entry entry, String entryType, Map<String, Object> customParsedAttributes) {
        Map<String, List<String>> attributes = new HashMap<>();

        for (Attribute attribute : entry.getAttributes()) {
            String attributeName = attribute.getName();

            if (isBinaryAttribute(attributeName)) {
                // 바이너리 속성은 커스텀 파싱 결과 사용
                @SuppressWarnings("unchecked")
                List<byte[]> binaryValues = (List<byte[]>) customParsedAttributes.get(attributeName);
                if (binaryValues != null) {
                    List<String> base64Values = new ArrayList<>();
                    for (byte[] binaryValue : binaryValues) {
                        base64Values.add(Base64.getEncoder().encodeToString(binaryValue));
                    }
                    attributes.put(attributeName, base64Values);
                    log.debug("Processed binary attribute '{}' with {} values", attributeName, base64Values.size());
                }
            } else {
                // 일반 속성 처리
                String[] values = attribute.getValues();
                attributes.put(attributeName, Arrays.asList(values));
            }
        }

        return new LdifEntryDto(entry.getDN(), entryType, attributes, entry.toLDIFString());
    }

    /**
     * 분석 결과 생성
     */
    private LdifAnalysisResult buildAnalysisResult(ParsingContext context) {
        LdifAnalysisSummary summary = new LdifAnalysisSummary();
        summary.setErrors(context.getErrors());
        summary.setWarnings(context.getWarnings());
        summary.setTotalEntries(context.getEntries().size());
        summary.setAddEntries(context.getAddCount());
        summary.setModifyEntries(0); // LDIF add entries only for now
        summary.setDeleteEntries(0);
        summary.setObjectClassCount(context.getObjectClassCount());
        summary.setCertificateValidationStats(context.buildCertificateStats());
        summary.setHasValidationErrors(!context.getErrors().isEmpty());

        LdifAnalysisResult result = new LdifAnalysisResult();
        result.setSummary(summary);
        result.setEntries(context.getEntries());

        logParsingResults(context);
        return result;
    }

    /**
     * 파싱 결과 로깅
     */
    private void logParsingResults(ParsingContext context) {
        log.info("LDIF parsing completed. Entries: {}, Errors: {}, Warnings: {}, " +
                "Certificates: {} (Valid: {}, Invalid: {}), MasterLists: {} (Valid: {}, Invalid: {}), Trust Anchors: {}",
                context.getEntries().size(), context.getErrors().size(), context.getWarnings().size(),
                context.getTotalCertificates(), context.getValidCertificates(), context.getInvalidCertificates(),
                context.getTotalMasterLists(), context.getValidMasterLists(), context.getInvalidMasterLists(),
                certificateVerifier.getTrustAnchors().size());
    }

    /**
     * 진행상태 발행
     */
    private void publishProgress(int processedLines, int totalLines, String currentLine) {
        Progress progress = new Progress(processedLines / (double) totalLines, "LDIF");
        ProgressEvent progressEvent = new ProgressEvent(progress, processedLines, totalLines, currentLine);
        progressPublisher.notifyProgressListeners(progressEvent);
    }

    /**
     * 현재 레코드 리셋
     */
    private void resetRecord(StringBuilder currentRecord) {
        currentRecord.setLength(0);
    }

    /**
     * 바이너리 속성 여부 확인
     */
    private boolean isBinaryAttribute(String attributeName) {
        return BINARY_ATTRIBUTES.stream()
                .anyMatch(binaryAttr -> attributeName.toLowerCase().contains(binaryAttr.toLowerCase()));
    }

    /**
     * objectClass 카운트
     */
    private void countObjectClasses(LdifEntryDto entry, Map<String, Integer> objectClassCount) {
        List<String> objectClasses = entry.getAttributes().get("objectClass");
        if (objectClasses != null) {
            for (String objectClass : objectClasses) {
                objectClassCount.merge(objectClass, 1, Integer::sum);
            }
        }
    }

    /**
     * 파일 라인 수 계산
     */
    private int countLines(MultipartFile file) throws IOException {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            while (reader.readLine() != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * DN에서 국가 코드 추출
     */
    private String extractCountryCodeFromDN(String dn) {
        if (dn == null) return "UNKNOWN";
        
        String[] parts = dn.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.toLowerCase().startsWith("c=")) {
                return part.substring(2).toUpperCase();
            }
        }
        return "UNKNOWN";
    }

    /**
     * LDIF 검증 (라인 번호 포함)
     */
    public boolean validateLdifWithLineNumbers(String content) {
        try {
            String[] lines = content.split("\n");
            int lineNumber = 0;
            boolean hasValidEntry = false;

            for (String line : lines) {
                lineNumber++;
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                if (line.startsWith("dn:") || line.startsWith("dn::")) {
                    hasValidEntry = true;
                    String dn = line.substring(line.indexOf(':') + 1).trim();
                    if (dn.isEmpty()) {
                        log.warn("Empty DN at line {}", lineNumber);
                        return false;
                    }
                } else if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length != 2 || parts[0].trim().isEmpty()) {
                        log.warn("Invalid attribute format at line {}: {}", lineNumber, line);
                        return false;
                    }
                } else {
                    log.warn("Invalid LDIF format at line {}: {}", lineNumber, line);
                    return false;
                }
            }

            return hasValidEntry;
        } catch (Exception e) {
            log.warn("LDIF validation failed: {}", e.getMessage());
            return false;
        }
    }

    // Delegate methods to CertificateVerifier
    public CertificateChainValidationResult validateCertificateChain(java.security.cert.X509Certificate certificate, String issuerCountry) {
        return certificateVerifier.validateCertificateChain(certificate, issuerCountry);
    }

    public Map<String, TrustAnchorInfo> getTrustAnchorsSummary() {
        return certificateVerifier.getTrustAnchorsSummary();
    }

    // 내부 클래스들
    private static class ParseResult {
        final LdifEntryDto entry;
        final BinaryAttributeProcessor.ProcessResult binaryResult;

        ParseResult(LdifEntryDto entry, BinaryAttributeProcessor.ProcessResult binaryResult) {
            this.entry = entry;
            this.binaryResult = binaryResult;
        }
    }

    /**
     * 파싱 컨텍스트 - 파싱 상태 관리
     */
    private static class ParsingContext {
        private final List<LdifEntryDto> entries = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final Map<String, Integer> objectClassCount = new HashMap<>();

        private int addCount = 0;
        private int totalCertificates = 0;
        private int validCertificates = 0;
        private int invalidCertificates = 0;
        private int totalMasterLists = 0;
        private int validMasterLists = 0;
        private int invalidMasterLists = 0;

        public void addEntry(LdifEntryDto entry) {
            entries.add(entry);
            addCount++;
            countObjectClasses(entry);
        }

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarnings(List<String> warnings) {
            this.warnings.addAll(warnings);
        }

        public void updateStatistics(ParseResult parseResult) {
            BinaryAttributeProcessor.ProcessResult result = parseResult.binaryResult;
            totalCertificates += result.getTotalCertificates();
            validCertificates += result.getValidCertificates();
            invalidCertificates += result.getInvalidCertificates();
            totalMasterLists += result.getTotalMasterLists();
            validMasterLists += result.getValidMasterLists();
            invalidMasterLists += result.getInvalidMasterLists();
            addWarnings(result.getWarnings());
        }

        private void countObjectClasses(LdifEntryDto entry) {
            List<String> objectClasses = entry.getAttributes().get("objectClass");
            if (objectClasses != null) {
                for (String objectClass : objectClasses) {
                    objectClassCount.merge(objectClass, 1, Integer::sum);
                }
            }
        }

        public Map<String, Integer> buildCertificateStats() {
            Map<String, Integer> stats = new HashMap<>();
            if (totalCertificates > 0) {
                stats.put("total", totalCertificates);
                stats.put("valid", validCertificates);
                stats.put("invalid", invalidCertificates);
            }
            if (totalMasterLists > 0) {
                stats.put("totalMasterLists", totalMasterLists);
                stats.put("validMasterLists", validMasterLists);
                stats.put("invalidMasterLists", invalidMasterLists);
            }
            return stats;
        }

        // Getters
        public List<LdifEntryDto> getEntries() { return entries; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public Map<String, Integer> getObjectClassCount() { return objectClassCount; }
        public int getAddCount() { return addCount; }
        public int getTotalCertificates() { return totalCertificates; }
        public int getValidCertificates() { return validCertificates; }
        public int getInvalidCertificates() { return invalidCertificates; }
        public int getTotalMasterLists() { return totalMasterLists; }
        public int getValidMasterLists() { return validMasterLists; }
        public int getInvalidMasterLists() { return invalidMasterLists; }
    }
}
