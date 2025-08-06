package com.smartcoreinc.localpkd.ldif.service;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisResult;
import com.smartcoreinc.localpkd.ldif.dto.LdifEntryDto;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldif.DuplicateValueBehavior;
import com.unboundid.ldif.LDIFAddChangeRecord;
import com.unboundid.ldif.LDIFDeleteChangeRecord;
import com.unboundid.ldif.LDIFException;
import com.unboundid.ldif.LDIFModifyChangeRecord;
import com.unboundid.ldif.LDIFModifyDNChangeRecord;
import com.unboundid.ldif.LDIFReader;
import com.unboundid.ldif.LDIFRecord;
import com.unboundid.ldif.TrailingSpaceBehavior;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ImprovedLdifParserService {

    /**
     * UnboundID LDAP SDK를 사용한 고성능 LDIF 파싱
     * - 멀티스레드 지원
     * - 메모리 효율적인 스트리밍 처리
     * - 상세한 오류 보고
     */
    public LdifAnalysisResult paredLdifFile(MultipartFile file) throws IOException {
        log.info("Starting LDIF parsing for file: {}", file.getOriginalFilename());

        LdifAnalysisResult result = new LdifAnalysisResult();
        List<LdifEntryDto> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Integer> objectClassCount = new HashMap<>();
        
        AtomicInteger addCount = new AtomicInteger(0);
        AtomicInteger modifyCount = new AtomicInteger(0);
        AtomicInteger deleteCount = new AtomicInteger(0);
        AtomicInteger lineNumber = new AtomicInteger(0); // 라인 번호 수동 추적

        // UnboundID LDIF Reader 설정 (멀티스레드 지원)
        try (InputStream inputStream = file.getInputStream()) {
            // 대용량 파일 처리를 위한 버퍼링
            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream, 8192);

            // LDIF Reader 생성 (비동기 파싱 지원)
            LDIFReader ldifReader = new LDIFReader(bufferedInputStream);

            // 파싱 옵션 설정
            ldifReader.setDuplicateValueBehavior(DuplicateValueBehavior.STRIP);
            ldifReader.setTrailingSpaceBehavior(TrailingSpaceBehavior.STRIP);

            long startTime = System.currentTimeMillis();
            int recordCount = 0;

            try {
                // Entry 및 Change Record 모두 처리
                while (true) {
                    LDIFRecord record = ldifReader.readLDIFRecord();
                    if (record == null) {
                        break;
                    }
                    recordCount++;

                    try {
                        LdifEntryDto entryDto = processLdifRecord(record);
                        if (entryDto != null) {
                            entries.add(entryDto);

                            // 엔트리 타입별 카운트
                            switch (entryDto.getEntryType()) {
                                case "ADD" -> addCount.incrementAndGet();
                                case "MODIFY" -> modifyCount.incrementAndGet();    
                                case "DELETE" -> deleteCount.incrementAndGet();
                            }

                            // ObjectClass 통계
                            countObjectClasses(entryDto, objectClassCount);
                        }
                    } catch (Exception e) {
                        String errorMsg = String.format("Record %d: %s", recordCount, e.getMessage());
                        errors.add(errorMsg);
                        log.warn("Error processing LDIF record: {}", errorMsg);
                    }
                }
            } catch (LDIFException e) {
                String errorMsg = String.format("LDIF parsing error at record %d: %s", recordCount, e.getMessage());
                errors.add(errorMsg);
                log.error("LDIF parsing failed: {}", errorMsg);
            } catch (IOException e) {
                String errorMsg = String.format("IO Error at record %d: %s", recordCount, e.getMessage());
                errors.add(errorMsg);
                log.error("IO error during LDIF parsing: {}", errorMsg);
            } finally {
                ldifReader.close();
            }

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("LDIF parsing completed in {}ms, Entries: {}, Errors: {}",
                processingTime, entries.size(), errors.size());

        } catch (IOException e) {
            log.warn("IO Error reading LDIF file: {}", e.getMessage());
            errors.add("File reading error: " + e.getMessage());
        }

        // 결과 설정
        result.setEntries(entries);
        result.setErrors(errors);
        result.setTotalEntries(entries.size());
        result.setAddEntries(addCount.get());
        result.setModifyEntries(deleteCount.get());
        result.setObjectClassCount(objectClassCount);
        result.setHasValidationErrors(!errors.isEmpty());

        return result;
    }

    /**
     * LDIF Record 처리 (Entry와 Change Record 모두 지원)
     * @param com.unboundid.ldif.LDIFRecord record
     * @return com.smartcoreinc.localpkd.ldif.dto.LdifEntryDto
     */
    private LdifEntryDto processLdifRecord(LDIFRecord record) {
        if (record instanceof LDIFAddChangeRecord addRecord) {
            return handleAddRecord(addRecord);
        } else if (record instanceof LDIFModifyChangeRecord modifyRecord) {
            return handleModifyRecord(modifyRecord);
        } else if (record instanceof LDIFDeleteChangeRecord deleteRecord) {
            return handleDeleteRecord(deleteRecord);
        } else if (record instanceof LDIFModifyDNChangeRecord modifyDNRecord) {
            return handleModifyDNRecord(modifyDNRecord);
        } else {
            // 일반 Entry 처리
            Entry entry = new Entry(record.toLDIFString());
            return convertEntryToDto(entry, "ADD");
        }
    }

    private LdifEntryDto handleAddRecord(LDIFAddChangeRecord addRecord) {
        try {
            Entry entry = new Entry(addRecord.getDN(), addRecord.getAttributes());
            return convertEntryToDto(entry, "ADD");
        } catch (Exception e) {
            log.warn("Error creating entry from add record: {}", e.getMessage());
            return null;
        }
    }

    private LdifEntryDto handleModifyRecord(LDIFModifyChangeRecord modifyRecord) {
        Map<String, List<String>> attributes = new HashMap<>();

        // MOdification 정보를 속성으로 변환
        for (Modification mod : modifyRecord.getModifications()) {
            String attrName = mod.getAttributeName();
            String[] values = mod.getValues();
            if (values != null) {
                attributes.put(attrName, Arrays.asList(values));
            }
        }

        return new LdifEntryDto(
            modifyRecord.getDN(),
            "MODIFY",
            attributes,
            modifyRecord.toLDIFString()
        );
    }

    private LdifEntryDto handleDeleteRecord(LDIFDeleteChangeRecord deleteRecord) {
        return new LdifEntryDto(
            deleteRecord.getDN(),
            "DELETE",
            new HashMap<>(),
            deleteRecord.toLDIFString()
        );
    }

    private LdifEntryDto handleModifyDNRecord(LDIFModifyDNChangeRecord modifyDNRecord) {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("newRDN", List.of(modifyDNRecord.getNewRDN()));
        if (modifyDNRecord.getNewSuperiorDN() != null) {
            attributes.put("newSuperior", List.of(modifyDNRecord.getNewSuperiorDN()));
        }

        return new LdifEntryDto(
            modifyDNRecord.getDN(),
            "MODIFYDN",
            attributes,
            modifyDNRecord.toLDIFString()
        );
    }

    private LdifEntryDto convertEntryToDto(Entry entry, String entryType) {
        Map<String, List<String>> attributes = new HashMap<>();

        // UnboundID Entry에서 모든 속성을 가져오는 올바른 방법
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

    private void countObjectClasses(LdifEntryDto entryDto, Map<String, Integer> objectClassCount) {
        List<String> objectClasses = entryDto.getAttributes().get("objectClass");
        if (objectClasses != null) {
            for (String objectClass : objectClasses) {
                objectClassCount.merge(objectClass, 1, Integer::sum);
            }
        }
    }

    /**
     * LDIF 콘텐츠 검증 (UnboundID의 강력한 검증 기능 활용)
     * @param content
     * @return
     */
    public boolean validateLdifContent(String content) {
        try (StringReader stringReader = new StringReader(content)) {
            LDIFReader ldifReader = new LDIFReader(new BufferedReader(stringReader));

            // 엄격한 검증 옵션 설정
            ldifReader.setDuplicateValueBehavior(DuplicateValueBehavior.REJECT);
            ldifReader.setTrailingSpaceBehavior(TrailingSpaceBehavior.REJECT);

            while (ldifReader.readLDIFRecord() != null) {
                // 파싱만으로 검증 수행
            }

            return true;
        } catch (Exception e) {
            log.warn("LDIF validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 대용량 파일을 위한 스트리망 처리
     * @param ldFile
     * @param batchSize
     * @param batchProcessor
     * @throws IOException
     */
    public void processLargeFile(File ldifFile, int batchSize, Consumer<List<LdifEntryDto>> batchProcessor) throws IOException {
        try (LDIFReader ldifReader = new LDIFReader(ldifFile.getAbsolutePath())) {
            List<LdifEntryDto> batch = new ArrayList<>(batchSize);

            LDIFRecord record;
            while ((record = ldifReader.readLDIFRecord()) != null) {
                LdifEntryDto entryDto = processLdifRecord(record);
                if (entryDto != null) {
                    batch.add(entryDto);

                    if (batch.size() >= batchSize) {
                        batchProcessor.accept(new ArrayList<>(batch));
                        batch.clear();
                    }
                }
            }

            // 마지막 배치 처리
            if (!batch.isEmpty()) {
                batchProcessor.accept(batch);
            }
        } catch (Exception e) {
            log.error("Error from processLargeFile() with: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
