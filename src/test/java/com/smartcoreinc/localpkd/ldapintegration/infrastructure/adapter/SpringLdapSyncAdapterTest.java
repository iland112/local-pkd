package com.smartcoreinc.localpkd.ldapintegration.infrastructure.adapter;

import com.smartcoreinc.localpkd.ldapintegration.domain.port.LdapSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.LdapTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

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

    private SpringLdapSyncAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpringLdapSyncAdapter(ldapTemplate);
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
}
