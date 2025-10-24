package com.smartcoreinc.localpkd.ldapintegration.domain.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * LdapSyncService - LDAP 동기화 관리 Port (Hexagonal Architecture)
 *
 * <p><b>Hexagonal Architecture Port</b>:</p>
 * <ul>
 *   <li>로컬 DB와 LDAP 서버 간 동기화 상태 관리</li>
 *   <li>배치 동기화 스케줄링</li>
 *   <li>동기화 오류 추적 및 재시도</li>
 *   <li>변경사항 감지 및 처리</li>
 * </ul>
 *
 * <h3>책임</h3>
 * <ul>
 *   <li>동기화 세션 생성 및 관리</li>
 *   <li>동기화 상태 추적 (대기, 진행 중, 완료, 실패)</li>
 *   <li>증분 동기화 지원</li>
 *   <li>재시도 로직 관리</li>
 *   <li>동기화 이력 기록</li>
 * </ul>
 *
 * <h3>동기화 모드</h3>
 * <ul>
 *   <li><b>Full Sync</b>: 모든 데이터 재동기화 (초기 구성, 재해 복구)</li>
 *   <li><b>Incremental Sync</b>: 마지막 동기화 이후 변경사항만 (정기 동기화)</li>
 *   <li><b>Selective Sync</b>: 특정 필터/범위로 동기화 (수동 동기화)</li>
 * </ul>
 *
 * <h3>동기화 상태 전이</h3>
 * <pre>{@code
 * PENDING
 *   ↓
 * IN_PROGRESS → SUCCESS
 *              ↘ FAILED (→ PENDING: 재시도)
 * }</pre>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // 전체 동기화 시작
 * SyncSession session = ldapSyncService.startFullSync();
 * log.info("Sync session started: {}", session.getId());
 *
 * // 증분 동기화 시작
 * SyncSession incrementalSession = ldapSyncService.startIncrementalSync();
 *
 * // 동기화 진행률 확인
 * SyncStatus status = ldapSyncService.getSyncStatus(session.getId());
 * log.info("Progress: {}/{} ({}%)",
 *     status.getProcessedCount(),
 *     status.getTotalCount(),
 *     status.getProgressPercentage()
 * );
 *
 * // 동기화 결과 확인
 * SyncResult result = ldapSyncService.waitForCompletion(session.getId(), timeout);
 * if (result.isSuccess()) {
 *     log.info("Sync completed successfully");
 * } else {
 *     log.error("Sync failed: {}", result.getErrorMessage());
 * }
 *
 * // 동기화 이력 조회
 * List<SyncSession> history = ldapSyncService.getSyncHistory(
 *     LocalDateTime.now().minusDays(7), 100
 * );
 * }</pre>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-10-25
 */
public interface LdapSyncService {

    /**
     * 전체 동기화(Full Sync) 시작
     *
     * <p>모든 Certificate/CRL 데이터를 LDAP 서버에 동기화합니다.
     * 기존 데이터는 덮어씁니다.</p>
     *
     * @return 동기화 세션
     * @throws LdapSyncException 동기화 시작 실패 시
     */
    SyncSession startFullSync();

    /**
     * 증분 동기화(Incremental Sync) 시작
     *
     * <p>마지막 성공한 동기화 이후 변경된 데이터만 동기화합니다.
     * 초기 동기화가 필요한 경우 전체 동기화로 실행됩니다.</p>
     *
     * @return 동기화 세션
     * @throws LdapSyncException 동기화 시작 실패 시
     */
    SyncSession startIncrementalSync();

    /**
     * 선택적 동기화(Selective Sync) 시작
     *
     * <p>특정 범위(예: 특정 국가, 특정 인증서 타입)만 동기화합니다.</p>
     *
     * @param filter 동기화할 범위 필터
     * @return 동기화 세션
     */
    SyncSession startSelectiveSync(String filter);

    /**
     * 실행 중인 동기화 세션 취소
     *
     * @param sessionId 세션 ID
     * @return 취소 성공 여부
     */
    boolean cancelSync(UUID sessionId);

    /**
     * 특정 동기화 세션의 상태 조회
     *
     * @param sessionId 세션 ID
     * @return 동기화 상태
     */
    Optional<SyncStatus> getSyncStatus(UUID sessionId);

    /**
     * 동기화 완료 대기 (블로킹)
     *
     * <p>동기화가 완료될 때까지 대기합니다.</p>
     *
     * @param sessionId 세션 ID
     * @param timeoutSeconds 타임아웃 시간 (초)
     * @return 동기화 결과
     * @throws LdapSyncException 타임아웃 또는 기타 오류 시
     */
    SyncResult waitForCompletion(UUID sessionId, long timeoutSeconds);

    /**
     * 특정 기간의 동기화 이력 조회
     *
     * @param from 조회 시작 일시
     * @param limit 최대 결과 개수
     * @return 동기화 세션 리스트
     */
    List<SyncSession> getSyncHistory(LocalDateTime from, int limit);

    /**
     * 가장 최근 동기화 세션 조회
     *
     * @return 최근 세션
     */
    Optional<SyncSession> getLatestSync();

    /**
     * 마지막 성공한 동기화 시간 조회
     *
     * @return 마지막 성공 시간
     */
    Optional<LocalDateTime> getLastSuccessfulSyncTime();

    /**
     * 특정 데이터가 이미 동기화되었는지 확인
     *
     * @param entityId 엔티티 ID
     * @return 동기화 여부
     */
    boolean isSynced(UUID entityId);

    /**
     * 동기화되지 않은 엔티티 개수 조회
     *
     * @return 대기 중인 엔티티 개수
     */
    long countPendingEntities();

    /**
     * 동기화 실패 항목 재시도
     *
     * @param sessionId 세션 ID
     * @return 재시도 세션
     */
    SyncSession retryFailedEntries(UUID sessionId);

    /**
     * 동기화 통계 조회
     *
     * @return 동기화 통계
     */
    SyncStatistics getStatistics();

    // ==================== Domain Model Interfaces ====================

    /**
     * 동기화 세션 정보
     */
    interface SyncSession {
        UUID getId();
        String getMode();  // FULL, INCREMENTAL, SELECTIVE
        LocalDateTime getStartedAt();
        LocalDateTime getFinishedAt();
        SyncStatus.State getState();
        String getDescription();
    }

    /**
     * 동기화 진행 상태
     */
    interface SyncStatus {
        enum State {
            PENDING("대기 중"),
            IN_PROGRESS("진행 중"),
            SUCCESS("성공"),
            FAILED("실패"),
            CANCELLED("취소됨");

            private final String displayName;

            State(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return displayName;
            }
        }

        UUID getSessionId();
        State getState();
        long getTotalCount();
        long getProcessedCount();
        long getSuccessCount();
        long getFailedCount();
        int getProgressPercentage();
        LocalDateTime getUpdatedAt();
        String getCurrentTask();
        String getLastError();
    }

    /**
     * 동기화 결과
     */
    interface SyncResult {
        boolean isSuccess();
        int getSuccessCount();
        int getFailedCount();
        LocalDateTime getStartedAt();
        LocalDateTime getFinishedAt();
        long getDurationSeconds();
        String getMessage();
        String getErrorMessage();
        List<FailedItem> getFailedItems();

        interface FailedItem {
            UUID getEntityId();
            String getErrorMessage();
            int getRetryCount();
        }
    }

    /**
     * 동기화 통계
     */
    interface SyncStatistics {
        long getTotalSynced();
        long getTotalFailed();
        long getAverageSyncTimeSeconds();
        LocalDateTime getLastSyncTime();
        double getSuccessRate();
        int getTodaysSyncCount();
    }

    /**
     * LDAP 동기화 예외
     */
    class LdapSyncException extends RuntimeException {
        public LdapSyncException(String message) {
            super(message);
        }

        public LdapSyncException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 동기화 이미 진행 중 예외
     */
    class LdapSyncAlreadyInProgressException extends LdapSyncException {
        private final UUID sessionId;

        public LdapSyncAlreadyInProgressException(UUID sessionId) {
            super("Sync already in progress with session ID: " + sessionId);
            this.sessionId = sessionId;
        }

        public UUID getSessionId() {
            return sessionId;
        }
    }

    /**
     * 동기화 타임아웃 예외
     */
    class LdapSyncTimeoutException extends LdapSyncException {
        private final UUID sessionId;
        private final long timeoutSeconds;

        public LdapSyncTimeoutException(UUID sessionId, long timeoutSeconds) {
            super(String.format("Sync operation timed out after %d seconds", timeoutSeconds));
            this.sessionId = sessionId;
            this.timeoutSeconds = timeoutSeconds;
        }

        public UUID getSessionId() {
            return sessionId;
        }

        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }
    }
}
