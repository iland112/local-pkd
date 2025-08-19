package com.smartcoreinc.localpkd.ldif.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.smartcoreinc.localpkd.SessionTaskManager;
import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisResult;
import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisSummary;
import com.smartcoreinc.localpkd.ldif.dto.LdifEntryDto;
import com.smartcoreinc.localpkd.ldif.service.LdapService;
import com.smartcoreinc.localpkd.ldif.service.LdifParser;
import com.smartcoreinc.localpkd.sse.Progress;
import com.smartcoreinc.localpkd.sse.ProgressEvent;
import com.smartcoreinc.localpkd.sse.ProgressPublisher;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/ldif")
public class LdifController {

    // Dependencies
    private final LdifParser ldifParserService;
    private final LdapService ldapService;
    // Session and Task Info Container
    private final SessionTaskManager sessionTaskManager;
    private final ProgressPublisher progressPublisher;

    public LdifController(LdifParser ldifParserService,
            LdapService ldapService,
            SessionTaskManager sessionTaskManager,
            ProgressPublisher progressPublisher) {
        this.ldifParserService = ldifParserService;
        this.ldapService = ldapService;
        this.sessionTaskManager = sessionTaskManager;
        this.progressPublisher = progressPublisher;
    }

    @GetMapping
    public String index() {
        return "ldif/upload-ldif";
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadLdifFile(@RequestParam("file") MultipartFile file,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        log.info("=== File upload endpoint called ===");
        
        // Upload된 ldif 파일 유효성 검증 
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

            // 세션에 결과 저장 전에 null 체크
            if (analysisResult == null) {
                redirectAttributes.addFlashAttribute("error", "파일 분석 결과를 얻을 수 없습니다.");
                return "redirect:/ldif";
            }

            String sessionId = generateSessionId();
            sessionTaskManager.getSessionResults().put(sessionId, analysisResult);
                        
            // 요약 정보만 뷰에 전달 - try-catch로 보호
            LdifAnalysisSummary summary = analysisResult.getSummary();
            if (summary == null) {
                log.error("Summary does not exists");
                throw new RuntimeException("Summary does not exists");
            }
            model.addAttribute("summary", summary);
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("fileName", originalFilename);
            
            if (summary.isHasValidationErrors()) {
                model.addAttribute("warning", "LDIF 파일에 오류가 있습니다. 아래 오류를 확인해주세요.");
            } else {
                model.addAttribute("success", "LDIF 파일이 성공적으로 분석되었습니다.");
            }
            
            log.info("Session {} created with {} entries", sessionId, analysisResult.getSummary().getTotalEntries());
            // 1초 대기 후 Anlaysis Result 페이지로 리다렉트
            // sleepQuietly(1000);
            return "ldif/analysis-result";
            
        } catch (StackOverflowError e) {
            log.error("StackOverflowError during file processing: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "파일이 너무 크거나 복잡합니다. 더 작은 파일로 시도해주세요.");
            return "redirect:/ldif";
        } catch (OutOfMemoryError e) {
            log.error("OutOfMemoryError during file processing: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "메모리가 부족합니다. 더 작은 파일로 시도해주세요.");
            return "redirect:/ldif";
        } catch (Exception e) {
            log.error("Error processing LDIF file: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "파일 처리 중 오류가 발생했습니다: " + e.getMessage());
            return "redirect:/ldif";
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

            LdifAnalysisResult analysisResult = sessionTaskManager.getSessionResults().get(sessionId);
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

            // 작업 시작 표시
            sessionTaskManager.getRunningTasks().put(taskId, new AtomicBoolean(true));

            CompletableFuture.supplyAsync(() -> {
                return saveEntriesWithProgress(analysisResult.getEntries(), taskId);
            }).thenAccept(savedCount -> {
                handleSaveCompletion(taskId, savedCount, analysisResult.getSummary().getTotalEntries());
            }).exceptionally(throwable -> {
                handleSaveError(taskId, throwable);
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

    @GetMapping(value = "/cancel-task", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelTask(@RequestParam String taskId) {
        Map<String, Object> response = new HashMap<>();

        try {
            AtomicBoolean taskFlag = sessionTaskManager.getRunningTasks().get(taskId);
            if (taskFlag != null) {
                taskFlag.set(false);
                cleanupTask(taskId);
                response.put("success", true);
                response.put("message", "작업이 취소되었습니다.");
                log.info("Task {} cancelled by user", taskId);
            } else {
                response.put("success", false);
                response.put("message", "취소할 작업을 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            log.error("Error cancelling task {}: {}", taskId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "작업 취소 중 오류가 발생했습니다.");
        }

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, Model model) {
        log.error("Unexpected error in LdifController: {}", e.getMessage(), e);
        model.addAttribute("error", "예상치 못한 오류가 발생했습니다: " + e.getMessage());
        return "error";
    }

    private int saveEntriesWithProgress(List<LdifEntryDto> entries, String taskId) {
        int successCount = 0;
        int totalCount = entries.size();
        AtomicBoolean shouldContinue = sessionTaskManager.getRunningTasks().get(taskId);

        log.info("Starting to save {} entries for task: {}", totalCount, taskId);

        for (int i = 0; i < entries.size(); i++) {
            // 작업 취소 확인
            if (shouldContinue == null || !shouldContinue.get()) {
                log.info("Task {} cancelled, stopping at entry {}/{}", taskId, i, totalCount);
                break;
            }

            try {
                if (ldapService.saveEntry(entries.get(i))) {
                    successCount++;
                }

                // 진행률 업데이트 (매 10개마다 또는 마지막에)
                if ((i + 1) % 10 == 0 || i == entries.size() - 1) {
                    sendProgressUpdate(taskId, i + 1, totalCount, successCount);
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

    private void sendProgressUpdate(String taskId, int current, int total, int successCount) {
        // SseEmitterWrapper wrapper = activeEmitters.get(taskId);
        // if (wrapper != null && wrapper.isConnected()) {
        // Map<String, Object> progressData = new HashMap<>();
        // progressData.put("type", "progress");
        // progressData.put("current", current);
        // progressData.put("total", total);
        // progressData.put("percentage", Math.round((double) current / total * 100));
        // progressData.put("successCount", successCount);

        // if (!wrapper.sendSafely(progressData)) {
        // log.debug("Failed to send progress update for task: {}, client likely
        // disconnected", taskId);
        // }
        // }
        double value = Math.round((double) current / total);
        Progress progress = new Progress(value, taskId);
        ProgressEvent progressEvent = new ProgressEvent(progress, current, total,
                "success count: %d".formatted(successCount));
        progressPublisher.notifyProgressListeners(progressEvent);
    }

    private void handleSaveCompletion(String taskId, int savedCount, int totalCount) {
        try {
            if (progressPublisher.getProgressListeners().isEmpty()) {
                log.warn("Currently, There is no subscripted listeners");
                return;
            } else {
                Map<String, Object> completeData = new HashMap<>();
                completeData.put("type", "complete");
                completeData.put("savedCount", savedCount);
                completeData.put("totalCount", totalCount);
                progressPublisher.notifyProgressListeners(null);
                log.info("Save process completed: {}/{} entries saved", savedCount, totalCount);
            }
        } catch (Exception e) {
            log.error("Error in save completion handling: {}", e.getMessage(), e);
        } finally {
            cleanupTask(taskId);
        }
    }

    private void handleSaveError(String taskId, Throwable throwable) {
        log.error("Error during save process for task {}: {}", taskId, throwable.getMessage(), throwable);

        try {
            if (progressPublisher.getProgressListeners().isEmpty()) {
                log.warn("Currently, There is no subscripted listeners");
                return;
            } else {
                Map<String, Object> errorData = new HashMap<>();
                errorData.put("type", "error");
                errorData.put("message", "저장 중 오류가 발생했습니다: " + throwable.getMessage());
                progressPublisher.notifyProgressListeners(null);
            }
        } catch (Exception e) {
            log.error("Error in save error handling: {}", e.getMessage(), e);
        } finally {
            cleanupTask(taskId);
        }
    }

    private void cleanupTask(String taskId) {
        try {
            sessionTaskManager.getRunningTasks().remove(taskId);
            log.debug("Cleaned up resources for task: {}", taskId);
        } catch (Exception e) {
            log.error("Error during task cleanup for {}: {}", taskId, e.getMessage(), e);
        }
    }

    private String generateSessionId() {
        return "session_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000);
    }

    private String generateTaskId() {
        return "task_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000);
    }

    private void sleepQuietly(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
