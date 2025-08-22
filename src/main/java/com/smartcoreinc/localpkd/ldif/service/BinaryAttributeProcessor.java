package com.smartcoreinc.localpkd.ldif.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * 바이너리 속성 처리 전담 클래스
 */
@Slf4j
public class BinaryAttributeProcessor {

    private static final List<String> BINARY_ATTRIBUTES = Arrays.asList(
            "userCertificate", "caCertificate", "crossCertificatePair",
            "certificateRevocationList", "authorityRevocationList", "pkdMasterListContent");

    private final CertificateVerifier certificateVerifier;

    public BinaryAttributeProcessor(CertificateVerifier certificateVerifier) {
        this.certificateVerifier = certificateVerifier;
    }

    /**
     * 바이너리 속성 처리 메인 메서드
     */
    public ProcessResult processBinaryAttributes(String recordText, int recordNumber) {
        ProcessResult result = new ProcessResult();
        String[] lines = recordText.split("\n");

        // DN에서 국가 코드 추출
        String countryCode = extractCountryCodeFromDN(recordText);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (shouldSkipLine(line)) {
                continue;
            }

            if (line.contains("::")) {
                i = processBase64Attribute(lines, i, recordNumber, countryCode, result);
            }
        }

        return result;
    }

    /**
     * Base64 인코딩된 속성 처리
     */
    private int processBase64Attribute(String[] lines, int startIndex, int recordNumber, String countryCode,
            ProcessResult result) {
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
            processBinaryAttributeValue(attrName, fullBase64.toString(), recordNumber, countryCode, result);
        }

        return currentIndex;
    }

    /**
     * 바이너리 속성 값 처리
     */
    private void processBinaryAttributeValue(String attrName, String base64Value, int recordNumber, String countryCode,
            ProcessResult result) {
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

            // 속성 타입에 따른 검증 수행
            if (isCertificateAttribute(attrName)) {
                processCertificateAttribute(binaryData, recordNumber, result);
            } else if (isMasterListAttribute(attrName)) {
                processMasterListAttribute(binaryData, recordNumber, countryCode, result);
            }

        } catch (IllegalArgumentException e) {
            String warning = String.format("Record %d: Failed to decode Base64 for attribute '%s': %s",
                    recordNumber, attrName, e.getMessage());
            result.addWarning(warning);
            log.warn("Failed to decode Base64 for attribute '{}' in record {}: {}",
                    attrName, recordNumber, e.getMessage());
        }
    }

    /**
     * 인증서 속성 처리
     */
    private void processCertificateAttribute(byte[] certData, int recordNumber, ProcessResult result) {
        result.totalCertificates++;

        CertificateValidationResult validationResult = certificateVerifier.validateX509Certificate(certData,
                recordNumber);
        if (validationResult.isValid()) {
            result.validCertificates++;
            log.debug("Valid X.509 certificate found in record {}: {}", recordNumber, validationResult.getDetails());
        } else {
            result.invalidCertificates++;
            String warning = String.format("Record %d: Invalid X.509 certificate - %s",
                    recordNumber, validationResult.getErrorMessage());
            result.addWarning(warning);
            log.warn("Invalid X.509 certificate in record {}: {}", recordNumber, validationResult.getErrorMessage());
        }
    }

    /**
     * Master List 속성 처리
     */
    private void processMasterListAttribute(byte[] masterListData, int recordNumber, String countryCode,
            ProcessResult result) {
        result.totalMasterLists++;

        MasterListValidationResult masterListResult = certificateVerifier.validateAndExtractMasterList(masterListData,
                recordNumber, countryCode);

        if (masterListResult.isValid()) {
            result.validMasterLists++;
            log.info("Valid Master List found in record {} for country {}: {} CSCA certificates extracted",
                    recordNumber, countryCode, masterListResult.getCscaCertificates().size());
        } else {
            result.invalidMasterLists++;
            String warning = String.format("Record %d: Invalid Master List for country %s - %s",
                    recordNumber, countryCode, masterListResult.getErrorMessage());
            result.addWarning(warning);
            log.warn("Invalid Master List in record {} for country {}: {}",
                    recordNumber, countryCode, masterListResult.getErrorMessage());
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
        return BINARY_ATTRIBUTES.stream()
                .anyMatch(binaryAttr -> attributeName.toLowerCase().contains(binaryAttr.toLowerCase()));
    }

    /**
     * 인증서 속성인지 확인
     */
    private boolean isCertificateAttribute(String attributeName) {
        return attributeName.toLowerCase().contains("certificate");
    }

    /**
     * Master List 속성인지 확인
     */
    private boolean isMasterListAttribute(String attributeName) {
        return attributeName.contains("pkdMasterListContent");
    }

    /**
     * DN에서 국가 코드 추출
     */
    private String extractCountryCodeFromDN(String recordText) {
        if (recordText == null)
            return "UNKNOWN";

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
        if (dn == null || dn.isEmpty())
            return "UNKNOWN";

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
     * 바이너리 속성 처리 결과
     */
    public static class ProcessResult {
        private final Map<String, Object> binaryAttributes = new HashMap<>();
        private final List<String> warnings = new ArrayList<>();

        private int totalCertificates = 0;
        private int validCertificates = 0;
        private int invalidCertificates = 0;
        private int totalMasterLists = 0;
        private int validMasterLists = 0;
        private int invalidMasterLists = 0;

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        // Getters
        public Map<String, Object> getBinaryAttributes() {
            return binaryAttributes;
        }

        public List<String> getWarnings() {
            return warnings;
        }

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
    }
}