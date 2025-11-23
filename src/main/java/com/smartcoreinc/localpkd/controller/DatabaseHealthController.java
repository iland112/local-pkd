package com.smartcoreinc.localpkd.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * 데이터베이스 헬스 체크 컨트롤러
 *
 * PostgreSQL 데이터베이스 연결 상태를 확인합니다.
 *
 * @author SmartCore Inc.
 * @version 1.0
 */
@Tag(name = "시스템 상태 API")
@Slf4j
@RestController
@RequiredArgsConstructor
public class DatabaseHealthController {

    private final DataSource dataSource;

    @Operation(summary = "데이터베이스 상태 확인",
               description = "PostgreSQL 데이터베이스의 연결 상태를 확인합니다.")
    @ApiResponse(responseCode = "200", description = "상태 확인 성공 (상세 내용은 응답 본문 확인)")
    @GetMapping("/api/database-health")
    public ResponseEntity<Map<String, Object>> checkDatabaseHealth() {
        Map<String, Object> response = new HashMap<>();

        try (Connection connection = dataSource.getConnection()) {
            boolean isValid = connection.isValid(5); // 5초 타임아웃

            if (isValid) {
                String databaseProductName = connection.getMetaData().getDatabaseProductName();
                String databaseProductVersion = connection.getMetaData().getDatabaseProductVersion();
                String url = connection.getMetaData().getURL();

                response.put("success", true);
                response.put("status", "connected");
                response.put("message", "데이터베이스 연결 정상");
                response.put("database", databaseProductName);
                response.put("version", databaseProductVersion);
                response.put("url", maskPassword(url));

                log.debug("Database health check: OK - {} {}", databaseProductName, databaseProductVersion);

                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("status", "invalid");
                response.put("message", "데이터베이스 연결이 유효하지 않습니다");

                log.warn("Database health check: Connection is not valid");

                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("status", "error");
            response.put("message", "데이터베이스 연결 실패: " + e.getMessage());

            log.error("Database health check failed", e);

            return ResponseEntity.ok(response);
        }
    }

    /**
     * URL에서 비밀번호 마스킹
     *
     * @param url JDBC URL
     * @return 마스킹된 URL
     */
    private String maskPassword(String url) {
        if (url == null) {
            return null;
        }
        // password=xxx 형태를 password=*** 로 변경
        return url.replaceAll("password=([^&;]+)", "password=***");
    }
}
