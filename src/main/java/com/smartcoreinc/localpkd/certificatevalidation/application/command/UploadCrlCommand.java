package com.smartcoreinc.localpkd.certificatevalidation.application.command;

import lombok.Builder;
import java.util.List;
import java.util.Objects;

/**
 * UploadCrlCommand - CRL LDAP 업로드 명령
 *
 * <p><b>목적</b>: 검증된 CRL (Certificate Revocation Lists)을 LDAP 서버에 업로드하기 위한 커맨드입니다.</p>
 *
 * <p><b>사용 흐름</b>:</p>
 * <pre>{@code
 * // 1. Command 생성
 * UploadCrlCommand command = UploadCrlCommand.builder()
 *     .uploadId(uploadId)
 *     .crlIds(crlIds)
 *     .baseDn("dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com")
 *     .isBatch(true)
 *     .build();
 *
 * // 2. UseCase 실행
 * UploadCrlResponse response = uploadCrlUseCase.execute(command);
 *
 * // 3. 결과 확인
 * if (response.success()) {
 *     log.info("CRL upload completed: {} uploaded, {} failed",
 *         response.successCount(), response.failedCrlIds().size());
 * }
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-13
 */
@Builder
public record UploadCrlCommand(
    /**
     * 파일 업로드 ID
     * 원본 LDIF 파일 업로드의 UUID
     */
    java.util.UUID uploadId,

    /**
     * CRL ID 목록
     * 업로드할 CRL(Certificate Revocation List)의 ID 목록
     */
    List<java.util.UUID> crlIds,

    /**
     * LDAP Base DN
     * LDAP 서버의 Base DN (예: "dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com")
     * null이면 default DN 사용
     */
    String baseDn,

    /**
     * 배치 처리 플래그
     * true: 여러 CRL을 한 번에 처리
     * false: 단일 CRL 처리
     */
    boolean isBatch
) {
    /**
     * Command 검증
     *
     * <p>비즈니스 규칙:</p>
     * <ul>
     *   <li>uploadId는 필수</li>
     *   <li>crlIds는 비어있지 않아야 함</li>
     * </ul>
     *
     * @throws IllegalArgumentException 검증 실패 시
     */
    public void validate() {
        if (uploadId == null) {
            throw new IllegalArgumentException("uploadId must not be null");
        }
        if (crlIds == null || crlIds.isEmpty()) {
            throw new IllegalArgumentException("crlIds must not be empty");
        }
    }

    /**
     * CRL 개수 반환
     */
    public int getCrlCount() {
        return crlIds != null ? crlIds.size() : 0;
    }

    @Override
    public String toString() {
        return String.format(
            "UploadCrlCommand{uploadId=%s, crlCount=%d, baseDn='%s', isBatch=%s}",
            uploadId, getCrlCount(), baseDn, isBatch
        );
    }
}
