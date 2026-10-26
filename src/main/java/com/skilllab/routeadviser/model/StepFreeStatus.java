package com.skilllab.routeadviser.model;

/**
 * Whether a path segment between two buildings is step-free (no stairs/steps on the path itself).
 * <ul>
 *   <li>{@code YES} — confirmed step-free.</li>
 *   <li>{@code NO} — confirmed NOT step-free (has steps/stairs).</li>
 *   <li>{@code UNKNOWN} — step-free status is not known; never equal to YES.</li>
 * </ul>
 */
public enum StepFreeStatus {
    YES,
    NO,
    UNKNOWN
}
