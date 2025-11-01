package com.smartcoreinc.localpkd.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import java.util.HashMap;
import java.util.Map;

/**
 * OpenLDAP 헬스 체크 컨트롤러
 *
 * OpenLDAP 서버 연결 상태를 확인합니다.
 *
 * @author SmartCore Inc.
 * @version 1.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class LdapHealthController {

    private final LdapTemplate ldapTemplate;

    @Value("${spring.ldap.urls}")
    private String ldapUrl;

    @Value("${spring.ldap.base}")
    private String ldapBase;

    /**
     * OpenLDAP 서버 연결 테스트
     *
     * @return 연결 상태 정보
     */
    @GetMapping("/api/ldap-health")
    public ResponseEntity<Map<String, Object>> checkLdapHealth() {
        Map<String, Object> response = new HashMap<>();

        try {
            // LDAP 연결 테스트
            DirContext context = ldapTemplate.getContextSource().getReadOnlyContext();

            if (context != null) {
                response.put("success", true);
                response.put("status", "connected");
                response.put("message", "OpenLDAP 연결 정상");
                response.put("server", maskCredentials(ldapUrl));
                response.put("baseDn", ldapBase);

                log.debug("LDAP health check: OK - {}", ldapUrl);

                // Close context
                try {
                    context.close();
                } catch (NamingException e) {
                    log.warn("Failed to close LDAP context", e);
                }

                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("status", "invalid");
                response.put("message", "OpenLDAP 연결이 유효하지 않습니다");

                log.warn("LDAP health check: Context is null");

                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("status", "error");
            response.put("message", "OpenLDAP 연결 실패: " + e.getMessage());

            log.error("LDAP health check failed", e);

            return ResponseEntity.ok(response);
        }
    }

    /**
     * URL에서 자격증명 마스킹
     *
     * @param url LDAP URL
     * @return 마스킹된 URL
     */
    private String maskCredentials(String url) {
        if (url == null) {
            return null;
        }
        // IP 주소만 남기고 자격증명 제거
        return url.replaceAll("://([^:@]+):([^@]+)@", "://*****:*****@");
    }
}
