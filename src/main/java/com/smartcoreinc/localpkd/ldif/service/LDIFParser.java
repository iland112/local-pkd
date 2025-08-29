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
    private final BinaryAttributeProcessor binaryAttributeProcessor;

    // 바이너리 속성 목록 (성능 최적화를 위해 Set 사용)
    private static final java.util.Set<String> BINARY_ATTRIBUTES = java.util.Set.of(
            "usercertificate", "cacertificate", "crosscertificatepair",
            "certificaterevocationlist", "authorityrevocationlist",
            "pkdmasterlistcontent", "pkddsccertificate");
 
    public LDIFParser(ProgressPublisher progressPublisher,
                      CertificateVerifier certificateVerifier,
                      BinaryAttributeProcessor binaryAttributeProcessor) {
        this.progressPublisher = progressPublisher;
        this.certificateVerifier = certificateVerifier;
        this.binaryAttributeProcessor = binaryAttributeProcessor;
    }

    /**
     * LDIF 파일 파싱 - Entry 단위 직접 처리
     */
    public LdifAnalysisResult parseLdifFile(MultipartFile file) throws IOException {
        log.info("Starting entry-based LDIF parsing for file: {}", file.getOriginalFilename());

        ParsingContext context = new ParsingContext();
        long fileSize = file.getSize();
        long processedBytes = 0;
        int entryCount = 0;
        
        try (InputStream inputStream = file.getInputStream();
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
             LDIFReader ldifReader = new LDIFReader(bufferedReader)) {

            Entry entry;
            while ((entry = ldifReader.readEntry()) != null) {
                entryCount++;
                
                // 진행률 계산 (대략적)
                processedBytes = estimateProcessedBytes(fileSize, entryCount);
                publishProgress(processedBytes, fileSize, entry.getDN());

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
            return;
        }
        
        // 2. 바이너리 속성 존재 여부 체크
        Map<String, List<byte[]>> binaryData = extractBinaryAttributes(entry);
        
        // 3. 바이너리 속성이 있는 경우에만 특별 처리
        BinaryAttributeProcessor.ProcessResult binaryResult = null;
        if (!binaryData.isEmpty()) {
            // Entry를 다시 LDIF 문자열로 변환하여 기존 BinaryAttributeProcessor 활용
            String entryLdifString = entry.toLDIFString();
            binaryResult = binaryAttributeProcessor.processBinaryAttributes(entryLdifString, entryNumber);
        } else {
            binaryResult = new BinaryAttributeProcessor.ProcessResult(); // 빈 결과
        }

        // 4. DTO 변환
        LdifEntryDto entryDto = convertToEntryDto(entry, entryType, binaryData);
        
        // 5. 컨텍스트 업데이트
        context.addEntry(entryDto);
        context.updateStatistics(binaryResult);
        
        // 6. 국가별 통계 업데이트
        String countryCode = extractCountryCodeFromDN(entry.getDN());
        if (!"UNKNOWN".equals(countryCode) && isBinaryEntry(entryType)) {
            context.addCountryStat(countryCode);
        }

        log.debug("Completed processing entry #{}: {}", entryNumber, entry.getDN());
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
                    binaryData.put(attribute.getName(), values); // 원본 대소문자 유지
                    log.debug("Extracted {} binary values for attribute '{}'", 
                        values.size(), attribute.getName());
                }
            }
        }
        
        return binaryData;
    }

    /**
     * 바이너리 속성 여부 확인
     */
    private boolean isBinaryAttribute(String attributeName) {
        return BINARY_ATTRIBUTES.stream()
                .anyMatch(binaryAttr -> attributeName.toLowerCase().contains(binaryAttr.toLowerCase()));
    }

    /**
     * Entry를 DTO로 변환 (개선된 버전)
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
     * 진행률 추정 (Entry 수 기반)
     */
    private long estimateProcessedBytes(long totalFileSize, int processedEntries) {
        // 대략적인 추정: 엔트리당 평균 크기를 기반으로 계산
        if (processedEntries < 10) return 0;
        
        long estimatedAvgEntrySize = totalFileSize / Math.max(processedEntries * 20, 1); // 추정치
        return Math.min(processedEntries * estimatedAvgEntrySize, totalFileSize);
    }

    /**
     * 진행상태 발행
     */
    private void publishProgress(long processedBytes, long totalBytes, String currentDN) {
        double progressRatio = totalBytes > 0 ? (double) processedBytes / totalBytes : 0.0;
        Progress progress = new Progress(progressRatio, "LDIF");
        ProgressEvent progressEvent = new ProgressEvent(progress, 
            (int) processedBytes, (int) totalBytes, "Processing: " + currentDN);
        progressPublisher.notifyProgressListeners(progressEvent);
    }

     /**
     * 분석 결과 생성 (기존 로직 재사용)
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

        // Trust Anchor 통계 - CertificateVerifier의 실제 메서드 사용
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
        log.info("=== PKD LDIF Parsing Results (Entry-based) ===");
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

        public void updateStatistics(BinaryAttributeProcessor.ProcessResult result) {
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
        public List<LdifEntryDto> getEntries() { return entries; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public Map<String, Integer> getObjectClassCount() { return objectClassCount; }
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
