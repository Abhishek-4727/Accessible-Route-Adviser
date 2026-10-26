package com.skilllab.routeadviser.model;

/**
 * Availability status for building-level accessibility features.
 * <ul>
 *   <li>{@code YES} — the feature is confirmed present.</li>
 *   <li>{@code NO} — the feature is confirmed absent.</li>
 *   <li>{@code UNKNOWN} — presence is not known; never treat as YES or NO.</li>
 *   <li>{@code NOT_APPLICABLE} — the feature does not apply (e.g. ramp/stairs/lift on a GATE node).</li>
 * </ul>
 */
public enum AvailabilityStatus {
    YES,
    NO,
    UNKNOWN,
    NOT_APPLICABLE
}
