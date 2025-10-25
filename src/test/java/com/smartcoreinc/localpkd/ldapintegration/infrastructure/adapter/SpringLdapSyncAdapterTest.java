package com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter;

import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRepository;
import com.smartcoreinc.localpkd.certificatevalidation.domain.repository.CertificateRevocationListRepository;
import com.smartcoreinc.localpkd.ldapintegration.domain.port.LdapSyncService;
import com.smartcoreinc.localpkd.ldapintegration.domain.port.LdapUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.LdapTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * SpringLdapSyncAdapterTest - Unit tests for LDAP sync adapter
 *
 * <p>Tests the synchronization functionality including:
 * - Full, incremental, selective sync initiation
 * - Sync control (cancel, status, wait)
 * - Sync history and statistics
 * - Entity tracking and retry logic
 * </p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpringLdapSyncAdapter Tests")
class SpringLdapSyncAdapterTest {

    @Mock
    private LdapTemplate ldapTemplate;

    @Mock
    private LdapUploadService ldapUploadService;

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private CertificateRevocationListRepository crlRepository;

    private SpringLdapSyncAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpringLdapSyncAdapter(
                ldapTemplate,
                ldapUploadService,
                certificateRepository,
                crlRepository
        );
    }

    // ======================== Sync Initiation Tests ========================

    @Test
    @DisplayName("startFullSync should return SyncSession with FULL mode")
    void testStartFullSyncSuccess() {
        // When
        LdapSyncService.SyncSession session = adapter.startFullSync();

        // Then
        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotNull();
        assertThat(session.getMode()).isEqualTo("FULL");
        assertThat(session.getStartedAt()).isNotNull();
        assertThat(session.getState()).isEqualTo(LdapSyncService.SyncStatus.State.PENDING);
        assertThat(session.getDescription()).contains("Full synchronization");
    }

    @Test
    @DisplayName("startIncrementalSync should return SyncSession with INCREMENTAL mode")
    void testStartIncrementalSyncSuccess() {
        // When
        LdapSyncService.SyncSession session = adapter.startIncrementalSync();

        // Then
        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotNull();
        assertThat(session.getMode()).isEqualTo("INCREMENTAL");
        assertThat(session.getStartedAt()).isNotNull();
        assertThat(session.getState()).isEqualTo(LdapSyncService.SyncStatus.State.PENDING);
    }

    @Test
    @DisplayName("startSelectiveSync should return SyncSession with SELECTIVE mode and filter description")
    void testStartSelectiveSyncSuccess() {
        // Given
        String filter = "countryCode=KR";

        // When
        LdapSyncService.SyncSession session = adapter.startSelectiveSync(filter);

        // Then
        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotNull();
        assertThat(session.getMode()).isEqualTo("SELECTIVE");
        assertThat(session.getDescription()).contains(filter);
    }

    @Test
    @DisplayName("startSelectiveSync should throw exception when filter is null")
    void testStartSelectiveSyncNullFilter() {
        // When & Then
        assertThatThrownBy(() -> adapter.startSelectiveSync(null))
                .isInstanceOf(LdapSyncService.LdapSyncException.class);
    }

    @Test
    @DisplayName("startSelectiveSync should throw exception when filter is blank")
    void testStartSelectiveSyncBlankFilter() {
        // When & Then
        assertThatThrownBy(() -> adapter.startSelectiveSync("   "))
                .isInstanceOf(LdapSyncService.LdapSyncException.class);
    }

    // ======================== Sync Control Tests ========================

    @Test
    @DisplayName("cancelSync should return false when session not found")
    void testCancelSyncNotFound() {
        // Given
        UUID nonExistentSessionId = UUID.randomUUID();

        // When
        boolean result = adapter.cancelSync(nonExistentSessionId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("cancelSync should throw exception when sessionId is null")
    void testCancelSyncNullSessionId() {
        // When & Then
        assertThatThrownBy(() -> adapter.cancelSync(null))
                .isInstanceOf(LdapSyncService.LdapSyncException.class);
    }

    // ======================== Sync Status Tests ========================

    @Test
    @DisplayName("getSyncStatus should return empty Optional when sessionId not found")
    void testGetSyncStatusNotFound() {
        // Given
        UUID nonExistentSessionId = UUID.randomUUID();

        // When
        Optional<LdapSyncService.SyncStatus> result = adapter.getSyncStatus(nonExistentSessionId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getSyncStatus should return empty Optional when sessionId is null")
    void testGetSyncStatusNullSessionId() {
        // When
        Optional<LdapSyncService.SyncStatus> result = adapter.getSyncStatus(null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("waitForCompletion should throw exception when sessionId is null")
    void testWaitForCompletionNullSessionId() {
        // When & Then
        assertThatThrownBy(() -> adapter.waitForCompletion(null, 30))
                .isInstanceOf(LdapSyncService.LdapSyncException.class);
    }

    // ======================== Sync History Tests ========================

    @Test
    @DisplayName("getSyncHistory should return empty list (stub)")
    void testGetSyncHistorySuccess() {
        // Given
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        int limit = 100;

        // When
        List<LdapSyncService.SyncSession> result = adapter.getSyncHistory(from, limit);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();  // Stub returns empty
    }

    @Test
    @DisplayName("getSyncHistory should throw exception when from is null")
    void testGetSyncHistoryNullFrom() {
        // When & Then
        assertThatThrownBy(() -> adapter.getSyncHistory(null, 100))
                .isInstanceOf(LdapSyncService.LdapSyncException.class);
    }

    @Test
    @DisplayName("getSyncHistory should throw exception when limit is invalid")
    void testGetSyncHistoryInvalidLimit() {
        // When & Then
        assertThatThrownBy(() -> adapter.getSyncHistory(LocalDateTime.now(), 0))
                .isInstanceOf(LdapSyncService.LdapSyncException.class);
    }

    @Test
    @DisplayName("getLatestSync should return empty Optional (stub)")
    void testGetLatestSyncSuccess() {
        // When
        Optional<LdapSyncService.SyncSession> result = adapter.getLatestSync();

        // Then
        assertThat(result).isEmpty();  // Stub returns empty
    }

    @Test
    @DisplayName("getLastSuccessfulSyncTime should return empty Optional (stub)")
    void testGetLastSuccessfulSyncTimeSuccess() {
        // When
        Optional<LocalDateTime> result = adapter.getLastSuccessfulSyncTime();

        // Then
        assertThat(result).isEmpty();  // Stub returns empty
    }

    // ======================== Entity Sync Status Tests ========================

    @Test
    @DisplayName("isSynced should return false (stub)")
    void testIsSyncedSuccess() {
        // Given
        UUID entityId = UUID.randomUUID();

        // When
        boolean result = adapter.isSynced(entityId);

        // Then
        assertThat(result).isFalse();  // Stub returns false
    }

    @Test
    @DisplayName("isSynced should return false when entityId is null")
    void testIsSyncedNullEntityId() {
        // When
        boolean result = adapter.isSynced(null);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("countPendingEntities should return 0 (stub)")
    void testCountPendingEntitiesSuccess() {
        // When
        long result = adapter.countPendingEntities();

        // Then
        assertThat(result).isZero();  // Stub returns 0
    }

    // ======================== Sync Retry Tests ========================

    @Test
    @DisplayName("retryFailedEntries should throw exception when sessionId is null")
    void testRetryFailedEntriesNullSessionId() {
        // When & Then
        assertThatThrownBy(() -> adapter.retryFailedEntries(null))
                .isInstanceOf(LdapSyncService.LdapSyncException.class);
    }

    @Test
    @DisplayName("retryFailedEntries should throw exception when session not found")
    void testRetryFailedEntriesSessionNotFound() {
        // Given
        UUID nonExistentSessionId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> adapter.retryFailedEntries(nonExistentSessionId))
                .isInstanceOf(LdapSyncService.LdapSyncException.class);
    }

    // ======================== Sync Statistics Tests ========================

    @Test
    @DisplayName("getStatistics should return SyncStatistics (stub)")
    void testGetStatisticsSuccess() {
        // When
        LdapSyncService.SyncStatistics result = adapter.getStatistics();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalSynced()).isZero();  // Stub returns 0
        assertThat(result.getTotalFailed()).isZero();
        assertThat(result.getAverageSyncTimeSeconds()).isZero();
        assertThat(result.getSuccessRate()).isZero();
        assertThat(result.getTodaysSyncCount()).isZero();
    }

    // ======================== Integration Tests ========================

    @Test
    @DisplayName("Adapter should be instantiated with LdapTemplate")
    void testAdapterInstantiation() {
        // Then
        assertThat(adapter).isNotNull();
    }

    @Test
    @DisplayName("Multiple sync sessions should be independent")
    void testMultipleSyncSessionsIndependent() {
        // When
        LdapSyncService.SyncSession session1 = adapter.startFullSync();
        LdapSyncService.SyncSession session2 = adapter.startIncrementalSync();

        // Then
        assertThat(session1).isNotNull();
        assertThat(session2).isNotNull();
        assertThat(session1.getId()).isNotEqualTo(session2.getId());
        assertThat(session1.getMode()).isEqualTo("FULL");
        assertThat(session2.getMode()).isEqualTo("INCREMENTAL");
    }

    // ======================== Async Execution Tests ========================

    @Test
    @DisplayName("Full sync should execute asynchronously and complete")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testFullSyncAsyncExecution() throws Exception {
        // When: Start full sync
        LdapSyncService.SyncSession session = adapter.startFullSync();
        UUID sessionId = session.getId();

        // Then: Session created immediately (non-blocking)
        assertThat(session).isNotNull();
        assertThat(session.getState()).isIn(
                LdapSyncService.SyncStatus.State.PENDING,
                LdapSyncService.SyncStatus.State.IN_PROGRESS
        );

        // When: Wait for completion with timeout
        LdapSyncService.SyncResult result = adapter.waitForCompletion(sessionId, 5);

        // Then: Sync completed successfully
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStartedAt()).isNotNull();
        assertThat(result.getFinishedAt()).isNotNull();
        assertThat(result.getDurationSeconds()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Incremental sync should execute asynchronously and complete")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testIncrementalSyncAsyncExecution() throws Exception {
        // When: Start incremental sync
        LdapSyncService.SyncSession session = adapter.startIncrementalSync();
        UUID sessionId = session.getId();

        // Then: Returns immediately
        assertThat(session).isNotNull();

        // When: Wait for completion
        LdapSyncService.SyncResult result = adapter.waitForCompletion(sessionId, 5);

        // Then: Completed successfully
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Selective sync should execute asynchronously and complete")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testSelectiveSyncAsyncExecution() throws Exception {
        // Given
        String filter = "countryCode=KR";

        // When: Start selective sync
        LdapSyncService.SyncSession session = adapter.startSelectiveSync(filter);
        UUID sessionId = session.getId();

        // Then: Returns immediately
        assertThat(session).isNotNull();

        // When: Wait for completion
        LdapSyncService.SyncResult result = adapter.waitForCompletion(sessionId, 5);

        // Then: Completed successfully
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Sync status should transition from PENDING to IN_PROGRESS to SUCCESS")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testSyncStateTransitions() {
        // When: Start full sync
        LdapSyncService.SyncSession session = adapter.startFullSync();
        UUID sessionId = session.getId();

        // Then: Initial state is PENDING
        assertThat(session.getState()).isEqualTo(LdapSyncService.SyncStatus.State.PENDING);

        // Wait for state to transition to IN_PROGRESS (async execution started)
        await()
                .atMost(2, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    Optional<LdapSyncService.SyncStatus> status = adapter.getSyncStatus(sessionId);
                    return status.isPresent() &&
                           status.get().getState() == LdapSyncService.SyncStatus.State.IN_PROGRESS;
                });

        // Wait for completion
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    Optional<LdapSyncService.SyncStatus> status = adapter.getSyncStatus(sessionId);
                    return status.isPresent() &&
                           status.get().getState() == LdapSyncService.SyncStatus.State.SUCCESS;
                });

        // Verify final state
        Optional<LdapSyncService.SyncStatus> finalStatus = adapter.getSyncStatus(sessionId);
        assertThat(finalStatus).isPresent();
        assertThat(finalStatus.get().getState()).isEqualTo(LdapSyncService.SyncStatus.State.SUCCESS);
    }

    @Test
    @DisplayName("getSyncStatus should return current status during execution")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testGetSyncStatusDuringExecution() {
        // When: Start sync
        LdapSyncService.SyncSession session = adapter.startFullSync();
        UUID sessionId = session.getId();

        // Then: Status should be available
        Optional<LdapSyncService.SyncStatus> status = adapter.getSyncStatus(sessionId);
        assertThat(status).isPresent();
        assertThat(status.get().getSessionId()).isEqualTo(sessionId);
        assertThat(status.get().getState()).isIn(
                LdapSyncService.SyncStatus.State.PENDING,
                LdapSyncService.SyncStatus.State.IN_PROGRESS
        );
        assertThat(status.get().getTotalCount()).isGreaterThanOrEqualTo(0);
        assertThat(status.get().getProcessedCount()).isGreaterThanOrEqualTo(0);
    }

    // ======================== Cancellation Tests ========================

    @Test
    @DisplayName("cancelSync should successfully cancel a running sync")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testCancelRunningSync() {
        // When: Start full sync
        LdapSyncService.SyncSession session = adapter.startFullSync();
        UUID sessionId = session.getId();

        // Allow sync to start
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When: Cancel sync
        boolean cancelled = adapter.cancelSync(sessionId);

        // Then: Cancellation successful
        assertThat(cancelled).isTrue();

        // Wait for state to update
        await()
                .atMost(2, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .until(() -> {
                    Optional<LdapSyncService.SyncStatus> status = adapter.getSyncStatus(sessionId);
                    return status.isPresent() &&
                           status.get().getState() == LdapSyncService.SyncStatus.State.CANCELLED;
                });

        // Verify cancelled state
        Optional<LdapSyncService.SyncStatus> status = adapter.getSyncStatus(sessionId);
        assertThat(status).isPresent();
        assertThat(status.get().getState()).isEqualTo(LdapSyncService.SyncStatus.State.CANCELLED);
    }

    @Test
    @DisplayName("cancelSync should return false when sync already completed")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testCancelCompletedSync() throws Exception {
        // Given: Completed sync
        LdapSyncService.SyncSession session = adapter.startFullSync();
        UUID sessionId = session.getId();
        adapter.waitForCompletion(sessionId, 5);

        // When: Try to cancel completed sync
        boolean cancelled = adapter.cancelSync(sessionId);

        // Then: Cannot cancel
        assertThat(cancelled).isFalse();
    }

    @Test
    @DisplayName("cancelSync should return false when cancelling already cancelled sync")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testCancelAlreadyCancelledSync() {
        // Given: Cancelled sync
        LdapSyncService.SyncSession session = adapter.startFullSync();
        UUID sessionId = session.getId();
        adapter.cancelSync(sessionId);

        // When: Try to cancel again
        boolean cancelled = adapter.cancelSync(sessionId);

        // Then: Cannot cancel
        assertThat(cancelled).isFalse();
    }

    // ======================== Timeout Tests ========================

    @Test
    @DisplayName("waitForCompletion should timeout if sync takes too long")
    void testWaitForCompletionTimeout() {
        // When: Start sync
        LdapSyncService.SyncSession session = adapter.startFullSync();
        UUID sessionId = session.getId();

        // Then: Timeout with very short timeout (0 seconds)
        assertThatThrownBy(() -> adapter.waitForCompletion(sessionId, 0))
                .isInstanceOf(LdapSyncService.LdapSyncTimeoutException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    @DisplayName("waitForCompletion should succeed within timeout")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testWaitForCompletionWithinTimeout() throws Exception {
        // When: Start sync
        LdapSyncService.SyncSession session = adapter.startFullSync();
        UUID sessionId = session.getId();

        // Then: Should complete within reasonable timeout
        LdapSyncService.SyncResult result = adapter.waitForCompletion(sessionId, 5);
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("waitForCompletion should throw exception when session not found")
    void testWaitForCompletionSessionNotFound() {
        // Given
        UUID nonExistentSessionId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> adapter.waitForCompletion(nonExistentSessionId, 5))
                .isInstanceOf(LdapSyncService.LdapSyncException.class);
    }

    // ======================== Concurrent Sync Tests ========================

    @Test
    @DisplayName("Only one sync should be allowed at a time")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testOnlyOneSyncAllowed() {
        // Given: First sync running
        LdapSyncService.SyncSession session1 = adapter.startFullSync();

        // When: Try to start another sync
        assertThatThrownBy(() -> adapter.startFullSync())
                .isInstanceOf(LdapSyncService.LdapSyncException.class)
                .hasMessageContaining("already in progress");
    }

    @Test
    @DisplayName("Should allow new sync after previous sync completes")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testAllowSyncAfterCompletion() throws Exception {
        // Given: First sync completed
        LdapSyncService.SyncSession session1 = adapter.startFullSync();
        adapter.waitForCompletion(session1.getId(), 5);

        // When: Start second sync
        LdapSyncService.SyncSession session2 = adapter.startFullSync();

        // Then: Second sync should succeed
        assertThat(session2).isNotNull();
        assertThat(session2.getId()).isNotEqualTo(session1.getId());

        // Wait for second sync to complete
        LdapSyncService.SyncResult result = adapter.waitForCompletion(session2.getId(), 5);
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should allow new sync after previous sync is cancelled")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testAllowSyncAfterCancellation() {
        // Given: First sync cancelled
        LdapSyncService.SyncSession session1 = adapter.startFullSync();
        adapter.cancelSync(session1.getId());

        // Wait for cancellation to complete
        await()
                .atMost(2, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .until(() -> {
                    Optional<LdapSyncService.SyncStatus> status = adapter.getSyncStatus(session1.getId());
                    return status.isPresent() &&
                           status.get().getState() == LdapSyncService.SyncStatus.State.CANCELLED;
                });

        // When: Start second sync
        LdapSyncService.SyncSession session2 = adapter.startFullSync();

        // Then: Second sync should succeed
        assertThat(session2).isNotNull();
        assertThat(session2.getId()).isNotEqualTo(session1.getId());
    }
}
