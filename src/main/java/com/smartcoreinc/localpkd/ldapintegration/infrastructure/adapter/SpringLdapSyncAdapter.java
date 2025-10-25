package com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter;

import com.smartcoreinc.localpkd.ldapintegration.domain.port.LdapSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
@RequiredArgsConstructor
public class SpringLdapSyncAdapter implements LdapSyncService {

    private final LdapTemplate ldapTemplate;

    // In-memory session storage (TODO: Move to database)
    private final Map<UUID, SyncSessionImpl> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, SyncStatusImpl> statuses = new ConcurrentHashMap<>();
    private final Map<UUID, SyncResultImpl> results = new ConcurrentHashMap<>();

    // Last successful sync time tracking
    private LocalDateTime lastSuccessfulSyncTime;

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

            // Initialize status
            SyncStatusImpl status = SyncStatusImpl.builder()
                    .sessionId(sessionId)
                    .state(SyncStatus.State.PENDING)
                    .totalCount(0)  // TODO: Query database for actual count
                    .processedCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .currentTask("Initializing full sync")
                    .updatedAt(LocalDateTime.now())
                    .build();

            sessions.put(sessionId, session);
            statuses.put(sessionId, status);

            log.info("Full sync session created: sessionId={}", sessionId);

            // TODO: Start async sync process
            // TODO: Submit sync task to thread pool or async service

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

            log.info("Incremental sync session created: sessionId={}", sessionId);

            // TODO: Start async sync process
            // TODO: Query records modified since lastSuccessfulSyncTime

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

            log.info("Selective sync session created: sessionId={}", sessionId);

            // TODO: Start async sync process with filter
            // TODO: Apply filter to select specific records

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

            // TODO: Implement actual cancellation
            // TODO: Stop background sync task
            // TODO: Mark session as CANCELLED

            log.warn("Sync cancellation stub implementation");

            // Update session state
            session.setFinishedAt(LocalDateTime.now());
            session.setState(SyncStatus.State.CANCELLED);

            // Update status
            SyncStatusImpl status = statuses.get(sessionId);
            if (status != null) {
                status.setState(SyncStatus.State.CANCELLED);
            }

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

            long startTime = System.currentTimeMillis();
            long timeoutMillis = timeoutSeconds * 1000L;

            // TODO: Implement actual polling/blocking mechanism
            // TODO: Use CompletableFuture or similar for async wait

            log.warn("Sync wait stub implementation");

            // Check for timeout
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > timeoutMillis) {
                throw new LdapSyncTimeoutException(sessionId, timeoutSeconds);
            }

            // Return cached result or create from status
            SyncResultImpl result = results.get(sessionId);
            if (result != null) {
                return result;
            }

            // Create default result for stub
            return SyncResultImpl.builder()
                    .success(false)
                    .successCount(0)
                    .failedCount(0)
                    .startedAt(LocalDateTime.now())
                    .finishedAt(LocalDateTime.now())
                    .durationSeconds(0)
                    .message("Stub implementation - sync not actually executed")
                    .errorMessage(null)
                    .failedItems(Collections.emptyList())
                    .build();

        } catch (LdapSyncTimeoutException e) {
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

            log.info("Retry sync session created: originalSessionId={}, retrySessionId={}", sessionId, retrySessionId);

            // TODO: Start retry process
            // TODO: Query failed items from original session
            // TODO: Retry each failed item

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
