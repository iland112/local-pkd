package com.smartcoreinc.localpkd.ldif.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisResult;
import com.smartcoreinc.localpkd.ldif.service.LdapService;
import com.smartcoreinc.localpkd.ldif.service.LineTrackingLdifParser;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/ldif")
public class LdifController {

    private final LineTrackingLdifParser ldifParserService;
    private final LdapService ldapService;

    private LdifAnalysisResult analysisResult = null;

    public LdifController(LineTrackingLdifParser ldifParserService, LdapService ldapService) {
        this.ldifParserService = ldifParserService;
        this.ldapService = ldapService;
    }

    @GetMapping
    public String index() {
        return "ldif/index";
    }

    @PostMapping("/upload")
    public String uploadLdifFile(@RequestParam("file") MultipartFile file,
                                 RedirectAttributes redirectAttributes,
                                 Model model) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "파일을 선택해주세요.");
            return "redirect:/ldif";
        }

        if (!file.getOriginalFilename().toLowerCase().endsWith(".ldif")) {
            redirectAttributes.addFlashAttribute("error", "LDIF 파일만 업로드 가능합니다.");
            return "redirect:/ldif";
        }
        

        try {
            log.info("Processing LDIF file: {}", file.getOriginalFilename());

            analysisResult = null;
            analysisResult = ldifParserService.parseLdifFile(file);

            model.addAttribute("analysisResult", analysisResult);
            model.addAttribute("fileName", file.getOriginalFilename());

            if (analysisResult.isHasValidationErrors()) {
                model.addAttribute("warning", "LDIF 파일에 오류가 있습니다. 아래 오류를 확인해주세요.");
            } else {
                model.addAttribute("success", "LDIF 파일이 성공적으로 분석되었습니다.");
            }

            return "ldif/analysis-result";
        } catch (Exception e) {
            log.error("Error processing LDIF file: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "파일 처리 중 오류가 발생했습니다: " + e.getMessage());
            return "redirect:/ldif";
        }
    }

    @PostMapping("/save-to-ldap")
    public ResponseEntity<Map<String, Object>> saveToLdap() {
        Map<String, Object> response = new HashMap<>();

        log.debug("the number of entry: {}", analysisResult.getEntries().size());

        try {
            // Test LDAP connection first
            if (!ldapService.testConnection()) {
                response.put("success", false);
                response.put("message", "LDAP 서버에 연결할 수 없습니다");
                return ResponseEntity.ok(response);
            }

            // Save entries to LDAP
            int savedCount = ldapService.saveAllEntries(analysisResult.getEntries());

            response.put("success", true);
            response.put("message", String.format("%d개의 엔트리가 성공적으로 저장되었습니다.", savedCount));
            response.put("savedCount", savedCount);
            response.put("totalCount", analysisResult.getTotalEntries());
        } catch (Exception e) {
            log.error("Error saving to LDAP: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "LDAP 저장 주 오류가 발생했습니다: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/test-connection")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testLdapConnection() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean connected = ldapService.testConnection();
            response.put("success", connected);
            response.put("message", connected ? "LDAP 연결 성공" : "LDAP 연결 실패");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "연결 테스트 중 오류 발생: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, Model model) {
        log.error("Unexpected error in controller: {}", e.getMessage());
        model.addAttribute("error", "예상치 못한 오류가 발생했습니다: " + e.getMessage());
        return "error";
    }
}
