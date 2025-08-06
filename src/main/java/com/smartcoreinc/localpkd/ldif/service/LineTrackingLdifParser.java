package com.smartcoreinc.localpkd.ldif.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
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

    public LdifAnalysisResult parseLdifFileWithLineTracking(MultipartFile file) throws IOException {
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
                            LdifEntryDto entry = parseRecord(currentRecord.toString(), recordNumber, lineNumber);
                            if (entry != null) {
                                entries.add(entry);
                                addCount++;
                                countObjectClasses(entry, objectClassCount);
                            }
                        } catch (Exception e) {
                            errors.add(String.format("Record %d (around line %d): %s", recordNumber, lineNumber, e.getMessage()));
                        }
                        currentRecord.setLength(0);
                        inRecord = false;
                    }
                    continue;
                }

                // ㅜ석 라인 스킵
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
        try (StringReader stringReader = new StringReader(recordText);
            LDIFReader ldifReader = new LDIFReader(new BufferedReader(stringReader))) {
                Entry entry = ldifReader.readEntry();
                if (entry != null) {
                    return convertEntryDto(entry, "ADD");
                }
                return null;
        } catch (LDIFException e) {
            throw new LDIFException(
                "LDIF parsing error in record " + recordNumber + ": " + e.getMessage(),
                lineNumber,
                true,
                e
            );
        }
    }

    private LdifEntryDto convertEntryDto(Entry entry, String entryType) {
        Map<String, List<String>> attributes = new HashMap<>();

        for (Attribute attribute : entry.getAttributes()) {
            String attributeName = attribute.getName();
            String[] values = attribute.getValues();
            attributes.put(attributeName, Arrays.asList(values));
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
}
