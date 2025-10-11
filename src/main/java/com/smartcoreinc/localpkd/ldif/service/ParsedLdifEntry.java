package com.smartcoreinc.localpkd.ldif.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.smartcoreinc.localpkd.enums.EntryType;
import com.smartcoreinc.localpkd.ldif.service.processing.ProcessingResult;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPException;

import lombok.extern.slf4j.Slf4j;

/**
 * LDIF Entry Wrapper - UnboundID Entry 객체를 감싸서 추가 기능 제공
 * 
 * 주요 기능:
 * - EntryType 및 CountryCode 캐싱
 * - 바이너리/텍스트 속성 직접 접근
 * - 처리 결과 집계
 * - Legacy DTO 변환 (필요시에만)
 */
@Slf4j
public class ParsedLdifEntry {

    // 바이너리 속성 목록 (상수로 분리)
    private static final Set<String> BINARY_ATTRIBUTES = Set.of(
        "usercertificate", "cacertificate", "crosscertificatepair",
        "certificaterevocationlist", "authorityrevocationlist",
        "pkdmasterlistcontent", "pkddsccertificate"
    );

    // 필수 필드
    private final Entry parsedEntry;
    private final int entryNumber;
    
    // 캐시된 계산 결과들 (Lazy Loading)
    private EntryType entryType;
    private String countryCode;
    private Map<String, List<ProcessingResult>> processingResults = new HashMap<>();
    private List<String> warnings = new ArrayList<>();

    // 통계 캐시
    private ProcessingStats cachedStats;

    public ParsedLdifEntry(Entry parsedEntry, int entryNumber) {
        this.parsedEntry = Objects.requireNonNull(parsedEntry, "Entry cannot be null");
        this.entryNumber = entryNumber;
    }

    /**
     * EntryType 결정 및 캐싱
     */
    public EntryType getEntryType() {
        if (entryType == null) {
            resolveEntryType();
        }
        return entryType;
    }
    
    public void resolveEntryType() {
        if (entryType == null) {
            try {
                entryType = EntryTypeResolver.resolveEntryType(parsedEntry);
                log.debug("Resolved EntryType: {} for DN: {}", entryType, parsedEntry.getDN());
            } catch (LDAPException e) {
                entryType = EntryType.UNKNOWN;
                log.debug("Cannot resolve EntryType for DN: {}, setting to UNKNOWN", parsedEntry.getDN());
            }
        }
    }

    /**
     * 국가 코드 추출 및 캐싱
     */
    public String getCountryCode() {
        if (countryCode == null) {
            resolveCountryCode();
        }
        return countryCode;
    }
    
    public void resolveCountryCode() {
        if (countryCode == null) {
            countryCode = extractCountryCodeFromDN(parsedEntry.getDN());
            log.debug("Resolved CountryCode: {} for DN: {}", countryCode, parsedEntry.getDN());
        }
    }

    /**
     * 처리 결과 추가
     */
    public void addProcessingResult(String attributeName, ProcessingResult result) {
        processingResults.computeIfAbsent(attributeName, k -> new ArrayList<>()).add(result);
        // 통계 캐시 무효화
        cachedStats = null;
    }

    /**
     * 경고 추가
     */
    public void addWarning(String warning) {
        warnings.add(warning);
        log.debug("Added warning for entry {}: {}", entryNumber, warning);
    }

    /**
     * 여러 경고 추가
     */
    public void addWarnings(List<String> warnings) {
        this.warnings.addAll(warnings);
    }

    // =================================
    // 바이너리 속성 직접 접근 메서드들
    // =================================

    /**
     * 바이너리 속성 값들 조회
     */
    public List<byte[]> getBinaryAttributeValues(String attributeName) {
        Attribute attr = parsedEntry.getAttribute(attributeName);
        if (attr != null && isBinaryAttribute(attributeName)) {
            return Arrays.asList(attr.getValueByteArrays());
        }
        return List.of();
    }

    /**
     * 첫 번째 바이너리 속성 값 조회
     */
    public byte[] getFirstBinaryAttributeValue(String attributeName) {
        List<byte[]> values = getBinaryAttributeValues(attributeName);
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * 바이너리 속성 존재 여부 확인
     */
    public boolean hasBinaryAttribute(String attributeName) {
        return parsedEntry.hasAttribute(attributeName) && isBinaryAttribute(attributeName);
    }

    /**
     * 바이너리 속성 값 개수 조회
     */
    public int getBinaryAttributeValueCount(String attributeName) {
        if (!hasBinaryAttribute(attributeName)) {
            return 0;
        }
        Attribute attr = parsedEntry.getAttribute(attributeName);
        return attr != null ? attr.getValueByteArrays().length : 0;
    }

    /**
     * 모든 바이너리 속성명 조회
     */
    public Set<String> getBinaryAttributeNames() {
        return parsedEntry.getAttributes().stream()
            .map(Attribute::getName)
            .filter(this::isBinaryAttribute)
            .collect(java.util.stream.Collectors.toSet());
    }

    // =================================
    // 텍스트 속성 직접 접근 메서드들
    // =================================

    /**
     * 텍스트 속성 값들 조회
     */
    public List<String> getTextAttributeValues(String attributeName) {
        Attribute attr = parsedEntry.getAttribute(attributeName);
        if (attr != null && !isBinaryAttribute(attributeName)) {
            return Arrays.asList(attr.getValues());
        }
        return List.of();
    }

    /**
     * 첫 번째 텍스트 속성 값 조회
     */
    public String getFirstTextAttributeValue(String attributeName) {
        List<String> values = getTextAttributeValues(attributeName);
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * 텍스트 속성 존재 여부 확인
     */
    public boolean hasTextAttribute(String attributeName) {
        return parsedEntry.hasAttribute(attributeName) && !isBinaryAttribute(attributeName);
    }

    /**
     * 모든 텍스트 속성명 조회
     */
    public Set<String> getTextAttributeNames() {
        return parsedEntry.getAttributes().stream()
            .map(Attribute::getName)
            .filter(name -> !isBinaryAttribute(name))
            .collect(java.util.stream.Collectors.toSet());
    }

    // =================================
    // 통계 및 집계 메서드들
    // =================================

    /**
     * 처리 통계 계산 (캐싱)
     */
    public ProcessingStats getProcessingStats() {
        if (cachedStats == null) {
            cachedStats = calculateProcessingStats();
        }
        return cachedStats;
    }

    private ProcessingStats calculateProcessingStats() {
        ProcessingStats stats = new ProcessingStats();
        
        processingResults.values().stream()
            .flatMap(List::stream)
            .forEach(result -> {
                ProcessingResult.ProcessingMetrics metrics = result.getMetrics();
                stats.totalProcessed += metrics.getProcessedItems();
                stats.totalValid += metrics.getValidItems();
                stats.totalInvalid += metrics.getInvalidItems();
                
                // 경고 수집
                stats.warnings.addAll(result.getWarnings());
            });
        
        // 직접 추가된 경고도 포함
        stats.warnings.addAll(this.warnings);
        
        return stats;
    }

    /**
     * 특정 속성의 처리 통계 조회
     */
    public ProcessingStats getAttributeProcessingStats(String attributeName) {
        ProcessingStats stats = new ProcessingStats();
        
        List<ProcessingResult> results = processingResults.get(attributeName);
        if (results != null) {
            results.forEach(result -> {
                ProcessingResult.ProcessingMetrics metrics = result.getMetrics();
                stats.totalProcessed += metrics.getProcessedItems();
                stats.totalValid += metrics.getValidItems();
                stats.totalInvalid += metrics.getInvalidItems();
                stats.warnings.addAll(result.getWarnings());
            });
        }
        
        return stats;
    }

    /**
     * 성공한 처리 결과만 조회
     */
    public Map<String, List<ProcessingResult>> getSuccessfulProcessingResults() {
        Map<String, List<ProcessingResult>> successful = new HashMap<>();
        
        processingResults.forEach((attributeName, results) -> {
            List<ProcessingResult> successfulResults = results.stream()
                .filter(ProcessingResult::isSuccess)
                .toList();
            if (!successfulResults.isEmpty()) {
                successful.put(attributeName, successfulResults);
            }
        });
        
        return successful;
    }

    /**
     * 실패한 처리 결과만 조회
     */
    public Map<String, List<ProcessingResult>> getFailedProcessingResults() {
        Map<String, List<ProcessingResult>> failed = new HashMap<>();
        
        processingResults.forEach((attributeName, results) -> {
            List<ProcessingResult> failedResults = results.stream()
                .filter(result -> !result.isSuccess())
                .toList();
            if (!failedResults.isEmpty()) {
                failed.put(attributeName, failedResults);
            }
        });
        
        return failed;
    }

    // =================================
    // Legacy 지원 메서드들
    // =================================

    /**
     * Legacy DTO 변환 (필요시에만)
     */
    public com.smartcoreinc.localpkd.ldif.dto.LdifEntryDto toLegacyDto() {
        Map<String, List<String>> attributes = new HashMap<>();
        
        // 모든 속성을 Legacy 형태로 변환
        for (Attribute attribute : parsedEntry.getAttributes()) {
            String attrName = attribute.getName();
            
            if (isBinaryAttribute(attrName)) {
                // 바이너리 속성은 Base64 인코딩
                List<String> base64Values = new ArrayList<>();
                for (byte[] binaryValue : attribute.getValueByteArrays()) {
                    base64Values.add(Base64.getEncoder().encodeToString(binaryValue));
                }
                attributes.put(attrName, base64Values);
            } else {
                // 텍스트 속성은 그대로
                attributes.put(attrName, Arrays.asList(attribute.getValues()));
            }
        }
        
        return new com.smartcoreinc.localpkd.ldif.dto.LdifEntryDto(
            parsedEntry.getDN(), getEntryType(), attributes, parsedEntry.toLDIFString());
    }

    /**
     * Base64로 인코딩된 바이너리 속성 맵 반환 (Legacy 지원)
     */
    public Map<String, List<String>> getBinaryAttributesAsBase64() {
        Map<String, List<String>> base64Map = new HashMap<>();
        
        for (String attrName : getBinaryAttributeNames()) {
            List<byte[]> binaryValues = getBinaryAttributeValues(attrName);
            List<String> base64Values = binaryValues.stream()
                .map(bytes -> Base64.getEncoder().encodeToString(bytes))
                .toList();
            base64Map.put(attrName, base64Values);
        }
        
        return base64Map;
    }

    // =================================
    // 유틸리티 메서드들
    // =================================

    /**
     * 바이너리 속성 여부 확인
     */
    private boolean isBinaryAttribute(String attributeName) {
        String lowerName = attributeName.toLowerCase();
        return BINARY_ATTRIBUTES.stream()
            .anyMatch(binaryAttr -> lowerName.contains(binaryAttr));
    }

    /**
     * DN에서 국가 코드 추출
     */
    private String extractCountryCodeFromDN(String dn) {
        if (dn == null || dn.isEmpty()) {
            return "UNKNOWN";
        }

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

    /**
     * Entry가 바이너리 데이터를 포함하는지 확인
     */
    public boolean hasBinaryData() {
        return parsedEntry.getAttributes().stream()
            .anyMatch(attr -> isBinaryAttribute(attr.getName()));
    }

    /**
     * Entry 요약 정보 생성
     */
    public String getSummary() {
        return String.format("Entry[%d]: %s (Type: %s, Country: %s, Attributes: %d, BinaryAttrs: %d, Warnings: %d)",
            entryNumber, parsedEntry.getDN(), getEntryType(), getCountryCode(),
            parsedEntry.getAttributes().size(), getBinaryAttributeNames().size(), warnings.size());
    }

    // =================================
    // Getter 메서드들
    // =================================

    public Entry getParsedEntry() { 
        return parsedEntry; 
    }
    
    public int getEntryNumber() { 
        return entryNumber; 
    }
    
    public String getDN() { 
        return parsedEntry.getDN(); 
    }
    
    public Map<String, List<ProcessingResult>> getProcessingResults() { 
        return Collections.unmodifiableMap(processingResults); 
    }
    
    public List<String> getWarnings() { 
        return Collections.unmodifiableList(warnings); 
    }
    
    public Collection<Attribute> getAllAttributes() {
        return parsedEntry.getAttributes();
    }

    public int getAttributeCount() {
        return parsedEntry.getAttributes().size();
    }

    public boolean hasProcessingResults() {
        return !processingResults.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    @Override
    public String toString() {
        return getSummary();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ParsedLdifEntry that = (ParsedLdifEntry) obj;
        return entryNumber == that.entryNumber && 
               Objects.equals(parsedEntry.getDN(), that.parsedEntry.getDN());
    }

    @Override
    public int hashCode() {
        return Objects.hash(entryNumber, parsedEntry.getDN());
    }

    /**
     * 처리 통계 클래스
     */
    public static class ProcessingStats {
        public int totalProcessed = 0;
        public int totalValid = 0;
        public int totalInvalid = 0;
        public final List<String> warnings = new ArrayList<>();

        public double getValidityRate() {
            return totalProcessed > 0 ? (double) totalValid / totalProcessed * 100.0 : 0.0;
        }

        public boolean hasErrors() {
            return totalInvalid > 0;
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        @Override
        public String toString() {
            return String.format("ProcessingStats[Processed: %d, Valid: %d, Invalid: %d, ValidityRate: %.2f%%, Warnings: %d]",
                totalProcessed, totalValid, totalInvalid, getValidityRate(), warnings.size());
        }
    }
}
