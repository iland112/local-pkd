package com.smartcoreinc.localpkd.ldapintegration.infrastructure.web;

import com.smartcoreinc.localpkd.ldapintegration.application.usecase.LdapHealthCheckUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * LdapUploadApiController - LDAP 서버 업로드 REST API
 *
 * <p><b>Endpoint</b>: /api/ldap/*</p>
 *
 * <p><b>기능</b>:</p>
 * <ul>
 *   <li>검증된 인증서/CRL LDAP 업로드 시작</li>
 *   <li>업로드 상태 조회</li>
 * </ul>
 *
 * <p><b>Event-Driven</b>:</p>
 * <ul>
 *   <li>LdapUploadCompletedEvent 발행</li>
 *   <li>SSE를 통한 실시간 진행 상황 전송</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-29
 */
@Slf4j
@RestController
@RequestMapping("/api/ldap")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LdapUploadApiController {

    private final LdapHealthCheckUseCase ldapHealthCheckUseCase;

    /**
     * LDAP 업로드 상태 조회
     *
     * <p>업로드 ID에 따른 현재 상태를 조회합니다.</p>
     *
     * @param uploadId 업로드 ID
     * @return 업로드 상태 정보
     */
    @Operation(summary = "LDAP 업로드 상태 조회",
               description = "특정 업로드 ID에 대한 LDAP 업로드 진행 상태를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "업로드 상태 조회 성공",
        content = @Content(mediaType = "application/json",
            schema = @Schema(example = "{\"uploadId\": \"uuid\", \"status\": \"IN_PROGRESS\", \"message\": \"LDAP 업로드가 진행 중입니다\"}")))
    @ApiResponse(responseCode = "500", description = "서버 내부 오류",
        content = @Content(mediaType = "application/json",
            schema = @Schema(example = "{\"success\": false, \"errorMessage\": \"상태 조회 중 오류가 발생했습니다\", \"errorCode\": \"INTERNAL_ERROR\"}")))
    @GetMapping("/upload/{uploadId}")
    public ResponseEntity<?> getUploadStatus(@PathVariable UUID uploadId) {
        log.debug("Getting LDAP upload status for uploadId: {}", uploadId);

        try {
            Map<String, Object> result = new HashMap<>();
            result.put("uploadId", uploadId);
            result.put("status", "IN_PROGRESS"); // TODO: 실제 상태 조회 구현
            result.put("message", "LDAP 업로드가 진행 중입니다");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error getting LDAP upload status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(createErrorResponse(
                false,
                "상태 조회 중 오류가 발생했습니다",
                "INTERNAL_ERROR"
            ));
        }
    }

    /**
     * LDAP 서버 헬스 체크 엔드포인트
     *
     * <p>LDAP 서버와의 연결 상태를 확인하고 그 결과를 반환합니다.</p>
     *
     * @return LDAP 연결 상태 정보를 담은 JSON 응답
     */
    @Operation(summary = "LDAP 서버 헬스 체크", description = "LDAP 서버와의 연결 상태를 확인합니다.")
    @ApiResponse(responseCode = "200", description = "LDAP 서버에 성공적으로 연결됨",
        content = @Content(mediaType = "application/json",
            schema = @Schema(example = "{\"success\": true, \"message\": \"LDAP 서버에 성공적으로 연결되었습니다.\"}")))
    @ApiResponse(responseCode = "503", description = "LDAP 서버 연결 실패",
        content = @Content(mediaType = "application/json",
            schema = @Schema(example = "{\"success\": false, \"message\": \"LDAP 서버 연결 실패: [에러 메시지]\"}")))
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkLdapHealth() {
        log.debug("Checking LDAP health status...");
        Map<String, Object> healthStatus = ldapHealthCheckUseCase.execute();
        HttpStatus status = (boolean) healthStatus.getOrDefault("success", false) ?
            HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return new ResponseEntity<>(healthStatus, status);
    }

    /**
     * 에러 응답 생성
     *
     * @param success 성공 여부
     * @param message 메시지
     * @param errorCode 에러 코드
     * @return 에러 응답 맵
     */
    private Map<String, Object> createErrorResponse(boolean success, String message, String errorCode) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("errorCode", errorCode);
        return response;
    }
}


