package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

/**
 * Status of a verification step.
 *
 * <p>Each verification step can have one of the following statuses:
 * <ul>
 *   <li>STARTED: Step has just begun</li>
 *   <li>IN_PROGRESS: Step is currently executing</li>
 *   <li>COMPLETED: Step finished successfully</li>
 *   <li>FAILED: Step failed with errors</li>
 * </ul>
 */
public enum StepStatus {
    /**
     * Step has started.
     */
    STARTED,

    /**
     * Step is in progress.
     */
    IN_PROGRESS,

    /**
     * Step completed successfully.
     */
    COMPLETED,

    /**
     * Step failed.
     */
    FAILED
}
