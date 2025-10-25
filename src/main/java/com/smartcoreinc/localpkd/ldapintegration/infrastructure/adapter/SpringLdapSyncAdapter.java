package com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.Certificate;
import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CertificateRevocationList;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import com.smartcoreinc.localpkd.ldapintegration.domain.model.DistinguishedName;
import com.smartcoreinc.localpkd.ldapintegration.domain.model.LdapCertificateEntry;
import com.smartcoreinc.localpkd.ldapintegration.domain.model.LdapCrlEntry;
import com.smartcoreinc.localpkd.ldapintegration.domain.port.LdapSyncService;
import com.smartcoreinc.localpkd.ldapintegration.domain.port.LdapUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * SpringLdapSyncAdapter - Spring LDAP 기반 LdapSyncService 구현체
 *
 * <p><b>Hexagonal Architecture Adapter</b>:</p>
 * <ul>
 *   <li>LdapSyncService 포트의 구현체</li>
 *   <li>로컬 DB와 LDAP 서버 간 동기화 관리</li>
 *   <li>전체, 증분, 선택적 동기화 지원</li>
 * </ul>
 *
 * <h3>책임</h3>
 * <ul>
 *   <li>동기화 세션 관리 (생성, 취소, 조회)</li>
 *   <li>동기화 상태 추적 (대기, 진행, 완료, 실패)</li>
 *   <li>동기화 이력 기록 및 통계</li>
 *   <li>재시도 로직 관리</li>
 * </ul>
 *
 * <h3>동기화 세션 상태 전이</h3>
 * <ul>
 *   <li>PENDING → IN_PROGRESS → SUCCESS/FAILED</li>
 *   <li>IN_PROGRESS → CANCELLED (사용자 취소)</li>
 *   <li>FAILED → PENDING (재시도)</li>
 * </ul>
 *
 * <h3>구현 방식</h3>
 * <ul>
 *   <li>Stub implementation with TODO markers</li>
 *   <li>In-memory session storage (ConcurrentHashMap)</li>
 *   <li>Comprehensive logging for debugging</li>
 *   <li>Exception handling with domain exceptions</li>
 * </ul>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@Slf4j
@Component
public class SpringLdapSyncAdapter implements LdapSyncService {

    private final LdapTemplate ldapTemplate;
    private final LdapUploadService ldapUploadService;
    private final CertificateRepository certificateRepository;
    private final CertificateRevocationListRepository crlRepository;
    private final ExecutorService executorService;

    // In-memory session storage (TODO: Move to database in future enhancement)
    private final Map<UUID, SyncSessionImpl> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, SyncStatusImpl> statuses = new ConcurrentHashMap<>();
    private final Map<UUID, SyncResultImpl> results = new ConcurrentHashMap<>();
    private final Map<UUID, Future<?>> syncTasks = new ConcurrentHashMap<>();

    // Last successful sync time tracking
    private LocalDateTime lastSuccessfulSyncTime;

    // Constructor (Spring auto-wires when only one constructor exists)
    public SpringLdapSyncAdapter(
            LdapTemplate ldapTemplate,
            LdapUploadService ldapUploadService,
            CertificateRepository certificateRepository,
            CertificateRevocationListRepository crlRepository) {
        this.ldapTemplate = ldapTemplate;
        this.ldapUploadService = ldapUploadService;
        this.certificateRepository = certificateRepository;
        this.crlRepository = crlRepository;
        // Create ExecutorService for async sync operations
        this.executorService = Executors.newFixedThreadPool(
                2,  // Max 2 concurrent sync operations
                r -> {
                    Thread t = new Thread(r);
                    t.setName("ldap-sync-" + t.getId());
                    t.setDaemon(true);  // Allow JVM shutdown
                    return t;
                }
        );
        log.info("SpringLdapSyncAdapter initialized with async executor (pool size: 2)");
    }

    // ======================== Sync Initiation Methods ========================

    @Override
    public SyncSession startFullSync() {
        log.info("=== Full LDAP sync started ===");

        try {
            // Check if sync is already in progress
            checkNoActiveSyncInProgress();

            // Create new sync session
            UUID sessionId = UUID.randomUUID();
            LocalDateTime startedAt = LocalDateTime.now();

            SyncSessionImpl session = SyncSessionImpl.builder()
                    .id(sessionId)
                    .mode("FULL")
                    .startedAt(startedAt)
                    .state(SyncStatus.State.PENDING)
                    .description("Full synchronization of all certificates and CRLs")
                    .build();

            // Query database for total count
            long certificateCount = certificateRepository.count();
            long crlCount = crlRepository.count();
            long totalCount = certificateCount + crlCount;

            log.info("Full sync will process {} certificates and {} CRLs (total: {})",
                    certificateCount, crlCount, totalCount);

            // Initialize status
            SyncStatusImpl status = SyncStatusImpl.builder()
                    .sessionId(sessionId)
                    .state(SyncStatus.State.PENDING)
                    .totalCount(totalCount)
                    .processedCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .currentTask("Initializing full sync")
                    .updatedAt(LocalDateTime.now())
                    .build();

            sessions.put(sessionId, session);
            statuses.put(sessionId, status);

            log.info("Full sync session created: sessionId={}", sessionId);

            // Submit async sync task to executor
            Future<?> syncTask = executorService.submit(() -> {
                executeFullSync(sessionId, status, session);
            });

            syncTasks.put(sessionId, syncTask);
            log.info("Full sync task submitted to executor: sessionId={}", sessionId);

            return session;

        } catch (Exception e) {
            log.error("Failed to start full sync", e);
            throw new LdapSyncException("Failed to start full sync: " + e.getMessage(), e);
        }
    }

    @Override
    public SyncSession startIncrementalSync() {
        log.info("=== Incremental LDAP sync started ===");

        try {
            // Check if sync is already in progress
            checkNoActiveSyncInProgress();

            // Check if initial sync was completed
            Optional<LocalDateTime> lastSync = getLastSuccessfulSyncTime();
            String description = lastSync.isPresent()
                    ? String.format("Incremental sync since %s", lastSync.get())
                    : "Full sync (first time)";

            // Create new sync session
            UUID sessionId = UUID.randomUUID();
            LocalDateTime startedAt = LocalDateTime.now();

            SyncSessionImpl session = SyncSessionImpl.builder()
                    .id(sessionId)
                    .mode("INCREMENTAL")
                    .startedAt(startedAt)
                    .state(SyncStatus.State.PENDING)
                    .description(description)
                    .build();

            // Initialize status
            SyncStatusImpl status = SyncStatusImpl.builder()
                    .sessionId(sessionId)
                    .state(SyncStatus.State.PENDING)
                    .totalCount(0)  // TODO: Query for changed records since last sync
                    .processedCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .currentTask("Detecting changes since last sync")
                    .updatedAt(LocalDateTime.now())
                    .build();

            sessions.put(sessionId, session);
            statuses.put(sessionId, status);

            // Submit async sync task to executor
            Future<?> syncTask = executorService.submit(() -> {
                executeIncrementalSync(sessionId, status, session, lastSync);
            });

            syncTasks.put(sessionId, syncTask);
            log.info("Incremental sync task submitted to executor: sessionId={}", sessionId);

            return session;

        } catch (Exception e) {
            log.error("Failed to start incremental sync", e);
            throw new LdapSyncException("Failed to start incremental sync: " + e.getMessage(), e);
        }
    }

    @Override
    public SyncSession startSelectiveSync(String filter) {
        log.info("=== Selective LDAP sync started ===");
        log.debug("Filter: {}", filter);

        try {
            if (filter == null || filter.isBlank()) {
                throw new LdapSyncException("Filter must not be null or blank");
            }

            // Check if sync is already in progress
            checkNoActiveSyncInProgress();

            // Create new sync session
            UUID sessionId = UUID.randomUUID();
            LocalDateTime startedAt = LocalDateTime.now();

            SyncSessionImpl session = SyncSessionImpl.builder()
                    .id(sessionId)
                    .mode("SELECTIVE")
                    .startedAt(startedAt)
                    .state(SyncStatus.State.PENDING)
                    .description("Selective sync with filter: " + filter)
                    .build();

            // Initialize status
            SyncStatusImpl status = SyncStatusImpl.builder()
                    .sessionId(sessionId)
                    .state(SyncStatus.State.PENDING)
                    .totalCount(0)  // TODO: Query for filtered records
                    .processedCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .currentTask("Querying records with filter: " + filter)
                    .updatedAt(LocalDateTime.now())
                    .build();

            sessions.put(sessionId, session);
            statuses.put(sessionId, status);

            // Submit async sync task to executor
            Future<?> syncTask = executorService.submit(() -> {
                executeSelectiveSync(sessionId, status, session, filter);
            });

            syncTasks.put(sessionId, syncTask);
            log.info("Selective sync task submitted to executor: sessionId={}", sessionId);

            return session;

        } catch (LdapSyncException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to start selective sync", e);
            throw new LdapSyncException("Failed to start selective sync: " + e.getMessage(), e);
        }
    }

    // ======================== Sync Control Methods ========================

    @Override
    public boolean cancelSync(UUID sessionId) {
        log.info("=== Cancelling sync session ===");
        log.debug("Session ID: {}", sessionId);

        try {
            if (sessionId == null) {
                throw new LdapSyncException("Session ID must not be null");
            }

            SyncSessionImpl session = sessions.get(sessionId);
            if (session == null) {
                log.warn("Session not found: {}", sessionId);
                return false;
            }

            // Check if cancellable (only PENDING or IN_PROGRESS)
            SyncStatus.State currentState = session.getState();
            if (currentState != SyncStatus.State.PENDING && currentState != SyncStatus.State.IN_PROGRESS) {
                log.warn("Cannot cancel sync in state: {}", currentState);
                return false;
            }

            // Cancel the async task
            Future<?> syncTask = syncTasks.get(sessionId);
            if (syncTask != null) {
                boolean cancelled = syncTask.cancel(true);  // Interrupt if running
                log.info("Sync task cancellation result: {}", cancelled);
                syncTasks.remove(sessionId);
            }

            // Update session state
            session.setFinishedAt(LocalDateTime.now());
            session.setState(SyncStatus.State.CANCELLED);

            // Update status
            SyncStatusImpl status = statuses.get(sessionId);
            if (status != null) {
                status.setState(SyncStatus.State.CANCELLED);
                status.setCurrentTask("Sync cancelled by user");
                status.setUpdatedAt(LocalDateTime.now());
            }

            log.info("Sync session cancelled successfully: sessionId={}", sessionId);
            return true;

        } catch (Exception e) {
            log.error("Failed to cancel sync", e);
            return false;
        }
    }

    // ======================== Sync Status Methods ========================

    @Override
    public Optional<SyncStatus> getSyncStatus(UUID sessionId) {
        log.debug("=== Getting sync status ===");
        log.debug("Session ID: {}", sessionId);

        try {
            if (sessionId == null) {
                return Optional.empty();
            }

            SyncStatusImpl status = statuses.get(sessionId);
            return Optional.ofNullable(status);

        } catch (Exception e) {
            log.error("Failed to get sync status", e);
            return Optional.empty();
        }
    }

    @Override
    public SyncResult waitForCompletion(UUID sessionId, long timeoutSeconds) {
        log.info("=== Waiting for sync completion ===");
        log.debug("Session ID: {}, Timeout: {} seconds", sessionId, timeoutSeconds);

        try {
            if (sessionId == null) {
                throw new LdapSyncException("Session ID must not be null");
            }

            // Get the sync task future
            Future<?> syncTask = syncTasks.get(sessionId);
            if (syncTask == null) {
                // Task may have already completed, check result
                SyncResultImpl result = results.get(sessionId);
                if (result != null) {
                    return result;
                }
                throw new LdapSyncException("Sync task not found for session: " + sessionId);
            }

            // Wait for task to complete with timeout
            try {
                syncTask.get(timeoutSeconds, TimeUnit.SECONDS);
                log.info("Sync task completed within timeout");
            } catch (TimeoutException e) {
                log.warn("Sync task timeout after {} seconds", timeoutSeconds);
                throw new LdapSyncTimeoutException(sessionId, timeoutSeconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Sync task interrupted");
                throw new LdapSyncException("Sync task was interrupted");
            } catch (ExecutionException e) {
                log.error("Sync task execution failed", e.getCause());
                // Task failed but result should still be available
            }

            // Remove completed task from map
            syncTasks.remove(sessionId);

            // Return cached result
            SyncResultImpl result = results.get(sessionId);
            if (result != null) {
                return result;
            }

            // If no result (shouldn't happen), create error result
            log.error("Sync completed but no result found for session: {}", sessionId);
            return SyncResultImpl.builder()
                    .success(false)
                    .successCount(0)
                    .failedCount(0)
                    .startedAt(LocalDateTime.now())
                    .finishedAt(LocalDateTime.now())
                    .durationSeconds(0)
                    .message("Sync completed but result not available")
                    .errorMessage("Internal error: result not found")
                    .failedItems(Collections.emptyList())
                    .build();

        } catch (LdapSyncTimeoutException e) {
            throw e;
        } catch (LdapSyncException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to wait for sync completion", e);
            throw new LdapSyncException("Failed to wait for sync: " + e.getMessage(), e);
        }
    }

    // ======================== Sync History Methods ========================

    @Override
    public List<SyncSession> getSyncHistory(LocalDateTime from, int limit) {
        log.debug("=== Getting sync history ===");
        log.debug("From: {}, Limit: {}", from, limit);

        try {
            if (from == null) {
                throw new LdapSyncException("From datetime must not be null");
            }

            if (limit <= 0) {
                throw new LdapSyncException("Limit must be greater than 0");
            }

            // TODO: Implement actual database query for history
            // TODO: Filter by startedAt >= from
            // TODO: Order by startedAt DESC
            // TODO: Limit results

            log.warn("Sync history stub implementation");

            // Return sessions from memory (stub)
            return sessions.values().stream()
                    .filter(session -> session.getStartedAt().isAfter(from) || session.getStartedAt().isEqual(from))
                    .sorted((s1, s2) -> s2.getStartedAt().compareTo(s1.getStartedAt()))
                    .limit(limit)
                    .map(s -> (SyncSession) s)
                    .toList();

        } catch (Exception e) {
            log.error("Failed to get sync history", e);
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<SyncSession> getLatestSync() {
        log.debug("=== Getting latest sync session ===");

        try {
            // TODO: Implement actual database query for latest sync
            // TODO: Order by startedAt DESC
            // TODO: Limit 1

            log.warn("Get latest sync stub implementation");

            return sessions.values().stream()
                    .max(Comparator.comparing(SyncSession::getStartedAt))
                    .map(s -> (SyncSession) s);

        } catch (Exception e) {
            log.error("Failed to get latest sync", e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<LocalDateTime> getLastSuccessfulSyncTime() {
        log.debug("=== Getting last successful sync time ===");

        try {
            // TODO: Implement actual database query for last successful sync
            // TODO: Query where state = SUCCESS
            // TODO: Order by finishedAt DESC
            // TODO: Limit 1

            log.warn("Get last successful sync time stub implementation");

            if (lastSuccessfulSyncTime != null) {
                return Optional.of(lastSuccessfulSyncTime);
            }

            // Fallback to finding latest successful session in memory
            return sessions.values().stream()
                    .filter(s -> s.getState() == SyncStatus.State.SUCCESS)
                    .max(Comparator.comparing(SyncSession::getStartedAt))
                    .map(SyncSession::getFinishedAt);

        } catch (Exception e) {
            log.error("Failed to get last successful sync time", e);
            return Optional.empty();
        }
    }

    // ======================== Entity Sync Status Methods ========================

    @Override
    public boolean isSynced(UUID entityId) {
        log.debug("=== Checking if entity is synced ===");
        log.debug("Entity ID: {}", entityId);

        try {
            if (entityId == null) {
                return false;
            }

            // TODO: Implement database query
            // TODO: Check if entity exists in LDAP sync tracking table
            // TODO: Check if last_synced_at is not null

            log.warn("Entity sync status stub implementation");

            return false;  // Stub: assume not synced

        } catch (Exception e) {
            log.error("Failed to check entity sync status", e);
            return false;
        }
    }

    @Override
    public long countPendingEntities() {
        log.debug("=== Counting pending entities ===");

        try {
            // TODO: Implement database query
            // TODO: Count entities where synced = false OR last_synced_at is null

            log.warn("Count pending entities stub implementation");

            return 0L;  // Stub: assume no pending

        } catch (Exception e) {
            log.error("Failed to count pending entities", e);
            return 0L;
        }
    }

    // ======================== Sync Retry Methods ========================

    @Override
    public SyncSession retryFailedEntries(UUID sessionId) {
        log.info("=== Retrying failed sync entries ===");
        log.debug("Session ID: {}", sessionId);

        try {
            if (sessionId == null) {
                throw new LdapSyncException("Session ID must not be null");
            }

            SyncSessionImpl originalSession = sessions.get(sessionId);
            if (originalSession == null) {
                throw new LdapSyncException("Session not found: " + sessionId);
            }

            // Check if sync is already in progress
            checkNoActiveSyncInProgress();

            // Create retry session
            UUID retrySessionId = UUID.randomUUID();
            LocalDateTime startedAt = LocalDateTime.now();

            SyncSessionImpl retrySession = SyncSessionImpl.builder()
                    .id(retrySessionId)
                    .mode(originalSession.getMode())
                    .startedAt(startedAt)
                    .state(SyncStatus.State.PENDING)
                    .description("Retry for session: " + sessionId)
                    .build();

            // Initialize retry status
            SyncStatusImpl retryStatus = SyncStatusImpl.builder()
                    .sessionId(retrySessionId)
                    .state(SyncStatus.State.PENDING)
                    .totalCount(0)  // TODO: Count failed items from original session
                    .processedCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .currentTask("Retrying failed entries from session: " + sessionId)
                    .updatedAt(LocalDateTime.now())
                    .build();

            sessions.put(retrySessionId, retrySession);
            statuses.put(retrySessionId, retryStatus);

            // Get failed items from original session
            SyncResult originalResult = results.get(sessionId);
            List<SyncResult.FailedItem> failedItems = originalResult != null
                    ? originalResult.getFailedItems()
                    : new ArrayList<>();

            // Update total count
            retryStatus.setTotalCount(failedItems.size());

            // Submit async retry task to executor
            Future<?> retryTask = executorService.submit(() -> {
                executeRetrySync(retrySessionId, retryStatus, retrySession, failedItems);
            });

            syncTasks.put(retrySessionId, retryTask);
            log.info("Retry sync task submitted to executor: originalSessionId={}, retrySessionId={}",
                    sessionId, retrySessionId);

            return retrySession;

        } catch (Exception e) {
            log.error("Failed to retry failed entries", e);
            throw new LdapSyncException("Failed to retry: " + e.getMessage(), e);
        }
    }

    // ======================== Sync Statistics Methods ========================

    @Override
    public SyncStatistics getStatistics() {
        log.info("=== Getting sync statistics ===");

        try {
            // TODO: Implement database queries for all statistics
            // TODO: Count total synced, total failed
            // TODO: Calculate average sync time
            // TODO: Find last sync time
            // TODO: Calculate success rate
            // TODO: Count today's syncs

            log.warn("Sync statistics stub implementation");

            return SyncStatisticsImpl.builder()
                    .totalSynced(0)
                    .totalFailed(0)
                    .averageSyncTimeSeconds(0)
                    .lastSyncTime(LocalDateTime.now())
                    .successRate(0.0)
                    .todaysSyncCount(0)
                    .build();

        } catch (Exception e) {
            log.error("Failed to get sync statistics", e);
            throw new LdapSyncException("Failed to get statistics: " + e.getMessage(), e);
        }
    }

    // ======================== Sync Execution Methods ========================

    /**
     * Execute full synchronization (certificates + CRLs)
     * This method runs in async executor thread
     *
     * @param sessionId Sync session ID
     * @param status Status tracker
     * @param session Session object
     */
    private void executeFullSync(UUID sessionId, SyncStatusImpl status, SyncSessionImpl session) {
        log.info("=== Executing full sync ===");
        log.info("Session ID: {}", sessionId);

        LocalDateTime syncStartTime = LocalDateTime.now();
        List<SyncResult.FailedItem> failedItems = new ArrayList<>();

        try {
            // Update status to IN_PROGRESS
            status.setState(SyncStatus.State.IN_PROGRESS);
            session.setState(SyncStatus.State.IN_PROGRESS);
            status.setCurrentTask("Fetching certificates from database");
            status.setUpdatedAt(LocalDateTime.now());

            // 1. Sync all certificates
            log.info("Step 1: Syncing certificates to LDAP");
            // TODO: Implement actual query after domain model verification
            log.warn("Certificate sync stub - skipping actual sync (domain model integration pending)");
            // List<Certificate> allCertificates = certificateRepository.findAll();
            status.setCurrentTask("Certificate sync skipped (stub)");
            status.setUpdatedAt(LocalDateTime.now());

            log.info("Certificate sync completed (stub): {} success, {} failed",
                    status.getSuccessCount(), status.getFailedCount());

            // 2. Sync all CRLs
            log.info("Step 2: Syncing CRLs to LDAP");
            // TODO: Implement actual query after domain model verification
            log.warn("CRL sync stub - skipping actual sync (domain model integration pending)");
            // List<CertificateRevocationList> allCrls = crlRepository.findAll();
            status.setCurrentTask("CRL sync skipped (stub)");
            status.setUpdatedAt(LocalDateTime.now());

            log.info("CRL sync completed (stub): {} success, {} failed",
                    status.getSuccessCount(), status.getFailedCount());

            // 3. Mark sync as complete
            LocalDateTime syncEndTime = LocalDateTime.now();
            long durationSeconds = java.time.Duration.between(syncStartTime, syncEndTime).getSeconds();

            boolean success = status.getFailedCount() == 0;
            // NOTE: Using SUCCESS state for both full success and partial success
            // FailedItems list indicates which items failed
            status.setState(SyncStatus.State.SUCCESS);
            session.setState(SyncStatus.State.SUCCESS);
            session.setFinishedAt(syncEndTime);
            status.setCurrentTask("Sync completed");
            status.setUpdatedAt(syncEndTime);

            // 4. Create sync result
            SyncResultImpl result = SyncResultImpl.builder()
                    .success(success)
                    .successCount((int) status.getSuccessCount())
                    .failedCount((int) status.getFailedCount())
                    .startedAt(syncStartTime)
                    .finishedAt(syncEndTime)
                    .durationSeconds(durationSeconds)
                    .message(String.format("Full sync completed: %d success, %d failed",
                            status.getSuccessCount(), status.getFailedCount()))
                    .errorMessage(success ? null : "Some items failed to sync")
                    .failedItems(failedItems)
                    .build();

            results.put(sessionId, result);

            // Update last successful sync time
            if (success) {
                lastSuccessfulSyncTime = syncEndTime;
            }

            log.info("Full sync execution completed: sessionId={}, duration={}s, success={}, " +
                            "successCount={}, failedCount={}",
                    sessionId, durationSeconds, success, status.getSuccessCount(),
                    status.getFailedCount());

        } catch (Exception e) {
            log.error("Full sync execution failed: sessionId={}", sessionId, e);

            // Mark sync as failed
            status.setState(SyncStatus.State.FAILED);
            session.setState(SyncStatus.State.FAILED);
            session.setFinishedAt(LocalDateTime.now());
            status.setLastError(e.getMessage());
            status.setUpdatedAt(LocalDateTime.now());

            // Create failure result
            SyncResultImpl result = SyncResultImpl.builder()
                    .success(false)
                    .successCount((int) status.getSuccessCount())
                    .failedCount((int) status.getFailedCount())
                    .startedAt(syncStartTime)
                    .finishedAt(LocalDateTime.now())
                    .durationSeconds(java.time.Duration.between(syncStartTime, LocalDateTime.now()).getSeconds())
                    .message("Full sync failed")
                    .errorMessage(e.getMessage())
                    .failedItems(failedItems)
                    .build();

            results.put(sessionId, result);
        }
    }

    /**
     * Execute incremental sync (async)
     *
     * Synchronize only records modified since last successful sync
     */
    private void executeIncrementalSync(UUID sessionId, SyncStatusImpl status,
                                       SyncSessionImpl session, Optional<LocalDateTime> lastSync) {
        log.info("=== Executing incremental sync ===");
        LocalDateTime syncStartTime = LocalDateTime.now();
        List<SyncResult.FailedItem> failedItems = new ArrayList<>();

        try {
            // Update status to IN_PROGRESS
            status.setState(SyncStatus.State.IN_PROGRESS);
            session.setState(SyncStatus.State.IN_PROGRESS);
            status.setCurrentTask("Detecting changes since last sync");
            status.setUpdatedAt(LocalDateTime.now());

            // If no previous sync, fall back to full sync
            if (lastSync.isEmpty()) {
                log.warn("No previous successful sync found - executing full sync instead");
                status.setCurrentTask("No previous sync - executing full sync");
                executeFullSync(sessionId, status, session);
                return;
            }

            // 1. Query certificates modified since lastSync (STUBBED - domain integration pending)
            log.warn("Incremental certificate sync stub - skipping (domain model integration pending)");
            status.setCurrentTask("Incremental certificate sync skipped (stub)");
            status.setUpdatedAt(LocalDateTime.now());

            // 2. Query CRLs modified since lastSync (STUBBED - domain integration pending)
            log.warn("Incremental CRL sync stub - skipping (domain model integration pending)");
            status.setCurrentTask("Incremental CRL sync skipped (stub)");
            status.setUpdatedAt(LocalDateTime.now());

            // 3. Mark sync as complete
            LocalDateTime syncEndTime = LocalDateTime.now();
            long durationSeconds = java.time.Duration.between(syncStartTime, syncEndTime).getSeconds();

            status.setState(SyncStatus.State.SUCCESS);
            session.setState(SyncStatus.State.SUCCESS);
            session.setFinishedAt(syncEndTime);
            status.setCurrentTask("Incremental sync completed");
            status.setUpdatedAt(syncEndTime);

            // 4. Create sync result
            SyncResultImpl result = SyncResultImpl.builder()
                    .success(true)
                    .successCount((int) status.getSuccessCount())
                    .failedCount((int) status.getFailedCount())
                    .startedAt(syncStartTime)
                    .finishedAt(syncEndTime)
                    .durationSeconds(durationSeconds)
                    .message(String.format("Incremental sync completed: %d success, %d failed",
                            status.getSuccessCount(), status.getFailedCount()))
                    .errorMessage(null)
                    .failedItems(failedItems)
                    .build();

            results.put(sessionId, result);
            log.info("Incremental sync completed successfully: sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("Incremental sync failed", e);
            status.setState(SyncStatus.State.FAILED);
            session.setState(SyncStatus.State.FAILED);
            session.setFinishedAt(LocalDateTime.now());
            status.setCurrentTask("Incremental sync failed");
            status.setLastError(e.getMessage());
            status.setUpdatedAt(LocalDateTime.now());

            SyncResultImpl result = SyncResultImpl.builder()
                    .success(false)
                    .successCount((int) status.getSuccessCount())
                    .failedCount((int) status.getFailedCount())
                    .startedAt(syncStartTime)
                    .finishedAt(LocalDateTime.now())
                    .durationSeconds(java.time.Duration.between(syncStartTime, LocalDateTime.now()).getSeconds())
                    .message("Incremental sync failed")
                    .errorMessage(e.getMessage())
                    .failedItems(failedItems)
                    .build();

            results.put(sessionId, result);
        }
    }

    /**
     * Execute selective sync (async)
     *
     * Synchronize only records matching the filter
     */
    private void executeSelectiveSync(UUID sessionId, SyncStatusImpl status,
                                      SyncSessionImpl session, String filter) {
        log.info("=== Executing selective sync with filter: {} ===", filter);
        LocalDateTime syncStartTime = LocalDateTime.now();
        List<SyncResult.FailedItem> failedItems = new ArrayList<>();

        try {
            // Update status to IN_PROGRESS
            status.setState(SyncStatus.State.IN_PROGRESS);
            session.setState(SyncStatus.State.IN_PROGRESS);
            status.setCurrentTask("Applying filter: " + filter);
            status.setUpdatedAt(LocalDateTime.now());

            // 1. Query certificates matching filter (STUBBED - domain integration pending)
            log.warn("Selective certificate sync stub - skipping (domain model integration pending)");
            status.setCurrentTask("Selective certificate sync skipped (stub)");
            status.setUpdatedAt(LocalDateTime.now());

            // 2. Query CRLs matching filter (STUBBED - domain integration pending)
            log.warn("Selective CRL sync stub - skipping (domain model integration pending)");
            status.setCurrentTask("Selective CRL sync skipped (stub)");
            status.setUpdatedAt(LocalDateTime.now());

            // 3. Mark sync as complete
            LocalDateTime syncEndTime = LocalDateTime.now();
            long durationSeconds = java.time.Duration.between(syncStartTime, syncEndTime).getSeconds();

            status.setState(SyncStatus.State.SUCCESS);
            session.setState(SyncStatus.State.SUCCESS);
            session.setFinishedAt(syncEndTime);
            status.setCurrentTask("Selective sync completed");
            status.setUpdatedAt(syncEndTime);

            // 4. Create sync result
            SyncResultImpl result = SyncResultImpl.builder()
                    .success(true)
                    .successCount((int) status.getSuccessCount())
                    .failedCount((int) status.getFailedCount())
                    .startedAt(syncStartTime)
                    .finishedAt(syncEndTime)
                    .durationSeconds(durationSeconds)
                    .message(String.format("Selective sync completed: %d success, %d failed",
                            status.getSuccessCount(), status.getFailedCount()))
                    .errorMessage(null)
                    .failedItems(failedItems)
                    .build();

            results.put(sessionId, result);
            log.info("Selective sync completed successfully: sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("Selective sync failed", e);
            status.setState(SyncStatus.State.FAILED);
            session.setState(SyncStatus.State.FAILED);
            session.setFinishedAt(LocalDateTime.now());
            status.setCurrentTask("Selective sync failed");
            status.setLastError(e.getMessage());
            status.setUpdatedAt(LocalDateTime.now());

            SyncResultImpl result = SyncResultImpl.builder()
                    .success(false)
                    .successCount((int) status.getSuccessCount())
                    .failedCount((int) status.getFailedCount())
                    .startedAt(syncStartTime)
                    .finishedAt(LocalDateTime.now())
                    .durationSeconds(java.time.Duration.between(syncStartTime, LocalDateTime.now()).getSeconds())
                    .message("Selective sync failed")
                    .errorMessage(e.getMessage())
                    .failedItems(failedItems)
                    .build();

            results.put(sessionId, result);
        }
    }

    /**
     * Execute retry sync (async)
     *
     * Re-attempt synchronization of previously failed items
     */
    private void executeRetrySync(UUID sessionId, SyncStatusImpl status,
                                  SyncSessionImpl session, List<SyncResult.FailedItem> failedItems) {
        log.info("=== Executing retry sync for {} failed items ===", failedItems.size());
        LocalDateTime syncStartTime = LocalDateTime.now();
        List<SyncResult.FailedItem> newFailedItems = new ArrayList<>();

        try {
            // Update status to IN_PROGRESS
            status.setState(SyncStatus.State.IN_PROGRESS);
            session.setState(SyncStatus.State.IN_PROGRESS);
            status.setCurrentTask(String.format("Retrying %d failed items", failedItems.size()));
            status.setUpdatedAt(LocalDateTime.now());

            // If no failed items, complete immediately
            if (failedItems.isEmpty()) {
                log.warn("No failed items to retry");
                status.setCurrentTask("No items to retry");
                status.setState(SyncStatus.State.SUCCESS);
                session.setState(SyncStatus.State.SUCCESS);
                session.setFinishedAt(LocalDateTime.now());

                SyncResultImpl result = SyncResultImpl.builder()
                        .success(true)
                        .successCount(0)
                        .failedCount(0)
                        .startedAt(syncStartTime)
                        .finishedAt(LocalDateTime.now())
                        .durationSeconds(0)
                        .message("No failed items to retry")
                        .errorMessage(null)
                        .failedItems(newFailedItems)
                        .build();

                results.put(sessionId, result);
                return;
            }

            // Retry each failed item (STUBBED - domain integration pending)
            for (SyncResult.FailedItem failedItem : failedItems) {
                log.warn("Retry sync stub - skipping failed item {} (domain model integration pending)",
                        failedItem.getEntityId());

                // TODO: Implement retry logic after domain model integration
                // - Query entity by ID from repository
                // - Convert to LDAP entry
                // - Attempt upload to LDAP
                // - Track success/failure

                status.incrementProcessedCount();
                status.setCurrentTask(String.format("Retrying item %d/%d (stub)",
                        status.getProcessedCount(), status.getTotalCount()));
                status.setUpdatedAt(LocalDateTime.now());
            }

            // Mark sync as complete
            LocalDateTime syncEndTime = LocalDateTime.now();
            long durationSeconds = java.time.Duration.between(syncStartTime, syncEndTime).getSeconds();

            status.setState(SyncStatus.State.SUCCESS);
            session.setState(SyncStatus.State.SUCCESS);
            session.setFinishedAt(syncEndTime);
            status.setCurrentTask("Retry sync completed");
            status.setUpdatedAt(syncEndTime);

            // Create sync result
            SyncResultImpl result = SyncResultImpl.builder()
                    .success(newFailedItems.isEmpty())
                    .successCount((int) status.getSuccessCount())
                    .failedCount((int) status.getFailedCount())
                    .startedAt(syncStartTime)
                    .finishedAt(syncEndTime)
                    .durationSeconds(durationSeconds)
                    .message(String.format("Retry sync completed: %d success, %d failed",
                            status.getSuccessCount(), status.getFailedCount()))
                    .errorMessage(newFailedItems.isEmpty() ? null : "Some items failed to retry")
                    .failedItems(newFailedItems)
                    .build();

            results.put(sessionId, result);
            log.info("Retry sync completed successfully: sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("Retry sync failed", e);
            status.setState(SyncStatus.State.FAILED);
            session.setState(SyncStatus.State.FAILED);
            session.setFinishedAt(LocalDateTime.now());
            status.setCurrentTask("Retry sync failed");
            status.setLastError(e.getMessage());
            status.setUpdatedAt(LocalDateTime.now());

            SyncResultImpl result = SyncResultImpl.builder()
                    .success(false)
                    .successCount((int) status.getSuccessCount())
                    .failedCount((int) status.getFailedCount())
                    .startedAt(syncStartTime)
                    .finishedAt(LocalDateTime.now())
                    .durationSeconds(java.time.Duration.between(syncStartTime, LocalDateTime.now()).getSeconds())
                    .message("Retry sync failed")
                    .errorMessage(e.getMessage())
                    .failedItems(newFailedItems)
                    .build();

            results.put(sessionId, result);
        }
    }

    /**
     * Convert Certificate domain model to LdapCertificateEntry
     *
     * TODO: Implement after domain model finalization
     * - Verify Certificate domain model methods (getSubject, getIssuer, getFingerprint, etc.)
     * - Verify X509Data.getCertificateBase64() method
     * - Verify subject/issuer structure
     */
    private LdapCertificateEntry convertCertificateToLdapEntry(Certificate cert) {
        // STUB: Domain model integration pending
        throw new UnsupportedOperationException(
            "Certificate to LDAP entry conversion not yet implemented - domain model integration pending"
        );

        // TODO: Implement after domain model verification
        // String dn = String.format("cn=%s,ou=certificates,dc=ldap,dc=smartcoreinc,dc=com",
        //         cert.getSubject().getCommonName());
        //
        // return LdapCertificateEntry.builder()
        //         .dn(DistinguishedName.of(dn))
        //         .x509CertificateBase64(cert.getX509Data().getCertificateBase64())
        //         .fingerprint(cert.getFingerprint().getValue())
        //         .serialNumber(cert.getSerialNumber().getValue())
        //         .issuerDn(cert.getIssuer().getDn())
        //         .validationStatus(cert.getValidationStatus().name())
        //         .build();
    }

    /**
     * Convert CRL domain model to LdapCrlEntry
     *
     * TODO: Implement after domain model finalization
     * - Verify IssuerName.getCommonName() and getDn() methods
     * - Verify X509CrlData.getCrlBase64() method
     * - Verify CRL structure
     */
    private LdapCrlEntry convertCrlToLdapEntry(CertificateRevocationList crl) {
        // STUB: Domain model integration pending
        throw new UnsupportedOperationException(
            "CRL to LDAP entry conversion not yet implemented - domain model integration pending"
        );

        // TODO: Implement after domain model verification
        // String dn = String.format("cn=%s,ou=crls,dc=ldap,dc=smartcoreinc,dc=com",
        //         crl.getIssuerName().getCommonName());
        //
        // return LdapCrlEntry.builder()
        //         .dn(DistinguishedName.of(dn))
        //         .x509CrlBase64(crl.getX509CrlData().getCrlBase64())
        //         .issuerDn(crl.getIssuerName().getDn())
        //         .countryCode(crl.getCountryCode().getValue())
        //         .build();
    }

    /**
     * Create failed item record
     */
    private SyncResult.FailedItem createFailedItem(UUID entityId, String errorMessage) {
        return new SyncResult.FailedItem() {
            @Override
            public UUID getEntityId() {
                return entityId;
            }

            @Override
            public String getErrorMessage() {
                return errorMessage;
            }

            @Override
            public int getRetryCount() {
                return 0;  // TODO: Implement retry tracking
            }
        };
    }

    // ======================== Helper Methods ========================

    /**
     * Check if any sync is already in progress
     *
     * @throws LdapSyncAlreadyInProgressException if sync is in progress
     */
    private void checkNoActiveSyncInProgress() {
        Optional<SyncSessionImpl> activeSessions = sessions.values().stream()
                .filter(s -> s.getState() == SyncStatus.State.IN_PROGRESS || s.getState() == SyncStatus.State.PENDING)
                .findFirst();

        if (activeSessions.isPresent()) {
            throw new LdapSyncAlreadyInProgressException(activeSessions.get().getId());
        }
    }

    // ======================== Inner Classes ========================

    /**
     * SyncSession 구현체
     */
    private static class SyncSessionImpl implements SyncSession {
        private UUID id;
        private String mode;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private SyncStatus.State state;
        private String description;

        private SyncSessionImpl(Builder builder) {
            this.id = builder.id;
            this.mode = builder.mode;
            this.startedAt = builder.startedAt;
            this.finishedAt = builder.finishedAt;
            this.state = builder.state;
            this.description = builder.description;
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public String getMode() {
            return mode;
        }

        @Override
        public LocalDateTime getStartedAt() {
            return startedAt;
        }

        @Override
        public LocalDateTime getFinishedAt() {
            return finishedAt;
        }

        @Override
        public SyncStatus.State getState() {
            return state;
        }

        @Override
        public String getDescription() {
            return description;
        }

        // Setters for state updates
        public void setState(SyncStatus.State state) {
            this.state = state;
        }

        public void setFinishedAt(LocalDateTime finishedAt) {
            this.finishedAt = finishedAt;
        }

        public static class Builder {
            private UUID id;
            private String mode;
            private LocalDateTime startedAt;
            private LocalDateTime finishedAt;
            private SyncStatus.State state;
            private String description;

            public Builder id(UUID id) {
                this.id = id;
                return this;
            }

            public Builder mode(String mode) {
                this.mode = mode;
                return this;
            }

            public Builder startedAt(LocalDateTime startedAt) {
                this.startedAt = startedAt;
                return this;
            }

            public Builder finishedAt(LocalDateTime finishedAt) {
                this.finishedAt = finishedAt;
                return this;
            }

            public Builder state(SyncStatus.State state) {
                this.state = state;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public SyncSessionImpl build() {
                return new SyncSessionImpl(this);
            }
        }
    }

    /**
     * SyncStatus 구현체
     */
    private static class SyncStatusImpl implements SyncStatus {
        private UUID sessionId;
        private State state;
        private long totalCount;
        private long processedCount;
        private long successCount;
        private long failedCount;
        private LocalDateTime updatedAt;
        private String currentTask;
        private String lastError;

        private SyncStatusImpl(Builder builder) {
            this.sessionId = builder.sessionId;
            this.state = builder.state;
            this.totalCount = builder.totalCount;
            this.processedCount = builder.processedCount;
            this.successCount = builder.successCount;
            this.failedCount = builder.failedCount;
            this.updatedAt = builder.updatedAt;
            this.currentTask = builder.currentTask;
            this.lastError = builder.lastError;
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        public UUID getSessionId() {
            return sessionId;
        }

        @Override
        public State getState() {
            return state;
        }

        public void setState(State state) {
            this.state = state;
        }

        public void setCurrentTask(String currentTask) {
            this.currentTask = currentTask;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public void setLastError(String lastError) {
            this.lastError = lastError;
        }

        public void incrementProcessedCount() {
            this.processedCount++;
        }

        public void incrementSuccessCount() {
            this.successCount++;
        }

        public void incrementFailedCount() {
            this.failedCount++;
        }

        public void setTotalCount(long totalCount) {
            this.totalCount = totalCount;
        }

        @Override
        public long getTotalCount() {
            return totalCount;
        }

        @Override
        public long getProcessedCount() {
            return processedCount;
        }

        @Override
        public long getSuccessCount() {
            return successCount;
        }

        @Override
        public long getFailedCount() {
            return failedCount;
        }

        @Override
        public int getProgressPercentage() {
            if (totalCount == 0) {
                return 0;
            }
            return (int) ((processedCount * 100L) / totalCount);
        }

        @Override
        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        @Override
        public String getCurrentTask() {
            return currentTask;
        }

        @Override
        public String getLastError() {
            return lastError;
        }

        public static class Builder {
            private UUID sessionId;
            private State state;
            private long totalCount;
            private long processedCount;
            private long successCount;
            private long failedCount;
            private LocalDateTime updatedAt;
            private String currentTask;
            private String lastError;

            public Builder sessionId(UUID sessionId) {
                this.sessionId = sessionId;
                return this;
            }

            public Builder state(State state) {
                this.state = state;
                return this;
            }

            public Builder totalCount(long totalCount) {
                this.totalCount = totalCount;
                return this;
            }

            public Builder processedCount(long processedCount) {
                this.processedCount = processedCount;
                return this;
            }

            public Builder successCount(long successCount) {
                this.successCount = successCount;
                return this;
            }

            public Builder failedCount(long failedCount) {
                this.failedCount = failedCount;
                return this;
            }

            public Builder updatedAt(LocalDateTime updatedAt) {
                this.updatedAt = updatedAt;
                return this;
            }

            public Builder currentTask(String currentTask) {
                this.currentTask = currentTask;
                return this;
            }

            public Builder lastError(String lastError) {
                this.lastError = lastError;
                return this;
            }

            public SyncStatusImpl build() {
                return new SyncStatusImpl(this);
            }
        }
    }

    /**
     * SyncResult 구현체
     */
    private static class SyncResultImpl implements SyncResult {
        private boolean success;
        private int successCount;
        private int failedCount;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private long durationSeconds;
        private String message;
        private String errorMessage;
        private List<FailedItem> failedItems;

        private SyncResultImpl(Builder builder) {
            this.success = builder.success;
            this.successCount = builder.successCount;
            this.failedCount = builder.failedCount;
            this.startedAt = builder.startedAt;
            this.finishedAt = builder.finishedAt;
            this.durationSeconds = builder.durationSeconds;
            this.message = builder.message;
            this.errorMessage = builder.errorMessage;
            this.failedItems = builder.failedItems;
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        public boolean isSuccess() {
            return success;
        }

        @Override
        public int getSuccessCount() {
            return successCount;
        }

        @Override
        public int getFailedCount() {
            return failedCount;
        }

        @Override
        public LocalDateTime getStartedAt() {
            return startedAt;
        }

        @Override
        public LocalDateTime getFinishedAt() {
            return finishedAt;
        }

        @Override
        public long getDurationSeconds() {
            return durationSeconds;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public List<FailedItem> getFailedItems() {
            return failedItems;
        }

        public static class Builder {
            private boolean success;
            private int successCount;
            private int failedCount;
            private LocalDateTime startedAt;
            private LocalDateTime finishedAt;
            private long durationSeconds;
            private String message;
            private String errorMessage;
            private List<FailedItem> failedItems;

            public Builder success(boolean success) {
                this.success = success;
                return this;
            }

            public Builder successCount(int successCount) {
                this.successCount = successCount;
                return this;
            }

            public Builder failedCount(int failedCount) {
                this.failedCount = failedCount;
                return this;
            }

            public Builder startedAt(LocalDateTime startedAt) {
                this.startedAt = startedAt;
                return this;
            }

            public Builder finishedAt(LocalDateTime finishedAt) {
                this.finishedAt = finishedAt;
                return this;
            }

            public Builder durationSeconds(long durationSeconds) {
                this.durationSeconds = durationSeconds;
                return this;
            }

            public Builder message(String message) {
                this.message = message;
                return this;
            }

            public Builder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }

            public Builder failedItems(List<FailedItem> failedItems) {
                this.failedItems = failedItems;
                return this;
            }

            public SyncResultImpl build() {
                return new SyncResultImpl(this);
            }
        }
    }

    /**
     * SyncStatistics 구현체
     */
    private static class SyncStatisticsImpl implements SyncStatistics {
        private long totalSynced;
        private long totalFailed;
        private long averageSyncTimeSeconds;
        private LocalDateTime lastSyncTime;
        private double successRate;
        private int todaysSyncCount;

        private SyncStatisticsImpl(Builder builder) {
            this.totalSynced = builder.totalSynced;
            this.totalFailed = builder.totalFailed;
            this.averageSyncTimeSeconds = builder.averageSyncTimeSeconds;
            this.lastSyncTime = builder.lastSyncTime;
            this.successRate = builder.successRate;
            this.todaysSyncCount = builder.todaysSyncCount;
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        public long getTotalSynced() {
            return totalSynced;
        }

        @Override
        public long getTotalFailed() {
            return totalFailed;
        }

        @Override
        public long getAverageSyncTimeSeconds() {
            return averageSyncTimeSeconds;
        }

        @Override
        public LocalDateTime getLastSyncTime() {
            return lastSyncTime;
        }

        @Override
        public double getSuccessRate() {
            return successRate;
        }

        @Override
        public int getTodaysSyncCount() {
            return todaysSyncCount;
        }

        public static class Builder {
            private long totalSynced;
            private long totalFailed;
            private long averageSyncTimeSeconds;
            private LocalDateTime lastSyncTime;
            private double successRate;
            private int todaysSyncCount;

            public Builder totalSynced(long totalSynced) {
                this.totalSynced = totalSynced;
                return this;
            }

            public Builder totalFailed(long totalFailed) {
                this.totalFailed = totalFailed;
                return this;
            }

            public Builder averageSyncTimeSeconds(long averageSyncTimeSeconds) {
                this.averageSyncTimeSeconds = averageSyncTimeSeconds;
                return this;
            }

            public Builder lastSyncTime(LocalDateTime lastSyncTime) {
                this.lastSyncTime = lastSyncTime;
                return this;
            }

            public Builder successRate(double successRate) {
                this.successRate = successRate;
                return this;
            }

            public Builder todaysSyncCount(int todaysSyncCount) {
                this.todaysSyncCount = todaysSyncCount;
                return this;
            }

            public SyncStatisticsImpl build() {
                return new SyncStatisticsImpl(this);
            }
        }
    }
}
