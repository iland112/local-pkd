package com.smartcoreinc.localpkd.ldif.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.smartcoreinc.localpkd.enums.EntryType;
import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisResult;
import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisSummary;
import com.smartcoreinc.localpkd.ldif.service.processing.BinaryAttributeProcessingStrategy;
import com.smartcoreinc.localpkd.ldif.service.processing.BinaryAttributeStrategyFactory;
import com.smartcoreinc.localpkd.ldif.service.processing.ProcessingContext;
import com.smartcoreinc.localpkd.ldif.service.processing.ProcessingResult;
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
public class StreamlinedLDIFParser {
    private final ProgressPublisher progressPublisher;
    private final CertificateVerifier certificateVerifier;
    private final BinaryAttributeStrategyFactory strategyFactory;
    
    // 바이너리 속성 목록
    private static final Set<String> BINARY_ATTRIBUTES = Set.of(
        "usercertificate", "cacertificate", "crosscertificatepair",
        "certificaterevocationlist", "authorityrevocationlist",
        "pkdmasterlistcontent", "pkddsccertificate"
    );

    public StreamlinedLDIFParser(ProgressPublisher progressPublisher,
                                CertificateVerifier certificateVerifier,
                                BinaryAttributeStrategyFactory strategyFactory) {
        this.progressPublisher = progressPublisher;
        this.certificateVerifier = certificateVerifier;
        this.strategyFactory = strategyFactory;
    }

    /**
     * LDIF 파일 파싱 - Entry Wrapper 방식
     */
    public LdifAnalysisResult parseLdifFile(MultipartFile file) throws IOException {
        long fileSize = file.getSize();
        log.info("Starting streamlined LDIF parsing for file: {}, {} bytes", file.getOriginalFilename(), fileSize);

        // LDIF 파일 내의 Entry 총 개수 구하기
        int totalEntryCount = getEntryCount(file);
        log.debug("Total Entries in LDIF file: {}", totalEntryCount);   

        AnalysisStatistics stats = new AnalysisStatistics();
        List<ParsedLdifEntry> processedEntries = new ArrayList<>();
        int entryCount = 0;

        try (InputStream inputStream = file.getInputStream();
             LDIFReader ldifReader = new LDIFReader(inputStream)) {

            Entry entry;
            while ((entry = ldifReader.readEntry()) != null) {
                entryCount++;
                
                // 진행률 발행 - Entry 기반
                publishEntryProgress(totalEntryCount, entryCount, entry.getDN());

                try {
                    ParsedLdifEntry wrapper = processEntry(entry, entryCount);
                    processedEntries.add(wrapper);
                    stats.updateFrom(wrapper);
                    
                } catch (Exception e) {
                    String error = String.format("Entry %d (%s): %s", 
                        entryCount, entry.getDN(), e.getMessage());
                    stats.addError(error);
                    log.error("Error processing entry {}: {}", entry.getDN(), e.getMessage(), e);
                }
            }

        } catch (LDIFException | IOException e) {
            log.error("LDIF processing error: {}", e.getMessage());
            stats.addError("LDIF processing error: " + e.getMessage());
        }

        return buildAnalysisResult(stats, processedEntries);
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
     * Entry 처리 - Wrapper 생성
     */
    private ParsedLdifEntry processEntry(Entry entry, int entryNumber) {
        ParsedLdifEntry wrapper = new ParsedLdifEntry(entry, entryNumber);
        
        // EntryType 결정 및 캐싱
        wrapper.resolveEntryType();
        
        // 국가 코드 추출 및 캐싱
        wrapper.resolveCountryCode();
        
        // 바이너리 속성 처리
        processBinaryAttributes(wrapper);
        
        return wrapper;
    }

    /**
     * 바이너리 속성 처리
     */
    private void processBinaryAttributes(ParsedLdifEntry wrapper) {
        Entry entry = wrapper.getParsedEntry();
        ProcessingContext context = new ProcessingContext(
            wrapper.getEntryNumber(), 
            wrapper.getCountryCode(), 
            wrapper.getEntryType()
        );

        for (Attribute attribute : entry.getAttributes()) {
            String attributeName = attribute.getName();
            
            if (isBinaryAttribute(attributeName)) {
                BinaryAttributeProcessingStrategy strategy = strategyFactory.getStrategy(attributeName);
                
                if (strategy != null) {
                    for (byte[] binaryValue : attribute.getValueByteArrays()) {
                        try {
                            ProcessingResult result = strategy.process(attributeName, binaryValue, context);
                            wrapper.addProcessingResult(attributeName, result);
                            
                        } catch (Exception e) {
                            wrapper.addWarning(String.format("Failed to process %s: %s", 
                                attributeName, e.getMessage()));
                        }
                    }
                } else {
                    wrapper.addWarning(String.format("No processor for attribute '%s'", attributeName));
                }
            }
        }
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
     * 분석 결과 생성
     */
    private LdifAnalysisResult buildAnalysisResult(AnalysisStatistics stats, 
                                                  List<ParsedLdifEntry> processedEntries) {
        LdifAnalysisSummary summary = new LdifAnalysisSummary();
        
        // 기본 통계 설정
        summary.setErrors(stats.getErrors());
        summary.setWarnings(stats.getWarnings());
        summary.setTotalEntries(processedEntries.size());
        summary.setAddEntries(processedEntries.size());
        summary.setEntryTypeCount(stats.getEntryTypeCount());
        summary.setHasValidationErrors(!stats.getErrors().isEmpty());

        // PKD 통계 설정
        summary.updateCertificateStats(stats.getTotalCertificates(), 
                                      stats.getValidCertificates(), 
                                      stats.getInvalidCertificates());
        summary.updateMasterListStats(stats.getTotalMasterLists(), 
                                     stats.getValidMasterLists(), 
                                     stats.getInvalidMasterLists());
        summary.updateCrlStats(stats.getTotalCrls(), 
                              stats.getValidCrls(), 
                              stats.getInvalidCrls());

        // Trust Anchor 통계
        Map<String, TrustAnchorInfo> trustAnchors = certificateVerifier.getTrustAnchorsSummary();
        summary.updateTrustAnchorStats(trustAnchors.size());

        // 국가별 통계
        stats.getCountryStats().forEach(summary::addCountryStat);

        // 통계 맵 설정
        Map<String, Integer> certificateStats = createStatsMap(stats, trustAnchors.size());
        summary.setCertificateValidationStats(certificateStats);

        LdifAnalysisResult result = new LdifAnalysisResult();
        result.setSummary(summary);
        
        // 필요한 경우에만 Entry 데이터 포함 (메모리 절약)
        if (shouldIncludeEntryData()) {
            result.setEntries(convertToLegacyDtos(processedEntries));
        }

        logResults(stats);
        return result;
    }

    /**
     * Legacy DTO 변환 (필요시에만)
     */
    private List<com.smartcoreinc.localpkd.ldif.dto.LdifEntryDto> convertToLegacyDtos(
            List<ParsedLdifEntry> wrappers) {
        return wrappers.stream()
            .map(ParsedLdifEntry::toLegacyDto)
            .toList();
    }

    private boolean shouldIncludeEntryData() {
        // 설정이나 요청에 따라 결정
        return true; // 또는 configuration에서 읽어오기
    }

    private Map<String, Integer> createStatsMap(AnalysisStatistics stats, int trustAnchorCount) {
        Map<String, Integer> map = new HashMap<>();
        map.put("total", stats.getTotalCertificates());
        map.put("valid", stats.getValidCertificates());
        map.put("invalid", stats.getInvalidCertificates());
        map.put("totalMasterLists", stats.getTotalMasterLists());
        map.put("validMasterLists", stats.getValidMasterLists());
        map.put("invalidMasterLists", stats.getInvalidMasterLists());
        map.put("totalCrls", stats.getTotalCrls());
        map.put("validCrls", stats.getValidCrls());
        map.put("invalidCrls", stats.getInvalidCrls());
        map.put("totalTrustAnchors", trustAnchorCount);
        return map;
    }

    private boolean isBinaryAttribute(String attributeName) {
        String lowerName = attributeName.toLowerCase();
        return BINARY_ATTRIBUTES.stream()
            .anyMatch(binaryAttr -> lowerName.contains(binaryAttr));
    }

    private void publishProgress(int entryCount, String currentDN) {
        // 간단한 진행률 발행 (총 개수를 미리 알 필요 없음)
        Progress progress = new Progress(0.0, "LDIF"); // 비율은 나중에 계산
        ProgressEvent event = new ProgressEvent(progress, entryCount, 0, 
                                               "Processing: " + currentDN, null);
        progressPublisher.notifyProgressListeners(event);
    }

    private void logResults(AnalysisStatistics stats) {
        log.info("=== Streamlined LDIF Parsing Results ===");
        log.info("Total Entries: {}", stats.getTotalEntries());
        log.info("Errors: {}, Warnings: {}", stats.getErrors().size(), stats.getWarnings().size());
        
        stats.getEntryTypeCount().forEach((type, count) -> {
            if (count > 0) {
                log.info("{}: {} entries", type, count);
            }
        });
        
        log.info("Certificates: {} (Valid: {}, Invalid: {})",
            stats.getTotalCertificates(), stats.getValidCertificates(), stats.getInvalidCertificates());
        log.info("Countries: {}", stats.getCountryStats().size());
    }

    // Delegate methods
    public CertificateChainValidationResult validateCertificateChain(
            java.security.cert.X509Certificate certificate, String issuerCountry, EntryType entryType) {
        return certificateVerifier.validateCertificateChain(certificate, issuerCountry, entryType);
    }

    public Map<String, TrustAnchorInfo> getTrustAnchorsSummary() {
        return certificateVerifier.getTrustAnchorsSummary();
    }

    // ParsedLdifEntry는 별도 파일로 분리됨

    /**
     * 통계 수집 클래스
     */
    private static class AnalysisStatistics {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final Map<String, Integer> entryTypeCount = new HashMap<>();
        private final Map<String, Integer> countryStats = new HashMap<>();
        
        private int totalEntries = 0;
        private int totalCertificates = 0;
        private int validCertificates = 0;
        private int invalidCertificates = 0;
        private int totalMasterLists = 0;
        private int validMasterLists = 0;
        private int invalidMasterLists = 0;
        private int totalCrls = 0;
        private int validCrls = 0;
        private int invalidCrls = 0;

        public void updateFrom(ParsedLdifEntry wrapper) {
            totalEntries++;
            entryTypeCount.merge(wrapper.getEntryType().name(), 1, Integer::sum);
            
            if (!"UNKNOWN".equals(wrapper.getCountryCode())) {
                countryStats.merge(wrapper.getCountryCode(), 1, Integer::sum);
            }
            
            warnings.addAll(wrapper.getWarnings());
            
            // 처리 결과 통계 업데이트
            wrapper.getProcessingResults().forEach((attrName, results) -> {
                for (ProcessingResult result : results) {
                    ProcessingResult.ProcessingMetrics metrics = result.getMetrics();
                    
                    if (attrName.toLowerCase().contains("certificate")) {
                        totalCertificates += metrics.getProcessedItems();
                        validCertificates += metrics.getValidItems();
                        invalidCertificates += metrics.getInvalidItems();
                    } else if (attrName.toLowerCase().contains("masterlist")) {
                        totalMasterLists += metrics.getProcessedItems();
                        validMasterLists += metrics.getValidItems();
                        invalidMasterLists += metrics.getInvalidItems();
                    } else if (attrName.toLowerCase().contains("crl") || attrName.toLowerCase().contains("revocation")) {
                        totalCrls += metrics.getProcessedItems();
                        validCrls += metrics.getValidItems();
                        invalidCrls += metrics.getInvalidItems();
                    }
                }
            });
        }

        public void addError(String error) {
            errors.add(error);
        }

        // Getters
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public Map<String, Integer> getEntryTypeCount() { return entryTypeCount; }
        public Map<String, Integer> getCountryStats() { return countryStats; }
        public int getTotalEntries() { return totalEntries; }
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
