package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit log entry for verification steps.
 *
 * <p>Records detailed step-by-step execution of Passive Authentication:
 * <ul>
 *   <li>Which verification step was executed</li>
 *   <li>Current status of the step</li>
 *   <li>Timing information</li>
 *   <li>Step-specific messages and details</li>
 *   <li>Log level for filtering</li>
 * </ul>
 *
 * <p>This entity is subordinate to PassportData aggregate and provides
 * a complete audit trail for compliance and debugging purposes.
 */
@Entity
@Table(
    name = "passport_verification_audit_log",
    indexes = {
        @Index(name = "idx_audit_passport_id", columnList = "passport_data_id"),
        @Index(name = "idx_audit_step", columnList = "step"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_log_level", columnList = "log_level")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // For JPA
public class PassiveAuthenticationAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "passport_data_id", nullable = false)
    private UUID passportDataId;

    @Enumerated(EnumType.STRING)
    @Column(name = "step", length = 50, nullable = false)
    private PassiveAuthenticationStep step;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_status", length = 20, nullable = false)
    private StepStatus stepStatus;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "log_level", length = 10, nullable = false)
    private LogLevel logLevel;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    /**
     * Create audit log entry for verification step start.
     *
     * @param passportDataId ID of PassportData aggregate
     * @param step verification step
     * @param message log message
     * @return PassiveAuthenticationAuditLog instance
     */
    public static PassiveAuthenticationAuditLog stepStarted(
        PassportDataId passportDataId,
        PassiveAuthenticationStep step,
        String message
    ) {
        PassiveAuthenticationAuditLog log = new PassiveAuthenticationAuditLog();
        log.passportDataId = passportDataId.getId();
        log.step = step;
        log.stepStatus = StepStatus.STARTED;
        log.timestamp = LocalDateTime.now();
        log.logLevel = LogLevel.INFO;
        log.message = message;
        return log;
    }

    /**
     * Create audit log entry for verification step in progress.
     *
     * @param passportDataId ID of PassportData aggregate
     * @param step verification step
     * @param message log message
     * @param details additional details
     * @return PassiveAuthenticationAuditLog instance
     */
    public static PassiveAuthenticationAuditLog stepInProgress(
        PassportDataId passportDataId,
        PassiveAuthenticationStep step,
        String message,
        String details
    ) {
        PassiveAuthenticationAuditLog log = new PassiveAuthenticationAuditLog();
        log.passportDataId = passportDataId.getId();
        log.step = step;
        log.stepStatus = StepStatus.IN_PROGRESS;
        log.timestamp = LocalDateTime.now();
        log.logLevel = LogLevel.DEBUG;
        log.message = message;
        log.details = details;
        return log;
    }

    /**
     * Create audit log entry for verification step completion.
     *
     * @param passportDataId ID of PassportData aggregate
     * @param step verification step
     * @param message log message
     * @param executionTimeMs step execution time in milliseconds
     * @return PassiveAuthenticationAuditLog instance
     */
    public static PassiveAuthenticationAuditLog stepCompleted(
        PassportDataId passportDataId,
        PassiveAuthenticationStep step,
        String message,
        Long executionTimeMs
    ) {
        PassiveAuthenticationAuditLog log = new PassiveAuthenticationAuditLog();
        log.passportDataId = passportDataId.getId();
        log.step = step;
        log.stepStatus = StepStatus.COMPLETED;
        log.timestamp = LocalDateTime.now();
        log.logLevel = LogLevel.INFO;
        log.message = message;
        log.executionTimeMs = executionTimeMs;
        return log;
    }

    /**
     * Create audit log entry for verification step failure.
     *
     * @param passportDataId ID of PassportData aggregate
     * @param step verification step
     * @param message error message
     * @param details error details
     * @return PassiveAuthenticationAuditLog instance
     */
    public static PassiveAuthenticationAuditLog stepFailed(
        PassportDataId passportDataId,
        PassiveAuthenticationStep step,
        String message,
        String details
    ) {
        PassiveAuthenticationAuditLog log = new PassiveAuthenticationAuditLog();
        log.passportDataId = passportDataId.getId();
        log.step = step;
        log.stepStatus = StepStatus.FAILED;
        log.timestamp = LocalDateTime.now();
        log.logLevel = LogLevel.ERROR;
        log.message = message;
        log.details = details;
        return log;
    }

    /**
     * Create custom audit log entry.
     *
     * @param passportDataId ID of PassportData aggregate
     * @param step verification step
     * @param stepStatus step status
     * @param logLevel log level
     * @param message log message
     * @param details additional details
     * @return PassiveAuthenticationAuditLog instance
     */
    public static PassiveAuthenticationAuditLog of(
        PassportDataId passportDataId,
        PassiveAuthenticationStep step,
        StepStatus stepStatus,
        LogLevel logLevel,
        String message,
        String details
    ) {
        PassiveAuthenticationAuditLog log = new PassiveAuthenticationAuditLog();
        log.passportDataId = passportDataId.getId();
        log.step = step;
        log.stepStatus = stepStatus;
        log.timestamp = LocalDateTime.now();
        log.logLevel = logLevel;
        log.message = message;
        log.details = details;
        return log;
    }

    /**
     * Set execution time for this step.
     *
     * @param executionTimeMs execution time in milliseconds
     */
    public void setExecutionTime(Long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    /**
     * Check if this is an error log.
     *
     * @return true if log level is ERROR
     */
    public boolean isError() {
        return logLevel == LogLevel.ERROR;
    }

    /**
     * Check if this is a warning log.
     *
     * @return true if log level is WARN
     */
    public boolean isWarning() {
        return logLevel == LogLevel.WARN;
    }

    /**
     * Check if step failed.
     *
     * @return true if step status is FAILED
     */
    public boolean isFailed() {
        return stepStatus == StepStatus.FAILED;
    }

    /**
     * Check if step completed successfully.
     *
     * @return true if step status is COMPLETED
     */
    public boolean isCompleted() {
        return stepStatus == StepStatus.COMPLETED;
    }
}
