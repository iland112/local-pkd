package com.smartcoreinc.localpkd.ldif.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.smartcoreinc.localpkd.enums.EntryType;
import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisResult;
import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisSummary;
import com.smartcoreinc.localpkd.ldif.dto.LdifEntryDto;
import com.smartcoreinc.localpkd.ldif.service.processing.BinaryAttributeProcessingStrategy;
import com.smartcoreinc.localpkd.ldif.service.processing.BinaryAttributeStrategyFactory;
import com.smartcoreinc.localpkd.ldif.service.processing.CRLAttributeProcessingStrategy;
import com.smartcoreinc.localpkd.ldif.service.processing.ProcessingContext;
import com.smartcoreinc.localpkd.ldif.service.processing.ProcessingResult;
import com.smartcoreinc.localpkd.sse.Progress;
import com.smartcoreinc.localpkd.sse.ProgressEvent;
import com.smartcoreinc.localpkd.sse.ProgressPublisher;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldif.LDIFException;
import com.unboundid.ldif.LDIFReader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LDIFParser {

    private final ProgressPublisher progressPublisher;
    private final CertificateVerifier certificateVerifier;
    private final BinaryAttributeStrategyFactory strategyFactory;

    // 바이너리 속성 목록 (성능 최적화를 위해 Set 사용)
    private static final java.util.Set<String> BINARY_ATTRIBUTES = java.util.Set.of(
            "usercertificate", "cacertificate", "crosscertificatepair",
            "certificaterevocationlist", "authorityrevocationlist",
            "pkdmasterlistcontent", "pkddsccertificate");
 
    public LDIFParser(ProgressPublisher progressPublisher,
                      CertificateVerifier certificateVerifier,
                      BinaryAttributeStrategyFactory strategyFactory) {
        this.progressPublisher = progressPublisher;
        this.certificateVerifier = certificateVerifier;
        this.strategyFactory = strategyFactory;
    }

    /**
     * LDIF 파일 파싱 - Entry 단위 직접 처리
     */
    public LdifAnalysisResult parseLdifFile(MultipartFile file) throws IOException {
        long fileSize = file.getSize();
        log.info("Starting entry-based LDIF parsing for file: {}, {} bytes", file.getOriginalFilename(), fileSize);

        // LDIF 파일 내의 Entry 총 개수 구하기
        int totalEntryCount = getEntryCount(file);
        log.debug("Total Entries in LDIF file: {}", totalEntryCount);        
        
        ParsingContext context = new ParsingContext();
        int entryCount = 0;
        try (InputStream inputStream = file.getInputStream();
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
             LDIFReader ldifReader = new LDIFReader(bufferedReader)) {
            
            Entry entry;
            while ((entry = ldifReader.readEntry()) != null) {
                entryCount++;
                
                // 진행률 발행 - Entry 기반
                publishEntryProgress(totalEntryCount, entryCount, entry.getDN());

                try {
                    processEntry(entry, entryCount, context);
                } catch (Exception e) {
                    String error = String.format("Entry %d (%s): %s", 
                        entryCount, entry.getDN(), e.getMessage());
                    context.addError(error);
                    log.error("Error processing entry {}: {}", entry.getDN(), e.getMessage(), e);
                }
            }

        } catch (LDIFException e) {
            log.error("LDIF format error: {}", e.getMessage());
            context.addError("LDIF format error: " + e.getMessage());
        } catch (IOException e) {
            log.error("IO Error reading LDIF file: {}", e.getMessage());
            context.addError("File reading error: " + e.getMessage());
        }

        return buildAnalysisResult(context);
    }

    private int getEntryCount(MultipartFile file) {
        int entryCount = 0;
        try (InputStream inputStream = file.getInputStream();
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
             LDIFReader ldifReader = new LDIFReader(bufferedReader)) {

            while (ldifReader.readEntry() != null) {
                entryCount++;
            }

        } catch (LDIFException e) {
            log.error("LDIF format error: {}", e.getMessage());
        } catch (IOException e) {
            log.error("IO Error reading LDIF file: {}", e.getMessage());
        }

        return entryCount;
    }

    /**
     * Entry 단위 처리 - 핵심 개선사항
     */
    private void processEntry(Entry entry, int entryNumber, ParsingContext context) {
        log.debug("Processing entry #{}: {}", entryNumber, entry.getDN());

        // 1. EntryType 결정
        EntryType entryType;
        try {
            entryType = EntryTypeResolver.resolveEntryType(entry);
        } catch (LDAPException e) {
            log.error("Cannot resolve entry type: {}", e.getDiagnosticMessage());
            entryType = EntryType.UNKNOWN;
        }
        log.debug("Resolved Entry Type: {}({})", entryType.name(), entryType.getDescription());
        
        // 2. 바이너리 속성 존재 여부 체크
        Map<String, List<byte[]>> binaryData = extractBinaryAttributes(entry);
        
        BinaryProcessingResult binaryResult = new BinaryProcessingResult();
        if (!binaryData.isEmpty()) {
            // 3. 바이너리 속성을 전략 패턴으로 직접 처리
            String countryCode = extractCountryCodeFromDN(entry.getDN());
            ProcessingContext processingContext = new ProcessingContext(entryNumber, countryCode, entryType);
            
            processBinaryAttributesDirect(binaryData, processingContext, binaryResult);
        }

        // 4. DTO 변환
        LdifEntryDto entryDto = convertToEntryDto(entry, entryType, binaryData);

        // 5. 컨텍스트 업데이트
        context.addEntry(entryDto);
        context.updateStatistics(binaryResult);
        
        // 6. EntryType 통계 업데이트
        context.addEntryType(entryType);
        
        // 7. 국가별 통계 업데이트
        String countryCode = extractCountryCodeFromDN(entry.getDN());
        if (!"UNKNOWN".equals(countryCode) && isBinaryEntry(entryType)) {
            context.addCountryStat(countryCode);
        }

        // 8. SSE로 EntryType 업데이트 발행
        // publishEntryTypeUpdate(entryType);

        log.debug("Completed processing entry #{}:{} {}", entryNumber, entryType.name(), entry.getDN());
    }

    /**
     * 바이너리 속성 직접 처리 - 전략 패턴 활용
     */
    private void processBinaryAttributesDirect(Map<String, List<byte[]>> binaryData, 
                                               ProcessingContext processingContext, 
                                               BinaryProcessingResult result) {
        
        for (Map.Entry<String, List<byte[]>> attrEntry : binaryData.entrySet()) {
            String attributeName = attrEntry.getKey();
            List<byte[]> values = attrEntry.getValue();
            
            // 속성에 맞는 전략 찾기
            BinaryAttributeProcessingStrategy strategy = strategyFactory.getStrategy(attributeName);
            if (strategy instanceof CRLAttributeProcessingStrategy) {
                log.debug("전략 패턴 클래스 이름: {}", strategy.getClass().getName());
            }
            
            if (strategy != null) {
                log.debug("Using strategy {} for attribute '{}'", 
                    strategy.getClass().getSimpleName(), attributeName);
                
                for (byte[] binaryValue : values) {
                    try {
                        ProcessingResult processingResult = strategy.process(
                            attributeName, binaryValue, processingContext);
                        
                        // 결과를 종합 결과에 반영
                        updateBinaryResult(result, processingResult, attributeName);
                        
                    } catch (Exception e) {
                        String warning = String.format("Entry %d: Failed to process binary attribute '%s': %s",
                            processingContext.getRecordNumber(), attributeName, e.getMessage());
                        result.addWarning(warning);
                        log.warn("Failed to process binary attribute '{}' in entry {}: {}",
                            attributeName, processingContext.getRecordNumber(), e.getMessage());
                    }
                }
            } else {
                log.debug("No strategy found for attribute '{}', using default processing", attributeName);
                String warning = String.format("Entry %d: No specific processor for attribute '%s'", 
                    processingContext.getRecordNumber(), attributeName);
                result.addWarning(warning);
                
                // 기본 통계 업데이트
                updateDefaultStatistics(result, attributeName, values.size());
            }
        }
        
        // 바이너리 속성 데이터 저장 (DTO 변환에 사용)
        for (Map.Entry<String, List<byte[]>> entry : binaryData.entrySet()) {
            result.getBinaryAttributes().put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 처리 결과를 종합 결과에 반영
     */
    private void updateBinaryResult(BinaryProcessingResult result, ProcessingResult processingResult, String attributeName) {
        // 경고 추가
        result.addWarnings(processingResult.getWarnings());
        
        // 메트릭 업데이트
        ProcessingResult.ProcessingMetrics metrics = processingResult.getMetrics();
        
        if (isCertificateAttribute(attributeName)) {
            result.totalCertificates += metrics.getProcessedItems();
            result.validCertificates += metrics.getValidItems();
            result.invalidCertificates += metrics.getInvalidItems();
        } else if (isMasterListAttribute(attributeName)) {
            result.totalMasterLists += metrics.getProcessedItems();
            result.validMasterLists += metrics.getValidItems();
            result.invalidMasterLists += metrics.getInvalidItems();
        } else if (isCrlAttribute(attributeName)) {
            result.totalCrls += metrics.getProcessedItems();
            result.validCrls += metrics.getValidItems();
            result.invalidCrls += metrics.getInvalidItems();
        }
        
        if (!processingResult.isSuccess()) {
            result.addWarning(String.format("Processing failed for attribute '%s': %s", 
                attributeName, processingResult.getMessage()));
        }
    }

    /**
     * 전략이 없는 속성에 대한 기본 통계 처리
     */
    private void updateDefaultStatistics(BinaryProcessingResult result, String attributeName, int valueCount) {
        if (isCertificateAttribute(attributeName)) {
            result.totalCertificates += valueCount;
            // 기본적으로 유효하다고 가정 (실제 검증은 전략에서 수행)
            result.validCertificates += valueCount;
        } else if (isMasterListAttribute(attributeName)) {
            result.totalMasterLists += valueCount;
            result.validMasterLists += valueCount;
        } else if (isCrlAttribute(attributeName)) {
            result.totalCrls += valueCount;
            result.validCrls += valueCount;
        }
    }

    /**
     * Entry에서 바이너리 속성 추출
     */
    private Map<String, List<byte[]>> extractBinaryAttributes(Entry entry) {
        Map<String, List<byte[]>> binaryData = new HashMap<>();
        
        for (Attribute attribute : entry.getAttributes()) {
            String originalAttrName = attribute.getName();
            
            if (isBinaryAttribute(originalAttrName)) {
                List<byte[]> values = new ArrayList<>();
                
                // UnboundID는 바이너리 데이터를 자동으로 처리
                for (byte[] value : attribute.getValueByteArrays()) {
                    values.add(value);
                }
                
                if (!values.isEmpty()) {
                    binaryData.put(originalAttrName, values);
                    log.debug("Extracted {} binary values for attribute '{}'", 
                        values.size(), originalAttrName);
                }
            }
        }
        
        return binaryData;
    }

    /**
     * Entry를 DTO로 변환
     */
    private LdifEntryDto convertToEntryDto(Entry entry, EntryType entryType, 
            Map<String, List<byte[]>> binaryData) {
        
        Map<String, List<String>> attributes = new HashMap<>();

        for (Attribute attribute : entry.getAttributes()) {
            String attributeName = attribute.getName();
            
            if (binaryData.containsKey(attributeName)) {
                // 바이너리 속성은 Base64 인코딩하여 저장
                List<String> base64Values = new ArrayList<>();
                for (byte[] binaryValue : binaryData.get(attributeName)) {
                    base64Values.add(Base64.getEncoder().encodeToString(binaryValue));
                }
                attributes.put(attributeName, base64Values);
            } else {
                // 일반 속성 처리
                attributes.put(attributeName, Arrays.asList(attribute.getValues()));
            }
        }

        return new LdifEntryDto(entry.getDN(), entryType, attributes, entry.toLDIFString());
    }

    /**
     * 바이너리 속성 여부 확인 (기존 LdifParser와 동일한 로직)
     */
    private boolean isBinaryAttribute(String attributeName) {
        return BINARY_ATTRIBUTES.stream()
                .anyMatch(binaryAttr -> attributeName.toLowerCase().contains(binaryAttr.toLowerCase()));
    }

    // 속성 타입 확인 메서드들
    private boolean isCertificateAttribute(String attributeName) {
        String lowerName = attributeName.toLowerCase();
        return lowerName.contains("certificate") || 
               lowerName.contains("usercertificate") || 
               lowerName.contains("cacertificate");
    }

    private boolean isMasterListAttribute(String attributeName) {
        return attributeName.contains("pkdMasterListContent") || 
               attributeName.toLowerCase().contains("masterlist");
    }

    private boolean isCrlAttribute(String attributeName) {
        String lowerName = attributeName.toLowerCase();
        return lowerName.contains("certificaterevocationlist") || 
               lowerName.contains("authorityrevocationlist") ||
               lowerName.contains("crl");
    }

    /**
     * Entry 기반 진행상태 발행
     */
    private void publishEntryProgress(int totalEntryCount, int processedEntries, String currentDN) {
        double percentage = processedEntries / (double) totalEntryCount;
        Progress progress = new Progress(percentage, "LDIF"); // Entry 기반이므로 비율 계산 없음
        ProgressEvent progressEvent = new ProgressEvent(progress, 
            processedEntries, totalEntryCount, "Processing Entry: " + currentDN, null);
        progressPublisher.notifyProgressListeners(progressEvent);
    }

    /**
     * EntryType 업데이트 발행 (SSE)
     */
    private void publishEntryTypeUpdate(EntryType entryType) {
        try {
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("type", "entryTypeUpdate");
            updateData.put("entryType", entryType.name());
            updateData.put("description", entryType.getDescription());
            
            Progress progress = new Progress(0.0, "entryType");
            ProgressEvent event = new ProgressEvent(progress, 0, 0, 
                "EntryType: " + entryType.name(), null);
            progressPublisher.notifyProgressListeners(event);
            
        } catch (Exception e) {
            log.debug("Failed to publish entry type update: {}", e.getMessage());
        }
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
        summary.setModifyEntries(0);
        summary.setDeleteEntries(0);

        // EntryType 통계 설정 (ObjectClass 대신)
        summary.setEntryTypeCount(context.getEntryTypeCount());
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
        Map<String, TrustAnchorInfo> trustAnchors = getTrustAnchorsSummary();
        summary.updateTrustAnchorStats(trustAnchors.size());

        // 국가별 통계
        context.getCountryStats().forEach(summary::addCountryStat);

        // 통계 맵 설정
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
        certificateStats.put("totalTrustAnchors", trustAnchors.size());

        summary.setCertificateValidationStats(certificateStats);

        LdifAnalysisResult result = new LdifAnalysisResult();
        result.setSummary(summary);
        result.setEntries(context.getEntries());

        logParsingResults(context, summary);
        return result;
    }

    private void logParsingResults(ParsingContext context, LdifAnalysisSummary summary) {
        log.info("=== PKD LDIF Parsing Results (Entry-based with EntryType Statistics) ===");
        log.info("Total Entries: {}", context.getEntries().size());
        log.info("Errors: {}, Warnings: {}", context.getErrors().size(), context.getWarnings().size());

        // EntryType별 통계 로깅
        Map<String, Integer> entryTypeStats = context.getEntryTypeCount();
        log.info("=== EntryType Distribution ===");
        for (EntryType entryType : EntryType.values()) {
            int count = entryTypeStats.getOrDefault(entryType.name(), 0);
            if (count > 0) {
                log.info("{}: {} entries ({})", entryType.name(), count, entryType.getDescription());
            }
        }

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
        }
    }

    // 유틸리티 메서드들
    private boolean isBinaryEntry(EntryType entryType) {
        return Arrays.asList(EntryType.CRL, EntryType.DSC, EntryType.ML).contains(entryType);
    }

    private String extractCountryCodeFromDN(String dn) {
        if (dn == null || dn.isEmpty()) return "UNKNOWN";

        String[] parts = dn.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.toLowerCase().startsWith("c=")) {
                String countryCode = part.substring(2).trim();
                return countryCode.replaceAll("\\\\(.)", "$1").toUpperCase();
            }
        }
        return "UNKNOWN";
    }

    // Delegate methods to CertificateVerifier (기존 LdifParser와 동일)
    public CertificateChainValidationResult validateCertificateChain(java.security.cert.X509Certificate certificate,
            String issuerCountry, EntryType entryType) {
        return certificateVerifier.validateCertificateChain(certificate, issuerCountry, entryType);
    }

    public Map<String, TrustAnchorInfo> getTrustAnchorsSummary() {
        return certificateVerifier.getTrustAnchorsSummary();
    }

    /**
     * 바이너리 처리 결과 클래스 - BinaryAttributeProcessor.ProcessResult와 호환
     */
    private static class BinaryProcessingResult {
        private final Map<String, Object> binaryAttributes = new HashMap<>();
        private final List<String> warnings = new ArrayList<>();

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

        private final Map<String, Integer> countryStats = new HashMap<>();

        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public void addWarnings(List<String> warnings) {
            this.warnings.addAll(warnings);
        }

        // Getters - BinaryAttributeProcessor.ProcessResult와 동일한 인터페이스
        public Map<String, Object> getBinaryAttributes() { return binaryAttributes; }
        public List<String> getWarnings() { return warnings; }
        public int getTotalCertificates() { return totalCertificates; }
        public int getValidCertificates() { return validCertificates; }
        public int getInvalidCertificates() { return invalidCertificates; }
        public int getTotalMasterLists() { return totalMasterLists; }
        public int getValidMasterLists() { return validMasterLists; }
        public int getInvalidMasterLists() { return invalidMasterLists; }
        public int getTotalCrls() { return totalCrls; }
        public int getValidCrls() { return validCrls; }
        public int getInvalidCrls() { return invalidCrls; }
        public Map<String, Integer> getCountryStats() { return countryStats; }
    }

    /**
     * 파싱 컨텍스트 - ParsingContext와 BinaryProcessingResult 브릿지
     */
    private static class ParsingContext {
        private final List<LdifEntryDto> entries = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final Map<String, Integer> entryTypeCount = new HashMap<>();
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

        public void addEntryType(EntryType entryType) {
            entryTypeCount.merge(entryType.name(), 1, Integer::sum);
        }

        public void updateStatistics(BinaryProcessingResult result) {
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

        // Getters
        public List<LdifEntryDto> getEntries() { return entries; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public Map<String, Integer> getEntryTypeCount() { return entryTypeCount; }
        public Map<String, Integer> getCountryStats() { return countryStats; }
        public int getAddCount() { return addCount; }

        // PKD 통계 Getters
        public int getTotalCertificates() { return totalCertificates; }
        public int getValidCertificates() { return validCertificates; }
        public int getInvalidCertificates() { return invalidCertificates; }
        public int getTotalMasterLists() { return totalMasterLists; }
        public int getValidMasterLists() { return validMasterLists; }
        public int getInvalidMasterLists() { return invalidMasterLists; }
        public int getTotalCrls() { return totalCrls; }
        public int getValidCrls() { return validCrls; }
        public int getInvalidCrls() { return invalidCrls; }
    }
}
