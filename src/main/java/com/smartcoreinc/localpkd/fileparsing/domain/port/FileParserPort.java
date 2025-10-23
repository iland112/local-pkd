package com.smartcoreinc.localpkd.fileparsing.domain.port;

import com.smartcoreinc.localpkd.fileparsing.domain.model.ParsedFile;
import com.smartcoreinc.localpkd.fileupload.domain.model.FileFormat;

/**
 * FileParserPort - 파일 파싱 Port Interface (Hexagonal Architecture)
 *
 * <p><b>Hexagonal Architecture (Port & Adapter)</b>:</p>
 * <ul>
 *   <li>Port: Domain Layer에 정의된 인터페이스 (이 파일)</li>
 *   <li>Adapter: Infrastructure Layer에서 구현 (LdifParserAdapter, MasterListParserAdapter)</li>
 *   <li>Dependency Inversion: Domain이 Infrastructure에 의존하지 않음</li>
 * </ul>
 *
 * <p><b>구현체</b>:</p>
 * <ul>
 *   <li>LdifParserAdapter: LDIF 파일 파싱 (UnboundID LDAP SDK 사용)</li>
 *   <li>MasterListParserAdapter: Master List 파싱 (BouncyCastle CMS 사용)</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>
 * // Application Layer (Use Case)
 * {@literal @}Autowired
 * private FileParserPort fileParserPort;  // 구현체 자동 주입
 *
 * // 파싱 실행
 * ParsedFile parsedFile = fileParserPort.parse(
 *     fileBytes,
 *     FileFormat.CSCA_COMPLETE_LDIF,
 *     parsedFileId,
 *     uploadId
 * );
 * </pre>
 *
 * @see com.smartcoreinc.localpkd.fileparsing.infrastructure.adapter.LdifParserAdapter
 * @see com.smartcoreinc.localpkd.fileparsing.infrastructure.adapter.MasterListParserAdapter
 */
public interface FileParserPort {

    /**
     * 파일 파싱
     *
     * <p><b>파싱 프로세스</b>:</p>
     * <ol>
     *   <li>파일 포맷 확인 및 적절한 파서 선택</li>
     *   <li>파일 구조 검증</li>
     *   <li>인증서 및 CRL 추출</li>
     *   <li>메타데이터 추출 (DN, Serial Number, Validity Period 등)</li>
     *   <li>ParsedFile Aggregate 생성 및 반환</li>
     * </ol>
     *
     * @param fileBytes 파일 바이너리 데이터
     * @param fileFormat 파일 포맷 (LDIF, Master List 등)
     * @param parsedFile ParsedFile Aggregate (파싱 결과 저장용)
     * @throws ParsingException 파싱 중 오류 발생 시
     */
    void parse(byte[] fileBytes, FileFormat fileFormat, ParsedFile parsedFile) throws ParsingException;

    /**
     * 특정 파일 포맷 지원 여부
     *
     * @param fileFormat 파일 포맷
     * @return 지원 여부
     */
    boolean supports(FileFormat fileFormat);

    /**
     * Parsing Exception
     */
    class ParsingException extends Exception {
        public ParsingException(String message) {
            super(message);
        }

        public ParsingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
