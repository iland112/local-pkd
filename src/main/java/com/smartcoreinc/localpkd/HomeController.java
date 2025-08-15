package com.smartcoreinc.localpkd;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.smartcoreinc.localpkd.ldif.service.LdapService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
@RequestMapping("/")
public class HomeController {

    private final LdapService ldapService;

    public HomeController(LdapService ldapService) {
        this.ldapService = ldapService;
    }

    @GetMapping
    public String index(Model model) {
        return "index";
    }

    @GetMapping("favicon.ico")
    @ResponseBody
    void returnNoFavicon() {
    }

    @GetMapping(value = "/ldap-test-connection", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testLdapConnection() {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean connected = ldapService.testConnection();
            response.put("success", connected);
            response.put("message", connected ? "LDAP 연결 성공" : "LDAP 연결 실패");
        } catch (Exception e) {
            log.error("Error testing LDAP connection: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "연결 테스트 중 오류 발생: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

}
