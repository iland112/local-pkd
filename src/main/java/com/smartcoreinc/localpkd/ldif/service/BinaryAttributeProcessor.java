package com.smartcoreinc.localpkd.ldif.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.ldif.service.processing.BinaryAttributeProcessingStrategy;
import com.smartcoreinc.localpkd.ldif.service.processing.ProcessingContext;
import com.smartcoreinc.localpkd.ldif.service.processing.ProcessingResult;

import lombok.extern.slf4j.Slf4j;

/**
 * 바이너리 속성 처리 전담 클래스
 */
@Slf4j
@Component
public class BinaryAttributeProcessor {

    private final List<BinaryAttributeProcessingStrategy> strategies;

    public BinaryAttributeProcessor(List<BinaryAttributeProcessingStrategy> strategies) {
        // 우선순위에 따라 전략들을 정렬
        this.strategies = strategies.stream()
            .sorted(Comparator.comparingInt(BinaryAttributeProcessingStrategy::getPriority))
            .collect(Collectors.toList());
        
        log.info("Initialized BinaryAttributeProcessor with {} strategies", strategies.size());
    }

    /**
     * 바이너리 속성 처리 메인 메서드
     */
    public ProcessResult processBinaryAttributes(String recordText, int recordNumber) {
        ProcessResult result = new ProcessResult();
        String[] lines = recordText.split("\n");

        // DN에서 국가 코드 추출
        String countryCode = extractCountryCodeFromDN(recordText);
        ProcessingContext context = new ProcessingContext(recordNumber, countryCode);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (shouldSkipLine(line)) {
                continue;
            }

            if (line.contains("::")) {
                i = processBase64Attribute(lines, i, context, result);
            }
        }

        return result;
    }

    /**
     * Base64 인코딩된 속성 처리
     */
    private int processBase64Attribute(String[] lines, int startIndex, ProcessingContext context, ProcessResult result) {
        String line = lines[startIndex].trim();
        String[] parts = line.split("::", 2);
        if (parts.length != 2) {
            return startIndex;
        }

        String attrName = parts[0].trim();
        String base64Value = parts[1].trim();

        // 여러 줄에 걸친 Base64 데이터 처리
        StringBuilder fullBase64 = new StringBuilder(base64Value);
        int currentIndex = startIndex;

        // 다음 줄들이 공백으로 시작하면 연속된 데이터
        while (currentIndex + 1 < lines.length && lines[currentIndex + 1].startsWith(" ")) {
            currentIndex++;
            fullBase64.append(lines[currentIndex].substring(1)); // 앞의 공백 제거
        }

        // 바이너리 속성인지 확인하고 처리
        if (isBinaryAttribute(attrName)) {
            processBinaryAttributeValue(attrName, fullBase64.toString(), context, result);
        }

        return currentIndex;
    }

    /**
     * 바이너리 속성 값 처리 (전략 패턴 적용)
     */
    private void processBinaryAttributeValue(String attrName, String base64Value, ProcessingContext context, ProcessResult result) {
        try {
            byte[] binaryData = Base64.getDecoder().decode(base64Value);
            log.debug("Successfully parsed binary attribute '{}' with {} bytes", attrName, binaryData.length);

            // 기존 값들에 추가
            @SuppressWarnings("unchecked")
            List<byte[]> existingValues = (List<byte[]>) result.binaryAttributes.get(attrName);
            if (existingValues == null) {
                existingValues = new ArrayList<>();
                result.binaryAttributes.put(attrName, existingValues);
            }
            existingValues.add(binaryData);

            // 적절한 전략 찾기 및 처리
            BinaryAttributeProcessingStrategy strategy = findStrategy(attrName);
            if (strategy != null) {
                ProcessingResult processingResult = strategy.process(attrName, binaryData, context);
                updateResultFromProcessing(result, processingResult, attrName);
            } else {
                log.debug("No specific strategy found for attribute '{}', using default processing", attrName);
                result.addWarning(String.format("Record %d: No specific processor for attribute '%s'", 
                    context.getRecordNumber(), attrName));
            }

        } catch (IllegalArgumentException e) {
            String warning = String.format("Record %d: Failed to decode Base64 for attribute '%s': %s",
                    context.getRecordNumber(), attrName, e.getMessage());
            result.addWarning(warning);
            log.warn("Failed to decode Base64 for attribute '{}' in record {}: {}",
                    attrName, context.getRecordNumber(), e.getMessage());
        }
    }

    /**
     * 속성에 적합한 전략 찾기
     */
    private BinaryAttributeProcessingStrategy findStrategy(String attributeName) {
        return strategies.stream()
            .filter(strategy -> strategy.supports(attributeName))
            .findFirst()
            .orElse(null);
    }

    /**
     * 처리 결과를 메인 결과에 반영
     */
    private void updateResultFromProcessing(ProcessResult result, ProcessingResult processingResult, String attrName) {
        // 경고 추가
        result.addWarnings(processingResult.getWarnings());
        
        // 메트릭 업데이트
        ProcessingResult.ProcessingMetrics metrics = processingResult.getMetrics();
        
        if (isCertificateAttribute(attrName)) {
            result.totalCertificates += metrics.getProcessedItems();
            result.validCertificates += metrics.getValidItems();
            result.invalidCertificates += metrics.getInvalidItems();
        } else if (isMasterListAttribute(attrName)) {
            result.totalMasterLists += metrics.getProcessedItems();
            result.validMasterLists += metrics.getValidItems();
            result.invalidMasterLists += metrics.getInvalidItems();
        } else if (isCrlAttribute(attrName)) {
            result.totalCrls += metrics.getProcessedItems();
            result.validCrls += metrics.getValidItems();
            result.invalidCrls += metrics.getInvalidItems();
        }
        
        if (!processingResult.isSuccess()) {
            result.addWarning(String.format("Processing failed for attribute '%s': %s", 
                attrName, processingResult.getMessage()));
        }
    }

    /**
     * 라인을 스킵할지 확인
     */
    private boolean shouldSkipLine(String line) {
        return line.isEmpty() || line.startsWith("#") || line.startsWith("dn:");
    }

    /**
     * 바이너리 속성인지 확인
     */
    private boolean isBinaryAttribute(String attributeName) {
        return strategies.stream()
            .anyMatch(strategy -> strategy.supports(attributeName));
    }

    /**
     * 인증서 속성인지 확인
     */
    private boolean isCertificateAttribute(String attributeName) {
        String lowerName = attributeName.toLowerCase();
        return lowerName.contains("certificate") || 
               lowerName.contains("usercertificate") || 
               lowerName.contains("cacertificate");
    }

    /**
     * Master List 속성인지 확인
     */
    private boolean isMasterListAttribute(String attributeName) {
        return attributeName.contains("pkdMasterListContent") || 
               attributeName.toLowerCase().contains("masterlist");
    }

    /**
     * CRL 속성인지 확인
     */
    private boolean isCrlAttribute(String attributeName) {
        String lowerName = attributeName.toLowerCase();
        return lowerName.contains("certificaterevocationlist") || 
               lowerName.contains("authorityrevocationlist") ||
               lowerName.contains("crl");
    }

    /**
     * DN에서 국가 코드 추출
     */
    private String extractCountryCodeFromDN(String recordText) {
        if (recordText == null) return "UNKNOWN";

        String[] lines = recordText.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.toLowerCase().startsWith("dn:")) {
                // DN 값 추출 (dn: 이후 부분)
                StringBuilder dnBuilder = new StringBuilder();
                dnBuilder.append(line.substring(3).trim());

                // 다음 줄들이 공백으로 시작하면 연속된 DN 데이터
                while (i + 1 < lines.length && lines[i + 1].startsWith(" ")) {
                    i++;
                    dnBuilder.append(lines[i].substring(1)); // 앞의 공백 제거
                }

                String completeDN = dnBuilder.toString();
                return extractCountryFromDN(completeDN);
            }
        }
        return "UNKNOWN";
    }

    /**
     * DN 문자열에서 국가 코드 추출
     */
    private String extractCountryFromDN(String dn) {
        if (dn == null || dn.isEmpty()) return "UNKNOWN";

        // DN에서 국가 코드를 찾는 여러 패턴 시도
        // 1. 일반적인 c= 패턴
        String[] parts = dn.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.toLowerCase().startsWith("c=")) {
                String countryCode = part.substring(2).trim();
                // 이스케이프된 문자 처리 (예: C\=FR -> FR)
                countryCode = countryCode.replaceAll("\\\\(.)", "$1");
                return countryCode.toUpperCase();
            }
        }

        // 2. CN 내부의 C= 패턴 검사 (예: CN=CSCA-FRANCE\,O\=Gouv\,C\=FR의 경우)
        for (String part : parts) {
            part = part.trim();
            if (part.toLowerCase().startsWith("cn=")) {
                String cnValue = part.substring(3);
                // CN 값에서 \,C\= 패턴 찾기
                String[] cnParts = cnValue.split("\\\\,");
                for (String cnPart : cnParts) {
                    if (cnPart.toLowerCase().startsWith("c\\=")) {
                        String countryCode = cnPart.substring(3).trim();
                        countryCode = countryCode.replaceAll("\\\\(.)", "$1");
                        return countryCode.toUpperCase();
                    }
                }
            }
        }

        return "UNKNOWN";
    }

    /**
     * 바이너리 속성 처리 결과 (기존 호환성 유지)
     */
    public static class ProcessResult {
        private final Map<String, Object> binaryAttributes = new HashMap<>();
        private final List<String> warnings = new ArrayList<>();

        // 인증서 관련 통계
        private int totalCertificates = 0;
        private int validCertificates = 0;
        private int invalidCertificates = 0;
        
        // Master List 관련 통계
        private int totalMasterLists = 0;
        private int validMasterLists = 0;
        private int invalidMasterLists = 0;
        
        // CRL 관련 통계
        private int totalCrls = 0;
        private int validCrls = 0;
        private int invalidCrls = 0;

        // 국가별 통계
        private final Map<String, Integer> countryStats = new HashMap<>();

        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public void addWarnings(List<String> warnings) {
            this.warnings.addAll(warnings);
        }

        public void addCountryStat(String country, int count) {
            countryStats.merge(country, count, Integer::sum);
        }

        // Getters
        public Map<String, Object> getBinaryAttributes() { return binaryAttributes; }
        public List<String> getWarnings() { return warnings; }
        
        // 인증서 통계
        public int getTotalCertificates() { return totalCertificates; }
        public int getValidCertificates() { return validCertificates; }
        public int getInvalidCertificates() { return invalidCertificates; }
        
        // Master List 통계
        public int getTotalMasterLists() { return totalMasterLists; }
        public int getValidMasterLists() { return validMasterLists; }
        public int getInvalidMasterLists() { return invalidMasterLists; }
        
        // CRL 통계
        public int getTotalCrls() { return totalCrls; }
        public int getValidCrls() { return validCrls; }
        public int getInvalidCrls() { return invalidCrls; }
        
        // 국가별 통계
        public Map<String, Integer> getCountryStats() { return countryStats; }

        // 전체 PKD 객체 통계
        public int getTotalPkdObjects() {
            return totalCertificates + totalMasterLists + totalCrls;
        }

        public int getTotalValidPkdObjects() {
            return validCertificates + validMasterLists + validCrls;
        }

        public int getTotalInvalidPkdObjects() {
            return invalidCertificates + invalidMasterLists + invalidCrls;
        }

        public double getOverallValidityRate() {
            int total = getTotalPkdObjects();
            if (total == 0) return 0.0;
            return (double) getTotalValidPkdObjects() / total * 100.0;
        }

        // 디버깅용 정보 출력
        public String getStatsSummary() {
            return String.format(
                "PKD Processing Stats - Certificates: %d/%d valid, Master Lists: %d/%d valid, CRLs: %d/%d valid, Countries: %d",
                validCertificates, totalCertificates,
                validMasterLists, totalMasterLists,
                validCrls, totalCrls,
                countryStats.size()
            );
        }
    }
}