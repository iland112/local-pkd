package com.smartcoreinc.localpkd.fileparsing.domain.port;

import com.smartcoreinc.localpkd.fileparsing.domain.model.MasterListParseResult;

/**
 * MasterListParser - Master List 파싱 Port Interface (Hexagonal Architecture)
 *
 * <p><b>Hexagonal Architecture (Port & Adapter)</b>:</p>
 * <ul>
 *   <li>Port: Domain Layer에 정의된 인터페이스 (이 파일)</li>
 *   <li>Adapter: Infrastructure Layer에서 구현 (MasterListParserAdapter)</li>
 *   <li>Dependency Inversion: Domain이 Infrastructure에 의존하지 않음</li>
 * </ul>
 *
 * <p><b>목적</b>: Master List 파싱을 위한 도메인 계약 정의</p>
 *
 * <p>이 인터페이스는 ICAO Master List (.ml) 파일을 파싱하여
 * 구조화된 데이터(MasterListParseResult)를 반환합니다.</p>
 *
 * <h3>파싱 프로세스</h3>
 * <ol>
 *   <li>CMS 서명 검증</li>
 *   <li>서명자 정보 추출 (DN, 알고리즘)</li>
 *   <li>개별 CSCA 인증서 추출</li>
 *   <li>메타데이터 추출 (국가 코드, 버전)</li>
 *   <li>MasterListParseResult 생성 및 반환</li>
 * </ol>
 *
 * <p><b>구현체</b>:</p>
 * <ul>
 *   <li>MasterListParserAdapter: BouncyCastle 라이브러리를 사용한 CMS 파싱</li>
 * </ul>
 *
 * <p><b>사용 예시</b>:</p>
 * <pre>{@code
 * @Service
 * public class ParseMasterListFileUseCase {
 *     private final MasterListParser masterListParser;
 *
 *     @Transactional
 *     public ParseMasterListResponse execute(ParseMasterListCommand command) {
 *         // Master List 파싱
 *         MasterListParseResult parseResult = masterListParser.parse(command.fileContent());
 *
 *         // MasterList aggregate 생성
 *         MasterList masterList = MasterList.create(
 *             MasterListId.newId(),
 *             command.uploadId(),
 *             parseResult.getCountryCode(),
 *             parseResult.getVersion(),
 *             parseResult.getCmsBinary(),
 *             parseResult.getSignerInfo(),
 *             parseResult.getCscaCount()
 *         );
 *
 *         // 저장
 *         masterListRepository.save(masterList);
 *     }
 * }
 * }</pre>
 *
 * @see MasterListParseResult
 * @see com.smartcoreinc.localpkd.fileparsing.infrastructure.adapter.MasterListParserAdapter
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-27
 */
public interface MasterListParser {

    /**
     * Master List 파일 파싱
     *
     * <p>CMS 서명된 Master List 바이너리를 파싱하여 다음 정보를 추출합니다:</p>
     * <ul>
     *   <li>원본 CMS 바이너리 (LDAP 업로드용)</li>
     *   <li>서명자 정보 (DN, 알고리즘)</li>
     *   <li>개별 CSCA 인증서 목록</li>
     *   <li>메타데이터 (국가 코드, 버전)</li>
     * </ul>
     *
     * @param masterListBytes Master List 파일의 바이너리 데이터
     * @return MasterListParseResult 파싱 결과 (MasterList aggregate 생성용)
     * @throws ParsingException 파싱 중 오류 발생 시 (CMS 구조 오류, 서명 검증 실패 등)
     */
    MasterListParseResult parse(byte[] masterListBytes) throws ParsingException;

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
