package com.smartcoreinc.localpkd.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 중복 파일 검사 응답 DTO
 *
 * @author Development Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateCheckResponse {

    /**
     * 중복 여부
     */
    private boolean isDuplicate;

    /**
     * 메시지
     */
    private String message;

    /**
     * 기존 업로드 ID (중복인 경우)
     */
    private Long existingUploadId;

    /**
     * 기존 파일명
     */
    private String existingFilename;

    /**
     * 기존 업로드 날짜
     */
    private LocalDateTime existingUploadDate;

    /**
     * 기존 파일 버전
     */
    private String existingVersion;

    /**
     * 기존 파일 상태
     */
    private String existingStatus;

    /**
     * 경고 유형 (EXACT_DUPLICATE, SAME_VERSION, OLDER_VERSION)
     */
    private String warningType;

    /**
     * 강제 업로드 가능 여부
     */
    private boolean canForceUpload;

    /**
     * 추가 정보
     */
    private String additionalInfo;

    /**
     * 정확히 동일한 파일 (동일한 체크섬)
     */
    public static DuplicateCheckResponse exactDuplicate(Long id, String filename, LocalDateTime uploadDate,
                                                         String version, String status) {
        return DuplicateCheckResponse.builder()
                .isDuplicate(true)
                .message("이 파일은 이전에 이미 업로드되었습니다.")
                .existingUploadId(id)
                .existingFilename(filename)
                .existingUploadDate(uploadDate)
                .existingVersion(version)
                .existingStatus(status)
                .warningType("EXACT_DUPLICATE")
                .canForceUpload(false)
                .additionalInfo("동일한 파일이 시스템에 존재합니다. 새로 업로드할 필요가 없습니다.")
                .build();
    }

    /**
     * 동일 버전 다른 내용
     */
    public static DuplicateCheckResponse sameVersionDifferentContent(Long id, String filename,
                                                                      LocalDateTime uploadDate, String version) {
        return DuplicateCheckResponse.builder()
                .isDuplicate(true)
                .message("동일한 버전의 다른 파일이 이미 존재합니다.")
                .existingUploadId(id)
                .existingFilename(filename)
                .existingUploadDate(uploadDate)
                .existingVersion(version)
                .warningType("SAME_VERSION")
                .canForceUpload(true)
                .additionalInfo("기존 파일과 내용이 다릅니다. 강제 업로드 시 기존 파일을 대체합니다.")
                .build();
    }

    /**
     * 이전 버전
     */
    public static DuplicateCheckResponse olderVersion(String currentVersion, String newVersion) {
        return DuplicateCheckResponse.builder()
                .isDuplicate(true)
                .message(String.format("업로드하려는 파일(%s)이 현재 시스템 버전(%s)보다 오래되었습니다.",
                        newVersion, currentVersion))
                .warningType("OLDER_VERSION")
                .canForceUpload(true)
                .additionalInfo("이전 버전을 업로드하면 데이터가 다운그레이드될 수 있습니다.")
                .build();
    }

    /**
     * 중복 없음
     */
    public static DuplicateCheckResponse noDuplicate() {
        return DuplicateCheckResponse.builder()
                .isDuplicate(false)
                .message("업로드 가능한 새로운 파일입니다.")
                .canForceUpload(true)
                .build();
    }
}
