package com.smartcoreinc.localpkd.passiveauthentication.domain.repository;

import com.smartcoreinc.localpkd.passiveauthentication.domain.model.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for PassiveAuthenticationAuditLog entity.
 *
 * <p>Provides persistence and query operations for verification audit logs,
 * supporting audit trail and compliance requirements.
 */
public interface PassiveAuthenticationAuditLogRepository {

    /**
     * Save PassiveAuthenticationAuditLog entry.
     *
     * @param log PassiveAuthenticationAuditLog to save
     * @return saved PassiveAuthenticationAuditLog
     */
    PassiveAuthenticationAuditLog save(PassiveAuthenticationAuditLog log);

    /**
     * Save multiple PassiveAuthenticationAuditLog entries.
     *
     * @param logs iterable of PassiveAuthenticationAuditLog entries
     * @return list of saved PassiveAuthenticationAuditLog entries
     */
    <S extends PassiveAuthenticationAuditLog> List<S> saveAll(Iterable<S> logs);

    /**
     * Find audit logs by PassportData ID.
     *
     * @param passportDataId PassportData ID
     * @return list of audit logs for given PassportData
     */
    List<PassiveAuthenticationAuditLog> findByPassportDataId(PassportDataId passportDataId);

    /**
     * Find audit logs by PassportData ID and verification step.
     *
     * @param passportDataId PassportData ID
     * @param step verification step
     * @return list of audit logs for given PassportData and step
     */
    List<PassiveAuthenticationAuditLog> findByPassportDataIdAndStep(
        PassportDataId passportDataId,
        PassiveAuthenticationStep step
    );

    /**
     * Find audit logs by PassportData ID and step status.
     *
     * @param passportDataId PassportData ID
     * @param stepStatus step status
     * @return list of audit logs for given PassportData and status
     */
    List<PassiveAuthenticationAuditLog> findByPassportDataIdAndStepStatus(
        PassportDataId passportDataId,
        StepStatus stepStatus
    );

    /**
     * Find audit logs by log level.
     *
     * @param logLevel log level
     * @return list of audit logs with given log level
     */
    List<PassiveAuthenticationAuditLog> findByLogLevel(LogLevel logLevel);

    /**
     * Find error logs (log level = ERROR).
     *
     * @return list of error logs
     */
    List<PassiveAuthenticationAuditLog> findErrorLogs();

    /**
     * Find failed step logs (step status = FAILED).
     *
     * @return list of failed step logs
     */
    List<PassiveAuthenticationAuditLog> findFailedSteps();

    /**
     * Find audit logs within date range.
     *
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @return list of audit logs in date range
     */
    List<PassiveAuthenticationAuditLog> findByTimestampBetween(
        LocalDateTime startDate,
        LocalDateTime endDate
    );

    /**
     * Find audit logs by PassportData ID ordered by timestamp.
     *
     * @param passportDataId PassportData ID
     * @return list of audit logs ordered by timestamp ascending
     */
    List<PassiveAuthenticationAuditLog> findByPassportDataIdOrderByTimestampAsc(PassportDataId passportDataId);

    /**
     * Count audit logs by PassportData ID.
     *
     * @param passportDataId PassportData ID
     * @return count of audit logs for given PassportData
     */
    long countByPassportDataId(PassportDataId passportDataId);

    /**
     * Count error logs.
     *
     * @return count of error logs
     */
    long countErrorLogs();

    /**
     * Count failed steps.
     *
     * @return count of failed steps
     */
    long countFailedSteps();

    /**
     * Delete audit logs by PassportData ID.
     *
     * @param passportDataId PassportData ID
     */
    void deleteByPassportDataId(PassportDataId passportDataId);

    /**
     * Delete audit logs older than specified date.
     *
     * @param date cutoff date
     */
    void deleteByTimestampBefore(LocalDateTime date);
}
