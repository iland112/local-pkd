package com.smartcoreinc.localpkd.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 중복 파일 검사 요청 DTO
 *
 * @author Development Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateCheckRequest {

    /**
     * 파일명
     */
    private String filename;

    /**
     * 파일 크기 (bytes)
     */
    private Long fileSize;

    /**
     * 파일 해시 (SHA-256)
     * 클라이언트에서 계산한 값
     */
    private String fileHash;

    /**
     * 예상 체크섬 (SHA-1, 선택사항)
     */
    private String expectedChecksum;
}
