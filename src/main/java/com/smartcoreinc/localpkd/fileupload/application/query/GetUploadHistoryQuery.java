package com.smartcoreinc.localpkd.fileupload.application.query;

import lombok.Builder;

/**
 * 업로드 이력 조회 Query (CQRS)
 *
 * <p>업로드된 파일 이력을 조회하는 Query 객체입니다.</p>
 *
 * <h3>필터 옵션</h3>
 * <ul>
 *   <li>검색어 (파일명, 버전, Collection 번호)</li>
 *   <li>상태 필터 (UploadStatus)</li>
 *   <li>포맷 필터 (FileFormat.Type)</li>
 *   <li>페이지 번호 (0부터 시작)</li>
 *   <li>페이지 크기 (기본 20)</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // 전체 조회 (첫 페이지)
 * GetUploadHistoryQuery query1 = GetUploadHistoryQuery.builder()
 *     .page(0)
 *     .size(20)
 *     .build();
 *
 * // 검색어로 필터링
 * GetUploadHistoryQuery query2 = GetUploadHistoryQuery.builder()
 *     .searchKeyword("009410")
 *     .page(0)
 *     .size(20)
 *     .build();
 *
 * // 상태 및 포맷으로 필터링
 * GetUploadHistoryQuery query3 = GetUploadHistoryQuery.builder()
 *     .status("COMPLETED")
 *     .fileFormat("CSCA_COMPLETE_LDIF")
 *     .page(0)
 *     .size(20)
 *     .build();
 *
 * Page<UploadHistoryResponse> response = getUploadHistoryUseCase.execute(query);
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-19
 */
@Builder
public record GetUploadHistoryQuery(
        String searchKeyword,   // optional
        String status,          // optional (UploadStatus name)
        String fileFormat,      // optional (FileFormat.Type name)
        int page,
        int size
) {
    /**
     * Compact Constructor - 유효성 검증
     */
    public GetUploadHistoryQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }

    /**
     * 기본 생성자 (page=0, size=20)
     */
    public GetUploadHistoryQuery() {
        this(null, null, null, 0, 20);
    }

    /**
     * 검증 메서드 (별도 호출용, compact constructor가 자동 호출)
     */
    public void validate() {
        // Compact constructor에서 이미 검증됨
        // 이 메서드는 backward compatibility를 위해 유지
    }
}
