package com.smartcoreinc.localpkd.common.dto;

import com.smartcoreinc.localpkd.common.enums.FileFormat;
import com.smartcoreinc.localpkd.common.enums.UploadStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 파일 업로드 이력 검색 조건 DTO
 *
 * @author Development Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileSearchCriteria {

    /**
     * 파일 포맷 필터
     */
    private FileFormat fileFormat;

    /**
     * 업로드 상태 필터
     */
    private UploadStatus uploadStatus;

    /**
     * 검색 시작 날짜
     */
    private LocalDateTime startDate;

    /**
     * 검색 종료 날짜
     */
    private LocalDateTime endDate;

    /**
     * 파일명 검색 키워드
     */
    private String fileName;

    /**
     * 정렬 필드 (uploadedAt, fileSize, originalFileName)
     */
    private String sortBy;

    /**
     * 정렬 방향 (ASC, DESC)
     */
    private String sortDirection;

    /**
     * 검색 조건이 비어있는지 확인
     *
     * @return 모든 조건이 null이면 true
     */
    public boolean isEmpty() {
        return fileFormat == null
                && uploadStatus == null
                && startDate == null
                && endDate == null
                && (fileName == null || fileName.trim().isEmpty());
    }

    /**
     * 날짜 범위가 유효한지 확인
     *
     * @return 시작일이 종료일보다 이전이면 true
     */
    public boolean isValidDateRange() {
        if (startDate == null || endDate == null) {
            return true;
        }
        return !startDate.isAfter(endDate);
    }
}