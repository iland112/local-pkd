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
import com.smartcoreinc.localpkd.ldif.dto.LdifEntryDto;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldif.LDIFException;
import com.unboundid.ldif.LDIFReader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LineTrackingLdifParser {

    private static final List<String> BINARY_ATTRIBUTES = Arrays.asList(
        "userCertificate", "caCertificate", "crossCertificatePair", 
        "certificateRevocationList", "authorityRevocationList"
    );

    public LdifAnalysisResult parseLdifFile(MultipartFile file) throws IOException {
        log.info("Starting line-tracking LDIF parsing for file: {}", file.getOriginalFilename());
        
        LdifAnalysisResult result = new LdifAnalysisResult();
        List<LdifEntryDto> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Integer> objectClassCount = new HashMap<>();

        int addCount = 0, modifyCount = 0, deleteCount = 0;

        // 라인 번호 추적을 위한 사전 처리
        try (InputStream inputStream = file.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder currentRecord = new StringBuilder();
            int lineNumber = 0;
            int recordNumber = 0;
            String line;
            boolean inRecord = false;

            while ((line = bufferedReader.readLine()) != null) {
                lineNumber++;

                // 빈 라인이나 주석 라인 처리
                if (line.trim().isEmpty()) {
                    if (inRecord && currentRecord.length() > 0) {
                        // 현재 레코드 완료, 파싱 시도
                        recordNumber++;
                        try {
                            log.debug("currentRecord of LDIF:\n{}", currentRecord.toString());
                            LdifEntryDto entry = parseRecord(currentRecord.toString(), recordNumber, lineNumber);
                            if (entry != null) {
                                entries.add(entry);
                                addCount++;
                                countObjectClasses(entry, objectClassCount);
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
                            LdifEntryDto entryDto = parseRecord(currentRecord.toString(), recordNumber, lineNumber - 1);
                            if (entryDto != null) {
                                entries.add(entryDto);
                                addCount++;
                                countObjectClasses(entryDto, objectClassCount);
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
                    LdifEntryDto entryDto = parseRecord(currentRecord.toString(), recordNumber, lineNumber);
                    if (entryDto != null) {
                        entries.add(entryDto);
                        addCount++;
                        countObjectClasses(entryDto, objectClassCount);
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

        // 결과 설정
        result.setEntries(entries);
        result.setErrors(errors);
        result.setTotalEntries(entries.size());
        result.setAddEntries(addCount);
        result.setModifyEntries(modifyCount);
        result.setDeleteEntries(deleteCount);
        result.setObjectClassCount(objectClassCount);
        result.setHasValidationErrors(!errors.isEmpty());

        log.info("LDIF parsing completed. Entries: {}, Errors: {}", entries.size(), errors.size());
        return result;
    }

    private LdifEntryDto parseRecord(String recordText, int recordNumber, int lineNumber) throws LDIFException, IOException {
        try {
            // 커스텀 파싱을 먼저 수행하여 바이너리 속성 처라
            Map<String, Object> customParsedAttributes = customParseRecord(recordText);

            try (StringReader stringReader = new StringReader(recordText);
                LDIFReader ldifReader = new LDIFReader(new BufferedReader(stringReader))) {
                    Entry entry = ldifReader.readEntry();
                    log.debug("entry data to LDIFString:\n {}", entry.toLDIFString());
                    if (entry != null) {
                        return convertEntryDto(entry, "ADD", customParsedAttributes);
                    }
                    return null;
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
     * @param recordText
     * @return
     */
    private Map<String, Object> customParseRecord(String recordText) {
        Map<String, Object> binaryAttributes = new HashMap<>();
        String[] lines = recordText.split("\n");

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
                    if (attrName.endsWith(";binary")) {
                        attrName = attrName.replace(";binary", "");
                    }

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

                            log.debug("Successfully parsed binary attribute '{}' with {} bytes", attrName, binaryData.length);
                        } catch (IllegalArgumentException e) {
                            log.warn("Failed to decode Base64 for attribute '{}': {}", attrName, e.getMessage());
                        }
                    }
                }
            }
        }

        return binaryAttributes;
    }

    /**
     * 바이너리 속성 여부 확인
     * @param String attributeName
     * @return boolean
     */
    private boolean isBinaryAttribute(String attributeName) {
        return BINARY_ATTRIBUTES.stream()
            .anyMatch(binaryAttr -> attributeName.toLowerCase().contains(binaryAttr.toLowerCase()));
    }

    private LdifEntryDto convertEntryDto(Entry entry, String entryType, Map<String, Object> customParsedAttributes) {
        Map<String, List<String>> attributes = new HashMap<>();

        for (Attribute attribute : entry.getAttributes()) {
            String attributeName = attribute.getName();

            // ;binary 접미사 정규화
            String normalizedAttrName = attributeName.replace(";binary", "");

            // 바이너리 속성인 경우 커스텀 파싱 결과 사용
            if (isBinaryAttribute(normalizedAttrName) && customParsedAttributes.containsKey(normalizedAttrName)) {
                @SuppressWarnings("unchecked")
                List<byte[]> binaryValues = (List<byte[]>) customParsedAttributes.get(normalizedAttrName);
                List<String> base64Values = new ArrayList<>();
                
                for (byte[] binaryValue : binaryValues) {
                    // Base64로 다시 인코딩하여 저장 (LDAP 저장용)
                    base64Values.add(Base64.getEncoder().encodeToString(binaryValue));
                }
                
                attributes.put(normalizedAttrName, base64Values);
                log.debug("Processed binary attribute '{}' with {} values", normalizedAttrName, base64Values.size());
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

    /**
     * Base64 문자열 검증
     */
    private boolean isValidBase64(String str) {
        try {
            Base64.getDecoder().decode(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * X.509 인증서 검증 (선택적)
     */
    private boolean isValidX509Certificate(byte[] certBytes) {
        try {
            // 기본적인 DER 인코딩 검증 (0x30으로 시작해야 함)
            if (certBytes.length < 4 || certBytes[0] != 0x30) {
                return false;
            }
            
            // java.security.cert.CertificateFactory로 추가 검증 가능
            // CertificateFactory cf = CertificateFactory.getInstance("X.509");
            // cf.generateCertificate(new ByteArrayInputStream(certBytes));
            
            return true;
        } catch (Exception e) {
            log.warn("Invalid X.509 certificate: {}", e.getMessage());
            return false;
        }
    }
}
