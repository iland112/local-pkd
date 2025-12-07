package com.smartcoreinc.localpkd.controller;

import com.smartcoreinc.localpkd.ldapintegration.application.usecase.LdapHealthCheckUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * LDAP 헬스 체크 컨트롤러
 *
 * OpenLDAP 서버 연결 상태를 확인합니다.
 *
 * @author SmartCore Inc.
 * @version 1.0
 */
@Tag(name = "시스템 상태 API")
@Slf4j
@RestController
@RequiredArgsConstructor
public class LdapHealthController {

    private final LdapHealthCheckUseCase ldapHealthCheckUseCase;

    @Operation(summary = "LDAP 서버 상태 확인",
               description = "OpenLDAP 서버의 연결 상태를 확인합니다.")
    @ApiResponse(responseCode = "200", description = "상태 확인 성공 (상세 내용은 응답 본문 확인)")
    @GetMapping("/api/ldap/health")
    public ResponseEntity<Map<String, Object>> checkLdapHealth() {
        Map<String, Object> response = ldapHealthCheckUseCase.execute();
        return ResponseEntity.ok(response);
    }
}
