package com.smartcoreinc.localpkd.passiveauthentication.infrastructure.repository;

import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassiveAuthenticationAuditLog;
import com.smartcoreinc.localpkd.passiveauthentication.domain.model.PassportDataId;
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
     * <p>Returns logs ordered by creation time (oldest first).
     *
     * @param passportDataId PassportData ID
     * @return list of audit logs ordered by createdAt
     */
    @Query("SELECT a FROM PassiveAuthenticationAuditLog a WHERE a.passportDataId = :passportDataId ORDER BY a.createdAt ASC")
    List<PassiveAuthenticationAuditLog> findByPassportDataId(@Param("passportDataId") PassportDataId passportDataId);

    /**
     * Find audit logs created within a time range.
     *
     * @param startTime start of the time range
     * @param endTime end of the time range
     * @return list of audit logs created within the given range
     */
    @Query("SELECT a FROM PassiveAuthenticationAuditLog a WHERE a.createdAt BETWEEN :startTime AND :endTime ORDER BY a.createdAt ASC")
    List<PassiveAuthenticationAuditLog> findByCreatedAtBetween(
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
    @Query("DELETE FROM PassiveAuthenticationAuditLog a WHERE a.createdAt < :cutoffDate")
    int deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Count audit logs for a specific PassportData.
     *
     * @param passportDataId PassportData ID
     * @return count of audit logs
     */
    @Query("SELECT COUNT(a) FROM PassiveAuthenticationAuditLog a WHERE a.passportDataId = :passportDataId")
    long countByPassportDataId(@Param("passportDataId") PassportDataId passportDataId);
}
