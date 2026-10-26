package com.skilllab.routeadviser.model;

/**
 * Step-free filtering policy used by the routing algorithm.
 * <ul>
 *   <li>{@code NONE} — no step-free filtering; used for the shortest-distance route.</li>
 *   <li>{@code AVOID_KNOWN_NO} — allow YES and UNKNOWN paths; exclude paths marked NO.</li>
 *   <li>{@code CONFIRMED_ONLY} — allow only YES paths; exclude NO and UNKNOWN.</li>
 * </ul>
 * Closed paths are excluded under every policy.
 */
public enum StepFreePolicy {
    NONE,
    AVOID_KNOWN_NO,
    CONFIRMED_ONLY
}
