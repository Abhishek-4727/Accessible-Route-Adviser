package com.skilllab.routeadviser.model;

/**
 * Overall step-free confidence for a found route, computed from its segments.
 * <ul>
 *   <li>{@code CONFIRMED_STEP_FREE} — every segment has stepFree == YES.</li>
 *   <li>{@code POSSIBLY_STEP_FREE} — no segment is NO, but at least one is UNKNOWN.</li>
 *   <li>{@code NOT_STEP_FREE} — at least one segment has stepFree == NO.</li>
 * </ul>
 */
public enum StepFreeConfidence {
    CONFIRMED_STEP_FREE,
    POSSIBLY_STEP_FREE,
    NOT_STEP_FREE
}
