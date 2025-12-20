package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

/**
 * Log level for audit trail.
 *
 * <p>Standard logging levels:
 * <ul>
 *   <li>DEBUG: Detailed diagnostic information</li>
 *   <li>INFO: Informational messages</li>
 *   <li>WARN: Warning messages</li>
 *   <li>ERROR: Error messages</li>
 * </ul>
 */
public enum LogLevel {
    /**
     * Debug level - detailed diagnostic information.
     */
    DEBUG,

    /**
     * Info level - informational messages.
     */
    INFO,

    /**
     * Warning level - potential issues.
     */
    WARN,

    /**
     * Error level - error events.
     */
    ERROR
}
