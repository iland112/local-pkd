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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.smartcoreinc.localpkd.SessionTaskManager;
import com.smartcoreinc.localpkd.enums.TaskType;
import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisResult;
import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisSummary;
import com.smartcoreinc.localpkd.ldif.dto.LdifEntryDto;
import com.smartcoreinc.localpkd.ldif.service.LdapService;
import com.smartcoreinc.localpkd.ldif.service.StreamlinedLDIFParser;
import com.smartcoreinc.localpkd.sse.Progress;
import com.smartcoreinc.localpkd.sse.ProgressEvent;
import com.smartcoreinc.localpkd.sse.ProgressPublisher;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/ldif")
public class LdifController {

    // Dependencies
    // private final LDIFParser ldifParserService;
    private final StreamlinedLDIFParser ldifParserService;
    private final LdapService ldapService;
    // Session and Task Info Container
    private final SessionTaskManager sessionTaskManager;
    private final ProgressPublisher progressPublisher;

    // EntryType 통계를 위한 실시간 카운터
    private final Map<String, Map<String, Integer>> sessionEntryTypeStats = new HashMap<>();

    // public LdifController(LDIFParser ldifParserService,
    // LdapService ldapService,
    // SessionTaskManager sessionTaskManager,
    // ProgressPublisher progressPublisher) {
    // this.ldifParserService = ldifParserService;
    // this.ldapService = ldapService;
    // this.sessionTaskManager = sessionTaskManager;
    // this.progressPublisher = progressPublisher;
    // }
    public LdifController(StreamlinedLDIFParser ldifParserService,
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

        // 파일 확장자 검증 .ldif 확장명을 가진 화일만 처리
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".ldif")) {
            redirectAttributes.addFlashAttribute("error", "LDIF 파일만 업로드 가능합니다.");
            return "redirect:/ldif";
        }

        log.info("Start processing LDIF file: {}", originalFilename);

        try {
            // 세션 ID 미리 생성 (EntryType 통계 추적용)
            String sessionId = generateSessionId();
            sessionEntryTypeStats.put(sessionId, new HashMap<>());

            // 분석 실행
            LdifAnalysisResult analysisResult = ldifParserService.parseLdifFile(file);

            // 세션에 결과 저장 전에 null 체크
            if (analysisResult == null) {
                redirectAttributes.addFlashAttribute("error", "파일 분석 결과를 얻을 수 없습니다.");
                return "redirect:/ldif";
            }

            // 분석 결과를 Session Task Manager 컨테이너에 저장
            sessionTaskManager.getSessionResults().put(sessionId, analysisResult);

            // 요약 정보만 뷰에 전달 - try-catch로 보호
            LdifAnalysisSummary summary = analysisResult.getSummary();
            if (summary == null) {
                log.error("LDIF 처리 결과 요약 정보가 생성되지 않았습니다.");
                throw new RuntimeException("Summary does not exists");
            }

            // certificateValidationStats null 체크 및 안전한 처리
            Map<String, Integer> certificateStats = summary.getCertificateValidationStats();
            if (certificateStats == null) {
                certificateStats = new HashMap<>();
                summary.setCertificateValidationStats(certificateStats);
            }

            // null 값들을 0으로 초기화
            certificateStats.putIfAbsent("total", 0);
            certificateStats.putIfAbsent("valid", 0);
            certificateStats.putIfAbsent("invalid", 0);
            certificateStats.putIfAbsent("totalMasterLists", 0);
            certificateStats.putIfAbsent("validMasterLists", 0);
            certificateStats.putIfAbsent("invalidMasterLists", 0);

            // EntryType 통계 null 체크 및 초기화
            Map<String, Integer> entryTypeStats = summary.getEntryTypeCount();
            if (entryTypeStats == null) {
                entryTypeStats = new HashMap<>();
                summary.setEntryTypeCount(entryTypeStats);
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
    @HxRequest
    @GetMapping(value = "/save-to-ldap/{sessionId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveToLdap(@PathVariable String sessionId) {
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

            int totalEntryCount = analysisResult.getEntries().size();
            log.debug("Number of entries to save: {}", totalEntryCount);

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

                // 진행률 업데이트 빈도 조정: 매 5개마다 또는 마지막에
                if ((i + 1) % 5 == 0 || i == entries.size() - 1) {
                    sendProgressUpdate(taskId, i + 1, totalCount, successCount);
                }

                // LDAP 서버 부하 방지를 위한 짧은 대기
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
        double value = (double) current / total; // 0.0 ~ 1.0 사이의 실제 진행률

        Progress progress = new Progress(value, TaskType.BIND.name());
        String message = String.format("작업[%s] 진행중: %d/%d 처리됨, 성공: %d개",
                taskId, current, total, successCount);

        // Add task ID to the progress event for better tracking
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("taskId", taskId);
        metadata.put("sessionId", getCurrentSessionId()); // implement this method

        ProgressEvent progressEvent = new ProgressEvent(progress, current, total, message, metadata);

        log.debug(
                "Sending progress update - Task: {}, Progress: {}%, Current: {}/{}, Success: {}",
                taskId, (int) (value * 100), current, total, successCount);

        try {
            progressPublisher.notifyProgressListeners(progressEvent);
            log.debug("Progress update sent successfully for task: {}", taskId);
        } catch (Exception e) {
            log.error("Failed to send progress update for task {}: {}", taskId, e.getMessage(), e);
        }
    }

    // Helper method to get current session ID from the running task
    private String getCurrentSessionId() {
        // This is a simplified implementation - you may need to adjust based on your architecture
        return sessionTaskManager.getSessionResults().keySet().stream().findFirst().orElse("unknown");
    }

    private void handleSaveCompletion(String taskId, int savedCount, int totalCount) {
        try {
            if (progressPublisher.getProgressListeners().isEmpty()) {
                log.warn("Currently, There is no subscripted listeners");
                return;
            }

            // 완료 시 진행률은 항상 1.0 (100%)
            Progress progress = new Progress(1.0, TaskType.BIND.name());
            String message = String.format("완료: %d/%d 저장됨 (성공률: %.1f%%)",
                    savedCount, totalCount,
                    (double) savedCount / totalCount * 100);

            ProgressEvent completionEvent = new ProgressEvent(progress, savedCount, totalCount, message);
            progressPublisher.notifyProgressListeners(completionEvent);

            log.info("Save process completed: {}/{} entries saved", savedCount, totalCount);
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
                Progress errorProgress = new Progress(0.0, TaskType.BIND.name());
                ProgressEvent errorEvent = new ProgressEvent(errorProgress, 0, 0, "Error: " + throwable.getMessage());
                progressPublisher.notifyProgressListeners(errorEvent);
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
