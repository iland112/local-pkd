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
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.smartcoreinc.localpkd.enums.EntryType;
import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisResult;
import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisSummary;
import com.smartcoreinc.localpkd.ldif.service.processing.BinaryAttributeProcessingStrategy;
import com.smartcoreinc.localpkd.ldif.service.processing.BinaryAttributeStrategyFactory;
import com.smartcoreinc.localpkd.ldif.service.processing.ProcessingContext;
import com.smartcoreinc.localpkd.ldif.service.processing.ProcessingResult;
import com.smartcoreinc.localpkd.sse.broker.ParsingProgressBroker;
import com.smartcoreinc.localpkd.sse.event.ParsingEvent;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldif.LDIFException;
import com.unboundid.ldif.LDIFReader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LDIFParser {

    private final CertificateVerifier certificateVerifier;
    private final BinaryAttributeStrategyFactory strategyFactory;
    private final ParsingProgressBroker parsingProgressBroker;
    
    // 바이너리 속성 목록
    private static final Set<String> BINARY_ATTRIBUTES = Set.of(
        "usercertificate", "cacertificate", "crosscertificatepair",
        "certificaterevocationlist", "authorityrevocationlist",
        "pkdmasterlistcontent", "pkddsccertificate"
    );

    // 진행률 발행 최적화를 위한 상수
    private static final int SMALL_FILE_THRESHOLD = 100;
    private static final int MEDIUM_FILE_THRESHOLD = 1000;
    private static final int PROGRESS_UPDATE_BATCH_SIZE = 50;

    public LDIFParser(
            CertificateVerifier certificateVerifier,
            BinaryAttributeStrategyFactory strategyFactory,
            ParsingProgressBroker parsingProgressBroker) {
        this.certificateVerifier = certificateVerifier;
        this.strategyFactory = strategyFactory;
        this.parsingProgressBroker = parsingProgressBroker;
    }

    /**
     * LDIF 파일 파싱 - Entry Wrapper 방식
     */
    public LdifAnalysisResult parseLdifFile(MultipartFile file, String sessionId) throws Exception {
        long fileSize = file.getSize();
        String fileName = file.getOriginalFilename();
        log.info("Starting LDIF parsing for session: {}, file: {}, {} bytes",
                sessionId, fileName, fileSize);

        // 파싱 세션 시작
        parsingProgressBroker.startParsingSession(sessionId);

        try {
            // 1단계: 파일 크기 확인 및 Entry 개수 계산
            publishParsingProgress(sessionId, 0.02, "파일 분석 중...", fileName, 0, 0);
            
            // 전체 Entry 개수 계산
            int totalEntryCount = getTotalEntryCount(file);
            log.debug("Total Entries in LDIF file: {}", totalEntryCount);
            
            if (totalEntryCount <= 0) {
                publishParsingError(sessionId, "LDIF 파일에 유효한 엔트리가 없습니다.", fileName);
                throw new IllegalArgumentException("No valid entries found in LDIF file");
            }

            // 2단계: 실제 파싱 시작
            publishParsingProgress(sessionId, 0.05, "LDIF 파싱 시작...", fileName, 0, totalEntryCount);

            AnalysisStatistics stats = new AnalysisStatistics();
            List<ParsedLdifEntry> processedEntries = new ArrayList<>();
            AtomicInteger entryCounter = new AtomicInteger(0);

            // 진행률 발행 주기 결정
            ProgressUpdateStrategy updateStrategy = determineUpdateStrategy(totalEntryCount);

            try (InputStream inputStream = file.getInputStream();
                 LDIFReader ldifReader = new LDIFReader(inputStream)) {

                Entry entry;
                while ((entry = ldifReader.readEntry()) != null) {
                    int currentEntryNum = entryCounter.incrementAndGet();
                    
                    try {
                        // Entry 처리
                        ParsedLdifEntry wrapper = processEntry(entry, currentEntryNum);
                        processedEntries.add(wrapper);
                        stats.updateFrom(wrapper);

                        // 최적화된 진행률 업데이트
                        if (updateStrategy.shouldUpdateProgress(currentEntryNum)) {
                            // 실제 엔트리 수가 예상과 다를 수 있으므로 동적 조정
                            int adjustedTotal = Math.max(totalEntryCount, currentEntryNum);
                            publishEntryProgress(sessionId, currentEntryNum, adjustedTotal, 
                                               entry.getDN(), fileName, stats);
                        }
                    } catch (Exception e) {
                        String error = String.format("Entry %d (%s): %s", 
                            currentEntryNum, entry.getDN(), e.getMessage());
                        stats.addError(error);
                        log.error("Error processing entry {}: {}", entry.getDN(), e.getMessage(), e);
                        
                        // 에러가 발생해도 주기적으로 진행률 업데이트
                        if (updateStrategy.shouldUpdateProgressOnError(currentEntryNum)) {
                            int adjustedTotal = Math.max(totalEntryCount, currentEntryNum);
                            publishEntryProgress(sessionId, currentEntryNum, adjustedTotal, 
                                               entry.getDN(), fileName, stats);
                        }
                    }
                }

                int finalEntryCount = entryCounter.get();
                log.info("Parsing completed - totalEntryCount: {}, Actual: {}", totalEntryCount, finalEntryCount);

            } catch (Exception e) {
                log.error("LDIF processing error: {}", e.getMessage());
                stats.addError("LDIF processing error: " + e.getMessage());
                publishParsingError(sessionId, "LDIF 처리 오류: " + e.getMessage(), fileName);
                throw e;
            }

            int finalEntryCount = entryCounter.get();

            // 3단계: 분석 결과 생성
            publishParsingProgress(sessionId, 0.95, "분석 결과 생성 중...", fileName, finalEntryCount, finalEntryCount);
            
            LdifAnalysisResult result = buildAnalysisResult(stats, processedEntries);
            
            // 4단계: 완료
            publishParsingComplete(sessionId, fileName, finalEntryCount, stats);
            
            return result;

        } catch (Exception e) {
            publishParsingError(sessionId, "파싱 중 오류: " + e.getMessage(), fileName);
            throw e;
        } finally {
            // 파싱 세션 완료 (지연 정리로 변경)
            parsingProgressBroker.completeParsingSession(sessionId);
        }
    }

    /**
     * 기존 parseLdifFile 메소드 (호환성 유지)
     */
    public LdifAnalysisResult parseLdifFile(MultipartFile file) throws Exception {
        // 기본 세션 ID로 처리
        String defaultSessionId = "default_" + System.currentTimeMillis();
        return parseLdifFile(file, defaultSessionId);
    }

    /**
     * 파일 크기 기반 엔트리 수 추정 (메모리 효율적)
     */
    // private int estimateEntryCount(long fileSize) {
    //     // LDIF 파일의 평균 엔트리 크기를 기반으로 추정 (경험적 값)
    //     // PKD LDIF 파일의 경우 평균적으로 엔트리당 2-5KB 정도
    //     long avgEntrySize = 3000; // 3KB
        
    //     // 최소값과 최대값 설정
    //     int estimated = (int) Math.max(1, fileSize / avgEntrySize);
    //     return Math.min(estimated, 100000); // 최대 10만개로 제한
    // }

    /**
     * 진행률 업데이트 전략 결정
     */
    private ProgressUpdateStrategy determineUpdateStrategy(int totalEntryCount) {
        if (totalEntryCount <= SMALL_FILE_THRESHOLD) {
            // 작은 파일: 매 엔트리마다 업데이트
            return new ProgressUpdateStrategy(1, 1);
        } else if (totalEntryCount <= MEDIUM_FILE_THRESHOLD) {
            // 중간 파일: 5개 마다 업데이트
            return new ProgressUpdateStrategy(5, 3);
        } else {
            // 큰 파일: 50개 마다 업데이트
            return new ProgressUpdateStrategy(PROGRESS_UPDATE_BATCH_SIZE, 20);
        }
    }

    /**
     * 진행률 업데이트 전략 클래스
     */
    private static class ProgressUpdateStrategy {
        private final int normalInterval;
        private final int errorInterval;

        public ProgressUpdateStrategy(int normalInterval, int errorInterval) {
            this.normalInterval = normalInterval;
            this.errorInterval = errorInterval;
        }

        public boolean shouldUpdateProgress(int entryNumber) {
            return entryNumber == 1 || entryNumber % normalInterval == 0;
        }

        public boolean shouldUpdateProgressOnError(int entryNumber) {
            return entryNumber % errorInterval == 0;
        }
    }

    /**
     * Entry별 진행률 발행
     */
    private void publishEntryProgress(String sessionId, int processedEntries, int totalEntries,
                                    String currentDN, String fileName, AnalysisStatistics stats) {
        try {
            double progressValue = totalEntries > 0 ? (double) processedEntries / totalEntries : 0.0;
            progressValue = Math.min(0.94, progressValue); // 분석 결과 생성을 위해 최대 94%까지만
            
            // 진행률 상세 정보
            String progressMessage = String.format("Entry 처리 중: %s/%s (%.1f%%)", 
                formatNumber(processedEntries), formatNumber(totalEntries), progressValue * 100);
            
            // 현재까지의 통계 정보 (간략화)
            Map<String, Object> progressMetadata = createOptimizedProgressMetadata(
                sessionId, fileName, stats, currentDN, processedEntries);
            
            ParsingEvent event = new ParsingEvent(
                sessionId,
                progressValue,
                processedEntries,
                totalEntries,
                fileName,
                truncateString(currentDN, 100),
                progressMessage,
                stats.getErrors().size(),
                stats.getWarnings().size(),
                createCompactEntryTypeMap(stats.getEntryTypeCount()),
                progressMetadata
            );
            
            parsingProgressBroker.publishParsingProgress(sessionId, event);
            
            if (log.isDebugEnabled()) {
                log.debug("Entry progress published - Session: {}, Entry: {}/{}, Progress: {}%", 
                         sessionId, processedEntries, totalEntries, 
                         String.format("%.1f", progressValue * 100));
            }
            
        } catch (Exception e) {
            log.error("Failed to publish entry progress for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 최적화된 진행률 메타데이터 생성 (메모리 사용량 감소)
     */
    private Map<String, Object> createOptimizedProgressMetadata(String sessionId, String fileName, 
                                                              AnalysisStatistics stats, String currentDN,
                                                              int processedEntries) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put("fileName", fileName);
        metadata.put("currentDN", truncateString(currentDN, 80));
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("processedEntries", processedEntries);
        
        // 통계 정보는 주기적으로만 포함 (메모리 절약)
        if (processedEntries % 10 == 0 || processedEntries <= 5) {
            metadata.put("certificateStats", Map.of(
                "total", stats.getTotalCertificates(),
                "valid", stats.getValidCertificates(),
                "invalid", stats.getInvalidCertificates()
            ));
            metadata.put("entryTypeCount", createCompactEntryTypeMap(stats.getEntryTypeCount()));
        }
        
        return metadata;
    }

     /**
     * 압축된 엔트리 타입 맵 생성 (0이 아닌 값만 포함)
     */
    private Map<String, Integer> createCompactEntryTypeMap(Map<String, Integer> entryTypeCount) {
        Map<String, Integer> compact = new HashMap<>();
        entryTypeCount.entrySet().stream()
            .filter(entry -> entry.getValue() > 0)
            .forEach(entry -> compact.put(entry.getKey(), entry.getValue()));
        return compact;
    }

    /**
     * 숫자 포맷팅 유틸리티
     */
    private String formatNumber(int number) {
        if (number >= 1000) {
            return String.format("%.1fK", number / 1000.0);
        }
        return String.valueOf(number);
    }

    /**
     * 파싱 단계별 진행률 발행
     */
    private void publishParsingProgress(String sessionId, double progress, String message, 
                                      String fileName, int processed, int total) {
        try {
            ParsingEvent event = new ParsingEvent(
                sessionId,
                progress,
                processed,
                total,
                fileName,
                "",
                message,
                0,
                0,
                new HashMap<>(),
                Map.of("stage", "parsing", "timestamp", System.currentTimeMillis())
            );
            
            parsingProgressBroker.publishParsingProgress(sessionId, event);
            log.debug("Parsing stage progress - Session: {}, Progress: {}%, Message: {}", 
                     sessionId, (int)(progress * 100), message);
                     
        } catch (Exception e) {
            log.error("Failed to publish parsing progress for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 파싱 완료 이벤트 발행
     */
    private void publishParsingComplete(String sessionId, String fileName, int totalProcessed, 
                                      AnalysisStatistics stats) {
        try {
            String completionMessage = String.format("분석 완료: %s개 엔트리 처리됨 (오류: %d, 경고: %d)", 
                formatNumber(totalProcessed), stats.getErrors().size(), stats.getWarnings().size());
            
            Map<String, Object> completionMetadata = createFinalProgressMetadata(sessionId, fileName, stats);
            completionMetadata.put("completed", true);
            completionMetadata.put("finalStats", createFinalStatsMap(stats));
            
            ParsingEvent completionEvent = new ParsingEvent(
                sessionId,
                1.0,
                totalProcessed,
                totalProcessed,
                fileName,
                "",
                completionMessage,
                stats.getErrors().size(),
                stats.getWarnings().size(),
                createCompactEntryTypeMap(stats.getEntryTypeCount()),
                completionMetadata
            );
            
            parsingProgressBroker.publishParsingProgress(sessionId, completionEvent);
            log.info("Parsing completion event sent - Session: {}, Total: {}, Errors: {}, Warnings: {}", 
                    sessionId, totalProcessed, stats.getErrors().size(), stats.getWarnings().size());
                    
        } catch (Exception e) {
            log.error("Failed to publish parsing completion for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 파싱 오류 이벤트 발행
     */
    private void publishParsingError(String sessionId, String errorMessage, String fileName) {
        try {
            ParsingEvent errorEvent = new ParsingEvent(
                sessionId,
                0.0,
                0,
                0,
                fileName,
                "",
                errorMessage,
                1,
                0,
                new HashMap<>(),
                Map.of("error", true, "timestamp", System.currentTimeMillis())
            );
            
            parsingProgressBroker.publishParsingProgress(sessionId, errorEvent);
            log.error("Parsing error event sent - Session: {}, Error: {}", sessionId, errorMessage);
            
        } catch (Exception e) {
            log.error("Failed to publish parsing error for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 최종 진행률 메타데이터 생성
     */
    private Map<String, Object> createFinalProgressMetadata(String sessionId, String fileName, 
                                                          AnalysisStatistics stats) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put("fileName", fileName);
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("certificateStats", Map.of(
            "total", stats.getTotalCertificates(),
            "valid", stats.getValidCertificates(),
            "invalid", stats.getInvalidCertificates()
        ));
        metadata.put("masterListStats", Map.of(
            "total", stats.getTotalMasterLists(),
            "valid", stats.getValidMasterLists(),
            "invalid", stats.getInvalidMasterLists()
        ));
        metadata.put("entryTypeCount", createCompactEntryTypeMap(stats.getEntryTypeCount()));
        return metadata;
    }

    /**
     * 최종 통계 맵 생성
     */
    private Map<String, Object> createFinalStatsMap(AnalysisStatistics stats) {
        Map<String, Object> finalStats = new HashMap<>();
        finalStats.put("totalEntries", stats.getTotalEntries());
        finalStats.put("entryTypes", createCompactEntryTypeMap(stats.getEntryTypeCount()));
        finalStats.put("countries", new HashMap<>(stats.getCountryStats()));
        finalStats.put("certificates", Map.of(
            "total", stats.getTotalCertificates(),
            "valid", stats.getValidCertificates(),
            "invalid", stats.getInvalidCertificates()
        ));
        finalStats.put("masterLists", Map.of(
            "total", stats.getTotalMasterLists(),
            "valid", stats.getValidMasterLists(),
            "invalid", stats.getInvalidMasterLists()
        ));
        finalStats.put("crls", Map.of(
            "total", stats.getTotalCrls(),
            "valid", stats.getValidCrls(),
            "invalid", stats.getInvalidCrls()
        ));
        return finalStats;
    }

    /**
     * 문자열 자르기 유틸리티
     */
    private String truncateString(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str != null ? str : "";
        }
        return str.substring(0, maxLength) + "...";
    }

    /**
     * LDIF 파일에 포함된 모든 Entry 개수를 카운팅.
     */
    private int getTotalEntryCount(MultipartFile file) {
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
            
            // 메모리 절약을 위해 warnings는 제한된 개수만 유지
            List<String> wrapperWarnings = wrapper.getWarnings();
            if (!wrapperWarnings.isEmpty()) {
                int maxWarnings = 1000; // 최대 1000개 경고만 유지
                if (warnings.size() < maxWarnings) {
                    warnings.addAll(wrapperWarnings.stream()
                        .limit(maxWarnings - warnings.size())
                        .toList());
                }
            }
            
            // 처리 결과 통계 업데이트
            wrapper.getProcessingResults().forEach((attrName, results) -> {
                for (ProcessingResult result : results) {
                    ProcessingResult.ProcessingMetrics metrics = result.getMetrics();
                    String lowerAttrName = attrName.toLowerCase();
                    
                    if (lowerAttrName.contains("certificate")) {
                        totalCertificates += metrics.getProcessedItems();
                        validCertificates += metrics.getValidItems();
                        invalidCertificates += metrics.getInvalidItems();
                    } else if (lowerAttrName.contains("masterlist")) {
                        totalMasterLists += metrics.getProcessedItems();
                        validMasterLists += metrics.getValidItems();
                        invalidMasterLists += metrics.getInvalidItems();
                    } else if (lowerAttrName.contains("crl") || lowerAttrName.contains("revocation")) {
                        totalCrls += metrics.getProcessedItems();
                        validCrls += metrics.getValidItems();
                        invalidCrls += metrics.getInvalidItems();
                    }
                }
            });
        }

        public void addError(String error) {
            // 메모리 절약을 위해 에러도 제한된 개수만 유지
            int maxErrors = 1000;
            if (errors.size() < maxErrors) {
                errors.add(error);
            }
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
