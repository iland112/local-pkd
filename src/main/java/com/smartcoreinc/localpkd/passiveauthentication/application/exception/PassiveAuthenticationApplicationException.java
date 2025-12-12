package com.smartcoreinc.localpkd.passiveauthentication.application.exception;

import com.smartcoreinc.localpkd.shared.exception.BusinessException;

/**
 * Application layer exception for Passive Authentication operations.
 * <p>
 * Thrown when PA verification encounters business logic violations or
 * application-level errors that prevent successful completion.
 * </p>
 */
public class PassiveAuthenticationApplicationException extends BusinessException {

    /**
     * Creates exception with error code and message.
     *
     * @param errorCode Error code
     * @param message Error message
     */
    public PassiveAuthenticationApplicationException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * Creates exception with error code, message, and cause.
     *
     * @param errorCode Error code
     * @param message Error message
     * @param cause Cause exception
     */
    public PassiveAuthenticationApplicationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
