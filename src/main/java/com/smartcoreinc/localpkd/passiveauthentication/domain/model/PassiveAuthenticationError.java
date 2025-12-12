package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

import com.smartcoreinc.localpkd.shared.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Passive Authentication error information.
 *
 * <p>Represents a specific error that occurred during Passive Authentication verification.
 * Includes error code, message, severity, and timestamp.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // For JPA
public class PassiveAuthenticationError {

    /**
     * Error severity level.
     */
    public enum Severity {
        /**
         * Critical error - verification cannot proceed.
         */
        CRITICAL,

        /**
         * Warning - verification can proceed but with caveats.
         */
        WARNING,

        /**
         * Informational message.
         */
        INFO
    }

    @Column(name = "error_code", length = 50)
    private String code;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_severity", length = 20)
    private Severity severity;

    @Column(name = "error_timestamp")
    private LocalDateTime timestamp;

    /**
     * Create a critical verification error.
     *
     * @param code error code
     * @param message error message
     * @return PassiveAuthenticationError instance
     */
    public static PassiveAuthenticationError critical(String code, String message) {
        return new PassiveAuthenticationError(code, message, Severity.CRITICAL, LocalDateTime.now());
    }

    /**
     * Create a warning verification error.
     *
     * @param code error code
     * @param message error message
     * @return PassiveAuthenticationError instance
     */
    public static PassiveAuthenticationError warning(String code, String message) {
        return new PassiveAuthenticationError(code, message, Severity.WARNING, LocalDateTime.now());
    }

    /**
     * Create an informational verification error.
     *
     * @param code error code
     * @param message error message
     * @return PassiveAuthenticationError instance
     */
    public static PassiveAuthenticationError info(String code, String message) {
        return new PassiveAuthenticationError(code, message, Severity.INFO, LocalDateTime.now());
    }

    private PassiveAuthenticationError(String code, String message, Severity severity, LocalDateTime timestamp) {
        validate(code, message);
        this.code = code;
        this.message = message;
        this.severity = severity;
        this.timestamp = timestamp;
    }

    private void validate(String code, String message) {
        if (code == null || code.isBlank()) {
            throw new DomainException("INVALID_ERROR_CODE", "Error code cannot be null or empty");
        }
        if (message == null || message.isBlank()) {
            throw new DomainException("INVALID_ERROR_MESSAGE", "Error message cannot be null or empty");
        }
    }

    /**
     * Check if this is a critical error.
     *
     * @return true if severity is CRITICAL
     */
    public boolean isCritical() {
        return severity == Severity.CRITICAL;
    }
}
