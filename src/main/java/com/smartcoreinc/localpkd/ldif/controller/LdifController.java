package com.smartcoreinc.localpkd.ldif.controller;

import java.util.ArrayList;
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
import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisResult;
import com.smartcoreinc.localpkd.ldif.dto.LdifAnalysisSummary;
import com.smartcoreinc.localpkd.ldif.dto.LdifEntryDto;
import com.smartcoreinc.localpkd.ldif.service.LdapService;
import com.smartcoreinc.localpkd.ldif.service.LDIFParser;
import com.smartcoreinc.localpkd.sse.broker.LdapProgressBroker;
import com.smartcoreinc.localpkd.sse.event.LdapSaveEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/ldif")
public class LdifController {

    // Dependencies
    // private final LDIFParser ldifParserService;
    private final LDIFParser ldifParserService;
    private final LdapService ldapService;
    // Session and Task Info Container
    private final SessionTaskManager sessionTaskManager;

    private final LdapProgressBroker ldapProgressBroker;

    public LdifController(LDIFParser ldifParserService,
            LdapService ldapService,
            SessionTaskManager sessionTaskManager,
            LdapProgressBroker ldapProgressBroker) {
        this.ldifParserService = ldifParserService;
        this.ldapService = ldapService;
        this.sessionTaskManager = sessionTaskManager;
        this.ldapProgressBroker = ldapProgressBroker;
    }

    @GetMapping
    public String index(HttpServletRequest request, Model model) {
        // 세션 ID를 모델에 추가하여 Thymeleaf에서 사용 가능하게 함
        String sessionId = request.getSession().getId();
        model.addAttribute("sessionId", sessionId);
        
        return "ldif/upload-ldif";
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadLdifFile(
            @RequestParam("file") MultipartFile file,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request,
            Model model) {
        String sessionId = request.getSession().getId();
        log.info("=== LDIF parsing request started for session: {} ===", sessionId);

        try {
            // 1. Upload된 ldif 파일 유효성 검증
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
    
            log.info("Starting LDIF parsing for file: {} (session: {})", originalFilename, sessionId);
            
            // 2. LDIF 파싱 실행 (진행률 발행은 StreamlinedLDIFParser에서 처리)
            // sessionId를 파라미터로 전달하여 파싱 중 진행률 발행 가능하게 함
            LdifAnalysisResult analysisResult = ldifParserService.parseLdifFile(file, sessionId);
            if (analysisResult == null) {
                log.error("LDIF parsing returned null result for session: {}", sessionId);
                redirectAttributes.addFlashAttribute("error", "파일 분석 결과를 얻을 수 없습니다.");
                return "redirect:/ldif";
            }

            // 3. 분석 결과 후처리
            LdifAnalysisSummary summary = analysisResult.getSummary();
            if (summary == null) {
                log.error("LDIF analysis summary is null for session: {}", sessionId);
                throw new RuntimeException("Analysis summary is missing");
            }

            // 4. null 값 안전 처리
            ensureSummaryIntegrity(summary);

            // 5. 세션에 결과 저장
            sessionTaskManager.getSessionResults().put(sessionId, analysisResult);

            // 6. 모델에 데이터 추가
            model.addAttribute("summary", summary);
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("fileName", originalFilename);

            // 7. 결과에 따른 메시지 설정
            if (summary.isHasValidationErrors()) {
                model.addAttribute("warning", 
                    String.format("LDIF 파일에 %d개의 오류가 발견되었습니다. 상세 내용을 확인해주세요.", 
                                summary.getErrors().size()));
            } else {
                model.addAttribute("success", 
                    String.format("LDIF 파일이 성공적으로 분석되었습니다. %d개의 엔트리가 처리되었습니다.", 
                                summary.getTotalEntries()));
            }

            log.info("LDIF parsing completed successfully - Session: {}, Entries: {}, Errors: {}, Warnings: {}", 
                    sessionId, summary.getTotalEntries(), summary.getErrors().size(), summary.getWarnings().size());

            return "ldif/analysis-result";

        } catch (StackOverflowError e) {
            log.error("StackOverflowError during LDIF parsing for session {}: {}", sessionId, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "파일이 너무 크거나 복잡합니다. 더 작은 파일로 시도해주세요.");
            return "redirect:/ldif";
        } catch (OutOfMemoryError e) {
            log.error("OutOfMemoryError during LDIF parsing for session {}: {}", sessionId, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "메모리가 부족합니다. 더 작은 파일로 시도해주세요.");
            return "redirect:/ldif";
        } catch (Exception e) {
            log.error("Error during LDIF parsing for session {}: {}", sessionId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "파일 처리 중 오류가 발생했습니다: " + e.getMessage());
            return "redirect:/ldif";
        }
    }

    /**
     * LDAP 저장 엔드포인트
     */
    @PostMapping(value = "/save-to-ldap/{sessionId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveToLdap(@PathVariable String sessionId) {
        log.info("=== LDAP Save endpoint called with sessionId: {} ===", sessionId);
        Map<String, Object> response = new HashMap<>();

        try {
            // 1. 세션 데이터 확인
            LdifAnalysisResult analysisResult = sessionTaskManager.getSessionResults().get(sessionId);
            if (analysisResult == null) {
                log.warn("Analysis result not found for session: {}", sessionId);
                response.put("success", false);
                response.put("message", "세션이 만료되었습니다. 파일을 다시 업로드해주세요.");
                return ResponseEntity.badRequest().body(response);
            }

            // 2. LDAP 연결 테스트
            if (!ldapService.testConnection()) {
                log.warn("LDAP connection test failed for session: {}", sessionId);
                response.put("success", false);
                response.put("message", "LDAP 서버에 연결할 수 없습니다");
                return ResponseEntity.ok(response);
            }

            // 3. 저장할 엔트리 확인
            List<LdifEntryDto> entries = analysisResult.getEntries();
            if (entries == null || entries.isEmpty()) {
                log.warn("No entries to save for session: {}", sessionId);
                response.put("success", false);
                response.put("message", "저장할 엔트리가 없습니다.");
                return ResponseEntity.ok(response);
            }

            // 4. 태스크 시작
            String taskId = generateTaskId();
            sessionTaskManager.getRunningTasks().put(taskId, new AtomicBoolean(true));
            
            log.info("LDAP save task created - TaskID: {}, Session: {}, Entries: {}", 
                    taskId, sessionId, entries.size());

            // 5. LDAP 저장 브로커 세션 시작
            ldapProgressBroker.startSaveTask(taskId);

            // 6. 비동기 저장 작업 시작
            CompletableFuture.supplyAsync(() -> {
                return saveEntriesWithProgress(entries, taskId, sessionId);
            }).thenAccept(savedCount -> {
                handleSaveCompletion(taskId, savedCount, entries.size());
            }).exceptionally(throwable -> {
                handleSaveError(taskId, throwable);
                return null;
            });

            // 7. 성공 응답
            response.put("success", true);
            response.put("taskId", taskId);
            response.put("totalEntries", entries.size());
            response.put("message", "LDAP 저장 작업이 시작되었습니다.");

        } catch (Exception e) {
            log.error("Error starting LDAP save for session {}: {}", sessionId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "저장 작업 시작 중 오류가 발생했습니다: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 작업 취소
     */
    @GetMapping(value = "/cancel-task", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelTask(@RequestParam String taskId) {
        Map<String, Object> response = new HashMap<>();

        try {
            AtomicBoolean taskFlag = sessionTaskManager.getRunningTasks().get(taskId);
            if (taskFlag != null) {
                taskFlag.set(false);
                cleanupTask(taskId);
                ldapProgressBroker.completeSaveTask(taskId);
                
                response.put("success", true);
                response.put("message", "작업이 취소되었습니다.");
                log.info("LDAP save task cancelled by user - TaskID: {}", taskId);
            } else {
                response.put("success", false);
                response.put("message", "취소할 작업을 찾을 수 없습니다.");
                log.warn("Task not found for cancellation: {}", taskId);
            }
        } catch (Exception e) {
            log.error("Error cancelling LDAP save task {}: {}", taskId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "작업 취소 중 오류가 발생했습니다.");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * LDAP 엔트리 저장 (진행률 발행 포함)
     */
    private int saveEntriesWithProgress(List<LdifEntryDto> entries, String taskId, String sessionId) {
        int successCount = 0;
        int failureCount = 0;
        int totalCount = entries.size();
        AtomicBoolean shouldContinue = sessionTaskManager.getRunningTasks().get(taskId);

        log.info("Starting LDAP save process - Task: {}, Session: {}, Entries: {}", taskId, sessionId, totalCount);

        // 시작 이벤트 발행
        publishLdapSaveProgress(taskId, 0, totalCount, 0, 0, "LDAP 저장 시작...");

        for (int i = 0; i < entries.size(); i++) {
            // 작업 취소 확인
            if (shouldContinue == null || !shouldContinue.get()) {
                log.info("LDAP save task cancelled - Task: {}, Entry: {}/{}", taskId, i, totalCount);
                publishLdapSaveProgress(taskId, i, totalCount, successCount, failureCount, "작업이 취소되었습니다.");
                break;
            }

            try {
                LdifEntryDto currentEntry = entries.get(i);
                boolean saveResult = ldapService.saveEntry(currentEntry);
                
                if (saveResult) {
                    successCount++;
                } else {
                    failureCount++;
                }

                // 진행률 업데이트 빈도 조정
                boolean shouldUpdateProgress = (i < 10) ||
                                             ((i + 1) % 3 == 0) ||
                                             (i == entries.size() - 1) ||
                                             (i > 0 && i % 50 == 0);

                if (shouldUpdateProgress) {
                    String progressMessage = String.format("Entry 저장 중: %s", 
                        truncateString(currentEntry.getDn(), 60));
                    publishLdapSaveProgress(taskId, i + 1, totalCount, successCount, failureCount, progressMessage);
                }

                // LDAP 서버 부하 방지
                Thread.sleep(30);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("LDAP save operation interrupted for task: {}", taskId);
                publishLdapSaveProgress(taskId, i, totalCount, successCount, failureCount, "작업이 중단되었습니다.");
                break;
            } catch (Exception e) {
                failureCount++;
                log.error("Error saving entry {}: {}", entries.get(i).getDn(), e.getMessage());
                
                // 에러가 발생해도 진행률 업데이트
                if ((i + 1) % 5 == 0 || i == entries.size() - 1) {
                    publishLdapSaveProgress(taskId, i + 1, totalCount, successCount, failureCount, 
                        "저장 중 오류 발생: " + e.getMessage());
                }
            }
        }
        
        // 최종 완료 이벤트 발행
        publishLdapSaveProgress(taskId, totalCount, totalCount, successCount, failureCount, 
            String.format("저장 완료: %d개 성공, %d개 실패", successCount, failureCount));

        log.info("LDAP save completed - Task: {}, Success: {}/{}, Failure: {}", 
                taskId, successCount, totalCount, failureCount);
        return successCount;
    }

    /**
     * LDAP 저장 진행률 발행
     */
    private void publishLdapSaveProgress(String taskId, int processed, int total, 
                                       int success, int failure, String message) {
        try {
            double progress = total > 0 ? (double) processed / total : 0.0;
            
            LdapSaveEvent event = new LdapSaveEvent(
                taskId,
                progress,
                processed,
                total,
                success,
                failure,
                progress >= 1.0 ? "completed" : "in-progress",
                message,
                Map.of(
                    "timestamp", System.currentTimeMillis(),
                    "successRate", total > 0 ? (double) success / processed * 100 : 0.0
                )
            );
            
            ldapProgressBroker.publishSaveProgress(taskId, event);
            
            log.debug("LDAP save progress published - Task: {}, Progress: {}%, Success: {}/{}", 
                     taskId, (int)(progress * 100), success, processed);
                     
        } catch (Exception e) {
            log.error("Failed to publish LDAP save progress for task {}: {}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 저장 완료 처리
     */
    private void handleSaveCompletion(String taskId, int savedCount, int totalCount) {
        try {
            log.info("LDAP save process completed - Task: {}, Saved: {}/{}", taskId, savedCount, totalCount);

            // 최종 완료 이벤트 (성공률 포함)
            double successRate = totalCount > 0 ? (double) savedCount / totalCount * 100 : 0.0;
            String completionMessage = String.format("저장 완료: %d/%d 엔트리 저장됨 (성공률: %.1f%%)", 
                savedCount, totalCount, successRate);

            LdapSaveEvent completionEvent = new LdapSaveEvent(
                taskId,
                1.0,
                totalCount,
                totalCount,
                savedCount,
                totalCount - savedCount,
                "completed",
                completionMessage,
                Map.of(
                    "completed", true,
                    "finalSuccessRate", successRate,
                    "timestamp", System.currentTimeMillis()
                )
            );

            ldapProgressBroker.publishSaveProgress(taskId, completionEvent);

        } catch (Exception e) {
            log.error("Error in LDAP save completion handling for task {}: {}", taskId, e.getMessage(), e);
        } finally {
            // 작업 정리를 지연시켜 클라이언트가 완료 메시지를 받을 시간 확보
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(3000); // 3초 대기
                    cleanupTask(taskId);
                    ldapProgressBroker.completeSaveTask(taskId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    /**
     * 저장 오류 처리
     */
    private void handleSaveError(String taskId, Throwable throwable) {
        log.error("Error during LDAP save process for task {}: {}", taskId, throwable.getMessage(), throwable);

        try {
            LdapSaveEvent errorEvent = new LdapSaveEvent(
                taskId,
                0.0,
                0,
                0,
                0,
                0,
                "error",
                "저장 중 오류 발생: " + throwable.getMessage(),
                Map.of(
                    "error", true,
                    "errorMessage", throwable.getMessage(),
                    "timestamp", System.currentTimeMillis()
                )
            );
            
            ldapProgressBroker.publishSaveProgress(taskId, errorEvent);
            
        } catch (Exception e) {
            log.error("Error in LDAP save error handling for task {}: {}", taskId, e.getMessage(), e);
        } finally {
            cleanupTask(taskId);
            ldapProgressBroker.completeSaveTask(taskId);
        }
    }

    /**
     * Summary 무결성 보장
     */
    private void ensureSummaryIntegrity(LdifAnalysisSummary summary) {
        // Certificate validation stats 초기화
        Map<String, Integer> certificateStats = summary.getCertificateValidationStats();
        if (certificateStats == null) {
            certificateStats = new HashMap<>();
            summary.setCertificateValidationStats(certificateStats);
        }

        // 필수 키들 초기화
        certificateStats.putIfAbsent("total", 0);
        certificateStats.putIfAbsent("valid", 0);
        certificateStats.putIfAbsent("invalid", 0);
        certificateStats.putIfAbsent("totalMasterLists", 0);
        certificateStats.putIfAbsent("validMasterLists", 0);
        certificateStats.putIfAbsent("invalidMasterLists", 0);

        // EntryType 통계 초기화
        Map<String, Integer> entryTypeStats = summary.getEntryTypeCount();
        if (entryTypeStats == null) {
            entryTypeStats = new HashMap<>();
            summary.setEntryTypeCount(entryTypeStats);
        }

        // 에러/경고 리스트 초기화
        if (summary.getErrors() == null) {
            summary.setErrors(new ArrayList<>());
        }
        if (summary.getWarnings() == null) {
            summary.setWarnings(new ArrayList<>());
        }
    }

    /**
     * 작업 정리
     */
    private void cleanupTask(String taskId) {
        try {
            sessionTaskManager.getRunningTasks().remove(taskId);
            log.debug("Task resources cleaned up: {}", taskId);
        } catch (Exception e) {
            log.error("Error during task cleanup for {}: {}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 문자열 자르기 유틸리티
     */
    private String truncateString(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str != null ? str : "";
        }
        return str.substring(0, maxLength) + "...";
    }

    /**
     * 태스크 ID 생성
     */
    private String generateTaskId() {
        return "ldap_task_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    /**
     * 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, Model model) {
        log.error("Unexpected error in LdifController: {}", e.getMessage(), e);
        model.addAttribute("error", "예상치 못한 오류가 발생했습니다: " + e.getMessage());
        return "error";
    }

}
