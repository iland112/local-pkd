package com.smartcoreinc.localpkd.ldif.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
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
public class LdifParserService {

    public LdifAnalysisResult parseLdif(MultipartFile file) throws IOException {
        LdifAnalysisResult result = new LdifAnalysisResult();
        List<LdifEntryDto> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Integer> objectClassCount = new HashMap<>();

        int addCount = 0, modifyCount = 0, deleteCount = 0;
        int recordNumber = 0; // 레코드 번호 수동 추적

        try (InputStream inputStream = file.getInputStream();
            LDIFReader ldifReader = new LDIFReader(inputStream)) {
            
            Entry entry;
            while (true) {
                try {
                    entry = ldifReader.readEntry();
                    if (entry == null) {
                        break;
                    }
                    recordNumber++;

                    LdifEntryDto entryDto = convertToDto(entry);
                    entries.add(entryDto);

                    // Count entry types
                    addCount++;

                    // Count object classes
                    if (entry.hasAttribute("objectClass")) {
                        String[] objectClasses = entry.getAttributeValues("objectClass");
                        for (String objectClass : objectClasses) {
                            objectClassCount.merge(objectClass, 1, Integer::sum);
                        }
                    }
                } catch (LDIFException e) {
                    log.error("Error parsing LDIF entry: {}", e.getMessage());
                    errors.add("Record " + recordNumber + ": " + e.getMessage());
                } catch (IOException e) {
                    log.error("IO error reading LDIF: {}", e.getMessage());
                    errors.add("IO Error at record" + recordNumber + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Unexpected error parsing LDIF file: {}", e.getMessage());
            errors.add("Unexpected error: " + e.getMessage());
        }

        // Set results
        result.setEntries(entries);
        result.setErrors(errors);
        result.setTotalEntries(entries.size());
        result.setAddEntries(addCount);
        result.setModifyEntries(modifyCount);
        result.setDeleteEntries(deleteCount);

        log.info("Parsed LDIF file: {} entries, {} errors", entries.size(), errors.size());

        return result;
    }

    private LdifEntryDto convertToDto(Entry entry) {
        Map<String, List<String>> attributes = new HashMap<>();

        for (Attribute attribute : entry.getAttributes()) {
            String attributeName = attribute.getName();
            String[] values = attribute.getValues();
            attributes.put(attributeName, Arrays.asList(values));
        }

        return new LdifEntryDto(
            entry.getDN(),
            "ADD",
            attributes,
            entry.toLDIFString()
        );
    }

    public boolean validateLdifContent(String content) {
        try (StringReader stringReader = new StringReader(content);
            LDIFReader ldifReader = new LDIFReader(new BufferedReader(stringReader))) {
            while (ldifReader.readEntry() != null) {
                // 단순히 파싱 가능한지 확인
            }
            return true;
        } catch (Exception e) {
            log.warn("LDIF validation failed: {}", e.getMessage());
            return false;
        }
    }
}
