package com.smartcoreinc.localpkd.parser.common;

import org.hibernate.query.sqm.ParsingException;

import com.smartcoreinc.localpkd.common.enums.FileFormat;
import com.smartcoreinc.localpkd.common.enums.FileType;
import com.smartcoreinc.localpkd.parser.common.domain.ParseContext;
import com.smartcoreinc.localpkd.parser.common.domain.ParseResult;

/**
 * ICAO PKD 파일 파서 인터페이스
 * 
 * 모든 파서는 이 인터페이스를 구현해야 함
 */
public interface FileParser {
    /**
     * 이 파서가 지원하는 파일 타입과 포맷인지 확인
     * 
     * @param fileType 파일 타입
     * @param fileFormat 파일 포맷
     * @return true if supported
     */
    boolean supports(FileType fileType, FileFormat fileFormat);
    
    /**
     * 파일 데이터를 파싱
     * 
     * @param fileData 파일 바이트 데이터
     * @param context 파싱 컨텍스트
     * @return 파싱 결과
     * @throws ParsingException 파싱 실패 시
     */
    ParseResult parse(byte[] fileData, ParseContext context) throws ParsingException;
    
    /**
     * 파서 우선순위 (낮을수록 먼저 시도)
     * 
     * @return 우선순위 (기본값: 100)
     */
    default int getPriority() {
        return 100;
    }
    
    /**
     * 파서 이름
     * 
     * @return 파서 이름
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
