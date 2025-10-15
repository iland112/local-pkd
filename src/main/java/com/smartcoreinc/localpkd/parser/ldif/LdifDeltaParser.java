package com.smartcoreinc.localpkd.parser.ldif;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;

import org.hibernate.query.sqm.ParsingException;
import org.springframework.stereotype.Component;

import com.smartcoreinc.localpkd.common.enums.FileFormat;
import com.smartcoreinc.localpkd.common.enums.FileType;
import com.smartcoreinc.localpkd.common.enums.LdifChangeType;
import com.smartcoreinc.localpkd.parser.common.FileParser;
import com.smartcoreinc.localpkd.parser.common.domain.ParseContext;
import com.smartcoreinc.localpkd.parser.common.domain.ParseResult;
import com.unboundid.ldif.LDIFAddChangeRecord;
import com.unboundid.ldif.LDIFChangeRecord;
import com.unboundid.ldif.LDIFDeleteChangeRecord;
import com.unboundid.ldif.LDIFModifyChangeRecord;
import com.unboundid.ldif.LDIFReader;

import lombok.extern.slf4j.Slf4j;

/**
 * LDIF Delta 파서
 * 
 * Delta LDIF 파일 파싱 (증분 업데이트)
 * - CSCA Delta: icaopkd-001-ml-delta-{version}.ldif
 * - DSC Delta: icaopkd-002-dscs-delta-{version}.ldif
 * - BCSC Delta: icaopkd-002-bcscs-delta-{version}.ldif
 * - CRL Delta: icaopkd-002-crls-delta-{version}.ldif
 */
@Slf4j
@Component
public class LdifDeltaParser implements FileParser {

    @Override
    public boolean supports(FileType fileType, FileFormat fileFormat) {
        return fileFormat.isLdif() && fileFormat.isDelta();
    }

    @Override
    public int getPriority() {
        return 70;
    }

    @Override
    public ParseResult parse(byte[] fileData, ParseContext context) throws ParsingException {
        log.info("=== LDIF Delta 파싱 시작: {} (타입: {}) ===",
            context.getOriginalFileName(), context.getDeltaType());

        ParseResult result = ParseResult.builder()
            .fileId(context.getFileId())
            .success(false)
            .parseStartTime(LocalDateTime.now())
            .build();

        try (InputStream is = new ByteArrayInputStream(fileData);
            LDIFReader ldifReader = new LDIFReader(is)) {
            int processed = 0;
            int added = 0;
            int modified = 0;
            int deleted = 0;
            int valid = 0;
            int invalid = 0;

            LDIFChangeRecord changeRecord;
            while ((changeRecord = ldifReader.readChangeRecord()) != null) {
                try {
                    // CheangType 판별
                    LdifChangeType changeType = LdifChangeType.fromCode(
                        changeRecord.getChangeType().getName()
                    );

                    if (changeType == null) {
                        log.warn("알 수 없는 changetype: {}", changeRecord.getChangeType());
                        continue;
                    }

                    // ChangeType별 처리
                    switch (changeType) {
                        case ADD:
                            if (changeRecord instanceof LDIFAddChangeRecord addRecord) {
                                processAddRecord(addRecord, context, result);
                                added++;
                                valid++;
                            }
                            break;
                    
                        case MODIFY:
                            if (changeRecord instanceof LDIFModifyChangeRecord modifyRecord) {
                                processModifyRecord(modifyRecord, context, result);
                                modified++;
                                valid++;
                            }
                            break;
                            
                        case DELETE:
                            if (changeRecord instanceof LDIFDeleteChangeRecord deleteRecord) {
                                processDeleteRecord(deleteRecord, context, result);
                                deleted++;
                                valid++;
                            }
                            break;
                            
                        case MODRDN:
                            log.debug("MODRDN 변경은 현재 지원하지 않음: {}", changeRecord.getDN());
                            result.addWarning("MODRDN 변경 무시: " + changeRecord.getDN());
                            break;
                    }

                    processed++;

                    if (processed % 20 == 0) {
                        log.debug("Delta 처리 진행: {} (추가: {}, 수정: {}, 삭제: {})",
                            processed, added, modified, deleted);
                    }
                } catch (Exception e) {
                    log.warn("ChangeRecord 처리 실패 (DN: {}): {}",
                        changeRecord.getDN(), e.getMessage());
                    result.addError(String.format("ChangeRecord %s 처리 실패: %s",
                        changeRecord.getDN(), e.getMessage()));
                    invalid++;
                    processed++;
                }
            }

            result.setAddedEntries(added);
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

}
