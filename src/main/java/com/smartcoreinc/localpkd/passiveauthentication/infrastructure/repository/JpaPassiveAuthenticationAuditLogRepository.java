package com.smartcoreinc.localpkd.passiveauthentication.infrastructure.repository;

import com.smartcoreinc.localpkd.passiveauthentication.domain.model.*;
import com.smartcoreinc.localpkd.passiveauthentication.domain.repository.PassiveAuthenticationAuditLogRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * JPA implementation of PassiveAuthenticationAuditLogRepository.
 *
 * <p>Provides persistence operations for PassiveAuthenticationAuditLog entities
 * using Spring Data JPA.
 *
 * <p><b>Infrastructure Layer Repository</b>:</p>
 * <ul>
 *   <li>Extends both JpaRepository (Spring Data) and PassiveAuthenticationAuditLogRepository (Domain)</li>
 *   <li>Provides CRUD operations via JpaRepository</li>
 *   <li>Implements custom queries and bulk operations</li>
 * </ul>
 *
 * <p><b>Audit Log Operations</b>:</p>
 * <ul>
 *   <li>findByPassportDataId - Retrieve audit trail for specific verification</li>
 *   <li>findByCreatedAtBetween - Find logs by date range</li>
 *   <li>deleteByPassportDataId - Cleanup logs for specific verification</li>
 *   <li>deleteOlderThan - Cleanup old audit logs</li>
 * </ul>
 */
@Repository
public interface JpaPassiveAuthenticationAuditLogRepository
    extends JpaRepository<PassiveAuthenticationAuditLog, UUID>,
            PassiveAuthenticationAuditLogRepository {

    /**
     * Find all audit logs for a specific PassportData verification.
     *
     * <p>Returns logs ordered by timestamp (oldest first).
     *
     * @param passportDataId PassportData ID
     * @return list of audit logs ordered by timestamp
     */
    @Query("SELECT a FROM PassiveAuthenticationAuditLog a WHERE a.passportDataId = :passportDataId ORDER BY a.timestamp ASC")
    List<PassiveAuthenticationAuditLog> findByPassportDataId(@Param("passportDataId") PassportDataId passportDataId);

    /**
     * Find audit logs created within a time range.
     *
     * @param startTime start of the time range
     * @param endTime end of the time range
     * @return list of audit logs created within the given range
     */
    @Query("SELECT a FROM PassiveAuthenticationAuditLog a WHERE a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp ASC")
    List<PassiveAuthenticationAuditLog> findByTimestampBetween(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Delete all audit logs for a specific PassportData.
     *
     * <p>Used for cleanup when PassportData is deleted or reset.
     *
     * @param passportDataId PassportData ID
     */
    @Modifying
    @Query("DELETE FROM PassiveAuthenticationAuditLog a WHERE a.passportDataId = :passportDataId")
    void deleteByPassportDataId(@Param("passportDataId") PassportDataId passportDataId);

    /**
     * Delete audit logs older than the specified date.
     *
     * <p>Used for periodic cleanup of old audit logs to manage database size.
     *
     * @param cutoffDate cutoff date (logs older than this will be deleted)
     * @return number of deleted logs
     */
    @Modifying
    @Query("DELETE FROM PassiveAuthenticationAuditLog a WHERE a.timestamp < :cutoffDate")
    int deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Count audit logs for a specific PassportData.
     *
     * @param passportDataId PassportData ID
     * @return count of audit logs
     */
    @Query("SELECT COUNT(a) FROM PassiveAuthenticationAuditLog a WHERE a.passportDataId = :passportDataId")
    long countByPassportDataId(@Param("passportDataId") PassportDataId passportDataId);

    /**
     * Find audit logs by PassportData ID and verification step.
     *
     * @param passportDataId PassportData ID
     * @param step verification step
     * @return list of audit logs for given PassportData and step
     */
    @Query("SELECT a FROM PassiveAuthenticationAuditLog a WHERE a.passportDataId = :passportDataId AND a.step = :step ORDER BY a.timestamp ASC")
    List<PassiveAuthenticationAuditLog> findByPassportDataIdAndStep(
        @Param("passportDataId") PassportDataId passportDataId,
        @Param("step") PassiveAuthenticationStep step
    );

    /**
     * Find audit logs by PassportData ID and step status.
     *
     * @param passportDataId PassportData ID
     * @param stepStatus step status
     * @return list of audit logs for given PassportData and status
     */
    @Query("SELECT a FROM PassiveAuthenticationAuditLog a WHERE a.passportDataId = :passportDataId AND a.stepStatus = :stepStatus ORDER BY a.timestamp ASC")
    List<PassiveAuthenticationAuditLog> findByPassportDataIdAndStepStatus(
        @Param("passportDataId") PassportDataId passportDataId,
        @Param("stepStatus") StepStatus stepStatus
    );

    /**
     * Find audit logs by log level.
     *
     * @param logLevel log level
     * @return list of audit logs with given log level
     */
    @Query("SELECT a FROM PassiveAuthenticationAuditLog a WHERE a.logLevel = :logLevel ORDER BY a.timestamp ASC")
    List<PassiveAuthenticationAuditLog> findByLogLevel(@Param("logLevel") LogLevel logLevel);

    /**
     * Find error logs (log level = ERROR).
     *
     * @return list of error logs
     */
    @Query("SELECT a FROM PassiveAuthenticationAuditLog a WHERE a.logLevel = 'ERROR' ORDER BY a.timestamp ASC")
    List<PassiveAuthenticationAuditLog> findErrorLogs();

    /**
     * Find failed step logs (step status = FAILED).
     *
     * @return list of failed step logs
     */
    @Query("SELECT a FROM PassiveAuthenticationAuditLog a WHERE a.stepStatus = 'FAILED' ORDER BY a.timestamp ASC")
    List<PassiveAuthenticationAuditLog> findFailedSteps();

    /**
     * Find audit logs by PassportData ID ordered by timestamp.
     *
     * @param passportDataId PassportData ID
     * @return list of audit logs ordered by timestamp ascending
     */
    @Query("SELECT a FROM PassiveAuthenticationAuditLog a WHERE a.passportDataId = :passportDataId ORDER BY a.timestamp ASC")
    List<PassiveAuthenticationAuditLog> findByPassportDataIdOrderByTimestampAsc(@Param("passportDataId") PassportDataId passportDataId);

    /**
     * Count error logs.
     *
     * @return count of error logs
     */
    @Query("SELECT COUNT(a) FROM PassiveAuthenticationAuditLog a WHERE a.logLevel = 'ERROR'")
    long countErrorLogs();

    /**
     * Count failed steps.
     *
     * @return count of failed steps
     */
    @Query("SELECT COUNT(a) FROM PassiveAuthenticationAuditLog a WHERE a.stepStatus = 'FAILED'")
    long countFailedSteps();

    /**
     * Delete audit logs older than specified date.
     *
     * @param date cutoff date
     */
    @Modifying
    @Query("DELETE FROM PassiveAuthenticationAuditLog a WHERE a.timestamp < :date")
    void deleteByTimestampBefore(@Param("date") LocalDateTime date);
}
