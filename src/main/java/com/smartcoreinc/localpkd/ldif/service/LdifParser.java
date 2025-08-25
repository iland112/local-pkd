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
            "certificateRevocationList", "authorityRevocationList",
            "pkdMasterListContent", "pkdDscCertificate");

    public LdifParser(ProgressPublisher progressPublisher,
            CertificateVerifier certificateVerifier,
            BinaryAttributeProcessor binaryAttributeProcessor) {
        this.progressPublisher = progressPublisher;
        this.certificateVerifier = certificateVerifier;
        this.binaryAttributeProcessor = binaryAttributeProcessor;
    }

    /**
     * LDIF 파일 파싱 (메인 진입점)
     */
    public LdifAnalysisResult parseLdifFile(MultipartFile file) throws IOException {
        log.info("Starting line-tracking LDIF parsing for file: {}", file.getOriginalFilename());

        ParsingContext context = new ParsingContext();

        // UI의 Progress Bar 표시를 위해 파일의 전체 라인 수를 계산
        int totalLines = countLines(file);
        log.info("파일의 전체 라인 수: {}", totalLines);

        try (InputStream inputStream = file.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            // LDIF file 내용 처리
            processLdifFile(bufferedReader, context, totalLines);
        } catch (IOException e) {
            log.error("IO Error reading LDIF file: {}", e.getMessage());
            context.addError("File reading error: " + e.getMessage());
        }

        // 처리 결과로 분석 결과 생성 후 리턴
        return buildAnalysisResult(context);
    }

    /**
     * LDIF 파일 처리 (라인별 파싱)
     */
    private void processLdifFile(BufferedReader bufferedReader, ParsingContext context, int totalLines)
            throws IOException {
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
                    ++recordNumber;
                    processRecord(currentRecord.toString(), recordNumber, lineNumber, context);
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
                    ++recordNumber;
                    processRecord(currentRecord.toString(), recordNumber, lineNumber - 1, context);
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
            ++recordNumber;
            processRecord(currentRecord.toString(), recordNumber, lineNumber, context);
        }
    }

    /**
     * 개별 레코드 처리
     */
    private void processRecord(String recordText, int recordNumber, int lineNumber, ParsingContext context) {
        log.debug("Start processing record #{}", recordNumber);
        try {
            ParseResult parseResult = parseRecord(recordText, recordNumber, lineNumber);
            if (parseResult.entry != null) {
                context.addEntry(parseResult.entry);
                context.updateStatistics(parseResult);

                // 국가별 통계 업데이트 - 국가 코드가 있는 경우에만
                String countryCode = extractCountryCodeFromDN(parseResult.entry.getDn());
                if (!"UNKNOWN".equals(countryCode)) {
                    context.addCountryStat(countryCode);
                    log.debug("Added country stat for: {} (DN: {})", countryCode, parseResult.entry.getDn());
                } else {
                    log.debug("Skipped country stat for entry without country code (DN: {})",
                            parseResult.entry.getDn());
                }
            }
        } catch (Exception e) {
            String error = String.format("Record %d (around line %d): %s", recordNumber, lineNumber, e.getMessage());
            context.addError(error);
            log.error("Error parsing record {}: {}", recordNumber, e.getMessage(), e);
        }
        log.debug("End processing record #{}", recordNumber);
    }

    /**
     * 레코드 파싱
     */
    private ParseResult parseRecord(String recordText, int recordNumber, int lineNumber)
            throws LDIFException, IOException {
        log.debug("Start processing record #{}", recordNumber);

        // 1. 먼저 binary attribute 존재 여부를 빠르게 쳌,
        boolean hasBinaryAttributes = hasBinaryAttributes(recordText);

        BinaryAttributeProcessor.ProcessResult binaryResult;

        if (hasBinaryAttributes) {
            // Binary attribute가 있는 경우만 커스텀 파싱 실행
            log.debug("Record {} contains binary attributes, using binary processor", recordNumber);
            binaryResult = binaryAttributeProcessor.processBinaryAttributes(recordText, recordNumber);
        } else {
            // Binary attribute가 없는 경우 빈 결과 객체 생성
            log.debug("Record {} has no binary attributes, skipping binary processing", recordNumber);
            binaryResult = createEmptyProcessResult();
        }

        // UnboundID LDIF 파서로 엔트리 파싱
        try (StringReader stringReader = new StringReader(recordText);
                LDIFReader ldifReader = new LDIFReader(new BufferedReader(stringReader))) {

            Entry entry = ldifReader.readEntry();
            log.debug("Parsed entry DN: {}", entry.getDN());

            if (entry != null) {
                // Binary attribute 유무에 따라 다른 변환 방법 사용
                LdifEntryDto entryDto = hasBinaryAttributes
                        ? convertToEntryDto(entry, "ADD", binaryResult.getBinaryAttributes())
                        : convertToEntryDtoSimple(entry, "ADD");
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

    private boolean hasBinaryAttributes(String recordText) {
        // "::" 패턴이 있는지 체크 (Base64 인코딩된 값의 표시)
        if (!recordText.contains("::")) {
            return false;
        }

        // 실제 binary attribute인지 확인
        String[] lines = recordText.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.contains("::")) {
                String[] parts = line.split("::", 2);
                if (parts.length == 2) {
                    String attrName = parts[0].trim();
                    if (isBinaryAttribute(attrName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 더 최적화된 binary attribute 체크 (정규식 사용)
     */
    private static final java.util.regex.Pattern BINARY_ATTR_PATTERN = java.util.regex.Pattern.compile(
            "^\\s*(userCertificate|caCertificate|crossCertificatePair|" +
                    "certificateRevocationList|authorityRevocationList|" +
                    "pkdMasterListContent|pkdDscCertificate)\\s*::",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.MULTILINE);

    /**
     * 정규식을 사용한 더 빠른 binary attribute 체크 (대안)
     */
    private boolean hasBinaryAttributesRegex(String recordText) {
        return BINARY_ATTR_PATTERN.matcher(recordText).find();
    }

    /**
     * Binary attribute가 없는 경우를 위한 빈 ProcessResult 생성
     */
    private BinaryAttributeProcessor.ProcessResult createEmptyProcessResult() {
        return new BinaryAttributeProcessor.ProcessResult();
    }

    /**
     * Binary attribute가 없는 단순한 엔트리를 위한 경량화된 변환 메서드
     */
    private LdifEntryDto convertToEntryDtoSimple(Entry entry, String entryType) {
        Map<String, List<String>> attributes = new HashMap<>();

        for (Attribute attribute : entry.getAttributes()) {
            String attributeName = attribute.getName();
            String[] values = attribute.getValues();
            attributes.put(attributeName, Arrays.asList(values));
        }

        return new LdifEntryDto(entry.getDN(), entryType, attributes, entry.toLDIFString());
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

        // 기본 통계
        summary.setErrors(context.getErrors());
        summary.setWarnings(context.getWarnings());
        summary.setTotalEntries(context.getEntries().size());
        summary.setAddEntries(context.getAddCount());
        summary.setModifyEntries(0); // LDIF add entries only for now
        summary.setDeleteEntries(0);
        summary.setObjectClassCount(context.getObjectClassCount());
        summary.setHasValidationErrors(!context.getErrors().isEmpty());

        // PKD 통계 업데이트
        summary.updateCertificateStats(
                context.getTotalCertificates(),
                context.getValidCertificates(),
                context.getInvalidCertificates());

        summary.updateMasterListStats(
                context.getTotalMasterLists(),
                context.getValidMasterLists(),
                context.getInvalidMasterLists());

        summary.updateCrlStats(
                context.getTotalCrls(),
                context.getValidCrls(),
                context.getInvalidCrls());

        // Trust Anchor 통계
        summary.updateTrustAnchorStats(certificateVerifier.getTrustAnchors().size());

        // 국가별 통계
        context.getCountryStats().forEach(summary::addCountryStat);

        // certificateValidationStats Map 설정 (Thymeleaf 호환성)
        Map<String, Integer> certificateStats = new HashMap<>();
        certificateStats.put("total", context.getTotalCertificates());
        certificateStats.put("valid", context.getValidCertificates());
        certificateStats.put("invalid", context.getInvalidCertificates());
        certificateStats.put("totalMasterLists", context.getTotalMasterLists());
        certificateStats.put("validMasterLists", context.getValidMasterLists());
        certificateStats.put("invalidMasterLists", context.getInvalidMasterLists());
        certificateStats.put("totalCrls", context.getTotalCrls());
        certificateStats.put("validCrls", context.getValidCrls());
        certificateStats.put("invalidCrls", context.getInvalidCrls());
        certificateStats.put("totalTrustAnchors", certificateVerifier.getTrustAnchors().size());

        summary.setCertificateValidationStats(certificateStats);

        LdifAnalysisResult result = new LdifAnalysisResult();
        result.setSummary(summary);
        result.setEntries(context.getEntries());

        logParsingResults(context, summary);
        return result;
    }

    /**
     * 파싱 결과 로깅 (PKD 정보 포함)
     */
    private void logParsingResults(ParsingContext context, LdifAnalysisSummary summary) {
        log.info("=== PKD LDIF Parsing Results ===");
        log.info("Total Entries: {}", context.getEntries().size());
        log.info("Errors: {}, Warnings: {}", context.getErrors().size(), context.getWarnings().size());

        if (summary.hasPkdContent()) {
            log.info("=== PKD Content Analysis ===");
            log.info("Certificates: {} (Valid: {}, Invalid: {})",
                    context.getTotalCertificates(), context.getValidCertificates(), context.getInvalidCertificates());
            log.info("Master Lists: {} (Valid: {}, Invalid: {})",
                    context.getTotalMasterLists(), context.getValidMasterLists(), context.getInvalidMasterLists());
            log.info("CRLs: {} (Valid: {}, Invalid: {})",
                    context.getTotalCrls(), context.getValidCrls(), context.getInvalidCrls());
            log.info("Overall PKD Validity Rate: {:.2f}%", summary.getPkdValidityRate());
            log.info("Countries represented: {}", context.getCountryStats().size());
            log.info("Trust Anchors available: {}", certificateVerifier.getTrustAnchors().size());
        } else {
            log.info("No PKD content detected in LDIF file");
        }
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
     * DN에서 국가 코드 추출
     */
    private String extractCountryCodeFromDN(String dn) {
        if (dn == null)
            return "UNKNOWN";

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
    public CertificateChainValidationResult validateCertificateChain(java.security.cert.X509Certificate certificate,
            String issuerCountry) {
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
        private final Map<String, Integer> countryStats = new HashMap<>();

        private int addCount = 0;

        // PKD 통계
        private int totalCertificates = 0;
        private int validCertificates = 0;
        private int invalidCertificates = 0;
        private int totalMasterLists = 0;
        private int validMasterLists = 0;
        private int invalidMasterLists = 0;
        private int totalCrls = 0;
        private int validCrls = 0;
        private int invalidCrls = 0;

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

        public void addCountryStat(String countryCode) {
            countryStats.merge(countryCode, 1, Integer::sum);
        }

        public void updateStatistics(ParseResult parseResult) {
            BinaryAttributeProcessor.ProcessResult result = parseResult.binaryResult;

            // PKD 통계 업데이트
            totalCertificates += result.getTotalCertificates();
            validCertificates += result.getValidCertificates();
            invalidCertificates += result.getInvalidCertificates();
            totalMasterLists += result.getTotalMasterLists();
            validMasterLists += result.getValidMasterLists();
            invalidMasterLists += result.getInvalidMasterLists();
            totalCrls += result.getTotalCrls();
            validCrls += result.getValidCrls();
            invalidCrls += result.getInvalidCrls();

            // 경고 추가
            addWarnings(result.getWarnings());

            // 국가별 통계 병합
            result.getCountryStats().forEach((country, count) -> countryStats.merge(country, count, Integer::sum));
        }

        private void countObjectClasses(LdifEntryDto entry) {
            List<String> objectClasses = entry.getAttributes().get("objectClass");
            if (objectClasses != null) {
                for (String objectClass : objectClasses) {
                    objectClassCount.merge(objectClass, 1, Integer::sum);
                }
            }
        }

        // Getters
        public List<LdifEntryDto> getEntries() {
            return entries;
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public Map<String, Integer> getObjectClassCount() {
            return objectClassCount;
        }

        public Map<String, Integer> getCountryStats() {
            return countryStats;
        }

        public int getAddCount() {
            return addCount;
        }

        // PKD 통계 Getters
        public int getTotalCertificates() {
            return totalCertificates;
        }

        public int getValidCertificates() {
            return validCertificates;
        }

        public int getInvalidCertificates() {
            return invalidCertificates;
        }

        public int getTotalMasterLists() {
            return totalMasterLists;
        }

        public int getValidMasterLists() {
            return validMasterLists;
        }

        public int getInvalidMasterLists() {
            return invalidMasterLists;
        }

        public int getTotalCrls() {
            return totalCrls;
        }

        public int getValidCrls() {
            return validCrls;
        }

        public int getInvalidCrls() {
            return invalidCrls;
        }
    }
}
