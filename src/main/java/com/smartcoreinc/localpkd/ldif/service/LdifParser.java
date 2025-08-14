package com.smartcoreinc.localpkd.ldif.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisResult;
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

    private static final List<String> BINARY_ATTRIBUTES = Arrays.asList(
        "userCertificate", "caCertificate", "crossCertificatePair", 
        "certificateRevocationList", "authorityRevocationList"
    );

    // 인증서 검증을 위한 CertificateFactory
    private static final CertificateFactory CERTIFICATE_FACTORY;
    
    static {
        try {
            CERTIFICATE_FACTORY = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new RuntimeException("Failed to initialize X.509 CertificateFactory", e);
        }
    }

    // SSE dependency
    private final ProgressPublisher progressPublisher;

    public LdifParser(ProgressPublisher progressPublisher) {
        this.progressPublisher = progressPublisher;
    }

    public LdifAnalysisResult parseLdifFile(MultipartFile file) throws IOException {
        log.info("Starting line-tracking LDIF parsing for file: {}", file.getOriginalFilename());
        
        LdifAnalysisResult result = new LdifAnalysisResult();
        List<LdifEntryDto> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> objectClassCount = new HashMap<>();
        Map<String, Integer> certificateValidationStats = new HashMap<>();

        // 데이터 처리 개수 로컬 변수들
        int addCount = 0, modifyCount = 0, deleteCount = 0;
        int totalCertificates = 0, validCertificates = 0, invalidCertificates = 0;

        int totalLines = countLines(file);
        
        // 라인 번호 추적을 위한 사전 처리
        try (InputStream inputStream = file.getInputStream();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder currentRecord = new StringBuilder();
            int processedLines = 0;
            int lineNumber = 0;
            int recordNumber = 0;
            String line;
            boolean inRecord = false;

            while ((line = bufferedReader.readLine()) != null) {
                // SSE로 진행 상대 전송.
                processedLines++;
                Progress progress = new Progress(processedLines / (double) totalLines, "LDIF");
                ProgressEvent progressEvent = new ProgressEvent(progress, processedLines, totalLines, line);
                progressPublisher.notifyProgressListeners(progressEvent);
                
                // 라인 분석 및 LDAP Entry 레코드 생성
                lineNumber++;
                // 빈 라인이나 주석 라인 처리
                if (line.trim().isEmpty()) {
                    if (inRecord && currentRecord.length() > 0) {
                        // 현재 레코드 완료, 파싱 시도
                        recordNumber++;
                        try {
                            log.debug("Number of th current record: {} ", recordNumber);
                            ParseResult parseResult = parseRecord(currentRecord.toString(), recordNumber, lineNumber);
                            if (parseResult.entry != null) {
                                entries.add(parseResult.entry);
                                addCount++;
                                countObjectClasses(parseResult.entry, objectClassCount);

                                // 인증서 검증 통계 업데이트
                                totalCertificates += parseResult.totalCertificates;
                                validCertificates += parseResult.validCertificates;
                                invalidCertificates += parseResult.invalidCertificates;

                                if (!parseResult.warnings.isEmpty()) {
                                    warnings.addAll(parseResult.warnings);
                                }
                            }
                        } catch (Exception e) {
                            errors.add(String.format("Record %d (around line %d): %s", recordNumber, lineNumber, e.getMessage()));
                            log.error("Error parsing record {}: {}", recordNumber, e.getMessage(), e);
                        }
                        currentRecord.setLength(0);
                        inRecord = false;
                    }
                    continue;
                }

                // 주석 라인 스킵
                if (line.startsWith("#")) {
                    continue;
                }

                // 새로운 DN으로 시작하는 레코드
                if (line.startsWith("dn:") || line.startsWith("dn::")) {
                    if (inRecord && currentRecord.length() > 0) {
                        // 이전 레코드 처리
                        recordNumber++;
                        try {
                            ParseResult parseResult = parseRecord(currentRecord.toString(), recordNumber, lineNumber - 1);
                            if (parseResult.entry != null) {
                                entries.add(parseResult.entry);
                                addCount++;
                                countObjectClasses(parseResult.entry, objectClassCount);

                                // 인증서 검증 통계 업데이트
                                totalCertificates += parseResult.totalCertificates;
                                validCertificates += parseResult.validCertificates;
                                invalidCertificates += parseResult.invalidCertificates;
                                
                                if (!parseResult.warnings.isEmpty()) {
                                    warnings.addAll(parseResult.warnings);
                                }
                            }
                        } catch (Exception e) {
                            errors.add(String.format("Record %d (around line %d): %s", recordNumber, lineNumber - 1, e.getMessage()));
                            log.error("Error parsing record {}: {}", recordNumber, e.getMessage(), e);
                        }
                    }
                    // 새로운 레코드 시작
                    currentRecord.setLength(0);
                    currentRecord.append(line).append("\n");
                    inRecord = true;
                } else if (inRecord) {
                    // 현재 레코드에 라인 추가
                    currentRecord.append(line).append("\n");
                }
            }

            // 마지막 래코드 처리
            if (inRecord && currentRecord.length() > 0) {
                recordNumber++;
                try {
                    ParseResult parseResult = parseRecord(currentRecord.toString(), recordNumber, lineNumber);
                    if (parseResult.entry != null) {
                        entries.add(parseResult.entry);
                        addCount++;
                        countObjectClasses(parseResult.entry, objectClassCount);
                        
                        // 인증서 검증 통계 업데이트
                        totalCertificates += parseResult.totalCertificates;
                        validCertificates += parseResult.validCertificates;
                        invalidCertificates += parseResult.invalidCertificates;
                        
                        if (!parseResult.warnings.isEmpty()) {
                            warnings.addAll(parseResult.warnings);
                        }
                    }
                } catch (Exception e) {
                    errors.add(String.format("Record %d (around line %d): %s", recordNumber, lineNumber, e.getMessage()));
                    log.error("Error parsing record {}: {}", recordNumber, e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            log.error("IO Error reading LDIF file: {}", e.getMessage());
            errors.add("File reading error: " + e.getMessage());
        }

        // 인증서 검증 통계 설정
        if (totalCertificates > 0) {
            certificateValidationStats.put("total", totalCertificates);
            certificateValidationStats.put("valid", validCertificates);
            certificateValidationStats.put("invalid", invalidCertificates);
        }

        // 결과 설정
        result.setEntries(entries);
        result.setErrors(errors);
        result.setWarnings(warnings);
        result.setTotalEntries(entries.size());
        result.setAddEntries(addCount);
        result.setModifyEntries(modifyCount);
        result.setDeleteEntries(deleteCount);
        result.setObjectClassCount(objectClassCount);
        result.setCertificateValidationStats(certificateValidationStats);
        result.setHasValidationErrors(!errors.isEmpty());

        log.info("LDIF parsing completed. Entries: {}, Errors: {}, Warnings: {}, Certificates: {} (Valid: {}, Invalid: {})", 
                entries.size(), errors.size(), warnings.size(), totalCertificates, validCertificates, invalidCertificates);
        return result;
    }

    private ParseResult parseRecord(String recordText, int recordNumber, int lineNumber) throws LDIFException, IOException {
        try {
            // 커스텀 파싱을 먼저 수행하여 바이너리 속성 처라
            CustomParseResult customParseResult = customParseRecord(recordText, recordNumber);

            try (StringReader stringReader = new StringReader(recordText);
                LDIFReader ldifReader = new LDIFReader(new BufferedReader(stringReader))) {
                    Entry entry = ldifReader.readEntry();
                    log.debug("entry dn: {}", entry.getDN());
                    if (entry != null) {
                        LdifEntryDto entryDto = convertEntryDto(entry, "ADD", customParseResult.binaryAttributes);
                        return new ParseResult(
                            entryDto,
                            customParseResult.warnings,
                            customParseResult.totalCertificates,
                            customParseResult.validCertificates,
                            customParseResult.invalidCertificates
                        );
                    } else {
                        return new ParseResult(null, customParseResult.warnings, 0, 0, 0);
                    }
            }
        } catch (LDIFException e) {
            throw new LDIFException(
                "LDIF parsing error in record " + recordNumber + ": " + e.getMessage(),
                lineNumber,
                true,
                e
            );
        }
    }

    /**
     * 바이너리 속성을 올바르게 처리하기 위한 커스텀 파싱
     * @param String recordText
     * @param int recordNumber
     * @return CustomParseResult
     */
    private CustomParseResult customParseRecord(String recordText, int recordNumber) {
        Map<String, Object> binaryAttributes = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        String[] lines = recordText.split("\n");

        int totalCertificates = 0;
        int validCertificates = 0;
        int invalidCertificates = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("dn:")) {
                continue;
            }

            // Base64 인코딩된 바이너리 속성 찾기 (:: 표기)
            if (line.contains("::")) {
                String[] parts = line.split("::", 2);
                if (parts.length == 2) {
                    String attrName = parts[0].trim();
                    String base64Value = parts[1].trim();

                    // ;binary 접미사 제거
                    // if (attrName.endsWith(";binary")) {
                    //     attrName = attrName.replace(";binary", "");
                    // }

                    // 여러 줄에 걸친 Base64 데이터 처리
                    StringBuilder fullBase64 = new StringBuilder(base64Value);

                    // 다음 줄들이 공백으로 시작하면 연속된 데이터
                    while (i + 1 < lines.length && lines[i + 1].startsWith(" ")) {
                        i++;
                        fullBase64.append(lines[i].substring(1)); // 앞의 공백 제거
                    }

                    // 바아너리 속성인지 확인
                    if (isBinaryAttribute(attrName)) {
                        try {
                            byte[] binaryData = Base64.getDecoder().decode(fullBase64.toString());

                            @SuppressWarnings("unchecked")
                            List<byte[]> existingValues = (List<byte[]>) binaryAttributes.get(attrName);
                            if (existingValues == null) {
                                existingValues = new ArrayList<>();
                                binaryAttributes.put(attrName, existingValues);
                            }
                            existingValues.add(binaryData);

                            if (attrName.toLowerCase().contains("certificate")) {
                                totalCertificates++;
                                CertificateValidationResult validationResult = validateX509Cerificate(binaryData, recordNumber);
                                if (validationResult.isValid) {
                                    validCertificates++;
                                    log.debug("Valid X.509 certificate found in record {}: {}", recordNumber, validationResult.details);
                                } else {
                                    invalidCertificates++;
                                    warnings.add(String.format("Record %d: Invalid X.509 certificate - %s", recordNumber, validationResult.errorMessage));
                                    log.warn("Invalid X.509 certificate in record {}: {}", recordNumber, validationResult.errorMessage);
                                }
                            }

                            log.debug("Successfully parsed binary attribute '{}' with {} bytes", attrName, binaryData.length);
                        } catch (IllegalArgumentException e) {
                            warnings.add(String.format("Record %d: Failed to decode Base64 for attribute '%s': %s", recordNumber, attrName, e.getMessage()));
                            log.warn("Failed to decode Base64 for attribute '{}' in record {}: {}", attrName, recordNumber, e.getMessage());
                        }
                    }
                }
            }
        }

        return new CustomParseResult(binaryAttributes, warnings, totalCertificates, validCertificates, invalidCertificates);
    }

    /**
     * X.509 인증서 검증
     */
    private CertificateValidationResult validateX509Cerificate(byte[] certBytes, int recordNumber) {
        try {
            // 기본적인 DER 인코딩 검증 (0x30으로 시작해야 함)
            if (certBytes.length < 10) {
                return new CertificateValidationResult(false, "Certificate data too short", null);
            }

            if (certBytes[0] != 0x30) {
                return new CertificateValidationResult(false, "Invalide DER encoding - does not start with 0x30", null);
            }

            // X.509 인증서 파싱 시도
            try (ByteArrayInputStream bis = new ByteArrayInputStream(certBytes)) {
                X509Certificate certificate = (X509Certificate) CERTIFICATE_FACTORY.generateCertificate(bis);

                // 인증서 기본 정보 추출
                String subject = certificate.getSubjectX500Principal().getName();
                String issuer = certificate.getIssuerX500Principal().getName();
                String serialNumber = certificate.getSerialNumber().toString();

                // 유효 기간 검증
                try {
                    certificate.checkValidity();
                    String details = String.format("Subject: %s, Issuer: %s, Serial: %s", subject, issuer, serialNumber);
                    return new CertificateValidationResult(true, "Valid certificate", details);
                } catch (Exception validityException) {
                    String details = String.format("Subject: %s, Issuer: %s, Serial: %s, Validity Error: %s", 
                                                  subject, issuer, serialNumber, validityException.getMessage());
                    return new CertificateValidationResult(false, "Certificate validity check failed", details);
                }
            }
        } catch (CertificateException e) {
            return new CertificateValidationResult(false, "Cerficiate parsing failed: " + e.getMessage(), null);
        } catch (Exception e) {
            return new CertificateValidationResult(false, "Unexpected error during validation: " + e.getMessage(), null);
        }
    }

    /**
     * 바이너리 속성 여부 확인
     * @param String attributeName
     * @return boolean
     */
    private boolean isBinaryAttribute(String attributeName) {
        return BINARY_ATTRIBUTES.stream()
            .anyMatch(binaryAttr -> attributeName.toLowerCase().contains(binaryAttr.toLowerCase()) || attributeName.contains(";binary"));
    }

    private LdifEntryDto convertEntryDto(Entry entry, String entryType, Map<String, Object> customParsedAttributes) {
        Map<String, List<String>> attributes = new HashMap<>();

        for (Attribute attribute : entry.getAttributes()) {
            String attributeName = attribute.getName();

            // ;binary 접미사 정규화
            // String normalizedAttrName = attributeName.replace(";binary", "");

            // 바이너리 속성인 경우 커스텀 파싱 결과 사용
            if (isBinaryAttribute(attributeName)) {
                @SuppressWarnings("unchecked")
                List<byte[]> binaryValues = (List<byte[]>) customParsedAttributes.get(attributeName);
                List<String> base64Values = new ArrayList<>();
                
                for (byte[] binaryValue : binaryValues) {
                    // Base64로 다시 인코딩하여 저장 (LDAP 저장용)
                    base64Values.add(Base64.getEncoder().encodeToString(binaryValue));
                }
                
                attributes.put(attributeName, base64Values);
                log.debug("Processed binary attribute '{}' with {} values", attributeName, base64Values.size());
            } else {
                // 일반 속성 처리
                String[] values = attribute.getValues();
                attributes.put(attributeName, Arrays.asList(values));
            }
        }

        return new LdifEntryDto(
            entry.getDN(),
            entryType,
            attributes,
            entry.toLDIFString()
        );
    }

    private void countObjectClasses(LdifEntryDto entry, Map<String, Integer> objectClassCount) {
        List<String> objectClasses = entry.getAttributes().get("objectClass");
        if (objectClasses != null) {
            for (String objectClass : objectClasses) {
                objectClassCount.merge(objectClass, 1, Integer::sum);
            }
        }
    }

    /**
     * 더 엄격한 LDIF 검증
     * @param content
     * @return
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
                    // DN 형식 검증
                    String dn = line.substring(line.indexOf(':') + 1).trim();
                    if (dn.isEmpty()) {
                        log.warn("Empty DN at line {}", lineNumber);
                        return false;
                    }
                } else if (line.contains(":")) {
                    // 속성:값 형식 검증
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

    private int countLines(MultipartFile file) throws IOException {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            while (reader.readLine() != null) {
                count++;
            }
        }
        return count;
    }

    // 내부 클래스들
    private static class ParseResult {
        final LdifEntryDto entry;
        final List<String> warnings;
        final int totalCertificates;
        final int validCertificates;
        final int invalidCertificates;

        ParseResult(
            LdifEntryDto entry,
            List<String> warnings,
            int totalCertificates,
            int validCertificates,
            int invalidCertificates
        ) {
            this.entry = entry;
            this.warnings = warnings;
            this.totalCertificates = totalCertificates;
            this.validCertificates = validCertificates;
            this.invalidCertificates = invalidCertificates;
        }
    }

    private static class CustomParseResult {
        final Map<String, Object> binaryAttributes;
        final List<String> warnings;
        final int totalCertificates;
        final int validCertificates;
        final int invalidCertificates;

        CustomParseResult(
            Map<String, Object> binaryAttributes,
            List<String> warnings,
            int totalCertificates,
            int validCertificates,
            int invalidCertificates
        ) {
            this.binaryAttributes = binaryAttributes;
            this.warnings = warnings;
            this.totalCertificates = totalCertificates;
            this.validCertificates = validCertificates;
            this.invalidCertificates = invalidCertificates;
        }
        
    }

    private static class CertificateValidationResult {
        final boolean isValid;
        final String errorMessage;
        final String details;

        CertificateValidationResult(boolean isValid, String errorMessage, String details) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.details = details;
        }
    }
}
