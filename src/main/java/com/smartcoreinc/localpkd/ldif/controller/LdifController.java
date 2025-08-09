package com.smartcoreinc.localpkd.ldif.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisResult;
import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisSummary;
import com.smartcoreinc.localpkd.ldif.dto.LdifEntryDto;
import com.smartcoreinc.localpkd.ldif.service.LdapService;
import com.smartcoreinc.localpkd.ldif.service.LineTrackingLdifParser;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/ldif")
public class LdifController {

    private final LineTrackingLdifParser ldifParserService;
    private final LdapService ldapService;

    // 세션별 분석 결과 저장 (실제로는 Redis나 DB 사용 권장)
    private final Map<String, LdifAnalysisResult> sessionResults = new ConcurrentHashMap<>();

    // SSE Emitter 관리
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    public LdifController(LineTrackingLdifParser ldifParserService, LdapService ldapService) {
        this.ldifParserService = ldifParserService;
        this.ldapService = ldapService;
    }

    @GetMapping
    public String index() {
        return "ldif/index";
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadLdifFile(@RequestParam("file") MultipartFile file,
                                 RedirectAttributes redirectAttributes,
                                 Model model) {
        log.info("=== File upload endpoint called ===");
        
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "파일을 선택해주세요.");
            return "redirect:/ldif";
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".ldif")) {
            redirectAttributes.addFlashAttribute("error", "LDIF 파일만 업로드 가능합니다.");
            return "redirect:/ldif";
        }

        try {
            log.info("Processing LDIF file: {}", originalFilename);

            // 분석 실행
            LdifAnalysisResult analysisResult = ldifParserService.parseLdifFile(file);

            // 세션에 결과 저장
            String sessionId = generateSessionId();
            sessionResults.put(sessionId, analysisResult);
            
            log.info("Session {} created with {} entries", sessionId, analysisResult.getTotalEntries());
            
            // 요약 정보만 뷰에 전달
            LdifAnalysisSummary summary = createSummary(analysisResult);
            model.addAttribute("analysisResult", summary);
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("fileName", originalFilename);

            if (analysisResult.isHasValidationErrors()) {
                model.addAttribute("warning", "LDIF 파일에 오류가 있습니다. 아래 오류를 확인해주세요.");
            } else {
                model.addAttribute("success", "LDIF 파일이 성공적으로 분석되었습니다.");
            }

            return "ldif/analysis-result";
        } catch (Exception e) {
            log.error("Error processing LDIF file: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "파일 처리 중 오류가 발생했습니다: " + e.getMessage());
            return "redirect:/ldif";
        }
    }

    @GetMapping(value = "/entries", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Page<LdifEntryDto>> getEntries(
        @RequestParam String sessionId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        try {
            LdifAnalysisResult result = sessionResults.get(sessionId);
            if (result == null) {
                log.warn("Session not found: {}", sessionId);
                return ResponseEntity.notFound().build();
            }

            Pageable pageable = PageRequest.of(page, size);
            List<LdifEntryDto> entries = result.getEntries();

            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), entries.size());

            List<LdifEntryDto> pageContent = entries.subList(start, end);
            Page<LdifEntryDto> pageResult = new PageImpl<>(pageContent, pageable, entries.size());

            return ResponseEntity.ok(pageResult);
        } catch (Exception e) {
            log.error("Error getting entries: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // LDAP 저장 엔드포인트 - GET 방식으로 통일
    @GetMapping(value = "/save-to-ldap", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveToLdap(@RequestParam String sessionId) {
        log.info("=== LDAP Save endpoint called with sessionId: {} ===", sessionId);
        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Starting LDAP save process for session: {}", sessionId);
            
            LdifAnalysisResult analysisResult = sessionResults.get(sessionId);
            if (analysisResult == null) {
                log.warn("Session not found: {}", sessionId);
                response.put("success", false);
                response.put("message", "세션이 만료되었습니다. 파일을 다시 업로드 해주세요.");
                return ResponseEntity.badRequest().body(response);
            }

            log.debug("Number of entries to save: {}", analysisResult.getEntries().size());

            // Test LDAP connection first
            if (!ldapService.testConnection()) {
                response.put("success", false);
                response.put("message", "LDAP 서버에 연결할 수 없습니다");
                return ResponseEntity.ok(response);
            }

            // 비동기로 저장 작업 수행
            String taskId = generateTaskId();
            log.info("Generated task ID: {} for session: {}", taskId, sessionId);
            
            CompletableFuture.supplyAsync(() -> {
                return saveEntriesWithProgress(analysisResult.getEntries(), taskId);
            }).thenAccept(savedCount -> {
                // 완료 후 결과 전송
                SseEmitter emitter = activeEmitters.get(taskId);
                if (emitter != null) {
                    try {
                        Map<String, Object> completeData = new HashMap<>();
                        completeData.put("type", "complete");
                        completeData.put("savedCount", savedCount);
                        completeData.put("totalCount", analysisResult.getTotalEntries());
                        emitter.send(SseEmitter.event().data(completeData));
                        emitter.complete();
                        log.info("Save process completed: {}/{} entries saved", savedCount, analysisResult.getTotalEntries());
                    } catch (Exception e) {
                        log.error("Error sending completion event: {}", e.getMessage(), e);
                    } finally {
                        activeEmitters.remove(taskId);
                    }
                }
            }).exceptionally(throwable -> {
                // 에러 처리
                log.error("Error during save process: {}", throwable.getMessage(), throwable);
                SseEmitter emitter = activeEmitters.get(taskId);
                if (emitter != null) {
                    try {
                        Map<String, Object> errorData = new HashMap<>();
                        errorData.put("type", "error");
                        errorData.put("message", "저장 중 오류가 발생했습니다: " + throwable.getMessage());
                        emitter.send(SseEmitter.event().data(errorData));
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("Error sending error event: {}", e.getMessage(), e);
                    } finally {
                        activeEmitters.remove(taskId);
                    }
                }
                return null;
            });

            response.put("success", true);
            response.put("taskId", taskId);
            response.put("message", "저장 작업이 시작되었습니다.");
            
        } catch (Exception e) {
            log.error("Error saving to LDAP: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "LDAP 저장 중 오류가 발생했습니다: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getProgress(@RequestParam String taskId) {
        log.info("Starting progress tracking for task: {}", taskId);
        
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        activeEmitters.put(taskId, emitter);

        emitter.onCompletion(() -> {
            log.debug("SSE completed for task: {}", taskId);
            activeEmitters.remove(taskId);
        });
        
        emitter.onTimeout(() -> {
            log.warn("SSE timeout for task: {}", taskId);
            activeEmitters.remove(taskId);
        });
        
        emitter.onError((e) -> {
            log.error("SSE error for task {}: {}", taskId, e.getMessage(), e);
            activeEmitters.remove(taskId);
        });

        return emitter;
    }

    @GetMapping(value = "/test-connection", produces = MediaType.APPLICATION_JSON_VALUE)
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

    private int saveEntriesWithProgress(List<LdifEntryDto> entries, String taskId) {
        int successCount = 0;
        int totalCount = entries.size();

        log.info("Starting to save {} entries for task: {}", totalCount, taskId);

        for (int i = 0; i < entries.size(); i++) {
            try {
                if (ldapService.saveEntry(entries.get(i))) {
                    successCount++;
                }

                // 진행률 업데이트 (매 10개마다 또는 마지막에)
                if ((i + 1) % 10 == 0 || i == entries.size() - 1) {
                    SseEmitter emitter = activeEmitters.get(taskId);
                    if (emitter != null) {
                        try {
                            Map<String, Object> progressData = new HashMap<>();
                            progressData.put("type", "progress");
                            progressData.put("current", i + 1);
                            progressData.put("total", totalCount);
                            progressData.put("percentage", Math.round((double) (i + 1) / totalCount * 100));
                            progressData.put("successCount", successCount);
                            emitter.send(SseEmitter.event().data(progressData));
                        } catch (Exception e) {
                            log.error("Error sending progress update: {}", e.getMessage(), e);
                            break;
                        }
                    }
                }

                // Add small delay to avoid overwhelming LDAP server
                Thread.sleep(50);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Save operation interrupted for task: {}", taskId);
                break;
            } catch (Exception e) {
                log.error("Unexpected error saving entry {}: {}", entries.get(i).getDn(), e.getMessage(), e);
            }
        }

        log.info("Completed saving {}/{} entries to LDAP for task: {}", successCount, entries.size(), taskId);
        return successCount;
    }

    private LdifAnalysisSummary createSummary(LdifAnalysisResult result) {
        LdifAnalysisSummary summary = new LdifAnalysisSummary();
        summary.setTotalEntries(result.getTotalEntries());
        summary.setAddEntries(result.getAddEntries());
        summary.setModifyEntries(result.getModifyEntries());
        summary.setDeleteEntries(result.getDeleteEntries());
        summary.setHasValidationErrors(result.isHasValidationErrors());
        summary.setObjectClassCount(result.getObjectClassCount());
        summary.setCertificateValidationStats(result.getCertificateValidationStats());

        // 에러와 경고는 처음 10개만 포함
        if (result.getErrors() != null) {
            summary.setErrors(result.getErrors().stream().limit(10).collect(Collectors.toList()));
            summary.setTotalErrors(result.getErrors().size());
        }
        
        if (result.getWarnings() != null) {
            summary.setWarnings(result.getWarnings().stream().limit(10).collect(Collectors.toList()));
            summary.setTotalWarnings(result.getWarnings().size());
        }
        
        return summary;
    }

    private String generateSessionId() {
        return "session_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    private String generateTaskId() {
        return "task_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, Model model) {
        log.error("Unexpected error in controller: {}", e.getMessage(), e);
        model.addAttribute("error", "예상치 못한 오류가 발생했습니다: " + e.getMessage());
        return "error";
    }
}
